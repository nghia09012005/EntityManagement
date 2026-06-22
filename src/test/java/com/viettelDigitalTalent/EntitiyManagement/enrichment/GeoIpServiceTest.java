package com.viettelDigitalTalent.EntitiyManagement.enrichment;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.GeoInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.geoip.GeoIpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GeoIpService integration-style unit tests.
 * If MaxMind .mmdb files are present in classpath, real lookups are performed.
 * If absent, null-reader code paths are verified instead.
 * Either way, the service must not throw.
 */
class GeoIpServiceTest {

    private GeoIpService service;

    @BeforeEach
    void setUp() {
        service = new GeoIpService();
        service.init();
    }

    @Test
    void lookupPublicIpDoesNotThrow() {
        // Either returns GeoInfo (db present) or null (db absent or IP not in db) — never throws
        service.lookup("8.8.8.8");
    }

    @Test
    void lookupAnotherPublicIpDoesNotThrow() {
        service.lookup("1.1.1.1");
    }

    @Test
    void lookupReturnsNullForInvalidHostname() {
        // InetAddress.getByName fails → caught → returns null
        GeoInfo result = service.lookup("not-a-valid-hostname-##.invalid");
        assertThat(result).isNull();
    }

    @Test
    void lookupLoopbackDoesNotThrow() {
        service.lookup("127.0.0.1");
    }

    @Test
    void lookupIpv6DoesNotThrow() {
        service.lookup("::1");
    }

    @Test
    void lookupNullDoesNotThrow() {
        // InetAddress.getByName(null) resolves to loopback — should not throw
        service.lookup(null);
    }

    @Test
    void cleanupDoesNotThrow() {
        service.cleanup();
    }

    @Test
    void secondInitDoesNotThrow() {
        service.init(); // calling init() twice should be safe
    }

    @Test
    void lookupMultiplePublicIps() {
        String[] ips = { "208.67.222.222", "9.9.9.9", "185.220.101.1", "185.199.108.153" };
        for (String ip : ips) {
            service.lookup(ip); // must not throw regardless of db presence
        }
    }
}
