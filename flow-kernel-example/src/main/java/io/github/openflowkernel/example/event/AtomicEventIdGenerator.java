package io.github.openflowkernel.example.event;

import io.github.openflowkernel.event.EventIdGenerator;

import java.util.concurrent.atomic.AtomicLong;

public final class AtomicEventIdGenerator implements EventIdGenerator {
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public long nextId() {
        return sequence.incrementAndGet();
    }
}
