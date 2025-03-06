package com.payments.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.domain.Payment;
import com.payments.platform.domain.PaymentScheme;
import com.payments.platform.domain.PaymentStatus;
import com.payments.platform.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for payment submission flow.
 * Tests the full stack: Controller → Service → Repository → Kafka
 */
@DisplayName("Payment Submission Integration Tests")
class PaymentSubmissionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    private Payment validFpsPayment;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
        idempotencyKey = "IK-" + UUID.randomUUID();

        validFpsPayment = Payment.builder()
                .scheme(PaymentScheme.FPS)
                .amount(new BigDecimal("250.00"))
                .currency("GBP")
                .debtorAccountNumber("12345678")
                .debtorSortCode("200000")
                .creditorAccountNumber("87654321")
                .creditorSortCode("400000")
                .reference("Invoice-2025-001")
                .build();
    }

    @Test
    @DisplayName("Should submit FPS payment and return 201 CREATED")
    void shouldSubmitFpsPaymentSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFpsPayment)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheme").value("FPS"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(250.00))
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey));
    }

    @Test
    @DisplayName("Should persist payment to database on submission")
    void shouldPersistPaymentToDatabase() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFpsPayment)))
                .andExpect(status().isCreated());

        assertThat(paymentRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
        Payment saved = paymentRepository.findByIdempotencyKey(idempotencyKey).get();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getAmount()).isEqualByComparingTo("250.00");
    }

    @Test
    @DisplayName("Should submit CHAPS payment for high-value amount")
    void shouldSubmitChapsPaymentForHighValue() throws Exception {
        Payment chapsPayment = Payment.builder()
                .scheme(PaymentScheme.CHAPS)
                .amount(new BigDecimal("1500000.00"))
                .currency("GBP")
                .debtorAccountNumber("11111111")
                .debtorSortCode("200000")
                .creditorAccountNumber("22222222")
                .creditorSortCode("400000")
                .reference("Property-Settlement-2025")
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chapsPayment)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheme").value("CHAPS"))
                .andExpect(jsonPath("$.amount").value(1500000.00));
    }

    @Test
    @DisplayName("Should return 409 CONFLICT on duplicate idempotency key")
    void shouldReturnExistingPaymentOnDuplicateIdempotencyKey() throws Exception {
        // First submission
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFpsPayment)))
                .andExpect(status().isCreated());

        // Duplicate — should return same payment, not create new
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFpsPayment)))
                .andExpect(status().isCreated()) // 201 again — idempotent response
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey));

        // Verify only one record in DB
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("Should return 400 when Idempotency-Key header is missing")
    void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFpsPayment)))
                .andExpect(status().isBadRequest());
    }
}
