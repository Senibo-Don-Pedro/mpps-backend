package com.minipay.mpps.wallet.dto;

import com.minipay.mpps.common.dto.CurrencyInfo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        BigDecimal balance,
        CurrencyInfo currency,
        UUID userId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
