# module-einv-boss-bridge

博思票据 health 集成层：ExtensionImpl、ContextLoader、出站 support（实现 domain access 端口）。

## 职责边界

| 允许 | 禁止 |
|------|------|
| `BossElectronicInvoiceExtensionImpl` | 依赖 application |
| 实现 domain `loader/*`、`access/*` 端口 | 直接调 UI AppService |
| HIS 快照 enrichment（就诊、支付、收费项） | `health-module-support` 厚编排 |
| `{cap}/support/` 出站编排（implements domain access） | 复制 gateway 业务 Result |

## 包结构

```text
com.zxplat.plugin.einvoice.boss.bridge.integration.einvoice/
  BossElectronicInvoiceExtensionImpl

com.zxplat.plugin.einvoice.boss.bridge/
  issue/context/        开具快照 enrichment
  issue/invoker/        EinvIssueInvoker
  issue/support/        BossIssueOutboundSupport、BossIssueDocumentResultSupport、BossPayloadMetadataSupport
  writeoff/context/     EinvoiceInvoiceWritebackLoaderImpl
  writeoff/invoker/     EinvWriteOffInvoker
  writeoff/support/     BossWriteOffOutboundSupport
  recover/invoker/      EinvRecoverInvoker
  recover/support/      BossRecoverOutboundSupport
  reconcile/invoker/    EinvReconcileInvoker
  reconcile/support/    BossReconcileService
  list/invoker/         EinvAccountStatusInvoker
  list/support/         BossAccountQueryService
  config/               BridgeAutoConfiguration
  module/               ModuleDescriptor
```

**出站**：`{cap}/invoker/` 注入 `EinvoiceBossGatewayClient` + `BossResponseParser`（gateway.scheme）；`{cap}/support/` 实现 domain access 供 application 注入。

**架构测试**：`EinvoiceBossBridgeArchitectureTest` 禁止 main 源码 import `module-einv-boss-application.*`（与 enforcer 互补）。

### Loader / access 对照

| domain 端口 | bridge 实现 |
|-------------|-------------|
| `issue.loader.EinvoiceIssueSnapshotContextLoader` | `issue.context.EinvoiceIssueSnapshotContextLoaderImpl` |
| `writeoff.loader.EinvoiceInvoiceWritebackLoader` | `writeoff.context.EinvoiceInvoiceWritebackLoaderImpl` |
| `issue.access.BossIssueOutboundAccess` | `issue.support.BossIssueOutboundSupport` |
| `issue.access.BossIssueDocumentResultAccess` | `issue.support.BossIssueDocumentResultSupport` |
| `issue.access.BossPayloadMetadataAccess` | `issue.support.BossPayloadMetadataSupport` |
| `writeoff.access.BossWriteOffOutboundAccess` | `writeoff.support.BossWriteOffOutboundSupport` |
| `recover.access.BossRecoverOutboundAccess` | `recover.support.BossRecoverOutboundSupport` |
| `reconcile.access.BossReconcileQueryAccess` | `reconcile.support.BossReconcileService` |
| `list.access.BossAccountStatusQueryAccess` | `list.support.BossAccountQueryService` |

## 脚本索引

| 阶段 | 脚本 |
|------|------|
| P9 | `scripts/boss-p9-bridge.ps1` | ✅ |
| P10-9 | `scripts/boss-p10-invoker-bridge.ps1` | ✅ |
| P14 | `scripts/boss-p14-bridge-support.ps1` | ✅ |
| P15 | `scripts/boss-p15-cap-model.ps1` | ✅ |
| P16 | `scripts/boss-p16-access.ps1` | ✅ |

## 与 CHS 差异

| 维度 | CHS bridge | Boss bridge |
|------|------------|-------------|
| Invoker | infno 编号 | **serviceId** 驱动 |
| support 层 | 较少 | `{cap}/support/` 实现 domain access |

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md)。
