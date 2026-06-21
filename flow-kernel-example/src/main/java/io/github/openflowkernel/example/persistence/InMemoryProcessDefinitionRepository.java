package io.github.openflowkernel.example.persistence;

import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessDefinitionRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryProcessDefinitionRepository
    implements ProcessDefinitionRepository {

    private final Map<String, ProcessDefinition> definitions = new ConcurrentHashMap<>();

    @Override
    public void save(ProcessDefinition definition) {
        definitions.put(definition.key(), definition);
    }

    @Override
    public Optional<ProcessDefinition> findByKey(String key) {
        return Optional.ofNullable(definitions.get(key));
    }
}
