# flow-kernel-core

核心 Process / Task / Relation 模块。

这个模块保存业务流程事实，不依赖 Camunda、JDBC、Spring、表单平台或 packet 模块。

主要入口：

- `ProcessService`：流程生命周期入口。
- `TaskFactory` / `TaskInstance`：任务创建、初始化、完成和状态推进。
- `AbstractTask` / `TaskPhase`：业务任务扩展点。
- `SubProcessTask` / `ExternalTask`：通用外部 relation 和子流程任务。
- `WorkflowEngine` / `WorkflowEngineListener`：流程引擎 adapter SPI。
- repository interfaces：持久化 adapter SPI。
- `TaskCandidateSource`：候选人来源解析 SPI。
- `ProcessStartHook`：流程实例创建后、workflow 启动前的扩展点。

边界：

- Camunda 只通过 `WorkflowEngine` 接入。
- 表单任务实现位于 `flow-kernel-form`。
- Packet 扩展位于 `flow-kernel-packet`。
- JDBC 实现位于 `flow-kernel-persistence-jdbc`。

更多说明见 `docs/API与边界/模块边界.md` 和 `docs/API与边界/核心接口.md`。
