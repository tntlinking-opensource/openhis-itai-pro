# GAP-BKG-004 虚拟号源物化竞态

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：门诊预约
> **优先级**：P2
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-003](../01-并发双写与条件更新/SYS-IMP-003-预约号源与床位预留并发边界.md)

---

## 1. 问题描述

虚拟号源 `virtualSlotKey` 物化竞态

## 2. 发生场景

`virtualSlotKey` 路径先 `materializeVirtualSlot` 再占号；两请求可能物化出**两个** slot 或重复物化失败。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

**已采用**（2026-06-28）：

1. Flyway `V11__Virtual_Slot_Unique.sql`：`(organization_id, start_time, end_time)` 部分唯一 `uk_wkf_slot_org_time_window`
2. `WeekTemplateMaterializeSupport#materializeVirtualSlot`：物化唯一冲突时回落查已有 slot
3. 物化与 `allocateBooking` 保持同一 UoW（沿用既有编排）

## 6. 工作量

2 人天

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | V11 唯一索引 + 物化幂等回落；状态 → 已实施 |
