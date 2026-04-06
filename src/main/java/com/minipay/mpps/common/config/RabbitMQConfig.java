package com.minipay.mpps.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for transaction message processing.
 *
 * <p>This configuration sets up a messaging infrastructure for asynchronous transaction handling.
 * It follows the common RabbitMQ pattern of Queue → Exchange → Binding, where:
 * <ul>
 *   <li><b>Queue:</b> A buffer that holds messages until consumers process them</li>
 *   <li><b>Exchange:</b> Routes incoming messages to the appropriate queues based on routing keys</li>
 *   <li><b>Binding:</b> Maps an exchange to a queue using a routing key</li>
 * </ul>
 *
 * <p>This configuration also implements a Dead Letter Queue (DLQ) pattern to handle messages
 * that fail processing. Failed messages are automatically routed to the DLQ for manual inspection.
 *
 * <p>All messages are serialized/deserialized using JSON format via Jackson2JsonMessageConverter.
 *
 * @see org.springframework.amqp.core.Queue
 * @see org.springframework.amqp.core.DirectExchange
 */
@Configuration
public class RabbitMQConfig {

    /** Queue name for transaction messages. */
    public static final String TRANSACTION_QUEUE = "transaction_queue";

    /** Exchange name for routing transaction messages. Uses DirectExchange for point-to-point routing. */
    public static final String TRANSACTION_EXCHANGE = "transaction_exchange";

    /** Routing key used to bind the queue to the exchange. Messages published with this key are routed to TRANSACTION_QUEUE. */
    public static final String TRANSACTION_ROUTING_KEY = "transaction_routing_key";

    /** Dead Letter Queue (DLQ) name for messages that fail processing. */
    public static final String TRANSACTION_DLQ = "transaction_queue.dlq";

    /** Dead Letter Exchange (DLX) name for routing failed messages to the DLQ. */
    public static final String TRANSACTION_DLX = "transaction_exchange.dlq";

    /** Routing key for dead letter messages. Failed messages use this key to reach the DLQ. */
    public static final String TRANSACTION_DLQ_ROUTING_KEY = "transaction.dlq.routing.key";

    /**
     * Creates the main transaction queue.
     *
     * <p>The queue is durable (persists across server restarts), ensuring no messages are lost
     * if the application shuts down unexpectedly.
     *
     * @return a durable queue for processing transaction messages
     */
    @Bean
    public Queue transactionQueue() {
        return QueueBuilder.durable(TRANSACTION_QUEUE)
                .withArgument("x-dead-letter-exchange",TRANSACTION_DLX)
                .withArgument("x-dead-letter-routing-key",TRANSACTION_DLQ_ROUTING_KEY)
                .build();
    }

    /**
     * Creates the transaction exchange.
     *
     * <p>A DirectExchange routes messages to queues based on exact routing key matches.
     * This is used for point-to-point messaging where one message goes to one specific queue.
     *
     * @return a direct exchange for transaction message routing
     */
    @Bean
    public DirectExchange transactionExchange() {
        return new DirectExchange(TRANSACTION_EXCHANGE);
    }

    /**
     * Binds the transaction queue to the transaction exchange.
     *
     * <p>This creates the connection between the exchange and queue using a routing key.
     * When a message is published to the exchange with the routing key "transaction_routing_key",
     * it will be delivered to the transaction queue.
     *
     * @param transactionQueue the queue to bind
     * @param transactionExchange the exchange to bind from
     * @return the binding configuration
     */
    @Bean
    public Binding transactionBinding(Queue transactionQueue, DirectExchange transactionExchange) {
        return BindingBuilder.bind(transactionQueue).to(transactionExchange).with(TRANSACTION_ROUTING_KEY);
    }

    /**
     * Creates the dead letter queue (DLQ).
     *
     * <p>The DLQ is where messages are sent when they fail processing (e.g., if a consumer
     * throws an exception multiple times). This prevents messages from being lost and allows
     * for manual inspection and debugging of failed messages. The queue is durable.
     *
     * @return a durable dead letter queue
     */
    @Bean
    public Queue deadLetterQueue() {
        return new Queue(TRANSACTION_DLQ, true);
    }

    /**
     * Creates the dead letter exchange (DLX).
     *
     * <p>The DLX receives messages from the main transaction queue when they exceed the retry limit.
     * It routes these failed messages to the dead letter queue for inspection.
     *
     * @return a direct exchange for routing dead letter messages
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(TRANSACTION_DLX);
    }

    /**
     * Binds the dead letter queue to the dead letter exchange.
     *
     * <p>This configuration ensures that when messages fail in the main transaction queue,
     * they are automatically rerouted to the DLQ via the DLX using the dead letter routing key.
     *
     * @param deadLetterQueue the queue to bind
     * @param deadLetterExchange the exchange to bind from
     * @return the dead letter binding configuration
     */
    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(TRANSACTION_DLQ_ROUTING_KEY);
    }

    /**
     * Creates the message converter for JSON serialization/deserialization.
     *
     * <p>RabbitMQ messages are bytes by default. This converter allows automatic conversion
     * between Java objects and JSON strings, making it easier to work with domain objects
     * directly without manual serialization logic.
     *
     * @return a Jackson-based JSON message converter
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
