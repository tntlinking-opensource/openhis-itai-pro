# DM-PUB-001 InfectiousDiseaseReport 传染病上报

> **限界上下文**：公共卫生域 · 传染病上报（卡片）  
> **聚合类型**：实体聚合（根：`InfectiousDiseaseReport）  
> **版本**：v1.1（列表走主表只读；v1.0：最小闭环）

---

## 1. 概述

### 1.1 业务定义

**传染病上报（InfectiousDiseaseReport）** 聚合表达院内发现的传染病上报事实：从诊断触发、填报卡片、提交、订正到归档的最小状态链；引用 **`Encounter`**、**患者**、**诊断编码**，与门诊接诊入口可在应用层组合。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 卡片生命周期 | 草稿 → 已提交 → 订正链（末态锁定策略见不变式） |
| 上报模式 | **强制上报 / 暂缓上报** 二选一（REQ） |
| 订正 | 保留版本链由 SDD 细化（事件载荷须含版本锚点） |

### 1.3 适用范围

全院门诊住院共用本聚合；权限过滤在应用层或主表只读查询中完成，不归入聚合写方法。

### 1.4 与 Condition（诊断）的职责边界

| 聚合 | 职责 |
| --- | --- |
| **Condition / 诊断记录**（**DM-CLN-003** 等） | 诊断事实来源 |
| **InfectiousDiseaseReport** | 上报业务状态与卡片字段；**重复录入诊断摘要允许快照** |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**主表单表聚合**；边界以 `InfectiousDiseaseReport.id` 划分。

**表结构**：`pub_infectious_disease_Report（主键：`id`）。

**高频查询模式**：按 `patient_id`；按 `encounter_id`；按 `report_status_enum`；按 `organization_id` + 填报时间。

**推荐索引**：`idx_pub_inf_org_time`（`organization_id`, `submitted_at`）；`idx_pub_inf_encounter`（`encounter_id`）。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 传染病上报标识 | `InfectiousDiseaseReportId` | 雪花 ID |

### 2.3 状态机

```text
[DRAFT] ──submit──> [SUBMITTED] ──correct──> [CORRECTED]
                         │                         │
                         └──────── correct ────────┘（版本链由版本号区分）
[DRAFT] ──defer──> [DEFERRED] ──resume──> [DRAFT]
```

#### 状态定义

| value | code | info | 语义 |
| --- | --- | --- | --- |
| 1 | `DRAFT` | 草稿 | 可编辑 |
| 2 | `SUBMITTED` | 已提交 | 对外成功送出由集成确认后可标记 |
| 3 | `CORRECTED` | 已订正 | 订正生成新版本行或同表版本字段（SDD） |
| 4 | `DEFERRED` | 暂缓 | 暂缓上报模式 |
| 5 | `VOIDED` | 已作废 | |

> **持久化**：`report_status_enum` **String 存 code**。

---

## 3. 实体

### 3.1 传染病上报（InfectiousDiseaseReport）

对应表 `pub_infectious_disease_Report。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 标识 | `id` | `Long` | 是 | 主键 |
| 机构 | `organization_id` | `Long` | 是 | 上报责任单位 |
| 患者标识 | `patient_id` | `Long` | 是 | |
| 就诊标识 | `encounter_id` | `Long` | 否 | |
| 上报模式 | `report_mode_enum` | `String` | 是 | 见 **§4.1** |
| 状态 | `report_status_enum` | `String` | 是 | **§2.3** |
| 触发诊断编码快照 | `trigger_icd_code` | `String` | 否 | ICD-10/11，字典口径 |
| 主诉快照 | `chief_complaint_snapshot` | `String` | 否 | REQ：引用就诊数据 |
| 诊断摘要快照 | `diagnosis_snapshot` | `String` | 否 | |
| 卡片扩展 JSON | `card_payload_json` | `String` | 否 | 国标卡片字段由集成映射 |
| 订正前标识 | `replaces_report_id` | `Long` | 否 | 订正链 |
| 版本号 | `report_version` | `Int` | 是 | 从 1 递增 |
| 提交时间 | `submitted_at` | `DateTime` | 条件 | |
| 提交人 | `submitted_by` | `Long` | 条件 | |
| 创建时间 | `created_at` | `DateTime` | 是 | |

---

## 4. 值对象与枚举

### 4.1 上报模式（InfectiousReportMode）

| value | code | info | 语义 |
| --- | --- | --- | --- |
| 1 | `MANDATORY` | 强制上报 | |
| 2 | `DEFERRABLE` | 可暂缓 | REQ 二选一 |

> **存储**：`report_mode_enum`，**String 存 code**。

---

## 5. 领域事件

### 5.1 事件定义

| 事件名称 | 英文名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 |
| --- | --- | --- | --- | --- |
| 上报已创建 | `InfectiousDiseaseReportCreatedEvent` | 创建草稿 | `reportId`, `patientId`, `encounterId`, `version` | 集成 |
| 上报已提交 | `InfectiousDiseaseReportSubmittedEvent` | 提交 | `reportId`, `submittedAt`, `version` | 前置预警适配 |
| 上报已订正 | `InfectiousDiseaseReportCorrectedEvent` | 订正保存 | `reportId`, `replacesReportId`, `version` | 审计 |
| 上报已暂缓 | `InfectiousDiseaseReportDeferredEvent` | 进入暂缓 | `reportId` | 集成 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
| --- | --- | --- | --- |
| — | `createDraft` | `DRAFT` | `InfectiousDiseaseReportCreatedEvent` |
| `DRAFT` | `submitReport | `SUBMITTED` | `InfectiousDiseaseReportSubmittedEvent` |
| `SUBMITTED` | `correctReport | `CORRECTED`（或新版本） | `InfectiousDiseaseReportCorrectedEvent` |
| `DRAFT` | `deferReport | `DEFERRED` | `InfectiousDiseaseReportDeferredEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务

#### 6.1.1 创建草稿（`createDraft`）

- **业务意义**：弹窗后保存卡片草稿。
- **入参**：患者、就诊、触发 ICD、快照字段、`reportModeEnum`。
- **出参**：`reportId`。
- **前置条件**：`report_mode_enum` 合法。
- **事件输出**：`InfectiousDiseaseReportCreatedEvent`。
- **失败语义**：参数非法拒绝。

#### 6.1.2 提交上报（`submitReport）

- **业务意义**：提交传染病卡片。
- **入参**：`reportId`、操作人、卡片载荷。
- **前置条件**：`DRAFT`；必填项满足卫健校验（应用层 + 规范）。
- **状态变化**：`DRAFT -> SUBMITTED`。
- **事件输出**：`InfectiousDiseaseReportSubmittedEvent`。
- **失败语义**：校验失败拒绝。

#### 6.1.3 订正（`correctReport）

- **业务意义**：REQ 订正。
- **入参**：原 `reportId`、新载荷、操作人。
- **前置条件**：`SUBMITTED` 或 `CORRECTED` 策略允许。
- **事件输出**：`InfectiousDiseaseReportCorrectedEvent`。
- **失败语义**：状态不允许拒绝。

#### 6.1.4 暂缓（`deferReport）

- **业务意义**：暂缓上报路径。
- **前置条件**：`report_mode_enum=DEFERRABLE`；`DRAFT`。
- **状态变化**：`-> DEFERRED`。
- **事件输出**：`InfectiousDiseaseReportDeferredEvent`。

#### 6.1.5 恢复暂缓（`resumeDeferredReport）

- **业务意义**：从暂缓回到可编辑草稿以便提交。
- **前置条件**：`DEFERRED`。
- **状态变化**：`DEFERRED -> DRAFT`。
- **事件输出**：可选 `InfectiousDiseaseReportCreatedEvent` 的轻量刷新或专用版本事件（SDD 二选一）。
- **失败语义**：状态不允许拒绝。

### 6.2 读侧查询服务

#### 6.2.1 分页查询（`pageReports`）

- **业务意义**：按科室权限筛选上报列表。
- **边界说明**：只读；权限裁剪在应用层或主表查询条件。
- **查询范围**：`pub_infectious_disease_Report 主表只读。
- **过滤口径**：机构、状态、时间、患者。
- **组装规则**：`submitted_at DESC`。
- **返回约束**：空页合法。

#### 6.2.2 详情（`getReport）

- **业务意义**：卡片明细。
- **过滤口径**：`reportId`。
- **返回约束**：不存在抛异常。

### 6.3 领域边界说明

- **国家前置预警单点登录与报文上传**为 **集成适配层**；领域发布 `SubmittedEvent` 后由适配消费。
- **完整 ICD 字典**不在本聚合。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
| --- | --- | --- |
| BR-PUB-INF-001 | `report_version` 从 1 递增；订正须指定 `replaces_report_id` 或等价版本策略 | SDD |
| BR-PUB-INF-002 | `DEFERRED` 仅允许从 `DRAFT` 且模式为可暂缓 | REQ |
| BR-PUB-INF-003 | `SUBMITTED` 后编辑须走 **订正** 路径 | |

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游 | 说明 |
| --- | --- |
| Patient / Encounter | **DM-ADM-001 / 002** |
| Condition | 诊断触发 |

### 8.2 下游影响

| 下游 | 说明 |
| --- | --- |
| 国家前置预警 | 集成适配 |
| 应用层 / UI | 列表与权限过滤 |

### 8.3 引用规则

- 卡片字段与国家规范冲突时以 **规范 + 映射表** 为准（**DOC-CONFLICT** 记录）。

### 8.4 事件流示意图

```text
Diagnosis trigger ── create/submit ──> InfectiousDiseaseReport ── SubmittedEvent ──> NationalAdapter
Patient / Encounter ─────────────────^
```

---

## 9. 补充

### 9.1 门诊引用

门诊病历、分诊主诉可写入快照字段。

---

**本 DM 正文结束。**
