# DM-BKG-003 Slot · DomainModel-SDD

> **对应 DM**：`../DomainModel/slot/DM-BKG-003-Slot-号源.md`  
> **包**：`com.zxplat.plugin.booking.domain.slot` · **模式**：B · **日期**：2026-09-24

## 1. 边界

在内：`wkf_slot` 容量/占用/封锁/暂定/差错；实体方法承载 BR-SLT-*。在外：Appointment 创建、Schedule 发布。

## 2. 包结构

`entity/Slot|SlotIdentifier` · `command/*` · `repository/*` · `service/SlotDomainService|SlotQueryService` · `queries*` · `result/BookableSlotHint`

## 3–6. 关键不变式（实体）

| 方法 | BR |
| --- | --- |
| `assertTimeWindowValid` | BR-SLT-001 |
| `assertBookable` / `increaseBookedCount…` | BR-SLT-003/004/007 |
| `toBusyUnavailable` / `busyUnavailableToFreeOrBusy` | BR-SLT-006 |
| `assertRetirable` / `retire` | BR-SLT-005 |

## 7–8. 事件与缺口

`share.events.workflow.slot.*`；无缺口。单测：`SlotTest` 覆盖满号拒绝。
