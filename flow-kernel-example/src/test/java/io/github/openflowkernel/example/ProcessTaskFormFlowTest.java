package io.github.openflowkernel.example;

import io.github.openflowkernel.core.constant.TaskInstDataKeyConstant;
import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;
import io.github.openflowkernel.core.candidate.TaskCandidate;
import io.github.openflowkernel.core.candidate.TaskCandidateService;
import io.github.openflowkernel.core.engine.EngineTaskDeleted;
import io.github.openflowkernel.core.engine.EngineProcessCompleted;
import io.github.openflowkernel.core.engine.EngineTaskCompleted;
import io.github.openflowkernel.core.engine.EngineTaskCreated;
import io.github.openflowkernel.core.engine.WorkflowEventListener;
import io.github.openflowkernel.core.engine.WorkflowEngineListener;
import io.github.openflowkernel.form.event.FormSubmitted;
import io.github.openflowkernel.form.event.FormSubmittedEventListener;
import io.github.openflowkernel.core.event.ProcessEvent;
import io.github.openflowkernel.core.event.ProcessTaskEvent;
import io.github.openflowkernel.core.event.SubProcessCompleteListener;
import io.github.openflowkernel.core.event.TaskInitCompleteEventListener;
import io.github.openflowkernel.core.event.TaskInitializationEventListener;
import io.github.openflowkernel.core.enums.ProcStatusEnum;
import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.form.FormCompleteListener;
import io.github.openflowkernel.form.FormInstance;
import io.github.openflowkernel.form.FormService;
import io.github.openflowkernel.form.FormTask;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessService;
import io.github.openflowkernel.core.relation.TaskRelationService;
import io.github.openflowkernel.core.task.TaskContext;
import io.github.openflowkernel.core.task.TaskDefinition;
import io.github.openflowkernel.core.task.TaskFactory;
import io.github.openflowkernel.core.task.TaskInstanceRecord;
import io.github.openflowkernel.core.task.TaskResult;
import io.github.openflowkernel.core.task.TaskRuntime;
import io.github.openflowkernel.core.task.SubProcessTask;
import io.github.openflowkernel.event.BackoffPolicy;
import io.github.openflowkernel.event.EventBus;
import io.github.openflowkernel.event.EventExecutionMode;
import io.github.openflowkernel.event.EventListenerRegistration;
import io.github.openflowkernel.event.RetryPolicy;
import io.github.openflowkernel.example.candidate.InMemoryTaskCandidateRepository;
import io.github.openflowkernel.example.event.AtomicEventIdGenerator;
import io.github.openflowkernel.example.event.DirectEventExecutor;
import io.github.openflowkernel.example.event.ImmediateTransactionBoundary;
import io.github.openflowkernel.example.event.InMemoryEventStore;
import io.github.openflowkernel.example.engine.InMemoryWorkflowEngine;
import io.github.openflowkernel.example.form.InMemoryFormService;
import io.github.openflowkernel.example.packet.InMemoryPacketDataRepo;
import io.github.openflowkernel.example.packet.InMemoryPacketRepo;
import io.github.openflowkernel.example.persistence.InMemoryProcessDefinitionRepository;
import io.github.openflowkernel.example.persistence.InMemoryProcessInstanceRepository;
import io.github.openflowkernel.example.persistence.InMemoryTaskInstanceRepository;
import io.github.openflowkernel.example.persistence.InMemoryTaskRelationRepository;
import io.github.openflowkernel.packet.PacketStatusEnum;
import io.github.openflowkernel.packet.PacketValueRecord;
import io.github.openflowkernel.packet.PacketService;
import io.github.openflowkernel.packet.event.PacketEventListener;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessTaskFormFlowTest {

    @Test
    void completesProcessThroughTwoFormTasks() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "application-review",
            Map.of("applicant", "Alice")
        );
        fixture.packetService.initPacketDataRecord(
            processInstanceId,
            "{\"applicant\":\"Alice\"}",
            "{\"source\":\"start\"}"
        );

        TaskInstanceRecord applicationTask = fixture.requiredTask(
            processInstanceId,
            "application"
        );
        assertThat(applicationTask.status()).isEqualTo(ProcTaskStatusEnum.INIT);
        long applicationFormId = formInstanceId(applicationTask);
        assertThat(fixture.formService.get(applicationFormId).definitionKey())
            .isEqualTo("application-form");
        fixture.candidateService.diffAndUpdateCandidates(
            processInstanceId,
            applicationTask.id(),
            List.of(
                new TaskCandidate("1001", "alice", "Alice"),
                new TaskCandidate("1001", "alice-duplicate", "Alice Duplicate"),
                new TaskCandidate("1002", "bob", "Bob")
            )
        );
        assertThat(fixture.candidateRepository.listEntities(applicationTask.id()))
            .extracting(candidate -> candidate.ucid())
            .containsExactlyInAnyOrder("1001", "1002");

        fixture.formService.submit(applicationFormId, Map.of("amount", "1200"));
        fixture.packetService.commit(
            processInstanceId,
            applicationTask.id(),
            "application",
            packet -> new PacketValueRecord(
                packet.id(),
                packet.processInstanceId(),
                packet.processTaskInstanceId(),
                packet.taskCode(),
                packet.initValue(),
                "{\"amount\":\"1200\"}",
                packet.initSource(),
                "{\"source\":\"application\"}",
                packet.status(),
                packet.createdAt(),
                packet.updatedAt()
            )
        );

        assertThat(fixture.requiredTask(processInstanceId, "application").status())
            .isEqualTo(ProcTaskStatusEnum.COMPLETE);
        assertThat(fixture.processService.get(processInstanceId).data())
            .containsEntry("amount", "1200");
        assertThat(fixture.packetService.getLatest(processInstanceId))
            .get()
            .extracting(PacketValueRecord::value)
            .isEqualTo("{\"amount\":\"1200\"}");

        TaskInstanceRecord reviewTask = fixture.requiredTask(processInstanceId, "review");
        assertThat(reviewTask.status()).isEqualTo(ProcTaskStatusEnum.INIT);
        long reviewFormId = formInstanceId(reviewTask);
        assertThat(fixture.formService.get(reviewFormId).definitionKey())
            .isEqualTo("review-form");

        fixture.formService.submit(reviewFormId, Map.of(
            "decision", "approved",
            "comment", "Looks good"
        ));

        assertThat(fixture.requiredTask(processInstanceId, "review").status())
            .isEqualTo(ProcTaskStatusEnum.COMPLETE);
        assertThat(fixture.processService.get(processInstanceId).status())
            .isEqualTo(ProcStatusEnum.NORMAL_END);
        assertThat(fixture.processService.get(processInstanceId).data())
            .containsEntry("decision", "approved")
            .containsEntry("reviewComment", "Looks good")
            .containsEntry("reviewPostComplete", "true");
        assertThat(fixture.packetRepository.getPacket(processInstanceId))
            .get()
            .extracting(packet -> packet.status())
            .isEqualTo(PacketStatusEnum.COMPLETED);

        fixture.formService.submit(reviewFormId, Map.of("decision", "rejected"));

        assertThat(fixture.processService.get(processInstanceId).status())
            .isEqualTo(ProcStatusEnum.NORMAL_END);
        assertThat(fixture.processService.get(processInstanceId).data())
            .containsEntry("decision", "approved");
    }

    @Test
    void taskInitRefreshesCandidatesFromSelectionConfig() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "application-review",
            Map.of(
                "applicant", "Alice",
                "reviewerUcid", "2001",
                "reviewerCode", "reviewer-a",
                "reviewerName", "Reviewer A"
            )
        );

        TaskInstanceRecord applicationTask = fixture.requiredTask(
            processInstanceId,
            "application"
        );

        assertThat(fixture.candidateRepository.listEntities(applicationTask.id()))
            .extracting(candidate -> candidate.ucid())
            .containsExactly("2001");
    }

    @Test
    void processServiceInvokesStartHookBeforeWorkflowStart() {
        InMemoryProcessDefinitionRepository definitionRepository =
            new InMemoryProcessDefinitionRepository();
        InMemoryProcessInstanceRepository processRepository =
            new InMemoryProcessInstanceRepository();
        InMemoryWorkflowEngine workflowEngine = new InMemoryWorkflowEngine();
        AtomicBoolean hookCalled = new AtomicBoolean(false);
        AtomicBoolean workflowStartedAfterHook = new AtomicBoolean(false);
        ProcessDefinition definition = new ProcessDefinition(
            "hook-flow",
            "Hook Flow",
            List.of(new TaskDefinition("noop", "Noop", "noop"))
        );
        definitionRepository.save(definition);
        workflowEngine.deploy(definition);
        workflowEngine.setListener(new WorkflowEngineListener() {
            @Override
            public void onTaskCreated(EngineTaskCreated event) {
                workflowStartedAfterHook.set(hookCalled.get());
            }

            @Override
            public void onTaskCompleted(EngineTaskCompleted event) {
            }

            @Override
            public void onTaskDeleted(EngineTaskDeleted event) {
            }

            @Override
            public void onProcessCompleted(EngineProcessCompleted event) {
            }
        });
        ProcessService processService = new ProcessService(
            definitionRepository,
            processRepository,
            workflowEngine,
            null,
            List.of((processDefinition, processInstance, initialData) -> {
                assertThat(processDefinition.key()).isEqualTo("hook-flow");
                assertThat(processInstance.id()).isGreaterThan(0);
                assertThat(initialData).containsEntry("source", "test");
                hookCalled.set(true);
            })
        );

        processService.start("hook-flow", Map.of("source", "test"));

        assertThat(hookCalled).isTrue();
        assertThat(workflowStartedAfterHook).isTrue();
    }

    @Test
    void taskFactoryCancelsActiveLatestTaskBeforeCreatingReentryTask() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "application-review",
            Map.of("applicant", "Alice")
        );
        TaskInstanceRecord firstTask = fixture.requiredTask(
            processInstanceId,
            "application"
        );

        fixture.taskFactory.createTask(
            processInstanceId,
            "manual-reentry-1",
            "application",
            fixture.taskConfig("application")
        );

        assertThat(fixture.taskRepository.findById(firstTask.id()).orElseThrow().status())
            .isEqualTo(ProcTaskStatusEnum.CANCEL);
        TaskInstanceRecord latestTask = fixture.requiredTask(processInstanceId, "application");
        assertThat(latestTask.id()).isGreaterThan(firstTask.id());
        assertThat(latestTask.status()).isEqualTo(ProcTaskStatusEnum.CREATE);
    }

    @Test
    void taskFactoryLinksCompletedLatestTaskAsLastTaskInstance() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "application-review",
            Map.of("applicant", "Alice")
        );
        TaskInstanceRecord completedTask = fixture.requiredTask(
            processInstanceId,
            "application"
        );
        fixture.formService.submit(formInstanceId(completedTask), Map.of("amount", "1200"));

        fixture.taskFactory.createTask(
            processInstanceId,
            "manual-reentry-2",
            "application",
            fixture.taskConfig("application")
        );

        TaskInstanceRecord latestTask = fixture.requiredTask(processInstanceId, "application");
        assertThat(latestTask.id()).isGreaterThan(completedTask.id());
        assertThat(latestTask.status()).isEqualTo(ProcTaskStatusEnum.CREATE);
        assertThat(latestTask.data())
            .containsEntry(
                TaskInstDataKeyConstant.LAST_TASK_INST_ID,
                Long.toString(completedTask.id())
            );
    }

    @Test
    void taskInitResultCanMoveCreateTaskToFailedStatus() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start("init-control", Map.of());

        TaskInstanceRecord task = fixture.requiredTask(processInstanceId, "failInit");
        assertThat(task.status()).isEqualTo(ProcTaskStatusEnum.FAILED);
    }

    @Test
    void formTaskUsesConfiguredInputAndOutputMappings() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "configured-form",
            Map.of("applicant", "Alice")
        );
        TaskInstanceRecord task = fixture.requiredTask(processInstanceId, "configuredForm");
        long formId = formInstanceId(task);
        assertThat(fixture.formService.get(formId).initialData())
            .containsEntry("applicantName", "Alice");

        fixture.formService.submit(formId, Map.of("result", "approved"));

        assertThat(fixture.requiredTask(processInstanceId, "configuredForm").status())
            .isEqualTo(ProcTaskStatusEnum.COMPLETE);
        assertThat(fixture.processService.get(processInstanceId).data())
            .containsEntry("configuredDecision", "approved");
    }

    @Test
    void formTaskSupportsConfiguredMultiFormMaps() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "multi-form",
            Map.of("applicant", "Alice")
        );
        TaskInstanceRecord task = fixture.requiredTask(processInstanceId, "multiForm");
        Map<String, String> formMap = parseFlatObject(task.data().get(FormTask.FORM_INSTANCE_MAP));
        long mainFormId = Long.parseLong(formMap.get("main"));
        long attachmentFormId = Long.parseLong(formMap.get("attachment"));

        assertThat(fixture.formService.get(mainFormId).definitionKey())
            .isEqualTo("main-form");
        assertThat(fixture.formService.get(mainFormId).initialData())
            .containsEntry("mainApplicant", "Alice");
        assertThat(fixture.formService.get(attachmentFormId).definitionKey())
            .isEqualTo("attachment-form");
        assertThat(fixture.formService.get(attachmentFormId).initialData())
            .containsEntry("attachmentApplicant", "Alice");
        assertThat(fixture.relationRepository.findByTaskInstanceId(task.id()))
            .hasSize(2);

        fixture.formService.submit(mainFormId, Map.of("result", "main-ok"));

        assertThat(fixture.requiredTask(processInstanceId, "multiForm").status())
            .isEqualTo(ProcTaskStatusEnum.INIT);

        fixture.formService.submit(attachmentFormId, Map.of("result", "attachment-ok"));

        assertThat(fixture.requiredTask(processInstanceId, "multiForm").status())
            .isEqualTo(ProcTaskStatusEnum.COMPLETE);
        assertThat(fixture.processService.get(processInstanceId).status())
            .isEqualTo(ProcStatusEnum.NORMAL_END);
        assertThat(fixture.processService.get(processInstanceId).data())
            .containsEntry("mainResult", "main-ok")
            .containsEntry("attachmentResult", "attachment-ok");
    }

    @Test
    void taskInstanceAppliesConfiguredOutputBeforeBeforeComplete() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "configured-output",
            Map.of("source", "flow-value")
        );

        fixture.taskFactory.getTask(processInstanceId, "outputTask")
            .complete(TaskResult.builder()
                .putTaskInstData("taskValue", "task-value")
                .build());

        assertThat(fixture.processService.get(processInstanceId).data())
            .containsEntry("fromFlow", "flow-value")
            .containsEntry("fromTask", "task-value");
    }

    @Test
    void taskInitCompleteEventRunsAfterInit() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start("after-init", Map.of());

        assertThat(fixture.processService.get(processInstanceId).data())
            .containsEntry("afterInit", "true");
        assertThat(fixture.requiredTask(processInstanceId, "afterInitTask").status())
            .isEqualTo(ProcTaskStatusEnum.COMPLETE);
    }

    @Test
    void taskRelationRegistrationIsIdempotentPerTask() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "application-review",
            Map.of("applicant", "Alice")
        );
        TaskInstanceRecord task = fixture.requiredTask(processInstanceId, "application");
        long formInstanceId = formInstanceId(task);

        fixture.relationService.register(
            task.id(),
            FormTask.RELATION_TYPE,
            Long.toString(formInstanceId)
        );

        assertThat(fixture.relationRepository.findByTaskInstanceId(task.id()))
            .hasSize(1);
    }

    @Test
    void processServiceSupportsSuspendContinueAndCancel() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "application-review",
            Map.of("applicant", "Alice")
        );

        fixture.processService.suspend(processInstanceId);
        assertThat(fixture.processService.get(processInstanceId).status())
            .isEqualTo(ProcStatusEnum.SUSPEND);

        fixture.processService.processContinue(processInstanceId);
        assertThat(fixture.processService.get(processInstanceId).status())
            .isEqualTo(ProcStatusEnum.INIT);

        fixture.processService.cancel(processInstanceId);
        assertThat(fixture.processService.get(processInstanceId).status())
            .isEqualTo(ProcStatusEnum.CANCEL);
    }

    @Test
    void workflowDeleteEventCancelsBusinessTask() {
        Fixture fixture = new Fixture();

        long processInstanceId = fixture.processService.start(
            "application-review",
            Map.of("applicant", "Alice")
        );
        TaskInstanceRecord task = fixture.requiredTask(processInstanceId, "application");

        fixture.workflowListener.onTaskDeleted(new EngineTaskDeleted(
            "application-review",
            Long.toString(processInstanceId),
            "application",
            task.requestId()
        ));

        assertThat(fixture.taskRepository.findById(task.id()).orElseThrow().status())
            .isEqualTo(ProcTaskStatusEnum.CANCEL);
    }

    @Test
    void subProcessTaskCompletesParentAfterChildProcessComplete() {
        Fixture fixture = new Fixture();

        long parentProcessId = fixture.processService.start(
            "parent-sub-process",
            Map.of("parentInput", "from-parent")
        );
        TaskInstanceRecord parentTask = fixture.requiredTask(parentProcessId, "createChild");
        assertThat(parentTask.status()).isEqualTo(ProcTaskStatusEnum.INIT);

        var childProcess = fixture.processRepository.findSubProcesses(
            parentProcessId,
            parentTask.id()
        ).get(0);
        assertThat(childProcess.data())
            .containsEntry("childInput", "from-parent")
            .containsEntry("PROC_TYPE", "relate");
        assertThat(fixture.relationRepository.findByTaskInstanceId(parentTask.id()))
            .singleElement()
            .satisfies(relation -> {
                assertThat(relation.relationType()).isEqualTo("subProcessTaskType");
                assertThat(relation.relationInstanceId())
                    .isEqualTo(Long.toString(childProcess.id()));
            });

        fixture.taskFactory.getTask(childProcess.id(), "childTask")
            .complete(TaskResult.builder()
                .putProcessInstData("result", "child-done")
                .build());

        assertThat(fixture.requiredTask(parentProcessId, "createChild").status())
            .isEqualTo(ProcTaskStatusEnum.COMPLETE);
        assertThat(fixture.processService.get(parentProcessId).status())
            .isEqualTo(ProcStatusEnum.NORMAL_END);
        assertThat(fixture.processService.get(parentProcessId).data())
            .containsEntry("childResult", "child-done");
    }

    @Test
    void subProcessTaskSupportsConfiguredMultipleChildren() {
        Fixture fixture = new Fixture();

        long parentProcessId = fixture.processService.start(
            "parent-multi-sub-process",
            Map.of("parentInput", "from-parent")
        );
        TaskInstanceRecord parentTask = fixture.requiredTask(parentProcessId, "createChildren");
        var children = fixture.processRepository.findSubProcesses(
            parentProcessId,
            parentTask.id()
        );

        assertThat(children)
            .extracting(child -> child.definitionKey())
            .containsExactlyInAnyOrder("child-flow-a", "child-flow-b");
        assertThat(fixture.relationRepository.findByTaskInstanceId(parentTask.id()))
            .hasSize(2);

        long childA = children.stream()
            .filter(child -> child.definitionKey().equals("child-flow-a"))
            .findFirst()
            .orElseThrow()
            .id();
        long childB = children.stream()
            .filter(child -> child.definitionKey().equals("child-flow-b"))
            .findFirst()
            .orElseThrow()
            .id();

        fixture.taskFactory.getTask(childA, "childTask")
            .complete(TaskResult.builder().build());

        assertThat(fixture.requiredTask(parentProcessId, "createChildren").status())
            .isEqualTo(ProcTaskStatusEnum.INIT);

        fixture.taskFactory.getTask(childB, "childTask")
            .complete(TaskResult.builder().build());

        assertThat(fixture.requiredTask(parentProcessId, "createChildren").status())
            .isEqualTo(ProcTaskStatusEnum.COMPLETE);
        assertThat(fixture.processService.get(parentProcessId).status())
            .isEqualTo(ProcStatusEnum.NORMAL_END);
    }

    private static long formInstanceId(TaskInstanceRecord task) {
        return Long.parseLong(task.data().get(FormTask.FORM_INSTANCE_ID));
    }

    private static Map<String, String> parseFlatObject(String value) {
        String body = value.substring(1, value.length() - 1);
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        for (String item : body.split(",")) {
            String[] pair = item.split(":", 2);
            result.put(unquote(pair[0]), unquote(pair[1]));
        }
        return result;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static final class Fixture {
        private final InMemoryProcessDefinitionRepository definitionRepository =
            new InMemoryProcessDefinitionRepository();
        private final InMemoryProcessInstanceRepository processRepository =
            new InMemoryProcessInstanceRepository();
        private final InMemoryTaskInstanceRepository taskRepository =
            new InMemoryTaskInstanceRepository();
        private final InMemoryTaskRelationRepository relationRepository =
            new InMemoryTaskRelationRepository();
        private final InMemoryTaskCandidateRepository candidateRepository =
            new InMemoryTaskCandidateRepository();
        private final InMemoryPacketDataRepo packetDataRepository =
            new InMemoryPacketDataRepo();
        private final InMemoryPacketRepo packetRepository =
            new InMemoryPacketRepo(packetDataRepository);
        private final InMemoryWorkflowEngine workflowEngine = new InMemoryWorkflowEngine();
        private final FormService formService = new InMemoryFormService();
        private final EventBus eventBus;
        private final ProcessService processService;
        private final TaskFactory taskFactory;
        private final WorkflowEventListener workflowListener;
        private final TaskRelationService relationService;
        private final TaskCandidateService candidateService;
        private final PacketService packetService;

        private Fixture() {
            ProcessDefinition definition = new ProcessDefinition(
                "application-review",
                "Application Review",
                List.of(
                    new TaskDefinition(
                        "application",
                        "Submit Application",
                        "applicationFormTask",
                        Map.of(
                            TaskConfigKeyConstant.FORM_DEF_ID,
                            "application-form",
                            TaskConfigKeyConstant.TASK_CANDIDATE,
                            "reviewer-from-process-data"
                        )
                    ),
                    new TaskDefinition(
                        "review",
                        "Review Application",
                        "reviewFormTask",
                        Map.of(TaskConfigKeyConstant.FORM_DEF_ID, "review-form")
                    )
                )
            );
            definitionRepository.save(definition);
            workflowEngine.deploy(definition);
            ProcessDefinition initControlDefinition = new ProcessDefinition(
                "init-control",
                "Init Control",
                List.of(new TaskDefinition(
                    "failInit",
                    "Fail Init",
                    "failingInitTask"
                ))
            );
            definitionRepository.save(initControlDefinition);
            workflowEngine.deploy(initControlDefinition);
            ProcessDefinition configuredFormDefinition = new ProcessDefinition(
                "configured-form",
                "Configured Form",
                List.of(new TaskDefinition(
                    "configuredForm",
                    "Configured Form",
                    "configDrivenFormTask",
                    Map.of(
                        TaskConfigKeyConstant.FORM_DEF_ID,
                        "configured-form-def",
                        TaskConfigKeyConstant.FORM_INPUT,
                        "{\"applicantName\":\"${flow.applicant}\"}",
                        TaskConfigKeyConstant.FORM_OUTPUT,
                        "{\"configuredDecision\":\"${value.result}\"}"
                    )
                ))
            );
            definitionRepository.save(configuredFormDefinition);
            workflowEngine.deploy(configuredFormDefinition);
            ProcessDefinition configuredOutputDefinition = new ProcessDefinition(
                "configured-output",
                "Configured Output",
                List.of(new TaskDefinition(
                    "outputTask",
                    "Output Task",
                    "outputTask",
                    Map.of(
                        TaskConfigKeyConstant.OUTPUT,
                        "{\"fromFlow\":\"${flow.source}\",\"fromTask\":\"${task.taskValue}\"}"
                    )
                ))
            );
            definitionRepository.save(configuredOutputDefinition);
            workflowEngine.deploy(configuredOutputDefinition);
            ProcessDefinition multiFormDefinition = new ProcessDefinition(
                "multi-form",
                "Multi Form",
                List.of(new TaskDefinition(
                    "multiForm",
                    "Multi Form",
                    "configDrivenFormTask",
                    Map.of(
                        TaskConfigKeyConstant.FORM_DEF_MAP,
                        "{\"main\":\"main-form\",\"attachment\":\"attachment-form\"}",
                        TaskConfigKeyConstant.FORM_DEF_INDEX,
                        "main",
                        TaskConfigKeyConstant.FORM_INPUT_MAP,
                        "{\"main\":\"{\\\"mainApplicant\\\":\\\"${flow.applicant}\\\"}\","
                            + "\"attachment\":\"{\\\"attachmentApplicant\\\":"
                            + "\\\"${flow.applicant}\\\"}\"}",
                        TaskConfigKeyConstant.FORM_OUTPUT_MAP,
                        "{\"main\":\"{\\\"mainResult\\\":\\\"${value.result}\\\"}\","
                            + "\"attachment\":\"{\\\"attachmentResult\\\":"
                            + "\\\"${value.result}\\\"}\"}"
                    )
                ))
            );
            definitionRepository.save(multiFormDefinition);
            workflowEngine.deploy(multiFormDefinition);
            ProcessDefinition afterInitDefinition = new ProcessDefinition(
                "after-init",
                "After Init",
                List.of(new TaskDefinition(
                    "afterInitTask",
                    "After Init Task",
                    "afterInitTask"
                ))
            );
            definitionRepository.save(afterInitDefinition);
            workflowEngine.deploy(afterInitDefinition);
            ProcessDefinition childDefinition = new ProcessDefinition(
                "child-flow",
                "Child Flow",
                List.of(new TaskDefinition("childTask", "Child Task", "childTask"))
            );
            definitionRepository.save(childDefinition);
            workflowEngine.deploy(childDefinition);
            ProcessDefinition childDefinitionA = new ProcessDefinition(
                "child-flow-a",
                "Child Flow A",
                List.of(new TaskDefinition("childTask", "Child Task", "childTask"))
            );
            definitionRepository.save(childDefinitionA);
            workflowEngine.deploy(childDefinitionA);
            ProcessDefinition childDefinitionB = new ProcessDefinition(
                "child-flow-b",
                "Child Flow B",
                List.of(new TaskDefinition("childTask", "Child Task", "childTask"))
            );
            definitionRepository.save(childDefinitionB);
            workflowEngine.deploy(childDefinitionB);
            ProcessDefinition parentSubProcessDefinition = new ProcessDefinition(
                "parent-sub-process",
                "Parent Sub Process",
                List.of(new TaskDefinition(
                    "createChild",
                    "Create Child",
                    "subProcessTask",
                    Map.of(
                        TaskConfigKeyConstant.SUB_PROCESS_DEF_CODE,
                        "child-flow",
                        TaskConfigKeyConstant.FORM_INPUT,
                        "{\"childInput\":\"${flow.parentInput}\"}",
                        TaskConfigKeyConstant.FORM_OUTPUT,
                        "{\"childResult\":\"${value.result}\"}"
                    )
                ))
            );
            definitionRepository.save(parentSubProcessDefinition);
            workflowEngine.deploy(parentSubProcessDefinition);
            ProcessDefinition parentMultiSubProcessDefinition = new ProcessDefinition(
                "parent-multi-sub-process",
                "Parent Multi Sub Process",
                List.of(new TaskDefinition(
                    "createChildren",
                    "Create Children",
                    "subProcessTask",
                    Map.of(
                        TaskConfigKeyConstant.SUB_PROCESS_DEF_CODE_MAP,
                        "{\"first\":\"child-flow-a\",\"second\":\"child-flow-b\"}",
                        TaskConfigKeyConstant.FORM_INPUT,
                        "{\"childInput\":\"${flow.parentInput}\"}"
                    )
                ))
            );
            definitionRepository.save(parentMultiSubProcessDefinition);
            workflowEngine.deploy(parentMultiSubProcessDefinition);

            candidateService = new TaskCandidateService(
                candidateRepository,
                (selectionConfig, processInstData) -> {
                    if (!"reviewer-from-process-data".equals(selectionConfig)
                        || !processInstData.containsKey("reviewerUcid")) {
                        return List.of();
                    }
                    return List.of(new TaskCandidate(
                        processInstData.get("reviewerUcid"),
                        processInstData.getOrDefault("reviewerCode", ""),
                        processInstData.getOrDefault("reviewerName", "")
                    ));
                }
            );
            TaskRuntime runtime = new TaskRuntime(
                definitionRepository,
                processRepository,
                taskRepository,
                workflowEngine,
                relationRepository,
                candidateService
            );
            taskFactory = new TaskFactory(runtime);
            relationService = new TaskRelationService(
                relationRepository,
                taskRepository,
                processRepository,
                definitionRepository,
                taskFactory
            );
            packetService = new PacketService(packetRepository, packetDataRepository);
            Clock clock = Clock.systemUTC();
            eventBus = new EventBus(
                new AtomicEventIdGenerator(),
                new InMemoryEventStore(clock),
                new ImmediateTransactionBoundary(),
                new DirectEventExecutor(),
                clock
            );
            eventBus.register(new EventListenerRegistration<>(
                "task-initialization-listener",
                ProcessTaskEvent.class,
                EventExecutionMode.ASYNCHRONOUS,
                new RetryPolicy(
                    20,
                    new BackoffPolicy(
                        Duration.ofSeconds(1),
                        2,
                        Duration.ofHours(1)
                    ),
                    List.of(RuntimeException.class),
                    List.of(IllegalArgumentException.class)
                ),
                new TaskInitializationEventListener(
                    definitionRepository,
                    taskRepository,
                    taskFactory,
                    eventBus
                )
            ));
            eventBus.register(new EventListenerRegistration<>(
                "task-init-complete-listener",
                ProcessTaskEvent.class,
                EventExecutionMode.ASYNCHRONOUS,
                RetryPolicy.noRetry(),
                new TaskInitCompleteEventListener(taskRepository, taskFactory)
            ));
            eventBus.register(new EventListenerRegistration<>(
                "form-submitted-listener",
                FormSubmitted.class,
                EventExecutionMode.SYNCHRONOUS,
                RetryPolicy.noRetry(),
                new FormSubmittedEventListener(relationService)
            ));
            eventBus.register(new EventListenerRegistration<>(
                "sub-process-complete-listener",
                ProcessEvent.class,
                EventExecutionMode.SYNCHRONOUS,
                RetryPolicy.noRetry(),
                new SubProcessCompleteListener(relationService)
            ));
            eventBus.register(new EventListenerRegistration<>(
                "packet-event-listener",
                ProcessEvent.class,
                EventExecutionMode.SYNCHRONOUS,
                RetryPolicy.noRetry(),
                new PacketEventListener(packetService)
            ));

            taskFactory.register(
                "applicationFormTask",
                () -> new ApplicationFormTask(formService, relationService)
            );
            taskFactory.register(
                "reviewFormTask",
                () -> new ReviewFormTask(formService, relationService)
            );
            taskFactory.register("failingInitTask", FailingInitTask::new);
            taskFactory.register(
                "configDrivenFormTask",
                () -> new ConfigDrivenFormTask(formService, relationService)
            );
            taskFactory.register("outputTask", OutputTask::new);
            taskFactory.register("afterInitTask", AfterInitTask::new);
            taskFactory.register(
                "subProcessTask",
                () -> new ExampleSubProcessTask(relationService)
            );
            taskFactory.register("childTask", ChildTask::new);

            processService = new ProcessService(
                definitionRepository,
                processRepository,
                workflowEngine,
                eventBus
            );
            workflowListener = new WorkflowEventListener(
                definitionRepository,
                taskRepository,
                taskFactory,
                processService,
                eventBus
            );
            workflowEngine.setListener(workflowListener);
            formService.setSubmissionListener(new FormCompleteListener(eventBus));
        }

        private TaskInstanceRecord requiredTask(long processInstanceId, String taskCode) {
            return taskRepository.findLatest(processInstanceId, taskCode)
                .orElseThrow();
        }

        private Map<String, String> taskConfig(String taskCode) {
            return definitionRepository.findByKey("application-review")
                .orElseThrow()
                .task(taskCode)
                .config();
        }
    }

    private static final class ApplicationFormTask extends FormTask {
        private ApplicationFormTask(
            FormService formService,
            TaskRelationService relationService
        ) {
            super(formService, relationService);
        }

        @Override
        protected String formDefinitionKey() {
            return "application-form";
        }

        @Override
        protected Map<String, String> buildFormOutput(
            TaskContext context,
            FormInstance form
        ) {
            return Map.of("amount", form.submittedData().get("amount"));
        }
    }

    private static final class ReviewFormTask extends FormTask {
        private ReviewFormTask(
            FormService formService,
            TaskRelationService relationService
        ) {
            super(formService, relationService);
        }

        @Override
        protected String formDefinitionKey() {
            return "review-form";
        }

        @Override
        protected Map<String, String> buildFormOutput(
            TaskContext context,
            FormInstance form
        ) {
            return Map.of(
                "decision", form.submittedData().get("decision"),
                "reviewComment", form.submittedData().get("comment")
            );
        }

        @Override
        public TaskResult postComplete(TaskContext context) {
            return TaskResult.builder()
                .putProcessInstData("reviewPostComplete", "true")
                .build();
        }
    }

    private static final class FailingInitTask extends io.github.openflowkernel.core.task.AbstractTask {
        @Override
        public TaskResult init(TaskContext context) {
            return TaskResult.builder()
                .putTaskInstData("currentTaskInitSuccess", "false")
                .build();
        }
    }

    private static final class ConfigDrivenFormTask extends FormTask {
        private ConfigDrivenFormTask(
            FormService formService,
            TaskRelationService relationService
        ) {
            super(formService, relationService);
        }

        @Override
        protected String formDefinitionKey() {
            return "fallback-form";
        }
    }

    private static final class OutputTask extends io.github.openflowkernel.core.task.AbstractTask {
    }

    private static final class AfterInitTask extends io.github.openflowkernel.core.task.AbstractTask {
        @Override
        public void afterInit(TaskContext context) {
            complete(TaskResult.builder()
                .putProcessInstData("afterInit", "true")
                .build());
        }
    }

    private static final class ExampleSubProcessTask extends SubProcessTask {
        private ExampleSubProcessTask(TaskRelationService taskRelationService) {
            super(taskRelationService);
        }
    }

    private static final class ChildTask extends io.github.openflowkernel.core.task.AbstractTask {
    }
}
