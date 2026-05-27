package com.viettelDigitalTalent.EntitiyManagement.parser.core;

import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.windows.WindowsEventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ParserDispatcher {
    @Autowired
    private WindowsEventParser windowsParser;
    // ... các parser khác

    public BaseEvent parse(String source, String rawData) {
        return switch (source.toLowerCase()) {
            case "windows" -> windowsParser.parse(rawData);
            default -> throw new RuntimeException("Unknown source: " + source);
        };
    }
}
