package com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel;

public interface AbuseIpDbService {
    /** Trả về [abuseScore 0-100, totalReports] */
    int[] check(String ip);
    String providerName();
}
