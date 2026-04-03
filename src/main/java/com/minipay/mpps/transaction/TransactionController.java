package com.minipay.mpps.transaction;

import com.minipay.mpps.common.dto.ApiSuccessResponse;
import com.minipay.mpps.transaction.dto.CreateTransactionRequest;
import com.minipay.mpps.transaction.dto.TransactionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiSuccessResponse<TransactionResponse> createTransaction(
            @Valid
            @RequestBody
            CreateTransactionRequest request
    ) {

        TransactionResponse transactionResponse = transactionService.createTransaction(request);

        return new ApiSuccessResponse<>(
                true,
                "Transaction created successfully",
                transactionResponse
        );

    }

    @GetMapping
    public ApiSuccessResponse<List<TransactionResponse>> getTransactionByWalletId(
            @RequestParam
            @NotNull(message = "Wallet Id Cannot be null")
            UUID walletId
    ) {
        List<TransactionResponse> transactionResponse =
                transactionService.getTransactionsByWalletId(walletId);

        return new ApiSuccessResponse<>(
                true,
                "Transaction returned successfully",
                transactionResponse
        );
    }

    @GetMapping("/{transactionId}")
    public ApiSuccessResponse<TransactionResponse> getTransactionById(
            @PathVariable UUID transactionId
    ) {
        TransactionResponse transactionResponse = transactionService.getTransactionById(
                transactionId);

        return new ApiSuccessResponse<>(
                true,
                "Transaction returned successfully",
                transactionResponse
        );
    }

    @GetMapping("/reference/{reference}")
    public ApiSuccessResponse<TransactionResponse> getTransactionByReference(
            @PathVariable String reference
    ) {
        TransactionResponse transactionResponse = transactionService.getTransactionByReference(
                reference);

        return new ApiSuccessResponse<>(
                true,
                "Transaction returned successfully",
                transactionResponse
        );
    }
}
