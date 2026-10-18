# GAP-ATD-002 Location 领域未断言床态 + 无乐观锁

> **问题类型**：并发双写与条件更新
> **严重度**：**高危**（GAP-ATD-001 根因之一）
> **影响范围**：住
> **优先级**：P0
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-004](../01-并发双写与条件更新/SYS-IMP-004-入出转分床与床位运营并发边界.md)

---

## 1. 问题描述

`Location.occupyBed` 领域未断言床态 + 无乐观锁

## 2. 发生场景

`LocationDomainService#occupyBed` 仅 `assertPatientAssignable()` + `occupy()`，**不**调用 `assertNotOccupiedBed`；`Location` 实体无 `@Version`。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

一床两患、床态与其它写路径丢失更新；难自动发现。

## 5. 修改方案

① 领域 `occupy()` 前 `assertNotOccupiedBed()`（将 private 方法用于占床路径）；② `Location` 映射 `@Version`（**仅实体级**，不上提 `AuditedEntityBase`）；③ 与 [GAP-ATD-001](../01-并发双写与条件更新/GAP-ATD-001.md) 条件 UPDATE 二选一或叠加。

## 6. 涉及代码

- `Location.java`
- `LocationDomainService.java`
- `NurseStationATDManagerAppService.java`（乐观锁异常映射）

## 7. 解决日志

| 项 | 说明 |
| --- | --- |
| **实施日期** | 2026-06-28 |
| **采用方案** | **方案 1**：仅 `Location` 映射 `@Version`；不上提基类 |
| ① 占床前断言 | `assertVacantForOccupy()` + `occupyBed` 悲观行锁（随 GAP-ATD-001） |
| ② 乐观锁 | `Location` 字段 `@Version` → `adm_location.version` |
| ③ 条件 UPDATE | 未采用（001 行锁已等价） |
| **App 层** | `executeBedOperation` 捕获 `OptimisticLockingFailureException` → **EX-IP-ATD-201** |
| **与 001 关系** | 悲观锁防同床双占；`@Version` 防其它写路径并发改同一床位行 |

**变更文件**

| 模块 | 文件 | 要点 |
| --- | --- | --- |
| zxplat-domain | `Location.java` | `@Version` 映射 `adm_location.version` |
| health-application | `NurseStationATDManagerAppService.java` | `OptimisticLockingFailureException` → `EX-IP-ATD-201` |

## 8. 验收用例

- 同床双分床：仍由 GAP-ATD-001 行锁拦截 → `EX-IP-ATD-201`。
- 占床与其它 `Location` 写（如主数据编辑）并发：`save` 抛乐观锁冲突 → ATD-02 映射 `EX-IP-ATD-201`。

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | ① 随 GAP-ATD-001 落地 |
| v1.2 | 2026-06-28 | ② `@Version` + 乐观锁异常映射；状态 → 已实施 |
