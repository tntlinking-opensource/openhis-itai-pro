# AS-PACS-REP-002 报告审核 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-REP-002-SDD · **版本**：v1.2 · **日期**：2026-09-22  
> **对应 AS**：AS-PACS-REP-002-报告审核.md（v1.4）  
> **实现阶段口径**：目标态全量契约（序号 31～35；同事务含 36/38；REP-09 取消审核）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application` · `pacs/report/audit` |
| **类** | `PacsReportAuditAppService` |
| **边界** | 待审列表/详情、通过/退回、**取消审核**、PDF 键预览；REP-07/09 写 DR + index + 投影 + 可选 PEIS ext |
| **方法权威** | DM-PACS-002、DM-CLN-006、DM-CLN-007 §6.3 |
| **HTTP** | `POST /api/app/pacsReportAudit/{method}` |

### 术语

| 项 | 约定 |
|----|------|
| **事务** | 写 IF 使用 `@Transactional`（与 REG/EXE/REP-001 一致） |
| **投影** | `LisResultProjectionService#projectFromPacsSummary`；无 AOL → 跳过 Observation |
| **取消回滚** | index `cancelled`；Observation `cancelled`；PEIS `resultReceived`→`sent`；均不软删 |
| **PDF 预览** | 返回 `pdfStorageKey`（不下载字节） |

---

## 2. 代码结构

```
health-application/.../pacs/report/audit/
├── service/PacsReportAuditAppService.java
├── request/SearchPendingAuditRequest.java
├── request/GetPendingAuditDetailRequest.java
├── request/ApproveReportRequest.java
├── request/RejectReportRequest.java
├── request/CancelAuditRequest.java
├── request/PreviewPdfRequest.java
├── response/...
├── dto/PacsAuditRowDto.java
└── mapper/PacsReportAuditMapper.java
```

### 领域白名单

| 服务 | 方法 | IF |
|------|------|-----|
| PacsDiagnosticReportQueryService | `searchPendingAudit` / `getPendingDetail` / `getById` | 06a/06/09 |
| PacsDiagnosticReportDomainService | `approve` / `reject` / `cancelAudit` | 07/08/09 |
| DiagnosticReportIndexUpsertService | `upsertFromPacs` / `revokeFromPacs` | 07/09 |
| LisResultProjectionService | `projectFromPacsSummary` / `revokeFromPacsSummary` | 07/09 |
| PeisServiceRequestExtDomainService | `updateDispatchFlags` | 07/09 可选 |
| PacsWorkItemQueryService | `getDetail` | 07/09 orderLinks |

---

## 3. 接口

| IF | 方法 | HTTP |
|----|------|------|
| 06a | `searchPendingAudit` | `.../searchPendingAudit` |
| 06 | `getPendingAuditDetail` | `.../getPendingAuditDetail` |
| 07 | `approveReport` | `.../approveReport` |
| 08 | `rejectReport` | `.../rejectReport` |
| 09 | `cancelAudit` | `.../cancelAudit` |
| 10 | `previewPdf` | `.../previewPdf` |

### 3.1 approveReport 编排序列

| # | 阶段 | 调用 |
|---|------|------|
| 1 | Gate | 操作人；reportId |
| 2 | Pre | getPendingDetail；作者≠审核人 |
| 3 | Cmd | approve |
| 4 | Cmd | upsertFromPacs |
| 5 | Pre | workItem.getDetail → orderLinks |
| 6 | Cmd | 逐 SR projectFromPacsSummary |
| 7 | Cmd | 有 PEIS ext 则 resultReceived |
| 8 | Asm | Response |

### 3.2 cancelAudit 编排序列

| # | 阶段 | 调用 |
|---|------|------|
| 1 | Gate | 操作人；原因非空 |
| 2 | Pre | getById；须 final |
| 3 | Cmd | cancelAudit → 待审 |
| 4 | Cmd | revokeFromPacs |
| 5 | Cmd | revokeFromPacsSummary |
| 6 | Cmd | PEIS resultReceived→sent |
| 7 | Asm | CancelAuditResponse |

---

## 4. 失败码

| 场景 | ErrorCode |
|------|-----------|
| 非待审 / 非 final | `PACS_REPORT_INVALID_STATUS` |
| 同人审核 | `PACS_REPORT_AUDITOR_SAME_AS_AUTHOR` |
| 退回无原因 | `PACS_REPORT_REJECT_REASON_REQUIRED` |
| 取消无原因 | `PACS_REPORT_CANCEL_REASON_REQUIRED` |
| 无 PDF 键 | `PACS_REPORT_PDF_NOT_FOUND` |

---

## 5. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-09-22 | REP-09 cancelAudit + 回滚口径 |
| v1.1 | 2026-09-21 | D-7；包路径对齐 compose；关闭投影最小实现；待审列表 |
| v1.0 | 2026-06-27 | 首版 |
