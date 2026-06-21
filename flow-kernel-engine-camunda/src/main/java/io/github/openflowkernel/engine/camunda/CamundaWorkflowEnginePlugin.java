package io.github.openflowkernel.engine.camunda;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

import java.util.ArrayList;
import java.util.Objects;

public final class CamundaWorkflowEnginePlugin extends AbstractProcessEnginePlugin {
    private final CamundaWorkflowEngine workflowEngine;

    public CamundaWorkflowEnginePlugin(CamundaWorkflowEngine workflowEngine) {
        this.workflowEngine = Objects.requireNonNull(workflowEngine);
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        if (configuration.getCustomPostBPMNParseListeners() == null) {
            configuration.setCustomPostBPMNParseListeners(new ArrayList<>());
        }
        configuration.getCustomPostBPMNParseListeners().add(
            new CamundaWorkflowParseListener(
                new CamundaWorkflowTaskListener(workflowEngine),
                new CamundaWorkflowProcessEndListener(workflowEngine)
            )
        );
    }

    @Override
    public void postProcessEngineBuild(ProcessEngine processEngine) {
        workflowEngine.bind(processEngine);
    }
}
