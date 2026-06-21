# 公共 API 边界

本文说明哪些类是使用者应该关注的 API，哪些只是内核运行时装配。

当前代码仍处于高保真抽取 baseline 阶段，部分类为了装配和测试仍是 public。Java `public` 不等于稳定业务 API。本文按 第三轮 当前状态说明稳定 API、Adapter SPI、adapter implementation 和内部 runtime wiring。模块依赖方向见 [模块边界](模块边界.md)。

## 用户 API

| API | 模块 | 用途 |
| --- | --- | --- |
| `ProcessService` | `flow-kernel-core` | 启动、查询、变更业务流程生命周期 |
| `ITask` | `flow-kernel-core` | 业务任务句柄 |
| `AbstractTask` | `flow-kernel-core` | 自定义任务处理器基类 |
| `TaskPhase` | `flow-kernel-core` | 任务生命周期钩子 |
| `TaskContext` | `flow-kernel-core` | 任务执行上下文 |
| `TaskResult` | `flow-kernel-core` | 任务数据和流程数据输出 |
| `FormTask` | `flow-kernel-form` | 表单任务基类 |
| `SubProcessTask` | `flow-kernel-core` | 子流程任务基类 |
| `Packet` | `flow-kernel-packet` | typed 流程数据包基类 |
| `PacketService` | `flow-kernel-packet` | packet 初始化、查询、commit、expire、生命周期状态更新 |
| `TaskCandidate` | `flow-kernel-core` | 任务候选人数据结构 |

典型业务使用者主要做两件事：

- 调用 `ProcessService` 启动流程。
- 继承 `AbstractTask` / `FormTask` / `SubProcessTask` 实现任务行为。
- 需要过程快照时定义 typed `Packet<V>`。

## Adapter SPI

| SPI | 模块 | 由谁实现 |
| --- | --- | --- |
| `WorkflowEngine` | `flow-kernel-core` | Camunda 适配器、示例 in-memory 引擎 |
| `WorkflowEngineListener` | `flow-kernel-core` | 内核监听器注册到引擎 |
| `CamundaWorkflowEngine` | `flow-kernel-engine-camunda` | Camunda 7 最小 adapter，生产系统可直接复用或替换 |
| `CamundaWorkflowParseListener` | `flow-kernel-engine-camunda` | 给 BPMN user task 和 process end 注入内核事件桥接 |
| `FormService` | `flow-kernel-form` | 真实表单系统、示例 in-memory 表单 |
| `EventStore` | `flow-kernel-event` | JDBC 事件存储、示例 in-memory 事件存储 |
| `TransactionBoundary` | `flow-kernel-event` | Spring 事务适配、示例立即执行适配 |
| `TaskCandidateSource` | `flow-kernel-core` | 候选人来源解析 |
| `ProcessStartHook` | `flow-kernel-core` | 流程实例创建后、workflow 启动前的扩展点 |
| `ProcessPacketInit` | `flow-kernel-packet` | 按流程启动参数初始化 typed packet |
| `PacketFactory` | `flow-kernel-packet` | resolver class 到 typed packet 的注册和实例化 |
| `PacketInitManager` | `flow-kernel-packet` | 根据 `PacketInitConfig.name` 调用 `ProcessPacketInit` |
| repositories | `flow-kernel-core` / `flow-kernel-packet` | JDBC / 示例持久化 |

适配器必须保持身份映射和生命周期顺序。

## Repository SPI

这些接口是持久化适配边界，不是业务服务 API：

| SPI | 表 / 语义 |
| --- | --- |
| `ProcessDefinitionRepository` | 流程定义和任务配置 |
| `ProcessInstanceRepository` | 流程实例、流程数据、子流程关系字段 |
| `TaskInstanceRepository` | 任务实例、任务数据、状态 CAS |
| `TaskRelationRepository` | 表单、子流程、外部等待 relation |
| `TaskCandidateRepository` | `process_task_inst_candidate` |
| `CandidateRelationRepository` | `candidate_inst_relation` |
| `ProcessInstanceRelationRepository` | `process_inst_relation` |
| `ProcessInstanceDataInitRepository` | `process_inst_data_init_config` |
| `ProcessLogRepository` | `process_log` |
| `PacketRepo` / `PacketDataRepo` | packet pointer/value |

## 内部 Runtime Wiring

这些类当前是 public，但不应被当成业务扩展点：

| 类 | 原因 |
| --- | --- |
| `WorkflowEventListener` | 把引擎回调桥接到内核生命周期 |
| `CamundaWorkflowTaskListener` | Camunda task event 到 `WorkflowEngineListener` 的桥接 |
| `CamundaWorkflowProcessEndListener` | Camunda process end 到 `WorkflowEngineListener` 的桥接 |
| `TaskInitializationEventListener` | 从 task-created 事件执行任务初始化 |
| `TaskInitCompleteEventListener` | 从 task-init-complete 事件执行 `afterInit` |
| `FormCompleteListener` | 表单 service 的直接提交回调桥接，兼容不走 EventBus 的 adapter |
| `FormSubmittedEventListener` | 把表单提交事件桥接到 relation 完成 |
| `SubProcessCompleteListener` | 把子流程完成桥接到父任务 relation 完成 |
| `PacketEventListener` | 根据流程完成/取消事件更新 packet 状态 |
| `PacketProcessStartHook` | 把 `ProcessStartHook` 适配到 packet 初始化 |
| `TaskRuntime` | `TaskInstance` 的依赖装配对象 |
| repository record classes | 持久化边界数据载体 |

## 示例代码

`flow-kernel-example` 包含确定性的 in-memory 支持代码：

- in-memory workflow engine
- in-memory form service
- in-memory event store
- in-memory repositories
- integrated example tests

这些代码用于理解、示例和测试，不是生产适配器。

## Camunda Adapter

`flow-kernel-engine-camunda` 是真实 Camunda 7 adapter baseline，但范围刻意很小：

- `CamundaWorkflowEngine.start`：按 process definition key 找 latest definition，用业务流程实例 ID 作为 `businessKey` 启动 Camunda。
- `CamundaWorkflowEngine.completeTask`：按 process definition key、`businessKey`、task definition key 查当前 Camunda task，并 complete 第一个匹配任务。
- `CamundaWorkflowParseListener`：给 user task 注入 create/complete/delete listener，给 process 注入 end listener。
- `CamundaWorkflowEnginePlugin`：以 Camunda `ProcessEnginePlugin` 形式注册 parse listener，适合独立 engine 配置。
- listener 只把 Camunda 事件翻译成 `EngineTaskCreated`、`EngineTaskCompleted`、`EngineTaskDeleted`、`EngineProcessCompleted`。

它不包含 Spring Boot 自动配置、部署管理、多租户策略、历史查询封装或生产级补偿逻辑。

## MySQL Verification Boundary

`flow-kernel-persistence-jdbc` 提供显式任务：

```bash
./gradlew :flow-kernel-persistence-jdbc:mysqlIntegrationTest --no-daemon --warning-mode all
```

该任务需要 Docker daemon。默认 CI 和默认 `test` 不运行它；CI 只编译 `mysqlIntegrationTestClasses`，保证 Testcontainers 测试代码不漂移。
