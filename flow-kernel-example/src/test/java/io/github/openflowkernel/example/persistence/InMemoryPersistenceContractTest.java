package io.github.openflowkernel.example.persistence;

import io.github.openflowkernel.example.candidate.InMemoryTaskCandidateRepository;
import io.github.openflowkernel.persistence.contract.PersistenceRepositories;
import io.github.openflowkernel.persistence.contract.PersistenceRepositoryContract;
import org.junit.jupiter.api.BeforeEach;

class InMemoryPersistenceContractTest implements PersistenceRepositoryContract {
    private PersistenceRepositories repositories;

    @BeforeEach
    void setUp() {
        repositories = new PersistenceRepositories(
            new InMemoryProcessDefinitionRepository(),
            new InMemoryProcessInstanceRepository(),
            new InMemoryTaskInstanceRepository(),
            new InMemoryTaskRelationRepository(),
            new InMemoryTaskCandidateRepository()
        );
    }

    @Override
    public PersistenceRepositories repositories() {
        return repositories;
    }
}
