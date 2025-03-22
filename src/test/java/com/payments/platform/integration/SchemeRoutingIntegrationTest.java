package com.payments.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.domain.Payment;
import com.payments.platform.domain.PaymentScheme;
import com.payments.platform.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for payment scheme routing.
 * Verifies correct scheme assignment based on amount thresholds.
 */
@DisplayName("Scheme Routing Integration Tests")
class SchemeRoutingIntegrationTest extends BaseIntegrationTest {

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

    @ParameterizedTest(name = "Amount {0} GBP should route to {1}")
    @CsvSource({
        "100.00,       FPS",
        "999999.99,    FPS",
        "1000000.00,   FPS",
        "1000000.01,   CHAPS",
        "5000000.00,   CHAPS"
    })
    @DisplayName("Should route to correct scheme based on payment amount")
    void shouldRouteToCorrectSchemeByAmount(String amount, String expectedScheme) throws Exception {
        Payment payment = Payment.builder()
                .amount(new BigDecimal(amount))
                .currency("GBP")
                .debtorAccountNumber("12345678")
                .debtorSortCode("200000")
                .creditorAccountNumber("87654321")
                .creditorSortCode("400000")
                .reference("Routing-Test")
                .build();
        // No scheme set — let router decide

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "IK-ROUTE-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheme").value(expectedScheme));
    }

    @ParameterizedTest(name = "Explicit scheme {0} should be honoured")
    @CsvSource({"FPS", "CHAPS", "BACS"})
    @DisplayName("Should honour explicitly set scheme regardless of amount")
    void shouldHonourExplicitScheme(String scheme) throws Exception {
        Payment payment = Payment.builder()
                .scheme(PaymentScheme.valueOf(scheme))
                .amount(new BigDecimal("100.00"))
                .currency("GBP")
                .debtorAccountNumber("12345678")
                .debtorSortCode("200000")
                .creditorAccountNumber("87654321")
                .creditorSortCode("400000")
                .reference("Explicit-Scheme-Test")
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", "IK-EXPLICIT-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheme").value(scheme));
    }
}
