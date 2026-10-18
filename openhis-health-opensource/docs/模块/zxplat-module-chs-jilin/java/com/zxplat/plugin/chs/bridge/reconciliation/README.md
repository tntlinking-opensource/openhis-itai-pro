# reconciliation bridge

医保对账/清算（3201、3202、3203A–3209A、9101）。

## 包结构

```text
reconciliation/
  support/   ReconPlatformSupport、ReconDetailFileSupport
  invoker/   Recon3201、3203A–3209A、3202、9101Invoker
  workflow/  Recon3202Workflow（9101→3202→9102 编排）
```

## infno 索引

| infno | Invoker | 说明 |
|-------|---------|------|
| 3201 | `Recon3201Invoker` | 日对账 + saveReconDaily |
| 3202 | `Recon3202Invoker` | 明细对账提交（workflow 内部） |
| 3203A | `Recon3203AInvoker` | 吉林清算申请 |
| 3204A–3209A | `Recon3204A`…`Recon3209AInvoker` | 清算相关 |
| 9101 | `Recon9101Invoker` | 明细 ZIP 上传（workflow 内部） |
| 9102 | `Cat9102FileDownloadAccess` | 对账结果下载（catalog access 共享） |

## 调用链

```text
InsuranceReconciliationAppService (application)
  → Recon3201Invoker … Recon3209AInvoker、Recon3202DetailInvoker (domain access)
  → Recon3201Invoker … Recon3209AInvoker、Recon3202Workflow (bridge)

Recon3202Workflow
  → Recon9101Invoker → Recon3202Invoker → Cat9102FileDownloadAccess
```

脚本：`scripts/chs-p12-reconciliation-invoker.ps1`
