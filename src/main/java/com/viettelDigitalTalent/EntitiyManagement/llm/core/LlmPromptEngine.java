package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import org.springframework.stereotype.Component;

@Component
public class LlmPromptEngine {

    private static final String SYSTEM_INSTRUCTION = """
            You are a security analyst AI. Extract security alert entities from the given free-text log line.
            Return ONLY a JSON object with these exact fields (use null for missing values):
            {
              "alertName":      "<short alert name, e.g. 'Brute Force Attempt'>",
              "severity":       "<one of: CRITICAL | HIGH | MEDIUM | LOW>",
              "description":    "<brief description of what happened>",
              "targetIp":       "<IPv4 or IPv6 address, null if none>",
              "targetUser":     "<username or account name, null if none>",
              "targetHost":     "<hostname or machine name, null if none>",
              "targetDomain":   "<domain name, null if none>",
              "targetFileHash": "<MD5/SHA256 hash, null if none>",
              "source":         "<log source or tool name, null if unknown>"
            }
            Return ONLY the JSON, no markdown, no explanation.
            """;

    public String build(String freeTextLog) {
        return SYSTEM_INSTRUCTION + "\n\nLog line:\n" + freeTextLog;
    }
}
