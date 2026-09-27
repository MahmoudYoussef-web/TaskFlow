package com.taskflow;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real Postgres + real Redis, two ways:
 *  - default: Testcontainers (CI, compatible Docker hosts);
 *  - override: TASKFLOW_PG_URL / TASKFLOW_REDIS_HOST / TASKFLOW_REDIS_PORT
 *    pointing at `docker compose up postgres redis` (used when the
 *    docker-java ↔ Docker Desktop handshake is broken, as on this host).
 * Either way the tests exercise genuine threads, locks, and databases — no mocks.
 */
public abstract class ContainersBase {

    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> redis;

    private static String pgUrl;
    private static String pgUser;
    private static String pgPass;
    private static String redisHost;
    private static int redisPort;
    private static boolean managed;

    @BeforeAll
    static void startInfra() {
        String envUrl = System.getenv("TASKFLOW_PG_URL");
        if (envUrl != null) {
            pgUrl = envUrl;
            pgUser = orDefault(System.getenv("TASKFLOW_PG_USER"), "taskflow");
            pgPass = orDefault(System.getenv("TASKFLOW_PG_PASS"), "taskflow");
            redisHost = orDefault(System.getenv("TASKFLOW_REDIS_HOST"), "localhost");
            redisPort = Integer.parseInt(orDefault(System.getenv("TASKFLOW_REDIS_PORT"), "6379"));
            managed = false;
            return;
        }
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
        redis.start();
        pgUrl = postgres.getJdbcUrl();
        pgUser = postgres.getUsername();
        pgPass = postgres.getPassword();
        redisHost = redis.getHost();
        redisPort = redis.getMappedPort(6379);
        managed = true;
    }

    @AfterAll
    static void stopInfra() {
        if (managed) {
            if (postgres != null) postgres.stop();
            if (redis != null) redis.stop();
        }
    }

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> pgUrl);
        r.add("spring.datasource.username", () -> pgUser);
        r.add("spring.datasource.password", () -> pgPass);
        r.add("spring.data.redis.host", () -> redisHost);
        r.add("spring.data.redis.port", () -> redisPort);
        r.add("taskflow.scheduler.enabled", () -> "false");
    }

    private static String orDefault(String v, String d) {
        return v != null && !v.isBlank() ? v : d;
    }
}
