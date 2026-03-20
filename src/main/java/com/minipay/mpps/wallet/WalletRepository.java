package com.minipay.mpps.wallet;

import com.minipay.mpps.currency.Currency;
import com.minipay.mpps.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
  boolean existsByUserAndCurrency(User user, Currency currency);

  Optional<Wallet> findByUserAndCurrency(User user, Currency currency);

  List<Wallet> findAllByUser(User user);
}
