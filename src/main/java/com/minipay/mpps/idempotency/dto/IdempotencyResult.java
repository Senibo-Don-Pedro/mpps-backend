package com.minipay.mpps.idempotency.dto;

import com.minipay.mpps.common.enums.IdempotencyStatus;

public record IdempotencyResult(
        IdempotencyStatus status,
        String cachedResponse // only populated when RESPONSE_FOUND
) {
}
