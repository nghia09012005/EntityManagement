package com.viettelDigitalTalent.EntitiyManagement.enrichment;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel.MockVirusTotalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockVirusTotalServiceTest {

    private MockVirusTotalService service;

    @BeforeEach
    void setUp() {
        service = new MockVirusTotalService();
        service.init();  // manually call @PostConstruct
    }

    @Test
    void providerNameReturnsMockVirusTotal() {
        assertThat(service.providerName()).isEqualTo("MockVirusTotal");
    }

    @Test
    void lookupKnownMaliciousHashReturnsMalicious() {
        MalwareInfo result = service.lookup("44d88612fea8a8f36de82e1278abb02f");
        assertThat(result).isNotNull();
        assertThat(result.isMalicious()).isTrue();
        assertThat(result.getFamily()).isEqualTo("Trojan.Generic");
    }

    @Test
    void lookupKnownCleanHashReturnsClean() {
        MalwareInfo result = service.lookup("5d41402abc4b2a76b9719d911017c592");
        assertThat(result).isNotNull();
        assertThat(result.isMalicious()).isFalse();
        assertThat(result.getFamily()).isEqualTo("Clean");
    }

    @Test
    void lookupUnknownHashReturnsUnknown() {
        MalwareInfo result = service.lookup("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(result).isNotNull();
        assertThat(result.getVerdict()).isEqualTo("UNKNOWN");
        assertThat(result.isMalicious()).isFalse();
        assertThat(result.getProvider()).isEqualTo("MockVirusTotal");
    }

    @Test
    void lookupNullHashReturnsNull() {
        assertThat(service.lookup(null)).isNull();
    }

    @Test
    void lookupEmptyHashReturnsNull() {
        assertThat(service.lookup("")).isNull();
        assertThat(service.lookup("   ")).isNull();
    }

    @Test
    void lookupNormalizesUppercaseHash() {
        // hash stored lowercase in dataset — uppercase input should still match
        MalwareInfo result = service.lookup("44D88612FEA8A8F36DE82E1278ABB02F");
        assertThat(result).isNotNull();
        assertThat(result.isMalicious()).isTrue();
    }

    @Test
    void lookupSecondMaliciousHashEntry() {
        MalwareInfo result = service.lookup("e2fc714c4727ee9395f324cd2e7f331f");
        assertThat(result).isNotNull();
        assertThat(result.isMalicious()).isTrue();
        assertThat(result.getFamily()).isEqualTo("Adware.Bundle");
    }
}
