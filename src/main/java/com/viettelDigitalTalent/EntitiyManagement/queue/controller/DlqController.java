package com.viettelDigitalTalent.EntitiyManagement.queue.controller;

import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.DlqEvent;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.DlqEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/dlq")
@RequiredArgsConstructor
public class DlqController {

    private final DlqEventRepository dlqEventRepository;

    @GetMapping("/events")
    public ResponseEntity<Page<DlqEvent>> listEvents(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<DlqEvent> result = dlqEventRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "failedAt")));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> summary() {
        Map<String, Long> counts = Map.of(
                "raw-logs",         dlqEventRepository.countBySourceTopic("raw-logs"),
                "normalized-events",dlqEventRepository.countBySourceTopic("normalized-events"),
                "enriched-events",  dlqEventRepository.countBySourceTopic("enriched-events"),
                "total",            dlqEventRepository.count()
        );
        return ResponseEntity.ok(counts);
    }
}
