# GAP-CLN-005 住院预结算诊断 Gate

> **问题类型**：临床规则与主数据
> **严重度**：中危
> **影响范围**：住
> **优先级**：P2
> **状态**：待验收
> **父文档**：[SYS-IMP-012](../07-临床规则与主数据/SYS-IMP-012-临床闭环Gate与主数据锁价.md)

---

## 1. 问题描述

住院预结算缺诊断 Gate（与门诊不对称）

## 2. 发生场景

门诊 `preSettleOutpatientCharges` 强制 `getEncounterDiagnosis` 非空；住院 `InpatientSettleAppService` 预结算路径 **需对称验收**。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

住院预结算/全结前 `assertEncounterHasDiagnosis`；失败码对齐门诊。


## 6. 涉及代码

- `InpatientSettleAppService.java`

## 7. 验收用例

- 无诊断住院预结算 → 拒绝。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
