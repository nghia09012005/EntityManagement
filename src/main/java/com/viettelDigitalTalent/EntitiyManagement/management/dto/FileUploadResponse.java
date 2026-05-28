package com.viettelDigitalTalent.EntitiyManagement.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileUploadResponse {
    private String fileName;
    private String url;
}