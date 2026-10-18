# Booking · 体检号源与预约 · 应用服务设计（AS）

> **编码**：AS-BKG-HC-001 · **日期**：2026-09-24  
> **实现**：`HealthCheckupSlotCalendarAppService` + `HealthCheckupBookingAppService`（+ Occupy）  
> **SDD**：`../AppService-SDD/AS-BKG-HC-001-体检号源与预约-SDD.md`  
> **产品**：`../18-体检号源日历与预约-需求定稿.md`

## 1. 边界

在内：号源模板/月览/日详/休班开放/容量/复制日计划；个人/团体体检预约、签到、套餐、团体单位/批次/名册。  
在外：门诊挂号 REG；HIS 患者写。

## 2. 接口总览（分组）

### 2.1 日历 `HealthCheckupSlotCalendarAppService`

| IF | 方法 |
| --- | --- |
| IF-BKG-HC-C01 | `getSlotTemplate` / `saveSlotTemplate` |
| IF-BKG-HC-C02 | `generateFutureDays` |
| IF-BKG-HC-C03 | `loadMonthOverview` / `loadDayDetail` |
| IF-BKG-HC-C04 | `updateSlotCapacity` / `setDayRest` / `setSlotOpen` / `setDayContractTypes` |
| IF-BKG-HC-C05 | `addDaySlot` / `batchAddDaySlots` / `deleteDaySlot` / `copyDayPlan` |

### 2.2 预约 `HealthCheckupBookingAppService`

| IF | 方法 |
| --- | --- |
| IF-BKG-HC-B01 | 团体单位/批次/名册 CRUD + 导入 |
| IF-BKG-HC-B02 | `batchCreateHealthCheckupBooking` / `createIndividualHealthCheckupBooking` |
| IF-BKG-HC-B03 | `checkInHealthCheckup` |
| IF-BKG-HC-B04 | 套餐 `page/save/deleteHealthCheckPackage` |

## 3. Gap

无。
