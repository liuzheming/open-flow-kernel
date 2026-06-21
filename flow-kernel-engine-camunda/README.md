# flow-kernel-engine-camunda

Camunda 7 adapter baseline。

本模块证明并提供“Camunda 只作为状态机和路由引擎”的最小接入方式。

主要入口：

- `CamundaWorkflowEngine`：实现 core 的 `WorkflowEngine`。
- `CamundaWorkflowEnginePlugin`：注册 BPMN parse listener。
- `CamundaWorkflowParseListener`：给 user task 和 process end 注入内核事件桥接。
- `CamundaWorkflowTaskListener`：Camunda task event 到 core engine event 的桥接。
- `CamundaWorkflowProcessEndListener`：Camunda process end 到 core engine event 的桥接。

身份映射：

```text
Camunda businessKey = ProcessInstance.id
Camunda taskDefinitionKey = TaskDefinition.code
```

边界：

- 不包含 Spring Boot starter。
- 不包含部署管理、多租户策略、历史查询 facade 或生产补偿逻辑。
- 业务状态、任务数据、表单关系、packet 和 candidate 都不落入 Camunda。

更多说明见 `docs/设计/Camunda作为状态机.md`。
