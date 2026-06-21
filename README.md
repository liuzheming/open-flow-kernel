# Open Flow Kernel

Open Flow Kernel 是一个面向业务流程系统的轻量流程内核。它解决的问题不是“再实现一个 BPMN 引擎”，而是把企业业务流程中容易混在一起的几类责任拆开：

- 流程编排和路由。
- 业务流程实例和任务实例状态。
- 表单、子流程、外部回调等任务等待关系。
- 过程数据、候选人、事件、持久化和恢复边界。

系统的核心思想是：

```text
Camunda / BPMN engine handles routing and state-machine transitions.
Open Flow Kernel owns business process semantics through Process -> Task -> Form.
```

也就是说，流程引擎负责“下一个 BPMN 节点是谁”；业务事实仍由内核自己的 `ProcessInstance`、`TaskInstance`、`TaskRelation`、`Form`、`Event`、`Packet` 和 `Candidate` 模型持有。

## Overview

在长期演进的业务流程系统里，常见问题是 BPMN、表单、审批页、外部回调、候选人、过程数据和业务状态逐渐耦合到一起。结果是：

- 流程引擎里的 token 状态被误当成业务状态。
- 表单提交、子流程结束、远程回调各自实现一套任务完成逻辑。
- 任务生命周期分散在 listener、service、controller 和外部系统回调中。
- 过程数据缺少版本化边界，难以审计和恢复。
- 事件变成普通通知，而不是可重试、可回放的生命周期机制。

Open Flow Kernel 提供的是一个流程内核设计样例：用明确的领域模型承载业务流程语义，用 adapter 隔离 Camunda、表单系统、JDBC 和事件基础设施。

## System Design Philosophy

### Workflow Engine Is Not The Business Source Of Truth

Camunda 适合做 BPMN 路由、网关和 token 流转，但不适合作为业务流程事实源。业务流程 ID、业务任务 ID、任务状态、表单 relation、候选人和过程数据都应保存在内核自己的模型中。

核心身份映射只有两条：

```text
engine businessKey = kernel processInstanceId
engine taskDefinitionKey = kernel taskCode
```

### Task Lifecycle Is Explicit

业务任务不是一个简单的“回调函数”。`TaskInstance` 统一控制任务生命周期：

```text
create -> init -> afterInit -> wait relations -> beforeComplete
-> persist output -> complete engine task -> postComplete
```

任务处理器只实现业务行为，状态流转、数据持久化、relation 检查和引擎推进由内核控制。

### External Work Completes Relations, Not Tasks

表单、子流程、远程回调、异步作业都可以被建模为 `TaskRelation`。外部系统完成的是 relation；只有当任务下所有 relation 都完成时，任务才会被完成一次。

```text
external system done
-> TaskRelation completed
-> all relations completed?
-> TaskInstance.complete
```

这个设计避免每类外部系统各自实现一套任务推进逻辑。

### Events Are Lifecycle Infrastructure

事件不是附带通知，而是流程生命周期的一部分。任务创建、任务初始化、表单提交、子流程完成、packet 状态更新、失败重试和 replay 都通过事件系统解耦。

当前 baseline 保留：

- event-first record
- after-commit dispatch
- listener delivery state
- retry / backoff
- permanent failure
- replay hook

### Adapters Keep Infrastructure Out Of Core

`flow-kernel-core` 不依赖 Camunda、Spring、JDBC 或具体表单平台。所有基础设施都通过 adapter 接入：

- `WorkflowEngine` adapter 接入 Camunda。
- `FormService` adapter 接入表单系统。
- repository adapter 接入持久化。
- event adapter 接入事件存储和分发。

## Core Architecture

```text
Business Application
  -> ProcessService
  -> TaskFactory / TaskInstance
  -> FormTask / ExternalTask / SubProcessTask
  -> TaskRelationService
  -> EventBus
  -> WorkflowEngine Adapter
  -> Persistence Adapter
```

核心分层：

| Layer | Responsibility |
| --- | --- |
| Process Definition / Instance | 流程定义、业务流程实例、流程状态、流程级数据、父子流程关系 |
| Task Runtime | 业务任务实例、任务生命周期、任务数据、任务处理器注册和执行 |
| Relation Layer | 表单、子流程、远程回调、异步作业等外部等待关系 |
| Event Layer | 生命周期事件、分发、listener delivery、retry、replay |
| Packet Layer | 过程数据快照、当前指针、value history、commit CAS |
| Candidate Layer | 任务候选人、差量更新、软删除、来源 SPI |
| Engine Adapter | 将 Camunda 等 BPMN 引擎映射到内核 `WorkflowEngine` |
| Persistence Adapter | 将 process/task/relation/event/packet/candidate 状态落库 |

模块依赖方向：

```text
flow-kernel-core
  <- flow-kernel-form
  <- flow-kernel-packet
  <- flow-kernel-engine-camunda
  <- flow-kernel-persistence-jdbc
  <- flow-kernel-example

flow-kernel-event is used by form/example/persistence adapters.
```

`core` 是领域内核，其他模块是扩展层、adapter 或示例运行时。

## Key Design Decisions

### 1. Keep Camunda Behind `WorkflowEngine`

Camunda process instance id 和 task id 不进入业务 API。业务只感知 `processInstanceId` 和 `taskCode`。这样可以替换引擎适配器，也可以避免业务库被 Camunda 内部模型绑死。

### 2. Use `TaskRelation` As The Waiting Primitive

任务等待外部输入时，不直接阻塞线程，也不让外部系统直接改 task 状态。外部实体完成 relation，内核再决定任务是否可以完成。

这使表单、子流程、异步回调共享一套一致的幂等完成规则。

### 3. Put Lifecycle Order In `TaskInstance`

如果生命周期顺序散落在不同 listener 或业务 service 中，流程系统很难排查和恢复。`TaskInstance` 将 init、after-init、before-complete、output merge、engine complete、post-complete 统一收口。

### 4. Treat Events As Recoverable Execution Records

事件系统保留 listener delivery 状态、attempts、next retry time 和失败快照。它不是简单发布订阅，而是承担生命周期动作的解耦、重试和 replay。

### 5. Use Packet For Versioned Process Data

流程运行中的数据会被多个任务读取和修改。`Packet` 使用 pointer/value/history 模型保存当前值和历史值，并通过 commit CAS 控制更新边界。

### 6. Keep Extension Points Adapter-Oriented

表单平台、候选人来源、事件执行器、持久化实现和流程引擎都通过接口接入。内核不假设具体框架，也不把平台类型泄漏给业务任务。

## System Capabilities

当前 baseline 覆盖：

- Process-Task-Form 生命周期。
- 基于 BPMN 引擎的流程编排、顺序流转和条件分支路由。
- 任务处理器节点模型：业务节点通过 `ITask` / `AbstractTask` / `TaskPhase` 扩展。
- Camunda 7 adapter 和真实 Camunda in-memory 闭环测试。
- Camunda + `ProcessService -> FormTask -> TaskRelation` 集成测试。
- 单表单和通用 multi-form 配置。
- 单子流程和通用 multi-subprocess 配置。
- 外部 relation 幂等完成，支持表单、子流程、远程回调和异步作业。
- Event baseline：record、dispatch、delivery、retry、permanent failure、replay。
- Packet baseline：pointer/value/history、typed packet、factory、init manager、process-start hook。
- TaskCandidate baseline：source SPI、diff update、soft delete、`candidate_inst_relation` JDBC baseline。
- JDBC persistence baseline。
- H2/MySQL DDL。
- 不依赖 Camunda/MySQL/真实表单平台的完整 in-memory reference example。

默认验收命令：

```bash
./gradlew test --no-daemon --warning-mode all
```

## Core Concepts

| Concept | Meaning |
| --- | --- |
| `ProcessDefinition` | 业务流程定义，包含流程编码、类型、packet 初始化配置和任务配置 |
| `ProcessInstance` | 业务流程实例，是业务流程状态的事实源 |
| `TaskDefinition` | 业务任务定义，通常映射到 BPMN task definition key |
| `TaskInstance` | 业务任务实例，负责生命周期协调和状态推进 |
| `ITask` / `AbstractTask` | 业务任务处理器扩展点 |
| `TaskContext` | 任务执行上下文，携带流程数据、任务数据、任务配置和当前身份 |
| `TaskRelation` | 任务等待外部实体完成的通用关系 |
| `FormTask` | 表单任务基类，创建表单并注册 relation |
| `SubProcessTask` | 子流程任务基类，创建子流程并等待子流程 relation |
| `WorkflowEngine` | 流程引擎 adapter 接口 |
| `EventBus` / `EventStore` | 生命周期事件、delivery、retry 和 replay 基础设施 |
| `Packet` | 流程过程数据的版本化快照模型 |
| `TaskCandidate` | 任务候选人 / 处理人状态模型 |

## Runtime Flow

典型表单任务链路：

```text
ProcessService.start
-> create ProcessInstance
-> WorkflowEngine.start
-> engine task-created callback
-> WorkflowEventListener
-> TaskFactory creates TaskInstance
-> TaskInstance.init
-> FormTask creates FormInstance
-> TaskRelation registered
-> FormService.submit
-> FormSubmitted event
-> TaskRelation completed
-> TaskInstance.beforeComplete
-> persist task/process output
-> WorkflowEngine.completeTask
-> engine task-completed callback
-> TaskInstance.postComplete
-> process-end callback
-> ProcessService.complete
```

这条链路体现了系统边界：

- 引擎只负责激活和推进 BPMN 节点。
- 表单只负责外部输入。
- relation 负责等待条件。
- task lifecycle 负责业务任务完成。
- process 负责业务流程状态。

## Modules

| Module | Responsibility |
| --- | --- |
| `flow-kernel-core` | Process、Task、Relation、WorkflowEngine、candidate、log 等核心模型 |
| `flow-kernel-event` | EventV2 baseline、event store、listener、delivery、retry 模型 |
| `flow-kernel-form` | FormService SPI、FormTask、form submit event bridge |
| `flow-kernel-packet` | Packet pointer/value/history、typed packet、packet init |
| `flow-kernel-engine-camunda` | Camunda 7 adapter baseline |
| `flow-kernel-persistence-jdbc` | JDBC repository adapter、schema initializer、event JDBC store |
| `flow-kernel-example` | in-memory reference runtime 和完整示例测试 |

## Documentation

完整文档见 [文档导航](docs/README.md)。

推荐阅读路径：

1. [快速开始](docs/快速开始/快速开始.md)
2. [设计精髓](docs/设计/设计精髓.md)
3. [完整示例](docs/设计/完整示例.md)
4. [Camunda 作为状态机](docs/设计/Camunda作为状态机.md)
5. [核心接口](docs/API与边界/核心接口.md)
6. [迁移指南](docs/迁移与治理/迁移指南.md)

专题文档：

| Topic | Document |
| --- | --- |
| Architecture | [架构说明](docs/设计/架构说明.md), [数据模型](docs/设计/数据模型.md), [运行时序](docs/设计/运行时序.md) |
| API Boundary | [模块边界](docs/API与边界/模块边界.md), [公共 API 边界](docs/API与边界/公共API边界.md) |
| Persistence | [持久化](docs/持久化与集成/持久化.md), [DB 建表语句](db/README.md), [参考 DDL 对照](docs/持久化与集成/参考DDL对照.md) |
| Event | [事件系统](docs/持久化与集成/事件系统.md), [参考事件模块分析](docs/持久化与集成/参考事件模块分析.md) |
| Audit | [第三轮完成度审计](docs/审计与计划/第三轮完成度审计.md), [高保真审计](docs/审计与计划/高保真审计.md), [延后范围](docs/迁移与治理/延后范围.md) |

## Current Boundaries

This repository is a high-fidelity extraction baseline, not a production-ready workflow platform.

当前边界：

- 不提供 production Spring Boot starter。
- 不提供 UI / admin console。
- 不内置人员、权限、签约、通知、RPC 等业务平台实现。
- MySQL/Testcontainers runtime 是可选外部环境验证项；当前保留 MySQL DDL 和测试编译保护。
- `LICENSE` 仍处于法务确认状态，正式开源发布前需要替换为批准的开源许可证。

这些边界是为了保持内核模型清晰，避免把业务平台实现误认为流程内核本身。
