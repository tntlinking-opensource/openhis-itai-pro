# chs-jilin-domain

吉林医保（CHS）**领域层** Maven 模块。承载平台语义下的实体、仓储、值对象、领域服务，以及面向应用层的 **Facade 操作接口** 与 **平台网关客户端**（`InsuranceGatewayClient`）。

> 与 health 的集成边界统一称为 **Extension**（见 [`02-Health第三方集成Extension架构.md`](../../../../whale-doc/01-架构/02-Health第三方集成Extension架构.md)）。  
> 设计真源（结算）：[`whale-doc/chs-jilin/19-结算领域模型定稿.md`](../../../../whale-doc/chs-jilin/19-结算领域模型定稿.md)  
> 接口规范：[`whale-doc/chs-jilin/参考/医疗保障信息平台定点基线版医药机构接口规范-AI.md`](../../../../whale-doc/chs-jilin/参考/医疗保障信息平台定点基线版医药机构接口规范-AI.md)

---

## 1. 模块定位

| 项 | 说明 |
|---|---|
| **Artifact** | `com.openhis:chs-jilin-domain` |
| **职责** | 医保业务模型、不变量、状态机；**不含** HTTP 报文组装、Spring MVC、HIS 主数据加载 |
| **依赖** | `whale-core`、Spring Data JPA、Fastjson2 |
| **被依赖** | `chs-jilin-application`（编排实现）、`chs-jilin-adapter`（HIS 桥接，仅引用 DTO/实体类型） |

### 分层边界

**术语约定**：与 `whale-health` 的集成统一叫 **Extension**；`health-application` 只依赖 Extension，不感知吉林模块内部结构。

**多省份策略**：一省一套独立 Maven 工程（如 `whale-chs-jilin`、`whale-chs-xxx`），各自实现同一组 Extension（`@Primary` + `openhis.modules.chs.enabled`）；health 侧接口与 DTO 保持不变。

```text
whale-health (收银/就诊)
       ↓ OutpatientChargeInsuranceExtension 等（HIS 扩展点，唯一对外概念）
chs-jilin-adapter          ← 吉林 Extension 实现 + HIS 上下文组装
       ↓ Facade 接口（ChsOutpatientSettleOperations 等）
chs-jilin-application      ← 平台报文组装、Gateway 调用、Writer 持久化
       ↓ 实现 Facade、注入 Repository
chs-jilin-domain  ← 本模块（模型 + 不变量 + 平台网关客户端）
```

`chs-jilin-contracts` 中的 `*Port` 接口为 **本工程内部** 分层用（adapter 委托 application），**不是** health 侧概念；口头与文档中不单独强调。

**本模块应包含：**

- JPA 实体与 `JpaRepository`
- 纯领域逻辑（金额校验、状态跃迁）
- 跨层稳定的 DTO / Command / Result
- `InsuranceGatewayClient`（调用国家平台的 HTTP 抽象）

**本模块不应包含：**

- `ClinicOutpatientPlatformAssembler` 等报文组装
- `ChsHttpAccessor`、SM 加解密、签到会话
- Flyway SQL（在 `chs-jilin-application/src/main/resources/db/migration/chs`）
- 对 `whale-health` 的直接依赖

---

## 2. 包结构

根包：`com.openhis.chs`

```text
com.openhis.chs
├── api                          # 平台网关客户端（HTTP 抽象，非 HIS Extension）
│   ├── InsuranceGatewayClient   # invoke / signIn / 9101·9102 文件
│   ├── ChsInfNo                 # infno 常量（1101、2207、2601…）
│   ├── InvokeContext / ResultBody
│   ├── dto/                     # 网关入参/出参 DTO（目录、签到等）
│   └── support/                 # 网关装饰器（ObservingInsuranceGatewayClient）
│
└── domain
    ├── ChsErrorCodes            # INS-C01…C08
    ├── support/                 # 模块级工具（ChsTextSupport 等）
    │
    ├── settlement/              # ★ 结算子域（门诊/住院共用）
    │   ├── entity/              # yb_settlement_* 凭证与审计
    │   ├── repository/
    │   ├── model/               # 值对象：ChrgBchno、SettlementAmounts、ChargeBatch
    │   ├── service/             # ChargeBatchLifecycle（状态机）
    │   ├── support/             # 2204/2301 result 解析、追溯码汇总
    │   ├── dto/                 # SettlementCorrectionCommand（2601）
    │   ├── PreSettleSnapshot    # 2206/2303 报价统一只读接口
    │   └── SettlementAmountValidator
    │
    ├── outpatient/              # 门诊结算
    │   ├── entity/              # yb_clinic_pre_settle、yb_clinic_fee_upload、yb_person_cache…
    │   ├── dto/                 # OpSettleContext、OpConfirmSettleCommand…
    │   ├── facade/              # ChsOutpatientSettleOperations
    │   └── repository/
    │
    ├── inpatient/               # 住院结算
    │   ├── entity/              # yb_inpatient_pre_settle、yb_inpatient_fee_upload
    │   ├── dto/                 # IpSettleContext、IpConfirmSettleCommand…
    │   ├── facade/              # ChsInpatientSettleOperations
    │   └── repository/
    │
    ├── gateway/                 # 调用审计实体
    │   └── entity/              # yb_invoke_log
    │
    ├── catalog/                 # 国家目录 1301/1312 等
    ├── inventory/               # 进销存 3501–3513
    ├── reconciliation/          # 对账 3201/3202
    ├── medicalinfo/             # 就诊/结算信息查询 5201–5206
    └── session/                 # 9001 签到凭证 yb_sign_session
```

### 设计理由（简）

1. **`api` vs `domain`**：`api` 是出站 **HTTP 契约**（`InsuranceGatewayClient`），实现在 application；`domain` 是 **业务模型**（实体、不变量、Facade），不依赖 Web 与 health。
2. **按 infno 能力分域**：`catalog`、`inventory`、`reconciliation` 等与结算生命周期解耦，改目录/进销存不牵动 `yb_settlement_*`。
3. **`settlement` 横切**：2207/2304 产出同构凭证（`yb_settlement_record`），状态机与金额校验门诊/住院共用；平台语义见 [19-结算领域模型定稿](../../../../whale-doc/chs-jilin/19-结算领域模型定稿.md)。
4. **`outpatient` / `inpatient` 竖切**：22xx 与 23xx/24xx 编排链、预结算/费用表不同，各自保留 `Op*` / `Ip*` DTO 与 Facade。
5. **能力包内四件套**：`entity` → `repository` → `dto` → **`facade`**。Facade 供 adapter 的 Extension 实现依赖；application 实现 Facade，满足 **adapter 禁止依赖 application** 的 Enforcer。
6. **`gateway` 实体**：`yb_invoke_log` 存全报文审计，与 `api` 里的网关客户端（行为）分离；2601 冲正通过 `msgid` 关联。

---

## 3. 结算子域（核心）

### 3.1 设计原则（Wave 1）

1. **health 管钱、CHS 管证**：`fin_payment_*` 在 health；`setl_id`、基金分项、2204 分割结果在 CHS。
2. **报价与凭证分离**：2206/2303 → `yb_*_pre_settle`；2207/2304 → `yb_settlement_record`。
3. **编排键**：
   - 门诊：`chrg_bchno`（≤30 字符，同批病种一致）
   - 住院：`IP{preSettleId}`（`PreSettleSnapshot.resolveBatchKey()`）
4. **不引入协同单**：Wave 1 无 `yb_coordination`。

### 3.2 平台身份键 → 值对象

| 规范字段 | 值对象 | 说明 |
|----------|--------|------|
| `chrg_bchno` | `ChrgBchno` | 收费批次号 |
| `mdtrt_id` | `PlatformMdtrtId` | 就诊 ID |
| `feedetl_sn` | `FeedetlSn` | 费用明细流水号 |
| `setl_id` | `PlatformSetlId` | 结算凭证 ID |
| — | `ChargeBatch` | 批次组合视图（chrgBchno + mdtrtId + preSettleId） |
| — | `SettlementAmounts` | setlinfo 金额组 + 恒等式校验 |

### 3.3 状态机（`ChargeBatchLifecycle`）

领域服务，**只改内存状态、不持久化**；Application 层 Writer 在 `save` 前调用。

```text
FeeDetailLine（yb_*_fee_upload.upload_status）
  UPLOADED ──2205/2302──► REVOKED
  UPLOADED ──2207/2304──► SETTLED

PreSettleQuote（yb_*_pre_settle.status）
  ACTIVE ──2207/2304──► SETTLED
  ACTIVE ──2205/失败──► CANCELLED
  SETTLED ──2208/2305/2601──► REVOKED

SettlementVoucher（yb_settlement_record.status）
  CONFIRMED ──2208/2305/2601──► REVOKED（写 yb_settlement_reversal）
```

非法跃迁抛出 `SettlementInvariantException`（错误码 `INS-C08`）。

### 3.4 金额不变量（`SettlementAmountValidator`）

- `medfee_sumamt = fund_pay_sumamt + acct_pay + psn_cash_pay`
- 2207/2304 须透传 2206/2303 的四个字段：`fulamt_ownpay_amt`、`overlmt_selfpay`、`preselfpay_amt`、`inscp_scp_amt`

### 3.5 主要实体与表

| 实体 | 表 | 用途 |
|------|-----|------|
| `YbClinicPreSettle` | `yb_clinic_pre_settle` | 门诊 2206 报价快照 |
| `YbInpatientPreSettle` | `yb_inpatient_pre_settle` | 住院 2303 报价快照 |
| `YbClinicFeeUpload` | `yb_clinic_fee_upload` | 门诊 2204 行 + 分割结果 |
| `YbInpatientFeeUpload` | `yb_inpatient_fee_upload` | 住院 2301 行 + 分割结果 |
| `YbSettlementRecord` | `yb_settlement_record` | 2207/2304 正式结算凭证（AMB/IMP 共用） |
| `YbSettlementFundDetail` | `yb_settlement_fund_detail` | setldetail 基金分项 |
| `YbSettlementReversal` | `yb_settlement_reversal` | 2208/2305 撤销反向凭证 |
| `YbSettlementCorrection` | `yb_settlement_correction` | 2601 冲正审计 |
| `YbInvokeLog` | `yb_invoke_log` | 全 infno 请求/响应审计 |

`YbClinicPreSettle` / `YbInpatientPreSettle` 均实现 `PreSettleSnapshot`，供住院/门诊共用校验与 `ChargeBatch.fromPreSettle()`。

---

## 4. 门诊 / 住院 Facade

`chs-jilin-adapter` 中的 **Extension 实现类**（如 `OutpatientChargeInsuranceExtensionImpl`）加载 HIS 上下文后，委托本节的 Facade 完成平台编排。Application 类对 adapter **不可见**（Enforcer 禁止 adapter 依赖 application）。

### `ChsOutpatientSettleOperations`

| 方法 | 平台交易 | 说明 |
|------|----------|------|
| `preSettle` | 2203→2204→2206 | 预结算 |
| `confirmSettle` | 2207 | 正式结算 |
| `revokeSettle` | 2208 | 撤销正式结算 |
| `revokeFeeDetails` | 2205 | 撤销未结算费用 |
| `correctTransaction` | 2601 | 冲正原交易 |
| `getPersonInfo` | 1101 | 读卡（含当日缓存） |

上下文：`OpSettleContext`（契约、就诊、收费行、读卡字段、追溯码数量等）。

### `ChsInpatientSettleOperations`

| 方法 | 平台交易 | 说明 |
|------|----------|------|
| `ensureAdmission` | 2401 | 入院登记 |
| `preSettle` | 2301→(2402)→2303 | 预结算 |
| `confirmSettle` | 2304 | 正式结算 |
| `revokeSettle` | 2305 | 撤销正式结算 |
| `revokeFeeDetails` | 2302 | 撤销费用明细 |
| `revokeAdmission` / `revokeDischarge` | 2404 / 2405 | 入出院撤销 |
| `changeInpatientInfo` | 2403 | 住院信息变更 |

上下文：`IpSettleContext`（含 `settleMode`：`DISCHARGE_FULL` / `MID_SETTLE`）。

---

## 5. 平台网关客户端（`com.openhis.chs.api`）

与上一节的 **Extension** 不同：`InsuranceGatewayClient` 是 CHS 模块内部调用**国家医保平台 HTTP** 的抽象，health 不直接接触。

```java
public interface InsuranceGatewayClient {
    ResultBody invoke(String contractBusNo, String infNo, Object input, InvokeContext context);
    String signIn(String contractBusNo, InvokeContext context);
    byte[] downloadFile(...);   // 9102
    ChsFileUploadResult uploadFile(...);  // 9101
}
```

- **`contractBusNo`**：对应 `fin_contract.bus_no`，关联 `chs-contract` YAML 配置。
- **`ChsInfNo`**：infno 常量，避免魔法字符串。
- **实现位置**：`chs-jilin-application` 的 `PersistingInsuranceGatewayClient`（落 `yb_invoke_log`）。

装饰器 `ObservingInsuranceGatewayClient` 可用于测试或链路观测。

---

## 6. 其他业务域 Facade（概要）

| Facade | 包 | 职责 |
|--------|-----|------|
| `ChsNationalCatalogOperations` | `catalog` | 1301 目录下载与入库 |
| `ChsCatalogMappingOperations` | `catalog` | 院内对照 |
| `ChsSessionOperations` | `catalog` | 9001 签到 |
| `ChsInventoryOperations` | `inventory` | 3501–3513 进销存 |
| `ChsReconciliationOperations` | `reconciliation` | 3201/3202 对账 |
| `ChsMedicalInfoQueryOperations` | `medicalinfo` | 5201–5206 信息查询 |
| `ChsGatewayOperations` | `gateway` | 网关运维（重试、日志查询等） |

---

## 7. 错误码（`ChsErrorCodes`）

| 码 | 常量 | 场景 |
|----|------|------|
| INS-C01 | `CONTRACT_INVALID` | 契约配置非法 |
| INS-C02 | `CONTRACT_NOT_FOUND` | 费用性质不存在 |
| INS-C03 | `PLATFORM_FAILED` | 平台 infcode 非 0 |
| INS-C04 | `FILE_PARSE_FAILED` | 目录/文件解析失败 |
| INS-C05 | `MAPPING_FAILED` | 对照失败 |
| INS-C06 | `SIGN_IN_FAILED` | 9001 签到失败 |
| INS-C07 | `SETTLEMENT_AMBIGUOUS` | 同一 payment 多笔结算无法唯一定位 |
| INS-C08 | `SETTLEMENT_INVARIANT` | 状态机/金额恒等式/2207 透传违反 |

health 侧扩展错误（如 INS-013 补偿失败）定义在 `health-integration-contracts`，不在本模块。

---

## 8. 扩展指南

### 8.1 新增结算相关字段

1. Flyway 脚本写在 **application** 模块 `db/migration/chs/V*.sql`。
2. 实体字段加在对应 `entity` 类（优先 `settlement` 共用表）。
3. 若涉及状态：在 `ChargeBatchLifecycle` 增加跃迁方法，并补 `ChargeBatchLifecycleTest`。
4. 若涉及金额：扩展 `SettlementAmounts` / `SettlementAmountValidator`。

### 8.2 新增 infno 编排

1. `ChsInfNo` 增加常量。
2. 在对应 `facade` 接口增加方法；Command/Result 放在 `dto` 包。
3. 报文组装、Gateway 调用、Writer 落在 **application** 层。
4. HIS 入口：在 **adapter** 的 Extension 实现中增加方法，经 Facade 编排；health 只改 `health-integration-contracts` 的 Extension 签名（若需新能力）。

### 8.3 新增实体

- 继承 `BaseAuditedEntity`（含 `tenant_id`、软删审计列）。
- Repository 接口放同域 `repository` 包，命名 `YbXxxRepository`。
- 避免在实体中注入 Spring Bean；领域逻辑用无状态的 `@Service`（如 `ChargeBatchLifecycle`）。

---

## 9. 测试

```bash
cd whale-sever/whale-chs-jilin/chs-jilin-domain
mvn test
```

| 测试类 | 覆盖 |
|--------|------|
| `ChargeBatchLifecycleTest` | 预结算/费用行状态跃迁 |
| `SettlementAmountValidatorTest` | 金额恒等式、2207 透传 |
| `ChargeBatchTest` | 批次键组合 |
| `YbNationalCatalogDomainServiceTest` 等 | 目录域 |

领域测试不启动 Spring 容器（除带 `@SpringBootTest` 的集成测试）；`ChargeBatchLifecycle` 可直接 `new` 使用。

---

## 10. 相关模块速查

| 模块 | 关系 |
|------|------|
| `health-integration-contracts` | **Extension** 接口（health 唯一感知的医保集成点） |
| `chs-jilin-adapter` | 吉林 **Extension** 实现（`@Primary`）+ HIS 上下文加载 |
| `chs-jilin-contracts` | 本工程内部 DTO/委托接口（实现细节，文档统称 Extension 链路） |
| `chs-jilin-application` | 实现 Facade、Gateway、Writer、Assembler |
| `chs-jilin-domain` | 本模块 |
| `whale-doc/chs-jilin/` | 概要设计、接口说明、领域定稿 |

其他省份：新建 `whale-chs-{province}` 工程，实现同一组 Extension，health 无需改动。

---

*文档版本：2026-06-20 · 对齐结算领域模型定稿 v1.3 · Extension 统一术语*
