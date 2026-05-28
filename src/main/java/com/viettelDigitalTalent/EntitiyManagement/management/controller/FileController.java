package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.management.dto.FileUploadResponse;
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

    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> upload(
            @RequestParam("file") MultipartFile file
    ) throws Exception {

        return ResponseEntity.ok(
                minioService.uploadFile(file)
        );
    }
}