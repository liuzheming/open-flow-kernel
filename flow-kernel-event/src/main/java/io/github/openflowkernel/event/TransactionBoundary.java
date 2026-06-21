package io.github.openflowkernel.event;

@FunctionalInterface
public interface TransactionBoundary {
    void afterCommit(Runnable action);
}
