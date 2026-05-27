package com.viettelDigitalTalent.EntitiyManagement.ingestion.controller;

import com.viettelDigitalTalent.EntitiyManagement.ingestion.service.IngestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ingest")
public class LogIngestionController {
    @Autowired
    private IngestionService ingestionService;

    @PostMapping("/{source}")
    public ResponseEntity<String> receiveLog(@PathVariable String source, @RequestBody String rawData) {
        ingestionService.sendToQueue(source, rawData);
        return ResponseEntity.accepted().body("Log queued");
    }
}