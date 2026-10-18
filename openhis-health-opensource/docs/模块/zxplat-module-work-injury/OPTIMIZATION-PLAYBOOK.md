# zxplat-module-work-injury 结构优化手册

> **类型**：Win 网关型五层 + OpenHIS.Win 出站（非 HTTP GatewayClient）  
> **对照样板**：[`zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md)  
> 架构真源：`docs/模块/zxplat-module-work-injury/17-zxplat-module-work-injury-架构-v3.md`

---

## 0. 模块画像

| 项 | 工伤（WI） |
|----|-----------|
| Maven 子模块 | domain · gateway · bridge · **application** · starter（**无** host） |
| 出站 | **OpenHIS.Win :5000** prepare/submit 循环，非 Whale HTTP |
| gateway | **仅 scheme**（26 类），无 `client/local/session` |
| UI | `module-wi-jilin-application` → `WorkInjuryInvokeAppService`；HIS 嵌入走 Extension |
| Java 规模 | domain 28 · gateway 26 · bridge 20 · starter 1 ≈ **75** |
| Flyway | 在 **starter**（CHS 在 application —  intentional delta） |

### 与 CHS  playbook 的差异（不要强行对齐）

| CHS 约定 | WI 处理 |
|----------|---------|
| 六层全有 | **五层**（无 host）；gateway 仅 scheme |
| `InsuranceGatewayClient` | **无**；Win 是传输层 |
| application `invoker/` | wi-application 直调 domain access；嵌入流程仍 Extension |
| gateway `*Transport` | 不适用；scheme 仍按 cap 切 |

---

## 1. 当前问题（相对 CHS 已落地状态）

| 问题 | 现状 | 目标 |
|------|------|------|
| domain 包名 | `dto/` 混放 14 类 | `{cap}/command` + `{cap}/result` + `share/` |
| gateway scheme | 26 类扁平在 `scheme/` | `scheme/{cap}/model` |
| assembler/parser | 在 **bridge** | ✅ 迁入 gateway `WiPlatformInputAssembler` + `*OutputParser` |
| bridge 包 | `orchestration/` 4 类 | `{cap}/workflow/`（Win 多步保留 Workflow） |
| bridge 出站 | 无 `*Invoker` | 单 infno 可抽 Invoker；invoke 循环留 Workflow |
| 文档 | 仅根 README | 各层 README + 本 playbook |
| 依赖 | bridge → `health-module-support` | ✅ 已改 `EncounterContextQueryService` + `ProductDefinitionQueryService` |

---

## 2. cap / infno 切片（迁移对齐用）

| cap | Phase | infno 链 | scheme 文件 | bridge 归属 |
|-----|-------|----------|-------------|-------------|
| **register** | REG_* | 9102；2301→9102 | `Wi9102*`, `Wi2301*` | Register Workflow |
| **settle** | OP_SETTLE | 2201→2203→2204→2206→2207 | `Wi2201`–`Wi2208` | OutpatientSettle + Invoke Workflow |
| **reverse** | OP_REVERSE | 2208 | `Wi2208Input` | OutpatientSettle Workflow |
| **recon** | RECON | 1320→1321→1322 | `Wi1320/21/22Input` | Reconciliation Extension |
| **shared** | — | 常量 9001/9002/1101/2202 | `WiInfnoConstants` 等 | — |

步骤链真源：`WorkInjurySettleStepPlanner`（bridge，迁移后 → `settle/workflow/`）。

---

## 3. 推荐阶段（WI 定制 P 表）

| 优先级 | 代号 | 动作 | 脚本（待建） | 状态 |
|--------|------|------|-------------|------|
| 1 | **P0** | 各层 README + 本 playbook | — | ✅ |
| 2 | **P2** | `domain/dto` → `{cap}/command|result` + `share/` | `wi-p2-domain-rename.ps1` | ✅ |
| 3 | **P3** | scheme cap 目录 | `wi-p3-scheme-cap.ps1` | ✅ |
| 4 | **P4** | scheme `{cap}/model/` | `wi-p4-scheme-model.ps1` | ✅ |
| 5 | **P5** | bridge cap 包 | `wi-p5-p6-bridge-restructure.ps1` | ✅ |
| 6 | **P6** | `orchestration/` → `{cap}/workflow/` | `wi-p5-p6-bridge-restructure.ps1` | ✅ |
| 7 | **P7** | gateway assembler/parser + `Build2301PayloadInvoker` | — | ✅ |
| 8 | **P8** | domain `access` 端口（register 2301 预览） | — | ✅ |
| 9 | **P9** | 去掉 bridge → health-module-support | pom + ContextLoader | ✅ |
| 10 | **P10** | `module-wi-jilin-application` + invoke access 端口 | — | ✅ |

**明确跳过**：application P1–P8、host P10-A、gateway `client/` HTTP 层。

---

## 4. 目标包结构

### domain

```text
com.zxplat.plugin.wi.domain/
  register/command|result/
  settle/command|result/
  recon/command/
  invoke/command|result/
  entity/ repository/ service/
  loader/                    WorkInjurySettleContextLoader
  share/                     WiErrorCodes, WorkInjuryConstants
```

### gateway

```text
com.zxplat.plugin.wi.gateway.scheme/
  register/model/     Wi9102*, Wi2301*
  settle/model/       Wi2201–Wi2208*
  recon/model/        Wi1320–1322*
  shared/model/       WiInfnoConstants, WiPhase, WiJsonSupport, ...
  register/assembler/ parser/
  settle/assembler/   parser/
```

### bridge

```text
com.zxplat.plugin.wi.bridge.integration.workinjury/   ExtensionImpl + IntegrationMapper

com.zxplat.plugin.wi.bridge/
  register/workflow/    WorkInjuryRegisterOrchestrator → *Workflow
  settle/workflow/        WorkInjuryOutpatientSettleOrchestrator, SettleStepPlanner
  invoke/workflow/        WorkInjuryInvokeOrchestrator
  context/              ContextLoader, ContractResolver
  config/
```

---

## 5. 脚本化迁移（复用 CHS 经验）

1. **P2 domain 重命名**：按 cap 移动 `dto/*` → 更新 import（最长优先）
2. **P3/P4 scheme**：可 fork `chs-jilin/scripts/p10c` + `p10d` + `p10d-fix-imports` + `fix-bom`
3. **P6 orchestration 重命名**：文件移动 + 类名 `*Orchestrator` 可保留（CHS bridge 仍用 Orchestrator 表多步）
4. **每步** `mvn compile -pl zxplat-module-work-injury -am`

PowerShell 禁忌同 CHS：**禁止 UTF-8 BOM**。

---

## 6. wi-application 侧（P10 ✅）

| 路径 | 说明 |
|------|------|
| `module-wi-jilin-application/.../invoke/service/WorkInjuryInvokeAppService` | Win invoke + `build2301Payload` |
| `.../register/support/WorkInjury2301PayloadPreviewSupport` | 注入 `Build2301PayloadInvoker` |

**health-app 嵌入**（Extension only，无 `com.zxplat.plugin.wi.*`）：

| 路径 | 说明 |
|------|------|
| `outpatient/.../OutpatientRegistrationAppService` | capability / bindQualification / 9102 |
| `outpatient/.../OutpatientChargeAppService` | 预结算 / refresh 金额 |
| `outpatient/.../DoctorStationEncounterManageAppService` | 列表角标 |

`health-application` **不再**依赖 `module-wi-jilin-domain`；运行时由 `wi-jilin-starter` + `zxplat.modules.work-injury.enabled=true` 装配 application + bridge。

---

## 7. 检查清单

### 启动

- [x] 确认 **不** 引入 `{mod}-application`（除非产品要求 WI 独立 UI 模块）
- [x] cap 表与 `WiInfnoConstants` 一致
- [x] 记录 Win-only 出站例外

### 每阶段

- [x] 脚本 + 层 README 更新
- [x] `mvn compile`
- [x] `fix-bom.ps1` 若用 PowerShell 写 Java

### 收尾

- [x] bridge 无 `orchestration/` 包段（改为 `{cap}/workflow`）
- [x] gateway 无 bridge 内 Assembler/Parser
- [x] whale-doc `13-实现状态` 与包名一致

---

## 8. 参考

| 文件 | 用途 |
|------|------|
| [`README.md`](./README.md) | 与 CHS 差异、装配 |
| [`../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md) | 通用方法论 |
| [`../../docs/模块/_templates/OPTIMIZATION-PLAYBOOK-template.md`](../../docs/模块/_templates/OPTIMIZATION-PLAYBOOK-template.md) | 新模块模板 |
| `docs/模块/zxplat-module-work-injury/17-zxplat-module-work-injury-架构-v3.md` | 架构真源 |
