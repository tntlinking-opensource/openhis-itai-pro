# DM-PEIS-008 PeisEncounterProgressQuery · 体检进度只读聚合

> **限界上下文**：health · peis（QueryService，**非**写聚合）  
> **版本**：v1.1 · **日期**：2026-06-27  
> **关联**：[Peis 04 §3.2](../../../../应用/zxplat-application/PeisAppService/04-FHIR领域模型.md#32-体检进度多模型联合不扩展-encounterstatus_enum)

---

## 1. 概述

联合 `Encounter(PE)` + `PeisServiceRequestExt` + `cln_observation` + `DiagnosticReport 输出 **展示态**（待检/待总检/可打印等），**禁止**扩展 Encounter 专用 status。

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `PeisEncounterProgressChangedEvent` | `encounterId, progressCode` | `signalProgressChanged` |

包路径：`com.zxplat.domain.share.events.custom.peisencounterprogress`

> 事件用于缓存失效/导检队列刷新；**不**持久化进度表。

---

## 6. 领域服务

### 6.1 写侧 — `PeisEncounterProgressQuery`（信号方法，无表写）

| 方法 | 说明 |
|------|------|
| `signalProgressChanged(SignalProgressChangedCommand)` | MAS-04/DES/INT 完成后发布进度变更事件 |

### 6.2 读侧 — `PeisEncounterProgressQuery`

| 接口签名 | 查询场景 | 说明 |
|----------|----------|------|
| `searchPendingDeptDesk(DeptDeskQuery, Pageable): Page<DeptDeskRowModel>` | DES-01 | 本科室待检 |
| `searchPendingMasterReview(MasterReviewQuery, Pageable): Page<MasterReviewRowModel>` | MAS-01 | 待总检（BR-MAS-01） |
| `searchPrintableReports(ReportPrintQuery, Pageable): Page<PrintableReportRowModel>` | REP-01 | DR final + Encounter finished |
| `searchGroupProgress(GroupProgressQuery, Pageable): Page<GroupProgressRowModel>` | GRP-06 | 团检批次进度 |
| `getProgressSummary(encounterId): PeisProgressSummaryModel` | REG-03 / MAS-02 | 单 Encounter 摘要 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-PRG-01** | 进度码由多表联合计算，禁止写 Encounter 专用 enum |
| **BR-PRG-02** | 分页过滤在 SQL 层完成，禁止先全量后内存过滤 |
| **BR-PRG-03** | `searchPrintableReports` 须 DR.status=final 且 Encounter=finished |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 增 §6.1 信号方法 + 事件 |
| v1.0 | 2026-06-27 | 首版 |
