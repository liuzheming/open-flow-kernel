package io.github.openflowkernel.persistence.contract;

import io.github.openflowkernel.core.process.ProcessDefinitionRepository;
import io.github.openflowkernel.core.process.ProcessInstanceRepository;
import io.github.openflowkernel.core.relation.TaskRelationRepository;
import io.github.openflowkernel.core.candidate.TaskCandidateRepository;
import io.github.openflowkernel.core.task.TaskInstanceRepository;

public record PersistenceRepositories(
    ProcessDefinitionRepository definitionRepository,
    ProcessInstanceRepository processRepository,
    TaskInstanceRepository taskRepository,
    TaskRelationRepository relationRepository,
    TaskCandidateRepository taskCandidateRepository
) {
}
