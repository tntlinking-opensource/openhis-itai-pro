# Booking · 门诊排班 · AS-SDD

> **AS-BKG-SCH-001-SDD** · `OutpatientScheduleAppService` · `POST /api/app/outpatient-schedule/{method}`

## 方法白名单

见 AS §2 IF-BKG-SCH-01～14；Request/Response 以 `application.outpatient.*` 源码为准。

## 依赖

`ScheduleDomainService` / `ScheduleQueryService`、`SlotDomainService`、停诊冻结域、模板解析。

本 SDD 不写 Gap。
