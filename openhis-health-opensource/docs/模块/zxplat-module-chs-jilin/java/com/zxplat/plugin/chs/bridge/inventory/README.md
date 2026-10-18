# inventory bridge

进销存出站：Extension 第三方上传（3501–3506）+ UI 运维台编排（3501–3513）。

## 3502 与其它 infno 搭配（吉林省落地）

> 真源：[09-医保进销存-详细设计.md](../../../09-医保进销存-详细设计.md) §2.6

| 业务 | infno | 调完是否再调 3502 |
|------|-------|-------------------|
| 首次批初始化 | 3501 + 3502(106) | **3501 每条必须跟 3502** |
| 进货同意 | 3503 + 3502(108) | **3503 成功后跟 3502**（正数 cnt） |
| 退供应商 | 3504 + 3502(109) | **3504 成功后跟 3502**（负数 cnt；批次=退供单号） |
| 发药 | 3502(107) → 3505 | **先试出库再销售**；3502 失败仍继续 3505 |
| 退药 | 3506 → 3502(106) | **先退销售再入库**；伴生批次=退药单号 |
| 日常盘点盈亏 | 3502(103/104) | —（**不调 3501**） |
| 损益 / 科室领退领 | 3502 | — |
| 院内调拨 | 不报 | — |

实现：`InventoryThirdPartyUploadExtensionImpl` → `*UploadContextLoader`（含 `tracePolicy`/`drugTracCodgs`）→ `InventoryUploadTraceLineSupport` → `InventoryThirdPartyUploadOrchestrator` → `Inv350xInvoker`。

**追溯码**：不再单独暴露 `DispenseTraceGateExtension`；应码未扫、码数不一致等校验在 Orchestrator 出站前完成（配置 `block-settle-without-trace` / `enforce-trace-count-equals-qty`）。

## 包结构（P14 ✅）

```text
inventory/
  context/     *UploadContextLoader（HIS → InvUpload 命令，Extension 专用；含 InventoryDeptIssueUploadContextLoader）
  assembler/   ChsTraceCodeExtractAssembler、InventoryPlatformFieldAssembler 等
  invoker/     Inv3501–3507、Inv35xxQuery、Inv35xxRetry（单 Bean，无 application 重复）
  workflow/    InventoryThirdPartyUploadOrchestrator、InventoryUpload/Retry/Compare/Initialize*Workflow
  support/     InvPlatformSupport、InventoryInitializeUploadContextLoader、InventoryUploadTraceLineSupport
```

## infno 索引

| infno | Invoker | Workflow |
|-------|---------|----------|
| 3501–3506 | `Inv3501`…`Inv3506Invoker` | `InventoryThirdPartyUploadOrchestrator`（Extension）/ `InventoryUploadWorkflow`（UI） |
| 3507 | `Inv3507Invoker` | 商品信息删除 |
| 3508–3513 | `Inv35xxQueryInvoker` | `InventoryCompareWorkflow`（平台侧查询） |
| 3501–3506（重试） | `Inv35xxRetryInvoker` | `InventoryRetryWorkflow`（重放 requestJson） |
| 3501+3502（初始化） | 复用 3501/3502 | `InventoryInitializeWorkflow` |

## 调用链

```text
InventoryThirdPartyUploadExtensionImpl
  → *UploadContextLoader (context)
  → InventoryThirdPartyUploadOrchestrator (workflow)
      → Inv3501–3506Invoker（按业务类型路由）

InsuranceInventoryAppService（application）
  → domain inventory.access 端口
      → bridge workflow / invoker（本包实现）
```
