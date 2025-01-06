package com.payments.platform.controller;

import com.payments.platform.domain.Payment;
import com.payments.platform.domain.PaymentStatus;
import com.payments.platform.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Payment> submitPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody Payment payment) {
        payment.setIdempotencyKey(idempotencyKey);
        log.info("POST /payments scheme={} idempotencyKey={}", payment.getScheme(), idempotencyKey);
        Payment result = paymentService.submitPayment(payment);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Payment> getPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    @GetMapping
    public ResponseEntity<List<Payment>> getPaymentsByStatus(
            @RequestParam(required = false) PaymentStatus status) {
        if (status != null) {
            return ResponseEntity.ok(paymentService.getPaymentsByStatus(status));
        }
        return ResponseEntity.ok(List.of());
    }
}
