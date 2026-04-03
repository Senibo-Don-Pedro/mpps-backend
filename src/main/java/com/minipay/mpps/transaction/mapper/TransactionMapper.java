package com.minipay.mpps.transaction.mapper;

import com.minipay.mpps.transaction.Transaction;
import com.minipay.mpps.transaction.dto.TransactionResponse;

public class TransactionMapper {

    public static TransactionResponse toResponse(Transaction transaction) {

        return new TransactionResponse(
                transaction.getId(),
                transaction.getWallet().getId(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getReference(),
                transaction.getIdempotencyKey(),
                transaction.getMetadata(),
                transaction.getCreatedAt()
        );
    }
}
