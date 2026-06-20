package com.viettelDigitalTalent.EntitiyManagement.parser.core;

import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.alert.AlertEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.network.NetworkEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.process.ProcessEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.windows.WindowsEventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ParserDispatcher {

    @Autowired private WindowsEventParser windowsParser;
    @Autowired private ProcessEventParser processParser;
    @Autowired private AlertEventParser alertParser;
    @Autowired private NetworkEventParser networkParser;

    /**
     * Tự động nhận dạng loại event từ nội dung JSON, không cần biết nguồn file.
     * Jackson đã xử lý ALERT/NETWORK (có field eventType); method này chỉ phân biệt
     * Windows auth vs Process từ các field đặc trưng.
     */
    public BaseEvent autoDetect(String rawData) {
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
        // Default: windows/auth event
        return windowsParser.parse(rawData);
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
