package com.example.printagent.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgentStompClient implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentStompClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private final ObjectMapper objectMapper;
    private final Consumer<AgentApiClient.JobAvailableNotification> notificationConsumer;
    private final CompletableFuture<Void> connected = new CompletableFuture<>();
    private final StringBuilder frameBuffer = new StringBuilder();
    private volatile WebSocket webSocket;
    private volatile boolean closed;

    public AgentStompClient(
            ObjectMapper objectMapper,
            Consumer<AgentApiClient.JobAvailableNotification> notificationConsumer) {
        this.objectMapper = objectMapper;
        this.notificationConsumer = notificationConsumer;
    }

    public static URI websocketUri(URI backendBaseUrl, String endpointPath) {
        String scheme = switch (backendBaseUrl.getScheme().toLowerCase()) {
            case "https" -> "wss";
            case "http" -> "ws";
            default -> throw new IllegalArgumentException("The backend URL scheme cannot be used for WebSocket.");
        };
        try {
            return new URI(
                    scheme,
                    null,
                    backendBaseUrl.getHost(),
                    backendBaseUrl.getPort(),
                    endpointPath,
                    null,
                    null);
        } catch (java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("The configured WebSocket endpoint is invalid.", exception);
        }
    }

    public void connect(URI uri, String accessToken, String jobDestination) {
        WebSocket socket;
        try {
            socket = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build()
                    .newWebSocketBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .buildAsync(uri, new Listener(accessToken, jobDestination))
                    .get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            webSocket = socket;
            connected.get(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            close();
            throw new IllegalStateException("The authenticated agent WebSocket connection was interrupted.", exception);
        } catch (ExecutionException | TimeoutException exception) {
            close();
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new IllegalStateException(
                    "The authenticated agent WebSocket connection could not be established.", cause);
        }
    }

    @Override
    public void close() {
        WebSocket current = webSocket;
        if (current != null && !closed && !current.isOutputClosed() && !current.isInputClosed()) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "Agent reconnect").join();
            } catch (CompletionException exception) {
                LOGGER.debug("WebSocket close completed with an error.", exception);
            }
        }
    }

    public boolean isConnected() {
        WebSocket current = webSocket;
        return connected.isDone()
                && !connected.isCompletedExceptionally()
                && !closed
                && current != null
                && !current.isInputClosed()
                && !current.isOutputClosed();
    }

    private final class Listener implements WebSocket.Listener {

        private final String accessToken;
        private final String jobDestination;

        private Listener(String accessToken, String jobDestination) {
            this.accessToken = accessToken;
            this.jobDestination = jobDestination;
        }

        @Override
        public void onOpen(WebSocket socket) {
            socket.request(1);
            String connectFrame = "CONNECT\naccept-version:1.2\nhost:printdesk\nheart-beat:0,0\n"
                    + "Authorization:Bearer " + accessToken + "\n\n\u0000";
            socket.sendText(connectFrame, true);
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            try {
                synchronized (frameBuffer) {
                    frameBuffer.append(data);
                    int frameEnd;
                    while ((frameEnd = frameBuffer.indexOf("\u0000")) >= 0) {
                        String frame = frameBuffer.substring(0, frameEnd);
                        frameBuffer.delete(0, frameEnd + 1);
                        processFrame(socket, frame);
                    }
                }
            } catch (RuntimeException exception) {
                connected.completeExceptionally(exception);
                LOGGER.warn("Could not process a backend STOMP frame.", exception);
                socket.abort();
            }
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            LOGGER.warn("Ignoring an unexpected binary WebSocket frame from the backend.");
            socket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
            closed = true;
            connected.completeExceptionally(new IllegalStateException(
                    "The backend closed the agent WebSocket connection (code " + statusCode + ")."));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket socket, Throwable error) {
            closed = true;
            connected.completeExceptionally(error);
            LOGGER.warn("The agent WebSocket connection failed.", error);
        }

        private void processFrame(WebSocket socket, String frame) {
            if (frame.isBlank()) {
                return;
            }
            int bodyStart = frame.indexOf("\n\n");
            if (bodyStart < 0) {
                throw new IllegalStateException("The backend sent an invalid STOMP frame.");
            }
            String headerBlock = frame.substring(0, bodyStart);
            String body = frame.substring(bodyStart + 2);
            String[] headerLines = headerBlock.split("\n");
            String command = headerLines[0];
            switch (command) {
                case "CONNECTED" -> {
                    connected.complete(null);
                    String subscribeFrame = "SUBSCRIBE\nid:printdesk-agent-jobs\ndestination:"
                            + jobDestination + "\nack:auto\n\n\u0000";
                    socket.sendText(subscribeFrame, true);
                }
                case "MESSAGE" -> notifyJob(body);
                case "ERROR" -> {
                    connected.completeExceptionally(
                            new IllegalStateException("The backend rejected the STOMP connection."));
                    socket.abort();
                }
                default -> LOGGER.debug("Ignoring STOMP frame command {}.", command);
            }
        }

        private void notifyJob(String body) {
            try {
                AgentApiClient.JobAvailableNotification notification =
                        objectMapper.readValue(body, AgentApiClient.JobAvailableNotification.class);
                if (!"JOB_AVAILABLE".equals(notification.eventType()) || notification.printerId() == null) {
                    throw new IllegalArgumentException("The job notification is missing required routing data.");
                }
                notificationConsumer.accept(notification);
            } catch (java.io.IOException | RuntimeException exception) {
                LOGGER.warn("Ignoring an invalid job-available notification.", exception);
            }
        }

    }
}
