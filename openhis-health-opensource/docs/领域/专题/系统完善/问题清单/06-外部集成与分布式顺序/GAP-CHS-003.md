# GAP-CHS-003 2208/2305 与本地退费/反结顺序

> **问题类型**：外部集成与分布式顺序
> **严重度**：中危
> **影响范围**：门+住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-006](../06-外部集成与分布式顺序/SYS-IMP-006-医保平台与本地结算顺序.md)
> **关联 IF/BR**：`ChsOutpatientSettlementFacadeImpl#revokeSettleInternal`；`ChargeBatchLifecycle#markVoucherRevoked`

---

## 1. 问题描述

2208/2305 撤销与本地退费/反结顺序

## 2. 发生场景

本地退费已完成，2208 未调或失败；或 2208 成功本地仍 SUCCESS。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

`markVoucherRevoked` 对已 REVOKED 幂等；2205 禁止撤 SETTLED 行（须先 2208）。

## 5. 修改方案

与 AS-OP-FIN-003 / AS-IP-FIN-004 编排对齐：**enterRefundProcessingWindow → 2208/2305 → 本地逆向 Payment**；任一步失败整单 REFUND_PROCESSING 可回滚；记录 `yb_settlement_reversal` 与本地 `paymentId` 双向锚。

## 6. 工作量

3～4 人天

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
