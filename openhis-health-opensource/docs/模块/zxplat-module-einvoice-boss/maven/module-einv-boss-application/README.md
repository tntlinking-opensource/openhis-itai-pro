# module-einv-boss-application

博思票据 UI 应用层：AppService、异步开具 Worker、Flyway。

## 职责边界

| 允许 | 禁止 |
|------|------|
| `*AppService` HTTP 入口 | 依赖 health-application |
| `request/` `response/` 对外契约 | 包名 `dto`（新代码禁止） |
| `{cap}/invoker/` 注入 GatewayClient（P7–P8 ✅，已迁 bridge P10-9） | bridge 反向依赖 |
| `internal/issue/*` Worker / Scheduler | 在 AppService 散落 Gateway 注入 |

## 包结构

```text
finance/einvoice/{feature}/
  service/          *AppService（主页面 = 一个类）
  request/ response/
  support/          多步编排（如 BossReconcileService）

internal/issue/       异步开具 Worker、Scheduler、任务队列
  support/            BossIssue/WriteOff/Recover OutboundSupport、DocumentResultSupport
issue/workflow/       EinvoiceIssueOrchestratorImpl、异步冲红桥
internal/document/  文档初始化
config/             AutoConfiguration、Flyway
```

出站 Invoker 已迁入 `module-einv-boss-bridge`（`bridge.{issue|writeoff|recover}.invoker`）；报文组装/解析在 `module-einv-boss-gateway.scheme`。

**架构测试**：`EinvoiceBossApplicationArchitectureTest` 禁止 main 源码 import `gateway.client|local|remote.*`。

## 命名·脚本

| 类型 | 包 | 示例 |
|------|-----|------|
| HTTP 入口 | `{feature}/service/` | `EinvoiceBossDocumentAppService` |
| 对外契约 | `request/` `response/` | 禁止 `dto` 包名 |
| 多步编排 | `support/` 或 `issue/workflow/` | `EinvoiceIssueOrchestratorImpl` |
| domain access | 注入 bridge support | 禁止直接 Gateway 注入 |

| 页面 | AppService | 说明 |
|------|------------|------|
| 票据文档 | `EinvoiceBossDocumentAppService` | 详情、调额 P1 |
| 票据列表 | `EinvoiceBossListAppService` | 分页、入账状态 |
| 待补偿 | `EinvoiceBossCompensationAppService` | 重新入队/立即开 |
| 对账 | `EinvoiceBossReconcileAppService` | 日总笔数核对 |
| 收银台 | `EinvoiceBossCashierAppService` | QR/进度 |
| 通用票 | `EinvoiceBossCommonAppService` | COM 开具 P3 |
| 收费对照 | `EinvoiceBossContrastAppService` | internalCode 映射 P2 |

| 阶段 | 脚本 | 状态 |
|------|------|------|
| P1–P3 | `scripts/boss-p1-app-rename.ps1` | ✅ |
| P7–P8 | `scripts/boss-p7-p8-app.ps1` | ✅ |
| P10-9 | `scripts/boss-p10-invoker-bridge.ps1` | ✅ |
| P11 | `scripts/boss-p11-workflow.ps1` | ✅ |

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md).

## 与 CHS 差异

| 维度 | CHS application | Boss application |
|------|-----------------|------------------|
| 出站键 | infno | **serviceId** |
| 异步 Worker | 较少 | `internal/issue/*` 开具队列 |
| Invoker 位置 | 部分在 application | 已迁 **bridge** `{cap}/invoker/` |
| 表前缀 | `yb_*` | `einv_boss_*` |
