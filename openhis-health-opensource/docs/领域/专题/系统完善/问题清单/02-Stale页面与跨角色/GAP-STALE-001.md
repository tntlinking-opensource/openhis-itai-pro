# GAP-STALE-001 门诊签退后 Stale 正式结算

> **问题类型**：Stale页面与跨角色
> **严重度**：**高危**（对已撤回医嘱收款）
> **影响范围**：门诊
> **优先级**：P0
> **状态**：已实施
> **父文档**：[SYS-IMP-007](../02-Stale页面与跨角色/SYS-IMP-007-跨角色Stale页面与状态不同步.md)
> **关联 IF/BR**：IF-DOR-16 签退；IF-OPC-07 正式结算；**BR-ORD-017** 签退回滚 `PLANNED→DRAFT`

---

## 1. 问题描述

门诊：预结算后医生签退，款台用旧 draft 正式结算

## 2. 发生场景

① 款台预结算（Charge 仍 `PLANNED`，生成 `paymentDraftId`）→ ② 医生签退（`rollbackPlanned…` → `DRAFT`，`releaseDispenseReserve`，`signOff`）→ ③ 款台**未刷新**，仍用旧 draft 点「正式结算」。

## 3. 根因分析

① `confirmOutpatientChargeSettlement` 重载 Charge 后仅 `assertNotSettled`（拦 `SETTLED`/`REFUNDED`），**不**要求 `PLANNED`；② `settleChargeItems` 无源态条件更新（**GAP-FIN-003**）；③ 未校验锚点 `MedicationRequest` 仍为可收费态（非 `signOff`/非软删）。

## 4. 业务影响

患者为已撤回医嘱付款；`syncAdviceStatesAfterChargeSettlement` 仍可能推进发药。

## 5. 修改方案

1. **正式结算 Gate（核心）**：`confirmOutpatientChargeSettlement` 在 `settleChargeItems` 前增加：
   - 全部 `chargeItem.status_enum == PLANNED`；
   - 锚点医嘱/发放未签退（`RequestStatus` 非回草稿路径 / Dispense 未 `is_deleted`）。
2. **条件 UPDATE（与 [GAP-FIN-003](../01-并发双写与条件更新/GAP-FIN-003.md) 合并）**：`UPDATE … SET status=SETTLED WHERE id IN (…) AND status_enum='PLANNED'`。
3. **draft 失效**：签退成功同事务或事件后，作废该就诊下仍挂载已回滚 Charge 的 `DRAFT` Payment（扩展 `cancelStaleOutpatientPaymentDrafts` 口径：绑定项含非 `PLANNED` 则作废 draft）。
4. **前端**：签退/预结算互斥提示；正式结算失败码明确「医嘱已变更，请重新预结算」。


## 6. 涉及代码

- `OutpatientChargeAppService#confirmOutpatientChargeSettlement`
- `ChargeItemDomainService#settleChargeItems`
- `DoctorStationAdviceAppService#withdrawDoctorStationAdviceBatch`（可选发布「签退作废 draft」事件）
- `DoctorStationTcmAppService#withdrawOrDeleteTcmHerbalPrescription`（对称）

## 7. 验收用例

- T1：预结算 → 签退 → 旧 draft 正式结算 → **拒绝**，无 SUCCESS Payment。
- T2：预结算 → 签退 → 重新预结算 → 仅未签退行可结。
- T3：已 SETTLED 后签退 → 签退失败（医嘱 `COMPLETED`）。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 四层防御落地：PLANNED Gate、条件 UPDATE、签退作废 draft、前端 EX-OPC-411 |
