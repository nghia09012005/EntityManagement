package com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel;

import org.springframework.stereotype.Service;

@Service
public class MockOtxService implements OtxService {

    private static final java.util.Map<String, int[]> KNOWN = java.util.Map.of(
            "185.220.101.1", new int[]{ 12, 80 },   // nhiều pulse, reputation xấu
            "45.33.32.156",  new int[]{ 5, 40 }
    );

    @Override
    public String providerName() { return "OTX-Mock"; }

    @Override
    public int[] check(String ip) {
        return KNOWN.getOrDefault(ip, new int[]{ 0, 0 });
    }
}
