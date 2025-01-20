package com.payments.platform.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("paymentsHealth")
public class ActuatorConfig implements HealthIndicator {

    @Override
    public Health health() {
        // Extend with real checks: Kafka broker, DB connectivity, scheme connectivity
        return Health.up()
                .withDetail("service", "payments-platform")
                .withDetail("schemes", "FPS, CHAPS, BACS")
                .build();
    }
}
