package com.payments.platform.integration;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for integration tests.
 * Uses embedded Kafka — no external broker needed.
 * Spring auto-assigns broker address via ${spring.embedded.kafka.brokers}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
    partitions = 1,
    topics = {
        "payment.submitted",
        "payment.processed",
        "payment.failed"
    }
)
public abstract class BaseIntegrationTest {
}
