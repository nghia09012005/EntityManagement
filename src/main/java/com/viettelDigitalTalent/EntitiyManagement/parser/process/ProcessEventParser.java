package com.viettelDigitalTalent.EntitiyManagement.parser.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.common.utils.JsonUtils;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.EventCategory;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.EventParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ProcessEventParser implements EventParser {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ProcessEvent parse(String rawData) {
        try {
            JsonNode root = objectMapper.readTree(rawData);

            ProcessEvent event = new ProcessEvent();
            event.setProcessName(JsonUtils.extractValue(root, "processName", "process_name", "name"));
            event.setProcessPath(JsonUtils.extractValue(root, "processPath", "process_path", "path"));
            event.setFileHash(JsonUtils.extractValue(root, "fileHash", "file_hash", "sha256", "md5"));
            event.setCommandLine(JsonUtils.extractValue(root, "commandLine", "command_line", "cmd"));

            event.getRawData().put("processName", event.getProcessName());
            event.getRawData().put("processPath", event.getProcessPath());
            event.getRawData().put("fileHash", event.getFileHash());
            event.getRawData().put("commandLine", event.getCommandLine());

            event.setTimestamp(LocalDateTime.now());
            event.setCategory(EventCategory.PROCESS.name());

            return event;
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi parse Process Log bằng Jackson: " + e.getMessage());
        }
    }
}