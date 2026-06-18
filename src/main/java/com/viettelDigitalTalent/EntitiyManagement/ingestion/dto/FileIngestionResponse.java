package com.viettelDigitalTalent.EntitiyManagement.ingestion.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileIngestionResponse {
    private String fileName;
    private String source;
    private int parsedLines;
    private int queuedMessages;
}