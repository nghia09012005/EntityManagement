package com.viettelDigitalTalent.EntitiyManagement.enrichment.core;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.GeoInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.geoip.GeoIpService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
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

        if (event instanceof NetworkEvent networkEvent) {
            enrichNetworkGeoIp(networkEvent);
        }

        if (event instanceof AlertEvent alertEvent) {
            enrichAlertGeoIp(alertEvent);
            enrichAlertFileHash(alertEvent);
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

        if (event instanceof NetworkEvent networkEvent) {
            GeoInfo srcGeo = (GeoInfo) networkEvent.getRawData().get("srcGeo");
            GeoInfo dstGeo = (GeoInfo) networkEvent.getRawData().get("dstGeo");
            log.info(
                "[Enrichment done] type=NETWORK srcIp={} srcCountry={} dstIp={} dstCountry={} dstDomain={} enriched={}",
                networkEvent.getSrcIp(),
                srcGeo != null ? srcGeo.getCountry() : null,
                networkEvent.getDstIp(),
                dstGeo != null ? dstGeo.getCountry() : null,
                networkEvent.getDstDomain(),
                networkEvent.isEnriched()
            );
            return;
        }

        if (event instanceof AlertEvent alertEvent) {
            GeoInfo geo = (GeoInfo) alertEvent.getRawData().get("geo");
            MalwareInfo malware = (MalwareInfo) alertEvent.getRawData().get("malware");
            log.info(
                "[Enrichment done] type=ALERT alertName={} severity={} targetIp={} country={} fileHash={} verdict={} malicious={} enriched={}",
                alertEvent.getAlertName(),
                alertEvent.getSeverity(),
                alertEvent.getTargetIp(),
                geo != null ? geo.getCountry() : null,
                alertEvent.getTargetFileHash(),
                malware != null ? malware.getVerdict() : null,
                malware != null && malware.isMalicious(),
                alertEvent.isEnriched()
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

        GeoInfo geo = lookupGeoWithCache(ipAddress);
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

    private void enrichNetworkGeoIp(NetworkEvent event) {
        if (event.getSrcIp() != null && !event.getSrcIp().isBlank()) {
            GeoInfo srcGeo = lookupGeoWithCache(event.getSrcIp());
            if (srcGeo != null) {
                event.getRawData().put("srcGeo", srcGeo);
            }
        }

        if (event.getDstIp() != null && !event.getDstIp().isBlank()) {
            GeoInfo dstGeo = lookupGeoWithCache(event.getDstIp());
            if (dstGeo != null) {
                event.getRawData().put("dstGeo", dstGeo);
            }
        }
    }

    private void enrichAlertGeoIp(AlertEvent event) {
        if (event.getTargetIp() == null || event.getTargetIp().isBlank()) {
            return;
        }
        GeoInfo geo = lookupGeoWithCache(event.getTargetIp());
        if (geo != null) {
            event.getRawData().put("geo", geo);
        }
    }

    private void enrichAlertFileHash(AlertEvent event) {
        if (event.getTargetFileHash() == null || event.getTargetFileHash().isBlank()) {
            return;
        }
        String normalizedHash = event.getTargetFileHash().trim().toLowerCase();
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

    private GeoInfo lookupGeoWithCache(String ip) {
        String cacheKey = "ip:" + ip;
        GeoInfo geo = (GeoInfo) redis.opsForValue().get(cacheKey);
        if (geo == null) {
            geo = geoIpService.lookup(ip);
            if (geo != null) {
                redis.opsForValue().set(cacheKey, geo, Duration.ofDays(7));
            }
        }
        return geo;
    }
}