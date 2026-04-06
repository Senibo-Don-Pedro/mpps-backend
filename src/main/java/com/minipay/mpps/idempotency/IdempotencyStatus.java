package com.minipay.mpps.idempotency;

public enum IdempotencyStatus {
    KEY_NOT_FOUND,
    RESPONSE_FOUND,
    IN_FLIGHT
}
