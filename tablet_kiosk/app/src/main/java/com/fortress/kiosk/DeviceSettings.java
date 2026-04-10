package com.fortress.kiosk;

public class DeviceSettings {
    public static final String DEFAULT_HOST = "192.168.1.111";
    public static final int DEFAULT_COMMAND_PORT = 7778;
    public static final int DEFAULT_EVENT_PORT = 7777;

    private final String host;
    private final int commandPort;
    private final int eventPort;

    public DeviceSettings(String host, int commandPort, int eventPort) {
        this.host = host;
        this.commandPort = commandPort;
        this.eventPort = eventPort;
    }

    public String getHost() {
        return host;
    }

    public int getCommandPort() {
        return commandPort;
    }

    public int getEventPort() {
        return eventPort;
    }
}
