package com.viettelDigitalTalent.EntitiyManagement.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ConfigCoverageTest {

    @Test
    void asyncConfig_createsExecutors() {
        AsyncConfig config = new AsyncConfig();

        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) config.taskExecutor();
        ThreadPoolTaskExecutor enrichmentExecutor = (ThreadPoolTaskExecutor) config.enrichmentExecutor();

        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("SOC-Async-");
        assertThat(enrichmentExecutor.getThreadNamePrefix()).isEqualTo("SOC-Enrich-");
        taskExecutor.shutdown();
        enrichmentExecutor.shutdown();
    }

    @Test
    void simpleConfigBeans_areCreated() {
        assertThat(new HttpClientConfig().restTemplate(new RestTemplateBuilder())).isNotNull();
        assertThat(new SecurityConfig(mock(com.viettelDigitalTalent.EntitiyManagement.management.filter.JwtAuthFilter.class))
                .passwordEncoder().encode("secret")).isNotBlank();
        assertThat(new SchedulingConfig()).isNotNull();

        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        assertThat(new RedisConfig().redisTemplate(factory).getConnectionFactory()).isSameAs(factory);
    }

    @Test
    void minioConfig_buildsClient() {
        MinioConfig config = new MinioConfig();
        ReflectionTestUtils.setField(config, "url", "http://localhost:9000");
        ReflectionTestUtils.setField(config, "accessKey", "admin");
        ReflectionTestUtils.setField(config, "secretKey", "password123");

        MinioClient client = config.minioClient();

        assertThat(client).isNotNull();
    }

    @Test
    void neo4jSchemaInitializer_runsIndexesAndDropsConstraints() {
        Neo4jClient neo4jClient = mock(Neo4jClient.class, Answers.RETURNS_DEEP_STUBS);
        when(neo4jClient.query("SHOW CONSTRAINTS YIELD name, type WHERE type = 'UNIQUENESS'")
                .fetch().all()).thenReturn(List.of(Map.of("name", "uniq_user")));

        new Neo4jSchemaInitializer(neo4jClient).initSchema();

        verify(neo4jClient, atLeast(12)).query(anyString());
    }

    @Test
    void neo4jSchemaInitializer_continuesWhenShowConstraintsFails() {
        Neo4jClient neo4jClient = mock(Neo4jClient.class, Answers.RETURNS_DEEP_STUBS);
        when(neo4jClient.query("SHOW CONSTRAINTS YIELD name, type WHERE type = 'UNIQUENESS'")
                .fetch().all()).thenThrow(new RuntimeException("neo4j unavailable"));

        new Neo4jSchemaInitializer(neo4jClient).initSchema();

        verify(neo4jClient, atLeast(11)).query(anyString());
    }
}
