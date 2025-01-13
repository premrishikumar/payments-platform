package com.payments.platform.service;

import com.payments.platform.domain.Payment;
import com.payments.platform.domain.PaymentScheme;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Routes payments to correct scheme based on amount and urgency.
 * FPS:   up to £1,000,000, near real-time
 * CHAPS: high-value same-day, no upper limit
 * BACS:  bulk, 3-day clearing
 */
@Component
@Slf4j
public class SchemeRouter {

    private static final BigDecimal FPS_LIMIT = new BigDecimal("1000000");

    public PaymentScheme determineScheme(Payment payment) {
        if (payment.getScheme() != null) {
            log.debug("Scheme explicitly set: {}", payment.getScheme());
            return payment.getScheme();
        }

        BigDecimal amount = payment.getAmount();
        if (amount.compareTo(FPS_LIMIT) <= 0) {
            log.info("Routing to FPS: amount={}", amount);
            return PaymentScheme.FPS;
        } else {
            log.info("Routing to CHAPS: amount={} exceeds FPS limit", amount);
            return PaymentScheme.CHAPS;
        }
    }
}
