package com.payments.platform.kafka;

import com.payments.platform.config.KafkaConfig;
import com.payments.platform.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Payment> kafkaTemplate;

    public void publishPaymentSubmitted(Payment payment) {
        publish(KafkaConfig.PAYMENT_SUBMITTED_TOPIC, payment);
    }

    public void publishPaymentFailed(Payment payment) {
        publish(KafkaConfig.PAYMENT_FAILED_TOPIC, payment);
    }

    private void publish(String topic, Payment payment) {
        String key = payment.getIdempotencyKey();
        CompletableFuture<SendResult<String, Payment>> future =
                kafkaTemplate.send(topic, key, payment);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish payment event to topic={} key={}: {}",
                        topic, key, ex.getMessage());
            } else {
                log.info("Published payment event to topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
