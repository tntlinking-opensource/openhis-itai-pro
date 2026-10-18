# GAP-FLW-003 办理出院 Gate 不含待结算费用

> **问题类型**：流程状态机与硬门禁
> **严重度**：中危
> **影响范围**：住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-009](../04-流程状态机与硬门禁/SYS-IMP-009-出院转科召回与财务硬门禁.md)
> **关联 IF/BR**：IF-IP-NUR-ATD-05；**BR-IP-ATD-016**（清床账户非零）

---

## 1. 问题描述

办理出院 Gate 不含待结算费用汇总

## 2. 发生场景

`assertDischargeGatePassed` 覆盖医嘱与待发药（301/302/303），**不**包含「存在 PENDING_SETTLEMENT ChargeItem」。

## 3. 根因分析

产品设计可能允许「先出院后结算」；AS 与实现需对齐。

## 4. 业务影响

若院方要求出院前结清，当前 Gate 不足。

## 5. 修改方案

1. **可配置策略**（推荐）：
   - `discharge.requireZeroPendingCharges=true` → 办理出院前 `pendingChargeCount==0`；
   - `false` → 仅清床/结算时拦（现状）。
2. 清床路径 `EX-IP-ATD-401` 保持硬拦未结算。


## 6. 涉及代码

- `NurseStationATDManagerAppService#assertDischargeGatePassed`（扩展或拆财务 Gate）

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
