package com.viettelDigitalTalent.EntitiyManagement.graph;

import com.viettelDigitalTalent.EntitiyManagement.graph.service.DedupSignal;
import com.viettelDigitalTalent.EntitiyManagement.graph.service.GraphDeduplicationService;
import com.viettelDigitalTalent.EntitiyManagement.storage.repository.GraphDedupLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GraphDeduplicationServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Neo4jClient neo4jClient;

    @Mock
    private GraphDedupLogRepository dedupLogRepository;

    private DedupSignal dedupSignal;
    private GraphDeduplicationService service;

    @BeforeEach
    void setUp() {
        dedupSignal = new DedupSignal();
        service = new GraphDeduplicationService(neo4jClient, dedupLogRepository, dedupSignal);
    }

    @Test
    void runDeduplication_skipsWhenFlagNotSet() {
        service.runDeduplication();

        verify(neo4jClient, never()).query(anyString());
        verify(dedupLogRepository, never()).saveAll(anyList());
    }

    @Test
    void runDeduplication_runsWhenFlagSet() {
        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of());

        dedupSignal.mark();
        service.runDeduplication();

        verify(neo4jClient, atLeastOnce()).query(anyString());
    }

    @Test
    void runDeduplication_resetsFlagAfterRun() {
        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of());
        clearInvocations(neo4jClient);  // reset call count after when() setup

        dedupSignal.mark();
        service.runDeduplication();
        // flag was consumed, second run should skip
        service.runDeduplication();

        verify(neo4jClient, times(2)).query(anyString()); // only the 2 from first run (users + hosts)
    }

    @Test
    void runDeduplication_savesAllLogsInOneCall() {
        Map<String, Object> userRow = Map.of("fromVal", "nghia", "toVal", "nghia@company.vn");
        Map<String, Object> hostRow = Map.of("fromVal", "win-pc01", "toVal", "win-pc01.corp.local");

        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of(userRow))   // first call: users
                .thenReturn(List.of(hostRow));   // second call: hosts

        dedupSignal.mark();
        service.runDeduplication();

        // saveAll called once with combined list (1 user + 1 host = 2 logs)
        verify(dedupLogRepository, times(1)).saveAll(argThat(iter -> StreamSupport.stream(iter.spliterator(), false).count() == 2));
    }

    @Test
    void runDeduplication_doesNotSaveWhenNoLinksCreated() {
        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of());

        dedupSignal.mark();
        service.runDeduplication();

        verify(dedupLogRepository, never()).saveAll(anyList());
    }

    @Test
    void runDeduplication_savesOnlyUserLogsWhenNoHostLinks() {
        Map<String, Object> userRow = Map.of("fromVal", "admin", "toVal", "admin@corp.local");

        when(neo4jClient.query(anyString()).bind(anyString()).to(anyString()).fetch().all())
                .thenReturn(List.of(userRow))   // users: 1 match
                .thenReturn(List.of());          // hosts: empty

        dedupSignal.mark();
        service.runDeduplication();

        verify(dedupLogRepository, times(1)).saveAll(argThat(iter -> StreamSupport.stream(iter.spliterator(), false).count() == 1));
    }
}
