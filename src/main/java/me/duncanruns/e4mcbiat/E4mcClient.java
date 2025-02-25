package me.duncanruns.e4mcbiat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import me.duncanruns.e4mcbiat.util.GrabUtil;
import me.duncanruns.e4mcbiat.util.SocketUtil;
import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


// My notes on the quiclime/e4mc protocol: https://gist.github.com/DuncanRuns/09a7193a16f28c1a1587bc08303494ff
public class E4mcClient {
    private static final Gson GSON = new Gson();
    private final Set<MCRelay> relays = ConcurrentHashMap.newKeySet();
    private final Consumer<String> onDomainAssigned;
    private final Consumer<String> onBroadcast;
    private QuicClientConnection connection;
    private boolean closed = false;

    public E4mcClient(Consumer<String> onDomainAssigned, Consumer<String> onBroadcast) {
        this.onDomainAssigned = onDomainAssigned;
        this.onBroadcast = onBroadcast;
    }

    private static void requestDomain(QuicStream stream) throws IOException {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("kind", "request_domain_assignment");
        sendQuiclimeControlString(stream.getOutputStream(), GSON.toJson(jsonObject));
    }

    private static Optional<RelayInfo> getBestRelay() {
        try {
            return Optional.ofNullable(GrabUtil.grabJson("https://broker.e4mc.link/getBestRelay", RelayInfo.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static void sendQuiclimeControlString(OutputStream outputStream, String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > Byte.MAX_VALUE) throw new IOException("String is too large!");
        // Send 4 bytes containing length
        outputStream.write((byte) bytes.length);
        if (bytes.length == 0) return;
        // Send bytes of that length
        outputStream.write(bytes);
        outputStream.flush();
    }

    public static String receiveQuiclimeControlString(InputStream inputStream) throws IOException {
        byte[] bytes = SocketUtil.readSpecific(inputStream, 1);
        if (bytes == null) return null;
        int length = bytes[0];
        if (length == 0) return "";
        bytes = SocketUtil.readSpecific(inputStream, length);
        if (bytes == null) return null;
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void run() throws IOException {
        try {
            Optional<RelayInfo> bestRelay = getBestRelay();
            if (bestRelay.isEmpty()) {
                throw new IOException("Failed to get best relay!");
            }
            RelayInfo relayInfo = bestRelay.get();

            connection = QuicClientConnection.newBuilder()
                    .host(relayInfo.host)
                    .port(relayInfo.port)
                    .applicationProtocol("quiclime")
                    .maxOpenPeerInitiatedBidirectionalStreams(512)
                    .maxIdleTimeout(Duration.ofSeconds(10))
                    .build();
            connection.connect();
            QuicStream stream = connection.createStream(true);
            connection.setPeerInitiatedStreamCallback(s -> {
                MCRelay relay = new MCRelay(s, relays::remove);
                relays.add(relay);
                relay.start();
            });

            requestDomain(stream);

            receiveLoop(stream);
        } catch (Exception e) {
            if (closed) return;
            close();
            throw e;
        }
    }

    private void receiveLoop(QuicStream stream) throws IOException {
        while (connection.isConnected() && !closed) {
            String s = receiveQuiclimeControlString(stream.getInputStream());
            if (s == null) connection.close();
            JsonObject jsonObject = GSON.fromJson(s, JsonObject.class);
            if (jsonObject == null) throw new IOException("Empty json object returned!");
            if (!jsonObject.has("kind")) throw new IOException("Invalid control message!");
            String kind = jsonObject.get("kind").getAsString();
            switch (kind) {
                case "domain_assignment_complete":
                    onDomainAssigned.accept(jsonObject.get("domain").getAsString());
                    break;
                case "request_message_broadcast":
                    onBroadcast.accept(jsonObject.get("message").getAsString());
                    break;
            }
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        relays.forEach(MCRelay::close);
        connection.close();
    }

    private static class RelayInfo {
        String id;
        String host;
        int port;

        @Override
        public String toString() {
            return "RelayInfo{" +
                    "id='" + id + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    '}';
        }
    }
}
