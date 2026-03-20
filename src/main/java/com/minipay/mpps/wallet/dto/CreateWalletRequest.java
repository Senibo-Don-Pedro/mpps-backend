package com.minipay.mpps.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWalletRequest(
        @NotBlank
        @Size(min = 3, max = 3, message = "Currency Code must be 3 letters in capital")
        String currencyCode,

        @NotNull(message = "User Id is required")
        UUID userId
) {
}
