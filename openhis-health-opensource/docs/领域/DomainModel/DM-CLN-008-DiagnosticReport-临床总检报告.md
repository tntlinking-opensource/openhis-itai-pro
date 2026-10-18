# DM-CLN-008 DiagnosticReport · 临床总检报告（PE）

> **限界上下文**：health · clinical  
> **表名**：`cln_diagnostic_report  
> **版本**：v1.1 · **日期**：2026-06-27  
> **关联**：[Peis 04 §4.3](../../../../应用/zxplat-application/PeisAppService/04-FHIR领域模型.md#43-diagnosticreport--总检报告)、[DM-CLN-006 索引](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md)、[Lis 01 §10.3](../../../../应用/zxplat-application/LisAppService/01-概要设计.md#103-diagnosticreport-统一索引d2)

---

## 1. 概述

**PEIS 总检主报告** 事实表；与 LIS `lis_diagnostic_report、PACS `pacs_diagnostic_report **分表存储**，对齐 D2 索引策略。

| 模块 | 事实表 | index.module_enum | 领域包（Java） |
|------|--------|-------------------|----------------|
| LIS | `lis_diagnostic_report | `LIS` | `domain.lis.DiagnosticReport |
| PACS | `pacs_diagnostic_report | `PACS` | `domain.pacs.DiagnosticReport |
| **PEIS 总检** | **`cln_diagnostic_report** | **`PEIS`** | **`domain.clinical.DiagnosticReport** |

实体/服务类名均为 **`DiagnosticReport / `DiagnosticReportDomainService`**；**禁止** import `domain.lis.diagnosticreport.*` 替代本聚合。

---

## 2. 持久化形态

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| encounter_id | Long | FK；**1 Encounter(PE) 1 份主报告** |
| patient_id | Long | 冗余 |
| status_enum | String | partial / preliminary / final / amended / cancelled |
| summary_text | Text | 总检综述（MAS-03） |
| conclusion_text | Text | 总检结论 |
| result_refs_json | Text | 可选；关键 Observation id 列表 |
| pdf_storage_key | String | 可选；PRT 渲染后 |
| finalized_at | timestamptz | MAS-04 |
| finalized_by | Long | |
| tenant_id / org_id | Long | |

UNIQUE (`encounter_id`) WHERE `is_deleted=false`（院点：团检仍 1 人 1 Encounter 1 报告）

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `DiagnosticReport |
| **标识** | `id`；业务键 `encounterId` |

### 3.1 聚合根字段

与 §2 一致（Java：`encounterId`, `patientId`, `statusEnum`, `summaryText`, `conclusionText`, `resultRefsJson`, `pdfStorageKey`, `finalizedAt`, `finalizedBy`）。

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `DiagnosticReportDraftSavedEvent` | `id, encounterId` | `saveMasterDraft` |
| `DiagnosticReportFinalizedEvent` | `id, encounterId, patientId, statusEnum` | `finalizeMaster` |

包路径：`com.zxplat.domain.share.events.clinical.DiagnosticReport

> **P0 索引写入**：由 **MAS-04 AppService 同事务** 直调 `DiagnosticReportIndexUpsertService#upsertFromPeis`，**不**依赖异步监听器（P1 可订阅事件做缓存）。

---

## 6. 领域服务

### 6.1 写侧 — `DiagnosticReportDomainService`

| 方法 | 说明 | IF |
|------|------|-----|
| `createOrGetForEncounter(CreateForEncounterCommand)` | 首次 MAS-03 时 lazy 创建 preliminary | MAS-03 |
| `saveMasterDraft(SaveMasterDraftCommand)` | 更新综述/结论 | MAS-03 |
| `finalizeMaster(FinalizeMasterCommand)` | → final | MAS-04 |

### 6.2 读侧 — `DiagnosticReportQueryService`

| 方法 | 说明 |
|------|------|
| `getByEncounterId(encounterId)` | MAS-02/03/REP |
| `mustGetById(id)` | REP-02 grant 前置 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CLN-DR-01** | 1 PE Encounter 1 行主报告 |
| **BR-CLN-DR-02** | `final` 后改正文须 amended 或 MAS-05 退回流程 |
| **BR-CLN-DR-03** | `finalizeMaster` 前 AS 校验分科完成（BR-MAS-01） |
| **BR-CLN-DR-04** | `peis_report_grant_ext.diagnostic_report_id` → **本表 id** |

---

## 8. 与 DM-CLN-006 衔接

MAS-04 完成后：

```text
DiagnosticReportDomainService#finalizeMaster
  → DiagnosticReportIndexUpsertService#upsertFromPeis(
        sourceReportId = cln_diagnostic_report.id,
        moduleEnum = PEIS,
        uniqueKey = "PEIS:" + id
     )
  → EncounterDomainService#finishEncounter
```

---

## 9. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 去 `Clinical` 前缀；类名 `DiagnosticReport（clinical 包与 LIS 包隔离） |
| v1.0 | 2026-06-27 | 拍板表名；自 DM-CLN-006 歧义项独立 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`DiagnosticReportDomainService`

| 方法 |
|------|
| `createOrGetForEncounter` |
| `saveMasterDraft` |
| `finalizeMaster` |
| `increasePrintCount` |

### QueryService：`DiagnosticReportQueryService`

| 方法 |
|------|
| `getByEncounterId` |
| `mustGetById` |

