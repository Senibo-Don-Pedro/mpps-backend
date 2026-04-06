package com.minipay.mpps.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.mpps.common.TransactionReferenceGenerator;
import com.minipay.mpps.idempotency.IdempotencyStatus;
import com.minipay.mpps.common.exception.BadRequestException;
import com.minipay.mpps.common.exception.ConflictException;
import com.minipay.mpps.common.exception.NotFoundException;
import com.minipay.mpps.idempotency.IdempotencyService;
import com.minipay.mpps.idempotency.dto.IdempotencyResult;
import com.minipay.mpps.messaging.TransactionEvent;
import com.minipay.mpps.transaction.dto.CreateTransactionRequest;
import com.minipay.mpps.transaction.dto.TransactionResponse;
import com.minipay.mpps.transaction.mapper.TransactionMapper;
import com.minipay.mpps.wallet.Wallet;
import com.minipay.mpps.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final IdempotencyService idempotencyService;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final TransactionReferenceGenerator referenceGenerator;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Creates a transaction (credit, debit, or transfer) with idempotency handling.
     */
    @Override
    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        // Check Idempotency
        IdempotencyResult idempotencyResult = idempotencyService.check(request.idempotencyKey());

        if (idempotencyResult.status() == IdempotencyStatus.RESPONSE_FOUND) {
            try {
                return objectMapper.readValue(idempotencyResult.cachedResponse(), TransactionResponse.class);
            } catch (Exception e) {
                log.error("Failed to deserialize cached response");
                throw new RuntimeException("Failed to process request due to idempotency cache error");
            }
        } else if (idempotencyResult.status() == IdempotencyStatus.IN_FLIGHT) {
            throw new ConflictException("Transaction is already being processed");
        }

        // Mark as in-flight
        idempotencyService.store(request.idempotencyKey());

        // 3. Basic Validation (NEW - Consolidated)
        if (request.fromWalletId() != null && request.fromWalletId().equals(request.toWalletId())) {
            throw new BadRequestException("Cannot transfer to the same wallet");
        }

        // Determine which wallet ID to link to the primary transaction record
        UUID primaryWalletId = request.transactionType() == TransactionType.CREDIT ? request.toWalletId() : request.fromWalletId();

        // Fetch the primary wallet (just to prove it exists before creating the transaction record)
        Wallet primaryWallet = walletRepository.findById(primaryWalletId)
                                               .orElseThrow(() -> new NotFoundException("Wallet not found with id: " + primaryWalletId));

        // Check balance for DEBIT or TRANSFER transactions
        if (request.transactionType() == TransactionType.DEBIT || request.transactionType() == TransactionType.TRANSFER) {
            Wallet fromWallet = walletRepository.findById(request.fromWalletId())
                                                .orElseThrow(() -> new NotFoundException("From wallet not found with id: " + request.fromWalletId()));
            if (fromWallet.getBalance().compareTo(request.amount()) < 0) {
                throw new BadRequestException("Insufficient funds");
            }
        }

        // Create the PENDING transaction (NEW - Consolidated)
        Transaction transaction = Transaction.builder()
                                             .wallet(primaryWallet)
                                             .type(request.transactionType())
                                             .status(TransactionStatus.PENDING) // ALWAYS PENDING NOW!
                                             .amount(request.amount())
                                             .reference(referenceGenerator.generateTransactionReference())
                                             .idempotencyKey(request.idempotencyKey())
                                             .metadata(request.metadata())
                                             .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        // Create the RabbitMQ Event
        TransactionEvent event = new TransactionEvent(
                savedTransaction.getId(),
                request.fromWalletId(),
                request.toWalletId(),
                request.transactionType(),
                request.amount(),
                request.idempotencyKey()
        );

        // Publish to RabbitMQ
        // Hand it to Spring. Spring will hold it until the @Transactional method finishes
        applicationEventPublisher.publishEvent(event);

        // Map and Return
        TransactionResponse response = TransactionMapper.toResponse(savedTransaction);

        return finalizeAndReturn(response, request.idempotencyKey());
    }

    /**
     * Retrieves a transaction by its unique identifier.
     */
    @Override
    public TransactionResponse getTransactionById(UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .map(TransactionMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Transaction not found with id: " +  transactionId));
    }

    /**
     * Retrieves a transaction using its reference value.
     */
    @Override
    public TransactionResponse getTransactionByReference(String reference) {
        return transactionRepository.findByReference(reference)
                                    .map(TransactionMapper::toResponse)
                                    .orElseThrow(() -> new NotFoundException("Transaction not " +
                                                                                     "found with " +
                                                                                     "reference: " +  reference));
    }

    /**
     * Retrieves all transactions associated with a given wallet.
     */
    @Override
    public List<TransactionResponse> getTransactionsByWalletId(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                                        .orElseThrow(() -> new NotFoundException("Wallet not found with id: " + walletId));

        return transactionRepository.findAllByWallet(wallet)
                                    .stream()
                                    .map(TransactionMapper::toResponse)
                                    .toList();
    }

    /**
     * Finalizes the transaction response and updates the idempotency record.
     */
    private TransactionResponse finalizeAndReturn(
            TransactionResponse response, UUID idempotencyKey) {

        try {
            String jsonResponse = objectMapper.writeValueAsString(response);

            idempotencyService.update(idempotencyKey, jsonResponse);
        } catch (Exception e) {
            log.error("Failed to serialize response");
            throw new RuntimeException("Failed to process request");
        }
        return response;
    }
}
