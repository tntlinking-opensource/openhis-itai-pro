# DM-BKG-002 Schedule · DomainModel-SDD

> **对应 DM**：`../DomainModel/schedule/DM-BKG-002-Schedule-门诊排班.md`  
> **包**：`com.zxplat.plugin.booking.domain.schedule` · **模式**：B · **日期**：2026-09-24

## 1. 边界

在内：`wkf_schedule` 生命周期与演员；在外：Slot 批量供给细节由 Slot 域承接、HTTP 由 OutpatientScheduleAppService。

## 2. 包结构

`entity/Schedule` · `command/*` · `repository/ScheduleRepository` · `service/ScheduleDomainService|ScheduleQueryService` · `queries*`

## 3–6. 口径与白名单

Long/LocalDateTime/软删；写方法一 Command；状态枚举 `ScheduleStatus`；演员规则 `ScheduleActorCompositionRules`。

## 7–8. 事件与缺口

发布 `share.events.workflow.schedule.*`；无待跟踪缺口。
