# zxplat-module-unionpay-bpc 结构优化手册

> **类型**：网关型六层（P0 Win 被扫 + HTTPS 渠道占位；**无 host 层**）  
> **对照样板**：[`zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md)  
> 架构真源：`docs/模块/zxplat-module-unionpay-bpc/17-zxplat-module-unionpay-bpc-架构-v3.md`

---

## 0. 模块画像

| 项 | 银联 BPC（unionpay-bpc） |
|----|-------------------------|
| 模块类型 | **网关型六层**（缺 host，其余五层已建） |
| 出站方式 | **P0**：OpenHIS.Win DLL 被扫；**P1+**：HTTPS `BpcScanInvoker` / `BpcQueryInvoker`（占位） |
| application 层 | 有 `module-bpc-application`（`UnionPayBpcAppService`、Flyway） |
| host 层 | **无**（专网隔离待产品确认后再建） |
| cap 切片 | scan · refund · query · order（本地落库） |
| 当前 Java 规模 | domain 18 · gateway 3 · bridge 6 · application 5 ≈ **32** |
| Maven 子模块 | **`module-bpc-*`**（P17 ✅ 自 `unionpay-bpc-*` 重命名） |

### 与 CHS playbook 的差异

| CHS 约定 | BPC 处理 |
|----------|----------|
| infno scheme 分包 | BPC 无定长 infno；按 **BPC 交易码 / HTTPS API** 切 gateway scheme |
| P0 出站 | **Win DLL** 执行被扫；JVM `BpcScanInvoker` 仅占位 |
| gateway `client/` | 待 P10 建 `BpcGatewayClient`；当前仅 `config` + `transport` |
| host 层 | 暂无；HTTPS 渠道若需网段隔离再补 `unionpay-bpc-host` |
| contracts | **已废止**；Command/Result 在 domain ✅ |

---

## 1. 当前问题 → 目标

| 问题 | 现状 | 目标 |
|------|------|------|
| v3 脚手架 | Command/Result、Enforcer、无 contracts | ✅ 已落地 |
| domain cap | `command/`、`result/` 扁平 | `{cap}/command|result` + `order/entity` + `share/` |
| bridge 包 | `orchestration/`、`invoker/` 占位 | `{cap}/workflow/` + `{cap}/invoker/`；`UnionPaySettleOrchestrator` → `settle/workflow/` |
| gateway | 仅 `config`、`transport` 常量 | `client/` + `scheme/{scan|refund|query}/model|assembler|parser` |
| application | 扁平 `order/` | `order/service|request|response`；UI 契约与 domain 分离 |
| 文档 / 脚本 | 仅根 README | 各层 README + 本 playbook + `scripts/` |
| Maven 命名 | `unionpay-bpc-*` | P17 对齐 `module-bpc-*` ✅ |

**成功标准**：每层 README 能回答「允许什么 / 禁止什么」；改被扫/查单链路不超过 3 次点击定位 Orchestrator 或 Invoker。

---

## 2. cap 切片表（BPC 交易 / API 对齐）

| cap | 场景 | 出站渠道 | domain Command/Result | bridge 归属 |
|-----|------|----------|----------------------|-------------|
| **scan** | 被扫支付 | P0 Win DLL；P1+ `BpcScanInvoker` | `ApplyClientScan*`、`PrepareConsumerScan*` | `UnionPaySettleOrchestrator` |
| **refund** | 被扫退款 | 同上 | `ApplyClientRefund*` | `UnionPaySettleOrchestrator` |
| **query** | 查单 / 补结算 | P1+ `BpcQueryInvoker` | `QueryBpcOrder*`、`PageBpcOrders*`、`RetrySettleFromBpcOrder*` | Orchestrator + `BpcSettleDomainService` |
| **order** | 本地订单落库 | 无出站 | `BpcOrder` 实体、`BpcSettleDomainService` | — |
| **share** | 院点契约 | gateway `BpcContractConfigLoader` | `BpcTranType` 等 | — |

步骤链真源：`ThirdPartyPaymentExtensionImpl` → `UnionPaySettleOrchestrator` → `BpcSettleDomainService`（迁移后 Orchestrator 在 `settle/workflow/`）。

---

## 3. 推荐阶段（BPC 定制 P 表）

| 优先级 | 代号 | 动作 | 脚本（待建） | 状态 |
|--------|------|------|-------------|------|
| 1 | **P0** | 各层 README + 本 playbook + `scripts/` 目录 | — | ✅ |
| 2 | **P2** | domain `command|result` → `{cap}/command|result`；`order/` 实体归位 | `bpc-p2-domain-cap.ps1` | ✅ |
| 3 | **P5** | bridge `orchestration/` → `settle/workflow/`；`invoker/` 按 cap 分包 | `bpc-p5-bridge-cap.ps1` | ✅ |
| 4 | **P9** | bridge cap 切片；domain `access` 端口（查单共享） | `bpc-p9-bridge-access.ps1` | ✅ |
| 5 | **P10-A** | gateway 建 `client/` + `local/`；`transport` → `session/` 或 `transport/` | `bpc-p10-gateway-client.ps1` | ✅ |
| 6 | **P10-C/D** | gateway `scheme/{scan|refund|query}` model/assembler/parser | `bpc-p10-scheme.ps1` | ✅ |
| 7 | **P1–P3** | application `order/` → `request/response` + `service/` | `bpc-p1-app-rename.ps1` | ✅ |
| 8 | **P10-9** | `BpcScanInvoker` / `BpcQueryInvoker` 接入 gateway client（HTTPS 渠道） | `bpc-p10-invoker-wire.ps1` | ✅ |
| 9 | **P10-host** | 可选：专网 `unionpay-bpc-host` forward proxy | `bpc-p10-host.ps1` | N/A |
| 10 | **P17** | Maven `unionpay-bpc-*` → `module-bpc-*` | `bpc-p17-maven-rename.ps1` | ✅ |
| 11 | **Doc** | JavaDoc + package-info | — | ✅ |

**明确跳过（当前）**：application P7–P8 厚编排（Orchestrator 在 bridge，单步为主）；host 直至 HTTPS 专网需求明确。

**v3 脚手架已落地**（不计入 P 表）：无 contracts、domain Command/Result、bridge Enforcer 禁 `health-module-support`。

---

## 4. 目标包结构

### domain

```text
com.zxplat.plugin.unionpay.bpc.domain/
  scan/command|result/
  refund/command|result/
  query/command|result/
  order/entity|repository/
  service/              BpcSettleDomainService
  share/                错误码、BpcTranType
  access/               跨层共享查单端口（P9）
```

### gateway

```text
com.zxplat.plugin.unionpay.bpc.gateway/
  client/           BpcGatewayClient + client.remote.*
  local/            JVM 侧实现
  scheme/scan/      model | assembler | parser
  scheme/refund/
  scheme/query/
  session/          院点终端、BpcContractConfigLoader
  transport/        BpcTransportConstants（HTTPS 占位）
  config/
```

### bridge

```text
com.openhis.health.integration.payment/   ThirdPartyPaymentExtensionImpl

com.zxplat.plugin.unionpay.bpc.bridge/
  settle/workflow/      UnionPaySettleOrchestrator
  scan/invoker/         BpcScanInvoker（P1+ 接线）
  query/invoker/        BpcQueryInvoker
  config/
```

### application

```text
com.zxplat.plugin.unionpay.bpc.application/
  order/service|request|response/   UnionPayBpcAppService、OrderQuery
  config/
```

---

## 5. 脚本化迁移

1. **fork CHS 节奏**：移动文件 → 更新 `package` → 全局 import 替换（**最长键优先**）
2. **P10 scheme**：可参考 `zxplat-module-chs-jilin/scripts/p10c` + `p10d`；Boss 无定长 infno 变体见 `boss-p10-scheme.ps1`
3. **每步** `mvn compile -pl zxplat-module-unionpay-bpc -am`
4. PowerShell 写 Java **禁止 UTF-8 BOM**；批量改包后跑 `scripts/fix-bom.ps1`

建议脚本目录：

```text
zxplat-module-unionpay-bpc/scripts/
  bpc-p{N}-{scope}-{action}.ps1
  fix-bom.ps1                  ✅
```

---

## 6. 检查清单

### 启动前

- [ ] 确认 P0 被扫仍走 OpenHIS.Win（见 `docs/模块/.../08-OpenHIS.Win银联本地桥接.md`）
- [ ] cap 表与 `05-BPC接口对照表.md` 一致
- [ ] 记录 HTTPS 渠道为占位，勿在 gateway 硬编码生产密钥

### 每阶段

- [ ] 脚本 + 层 README 更新
- [ ] `mvn compile -pl zxplat-module-unionpay-bpc -am`
- [ ] bridge 无新增 `health-module-support` 依赖
- [ ] 无 UTF-8 BOM 编译错误

### 收尾

- [ ] bridge 无 `orchestration/` 包段（改为 `{cap}/workflow`）
- [ ] gateway scheme cap 与 bridge 一致
- [ ] whale-doc `13-实现状态` 与包名一致

---

## 7. 已落地阶段一览

| 阶段 | 状态 | 要点 |
|------|------|------|
| v3 脚手架（无 contracts） | ✅ | domain Command/Result、bridge Enforcer |
| P0 Win 被扫链路 | ✅ | Extension → Orchestrator → DomainService |
| HTTPS Invoker 占位 | ✅ | `BpcScanInvoker`、`BpcQueryInvoker` 空壳 |
| 结构优化 P0–P17 | ✅ P0–P10-9、P17 已执行；P10-host 跳过 |

---

## 8. 反模式速查

| 反模式 | 应改为 |
|--------|--------|
| 恢复 `unionpay-bpc-contracts` | domain Command/Result |
| JVM 直接调银联 DLL | P0 走 OpenHIS.Win；P1+ 走 gateway client |
| bridge `orchestration/` 长期存在 | `{cap}/workflow/` |
| gateway 业务编排在 `config` | 编排在 bridge workflow；gateway 仅传输 |
| application 注入 GatewayClient | 收敛到 bridge `invoker/`（HTTPS 渠道后） |
| bridge 依赖 `health-module-support` | health-domain QueryService（Enforcer 已禁） |

---

## 9. 与 CHS 差异说明

| 维度 | CHS | unionpay-bpc |
|------|-----|---------------|
| 出站键 | infno（9102/2301…） | BPC 交易码 / HTTPS REST |
| P0 传输 | HTTP GatewayClient | **Win DLL** 本地桥 |
| host | 常用专网 proxy | **暂无** |
| scheme | 医保 JSON 定长 | HTTPS 请求体（待 P10 建模） |
| application | health-app 或独立 | 独立 `module-bpc-application` |
| Maven 命名 | `module-chs-jilin-*` | `module-bpc-*` ✅ |

---

## 10. 参考文件

| 文件 | 用途 |
|------|------|
| [`README.md`](./README.md) | Module 总览、六层职责 |
| [`../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md) | 通用方法论 |
| [`../../docs/模块/_templates/OPTIMIZATION-PLAYBOOK-template.md`](../../docs/模块/_templates/OPTIMIZATION-PLAYBOOK-template.md) | 新模块模板 |
| [`../../docs/模块/zxplat-module-unionpay-bpc/13-实现状态与文档对照.md`](../../docs/模块/zxplat-module-unionpay-bpc/13-实现状态与文档对照.md) | 文档对照 |
| `docs/模块/zxplat-module-unionpay-bpc/17-zxplat-module-unionpay-bpc-架构-v3.md` | 架构真源 |
| `docs/模块/zxplat-module-unionpay-bpc/08-OpenHIS.Win银联本地桥接.md` | P0 Win 出站 |
