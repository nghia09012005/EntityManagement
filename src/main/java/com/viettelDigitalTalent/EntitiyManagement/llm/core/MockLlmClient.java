package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import org.springframework.stereotype.Service;

@Service
public class MockLlmClient implements LlmClient {

    @Override
    public String modelName() { return "mock-llm"; }

    @Override
    public String call(String prompt) {
        // Trả về AlertEvent JSON mẫu để test khi chưa có Gemini key
        return """
                {
                  "alertName": "Suspicious Activity Detected",
                  "severity": "MEDIUM",
                  "description": "Free-text log parsed by mock LLM",
                  "targetIp": null,
                  "targetUser": null,
                  "targetHost": null,
                  "targetDomain": null,
                  "targetFileHash": null,
                  "source": "llm-mock"
                }
                """;
    }
}
