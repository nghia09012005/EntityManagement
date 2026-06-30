package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import org.springframework.stereotype.Component;

@Component
public class LlmPromptEngine {

    private static final String SYSTEM_INSTRUCTION = """
            You are a security analyst AI. Extract a security finding from the given free-text log line.
            Return ONLY one OCSF-like JSON object for Security Finding with these exact fields.
            Use null for missing optional nested values. Use 0 for time if the log line has no timestamp.
            {
               "eventType": "ALERT",
               "class_uid": 2001,
               "category_uid": 2,
               "activity_id": 1,
               "severity_id": "<1=INFO, 2=LOW, 3=MEDIUM, 4=HIGH, 5=CRITICAL>",
               "time": "<epoch milliseconds, or 0>",
               "severity": "<INFO | LOW | MEDIUM | HIGH | CRITICAL>",
               "message": "<brief description of what happened>",
            
               "finding": {
                 "title": "<short alert name, e.g. Brute Force Attempt>",
                 "desc": "<brief description>",
                 "types": [
                   "security_finding"
                 ]
               },
            
               "actor": {
                 "user": {
                   "name": "<username, null if none>"
                 }
               },
            
               "dst_endpoint": {
                 "ip": "<IPv4/IPv6, null if none>",
                 "hostname": "<hostname, null if none>",
                 "domain": "<domain name, null if none>"
               },
            
               "process": {
                 "name": "<process name, null if none>",
                 "file": {
                   "hashes": [
                     {
                       "algorithm_id": 3,
                       "algorithm": "SHA-256",
                       "value": "<SHA256/MD5 hash, null if none>"
                     }
                   ]
                 }
               },
            
               "target_url": "<URL if present, null if none>",
               "target_email": "<email address if present, null if none>",
               "cloud_resource_id": "<AWS/Azure/GCP resource identifier, null if none>",
               "cve_uid": "<CVE identifier such as CVE-2021-44228, null if none>",
            
               "source": "<log source or tool name, null if unknown>"
             }
            If there is no file hash, return "hashes": [].
            Return ONLY the JSON, no markdown, no explanation.
            """;

    public String build(String freeTextLog) {
        return SYSTEM_INSTRUCTION + "\n\nLog line:\n" + freeTextLog;
    }
}
