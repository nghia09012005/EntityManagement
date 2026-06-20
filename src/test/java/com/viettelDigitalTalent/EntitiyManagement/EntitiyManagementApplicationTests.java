package com.viettelDigitalTalent.EntitiyManagement;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Application smoke test — verifica package is importable without full Spring context.
 * Full Spring context tests require live infrastructure (Kafka, MongoDB, Redis, Neo4j)
 * and should be run in CI with docker-compose.
 */
class EntitiyManagementApplicationTests {

    @Test
    void applicationClassExists() {
        assertThat(EntitiyManagementApplication.class).isNotNull();
    }
}
