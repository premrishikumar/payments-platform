package com.payments.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.domain.Payment;
import com.payments.platform.domain.PaymentScheme;
import com.payments.platform.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests specifically for idempotency behaviour.
 * Covers: duplicate detection, concurrent submissions, retry safety.
 */
@DisplayName("Idempotency Integration Tests")
class IdempotencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    private Payment payment;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        idempotencyKey = "IK-IDEM-" + UUID.randomUUID();

        payment = Payment.builder()
                .scheme(PaymentScheme.FPS)
                .amount(new BigDecimal("500.00"))
                .currency("GBP")
                .debtorAccountNumber("12345678")
                .debtorSortCode("200000")
                .creditorAccountNumber("87654321")
                .creditorSortCode("400000")
                .reference("Idempotency-Test")
                .build();
    }

    @Test
    @DisplayName("Should return same payment ID on repeated submissions with same idempotency key")
    void shouldReturnSamePaymentIdOnRetry() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated())
                .andReturn();

        Payment p1 = objectMapper.readValue(first.getResponse().getContentAsString(), Payment.class);
        Payment p2 = objectMapper.readValue(second.getResponse().getContentAsString(), Payment.class);

        assertThat(p1.getId()).isEqualTo(p2.getId());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should allow different payments with different idempotency keys")
    void shouldAllowDifferentPaymentsWithDifferentKeys() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "KEY-A-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "KEY-B-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated());

        assertThat(paymentRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle concurrent submissions with same idempotency key safely")
    void shouldHandleConcurrentDuplicateSubmissionsSafely() throws Exception {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        String sharedKey = "IK-CONCURRENT-" + UUID.randomUUID();
        String body = objectMapper.writeValueAsString(payment);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                MvcResult result = mockMvc.perform(post("/api/v1/payments")
                                .header("Idempotency-Key", sharedKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andReturn();
                return result.getResponse().getStatus();
            });
        }

        List<Future<Integer>> results = executor.invokeAll(tasks);
        executor.shutdown();

        // All responses should be 201
        for (Future<Integer> result : results) {
            assertThat(result.get()).isEqualTo(201);
        }

        // Only one record created regardless of concurrent requests
        assertThat(paymentRepository.count()).isEqualTo(1);
    }
}
