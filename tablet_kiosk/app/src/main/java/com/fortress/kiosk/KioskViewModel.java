package com.fortress.kiosk;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class KioskViewModel extends ViewModel {
    private final MutableLiveData<KioskUiState> uiState = new MutableLiveData<>(KioskUiState.idle());
    private final MutableLiveData<Boolean> simulationRunning = new MutableLiveData<>(false);

    public LiveData<KioskUiState> getUiState() {
        return uiState;
    }

    public LiveData<Boolean> getSimulationRunning() {
        return simulationRunning;
    }

    public void updateState(KioskEventType eventType, String detail) {
        String headline;
        switch (eventType) {
            case FACE_TOO_SMALL:
                headline = "Move Closer";
                break;
            case HEAD_POSE_WRONG:
                headline = "Adjust Head Pose";
                break;
            case FACE_DETECTED:
                headline = "Face Detected";
                break;
            case ACCESS_GRANTED:
                headline = "Access Granted";
                break;
            case ACCESS_DENIED:
                headline = "Access Denied";
                break;
            case CONNECTION_ERROR:
                headline = "Connection Error";
                break;
            case IDLE:
            default:
                headline = "Ready";
                if (detail == null || detail.isEmpty()) {
                    detail = "Waiting for camera device events";
                }
                break;
        }

        uiState.setValue(new KioskUiState(
                eventType,
                headline,
                detail,
                System.currentTimeMillis()
        ));
    }

    public void setSimulationRunning(boolean running) {
        simulationRunning.setValue(running);
    }
}
