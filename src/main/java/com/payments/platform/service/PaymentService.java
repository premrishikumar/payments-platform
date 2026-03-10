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

    @Transactional
    public Payment submitPayment(Payment payment) {
        // Check idempotency before attempting insert
        if (idempotencyService.isDuplicate(payment.getIdempotencyKey())) {
            log.info("Duplicate detected via idempotency check: {}", payment.getIdempotencyKey());
            return idempotencyService.getExistingPayment(payment.getIdempotencyKey())
                    .orElseThrow(() -> new DuplicatePaymentException(payment.getIdempotencyKey()));
        }

        try {
            PaymentScheme resolvedScheme = schemeRouter.determineScheme(payment);
            payment.setScheme(resolvedScheme);
            payment.setStatus(PaymentStatus.PENDING);

            Payment saved = paymentRepository.saveAndFlush(payment);
            log.info("Payment saved: id={} scheme={} amount={}", saved.getId(), saved.getScheme(), saved.getAmount());

            eventProducer.publishPaymentSubmitted(saved);
            return saved;

        } catch (DataIntegrityViolationException ex) {
            // Concurrent request already inserted — return existing (race condition safe)
            log.warn("Concurrent duplicate detected for key: {}", payment.getIdempotencyKey());
            return idempotencyService.getExistingPayment(payment.getIdempotencyKey())
                    .orElseThrow(() -> new DuplicatePaymentException(payment.getIdempotencyKey()));
        }
    }

    public Payment getPayment(UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
    }

    public List<Payment> getPaymentsByStatus(PaymentStatus status) {
        return paymentRepository.findByStatus(status);
    }
}
