# Camunda 作为状态机

`open-flow-kernel` 把 Camunda 当成路由状态机，而不是业务流程事实源。

本文定义 Camunda 7 适配器必须保持的边界。当前代码中的最小实现位于 `flow-kernel-engine-camunda`。

## 职责拆分

| 关注点 | 负责人 |
| --- | --- |
| BPMN 路由、网关、token | Camunda |
| 业务流程 ID 和状态 | `ProcessInstance` |
| 业务任务 ID 和状态 | `TaskInstance` |
| 任务生命周期 | `TaskInstance` + task handler |
| 表单/子流程/外部回调等待 | `TaskRelation` |
| 表单值 | `FormService` 适配器 |
| 生命周期重试/失败/回放 | `flow-kernel-event` |

Camunda 可以告诉内核“哪个 BPMN 节点激活了”，但不能替代业务流程和业务任务模型。

## 身份映射

Camunda 和内核之间只保留窄桥：

```text
Camunda processInstance businessKey = kernel processInstanceId
Camunda taskDefinitionKey = kernel taskCode
```

Camunda 自己的 process instance id 和 task id 是适配器细节，不应该成为业务 API 的主身份。

## 引擎适配器接口

核心接口：

```java
void setListener(WorkflowEngineListener listener);
void start(String processDefinitionKey, String businessKey, Map<String, Object> variables);
void completeTask(String processDefinitionKey, String businessKey, String taskCode, Map<String, Object> variables);
```

Camunda 适配器必须实现这些接口，并且不能把 Camunda 类泄漏到 `flow-kernel-core`。

当前最小实现：

| 类 | 职责 |
| --- | --- |
| `CamundaWorkflowEngine` | 实现 `WorkflowEngine`，负责 start 和 complete task |
| `CamundaWorkflowTaskListener` | 把 Camunda user task create/complete/delete 事件转成内核回调 |
| `CamundaWorkflowProcessEndListener` | 把 Camunda process end 转成内核流程完成回调 |
| `CamundaWorkflowParseListener` | 复刻原实现 parse-listener 思路，把 listener 注入 BPMN |

这几个类构成最小闭环，不包含 Spring Boot 自动配置。

## 回调映射

| Camunda 侧 | 内核回调 |
| --- | --- |
| user task created | `onTaskCreated(EngineTaskCreated)` |
| user task completed | `onTaskCompleted(EngineTaskCompleted)` |
| user task deleted/cancelled | `onTaskDeleted(EngineTaskDeleted)` |
| process ended | `onProcessCompleted(EngineProcessCompleted)` |

内核监听器再负责业务行为：

```text
engine task-created
-> create business task
-> publish task-created event
-> task initialization listener
-> TaskInstance.init
-> TaskInstance.afterInit
```

```text
task complete request
-> TaskInstance.beforeComplete
-> persist task/process output
-> mark business task complete
-> WorkflowEngine.completeTask
-> engine task-completed callback
-> TaskInstance.postComplete
```

## 第二轮 最小闭环

第二轮 只需要证明最小闭环：

1. 准备一个包含两个 user task 的 BPMN。
2. 通过 `ProcessService` 启动流程。
3. Camunda task-created 回调创建第一个业务任务。
4. 表单提交后完成 `TaskRelation`。
5. 业务任务完成后推进 Camunda task。
6. Camunda 创建第二个任务。
7. 第二个任务完成。
8. Camunda process-end 回调。
9. 内核完成业务流程。

这个闭环证明：

```text
BPMN 负责路由；
内核负责 Process / Task / Form / Relation / Event 状态。
```

当前代码已经具备 adapter baseline，并通过真实 Camunda in-memory engine + 两节点 BPMN 示例形成两层可执行闭环测试。

第一层只验证 adapter 和 Camunda 事件桥接：

```text
CamundaWorkflowEngineTest
-> deploy application-review BPMN
-> start by ProcessService 等价入口 WorkflowEngine.start
-> user task create callback
-> complete application task
-> user task complete callback
-> create review task
-> complete review task
-> process end callback
```

测试位于 `flow-kernel-engine-camunda/src/test/java/.../CamundaWorkflowEngineTest.java`。

第二层验证完整 Process-Task-Form 链路通过真实 Camunda：

```text
CamundaProcessTaskFormIntegrationTest
-> ProcessService.start
-> CamundaWorkflowEngine.start
-> Camunda task-created callback
-> WorkflowEventListener creates TaskInstance
-> FormTask creates FormInstance and TaskRelation
-> form submit completes TaskRelation
-> TaskInstance.complete advances Camunda task
-> Camunda process-end callback completes ProcessInstance
```

测试位于 `flow-kernel-engine-camunda/src/test/java/.../CamundaProcessTaskFormIntegrationTest.java`。

为了忠于原项目使用的 Camunda 7.11，Camunda 模块测试使用 H2 `1.4.200`。H2 2.x 对老版本 Camunda SQL 不兼容，会在变量查询时出现 boolean/integer 比较错误。

## 不阻塞最小闭环的事项

第一版 Camunda 适配器不要被这些事项阻塞：

- 完整部署管理。
- Spring Boot starter。
- 多租户部署。
- 流程图渲染。
- 完整历史查询。
- 管理后台 API。
- 完整取消/补偿语义。
- outbox / command-store 可靠性重设计。
