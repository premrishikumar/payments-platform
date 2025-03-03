package com.payments.platform.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests.
 * Uses embedded Kafka — no external broker needed.
 * Each test class gets a fresh Spring context via @DirtiesContext.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
    partitions = 1,
    topics = {"payment.submitted", "payment.processed", "payment.failed"},
    brokerProperties = {"listeners=PLAINTEXT://localhost:9093", "port=9093"}
)
public abstract class BaseIntegrationTest {
    // Shared setup goes here
}
