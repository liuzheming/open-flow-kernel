package io.github.openflowkernel.event;

@FunctionalInterface
public interface EventExecutor {
    void execute(Runnable action);
}
