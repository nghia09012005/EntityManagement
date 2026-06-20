package com.viettelDigitalTalent.EntitiyManagement.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.windows.WindowsEventParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WindowsEventParserTest {

    private WindowsEventParser parser;

    @BeforeEach
    void setUp() {
        parser = new WindowsEventParser();
        ReflectionTestUtils.setField(parser, "objectMapper", new ObjectMapper());
    }

    @Test
    void parsesSuccessfulLogin() {
        String raw = "{\"user\":\"admin\",\"ip\":\"192.168.1.1\",\"is_success\":true,\"workstation\":\"WIN-PC01\"}";
        AuthenticationEvent event = parser.parse(raw);
        assertThat(event.getUsername()).isEqualTo("admin");
        assertThat(event.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(event.isSuccess()).isTrue();
        assertThat(event.getWorkstation()).isEqualTo("WIN-PC01");
    }

    @Test
    void parsesFailedLogin() {
        String raw = "{\"user\":\"attacker\",\"ip\":\"185.220.101.42\",\"is_success\":false}";
        AuthenticationEvent event = parser.parse(raw);
        assertThat(event.getUsername()).isEqualTo("attacker");
        assertThat(event.isSuccess()).isFalse();
    }

    @Test
    void handlesAlternativeFieldNames() {
        String raw = "{\"username\":\"nghia\",\"sourceIp\":\"10.0.0.1\",\"is_success\":true}";
        AuthenticationEvent event = parser.parse(raw);
        assertThat(event.getUsername()).isEqualTo("nghia");
    }

    @Test
    void setsAuthenticationCategory() {
        String raw = "{\"user\":\"admin\",\"ip\":\"1.2.3.4\",\"is_success\":true}";
        AuthenticationEvent event = parser.parse(raw);
        assertThat(event.getCategory()).isEqualTo("AUTHENTICATION");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() -> parser.parse("not-json"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lỗi khi parse");
    }
}
