# GAP-CLN-006 患者合并与历史费用

> **问题类型**：临床规则与主数据
> **严重度**：中危
> **影响范围**：门+住
> **优先级**：P2
> **状态**：待实施
> **父文档**：[SYS-IMP-012](../07-临床规则与主数据/SYS-IMP-012-临床闭环Gate与主数据锁价.md)

---

## 1. 问题描述

患者合并后双 patientId 历史费用分散

## 2. 发生场景

`PatientDomainService#mergePatient` 标记 source MERGED 并发 `PatientMergedEvent`；**未见**统一投影将历史 Encounter/Charge 迁移至 target 的叙事（可能由 listener 处理 — 需验收）。

## 3. 根因分析

合并与挂号防重（身份证 vs patientId）割裂；`resolvePatientIdForPreRegister` 身份证命中旧 id，合并后可能仍有 orphan encounter。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

1. 合并 Saga：迁移 encounter.patientId、account.patientId；2. 挂号 Gate：拒绝 MERGED 源 patientId；3. 与 **[GAP-FIN-006](../01-并发双写与条件更新/GAP-FIN-006.md)** 联动测试。


## 6. 涉及代码

- `PatientDomainService#mergePatient`
- `PatientMergedEvent` listeners（若有）
- `OutpatientRegistrationAppService`

## 7. 验收用例

- 合并后 source patientId 挂号 → 拒绝或重定向 target。
- 合并后历史 Charge 仍可在 target 下结算。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
