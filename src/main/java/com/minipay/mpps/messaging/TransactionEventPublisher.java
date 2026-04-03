package com.minipay.mpps.messaging;

import com.minipay.mpps.common.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventPublisher {

    /**
     * RabbitTemplate is Spring AMQP's core helper class.
     * It automatically handles opening and closing the TCP connection to the RabbitMQ server.
     */
    private final RabbitTemplate rabbitTemplate;

    // We changed this from a normal method to a TransactionalEventListener
    // It will now WAIT until the database says "COMMIT SUCCESSFUL" before running.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(TransactionEvent event) {
        log.info("Publishing transaction event to RabbitMQ: {}", event.transactionId());

         // convertAndSend performs two distinct actions:
         // 1. Converts our Java record (TransactionEvent) into a JSON string
         //  (using the Jackson2JsonMessageConverter we defined in RabbitMQConfig).
         // 2. Transmits that JSON to the RabbitMQ Exchange.
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TRANSACTION_EXCHANGE,   // The router that receives the message
                RabbitMQConfig.TRANSACTION_ROUTING_KEY, // The tag the Exchange uses to route it to the correct Queue
                event                                  // The actual data payload
        );
    }
}
