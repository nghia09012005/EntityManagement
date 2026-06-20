package com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel;

public interface OtxService {
    /** Trả về [pulseCount, reputation*10 as int] */
    int[] check(String ip);
    String providerName();
}
