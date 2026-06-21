package io.github.openflowkernel.engine.camunda;

import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.task.TaskDefinition;
import org.camunda.bpm.engine.impl.util.xml.Element;

import java.util.List;
import java.util.Objects;

public final class CamundaWorkflowParseListener extends AbstractBpmnParseListener {
    private static final List<String> TASK_EVENTS = List.of(
        TaskListener.EVENTNAME_CREATE,
        TaskListener.EVENTNAME_COMPLETE,
        TaskListener.EVENTNAME_DELETE
    );

    private final TaskListener taskListener;
    private final ExecutionListener processEndListener;

    public CamundaWorkflowParseListener(
        TaskListener taskListener,
        ExecutionListener processEndListener
    ) {
        this.taskListener = Objects.requireNonNull(taskListener);
        this.processEndListener = Objects.requireNonNull(processEndListener);
    }

    @Override
    public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
        addTaskListener(taskDefinition(activity));
    }

    @Override
    public void parseProcess(Element processElement, ProcessDefinitionEntity processDefinition) {
        processDefinition.addListener(
            ExecutionListener.EVENTNAME_END,
            processEndListener
        );
    }

    private void addTaskListener(TaskDefinition taskDefinition) {
        for (String event : TASK_EVENTS) {
            taskDefinition.addTaskListener(event, taskListener);
        }
    }

    private TaskDefinition taskDefinition(ActivityImpl activity) {
        UserTaskActivityBehavior behavior =
            (UserTaskActivityBehavior) activity.getActivityBehavior();
        return behavior.getTaskDefinition();
    }
}
