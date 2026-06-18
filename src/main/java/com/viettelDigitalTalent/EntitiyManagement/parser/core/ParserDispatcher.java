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
    @Autowired
    private WindowsEventParser windowsParser;
    @Autowired
    private ProcessEventParser processParser;
    @Autowired
    private AlertEventParser alertParser;
    @Autowired
    private NetworkEventParser networkParser;

    public BaseEvent parse(String source, String rawData) {
        return switch (source.toLowerCase()) {
            case "windows" -> windowsParser.parse(rawData);
            case "process" -> processParser.parse(rawData);
            case "alert" -> alertParser.parse(rawData);
            case "network" -> networkParser.parse(rawData);
            default -> throw new RuntimeException("Unknown source: " + source);
        };
    }
}
