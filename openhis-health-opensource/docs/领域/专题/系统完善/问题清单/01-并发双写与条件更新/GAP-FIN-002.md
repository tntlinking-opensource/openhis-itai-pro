# GAP-FIN-002 账户余额丢失更新

> **问题类型**：并发双写与条件更新
> **严重度**：**高危**（预交金余额错、出院少退/多扣）
> **影响范围**：住
> **优先级**：P0
> **状态**：待实施
> **父文档**：[SYS-IMP-002](../01-并发双写与条件更新/SYS-IMP-002-收费结算与账户并发边界.md)
> **关联 IF/BR**：IF-IP-PREP-03/04；IF-IP-STL-06（结算扣余额）；**BR-IP-PREP-005**

---

## 1. 问题描述

账户余额 `adjustAdvanceDepositBalance` 丢失更新

## 2. 发生场景

同一就诊并发「收预交金 + 住院结算扣余额」或「双收预交金」。

## 3. 根因分析

`AccountDomainService#adjustAdvanceDepositBalance`：`getBalance()` → `add(delta)` → `saveOrUpdateAccount`，**无** `SELECT FOR UPDATE`；`adm_account.version` 存在但实体**未** `@Version`。

## 4. 业务影响

展示余额与流水不一致；出院全结多退/少补错误。

## 5. 修改方案

1. **行锁（推荐）**：`requireActivePersonalCashAccount` 改为 `findForUpdateByPatientIdAndEncounterIdAndTypeCode`（`FOR UPDATE`）。
2. **或乐观锁**：`Account` 映射 `@Version`；冲突 → 「账户余额已变更，请刷新」。
3. **流水幂等**：预交金收/退接口带业务幂等键（如 `busNo` / 前端 requestId），重复提交返回同一 `paymentId`。


## 6. 涉及代码

- `AccountDomainService.java`
- `AccountRepository.java`
- `Account.java`（实体）
- `InpatientPrepayAppService.java`
- `InpatientSettleAppService.java`（`adjustAdvanceDepositBalance` 调用链）

## 7. 验收用例

- 并发两笔 +1000 预交金：余额 +2000，非 +1000。
- 并发收预交金与结算扣款：最终余额 = 初始 + 收 − 扣。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
