package org.igniterealtime.openfire.plugin.pubsubserverinfo;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.ServerSessionEventListener;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.LocalOutgoingServerSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;

public class OptInDetector implements PacketInterceptor, ServerSessionEventListener
{
    private static final Logger Log = LoggerFactory.getLogger(OptInDetector.class);

    private final Cache<String, Boolean> optIns;

    public OptInDetector() {
        optIns = CacheFactory.createCache("pubsub-serverinfo-optin");
        optIns.setMaxLifetime(-1);
    }

    public void init() {
        // At startup, query all local outgoing sessions for their disco/info.
        SessionManager.getInstance().getOutgoingDomainPairs()
            .forEach(pair -> sessionCreated(SessionManager.getInstance().getOutgoingServerSession(pair)));
    }

    public boolean optsIn(final String domain) {
        return optIns.containsKey(domain);
    }

    @Override
    public void sessionCreated(Session session)
    {
        if (!(session instanceof LocalOutgoingServerSession) || session.getAddress().getNode() != null || session.getAddress().getResource() != null) {
            // Do not create a new outgoing server session if this was an inbound connection.
            return;
        }

        try {
            final IQ discoRequest = new IQ(IQ.Type.get);
            discoRequest.setTo(session.getAddress());
            discoRequest.setFrom(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
            discoRequest.setChildElement("query", "http://jabber.org/protocol/disco#info");
            Log.trace("Querying '{}' for disco/info: {}", session.getAddress(), discoRequest.toXML());
            XMPPServer.getInstance().getIQRouter().route(discoRequest);
        } catch (Exception e) {
            Log.warn("Exception while trying to query server '{}' for its disco/info.", session.getAddress(), e);
        }
    }

    @Override
    public void sessionDestroyed(Session session) {}

    @Override
    public void interceptPacket(Packet packet, Session session, boolean incoming, boolean processed) throws PacketRejectedException
    {
        if (!incoming || processed || packet.getFrom() == null || packet.getFrom().getNode() != null || packet.getFrom().getResource() != null || !(packet instanceof IQ)) {
            return;
        }

        final IQ iq = (IQ) packet;
        if (!iq.getType().equals(IQ.Type.result)) {
            return;
        }

        final Element el = iq.getChildElement();
        if (el == null || !"query".equals(el.getName()) || !"http://jabber.org/protocol/disco#info".equals(el.getNamespaceURI()) ) {
            return;
        }

        final boolean optIn = el.elements("feature").stream().anyMatch(feature -> {
            final String var = feature.attributeValue("var");
            return var != null && var.startsWith("urn:xmpp:serverinfo:");
        });

        Log.trace("Received disco/info from '{}' indicating {}: {}", packet.getFrom(), optIn ? "opt-in" : "no opt-in", packet.toXML());

        if (optIn) {
            optIns.put(packet.getFrom().getDomain(), true);
        } else {
            optIns.remove(packet.getFrom().getDomain());
        }
    }
}
