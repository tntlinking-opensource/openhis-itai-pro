# GAP-BKG-003 床位预留同日同床防重 TOCTOU

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住院预约
> **优先级**：P1
> **状态**：**已实施**（2026-06-28，与 GAP-ATD-007 同一 epic）
> **父文档**：[SYS-IMP-003](../01-并发双写与条件更新/SYS-IMP-003-预约号源与床位预留并发边界.md)

---

## 1. 问题描述

床位预留同日同床防重 TOCTOU

## 2. 发生场景

两窗口对同一 `bedLocationId + intendedAdmissionDate` 同时预留；读 Gate 后双写。

## 3. 根因分析

无 DB 唯一；`reserveBed` 与 Appointment 非原子可见。

## 4. 业务影响

同床双预留；与分床竞态（GAP-ATD-007）。

## 5. 修改方案

**已采用**：
1. 部分唯一索引 `uk_bkg_bed_reservation_ext_bed_date_active`
2. `reserveBed` 悲观行锁（GAP-ATD-007）
3. 读 Gate `findActiveBedBookingByBedAndDate` 保留作软预检

## 6. 涉及代码

- `V9__Bed_Reservation_Unique.sql`
- `BedBookingAppService#reserveBed`
- `LocationDomainService#reserveBed`

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿 |
| v1.1 | 2026-06-28 | 唯一索引 + 行锁；状态 → 已实施 |
