package com.minipay.mpps.transaction.dto;

import com.minipay.mpps.common.enums.TransactionStatus;
import com.minipay.mpps.common.enums.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        UUID walletId,
        TransactionType type,
        TransactionStatus status,
        BigDecimal amount,
        String reference,
        UUID idempotencyKey,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
        ) {
}
