# GAP-ATD-004 PLANNED 住院登记双窗

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住
> **优先级**：P2
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-004](../01-并发双写与条件更新/SYS-IMP-004-入出转分床与床位运营并发边界.md)
> **关联 IF/BR**：`InpatientRegistrationAppService#submitInpatientRegistration`；**AS-IP-FIN-001 §7**

---

## 1. 问题描述

PLANNED 住院登记/有申请登记双窗

## 2. 发生场景

两窗口对同一 `encounterId`（PLANNED 有申请）同时登记；均读到 PLANNED，均 `registerInpatientEncounter`。

## 3. 根因分析

读时 `EncounterStatus.PLANNED` Gate；`registerInpatientEncounter` 无 `UPDATE ... WHERE status=PLANNED` 条件迁移。

## 4. 业务影响

重复登记、就诊主档 last-write-wins、`InpatientAdmissionCompletedEvent` 双发，读模型短暂不一致。

## 5. 修改方案

登记 Cmd 改为条件更新就诊状态；影响行数=0 → EX-IP-REG-003。

**已采用**：**方案 1** — 就诊行 `PESSIMISTIC_WRITE` + 锁内 `assertInpatientRegistrationAllowed`（等价于条件 UPDATE，对齐 GAP-ATD-001）。

## 6. 涉及代码

- `EncounterRepository.java`
- `EncounterDomainService#registerInpatientEncounter`
- `InpatientRegistrationAppService#submitInpatientRegistration`

## 7. 解决日志

| 项 | 说明 |
| --- | --- |
| **实施日期** | 2026-06-28 |
| **采用方案** | **方案 1**：悲观行锁（等价条件 UPDATE）；未新增 Flyway / 原生 SQL |
| **硬门禁** | `registerInpatientEncounter`：`findByIdAndClassEnumAndIsDeletedFalse` + 写锁；锁内 `PLANNED` 断言后 `transitToInpatientRegistered` |
| **事务** | `registerInpatientEncounter` 标注 `@Transactional`，持锁至 save + 事件发布 |
| **批量** | 多项登记按 `encounterId` 升序加锁（防死锁） |
| **App 映射** | 领域 `ENCOUNTER_INVALID_STATUS` → **EX-IP-REG-003** |
| **软 Gate 保留** | Pre-8b `mustGetEncounterOfClass` + `PLANNED` 读校验，**不作**并发硬兜底 |
| **不在范围** | 无申请直登（INSERT 新行） |
| **关联 GAP-ATD-005** | `Encounter` `@Version` 于 005 阶段 B 落地；登记路径 `OptimisticLockingFailureException` → **EX-IP-REG-003** |

**变更文件**

| 模块 | 文件 | 要点 |
| --- | --- | --- |
| zxplat-domain | `EncounterRepository.java` | 悲观写锁查询 |
| zxplat-domain | `EncounterDomainService.java` | `requireEncounterForUpdate`；登记路径行锁 + `@Transactional` |
| health-application | `InpatientRegistrationAppService.java` | `ENCOUNTER_INVALID_STATUS` / `OptimisticLockingFailureException` → `EX-IP-REG-003` |

## 8. 验收用例

- 同 `encounterId` 双窗口有申请登记 → 1 成功 `REGISTERED`，1 **EX-IP-REG-003**（SYS-IMP-004 T4）。
- 先登记成功后再提交 → **EX-IP-REG-003**（Pre-8b 或领域层均可拦）。
- 正常有申请登记 → 状态迁移一次，事件一次。

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 行锁 + 异常映射；状态 → 已实施 |
