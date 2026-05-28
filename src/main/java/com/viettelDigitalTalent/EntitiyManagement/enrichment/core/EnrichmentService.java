package com.viettelDigitalTalent.EntitiyManagement.enrichment.core;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.GeoInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.geoip.GeoIpService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
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
//    @Autowired private MalwareService malwareService;  // API/Dataset Malware
    @Autowired private RedisTemplate<String, Object> redis;

    public void enrichEvent(AuthenticationEvent event) {
        log.info("[Ip address:] "+event.getIpAddress());

        // 1. Enrich IP (GeoIP)
        if (event.getIpAddress() != null) {
            String cacheKey = "ip:" + event.getIpAddress();

            GeoInfo geo = (GeoInfo) redis.opsForValue().get(cacheKey);

            if (geo == null) {
                log.info("[Geo lookup start]");
                geo = geoIpService.lookup(event.getIpAddress()); // Gọi MaxMind

                log.info("[Geo after lookup] "+geo.getCountry()+geo.getCity());

                redis.opsForValue().set(cacheKey, geo, Duration.ofDays(7)); // TTL 7 ngày
            }
            event.getRawData().put("geo", geo);
        }

//        // 2. Enrich File Hash
//        if (event.getFileHash() != null) {
//            String cacheKey = "hash:" + event.getFileHash();
//            MalwareInfo malware = (MalwareInfo) redis.opsForValue().get(cacheKey);
//            if (malware == null) {
//                malware = malwareService.lookup(event.getFileHash()); // Mock VirusTotal
//                redis.opsForValue().set(cacheKey, malware, Duration.ofHours(1)); // TTL 1 giờ
//            }
//            event.getMetadata().put("malware", malware);
//        }


    }
}