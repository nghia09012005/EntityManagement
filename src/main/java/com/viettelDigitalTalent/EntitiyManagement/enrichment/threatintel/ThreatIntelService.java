package com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;

public interface ThreatIntelService {
    MalwareInfo lookup(String fileHash);
    String providerName();
}
