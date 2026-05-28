package com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnMissingBean( value = com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel.ThreatIntelService.class )
public class MockVirusTotalService implements ThreatIntelService {

    private final Map<String, MalwareInfo> dataset = new HashMap<>();

    @PostConstruct
    public void init() {
        dataset.put("44d88612fea8a8f36de82e1278abb02f", createEntry(
                "44d88612fea8a8f36de82e1278abb02f",
                "Trojan.Generic",
                "EICAR-Test-File",
                true,
                "Known test signature used to validate malware scanning flows"
        ));

        dataset.put("e2fc714c4727ee9395f324cd2e7f331f", createEntry(
                "e2fc714c4727ee9395f324cd2e7f331f",
                "Adware.Bundle",
                "Potentially Unwanted Program",
                true,
                "Mock offline sample for demonstrating threat intel enrichment"
        ));

        dataset.put("5d41402abc4b2a76b9719d911017c592", createEntry(
                "5d41402abc4b2a76b9719d911017c592",
                "Clean",
                null,
                false,
                "Benign sample entry"
        ));
    }

    public MalwareInfo lookup(String fileHash) {
        String normalizedHash = normalize(fileHash);
        if (normalizedHash == null) {
            return null;
        }

        MalwareInfo found = dataset.get(normalizedHash);
        if (found != null) {
            return found;
        }

        MalwareInfo fallback = new MalwareInfo();
        fallback.setProvider("MockVirusTotal");
        fallback.setVerdict("UNKNOWN");
        fallback.setMalicious(false);
        fallback.setFileHash(normalizedHash);
        fallback.setDescription("Hash chưa có trong dataset offline");
        return fallback;
    }

    @Override
    public String providerName() {
        return "MockVirusTotal";
    }

    private MalwareInfo createEntry(String fileHash, String family, String verdict, boolean malicious, String description) {
        MalwareInfo info = new MalwareInfo();
        info.setProvider("MockVirusTotal");
        info.setVerdict(verdict);
        info.setFamily(family);
        info.setFileHash(fileHash);
        info.setMalicious(malicious);
        info.setDescription(description);
        return info;
    }

    private String normalize(String fileHash) {
        if (fileHash == null) {
            return null;
        }

        String normalized = fileHash.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }
}