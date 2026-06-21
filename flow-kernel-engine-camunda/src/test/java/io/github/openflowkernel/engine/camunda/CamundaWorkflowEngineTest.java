package io.github.openflowkernel.engine.camunda;

import io.github.openflowkernel.core.engine.EngineProcessCompleted;
import io.github.openflowkernel.core.engine.EngineTaskCompleted;
import io.github.openflowkernel.core.engine.EngineTaskCreated;
import io.github.openflowkernel.core.engine.EngineTaskDeleted;
import io.github.openflowkernel.core.engine.WorkflowEngineListener;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CamundaWorkflowEngineTest {
    @Test
    void routesTwoUserTasksThroughCamundaCallbacks() {
        CamundaWorkflowEngine workflowEngine = new CamundaWorkflowEngine();
        RecordingListener listener = new RecordingListener();
        workflowEngine.setListener(listener);

        ProcessEngine processEngine = processEngine(workflowEngine);
        try {
            processEngine.getRepositoryService().createDeployment()
                .addString("application-review.bpmn", bpmn())
                .deploy();

            workflowEngine.start("application-review", "1001", Map.of("applicant", "Alice"));

            assertThat(listener.events).containsExactly(
                "created:application:1001"
            );

            workflowEngine.completeTask(
                "application-review",
                "1001",
                "application",
                Map.of("amount", "1200")
            );

            assertThat(listener.events).containsExactly(
                "created:application:1001",
                "completed:application:1001",
                "created:review:1001"
            );

            workflowEngine.completeTask(
                "application-review",
                "1001",
                "review",
                Map.of("decision", "approved")
            );

            assertThat(listener.events).containsExactly(
                "created:application:1001",
                "completed:application:1001",
                "created:review:1001",
                "completed:review:1001",
                "process-completed:application-review:1001"
            );
        } finally {
            processEngine.close();
        }
    }

    private static ProcessEngine processEngine(CamundaWorkflowEngine workflowEngine) {
        ProcessEngineConfigurationImpl configuration =
            new StandaloneInMemProcessEngineConfiguration();
        configuration.setJdbcUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=1000");
        configuration.setDatabaseSchemaUpdate(
            ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE
        );
        configuration.setJobExecutorActivate(false);
        configuration.getProcessEnginePlugins().add(
            new CamundaWorkflowEnginePlugin(workflowEngine)
        );
        return configuration.buildProcessEngine();
    }

    private static String bpmn() {
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

    private static final class RecordingListener implements WorkflowEngineListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onTaskCreated(EngineTaskCreated event) {
            events.add("created:" + event.taskCode() + ":" + event.businessKey());
        }

        @Override
        public void onTaskCompleted(EngineTaskCompleted event) {
            events.add("completed:" + event.taskCode() + ":" + event.businessKey());
        }

        @Override
        public void onTaskDeleted(EngineTaskDeleted event) {
            events.add("deleted:" + event.taskCode() + ":" + event.businessKey());
        }

        @Override
        public void onProcessCompleted(EngineProcessCompleted event) {
            events.add(
                "process-completed:" + event.processDefinitionKey() + ":"
                    + event.businessKey()
            );
        }
    }
}
