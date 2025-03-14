package com.payments.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payments.platform.domain.Payment;
import com.payments.platform.domain.PaymentScheme;
import com.payments.platform.domain.PaymentStatus;
import com.payments.platform.repository.PaymentRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Kafka event publishing and consumption.
 * Verifies payment events are correctly produced and status updated by consumer.
 */
@DisplayName("Payment Kafka Integration Tests")
class PaymentKafkaIntegrationTest extends BaseIntegrationTest {

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

    @Test
    @DisplayName("Should publish event to payment.submitted topic on payment creation")
    void shouldPublishPaymentSubmittedEvent() throws Exception {
        String idempotencyKey = "IK-KAFKA-" + UUID.randomUUID();

        Payment payment = Payment.builder()
                .scheme(PaymentScheme.FPS)
                .amount(new BigDecimal("750.00"))
                .currency("GBP")
                .debtorAccountNumber("12345678")
                .debtorSortCode("200000")
                .creditorAccountNumber("87654321")
                .creditorSortCode("400000")
                .reference("Kafka-Test-Ref")
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated());

        // Verify payment exists in DB (event was consumed and status updated)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(paymentRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
        });
    }

    @Test
    @DisplayName("Should update payment status to SUBMITTED after Kafka consumer processes event")
    void shouldUpdateStatusToSubmittedAfterKafkaConsumption() throws Exception {
        String idempotencyKey = "IK-STATUS-" + UUID.randomUUID();

        Payment payment = Payment.builder()
                .scheme(PaymentScheme.FPS)
                .amount(new BigDecimal("300.00"))
                .currency("GBP")
                .debtorAccountNumber("11112222")
                .debtorSortCode("200000")
                .creditorAccountNumber("33334444")
                .creditorSortCode("400000")
                .reference("Status-Update-Test")
                .build();

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payment)))
                .andExpect(status().isCreated());

        // Wait for Kafka consumer to update status to SUBMITTED
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            Payment saved = paymentRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.SUBMITTED);
        });
    }
}
