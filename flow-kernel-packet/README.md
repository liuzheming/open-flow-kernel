# flow-kernel-packet

流程数据包模块。

Packet 保存流程数据的版本化变更，挂在 process/task 生命周期上，不替代流程系统。

主要入口：

- `PacketService`：初始化、查询、commit、expire 和状态更新。
- `Packet<V>`：typed packet 基类。
- `PacketFactory`：resolver class 到 typed packet 的注册和实例化。
- `PacketInitManager` / `ProcessPacketInit`：流程启动时的 packet 初始化 SPI。
- `PacketProcessStartHook`：通过 `ProcessStartHook` 接入 `ProcessService.start`。
- `PacketRepo` / `PacketDataRepo`：持久化 adapter SPI。

当前支持：

- pointer/value/history。
- commit CAS。
- expire reset。
- process complete/cancel 状态更新。
- typed packet baseline。
- process-start packet init hook。

边界：

- 本模块不包含业务 packet 子类、合同/签约/文档 helper 或 parent/root helper。
- JDBC repository 实现在 `flow-kernel-persistence-jdbc`。

更多说明见 `docs/API与边界/核心接口.md` 和 `docs/迁移与治理/延后范围.md`。
