package com.viettelDigitalTalent.EntitiyManagement.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.graph.service.GraphEntityService;
import com.viettelDigitalTalent.EntitiyManagement.normalize.event.AuthenticationEvent;
import com.viettelDigitalTalent.EntitiyManagement.queue.publisher.DeadLetterPublisher;
import com.viettelDigitalTalent.EntitiyManagement.queue.worker.GraphWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphWorkerTest {

    @Mock private GraphEntityService graphEntityService;
    @Mock private DeadLetterPublisher deadLetterPublisher;

    private GraphWorker worker;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules();

    @BeforeEach
    void setUp() {
        worker = new GraphWorker(graphEntityService, objectMapper, deadLetterPublisher);
    }

    @Test
    void consume_callsGraphEntityService() throws Exception {
        AuthenticationEvent event = new AuthenticationEvent();
        event.setEventId("evt-001");
        event.setUsername("admin");
        event.setWorkstation("WIN-PC01");
        String payload = objectMapper.writeValueAsString(event);

        worker.consume(payload);

        verify(graphEntityService, times(1)).save(any());
    }

    @Test
    void consume_publishesToDlqOnException() throws Exception {
        AuthenticationEvent event = new AuthenticationEvent();
        event.setEventId("evt-002");
        event.setUsername("admin");
        event.setWorkstation("WIN-PC01");
        String payload = objectMapper.writeValueAsString(event);

        doThrow(new RuntimeException("Neo4j down")).when(graphEntityService).save(any());

        worker.consume(payload);

        verify(deadLetterPublisher, times(1)).publish(anyString(), eq(payload), any(Exception.class));
    }

    @Test
    void consume_publishesToDlqOnInvalidJson() {
        worker.consume("not-valid-json");

        verify(deadLetterPublisher, times(1)).publish(anyString(), eq("not-valid-json"), any(Exception.class));
        verifyNoInteractions(graphEntityService);
    }

    @Test
    void consume_logsEventIdOnSuccess() throws Exception {
        AuthenticationEvent event = new AuthenticationEvent();
        event.setEventId("evt-003");
        event.setUsername("bob");
        event.setWorkstation("LAP-01");
        String payload = objectMapper.writeValueAsString(event);

        worker.consume(payload);

        verify(graphEntityService, times(1)).save(any());
        verifyNoInteractions(deadLetterPublisher);
    }
}
