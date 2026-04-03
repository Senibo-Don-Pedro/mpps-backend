package com.minipay.mpps.transaction;

import com.minipay.mpps.transaction.dto.CreateTransactionRequest;
import com.minipay.mpps.transaction.dto.TransactionResponse;

import java.util.List;
import java.util.UUID;

public interface TransactionService {

    TransactionResponse createTransaction(CreateTransactionRequest request);

    TransactionResponse getTransactionById(UUID transactionId);

    TransactionResponse getTransactionByReference(String reference);

    List<TransactionResponse> getTransactionsByWalletId(UUID walletId);


}
