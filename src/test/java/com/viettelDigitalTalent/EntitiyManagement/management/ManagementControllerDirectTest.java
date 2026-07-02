package com.viettelDigitalTalent.EntitiyManagement.management;

import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.FileIngestionService;
import com.viettelDigitalTalent.EntitiyManagement.management.controller.AuthController;
import com.viettelDigitalTalent.EntitiyManagement.management.controller.FileController;
import com.viettelDigitalTalent.EntitiyManagement.management.controller.ThreatIntelController;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.AuthResponse;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.FileUploadResponse;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.LoginRequest;
import com.viettelDigitalTalent.EntitiyManagement.management.dto.RegisterRequest;
import com.viettelDigitalTalent.EntitiyManagement.management.service.AuthService;
import com.viettelDigitalTalent.EntitiyManagement.management.service.MinioService;
import com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel.ThreatIntelService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ManagementControllerDirectTest {

    @Test
    void authController_registerLoginAndErrorHandler() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService);
        AuthResponse response = AuthResponse.builder()
                .token("jwt")
                .username("analyst")
                .email("a@example.com")
                .role("ANALYST")
                .tenantId("tenant-1")
                .build();

        RegisterRequest register = new RegisterRequest("analyst", "a@example.com", "secret1");
        LoginRequest login = new LoginRequest("analyst", "secret1");
        when(authService.register(register)).thenReturn(response);
        when(authService.login(login)).thenReturn(response);

        assertThat(controller.register(register).getBody()).isSameAs(response);
        assertThat(controller.login(login).getBody()).isSameAs(response);
        assertThat(controller.handleIllegalArgument(new IllegalArgumentException("bad")).getBody())
                .containsEntry("error", "bad");
    }

    @Test
    void fileController_uploadUsesTenantFromAuthentication() throws Exception {
        MinioService minioService = mock(MinioService.class);
        FileIngestionService fileIngestionService = mock(FileIngestionService.class);
        FileController controller = new FileController(minioService, fileIngestionService);
        MockMultipartFile file = new MockMultipartFile("file", "alerts.log", "text/plain", "line".getBytes());
        when(minioService.uploadFile(file)).thenReturn(new FileUploadResponse("stored.log", "http://minio/uploads/stored.log"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("user", null);
        auth.setDetails(Map.of("tenantId", "tenant-123"));

        var response = controller.upload(file, auth);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getFileName()).isEqualTo("stored.log");
        verify(fileIngestionService).ingestFileAsync("stored.log", "tenant-123");
    }

    @Test
    void fileController_uploadFallsBackToDefaultTenant() throws Exception {
        MinioService minioService = mock(MinioService.class);
        FileIngestionService fileIngestionService = mock(FileIngestionService.class);
        FileController controller = new FileController(minioService, fileIngestionService);
        MockMultipartFile file = new MockMultipartFile("file", "alerts.log", "text/plain", "line".getBytes());
        when(minioService.uploadFile(file)).thenReturn(new FileUploadResponse("stored.log", "url"));

        controller.upload(file, null);

        verify(fileIngestionService).ingestFileAsync("stored.log", "default");
    }

    @Test
    void threatIntelController_returnsProviderName() {
        ThreatIntelService service = mock(ThreatIntelService.class);
        when(service.providerName()).thenReturn("virustotal");

        assertThat(new ThreatIntelController(service).provider().getBody()).isEqualTo("virustotal");
    }
}
