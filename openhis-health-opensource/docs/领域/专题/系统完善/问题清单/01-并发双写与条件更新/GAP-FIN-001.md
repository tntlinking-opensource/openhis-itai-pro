# GAP-FIN-001 正式结算双提交（读时防重 TOCTOU）

> **问题类型**：并发双写与条件更新
> **严重度**：**高危**（直接多收患者钱）
> **影响范围**：门+住
> **优先级**：P0
> **状态**：待实施
> **父文档**：[SYS-IMP-002](../01-并发双写与条件更新/SYS-IMP-002-收费结算与账户并发边界.md)
> **关联 IF/BR**：IF-OPC-07 / **BR-OPC-005**；IF-IP-STL-06 / **BR-IP-STL-007**

---

## 1. 问题描述

正式结算 ChargeItem / Payment 双提交（读时防重 TOCTOU）

## 2. 发生场景

两个收银窗口对**同一批 ChargeItem** 几乎同时点「正式结算」；或同一草稿支付单双提交。

## 3. 根因分析

① `assertNotAlreadySettled` / `findPaySuccessOrRefundingOverlappingChargeItems` 在事务开头**只读**；② `settleOutpatientPayment` 仅 `assertStatusTransitionAllowed(DRAFT)` 读时校验；③ 两事务均可能在对方提交 SUCCESS 前通过 Gate。

## 4. 业务影响

同一费用双 SUCCESS 流水、患者多付、日结对账不平。

## 5. 修改方案

1. **条件更新（核心）**：`ChargeItemDomainService#settleChargeItems` / `#settleInpatientChargeItems` 改为：
   - `UPDATE adm_charge_item SET status_enum=?, version=version+1 WHERE id IN (...) AND status_enum IN (期望源态)`；
   - 影响行数 ≠ 期望条数 → 抛 `CHARGE_ITEM_INVALID_STATUS`（重复结算）。
2. **Payment 条件迁移**：`settleOutpatientPayment` / `#settleInpatientPayment` 使用 `UPDATE ... WHERE id=? AND status_enum='DRAFT'` 或 JPA `@Version`。
3. **提交前二次校验（可选）**：正式结算 Cmd 序列末尾再查一次 `findPaySuccessOrRefundingOverlappingChargeItems`。
4. **幂等键（体验层）**：`confirmOutpatientChargeSettlement` / `confirmInpatientSettle` 支持 `Idempotency-Key`，同 key 返回同一 `paymentId`。


## 6. 涉及代码

- `ChargeItemDomainService.java`（`batchUpdateStatus` 或专用 settle 方法）
- `ChargeItemRepository`（新增 `updateStatusIfIn`）
- `PaymentReconciliationDomainService.java`
- `OutpatientChargeAppService#confirmOutpatientChargeSettlement`
- `InpatientSettleAppService#confirmInpatientSettle`

## 7. 验收用例

- 两线程同一 `chargeItemIds` 正式结算：仅 1 条 SUCCESS 流水，另 1 明确失败。
- 同一 `paymentDraftId` 双 confirm：第二笔「支付草稿状态非法」。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
