package com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * WHOIS lookup qua ipapi.co — miễn phí, không cần API key, HTTPS.
 * Trả về org và ASN cho IP address.
 */
@Service
@Slf4j
public class WhoisService {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public String[] lookup(String ip) {
        // returns [org, asn] or ["", ""] on failure
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://ipapi.co/" + ip + "/json/"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("User-Agent", "SOC-EntityManagement/1.0")
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                String org = root.path("org").asText("");
                String asn = root.path("asn").asText("");
                return new String[]{ org, asn };
            }
        } catch (Exception e) {
            log.warn("[WHOIS] Lookup failed for {}: {}", ip, e.getMessage());
        }
        return new String[]{ "", "" };
    }
}
