# 贡献说明

`open-flow-kernel` 当前处于高保真抽取阶段。贡献的优先级不是“重新设计得更漂亮”，而是让公开仓库更忠于原版 `merchant-kernel-flow` 的核心设计。

## 基本原则

1. 保持 `Process -> Task -> Form` 三层模型。
2. 保持 Camunda 只作为状态机和路由引擎。
3. 不把 Camunda、Spring、JDBC、表单平台类型泄漏到 `flow-kernel-core`。
4. 不引入原系统没有的流程概念，除非明确记录为后续增强。
5. 任何有意偏离原实现的地方，都要更新 [设计来源与偏离](docs/审计与计划/设计来源与偏离.md) 或 [高保真审计](docs/审计与计划/高保真审计.md)。

## 开发流程

修改前建议先读：

```text
docs/审计与计划/项目计划.md
docs/审计与计划/设计来源与偏离.md
docs/审计与计划/高保真审计.md
docs/README.md
```

提交前至少运行：

```bash
./gradlew test --no-daemon --warning-mode all
```

涉及 JDBC schema 时，同时更新：

```text
db/schema/h2/open-flow-kernel-jdbc-schema.sql
db/schema/mysql/open-flow-kernel-jdbc-schema.sql
docs/持久化与集成/持久化.md
```

涉及公开 API 或模块边界时，同时更新：

```text
docs/API与边界/公共API边界.md
docs/API与边界/核心接口.md
README.md
```

## 不接受的方向

- 把 `flow-kernel-core` 改成依赖 Camunda/Spring/JDBC。
- 为了示例方便绕过 `TaskRelation`、`EventBus` 或 `TaskInstance` 生命周期。
- 新增业务平台专属 RPC、权限、签约、通知实现。
- 未记录原因地替换原版命名和生命周期顺序。
