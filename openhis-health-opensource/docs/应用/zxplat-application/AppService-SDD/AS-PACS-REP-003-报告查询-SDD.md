# AS-PACS-REP-003 报告查询 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-REP-003-SDD · **版本**：v1.0 · **日期**：2026-09-22  
> **对应 AS**：AS-PACS-REP-003-报告查询.md（v1.0）  
> **实现阶段口径**：目标态全量契约（序号 45）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application` · `pacs/report/query` |
| **边界** | 只读列表/历史/详情；不写报告、不投影 |
| **方法权威** | DM-PACS-002 读侧扩展 |
| **实现方式** | `PacsReportQueryAppService`；HTTP 一律 POST |

---

## 2. 代码结构

```
health-application/.../pacs/report/query/
├── service/PacsReportQueryAppService.java
├── request/SearchReportsRequest.java
├── request/SearchPatientReportHistoryRequest.java
├── request/GetReportDetailRequest.java
├── response/SearchReportsResponse.java
├── response/SearchPatientReportHistoryResponse.java
├── response/GetReportDetailResponse.java
├── dto/PacsReportQueryRowDto.java
└── mapper/PacsReportQueryMapper.java
```

| 注入 | 用途 |
|------|------|
| `PacsDiagnosticReportQueryService` | 列表/历史/详情 |
| `PacsWorkItemQueryService` | 检查号（批量优先） |
| `PatientQueryService` | 姓名批量 |
| `PageWindowGate` | 分页 |
| `PacsReportQueryMapper` | DTO |

### 2.2 领域方法白名单

| 方法 | IF |
|------|-----|
| `searchReports(...)` | REP-11 |
| `searchHistoryByPatient(...)` | REP-12 |
| `getById` | REP-13 |

### 2.3 读模型

可复用 `PacsDiagnosticReportDetailQueryModel`；列表行 App 侧 DTO 再加 accessionNumber、patientName、modalityCode（自工单）。

---

## 3. 按接口展开

### 3.1 方法表

| IF | 方法 | HTTP |
|----|------|------|
| IF-PACS-REP-11 | `searchReports` | `POST /api/app/pacsReportQuery/searchReports` |
| IF-PACS-REP-12 | `searchPatientReportHistory` | `POST /api/app/pacsReportQuery/searchPatientReportHistory` |
| IF-PACS-REP-13 | `getReportDetail` | `POST /api/app/pacsReportQuery/getReportDetail` |

### 3.2 IF-PACS-REP-11 `searchReports`

**Request**：pageNo、pageSize、statusEnum（否）、organizationId（否）、patientId（否）、issuedFrom（否）、issuedTo（否）  
**Response**：total、records（id/workItemId/accessionNumber/patientId/patientName/statusEnum/organizationId/submittedAt/issuedAt/pdfStorageKey/modalityCode）

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Gate | PageWindowGate |
| 2 | Pre | QueryService#searchReports |
| 3 | Asm | 批量工单 + 姓名 → Mapper |

### 3.3 IF-PACS-REP-12 `searchPatientReportHistory`

**Request**：pageNo、pageSize、patientId（必填）、excludeReportId（否）  
**Response**：同列表行结构

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Gate | patientId 非空；PageWindowGate |
| 2 | Pre | searchHistoryByPatient |
| 3 | Asm | 同上 |

### 3.4 IF-PACS-REP-13 `getReportDetail`

**Request**：reportId  
**Response**：详情字段 + findings/conclusion + accessionNumber/patientName

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Gate | reportId 非空 |
| 2 | Pre | getById |
| 3 | Asm | 工单 + 姓名 |

---

## 4. QueryService 与领域事件

无写事件。读侧扩展须在 `PacsDiagnosticReportQueryService`：

| 方法 | 过滤 |
|------|------|
| `searchReports` | 未软删；amendedFromId IS NULL；可选 status/org/patient；issuedAt 闭开区间（issuedFrom≤x＜issuedTo+1日或按文档用 ≤issuedTo） |
| `searchHistoryByPatient` | patientId；主报告；可选 excludeReportId；按 issuedAt/submittedAt/updatedAt 倒序 |

---

## 5. 写库顺序

无写库。

---

## 6. NFR

姓名/工单：`findPatientNamesByIds` + 按 workItemId 批量 `getDetail` 或 repository `findAllById`（若已有批量方法优先）。

---

## 7. 测试要点

| 场景 | 优先级 |
|------|:------:|
| status=final 过滤 | P0 |
| 历史缺 patientId 拒绝 | P0 |
| 详情 not found | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-22 | D-13；驱动序号 45 |
