# GAP-CHS-001 同一 ACTIVE 预结算快照双 confirm

> **问题类型**：外部集成与分布式顺序
> **严重度**：**高危**（双 setl_id、基金重复记账）
> **影响范围**：门+住
> **优先级**：P0
> **状态**：待实施
> **父文档**：[SYS-IMP-006](../06-外部集成与分布式顺序/SYS-IMP-006-医保平台与本地结算顺序.md)
> **关联 IF/BR**：`ChsOutpatientSettlementFacadeImpl#confirmSettleInternal` / `doConfirmSettleInternal`；住院 2304 对称

---

## 1. 问题描述

同一 ACTIVE 预结算快照双 confirm（2207/2304）

## 2. 发生场景

两收银窗对同一 `paymentDraftId` / `chrg_bchno` 几乎同时 confirm；均读到 `YbClinicPreSettle.STATUS_ACTIVE`，均调 2207/2304。

## 3. 根因分析

① Gate 读 `pre.getStatus()`；② `markClinicPreSettleSettled` 普通 save，**无** `UPDATE yb_clinic_pre_settle SET status=SETTLED WHERE id=? AND status=ACTIVE`；③ 与 GAP-FIN-001 本地 Payment 双提交**叠加**。

## 4. 业务影响

医保中心与 HIS 对账不平；冲正复杂。

## 5. 修改方案

1. **预结算快照条件迁移**（confirm 第一步）：

```sql
UPDATE yb_clinic_pre_settle
SET status = 'SETTLED', version = version + 1
WHERE id = :preId AND status = 'ACTIVE';
-- 影响行数=0 → 拒绝，提示重新预结算
```

2. **本地 Payment/ChargeItem 条件更新**（见 SYS-IMP-002 [GAP-FIN-001](../01-并发双写与条件更新/GAP-FIN-001.md)）**先于**平台 invoke，或同一本地事务内完成本地态迁移后再调平台（失败则本地回滚）。
3. **业务幂等键**：`confirmSettle` 入参 `Idempotency-Key` + `(tenantId, chrg_bchno)` 唯一表；重复返回同一 `settlementRecordId`。
4. **住院对称**：`yb_inpatient_pre_settle` 同等改造。


## 6. 涉及代码

- `chs-jilin-application/.../facadeimpl/ChsOutpatientSettlementFacadeImpl.java`
- `chs-jilin-application/.../inpatient/`（2304 路径）
- `chs-jilin-domain/.../settlement/service/ChargeBatchLifecycle.java`
- `chs-jilin-domain/.../outpatient/entity/YbClinicPreSettle.java`（建议加 `@Version`）

## 7. 验收用例

- 两线程同一 preId confirm：仅 1 次 2207/2304，1 条 `YbSettlementRecord`。
- 第二笔：本地拒绝，**不**再调平台。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
