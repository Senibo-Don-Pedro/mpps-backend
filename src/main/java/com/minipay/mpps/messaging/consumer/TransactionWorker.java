package com.minipay.mpps.messaging.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.mpps.common.config.RabbitMQConfig;
import com.minipay.mpps.common.enums.TransactionStatus;
import com.minipay.mpps.common.enums.TransactionType;
import com.minipay.mpps.common.exception.BadRequestException;
import com.minipay.mpps.common.exception.NotFoundException;
import com.minipay.mpps.idempotency.IdempotencyService;
import com.minipay.mpps.messaging.TransactionEvent;
import com.minipay.mpps.transaction.Transaction;
import com.minipay.mpps.transaction.TransactionRepository;
import com.minipay.mpps.transaction.dto.TransactionResponse;
import com.minipay.mpps.transaction.mapper.TransactionMapper;
import com.minipay.mpps.wallet.Wallet;
import com.minipay.mpps.wallet.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionWorker {

    // Inject the tools the worker needs to do its job
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;

    @RabbitListener(queues = RabbitMQConfig.TRANSACTION_QUEUE)
    @Transactional
    public void processTransaction(TransactionEvent event) {
        log.info("Worker picked up transaction: {} of type {}",
                 event.transactionId(),
                 event.transactionType());

        try {
            switch (event.transactionType()) {
                case CREDIT -> handleCredit(event);

                case DEBIT -> handleDebit(event);

                case TRANSFER -> handleTransfer(event);

                default -> throw new IllegalArgumentException("Unknown transaction type");
            }
        } catch (BadRequestException | NotFoundException | IllegalArgumentException e) {
            log.error("Transaction {} failed: {}", event.transactionId(), e.getMessage());

            // 1. Fetch the transaction from the DB safely using ifPresent
            transactionRepository.findById(event.transactionId()).ifPresent(transaction -> {
                // 2. Mark it as FAILED
                transaction.setStatus(TransactionStatus.FAILED);
                // 3. Save it back to the DB
                Transaction savedTransaction = transactionRepository.save(transaction);

                //Update the idempotency key
                convertToTransactionString(savedTransaction);
            });
        } catch (Exception e) {
            // THESE ARE INFRASTRUCTURE FAILURES.
            log.error("Infrastructure or system error for transaction {}: {}", event.transactionId(), e.getMessage());

            throw new RuntimeException("Infrastructure or system error", e);
        }
    }

    protected void handleCredit(TransactionEvent event) {
        log.info("Executing CREDIT logic for wallet {}", event.toWalletId());
        //Fetch the target wallet using event.toWalletId().
        Wallet toWallet = walletRepository.findByIdForUpdate(event.toWalletId()).orElseThrow(
                () -> new NotFoundException("To wallet not found with id: " + event.toWalletId())
        );

        // Add the event.amount() to the targetWallet's current balance.
        toWallet.setBalance(toWallet.getBalance().add(event.amount()));

        // Save the updated wallet back to the database.
        walletRepository.save(toWallet);

        // Fetch the original PENDING transaction using event.transactionId().
        Transaction transaction = transactionRepository.findById(event.transactionId())
                                                       .orElseThrow(
                                                               () -> new NotFoundException(
                                                                       "Transaction not found with id: " + event.transactionId())
                                                       );

        // Change the transaction status to SUCCESS and save it.
        transaction.setStatus(TransactionStatus.SUCCESS);
        Transaction savedTransaction = transactionRepository.save(transaction);

        //Update the idempotency key
        convertToTransactionString(savedTransaction);

        log.info("Successfully credited {} to wallet {}", event.amount(), event.toWalletId());

    }

    protected void handleDebit(TransactionEvent event) {
        log.info("Executing DEBIT logic for wallet {}", event.fromWalletId());

        //Fetch the target wallet using event.fromWalletId().
        Wallet fromWallet = walletRepository.findByIdForUpdate(event.fromWalletId()).orElseThrow(
                () -> new NotFoundException("From wallet not found with id: " + event.fromWalletId())
        );

        //Check if there is sufficient amount
        if (fromWallet.getBalance().compareTo(event.amount()) < 0) {
            throw new BadRequestException("Insufficient funds in wallet: " + event.fromWalletId());
        }

        // Subtract the event.amount() from the targetWallet's current balance.
        fromWallet.setBalance(fromWallet.getBalance().subtract(event.amount()));

        // Save the updated wallet back to the database.
        walletRepository.save(fromWallet);

        // Fetch the original PENDING transaction using event.transactionId().
        Transaction transaction = transactionRepository.findById(event.transactionId())
                                                       .orElseThrow(
                                                               () -> new NotFoundException(
                                                                       "Transaction not found with id: " + event.transactionId())
                                                       );

        // Change the transaction status to SUCCESS and save it.
        transaction.setStatus(TransactionStatus.SUCCESS);

        Transaction savedTransaction = transactionRepository.save(transaction);

        //Update the idempotency key
        convertToTransactionString(savedTransaction);

        log.info("Successfully debited {} from wallet {}", event.amount(), event.fromWalletId());

    }

    protected void handleTransfer(TransactionEvent event) {
        log.info("Executing TRANSFER logic from wallet {} to wallet {}",
                 event.fromWalletId(),
                 event.toWalletId());

        // Fetch and lock the sender's wallet
        Wallet fromWallet = walletRepository.findByIdForUpdate(event.fromWalletId()).orElseThrow(
                () -> new NotFoundException("From wallet not found with id: " + event.fromWalletId())
        );

        // Fetch and lock the receiver's wallet
        Wallet toWallet = walletRepository.findByIdForUpdate(event.toWalletId()).orElseThrow(
                () -> new NotFoundException("To wallet not found with id: " + event.toWalletId())
        );

        // Check if sender has enough money
        if (fromWallet.getBalance().compareTo(event.amount()) < 0) {
            throw new BadRequestException("Insufficient funds in wallet: " + event.fromWalletId());
        }

        // Move the money in memory
        fromWallet.setBalance(fromWallet.getBalance().subtract(event.amount()));
        toWallet.setBalance(toWallet.getBalance().add(event.amount()));

        // Save both wallets back to the database
        walletRepository.save(fromWallet);
        walletRepository.save(toWallet);

        // Fetch the original PENDING transaction
        Transaction transaction = transactionRepository.findById(event.transactionId()).orElseThrow(
                () -> new NotFoundException("Transaction not found with id: " + event.transactionId())
        );

        // Change the debit transaction status to SUCCESS and save it
        transaction.setStatus(TransactionStatus.SUCCESS);

        Transaction savedTransaction = transactionRepository.save(transaction);

        // Create a new Credit leg and save it to the database
        Transaction creditTransaction = Transaction.builder()
                                                   .wallet(toWallet)
                                                   .type(TransactionType.CREDIT)
                                                   .status(TransactionStatus.SUCCESS)
                                                   .amount(event.amount())
                                                   .reference(transaction.getReference())
                                                   .idempotencyKey(UUID.randomUUID())
                                                   .build();



        transactionRepository.save(creditTransaction);

        //Update the idempotency key
        convertToTransactionString(savedTransaction);


        log.info("Successfully transferred {} from {} to {}",
                 event.amount(),
                 event.fromWalletId(),
                 event.toWalletId());
    }

    private void convertToTransactionString(Transaction transaction) {
        TransactionResponse newResponse = TransactionMapper.toResponse(transaction);

        try {
            String convertedResponse = objectMapper.writeValueAsString(newResponse);

            idempotencyService.update(transaction.getIdempotencyKey(), convertedResponse);

        } catch (Exception e) {
            log.error("Error converting transaction to string: {}", e.getMessage());
            throw new RuntimeException("Error converting transaction to string", e);
        }


    }
}
