# GAP-STALE-002 正式结算 / 核发双提交

> **问题类型**：Stale页面与跨角色
> **严重度**：**高危**
> **影响范围**：门+住
> **优先级**：P0
> **状态**：已实施
> **父文档**：[SYS-IMP-007](../02-Stale页面与跨角色/SYS-IMP-007-跨角色Stale页面与状态不同步.md)

---

## 1. 问题描述

门诊/住院：正式结算或核发双提交（Stale + 并发）

## 2. 发生场景

① 两收银窗口对同一批 ChargeItem / 同一 `paymentDraftId` 几乎同时点「正式结算」；② 两药房窗口对同一 `summaryNo` 同时核发；Stale 页面未刷新时与 ① 叠加。

## 3. 根因分析

① 读 Gate（`assertNotAlreadySettled` / Supply `APPROVAL` + Dispense `SUMMARIZED`）与写之间存在 TOCTOU；② `settleOutpatientPayment` / `#settleInpatientPayment`、住院 `settleInpatientChargeItems`、汇总 `agreeRequest` / `completeSummaryDispense` 均为读时校验 + 内存改态写回，无源态条件 UPDATE；③ 门诊 Stale 签退场景已由 **GAP-STALE-001** 单独 Gate，与本项并发防护互补。

## 4. 业务影响

同一费用双 SUCCESS 流水、患者多付；或同一汇总单双核发、账实不符。

## 5. 修改方案

与 **[GAP-FIN-001](../01-并发双写与条件更新/GAP-FIN-001.md)**、**[GAP-INV-004](../01-并发双写与条件更新/GAP-INV-004.md)** 合并落地；Stale 与并发为同一修复。

1. **Payment 条件迁移（核心）**：`updateSettleFieldsWhereIdAndSourceStatus` — `DRAFT → SUCCESS`；影响行数 ≠ 1 → `PAYMENT_RECONCILIATION_003`。
2. **ChargeItem 条件 UPDATE**：
   - 门诊：`settleChargeItems` 已要求 `PLANNED` 条件更新（GAP-STALE-001 / GAP-FIN-003）；
   - 住院：`settleInpatientChargeItems` — `PENDING_SETTLEMENT → SETTLED` 条件批量更新。
3. **汇总核发条件 UPDATE + 编排**：
   - Supply 同号行 `APPROVAL → AGREE`；
   - Dispense `SUMMARIZED → COMPLETED`（按 `summaryNo`）；
   - **须在扣库前**完成 ①②，扣库仍走 `decreaseQuantitiesMerged`（最后防线）。
4. **前端（体验层）**：正式结算 / 核发失败时提示「状态已变更，请刷新后重试」（与后端业务码对齐）。

## 6. 涉及代码

- `PaymentReconciliationRepository#updateSettleFieldsWhereIdAndSourceStatus`
- `PaymentReconciliationDomainService#settleOutpatientPayment` / `#settleInpatientPayment`
- `ChargeItemRepository#updateStatusWhereIdInAndSourceStatus`
- `ChargeItemDomainService#settleInpatientChargeItems`（门诊 `#settleChargeItems` 见 GAP-STALE-001）
- `SupplyRequestRepository#approveToAgreeWhereBusNoAndSourceStatus`
- `SupplyRequestDomainService#agreeRequest`
- `MedicationDispenseRepository#completeSummaryDispenseWhereSummaryNoAndSourceStatus`
- `MedicationDispenseDomainService#completeSummaryDispense`
- `InpatientPharmacySummaryDispenseAppService#dispenseSummaryMedications`（编排：同意 + 完成发放 → 再扣库）
- `OutpatientChargeAppService#confirmOutpatientChargeSettlement` / `InpatientSettleAppService#confirmInpatientSettle`（调用链受益）

## 7. 验收用例

- T1（门）：两线程同一 `chargeItemIds` + 同一 `paymentDraftId` 正式结算 → 仅 1 条 SUCCESS，另 1 `PAYMENT_RECONCILIATION_003` 或 `CHARGE_ITEM_INVALID_STATUS`。
- T2（住）：两线程同一住院草稿正式结算 → 同上。
- T3（核发）：两线程同一 `summaryNo` 核发 → 仅 1 次扣库与 AGREE，另 1「汇总单已核发或状态不允许，请刷新」。
- T4（Stale 叠加）：预结算后签退再双提交 → GAP-STALE-001 Gate 拒绝（与 T1 独立回归）。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 条件 UPDATE 落地：Payment / 住院 ChargeItem / 汇总核发；核发编排调整为态占位先于扣库 |
