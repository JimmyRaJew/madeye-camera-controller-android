package com.fortress.poc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicInteger;

public final class CommandClient {
    private static final String TX_HEADER = "MADEYE_CMD_TX";
    private static final String RX_HEADER = "MADEYE_CMD_RX";
    private static final int HEADER_SIZE = 32;
    private static final int CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int SOCKET_TIMEOUT_MILLIS = 10000;
    private static final int MAX_ATTEMPTS = 3;

    public static final byte COMMAND_VERSION_GET = (byte) 0xA0;
    public static final byte COMMAND_VIDEO_GET = (byte) 0xA1;
    public static final byte COMMAND_VIDEO_SET = (byte) 0xA2;
    public static final byte COMMAND_FACE_GET = (byte) 0xA3;
    public static final byte COMMAND_FACE_SET = (byte) 0xA4;
    public static final byte COMMAND_NETWORK_GET = (byte) 0xA5;
    public static final byte COMMAND_NETWORK_SET = (byte) 0xA6;
    public static final byte COMMAND_COMM_GET = (byte) 0xA7;
    public static final byte COMMAND_COMM_SET = (byte) 0xA8;
    public static final byte COMMAND_FIRMWARE_UPDATE = (byte) 0xB0;
    public static final byte COMMAND_USER_ADD = (byte) 0xC0;
    public static final byte COMMAND_USER_DELETE = (byte) 0xC1;
    public static final byte COMMAND_USER_DELETE_ALL = (byte) 0xC2;
    public static final byte COMMAND_USER_LIST = (byte) 0xC3;
    public static final byte COMMAND_DATABASE_GET = (byte) 0xD0;
    public static final byte COMMAND_DATABASE_SET = (byte) 0xD1;
    public static final byte COMMAND_CAMERA_ON = (byte) 0xE0;
    public static final byte COMMAND_CAMERA_OFF = (byte) 0xE1;

    private final AtomicInteger sequence = new AtomicInteger(1);

    public VersionInfo versionGet(String host, int port) throws IOException {
        byte[] payload = exchange(host, port, COMMAND_VERSION_GET, new byte[0], COMMAND_VERSION_GET);
        Parser parser = new Parser(payload);
        parser.expectSuccess();
        return new VersionInfo(
                parser.readLengthPrefixedString(),
                parser.readLengthPrefixedString(),
                parser.readLengthPrefixedString()
        );
    }

    public VideoSettings videoGet(String host, int port) throws IOException {
        byte[] payload = exchange(host, port, COMMAND_VIDEO_GET, new byte[0], COMMAND_VIDEO_GET);
        Parser parser = new Parser(payload);
        parser.expectSuccess();
        return new VideoSettings(
                parser.readUInt16(),
                parser.readUInt16(),
                parser.readUInt8(),
                parser.readUInt8(),
                parser.readUInt8()
        );
    }

    public void videoSet(String host, int port, VideoSettings settings) throws IOException {
        byte[] body = new byte[7];
        writeUInt16(body, 0, settings.width());
        writeUInt16(body, 2, settings.height());
        body[4] = (byte) settings.rotation();
        body[5] = (byte) settings.camera();
        body[6] = (byte) settings.balance();
        expectAck(exchange(host, port, COMMAND_VIDEO_SET, body, COMMAND_VIDEO_SET));
    }

    public FaceSettings faceGet(String host, int port) throws IOException {
        byte[] payload = exchange(host, port, COMMAND_FACE_GET, new byte[0], COMMAND_FACE_GET);
        Parser parser = new Parser(payload);
        parser.expectSuccess();
        return new FaceSettings(
                parser.readScaledFloat(),
                parser.readUInt8(),
                parser.readUInt8(),
                parser.readScaledFloat(),
                parser.readUInt8(),
                parser.readUInt16()
        );
    }

    public void faceSet(String host, int port, FaceSettings settings) throws IOException {
        byte[] body = new byte[13];
        writeScaledFloat(body, 0, settings.threshold());
        body[4] = (byte) settings.attempts();
        body[5] = (byte) settings.liveness();
        writeScaledFloat(body, 6, settings.livenessThreshold());
        body[10] = (byte) settings.faceMinimum();
        writeUInt16(body, 11, settings.faceSize());
        expectAck(exchange(host, port, COMMAND_FACE_SET, body, COMMAND_FACE_SET));
    }

    public NetworkSettings networkGet(String host, int port) throws IOException {
        byte[] payload = exchange(host, port, COMMAND_NETWORK_GET, new byte[0], COMMAND_NETWORK_GET);
        Parser parser = new Parser(payload);
        parser.expectSuccess();
        return new NetworkSettings(
                parser.readLengthPrefixedString(),
                parser.readLengthPrefixedString(),
                parser.readLengthPrefixedString()
        );
    }

    public void networkSet(String host, int port, NetworkSettings settings) throws IOException {
        byte[] body = concat(
                lengthPrefixed(settings.address()),
                lengthPrefixed(settings.gateway()),
                lengthPrefixed(settings.mask())
        );
        expectAck(exchange(host, port, COMMAND_NETWORK_SET, body, COMMAND_NETWORK_SET));
    }

    public CommSettings commGet(String host, int port) throws IOException {
        byte[] payload = exchange(host, port, COMMAND_COMM_GET, new byte[0], COMMAND_COMM_GET);
        Parser parser = new Parser(payload);
        parser.expectSuccess();
        return new CommSettings(
                parser.readLengthPrefixedString(),
                parser.readUInt16(),
                parser.readUInt16()
        );
    }

    public void commSet(String host, int port, CommSettings settings) throws IOException {
        byte[] body = concat(
                lengthPrefixed(settings.host()),
                uint16(settings.eventPort()),
                uint16(settings.commandPort())
        );
        expectAck(exchange(host, port, COMMAND_COMM_SET, body, COMMAND_COMM_SET));
    }

    public void firmwareUpdate(String host, int port, byte[] firmware, String md5) throws IOException {
        byte[] body = concat(
                uint32(firmware.length),
                firmware,
                lengthPrefixed(md5)
        );
        expectAck(exchange(host, port, COMMAND_FIRMWARE_UPDATE, body, COMMAND_FIRMWARE_UPDATE));
    }

    public void userAdd(String host, int port, String id, byte[] face) throws IOException {
        byte[] body = concat(
                lengthPrefixed(id),
                uint16(face.length),
                face
        );
        expectAck(exchange(host, port, COMMAND_USER_ADD, body, COMMAND_USER_ADD));
    }

    public void userDelete(String host, int port, String id) throws IOException {
        expectAck(exchange(host, port, COMMAND_USER_DELETE, lengthPrefixed(id), COMMAND_USER_DELETE));
    }

    public void userDeleteAll(String host, int port) throws IOException {
        expectAck(exchange(host, port, COMMAND_USER_DELETE_ALL, new byte[0], COMMAND_USER_DELETE_ALL));
    }

    public UserListResult userList(String host, int port) throws IOException {
        byte[] payload = exchange(host, port, COMMAND_USER_LIST, new byte[0], COMMAND_USER_LIST);
        Parser parser = new Parser(payload);
        parser.expectSuccess();
        int count = parser.readInt32();
        int length = parser.readInt32();
        byte[] listBytes = parser.readBytes(length);
        return new UserListResult(count, new String(listBytes, StandardCharsets.UTF_8));
    }

    public DatabaseDownload databaseGet(String host, int port) throws IOException {
        byte[] payload = exchange(host, port, COMMAND_DATABASE_GET, new byte[0], COMMAND_DATABASE_GET);
        Parser parser = new Parser(payload);
        parser.expectSuccess();
        int size = parser.readInt32();
        byte[] database = parser.readBytes(size);
        String md5 = parser.readLengthPrefixedString();
        return new DatabaseDownload(database, md5);
    }

    public void databaseSet(String host, int port, byte[] database, String md5) throws IOException {
        byte[] body = concat(
                uint32(database.length),
                database,
                lengthPrefixed(md5)
        );
        expectAck(exchange(host, port, COMMAND_DATABASE_SET, body, COMMAND_DATABASE_SET));
    }

    public void cameraOn(String host, int port) throws IOException {
        expectAck(exchange(host, port, COMMAND_CAMERA_ON, new byte[0], COMMAND_CAMERA_ON));
    }

    public void cameraOff(String host, int port) throws IOException {
        expectAck(exchange(host, port, COMMAND_CAMERA_OFF, new byte[0], COMMAND_CAMERA_OFF));
    }

    public CommandDiagnosticResult diagnosticVersionGet(String host, int port) {
        int attemptCount = 0;
        int sequenceNumber = sequence.incrementAndGet();
        byte[] request = buildPacket(TX_HEADER, new byte[0], sequenceNumber, COMMAND_VERSION_GET);
        String localAddressText = "unbound";
        AppLog.log("Diagnostic VERSION_GET start host=" + host + " port=" + port + " seq=" + sequenceNumber
                + " request=" + toHex(request));

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            attemptCount = attempt;
            Socket socket = new Socket();
            try (socket) {
                InetAddress localAddress = findMatchingLocalAddress(host);
                if (localAddress != null) {
                    socket.bind(new InetSocketAddress(localAddress, 0));
                    localAddressText = localAddress.getHostAddress();
                    AppLog.log("Diagnostic VERSION_GET bound local=" + localAddressText + " attempt=" + attempt);
                }
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
                socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                AppLog.log("Diagnostic VERSION_GET connected remote=" + socket.getRemoteSocketAddress()
                        + " local=" + socket.getLocalSocketAddress() + " attempt=" + attempt);

                BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
                output.write(request);
                output.flush();
                AppLog.log("Diagnostic VERSION_GET sent bytes=" + request.length + " attempt=" + attempt);

                BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                byte[] header = readFully(input, HEADER_SIZE);
                validateHeader(header, COMMAND_VERSION_GET);
                int payloadSize = readInt32(header, 16);
                byte[] payload = readFully(input, payloadSize);
                AppLog.log("Diagnostic VERSION_GET received header=" + toHex(header) + " payload=" + toHex(payload));
                validateLrc(header, payload);
                Parser parser = new Parser(payload);
                parser.expectSuccess();
                String firmware = parser.readLengthPrefixedString();
                String face = parser.readLengthPrefixedString();
                String os = parser.readLengthPrefixedString();
                AppLog.log("Diagnostic VERSION_GET success firmware=" + firmware + " face=" + face + " os=" + os);
                return new CommandDiagnosticResult(
                        true,
                        "Received version response",
                        localAddressText,
                        attemptCount,
                        request,
                        concat(header, payload),
                        firmware,
                        face,
                        os
                );
            } catch (IOException e) {
                AppLog.log("Diagnostic VERSION_GET failed attempt=" + attempt + " local=" + localAddressText, e);
                if (attempt == MAX_ATTEMPTS) {
                    return new CommandDiagnosticResult(
                            false,
                            e.getClass().getSimpleName() + ": " + e.getMessage(),
                            localAddressText,
                            attemptCount,
                            request,
                            null,
                            null,
                            null,
                            null
                    );
                }
            } finally {
                closeSocketGracefully(socket, "diagnostic VERSION_GET attempt=" + attempt);
            }
        }

        return new CommandDiagnosticResult(
                false,
                "Unknown failure",
                localAddressText,
                attemptCount,
                request,
                null,
                null,
                null,
                null
        );
    }

    private byte[] exchange(String host, int port, byte command, byte[] body, byte expectedResponseCommand) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            int sequenceNumber = sequence.incrementAndGet();
            byte[] request = buildPacket(TX_HEADER, body, sequenceNumber, command);
            AppLog.log(String.format(
                    "Command 0x%02X start host=%s port=%d seq=%d attempt=%d request=%s",
                    command & 0xFF,
                    host,
                    port,
                    sequenceNumber,
                    attempt,
                    toHex(request)
            ));

            Socket socket = new Socket();
            try (socket) {
                InetAddress localAddress = findMatchingLocalAddress(host);
                if (localAddress != null) {
                    socket.bind(new InetSocketAddress(localAddress, 0));
                    AppLog.log(String.format(
                            "Command 0x%02X bound local=%s attempt=%d",
                            command & 0xFF,
                            localAddress.getHostAddress(),
                            attempt
                    ));
                }
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
                socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
                AppLog.log(String.format(
                        "Command 0x%02X connected remote=%s local=%s attempt=%d",
                        command & 0xFF,
                        socket.getRemoteSocketAddress(),
                        socket.getLocalSocketAddress(),
                        attempt
                ));

                BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
                output.write(request);
                output.flush();
                AppLog.log(String.format(
                        "Command 0x%02X sent bytes=%d attempt=%d",
                        command & 0xFF,
                        request.length,
                        attempt
                ));

                BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                byte[] header = readFully(input, HEADER_SIZE);
                validateHeader(header, expectedResponseCommand);
                int payloadSize = readInt32(header, 16);
                byte[] payload = readFully(input, payloadSize);
                AppLog.log(String.format(
                        "Command 0x%02X received header=%s payload=%s attempt=%d",
                        command & 0xFF,
                        toHex(header),
                        toHex(payload),
                        attempt
                ));
                validateLrc(header, payload);
                return payload;
            } catch (IOException e) {
                AppLog.log(String.format("Command 0x%02X failed attempt=%d", command & 0xFF, attempt), e);
                lastError = e;
            } finally {
                closeSocketGracefully(socket, String.format("command 0x%02X attempt=%d", command & 0xFF, attempt));
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Command exchange failed");
    }

    private InetAddress findMatchingLocalAddress(String remoteHost) {
        try {
            InetAddress remoteAddress = InetAddress.getByName(remoteHost);
            if (!(remoteAddress instanceof Inet4Address remoteIpv4)) {
                return null;
            }

            byte[] remoteBytes = remoteIpv4.getAddress();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address ipv4) {
                        byte[] localBytes = ipv4.getAddress();
                        if (localBytes[0] == remoteBytes[0] && localBytes[1] == remoteBytes[1] && localBytes[2] == remoteBytes[2]) {
                            return ipv4;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            return null;
        }
        return null;
    }

    private void validateHeader(byte[] header, byte expectedCommand) throws IOException {
        String headerText = new String(header, 0, RX_HEADER.length(), StandardCharsets.US_ASCII);
        if (!RX_HEADER.equals(headerText)) {
            throw new IOException("Unexpected response header");
        }
        int payloadSize = readInt32(header, 16);
        if (payloadSize <= 0) {
            throw new IOException("Invalid response payload size");
        }
        int responseSequence = readInt32(header, 20);
        if (responseSequence <= 0) {
            throw new IOException("Invalid response sequence");
        }
        byte responseCommand = header[24];
        if (!isAcceptedResponseCommand(expectedCommand, responseCommand)) {
            throw new IOException(String.format(
                    "Unexpected response command 0x%02X for request 0x%02X",
                    responseCommand & 0xFF,
                    expectedCommand & 0xFF
            ));
        }
    }

    private boolean isAcceptedResponseCommand(byte expectedCommand, byte responseCommand) {
        if (responseCommand == expectedCommand) {
            return true;
        }
        if ((expectedCommand == COMMAND_CAMERA_ON || expectedCommand == COMMAND_CAMERA_OFF)
                && responseCommand == COMMAND_DATABASE_SET) {
            AppLog.log(String.format(
                    "Accepting alternate ack command 0x%02X for request 0x%02X",
                    responseCommand & 0xFF,
                    expectedCommand & 0xFF
            ));
            return true;
        }
        return false;
    }

    private void validateLrc(byte[] header, byte[] payload) throws IOException {
        byte[] packet = concat(header, payload);
        byte expected = lrc(packet, packet.length - 1);
        byte actual = packet[packet.length - 1];
        if (expected != actual) {
            throw new IOException("Invalid response checksum");
        }
    }

    private void expectAck(byte[] payload) throws IOException {
        Parser parser = new Parser(payload);
        parser.expectSuccess();
    }

    private byte[] buildPacket(String headerText, byte[] body, int sequenceNumber, byte command) {
        int payloadSize = body.length + 1;
        byte[] packet = new byte[HEADER_SIZE + payloadSize];
        byte[] headerBytes = headerText.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(headerBytes, 0, packet, 0, headerBytes.length);
        writeInt32(packet, 16, payloadSize);
        writeInt32(packet, 20, sequenceNumber);
        packet[24] = command;
        System.arraycopy(body, 0, packet, HEADER_SIZE, body.length);
        packet[packet.length - 1] = lrc(packet, packet.length - 1);
        return packet;
    }

    private static byte[] readFully(BufferedInputStream input, int size) throws IOException {
        byte[] data = new byte[size];
        int offset = 0;
        while (offset < size) {
            int read = input.read(data, offset, size - offset);
            if (read < 0) {
                throw new EOFException("Unexpected end of stream");
            }
            offset += read;
        }
        return data;
    }

    private static byte[] uint16(int value) {
        byte[] bytes = new byte[2];
        writeUInt16(bytes, 0, value);
        return bytes;
    }

    private static byte[] uint32(int value) {
        byte[] bytes = new byte[4];
        writeInt32(bytes, 0, value);
        return bytes;
    }

    private static byte[] lengthPrefixed(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[encoded.length + 1];
        bytes[0] = (byte) encoded.length;
        System.arraycopy(encoded, 0, bytes, 1, encoded.length);
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {
        int size = 0;
        for (byte[] part : parts) {
            size += part.length;
        }
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    private static void writeUInt16(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 8) & 0xFF);
        data[offset + 1] = (byte) (value & 0xFF);
    }

    private static void writeInt32(byte[] data, int offset, int value) {
        data[offset] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >> 8) & 0xFF);
        data[offset + 3] = (byte) (value & 0xFF);
    }

    private static void writeScaledFloat(byte[] data, int offset, float value) {
        writeInt32(data, offset, Math.round(value * 10000.0f));
    }

    private static int readInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }

    private static byte lrc(byte[] data, int size) {
        int checksum = 0;
        for (int i = 0; i < size; i++) {
            checksum ^= data[i];
        }
        return (byte) (checksum & 0xFF);
    }

    private static void closeSocketGracefully(Socket socket, String context) {
        if (socket == null) {
            return;
        }
        if (socket.isConnected() && !socket.isOutputShutdown()) {
            try {
                socket.shutdownOutput();
            } catch (IOException ignored) {
            }
        }
        if (socket.isConnected() && !socket.isInputShutdown()) {
            try {
                socket.shutdownInput();
            } catch (IOException ignored) {
            }
        }
        if (!socket.isClosed()) {
            try {
                socket.close();
                AppLog.log("Closed socket for " + context);
            } catch (IOException e) {
                AppLog.log("Failed closing socket for " + context, e);
            }
        } else {
            AppLog.log("Socket already closed for " + context);
        }
    }

    private static String toHex(byte[] data) {
        if (data == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte value : data) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static final class Parser {
        private final byte[] payload;
        private final byte[] content;
        private int offset;

        private Parser(byte[] payload) {
            if (payload.length < 2) {
                throw new IllegalArgumentException("Payload too small");
            }
            this.payload = payload;
            this.content = Arrays.copyOf(payload, payload.length - 1);
        }

        private void expectSuccess() throws IOException {
            int status = readUInt8();
            if (status != 0x01) {
                throw new IOException(String.format("Command failed with status 0x%02X", status));
            }
        }

        private int readUInt8() {
            return content[offset++] & 0xFF;
        }

        private int readUInt16() {
            int value = ((content[offset] & 0xFF) << 8)
                    | (content[offset + 1] & 0xFF);
            offset += 2;
            return value;
        }

        private int readInt32() {
            int value = CommandClient.readInt32(content, offset);
            offset += 4;
            return value;
        }

        private float readScaledFloat() {
            return readInt32() / 10000.0f;
        }

        private String readLengthPrefixedString() {
            int length = readUInt8();
            byte[] data = readBytes(length);
            return new String(data, StandardCharsets.UTF_8);
        }

        private byte[] readBytes(int length) {
            byte[] data = Arrays.copyOfRange(content, offset, offset + length);
            offset += length;
            return data;
        }
    }

    public record VersionInfo(String firmware, String face, String os) { }
    public record VideoSettings(int width, int height, int rotation, int camera, int balance) { }
    public record FaceSettings(float threshold, int attempts, int liveness, float livenessThreshold, int faceMinimum, int faceSize) { }
    public record NetworkSettings(String address, String gateway, String mask) { }
    public record CommSettings(String host, int eventPort, int commandPort) { }
    public record UserListResult(int count, String rawList) { }
    public record DatabaseDownload(byte[] database, String md5) { }
    public record CommandDiagnosticResult(
            boolean success,
            String summary,
            String localAddress,
            int attempts,
            byte[] requestPacket,
            byte[] responsePacket,
            String firmware,
            String face,
            String os
    ) { }
}
