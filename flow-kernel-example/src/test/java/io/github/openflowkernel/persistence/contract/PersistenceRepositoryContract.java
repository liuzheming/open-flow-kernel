package io.github.openflowkernel.persistence.contract;

import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;
import io.github.openflowkernel.core.candidate.TaskCandidate;
import io.github.openflowkernel.core.candidate.TaskCandidateService;
import io.github.openflowkernel.core.enums.ProcStatusEnum;
import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessInstance;
import io.github.openflowkernel.core.relation.TaskRelation;
import io.github.openflowkernel.core.relation.TaskRelationStatus;
import io.github.openflowkernel.core.task.TaskDefinition;
import io.github.openflowkernel.core.task.TaskInstanceRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public interface PersistenceRepositoryContract {
    PersistenceRepositories repositories();

    @Test
    default void savesAndLoadsProcessDefinitionWithTaskConfig() {
        PersistenceRepositories repositories = repositories();

        repositories.definitionRepository().save(definition());

        ProcessDefinition loaded = repositories.definitionRepository()
            .findByKey("order-flow")
            .orElseThrow();
        assertThat(loaded.name()).isEqualTo("Order Flow");
        assertThat(loaded.tasks()).hasSize(2);
        assertThat(loaded.task("submit").handlerName()).isEqualTo("submitTask");
        assertThat(loaded.task("submit").config())
            .containsEntry(TaskConfigKeyConstant.FORM_DEF_ID, "submit-form");
    }

    @Test
    default void persistsProcessInstanceDataAndStatusCas() {
        PersistenceRepositories repositories = repositories();
        repositories.definitionRepository().save(definition());

        ProcessInstance created = repositories.processRepository().create(
            "order-flow",
            "Order Flow",
            Map.of("orderNo", "O-1")
        );

        assertThat(created.status()).isEqualTo(ProcStatusEnum.INIT);
        assertThat(created.data()).containsEntry("orderNo", "O-1");
        repositories.processRepository().mergeData(created.id(), Map.of("amount", "100"));
        assertThat(repositories.processRepository().findById(created.id()).orElseThrow().data())
            .containsEntry("orderNo", "O-1")
            .containsEntry("amount", "100");
        assertThat(repositories.processRepository().compareAndSetStatus(
            created.id(),
            ProcStatusEnum.SUSPEND,
            ProcStatusEnum.NORMAL_END
        )).isFalse();
        assertThat(repositories.processRepository().compareAndSetStatus(
            created.id(),
            ProcStatusEnum.INIT,
            ProcStatusEnum.NORMAL_END
        )).isTrue();
        assertThat(repositories.processRepository().findById(created.id()).orElseThrow().status())
            .isEqualTo(ProcStatusEnum.NORMAL_END);
    }

    @Test
    default void persistsSubProcessParentRelations() {
        PersistenceRepositories repositories = repositories();
        repositories.definitionRepository().save(definition());

        ProcessInstance parent = repositories.processRepository().create(
            "order-flow",
            "Order Flow",
            Map.of("orderNo", "O-1")
        );
        ProcessInstance first = repositories.processRepository().createSubProcess(
            "order-flow",
            "Order Sub Flow",
            parent.id(),
            101,
            Map.of("lineNo", "L-1")
        );
        ProcessInstance second = repositories.processRepository().createSubProcess(
            "order-flow",
            "Order Sub Flow",
            parent.id(),
            102,
            Map.of("lineNo", "L-2")
        );

        assertThat(repositories.processRepository().findById(first.id()).orElseThrow())
            .extracting(
                ProcessInstance::relateProcessInstanceId,
                ProcessInstance::relateTaskInstanceId
            )
            .containsExactly(parent.id(), 101L);
        assertThat(repositories.processRepository().findSubProcesses(parent.id()))
            .extracting(ProcessInstance::id)
            .containsExactly(first.id(), second.id());
        assertThat(repositories.processRepository().findSubProcesses(parent.id(), 102))
            .extracting(ProcessInstance::id)
            .containsExactly(second.id());
    }

    @Test
    default void persistsTaskLatestDataAndStatusCas() {
        PersistenceRepositories repositories = repositories();
        repositories.definitionRepository().save(definition());
        ProcessInstance process = repositories.processRepository()
            .create("order-flow", "Order Flow", Map.of());

        TaskInstanceRecord first = repositories.taskRepository().create(
            process.id(),
            "engine-1",
            "submit",
            "Submit",
            Map.of("a", "1")
        );
        TaskInstanceRecord second = repositories.taskRepository().create(
            process.id(),
            "engine-2",
            "submit",
            "Submit"
        );

        assertThat(repositories.taskRepository().findByEngineTaskId("engine-1").orElseThrow().id())
            .isEqualTo(first.id());
        assertThat(repositories.taskRepository().findLatest(process.id(), "submit").orElseThrow().id())
            .isEqualTo(second.id());
        repositories.taskRepository().mergeData(second.id(), Map.of("b", "2"));
        assertThat(repositories.taskRepository().findById(second.id()).orElseThrow().data())
            .containsEntry("b", "2");
        assertThat(repositories.taskRepository().compareAndSetStatus(
            second.id(),
            ProcTaskStatusEnum.INIT,
            ProcTaskStatusEnum.COMPLETE
        )).isFalse();
        assertThat(repositories.taskRepository().compareAndSetStatus(
            second.id(),
            ProcTaskStatusEnum.CREATE,
            ProcTaskStatusEnum.INIT
        )).isTrue();
    }

    @Test
    default void persistsTaskRelationsAndStatusCas() {
        PersistenceRepositories repositories = repositories();
        repositories.definitionRepository().save(definition());
        ProcessInstance process = repositories.processRepository()
            .create("order-flow", "Order Flow", Map.of());
        TaskInstanceRecord task = repositories.taskRepository().create(
            process.id(),
            "engine-1",
            "submit",
            "Submit"
        );

        TaskRelation created = repositories.relationRepository()
            .create(task.id(), "RELATION_FORM", "10");
        TaskRelation second = repositories.relationRepository()
            .create(task.id(), "RELATION_FORM", "11");

        assertThat(second.id()).isGreaterThan(created.id());
        assertThat(repositories.relationRepository().find("RELATION_FORM", "10"))
            .isPresent();
        assertThat(repositories.relationRepository().findByTaskInstanceId(task.id()))
            .hasSize(2);
        assertThat(repositories.relationRepository().compareAndSetCompleted(created.id()))
            .isTrue();
        assertThat(repositories.relationRepository().compareAndSetCompleted(created.id()))
            .isFalse();
        assertThat(repositories.relationRepository().findByTaskInstanceId(task.id()).get(0).status())
            .isEqualTo(TaskRelationStatus.COMPLETED);
    }

    @Test
    default void persistsAndDiffsTaskCandidates() {
        PersistenceRepositories repositories = repositories();
        repositories.definitionRepository().save(definition());
        ProcessInstance process = repositories.processRepository()
            .create("order-flow", "Order Flow", Map.of());
        TaskInstanceRecord task = repositories.taskRepository().create(
            process.id(),
            "engine-1",
            "submit",
            "Submit"
        );
        TaskCandidateService service = new TaskCandidateService(
            repositories.taskCandidateRepository()
        );

        service.diffAndUpdateCandidates(
            process.id(),
            task.id(),
            List.of(
                new TaskCandidate("100", "u100", "Alice"),
                new TaskCandidate("100", "u100-duplicate", "Alice Duplicate"),
                new TaskCandidate("101", "u101", "Bob")
            )
        );

        assertThat(repositories.taskCandidateRepository().list(task.id()))
            .extracting(TaskCandidate::getUcid)
            .containsExactly("100", "101");
        assertThat(repositories.taskCandidateRepository().listAllByProInstId(process.id()))
            .extracting(TaskCandidate::getUcid)
            .containsExactly("100", "101");

        service.diffAndUpdateCandidates(
            process.id(),
            task.id(),
            List.of(new TaskCandidate("101", "u101", "Bob"))
        );

        assertThat(repositories.taskCandidateRepository().list(task.id()))
            .extracting(TaskCandidate::getUcid)
            .containsExactly("101");
        assertThat(repositories.taskCandidateRepository()
            .distinctCandidateByTaskInstIds(List.of(task.id())))
            .extracting(TaskCandidate::getUcid)
            .containsExactly("101");
    }

    static ProcessDefinition definition() {
        return new ProcessDefinition(
            "order-flow",
            "Order Flow",
            List.of(
                new TaskDefinition(
                    "submit",
                    "Submit",
                    "submitTask",
                    Map.of(TaskConfigKeyConstant.FORM_DEF_ID, "submit-form")
                ),
                new TaskDefinition("review", "Review", "reviewTask")
            )
        );
    }
}
