# zxplat-module-elep-jilin 结构优化手册

> **类型**：网关型六层（HTTP `ElepGatewayClient` + GM 签名 + host Remote）  
> **对照样板**：[`zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md)  
> 架构真源：`docs/模块/zxplat-module-elep-jilin/17-zxplat-module-elep-jilin-架构-v3.md`

---

## 0. 模块画像

| 项 | 吉林 ELEP（elep-jilin） |
|----|------------------------|
| 模块类型 | **网关型六层** |
| 出站方式 | HTTP `ElepGatewayClient`（GM 加解密 + RestTemplate） |
| application 层 | 有 `module-elep-jilin-application`（`ElepPrescriptionOrchestrator`、`ElepPrescriptionAppService`） |
| host 层 | 有 `module-elep-jilin-host`（Remote Client，原 http-starter） |
| cap 切片 | prescription · catalog · share |
| 当前 Java 规模 | domain 59 · gateway 11 · bridge 5 · application 19 · host 7 ≈ **101** |
| Maven 子模块 | **`module-elep-jilin-*`**（P17 ✅ 自 `elep-jilin-*` 重命名） |

### 与 CHS playbook 的差异

| CHS 约定 | ELEP 处理 |
|----------|-----------|
| infno scheme | ELEP 用 **OpCode**（`A0001`–`A0006`，见 `ElepOpCode`） |
| bridge Invoker | 当前在 **application** `orchestration/`；目标迁 bridge `{cap}/invoker/` |
| domain facade | `prescription/facade/ElepPrescriptionOperations` 待收敛为 access 端口 |
| 本地 PDF | A0001 后、A0002 前；留在 application `prescription/pdf/` |
| contracts | **已废止**；Command/Result 在 domain ✅ |

---

## 1. 当前问题 → 目标

| 问题 | 现状 | 目标 |
|------|------|------|
| v3 迁移 | Wave4 完成，`mvn compile` 通过 | ✅ 基线 |
| domain 包名 | `dto/`、`facade/` 混放（prescription、catalog） | `{cap}/command|result` + `access|loader`；废止 `facade/` ✅ |
| application Invoker | `prescription/orchestration/ElepA0001`–`A0006` | `prescription/invoker/`；多步保留 `prescription/workflow/` |
| application Orchestrator | `ElepPrescriptionOrchestrator` 厚类 | 降级为 `support/*Workflow` 或保留 workflow 包 |
| gateway scheme | `dto/` 扁平；`crypto/`、`support/` 杂糅 | `scheme/{prescription|catalog}/model|assembler|parser` + `session/` |
| bridge 包 | 扁平 `config` + ExtensionImpl | `{cap}/context/`、`{cap}/invoker/` |
| 文档 / 脚本 | 根 README + whale-doc | 各层 README + 本 playbook + `scripts/` |

**成功标准**：按 OpCode 找 Invoker/scheme 不超过 3 次点击；domain 无 `Dto` 后缀对外泄漏 ✅。

---

## 2. cap / OpCode 切片表

| cap | OpCode | 平台路径（`ElepOpCode`） | application 归属 | bridge / gateway 归属 |
|-----|--------|-------------------------|------------------|----------------------|
| **prescription** | A0001 | `/fixmedins/uploadChk` 上传预核验 | `ElepA0001Invoker` | scheme `prescription` |
| **prescription** | A0002 | `/fixmedins/rxFixmedinsSign` 医保电子签名 | `ElepA0002Invoker` | scheme + `session`（GM） |
| **prescription** | A0003 | `/fixmedins/rxFileUpld` 电子处方上传 | `ElepA0003Invoker` | scheme `prescription` |
| **prescription** | A0004 | `/fixmedins/rxUndo` 电子处方撤销 | `ElepA0004Invoker` | scheme `prescription` |
| **prescription** | A0005 | `/fixmedins/hospRxDetlQuery` 平台处方查询 | `ElepA0005Invoker` | scheme `prescription` |
| **prescription** | A0006 | `/fixmedins/rxSetlInfoQuery` 取药结果查询 | `ElepA0006Invoker` | scheme `prescription` |
| **catalog** | — | 国家目录只读 | `application/catalog` | `domain/catalog` + gateway 查询 |
| **share** | — | 契约、快照、RxNo | `domain/snapshot`、`domain/config` | gateway `crypto` |

步骤链真源：`ElepPrescriptionOrchestrator`（A0001→PDF→A0002→A0003 多步链）。

---

## 3. 推荐阶段（ELEP 定制 P 表）

| 优先级 | 代号 | 动作 | 脚本（待建） | 状态 |
|--------|------|------|-------------|------|
| 1 | **P0** | 各层 README + 本 playbook + `scripts/` | — | ✅ |
| 2 | **P2** | domain `dto/`、`facade/` → `{cap}/command|result` + `access` | `elep-p2-domain.ps1` | ✅ |
| 3 | **P1–P3** | application 契约 `request/response`（hisproxy 页） | `elep-p1-app-rename.ps1` | ✅ N/A |
| 4 | **P7–P8** | `orchestration/` → `prescription/invoker/` + `prescription/workflow/` | `elep-p7-p8-app.ps1` | ✅ |
| 5 | **P9** | bridge cap 包；`ElepPrescriptionExtensionImpl` + ContextLoader | `elep-p9-bridge.ps1` | ✅ |
| 6 | **P10-C/D** | gateway `scheme/{prescription|catalog}` model/assembler/parser | `elep-p10-scheme.ps1` | ✅ |
| 7 | **P10-A** | gateway `crypto` → `session/`；`dto` 迁入 `client.remote` | `elep-p10-gateway.ps1` | ✅ |
| 8 | **P10-9** | `ElepA0001`–`A0006` Invoker 迁 bridge `{cap}/invoker/` | `elep-p10-invoker-bridge.ps1` | ✅ |
| 9 | **P10-host** | host remote DTO 归位 `gateway.client.remote` | `elep-p10-host.ps1` | ✅ |
| 10 | **P17** | Maven `elep-jilin-*` → `module-elep-jilin-*` + Enforcer/ArchTest | `elep-p17-maven-rename.ps1` | ✅ |
| 11 | **P13** | domain `result/`、`catalog/model/` 去 `Dto` 后缀 | `elep-p13-rename-dto.ps1` | ✅ |
| 12 | **Doc** | JavaDoc（Invoker 标 `【A000x】`）+ 各层 README 四块 | — | ✅ |

**v3 Wave4 已落地**（不计入待办）：六层模块、无 contracts、`ElepPrescriptionExtensionImpl`、`ElepGatewayClient`、`mvn compile` ✅。

---

## 4. 目标包结构

### domain

```text
com.zxplat.elep.domain/
  prescription/command|result|entity|repository|access|loader/
  catalog/model|repository|access/
  snapshot/                 ElepRxNoSnapshot
  share/                    ElepOpCode、错误码
  config/                   院点契约
```

### gateway

```text
com.zxplat.elep.gateway/
  client/           ElepGatewayClient + client.remote.*
  local/ remote/    实现（remote 在 host）
  scheme/prescription/   model | assembler | parser（按 A000x）
  scheme/catalog/
  session/          GM 加解密（原 crypto）
  config/
```

### bridge

```text
com.zxplat.plugin.elep.bridge.integration.elep/        ElepPrescriptionExtensionImpl
com.zxplat.plugin.elep.bridge.integration.elep.mapper/ ElepIntegrationMapper

com.zxplat.elep.bridge/
  prescription/context|invoker/
  catalog/context/
  config/
```

### application

```text
com.zxplat.elep.application/
  prescription/service|request|response/
  prescription/invoker/       ElepA0001–A0006（迁移前在 orchestration）
  prescription/workflow/      ElepPrescriptionOrchestrator
  prescription/pdf|support/   本地 PDF、草稿
  catalog/service|support/
  hisproxy/                   ElepPrescriptionAppService
  config/
```

### host

```text
com.zxplat.plugin.elep.bridge.integration.elep.remote/
  client/           ElepPrescriptionOperationsRemoteClientImpl 等
  appservice/       ElepRemoteAppServiceClient
  config/
```

---

## 5. 脚本化迁移

1. **P2 domain**：`prescription/dto/*` → `prescription/command|result`；废止 `facade/ElepPrescriptionOperations` → `access`
2. **P7/P8 application**：fork `boss-p7-p8-app.ps1` / `chs-jilin/scripts/p7-p8`
3. **P10 scheme**：按 OpCode 分包；`ElepGatewayResponse` → `scheme.share.model`
4. **每步** `mvn compile -pl zxplat-module-elep-jilin -am`
5. PowerShell **禁止 UTF-8 BOM**；`scripts/fix-bom.ps1` ✅

```text
zxplat-module-elep-jilin/scripts/
  elep-p{N}-{scope}-{action}.ps1
  fix-bom.ps1                  ✅
```

---

## 6. 检查清单

### 启动前

- [ ] cap 表与 `ElepOpCode` 枚举一致
- [ ] 确认 host Remote 仅引用 `gateway.client.remote.*`
- [ ] 记录 PDF 生成在 application（非平台 OpCode）

### 每阶段

- [ ] 脚本 + 层 README 更新
- [ ] `mvn compile -pl zxplat-module-elep-jilin -am`
- [ ] bridge 无 `application` 反向依赖
- [ ] 无 UTF-8 BOM 编译错误

### 收尾

- [ ] application 无 `orchestration/` 包段
- [ ] gateway scheme cap 与 bridge/application 一致
- [ ] whale-doc `13-实现状态` 与包名一致

---

## 7. 已落地阶段一览

| 阶段 | 状态 | 要点 |
|------|------|------|
| v3 Wave4 迁移 | ✅ | 六层、无 contracts、compile 通过 |
| Extension + Orchestrator | ✅ | `ElepPrescriptionExtensionImpl`、`ElepPrescriptionOrchestrator` |
| A0001–A0006 Invoker | ✅ | bridge `prescription/invoker/` + domain access |
| 结构优化 P0–P13 | ✅ | 本 playbook 已执行 |

---

## 8. 反模式速查

| 反模式 | 应改为 |
|--------|--------|
| domain `facade/` 对外泄漏 | `access` 端口 + bridge 实现 |
| application 长期放 Invoker | 迁 bridge `{cap}/invoker/` |
| gateway `dto/` 业务模型 | `scheme.{cap}.model` + `client.remote` |
| 为单 OpCode 建厚 Orchestrator | AppService 直调 Invoker；多步才 Workflow |
| host 复制 gateway DTO | 引用 `gateway.client.remote.*` |
| bridge 依赖 application | 共享逻辑下沉 domain |

---

## 9. 与 CHS 差异说明

| 维度 | CHS | elep-jilin |
|------|-----|------------|
| 出站切片键 | infno | **OpCode** A0001–A0006 |
| 签名 | 医保会话 | **GM 国密**（gateway session） |
| 多步链 | 挂号/结算步骤链 | A0001→PDF→A0002→A0003 |
| catalog | 医保目录 1312 等 | 国家目录只读 `yb_national_catalog_item` |
| UI | health-application | `module-elep-jilin-application` + hisproxy |

---

## 10. 参考文件

| 文件 | 用途 |
|------|------|
| [`README.md`](./README.md) | Module 总览 |
| [`../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-chs-jilin/OPTIMIZATION-PLAYBOOK.md) | 通用方法论 |
| [`../zxplat-module-einvoice-boss/OPTIMIZATION-PLAYBOOK.md`](../zxplat-module-einvoice-boss/OPTIMIZATION-PLAYBOOK.md) | serviceId 变体（无 infno） |
| [`../../docs/模块/zxplat-module-elep-jilin/13-实现状态与文档对照.md`](../../docs/模块/zxplat-module-elep-jilin/13-实现状态与文档对照.md) | 文档对照 |
| `docs/模块/zxplat-module-elep-jilin/17-zxplat-module-elep-jilin-架构-v3.md` | 架构真源 |
| `docs/模块/zxplat-module-elep-jilin/04-电子处方上传链路-详细设计.md` | A0001–A0003 链路 |
