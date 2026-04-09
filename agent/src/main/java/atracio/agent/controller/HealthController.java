package atracio.agent.controller;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class HealthController implements HealthIndicator{
    @Override
    public Health health() {
        boolean isHealthy = false; // your custom logic
        if (!isHealthy) {
            return Health.down().withDetail("Error", "Service unreachable").build();
        }
        return Health.up().build();
    }
}