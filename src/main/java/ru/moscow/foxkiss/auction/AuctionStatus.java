package ru.moscow.foxkiss.auction;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuctionStatus {
    SELLING(0),
    PROCESSING(1);

    private final int code;

    public static AuctionStatus fromInt(int code) {
        for (AuctionStatus status : values()) {
            if (status.code == code) return status;
        }
        return SELLING;
    }
}