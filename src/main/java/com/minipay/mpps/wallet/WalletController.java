package com.minipay.mpps.wallet;

import com.minipay.mpps.common.dto.ApiSuccessResponse;
import com.minipay.mpps.wallet.dto.CreateWalletRequest;
import com.minipay.mpps.wallet.dto.WalletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wallets")
public class WalletController {
    private final WalletService walletService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiSuccessResponse<WalletResponse> createWallet(
            @Valid
            @RequestBody
            CreateWalletRequest request
    ) {

        WalletResponse walletResponse = walletService.createWallet(request);

        return new ApiSuccessResponse<>(
                true,
                "Wallet created successfully",
                walletResponse
        );

    }

    @GetMapping
    public ApiSuccessResponse<List<WalletResponse>> getWalletByUserId(
            @RequestParam
            UUID userId
    ) {
        List<WalletResponse> walletResponse = walletService.getAllWalletsByUserId(userId);

        return new ApiSuccessResponse<>(
                true,
                "User wallets returned successfully",
                walletResponse
        );
    }

    @GetMapping("/{walletId}")
    public ApiSuccessResponse<WalletResponse> getWalletById(
            @PathVariable UUID walletId
    ) {
        WalletResponse walletResponse = walletService.getWalletById(walletId);

        return new ApiSuccessResponse<>(
                true,
                "Wallet returned successfully",
                walletResponse
        );
    }


}
