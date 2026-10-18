# 住院预交金 · 应用服务详细设计（SDD）

> 编码：AS-IP-FIN-002-SDD  
> 版本：v1.21 | 日期：2026-10-13 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院预交金应用服务（住院收费「预交金管理」页 `/inpatient/charge/prepay`） |
| 边界 | 编排 Encounter 门禁、Account 余额、PaymentReconciliation 住院存款收/退；不在应用层实现聚合不变式 |
| 编排权威 | `AS-IP-FIN-002-住院预交金.md`（**v2.7**）§2、§4 |
| 方法权威 | `DM-ADM-002`、`DM-ADM-009`、`DM-FIN-002` §6；`DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §3 |
| 严格对齐 | IF 编号严格对齐 AS 的 **IF-IP-PREP-00～04** |
| 实现阶段口径 | **目标态全量成码**（IF-00～04）；领域 `collectAdvanceDeposit` / `refundAdvanceDeposit` / `settleInpatientPayment` **已存在**；应用层 `com.zxplat.domain.application.inpatient.financial.prepay` **已实现**（`InpatientPrepayAppService` IF-IP-PREP-00～04） |

**Out of scope（重复 AS §1.4，SDD 不得扩写）**

| 类别 | 内容 |
| --- | --- |
| 住院登记与首笔预交金 | **AS-IP-FIN-001**（登记弹窗内可选首笔；本页仅续收/退费） |
| 住院结算、出院多退少补 | 住院结算 AS，本期不展开 |
| 医保并行户预交金 | 本期不编排 |
| 打印凭证 | UI 占位，不纳入 IF |
| 扫码/网银异步回执 | 契约保留分支，实现可分期 |
| 病区维度筛选 | 老系统可选字段，新 UI 未设计，本期舍弃 |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-FIN-001-住院登记.md` | 登记必开个人现金户；首笔预交金在登记 IF 内完成 |
| `DM-FIN-002` v1.4 | 预交金收/退 **DRAFT → settleInpatientPayment**；退费无 `relation_id` |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | Request/Response 枚举持久化一律 **String code**（`EncounterStatus.getCode()`、`PaymentKind` 等） |
| QueryService口径 | 在院队列为 `InpatientEncounterContextQueryModel` + **展示余额**组装；流水为 `PaymentReconciliationQueryService` 按就诊查询后 App 过滤 `HOSPITAL_DEPOSIT` |
| 收退收口 | `collectAdvanceDeposit` / `refundAdvanceDeposit` → `settleInpatientPayment` → `AccountDomainService#adjustAdvanceDepositBalance`（DM-FIN-002 §9.1） |
| 可收退状态（写） | `IMP` 且 `status_enum` ∈ `REGISTERED` / `IN_PROGRESS` / `ON_HOLD` / `PENDING`（**BR-IP-PREP-001** 收退写门禁） |
| 队列可见（读） | `IMP` 且 `status_enum` ∈ 上四态 **+ `COMPLETED` + `DISCHARGED` + `CANCELLED`**（`applyPrepayEligibleStatus`；`stayScope` 求交）；**不在院三态另 EXISTS** `HOSPITAL_DEPOSIT`/`PRE_DEPOSIT` + `SUCCESS` |
| 退费口径 | 不绑原单；退费金额 ≤ **Account.balance（库内余额）**；金额为**正数** |
| 分页口径 | `pageNo` **1-based**；出参经 `PagedResultBuilder`（B.10.11） |
| 事务口径 | IF-IP-PREP-03/04 **产品期望**单事务；**成码口径**见 §5.2 **DOC-CONFLICT**（App 不声明 `@Transactional`） |
| 就诊读侧隔离 | 写接口 Pre 须 `EncounterQueryService#mustGetEncounterOfClass`（`IMP`）+ 应用层状态白名单 |
| IMP 状态展示 | 队列 `statusEnumText` 经 **`InpatientEncounterStatusDisplayUtil`**（**DM-ADM-002** §2.3.2）；`InpatientPrepayMapperImpl` 与登记/护士站同装配优先级 |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/financial/prepay/
  service/
    InpatientPrepayAppService.java
  request/
    PagePrepayPatientQueueRequest.java
    ListPrepayPaymentRecordsRequest.java
    CollectPrepayAdvanceDepositRequest.java
    RefundPrepayAdvanceDepositRequest.java
  response/
    PagePrepayPatientQueueResponse.java
    ListPrepayPaymentRecordsResponse.java
    CollectPrepayAdvanceDepositResponse.java
    RefundPrepayAdvanceDepositResponse.java
  dto/
    PrepayQueueItemDto.java
    PrepayPaymentRecordDto.java
    PrepayPaymentDetailDto.java
  mapper/
    InpatientPrepayMapper.java
    InpatientPrepayMapperImpl.java
```

> 包名：`com.zxplat.domain.application.inpatient.financial.prepay`  
> HTTP：`AppServiceControllerRegistrar` → `POST /api/app/inpatientPrepay/{methodName}`  
> 余额编排（领域）：`com.zxplat.domain.administration.account.service.AccountDomainService#adjustAdvanceDepositBalance`（`AdjustAdvanceDepositBalanceCommand`；收取 `balanceDelta>0`、退费 `balanceDelta<0`）

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径操作人（IF-IP-PREP-03/04，`B.6.1`） |
| `PageWindowGate` | 分页入站门控（IF-IP-PREP-01，`B.10.11`） |
| `PatientEncounterConsistencyGate` | IF-IP-PREP-03/04 强制；IF-IP-PREP-02 **仅当** `patientId!=null` 时校验 |
| `EncounterQueryService` | 在院患者队列（IF-IP-PREP-01） |
| `EncounterContextReader` | IF-IP-PREP-01：`pageInpatientPrepayQueue`；IF-IP-PREP-03/04：`mustGetInpatientContextByEncounterId` |
| `EncounterQueryService` | `mustGetEncounterOfClass`（IF-IP-PREP-02/03/04） |
| `AccountQueryService` | 个人现金户加载、余额校验（IF-IP-PREP-01/03/04） |
| `PaymentReconciliationDomainService` | 预交金收/退 + 结算（IF-IP-PREP-03/04） |
| `PaymentReconciliationQueryService` | 预交金流水列表（IF-IP-PREP-02）；`mapPaymentRecDetailsByPaymentIds` 批量取明细拼多渠道展示 |
| `AccountDomainService` | settle 成功后 `adjustAdvanceDepositBalance`（IF-IP-PREP-03/04） |
| `PractitionerQueryService` | 流水操作员展示名（IF-IP-PREP-02，`mapDisplayNameByEntererIds`） |
| `PaymentChannelDisplayAssembler` | `payMethodText`：`pay_enum` → 中文渠道名（`PaymentChannelConstants`） |
| `InpatientInfoQueueDisplayAssembler` | IF-IP-PREP-01 队列展示回填 + `contractDisplay` |
| `ContractQueryService` | `mapContractNameByBusNoIn`（账户 `contract_no` → 费用性质名） |
| `InpatientPrepayMapper` | DTO / Command 装配 |
| `InpatientAccountBalanceDisplayAssembler` | 展示余额数值（`computeDisplayAccountBalance`） |
| `InpatientAccountBalanceDisplayDtoApplier` | 队列行文案覆盖（`application.shared.inpatient`） |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `EncounterContextReader` | `pageInpatientPrepayQueue`（**Q-IPI-004**；`application.shared.encounter`） | IF-IP-PREP-01 |
| `EncounterContextReader` | `mustGetInpatientContextByEncounterId` | IF-IP-PREP-03/04 |
| `EncounterQueryService` | `mustGetEncounterOfClass` | IF-IP-PREP-02/03/04 |
| `AccountQueryService` | `mapRegistrationAccountsByEncounterIds` | IF-IP-PREP-01（**批量**，见 §2.6） |
| `AccountQueryService` | `getRegistrationAccount` | IF-IP-PREP-03/04 |
| `PaymentReconciliationQueryService` | `findPaymentsByEncounter` | IF-IP-PREP-02 |
| `PaymentReconciliationQueryService` | `mapPaymentRecDetailsByPaymentIds` | IF-IP-PREP-02（批量明细拼多渠道展示，禁止逐单 N+1） |
| `PaymentReconciliationDomainService` | `softDeleteAdvanceDepositDraft` | IF-IP-PREP-02（历史 CANCELLED 预交金软删） |
| `PractitionerQueryService` | `mapDisplayNameByEntererIds` | IF-IP-PREP-02（`entererId` 仅医务人员主键，**BR-OPC-016**） |
| `ContractQueryService` | `mapContractNameByBusNoIn` | IF-IP-PREP-01（`contractDisplay`） |
| `PaymentReconciliationDomainService` | `collectAdvanceDeposit` | IF-IP-PREP-03 |
| `PaymentReconciliationDomainService` | `refundAdvanceDeposit` | IF-IP-PREP-04 |
| `PaymentReconciliationDomainService` | `settleInpatientPayment` | IF-IP-PREP-03/04 |
| `AccountDomainService` | `adjustAdvanceDepositBalance` | IF-IP-PREP-03/04（收/退共用；`balanceDelta` 正/负） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3.1 `adm_encounter.class_enum` | 本场景 **`IMP`** |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §2.3.1 预交金行 | 可收退：`REGISTERED`/`IN_PROGRESS`/`ON_HOLD`/`PENDING` |
| 账户类型 | `com.zxplat.domain.share.enums.AccountTypeCode` | `DM-ADM-009` §4.3 | **`PERSONAL_CASH_ACCOUNT`** |
| 支付业务类型 | `com.zxplat.domain.share.enums.PaymentKind` | `DM-FIN-002` §3.1 `kind_enum` | **`HOSPITAL_DEPOSIT`** |
| 收付方向 | `com.zxplat.domain.share.enums.PaymentType` | `DM-FIN-002` §3.1 `payment_enum` | 收取 **`PAY`**；退还 **`UN_PAY`** |
| 支付流水状态 | `com.zxplat.domain.share.enums.PaymentStatus` | `DM-FIN-002` §3.1 `status_enum` | 草稿 **`DRAFT`** → 结算 **`SUCCESS`** |
| 支付渠道 | `PrepayPaymentDetailDto.payChannelCode` | `DM-FIN-002` 明细 `pay_enum` | IF-IP-PREP-03/04 明细必填 |
| 编排消息码 | `com.zxplat.domain.share.constants.InpatientPrepayConstants` | `AS-IP-FIN-002` §5 | `EX-IP-PREP-*`（**已有**） |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 正向（白名单 → §3 序列） | PASS | 白名单方法均出现在至少一个 IF 编排序列 |
| 反向（§3 序列 → 白名单） | PASS | 各 IF 的 Pre/Cmd/Support 调用均可在 §2.2 定位 |
| 互斥步骤对 | PASS | IF-IP-PREP-03 三方收款分支在说明列写明「否则跳过」 |

### 2.5 领域层能力核对（非 Gap）

> **Gap 仅登记于 AS `AS-IP-FIN-002` §3**（当前**无待跟踪缺口**）；本 SDD **禁止**维护 Gap 表。读侧 **`EncounterContextReader#pageInpatientPrepayQueue`（Q-IPI-004）**、流水 **`PaymentReconciliationQueryService#findPaymentsByEncounter`** 已落地；IF-IP-PREP-01 **不**返回 `arrearsAlert`（产品裁决，非缺口）。

### 2.6 成码前已闭合契约（原评审 P0～P2，2026-05-19）

| 主题 | 影响 IF | 约定（成码必须遵守） |
| --- | --- | --- |
| **支付明细合计** | IF-IP-PREP-03/04；登记首笔（**AS-IP-FIN-001** IF-IP-REG-03） | Gate：`sum(paymentDetails[].amount)`（空明细按 0）须 **等于** 主金额：`amount` / `refundAmount` / `advanceDepositAmount`（`BigDecimal` 比较，精度与收银台一致，建议 scale=2、`compareTo`）。不等 → **EX-IP-PREP-007**（预交金）/ **EX-IP-REG-001**（登记首笔，文案「支付明细合计与预交金金额不一致」）。明细各行 `amount` 均须 **> 0**。**登记首笔**：`InpatientRegistrationAppService` **已实现**（2026-05-19）。 |
| **EX-IP-PREP-006（账户下限）** | IF-IP-PREP-04 | `adm_account` **无** `limit_account` 列时：**不单独抛 006**；退费后余额 &lt; 0 由 `AccountDomainService#adjustAdvanceDepositBalance` 抛 **EX-IP-PREP-005**。App **无需**预检 006 分支；待 DM 落 `limit_account` 后再在领域/App 补 006。 |
| **队列账户批量加载** | IF-IP-PREP-01 | **强制** `AccountQueryService#mapRegistrationAccountsByEncounterIds(当前页 encounterId 集合)`；**禁止**对当前页逐行 `getRegistrationAccount`（N+1）。未开户行：展示余额按 0 计，收退仍由 IF-03/04 抛 EX-IP-PREP-002。 |
| **展示余额** | IF-IP-PREP-01/03/04 及住院其它需展示「还剩多少钱」的 IF | **`InpatientAccountBalanceDisplayAssembler`**：`computeDisplayAccountBalance(account, pending)` = **库内 `balance` − 待结算**（`mapPendingSettlementTotalPriceByEncounterIds`）；卡片/队列**单一余额文案**；**`PLANNED` 不扣减** |
| **流水 patientId 门控** | IF-IP-PREP-02 | `patientId` **可选**；未传时仅按 `encounterId` 只读返回 `HOSPITAL_DEPOSIT` 流水；传了则 **`PatientEncounterConsistencyGate`** |
| **流水 kind 过滤** | IF-IP-PREP-02 | App 过滤 **`PaymentKind#allowsEmptyChargeItemIds()`**（`HOSPITAL_DEPOSIT` / 历史 `PRE_DEPOSIT` 同义）；**不**限制就诊 `COMPLETED` |
| **队列可见范围** | IF-IP-PREP-01 | **`EncounterContextReader#pageInpatientPrepayQueue`** → `applyPrepayEligibleStatus`：资格态 = 四态 + `COMPLETED` + `DISCHARGED` + `CANCELLED`；`stayScope`→`statusEnumIn` 求交；**不在院三态**另 `existsHospitalDepositHistoryForEncounter`（`HOSPITAL_DEPOSIT`/`PRE_DEPOSIT` + `SUCCESS`，无流水不入队）；`ALL` = 在院 ∪（不在院∧有流水）；收退写仍四态白名单 |
| **退费上限** | IF-IP-PREP-04 | Pre 门禁与领域扣减以 **`Account.balance`（库内余额）** 为准，**非**展示余额。 |
| **流水操作员名** | IF-IP-PREP-02 | `operatorName`：`entererId` → **`PractitionerQueryService#mapDisplayNameByEntererIds`**（仅 `adm_practitioner.id`，**BR-OPC-016**）；查无则 `null`。 |
| **支付方式展示** | IF-IP-PREP-02 | `payMethodText`：`mapPaymentRecDetailsByPaymentIds` → **`PaymentChannelDisplayAssembler`**（登记已**一渠道一流水**，正常仅渠道名；历史多明细用「，」拼接）；`payChannelCode` 取首条。**不展示** `DRAFT`；历史 `CANCELLED` 调用 `softDeleteAdvanceDepositDraft` 后不可见。 |
| **队列展示回填** | IF-IP-PREP-01 | 与登记列表同 **`InpatientInfoQueueDisplayAssembler`**；费用性质按 **`AccountQueryService#mapPreferredContractNoByEncounterIds`**（医保户优先）→ `ContractQueryService#mapContractNameByBusNoIn` → `contractDisplay`。 |
| **队列 patient_id 兜底** | IF-IP-PREP-01 | QueryService `patient_id` 为空时，`InpatientPrepayAppService` 装配前调用 **`EncounterQueryService#mapPatientIdByEncounterIds`**，避免 `PatientEncounterConsistencyGate` 误抛 **EX-IP-PREP-102**（与登记投影 **v3.1** 对齐）。 |
| **收付方向落库** | IF-IP-PREP-03/04 | 支付头 `payment_enum` 落 **`PaymentType.PAY` / `UN_PAY`**；渠道在 **明细** `pay_enum`（与登记首笔「头表存渠道」存量差异见 §8 DOC-CONFLICT）。 |
| **QueryService场景登记** | IF-IP-PREP-01 | 见 **InpatientEncounterContextQueryModel** QueryService **Q-IPI-004**；与 §2.5 **Q-IPI-004** `pageInpatientPrepayQueue` 同步。 |

**成码顺序建议**：① 读侧 `pageInpatientPrepayQueue`（**已完成**）→ ② `InpatientPrepayAppService` IF-01～04（**已完成**）→ ③ 前端 `inpatient-prepay.ts` 联调。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败都不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-PREP-00 | `loadInpatientPrepayPageInit` | 预交金页初始化 | `POST /api/app/inpatientPrepay/loadInpatientPrepayPageInit` |
| IF-IP-PREP-01 | `pagePrepayPatientQueue` | 患者队列查询（在院/不在院/全部） | `POST /api/app/inpatientPrepay/pagePrepayPatientQueue` |
| IF-IP-PREP-02 | `listPrepayPaymentRecords` | 预交金流水查询 | `POST /api/app/inpatientPrepay/listPrepayPaymentRecords` |
| IF-IP-PREP-03 | `collectPrepayAdvanceDeposit` | 收取预交金（续收） | `POST /api/app/inpatientPrepay/collectPrepayAdvanceDeposit` |
| IF-IP-PREP-04 | `refundPrepayAdvanceDeposit` | 退预交金 | `POST /api/app/inpatientPrepay/refundPrepayAdvanceDeposit` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：就诊状态、账户、余额门禁 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写 + 结算 + 账户余额编排 | 事务回滚见 §5 |
| **Asm** | Mapper 纯 DTO 装配 | — |

记法：`PaymentReconciliationDomainService#collectAdvanceDeposit(CollectAdvanceDepositCommand)` 表示注入 Bean 调用。

### 3.1A IF-IP-PREP-00 `loadInpatientPrepayPageInit`（预交金页初始化）

**HTTP**：`POST /api/app/inpatientPrepay/loadInpatientPrepayPageInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 占位请求体 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentChannelOptions` | `List<OptionDto>` | 用户可选支付渠道（**ADR-PAY-001**；`PaymentChannelOptionProvider`） |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Pre | `PaymentChannelOptionProvider#loadUserSelectableOptions` | 477 ∩ 枚举 ∩ 模块 Gate |
| 2 | Asm | `InpatientPrepayMapper#toLoadInpatientPrepayPageInitResponse` | 纯 DTO 装配 |

**失败码**：EX-IP-PREP-101 → 初始化失败。

### 3.2 IF-IP-PREP-01 `pagePrepayPatientQueue`（患者队列查询）

**HTTP**：`POST /api/app/inpatientPrepay/pagePrepayPatientQueue`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | N | 住院号/姓名/床号模糊（不区分大小写，读侧 `SearchCodeUtil`） |
| `admissionDate` | LocalDate | N | 入院日期（对齐 `InpatientEncounterContextQueryModel.start_time` 日期部分） |
| `stayScope` | String | N | 在院范围：`IN_HOSPITAL`（默认在院四态）/ `NOT_IN_HOSPITAL`（不在院三态**且须有预交金流水**）/ `ALL`（在院 ∪ 有流水的不在院）；见 `InpatientPrepayStayScope`；空/未知按 `ALL` |
| `pageNo` | Integer | Y | 页码（1-based） |
| `pageSize` | Integer | Y | 页大小 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<PrepayQueueItemDto>` | 队列卡片 |
| `totalCount` | Long | 总条数 |
| `pageNo` | Integer | 当前页（1-based） |
| `pageSize` | Integer | 页大小 |

**DTO 字段表：PrepayQueueItemDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |
| `encounterBusNo` | String | Y | 住院号 |
| `patientName` | String | Y | 患者姓名 |
| `genderEnum` | String | N | 性别 code |
| `genderEnumText` | String | N | 性别展示 |
| `ageText` | String | N | 年龄展示 |
| `organizationName` | String | N | 科室名称 |
| `wardName` | String | N | 病区名称 |
| `bedName` | String | N | 床号展示 |
| `statusEnum` | String | Y | 就诊状态 code |
| `statusEnumText` | String | Y | IMP 产品阶段文案（**DM-ADM-002** §2.3.2；禁止 `EncounterStatus#getDisplay()`） |
| `startTime` | LocalDateTime | N | 入院时间 |
| `accountBalance` | BigDecimal | Y | **展示余额**：`Account.balance` − **待结算合计**（`PENDING_SETTLEMENT`；见 §2.6） |
| `contractDisplay` | String | N | 费用性质名称：优先医保账户 `contract_no`，否则登记个人现金户 → `fin_contract.name`（与护士站/医生站同口径） |

**关键校验**

- `pageNo`≥1，`pageSize`≥1。
- 查询异常 → EX-IP-PREP-101。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-PREP-101 | 队列查询失败 | 缩小条件或稍后重试 |

**Mapper 接口方法签名**

- `toInpatientEncounterPageQuery(PagePrepayPatientQueueRequest): InpatientEncounterPageQuery`
- `toPrepayQueueItemDto(InpatientEncounterContextQueryModel, Account, InpatientQueueDisplayOverlay displayOverlay, BigDecimal pendingSettlementTotal): PrepayQueueItemDto`
- `toPagePrepayPatientQueueResponse(List<PrepayQueueItemDto>, Page<InpatientEncounterContextQueryModel>): PagePrepayPatientQueueResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `PagePrepayPatientQueueRequest` | 校验分页参数；非法 → EX-IP-PREP-101 |
| 2 | Pre | `InpatientPrepayMapper#toInpatientEncounterPageQuery` | Request | 装配 `InpatientEncounterPageQuery`（含 keyword、admissionDate、`stayScope`→`statusEnumIn`） |
| 3 | Pre | **`EncounterContextReader#pageInpatientPrepayQueue`** | 上步 Query | 读侧按页签过滤状态 + 入院日；**Q-IPI-004** |
| 4 | Pre | `AccountQueryService#mapRegistrationAccountsByEncounterIds` | 当前页 `encounterId` 集合 | **批量**加载 `PERSONAL_CASH_ACCOUNT`（§2.6 余额）；禁止逐行查询 |
| 4a | Pre | `AccountQueryService#mapPreferredContractNoByEncounterIds` | 当前页 `encounterId` 集合 | 费用性质合同号（医保户优先，否则个人现金户） |
| 5 | Pre | `ChargeItemQueryService#mapPendingSettlementTotalPriceByEncounterIds` | 当前页 `encounterId` 集合 | **待结算合计**；禁止 N+1 |
| 6 | Pre | `InpatientInfoQueueDisplayAssembler#buildOverlay` | 当前页行 + 步骤 4a `contractNoByEncounterId` | 展示字段回填 + `contractDisplay` |
| 7 | Asm | `InpatientPrepayMapper#toPrepayQueueItemDto` + **`InpatientAccountBalanceDisplayDtoApplier#apply`** | QueryService行 + 账户 + 展示覆盖 + 步骤 5 待结算合计 | `computeDisplayAccountBalance` → 数值；Applier → 文案化 `accountBalance` |
| 8 | Asm | `PagedResultBuilder` | Page + content | 出参 1-based 分页；仅读路径 |

---

### 3.3 IF-IP-PREP-02 `listPrepayPaymentRecords`（预交金流水查询）

**HTTP**：`POST /api/app/inpatientPrepay/listPrepayPaymentRecords`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | N | 住院就诊 ID；未传返回空列表 |
| `patientId` | Long | N | 患者 ID（与 encounter 联校） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `records` | `List<PrepayPaymentRecordDto>` | 流水列表（倒序） |

**DTO 字段表：PrepayPaymentRecordDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paymentId` | Long | Y | 支付流水 ID |
| `tradeTime` | LocalDateTime | Y | 收费/退费时间（`bill_date` 或 `created_at`） |
| `bizTypeText` | String | Y | 业务类型展示：「收预交金」/「退预交金」（由 `payment_enum` 派生） |
| `paymentEnum` | String | Y | `PAY` / `UN_PAY` |
| `amount` | BigDecimal | Y | 金额（正数） |
| `operatorName` | String | N | 操作员展示名：`entererId`→`PractitionerQueryService#mapDisplayNameByEntererIds`（§2.6） |
| `payMethodText` | String | N | 支付方式中文展示（明细首条 `pay_enum` 经 `PaymentChannelDisplayAssembler`） |
| `ticketNo` | String | N | 票据号/支付单号 |
| `statusEnum` | String | Y | 支付状态 code |
| `statusEnumText` | String | Y | 状态展示 |

**关键校验**

- `encounterId` 为空 → 返回空 `records`（不报错）。
- 就诊非住院 → EX-IP-PREP-102。
- **`patientId` 可选**：未传时不做患者门控；传了且与就诊主档不一致 → EX-IP-PREP-102。
- **`COMPLETED` 待清床**：允许只读查流水（含 **AS-IP-FIN-003** 出院多退 `UN_PAY`）。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-PREP-102 | 就诊非住院或不存在；或请求 `patientId` 与就诊不一致 | 刷新并重新选择患者 |

**Mapper 接口方法签名**

- `toPrepayPaymentRecordDtoList(List<PaymentReconciliationQueryModel>, Map<Long, String> operatorNames, Map<Long, String> payMethodTexts): List<PrepayPaymentRecordDto>`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | Request | 若 `encounterId==null` → Asm 空列表并返回 |
| 2 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | encounterId, `IMP` | 类别非 IMP → EX-IP-PREP-102；**不**限制 `COMPLETED` 只读查阅 |
| 3 | Pre | `PaymentReconciliationQueryService#findPaymentsByEncounter` | encounterId | 仅读 |
| 4 | Pre/Cmd | App 过滤 + 软删 CANCELLED + 批量解析操作员名与支付方式 | `HOSPITAL_DEPOSIT`（含 `allowsEmptyChargeItemIds` 口径）；`CANCELLED`→`softDeleteAdvanceDepositDraft`；滤 `DRAFT`；`mapDisplayNameByEntererIds`；`mapPaymentRecDetailsByPaymentIds` + `PaymentChannelDisplayAssembler`（§2.6） |
| 5 | Pre | `PatientEncounterConsistencyGate` | **仅当** `patientId!=null` | 与 IF-03/04 同口径；未传 `patientId` 时跳过 |
| 6 | Asm | `InpatientPrepayMapper#toPrepayPaymentRecordDtoList` | 支付列表 + 操作员/渠道 Map | `bill_date`/`id` 降序；纯展示，**禁止**同步写QueryService |

---

### 3.4 IF-IP-PREP-03 `collectPrepayAdvanceDeposit`（收取预交金（续收））

**HTTP**：`POST /api/app/inpatientPrepay/collectPrepayAdvanceDeposit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |
| `amount` | BigDecimal | Y | 收取金额，须 > 0 |
| `paymentDetails` | `List<PrepayPaymentDetailDto>` | Y | 支付明细（至少一条） |
| `paymentCode` | String | N | 扫码支付码（有三方分支时使用） |
| `operatorId` | Long | N | 操作员 ID；缺省取 `CurrentOperatorResolver#requireOperatorId` |
| `remark` | String | N | 备注 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentId` | Long | 支付流水 ID |
| `accountBalance` | BigDecimal | 结算后**展示余额**（动态计算，同 §2.6） |

**DTO 字段表：PrepayPaymentDetailDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payChannelCode` | String | Y | 支付渠道 code |
| `amount` | BigDecimal | Y | 明细金额 |
| `accountId` | Long | N | 个人现金户 ID（缺省由 App 填入登记账户） |

**关键校验**

- `encounterId`/`patientId` 缺失或 `amount`≤0 → EX-IP-PREP-001。
- `paymentDetails` 空 → EX-IP-PREP-008。
- `sum(paymentDetails[].amount) ≠ amount`（或明细行金额≤0）→ **EX-IP-PREP-007**（§2.6，与登记首笔对齐）。
- 就诊状态不在可收退集合 → EX-IP-PREP-003。
- 无个人现金户 → EX-IP-PREP-002。
- 支付/结算失败 → EX-IP-PREP-004。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-PREP-001 | 未选患者或金额≤0 | 请选择患者并输入正确金额 |
| EX-IP-PREP-002 | 账户不存在 | 请先完成住院登记 |
| EX-IP-PREP-003 | 状态不允许 | 说明当前状态不可办理 |
| EX-IP-PREP-004 | 收取或结算失败 | 以流水为准，引导重试 |
| EX-IP-PREP-008 | 未填支付明细 | 补全支付方式后再提交 |
| EX-IP-PREP-007 | 明细合计与主金额不一致 | 调整明细或主金额 |

**Mapper 接口方法签名**

- `toCollectAdvanceDepositCommand(CollectPrepayAdvanceDepositRequest, Long accountId, Long operatorId): CollectAdvanceDepositCommand`
- `toSettleInpatientPaymentCommand(Long paymentId): SettleInpatientPaymentCommand`
- `toCollectPrepayAdvanceDepositResponse(Long paymentId, BigDecimal accountBalance): CollectPrepayAdvanceDepositResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | Request | `encounterId`/`patientId`/`amount` 校验；失败 → EX-IP-PREP-001 |
| 2 | Gate | — | `paymentDetails` | 非空 → EX-IP-PREP-008；合计= `amount` → EX-IP-PREP-007（§2.6） |
| 3 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | encounterId, `IMP` | 加载就诊；失败 → EX-IP-PREP-102 |
| 4 | Pre | App 状态白名单 | `status_enum` | 须 ∈ {REGISTERED,IN_PROGRESS,ON_HOLD,PENDING}；否则 EX-IP-PREP-003 |
| 5 | Pre | `PatientEncounterConsistencyGate` | request.patientId vs encounter.patientId | 不一致 → EX-IP-PREP-001 |
| 6 | Pre | `AccountQueryService#getRegistrationAccount` | patientId, encounterId | 无户 → EX-IP-PREP-002 |
| 7 | Gate | App | `paymentDetails` 扫码渠道至多一条 | 多于一条 → EX-IP-PREP-001 |
| 8 | Cmd | 按 `paymentDetails` **循环**：`toCollectAdvanceDepositCommandForChannel` → `collectAdvanceDeposit`（扫码行可复用 `paymentDraftId`）→（扫码）`applyScanBeforeSettle` → `settleInpatientPayment` | 每渠道一笔：`kind=HOSPITAL_DEPOSIT`，头金额=该行金额 | **一渠道一流水**；发布收取/结算事件 |
| 9 | Cmd | `AccountDomainService#adjustAdvanceDepositBalance` | `balanceDelta=amount`（合计正数） | **写库**：Account 余额 += 合计 |
| 10 | Asm | `InpatientPrepayMapper#toCollectPrepayAdvanceDepositResponse` | 最后一笔 paymentId, 新余额 | 返回响应 |

---

### 3.5 IF-IP-PREP-04 `refundPrepayAdvanceDeposit`（退预交金）

**HTTP**：`POST /api/app/inpatientPrepay/refundPrepayAdvanceDeposit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |
| `refundAmount` | BigDecimal | Y | 退费金额，须 > 0 且 ≤ 账户余额 |
| `paymentDetails` | `List<PrepayPaymentDetailDto>` | Y | 退现渠道明细 |
| `operatorId` | Long | N | 操作员 ID |
| `remark` | String | N | 备注 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentId` | Long | 退款支付流水 ID |
| `accountBalance` | BigDecimal | 结算后**展示余额**（动态计算，同 §2.6） |

**关键校验**

- 同 IF-IP-PREP-03 的就诊/账户门禁。
- `sum(paymentDetails[].amount) ≠ refundAmount` → **EX-IP-PREP-007**。
- `refundAmount` > 当前账户余额 → EX-IP-PREP-005。
- 退费后余额 < 账户下限（**BR-ACC-001**；`adm_account` 无 `limit_account` 列时仅校验余额 **≥ 0**，见 §2.6 **EX-IP-PREP-006**）→ 领域抛 **005**；**006 本期不单独分支**。
- **不**校验 `relation_id` / 原收款单（**BR-IP-PREP-006**）。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-PREP-001 | 参数非法 | 请选择患者并输入正确金额 |
| EX-IP-PREP-002 | 账户不存在 | 请先完成住院登记 |
| EX-IP-PREP-003 | 状态不允许 | 说明当前状态不可办理 |
| EX-IP-PREP-004 | 退款或结算失败 | 以流水为准，引导重试 |
| EX-IP-PREP-005 | 退费金额 > 余额 | 减小退费金额 |
| EX-IP-PREP-006 | 退费后低于账户下限 | 调整退费金额 |

**Mapper 接口方法签名**

- `toRefundAdvanceDepositCommand(RefundPrepayAdvanceDepositRequest, Long accountId, Long operatorId): RefundAdvanceDepositCommand`
- `toSettleInpatientPaymentCommand(Long paymentId): SettleInpatientPaymentCommand`
- `toRefundPrepayAdvanceDepositResponse(Long paymentId, BigDecimal accountBalance): RefundPrepayAdvanceDepositResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | Request | `encounterId`/`patientId`/`refundAmount`；≤0 → EX-IP-PREP-001 |
| 2 | Gate | — | `paymentDetails` | 非空；合计= `refundAmount`（§2.6）；否则 EX-IP-PREP-008/007 |
| 3 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | encounterId, `IMP` | 同 §3.4 |
| 4 | Pre | App 状态白名单 | `status_enum` | 同 §3.4 |
| 5 | Pre | `PatientEncounterConsistencyGate` | request.patientId vs encounter.patientId | 不一致 → EX-IP-PREP-001 |
| 6 | Pre | `AccountQueryService#getRegistrationAccount` | patientId, encounterId | 无户 → EX-IP-PREP-002 |
| 7 | Pre | App 余额门禁 | `refundAmount`, `Account.balance`（库内余额） | 若 `refundAmount` > 库内余额 → EX-IP-PREP-005 |
| 8 | Asm | `InpatientPrepayMapper#toRefundAdvanceDepositCommand` | Request + accountId | 头 `payment_enum=UN_PAY`，**无** relationId |
| 9 | Cmd | `PaymentReconciliationDomainService#refundAdvanceDeposit` | `RefundAdvanceDepositCommand` | **写库点 1**：DRAFT；`AdvanceDepositRefundedEvent` |
| 10 | Cmd | `PaymentReconciliationDomainService#settleInpatientPayment` | `SettleInpatientPaymentCommand` | **写库点 2**：SUCCESS |
| 11 | Cmd | `AccountDomainService#adjustAdvanceDepositBalance` | `AdjustAdvanceDepositBalanceCommand`：`balanceDelta=-refundAmount` | **写库点 3**：余额 -=；不足 → EX-IP-PREP-005 |
| 12 | Asm | `InpatientPrepayMapper#toRefundPrepayAdvanceDepositResponse` | paymentId, 新余额 | 返回响应 |

## 4. QueryService 与事件刷新

### 4.1 在院队列（IF-IP-PREP-01）

| 维度 | 说明 |
| --- | --- |
| 主QueryService | `InpatientEncounterContextQueryModel`（``adm_encounter` + `InpatientEncounterContextQueryModel``） |
| 状态过滤 | 读侧 `pageInpatientPrepayQueue` 内置四态（**Q-IPI-004**；**Q-IPI-004** `pageInpatientPrepayQueue` 文档已闭合） |
| 展示余额 | `accountBalance` = `Account.balance` − **待结算合计**（`ChargeItemQueryService` Criteria **`SUM`+`GROUP BY`**，§2.6）；**不**直出 Account 净值；**不**用QueryService `total_amount` 扣减；IF-01 **批量** `mapRegistrationAccountsByEncounterIds` + `mapPendingSettlementTotalPriceByEncounterIds` |
| 关键字 | `patient_name` / `encounter_bus_no` / `bed_name`（QueryService §3.3） |
| 欠费/不足标记 | **不**在 IF-IP-PREP-01 出参返回（AS §1.3 / AS §1.3（废止 `arrearsAlert`） 已撤销） |

### 4.2 事件 → QueryService / 日结（本场景相关）

| 事件 | 触发 IF | 消费语义 |
| --- | --- | --- |
| `AdvanceDepositCollectedEvent` | IF-IP-PREP-03 序号 7 | `PaymentReconciliationQueryService / ChargeItemQueryService` 日结押金累加；`InpatientEncounterContextQueryModel` 费用摘要（DM §6.2） |
| `AdvanceDepositRefundedEvent` | IF-IP-PREP-04 序号 8 | 日结押金扣减 |
| `InpatientPaymentSettledEvent` | IF-IP-PREP-03/04 序号 8/9 | 流水 SUCCESS 展示 |
| `AccountBalanceEditedEvent` | IF-IP-PREP-03/04 序号 9/10 | 患者栏余额 |

字段级映射以 `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §4.2 为唯一真源；本 SDD 不重复字段表。

### 4.3 流水列表（IF-IP-PREP-02）

- 数据源：`PaymentReconciliationQueryService#findPaymentsByEncounter` + App 过滤 **`PaymentKind#allowsEmptyChargeItemIds()`**（`HOSPITAL_DEPOSIT`；含历史 `PRE_DEPOSIT` 同义）。
- 排序：`bill_date` 降序，同秒按 `paymentId` 降序。
- `operatorName` / `payMethodText`：§2.6。
- **`patientId` 可选**：前端可仅传 `encounterId`；传 `patientId` 时须与就诊一致。
- 不要求选中流水才能退费（AS §4 IF-IP-PREP-04）。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | Cmd 序号（§3） | 领域调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-IP-PREP-03 | `collectPrepayAdvanceDeposit` | 7 | `collectAdvanceDeposit` | PaymentReconciliation（DRAFT）+ Detail |
| IF-IP-PREP-03 | 同上 | 8 | `settleInpatientPayment` | PaymentReconciliation → SUCCESS |
| IF-IP-PREP-03 | 同上 | 9 | `adjustAdvanceDepositBalance` | Account 余额增加（`balanceDelta>0`） |
| IF-IP-PREP-04 | `refundPrepayAdvanceDeposit` | 8 | `refundAdvanceDeposit` | PaymentReconciliation（DRAFT，无 relation_id） |
| IF-IP-PREP-04 | 同上 | 9 | `settleInpatientPayment` | SUCCESS |
| IF-IP-PREP-04 | 同上 | 10 | `adjustAdvanceDepositBalance` | Account 余额减少（`balanceDelta<0`） |

### 5.2 事务边界与失败回滚

| IF | 事务策略 | 失败回滚 |
| --- | --- | --- |
| IF-IP-PREP-03 | **产品期望**：序号 7～9 同一业务事务 | 任一步失败则支付不得对外展示 SUCCESS；余额不得增加 |
| IF-IP-PREP-04 | **产品期望**：序号 8～10 同一业务事务 | 任一步失败则不得仅留 DRAFT 且余额已减 |
| IF-IP-PREP-01/02 | 无事务 | 只读 |

**DOC-CONFLICT（成码口径）**

| 项 | SDD/AS 期望 | 成码实现 |
| --- | --- | --- |
| App 层 `@Transactional` | §5.2「单事务」 | **禁止**（《代码生成通用规范》）；`InpatientPrepayAppService` **不**声明事务注解 |
| 实际事务边界 | — | 由 `PaymentReconciliationDomainService`、`AccountDomainService` 各写方法承担；余额编排紧接 settle **同请求顺序**执行 |
| 关闭条件 | 若强制跨聚合原子回滚 | 领域层或统一编排门面补事务，不在 App 加 `@Transactional` |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 短时重复提交收/退：以支付单号/业务流水防双账 | `PaymentReconciliationDomainService` | §3.4 序号 7～8；§3.5 序号 8～9 |
| 三方回调单独幂等 | 渠道适配层（后续） | §3.4 序号 11 |

### 6.2 并发控制

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 同一就诊并发收退：以后写者余额校验为准 | `AccountDomainService#adjustAdvanceDepositBalance` | §3.4 序号 9；§3.5 序号 10 |

### 6.3 重试与容错

| 承诺 | 说明 |
| --- | --- |
| 结算失败不得对外成功 | 序号 8/9 失败时返回 EX-IP-PREP-004；前端以流水为准 |
| QueryService最终一致 | 队列/流水刷新允许QueryService 刷新延迟 |

### 6.4 数据脱敏

| 承诺 | 说明 |
| --- | --- |
| 患者信息展示 | 与机构脱敏规则一致（可选 Asm） |

### 6.5 审计日志

| 承诺 | 关联步骤 |
| --- | --- |
| `entererId`/`operatorId` 写入支付命令 | §3.4 序号 6～7；§3.5 序号 7～8 |
| 退费金额、渠道可追溯 | §3.5 全流程；**不要求** relation_id |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 优先级 | 测试层级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| 队列含待入科/已入科/待出院/待结算 | 可查询 | P0 | 集成 | 后端 |
| `PLANNED`/`DISCHARGED` 不可收退 | EX-IP-PREP-003 | P0 | 集成 | 后端 |
| 续收后余额与流水增加 | PAY + SUCCESS | P0 | 集成 | 后端 |
| 部分退费 ≤ 余额 | 余额减少、UN_PAY 流水 | P0 | 集成 | 后端 |
| 退费 > 余额 | EX-IP-PREP-005 | P0 | 单元 | 后端 |
| 未选患者收/退 | EX-IP-PREP-001 | P0 | 单元 | 后端 |
| 登记取消前须退清 | 与 **AS-IP-FIN-001** 联动 | P1 | 集成 | 后端 |
| `COMPLETED` 待清床队列可见、只读查流水 | IF-IP-PREP-01/02 可查；IF-IP-PREP-03/04 拒绝 | P0 | 集成 | 后端 |
| IF-IP-PREP-02 仅传 `encounterId` | 未传 `patientId` 时不做门控，返回 `HOSPITAL_DEPOSIT` 流水 | P0 | 集成 | 后端 |

### 7.2 非功能测试

| 用例 | 指标 | 优先级 | 测试层级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| 队列查询 | ≤1000ms | P1 | 性能 | 后端 |
| 单笔收/退 | ≤2000ms（不含三方） | P1 | 性能 | 后端 |

### 7.3 QueryService一致性

| 用例 | 预期 | 优先级 | 测试层级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| 收/退后刷新队列余额 | 与展示余额公式一致 | P0 | 集成 | 后端 |
| 流水表展示 SUCCESS | QueryService 刷新延迟可接受 | P1 | 集成 | 后端 |

### 7.4 集成测试

| 用例 | 预期 | 优先级 | 测试层级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| 登记 → 本页续收 | 首笔在 REG-03，续收在 PREP-03 | P0 | E2E | 前后端 |
| 退清后登记取消 | BR-IP-REG-009 | P1 | E2E | 前后端 |

### 7.5 测试缺口跟踪

| 缺口 | 状态 | 说明 | 优先级 |
| --- | --- | --- | --- |
| `pageInpatientPrepayQueue` 读侧 | **已完成** | **`EncounterContextReader#pageInpatientPrepayQueue`** + 四态/入院日/关键字；集成用例随 App IF-01 一并补 | — |
| `InpatientPrepayAppService` 契约测试 | **待补** | 建议 Mock 领域 Bean，覆盖 Gate（007/008）、状态白名单、批量账户索引 | P0 |
| `InpatientPrepayAppService` 集成测试 | **待补** | SpringBootTest：登记开户 → IF-03 续收 → IF-04 部分退 → 队列余额与流水一致 | P0 |
| 登记首笔明细合计 | **已完成** | `InpatientRegistrationAppService` 校验 `sum(details)=advanceDepositAmount`（EX-IP-REG-001 文案） | — |
| 前端 E2E | **API 已对接** | `apps/web-ele/src/api/inpatient-prepay.ts` + 预交金页；待后端 IF 就绪后跑 §7.4「登记→续收」 | P1 |

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.21 | 2026-10-13 | **BR-IP-PREP-001**：`applyPrepayEligibleStatus` 不在院三态须 EXISTS 已收口预交金流水；对齐 **AS v2.7** |
| v1.20 | 2026-10-13 | **代码先行回写**：IF-IP-PREP-01 Request 以 `stayScope` 替代 `prepayQueueTab`；`NOT_IN_HOSPITAL` 含 `COMPLETED`+`DISCHARGED`+**`CANCELLED`（退院）**；`applyPrepayEligibleStatus` 资格态同步含 `CANCELLED`；对齐 **AS v2.6** |
| v1.19 | 2026-08-17 | **IF-IP-PREP-01**：Request 增 `prepayQueueTab`；`InpatientEncounterPageQuery#prepayQueueTab` → `applyPrepayEligibleStatus` 在院/出院二分；对齐 **AS v2.2** |
| v1.18 | 2026-06-25 | **IF-IP-PREP-00** `loadInpatientPrepayPageInit`：`paymentChannelOptions`（**ADR-PAY-001** / `PaymentChannelOptionProvider`） |
| v1.17 | 2026-06-20 | **support 落点对齐**：`InpatientAccountBalanceDisplayDtoApplier` → `module.common.assembler.inpatient`；§3.1 步骤 7 增补 Applier；对齐 **AS v1.14** |（已迁至 application.shared.inpatient）
| v1.16 | 2026-06-19 | §2.6 展示余额：**库内 balance − 待结算**；结算扣已结写入 balance；废弃 chargedOccupancy/settleAutoSupplement 展示公式 |
| v1.15 | 2026-06-19 | §2.6 展示余额对齐 FIN-003：**占用含 SETTLED** + **扣减 settleAutoSupplement**；与 `InpatientDisplayAccountBalanceAssembler` 三因子公式一致 |
| v1.14 | 2026-05-30 | IF-IP-PREP-02：`patientId` 可选；仅传时做 `PatientEncounterConsistencyGate`；`COMPLETED` 待清床可只读查流水 |
| v1.13 | 2026-05-30 | §2.3 拆分队列可见（含 `COMPLETED`）与收退写门禁；对齐 **AS-IP-FIN-002** v1.11 |
| v1.10 | 2026-05-28 | **六维对账**：`InpatientPrepayAppService` 四方法与 IF-IP-PREP-01～04 对齐；收/退 DRAFT+settle+`adjustAdvanceDepositBalance` 与代码一致；写路径无 App `@Transactional`（§8 DOC-CONFLICT） |
| v1.3.1 | 2026-05-28 | IF-IP-PREP-01：**`EncounterContextFacade#pageInpatientPrepayQueue`**（§2.1/§2.2/§3 序列） |（已迁至 EncounterContextReader / application.shared.encounter）
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.12 | 2026-05-24 | §2.6 展示余额：待结算合计对齐 **DM-ADM-011** Criteria **`SUM`+`GROUP BY`**（非明细行内存累加） |
| v1.11 | 2026-05-24 | 展示余额改为 **预交金 − 待结算合计**（`ChargeItemQueryService`）；`toPrepayQueueItemDto` 增 `pendingSettlementTotal` 入参 |
| v1.10 | 2026-05-20 | §2.6 增补队列 `patient_id` 兜底：`EncounterQueryService#mapPatientIdByEncounterIds`；对齐登记投影QueryService **v3.1** |
| v1.9 | 2026-05-20 | **Gap 收口**：删除 §2.5 SDD-GAP 表；改为领域能力核对；对齐 AS/SDD Skill |
| v1.8 | 2026-05-20 | §1.2 IMP `statusEnumText` 口径；队列 DTO 说明对齐 **DM-ADM-002** §2.3.2 / **AS-IP-FIN-001-SDD** §4.4 |
| v1.7 | 2026-05-20 | IF-IP-PREP-01：`InpatientInfoQueueDisplayAssembler` + `contractDisplay`；IF-IP-PREP-02：`mapDisplayNameByEntererIds`、`mapFirstPayEnumByPaymentIds`、`PaymentChannelDisplayAssembler` |
| v1.6 | 2026-05-19 | `accountBalance` 统一为展示余额（净值−累计费用）；退费上限仍用 Account.balance；`InpatientDisplayAccountBalanceAssembler`（common） |
| v1.5 | 2026-05-19 | IF-IP-PREP-01 废止 `arrearsAlert` 出参；AS §1.3（废止 `arrearsAlert`） 撤销；对齐 AS v1.7 |
| v1.4 | 2026-05-19 | 应用层 IF-01～04 已实现；§2.1 依赖与 §2.6 收付方向落库对齐代码；IF-02 补 `listPaymentRecDetailsByPaymentId` 与患者门控；§7.5 测试待补 |
| v1.3 | 2026-05-19 | §2.2/§2.5 标注 `pageInpatientPrepayQueue` 已实现；§2.6 补 006 口径与登记 007 已实现；§7.5 测试跟踪细化；前端 API 约定 |
| v1.2 | 2026-05-19 | 对齐成码：余额编排下沉 `AccountDomainService#adjustAdvanceDepositBalance`（`AdjustAdvanceDepositBalanceCommand`）；移除 `InpatientAdvanceDepositsupport |
| v1.1 | 2026-05-19 | §2.5～§2.6 闭合 **Q-IPI-004** `pageInpatientPrepayQueue`～003 及评审缺口：QueryService Q-IPI-004、明细合计、批量账户、操作员名；EX-IP-PREP-007 |
| v1.0 | 2026-05-19 | 初版：对齐 `AS-IP-FIN-002` v1.4；IF-IP-PREP-01～04；收/退 DRAFT+settle+账户余额更新；§2.5 **Q-IPI-004** `pageInpatientPrepayQueue`～003 |

---

## 8. 实现与存量差异（DOC-CONFLICT）

| 项 | 文档/AS 期望 | 当前实现 | 收敛建议 |
| --- | --- | --- | --- |
| 支付头 `payment_enum` | 本页 IF-03/04 落 **`PAY`/`UN_PAY`**（收付方向） | `InpatientPrepayMapperImpl` 已按 SDD 落库；渠道在明细 `pay_enum` | 与 AS §1.3 一致；**登记首笔**（AS-IP-FIN-001）仍将渠道写在头表，存量流水 `bizTypeText` 对首笔可能需按 `kind`+方向规则展示 |
| `payMethodText` 字典 | §2.6 | **已闭合（首期）**：`PaymentChannelDisplayAssembler` + `domain.share.constants.PaymentChannelConstants`；术语 `codeSystemName` 统一后可叠加 `TerminologyOptionLoader` |
| 应用层 `@Transactional` | §5.2 产品单事务 | 禁止 App 声明（通用规范） | 已由领域写服务分段承担；跨聚合强一致需领域门面 |

---

**文档负责人**：（待填） · **评审**：（待填）
