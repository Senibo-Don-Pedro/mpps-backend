package com.minipay.mpps.idempotency;

import com.minipay.mpps.idempotency.dto.IdempotencyResult;

import java.util.UUID;

public interface IdempotencyService {

    IdempotencyResult check(UUID idempotencyKey);

    void store(UUID idempotencyKey);

    void update(UUID idempotencyKey, String response);
}
