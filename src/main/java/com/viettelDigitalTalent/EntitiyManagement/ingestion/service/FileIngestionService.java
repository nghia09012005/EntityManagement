package com.viettelDigitalTalent.EntitiyManagement.ingestion.service;

import com.viettelDigitalTalent.EntitiyManagement.ingestion.dto.FileIngestionResponse;
import com.viettelDigitalTalent.EntitiyManagement.management.service.MinioService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileIngestionService {

    private final MinioService minioService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TaskExecutor taskExecutor;

    public void ingestFileAsync(String fileName) {
        CompletableFuture.runAsync(() -> {
            try {
                FileIngestionResponse response = ingestFile(fileName);
                log.info("File ingestion finished: fileName={} source={} readLines={} queuedMessages={}",
                        response.getFileName(),
                        response.getSource(),
                        response.getParsedLines(),
                        response.getQueuedMessages());
            } catch (Exception e) {
                log.error("File ingestion failed for fileName={}", fileName, e);
            }
        }, taskExecutor);
    }

    public FileIngestionResponse ingestFile(String fileName) throws Exception {
        int readLines = 0;
        int queuedMessages = 0;

        try (InputStream inputStream = minioService.downloadFile(fileName);
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.isBlank()) continue;
                kafkaTemplate.send("raw-logs", line);  // no key — worker tự detect từ content
                readLines++;
                queuedMessages++;
            }
        }

        return new FileIngestionResponse(fileName, "auto", readLines, queuedMessages);
    }
}