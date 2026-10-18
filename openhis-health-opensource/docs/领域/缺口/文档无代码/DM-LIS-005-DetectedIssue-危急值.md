# DM-LIS-005 DetectedIssue 危急值

> **限界上下文**：检验域 · 危急值管理  
> **聚合类型**：实体聚合  
> **版本**：v1.0 · **日期**：2026-06-22

## 1. 概述

### 1.1 业务定义

DetectedIssue 表达**检验结果触发的危急值告警与闭环管理**。事实源在 `Observation.interpretation`，DetectedIssue 为此事实的告警包装——含严重程度、通知、临床确认闭环。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 告警记录 | 严重程度、识别时间、涉及患者/就诊/结果 |
| 通知管理 | 电话/短信/站内信记录 |
| 闭环确认 | 临床确认时间、处理措施 |

---

## 2. 持久化形态

**表**：`lis_detected_issue`（主键：`id`）

---

## 3. 领域模型

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 聚合根标识 |
| statusEnum | IssueStatus | preliminary / final / mitigated / entered-in-error |
| severityEnum | Severity | high（危急）/ moderate |
| code | String | 危急值类别编码 |
| patientId | Long | 患者 |
| encounterId | Long | 就诊 |
| observationId | Long | 触发的结果 |
| identifiedDateTime | Instant | 识别时间 |
| authorId | Long | 检验师 |
| notificationMethod | String | 通知方式 |
| notifiedAt / notifiedTo | Instant / String | 通知时间/对象 |
| mitigationAction | String | 临床处理措施 |
| acknowledgedAt | Instant | 临床确认时间 |
| acknowledgedBy | Long | 确认人 |
| tenantId / orgId | Long | 租户/院区 |

---

## 4. 枚举

**IssueStatus**：`preliminary` / `final` / `mitigated`

**Severity**：`high` / `moderate`

---

## 5. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CRT-01** | 触发条件：Observation.interpretation = HH/LL + 值超过阈值 |
| **BR-CRT-02** | 阈值按项目+性别+年龄分层 |
| **BR-CRT-03** | 通知完成后 status → final；临床确认后 → mitigated |
| **BR-CRT-04** | 超时未确认（`lis_config.crt.acknowledgeTimeoutMinutes`）→ 升级通知 |

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-22 | 首版 |
