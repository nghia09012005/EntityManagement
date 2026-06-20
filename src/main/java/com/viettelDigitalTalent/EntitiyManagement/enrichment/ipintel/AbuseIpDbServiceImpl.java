package com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@ConditionalOnProperty(prefix = "abuseipdb", name = "api-key")
@Primary
@Slf4j
public class AbuseIpDbServiceImpl implements AbuseIpDbService {

    private final String apiKey;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AbuseIpDbServiceImpl(@Value("${abuseipdb.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("AbuseIpDbService initialized (key: {}...)", apiKey.length() > 6 ? apiKey.substring(0, 6) : apiKey);
    }

    @Override
    public String providerName() { return "AbuseIPDB"; }

    @Override
    public int[] check(String ip) {
        try {
            String url = "https://api.abuseipdb.com/api/v2/check?ipAddress=" + ip + "&maxAgeInDays=90";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("Key", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode data = mapper.readTree(resp.body()).path("data");
                int score   = data.path("abuseConfidenceScore").asInt(0);
                int reports = data.path("totalReports").asInt(0);
                return new int[]{ score, reports };
            }
            log.warn("[AbuseIPDB] HTTP {} for ip={}", resp.statusCode(), ip);
        } catch (Exception e) {
            log.warn("[AbuseIPDB] Error for ip={}: {}", ip, e.getMessage());
        }
        return new int[]{ 0, 0 };
    }
}
