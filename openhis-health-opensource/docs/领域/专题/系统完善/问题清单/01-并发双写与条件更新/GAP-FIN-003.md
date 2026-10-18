# GAP-FIN-003 门诊 settleChargeItems 不校验源状态

> **问题类型**：并发双写与条件更新
> **严重度**：**高危**（GAP-FIN-001 子项，单独列出便于 Code Review）
> **影响范围**：门诊
> **优先级**：P0
> **状态**：待实施
> **父文档**：[SYS-IMP-002](../01-并发双写与条件更新/SYS-IMP-002-收费结算与账户并发边界.md)
> **关联 IF/BR**：IF-OPC-07；`ChargeItemDomainService#settleChargeItems`

---

## 1. 问题描述

门诊 `settleChargeItems` 不校验源状态（PLANNED）

## 2. 发生场景

住院 `settleInpatientChargeItems` 要求源态 `PENDING_SETTLEMENT`；门诊 `settleChargeItems` 直接 `batchUpdateStatus` → SETTLED，**不校验 PLANNED**。

## 3. 根因分析

`batchUpdateStatus` 通用方法无源态门禁。

## 4. 业务影响

异常态 ChargeItem 被强行 SETTLED；与 GAP-FIN-001 叠加放大双结风险；**医生签退后 Stale 正式结算**见 **SYS-IMP-007 GAP-STALE-001**。

## 5. 修改方案

- 门诊 settle 与住院对称：批量更新前断言 `status_enum = PLANNED`（或预结算后的允许态集合）。
- 与 [GAP-FIN-001](../01-并发双写与条件更新/GAP-FIN-001.md) 条件更新一并实现；签退/draft 失效见 **SYS-IMP-007**。

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
