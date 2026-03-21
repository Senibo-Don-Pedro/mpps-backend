package com.minipay.mpps.wallet;

import com.minipay.mpps.wallet.dto.CreateWalletRequest;
import com.minipay.mpps.wallet.dto.WalletResponse;

import java.util.List;
import java.util.UUID;

public interface WalletService {
    WalletResponse createWallet(CreateWalletRequest createWalletRequest);

    WalletResponse getWalletById(UUID walletId);

    List<WalletResponse> getAllWalletsByUserId(UUID userId);
}
