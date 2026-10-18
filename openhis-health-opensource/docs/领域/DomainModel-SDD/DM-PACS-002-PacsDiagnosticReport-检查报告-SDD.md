# DM-PACS-002 PacsDiagnosticReport · Domain SDD

> **编码**：DM-PACS-002-SDD · **版本**：v1.2 · **日期**：2026-09-22  
> **成稿编号**：DM-PACS-002

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **包** | `com.zxplat.plugin.pacs.domain.diagnosticreport` |
| **表** | `pacs_diagnostic_report`（Flyway V13） |
| **依赖** | `PacsWorkItemRepository`（读 status）；`IDomainEventPublisher` |
| **不在内** | index upsert、CLN 投影、对象存储上传字节 |

---

## 2. 代码结构

```
domain/pacs/diagnosticreport/
├── entity/PacsDiagnosticReport.java
├── repository/PacsDiagnosticReportRepository.java
├── command/OpenOrCreatePacsReportCommand.java
├── command/SavePacsReportDraftCommand.java
├── command/SubmitPacsReportCommand.java
├── command/BindPacsReportPdfCommand.java
├── command/ApprovePacsReportCommand.java
├── command/RejectPacsReportCommand.java
├── command/CancelPacsReportAuditCommand.java
├── service/PacsDiagnosticReportDomainService.java
├── service/PacsDiagnosticReportQueryService.java
└── queries/model/PacsDiagnosticReportDetailQueryModel.java
```

共享：`PacsReportStatus` 枚举；错误码 `PACS_REPORT_*`；事件包 `share.events.pacs.diagnosticreport`。

---

## 3. 方法契约

| 方法 | 要点 |
|------|------|
| openOrCreate | 幂等按 workItemId；WorkItem 须 completed；新建 status=preliminary |
| saveDraft | BR-PDR-03/04；写 findings/conclusion/author |
| submit | 须 preliminary 且 submittedAt 空；置 submittedAt |
| bindPdf | pdfStorageKey 非空；允许 preliminary（含待审）与 final 补传 |
| approve | 须待审；→ final + issuedAt（供 REP-002） |
| reject | 须待审；清 submittedAt |
| cancelAudit | 须 final；→ 待审（保留 submittedAt；清 issuedAt/auditor） |
| getById / findByWorkItemId / getPendingDetail / searchPendingAudit | 读侧既有 |
| searchReports | 主报告；可选 status/org/patient；issuedAt 闭区间；updatedAt desc（REP-003） |
| searchHistoryByPatient | patientId 必填；主报告；可选 exclude；updatedAt desc（REP-003） |

待审判定：`statusEnum=preliminary && submittedAt != null`。

---

## 4. Flyway

`V13__pacs_diagnostic_report_ddl.sql`：表 + `uk (tenant_id, work_item_id) WHERE amended_from_id IS NULL AND is_deleted=false`。

---

## 5. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-20 | 首版 |
| v1.1 | 2026-09-22 | 读侧 searchReports / searchHistoryByPatient（REP-003） |
| v1.2 | 2026-09-22 | cancelAudit（REP-09） |
