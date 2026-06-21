package io.github.openflowkernel.core.process;

import java.util.Optional;

public interface ProcessDefinitionRepository {
    void save(ProcessDefinition definition);

    Optional<ProcessDefinition> findByKey(String key);
}
