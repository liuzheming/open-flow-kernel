package io.github.openflowkernel.event;

@FunctionalInterface
public interface EventIdGenerator {
    long nextId();
}
