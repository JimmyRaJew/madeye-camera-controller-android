package com.fortress.kiosk;

public class KioskUiState {
    private final KioskEventType eventType;
    private final String headline;
    private final String detail;
    private final long updatedAtMillis;

    public KioskUiState(KioskEventType eventType, String headline, String detail, long updatedAtMillis) {
        this.eventType = eventType;
        this.headline = headline;
        this.detail = detail;
        this.updatedAtMillis = updatedAtMillis;
    }

    public static KioskUiState idle() {
        return new KioskUiState(
                KioskEventType.IDLE,
                "Ready",
                "Waiting for camera device events",
                System.currentTimeMillis()
        );
    }

    public KioskEventType getEventType() {
        return eventType;
    }

    public String getHeadline() {
        return headline;
    }

    public String getDetail() {
        return detail;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }
}
