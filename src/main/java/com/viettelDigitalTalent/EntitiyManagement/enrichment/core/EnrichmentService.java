package com.viettelDigitalTalent.EntitiyManagement.enrichment.core;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.GeoInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.geoip.GeoIpService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel.MockVirusTotalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
public class EnrichmentService {

    @Autowired
    private GeoIpService geoIpService;      // API/Library GeoIP
    @Autowired(required = false)
    private com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel.ThreatIntelService threatIntelService;
    @Autowired private RedisTemplate<String, Object> redis;

    public void enrich(BaseEvent event) {
        if (event instanceof AuthenticationEvent authenticationEvent) {
            enrichGeoIp(authenticationEvent);
        }

        if (event instanceof ProcessEvent processEvent) {
            enrichFileHash(processEvent);
        }

        event.setEnriched(true);
        logEnrichmentResult(event);
    }

    private void logEnrichmentResult(BaseEvent event) {
        if (event instanceof AuthenticationEvent authenticationEvent) {
            GeoInfo geo = (GeoInfo) authenticationEvent.getRawData().get("geo");
            log.info(
                    "[Enrichment done] type=AUTHENTICATION username={} ipAddress={} country={} city={} isoCode={} asn={} enriched={}",
                    authenticationEvent.getUsername(),
                    authenticationEvent.getIpAddress(),
                    geo != null ? geo.getCountry() : null,
                    geo != null ? geo.getCity() : null,
                    geo != null ? geo.getIsoCode() : null,
                    geo != null ? geo.getAsn() : null,
                    authenticationEvent.isEnriched()
            );
            return;
        }

        if (event instanceof ProcessEvent processEvent) {
            MalwareInfo malware = (MalwareInfo) processEvent.getRawData().get("malware");
                log.info(
                    "[Enrichment done] type=PROCESS provider={} fileHash={} verdict={} family={} malicious={} description={} enriched={}",
                    malware != null ? malware.getProvider() : "none",
                    processEvent.getFileHash(),
                    malware != null ? malware.getVerdict() : null,
                    malware != null ? malware.getFamily() : null,
                    malware != null && malware.isMalicious(),
                    malware != null ? malware.getDescription() : null,
                    processEvent.isEnriched()
                );
            return;
        }

        log.info("[Enrichment done] type={} enriched={}", event.getClass().getSimpleName(), event.isEnriched());
    }

    private void enrichGeoIp(AuthenticationEvent event) {
        String ipAddress = event.getIpAddress();
        log.info("[Ip address:] {}", ipAddress);

        if (ipAddress == null || ipAddress.isBlank()) {
            return;
        }

        String cacheKey = "ip:" + ipAddress;
        GeoInfo geo = (GeoInfo) redis.opsForValue().get(cacheKey);

        if (geo == null) {
            log.info("[Geo lookup start]");
            geo = geoIpService.lookup(ipAddress);

            if (geo != null) {
                log.info("[Geo after lookup] {} {}", geo.getCountry(), geo.getCity());
                redis.opsForValue().set(cacheKey, geo, Duration.ofDays(7));
            }
        }

        if (geo != null) {
            event.getRawData().put("geo", geo);
        }
    }

    private void enrichFileHash(ProcessEvent event) {
        String fileHash = event.getFileHash();
        if (fileHash == null || fileHash.isBlank()) {
            Object rawHash = event.getRawData().get("fileHash");
            if (rawHash == null) {
                rawHash = event.getRawData().get("sha256");
            }
            if (rawHash instanceof String hashValue) {
                fileHash = hashValue;
            }
        }

        if (fileHash == null || fileHash.isBlank()) {
            return;
        }

        String normalizedHash = fileHash.trim().toLowerCase();
        String cacheKey = "hash:" + normalizedHash;
        MalwareInfo malware = (MalwareInfo) redis.opsForValue().get(cacheKey);

        if (malware == null) {
            if (threatIntelService != null) {
                malware = threatIntelService.lookup(normalizedHash);
            }
            if (malware == null) {
                malware = new MalwareInfo();
                malware.setProvider("none");
                malware.setVerdict("UNKNOWN");
                malware.setMalicious(false);
                malware.setFileHash(normalizedHash);
                malware.setDescription("No threat intel available");
            }
            redis.opsForValue().set(cacheKey, malware, Duration.ofHours(6));
        }

        event.getRawData().put("malware", malware);
    }
}