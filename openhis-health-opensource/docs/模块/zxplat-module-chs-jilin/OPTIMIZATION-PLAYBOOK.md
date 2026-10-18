# zxplat-module-chs-jilin 结构优化经验（推广手册）

> 面向：新建或重构 ZXPlat 可选扩展 Module（如 `zxplat-module-work-injury`、区域医保变体）。  
> 真源架构：`docs/架构/模块化开发规范.md` · 本 Module 增量：`docs/模块/zxplat-module-chs-jilin/17-zxplat-module-chs-jilin-架构-v3.md`

---

## 1. 为什么要做（目标）

| 问题 | 优化后 |
|------|--------|
| 包名与 health 不一致（`dto`、`appservice`、`orchestration`） | 与 health 同构：`request/response`、`service/invoker/support` |
| 为每个页面强制 Orchestrator 转发层 | **按需**编排：单 infno 直调 Invoker；多步才用 `*Workflow` |
| bridge 出现 `port/`、`*Support` 厚层 | ExtensionImpl + Invoker + ContextLoader；端口定义在 **domain** |
| gateway scheme 扁平、与 application/bridge 能力切片不对齐 | `scheme/{cap}/model|assembler|parser`，cap 与 domain/bridge 一致 |
| 远程 DTO 散落在 host/gateway 多处 | **gateway 拥有线协议**；host 只引用 `gateway.client.remote.*` |
| 改包靠 IDE 手工、易漏 import | **脚本化迁移** + 每阶段 `mvn compile` |

**成功标准**：每层 README 能回答「允许什么 / 禁止什么」；新人按 cap 切片找代码不超过 3 次点击。

---

## 2. 六层职责（复制到其他 Module 的骨架）

```text
{mod}-domain/       扩展表实体、DomainService、Command/Result、access/loader 端口
{mod}-gateway/      外平台 HTTP、报文 scheme、审计、会话（无业务编排）
{mod}-bridge/       ExtensionImpl、Invoker、ContextLoader、IntegrationMapper
{mod}-application/  扩展 UI AppService、Flyway、config
{mod}-starter/      HIS embedded 装配
{mod}-host/         可选：Gateway-Only Remote（proxy + RemoteClient）
```

**依赖铁律**（Maven Enforcer 应固化）：

```text
domain ← gateway ← bridge
domain ← application
bridge ✓ gateway、health-domain
bridge ✗ application、health-module-support
host   → gateway only（无 bridge/application）
```

---

## 3. 分阶段落地法（P1–P10 可复用节奏）

原则：**小步、可编译、可回滚**；每阶段一个脚本 + README 一行记录。

| 阶段 | 范围 | 动作 | 验证 |
|------|------|------|------|
| **P1–P3** | application | `dto`→`request/response`；去掉 `appservice` 包段 | compile + API 契约不变 |
| **P4–P5** | application | foundation 聚合；删空壳编排；内部 dto 收拢 | compile |
| **P6** | 文档 | codegen + AppService/SDD 与包结构对齐 | 文档 review |
| **P7** | application | Orchestrator 降级为 `support/*Workflow`（仅多步保留） | compile |
| **P8** | application | 去掉 `orchestration/` → 页内 `invoker/` + `support/` | compile |
| **P9** | bridge | 能力切片包；domain `access`/`loader` 端口；删 `bridge/port/` | compile + enforcer |
| **P10-A** | gateway/host | 删孤儿 DTO；observability/session 分包；`*Transport` 命名 | compile |
| **P10-C** | gateway scheme | infno 文件迁入与 application/bridge **同 cap** | compile |
| **P10-D** | gateway scheme | cap 内 `model/` / `assembler/` / `parser/` | compile + fix-imports |
| **P10-8** | gateway | domain access 实现迁入 `{cap}/access/`（非 config） | ComponentScan 补包 |
| **Doc** | 全模块 | 各层 README + JavaDoc 约定 | 代码 review |

CHS 脚本目录：`zxplat-module-chs-jilin/scripts/p*.ps1`

---

## 4. 各层可推广约定

### 4.1 application

```text
{boundedContext}/{feature}/
  service/          *AppService（HTTP 入口）
  request/ response/  对外契约（禁止包名 dto）
  invoker/          唯一注入 GatewayClient（单 infno）
  support/          *Workflow、平台上下文、文件解析
  mapper/           AppService ↔ Command
```

| 规则 | 说明 |
|------|------|
| 单 infno 页面 | AppService **直调** Invoker，不插 Orchestrator |
| 多 infno 页面 | `support/*Workflow` 或 AppService 内私有方法 |
| Gateway 注入点 | **仅** `invoker/`（Enforcer 可检） |

### 4.2 bridge

```text
com.zxplat.plugin.chs.bridge.integration.{domain}.*     ExtensionImpl、IntegrationMapper
com.zxplat.plugin.{mod}.bridge.{cap}/
  invoker/          {Op|Ip}<InfNo>Invoker
  context/          *ContextLoader（实现 domain.loader）
  workflow/         *Orchestrator（多步、不调 gateway）
  mapper/           Extension ↔ Domain
```

| 规则 | 说明 |
|------|------|
| Invoker 命名 | `{Op\|Ip}<InfNo>Invoker`，如 `Op2601Invoker` |
| 共享出站 | 端口放 **domain** `access`（如 `Med5206QueryInvoker`），bridge + application 共用 |
| 禁止 | `bridge/port/`、`*PortImpl`、依赖 application |

### 4.3 domain

```text
{cap}/entity|repository|service|command|result|access|loader|support
share/          错误码、工具
```

| 规则 | 说明 |
|------|------|
| 出站端口 | `access` = 跨模块共享 Invoker 接口；`loader` = HIS enrichment |
| 禁止 | `import ...gateway.*`；直接改 health 实体 |

### 4.4 gateway

```text
client/           业务唯一出站口 + client.remote.*（线协议 DTO）
local/ remote/    实现（remote 实现在 host）
scheme/{cap}/     model | assembler | parser
session/          签到；transport/ 放 *Transport（非 Invoker）
observability/    审计装饰器
catalog/access/   domain access 实现
config/           Spring 装配、契约文件加载
```

| 命名 | 含义 |
|------|------|
| `*Transport` | gateway 内部 HTTP（如 Sign9001） |
| `*InputAssembler` / `*OutputParser` | Command ↔ 平台 JSON |
| `*Invoker` | **仅 bridge/application** 业务出站类 |

### 4.5 host

- 只含 `remote/`（HIS 侧）与 `proxy/`（专网侧）
- **不复制** gateway DTO；引用 `gateway.client.remote.*`
- proxy Controller 透传 `InsuranceGatewayClient`，零业务编排

---

## 5. 脚本化迁移套路（技术经验）

### 5.1 标准脚本步骤

1. **移动文件** → 更新 `package` 声明  
2. **构建 import 映射表**（`旧FQCN` → `新FQCN`）  
3. **全模块替换 import**（键按**长度降序**，避免部分匹配）  
4. **子包分裂后**：对 `assembler/`、`parser/` 跑 fix-imports（补 model 引用）  
5. **`mvn compile -pl {module} -am`**

### 5.2 PowerShell 踩坑（必记）

| 坑 | 对策 |
|----|------|
| `Set-Content -Encoding UTF8` 写 Java 会带 **BOM** | 用 `[System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding $false))` |
| 批量改 import 后 bridge 等文件也带 BOM | 全模块跑 `scripts/fix-bom.ps1` |
| 同 cap 分包后 assembler 缺 model import | 单独脚本扫描 `\bClassName\b` 补 import |
| 全局 `$old` 字符串替换误伤 | 优先只替换 `import $old;`；FQCN 替换需最长优先 |

### 5.3 建议仓库约定

```text
{module}/scripts/
  p{N}-{scope}-{action}.ps1    一阶段一脚本
  fix-bom.ps1                  编码修复工具脚本
{module}/OPTIMIZATION-PLAYBOOK.md
{module}/README.md             迁移阶段表 + 脚本索引
{submodule}/README.md          层边界 + 包树 + JavaDoc 约定
```

---

## 6. 文档与 JavaDoc（与人协作）

每层 README **固定四块**：

1. 职责边界（允许 / 禁止表）  
2. 包结构树  
3. 命名约定  
4. 脚本索引 + 落地阶段表  

JavaDoc 按类型模板（见 domain/bridge/application README）：

- **做什么、调谁、副作用**（表/出站/IO）  
- Invoker / access：`【infno】` 前缀  
- 不为 getter/setter、显然方法堆砌  

标杆类：`InsuranceMedicalInfoQueryAppService`、`Op1101Invoker`、`Med5206QueryInvoker`

---

## 7. 推广到其他 Module 的检查清单

### 启动前

- [ ] 确认六层模块划分与 Enforcer 规则  
- [ ] 列出 bounded context / cap 切片（与 infno 或外部 API 对齐）  
- [ ] 标记「Gateway-Only Remote」是否需要 host 模块  

### 迁移中（每阶段）

- [ ] 脚本 + 本阶段 README 更新  
- [ ] `mvn compile` 通过  
- [ ] 无新增反向依赖（application → bridge 等）  
- [ ] 无 UTF-8 BOM 编译错误  

### 收尾

- [ ] 根 README 迁移表与包结构树已更新  
- [ ] gateway scheme cap 与 bridge/application 一致  
- [ ] domain access 实现在 gateway（非 config 杂项包）  
- [ ] host 不维护重复 DTO  
- [ ] 本模块架构说明（17-v3）与代码一致  

---

## 8. CHS 已落地阶段一览

| 阶段 | 状态 | 要点 |
|------|------|------|
| P1–P6 | ✅ | application 包收敛 + 文档 |
| P7–P8 | ✅ | 去强制 Orchestrator / orchestration |
| P9 | ✅ | bridge 能力切片 + domain 端口 |
| P10-A/B | ✅ | gateway/host 清理 + README |
| P10-C | ✅ | scheme 与 cap 对齐（benefit/settlement/platformdict） |
| P10-D | ✅ | scheme model/assembler/parser |
| P10-8 | ✅ | `ChsContractPropertiesAccessImpl` → `catalog/access` |
| JavaDoc | ✅ domain + bridge；gateway 可选 |
| ArchitectureTest | ✅ application + bridge 互禁 import；application 全 cap 禁 gateway（P15 完成 Invoker 迁移） |
| P11 medicalinfo | ✅ | 5201–5301 Invoker application→bridge；domain access 端口；medicalinfo cap 禁 gateway |
| P12 reconciliation | ✅ | 3201–3209A Invoker application→bridge；Cat9102→domain access；reconciliation cap 禁 gateway |
| P13 catalog | ✅ | 1312/13xx/3301/3302 Invoker + download/mapping workflow application→bridge；catalog cap 禁 gateway |
| P14 inventory | ✅ | 删除 application Inv3501–3506 重复 Bean；3507/35xx/workflow/support→bridge；domain access 端口；inventory cap 禁 gateway |
| P15 foundation | ✅ | sign/platformdict→bridge/foundation；domain `session.access`/`platformdict.access` 端口；application 全层禁 gateway |

**推广**：[`docs/_templates/OPTIMIZATION-PLAYBOOK-template.md`](../../.cursor/templates/OPTIMIZATION-PLAYBOOK-template.md) · [`docs/架构/新扩展包开发指南.md`](../../docs/架构/新扩展包开发指南.md) · 工伤 [`zxplat-module-work-injury/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-work-injury/OPTIMIZATION-PLAYBOOK.md) · 博思 [`zxplat-module-einvoice-boss/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-einvoice-boss/OPTIMIZATION-PLAYBOOK.md)

---

## 9. 反模式速查（禁止带回其他 Module）

| 反模式 | 应改为 |
|--------|--------|
| `internal/*Support` 厚编排 | ExtensionImpl + DomainService |
| application 注入 Gateway 到处散落 | 收敛到 `invoker/` |
| bridge 依赖 application | 共享逻辑下沉 domain 或 health-domain |
| host 自定义 gateway DTO | 迁入 `gateway.client.remote` |
| scheme 全堆在 `gateway.scheme` 根下 | `{cap}/model|assembler|parser` |
| gateway 类名 `*Invoker` | bridge 用 Invoker；gateway 用 Transport/Assembler/Parser |
| 为单接口建 Orchestrator | AppService 直调 Invoker |
| contracts/Facade 层 | domain Command/Result + Extension |

---

## 10. 模块类型变体（推广时选型）

| 类型 | 代表 Module | 层数 | playbook 差异 |
|------|-------------|------|---------------|
| **网关型六层** | chs-jilin、elep、unionpay-bpc | domain+gateway+bridge+application+starter+host | 全文适用 |
| **scheme-only** | work-injury | domain+gateway+bridge+starter | 跳过 application/host/gateway client；Win 作出站 |
| **领域型** | booking、emr | domain+bridge+application+starter | 跳过 gateway/host；无 infno scheme |

---

## 11. 参考文件

| 文件 | 用途 |
|------|------|
| `README.md` | Module 总览、依赖方向、部署 |
| `module-chs-jilin-application/.../README.md` | application 编排规则 P1–P8 |
| `module-chs-jilin-bridge/.../README.md` | bridge 双根包、P9 端口 |
| `module-chs-jilin-domain/README.md` | domain 边界、access 示例 |
| `module-chs-jilin-gateway/README.md` | gateway/scheme P10 |
| `module-chs-jilin-host/README.md` | remote 部署 |
| `scripts/p*.ps1` | 可改编的迁移脚本 |
