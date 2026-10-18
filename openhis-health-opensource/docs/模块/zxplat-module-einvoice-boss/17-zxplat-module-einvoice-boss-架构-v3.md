# zxplat-module-einvoice-boss 架构 v3 — Gateway + Bridge

> **文档性质**：博思电子票据重架构决策  
> **日期**：2026-07-11  
> **代码**：`zxplat-plugin/zxplat-module-einvoice-boss/`

---

## 1. 与 v2 的差异

| 项 | v2 | v3 |
|----|----|-----|
| 父工程 | `zxplat-module-einvoice-boss` | **`zxplat-module-einvoice-boss`** |
| 集成层 | `einvoice-boss-bridge` | **`einvoice-boss-bridge`** |
| 契约 | `einvoice-boss-contracts` | **无**；契约并入 `einvoice-boss-domain` |
| 出站 HTTP | `BossGatewayClient` in application | **`einvoice-boss-gateway`** |
| 业务 Client | `BossElectronicInvoiceClient` | **`EinvoiceIssueOrchestrator` + Invokers** |
| HTTP 中转 | `einvoice-boss-host` | **`einvoice-boss-host`** |

---

## 2. 六层模块

| 层 | 模块 | 职责 |
|----|------|------|
| domain | `einvoice-boss-domain` | `einv_*` 表、DTO、Loader 端口 |
| gateway | `einvoice-boss-gateway` | `EinvoiceBossGatewayClient`、签名、RestTemplate |
| bridge | `einvoice-boss-bridge` | ExtensionImpl、ContextLoader、health 回写 |
| application | `einvoice-boss-application` | Worker、Orchestrator、UI AppService |
| host | `einvoice-boss-host` | 网段隔离时的 HTTP forward |
| starter | `einvoice-boss-starter` | embedded 装配 |

---

## 3. 依赖方向

```text
domain ← gateway ← application
domain ← bridge
bridge  → health-domain（禁止 health-module-support、application）
starter → domain + gateway + bridge + application
host    → domain（仅 forward，无 application）
```

---

## 4. Extension 链

```text
health 支付/退费事件
  → BossElectronicInvoiceExtension
  → BossElectronicInvoiceExtensionImpl
  → EinvoiceIssueOrchestrator
  → EinvoiceBossIssueService / Worker
  → EinvIssueInvoker → EinvoiceBossGatewayClient → 博思平台
  → EinvoiceInvoiceWritebackLoaderImpl → InvoiceDomainService
```

---

## 5. Invoker 命名

| Invoker | serviceId 域 |
|---------|----------------|
| `EinvIssueInvoker` | 开具（按 issueKind 解析） |
| `EinvWriteOffInvoker` | `writeOffEBill` |
| `EinvRecoverInvoker` | `getEBillByBusNo` |

---

## 6. 迁移状态

| 阶段 | 状态 |
|------|------|
| scaffold + contracts→domain | ✅ |
| adapter→bridge + ContextLoader 重命名 | ✅ |
| gateway 从 application 拆出 | ✅ |
| http-starter→host | ✅ |
| Orchestrator + Invokers | ✅ |
| 根 pom / BOM 更新 | ✅ |
| 删除 `zxplat-module-einvoice-boss` | ✅ |
