# unionpay-bpc-bridge

银联 BPC HIS 侧集成：`ThirdPartyPaymentExtensionImpl`、Settle Orchestrator、HTTPS Invoker 占位。

## 边界

| 允许 | 禁止 |
|------|------|
| `ThirdPartyPaymentExtensionImpl` | 依赖 `unionpay-bpc-application` |
| `{cap}/invoker/` 注入 `BpcGatewayClient` | 依赖 `health-module-support`（Enforcer 已禁） |
| `settle/workflow/` 多步编排 | gateway 业务编排在 config |

## 包结构

```text
com.openhis.health.integration.payment/   ThirdPartyPaymentExtensionImpl

com.zxplat.plugin.unionpay.bpc.bridge/
  settle/workflow/      UnionPaySettleOrchestrator
  scan/invoker/         BpcScanInvoker
  query/invoker/        BpcQueryInvoker（实现 BpcHttpsQueryAccess）
  config/
```

## 脚本

| 脚本 | 阶段 |
|------|------|
| `scripts/bpc-p5-bridge-cap.ps1` | P5 orchestration → workflow |
| `scripts/bpc-p9-bridge-access.ps1` | P9 domain access 端口 |
| `scripts/bpc-p10-invoker-wire.ps1` | P10-9 Invoker 接线 gateway |

## 与 CHS 差异

| 维度 | CHS bridge | BPC bridge |
|------|------------|------------|
| P0 出站 | 医保 HTTP | **OpenHIS.Win** 被扫 |
| HTTPS | 全量 | `BpcQueryInvoker` **占位** |
