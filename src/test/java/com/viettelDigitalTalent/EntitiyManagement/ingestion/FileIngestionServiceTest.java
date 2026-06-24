package com.viettelDigitalTalent.EntitiyManagement.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.ingestion.dto.FileIngestionResponse;
import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.FileIngestionService;
import com.viettelDigitalTalent.EntitiyManagement.management.service.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileIngestionServiceTest {

    private FileIngestionService service;
    private final ObjectMapper objectMapper = new ObjectMapper(); // Khởi tạo ObjectMapper

    @Mock MinioService minioService;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        // Cập nhật constructor với objectMapper
        service = new FileIngestionService(minioService, kafkaTemplate, new SyncTaskExecutor(), objectMapper);
    }

    @Test
    void ingestFileSendsEachLineToKafka() throws Exception {
        String content = "{\"user\":\"admin\"}\n{\"srcIp\":\"1.1.1.1\"}\n";
        String tenantId = "tenant-1";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(minioService.downloadFile("test.log")).thenReturn(stream);

        FileIngestionResponse response = service.ingestFile("test.log", tenantId);

        assertThat(response.getParsedLines()).isEqualTo(2);

        // Kiểm tra xem JSON có chứa đúng tenantId không
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2)).send(eq("raw-logs"), captor.capture());

        assertThat(captor.getAllValues().get(0)).contains("\"tenantId\":\"tenant-1\"");
        assertThat(captor.getAllValues().get(0)).contains("\"user\":\"admin\"");
    }

    @Test
    void ingestFileSkipsBlankLines() throws Exception {
        String content = "line1\n\n  \nline2\n";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(minioService.downloadFile("test.log")).thenReturn(stream);

        service.ingestFile("test.log", "tenant-1");

        verify(kafkaTemplate, times(2)).send(eq("raw-logs"), anyString());
    }

    @Test
    void ingestEmptyFileReturnsZeroCounts() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        when(minioService.downloadFile("empty.log")).thenReturn(stream);

        FileIngestionResponse response = service.ingestFile("empty.log", "tenant-1");

        assertThat(response.getParsedLines()).isEqualTo(0);
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    void ingestFileAsyncCompletesWithoutError() throws Exception {
        InputStream stream = new ByteArrayInputStream("{\"user\":\"admin\"}".getBytes(StandardCharsets.UTF_8));
        when(minioService.downloadFile("async.log")).thenReturn(stream);

        service.ingestFileAsync("async.log", "tenant-1");

        verify(kafkaTemplate, timeout(1000).times(1)).send(anyString(), anyString());
    }

    @Test
    void ingestFileAsyncHandlesExceptionGracefully() throws Exception {
        when(minioService.downloadFile(anyString())).thenThrow(new RuntimeException("MinIO down"));
        service.ingestFileAsync("fail.log", "tenant-1");
        // Không có exception văng ra là test pass
    }
}