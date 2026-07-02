package com.viettelDigitalTalent.EntitiyManagement.enrichment;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel.AbuseIpDbServiceImpl;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel.AlienVaultOtxService;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.ipintel.WhoisService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IpIntelHttpClientCoverageTest {

    @Test
    void abuseIpDb_successNon200AndExceptionPaths() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> ok = response(200, "{\"data\":{\"abuseConfidenceScore\":42,\"totalReports\":7}}");
        HttpResponse<String> bad = response(429, "{}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(ok)
                .thenReturn(bad)
                .thenThrow(new RuntimeException("network"));

        AbuseIpDbServiceImpl service = new AbuseIpDbServiceImpl("abcdef123456");
        ReflectionTestUtils.setField(service, "client", client);

        service.init();
        assertThat(service.providerName()).isEqualTo("AbuseIPDB");
        assertThat(service.check("1.1.1.1")).containsExactly(42, 7);
        assertThat(service.check("1.1.1.1")).containsExactly(0, 0);
        assertThat(service.check("1.1.1.1")).containsExactly(0, 0);
    }

    @Test
    void alienVaultOtx_successNon200AndExceptionPaths() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> ok = response(200, "{\"pulse_info\":{\"count\":3},\"reputation\":1.2}");
        HttpResponse<String> bad = response(500, "{}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(ok)
                .thenReturn(bad)
                .thenThrow(new RuntimeException("network"));

        AlienVaultOtxService service = new AlienVaultOtxService("abcdef123456");
        ReflectionTestUtils.setField(service, "client", client);

        service.init();
        assertThat(service.providerName()).isEqualTo("AlienVault-OTX");
        assertThat(service.check("8.8.8.8")).containsExactly(3, 12);
        assertThat(service.check("8.8.8.8")).containsExactly(0, 0);
        assertThat(service.check("8.8.8.8")).containsExactly(0, 0);
    }

    @Test
    void whois_successNon200AndExceptionPaths() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> ok = response(200, "{\"org\":\"Example ISP\",\"asn\":\"AS123\"}");
        HttpResponse<String> bad = response(404, "{}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(ok)
                .thenReturn(bad)
                .thenThrow(new RuntimeException("network"));

        WhoisService service = new WhoisService();
        ReflectionTestUtils.setField(service, "client", client);

        assertThat(service.lookup("8.8.8.8")).containsExactly("Example ISP", "AS123");
        assertThat(service.lookup("8.8.8.8")).containsExactly("", "");
        assertThat(service.lookup("8.8.8.8")).containsExactly("", "");
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
