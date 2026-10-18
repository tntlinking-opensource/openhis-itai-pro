# 检查域 · 报告书写 · 应用服务设计（AS）

> **编码**：AS-PACS-REP-001 · **版本**：v1.2 · **日期**：2026-09-22  
> **菜单**：报告书写 · **类名**：`PacsReportComposeAppService` · **路由**：`POST /api/app/pacsReportCompose/{method}`  
> **领域**：[DM-PACS-002](./DM-PACS-002-PacsDiagnosticReport-检查报告.md)

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 进入编辑、保存草稿、提交待审、绑定 PDF 存储键；进入编辑时可选装配关键图预览与阅片深链 |
| **在外** | 审核 REP-002；DR index / CLN 投影；像素/MinIO/Orthanc（imaging） |
| **涉及域** | PacsDiagnosticReport、PacsWorkItem、Patient（读侧姓名）；影像附件经 SPI `PacsReportImagingAttachmentPort` |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **报告医生** | 编辑所见/结论、提交审核、登记 PDF 键 |
| **系统** | 草稿 `preliminary`；提交置 `submittedAt` |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **草稿** | `preliminary` 且 `submittedAt` 空 |
| **待审** | `preliminary` 且 `submittedAt` 非空 |
| **pdfStorageKey** | 对象存储键；本 IF 不上传字节 |

### 1.4 范围

| P0 | REP-01～03、REP-05 |
| P1 | **REP-04 模板套用**（依赖 CFG-002） |

### 1.5 领域清单

| 聚合 | DM |
|------|-----|
| PacsDiagnosticReport | [DM-PACS-002](./DM-PACS-002-PacsDiagnosticReport-检查报告.md) |
| PacsWorkItem | [DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md) |

### 1.6 BR

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-REP-01** | 草稿 `preliminary`；提交 → 待审 | REP-02/03 |
| **BR-REP-02** | 关联 WorkItem 须 `completed` | REP-01 |
| **BR-REP-03** | 1 WorkItem 对应 1 主报告 | REP-01/02 |
| **BR-REP-04** | PDF 存对象存储；表存 `pdfStorageKey` | REP-05 |
| **BR-REP-05** | imaging 未启用或无 Study/端点时，`openCompose.imaging` 降级为空，不得阻断开写 | REP-01 |
| **BR-REP-06** | 套用模板仅覆盖 findings/conclusion；模板须启用且未软删 | REP-04 |

---

## 2. 接口总览

| IF | 动作 | 语义 | 读写 | 关键规则 |
|----|------|------|:--:|----------|
| **IF-PACS-REP-01** | 进入编辑 | openOrCreate + 装配 | 写（幂等建）/读 | BR-REP-02/03 |
| **IF-PACS-REP-02** | 保存草稿 | 写正文 | 写 | BR-REP-01/03 |
| **IF-PACS-REP-03** | 提交 | 进待审 | 写 | BR-REP-01 |
| **IF-PACS-REP-04** | 应用报告模板 | 套用所见/结论 | 写 | BR-REP-06 |
| **IF-PACS-REP-05** | 保存 PDF 键 | 绑定 storageKey | 写 | BR-REP-04 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（对象上传组件属基础设施；本 AS 只收键）。

---

## 4. 编排说明书

### 4.1 IF-PACS-REP-01

`openOrCreate` → JOIN Patient 姓名 → 可选 `PacsReportImagingAttachmentPort#loadAttachment` → 详情 Response（`imaging`：关键图预览 URL + 阅片深链）。

**imaging 字段**：`hasMedia` / `hasStudy` / `studyInstanceUid` / `viewerAvailable` / `viewerLaunchUrl` / `viewerExpiresAt` / `keyImages[]`。

### 4.2 IF-PACS-REP-02

Gate 操作人 → `saveDraft`。

### 4.3 IF-PACS-REP-03

Gate 操作人 → `submit` → `PacsReportSubmittedEvent`。

### 4.4 IF-PACS-REP-04

Gate 操作人 → 加载启用模板 → 仅覆盖当前草稿 `findings`/`conclusion` → 落库（不改 status/PDF）。

### 4.5 IF-PACS-REP-05

Gate → `bindPdf(pdfStorageKey)`。

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `PACS_REPORT_WORKITEM_NOT_COMPLETED` | 未检查完成不可写报告 |
| `PACS_REPORT_ALREADY_FINAL` | 已审核不可改草稿 |
| `PACS_REPORT_PENDING_AUDIT` | 待审中不可改正文 |
| `PACS_REPORT_INVALID_ARGUMENT` | 参数非法 |
| `PACS_REPORT_TEMPLATE_NOT_FOUND` | 模板不存在或已删 |
| `PACS_REPORT_TEMPLATE_DISABLED` | 模板已停用不可套用 |

---

## 6. 事件

`PacsReportSubmittedEvent` → REP-002 待审列表。

---

## 7. NFR

草稿 autosave（P1）；PDF 上传失败可重试（重调 REP-05）。

---

## 8. 测试

- 未完成检查 → 不可 REP-01
- 提交后审核台可见（submittedAt）
- final 后不可 saveDraft
- imaging 启用：openCompose 含关键图 previewUrl 与 viewerLaunchUrl（含 lng=zh）
- imaging 未启用 / 无端点：openCompose 仍成功，imaging 为空或 viewerAvailable=false

---

## 9. 关联文档

- SDD：[AS-PACS-REP-001-SDD](../AppService-SDD/AS-PACS-REP-001-报告书写-SDD.md)
- 审核：[AS-PACS-REP-002](./AS-PACS-REP-002-报告审核.md)
- EXE：[AS-PACS-EXE-001](./AS-PACS-EXE-001-检查执行.md)
- 影像：[AS-PACS-IM-001](../../../模块/zxplat-module-pacs-imaging/AppService/AS-PACS-IM-001-影像采集.md) / [AS-PACS-IM-002](../../../模块/zxplat-module-pacs-imaging/AppService/AS-PACS-IM-002-影像阅片.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版 P0 REP 书写 |
| v1.1 | 2026-09-20 | 对齐 DM-PACS-002；HTTP POST 口径；PDF=存储键 |
| v1.2 | 2026-09-22 | 序号 56：openCompose 装配关键图/阅片 SPI；BR-REP-05 |
| v1.3 | 2026-09-22 | 序号 43：IF-PACS-REP-04 套用模板；BR-REP-06 |
