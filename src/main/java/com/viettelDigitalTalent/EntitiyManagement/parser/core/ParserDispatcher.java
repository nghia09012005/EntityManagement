package com.viettelDigitalTalent.EntitiyManagement.parser.core;

import com.viettelDigitalTalent.EntitiyManagement.llm.core.LlmProcess;
import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.alert.AlertEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.network.NetworkEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.process.ProcessEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.windows.WindowsEventParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ParserDispatcher {

    @Autowired private WindowsEventParser windowsParser;
    @Autowired private ProcessEventParser processParser;
    @Autowired private AlertEventParser alertParser;
    @Autowired private NetworkEventParser networkParser;
    @Autowired private LlmProcess llmProcess;

    /**
     * Tự động nhận dạng loại event từ nội dung JSON, không cần biết nguồn file.
     * Jackson đã xử lý ALERT/NETWORK (có field eventType); method này chỉ phân biệt
     * Windows auth vs Process từ các field đặc trưng.
     * Nếu input là free-text (không phải JSON) → fallback sang LLM để trích xuất AlertEvent.
     */
    public BaseEvent autoDetect(String rawData) {
        // Free-text (không phải JSON) → trả placeholder ngay, LLM sẽ chạy async trong ParserWorker
        String trimmed = rawData.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            log.info("[ParserDispatcher] Input là free-text, tạo placeholder — LLM sẽ enrich async.");
            AlertEvent placeholder = new AlertEvent();
            placeholder.setAlertName("Pending LLM Analysis");
            placeholder.setSeverity("LOW");
            placeholder.setDescription(rawData.length() > 500 ? rawData.substring(0, 500) : rawData);
            placeholder.setSource("free-text");
            placeholder.setCategory(com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory.THREAT.name());
            return placeholder;
        }

        if (rawData.contains("\"processName\"")
                || rawData.contains("\"commandLine\"")
                || rawData.contains("\"parentProcess\"")) {
            return processParser.parse(rawData);
        }
        if (rawData.contains("\"srcIp\"") || rawData.contains("\"dstIp\"")) {
            return networkParser.parse(rawData);
        }
        if (rawData.contains("\"alertName\"") || rawData.contains("\"severity\"")) {
            return alertParser.parse(rawData);
        }
        if (rawData.contains("\"username\"") || rawData.contains("\"workstation\"")
                || rawData.contains("\"accountName\"") || rawData.contains("\"user\"")) {
            return windowsParser.parse(rawData);
        }
        // Unknown format — let ParserWorker catch and route to DLQ
        log.warn("[ParserDispatcher] Không nhận dạng được định dạng log, đẩy vào DLQ. Preview: {}",
                rawData.length() > 120 ? rawData.substring(0, 120) + "…" : rawData);
        throw new IllegalArgumentException("Unknown log format: no recognizable fields found");
    }

    /** Kept for backward-compat nếu có code khác gọi. */
    public BaseEvent parse(String source, String rawData) {
        return switch (source.toLowerCase()) {
            case "windows" -> windowsParser.parse(rawData);
            case "process" -> processParser.parse(rawData);
            case "alert"   -> alertParser.parse(rawData);
            case "network" -> networkParser.parse(rawData);
            default        -> autoDetect(rawData);
        };
    }
}
