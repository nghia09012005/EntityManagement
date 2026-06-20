package com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel;

import org.springframework.stereotype.Service;

@Service
public class MockAbuseIpDbService implements AbuseIpDbService {

    // Vài IP nổi tiếng xấu để demo
    private static final java.util.Map<String, int[]> KNOWN = java.util.Map.of(
            "185.220.101.1",  new int[]{ 100, 450 },  // Tor exit node
            "45.33.32.156",   new int[]{ 85,  120 },  // scanner
            "198.20.69.74",   new int[]{ 72,  89 }    // shodan scanner
    );

    @Override
    public String providerName() { return "AbuseIPDB-Mock"; }

    @Override
    public int[] check(String ip) {
        return KNOWN.getOrDefault(ip, new int[]{ 0, 0 });
    }
}
