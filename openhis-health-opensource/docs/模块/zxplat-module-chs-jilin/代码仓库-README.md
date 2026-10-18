# zxplat-module-chs-jilin

吉林省医保（CHS）扩展 Module，对齐 `docs/架构/模块化开发规范.md`。

> **v3 真源**：`zxplat/zxplat-module-chs-jilin` 已下线；国标医保与工伤分别由 `zxplat-module-chs-jilin`、`zxplat-module-work-injury` 承载。

---

## 核心原则

| 原则 | 说明 |
|------|------|
| **可选扩展** | 未开通医保的医院不装 starter（`zxplat.modules.chs.enabled=false`） |
| **业务跟 HIS 走** | domain / application / bridge **永远**与 health 同进程、共库 |
| **Gateway-Only Remote** | 仅当 HIS 机器访问不了医保专网时，**出站 HTTP** 走 gateway-proxy |
| **无 contracts** | Extension 链用 `chs.domain.*.loader` + domain Command/Result，不用 `*-contracts` Facade |
| **bridge 分层** | `context/` + `invoker/` + `workflow/*Orchestrator`；**无** `bridge/port/`、`orchestration/` |
| **application 分层** | 页内 `service/invoker/support`；**无**强制 Orchestrator、**无** `orchestration/` |
| **Invoker** | 单 infno 出站命名 `{Op\|Ip}<InfNo>Invoker`（例 `Op2601Invoker`）；无 Port 接口禁止 `*PortImpl` |
| **不含工伤** | 工伤（`workinjury/`、`Wi*`）**不迁入**本 Module；后续独立扩展包 |

### 物理部署

```text
┌──────────────────── HIS 网段 ────────────────────┐
│  health-app                                       │
│    + module-chs-jilin-bridge      ExtensionImpl          │
│    + module-chs-jilin-application 医保 UI AppService     │
│    + module-chs-jilin-domain       yb_* 表               │
│    + module-chs-jilin-gateway      client（Local/Remote）│
└───────────────────────┬──────────────────────────┘
                        │ 仅 gateway.mode=remote 时
                        ▼
┌──────────────────── 医保网段 ────────────────────┐
│  module-chs-jilin-host（gateway-proxy exec jar）         │
│    POST /chs/gateway/* → ChsHttpTransport         │
└───────────────────────┬──────────────────────────┘
                        ▼
                 吉林省医保平台 HTTP
```

---

## 六层模块

| 层 | Maven 模块 | 职责 |
|----|------------|------|
| **domain** | `module-chs-jilin-domain` | `yb_*` 实体、仓储、DomainService、Command/Result |
| **gateway** | `module-chs-jilin-gateway` | `InsuranceGatewayClient`、`scheme/`、`observability/` 审计、HTTP 传输 |
| **bridge** | `module-chs-jilin-bridge` | `*ExtensionImpl`、`*Invoker`、`*ContextLoader`、Mapper；读写 **health-domain** / **chs-domain** |
| **application** | `module-chs-jilin-application` | 医保页面 `Insurance*AppService`、Flyway(chs)、config |
| **starter** | `module-chs-jilin-starter` | HIS embedded 安装入口 |
| **host** | `module-chs-jilin-host` | gateway-proxy 独立进程 + HIS 侧 Remote Client |

### 命名契约（Maven 目录 = artifactId）

| Maven 模块 | Java 根包 | 说明 |
|-----------|----------|------|
| `module-chs-jilin-domain` | `com.zxplat.plugin.chs.domain` | `jilin` 由 Module 边界表达，不进包名 |
| `module-chs-jilin-gateway` | `com.zxplat.plugin.chs.gateway` | HTTP 出站 |
| `module-chs-jilin-bridge` | `com.zxplat.plugin.chs.bridge` + `com.zxplat.plugin.chs.bridge.integration.insurance` | 编排 + SPI |
| `module-chs-jilin-application` | `com.zxplat.plugin.chs.application` | 医保 UI AppService |
| `module-chs-jilin-starter` | `com.zxplat.plugin.chs.starter` | 装配入口 |
| `module-chs-jilin-host` | `com.zxplat.plugin.chs.host` | gateway-proxy |

### 依赖方向

```text
domain ← gateway ← bridge          # gateway POM 可依赖 domain（审计落库）
domain ← application

bridge  ──✓── gateway
bridge  ──✓── health-domain（*QueryService / *DomainService）
bridge  ──✗── application / health-module-support

starter → bridge + application + gateway + domain
host    → gateway（proxy / remote client；不含 application / bridge）
```

---

## bridge 读写 health-domain

| 操作 | API | 落点 |
|------|-----|------|
| **读 HIS** | `health-domain.*QueryService`、`queries/model` | bridge `context/*ContextLoader` 内组装 Command |
| **写 HIS** | `health-domain.*DomainService` | 同上 |
| **读/写 yb_*** | `chs-domain.*QueryService` / `*DomainService` | Invoker / ExtensionImpl / ContextLoader |
| **调省平台** | `InsuranceGatewayClient` | gateway |

**硬约束**

- **禁止** `health-module-support`、`health-domain.*Repository`、bridge 内自写 SQL。
- **禁止** `internal/*Support`、`*FacadeImpl` 式厚编排层；逻辑在 ExtensionImpl 或 Port 实现中，通过 Domain 服务组合完成。
- **能力不够时**：在 **health-domain** 扩展 `*QueryService` / `*DomainService`（及必要 `queries/model`），并 **同步更新** `docs/领域/DomainModel/DM-*.md` 与对应 SDD；**不在 bridge 里绕过 Domain 层补洞**。

---

## 两条业务链

### Extension 链（收银 / 挂号 / 进销存）

```text
health-application
  → *Extension（health-infrastructure/integration）
  → bridge *ExtensionImpl（+ Mapper）
  → {Op|Ip}<InfNo>Invoker（单 infno 时，例 Op2601Invoker）
  → health-domain *QueryService / *DomainService   ← 读/写 HIS
  → chs-domain *QueryService / *DomainService      ← 读/写 yb_*
  → InsuranceGatewayClient
       local：InsuranceGatewayClientLocalImpl → 医保平台
       remote：InsuranceGatewayClientRemoteImpl → gateway-proxy → 医保平台
```

health **只依赖 Extension**；ExtensionImpl 与 Port 实现始终在 HIS 进程。

### UI 链（目录 / 字典 / 进销存页）

```text
前端
  → application Insurance*AppService（/api/app/insurance*/*）
  → 编排逻辑（AppService 内，或过渡态 internal/*Operations）
  → chs-domain / health-domain DomainService
  → InsuranceGatewayClient（同上，无 application 层 remote）
```

---

## 配置

| 开关 | 含义 |
|------|------|
| `zxplat.modules.chs.enabled=true` | 装配 CHS 扩展（starter） |
| `zxplat.chs.gateway.mode=local` | HIS 直连医保平台（默认） |
| `zxplat.chs.gateway.mode=remote` | 出站 HTTP 经 gateway-proxy |
| `zxplat.chs.gateway.remote.base-url` | proxy 地址，如 `http://chs-proxy:8080` |
| `zxplat.chs.gateway.remote.api-key` | proxy 鉴权（可选） |
| `zxplat.chs.gateway.proxy.standalone=true` | gateway-proxy 独立进程 |

### 装配矩阵

| 场景 | 依赖 | 配置 |
|------|------|------|
| 无医保 | 不引 starter | `zxplat.modules.chs.enabled=false` |
| 有医保，HIS 可直连医保网 | `module-chs-jilin-starter` | `enabled=true`，`gateway.mode=local` |
| 有医保，HIS 不可直连医保网 | `module-chs-jilin-starter-remote` | `enabled=true`，`gateway.mode=remote`；医保网另启 proxy |

---

## 包结构（当前已落地 + 骨架）

```text
module-chs-jilin-domain/
  com.zxplat.plugin.chs.domain/
    settlement/command/          OnPaymentSettledCommand、OnPaymentSettledYbSettlementLine
    settlement/model/            PlatformSetlinfoFields 等值对象
    settlement/constants/        ChsSettlementInfNo
    invoke/entity|service/     YbInvokeLog、YbInvokeLogDomainService、YbInvokeLogQueryService

module-chs-jilin-gateway/
  com.zxplat.plugin.chs.gateway.client/       InsuranceGatewayClient、InvokeContext
    client.remote/                      ChsGatewayRemoteInvokeRequest（host 共用）
  com.zxplat.plugin.chs.gateway.scheme.{cap}/ model | assembler | parser
  com.zxplat.plugin.chs.gateway.observability/ 审计装饰器
  com.zxplat.plugin.chs.gateway.catalog.access/ 契约 properties 读取
  com.zxplat.plugin.chs.gateway.local/       InsuranceGatewayClientLocalImpl

module-chs-jilin-bridge/
  com.zxplat.plugin.chs.bridge.settlement/   Op2601Invoker、InsurancePaymentSettlementIntegrationMapper
  com.zxplat.plugin.chs.bridge.integration.insurance/  InsurancePaymentSettlementExtensionImpl

module-chs-jilin-host/
  com.zxplat.plugin.chs.host.proxy/     ChsGatewayProxyController、ChsGatewayProxyApplication
  com.zxplat.plugin.chs.host.remote/    InsuranceGatewayClientRemoteImpl

module-chs-jilin-application/
  com.zxplat.plugin.chs.application/
    catalog/   nationalcatalog | download | mapping | workbench（service/invoker/support）
    reconciliation/  request/ + response/
    foundation/  person | sign | session | platformdict
    medicalinfo/ AppService 直调 Invoker
    inventory/
module-chs-jilin-starter/       module-chs-jilin-starter（HIS embedded）
```

---

## 明确不做

- **整包 remote**：不对 application / bridge / domain 做 HTTP 代理（无 `*Access` remote、无 Facade RemoteImpl）
- **contracts 层**：不引入 `chs-jilin-contracts`
- **internal/*Support**：不用 Support/FacadeImpl 厚层；编排经 ExtensionImpl + Domain 服务
- **bridge 依赖 health-module-support**：HIS 读写只经 health-domain 公开服务
- **工伤代码**：不迁入本 Module（`workinjury/`、`Wi*`、工伤 Facade）；后续独立扩展包

---

## 结构优化（P1–P10 ✅）

| 阶段 | 层 | 内容 |
|------|-----|------|
| P1–P6 | application | dto→request/response；foundation 聚合；文档/codegen |
| P7–P8 | application | 去强制 Orchestrator；去掉 `orchestration/` |
| P9 | bridge | 能力切片；domain access/loader 端口 |
| P10 | gateway/host | 分包清理；scheme cap 对齐；model/assembler/parser |
| Doc | domain/bridge | README + JavaDoc 约定 |

**推广手册**：[`OPTIMIZATION-PLAYBOOK.md`](./OPTIMIZATION-PLAYBOOK.md)（可复制到其他 Module）

**推广手册**：[`OPTIMIZATION-PLAYBOOK.md`](./OPTIMIZATION-PLAYBOOK.md) · 模板 [`docs/_templates/OPTIMIZATION-PLAYBOOK-template.md`](../../.cursor/templates/OPTIMIZATION-PLAYBOOK-template.md)

脚本索引：`scripts/p1-p3-application-migrate.ps1` … `p10d-scheme-subfolder.ps1` · `fix-bom.ps1`

---

## 迁移路线（业务能力）

| 阶段 | 状态 | 内容 |
|------|------|------|
| domain 结算 + invoke | ✅ | Command/Line、QueryService/DomainService |
| gateway scheme + 审计 | ✅ | scheme/{cap}、PersistingInsuranceGatewayClient |
| bridge 2601 + Extension 链 | ✅ 部分 | Op2601；其它 infno 持续补全 |
| host remote/proxy | ✅ | RemoteImpl + ChsGatewayProxyController |
| application UI | ✅ 骨架 | catalog/recon/dict/sign/inventory |
| 工伤 | ✅ 独立 | `zxplat-module-work-injury` |

health-app 依赖 `module-chs-jilin-starter`（profile `with-chs`）。

文档真源：`docs/架构/模块化开发规范.md` · CHS 增量：`docs/模块/zxplat-module-chs-jilin/17-zxplat-module-chs-jilin-架构-v3.md`
