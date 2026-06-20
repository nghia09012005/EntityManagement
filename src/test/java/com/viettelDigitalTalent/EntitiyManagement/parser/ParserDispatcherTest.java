package com.viettelDigitalTalent.EntitiyManagement.parser;

import com.viettelDigitalTalent.EntitiyManagement.normalize.alert.AlertEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.base.BaseEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.NetworkEvent;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.ProcessEvent;
import com.viettelDigitalTalent.EntitiyManagement.parser.alert.AlertEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.core.ParserDispatcher;
import com.viettelDigitalTalent.EntitiyManagement.parser.network.NetworkEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.process.ProcessEventParser;
import com.viettelDigitalTalent.EntitiyManagement.parser.windows.WindowsEventParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParserDispatcherTest {

    @InjectMocks
    private ParserDispatcher dispatcher;

    @Mock private WindowsEventParser windowsParser;
    @Mock private ProcessEventParser processParser;
    @Mock private AlertEventParser alertParser;
    @Mock private NetworkEventParser networkParser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(dispatcher, "windowsParser", windowsParser);
        ReflectionTestUtils.setField(dispatcher, "processParser", processParser);
        ReflectionTestUtils.setField(dispatcher, "alertParser", alertParser);
        ReflectionTestUtils.setField(dispatcher, "networkParser", networkParser);
    }

    @Test
    void autoDetectsProcessEventByProcessName() {
        when(processParser.parse(anyString())).thenReturn(new ProcessEvent());
        BaseEvent event = dispatcher.autoDetect("{\"processName\":\"cmd.exe\",\"commandLine\":\"cmd /c whoami\"}");
        assertThat(event).isInstanceOf(ProcessEvent.class);
    }

    @Test
    void autoDetectsProcessEventByCommandLine() {
        when(processParser.parse(anyString())).thenReturn(new ProcessEvent());
        BaseEvent event = dispatcher.autoDetect("{\"commandLine\":\"powershell -enc abc\"}");
        assertThat(event).isInstanceOf(ProcessEvent.class);
    }

    @Test
    void autoDetectsNetworkEventBySrcIp() {
        when(networkParser.parse(anyString())).thenReturn(new NetworkEvent());
        BaseEvent event = dispatcher.autoDetect("{\"srcIp\":\"10.0.0.1\",\"dstIp\":\"8.8.8.8\"}");
        assertThat(event).isInstanceOf(NetworkEvent.class);
    }

    @Test
    void autoDetectsAlertEventByAlertName() {
        when(alertParser.parse(anyString())).thenReturn(new AlertEvent());
        BaseEvent event = dispatcher.autoDetect("{\"alertName\":\"Brute Force\",\"severity\":\"HIGH\"}");
        assertThat(event).isInstanceOf(AlertEvent.class);
    }

    @Test
    void autoDetectsWindowsEventByDefault() {
        when(windowsParser.parse(anyString())).thenReturn(new AuthenticationEvent());
        BaseEvent event = dispatcher.autoDetect("{\"user\":\"admin\",\"is_success\":true}");
        assertThat(event).isInstanceOf(AuthenticationEvent.class);
    }

    @Test
    void parseBySourceWindowsRoutesToWindowsParser() {
        when(windowsParser.parse(anyString())).thenReturn(new AuthenticationEvent());
        BaseEvent event = dispatcher.parse("windows", "{\"user\":\"admin\"}");
        assertThat(event).isInstanceOf(AuthenticationEvent.class);
    }

    @Test
    void parseBySourceAlertRoutesToAlertParser() {
        when(alertParser.parse(anyString())).thenReturn(new AlertEvent());
        BaseEvent event = dispatcher.parse("alert", "{\"alertName\":\"test\"}");
        assertThat(event).isInstanceOf(AlertEvent.class);
    }

    @Test
    void parseBySourceNetworkRoutesToNetworkParser() {
        when(networkParser.parse(anyString())).thenReturn(new NetworkEvent());
        BaseEvent event = dispatcher.parse("network", "{\"srcIp\":\"1.1.1.1\"}");
        assertThat(event).isInstanceOf(NetworkEvent.class);
    }
}
