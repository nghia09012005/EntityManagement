package com.viettelDigitalTalent.EntitiyManagement.graph;

import com.viettelDigitalTalent.EntitiyManagement.graph.service.DedupSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DedupSignalTest {

    private DedupSignal signal;

    @BeforeEach
    void setUp() {
        signal = new DedupSignal();
    }

    @Test
    void initiallyNotDirty() {
        assertThat(signal.getAndReset()).isFalse();
    }

    @Test
    void markSetsDirty() {
        signal.mark();
        assertThat(signal.getAndReset()).isTrue();
    }

    @Test
    void getAndResetClearsFlag() {
        signal.mark();
        signal.getAndReset();            // consume
        assertThat(signal.getAndReset()).isFalse();  // should be false now
    }

    @Test
    void multipleMarksSingleReset() {
        signal.mark();
        signal.mark();
        signal.mark();
        assertThat(signal.getAndReset()).isTrue();
        assertThat(signal.getAndReset()).isFalse();
    }

    @Test
    void markAfterResetSetsDirtyAgain() {
        signal.mark();
        signal.getAndReset();
        signal.mark();
        assertThat(signal.getAndReset()).isTrue();
    }
}
