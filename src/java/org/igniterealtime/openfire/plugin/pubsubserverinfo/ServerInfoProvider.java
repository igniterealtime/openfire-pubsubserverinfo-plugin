/*
 * Copyright (C) 2024-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.disco.ExtendedDiscoInfoProvider;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.packet.JID;

import java.util.HashSet;
import java.util.Set;

public class ServerInfoProvider implements ExtendedDiscoInfoProvider
{
    /**
     * Returns a collection of data forms with extended information about the entity.
     * <p>
     * The returned Set may be empty but should not be null. It may be immutable.
     * Forms should include a FORM_TYPE field to enable proper merging.
     * </p>
     *
     * @param domain the domain of the target JID (e.g., "localhost", "conference.localhost", "pubsub.localhost")
     * @param name the node part of the target JID (null for service-level queries)
     * @param node the requested disco node parameter (null if not specified)
     * @param senderJID the XMPPAddress of user that sent the disco info request
     * @return A Set of data forms (possibly empty, never null). May be immutable.
     */
    @Override
    public Set<DataForm> getExtendedInfos(String domain, String name, String node, JID senderJID)
    {
        final Set<DataForm> result = new HashSet<>();

        // Only respond for server domain (not MUC, PubSub, etc.)
        final String serverDomain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        if (!serverDomain.equals(domain)) {
            return result;
        }

        if (name == null && node == null) {
            final DataForm dataForm = new DataForm(DataForm.Type.result);

            final FormField fieldType = dataForm.addField();
            fieldType.setVariable("FORM_TYPE");
            fieldType.setType(FormField.Type.hidden);
            fieldType.addValue("http://jabber.org/network/serverinfo");

            final FormField nodeField = dataForm.addField();
            nodeField.setVariable("serverinfo-pubsub-node");
            nodeField.addValue("xmpp:" + XMPPServer.getInstance().getPubSubModule().getServiceDomain() + "?;node=" + PubSubServerInfoPlugin.NODE_ID.getValue());

            result.add(dataForm);
        }
        return result;
    }
}
