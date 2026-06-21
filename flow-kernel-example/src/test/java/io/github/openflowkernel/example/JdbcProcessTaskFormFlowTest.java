package io.github.openflowkernel.example;

import io.github.openflowkernel.core.constant.TaskConfigKeyConstant;
import io.github.openflowkernel.core.engine.WorkflowEventListener;
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
import io.github.openflowkernel.example.event.AtomicEventIdGenerator;
import io.github.openflowkernel.example.event.DirectEventExecutor;
import io.github.openflowkernel.example.event.ImmediateTransactionBoundary;
import io.github.openflowkernel.example.event.InMemoryEventStore;
import io.github.openflowkernel.jdbc.persistence.JdbcProcessDefinitionRepository;
import io.github.openflowkernel.jdbc.persistence.JdbcProcessInstanceRepository;
import io.github.openflowkernel.jdbc.persistence.JdbcSchemaInitializer;
import io.github.openflowkernel.jdbc.persistence.JdbcTaskInstanceRepository;
import io.github.openflowkernel.jdbc.persistence.JdbcTaskRelationRepository;
import io.github.openflowkernel.example.engine.InMemoryWorkflowEngine;
import io.github.openflowkernel.example.form.InMemoryFormService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcProcessTaskFormFlowTest {

    @Test
    void completesProcessThroughTwoFormTasksWithJdbcRepositories() {
        Fixture fixture = new Fixture();

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
            .containsEntry("applicant", "Alice")
            .containsEntry("amount", "1200")
            .containsEntry("decision", "approved")
            .containsEntry("reviewComment", "Looks good")
            .containsEntry("reviewPostComplete", "true");
    }

    @Test
    void subProcessTaskCompletesParentAfterChildProcessCompleteWithJdbcRepositories() {
        Fixture fixture = new Fixture();

        long parentProcessId = fixture.processService.start(
            "parent-sub-process",
            Map.of("parentInput", "from-parent")
        );
        TaskInstanceRecord parentTask = fixture.requiredTask(parentProcessId, "createChild");

        var childProcess = fixture.processRepository.findSubProcesses(
            parentProcessId,
            parentTask.id()
        ).get(0);
        assertThat(childProcess.data())
            .containsEntry("childInput", "from-parent")
            .containsEntry("PROC_TYPE", "relate");

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

    private static long formInstanceId(TaskInstanceRecord task) {
        return Long.parseLong(task.data().get(FormTask.FORM_INSTANCE_ID));
    }

    private static final class Fixture {
        private final JdbcProcessDefinitionRepository definitionRepository;
        private final JdbcProcessInstanceRepository processRepository;
        private final JdbcTaskInstanceRepository taskRepository;
        private final JdbcTaskRelationRepository relationRepository;
        private final InMemoryWorkflowEngine workflowEngine = new InMemoryWorkflowEngine();
        private final FormService formService = new InMemoryFormService();
        private final ProcessService processService;
        private final TaskFactory taskFactory;

        private Fixture() {
            DataSource dataSource = dataSource();
            new JdbcSchemaInitializer(dataSource).initialize();
            definitionRepository = new JdbcProcessDefinitionRepository(dataSource);
            processRepository = new JdbcProcessInstanceRepository(dataSource);
            taskRepository = new JdbcTaskInstanceRepository(dataSource);
            relationRepository = new JdbcTaskRelationRepository(dataSource);

            ProcessDefinition definition = new ProcessDefinition(
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
            );
            definitionRepository.save(definition);
            workflowEngine.deploy(definition);
            ProcessDefinition childDefinition = new ProcessDefinition(
                "child-flow",
                "Child Flow",
                List.of(new TaskDefinition("childTask", "Child Task", "childTask"))
            );
            definitionRepository.save(childDefinition);
            workflowEngine.deploy(childDefinition);
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

            TaskRuntime runtime = new TaskRuntime(
                definitionRepository,
                processRepository,
                taskRepository,
                workflowEngine,
                relationRepository
            );
            taskFactory = new TaskFactory(runtime);
            TaskRelationService relationService = new TaskRelationService(
                relationRepository,
                taskRepository,
                processRepository,
                definitionRepository,
                taskFactory
            );
            Clock clock = Clock.systemUTC();
            EventBus eventBus = new EventBus(
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
                    new BackoffPolicy(Duration.ofSeconds(1), 2, Duration.ofHours(1)),
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

            taskFactory.register(
                "applicationFormTask",
                () -> new ApplicationFormTask(formService, relationService)
            );
            taskFactory.register(
                "reviewFormTask",
                () -> new ReviewFormTask(formService, relationService)
            );
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
            workflowEngine.setListener(new WorkflowEventListener(
                definitionRepository,
                taskRepository,
                taskFactory,
                processService,
                eventBus
            ));
            formService.setSubmissionListener(new FormCompleteListener(eventBus));
        }

        private TaskInstanceRecord requiredTask(long processInstanceId, String taskCode) {
            return taskRepository.findLatest(processInstanceId, taskCode).orElseThrow();
        }

        private static DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setUrl(
                "jdbc:h2:mem:" + System.nanoTime()
                    + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
            );
            dataSource.setUser("sa");
            dataSource.setPassword("");
            return dataSource;
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

    private static final class ExampleSubProcessTask extends SubProcessTask {
        private ExampleSubProcessTask(TaskRelationService taskRelationService) {
            super(taskRelationService);
        }
    }

    private static final class ChildTask extends io.github.openflowkernel.core.task.AbstractTask {
    }
}
