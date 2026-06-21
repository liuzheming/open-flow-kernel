# flow-kernel-example

这个模块是 `open-flow-kernel` 的示例和 reference runtime。

它包含：

- in-memory workflow engine
- in-memory form service
- in-memory event store
- in-memory process/task/relation/candidate repositories
- integrated example tests

这些实现用于让读者不依赖 Camunda、MySQL 或真实表单平台也能跑通核心设计。它们不是生产 adapter。

生产接入时应优先看：

- `flow-kernel-engine-camunda`：Camunda 7 adapter baseline
- `flow-kernel-persistence-jdbc`：JDBC persistence baseline
- `flow-kernel-form`：真实表单系统 adapter SPI
- `docs/API与边界/模块边界.md`：模块边界
- `docs/API与边界/公共API边界.md`：公共 API 和 adapter SPI 边界
