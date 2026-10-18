# AS-PACS-REP-001 报告书写 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-REP-001-SDD · **版本**：v1.2 · **日期**：2026-09-22  
> **对应 AS**：AS-PACS-REP-001-报告书写.md（v1.3）  
> **实现阶段口径**：目标态全量契约（序号 27～30）+ 序号 56 影像附件装配 + 序号 43 REP-04 套用模板

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application` · `pacs/report/compose` |
| **边界** | 进入编辑/草稿/提交/绑定 PDF 键；不写 index、不投影、不上传字节 |
| **编排权威** | AS-PACS-REP-001 §2、§4 |
| **方法权威** | DM-PACS-002 §6 / Domain SDD |
| **实现方式** | `PacsReportComposeAppService`；HTTP 一律 POST |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务** | 写 IF 单事务 |
| **PDF** | Request 传 `pdfStorageKey`；C7 首版「存储键可用」 |
| **姓名** | `PatientQueryService#findPatientNamesByIds` |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../pacs/report/compose/
├── service/PacsReportComposeAppService.java
├── request/OpenComposeRequest.java
├── request/SaveReportDraftRequest.java
├── request/SubmitReportRequest.java
├── request/SaveReportPdfRequest.java
├── response/OpenComposeResponse.java
├── response/SaveReportDraftResponse.java
├── response/SubmitReportResponse.java
├── response/SaveReportPdfResponse.java
└── mapper/PacsReportComposeMapper.java
```

| 注入 | 用途 |
|------|------|
| `PacsDiagnosticReportDomainService` | open/save/submit/bindPdf |
| `PacsDiagnosticReportQueryService` | 详情读（可选，写后回读实体即可） |
| `PacsWorkItemQueryService` | 详情装配工单字段 |
| `PatientQueryService` | 姓名 |
| `CurrentPractitionerResolver` | 操作人 |
| `PacsReportComposeMapper` | DTO |
| `ReportImagingAttachmentAssembler` | 可选 imaging SPI → `imaging` 字段 |

### 2.2 领域方法白名单

| 聚合 | 方法 | IF |
|------|------|-----|
| PacsDiagnosticReport | `openOrCreate` | REP-01 |
| PacsDiagnosticReport | `saveDraft` | REP-02 |
| PacsDiagnosticReport | `submit` | REP-03 |
| PacsDiagnosticReport | `bindPdf` | REP-05 |
| PacsWorkItem | `getDetail` | REP-01 Asm |
| Patient | `findPatientNamesByIds` | REP-01 |
| `PacsReportImagingAttachmentPort`（可选） | `loadAttachment` | REP-01 Asm（序号 56） |

---

## 3. 按接口展开

### 3.1 应用服务接口

| IF | 方法 | HTTP |
|----|------|------|
| IF-PACS-REP-01 | `openCompose` | `POST /api/app/pacsReportCompose/openCompose` |
| IF-PACS-REP-02 | `saveDraft` | `POST /api/app/pacsReportCompose/saveDraft` |
| IF-PACS-REP-03 | `submitReport` | `POST /api/app/pacsReportCompose/submitReport` |
| IF-PACS-REP-04 | `applyReportTemplate` | `POST /api/app/pacsReportCompose/applyReportTemplate` |
| IF-PACS-REP-05 | `savePdf` | `POST /api/app/pacsReportCompose/savePdf` |

### 3.2 IF-PACS-REP-01 `openCompose`

**Request**：`workItemId` 必填。  
**Response**：reportId、workItemId、accessionNumber、patientId、patientName、statusEnum、findings、conclusion、pdfStorageKey、submittedAt、workItemStatusEnum、**imaging**（可选）。

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Gate | workItemId 非空；操作人 |
| 2 | Cmd | `openOrCreate`（幂等） |
| 3 | Pre | `PacsWorkItemQueryService#getDetail` |
| 4 | Pre | 姓名 |
| 5 | Asm | Mapper |
| 6 | Asm | `ReportImagingAttachmentAssembler#assemble`（Port 缺失则空附件；不得失败） |

### 3.3 IF-PACS-REP-02 `saveDraft`

**Request**：reportId、findings、conclusion。  
| 1 Gate 操作人 | 2 Cmd saveDraft | 3 Asm |

### 3.4 IF-PACS-REP-03 `submitReport`

**Request**：reportId。  
| 1 Gate 操作人 | 2 Cmd submit | 3 Asm |

### 3.5 IF-PACS-REP-04 `applyReportTemplate`

**Request**：reportId、templateId。  
| 1 Gate 操作人 | 2 Pre `PacsReportTemplateDomainService#requireEnabled` | 3 Cmd 覆盖 findings/conclusion | 4 Asm |

### 3.6 IF-PACS-REP-05 `savePdf`

**Request**：reportId、pdfStorageKey（必填）。  
| 1 Gate | 2 Cmd bindPdf | 3 Asm |

---

## 4. 失败码映射

| 业务场景 | ErrorCode |
|----------|-----------|
| 参数非法 | `PACS_REPORT_INVALID_ARGUMENT` |
| 报告不存在 | `PACS_REPORT_NOT_FOUND` |
| 工单未完成 | `PACS_REPORT_WORKITEM_NOT_COMPLETED` |
| 已 final | `PACS_REPORT_ALREADY_FINAL` |
| 待审中 | `PACS_REPORT_PENDING_AUDIT` |
| 模板不存在/已删 | `PACS_REPORT_TEMPLATE_NOT_FOUND` |
| 模板已停用 | `PACS_REPORT_TEMPLATE_DISABLED` |

---

## 5. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-20 | D-6；解锁成码 27～30 |
| v1.1 | 2026-09-22 | 序号 56：openCompose 装配 `imaging`；SPI `PacsReportImagingAttachmentPort` |
| v1.2 | 2026-09-22 | 序号 43：IF-PACS-REP-04 套用模板 |
