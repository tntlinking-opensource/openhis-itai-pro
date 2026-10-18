# GAP-BKG-006 booking 预留 vs health 分床未联动

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住
> **优先级**：P2
> **状态**：**已实施**（2026-06-28，与 GAP-ATD-007 同一 epic）
> **父文档**：[SYS-IMP-003](../01-并发双写与条件更新/SYS-IMP-003-预约号源与床位预留并发边界.md)
> **关联 IF/BR**：`SYS-IMP-004` GAP-ATD-007

---

## 1. 问题描述

booking 床位预留 vs health 分床占床未联动

## 2. 发生场景

booking 标记床位 RESERVED；health 分床仅查 `operational_enum`；预留时未锁 health Location。

## 3. 根因分析

双模块各写床态；`reserveBed` 误用 `occupy()`。

## 4. 业务影响

同 GAP-ATD-007。

## 5. 修改方案

**已采用**：health Location 唯一真相；`reserveBed`/`releaseReservedBed` 行锁；booking Reader/Consume Client；护士站分床联动。

## 6. 工作量

4～5 人天（**已落地**）

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿 |
| v1.1 | 2026-06-28 | 与 ATD-007 联动实施 |
