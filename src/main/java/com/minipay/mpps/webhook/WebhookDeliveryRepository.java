package com.minipay.mpps.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    List<WebhookDelivery> findByStatusAndAttemptCountLessThan(WebhookStatus status,
                                                              int maxAttemptCount);
}
