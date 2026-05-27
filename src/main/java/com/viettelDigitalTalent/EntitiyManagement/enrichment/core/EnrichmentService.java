package com.viettelDigitalTalent.EntitiyManagement.enrichment.core;

import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EnrichmentService {

    @Autowired
    private GeoIpService geoIpService;      // API/Library GeoIP
    @Autowired private MalwareService malwareService;  // API/Dataset Malware
    @Autowired private RedisTemplate<String, Object> redis;

    public void enrichEvent(BaseEvent event) {
        // 1. Enrich IP (GeoIP)
        if (event.getIpAddress() != null) {
            String cacheKey = "ip:" + event.getIpAddress();
            GeoInfo geo = (GeoInfo) redis.opsForValue().get(cacheKey);
            if (geo == null) {
                geo = geoIpService.lookup(event.getIpAddress()); // Gọi MaxMind
                redis.opsForValue().set(cacheKey, geo, Duration.ofDays(7)); // TTL 7 ngày
            }
            event.getMetadata().put("geo", geo);
        }

        // 2. Enrich File Hash
        if (event.getFileHash() != null) {
            String cacheKey = "hash:" + event.getFileHash();
            MalwareInfo malware = (MalwareInfo) redis.opsForValue().get(cacheKey);
            if (malware == null) {
                malware = malwareService.lookup(event.getFileHash()); // Mock VirusTotal
                redis.opsForValue().set(cacheKey, malware, Duration.ofHours(1)); // TTL 1 giờ
            }
            event.getMetadata().put("malware", malware);
        }
    }
}