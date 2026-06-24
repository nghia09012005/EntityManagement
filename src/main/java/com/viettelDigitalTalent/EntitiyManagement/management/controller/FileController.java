package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.management.dto.FileUploadResponse;
import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.FileIngestionService;
import com.viettelDigitalTalent.EntitiyManagement.management.service.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final MinioService minioService;
    private final FileIngestionService fileIngestionService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) throws Exception {

        String tenantId = extractTenantId(authentication);
        FileUploadResponse uploadResponse = minioService.uploadFile(file);
        fileIngestionService.ingestFileAsync(uploadResponse.getFileName(), tenantId);

        return ResponseEntity.accepted().body(uploadResponse);
    }

    @SuppressWarnings("unchecked")
    private String extractTenantId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object tid = details.get("tenantId");
            if (tid instanceof String s) return s;
        }
        return "default";
    }
}