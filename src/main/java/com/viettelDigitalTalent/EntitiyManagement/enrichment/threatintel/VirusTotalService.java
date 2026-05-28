package com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@ConditionalOnProperty(prefix = "virustotal", name = "api-key")
@Primary
@Slf4j
public class VirusTotalService implements ThreatIntelService {

    private final String apiKey;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public VirusTotalService(@Value("${virustotal.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @PostConstruct
    public void started() {
        log.info("VirusTotalService initialized using configured API key (first 6 chars): {}", apiKey == null ? "<none>" : (apiKey.length() > 6 ? apiKey.substring(0,6) + "..." : apiKey));
    }

    @Override
    public String providerName() {
        return "VirusTotal";
    }

    @Override
    public MalwareInfo lookup(String fileHash) {
        try {
            String normalized = fileHash == null ? "" : fileHash.trim().toLowerCase();
            if (normalized.isEmpty()) return null;

            String url = "https://www.virustotal.com/api/v3/files/" + normalized;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("x-apikey", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

            MalwareInfo info = new MalwareInfo();
            info.setProvider("VirusTotal");
            info.setFileHash(normalized);

            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                JsonNode stats = root.path("data").path("attributes").path("last_analysis_stats");
                int malicious = stats.path("malicious").asInt(0);
                int suspicious = stats.path("suspicious").asInt(0);
                info.setMalicious(malicious > 0 || suspicious > 0);
                info.setVerdict(info.isMalicious() ? "MALICIOUS" : "CLEAN");

                // try to extract some vendor family or tags
                JsonNode vendors = root.path("data").path("attributes").path("last_analysis_results");
                String family = null;
                if (vendors.isObject()) {
                    for (JsonNode v : vendors) {
                        JsonNode result = v.path("result");
                        if (!result.isMissingNode() && !result.isNull()) {
                            family = result.asText();
                            break;
                        }
                    }
                }
                info.setFamily(family);
                info.setDescription("VirusTotal lookup returned status " + resp.statusCode());
                return info;
            } else if (resp.statusCode() == 404) {
                info.setMalicious(false);
                info.setVerdict("NOT_FOUND");
                info.setDescription("Hash not present in VirusTotal");
                return info;
            } else {
                info.setMalicious(false);
                info.setVerdict("UNKNOWN");
                info.setDescription("VirusTotal HTTP " + resp.statusCode());
                return info;
            }

        } catch (Exception e) {
            MalwareInfo fallback = new MalwareInfo();
            fallback.setProvider("VirusTotal");
            fallback.setFileHash(fileHash);
            fallback.setVerdict("ERROR");
            fallback.setMalicious(false);
            fallback.setDescription("Error calling VirusTotal: " + e.getMessage());
            return fallback;
        }
    }
}
