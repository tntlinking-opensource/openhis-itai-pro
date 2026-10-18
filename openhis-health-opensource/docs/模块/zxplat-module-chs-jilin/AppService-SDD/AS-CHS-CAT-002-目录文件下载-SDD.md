# 目录文件下载 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-CAT-002-SDD** · v0.1 · 2026-07-11  
> **应用服务编号**：AS-CHS-CAT-002

## 1. 边界

| 项 | 内容 |
| --- | --- |
| AppService | `InsuranceCatalogDownloadAppService` |
| HTTP | `/api/app/insuranceCatalogDownload` |
| infno | 1301/1305 等 + 9102 |

## 2. 包结构

```text
catalog/download/
  service/InsuranceCatalogDownloadAppService.java
  request/  response/  mapper/
  invoker/Cat13xxInvoker.java, Cat9102Invoker.java
  support/
    CatalogDownloadWorkflow.java
    dto/   # Phase1 内部上下文（CatalogDownloadPhase1Context 等）
```

## 3. HTTP ↔ IF

| 方法 | IF | 编排 |
| --- | --- | --- |
| `previewCatalogDownload` | IF-CHS-CAT-002-R-01 | `CatalogDownloadWorkflow.preview` |
| `startCatalogDownload` | IF-CHS-CAT-002-W-01 | `CatalogDownloadWorkflow.start`（异步入库） |
| `getCatalogDownloadJobProgress` | IF-CHS-CAT-002-R-02 | domain job progress |
| `listDownloadCursors` | IF-CHS-CAT-002-R-03 | `YbCatalogDownloadCursorQueryService` |

## 4. 说明

- 9102 下载由 `Cat9102Invoker` 封装；对账 3202 链复用同一 Invoker。
- 阶段 1 ZIP 拉取在 `Cat13xxInvoker`；入库在 support Workflow + 异步任务。
- 入库成功后可选触发 `NoticeComplianceWorkflow.runAfterImport`（**AS-CHS-CAT-005-SDD**）；校验失败不回滚本批下载。
