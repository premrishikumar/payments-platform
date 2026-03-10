package com.payments.platform.kafka;

import com.payments.platform.config.KafkaConfig;
import com.payments.platform.domain.Payment;
import com.payments.platform.domain.PaymentStatus;
import com.payments.platform.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentRepository paymentRepository;

    @Transactional
    @KafkaListener(topics = KafkaConfig.PAYMENT_SUBMITTED_TOPIC, groupId = "payments-group")
    public void onPaymentSubmitted(Payment payment) {
        log.info("Received payment submitted event: id={} scheme={}", payment.getId(), payment.getScheme());
        paymentRepository.findById(payment.getId()).ifPresent(p -> {
            p.setStatus(PaymentStatus.SUBMITTED);
            paymentRepository.saveAndFlush(p);
            log.info("Payment status updated to SUBMITTED: {}", p.getId());
        });
    }

    @Transactional
    @KafkaListener(topics = KafkaConfig.PAYMENT_FAILED_TOPIC, groupId = "payments-group")
    public void onPaymentFailed(Payment payment) {
        log.warn("Received payment failed event: id={}", payment.getId());
        paymentRepository.findById(payment.getId()).ifPresent(p -> {
            p.setStatus(PaymentStatus.FAILED);
            paymentRepository.saveAndFlush(p);
        });
    }
}
