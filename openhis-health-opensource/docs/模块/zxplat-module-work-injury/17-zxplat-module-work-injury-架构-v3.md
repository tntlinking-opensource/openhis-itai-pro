# zxplat-module-work-injury 架构 v4 — OpenHIS.Win Gateway

> **文档性质**：`zxplat-module-work-injury` 重架构决策（覆盖 [08-概要设计.md](./08-概要设计.md) 中「挂在 chs-jilin 工程内」表述）  
> **日期**：2026-07-12（v4：新增 `module-wi-jilin-application`）  
> **代码**：[`zxplat-module-work-injury/代码仓库-README.md`](../zxplat-module-work-injury/代码仓库-README.md)

---

## 1. 与 v2 / 08-HLD 的差异

| 项 | v2 / 08-HLD | v3（本目录） |
|----|-------------|--------------|
| 父工程 | `zxplat-module-chs-jilin` 内 `wi-jilin-starter` | **独立** `zxplat-module-work-injury` |
| 集成层 | `wi-jilin-bridge` + `chs-jilin-bridge` | **`wi-jilin-bridge`**（ExtensionImpl + Orchestrator） |
| 契约层 | 隐含 contracts / Facade | **无 contracts**；domain Command/Result |
| 出站 | 混在 chs application | **OpenHIS.Win :5000**（`wi-jilin-gateway` 仅 scheme，无 HTTP 客户端） |
| application | `chs-jilin-application/workinjury/*`（历史） | **`module-wi-jilin-application`**（`/api/app/workInjuryInvoke/*`） |
| host | — | **无**（不走 gateway-proxy） |
| DTO | `zxplat-module-chs-jilin/chs-jilin-domain/.../workinjury` | **`wi-jilin-domain`** + 复用 `zxplat-module-chs-jilin/chs-jilin-domain` 结算/审计实体 |

---

## 2. 与 CHS 模块对照

| 项 | CHS (`zxplat-module-chs-jilin`) | 工伤（本模块） |
|----|-------------------------------|--------------|
| 平台出站 | `InsuranceGatewayClient` HTTP | **OpenHIS.Win** prepare/submit |
| gateway 层 | HTTP + scheme | **仅 scheme** |
| 开关 | `zxplat.modules.chs.enabled` | `zxplat.modules.work-injury.enabled` |

---

## 3. 五层模块

| 层 | Maven 模块 | Java 根包 | 职责 |
|----|------------|------------|------|
| **domain** | `module-wi-jilin-domain` | `com.zxplat.plugin.wi.domain` | `yb_wi_*` 实体、Command/Result、DomainService、`{cap}/access` |
| **gateway** | `module-wi-jilin-gateway` | `com.zxplat.plugin.wi.gateway.scheme` | scheme model + **assembler/parser**（无 HTTP） |
| **bridge** | `module-wi-jilin-bridge` | `com.zxplat.plugin.wi.bridge` + `com.zxplat.plugin.wi.bridge.integration.workinjury` | ExtensionImpl、Workflow、Invoker、ContextLoader |
| **application** | `module-wi-jilin-application` | `com.zxplat.plugin.wi.application` | 工伤专属 UI AppService（invoke / 2301 预览） |
| **starter** | `module-wi-jilin-starter` | `com.openhis.whale.wi.starter` | Flyway `wi/` + 装配 bridge + application |

**无** `wi-jilin-host`。

---

## 4. 编排链

```text
module-wi-jilin-application（工伤专属 HTTP）
  → domain invoke/register access
  → bridge InvokerImpl

health-application（HIS 嵌入）
  → WorkInjury*Extension（health-infrastructure）
  → *ExtensionImpl（wi-jilin-bridge）
  → WorkInjury*Orchestrator
  → yb_wi_* DomainService + yb_invoke_log / yb_settlement_record
  → OpenHIS.Win（前端 invoke 循环：prepare → :5000 → submit）
```

**禁止**：`ChsHttpAccessor`、JVM 直调 DLL、`yb_coordination`、`wi-jilin-bridge` 依赖 `chs-jilin-application`。

---

## 5. 装配

```xml
<dependency>
  <groupId>com.openhis</groupId>
  <artifactId>wi-jilin-starter</artifactId>
</dependency>
```

```properties
zxplat.modules.work-injury.enabled=true
```

`health-app` Profile：**`-Pwith-work-injury`**（可与 `-Pwith-chs` 组合）。

---

## 6. 明确不做

- **contracts / Facade 层**
- **迁入 CHS 国标医保代码**
- **Whale JVM 直调工伤 DLL**
- **挂在 `zxplat-module-chs-jilin` 子目录**（已迁入独立 Module）

---

## 7. 迁移状态

| 阶段 | 状态 |
|------|------|
| 独立 `zxplat-module-work-injury` | ✅ |
| `wi-jilin-bridge` → `wi-jilin-bridge` | ✅ |
| Flyway `db/migration/wi/` | ✅ |
| 根 pom / BOM / health-app `with-work-injury` | ✅ |
| 删 `zxplat-module-work-injury/wi-jilin-*` 存量 | ✅ |
| 包结构优化 P2–P9（playbook） | ✅ |

包结构真源：[[`zxplat-module-work-injury/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-work-injury/OPTIMIZATION-PLAYBOOK.md)](../zxplat-module-work-injury/OPTIMIZATION-PLAYBOOK.md) · [13 §1A](./13-实现状态与文档对照.md)
