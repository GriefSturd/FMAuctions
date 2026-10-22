package ru.moscow.foxkiss.auction;

public record TransactionJournalRecord(
        String operationId,
        long lotId,
        String type,
        String buyerName,
        String sellerName,
        AuctionCurrency currency,
        int amount,
        double price,
        String status,
        long createdAt,
        long updatedAt
) {}
