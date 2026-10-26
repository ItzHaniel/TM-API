package com.tmapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TMAPI — RESTful Task Management API
 *
 * Features:
 *  ✓ Stateless JWT authentication with Spring Security RBAC
 *  ✓ Multi-tenant workspace isolation
 *  ✓ Redis caching layer (P99 < 30ms read latency goal)
 *  ✓ JPA Specifications for composable task filtering
 *  ✓ Optimistic locking (@Version) for concurrent state transitions
 *  ✓ Async deadline notification pipeline (Spring TaskExecutors)
 *  ✓ Flyway database migrations
 */
@SpringBootApplication
@EnableRetry
@EnableScheduling
public class TmapiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TmapiApplication.class, args);
    }
}
