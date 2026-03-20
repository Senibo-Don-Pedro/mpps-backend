package com.minipay.mpps.wallet.mapper;

import com.minipay.mpps.common.dto.CurrencyInfo;
import com.minipay.mpps.wallet.Wallet;
import com.minipay.mpps.wallet.dto.WalletResponse;

public class WalletMapper {
    public static WalletResponse toResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getBalance(),
                new CurrencyInfo(
                        wallet.getCurrency().getCode(),
                        wallet.getCurrency().getName(),
                        wallet.getCurrency().getSymbol()
                ),
                wallet.getUser().getId(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }
}
