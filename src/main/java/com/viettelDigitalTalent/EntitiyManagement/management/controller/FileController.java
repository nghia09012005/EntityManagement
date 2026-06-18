package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.management.dto.FileUploadResponse;
import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.FileIngestionService;
import com.viettelDigitalTalent.EntitiyManagement.management.service.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final MinioService minioService;
    private final FileIngestionService fileIngestionService;

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file
    ) throws Exception {

        FileUploadResponse uploadResponse = minioService.uploadFile(file);
        fileIngestionService.ingestFileAsync(uploadResponse.getFileName());

        return ResponseEntity.accepted().body(uploadResponse);
    }
}