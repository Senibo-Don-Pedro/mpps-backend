package com.minipay.mpps.idempotency;

import com.minipay.mpps.common.exception.NotFoundException;
import com.minipay.mpps.idempotency.dto.IdempotencyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyServiceImpl implements IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;


    /**
     * Check the status of an idempotency key. <br/>
     * - If the key exists and has a cached response, return RESPONSE_FOUND with the cached response. <br/>
     * - If the key exists but has no cached response, return IN_FLIGHT. <br/>
     * - If the key does not exist, return KEY_NOT_FOUND.
     */
    @Override
    public IdempotencyResult check(UUID idempotencyKey) {
        Optional<IdempotencyKey> key = idempotencyKeyRepository.findByKey(idempotencyKey);

        if (key.isPresent()) {
            // Treat expired keys as non-existent to trigger re-processing.
            if (key.get().getExpiresAt().isBefore(OffsetDateTime.now())) {
                return new IdempotencyResult(IdempotencyStatus.KEY_NOT_FOUND, null);
            }

            // Presence of response distinguishes completed from in-progress requests.
            if (key.get().getResponse() != null) {
                return new IdempotencyResult(IdempotencyStatus.RESPONSE_FOUND,
                                             key.get().getResponse());
            }

            return new IdempotencyResult(IdempotencyStatus.IN_FLIGHT, null);
        }

        return new IdempotencyResult(IdempotencyStatus.KEY_NOT_FOUND, null);
    }

    /**
     * Store a new idempotency key with an expiration time of 24 hours. The response is initially null, indicating that the request is in-flight.
     */
    @Override
    @Transactional
    public void store(UUID idempotencyKey) {
        idempotencyKeyRepository.save(
                IdempotencyKey.builder()
                              .key(idempotencyKey)
                              .expiresAt(OffsetDateTime.now().plusDays(1))
                              .build()
        );
    }

    /**
     * Update the response for an existing idempotency key. If the key does not exist, throw a NotFoundException.
     */
    @Override
    @Transactional
    public void update(UUID idempotencyKey, String response) {
        IdempotencyKey currentKeyEntity = idempotencyKeyRepository.findByKey(idempotencyKey)
                                                                  .orElseThrow(
                                                                          () -> new NotFoundException(
                                                                                  "Idempotency key not found: " + idempotencyKey)
                                                                  );

        currentKeyEntity.setResponse(response);

        idempotencyKeyRepository.save(currentKeyEntity);
    }

    /**
     * This method is scheduled to run every day at 2 AM to clean up expired idempotency keys from the database. It deletes all keys where the expiresAt timestamp is before the current time.
     */
    @Override
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanUpExpiredKeys() {

        log.info("Begin cleaning up expired keys");

        idempotencyKeyRepository.deleteByExpiresAtBefore(OffsetDateTime.now());

        log.info("Done cleaning up expired keys");

    }
}
