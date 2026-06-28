package com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.IpIntelInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class IpIntelligenceService {

    @Autowired private WhoisService whoisService;
    @Autowired private AbuseIpDbService abuseIpDbService;
    @Autowired private OtxService otxService;
    @Autowired private RedisTemplate<String, Object> redis;

    private static final String CACHE_PREFIX = "ipintel:";
    private static final Duration CACHE_TTL  = Duration.ofHours(1);

    public IpIntelInfo enrich(String ip) {
        if (ip == null || ip.isBlank()) return null;

        String cacheKey = CACHE_PREFIX + ip;
        IpIntelInfo cached = (IpIntelInfo) redis.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        // Gọi 3 nguồn song song — mỗi future tự handle lỗi để tránh làm pipeline chết
        CompletableFuture<String[]> whoisFuture = CompletableFuture
                .supplyAsync(() -> whoisService.lookup(ip))
                .exceptionally(e -> { log.warn("[IpIntel] WHOIS failed for {}: {}", ip, e.getMessage()); return new String[]{"unknown", "unknown"}; });
        CompletableFuture<int[]> abuseFuture = CompletableFuture
                .supplyAsync(() -> abuseIpDbService.check(ip))
                .exceptionally(e -> { log.warn("[IpIntel] AbuseIPDB failed for {}: {}", ip, e.getMessage()); return new int[]{0, 0}; });
        CompletableFuture<int[]> otxFuture = CompletableFuture
                .supplyAsync(() -> otxService.check(ip))
                .exceptionally(e -> { log.warn("[IpIntel] OTX failed for {}: {}", ip, e.getMessage()); return new int[]{0, 0}; });

        CompletableFuture.allOf(whoisFuture, abuseFuture, otxFuture).join();

        String[] whois = whoisFuture.join();
        int[]    abuse = abuseFuture.join();
        int[]    otx   = otxFuture.join();

        IpIntelInfo info = new IpIntelInfo();

        // WHOIS
        info.setOrg(whois[0]);
        info.setAsn(whois[1]);

        // AbuseIPDB
        info.setAbuseScore(abuse[0]);
        info.setTotalReports(abuse[1]);
        info.setAbuseProvider(abuseIpDbService.providerName());

        // OTX
        info.setOtxPulseCount(otx[0]);
        info.setOtxReputation(otx[1] / 10.0);
        info.setOtxProvider(otxService.providerName());

        // Derived threat level
        info.setMalicious(abuse[0] > 25 || otx[0] > 0);
        info.setThreatLevel(calcThreatLevel(abuse[0], otx[0]));

        log.info("[IpIntel] ip={} abuse={} otxPulses={} malicious={} level={}",
                ip, abuse[0], otx[0], info.isMalicious(), info.getThreatLevel());

        redis.opsForValue().set(cacheKey, info, CACHE_TTL);
        return info;
    }

    private String calcThreatLevel(int abuseScore, int otxPulses) {
        if (abuseScore >= 75 || otxPulses >= 5)  return "CRITICAL";
        if (abuseScore >= 50 || otxPulses >= 2)  return "HIGH";
        if (abuseScore >= 25 || otxPulses >= 1)  return "MEDIUM";
        if (abuseScore >  0)                     return "LOW";
        return "NONE";
    }
}
