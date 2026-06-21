# Open Flow Kernel

Open Flow Kernel 是围绕三层模型构建的业务流程内核：

```text
Process -> Task -> Form
```

流程引擎被明确定位为状态机和路由引擎。业务流程实例、任务实例、任务生命周期、表单关系和业务数据仍由内核持有。

这个项目是对生产验证过的流程设计做高保真开源抽取。范围和里程碑见 [项目计划](docs/审计与计划/项目计划.md)，设计映射和有意偏离见 [设计来源与偏离](docs/审计与计划/设计来源与偏离.md)。

## 设计精髓

Open Flow Kernel 不试图替代 BPMN 引擎。核心思路是让流程引擎继续作为状态机，而业务流程模型由内核持有。

这套设计有六条规则：

1. `Process -> Task -> Form` 是业务模型。
   Process 持有业务状态和共享数据。Task 持有生命周期执行和任务级数据。Form 或外部 relation 持有用户输入侧的完成信号。
2. 流程引擎负责路由，不负责业务事实。
   引擎 `businessKey` 映射到业务流程实例 ID，引擎 task definition key 映射到业务任务 code。
3. `TaskInstance` 是生命周期协调者。
   任务处理器实现业务行为；`TaskInstance` 负责 init、after-init、output、before-complete、状态 CAS、推进引擎和完成后顺序。
4. `TaskRelation` 是通用等待机制。
   表单、子流程、远程回调和其他外部工作都通过同一套 relation 规则完成：先完成 relation，再检查全部 relation，最后只完成一次任务。
5. 事件把引擎回调和业务生命周期动作解耦。
   任务初始化、表单提交、子流程完成、packet 状态更新、重试和 replay 都通过内核事件完成，而不是隐藏的直接耦合。
6. Packet 和 Candidate 是生命周期扩展，不是另一套流程系统。
   Packet 保存流程数据的版本化变更，Candidate 保存任务处理人/候选人状态。它们挂在 process/task 生命周期上，而不是替代流程系统。

这是抽取原实现时要保留的设计目标。代码与参考项目存在偏离时，原因记录在 [设计来源与偏离](docs/审计与计划/设计来源与偏离.md)。

## 当前状态

当前 第二轮 参考采用闭环已实现并有测试覆盖：

- Process-Task-Form 生命周期
- Event baseline：先记录事件、事务后分发、listener delivery 记录、retry/backoff、永久失败和 replay hook
- 独立 `flow-kernel-form` 模块：表单契约、`FormTask`、表单提交事件
- `flow-kernel-engine-camunda`：Camunda 7 adapter baseline，并有真实 Camunda in-memory 闭环测试
- Camunda + `ProcessService -> FormTask -> TaskRelation` 完整集成测试
- 单子流程 `SubProcessTask`
- 示例用 in-memory runtime 和 JDBC persistence adapter
- Packet baseline：pointer/value、commit CAS、expire reset、typed packet、factory、init manager、process-start hook
- TaskCandidate baseline：软删除、差量更新、`TaskInstance.initCandidate` SPI、`candidate_inst_relation` JDBC baseline
- Event JDBC baseline：event record、listener delivery、retry/replay
- multi-form / multi-subprocess 通用配置 baseline
- 根目录 `db/` 下提供 H2/MySQL 建表语句

`flow-kernel-example` 包含刻意保持轻量的 in-memory/reference 实现，用于示例和测试。它们的目的，是让读者不依赖 Camunda 或 MySQL 也能跑通设计；它们不是生产 adapter。真实 Camunda 闭环位于 `flow-kernel-engine-camunda` 的测试中。

当前验收测试命令：

```bash
./gradlew test --no-daemon --warning-mode all
```

当前 第三轮 的核心复刻已经覆盖 typed Packet、Candidate SPI、multi-form、multi-subprocess、Event JDBC、通用持久化表和 DB DDL。`mysqlIntegrationTest` 任务已存在并可编译；真实 MySQL/Testcontainers 运行属于可选外部环境验证，不作为当前核心复刻闭环的阻塞项。

## 文档导读

完整分类导航见 [文档导航](docs/README.md)。第一次阅读建议只看这条主线：

1. [快速开始](docs/快速开始/快速开始.md)
2. [设计精髓](docs/设计/设计精髓.md)
3. [完整示例](docs/设计/完整示例.md)
4. [Camunda 作为状态机](docs/设计/Camunda作为状态机.md)
5. [核心接口](docs/API与边界/核心接口.md)
6. [迁移指南](docs/迁移与治理/迁移指南.md)

常用专题入口：

| 主题 | 文档 |
| --- | --- |
| 模块/API 边界 | [模块边界](docs/API与边界/模块边界.md), [公共 API 边界](docs/API与边界/公共API边界.md) |
| 状态与时序 | [状态机](docs/设计/状态机.md), [运行时序](docs/设计/运行时序.md), [数据模型](docs/设计/数据模型.md) |
| 持久化与 DDL | [持久化](docs/持久化与集成/持久化.md), [DB 建表语句](db/README.md), [参考 DDL 对照](docs/持久化与集成/参考DDL对照.md) |
| 事件系统 | [事件系统](docs/持久化与集成/事件系统.md), [参考事件模块分析](docs/持久化与集成/参考事件模块分析.md) |
| 当前完成度 | [第三轮 完成度审计](docs/审计与计划/第三轮完成度审计.md), [延后范围](docs/迁移与治理/延后范围.md), [发布前检查清单](docs/迁移与治理/发布前检查清单.md) |

[项目计划](docs/审计与计划/项目计划.md) 跟踪实现里程碑。[设计来源与偏离](docs/审计与计划/设计来源与偏离.md) 记录哪些行为保留自参考设计，以及为什么存在有意偏离。

## 重要限制

当前实现会先更新业务任务状态，再直接调用流程引擎，保持与参考实现一致的顺序。durable completion intent、command delivery、reconciliation 属于复刻之后的改进项。这个仓库目前是高保真抽取 baseline，不是生产级流程运行时。
