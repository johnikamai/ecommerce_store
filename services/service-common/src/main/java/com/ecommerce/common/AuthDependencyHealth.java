package com.ecommerce.common;
import org.springframework.boot.actuate.health.*;
import org.springframework.web.client.RestTemplate;
/** Readiness includes the identity dependency required by protected requests. */
public class AuthDependencyHealth implements HealthIndicator {
    private final RestTemplate http;
    public AuthDependencyHealth(RestTemplate http){this.http=http;}
    public Health health(){try{var response=http.getForEntity("http://auth-service/actuator/health",String.class);return response.getStatusCode().is2xxSuccessful()?Health.up().build():Health.down().build();}catch(Exception unavailable){return Health.down().withDetail("dependency","auth-service").build();}}
}
