# GAP-REG-006 补打/重打审计

> **问题类型**：监管合规与追溯
> **严重度**：中危
> **影响范围**：门+住
> **优先级**：P2
> **状态**：待实施
> **父文档**：[SYS-IMP-013](../08-监管合规与追溯/SYS-IMP-013-监管合规链与核发追溯.md)

---

## 1. 问题描述

处方/小票/挂号单补打审计不完整

## 2. 发生场景

`printRegisterTicket` 累加 `printCount`；`PaymentReconciliation.printCount` 存在；**未见**强制写入 `whale_audit_logs` 或专用「补打原因 + 操作人」表。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

1. 补打 API 必填 `reprintReason`；2. 写 `PrintAuditRecord`（entityType, entityId, operator, reason, copyNo）；3. 超 N 次补打需主管授权。


## 6. 涉及代码

- `OutpatientRegistrationAppService#printRegisterTicket`
- 打印数据集服务（处方、收费小票）

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
