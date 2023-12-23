/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.igniterealtime.openfire.plugin.pubsubserverinfo;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.event.ServerSessionEventDispatcher;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.pubsub.LeafNode;
import org.jivesoftware.openfire.pubsub.PubSubEngine;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.io.File;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An Openfire plugin that periodically collects server information and publishes it on a pub-sub node.
 *
 * @author Guus der Kinderen
 * @see <a href="https://xmpp.org/extensions/inbox/xep-pubsub-server-info.html">XEP-xxxx: PubSub Server Info</a>
 */
public class PubSubServerInfoPlugin implements Plugin
{
    private static final Logger Log = LoggerFactory.getLogger(PubSubServerInfoPlugin.class);

    public static final SystemProperty<String> NODE_ID = SystemProperty.Builder.ofType(String.class)
        .setPlugin("PubSub Server Info")
        .setKey("plugin.pubsubserverinfo.nodeid")
        .setDefaultValue("serverinfo")
        .setDynamic(false)
        .build();

    public static final SystemProperty<Duration> REFRESH_INTERVAL = SystemProperty.Builder.ofType(Duration.class)
        .setPlugin("PubSub Server Info")
        .setKey("plugin.pubsubserverinfo.refresh.interval")
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDefaultValue(Duration.ofMinutes(5))
        .setDynamic(false)
        .build();

    private OptInDetector optInDetector;

    private final TimerTask task = new TimerTask()
    {
        @Override
        public void run()
        {
            populatePubSubNode();
        }
    };

    @Override
    public void initializePlugin( PluginManager manager, File pluginDirectory )
    {
        optInDetector = new OptInDetector();
        ServerSessionEventDispatcher.addListener(optInDetector);
        InterceptorManager.getInstance().addInterceptor(optInDetector);
        XMPPServer.getInstance().getIQDiscoInfoHandler().addServerFeature("urn:xmpp:serverinfo:0");
        optInDetector.init();

        // Delay the first publication of data with a few seconds, so that remote domains have a chance to answer the
        // disco#info queries that are sent by the optIn detector.
        final Duration delay = Duration.ofSeconds(10);
        TaskEngine.getInstance().schedule(task, delay, REFRESH_INTERVAL.getValue());
    }

    @Override
    public void destroyPlugin()
    {
        TaskEngine.getInstance().cancelScheduledTask(task);
        XMPPServer.getInstance().getIQDiscoInfoHandler().removeServerFeature("urn:xmpp:serverinfo:0");
        InterceptorManager.getInstance().removeInterceptor(optInDetector);
        ServerSessionEventDispatcher.removeListener(optInDetector);

        // Clear out items from the node, but do not delete the node. In case of a plugin reload, it is preferred to retain the node config and subscribers.
        clearPubSubNode();
    }

    public void clearPubSubNode() {
        final LeafNode node = (LeafNode) XMPPServer.getInstance().getPubSubModule().getNode(NODE_ID.getValue());
        if (node != null) {
            Log.trace("Retracting all published items from pub-sub node.");
            node.deleteItems(node.getPublishedItems());
        }
    }

    public void populatePubSubNode() {
        if (!ClusterManager.isSeniorClusterMember()) {
            Log.debug("Skip population of pub-sub node, as this server is not the senior node in the Openfire cluster.");
            return;
        }
        Log.trace("Populating pub-sub node...");
        final JID publisher = XMPPServer.getInstance().getAdmins().stream()
            .filter(j -> j.getDomain().equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain()))
            .filter(j -> UserManager.getInstance().isRegisteredUser(j, false))
            .collect(Collectors.toSet()).iterator().next();
        LeafNode node = (LeafNode) XMPPServer.getInstance().getPubSubModule().getNode(NODE_ID.getValue());
        if (node == null) {
            final PubSubEngine.CreateNodeResponse response = PubSubEngine.createNodeHelper(XMPPServer.getInstance().getPubSubModule(), publisher, null, NODE_ID.getValue(), null);
            if (response.newNode == null) {
                Log.warn("Unable to create the pub-sub node using {}. Condition: {}, error: {}", publisher, response.creationStatus, response.pubsubError);
            }
            node = (LeafNode) response.newNode;
        }
        if (node == null) {
            return;
        }
        Log.trace("Looking up servers...");

        final Set<String> incomingServers = new HashSet<>(XMPPServer.getInstance().getSessionManager().getIncomingServers());
        final Set<String> outgoingServers = new HashSet<>(XMPPServer.getInstance().getSessionManager().getOutgoingServers());

        Log.trace("Incoming/outgoing servers: {}/{}", incomingServers.size(), outgoingServers.size());
        final Element item = DocumentHelper.createElement(QName.get("item", "http://jabber.org/protocol/pubsub"));
        final Element serverinfo = item.addElement(QName.get("serverinfo", "urn:xmpp:serverinfo:0"));
        final Element domain = serverinfo.addElement("domain");
        domain.addAttribute("name", XMPPServer.getInstance().getServerInfo().getXMPPDomain());
        final Element federation = domain.addElement("federation");

        Log.trace("Processing outgoing servers...");
        for (final String outgoingServer : outgoingServers) {
            final String type;
            if (incomingServers.remove(outgoingServer)) {
                type = "both";
            } else {
                type = "incoming";
            }

            addRemoteDomain(federation, outgoingServer, type);
        }

        // Duplicates are removed above.
        Log.trace("Processing incoming servers...");
        for (final String incomingServer : incomingServers) {
            addRemoteDomain(federation, incomingServer, "incoming");
        }

        Log.trace("Publishing item: {}", item.asXML());
        node.publishItems(publisher, Collections.singletonList(item));
    }

    void addRemoteDomain(final Element federation, String domainName, String type) {
        Log.trace("add remote domain: {} type: {}", domainName, type);
        final Element remoteDomain = federation.addElement("remote-domain");
        final Element connection = remoteDomain.addElement("connection");
        if (optInDetector.optsIn(domainName)) {
            remoteDomain.addAttribute("name", domainName);
            connection.addAttribute("type", type);
        }
    }
}
