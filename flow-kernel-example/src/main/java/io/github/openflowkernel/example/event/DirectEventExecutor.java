package io.github.openflowkernel.example.event;

import io.github.openflowkernel.event.EventExecutor;

public final class DirectEventExecutor implements EventExecutor {
    @Override
    public void execute(Runnable action) {
        action.run();
    }
}
