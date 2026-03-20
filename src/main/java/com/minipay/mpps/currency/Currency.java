package com.minipay.mpps.currency;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.minipay.mpps.wallet.Wallet;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
@EntityListeners(AuditingEntityListener.class)
@Table(name = "currencies")
public class Currency {
    @Id
    @Column(columnDefinition = "CHAR(3)", nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String symbol;

    @Builder.Default
    @Column(name = "is_active")
    private Boolean isActive = true;

    // Auditing
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;



}
