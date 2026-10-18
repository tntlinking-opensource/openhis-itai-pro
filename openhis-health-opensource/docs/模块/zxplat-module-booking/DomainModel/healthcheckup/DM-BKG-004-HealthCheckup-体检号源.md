# DM-BKG-004 HealthCheckup 体检号源与预约扩展

> **包**：`com.zxplat.plugin.booking.domain.healthcheckup`  
> **写侧**：`HealthCheckupDomainService`、`HealthCheckupSlotCalendarDomainService`  
> **读侧**：`HealthCheckupQueryService`、`HealthCheckupSlotCalendarQueryService`  
> **版本**：v1.1 · **模式**：B · **日期**：2026-09-24

## 1. 概述

体检号源日历：按日计划（`HcDayPlan`）+ 时段（`HcDaySlot`）+ 模板（`HcSlotTemplate`）+ 日类型（`HcDayType`）；预约扩展表 `HealthCheckBookingExt` / 套餐 `HealthCheckPackage`。

对外占号端口：`HealthCheckupSlotOccupyPort`（实现在 application）。

## 2. 主要表/实体

| 实体 | 说明 |
| --- | --- |
| `HcSlotTemplate` | 号源模板 |
| `HcDayPlan` / `HcDaySlot` / `HcDayType` | 日计划/时段/类型 |
| `HealthCheckBookingExt` | 体检预约扩展 |
| `HealthCheckPackage` | 套餐 |

## 6. 领域服务

| 服务 | 职责 |
| --- | --- |
| `HealthCheckupSlotCalendarDomainService` | 模板保存、生成未来日、日休/开放、容量、复制日计划 |
| `HealthCheckupDomainService` | 体检预约扩展写 |
| 对应 QueryService | 月览/日详/可约日与时段 |

## 7. 不变式（摘要）

- 合同类型编码与 `PeisContractTypeCodes` 对齐  
- 被引用时段删除受限  
- 占号经 Port 与 Appointment 协作（应用层编排）

## 8–9

上游 Appointment/Slot；产品细则见 `18-体检号源日历与预约-需求定稿.md`。v1.1 M31 质量收口。
