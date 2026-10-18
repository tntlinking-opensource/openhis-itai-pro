# GAP-BKG-007 Appointment 乐观锁

> **问题类型**：并发双写与条件更新
> **严重度**：低危
> **影响范围**：门+住
> **优先级**：P3
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-003](../01-并发双写与条件更新/SYS-IMP-003-预约号源与床位预留并发边界.md)

---

## 1. 问题描述

`Appointment` 无 `@Version` / 无自然键唯一

## 2. 发生场景

改约/取消并发写 Appointment 可能 last-write-wins。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

**已采用**（2026-06-28）：

1. `Appointment` 映射 `@Version`（沿用表 `version` 列）
2. `cancelAppointment` / `rescheduleOutpatientBooking` 捕获乐观锁 → `APPOINTMENT_004`「预约已被修改，请刷新后重试」

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | Appointment @Version + 改约/取消乐观锁映射；状态 → 已实施 |
