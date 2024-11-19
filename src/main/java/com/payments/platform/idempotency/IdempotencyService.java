package com.payments.platform.idempotency;

import com.payments.platform.domain.Payment;
import com.payments.platform.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Idempotency guard — prevents duplicate payment processing.
 * Checks idempotency key before allowing payment to proceed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final PaymentRepository paymentRepository;

    public boolean isDuplicate(String idempotencyKey) {
        boolean exists = paymentRepository.existsByIdempotencyKey(idempotencyKey);
        if (exists) {
            log.warn("Duplicate payment detected for idempotency key: {}", idempotencyKey);
        }
        return exists;
    }

    public Optional<Payment> getExistingPayment(String idempotencyKey) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey);
    }
}
