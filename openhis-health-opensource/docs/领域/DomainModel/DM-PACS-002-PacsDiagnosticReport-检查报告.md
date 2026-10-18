# DM-PACS-002 PacsDiagnosticReport 检查报告

> **限界上下文**：检查域（PACS/RIS）· 报告管理  
> **聚合类型**：实体聚合  
> **版本**：v1.2 · **日期**：2026-09-22  
> **输入**：[PacsAppService/04-FHIR](../../../../应用/zxplat-application/PacsAppService/04-FHIR领域模型.md)、AS-PACS-REP-001、AS-PACS-REP-002、[DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md)  
> **对称参考**：[DM-LIS-003](./DM-LIS-003-DiagnosticReport-检验报告.md)、[DM-CLN-008](./DM-CLN-008-DiagnosticReport-临床总检报告.md)

---

## 1. 概述

### 1.1 业务定义

**PacsDiagnosticReport** 是影像科检查报告事实表（`pacs_diagnostic_report`），按 **WorkItem 粒度** 一份主报告；与 LIS / CLN 报告分表存储。审核 `final` 后 upsert [DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md)。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 正文 | 所见 `findings`、结论 `conclusion` |
| 书写生命周期 | 草稿 → 提交待审 →（审核）final / 退回草稿 |
| PDF | 表存 `pdfStorageKey`；对象字节不进本聚合 |
| 修订 | `amendedFromId` 指向原版本（P1） |

### 1.3 非本聚合

| 不在内 | 归属 |
|--------|------|
| 审核通过后 index / CLN 投影 | REP-002 AppService 编排 |
| DICOM / 关键图实体 | imaging |
| 报告模板维护 | CFG-002 |

---

## 2. 持久化

**表**：`pacs_diagnostic_report`（主键 `id`）

唯一：租户内 **1 WorkItem : 1 主报告**（`amended_from_id IS NULL`）。

---

## 3. 领域模型

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | Long | 是 | 聚合根 |
| workItemId | Long | 是 | 关联 PacsWorkItem |
| patientId | Long | 是 | 冗余锚点（无 PHI） |
| encounterId | Long | 是 | 就诊 |
| organizationId | Long | 否 | 书写科室 |
| serviceRequestId | Long | 否 | 主医嘱（分项索引用） |
| statusEnum | String | 是 | 见 §4 |
| findings | String | 否 | 所见 |
| conclusion | String | 否 | 结论 |
| pdfStorageKey | String | 否 | PDF 对象存储键 |
| authorPractitionerId | Long | 否 | 书写医生 |
| submittedAt | Instant/LocalDateTime | 否 | 非空且 status=preliminary → **待审** |
| submittedBy | Long | 否 | 提交人 |
| issuedAt | LocalDateTime | 否 | final 发布时间 |
| auditorPractitionerId | Long | 否 | 审核人 |
| amendedFromId | Long | 否 | 修订来源 |
| tenantId | Long | 是 | 租户 |

---

## 4. 状态

| code | 含义 |
|------|------|
| `preliminary` | 草稿可编辑；若 `submittedAt≠null` 则为**待审**（不可改正文，除非退回清 submittedAt） |
| `final` | 已审核发布 |
| `amended` | 修订版（P1） |
| `entered-in-error` | 作废 |

```text
（无）──open/save──► preliminary(submittedAt=null)
                         │ submit
                         ▼
                   preliminary(submittedAt≠null) 待审
                      │ approve          │ reject
                      ▼                  ▼
                    final          preliminary(submittedAt=null)
                      │ cancelAudit
                      ▼
                   preliminary(submittedAt≠null) 待审
```

报告态与 WorkItem 检查态**正交**；书写前置：WorkItem.`completed`。

---

## 5. 领域事件

| 事件 | 触发 |
|------|------|
| PacsReportDraftSavedEvent | 保存草稿 |
| PacsReportSubmittedEvent | 提交待审 |
| PacsReportPdfBoundEvent | 绑定 PDF 键 |
| PacsReportApprovedEvent | 审核通过（REP-002） |
| PacsReportRejectedEvent | 审核退回（REP-002） |
| PacsReportAuditCancelledEvent | 取消审核（REP-09） |

---

## 6. 领域方法（写侧白名单）

| 方法 | 语义 |
|------|------|
| `openOrCreate(OpenOrCreatePacsReportCommand)` | 按 workItemId 取或建草稿；校验 WorkItem completed |
| `saveDraft(SavePacsReportDraftCommand)` | 写所见/结论；禁 final / 已提交待审 |
| `submit(SubmitPacsReportCommand)` | 置 submittedAt；发 Submitted 事件 |
| `bindPdf(BindPacsReportPdfCommand)` | 写 pdfStorageKey |
| `approve(ApprovePacsReportCommand)` | → final（REP-002） |
| `reject(RejectPacsReportCommand)` | 清 submittedAt，回可编辑（REP-002） |
| `cancelAudit(CancelPacsReportAuditCommand)` | final→待审；清 issuedAt/auditor（REP-09） |

读侧：`getById`、`getByWorkItemId`、`getPendingDetail`（待审）；**`searchReports` / `searchHistoryByPatient`**（REP-003 列表/历史）。

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-PDR-01** | 创建/书写前 WorkItem 须 `completed` |
| **BR-PDR-02** | 1 WorkItem 仅 1 主报告（幂等 openOrCreate） |
| **BR-PDR-03** | `final` 禁止 saveDraft / submit |
| **BR-PDR-04** | 已提交待审（submittedAt≠null 且 preliminary）禁止改正文，除非 reject |
| **BR-PDR-05** | PDF 仅存存储键；禁止在本表存二进制 |
| **BR-PDR-06** | submit 前建议已有 findings 或 conclusion（可配置；首版软校验非空其一） |
| **BR-PDR-07** | 报告查询默认仅主报告（`amendedFromId` 空） |
| **BR-PDR-08** | 取消审核仅 `final`；回退后仍待审（不可改正文，须再审或退回） |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-09-22 | cancelAudit / REP-09；状态机补 final→待审 |
| v1.0 | 2026-09-20 | 首版；解锁 REP-001/002 成码 |
| v1.1 | 2026-09-22 | 读侧 searchReports / searchHistoryByPatient；BR-PDR-07；解锁 REP-003 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PacsDiagnosticReportDomainService`

| 方法 |
|------|
| `openOrCreate` |
| `saveDraft` |
| `submit` |
| `bindPdf` |
| `approve` |
| `reject` |
| `cancelAudit` |

### QueryService：`PacsDiagnosticReportQueryService`

| 方法 |
|------|
| `searchPendingAudit` |
| `getById` |
| `findByWorkItemId` |
| `getPendingDetail` |
| `searchReports` |
| `searchHistoryByPatient` |

