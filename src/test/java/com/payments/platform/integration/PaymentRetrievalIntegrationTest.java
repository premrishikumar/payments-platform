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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for payment retrieval.
 * Tests GET by ID, filter by status, and 404 handling.
 */
@DisplayName("Payment Retrieval Integration Tests")
class PaymentRetrievalIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAll();
    }

    private String submitPayment(PaymentScheme scheme, BigDecimal amount) throws Exception {
        Payment payment = Payment.builder()
                .scheme(scheme)
                .amount(amount)
                .currency("GBP")
                .debtorAccountNumber("12345678")
                .debtorSortCode("200000")
                .creditorAccountNumber("87654321")
                .creditorSortCode("400000")
                .reference("REF-" + UUID.randomUUID())
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "IK-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated())
                .andReturn();

        Payment created = objectMapper.readValue(result.getResponse().getContentAsString(), Payment.class);
        return created.getId().toString();
    }

    @Test
    @DisplayName("Should retrieve payment by ID")
    void shouldRetrievePaymentById() throws Exception {
        String paymentId = submitPayment(PaymentScheme.FPS, new BigDecimal("100.00"));

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.scheme").value("FPS"))
                .andExpect(jsonPath("$.currency").value("GBP"));
    }

    @Test
    @DisplayName("Should return 404 for unknown payment ID")
    void shouldReturn404ForUnknownPaymentId() throws Exception {
        mockMvc.perform(get("/api/v1/payments/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Should filter payments by PENDING status")
    void shouldFilterPaymentsByStatus() throws Exception {
        // Submit 2 payments — both will be PENDING
        submitPayment(PaymentScheme.FPS, new BigDecimal("100.00"));
        submitPayment(PaymentScheme.BACS, new BigDecimal("500.00"));

        mockMvc.perform(get("/api/v1/payments").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status", everyItem(is("PENDING"))));
    }

    @Test
    @DisplayName("Should return empty list for status with no matching payments")
    void shouldReturnEmptyListForUnmatchedStatus() throws Exception {
        submitPayment(PaymentScheme.FPS, new BigDecimal("100.00"));

        mockMvc.perform(get("/api/v1/payments").param("status", "SETTLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
