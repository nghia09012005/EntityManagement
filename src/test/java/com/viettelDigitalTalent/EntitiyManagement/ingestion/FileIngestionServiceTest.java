package com.viettelDigitalTalent.EntitiyManagement.ingestion;

import com.viettelDigitalTalent.EntitiyManagement.ingestion.dto.FileIngestionResponse;
import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.FileIngestionService;
import com.viettelDigitalTalent.EntitiyManagement.management.service.MinioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @Mock MinioService minioService;
    @Mock KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        service = new FileIngestionService(minioService, kafkaTemplate, new SyncTaskExecutor());
    }

    @Test
    void ingestFileSendsEachLineToKafka() throws Exception {
        String content = "{\"user\":\"admin\"}\n{\"srcIp\":\"1.1.1.1\"}\n";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(minioService.downloadFile("test.log")).thenReturn(stream);

        FileIngestionResponse response = service.ingestFile("test.log");

        assertThat(response.getParsedLines()).isEqualTo(2);
        assertThat(response.getQueuedMessages()).isEqualTo(2);
        verify(kafkaTemplate, times(2)).send(eq("raw-logs"), anyString());
    }

    @Test
    void ingestFileSkipsBlankLines() throws Exception {
        String content = "{\"user\":\"admin\"}\n\n  \n{\"srcIp\":\"1.1.1.1\"}\n";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(minioService.downloadFile("test.log")).thenReturn(stream);

        FileIngestionResponse response = service.ingestFile("test.log");

        assertThat(response.getParsedLines()).isEqualTo(2);
        verify(kafkaTemplate, times(2)).send(anyString(), anyString());
    }

    @Test
    void ingestEmptyFileReturnsZeroCounts() throws Exception {
        InputStream stream = new ByteArrayInputStream(new byte[0]);
        when(minioService.downloadFile("empty.log")).thenReturn(stream);

        FileIngestionResponse response = service.ingestFile("empty.log");

        assertThat(response.getParsedLines()).isEqualTo(0);
        assertThat(response.getQueuedMessages()).isEqualTo(0);
        verify(kafkaTemplate, never()).send(anyString(), anyString());
    }

    @Test
    void ingestFileSetsFileNameInResponse() throws Exception {
        InputStream stream = new ByteArrayInputStream("{\"user\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        when(minioService.downloadFile("events.log")).thenReturn(stream);

        FileIngestionResponse response = service.ingestFile("events.log");

        assertThat(response.getFileName()).isEqualTo("events.log");
        assertThat(response.getSource()).isEqualTo("auto");
    }

    @Test
    void ingestFileAsyncCompletesWithoutError() throws Exception {
        InputStream stream = new ByteArrayInputStream("{\"user\":\"admin\"}".getBytes(StandardCharsets.UTF_8));
        when(minioService.downloadFile("async.log")).thenReturn(stream);

        service.ingestFileAsync("async.log");

        verify(kafkaTemplate, times(1)).send(anyString(), anyString());
    }

    @Test
    void ingestFileAsyncHandlesExceptionGracefully() throws Exception {
        when(minioService.downloadFile(anyString())).thenThrow(new RuntimeException("MinIO down"));

        service.ingestFileAsync("fail.log"); // must not throw
    }

    @Test
    void ingestFileSendsFreeTextLinesToKafka() throws Exception {
        String content = "Failed password for admin from 192.168.1.1 port 22 ssh2\n";
        InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
        when(minioService.downloadFile("syslog.log")).thenReturn(stream);

        FileIngestionResponse response = service.ingestFile("syslog.log");

        assertThat(response.getParsedLines()).isEqualTo(1);
        verify(kafkaTemplate).send(eq("raw-logs"), contains("Failed password"));
    }
}
