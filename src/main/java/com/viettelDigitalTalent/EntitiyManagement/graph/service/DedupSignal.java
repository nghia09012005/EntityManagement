package com.viettelDigitalTalent.EntitiyManagement.graph.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DedupSignal {

    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public void mark() {
        dirty.set(true);
    }

    /** Returns true và reset về false atomically. */
    public boolean getAndReset() {
        return dirty.getAndSet(false);
    }
}
