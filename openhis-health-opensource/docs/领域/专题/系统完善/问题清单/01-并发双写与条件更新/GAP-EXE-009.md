# GAP-EXE-009 MedicationDispense 乐观锁未映射

> **问题类型**：并发双写与条件更新
> **严重度**：低危
> **影响范围**：门+住
> **优先级**：P3
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)
> **关联 IF/BR**：`AS-IP-PHA-001-SDD` §6.1 承诺「同一发放行并发配药/发药 乐观锁/版本」

---

## 1. 问题描述

`MedicationDispense` 表有 version 列但实体未映射乐观锁

## 2. 发生场景

表 `med_medication_dispense.version` 存在，JPA 实体 `MedicationDispense` **未** `@Version` 映射。

## 3. 根因分析

实现与 SDD 差距。

## 4. 业务影响

同发放行并发配药/核发可能 last-write-wins；物理库存仍靠 `InventoryItem` 防超扣。

## 5. 修改方案

1. `MedicationDispense` 增加 `@Version Integer version`。
2. `prepareMedicationDispense` / `dispenseMedication` 捕获 `OptimisticLockException` → 业务码「发放单已被他人处理，请刷新」。


## 6. 涉及代码

- `zxplat-domain/.../medication/medicationdispense/entity/MedicationDispense.java`
- `MedicationDispenseDomainService.java`

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | `MedicationDispense @Version` + 配药/核发乐观锁映射；状态 → 已实施 |
