package com.minipay.mpps.wallet;

import com.minipay.mpps.common.exception.AlreadyExistsException;
import com.minipay.mpps.common.exception.NotFoundException;
import com.minipay.mpps.currency.Currency;
import com.minipay.mpps.currency.CurrencyRepository;
import com.minipay.mpps.user.User;
import com.minipay.mpps.user.UserRepository;
import com.minipay.mpps.wallet.dto.CreateWalletRequest;
import com.minipay.mpps.wallet.dto.WalletResponse;
import com.minipay.mpps.wallet.mapper.WalletMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService{
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;

    /** Creates a new wallet for a user in the specified currency. */
    @Override
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest createWalletRequest) {
        //Get the User details
        User user = userRepository.findById(createWalletRequest.userId())
                .orElseThrow(() -> new NotFoundException("User not found with id: " + createWalletRequest.userId()));

        //Get the currency details
        Currency currency = currencyRepository.findById(createWalletRequest.currencyCode())
                                              .orElseThrow(() -> new NotFoundException("Currency not found with code: " + createWalletRequest.currencyCode()));

        //Check to see if the currency already exists
        if(walletRepository.existsByUserAndCurrency(user,currency)){
            throw new AlreadyExistsException("Wallet already exists for user: " + createWalletRequest.userId() + " and currency: " + createWalletRequest.currencyCode());
        }

        //Create a new Wallet
        Wallet newWallet = Wallet.builder()
                                 .currency(currency)
                                 .user(user)
                                 .build();

        Wallet savedWallet = walletRepository.save(newWallet);

        //Map to wallet response and return
        return WalletMapper.toResponse(savedWallet);
    }

    /** Returns a specific wallet by id */
    @Override
    public WalletResponse getWalletById(UUID walletId) {
        return walletRepository.findById(walletId)
                .map(WalletMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Wallet not found with id: " + walletId));
    }

    /** Returns all wallets belonging to the given user. */
    @Override
    public List<WalletResponse> getAllWalletsByUserId(UUID userId) {
        //Get the User details
        User user = userRepository.findById(userId)
                                  .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));

        //Return all wallets
        return walletRepository.findAllByUser(user)
                .stream()
                .map(WalletMapper::toResponse)
                .toList();
    }
}
