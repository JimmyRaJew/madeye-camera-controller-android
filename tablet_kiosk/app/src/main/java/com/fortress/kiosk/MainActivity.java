package com.fortress.kiosk;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.fortress.kiosk.databinding.ActivityMainBinding;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private KioskViewModel viewModel;
    private SettingsRepository settingsRepository;
    private SimulatedEventController simulatedEventController;
    private CameraEventServer cameraEventServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        settingsRepository = new SettingsRepository(this);
        viewModel = new ViewModelProvider(this).get(KioskViewModel.class);
        simulatedEventController = new SimulatedEventController(
                (eventType, detail) -> viewModel.updateState(eventType, detail)
        );
        cameraEventServer = new CameraEventServer(
                (eventType, detail) -> runOnUiThread(() -> viewModel.updateState(eventType, detail))
        );

        bindListeners();
        bindObservers();
        renderDeviceSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderDeviceSettings();
        startCameraEventServer();
    }

    @Override
    protected void onDestroy() {
        simulatedEventController.stop();
        cameraEventServer.stop();
        super.onDestroy();
    }

    private void bindListeners() {
        binding.settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class))
        );

        binding.simulateToggleButton.setOnClickListener(v -> {
            Boolean running = viewModel.getSimulationRunning().getValue();
            if (Boolean.TRUE.equals(running)) {
                simulatedEventController.stop();
                viewModel.setSimulationRunning(false);
                startCameraEventServer();
                viewModel.updateState(KioskEventType.IDLE, "Simulation stopped");
            } else {
                cameraEventServer.stop();
                simulatedEventController.start();
                viewModel.setSimulationRunning(true);
            }
        });

        binding.idleButton.setOnClickListener(v ->
                viewModel.updateState(KioskEventType.IDLE, "Manual reset")
        );

        binding.detectButton.setOnClickListener(v ->
                viewModel.updateState(KioskEventType.FACE_DETECTED, "Manual detection trigger")
        );

        binding.grantButton.setOnClickListener(v ->
                viewModel.updateState(KioskEventType.ACCESS_GRANTED, "Manual granted trigger")
        );

        binding.denyButton.setOnClickListener(v ->
                viewModel.updateState(KioskEventType.ACCESS_DENIED, "Manual denied trigger")
        );
    }

    private void bindObservers() {
        viewModel.getUiState().observe(this, state -> {
            binding.statusHeadline.setText(state.getHeadline());
            binding.statusDetail.setText(state.getDetail());
            binding.lastUpdatedText.setText(buildUpdatedLabel(state));
            binding.statusPanel.setBackgroundColor(resolveStatusColor(state.getEventType()));
        });

        viewModel.getSimulationRunning().observe(this, running -> {
            String label = Boolean.TRUE.equals(running) ? "Stop Simulation" : "Start Simulation";
            binding.simulateToggleButton.setText(label);
        });
    }

    private void renderDeviceSettings() {
        DeviceSettings settings = settingsRepository.load();
        String summary = String.format(
                Locale.getDefault(),
                "Camera %s  •  Command %d  •  Listening on 0.0.0.0:%d",
                settings.getHost(),
                settings.getCommandPort(),
                settings.getEventPort()
        );
        binding.connectionSummary.setText(summary);
    }

    private String buildUpdatedLabel(KioskUiState state) {
        String time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date(state.getUpdatedAtMillis()));
        return "Last updated " + time;
    }

    @ColorInt
    private int resolveStatusColor(KioskEventType eventType) {
        switch (eventType) {
            case FACE_TOO_SMALL:
                return Color.parseColor("#D98C2B");
            case HEAD_POSE_WRONG:
                return Color.parseColor("#CC6B2C");
            case FACE_DETECTED:
                return Color.parseColor("#F4C542");
            case ACCESS_GRANTED:
                return Color.parseColor("#2EAD62");
            case ACCESS_DENIED:
                return Color.parseColor("#C64545");
            case CONNECTION_ERROR:
                return Color.parseColor("#7B1E1E");
            case IDLE:
            default:
                return Color.parseColor("#263238");
        }
    }

    private void startCameraEventServer() {
        if (Boolean.TRUE.equals(viewModel.getSimulationRunning().getValue())) {
            return;
        }
        DeviceSettings settings = settingsRepository.load();
        cameraEventServer.start(settings.getEventPort());
    }
}
