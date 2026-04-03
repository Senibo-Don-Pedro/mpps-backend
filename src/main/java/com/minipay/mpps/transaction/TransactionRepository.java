package com.minipay.mpps.transaction;

import com.minipay.mpps.wallet.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository  extends JpaRepository<Transaction, UUID> {
    Optional<Transaction> findByReference(String reference);

    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);

    List<Transaction> findAllByWallet(Wallet wallet);
}
