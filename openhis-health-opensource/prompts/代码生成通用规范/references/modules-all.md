# 模块与包规范（health / Print / emr / chs / framework / project）

> 与 [`full-standards.md`](full-standards.md) 配套。
>
> **通用门禁不在本节**：注释、禁三元、Repository、批量 I/O、禁 JSON 出参、闭环执行等 **§2.1 全框架 P0** 已由 Skill + Rule [`zxplat-java-codegen.mdc`](../../../.cursor/rules/) 统一约束——**所有 zxplat 模块相同，无需按包重复文档**。
>
> **本节只写各 Maven 模块差异**（v3 六层分包、Flyway 目录、依赖红线）。

架构真源：`docs/架构/模块化开发规范.md` · v3 模板：`docs/templates/17-zxplat-module-架构-v3-template.md`。

**扩展 Module 文档目录**（`zxplat-module-{name}/`）：`zxplat-module-booking`、`zxplat-module-chs-jilin`、`zxplat-module-work-injury`、`zxplat-module-crb`、`zxplat-module-elep-jilin`、`zxplat-module-emr`、`zxplat-module-unionpay-bpc`、`zxplat-module-boc-pay`、`zxplat-module-einvoice-boss`、`zxplat-module-kiosk`、`zxplat-module-mashangfangxin`、`zxplat-module-lis-kingmed`、`zxplat-module-lis-collector`、`zxplat-module-pacs-imaging`。

---

## 通用：扩展 Module v3 六层

每个 `zxplat-module-{name}` 标准分层（**无 contracts**）：

| 层 | Maven 模块 | 职责 | 依赖要点 |
|----|------------|------|----------|
| **domain** | `{mod}-domain` | 实体、Repository、`*DomainService`、`*QueryService`、Command/Result | 不依赖 health、gateway、application |
| **gateway** | `{mod}-gateway` | 外平台传输（网关型）；领域型可省略或 NoOp | domain；**禁止** application/bridge |
| **bridge** | `{mod}-bridge` | `*ExtensionImpl`、`*IntegrationMapper`、`*Invoker`；读/写 HIS | domain [+ gateway] + **zxplat-domain** + zxplat-domain；**禁** application、health-module-support |
| **application** | `{mod}-application` | `*AppService`、Flyway、config | domain [+ gateway]；**禁** bridge |
| **starter** | `{mod}-starter` | 安装入口 | bridge + application [+ gateway + domain] |
| **host** | `{mod}-host` | gateway-proxy + Remote Client（可选） | gateway + domain；**禁** application/bridge |

**依赖红线**：`zxplat-domain` / `zxplat-application` **不依赖** 任何 `{mod}-*`。Health→Module 只经 **Extension SPI**（`zxplat-domain/integration`）。

**AppService 红线**：仅 `{mod}-application`（及 `zxplat-application`）。编排落在 bridge **ExtensionImpl / Invoker**，**禁止** `*Facade`、contracts。

**JPA 扫描（全模块）**：新增/搬迁 `@Entity` 或 `JpaRepository` 包后，必须确认本模块 `*AutoConfiguration`（或启动类）上 `@EntityScan` / `@EnableJpaRepositories` 已覆盖；白名单式模块（如 CHS）尤其易漏。Rule：`zxplat-jpa-scan.mdc`。

**remote（网关型）**：**Gateway-Only Remote** — 仅 `{mod}-gateway` 的 `*GatewayClient` 可 HTTP 出站。

**样板代码**：`zxplat-plugin/zxplat-module-chs-jilin`（CHS）、`zxplat-plugin/zxplat-module-unionpay-bpc`（银联，已 v3）。

---

## zxplat-app（HIS 核心）

### Maven 与包

| 模块 | Java 包 | 放什么 | 禁止 |
|------|---------|--------|------|
| **zxplat-domain** | `com.zxplat.domain.share.{constants,enums,utils,dto,events}` | 常量、枚举、无 Spring 工具 | Spring Bean |
| **zxplat-domain** | `com.zxplat.domain.{boundedContext}.*` | 实体、Repository、`*DomainService`、`*QueryService`、Command | 跨聚合读组装（见 B.6.4） |
| **health-module-support** | `module.common.*` | Resolver、Gate、PagedResultBuilder、跨场景 assembler | HTTP 契约、**写编排**、业务 constants |
| **health-module-support** | `module.querysupport.{域}` | `*Loader`、`*ReadAssembler`、读侧 dto | Repository、**写路径** |
| **health-module-support** | `module.orchestration.{域}` | **跨 application 子包**写编排 | 依赖 `zxplat-application`、Request/Response |
| **health-module-support** | `module.shared.*` | 诊断 JSON 解析、Whale identity 桥、OU 桥接 | 业务 IF 编排 |
| **zxplat-application** | `application.{basic,inpatient,outpatient,inventory}/**` | request/response/dto/mapper/service、**单子模块** `{IF}/{子模块}/support | 父级 `*.support；**跨子包 import `*/support**；`common`/`platform`/`querysupport/`Report 子包 |
| **zxplat-domain** | Flyway | `db/migration/health/` | SQL 进领域 Java 包 |

### health 专规（full-standards 全文适用）

- 常量：`com.zxplat.domain.share.constants`（**B.10.2.1**）
- 枚举：`domain.share.enums` + `EnumUtil`（**B.10.7–8.1**）
- 分页：`AbstractPageQuery` + `PagedResultBuilder`（**B.10.11**）
- 会话：五类 Resolver（**B.6.5**）；`adm_*` 与 `zxplat_*` 主键不混用
- busNo：`BusinessSequenceService` + `AggregateTypeConstants`（**B.10.9**）
- 模糊查询：`SearchCodeUtil.toCaseInsensitiveLikePattern`（**B.2.1**）
- QueryService 读侧：**B.15**（禁止 readmodel；禁止 App 注入 ReadRepository）
- **`support / `orchestration` 落点**：**B.6.6～B.6.3a** / Skill **§2.2.1** — 禁止父级 `*.support；**禁止** `application` 跨子包互调 `*/support；多子包写编排 → `module.orchestration.{域}`

---

## zxplat-app / print（内置 PrintTemplate）

### 目录与包

```text
zxplat-app/
  zxplat-domain/.../domain/custom/
    printtemplate/ · printbinding/ · printlog/
  zxplat-application/.../application/basic/
    printtemplate/ · printbinding/ · printlog/ · printjob/
  zxplat-domain/ → db/migration/health/   # prt_*
  zxplat-app/
```

### print 成码要点

- **AppService** 在 `zxplat-application` · `application.basic.*`。
- **领域** 在 `zxplat-domain` · `domain.custom.*`；**不**单独建 `print-*` Maven 模块。
- **读 HIS**：本期不需要 adapter（printData 由 Vue 组装）。
- **Flyway**：`zxplat-domain` → `db/migration/health/`。
- **Maven**：`zxplat-app` 依赖 `zxplat-application`（无 print-starter）。

文档真源：`docs/print/`（Java 服务端）、`docs/print-client/`（Windows 客户端 **openhis-pro-win** / **OpenHIS.Win**）。

---

## zxplat-module-booking（领域型，迁移中）

### 目录与包

```text
zxplat-module-booking/
  booking-domain/      com.zxplat.framework.booking.domain.{schedule,slot,appointment}
  booking-bridge/      com.zxplat.domain.integration.booking/*ExtensionImpl
                       com.zxplat.framework.booking.bridge/internal/
  booking-application/ com.zxplat.framework.booking.application.{*,share}
  booking-starter/     bridge + application
```

### booking 成码要点

- **挂号衔接**：`zxplat-application` 经 `OutpatientBooking*Extension`；**禁止**依赖 booking 包
- **bridge**：读/写 HIS 经 `zxplat-domain.*QueryService`/`*DomainService`；**无** contracts Reader/Writer
- **写侧**：`booking-application` → `booking-domain`
- **Profile**：`-Pwith-booking`

文档真源：`docs/zxplat-module-booking/03-详细设计与编码指导.md` · Rule：`zxplat-module-booking-codegen.mdc`。

---

## zxplat-module-emr（领域型，迁移中）

### 目录与包

```text
zxplat-module-emr/
  emr-domain/       com.zxplat.framework.emr.domain.*
  emr-application/  com.zxplat.framework.emr.application.*
  emr-bridge/       health.integration.emr/*ExtensionImpl；whale.emr.bridge/internal/
  emr-starter/
```

### emr 成码要点

- **enforcer**：`emr-bridge` 禁 `emr-application` + **`zxplat-application`**
- **Flyway**：`emr-application` → `db/migration/emr/`
- **Extension**：读 health QueryService + 写 emr-domain；**无** contracts Reader

文档真源：`docs/zxplat-module-emr/` · Rule：`zxplat-module-emr-codegen.mdc`。

---

## zxplat-module-chs-jilin（网关型，v3 已落地）

### 目录与包

```text
zxplat-module-chs-jilin/
  chs-jilin-domain/       com.zxplat.chs.domain.*
  chs-jilin-gateway/      com.zxplat.chs.gateway.{client,scheme,observability,local}
  chs-jilin-bridge/       com.zxplat.domain.integration.insurance/*ExtensionImpl
                          com.zxplat.chs.bridge.{settlement,…}/*Invoker
  chs-jilin-application/  com.zxplat.chs.application.appservice/Insurance*AppService
  chs-jilin-starter/      embedded
  chs-jilin-host/         gateway-proxy + InsuranceGatewayClientRemoteImpl
```

### chs 成码要点

- **无 contracts**；Extension 链经 bridge **Invoker**（例 `Op2601Invoker`）
- **Gateway-Only Remote**：`zxplat.chs.deployment.mode=remote` 时仅 gateway 出站
- **AppService**：`Insurance*` → `appservice/`（UI 页）；**禁止** Facade RemoteImpl
- **Flyway**：`chs-jilin-application` / starter → `db/migration/chs/`（以实际落库模块为准）
- **JPA 扫描（白名单）**：见上文「全模块」通则 + `ChsModuleDomainAutoConfiguration` / `WiChsJpaRepositoryScanOverlapTest`。Rule：`zxplat-jpa-scan.mdc`。

文档真源：`docs/zxplat-module-chs-jilin/` · 代码 README：`zxplat-plugin/zxplat-module-chs-jilin/README.md` · Rule：`zxplat-chs-jilin-codegen.mdc`、`zxplat-jpa-scan.mdc`。

---

## zxplat-module-einvoice-boss（网关型，迁移中）

```text
zxplat-module-einvoice-boss/
  einvoice-boss-domain/
  einvoice-boss-gateway/      BossGatewayClient、报文 scheme
  einvoice-boss-bridge/       BossElectronicInvoiceExtensionImpl、*Invoker
  einvoice-boss-application/  AppService、Worker
  einvoice-boss-starter/
  einvoice-boss-host/         gateway-proxy（Gateway-Only Remote）
```

- **挂接**：health 经 `BossElectronicInvoiceExtension`；**无** contracts
- **bridge**：读 health 快照、回写 Invoice 经 domain Service + Invoker 出站博思

文档真源：`docs/zxplat-module-einvoice-boss/03-详细设计与编码指导.md` · Rule：`zxplat-module-einvoice-boss-codegen.mdc`。

---

## zxplat-module-unionpay-bpc（网关型，v3 已落地）

### 目录与包

```text
zxplat-module-zxplat-module-unionpay-bpc/   unionpay-bpc-domain/       com.zxplat.framework.unionpay.bpc.domain.*
  unionpay-bpc-gateway/      BpcGatewayClient（通道 A HTTPS）
  unionpay-bpc-bridge/       ThirdPartyPaymentExtensionImpl、BpcScanInvoker、BpcQueryInvoker
  unionpay-bpc-application/  UnionPayBpcAppService
  unionpay-bpc-starter/
```

### unionpay-bpc 成码要点

- **挂接**：`ThirdPartyPaymentExtension`；bridge `@Primary`；**无** contracts
- **P0 通道**：OpenHIS.Win DLL；JVM 侧 bridge Invoker 占位，落账在 application
- **Flyway**：`unionpay-bpc-application` → `db/migration/unionpay_bpc/`
- **Profile**：`-Pwith-unionpay-bpc`

文档真源：`docs/zxplat-module-zxplat-module-unionpay-bpc/03-详细设计与编码指导.md` · Rule：`zxplat-unionpay-bpc-codegen.mdc`。

---

## zxplat-module-elep-jilin（网关型，v3 已落地）

```text
zxplat-module-elep-jilin/
  elep-jilin-domain/
  elep-jilin-gateway/      ElepGatewayClient、报文 scheme
  elep-jilin-bridge/       ElepPrescriptionExtensionImpl、*Invoker
  elep-jilin-application/  AppService
  elep-jilin-starter/
  elep-jilin-host/         gateway-proxy（Gateway-Only Remote）
```

- **挂接**：health 经 `ElepPrescriptionExtension`；**无** contracts
- **Profile**：`-Pwith-elep`

文档真源：`docs/zxplat-module-elep-jilin/` · Rule：`zxplat-module-elep-jilin-codegen.mdc`。

---

## zxplat-module-crb / crb-win（传染病）

- **Java `zxplat-module-crb` 已拆除**（2026-08-02）；**无** `-Pwith-crb`
- **运行时**：`docs/zxplat-module-crb-win/`（C# Worker + WPF）
- **规范/字段映射**：`docs/zxplat-module-crb/`（04-字段映射、参考 API）

---

## zxplat-module-boc-pay（网关型，待建）

```text
zxplat-module-boc-pay/   （待创建）
  boc-pay-domain/
  boc-pay-gateway/      ComlinkGatewayClient
  boc-pay-bridge/       ThirdPartyPaymentExtensionImpl（中行通道）
  boc-pay-application/
  boc-pay-starter/
  boc-pay-host/
```

- **挂接**：与银联 `zxplat-module-unionpay-bpc` 单卖单装；**禁止**同院捆绑
- **Profile**：`-Pwith-boc-pay`（规划）

文档真源：`docs/zxplat-module-boc-pay/` · Rule：待建。

---

## zxplat-module-work-injury（网关型）

```text
zxplat-module-work-injury/
  wi-jilin-domain/
  wi-jilin-gateway/      WorkInjuryDllGatewayClient（OpenHIS.Win 出站）
  wi-jilin-bridge/       WorkInjury*ExtensionImpl、*Invoker
  wi-jilin-application/
  wi-jilin-starter/
```

- **出站**：纯 DLL `JLSiInterface.dll` via OpenHIS.Win；**无** contracts

文档真源：`docs/zxplat-module-work-injury/` · Rule：`zxplat-chs-jilin-gs-codegen.mdc`。

---

## zxplat-app / MRMS（病案，health 内嵌子域）

```text
zxplat-app/
  zxplat-domain/mrms/           entity、service、support、model、event
  zxplat-application/mrms/      Mrms*AppService、support/emr
  zxplat-domain/        db/migration/health/（V77__、V78__ mrms）
```

**代码状态**：已迁入 health（见 `docs/应用/zxplat-application/MrmsAppService/`）。

---

## zxplat-app / PEIS（健康体检，health 子包）

### 目录与包

```text
zxplat-app/
  zxplat-domain/clinical/       cln_observation、cln_observation_definition、cln_diagnostic_report_index
  zxplat-domain/peis/             peis_* 实体、Peis*DomainService、Peis*QueryService
  zxplat-application/peis/        Peis*AppService（REG/DES/MAS/CFG/INT/REP…）
  zxplat-domain/          db/migration/health/  （PEIS DDL 与 health 同库）
  peis-adapter/                   sdpeis 兼容、第三方入站
  peis-starter/                   zxplat.modules.peis.enabled=true
```

### PEIS 成码要点

- **AppService** 在 `zxplat-application/peis`；**不**单独建 `zxplat-peis` Maven 顶层模块。
- **观察事实**：`**cln_observation**`（禁止 `peis_observation`）；LIS 投影须 `source_observation_id`。
- **INT-01**：同进程 `LisWorkItem` / `PacsWorkItem`；**无** `peis_*_project_map`。
- **「外发 LIS」** = **金域外送**（[LisKingMed](../../../zxplat-module-lis-kingmed/)），**≠** 院内不发 LIS 申请；口径见 `@PEIS-LIS-PACS文档口径` SKILL 与 `zxplat-peis-codegen.mdc`。
- **Flyway（PEIS）**：`.../db/migration/peis/V1__peis_ddl.sql`（history=`flyway_schema_history_peis`，`zxplat.modules.peis.enabled`）；**CLN/AOL** 在 health 核心 V3/V7。索引见 `docs/应用/zxplat-application/PeisAppService/06-Flyway-表结构.md`。

文档真源：`docs/应用/zxplat-application/PeisAppService/`、`docs/应用/zxplat-application/AppService/AS-PEIS-*`。

---

## zxplat-app / LIS · PACS · LisKingMed

| Module | 文档 | 代码 | 说明 |
|--------|------|------|------|
| LIS 核心 | `docs/应用/zxplat-application/LisAppService/` | 规划 | 院内 LisWorkItem；与 PEIS 同 `activity_id` |
| LIS 金域外送 | `docs/zxplat-module-lis-kingmed/` | `zxplat-plugin/zxplat-module-lis-kingmed/` 待建 | 用户口语「外发 LIS」；EXT 在 WorkItem 之后 |
| LIS 采集 | `docs/zxplat-module-lis-collector/` | `zxplat-plugin/zxplat-module-lis-collector/` 待建 | C# Agent + 动态脚本 |
| PACS 核心 | `docs/应用/zxplat-application/PacsAppService/` | 规划 | INT-01 → PacsWorkItem |
| PACS 影像 | `docs/zxplat-module-pacs-imaging/` | `zxplat-plugin/zxplat-module-pacs-imaging/` 待建 | DICOM 采集与 Web 阅片 |
| elep-jilin | `docs/zxplat-module-elep-jilin/` | `zxplat-module-elep-jilin/` ✅ | Extension + gateway Invoker |
| feeAi | `docs/zxplat-module-feeAi/` | `zxplat-module-feeAi/` 待建 | |
| CRB | `docs/zxplat-module-crb/`（文档） | `zxplat-module-crb-win/`（C#）✅；Java 已拆除 | |
| boc-pay | `docs/zxplat-module-boc-pay/` | 待建 | Comlink 中行 |
| work-injury | `docs/zxplat-module-work-injury/` | `zxplat-module-work-injury/` ✅ |
| kiosk | `docs/zxplat-module-kiosk/` | 待建 | 自助终端 |
| mashangfangxin | `docs/zxplat-module-mashangfangxin/` | 待建 | 码上放心追溯 |

均遵循六层 + starter（LIS/PACS 见定稿 §12–§12；PEIS 为 health 子包）。

---

## zxplat-framework

| 模块 | 成码相关能力 |
|------|----------------|
| **zxplat-framework** | `BusinessException`、`PagedResult`、`AbstractPageQuery`、`@Excel`、`AnnotatedExcelExporter`、`BusinessSequenceService`、软删底座 |
| **zxplat-identity** | `ICurrentUser`（**health App 禁止直注**，见 B.6.5） |
| **zxplat-web** | AppService 自动暴露 API |

**唯一闭源**：`zxplat-framework`；其余 Module 开源。

---

## zxplat-project（院点私有）

```text
zxplat-project/
  project-H22010402403/
  project-H13010800833/
  …
```

- 院点定制在 **project-H***；**不**污染 health 开源包路径。
- 可扩展 starter、覆盖配置；**禁止**让 zxplat-domain 依赖 project 模块。

---

## 跨模块对照（成码时快速查）

| 能力 | health | print | emr | chs | mrms | einvoice-boss | booking | unionpay |
|------|--------|-------|-----|-----|------|---------------|---------|----------|
| Maven 父工程 | — | — | `zxplat-module-emr` | `zxplat-module-chs-jilin` | —（内嵌） | `zxplat-module-einvoice-boss` | `zxplat-module-booking` | `zxplat-module-unionpay-bpc` |
| AppService 包 | `health.application.*` | `health.application.basic.*` | `whale.emr.application.*` | `chs.application.*` | `health.application.mrms.*` | `whale.einvoice.boss.application.*` | `whale.booking.application.*` | `whale.unionpay.bpc.application.*` |
| Flyway | zxplat-domain | zxplat-domain | emr-application | chs-jilin-application | zxplat-domain | einvoice-boss-application | booking-application | unionpay-bpc-application |
| bridge 层 | — | — | emr-bridge | chs-jilin-bridge | — | einvoice-boss-bridge | booking-bridge | unionpay-bpc-bridge |
| 读 HIS | 本域 QueryService | 无 | emr-bridge | chs-jilin-bridge | domain/mrms/support | einvoice-boss-bridge | booking-bridge | unionpay-bpc-bridge |
| contracts | ✗ | ✗ | ✗（v3） | ✗（v3） | ✗ | ✗（v3） | ✗（v3） | ✗（v3） |
| health 依赖 Extension | — | — | ✓ | ✓ | — | ✓ | ✓ | ✓ |

---

## 加载建议

| 成码场景 | 必读文件 |
|----------|----------|
| HIS 领域 | SKILL.md + full-standards B.2–B.15 + 领域层模板 |
| HIS 应用 | SKILL.md + full-standards B.6、B.10.11–12 + 应用层模板 + 本节 §health |
| Print | SKILL.md + 本节 §Print + `docs/print/` |
| Booking | SKILL.md + 本节 §booking + `zxplat-module-booking/03` |
| EMR | SKILL.md + 本节 §emr + `zxplat-module-emr/` |
| CHS | SKILL.md + 本节 §chs + `zxplat-module-chs-jilin/` |
| ELEP | SKILL.md + 本节 §elep + `zxplat-module-elep-jilin/` |
| CRB | SKILL.md + 本节 §crb + `zxplat-module-crb-win/` + 文档 `zxplat-module-crb/` |
| Work-Injury | SKILL.md + `zxplat-module-work-injury/` |
| EInvoice-Boss | SKILL.md + 本节 §einvoice-boss + `zxplat-module-einvoice-boss/03` |
| UnionPay-BPC | SKILL.md + 本节 §unionpay-bpc + `zxplat-module-zxplat-module-unionpay-bpc/03` |
| BOC-Pay | SKILL.md + `zxplat-module-boc-pay/` |
| MRMS | SKILL.md + 本节 §mrms + mrms SDD |
| PEIS | SKILL.md + `@PEIS-LIS-PACS文档口径` + `zxplat-peis-codegen.mdc` + `PeisAppService/06-Flyway` |
| 院点 | SKILL.md + 本节 §project + project 任务书 |
