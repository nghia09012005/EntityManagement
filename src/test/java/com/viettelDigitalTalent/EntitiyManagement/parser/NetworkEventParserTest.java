package com.viettelDigitalTalent.EntitiyManagement.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.network.NetworkEventParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkEventParserTest {

    private NetworkEventParser parser;

    @BeforeEach
    void setUp() {
        parser = new NetworkEventParser();
        ReflectionTestUtils.setField(parser, "objectMapper", new ObjectMapper());
    }

    @Test
    void parsesFullNetworkEvent() {
        String raw = "{\"srcIp\":\"192.168.1.100\",\"dstIp\":\"185.220.101.42\",\"dstPort\":443,\"dstDomain\":\"malware-c2.example.com\"}";
        NetworkEvent event = parser.parse(raw);
        assertThat(event.getSrcIp()).isEqualTo("192.168.1.100");
        assertThat(event.getDstIp()).isEqualTo("185.220.101.42");
        assertThat(event.getDstPort()).isEqualTo(443);
        assertThat(event.getDstDomain()).isEqualTo("malware-c2.example.com");
    }

    @Test
    void parsesEventWithoutDomain() {
        String raw = "{\"srcIp\":\"10.0.0.1\",\"dstIp\":\"8.8.8.8\",\"dstPort\":53}";
        NetworkEvent event = parser.parse(raw);
        assertThat(event.getSrcIp()).isEqualTo("10.0.0.1");
        assertThat(event.getDstPort()).isEqualTo(53);
        assertThat(event.getDstDomain()).isNull();
    }

    @Test
    void setsNetworkCategory() {
        String raw = "{\"srcIp\":\"1.1.1.1\",\"dstIp\":\"2.2.2.2\"}";
        NetworkEvent event = parser.parse(raw);
        assertThat(event.getCategory()).isEqualTo("NETWORK");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void storesFieldsInRawData() {
        String raw = "{\"srcIp\":\"10.0.0.5\",\"dstIp\":\"1.2.3.4\",\"dstPort\":80}";
        NetworkEvent event = parser.parse(raw);
        assertThat(event.getRawData()).containsEntry("srcIp", "10.0.0.5");
        assertThat(event.getRawData()).containsEntry("dstIp", "1.2.3.4");
    }

    @Test
    void handlesInvalidPortGracefully() {
        String raw = "{\"srcIp\":\"1.1.1.1\",\"dstIp\":\"2.2.2.2\",\"dstPort\":\"not-a-number\"}";
        NetworkEvent event = parser.parse(raw);
        assertThat(event.getDstPort()).isEqualTo(0);
    }
}
