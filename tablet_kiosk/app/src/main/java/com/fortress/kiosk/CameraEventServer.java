package com.fortress.kiosk;

import androidx.annotation.NonNull;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraEventServer {
    public interface Listener {
        void onEvent(@NonNull KioskEventType eventType, @NonNull String detail);
    }

    private static final String EVENT_HEADER = "MADEYE_EVENT";
    private static final int EVENT_HEADER_SIZE = 32;
    private static final int ACCEPT_TIMEOUT_MILLIS = 1000;
    private static final int IDLE_TIMEOUT_MILLIS = 2000;

    private final Listener listener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private volatile long lastEventAtMillis;
    private volatile boolean idleEmitted;

    public CameraEventServer(Listener listener) {
        this.listener = listener;
        this.lastEventAtMillis = 0L;
    }

    public void start(int port) {
        stop();
        running = true;
        executor.execute(() -> runServer(port));
    }

    public void stop() {
        running = false;
        closeServerSocket();
    }

    private void runServer(int port) {
        lastEventAtMillis = 0L;
        idleEmitted = false;

        try (ServerSocket socket = new ServerSocket()) {
            serverSocket = socket;
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("0.0.0.0", port));
            socket.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);

            while (running) {
                try {
                    Socket client = socket.accept();
                    handleClient(client);
                } catch (SocketTimeoutException ignored) {
                    emitIdleIfNeeded();
                }
            }
        } catch (IOException e) {
            if (running) {
                listener.onEvent(
                        KioskEventType.CONNECTION_ERROR,
                        String.format(Locale.US, "Unable to listen on event port %d: %s", port, e.getMessage())
                );
            }
        } catch (RuntimeException e) {
            if (running) {
                listener.onEvent(
                        KioskEventType.CONNECTION_ERROR,
                        "Invalid event payload: " + e.getMessage()
                );
            }
        } finally {
            serverSocket = null;
        }
    }

    private void handleClient(Socket client) {
        try (Socket socket = client;
             DataInputStream input = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout(ACCEPT_TIMEOUT_MILLIS);
            processFrame(input);
        } catch (EOFException ignored) {
            emitIdleIfNeeded();
        } catch (IOException e) {
            if (running) {
                listener.onEvent(
                        KioskEventType.CONNECTION_ERROR,
                        "Event stream error: " + e.getMessage()
                );
            }
        }
    }

    private void processFrame(DataInputStream input) throws IOException {
        byte[] header = new byte[EVENT_HEADER_SIZE];
        input.readFully(header);
        String headerText = new String(header, 0, EVENT_HEADER.length(), StandardCharsets.US_ASCII);
        if (!EVENT_HEADER.equals(headerText)) {
            throw new IOException("Unexpected event header");
        }

        int payloadSize = ((header[16] & 0xFF) << 24)
                | ((header[17] & 0xFF) << 16)
                | ((header[18] & 0xFF) << 8)
                | (header[19] & 0xFF);
        if (payloadSize < 5) {
            throw new IOException("Payload too small");
        }

        byte[] payload = new byte[payloadSize];
        input.readFully(payload);
        CameraEvent event = CameraEvent.fromPayload(payload);
        lastEventAtMillis = System.currentTimeMillis();
        idleEmitted = false;
        listener.onEvent(event.eventType, event.detail);
    }

    private void emitIdleIfNeeded() {
        long now = System.currentTimeMillis();
        if (!idleEmitted && (lastEventAtMillis == 0L || now - lastEventAtMillis >= IDLE_TIMEOUT_MILLIS)) {
            idleEmitted = true;
            listener.onEvent(KioskEventType.IDLE, "Waiting for camera device events");
        }
    }

    private void closeServerSocket() {
        ServerSocket socket = serverSocket;
        if (socket == null) {
            return;
        }

        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static final class CameraEvent {
        private final KioskEventType eventType;
        private final String detail;

        private CameraEvent(KioskEventType eventType, String detail) {
            this.eventType = eventType;
            this.detail = detail;
        }

        private static CameraEvent fromPayload(byte[] payload) {
            int offset = 0;
            int jpgSize = readInt(payload, offset);
            offset += 4;

            if (jpgSize < 0 || offset + jpgSize > payload.length) {
                throw new IllegalArgumentException("Invalid JPG payload size");
            }
            offset += jpgSize;

            int detectStatus = offset < payload.length ? payload[offset++] & 0xFF : 0;
            if (detectStatus > 0 && offset + 8 <= payload.length) {
                offset += 8;
            }

            int identifyStatus = offset < payload.length ? payload[offset++] & 0xFF : 0;
            String identifyId = "";
            if (identifyStatus != 0 && offset < payload.length) {
                int idLength = payload[offset++] & 0xFF;
                if (offset + idLength <= payload.length) {
                    identifyId = new String(payload, offset, idLength, StandardCharsets.UTF_8).trim();
                    offset += idLength;
                } else {
                    offset = payload.length;
                }
            }

            if (identifyStatus == 2 || isNotRecognised(identifyId)) {
                return new CameraEvent(KioskEventType.ACCESS_DENIED, "User not recognised");
            }
            if (identifyStatus == 1) {
                String detail = identifyId.isEmpty()
                        ? "Recognized user"
                        : "Recognized user " + identifyId;
                return new CameraEvent(KioskEventType.ACCESS_GRANTED, detail);
            }
            if (!identifyId.isEmpty()) {
                return detectionEvent(detectStatus);
            }

            return detectionEvent(detectStatus);
        }

        private static CameraEvent detectionEvent(int detectStatus) {
            switch (detectStatus) {
                case 1:
                    return new CameraEvent(KioskEventType.FACE_TOO_SMALL, "Face too small");
                case 2:
                    return new CameraEvent(KioskEventType.HEAD_POSE_WRONG, "Head pose wrong");
                case 3:
                    return new CameraEvent(KioskEventType.FACE_DETECTED, "Face detected by device");
                case 0:
                default:
                    return new CameraEvent(KioskEventType.IDLE, "Waiting for camera device events");
            }
        }

        private static boolean isNotRecognised(String identifyId) {
            String normalized = identifyId.trim().toLowerCase(Locale.US);
            return "not recognised".equals(normalized) || "not recognized".equals(normalized);
        }

        private static int readInt(byte[] data, int offset) {
            return ((data[offset] & 0xFF) << 24)
                    | ((data[offset + 1] & 0xFF) << 16)
                    | ((data[offset + 2] & 0xFF) << 8)
                    | (data[offset + 3] & 0xFF);
        }
    }
}
