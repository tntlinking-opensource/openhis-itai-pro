# GAP-EXE-004 住院绑定耗材执行无药嘱级 Gate

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住院
> **优先级**：P2
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)
> **关联 IF/BR**：IF-IP-EXE-02 bindItems；BR-IP-EXE-019；文档明确绑定耗材**不参与**药嘱库存 Gate

---

## 1. 问题描述

住院绑定耗材执行无药嘱级 Gate、当场扣库

## 2. 发生场景

执行勾选 `bindItems` 耗材时，路径为 `finalizeInpatientTemporaryPathDispense` + 批次末 `decreaseQuantitiesMerged`，无与药品同级的 FIFO dry-run / 全局预占。

## 3. 根因分析

设计刻意排除 bind 于 `NurseStationExecutionInventoryAssembler`；扣库与建单同事务但无预占。

## 4. 业务影响

并发执行同一绑定项可能双扣库存、双建 DeviceDispense。

## 5. 修改方案

1. 执行前对 bind 耗材做 **dry-run FIFO**（复用 Device 库存 Query + 模板库位，口径对齐 `DeviceDispenseNurseWorkflowsupport）。
2. Gate 通过后同事务写发放单；扣库仍在批次末合并扣减。
3. 幂等：同 `(deviceReqId, procedureId)` 或执行锚点已存在非删除发放 → 拒绝（可选 DB 部分唯一索引）。


## 6. 涉及代码

- `health-application/.../orderexecute/service/NurseStationOrderExecuteAppService.java`（`executeBindItems`）
- `health-module-support/.../orchestration/nursestation/DeviceDispenseNurseWorkflowSupport.java`

## 7. 验收用例

- 绑定耗材并发双执行：至多一条扣库成功。
- 库存不足整批失败（与药嘱 Gate 整批回滚策略一致）。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 绑定耗材执行前 `allocateDeviceFifoAtPerformLocation` dry-run Gate；状态 → 已实施 |
