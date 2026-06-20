package com.viettelDigitalTalent.EntitiyManagement.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.process.ProcessEventParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessEventParserTest {

    private ProcessEventParser parser;

    @BeforeEach
    void setUp() {
        parser = new ProcessEventParser();
        ReflectionTestUtils.setField(parser, "objectMapper", new ObjectMapper());
    }

    @Test
    void parsesFullProcessEvent() {
        String raw = "{\"processName\":\"powershell.exe\",\"processPath\":\"C:\\\\Windows\\\\System32\\\\powershell.exe\",\"fileHash\":\"44d88612fea8a8f36de82e1278abb02f\",\"commandLine\":\"powershell.exe -nop\",\"hostname\":\"WIN-PC01\"}";
        ProcessEvent event = parser.parse(raw);
        assertThat(event.getProcessName()).isEqualTo("powershell.exe");
        assertThat(event.getFileHash()).isEqualTo("44d88612fea8a8f36de82e1278abb02f");
        assertThat(event.getCommandLine()).isEqualTo("powershell.exe -nop");
        assertThat(event.getRawData()).containsKey("hostname");
    }

    @Test
    void acceptsAlternativeHashField() {
        String raw = "{\"processName\":\"mimikatz.exe\",\"sha256\":\"d41d8cd98f00b204e9800998ecf8427e\"}";
        ProcessEvent event = parser.parse(raw);
        assertThat(event.getFileHash()).isEqualTo("d41d8cd98f00b204e9800998ecf8427e");
    }

    @Test
    void setsProcessCategory() {
        String raw = "{\"processName\":\"cmd.exe\"}";
        ProcessEvent event = parser.parse(raw);
        assertThat(event.getCategory()).isEqualTo("PROCESS");
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void storesFieldsInRawData() {
        String raw = "{\"processName\":\"calc.exe\",\"fileHash\":\"abc123\"}";
        ProcessEvent event = parser.parse(raw);
        assertThat(event.getRawData()).containsEntry("processName", "calc.exe");
        assertThat(event.getRawData()).containsEntry("fileHash", "abc123");
    }

    @Test
    void throwsOnInvalidJson() {
        assertThatThrownBy(() -> parser.parse("{bad json"))
                .isInstanceOf(RuntimeException.class);
    }
}
