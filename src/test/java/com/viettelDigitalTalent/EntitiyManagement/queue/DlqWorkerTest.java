package com.viettelDigitalTalent.EntitiyManagement.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viettelDigitalTalent.EntitiyManagement.queue.worker.DlqWorker;
import com.viettelDigitalTalent.EntitiyManagement.storage.mongodb.DlqEvent;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.DlqEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DlqWorkerTest {

    private DlqWorker worker;

    @Mock
    private DlqEventRepository dlqEventRepository;

    @BeforeEach
    void setUp() {
        worker = new DlqWorker(dlqEventRepository, new ObjectMapper(), new SimpleMeterRegistry());
    }

    private ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>("dead-letter-queue", 0, 0L, null, value);
    }

    @Test
    void consume_savesEventToMongo() {
        String msg = "{\"sourceTopic\":\"raw-logs\",\"originalPayload\":\"{}\",\"error\":\"parse error\",\"errorClass\":\"RuntimeException\"}";

        worker.consume(record(msg));

        verify(dlqEventRepository, times(1)).save(any(DlqEvent.class));
    }

    @Test
    void consume_populatesSourceTopic() {
        String msg = "{\"sourceTopic\":\"normalized-events\",\"originalPayload\":\"test\",\"error\":\"npe\",\"errorClass\":\"NullPointerException\"}";

        worker.consume(record(msg));

        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqEventRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceTopic()).isEqualTo("normalized-events");
    }

    @Test
    void consume_populatesErrorFields() {
        String msg = "{\"sourceTopic\":\"raw-logs\",\"originalPayload\":\"bad json\",\"error\":\"Unexpected character\",\"errorClass\":\"JsonParseException\"}";

        worker.consume(record(msg));

        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqEventRepository).save(captor.capture());
        DlqEvent saved = captor.getValue();
        assertThat(saved.getError()).isEqualTo("Unexpected character");
        assertThat(saved.getErrorClass()).isEqualTo("JsonParseException");
    }

    @Test
    void consume_setsFailedAtTimestamp() {
        String msg = "{\"sourceTopic\":\"raw-logs\",\"originalPayload\":\"\",\"error\":\"err\",\"errorClass\":\"Exception\"}";

        worker.consume(record(msg));

        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqEventRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedAt()).isNotNull();
    }

    @Test
    void consume_doesNotThrowOnInvalidJson() {
        // garbage payload → should not propagate exception
        worker.consume(record("not valid json"));

        verify(dlqEventRepository, never()).save(any());
    }

    @Test
    void consume_usesDefaultsWhenFieldsMissing() {
        String msg = "{}";  // all fields missing → defaults applied

        worker.consume(record(msg));

        ArgumentCaptor<DlqEvent> captor = ArgumentCaptor.forClass(DlqEvent.class);
        verify(dlqEventRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceTopic()).isEqualTo("unknown");
    }
}
