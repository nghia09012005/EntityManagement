package com.viettelDigitalTalent.EntitiyManagement.enrichment;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.dtos.MalwareInfo;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel.VirusTotalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VirusTotalServiceTest {

    private VirusTotalService service;

    @BeforeEach
    void setUp() {
        service = new VirusTotalService("test-api-key-1234567890");
    }

    @Test
    void providerNameReturnsVirusTotal() {
        assertThat(service.providerName()).isEqualTo("VirusTotal");
    }

    @Test
    void lookupNullHashReturnsNull() {
        assertThat(service.lookup(null)).isNull();
    }

    @Test
    void lookupEmptyHashReturnsNull() {
        assertThat(service.lookup("")).isNull();
        assertThat(service.lookup("   ")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void lookupReturnsErrorInfoWhenHttpClientThrows() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        ReflectionTestUtils.setField(service, "client", mockClient);

        MalwareInfo result = service.lookup("d41d8cd98f00b204e9800998ecf8427e");

        assertThat(result).isNotNull();
        assertThat(result.getVerdict()).isEqualTo("ERROR");
        assertThat(result.isMalicious()).isFalse();
        assertThat(result.getProvider()).isEqualTo("VirusTotal");
        assertThat(result.getDescription()).contains("Error calling VirusTotal");
    }

    @Test
    @SuppressWarnings("unchecked")
    void lookupReturns404AsNotFound() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(404);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ReflectionTestUtils.setField(service, "client", mockClient);

        MalwareInfo result = service.lookup("unknownhash123");

        assertThat(result.getVerdict()).isEqualTo("NOT_FOUND");
        assertThat(result.isMalicious()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void lookupReturns200WithMaliciousStats() throws Exception {
        String body = """
                {
                  "data": {
                    "attributes": {
                      "last_analysis_stats": {"malicious": 15, "suspicious": 2, "undetected": 50},
                      "last_analysis_results": {
                        "Kaspersky": {"result": "Trojan.Win32.Agent"}
                      }
                    }
                  }
                }
                """;

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(body);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ReflectionTestUtils.setField(service, "client", mockClient);

        MalwareInfo result = service.lookup("d41d8cd98f00b204e9800998ecf8427e");

        assertThat(result.isMalicious()).isTrue();
        assertThat(result.getVerdict()).isEqualTo("MALICIOUS");
        assertThat(result.getFamily()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void lookupReturns200WithCleanStats() throws Exception {
        String body = """
                {
                  "data": {
                    "attributes": {
                      "last_analysis_stats": {"malicious": 0, "suspicious": 0, "undetected": 70},
                      "last_analysis_results": {}
                    }
                  }
                }
                """;

        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(body);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        ReflectionTestUtils.setField(service, "client", mockClient);

        MalwareInfo result = service.lookup("5d41402abc4b2a76b9719d911017c592");

        assertThat(result.isMalicious()).isFalse();
        assertThat(result.getVerdict()).isEqualTo("CLEAN");
    }
}
