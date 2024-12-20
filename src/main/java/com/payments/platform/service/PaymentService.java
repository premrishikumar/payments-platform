package com.payments.platform.service;

import com.payments.platform.domain.Payment;
import com.payments.platform.domain.PaymentStatus;
import com.payments.platform.exception.DuplicatePaymentException;
import com.payments.platform.exception.PaymentNotFoundException;
import com.payments.platform.idempotency.IdempotencyService;
import com.payments.platform.kafka.PaymentEventProducer;
import com.payments.platform.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyService idempotencyService;
    private final PaymentEventProducer eventProducer;

    @Transactional
    public Payment submitPayment(Payment payment) {
        if (idempotencyService.isDuplicate(payment.getIdempotencyKey())) {
            // Return existing payment rather than throwing — safe retry behaviour
            return idempotencyService.getExistingPayment(payment.getIdempotencyKey())
                    .orElseThrow(() -> new DuplicatePaymentException(payment.getIdempotencyKey()));
        }

        payment.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);
        log.info("Payment saved: id={} scheme={} amount={}", saved.getId(), saved.getScheme(), saved.getAmount());

        eventProducer.publishPaymentSubmitted(saved);
        return saved;
    }

    public Payment getPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public List<Payment> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status);
    }
}
