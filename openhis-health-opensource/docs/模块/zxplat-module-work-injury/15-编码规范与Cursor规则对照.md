# 工伤模块 · 编码规范与 Cursor 规则对照（WORK_INJURY）

> **文档性质**：成码 **合规门禁** — 与业务设计文档（08～11）并列，编码会话须同时遵守  
> **Cursor 真源目录**：[`docs/模块/.cursor/`](../.cursor/)  
> **业务真源**：[08-概要设计.md](./08-概要设计.md) · [12-成码前Checklist.md](./12-成码前Checklist.md) · [13-实现状态与文档对照.md](./13-实现状态与文档对照.md)  
> **版本**：v1.0 · **日期**：2026-07-01

---

## 1. 编码前必读（顺序）

| 顺序 | 读什么 | 路径 |
|:----:|--------|------|
| 1 | 业务 HLD/LLD | 本目录 [08](./08-概要设计.md)、[09](./09-门诊工伤结算-详细设计.md)、[11](./11-客户端invoke会话-详细设计.md) |
| 2 | 成码门禁 | [12-成码前Checklist.md](./12-成码前Checklist.md) §2 R-01～R-08 |
| 3 | **Whale Java 全框架规范** | Cursor Skill [`@代码生成通用规范`](../.cursor/skills/代码生成通用规范/SKILL.md) |
| 4 | **CHS 模块分层** | Rule [`whale-chs-jilin-codegen.mdc`](../.cursor/rules/whale-chs-jilin-codegen.mdc) |
| 5 | **工伤专规** | Rule [`whale-chs-jilin-gs-codegen.mdc`](../.cursor/rules/whale-chs-jilin-gs-codegen.mdc)（本模块） |
| 6 | 编码进度 | [13-实现状态与文档对照.md](./13-实现状态与文档对照.md) — 避免重复实现 |

**Agent 会话建议**：在 Cursor 中 `@代码生成通用规范` + `@whale-chs-jilin-gs-codegen`（或打开 `zxplat-module-work-injury/**` 文件触发 Rule）。

---

## 2. Cursor 规则与 Skill 索引

### 2.1 Java 后端（Whale / zxplat-module-chs-jilin / wi-jilin）

| 类型 | 名称 | 路径 | 适用范围 |
|------|------|------|----------|
| Rule | **whale-java-codegen** | [`.cursor/rules/whale-java-codegen.mdc`](../.cursor/rules/whale-java-codegen.mdc) | `zxplat-plugin/**/*.java` 全框架 P0 |
| Skill | **代码生成通用规范** | [`.cursor/skills/代码生成通用规范/SKILL.md`](../.cursor/skills/代码生成通用规范/SKILL.md) | 注释、禁三元、Repository、交付 checklist |
| Rule | **whale-chs-jilin-codegen** | [`.cursor/rules/whale-chs-jilin-codegen.mdc`](../.cursor/rules/whale-chs-jilin-codegen.mdc) | 省医保 CHS 五层、Flyway、Extension 红线 |
| Rule | **whale-chs-jilin-gs-codegen** | [`.cursor/rules/whale-chs-jilin-gs-codegen.mdc`](../.cursor/rules/whale-chs-jilin-gs-codegen.mdc) | **工伤** wi-jilin、WorkInjury*Extension、invoke |
| Rule | **whale-java-string-utils** | [`.cursor/rules/whale-java-string-utils.mdc`](../.cursor/rules/whale-java-string-utils.mdc) | 字符串工具；chs 内优先 `ChsTextSupport.blankToNull` |
| Rule | **karpathy-guidelines** | [`.cursor/rules/karpathy-guidelines.mdc`](../.cursor/rules/karpathy-guidelines.mdc) | 最小改动、不臆造需求 |
| Ref | modules-all §chs | [`.cursor/skills/代码生成通用规范/references/modules-all.md`](../.cursor/skills/代码生成通用规范/references/modules-all.md) | Maven 分包、Flyway 目录 |
| Ref | comments-gate | [`.cursor/skills/代码生成通用规范/references/comments-gate.md`](../.cursor/skills/代码生成通用规范/references/comments-gate.md) | JavaDoc / `//` 注释专闸 |
| Ref | checklist | [`.cursor/skills/代码生成通用规范/checklist.md`](../.cursor/skills/代码生成通用规范/checklist.md) | 交付前 P0 rg 与人工项 |
| Ref | anti-patterns | [`.cursor/skills/代码生成通用规范/references/anti-patterns.md`](../.cursor/skills/代码生成通用规范/references/anti-patterns.md) | 反模式对照 |

### 2.2 health-application 挂接

| 文档 | 路径 |
|------|------|
| Extension 架构 | [`docs/模块/01-架构/模块化开发规范.md`](../01-架构/模块化开发规范.md) |
| 扩展集成模式 | [`docs/模块/01-架构/模块化开发规范.md`](../01-架构/模块化开发规范.md) |

### 2.3 前端（web-ele）

| 类型 | 名称 | 路径 | 说明 |
|------|------|------|------|
| Rule | **whale-long-id-frontend** | [`.cursor/rules/whale-long-id-frontend.mdc`](../.cursor/rules/whale-long-id-frontend.mdc) | Snowflake ID 必须 string |
| Skill | **前端Long主键规范** | [`.cursor/skills/前端Long主键规范/SKILL.md`](../.cursor/skills/前端Long主键规范/SKILL.md) | `encounterId` 等禁止 `Number()` |

工伤 UI（9102/2301、invoke 循环）还须遵守 [09 §8](./09-门诊工伤结算-详细设计.md)、[07](./07-OpenHIS.Win工伤本地桥接.md) HTTP 契约。

### 2.4 OpenHIS.Win（C# / x86）

| 文档 | 路径 |
|------|------|
| 工伤 Win 真源 | [07-OpenHIS.Win工伤本地桥接.md](./07-OpenHIS.Win工伤本地桥接.md) |
| 银联同构参考 | [`docs/模块/zxplat-module-zxplat-module-unionpay-bpc/08-OpenHIS.Win银联本地桥接.md`](../zxplat-module-zxplat-module-unionpay-bpc/08-OpenHIS.Win银联本地桥接.md) |

Win 侧：**只做** 9001 会话 + `Si_Busi` 透传；**禁止**在 Win 内编排 2201→2207 或写 Whale 业务表。

---

## 3. 规范层级（冲突时优先级）

```text
① 院方决策 [14-院方确认决策.md] + 本目录 BR-WI-* / R-01～R-08
② whale-chs-jilin-gs-codegen（工伤专规）
③ whale-chs-jilin-codegen + modules-all §chs
④ @代码生成通用规范 / whale-java-codegen（全框架 P0）
⑤ 存量代码习惯（不得作为豁免理由）
```

与业务文档冲突时：在 PR / 会话中标注 **DOC-CONFLICT**，先更新 08/09/13 再改码。

---

## 4. 工伤架构硬规则（=R-01～R-08 展开）

| # | 必须 | 禁止 |
|---|------|------|
| WI-C-01 | Extension 接口在 `health-infrastructure` · `integration.workinjury` | `health-application` import `com.zxplat.plugin.wi.*` / 工伤 DTO |
| WI-C-02 | UI AppService 在 `health-application` · `insurance/workinjury`（`WorkInjuryInvokeAppService`） | `wi-jilin-bridge` / `chs-jilin-application` 内新建 UI `*AppService` |
| WI-C-03 | Impl 在 `wi-jilin-bridge` · `integration.workinjury`；`@ConditionalOnProperty(work-injury.enabled=true)` | 未启用时注册非 NoOp Bean |
| WI-C-04 | 客户端 invoke：`prepareInvoke` → Win `:5000` → `submitInvokeResult` | Whale JVM 调 DLL；`InsuranceGatewayClient` 新增 WORK_INJURY HTTP |
| WI-C-05 | 工伤结算走 `WorkInjuryOutpatientSettlementExtension` | 走 `OutpatientChargeInsuranceExtension` / `ChsHttpAccessor` |
| WI-C-06 | `scheme_code=WORK_INJURY` 写 `yb_invoke_log` / `yb_settlement_record` | 使用 `yb_coordination` |
| WI-C-07 | Flyway 在 **`wi-jilin-starter`**（或 chs 子模块）→ `db/migration/wi/` | 工伤表进 health Flyway |
| WI-C-08 | infno 链在 `WorkInjurySettleStepPlanner` **Java 常量** | YAML 逐步配置 infno |
| WI-C-09 | 操作人：`ChsCurrentOperatorResolver`（application） | AppService 直注 `ICurrentUser` |
| WI-C-10 | 350x 仍走省医保 `default-contract-bus-no` | 3505 指向工伤 `-GS` bus_no |

---

## 5. 全框架 Java P0 摘要（@代码生成通用规范 §2.1）

编码 **WorkInjury*AppService** 时与省医保 CHS 相同，无例外：

| # | 必须 | 禁止 |
|---|------|------|
| 0 | 类/方法 JavaDoc；编排 `// Gate/Pre/Cmd/Asm`；DTO 每字段 JavaDoc | 无注释交付；`{@inheritDoc}` 占位 |
| 1 | `if-else` | 三元 `?:` |
| 2 | 枚举 / 库表状态用 String `getCode()` | `parseInt` 作状态身份 |
| 3 | Response 结构化 DTO | `*Json` 整段 String 出参 |
| 4 | 常量进 `wi-jilin-gateway`（如 `WiInfnoConstants`） | AppService 内 magic string |
| 5 | `ChsCurrentOperatorResolver` | AppService 直注 `ICurrentUser` |
| 6 | 条件下推 Repository；批量 I/O | N+1；AppService 用 `EntityManager` |
| 7 | chs 空白归一 `ChsTextSupport.blankToNull` | 新增 `private trimToNull` |
| 8 | 文档 0 差异或 **DOC-CONFLICT** 留痕 | 静默偏离 08/09 |

**invoke 出参**：平台 `output` 在 application 层解析为 `Wi*Output` DTO，**禁止**把原始 JSON 字符串透出给前端 Response。

---

## 6. Maven / 包结构（目标态）

```text
zxplat-module-work-injury/
  wi-jilin-domain/              com.zxplat.plugin.wi.domain          yb_wi_* 实体、Command/Result
  wi-jilin-gateway/             com.zxplat.plugin.wi.gateway.scheme  Wi*Input/Output
  wi-jilin-bridge/              com.zxplat.plugin.wi.bridge          Orchestrator、Assembler
                                com.zxplat.plugin.wi.bridge.integration.workinjury  *ExtensionImpl
  wi-jilin-starter/             装配 + db/migration/wi/        V*_yb_wi_*.sql

zxplat-module-chs-jilin/chs-jilin-domain/   复用 yb_invoke_log、yb_settlement_record 等结算实体

health-infrastructure/        …/integration/workinjury/        WorkInjury*Extension
health-infrastructure/        …/integration/noop/insurance/workinjury/  NoOp*Extension
health-application/           …/insurance/workinjury/          WorkInjuryInvokeAppService

openhis-pro-win/OpenHIS.Win.WorkInjury/   Win HTTP + DllImport
```

| 依赖红线 | 规则 |
|----------|------|
| `wi-jilin-bridge` | **禁止**依赖 `chs-jilin-application`、`health-application`、`ChsHttpAccessor` |
| `health-application` | **禁止** import `com.zxplat.plugin.wi.*` / `com.zxplat.plugin.chs.application.workinjury.*` |
| `wi-jilin-starter` | `@ConditionalOnProperty(zxplat.modules.work-injury.enabled=true)` |

---

## 7. 前端 invoke 与 Long ID

| 场景 | 规范 |
|------|------|
| `encounterId` / `sessionId` / `paymentDraftId` | TS 类型 **string**；禁止 `Number(id)` |
| invoke 循环 | 每步必须 **submit** 后再 prepare 下一步；禁止跳过 Whale 直连多 infno |
| Win 地址 | 固定 `http://127.0.0.1:5000`（与 PluginSettings 一致） |
| capability | 后端 `outpatientRegistration/getWorkInjuryCapability` 门控；未 enabled 不渲染工伤 UI / Tab |

---

## 8. 交付自检（合并 12 + checklist）

### 8.1 业务门禁（12 §2）

- [ ] R-01～R-08 全部满足
- [ ] 对照 [13](./13-实现状态与文档对照.md) §2 更新矩阵

### 8.2 规范门禁（checklist.md）

- [ ] Read `comments-gate.md`；TOUCHED 文件注释 pass
- [ ] §P0 rg 已跑（禁三元、禁 ICurrentUser、禁 @Query on Repository…）
- [ ] 可编译（用户明示跳过则记录原因）
- [ ] 汇报含 **「规范合规摘要」**（Skill §0.4）

### 8.3 工伤专项

- [ ] 无 `ChsHttpAccessor` 调用工伤 infno
- [ ] `prepareInvoke` / `submitInvokeResult` 成对出现
- [ ] `yb_wi_invoke_session.step_token` 校验
- [ ] `work-injury.enabled=false` 回归（06 §10 R1～R6）
- [ ] Win `fixmedinsCode=JL2201992002`、`recerSysCode=LNYTH`（[14](./14-院方确认决策.md)）

---

## 9. 按 Wave 触发的规范要点

| Wave | 路径 | 额外注意 |
|------|------|----------|
| W0 Win | `openhis-pro-win/OpenHIS.Win.WorkInjury/**` | x86；`native/workinjury/`；不编排业务 |
| W1 后端 | `zxplat-module-work-injury/**` | Extension 五接口 + NoOp；Flyway 三表 |
| W1 前端 | `openhis-pro-ui/**` 工伤页面 | Long ID + invoke 插件 |
| W1 health 挂接 | `health-application` 仅改 Extension 调用点 | **禁止** import 工伤 DTO |

---

## 10. 关联文档

| 文档 | 内容 |
|------|------|
| [06-扩展组件与隔离策略.md](./06-扩展组件与隔离策略.md) | NoOp、开关、回归 |
| [zxplat-module-unionpay-bpc 成码模式](../zxplat-module-zxplat-module-unionpay-bpc/03-详细设计与编码指导.md) | prepare + 客户端结果回写（同构参考） |
| [zxplat-module-chs-jilin 13-实现状态](../zxplat-module-chs-jilin/13-实现状态与文档对照.md) | 省医保已落地 infno，勿混抄 |

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-01 | 首版；索引 docs/模块/.cursor 规范并展开工伤 WI-C-* |
