package com.minipay.mpps.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minipay.mpps.transaction.Transaction;
import com.minipay.mpps.transaction.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatcherService {

    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${webhook.target-url}")
    private String targetUrl;

    @Transactional
    public void dispatch(Transaction transaction) {
        log.info("Preparing to dispatch webhook for transaction: {}", transaction.getId());

        WebhookDelivery delivery = WebhookDelivery.builder()
                                                  .transaction(transaction)
                                                  .url(targetUrl)
                                                  .status(WebhookStatus.PENDING)
                                                  .build();

        try {
            // Convert transaction to JSON string for the payload
            String payload = objectMapper.writeValueAsString(TransactionMapper.toResponse(transaction));
            delivery.setPayload(payload);

            // Save the initial PENDING state
            webhookDeliveryRepository.save(delivery);

            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            log.info("Firing webhook to {}...", targetUrl);

            ResponseEntity<String> response = restTemplate.postForEntity(targetUrl, request,
                                                                   String.class);


            if (response.getStatusCode().is2xxSuccessful()) {

                delivery.setStatus(WebhookStatus.SUCCESS);

                log.info("Webhook delivered successfully for transaction {}", transaction.getId());
            } else {

                delivery.setStatus(WebhookStatus.FAILED);

                log.error("Webhook delivery failed for transaction {}: Received non-2xx response: {}",
                          transaction.getId(), response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Webhook delivery failed for transaction {}: {}", transaction.getId(), e.getMessage());
            // Update status to FAILED on exception
            delivery.setStatus(WebhookStatus.FAILED);
        } finally {
            // Update the attempt count and last attempted time
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setLastAttemptAt(OffsetDateTime.now());

            // Save the final result
            webhookDeliveryRepository.save(delivery);
        }
    }

    public void retryDispatch(WebhookDelivery delivery) {
        log.info("Retrying webhook delivery ID: {}, Attempt: {}", delivery.getId(), delivery.getAttemptCount() + 1);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // We use the payload we already saved in the database!
            HttpEntity<String> request = new HttpEntity<>(delivery.getPayload(), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(delivery.getUrl(), request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                delivery.setStatus(WebhookStatus.SUCCESS);
                log.info("Webhook retry successful for delivery {}", delivery.getId());
            } else {
                handleFailure(delivery);
            }
        } catch (Exception e) {
            log.error("Webhook retry failed for delivery {}: {}", delivery.getId(), e.getMessage());
            handleFailure(delivery);
        } finally {
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setLastAttemptAt(OffsetDateTime.now());
            webhookDeliveryRepository.save(delivery);
        }
    }

    private void handleFailure(WebhookDelivery delivery) {
        // If we have tried 5 times already (this is the 5th failure), mark it permanently failed
        if (delivery.getAttemptCount() >= 4) {
            delivery.setStatus(WebhookStatus.PERMANENTLY_FAILED);
            log.error("Webhook permanently failed after 5 attempts for delivery {}", delivery.getId());
        } else {
            delivery.setStatus(WebhookStatus.FAILED);
        }
    }
}
