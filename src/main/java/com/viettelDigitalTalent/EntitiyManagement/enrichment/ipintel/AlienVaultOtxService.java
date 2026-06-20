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
@ConditionalOnProperty(prefix = "otx", name = "api-key")
@Primary
@Slf4j
public class AlienVaultOtxService implements OtxService {

    private final String apiKey;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public AlienVaultOtxService(@Value("${otx.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @PostConstruct
    public void init() {
        log.info("AlienVault OTX initialized (key: {}...)", apiKey.length() > 6 ? apiKey.substring(0, 6) : apiKey);
    }

    @Override
    public String providerName() { return "AlienVault-OTX"; }

    @Override
    public int[] check(String ip) {
        try {
            String url = "https://otx.alienvault.com/api/v1/indicators/IPv4/" + ip + "/general";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("X-OTX-API-KEY", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root       = mapper.readTree(resp.body());
                int pulseCount      = root.path("pulse_info").path("count").asInt(0);
                double reputation   = root.path("reputation").asDouble(0);
                return new int[]{ pulseCount, (int)(reputation * 10) };
            }
            log.warn("[OTX] HTTP {} for ip={}", resp.statusCode(), ip);
        } catch (Exception e) {
            log.warn("[OTX] Error for ip={}: {}", ip, e.getMessage());
        }
        return new int[]{ 0, 0 };
    }
}
