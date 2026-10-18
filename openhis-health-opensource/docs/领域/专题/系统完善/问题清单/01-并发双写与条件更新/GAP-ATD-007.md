# GAP-ATD-007 booking 预留与分床竞态

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住
> **优先级**：P1
> **状态**：**已实施**（2026-06-28，联动 GAP-BKG-003/006）
> **父文档**：[SYS-IMP-004](../01-并发双写与条件更新/SYS-IMP-004-入出转分床与床位运营并发边界.md)
> **关联**：[GAP-BKG-006](./GAP-BKG-006.md)、[GAP-BKG-003](./GAP-BKG-003.md)

---

## 1. 问题描述

booking 床位预留与护士站分床争用同一 `adm_location` 床态，缺乏统一锁与语义。

## 2. 发生场景

- 双窗 booking 预留 vs 护士站 `occupyBed` 并发
- 预留床被其他患者分床
- `reserveBed` 误写 `IN_USE` 与在院占床不可分

## 3. 根因分析

`operational_enum` 仅 `NOT_IN_USE`/`IN_USE`；`reserveBed` 无行锁；分床不查 booking 患者。

## 4. 业务影响

一床双占、预约失效、护士端提示不清。

## 5. 修改方案

**已采用**（与 GAP-BKG-006 同一 epic）：
- `LocationOperational.RESERVED`；`reserveBed` 行锁 + `NOT_IN_USE→RESERVED`
- `releaseReservedBed`；`occupyFromReservation` 路径 `RESERVED→IN_USE`
- `InpatientBedBookingQueryExtension` / `ConsumeExtension`；分床 Pre 校验患者
- `bkg_bed_reservation_ext` 同日同床唯一索引（GAP-BKG-003）

## 6. 涉及代码

- `Location.java`、`LocationDomainService.java`
- `NurseStationATDManagerAppService.java`
- `BedBookingAppService.java`、`HealthBedReservationWriterAdapter.java`
- `InpatientBedBookingReaderAdapter`、`InpatientBedBooking*Extension*`
- Flyway `V9__Bed_Reservation_Unique.sql`

## 7. 解决日志

| 项 | 说明 |
| --- | --- |
| **实施日期** | 2026-06-28 |
| **床态三态** | `NOT_IN_USE` / `RESERVED` / `IN_USE` |
| **预留写** | `requireBedForUpdate` + `reserve()` |
| **分床读** | `resolveTargetBedAssignGate`；异患者 → **EX-IP-ATD-207** |
| **分床写** | `occupyFromReservation` + 消费预约扩展 |
| **DB** | `(location_id, intended_admission_date)` 部分唯一 |

## 8. 验收用例

- 同床同日双预留：1 成功，另 1 冲突。
- 预留完成后他人分床：**EX-IP-ATD-207**。
- 同患者预约床分床：成功，`RESERVED→IN_USE`，预约关闭。
- 预留与 `occupyBed` 并发：行锁互斥。

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分 |
| v1.1 | 2026-06-28 | 与 BKG-003/006 联动落地；状态 → 已实施 |
