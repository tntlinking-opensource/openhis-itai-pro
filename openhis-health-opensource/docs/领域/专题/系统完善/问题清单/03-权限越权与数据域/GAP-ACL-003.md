# GAP-ACL-003 住院结算/预交金无病区 Gate

> **问题类型**：权限越权与数据域
> **严重度**：中危
> **影响范围**：住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-008](../03-权限越权与数据域/SYS-IMP-008-多租户岗位与财务数据域.md)
> **关联 IF/BR**：IF-IP-STL-06；IF-IP-PREP-03

---

## 1. 问题描述

住院结算/预交金无病区-财务岗位 Gate

## 2. 发生场景

`InpatientSettleAppService`、`InpatientPrepayAppService` 校验 encounter 住院态与 Charge 归属，**不**校验财务岗位是否覆盖该就诊当前病区/科室。

## 3. 根因分析

住院财务 Gate 与护士站可见性未共享。

## 4. 业务影响

住院处可对非管辖病区患者结算/收退预交金（视院方岗位划分而定，多数医院应限制）。

## 5. 修改方案

1. 扩展 `EncounterFinancialAccesssupport 住院分支：对齐 `managedWardIds` 或财务科「全院结算」白名单角色；
2. 列表与写路径一致：`InpatientSettleAppService` 待结队列 Query 同源收窄。


## 6. 涉及代码

- `InpatientSettleAppService.java`
- `InpatientPrepayAppService.java`

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
