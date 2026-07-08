package com.viettelDigitalTalent.EntitiyManagement.llm.core;

import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class LogSanitizer {

    @Data
    public static class SanitizedLog {
        private final String sanitizedLog;
        private final Map<String, List<String>> replacements;
    }

    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    public LogSanitizer() {
        patterns.put("<MAC>", Pattern.compile("\\b(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}\\b"));
        patterns.put("<URL>", Pattern.compile("(?i)https?://[^\\s]+"));
        patterns.put("<EMAIL>", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"));
        patterns.put("<IP>", Pattern.compile("\\b(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)(?:\\.(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}\\b|\\b(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}\\b"));
        patterns.put("<SHA256>", Pattern.compile("\\b[a-fA-F0-9]{64}\\b"));
        patterns.put("<SHA1>", Pattern.compile("\\b[a-fA-F0-9]{40}\\b"));
        patterns.put("<MD5>", Pattern.compile("\\b[a-fA-F0-9]{32}\\b"));
        patterns.put("<FILE_PATH>", Pattern.compile("[A-Za-z]:\\\\(?:[^\\\\\s]+\\\\)*[^\\\\\s]*|(?<!\\w)/(?:[^\\s/]+/)*[^\\s]*"));
        patterns.put("<DOMAIN>", Pattern.compile("\\b(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}\\b"));
        patterns.put("<HOST>", Pattern.compile("\\b[a-zA-Z][a-zA-Z0-9-]{0,62}\\b(?=\\s+(?:connected|login|logged|failed|access|host|server))"));
    }

    public String sanitize(String log) {
        return sanitizeWithMapping(log).getSanitizedLog();
    }

    public SanitizedLog sanitizeWithMapping(String log) {
        if (log == null || log.isBlank()) {
            return new SanitizedLog(log, Map.of());
        }

        String sanitized = log;
        Map<String, List<String>> replacements = new LinkedHashMap<>();

        for (Map.Entry<String, Pattern> entry : patterns.entrySet()) {
            String token = entry.getKey();
            Pattern pattern = entry.getValue();
            Matcher matcher = pattern.matcher(sanitized);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String value = matcher.group();
                replacements.computeIfAbsent(token, k -> new ArrayList<>()).add(value);
                matcher.appendReplacement(buffer, token);
            }
            matcher.appendTail(buffer);
            sanitized = buffer.toString();
        }

        return new SanitizedLog(sanitized, replacements);
    }

    public AlertEvent restoreMaskedValues(AlertEvent event, Map<String, List<String>> replacements) {
        if (event == null || replacements == null || replacements.isEmpty()) {
            return event;
        }

        String alertName = event.getAlertName();
        if (alertName != null) {
            event.setAlertName(restoreText(alertName, replacements));
        }


        String description = event.getDescription();
        if (description != null) {
            event.setDescription(restoreText(description, replacements));
        }

        if (event.getSourceIp() != null) {
            event.setSourceIp(restoreText(event.getSourceIp(), replacements));
        }
        if (event.getTargetIp() != null) {
            event.setTargetIp(restoreText(event.getTargetIp(), replacements));
        }

        if (event.getTargetUser() != null) {
            event.setTargetUser(restoreText(event.getTargetUser(), replacements));
        }
        if (event.getTargetHost() != null) {
            event.setTargetHost(restoreText(event.getTargetHost(), replacements));
        }
        if (event.getTargetDomain() != null) {
            event.setTargetDomain(restoreText(event.getTargetDomain(), replacements));
        }
        if (event.getTargetUrl() != null) {
            event.setTargetUrl(restoreText(event.getTargetUrl(), replacements));
        }
        if (event.getTargetEmail() != null) {
            event.setTargetEmail(restoreText(event.getTargetEmail(), replacements));
        }
        if (event.getTargetCloudResourceId() != null) {
            event.setTargetCloudResourceId(restoreText(event.getTargetCloudResourceId(), replacements));
        }
        if (event.getTargetCve() != null) {
            event.setTargetCve(restoreText(event.getTargetCve(), replacements));
        }

        return event;
    }

    private String restoreText(String text, Map<String, List<String>> replacements) {
        if (text == null) {
            return null;
        }

        String restored = text;
        for (Map.Entry<String, List<String>> entry : replacements.entrySet()) {
            String token = entry.getKey();
            List<String> originals = entry.getValue();
            if (token == null || originals == null || originals.isEmpty()) {
                continue;
            }

            String[] values = originals.toArray(new String[0]);
            int idx = 0;
            while (idx < values.length) {
                String original = values[idx++];
                if (original != null && !token.equals(original)) {
                    restored = restored.replaceFirst(token, original);
                }
            }
        }
        return restored;
    }
}
