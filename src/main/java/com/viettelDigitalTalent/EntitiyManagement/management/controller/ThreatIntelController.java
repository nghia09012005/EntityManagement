package com.viettelDigitalTalent.EntitiyManagement.management.controller;

import com.viettelDigitalTalent.EntitiyManagement.enrichment.threatintel.ThreatIntelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/threat-intel")
@RequiredArgsConstructor
public class ThreatIntelController {

    private final ThreatIntelService threatIntelService;

    @GetMapping("/provider")
    public ResponseEntity<String> provider() {
        if (threatIntelService == null) {
            return ResponseEntity.ok("none");
        }
        return ResponseEntity.ok(threatIntelService.providerName());
    }
}
