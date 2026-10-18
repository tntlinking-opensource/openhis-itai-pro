# DM-BKG-003 Slot 号源

> **包**：`com.zxplat.plugin.booking.domain.slot`  
> **表**：`wkf_slot` · **写侧**：`SlotDomainService` · **读侧**：`SlotQueryService`  
> **版本**：v1.1 · **模式**：B · **日期**：2026-09-24

## 1. 概述

号源（Slot）是排班下可预约的时间片，维护容量、已约数、状态（free / busy / busy-unavailable / busy-tentative / entered-in-error）。

**职责**：供给、封锁/解封、占号/释放、超售标记、暂定确认、标差错、退役删除。  
**不做**：创建 Appointment；排班生命周期。

## 2–3. 持久化与实体

| 表 | 实体 |
| --- | --- |
| `wkf_slot` | `Slot` |

关键字段：`scheduleId`、`startTime`/`endTime`、`statusCode`、`capacity`、`bookedCount`、`overbookedFlag`、服务覆盖 JSON、`@Version`。

实体方法承载大量不变式（`assertBookable`、`increaseBookedCount…`、`toBusyUnavailable` 等）。

## 5. 事件

`SlotBooked/Released/Blocked/Unblocked/...`（`share.events.workflow.slot`）。

## 6. 领域服务

`SlotDomainService`：批量供给、封锁、占号、释放、删除等。  
读：`SlotQueryService`。

## 7. 不变式

| 规则 | 描述 |
| --- | --- |
| BR-SLT-001 | endTime > startTime |
| BR-SLT-002 | capacity > 0 |
| BR-SLT-003 | 封锁态不可预约 |
| BR-SLT-004 | 满号且未允许超售则拒绝占号 |
| BR-SLT-005 | 有占用时不可删除/标差错（除非 force） |
| BR-SLT-006 | 仅 busy-unavailable 可解封 |

## 8–9

上游 Schedule；下游挂号/改约 AS、HIS Extension。v1.1 M31 质量收口。
