package io.github.openflowkernel.engine.camunda;

import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;
import io.github.openflowkernel.core.engine.WorkflowEventListener;
import io.github.openflowkernel.core.enums.ProcStatusEnum;
import io.github.openflowkernel.core.enums.ProcTaskStatusEnum;
import io.github.openflowkernel.core.event.ProcessTaskEvent;
import io.github.openflowkernel.core.event.TaskInitCompleteEventListener;
import io.github.openflowkernel.core.event.TaskInitializationEventListener;
import io.github.openflowkernel.core.process.ProcessDefinition;
import io.github.openflowkernel.core.process.ProcessService;
import io.github.openflowkernel.core.relation.TaskRelationService;
import io.github.openflowkernel.core.task.TaskContext;
import io.github.openflowkernel.core.task.TaskDefinition;
import io.github.openflowkernel.core.task.TaskFactory;
import io.github.openflowkernel.core.task.TaskInstanceRecord;
import io.github.openflowkernel.core.task.TaskResult;
import io.github.openflowkernel.core.task.TaskRuntime;
import io.github.openflowkernel.event.BackoffPolicy;
import io.github.openflowkernel.event.EventBus;
import io.github.openflowkernel.event.EventExecutionMode;
import io.github.openflowkernel.event.EventListenerRegistration;
import io.github.openflowkernel.event.RetryPolicy;
import io.github.openflowkernel.example.event.AtomicEventIdGenerator;
import io.github.openflowkernel.example.event.DirectEventExecutor;
import io.github.openflowkernel.example.event.ImmediateTransactionBoundary;
import io.github.openflowkernel.example.event.InMemoryEventStore;
import io.github.openflowkernel.example.form.InMemoryFormService;
import io.github.openflowkernel.example.persistence.InMemoryProcessDefinitionRepository;
import io.github.openflowkernel.example.persistence.InMemoryProcessInstanceRepository;
import io.github.openflowkernel.example.persistence.InMemoryTaskInstanceRepository;
import io.github.openflowkernel.example.persistence.InMemoryTaskRelationRepository;
import io.github.openflowkernel.form.FormCompleteListener;
import io.github.openflowkernel.form.FormInstance;
import io.github.openflowkernel.form.FormService;
import io.github.openflowkernel.form.FormTask;
import io.github.openflowkernel.form.event.FormSubmitted;
import io.github.openflowkernel.form.event.FormSubmittedEventListener;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CamundaProcessTaskFormIntegrationTest {
    @Test
    void processTaskFormFlowCompletesThroughRealCamundaEngine() {
        Fixture fixture = new Fixture();
        fixture.deployBpmn();

        long processInstanceId = fixture.processService.start(
            "application-review",
            Map.of("applicant", "Alice")
        );

        TaskInstanceRecord applicationTask = fixture.requiredTask(
            processInstanceId,
            "application"
        );
        assertThat(applicationTask.status()).isEqualTo(ProcTaskStatusEnum.INIT);
        long applicationFormId = formInstanceId(applicationTask);

        fixture.formService.submit(applicationFormId, Map.of("amount", "1200"));

        assertThat(fixture.requiredTask(processInstanceId, "application").status())
            .isEqualTo(ProcTaskStatusEnum.COMPLETE);
        assertThat(fixture.processService.get(processInstanceId).data())
            .containsEntry("amount", "1200");

        TaskInstanceRecord reviewTask = fixture.requiredTask(processInstanceId, "review");
        assertThat(reviewTask.status()).isEqualTo(ProcTaskStatusEnum.INIT);
        long reviewFormId = formInstanceId(reviewTask);

        fixture.formService.submit(reviewFormId, Map.of(
            "decision",
            "approved",
            "comment",
            "Looks good"
        ));

        assertThat(fixture.requiredTask(processInstanceId, "review").status())
            .isEqualTo(ProcTaskStatusEnum.COMPLETE);
        assertThat(fixture.processService.get(processInstanceId).status())
            .isEqualTo(ProcStatusEnum.NORMAL_END);
        assertThat(fixture.processService.get(processInstanceId).data())
            .containsEntry("decision", "approved")
            .containsEntry("reviewComment", "Looks good")
            .containsEntry("reviewPostComplete", "true");

        fixture.close();
    }

    private static long formInstanceId(TaskInstanceRecord task) {
        return Long.parseLong(task.data().get(FormTask.FORM_INSTANCE_ID));
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
        private final CamundaWorkflowEngine workflowEngine = new CamundaWorkflowEngine();
        private final FormService formService = new InMemoryFormService();
        private final ProcessEngine processEngine;
        private final ProcessService processService;

        private Fixture() {
            processEngine = processEngine(workflowEngine);
            definitionRepository.save(new ProcessDefinition(
                "application-review",
                "Application Review",
                List.of(
                    new TaskDefinition(
                        "application",
                        "Submit Application",
                        "applicationFormTask",
                        Map.of(TaskConfigKeyConstant.FORM_DEF_ID, "application-form")
                    ),
                    new TaskDefinition(
                        "review",
                        "Review Application",
                        "reviewFormTask",
                        Map.of(TaskConfigKeyConstant.FORM_DEF_ID, "review-form")
                    )
                )
            ));

            TaskRuntime runtime = new TaskRuntime(
                definitionRepository,
                processRepository,
                taskRepository,
                workflowEngine,
                relationRepository
            );
            TaskFactory taskFactory = new TaskFactory(runtime);
            TaskRelationService relationService = new TaskRelationService(
                relationRepository,
                taskRepository,
                processRepository,
                definitionRepository,
                taskFactory
            );
            EventBus eventBus = eventBus();
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

            taskFactory.register(
                "applicationFormTask",
                () -> new ApplicationFormTask(formService, relationService)
            );
            taskFactory.register(
                "reviewFormTask",
                () -> new ReviewFormTask(formService, relationService)
            );

            processService = new ProcessService(
                definitionRepository,
                processRepository,
                workflowEngine,
                eventBus
            );
            workflowEngine.setListener(new WorkflowEventListener(
                definitionRepository,
                taskRepository,
                taskFactory,
                processService,
                eventBus
            ));
            formService.setSubmissionListener(new FormCompleteListener(eventBus));
        }

        private void deployBpmn() {
            processEngine.getRepositoryService().createDeployment()
                .addString("application-review.bpmn", applicationReviewBpmn())
                .deploy();
        }

        private TaskInstanceRecord requiredTask(long processInstanceId, String taskCode) {
            return taskRepository.findLatest(processInstanceId, taskCode)
                .orElseThrow();
        }

        private void close() {
            processEngine.close();
        }

        private static EventBus eventBus() {
            Clock clock = Clock.systemUTC();
            return new EventBus(
                new AtomicEventIdGenerator(),
                new InMemoryEventStore(clock),
                new ImmediateTransactionBoundary(),
                new DirectEventExecutor(),
                clock
            );
        }

        private static ProcessEngine processEngine(CamundaWorkflowEngine workflowEngine) {
            ProcessEngineConfigurationImpl configuration =
                new StandaloneInMemProcessEngineConfiguration();
            configuration.setJdbcUrl(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=1000"
            );
            configuration.setDatabaseSchemaUpdate(
                ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE
            );
            configuration.setJobExecutorActivate(false);
            configuration.getProcessEnginePlugins().add(
                new CamundaWorkflowEnginePlugin(workflowEngine)
            );
            return configuration.buildProcessEngine();
        }
    }

    private static String applicationReviewBpmn() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                targetNamespace="http://open-flow-kernel.github.io/bpmn">
              <process id="application-review" name="Application Review" isExecutable="true">
                <startEvent id="start" />
                <sequenceFlow id="flow-start-application" sourceRef="start" targetRef="application" />
                <userTask id="application" name="Submit Application" />
                <sequenceFlow id="flow-application-review" sourceRef="application" targetRef="review" />
                <userTask id="review" name="Review Application" />
                <sequenceFlow id="flow-review-end" sourceRef="review" targetRef="end" />
                <endEvent id="end" />
              </process>
            </definitions>
            """;
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
                "decision",
                form.submittedData().get("decision"),
                "reviewComment",
                form.submittedData().get("comment")
            );
        }

        @Override
        public TaskResult postComplete(TaskContext context) {
            return TaskResult.builder()
                .putProcessInstData("reviewPostComplete", "true")
                .build();
        }
    }
}
