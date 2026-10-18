# zxplat-module-einvoice-boss 结构优化手册

> **类型**：网关型六层（HTTP GatewayClient + Extension 链）  
> **对照样板**：[`zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md)  
> 架构真源：`docs/模块/01-架构/模块化开发规范.md` · `docs/模块/zxplat-module-einvoice-boss/17-zxplat-module-einvoice-boss-架构-v3.md`

---

## 0. 模块画像

| 项 | 博思 Boss（einvoice-boss） |
|----|---------------------------|
| 模块类型 | **网关型六层** |
| 出站方式 | HTTP `EinvoiceBossGatewayClient`（签名 + RestTemplate） |
| application 层 | 有 `module-einv-boss-application`（UI AppService + issue Worker） |
| host 层 | 有 `module-einv-boss-host`（网段隔离 forward proxy） |
| cap 切片 | issue · writeoff · recover · reconcile · document · cashier · list · invoke · contrast |
| 当前 Java 规模 | `module-einv-boss-domain` 49 · `module-einv-boss-gateway` 27 · `module-einv-boss-bridge` 24 · `module-einv-boss-application` 65 · `module-einv-boss-host` 4 ≈ **169** |
| Maven 子模块 | **`module-einv-boss-*`**（P17 ✅ 自 `einvoice-boss-*` 重命名） |

### 与 CHS playbook 的差异

| CHS 约定 | Boss 处理 |
|----------|-----------|
| infno scheme 分包 | Boss 无 infno；按 **serviceId**（`writeOffEBill` 等）切 gateway scheme |
| bridge Invoker | ✅ 出站 Invoker 在 **bridge** `{cap}/invoker/` |
| Flyway | 在 **application**（与 CHS 一致） |
| UI | 独立 `{mod}-application`，非 health-app |

---

## 1. 当前问题（相对 CHS 已落地状态）

| 问题 | 现状 | 目标 |
|------|------|------|
| application 包名 | ~~`orchestration/` 混放 Invoker~~ | ✅ `{cap}/invoker/`（P7–P8） |
| application 契约 | ~~`dto/` 16 类~~ | ✅ `request/response`（P1–P3） |
| application 厚层 | `internal/issue/*` 20+ 类 | Worker 保留；出站收敛 Invoker |
| domain 包名 | ~~`dto/` 混放 6 类~~ | ✅ `{cap}/result`（P2） |
| bridge 包 | ~~扁平 `context/` 6 Loader~~ | ✅ `{cap}/context/`（P9） |
| gateway scheme | ~~无 cap 目录；`support/BossSignSupport`~~ | ✅ `scheme/{share|issue}` + `session/`（P10-C/D、P10-A） |
| 出站 Invoker | ~~application `{cap}/invoker/`~~ | ✅ bridge `{cap}/invoker/`（P10-9） |
| 出站编排 | ~~`EinvoiceIssueOrchestrator` 在 application/client~~ | ✅ `application/issue/workflow/`（Boss Worker 保留 application） |
| 文档 | 仅根 README | 各层 README + 本 playbook + 脚本索引 | ✅ |

**成功标准**：每层 README 能回答「允许什么 / 禁止什么」；改 Boss 开具链路不超过 3 次点击定位 Invoker。

---

## 2. cap / serviceId 切片（迁移对齐用）

| cap | 场景 | serviceId / 能力 | application 归属 | bridge 归属 |
|-----|------|------------------|------------------|-------------|
| **issue** | 支付后异步开具 | `issueEBill` 等 | `internal/issue/*` Worker | `BossElectronicInvoiceExtensionImpl` |
| **writeoff** | 冲红 | `writeOffEBill` | `EinvWriteOffInvoker` | Extension 链 |
| **recover** | 查票补开 | `getEBillByBusNo` | `EinvRecoverInvoker` | Extension 链 |
| **reconcile** | 对账页 | 本地汇总 + 平台核对 | `EinvoiceBossReconcileAppService` | — |
| **document** | 票据文档初始化 | — | `EinvoiceBossDocumentAppService` | ContextLoader |
| **cashier** | 收银台开票 | — | `EinvoiceBossCashierAppService` | — |
| **list** | 票据列表 | — | `EinvoiceBossListAppService` | — |

步骤链真源：`EinvoiceIssueOrchestratorImpl`（`application/issue/workflow/`，委托 Worker / 同步服务）。

---

## 3. 推荐阶段（Boss 定制 P 表）

| 优先级 | 代号 | 动作 | 脚本（待建） | 状态 |
|--------|------|------|-------------|------|
| 1 | **P0** | 各层 README + 本 playbook | — | ✅ |
| 2 | **P1–P3** | application `dto`→`request/response`（页面包已有部分） | `boss-p1-app-rename.ps1` | ✅ |
| 3 | **P7–P8** | `orchestration/` → 页内 `invoker/` + `support/*Workflow` | `boss-p7-p8-app.ps1` | ✅ |
| 4 | **P2** | domain `dto` → `{cap}/command|result` | `boss-p2-domain.ps1` | ✅ |
| 5 | **P9** | bridge cap 包；domain `access`/`loader` 端口命名统一 | `boss-p9-bridge.ps1` | ✅ |
| 6 | **P10-C/D** | gateway `scheme/{share|issue}`（parser + assembler） | `boss-p10-scheme.ps1` | ✅ |
| 7 | **P10-A** | gateway `BossSignSupport` → `session/`；host DTO 归位 | `boss-p10-gateway.ps1` | ✅ |
| 8 | **P10-9** | `Einv*Invoker` 从 application 迁入 bridge | `boss-p10-invoker-bridge.ps1` | ✅ |
| 9 | **P11** | writeoff/recover 出站 support；Orchestrator → `issue/workflow/` | `boss-p11-workflow.ps1` | ✅ |
| 10 | **P12** | `finance/einvoice/**` 归 cap；`reader/writer/client` → `loader/access` | `boss-p12-domain.ps1` | ✅ |
| 11 | **P13** | domain/application 去 `*Dto` 后缀 | `boss-p13-rename-dto.ps1` | ✅ |
| 12 | **P14** | `application/{cap}/` 扁平化；出站 support 迁 bridge | `boss-p14-app-flatten.ps1` + `boss-p14-bridge-support.ps1` | ✅ |
| 13 | **P15** | `BossInvokeResult` 等归 domain；`writeback` → `writeoff/context` | `boss-p15-cap-model.ps1` | ✅ |
| 14 | **P16** | domain `access` 端口；application 去 bridge 编译依赖 + enforcer | `boss-p16-access.ps1` | ✅ |
| 15 | **P17** | Maven 子模块 `module-einv-boss-*` | `boss-p17-maven-rename.ps1` | ✅ |
| 16 | **Doc** | JavaDoc（domain → bridge → application 出站链） | — | ✅ |

**明确保留**：`internal/issue/*` Worker/Scheduler（异步开具，非页面直调 Invoker）。

---

## 4. 目标包结构

### domain

```text
com.zxplat.plugin.einvoice.boss.domain/
  share/                   错误码、BossServiceIds
  issue/entity|repository|loader|access|result/
  document/entity|repository|service|result/
  invoke/entity|repository|support|result/
  contrast/entity|repository/
  writeoff/loader|access|result/
  recover/access/
  reconcile/access|result/
  list/access|result/
```

### gateway

```text
com.zxplat.plugin.einvoice.boss.gateway/
  client/           EinvoiceBossGatewayClient + client.remote.*
  local/ remote/    实现（remote 在 host）
  scheme/issue/     model | assembler | parser（按 serviceId）
  session/          BossSignSupport
  config/
```

### bridge

```text
com.zxplat.plugin.einvoice.boss.bridge.integration.einvoice/   ExtensionImpl

com.zxplat.plugin.einvoice.boss.bridge/
  issue/context|invoker|support/    快照 enrichment、出站、Payload 元数据
  writeoff/context|invoker|support/
  recover/invoker|support/
  reconcile/invoker|support/
  list/invoker|support/
  config/
```

### application

```text
com.zxplat.plugin.einvoice.boss.application/
  {cap}/service|request|response/   common、cashier、compensation、contrast、document、list、reconcile
  internal/issue/                   Worker、Scheduler（后台）
  issue/workflow/                   Orchestrator（异步开具）
```

---

## 5. 脚本化迁移（复用 CHS 经验）

1. **移动 + 改 package** → 全局 import 替换（**最长键优先**）
2. **每步** `mvn compile -pl zxplat-module-einvoice-boss -am`
3. PowerShell 写 Java **禁止 UTF-8 BOM**；批量改包后跑 `scripts/fix-bom.ps1`（⬜ 待建）

### 脚本索引（`zxplat-module-einvoice-boss/scripts/`）

| 阶段 | 脚本 | 状态 |
|------|------|------|
| P1 application dto→response | `boss-p1-app-rename.ps1` | ✅ |
| P2 domain dto→cap/result | `boss-p2-domain.ps1` | ✅ |
| P7–P8 application 去 orchestration | `boss-p7-p8-app.ps1` | ✅ |
| P9 bridge cap/context | `boss-p9-bridge.ps1` | ✅ |
| P10-A gateway session | `boss-p10-gateway.ps1` | ✅ |
| P10-C/D gateway scheme | `boss-p10-scheme.ps1` | ✅ |
| P10-9 invoker→bridge | `boss-p10-invoker-bridge.ps1` | ✅ |
| P11 workflow + outbound support | `boss-p11-workflow.ps1` | ✅ |
| P12 domain cap 收敛 | `boss-p12-domain.ps1` | ✅ |
| P13 去 `*Dto` 后缀 | `boss-p13-rename-dto.ps1` | ✅ |
| P14 application 扁平 + support→bridge | `boss-p14-app-flatten.ps1` + `boss-p14-bridge-support.ps1` | ✅ |
| P15 cap 模型归 domain | `boss-p15-cap-model.ps1` | ✅ |
| P16 domain access 端口 + enforcer | `boss-p16-access.ps1` | ✅ |
| P17 Maven `module-einv-boss-*` | `boss-p17-maven-rename.ps1` | ✅ |
| 编码修复 | `fix-bom.ps1` | ⬜ |

> 脚本清单与 P16 端口对照见 [`docs/模块/.../13-实现状态与文档对照.md`](../../docs/模块/zxplat-module-einvoice-boss/13-实现状态与文档对照.md) **v3.5** §4。

---

## 6. 各层可推广约定（Boss 裁剪）

| 规则 | Boss 落地 |
|------|-----------|
| 单 serviceId | AppService **直调** Invoker |
| 支付事件 → 异步开具 | Extension → Orchestrator → Worker（**保留** Workflow） |
| Gateway 注入点 | 最终收敛到 bridge `invoker/`；过渡期允许 application `orchestration/` |
| host | 只 forward；**不复制** gateway DTO |
| bridge | 禁止依赖 application |

---

## 7. 检查清单

### 启动前

- [x] 确认六层模块划分与 v3 架构文档一致
- [x] 列出 cap 与 Boss serviceId 对照表
- [ ] 确认 host 网段隔离部署需求

### 每阶段

- [x] 脚本 + 层 README 更新（P2 `boss-p2-domain.ps1`）
- [x] `mvn compile` 通过（2026-07-12：P10-C/D、P10-9）
- [x] `mvn test` 通过（gateway scheme + bridge invoker）
- [ ] 无新增 bridge → application 反向依赖
- [ ] 无 UTF-8 BOM 编译错误

### 收尾

- [x] application 无 `orchestration/` 包段（P7–P8 ✅）
- [x] gateway scheme 与 bridge cap 一致（P10-C/D ✅）
- [x] Maven 子模块为 `module-einv-boss-*`（P17 ✅）
- [x] whale-doc [`13-实现状态与文档对照`](../../docs/模块/zxplat-module-einvoice-boss/13-实现状态与文档对照.md) **v3.5** 与包名一致

---

## 8. v3 已落地阶段一览

| 阶段 | 状态 | 要点 |
|------|------|------|
| scaffold + contracts→domain | ✅ | 废止 contracts 模块 |
| domain P2 dto→cap/result | ✅ | `scripts/boss-p2-domain.ps1` |
| gateway 从 application 拆出 | ✅ | `EinvoiceBossGatewayClient` |
| bridge Extension + ContextLoader | ✅ | 6× Loader 重命名 |
| http-starter→host | ✅ | `module-einv-boss-host` forward proxy |
| Orchestrator + Invoker | ✅ | Invoker 在 bridge；Orchestrator 在 `module-einv-boss-application/issue/workflow/` |
| application P1 dto→response | ✅ | `scripts/boss-p1-app-rename.ps1` |
| bridge P9 cap/context | ✅ | `scripts/boss-p9-bridge.ps1` |
| gateway P10-A session | ✅ | `scripts/boss-p10-gateway.ps1` |
| gateway P10-C/D scheme | ✅ | `scripts/boss-p10-scheme.ps1` |
| bridge P10-9 invoker | ✅ | `scripts/boss-p10-invoker-bridge.ps1` |
| 根 README + 本 playbook | ✅ | P0 |
| P12 domain cap 收敛 | ✅ | `boss-p12-domain.ps1` |
| P13 去 *Dto | ✅ | `boss-p13-rename-dto.ps1` |
| P14 application 扁平 | ✅ | `boss-p14-app-flatten.ps1` + `boss-p14-bridge-support.ps1` |
| P15 cap 模型归 domain | ✅ | `boss-p15-cap-model.ps1` |
| P16 domain access + enforcer | ✅ | `boss-p16-access.ps1` |
| P17 Maven 重命名 | ✅ | `boss-p17-maven-rename.ps1` |

---

## 9. 反模式速查

| 反模式 | 应改为 |
|--------|--------|
| application `orchestration/` 长期存放 Invoker | 迁 bridge `{cap}/invoker/` |
| `internal/*` 直接调 GatewayClient | Invoker 封装出站 |
| domain `dto/` 对外泄漏 | Command/Result + application request/response |
| bridge 依赖 application | 共享逻辑下沉 domain |
| host 自定义 Boss DTO | 迁入 `gateway.client.remote` |
| 为单 serviceId 建 Orchestrator | AppService 直调 Invoker |

---

## 10. 参考文件

| 文件 | 用途 |
|------|------|
| [`README.md`](./README.md) | Module 总览、六层职责 |
| [`../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md) | 通用方法论 |
| [`../../docs/模块/_templates/OPTIMIZATION-PLAYBOOK-template.md`](../../docs/模块/_templates/OPTIMIZATION-PLAYBOOK-template.md) | 新模块模板 |
| `docs/模块/zxplat-module-einvoice-boss/17-zxplat-module-einvoice-boss-架构-v3.md` | 架构真源 |
| [`docs/模块/zxplat-module-einvoice-boss/13-实现状态与文档对照.md`](../../docs/模块/zxplat-module-einvoice-boss/13-实现状态与文档对照.md) | 文档对照 **v3.5**（含 P12–P17 脚本索引） |
| `module-einv-boss-domain/README.md` 等 | 各层边界（P0） |

---

## 11. 借鉴 CHS / 工伤的经验（Boss 落地版）

> 真源：[`zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md) · [`zxplat-module-work-injury/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-work-injury/OPTIMIZATION-PLAYBOOK.md)

### 11.1 可复用节奏

| 经验 | CHS/工伤做法 | Boss 已落地 |
|------|-------------|------------|
| **脚本先行** | `p*.ps1` 改包 + 全局 import 替换 | `boss-p2/p1/p7-p8/p9/p10-gateway.ps1` |
| **小步可编译** | 每阶段 `mvn compile -pl {mod} -am` | 每脚本后 compile ✅ |
| **UTF-8 无 BOM** | `fix-bom.ps1` / `UTF8Encoding $false` | 脚本统一无 BOM 写文件 |
| **P 表驱动** | P1→P10 按层推进，README 同步 | 本 playbook §3 P 表 |
| **cap 切片对齐** | infno ↔ domain/bridge/gateway 同 cap | serviceId ↔ issue/writeoff/recover |

### 11.2 Boss 与 CHS/工伤的差异（勿生搬硬套）

| 维度 | CHS / 工伤 | Boss |
|------|-----------|------|
| 出站切片键 | `infno`（9102/2301…） | `serviceId`（`issueEBill`/`writeOffEBill`…） |
| 多步编排 | 页面 Workflow / REG_APPROVAL 链 | **支付事件 → Orchestrator → Worker**（保留） |
| UI 层 | health-application 注入 domain Invoker | 独立 `module-einv-boss-application` AppService |
| scheme 内容 | 医保 JSON 报文 | 博思 base64+sign 信封；业务 JSON 在 `internal/boss/assembler` |
| Invoker 迁 bridge 前置 | gateway scheme 已有 parser | ✅ `BossResponseParser` 在 `scheme.share.parser` |

### 11.3 下一阶段建议

1. ~~**issue parser 收敛**~~：`IssueOutputParser` 承接 `toIssueResult`；冲红 `eScarlet*` 仍由 `BossWriteOffService.markSuccess` 单独映射
2. ~~**Worker 瘦身**~~：`BossIssueWorkerService` 仅保留抢锁/状态机；出站委托 support 类
3. ~~**Enforcer 扩展**~~：架构测试禁止 application import `gateway.client|local|remote.*`；bridge 禁止 import `application.*`
4. ~~**writeoff/recover 出站 support**~~：`BossWriteOffOutboundSupport`、`BossRecoverOutboundSupport`
5. ~~**Orchestrator 包归位**~~：`application/issue/workflow/`（Boss 与 CHS 不同，实现保留 application）
6. ~~**JavaDoc**~~：domain 端口/实体/仓储、bridge Invoker/Extension、application Worker/support/workflow、gateway parser/assembler/support

### 11.4 反模式复盘（CHS 教训在 Boss 的映射）

| CHS 踩坑 | Boss 对应 |
|----------|----------|
| bridge 依赖 application | Invoker 已迁 bridge；application 仅依赖 bridge 注入出站 Bean |
| 为单 infno 建 Orchestrator | 单 serviceId 直调 Invoker；仅异步开具保留 Orchestrator |
| domain `dto` 泄漏 | application 禁止 `dto` 包名（P1 ✅） |
| gateway `support/` 杂糅 | 签名归 `session/`（P10-A ✅） |

---

## 12. P12–P17 结构收敛（✅）

| 阶段 | 目标 | 脚本 | 状态 |
|------|------|------|------|
| **P12** | `finance/einvoice/**` 归 cap；`reader/writer/client` → `loader/access`；`constants` → `share` | `boss-p12-domain.ps1` | ✅ |
| **P13** | domain/application 去 `*Dto` 后缀 | `boss-p13-rename-dto.ps1` | ✅ |
| **P14** | `application/{cap}/` 扁平化；出站 support 迁 bridge；禁 `gateway.scheme` import | `boss-p14-app-flatten.ps1` + `boss-p14-bridge-support.ps1` | ✅ |
| **P15** | `BossInvokeResult`/`BossAccountStatusResult` 归 domain；`writeback` → `writeoff/context` | `boss-p15-cap-model.ps1` | ✅ |
| **P16** | domain `access` 端口；application 去 bridge 编译依赖 + enforcer | `boss-p16-access.ps1` | ✅ |
| **P17** | Maven 子模块 `module-einv-boss-*` | `boss-p17-maven-rename.ps1` | ✅ |

**P16 端口一览**（application 仅注入 domain access）：

| 端口 | bridge 实现 |
|------|-------------|
| `issue/access/BossIssueOutboundAccess` | `bridge.issue.support.BossIssueOutboundSupport` |
| `issue/access/BossIssueDocumentResultAccess` | `bridge.issue.support.BossIssueDocumentResultSupport` |
| `issue/access/BossPayloadMetadataAccess` | `bridge.issue.support.BossPayloadMetadataSupport` |
| `writeoff/access/BossWriteOffOutboundAccess` | `bridge.writeoff.support.BossWriteOffOutboundSupport` |
| `recover/access/BossRecoverOutboundAccess` | `bridge.recover.support.BossRecoverOutboundSupport` |
| `reconcile/access/BossReconcileQueryAccess` | `bridge.reconcile.support.BossReconcileService` |
| `list/access/BossAccountStatusQueryAccess` | `bridge.list.support.BossAccountQueryService` |
| `invoke/access/BossInvokeLogAccess` | `application.internal.issue.BossInvokeLogWriter` |
