# ZXPlat 医保多方案扩展骨架

> **架构真源**：[01-概要设计.md](./01-概要设计.md) · [13-实现状态与文档对照.md](./13-实现状态与文档对照.md)

> **文档性质**：项目结构规划 — 吉林省医保首期落地 + 未来省医保 / 工伤 / 市医保扩展  
> **关联文档**：[02-架构设计](./02-Whale吉林省医保模块-架构设计.md) | [03-概要设计](./05-Whale吉林省医保模块-概要设计.md)  
> **版本**：v1.0 | **日期**：2026-05-28

---

## 1. 设计目标

| 目标 | 说明 |
|------|------|
| **首期可交付** | 吉林省国家平台（FSI + 320xA + extData）按本骨架一次建对 |
| **扩展不加乱** | 新增河南省医保、工伤联网等 = **新增 scheme 包/模块**，不改协同单/Worker 主循环 |
| **业务无感 infno** | 收费/出院 AppService 只认 `contractId` + `account`，不 if-else 吉林/工伤 |
| **配置驱动** | `fin_contract` + `chs-contract.properties` 决定走哪套 scheme |
| **网关可复用** | 同一 `InsuranceGatewayClient`；不同 scheme 可共用或分平台 URL |

---

## 2. 核心概念：三层标识

```text
┌─────────────────────────────────────────────────────────────┐
│ ContractCategory（费用性质 · 业务层 · 已有）                  │
│   SELF_CHARGE | PROVINCIAL_INSURANCE | MUNICIPAL_INSURANCE  │
│   | WORKER_INSURANCE（工伤/职工医保等业务归类）               │
└───────────────────────────┬─────────────────────────────────┘
                            │ fin_contract 一行
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ InsuranceSchemeCode（对接方案 · 技术路由 · 新增）            │
│   CHS_NATIONAL_JILIN | CHS_NATIONAL_HEBEI | WORK_INJURY_* …  │
└───────────────────────────┬─────────────────────────────────┘
                            │ chs-contract.properties
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ PlatformProfile（平台连接 · 网关层 · ContractConfig）         │
│   base_url、fixmedins_code、国密密钥、OAuth、adm_vs          │
└─────────────────────────────────────────────────────────────┘
```

### 2.1 为何需要 InsuranceSchemeCode

`ContractCategory.PROVINCIAL_INSURANCE` 无法区分 **吉林省** 与 **河南省**（接口大体相同，但 extData、320xA、区划规则不同）。  
`WORKER_INSURANCE` 与 **省医保** 往往 **不同平台 URL、不同报文或不同 infno 子集**。

因此增加 **InsuranceSchemeCode**（对接方案码），由 **`fin_contract.insurance_scheme_code`**（或通过 config 映射）绑定。

### 2.2 InsuranceSchemeCode 枚举（建议）

```java
// health-domain-shared / domain.share.enums.insurance
public enum InsuranceSchemeCode {
    /** 国家平台 · 吉林省本地化（首期） */
    CHS_NATIONAL_JILIN,
    /** 国家平台 · 其他省（占位，结构同吉林，换本地化包） */
    CHS_NATIONAL_GENERIC,
    /** 工伤联网（独立平台/接口子集，二期） */
    WORK_INJURY,
    /** 市级医保差异方案（若与省平台不同 endpoint） */
    CHS_MUNICIPAL,
    /** 未对接，仅院内账户 */
    NONE
}
```

### 2.3 与现有 ContractCategory 映射（默认）

| ContractCategory | 默认 InsuranceSchemeCode | 说明 |
|------------------|--------------------------|------|
| `SELF_CHARGE` | `NONE` | 不走 insurance 编排 |
| `PROVINCIAL_INSURANCE` | `CHS_NATIONAL_JILIN`（可 per-contract 覆盖） | 首期吉林 |
| `MUNICIPAL_INSURANCE` | `CHS_MUNICIPAL` 或同省 scheme | 按院方配置 |
| `WORKER_INSURANCE` | `WORK_INJURY` | 工伤专用插件 |

**映射来源（优先级从高到低）：**

1. `fin_contract.insurance_scheme_code`（列扩展，推荐）  
2. `chs-contract.properties` → `contract.{id}.scheme=CHS_NATIONAL_JILIN`  
3. `ContractCategory` 默认表（上表）

---

## 3. 总体项目骨架

### 3.1 仓库与 Maven 结构（推荐：单仓分包，二期可拆模块）

**首期（Wave 1–3）**：不增加 Maven 子模块数量，用 **包隔离** 降低成本。  
**多省并行时**：将 `scheme/jilin` 抽为 `chs-scheme-jilin` 独立 artifact。

```text
zxplat-module-chs-jilin/                          # 已收敛为六层 + starter；下为 Wave 1 规划骨架
├── pom.xml
├── chs-api/                              # 对外依赖面（health 只依赖此 jar）
│   └── com.zxplat.plugin.chs.api/
│       ├── core/                         # ★ 稳定内核（所有方案共用）
│       │   InsuranceGatewayClient.java
│       │   InvokeContext.java
│       │   ResultBody.java
│       │   InsuranceSchemeCode.java      # 或放 health-domain-shared
│       │   ChsInvokeException.java
│       ├── dto/national/                 # ★ 国家基线 DTO（2207/2305/1101…）
│       │   person/
│       │   outpatient/
│       │   inpatient/
│       │   recon/
│       └── dto/scheme/jilin/             # ★ 吉林增量（不污染 national）
│           JilinExtDataDto.java
│           Clear3203AInput.java
│           ...
│       └── dto/scheme/workinjury/        # 占位 package-info.java
│
├── chs-impl/
│   └── com.zxplat.plugin.chs.impl/
│       ├── core/                         # ★ 通用网关（所有 scheme 共用）
│       │   client/ChsHttpAccessor.java
│       │   client/InsuranceGatewayClientLocalImpl.java
│       │   client/remote/*
│       │   config/ContractConfig.java    # + scheme 字段
│       │   session/InsuranceSignSessionService.java
│       │   crypto/ChsSmCryptoService.java
│       │   support/TransBody.java
│       └── scheme/jilin/                 # 吉林协议适配（可选薄层）
│           JilinInsuplcAdmdvsRule.java   # 3203A 等特殊区划规则
│           JilinInfNoConstants.java
│       └── scheme/workinjury/            # 二期
│
└── docs/
    ├── scheme/jilin/                     # 吉林对接说明（链到 apis/320xA）
    └── scheme/workinjury/                # 占位

zxplat-core/
├── health-domain-shared/
│   └── enums/insurance/
│       InsuranceSchemeCode.java
│       YbCoordinationBizType.java        # 方案无关
│       YbCoordinationState.java
│
├── health-domain/
│   └── insurance/
│       coordination/                     # ★ 方案无关（所有医保/工伤共用）
│       scheme/                           # 可选：scheme 级配置实体
│
└── health-application/
    └── insurance/
        ├── core/                         # ★ 方案无关内核
        │   coordination/                 # Worker、DomainService 委托
        │   context/InsuranceSettlementContext.java
        │   precheck/InsurancePrecheckService.java  # 接口 + 链式规则
        │   facade/InsuranceSettlementFacade.java # 按 scheme 路由
        │   ops/InsuranceCoordinationOpsAppService.java
        │   session/InsuranceSessionAppService.java
        │   person/InsurancePersonAppService.java   # 委托给 scheme
        │   spi/InsuranceSchemePlugin.java        # ★ 扩展点
        │   spi/InsuranceSchemeRegistry.java
        │
        └── scheme/
            ├── jilin/                    # ★ 吉林省实现（首期主代码）
            │   JilinInsuranceSchemePlugin.java
            │   assembler/
            │   executor/OpSettleCoordinationExecutor.java
            │   precheck/JilinCatalogPrecheckRule.java
            │   localization/JilinExtDataBuilder.java
            │
            └── workinjury/               # 二期占位
                package-info.java
                WorkInjuryInsuranceSchemePlugin.java（stub）
```

### 3.2 依赖规则

```text
health-application
  → chs-api（仅 core + dto，不依赖 chs-impl 源码）
  → health-domain

health-app
  → chs-impl

chs-impl
  → chs-api
  → 不依赖 health-*

scheme/jilin 包
  → 可依赖 chs-api.dto.scheme.jilin + chs-api.dto.national
  → 不可依赖 scheme/workinjury

scheme/workinjury 包
  → 独立，禁止 import scheme.jilin
```

---

## 4. 扩展点：InsuranceSchemePlugin

所有 **省别/工伤差异** 通过 SPI 插件注入，**协同单、Worker、运维台** 保持一份。

```java
/**
 * 医保/工伤对接方案插件（每个 InsuranceSchemeCode 一个实现）。
 */
public interface InsuranceSchemePlugin {

    InsuranceSchemeCode schemeCode();

    /** 是否处理该 contract（scheme + category 校验） */
    boolean supports(Long contractId, ContractDetailQueryModel contract);

    /** 读卡/1101 等人员信息 */
    YbPersonInfoDto getPersonInfo(QueryYbPersonRequest req, InvokeContext ctx);

    /** 结算前校验（未对照阻断等；可叠加通用规则） */
    void precheckSettle(InsuranceSettlementContext context);

    /** 返回该场景的平台调用步骤链（infno 顺序） */
    List<CoordinationStepPlan> planSteps(YbCoordinationBizType bizType, InsuranceSettlementContext ctx);

    /** 组装某步 input（Loader 已填充 context） */
    Object assembleInput(String infno, InsuranceSettlementContext ctx, YbCoordinationStep priorStep);

    /** 解析 output → 写回 context / settlement record */
    void absorbOutput(String infno, ResultBody body, InsuranceSettlementContext ctx);

    /** 吉林 3203A 等仅部分 scheme 实现的运维能力；默认 unsupported */
    default boolean supportsReconciliation(String infno) { return false; }
}
```

**注册：**

```java
@Component
public class InsuranceSchemeRegistry {
    private final Map<InsuranceSchemeCode, InsuranceSchemePlugin> plugins;

    public InsuranceSchemePlugin require(Long contractId) {
        InsuranceSchemeCode code = contractSchemeResolver.resolve(contractId);
        return plugins.get(code);
    }
}
```

**门面（业务唯一入口）：**

```java
@Service
public class InsuranceSettlementFacade {
    public StartSettleResult startOutpatientSettle(StartOpInsuranceSettleRequest req) {
        var plugin = registry.require(req.getContractId());
        // core：拆单、建协同单、SETTLING — 与 scheme 无关
        // plugin：precheck + planSteps
    }
}
```

---

## 5. 吉林省方案（CHS_NATIONAL_JILIN）结构详图

```text
InsuranceSettlementFacade
    → InsuranceSchemeRegistry → JilinInsuranceSchemePlugin
        │
        ├─ precheck
        │    JilinCatalogPrecheckRule      # yb_match_flag 阻断
        │    JilinDiagnosisPrecheckRule
        │
        ├─ planSteps(OP_SETTLE)
        │    [2201?, 2203?, 2204, 2206, 2207]  # 按院方开关配置链
        │
        ├─ assembler
        │    JilinOp2204Assembler
        │    JilinOp2206Assembler
        │    JilinOp2207Assembler + JilinExtDataBuilder
        │
        ├─ executor (注册到 core Worker)
        │    JilinOpSettleCoordinationExecutor implements YbCoordinationStepExecutor
        │
        └─ recon/list
             JilinRecon3201Executor
             JilinClear3203AExecutor      # 吉林 A 变体
             JilinSettlementList4101Executor
```

### 5.1 吉林专用资产清单

| 类型 | 位置 | 内容 |
|------|------|------|
| 接口文档 | `apis/**` + `scheme/jilin/README.md` | 320xA、extData 索引 |
| DTO | `chs-api/.../dto/scheme/jilin/` | extData、3203A Input |
| 规则 | `impl/scheme/jilin/JilinInsuplcAdmdvsRule` | insuplc 特殊 infno |
| 组装 | `application/insurance/scheme/jilin/assembler/` | 2204/2207 + extData |
| 配置 | `contract.{id}.scheme=CHS_NATIONAL_JILIN` | base_url=jlhs |

### 5.2 国家基线 vs 吉林增量

| 层次 | 国家基线（多省复用） | 吉林增量 |
|------|----------------------|----------|
| DTO | `dto/national/outpatient/OpSettle2207Input` | `dto/scheme/jilin/JilinExtDataDto` 嵌入 extData |
| infno | 2207、3201、4101 | 3203A–3209A |
| Precheck | 通用：对照、诊断非空 | 吉林 extData 必填项 |
| 网关 | `ChsHttpAccessor` | 同 accessor，仅 ContractConfig 不同 |

**原则：** 新增「河南省医保」时 **复制 scheme/jilin 目录为 scheme/henan**，改本地化包；**national DTO 与 core 不动**。

---

## 6. 工伤方案（WORK_INJURY）预留

工伤与省医保常见差异：

| 维度 | 省医保（CHS） | 工伤（典型） |
|------|---------------|--------------|
| 平台 URL | 省 FSI（jlhs） | 人社/工伤专网，可能不同 |
| 报文规范 | 国家医保 1.6.8 | 可能非同一套 infno |
| 结算链 | 2207 / 2305 | 独立接口或子集 |
| 账户 | `PROVINCIAL_INSURANCE` account | `WORKER_INSURANCE` account |
| 协同单 | 同一 `yb_coordination` 表 | **共用**，`scheme_code=WORK_INJURY` |

**骨架预留：**

```text
scheme/workinjury/
  WorkInjuryInsuranceSchemePlugin.java
  assembler/
  executor/
  dto/   # 若与国家 DTO 不兼容，全部放 scheme 包，勿改 national
```

**ContractCategory.WORKER_INSURANCE** → `InsuranceSchemeCode.WORK_INJURY` → 独立 `contract.{id}.base_url`。

---

## 7. 协同单与表结构扩展

在 `yb_coordination` 增加（概要设计 DDL 补充）：

```sql
scheme_code VARCHAR(32) NOT NULL DEFAULT 'CHS_NATIONAL_JILIN',
```

- **biz_type** 保持方案无关（`OP_SETTLE` 不写成 `JILIN_OP_SETTLE`）。  
- 运维筛选：`WHERE scheme_code = 'CHS_NATIONAL_JILIN'`。  
- 唯一约束不变：仍 `(payment_id, account_id, biz_type)`。

`fin_contract` 扩展（Flyway）：

```sql
ALTER TABLE fin_contract ADD COLUMN insurance_scheme_code VARCHAR(32);
-- 空则走 ContractCategory 默认映射
```

---

## 8. 配置约定

### 8.1 chs-contract.properties

```properties
# 吉林省省医保
contract.1.scheme=CHS_NATIONAL_JILIN
contract.1.base_url=http://ddjk.jlhs.gov.cn:20215
contract.1.fixmedins_code=H22010402403
contract.1.adm_vs=229900
# ...

# 工伤（二期示例）
contract.10.scheme=WORK_INJURY
contract.10.base_url=https://work-injury.example.gov.cn
contract.10.fixmedins_code=...
```

### 8.2 步骤链配置（可选 YAML）

```yaml
insurance:
  schemes:
    CHS_NATIONAL_JILIN:
      op-settle-steps: [2204, 2206, 2207]
      ip-settle-steps: [2301, 2303, 2305]
      recon-clear-infno: 3203A
    WORK_INJURY:
      op-settle-steps: [WJ_SETTLE_01]   # 工伤自有编码，映射在 plugin 内
```

---

## 9. 新增一种医保/工伤的 Checklist

| 步骤 | 动作 | 是否改 core |
|------|------|-------------|
| 1 | 新增 `InsuranceSchemeCode` 枚举值 | shared 枚举 |
| 2 | `chs-api/dto/scheme/{name}/` 增量 DTO | 否 |
| 3 | `application/insurance/scheme/{name}/` 实现 `InsuranceSchemePlugin` | 否 |
| 4 | 注册 `*CoordinationExecutor`（或 plugin 内 factory） | 否 |
| 5 | `scheme/{name}/` 对接说明 | 否 |
| 6 | `chs-contract.properties` 增加 contract 段 | 否 |
| 7 | `fin_contract` 行 + `insurance_scheme_code` | 数据 |
| 8 | 单测：Mock Gateway + Plugin 契约测试 | 否 |

**不应修改：** `YbCoordinationWorker` 主循环、`InsuranceCoordinationOpsAppService`、协同单状态机。

---

## 10. 与现有文档的衔接

| 文档 | 调整 |
|------|------|
| **02 架构设计** | 增加 §「多方案扩展」指向本文 |
| **03 概要设计** | §3 包规划改为 `core` + `scheme/jilin` 结构；DDL 增加 `scheme_code` |
| **apis/** | 国家接口保持；吉林索引导 `scheme/jilin/` |

---

## 11. 首期实施建议（吉林省）

**只建一套 core + 一套 scheme/jilin**，但 **目录与 SPI 一次到位**：

1. Wave 1：`core`（协同单、Worker、Facade、Registry）+ `scheme/jilin`（2207 链）  
2. 不在代码中出现 `if (jilin)`；一律 `registry.require(contractId)`  
3. `scheme/workinjury` 仅 `package-info.java` + 空 Plugin 抛 `UnsupportedOperationException`（可选，便于 Spring 扫描结构稳定）  
4. DTO：`national` 放 2207 基线字段；`JilinExtDataDto` 仅吉林包  

---

## 12. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-05-28 | 多方案扩展骨架初版 |
