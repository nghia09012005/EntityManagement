package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import org.springframework.stereotype.Service;

@Service
public class MockLlmClient implements LlmClient {

    @Override
    public String modelName() { return "mock-llm"; }

    @Override
    public String call(String prompt) {
        // Trả về OCSF Security Finding mẫu để test khi chưa có Gemini key
        return """
                {
                  "eventType": "THREAT",
                  "class_uid": 2001,
                  "category_uid": 2,
                  "activity_id": 1,
                  "severity_id": 3,
                  "time": 0,
                  "severity": "MEDIUM",
                  "message": "Free-text log parsed by mock LLM",
                  "finding": {
                    "title": "Suspicious Activity Detected",
                    "desc": "Free-text log parsed by mock LLM",
                    "types": ["security_finding"]
                  },
                  "actor": { "user": { "name": null } },
                  "dst_endpoint": {
                    "ip": null,
                    "hostname": null,
                    "domain": null
                  },
                  "process": { "file": { "hashes": [] } },
                  "source": "llm-mock"
                }
                """;
    }
}
