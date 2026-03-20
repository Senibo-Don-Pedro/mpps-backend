package com.minipay.mpps.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository  extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByReference(String reference);

    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);
}
