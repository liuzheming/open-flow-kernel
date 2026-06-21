package io.github.openflowkernel.core.process;

import java.util.Map;

@FunctionalInterface
public interface ProcessStartHook {
    void afterProcessCreated(
        ProcessDefinition definition,
        ProcessInstance instance,
        Map<String, String> initialData
    );
}
