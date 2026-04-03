package com.minipay.mpps.wallet;

import com.minipay.mpps.common.BaseEntity;
import com.minipay.mpps.currency.Currency;
import com.minipay.mpps.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "wallets")
public class Wallet extends BaseEntity {

    @Column(precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    // Relationships
    @ManyToOne
    @JoinColumn(name = "currency_code", nullable = false)
    private Currency currency;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
