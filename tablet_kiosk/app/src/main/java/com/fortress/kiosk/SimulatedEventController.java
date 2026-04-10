package com.fortress.kiosk;

import android.os.Handler;
import android.os.Looper;

public class SimulatedEventController {
    public interface Listener {
        void onEvent(KioskEventType eventType, String detail);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private int cursor = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Step step = steps()[cursor % steps().length];
            listener.onEvent(step.eventType, step.detail);
            cursor++;
            handler.postDelayed(this, step.durationMillis);
        }
    };

    public SimulatedEventController(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        stop();
        cursor = 0;
        handler.post(tick);
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    private Step[] steps() {
        return new Step[]{
                new Step(KioskEventType.IDLE, "Waiting for a face", 1800),
                new Step(KioskEventType.FACE_TOO_SMALL, "Face too small", 1500),
                new Step(KioskEventType.HEAD_POSE_WRONG, "Head pose wrong", 1500),
                new Step(KioskEventType.FACE_DETECTED, "Face detected by device", 1500),
                new Step(KioskEventType.ACCESS_GRANTED, "Recognized user 1001", 2200),
                new Step(KioskEventType.IDLE, "Reset after successful event", 1400),
                new Step(KioskEventType.FACE_DETECTED, "Face detected by device", 1500),
                new Step(KioskEventType.ACCESS_DENIED, "User not recognised on device", 2200)
        };
    }

    private static class Step {
        private final KioskEventType eventType;
        private final String detail;
        private final long durationMillis;

        private Step(KioskEventType eventType, String detail, long durationMillis) {
            this.eventType = eventType;
            this.detail = detail;
            this.durationMillis = durationMillis;
        }
    }
}
