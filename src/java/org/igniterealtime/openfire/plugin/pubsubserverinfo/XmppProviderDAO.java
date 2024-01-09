package org.igniterealtime.openfire.plugin.pubsubserverinfo;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class XmppProviderDAO
{
    private static final Logger Log = LoggerFactory.getLogger(XmppProviderDAO.class);

    private Instant lastRefresh = Instant.EPOCH;
    private static final Duration TTL = Duration.ofHours(12);
    private Set<JID> data = new HashSet<>();

    public static Set<JID> requestServiceProviders() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://data.xmpp.net/providers/v2/providers-Ds.json"))
            .build();

        try {
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(XmppProviderDAO::parse)
                .join();
        } catch (Exception e) {
            Log.warn("Unable to obtain collection of XMPP service providers.", e);
            return Collections.emptySet();
        }
    }

    public static Set<JID> parse(final String data) {
        return new JSONArray(data).toList().stream()
            .map(Object::toString)
            .map(JID::new)
            .filter(jid -> jid.getNode() == null && jid.getResource() == null)
            .collect(Collectors.toSet());
    }
    public synchronized Set<JID> getXmppProviders() {
        if (data.isEmpty() || Duration.between(lastRefresh, Instant.now()).compareTo(TTL) > 0) {
            data = requestServiceProviders();
            lastRefresh = Instant.now();
        }
        return data;
    }

    public boolean isXmppProvider(final @Nonnull JID address) {
        return getXmppProviders().contains(address);
    }
}
