# DM-ADM-009 Account 就诊账户

> **限界上下文**：财务域 · 就诊账户管理  
> **聚合类型**：单表聚合  
> **版本**：v1.3

---

## 1. 概述

### 1.1 业务定义

就诊账户（Account）是围绕**患者 + 就诊**建立的财务载体，记录账户开立、余额变更与结算状态。一个就诊下可存在多行并行账户（**默认个人现金户** `AccountTypeCode.PERSONAL_CASH_ACCOUNT`、医保并行户 `MEDICAL_INSURANCE` 等），供收费、结算、支付主单引用稳定的 `accountId`。

**开户口径（门诊挂号 / 住院登记同构）**：人人必开个人现金户，其 `contract_no` 为**自费**费用性质；省/市医保或工伤挂号/入院时**另开**医保户，其 `contract_no` 为所选医保/工伤合同。挂号费是否挂医保户由「医保支付挂号费」决定；诊间账单挂账真源为 `ChargeItem.account_id`。费用性质展示与统计跟账户 `contract_no`，**不**用 `type_code` 充当业务身份。历史仅单户且现金户误挂医保合同的数据不强制修正。

### 1.2 核心职责

| 职责 | 说明 |
|---|---|
| 多户开立 | 按「患者 + 就诊」维度维护多条账户行（默认个人现金、医保并行户等；`type_code` 见 **§4.3 `AccountTypeCode`**） |
| 余额管理 | 管理患者在院期间的账户余额（预交金充退、费用入账扣减） |
| 结账状态追踪 | 通过 `billingStatusEnum` 表达可用/已出院未结账/已结账/坏账等结账阶段 |
| 收费引用锚点 | 为 ChargeItem、PaymentReconciliation 等提供稳定的 `accountId` 引用 |

### 1.3 适用范围

| 类型 | 说明 |
|---|---|
| 门诊就诊 | 预结算开立个人现金自费户；医保/工伤费用性质另开 **`MEDICAL_INSURANCE`**；挂号费按 `ybRegisterFee` 挂户 |
| 住院就诊 | 入院同构开户（预交金进个人现金户）；出院结算时关账 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表

聚合根对应单表，聚合边界以主表主键 `id`（账户标识）划分。

**表结构**：
- 主表：`adm_account`（主键：`id`）
- 索引：`patient_id`（患者）、`encounter_id`（就诊）、`type_code`（账户类型）、`status_enum`（状态）、`billing_status_enum`（结算状态）

**高频查询模式**：
- 按账户标识查询：主表主键查询
- 按患者查询：`patient_id` 索引扫描
- 按就诊查询：`encounter_id` 索引扫描
- 按账户类型筛选：`type_code` 索引扫描
- 按状态筛选：`status_enum` 索引扫描
- 按结算状态筛选：`billing_status_enum` 索引扫描

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|---|---|---|
| 账户标识 | `Long` | 聚合根主键，雪花 ID |

### 2.3 状态机

无独立账户状态机。账户主状态（`statusEnum`）由写侧命令写入，不以状态机驱动流转；结账状态（`billingStatusEnum`）反映账单结算阶段。

```
  [ACTIVE(有效)] ──账单关闭──> [INACTIVE(无效)]
       │
       └──错误录入──> [ERROR(错误)]
```

#### 状态定义（accountStatus）

| value | code | info | 语义 |
| ----- | ---- | ---- | ---- |
| 1 | ACTIVE | 有效 | 可作为收费/支付引用的有效账户行，允许余额变更、关账 |
| 2 | INACTIVE | 无效 | 账户已失效/停用 | — |
| 3 | ERROR | 错误 | 误录入 | — |
| 4 | ON_HOLD | 冻结 | 账户暂挂/冻结 | — |
| 5 | UNKNOWN | 未知 | 状态未知 | — |

> 非 `ACTIVE` 行是否参与 `saveOrUpdateAccount` 判重：以本稿 **§6.1** 与持久化唯一口径为准；并行多户下须谨慎使用 upsert。

---

## 3. 实体

```text
Account（聚合根，单表聚合）
════════════════════════════════════════════════════════════

  ┌──────────────────────────────────────────────────────┐
  │                     Account                          │
  │                                                      │
  │  - patientId / encounterId                            │
  │  - typeCode / contractNo / encounterFlag              │
  │  - balanceAmount / limitAccount                       │
  │  - statusEnum / billingStatusEnum                     │
  │  - no / name / ybAreaNo / insutype                    │
  └──────────────────────────────────────────────────────┘
```

### 3.1 就诊账户（Account）

Account 聚合根的属性定义，对应 `adm_account` 表。

| 属性 | 字段名 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| 账户标识 | `id` | `Long` | 是 | 聚合根主键，雪花 ID |
| 账户主状态 | `status_enum` | `String`（`AccountStatus` code） | 是 | 账户有效性状态，见 §4.1 |
| 结账状态 | `billing_status_enum` | `String`（`AccountBillingStatus` code） | 是 | 结账阶段状态，见 §4.2 |
| 账户类型编码 | `type_code` | `String`（**`AccountTypeCode.getCode()`**） | 是 | 与共享枚举 **`com.zxplat.domain.share.enums.AccountTypeCode`** 持久化码一致，见 §4.3 |
| 账户业务编码 | `bus_no` | `String` | 是 | 院内账户编号（实体属性名 `no`，持久化列名 `bus_no`） |
| 账户名称 | `name` | `String` | 否 | 展示名称 |
| 患者标识 | `patient_id` | `Long` | 是 | 关联患者聚合根 ID |
| 就诊标识 | `encounter_id` | `Long` | 是 | 关联就诊聚合根 ID |
| 账户余额 | `balance_amount` | `BigDecimal` | 是 | **库内账户余额**（预交金收退、出院结算**冲抵占用 −O**、有余原路退、反结回冲 +O 后的值；**禁止**再按「本批已结全额」或少补收预交金理解）；展示余额 = 本字段 − 待结算（读侧计算，非本列） |
| 医保区划 | `yb_area_no` | `String` | 否 | 医保区域编码 |
| 合同号 | `contract_no` | `String` | 否 | 默认自费 `DEFAULT_CONTRACT_NO`、学生占位 `DEFAULT_STUDENT_CONTRACT_NO`、医保协议号等 |
| 欠费限额 | `limit_account` | `BigDecimal` | 否 | 与 `BR-ACC-001` 目标不变式相关 |
| 是否登记用账户 | `encounter_flag` | `Boolean` | 否 | `true`/`false`，标识该行是否为挂号登记使用 |
| 险种 | `insutype` | `String` | 否 | 险种类型 |

> 通用审计字段（`createdAt/updatedAt/createdBy/updatedBy`）、租户字段（`tenantId`）、逻辑删除字段（`isDeleted/deletedAt/deletedBy`）继承自 `BaseAuditedEntity`（对应 `HisBaseEntity`），遵循统一约定。

### 3.2 子实体

> 本聚合为单表聚合，无聚合内子实体。若后续拆分附件行，在本表补充名称与职责。

---

## 4. 值对象 / 枚举

### 4.1 账户主状态（AccountStatus）

枚举类：`AccountStatus`（共享枚举库）

| 枚举常量 | 整型码 | 英文编码 | 中文 | 说明 |
|---|---|---|---|---|
| `ACTIVE` | 1 | `active` | 有效 | 可作为收费/支付引用的有效账户行 |
| `INACTIVE` | 2 | `inactive` | 无效 | 账户已失效/停用 |
| `ERROR` | 3 | `entered-in-error` | 错误 | 误录入 |
| `ON_HOLD` | 4 | `on-hold` | 冻结 | 账户暂挂/冻结（对外映射 R5 `on-hold` 语义） |
| `UNKNOWN` | 5 | `unknown` | 未知 | 状态未知 |

> **存储口径**：对应主表字段 `status_enum`，使用**字符串编码存储（存枚举 code）**。
>
> **成码口径（2026-05-18）**：新开账户主状态使用 `PublicationStatus.ACTIVE`（`ACTIVE`＝有效/已启用）；读侧默认账户查询仅命中 `ACTIVE` code，**不接受**历史数字串 `"1"`。

### 4.2 结账状态（AccountBillingStatus）

枚举类：`com.zxplat.domain.share.enums.AccountBillingStatus`（`EnumInterface`）

| 枚举常量 | 整型码 | 英文编码 | 中文 | 说明 |
|---|---|---|---|---|
| `OPEN` | 1 | `open` | 可用 | 账户开放，可产生费用 |
| `CARECOMPLETE_NOTBILLED` | 2 | `carecomplete-notbilled` | 已出院未结账 | 住院已出院但尚未完成结算 |
| `CLOSED_BADDEBT` | 3 | `closed-baddebt` | 坏账 | 账户因坏账关闭 |
| `CLOSED_VOIDED` | 4 | `closed-voided` | 不再记账 | 账户作废，不再记录费用 |
| `CLOSED_COMPLETED` | 5 | `closed-completed` | 已结账 | 账户正常结算完成并关闭 |
| `CLOSED_COMBINED` | 6 | `closed-combined` | 已合并 | 账户因合并而关闭 |

> **存储口径**：对应主表字段 `billing_status_enum`，使用**字符串编码存储（存枚举 code）**。

### 4.3 账户类型（`AccountTypeCode`）

枚举类：`com.zxplat.domain.share.enums.AccountTypeCode`（`EnumInterface`；**持久化列 `type_code` 存 `getCode()`**）。

| 枚举常量 | `sortOrder`（展示序） | `code`（落库 `type_code`） | `display` | 说明 |
|---|---|---|---|---|
| `MEDICAL_ELECTRONIC_CERTIFICATE` | 10 | `MEDICAL_ELECTRONIC_CERTIFICATE` | 医保电子凭证 | 医保电子凭证介质账户 |
| `ID_CARD` | 20 | `ID_CARD` | 居民身份证 | 居民身份证介质账户 |
| `SOCIAL_SECURITY_CARD` | 30 | `SOCIAL_SECURITY_CARD` | 社会保障卡 | 社会保障卡介质账户 |
| `PERSONAL_CASH_ACCOUNT` | 40 | `PERSONAL_CASH_ACCOUNT` | 个人现金账户 | **门诊默认挂账**：挂号费、`ChargeItem` 草稿（含医生站 IF-DOR-14）共用锚点；`AccountQueryService#getSelfPayAccount` / `getRegistrationAccount` 默认命中 |
| `MEDICAL_INSURANCE` | 50 | `MEDICAL_INSURANCE` | 医保账户 | 就诊默认医保分账行 |
| `REG` | 0 | `REG` | 挂号（非账户主路径） | **仅**用于挂号费**发票草稿**等侧 `type_code`；**不作为**门诊 `openEncounterAccounts` 开立 `adm_account` 的账户介质码 |

> **存储口径**：`adm_account.type_code` 与上表 **`code`** 列逐字一致；历史 `SELF_PAY` 由工程 Flyway **V37** 迁移为 `PERSONAL_CASH_ACCOUNT`。

---

## 5. 领域事件

### 5.1 事件定义

| 编号 | 事件名称 | 触发时机 | 携带数据 / 最小载荷 | 消费方 | 版本 |
|---|---|---|---|---|---|
| 5.1.1 | `AccountDomainEvents.AccountOpenedEvent` | 每行账户插入成功（开立命令路径或 upsert 插入路径） | 账户标识, 患者标识, 患者姓名快照, 就诊标识, 账户类型编码, 初始余额 | 收费、支付编排、QueryService（可选） | v1 |
| 5.1.2 | `AccountDomainEvents.AccountBalanceEditedEvent` | `saveOrUpdateAccount` 更新且余额字段变化 | 账户标识, 就诊标识, 变更前余额, 变更后余额 | 收费、支付、流水投影（可选） | v1 |

### 5.2 事件触发规则

| 当前状态 | 操作 | 目标状态 | 发布事件 |
|---|---|---|---|
| — | 开立账户（批量/登记主户） | `ACTIVE` | `AccountDomainEvents.AccountOpenedEvent` |
| `ACTIVE` | `saveOrUpdateAccount` 命中更新且余额变化 | `ACTIVE` | `AccountDomainEvents.AccountBalanceEditedEvent` |
| `ACTIVE` | `saveOrUpdateAccount` 未命中转插入 | `ACTIVE` | `AccountDomainEvents.AccountOpenedEvent` |

---

## 6. 领域服务

### 6.1 写侧领域服务（AccountDomainService）

#### 6.1.1 开立就诊账户（批量）（`openEncounterAccounts`）
- **业务意义**：按一次就诊批量开立账户行（门诊当前实现为默认 **`PERSONAL_CASH_ACCOUNT`** 单行；扩展可含医保等并行行）。
- **边界说明**：只维护账户主档，不处理支付流水与票据编排。
- **前置条件**：同一 `patientId + encounterId` 下按批次处理；每行 `typeCode` 须为 **§4.3** 合法 `AccountTypeCode` 持久化码；每行草稿具备最小可插入字段。
- **聚合内影响**：批量插入账户行。
- **事件输出**：每成功插入一行发布 `AccountOpenedEvent`。

#### 6.1.2 登记主账户插入（`saveAccountByRegister`）
- **业务意义**：保证登记主户存在并可被后续流程稳定引用。
- **前置条件**：强制 `encounterFlag = YES`。
- **聚合内影响**：主户已存在时幂等返回；不存在则插入。
- **事件输出**：插入成功发布 `AccountOpenedEvent`。

#### 6.1.3 按患者+就诊 upsert 有效账户（`saveOrUpdateAccount`）
- **业务意义**：在统一口径下对有效账户执行“命中更新/未命中插入”。
- **前置条件**：按 `patientId + encounterId + statusEnum = ACTIVE` 判重（BR-ACC-003）。
- **聚合内影响**：命中则整行更新，否则插入。
- **事件输出**：插入发布 `AccountOpenedEvent`；更新且余额变化时发布 `AccountBalanceEditedEvent`。

#### 6.1.4 住院登记开户（`openInpatientRegAccount`）
- **业务意义**：住院登记提交时为就诊开立 **`PERSONAL_CASH_ACCOUNT`** 行（余额可为 0），供预交金收退与结算引用。
- **前置条件**：`patientId`、`encounterId` 有效；`contractNo` 与登记所选费用性质一致。
- **聚合内影响**：幂等插入或跳过已存在行。
- **事件输出**：新插入发布 `AccountOpenedEvent`。

#### 6.1.5 合同号回填（`backfillContractNoIfMissing`）
- **业务意义**：登记/挂账后若账户行 `contract_no` 为空，按业务单回填。
- **前置条件**：目标 `accountId` 存在且当前 `contract_no` 为空。
- **聚合内影响**：更新 `contract_no` 字段。
- **事件输出**：无独立领域事件。

#### 6.1.6 预交金余额调整（`adjustAdvanceDepositBalance`）
- **业务意义**：住院预交金收/退、出院结算**冲抵占用（仅 O>0 时 −O）**、有余原路退、反结回冲 +O 时，由应用编排调整账户 **`balance`**（与 **DM-FIN-002** / DD「住院预交金结算口径纠错」衔接；**废除**少补另记收预交金、以及自费「本批已结直接全额扣余额」旧语义）。
- **前置条件**：`AdjustAdvanceDepositBalanceCommand` 含账户主键与有符号 `balanceDelta`；调整后余额不得低于账户下限（**BR-ACC-001**）。
- **聚合内影响**：更新 `balance`。
- **展示口径（读侧，非本聚合字段）**：展示余额 = `balance` − Σ待结算；冲抵占用已写入 `balance`，读侧**不再**减 `SETTLED`；卡片/队列仅输出单一余额文案。
- **事件输出**：余额变化发布 `AccountBalanceEditedEvent`。

#### 6.1.7 停用医保挂账账户（`deactivateMedicalInsuranceAccount`）
- **业务意义**：住院医保转自费后，默认挂账不再命中医保户，新费用回到个人现金户。
- **前置条件**：按就诊查找 `MEDICAL_INSURANCE`（含已停用）。
- **聚合内影响**：启用中的医保户改为 `INACTIVE`，结账状态 `closed-voided`。不存在或已停用则幂等返回。不改余额。
- **事件输出**：无。

#### 6.1.8 重新启用医保挂账账户（`reactivateMedicalInsuranceAccount`）
- **业务意义**：转自费之后再次转医保时，启用原先停用的医保户并写入本次费用性质与险种。
- **前置条件**：同就诊已有医保户行。仍为启用态时不改合同号。
- **聚合内影响**：`INACTIVE` → `ACTIVE`，结账状态 `open`，可更新 `contract_no` / `insutype`。无行则不插入。
- **事件输出**：无。

### 6.2 读侧查询服务（AccountQueryService）

> 查询类动作无写库，以"查询范围（读库，无写入）+ 组装规则"表达QueryService。

#### 6.2.1 按就诊取默认个人现金账户主键（`getSelfPayAccount`）
- **业务意义**：定位 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT`** 且 `defaultFlag=true` 的账户主键（医生站费用草稿等下游引用）；方法名为历史沿用。
- **返回约束**：未命中返回 `null`；仅返回 `id`。

#### 6.2.2 按就诊取医保账户主键（`getMedicalInsuranceAccount`）
- **业务意义**：按医保口径定位账户主键。
- **返回约束**：仅返回 `id`；与 `getYbAccount` 口径不同，禁止混用。

#### 6.2.3 按就诊与类型取默认账户实体（`getAccountByEncounterFlag`）
- **业务意义**：按 `encounterId` + 传入 `typeCode`（通常为 **`AccountTypeCode` 持久化码**）定位 `defaultFlag=true` 的账户实体。
- **过滤口径**：由调用方传入 `typeCode`；无额外隐式过滤。

#### 6.2.4 按就诊取医保侧实体（`getYbAccount`）
- **业务意义**：按宽口径返回医保侧账户实体。
- **使用约束**：调用前需明确业务场景。

#### 6.2.5 按就诊取默认个人现金账户实体（`getSelfAccount`）
- **业务意义**：返回 **`PERSONAL_CASH_ACCOUNT`** 默认行完整实体。

#### 6.2.6 按就诊列出全部账户行（`getAccountListByEncounter`）
- **业务意义**：提供就诊维度账户全量行视图。
- **使用约束**：禁止直接用于大报表联表。

#### 6.2.7 学生场景取个人现金账户主键（`getStudentSelfAccount`）
- **业务意义**：与 **`getSelfPayAccount`** 同口径（学生场景历史命名）。
- **返回约束**：仅返回 `id`。

#### 6.2.8 学生场景取医保账户主键（`getStudentYbAccount`）
- **业务意义**：按学生场景定位医保主键。
- **返回约束**：仅返回 `id`。

#### 6.2.9 判断是否个人现金挂账账户（`isSelfPay`）
- **业务意义**：判断账户行是否为**个人现金**挂账介质（与门诊默认锚点一致）。
- **判定口径**：`type_code` 等于 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT.getCode()`**。

#### 6.2.10 按患者+就诊取登记主账户（`getRegistrationAccount`）
- **业务意义**：门诊挂号/住院登记提交时解析 **`encounterFlag=YES`** 的个人现金账户实体。
- **边界说明**：只读；与 **AS-OP-FIN-001** IF-REG-07、**AS-IP-FIN-001** 登记开户编排衔接。

#### 6.2.11 批量按就诊取登记账户（`mapRegistrationAccountsByEncounterIds`）
- **业务意义**：住院登记/预交金/结算队列批量回填 **`accountBalance`**；费用性质展示优先走 **§6.2.13**。
- **边界说明**：只读 Map；未开户就诊缺键或 null 以实现约定为准。

#### 6.2.12 批量按账户主键加载（`mapAccountsByIds`）
- **业务意义**：流水/退费场景按 `accountId` 集合批量取账户行。
- **边界说明**：只读 Map。

#### 6.2.13 批量解析优先合同号（`mapPreferredContractNoByEncounterIds`）
- **业务意义**：医生站/护士站患者卡片与医嘱默认费用性质：有默认**医保账户**且 `contract_no` 非空则用其合同号，否则回落登记**个人现金账户**合同号。
- **边界说明**：只读 Map；仅返回非空 `contract_no`；合同名称解析由应用层 `ContractQueryService` / `EncounterContractDisplayAssembler` 完成。

#### 6.2.14 批量按就诊取医保账户（`mapMedicalInsuranceAccountsByEncounterIds`）
- **业务意义**：与 §6.2.13 配套，供双账户场景优先展示医保合同。
- **边界说明**：只读 Map；同就诊同类型仅保留首条有效行。

#### 6.2.15 按优先合同号反查就诊（`listEncounterIdsByPreferredContractNo`）
- **业务意义**：报表/队列按 **费用性质**（`fin_contract.bus_no`）筛选：命中就诊优先账户（§6.2.13 同口径）合同号等于入参的就诊主键列表。
- **边界说明**：只读 List；空合同入参返回空列表；未筛选时由应用层传 `null` 不收窄。

### 6.3 领域边界说明

- `Account` 领域动作只描述就诊账户主档及其开立、定位查询、按患者就诊 upsert 等与账户行直接相关的业务事实。
- 支付主单、预交金流水、对账与发票等他域对象不在本聚合内维护；通过 `accountId` 关联协作。
- 页面初始化、下拉字典、多域拼装回显等应用层能力不记入本节动作编号。
- 写编排由 `AccountDomainService` 承担；读编排由 `AccountQueryService` 承担；持久化由 `AccountRepository` 承担；不使用 `AccountManager` 命名。

---

## 7. 不变式

| 编号 | 不变式 | 约束/落地要点 |
|---|---|---|
| BR-ACC-001 | 余额目标不变式：出账后余额不得低于 `-limitAccount` | 出账前断言由收费/支付应用编排承担；写侧在余额变更观测上可配合领域事件，**不**替代不变式断言 |
| BR-ACC-002 | 冻结（扩展）：当前实体无冻结金额字段 | 若产品扩展冻结能力，需同步扩展字段与动作 |
| BR-ACC-003 | `saveOrUpdateAccount` 以 `patientId + encounterId + statusEnum = ACTIVE` 判重；命中则整行更新，否则插入 | 判重不含 `contractNo`/`typeCode`；与「同一就诊多并行户」组合使用时须评估覆盖风险 |
| BR-ACC-004 | 开立途径不按门诊/住院拆分：门诊默认 **`PERSONAL_CASH_ACCOUNT`** 可与医保等并行户同批开立 | 并行行 `typeCode` 须区分；`encounter_flag` 语义以产品配置为准 |
| BR-ACC-QRY-001 | 列表/报表不在本聚合写模型上做大 JOIN；流水走QueryService或专用读编排 | 就诊维度列表取 `getAccountListByEncounter` 等窄查询；大报表走 `RM_AccountTransaction` 等读边界 |
| BR-ACC-QRY-002 | 学生合同与医保合同定位方法禁止混用口径 | 调用方按场景选择 Q-02/Q-05/Q-07/Q-08 |
| BR-ACC-QRY-003 | 写入前需要账户事实时先读再写 | 与支付、收费编排的「先查再改」一致 |

### 7.1 子实体规则

> 本聚合为单表聚合，无聚合内子实体行；下列条目按「并行账户事实」约束表述，编号预留为 `PR-ACC-*`。

- `PR-ACC-001`：同一 `encounterId + typeCode（含 defaultFlag 语义组合）` 下，业务约定的默认行不得重复插入；重复开立须幂等返回已有主键或拒绝，由写侧编排统一处理。
- `PR-ACC-002`：同一 `encounterId + typeCode + contractNo` 组合在开立命令路径上应先查后写，避免重复插入破坏收费引用稳定。
- `PR-ACC-003`：`saveOrUpdateAccount` 不得用于替代「按合同/类型精确定位单行」的更新意图；并行多户场景优先使用开立命令或带主键的定向更新。

---

## 8. 上下文映射

### 8.1 上游依赖

| 上游上下文 | 聚合 | 关联方式 | 说明 |
|---|---|---|---|
| 患者管理 | Patient | `patientId` | 账户归属的患者 |
| 就诊管理 | Encounter | `encounterId` | 账户关联的就诊 |

### 8.2 下游影响

| 下游上下文 | 聚合 | 触发方式 | 说明 |
|---|---|---|---|
| 收费管理 | ChargeItem | 引用 | 收费明细通过 `accountId` 关联账户 |
| 支付对账 | PaymentReconciliation | 引用/事件 | 支付主单引用 `accountId`（门诊挂号回填与 **`PERSONAL_CASH_ACCOUNT`** 行对齐） |
| 流水投影 | RM_AccountTransaction | 依赖 | 流水只读视图边界 |

### 8.3 引用规则

- **患者与就诊**：账户主体通过 `patientId` 归属患者（`DM-ADM-001 Patient`），通过 `encounterId` 归属就诊（`DM-ADM-002 Encounter`）；开立与查询编排须同时满足两标识的业务约束。
- **收费与支付**：收费明细通过 `accountId` 引用本聚合；支付对账主单（`DM-FIN-002 PaymentReconciliation`）引用 `accountId` 完成支付事实与账户余额闭环。
- **QueryService 能力**：流水只读视图 `RM_AccountTransaction` 为查询边界，不在本聚合内写入。

| 模型名称 | 关系类型 | 说明 |
|---|---|---|
| `DM-ADM-001 Patient` | 关联 | 通过 `patientId` |
| `DM-ADM-002 Encounter` | 关联 | 通过 `encounterId` |
| `DM-FIN-002 PaymentReconciliation` | 依赖 | 支付主单引用 `accountId` |
| `RM_AccountTransaction` | 依赖 | 流水只读视图边界 |

### 8.4 事件流示意图

```text
Patient / Encounter
        └──(开立)──> Account
                          ├── AccountBalanceChangedEvent ──> QueryService / 欠费提示
                          └── 支付编排引用 accountId ──> PaymentReconciliation / ChargeItem
```

---

## 9. 补充

### 9.1 与收费/支付的职责边界

| 职责 | ChargeItem | Account | PaymentReconciliation |
|---|---|---|---|
| 计费明细 | 管理每一条收费明细 | — | — |
| 账户余额 | — | 管理余额增减 | — |
| 结账状态 | — | 管理结账生命周期 | — |
| 支付流水 | — | — | 管理支付/退款流水 |
| 引用关系 | 引用 accountId | 被引用 | 引用 accountId |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.2 | 2026-07-18 | **§6.2.13/14**：`mapPreferredContractNoByEncounterIds` / `mapMedicalInsuranceAccountsByEncounterIds`（展示与医嘱默认：有医保账户优先） |
| v1.1 | 2026-05-04 | **§4.3** 与工程枚举 **`AccountTypeCode`** 对齐（持久化 `getCode()`）；**§6.1/§6.2**、**BR-ACC-004**、**PR-ACC-001** 与门诊单默认个人现金户、`getSelfPayAccount`/`isSelfPay` 实现一致；废止旧 `01`～`04` 与 `AccountType` 命名。 |
| v1.0 | 2026-04-15 | 初版。 |

*最后更新：2026-05-04*

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`AccountDomainService`

| 方法 |
|------|
| `openEncounterAccounts` |
| `saveAccountByRegister` |
| `backfillContractNoIfMissing` |
| `openInpatientRegAccount` |
| `saveOrUpdateAccount` |
| `adjustAdvanceDepositBalance` |
| `deactivateMedicalInsuranceAccount` |
| `reactivateMedicalInsuranceAccount` |

### QueryService：`AccountQueryService`

| 方法 |
|------|
| `getSelfPayAccount` |
| `getMedicalInsuranceAccount` |
| `getAccountByEncounterFlag` |
| `getRegistrationAccount` |
| `getYbAccount` |
| `getSelfAccount` |
| `hasDualChargeAccounts` |
| `getAccountListByEncounter` |
| `getStudentSelfAccount` |
| `getStudentYbAccount` |
| `isSelfPay` |
| `mapRegistrationAccountsByEncounterIds` |
| `mapMedicalInsuranceAccountsByEncounterIds` |
| `mapPreferredContractNoByEncounterIds` |
| `getDefaultChargeAccount` |
| `mapDefaultChargeAccountsByEncounterIds` |
| `listEncounterIdsByAnyAccountContractNo` |
| `listEncounterIdsByPreferredContractNo` |
| `mapAccountsByIds` |

