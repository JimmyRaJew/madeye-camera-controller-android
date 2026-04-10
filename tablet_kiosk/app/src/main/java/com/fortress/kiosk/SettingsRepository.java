package com.fortress.kiosk;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsRepository {
    private static final String PREFS_NAME = "fortress_kiosk_prefs";
    private static final String KEY_HOST = "host";
    private static final String KEY_COMMAND_PORT = "command_port";
    private static final String KEY_EVENT_PORT = "event_port";

    private final SharedPreferences preferences;

    public SettingsRepository(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public DeviceSettings load() {
        String host = preferences.getString(KEY_HOST, DeviceSettings.DEFAULT_HOST);
        int commandPort = preferences.getInt(KEY_COMMAND_PORT, DeviceSettings.DEFAULT_COMMAND_PORT);
        int eventPort = preferences.getInt(KEY_EVENT_PORT, DeviceSettings.DEFAULT_EVENT_PORT);
        return new DeviceSettings(host, commandPort, eventPort);
    }

    public void save(DeviceSettings settings) {
        preferences.edit()
                .putString(KEY_HOST, settings.getHost())
                .putInt(KEY_COMMAND_PORT, settings.getCommandPort())
                .putInt(KEY_EVENT_PORT, settings.getEventPort())
                .apply();
    }
}
