package ru.moscow.foxkiss.auction;

public enum AuctionStatus {
    SELLING(0),
    PROCESSING(1);

    private final int code;

    AuctionStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static AuctionStatus fromInt(int code) {
        for (AuctionStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return SELLING;
    }
}