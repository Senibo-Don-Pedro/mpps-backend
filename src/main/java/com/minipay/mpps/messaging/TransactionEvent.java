package com.minipay.mpps.messaging;

import com.minipay.mpps.common.enums.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionEvent(
        UUID transactionId,
        UUID fromWalletId,
        UUID toWalletId,
        TransactionType transactionType,
        BigDecimal amount,
        UUID idempotencyKey
) {}
