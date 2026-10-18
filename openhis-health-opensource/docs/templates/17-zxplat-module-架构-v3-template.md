# zxplat-module-{name}

{一句话描述}扩展 Module，对齐 [模块化开发规范.md](../架构/模块化开发规范.md) v3。

> **类型**：`网关型` / `领域型`（删去不适用项）  
> **代码**：`zxplat-plugin/zxplat-module-{name}/`  
> **文档**：`docs/模块/zxplat-module-{name}/`  
> **CHS 对照样板**：[`模块/zxplat-module-chs-jilin/说明.md`](../模块/zxplat-module-chs-jilin/说明.md)

---

## 核心原则

| 原则 | 说明 |
|------|------|
| **可选扩展** | 未开通时不装 starter（`zxplat.modules.{mod}.enabled=false`） |
| **业务跟 HIS 走** | domain / application / bridge **永远**与主 HIS 同进程、共库 |
| **Gateway-Only Remote** | （网关型）仅当 HIS 机器访问不了外网/专网时，**出站 HTTP** 走 gateway-proxy |
| **无 contracts** | Extension 链用 bridge **Invoker** / `port/*Port` + domain Command/Result，**不**引入 `{mod}-contracts` / `*Facade` |
| **无 Support 层** | bridge **不**设 `internal/*Support`；编排在 ExtensionImpl / Invoker / Port 实现，**直调** Domain 层服务 |
| **Invoker** | （网关型）单次外平台出站：`{Context}{Op}Invoker` |

### 物理部署（网关型）

```text
┌──────────────────── HIS 网段 ────────────────────┐
│  zxplat-app / 院点 backend                        │
│    + {mod}-bridge       ExtensionImpl / Invoker   │
│    + {mod}-application  管理页 AppService         │
│    + {mod}-domain       本 Module 表              │
│    + {mod}-gateway      client（Local/Remote）    │
└───────────────────────┬──────────────────────────┘
                        │ 仅 gateway.mode=remote 时
                        ▼
┌──────────────────── 外网/专网 ───────────────────┐
│  {mod}-host（gateway-proxy exec jar）             │
│    POST /{mod}/gateway/* → HttpTransport          │
└───────────────────────┬──────────────────────────┘
                        ▼
                 外平台 HTTP/API
```

（领域型删除 gateway/host 行，bridge 与 application 同进程即可。）

---

## 六层模块

| 层 | Maven 模块 | 职责 |
|----|------------|------|
| **domain** | `module-*-domain` | 实体、仓储、`*DomainService`、`*QueryService`、Command/Result |
| **gateway** | `module-*-gateway` | （网关型）`*GatewayClient`、报文 scheme、HTTP 传输、出站审计 |
| **bridge** | `module-*-bridge` | `*ExtensionImpl`、`*Invoker`、`port/*Port`、Mapper |
| **application** | `module-*-application` | 管理页 `*AppService`、Flyway、config |
| **starter** | `module-*-starter` | HIS embedded 安装入口 |
| **host** | `module-*-host` | （可选）gateway-proxy 独立进程 |

### 命名契约

| Maven 模块 | Java 根包（示意） |
|-----------|-------------------|
| `module-*-domain` | `com.zxplat.plugin.{mod}.domain` |
| `module-*-gateway` | `com.zxplat.plugin.{mod}.gateway` |
| `module-*-bridge` | `com.zxplat.plugin.{mod}.bridge` + `…bridge.integration…` |
| `module-*-application` | `com.zxplat.plugin.{mod}.application` |
| `module-*-starter` | `com.zxplat.plugin.{mod}.starter` |
| `module-*-host` | `com.zxplat.plugin.{mod}.host` |

### 依赖方向

```text
domain ← gateway ← bridge
domain ← application
bridge  ──✓── gateway（网关型）
bridge  ──✓── zxplat-domain（*QueryService / *DomainService）
bridge  ──✗── application
starter → bridge + application + gateway + domain
host    → gateway
```

---

## bridge 读写 HIS domain

| 操作 | API |
|------|-----|
| **读 HIS** | `zxplat-domain.*QueryService`、`queries/model` |
| **写 HIS** | `zxplat-domain.*DomainService` |
| **读/写本 Module 表** | `{mod}-domain.*QueryService` / `*DomainService` |
| **调外平台** | `{mod}-gateway.*GatewayClient` |

**硬约束**

- **禁止** 已废弃的 support 层、直接 `*Repository`、bridge 内自写 SQL。
- **禁止** `internal/*Support`、`*FacadeImpl` 式厚编排层。
- **能力不够时**：在 **zxplat-domain** 扩展服务，并 **同步更新** `docs/领域/DomainModel/**`；**不在 bridge 里绕过 Domain 层补洞**。

---

## 配置

| 开关 | 含义 |
|------|------|
| `zxplat.modules.{mod}.enabled=true` | 装配扩展 starter |
| `zxplat.{mod}.gateway.mode=local` | HIS 直连外平台（默认，网关型） |
| `zxplat.{mod}.gateway.mode=remote` | 出站 HTTP 经 gateway-proxy |

`zxplat-app` / 院点 backend 通过 profile `with-{mod}` 引入 `{mod}-starter`。

文档真源：[模块化开发规范.md](../架构/模块化开发规范.md) · 成码：[prompts/](../../prompts/) / [`.cursor/skills/`](../../.cursor/skills/)
