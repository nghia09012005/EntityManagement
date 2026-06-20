package com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos;

import lombok.Data;
import java.io.Serializable;

@Data
public class IpIntelInfo implements Serializable {

    // WHOIS
    private String org;
    private String isp;
    private String asn;

    // AbuseIPDB
    private int abuseScore;     // 0–100
    private int totalReports;
    private String abuseProvider;

    // AlienVault OTX
    private int otxPulseCount;
    private double otxReputation;
    private String otxProvider;

    // Derived — tính sau khi merge 2 nguồn
    private boolean isMalicious;   // abuseScore > 25 || otxPulseCount > 0
    private String threatLevel;    // NONE / LOW / MEDIUM / HIGH / CRITICAL
}
