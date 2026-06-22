package com.viettelDigitalTalent.EntitiyManagement.enrichment;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.IpIntelInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel.AbuseIpDbService;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel.IpIntelligenceService;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel.MockAbuseIpDbService;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel.MockOtxService;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel.OtxService;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel.WhoisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpIntelligenceServiceTest {

    private IpIntelligenceService service;

    @Mock WhoisService whoisService;
    @Mock AbuseIpDbService abuseIpDbService;
    @Mock OtxService otxService;
    @Mock RedisTemplate<String, Object> redis;
    @Mock ValueOperations<String, Object> valueOps;

    @BeforeEach
    void setUp() {
        service = new IpIntelligenceService();
        ReflectionTestUtils.setField(service, "whoisService",     whoisService);
        ReflectionTestUtils.setField(service, "abuseIpDbService", abuseIpDbService);
        ReflectionTestUtils.setField(service, "otxService",       otxService);
        ReflectionTestUtils.setField(service, "redis",            redis);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        lenient().when(abuseIpDbService.providerName()).thenReturn("AbuseIPDB");
        lenient().when(otxService.providerName()).thenReturn("OTX");
    }

    @Test
    void returnsCachedValueWithoutCallingApis() {
        IpIntelInfo cached = new IpIntelInfo();
        cached.setThreatLevel("NONE");
        when(valueOps.get("ipintel:1.2.3.4")).thenReturn(cached);

        IpIntelInfo result = service.enrich("1.2.3.4");

        assertThat(result).isEqualTo(cached);
        verifyNoInteractions(whoisService, abuseIpDbService, otxService);
    }

    @Test
    void callsAllThreeSourcesAndBuildsInfo() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(whoisService.lookup("8.8.8.8")).thenReturn(new String[]{"Google LLC", "AS15169"});
        when(abuseIpDbService.check("8.8.8.8")).thenReturn(new int[]{0, 0});
        when(otxService.check("8.8.8.8")).thenReturn(new int[]{0, 0});

        IpIntelInfo result = service.enrich("8.8.8.8");

        assertThat(result.getOrg()).isEqualTo("Google LLC");
        assertThat(result.getAsn()).isEqualTo("AS15169");
        assertThat(result.getThreatLevel()).isEqualTo("NONE");
        assertThat(result.isMalicious()).isFalse();
        verify(valueOps).set(eq("ipintel:8.8.8.8"), eq(result), any(Duration.class));
    }

    @Test
    void highAbuseScoreSetsCriticalThreatLevel() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(whoisService.lookup(anyString())).thenReturn(new String[]{"Bad Actor", "AS999"});
        when(abuseIpDbService.check(anyString())).thenReturn(new int[]{80, 200});
        when(otxService.check(anyString())).thenReturn(new int[]{0, 0});

        IpIntelInfo result = service.enrich("185.220.101.1");

        assertThat(result.getThreatLevel()).isEqualTo("CRITICAL");
        assertThat(result.isMalicious()).isTrue();
    }

    @Test
    void otxPulsesGe5SetsCriticalThreatLevel() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(whoisService.lookup(anyString())).thenReturn(new String[]{"X", "AS1"});
        when(abuseIpDbService.check(anyString())).thenReturn(new int[]{0, 0});
        when(otxService.check(anyString())).thenReturn(new int[]{5, 80});

        IpIntelInfo result = service.enrich("10.10.10.10");

        assertThat(result.getThreatLevel()).isEqualTo("CRITICAL");
    }

    @Test
    void abuseScore50SetsHighThreatLevel() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(whoisService.lookup(anyString())).thenReturn(new String[]{"X", "AS1"});
        when(abuseIpDbService.check(anyString())).thenReturn(new int[]{50, 10});
        when(otxService.check(anyString())).thenReturn(new int[]{0, 0});

        assertThat(service.enrich("1.1.1.1").getThreatLevel()).isEqualTo("HIGH");
    }

    @Test
    void otxPulses2SetsHighThreatLevel() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(whoisService.lookup(anyString())).thenReturn(new String[]{"X", "AS1"});
        when(abuseIpDbService.check(anyString())).thenReturn(new int[]{0, 0});
        when(otxService.check(anyString())).thenReturn(new int[]{2, 20});

        assertThat(service.enrich("2.2.2.2").getThreatLevel()).isEqualTo("HIGH");
    }

    @Test
    void abuseScore25SetsMediumThreatLevel() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(whoisService.lookup(anyString())).thenReturn(new String[]{"X", "AS1"});
        when(abuseIpDbService.check(anyString())).thenReturn(new int[]{25, 3});
        when(otxService.check(anyString())).thenReturn(new int[]{0, 0});

        assertThat(service.enrich("3.3.3.3").getThreatLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void otxPulses1SetsMediumThreatLevel() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(whoisService.lookup(anyString())).thenReturn(new String[]{"X", "AS1"});
        when(abuseIpDbService.check(anyString())).thenReturn(new int[]{0, 0});
        when(otxService.check(anyString())).thenReturn(new int[]{1, 10});

        assertThat(service.enrich("4.4.4.4").getThreatLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void lowAbuseScoreSetsLowThreatLevel() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(whoisService.lookup(anyString())).thenReturn(new String[]{"X", "AS1"});
        when(abuseIpDbService.check(anyString())).thenReturn(new int[]{5, 1});
        when(otxService.check(anyString())).thenReturn(new int[]{0, 0});

        assertThat(service.enrich("5.5.5.5").getThreatLevel()).isEqualTo("LOW");
    }

    @Test
    void returnsNullForNullIp() {
        assertThat(service.enrich(null)).isNull();
    }

    @Test
    void returnsNullForBlankIp() {
        assertThat(service.enrich("   ")).isNull();
        assertThat(service.enrich("")).isNull();
    }

    @Test
    void otxReputationIsScaledCorrectly() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(whoisService.lookup(anyString())).thenReturn(new String[]{"X", "AS1"});
        when(abuseIpDbService.check(anyString())).thenReturn(new int[]{0, 0});
        when(otxService.check(anyString())).thenReturn(new int[]{0, 30});

        IpIntelInfo result = service.enrich("6.6.6.6");
        assertThat(result.getOtxReputation()).isEqualTo(3.0);
    }

    // ── Mock implementations (unit tests without network) ────────────────────

    @Test
    void mockAbuseIpDbKnownBadIpReturnsHighScore() {
        MockAbuseIpDbService mockSvc = new MockAbuseIpDbService();
        assertThat(mockSvc.check("185.220.101.1")[0]).isGreaterThan(50);
        assertThat(mockSvc.providerName()).isEqualTo("AbuseIPDB-Mock");
    }

    @Test
    void mockAbuseIpDbUnknownIpReturnsZero() {
        MockAbuseIpDbService mockSvc = new MockAbuseIpDbService();
        assertThat(mockSvc.check("8.8.8.8")).isEqualTo(new int[]{0, 0});
    }

    @Test
    void mockOtxKnownBadIpReturnsPulseCount() {
        MockOtxService mockSvc = new MockOtxService();
        assertThat(mockSvc.check("185.220.101.1")[0]).isGreaterThan(0);
        assertThat(mockSvc.providerName()).isEqualTo("OTX-Mock");
    }

    @Test
    void mockOtxCleanIpReturnsZero() {
        MockOtxService mockSvc = new MockOtxService();
        assertThat(mockSvc.check("8.8.8.8")).isEqualTo(new int[]{0, 0});
    }
}
