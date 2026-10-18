# GAP-STALE-004 药房已配药后护士仍可取消执行（费退药仍发）

> **问题类型**：Stale页面与跨角色
> **严重度**：中危
> **影响范围**：住
> **优先级**：P1
> **状态**：已实施
> **父文档**：[SYS-IMP-007](../02-Stale页面与跨角色/SYS-IMP-007-跨角色Stale页面与状态不同步.md)
> **关联 IF/BR**：IF-IP-CXL-01；**BR-IP-CXL-010**（已核发走待退药，**未覆盖 IN_PROGRESS/PREPARED**）

---

## 1. 问题描述

住院：药房已配药（IN_PROGRESS/PREPARED）后护士取消执行

## 2. 发生场景

① 药房「保存配药」（`PREPARATION→IN_PROGRESS`）→ ② 护士取消执行 → ③ 费用回滚/void，Dispense **未**删除 → ④ 药房「核发」成功。

## 3. 根因分析

`rollbackMainDispenseAndCharge` 仅分支处理 `PREPARATION` / `SUMMARIZED` / `COMPLETED`；**无** `PREPARED` / `IN_PROGRESS` 分支；药房写路径**不**校验 Charge 仍为 `PENDING_SETTLEMENT`。

## 4. 业务影响

费用已取消/void，药品仍发出；住院账实不符。

## 5. 修改方案

1. **取消执行 Gate（推荐）**：存在关联 Dispense 态 ∈ `{PREPARED, IN_PROGRESS}` → **EX-IP-CXL-204** 或新码「药房配药中，请先作废配药或完成退药流程」。
2. **取消时强制回退 Dispense**：对 `PREPARED/IN_PROGRESS` 调用 `cancelMedicationDispense`（`DECLINED`）或回退 `PREPARATION`，再 void Charge。
3. **核发 Gate**：`dispenseMedication` 前校验关联 Charge（按 `procedureId` 或 dispense 锚点）仍为 `PENDING_SETTLEMENT` 且 Procedure 未被 CANCEL 对冲。

**落地（2026-06-28）**：采用方案 1 + 3（双层 Gate，不强制护士侧回退 Dispense）：
- `NurseStationOrderCancelExecuteWriteAssembler#assertPharmacyPreparingDispenseGate`：Pre 链拦截 `PREPARED/IN_PROGRESS` → **EX-IP-CXL-204**。
- `InpatientPharmacyDispenseAppService#assertDispenseExecutionChargeGate`：核发前校验 Procedure 未对冲 + Charge 仍为 `PENDING_SETTLEMENT` → **EX-OPP-203**。

## 6. 涉及代码

- `NurseStationOrderCancelExecuteWriteAssembler#assertPharmacyPreparingDispenseGate`
- `NurseStationOrderCancelExecuteWriteAssembler#rollbackMainDispenseAndCharge`（`COMPLETED` 仍走待退药，无变更）
- `InpatientPharmacyDispenseAppService#dispenseInpatientMedications`
- `InpatientPharmacyDispenseAppService#assertDispenseExecutionChargeGate`
- `ChargeItemQueryService` + 取消执行 Pre 链

## 7. 验收用例

- T1：`IN_PROGRESS` 后取消执行 → 拒绝或 Dispense 同步作废。
- T2：取消成功后同一 dispense 核发 → 失败。
- T3：`COMPLETED` 仍走待退药（回归 BR-IP-CXL-010）。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 双层 Gate 落地：取消执行拦截 PREPARED/IN_PROGRESS；核发校验 Charge/Procedure |
