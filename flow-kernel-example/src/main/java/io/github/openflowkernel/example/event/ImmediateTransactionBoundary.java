package io.github.openflowkernel.example.event;

import io.github.openflowkernel.event.TransactionBoundary;

public final class ImmediateTransactionBoundary implements TransactionBoundary {
    @Override
    public void afterCommit(Runnable action) {
        action.run();
    }
}
