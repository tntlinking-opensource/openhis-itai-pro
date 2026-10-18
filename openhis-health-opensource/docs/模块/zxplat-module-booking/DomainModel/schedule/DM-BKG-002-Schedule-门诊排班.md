# DM-BKG-002 Schedule 门诊排班

> **包**：`com.zxplat.plugin.booking.domain.schedule`  
> **表**：`wkf_schedule` · **写侧**：`ScheduleDomainService` · **读侧**：`ScheduleQueryService`  
> **版本**：v1.1 · **模式**：B · **日期**：2026-09-24

## 1. 概述

排班（Schedule）定义某机构在规划时间窗内可提供的门诊服务轮廓，可发布后批量生成号源（Slot）。

**职责**：草稿创建/更新 → 发布 → 停用/再激活/退役；代诊；从模板生成；复制到下周。  
**不做**：号源占用计数（Slot）；预约入队（Appointment）。

## 2. 持久化

| 表 | 实体 |
| --- | --- |
| `wkf_schedule` | `Schedule` |

关键字段：`organizationId`、`name`、`statusEnum`、`activeFlag`、`planningHorizonStart/End`、`healthcareServiceId`、服务/专科 JSON 快照、`sourceScheduleTemplateId`。

## 3–4. 实体与枚举

状态/角色枚举见 `com.zxplat.domain.share.enums.ScheduleStatus`、`ScheduleActorKind`；演员组合规则 `ScheduleActorCompositionRules`。

## 5. 事件

`ScheduleCreated/Published/Deactivated/Reactivated/Retired/...`（`share.events.workflow.schedule`）。

## 6. 领域服务

写：`ScheduleDomainService`（创建、发布、停用、退役、代诊、演员替换、标识同步等 Command）。  
读：`ScheduleQueryService`。

## 7. 不变式（摘要）

- 发布前须规划窗与服务轮廓合法  
- 已退役不可再发布占用  
- 演员组合满足 `ScheduleActorCompositionRules`

## 8–9. 映射与修订

上游：排班模板；下游：Slot 批量供给、门诊排班 AS。  
v1.1 M31 质量收口 Mode B。
