package com.payments.platform.service;

import com.payments.platform.domain.Payment;
import com.payments.platform.domain.PaymentScheme;
import com.payments.platform.domain.PaymentStatus;
import com.payments.platform.exception.DuplicatePaymentException;
import com.payments.platform.exception.PaymentNotFoundException;
import com.payments.platform.idempotency.IdempotencyService;
import com.payments.platform.kafka.PaymentEventProducer;
import com.payments.platform.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
    private final SchemeRouter schemeRouter;

    /**
     * Outer method — NOT transactional.
     * Catches DataIntegrityViolationException from concurrent duplicate inserts
     * BEFORE Spring can mark any transaction as rollback-only.
     */
    public Payment submitPayment(Payment payment) {
        // Fast path: idempotency check before attempting insert
        if (idempotencyService.isDuplicate(payment.getIdempotencyKey())) {
            log.info("Duplicate detected for key: {}", payment.getIdempotencyKey());
            return idempotencyService.getExistingPayment(payment.getIdempotencyKey())
                    .orElseThrow(() -> new DuplicatePaymentException(payment.getIdempotencyKey()));
        }

        try {
            return persistAndPublish(payment);
        } catch (DataIntegrityViolationException ex) {
            // Two threads passed idempotency check simultaneously —
            // one won the DB insert, the other hits unique constraint.
            // Safe to return the winner's payment.
            log.warn("Concurrent duplicate insert for key: {} — returning existing",
                    payment.getIdempotencyKey());
            return idempotencyService.getExistingPayment(payment.getIdempotencyKey())
                    .orElseThrow(() -> new DuplicatePaymentException(payment.getIdempotencyKey()));
        }
    }

    /**
     * Inner method — transactional boundary isolated here.
     * If this throws, the transaction is rolled back cleanly
     * and the exception propagates up to submitPayment() for handling.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Payment persistAndPublish(Payment payment) {
        PaymentScheme resolvedScheme = schemeRouter.determineScheme(payment);
        payment.setScheme(resolvedScheme);
        payment.setStatus(PaymentStatus.PENDING);

        Payment saved = paymentRepository.saveAndFlush(payment);
        log.info("Payment saved: id={} scheme={} amount={}",
                saved.getId(), saved.getScheme(), saved.getAmount());

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
