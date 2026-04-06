package com.minipay.mpps.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookRetryJob {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final WebhookDispatcherService webhookDispatcherService;

    // Runs every 10 minutes (600000 ms)
    @Scheduled(fixedDelay = 600000)
    public void processFailedWebhooks() {
        log.info("Starting Webhook Retry Job...");

        // Fetch all FAILED webhooks that haven't hit the 5-attempt limit
        List<WebhookDelivery> failedDeliveries = webhookDeliveryRepository
                .findByStatusAndAttemptCountLessThan(WebhookStatus.FAILED, 5);

        if (failedDeliveries.isEmpty()) {
            log.info("No failed webhooks to retry.");
            return;
        }

        log.info("Found {} failed webhooks to retry.", failedDeliveries.size());

        // Loop through and retry each one
        for (WebhookDelivery delivery : failedDeliveries) {
            webhookDispatcherService.retryDispatch(delivery);
        }

        log.info("Finished Webhook Retry Job.");
    }
}
