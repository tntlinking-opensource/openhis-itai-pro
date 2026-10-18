# Booking · 体检号源与预约 · AS-SDD

> **AS-BKG-HC-001-SDD** · 日历/预约/占号三类 AppService · **日期**：2026-09-24

## 1. 类落点

| 类 | HTTP 前缀（约定） |
| --- | --- |
| `HealthCheckupSlotCalendarAppService` | `/api/app/health-checkup-slot-calendar/` |
| `HealthCheckupBookingAppService` | `/api/app/health-checkup-booking/` |
| `HealthCheckupSlotOccupyAppService` | 占号端口适配 |

## 2. 方法白名单

见 AS §2；Request/Response 以 `application.healthcheckup.*` 源码为准。

## 3. 依赖

`HealthCheckupSlotCalendarDomainService`、`HealthCheckupDomainService`、对应 Query、`HealthCheckupSlotOccupyPort`。

本 SDD 不写 Gap。
