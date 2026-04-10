package com.fortress.kiosk;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.fortress.kiosk.databinding.ActivitySettingsBinding;

public class SettingsActivity extends AppCompatActivity {
    private ActivitySettingsBinding binding;
    private SettingsRepository settingsRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        settingsRepository = new SettingsRepository(this);
        populate();
        bindListeners();
    }

    private void populate() {
        DeviceSettings settings = settingsRepository.load();
        binding.hostInput.setText(settings.getHost());
        binding.commandPortInput.setText(String.valueOf(settings.getCommandPort()));
        binding.eventPortInput.setText(String.valueOf(settings.getEventPort()));
    }

    private void bindListeners() {
        binding.cancelButton.setOnClickListener(v -> finish());
        binding.saveButton.setOnClickListener(v -> save());
    }

    private void save() {
        String host = binding.hostInput.getText() == null ? "" : binding.hostInput.getText().toString().trim();
        String commandPortText = binding.commandPortInput.getText() == null ? "" : binding.commandPortInput.getText().toString().trim();
        String eventPortText = binding.eventPortInput.getText() == null ? "" : binding.eventPortInput.getText().toString().trim();

        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(commandPortText) || TextUtils.isEmpty(eventPortText)) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        int commandPort;
        int eventPort;
        try {
            commandPort = Integer.parseInt(commandPortText);
            eventPort = Integer.parseInt(eventPortText);
        } catch (NumberFormatException exception) {
            Toast.makeText(this, "Ports must be numeric", Toast.LENGTH_SHORT).show();
            return;
        }

        settingsRepository.save(new DeviceSettings(host, commandPort, eventPort));
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
