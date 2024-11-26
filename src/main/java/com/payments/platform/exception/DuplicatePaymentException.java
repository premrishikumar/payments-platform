package com.payments.platform.exception;

public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException(String idempotencyKey) {
        super("Duplicate payment request detected for key: " + idempotencyKey);
    }
}
