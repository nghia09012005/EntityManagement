package com.viettelDigitalTalent.EntitiyManagement.enrichment;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.core.EnrichmentService;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.GeoInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.geoip.GeoIpService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrichmentServiceTest {

    private EnrichmentService enrichmentService;

    @Mock private GeoIpService geoIpService;
    @Mock private RedisTemplate<String, Object> redis;
    @Mock private ValueOperations<String, Object> valueOps;

    @BeforeEach
    void setUp() {
        enrichmentService = new EnrichmentService();
        ReflectionTestUtils.setField(enrichmentService, "geoIpService", geoIpService);
        ReflectionTestUtils.setField(enrichmentService, "redis", redis);
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void enrichAuthEventWithGeoIp() {
        GeoInfo geo = new GeoInfo();
        geo.setCountry("Vietnam");
        geo.setCity("Hanoi");
        when(valueOps.get(anyString())).thenReturn(null);
        when(geoIpService.lookup("192.168.1.1")).thenReturn(geo);

        AuthenticationEvent event = new AuthenticationEvent();
        event.setIpAddress("192.168.1.1");
        enrichmentService.enrich(event);

        assertThat(event.isEnriched()).isTrue();
        assertThat(event.getRawData()).containsKey("geo");
        GeoInfo result = (GeoInfo) event.getRawData().get("geo");
        assertThat(result.getCountry()).isEqualTo("Vietnam");
    }

    @Test
    void enrichAuthEventSkipsWhenIpNull() {
        AuthenticationEvent event = new AuthenticationEvent();
        event.setIpAddress(null);
        enrichmentService.enrich(event);
        assertThat(event.isEnriched()).isTrue();
        assertThat(event.getRawData()).doesNotContainKey("geo");
        verifyNoInteractions(geoIpService);
    }

    @Test
    void enrichProcessEventWithMalwareInfo() {
        MalwareInfo malware = new MalwareInfo();
        malware.setVerdict("MALICIOUS");
        malware.setMalicious(true);
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.get("hash:abc123")).thenReturn(null);

        ProcessEvent event = new ProcessEvent();
        event.setFileHash("abc123");
        enrichmentService.enrich(event);

        assertThat(event.isEnriched()).isTrue();
        assertThat(event.getRawData()).containsKey("malware");
    }

    @Test
    void enrichProcessEventUsesRedisCache() {
        MalwareInfo cached = new MalwareInfo();
        cached.setVerdict("CLEAN");
        when(valueOps.get("hash:cached123")).thenReturn(cached);

        ProcessEvent event = new ProcessEvent();
        event.setFileHash("cached123");
        enrichmentService.enrich(event);

        assertThat(event.getRawData()).containsKey("malware");
        MalwareInfo result = (MalwareInfo) event.getRawData().get("malware");
        assertThat(result.getVerdict()).isEqualTo("CLEAN");
    }

    @Test
    void enrichNetworkEventWithSrcAndDstGeo() {
        GeoInfo srcGeo = new GeoInfo();
        srcGeo.setCountry("Vietnam");
        GeoInfo dstGeo = new GeoInfo();
        dstGeo.setCountry("Russia");

        when(valueOps.get(anyString())).thenReturn(null);
        when(geoIpService.lookup("10.0.0.1")).thenReturn(srcGeo);
        when(geoIpService.lookup("185.220.101.42")).thenReturn(dstGeo);

        NetworkEvent event = new NetworkEvent();
        event.setSrcIp("10.0.0.1");
        event.setDstIp("185.220.101.42");
        enrichmentService.enrich(event);

        assertThat(event.isEnriched()).isTrue();
        assertThat(event.getRawData()).containsKey("srcGeo");
        assertThat(event.getRawData()).containsKey("dstGeo");
    }

    @Test
    void enrichAlertEventWithGeoIpAndNoFileHash() {
        GeoInfo geo = new GeoInfo();
        geo.setCountry("China");
        when(valueOps.get(anyString())).thenReturn(null);
        when(geoIpService.lookup("1.2.3.4")).thenReturn(geo);

        AlertEvent event = new AlertEvent();
        event.setTargetIp("1.2.3.4");
        enrichmentService.enrich(event);

        assertThat(event.isEnriched()).isTrue();
        assertThat(event.getRawData()).containsKey("geo");
    }
}
