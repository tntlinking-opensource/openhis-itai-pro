# GAP-FIN-005 退款受理窗口并发

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：门+住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-002](../01-并发双写与条件更新/SYS-IMP-002-收费结算与账户并发边界.md)
> **关联 IF/BR**：IF-REF-03；退号 IF-REG-10；`enterRefundProcessingWindow`

---

## 1. 问题描述

退款受理窗口 `enterRefundProcessingWindow` 并发

## 2. 发生场景

两窗口对同一 SUCCESS 支付单同时发起退费。

## 3. 根因分析

均读到 `SUCCESS` → 均 `setStatusEnum(REFUND_PROCESSING)`；`requestOutpatientRefund` 有 `existsByRelationId` 防重复逆向单，但**原单状态**仍可能被重复推进。

## 4. 业务影响

极端下双逆向单申请或状态机混乱。

## 5. 修改方案

1. `enterRefundProcessingWindow`：`UPDATE fin_payment_reconciliation SET status_enum='REFUND_PROCESSING' WHERE id=? AND status_enum IN ('SUCCESS','PARTIAL_REFUNDED')`。
2. 映射 `@Version`（[GAP-FIN-004](../01-并发双写与条件更新/GAP-FIN-004.md)）。
3. 退费 App 层：进入窗口失败 → 提示「已在退款处理中」。

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
