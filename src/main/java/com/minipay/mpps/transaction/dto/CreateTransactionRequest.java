package com.minipay.mpps.transaction.dto;

import com.minipay.mpps.common.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record CreateTransactionRequest(

        // Nullable: required for DEBIT/TRANSFER, optional for CREDIT. Cross-field validation handled in service layer.
        UUID fromWalletId,
        UUID toWalletId,

        @NotNull(message = "Transaction type is required")
        TransactionType transactionType,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.0001", message = "Amount must be greater than zero")
        BigDecimal amount,

        @NotNull(message = "Idempotency key is required")
        UUID idempotencyKey,


        Map<String, Object> metadata
) {
}
