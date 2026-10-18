# GAP-BKG-002 末号源双请求（占号竞态）

> **问题类型**：并发双写与条件更新
> **严重度**：低危～中危（Slot 层相对成熟）
> **影响范围**：门+渠道
> **优先级**：P2
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-003](../01-并发双写与条件更新/SYS-IMP-003-预约号源与床位预留并发边界.md)

---

## 1. 问题描述

末号源双请求（占号竞态）

## 2. 发生场景

容量=1 的 slot，两请求同时 `allocateBooking`；一个成功，一个 `OptimisticLockingFailureException` 或「号源已满」。

## 3. 根因分析

JPA 乐观锁重试未统一封装；App 层可能未映射为业务码。

## 4. 业务影响

`Slot.increaseBookedCountAndRefreshStatus` → `assertBookable` 在实体态校验；`@Version` 冲突时第二笔失败。

## 5. 修改方案

**已采用**（2026-06-28）：

1. `ErrorCodeConstants.SLOT_CONFLICT`（`SLOT_003`）
2. `SlotDomainService#allocateBooking` / `#releaseBooking` 捕获 `OptimisticLockingFailureException` → 可理解业务提示
3. 有限重试：本次未实施（直接返回 `SLOT_003`）

## 6. 工作量

1 人天

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | Slot 乐观锁业务化；状态 → 已实施 |
