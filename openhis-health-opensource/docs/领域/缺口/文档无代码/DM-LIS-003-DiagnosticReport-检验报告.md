# DM-LIS-003 DiagnosticReport 检验报告

> **限界上下文**：检验域 · 报告管理  
> **聚合类型**：实体聚合  
> **版本**：v1.0 · **日期**：2026-06-22

## 1. 概述

### 1.1 业务定义

DiagnosticReport 是检验报告的聚合，默认按 `ServiceRequest` 粒度生成（一个医嘱项一份报告）。发布后 upsert [DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md) 供医生站/PEIS 读侧。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 报告聚合 | 聚合同一 serviceRequestId 下的所有 Observation |
| 报告发布 | 审核通过后 final → 回写 health 报告索引 |
| 报告修订 | 召回 → amended → 重新发布 |
| 版本管理 | 保留修订历史 |

---

## 2. 持久化形态

**表**：`lis_diagnostic_report（主键：`id`）

---

## 3. 领域模型

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 聚合根标识 |
| serviceRequestId | Long | 关联医嘱 |
| workItemId | Long | 关联工单 |
| patientId | Long | 患者 |
| encounterId | Long | 就诊 |
| statusEnum | ReportStatus | preliminary / partial / final / amended / cancelled |
| resultIds | String (JSON) | Observation ID 列表 |
| issuedAt | Instant | 发布日期 |
| previousId | Long | 修订前版本 |
| reportTemplateId | Long | 版式模板 |
| tenantId / orgId | Long | 租户/院区 |

---

## 4. 枚举

**ReportStatus**：`partial`（部分，如微生物涂片报告） / `final`（已发布） / `amended`（已修订）

---

## 5. 领域事件

| 事件 | 触发条件 | 消费方 |
|------|----------|--------|
| DiagnosticReportFinalizedEvent | 报告发布 | CRB-01 / health 报告索引 |
| DiagnosticReportAmendedEvent | 报告修订重新发布 | CRB-03 / 临床端通知 |

---

## 6. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-DR-01** | 发布条件：所有关联 Observation 均为 final（或 cancelled） |
| **BR-DR-02** | 召回仅允许 statusEnum = final |
| **BR-DR-03** | 召回后 previousId 指向原版本 |
| **BR-DR-04** | 微生物三级报告：status 从 partial → final |

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-22 | 首版 |
