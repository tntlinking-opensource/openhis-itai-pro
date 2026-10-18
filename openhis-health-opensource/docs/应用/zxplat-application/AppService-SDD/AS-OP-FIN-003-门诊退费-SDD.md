# 门诊退费 · 应用服务详细设计（SDD）

> **编码**：AS-OP-FIN-003-SDD  
> **版本**：v1.0.16  
> **编排权威**：`AS-OP-FIN-003-门诊退费.md`（**v1.0.20**）  
> **说明**：本 SDD **不维护 Gap 表**；与 AS/DM/代码分叉时**回更 AS 或 DM**，不在本文登记实现 backlog。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊退费应用服务（收费员退费窗：队列、执行明细、执行前门禁、按支付执行退费、半退保留行账户切换与再结算） |
| **边界** | 编排 Encounter / ChargeItem / PaymentReconciliation / Account / MedicationRequest / DeviceRequest 及 **OutpatientEncounterContextQueryModel**、**BillingStatement** QueryService；**不**编排医生站申请（**AS-OP-DOR-004**）、**不**调用 `completeRegistrationCancelRefund`（退号 **IF-REG-10**） |
| **编排权威** | `AS-OP-FIN-003-门诊退费.md` §2 / §4 / §8.1 |
| **方法权威** | `DM-FIN-002`、`DM-ADM-011`、`DM-ADM-002`、`DM-MED-003`、`DM-WKF-003` 各 §6；**DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）**、**DM-FIN-002 §6.2 PaymentReconciliationQueryService** §3 |
| **严格对齐** | IF 编号 **IF-REF-01～06** 与 AS 一致 |
| **实现阶段口径** | **域读能力已对齐**（`pageEncounterIdsForOutpatientRefundQueue`、`listByRefundMedicineIdIn`、`listByRefundDeviceIdIn` 等，见 **DM-ADM-011 §6.2.12**、**DM-MED-003 §6.2.6**、**DM-WKF-003 §6.2.8**）。**应用层**已落盘于 `outpatient/financial/refund`（与 **AS-OP-FIN-003** 财务域分包一致），`OutpatientRefundAppService` 及本章 request/response/dto/mapper/constants **已生成**，覆盖 **IF-REF-01～06**。 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段口径** | 枚举/状态持久化 **String `code`**（`getCode()`），**B.10.8.1** |
| **QueryService口径** | IF-REF-02：**`OutpatientChargeEncounterQueueLoader#pageEncounterIdsForOutpatientRefundQueue`** 收敛就诊 → **`EncounterContextReader#listOutpatientContextsByEncounterIdsOrdered`** 回补 → `PaymentReconciliationQueryService#findPaymentsByEncounter` 打 **BR-REF-002** 标签 |
| **执行明细口径** | IF-REF-03：`PaymentReconciliationQueryService` 解析支付快照 + `ChargeItemQueryService` 装载 `REFUNDING` / 保留 `PLANNED`；`filterOutSupersededSettledLines`、`mapPreservedNewChargeItemIdBySourceId`（**BR-REF-020**） |
| **半退再结算** | IF-REF-05 步骤 **12～13** **委托** `OutpatientChargeAppService#preSettleOutpatientCharges` / `#confirmOutpatientChargeSettlement`（与 **AS-OP-FIN-002** **IF-OPC-06/07** 同域语义，避免重复编排） |
| **事务口径** | `OutpatientRefundAppService` **禁止** `@Transactional`（《代码生成通用规范》）；Gate/Pre 失败禁止 Cmd |
| **HTTP** | `AppServiceControllerRegistrar`：`POST /api/app/outpatientRefund/{methodName}` |
| **分页与队列** | `AbstractPageQuery` + `PagedResult.pageNo` **1-based**；**禁止** Request 传 `organizationId`；**`OutpatientChargeEncounterQueueLoader`** 按日期窗、关键字筛选，**不按**会话机构收窄（**B.10.11** / **B.6.5**）；写路径 `operatorId` 由 **`CurrentOperatorResolver`** 解析 |
| **多笔支付** | **IF-REF-05** 每次请求仅处理**一个** `originalPaymentId`；同就诊多支付由前端**逐笔**调用 |
| **逆向明细账户** | **IF-REF-05**：`RefundPaymentDetailSubmitDto` **不传** `accountId`；`OutpatientRefundAppService#attachOriginalPaymentAccountsToRefundPaymentDetails` 在 `requestOutpatientRefund` 前自 `listPaymentRecDetailsByPaymentId` 回填，语义对齐 **IF-OPC-07** `attachDraftAccountsToPayReceiptDetails`（**DM-FIN-002** `fin_payment_rec_detail.account_id` 非空） |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/financial/refund/
  service/
    OutpatientRefundAppService.java
  request/
    LoadOutpatientRefundPageInitRequest.java
    PageOutpatientRefundQueueRequest.java
    LoadOutpatientRefundExecutionDetailRequest.java
    VerifyRefundableBeforeExecutionRequest.java
    ExecuteOutpatientBillRefundByPaymentRequest.java
    SwitchPreservedChargeItemsToInsuranceRequest.java
    SwitchPreservedChargeItemsToSelfPayRequest.java
  response/
    LoadOutpatientRefundPageInitResponse.java
    PageOutpatientRefundQueueResponse.java
    LoadOutpatientRefundExecutionDetailResponse.java
    VerifyRefundableBeforeExecutionResponse.java
    ExecuteOutpatientBillRefundByPaymentResponse.java
    SwitchPreservedChargeItemsAccountResponse.java
  dto/
    OutpatientRefundQueueRowDto.java
    OutpatientRefundPaymentGroupDto.java
    OutpatientRefundLineItemDto.java
    RefundPaymentDetailSubmitDto.java
  mapper/
    OutpatientRefundMapper.java
    OutpatientRefundMapperImpl.java
```

> 包名：`com.zxplat.domain.application.outpatient.financial.refund`（与 **AS-OP-FIN-003**、同级 **AS-OP-FIN-001** 挂号 `financial/registration`、**AS-OP-FIN-002** 收费 `financial/charge` 一致）。  
> **编排常量**（《代码生成通用规范》**B.10.2**）：`OutpatientRefundConstants`、`OutpatientRefundQueueTab` 落在 **`zxplat-domain`** → `com.zxplat.domain.share.constants`（与 `OutpatientChargeConstants` / `OutpatientChargeQueueTab` 同构），**不在** `health-application` 下建 `constants` 子包。

> `OptionDto` 使用公共库，不在本模块 `dto` 声明。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径操作人（`operatorId`，B.6.1） |
| `CodeSystemQueryService` | IF-REF-01：收费项状态筛选项 |
| `EncounterQueryService` | `mustGetEncounter` |
| `ChargeItemQueryService` | 队列分页、明细装载、**BR-REF-020** 工具方法、执行 id 解析 |
| `ChargeItemDomainService` | `completeOutpatientBillRefund`、`switchChargeItemAccount` |
| `PaymentReconciliationQueryService` | `findPaymentsByEncounter`、`mustGetPayment`、`getChargeItemIdsByPayments` |
| `PaymentReconciliationDomainService` | `requestOutpatientRefund`、`completeOutpatientRefund`、`abortOutpatientRefundAttempt` |
| `MedicationRequestQueryService` | IF-REF-04：`listByRefundMedicineIdIn` |
| `DeviceRequestQueryService` | IF-REF-04：`listByRefundDeviceIdIn` |
| `AccountQueryService` | IF-REF-06：解析医保/自费账户 |
| `EncounterContextReader` | IF-REF-02：`listOutpatientContextsByEncounterIdsOrdered` |
| `OutpatientChargeEncounterQueueLoader` | IF-REF-02：`pageEncounterIdsForOutpatientRefundQueue`（与收费队列同源 Loader） |
| `EncounterQueryService` | IF-REF-02～06 写前 `mustGetEncounter` 等 |
| `PaymentReconciliationQueryService` | IF-REF-02 队列标签 |
| `ChargeItemDefinitionQueryService` | IF-REF-03 项目名称回落 |
| `ProductDefinitionBatchLoader` | IF-REF-03 产品名回落 |
| `PractitionerQueryService` | IF-REF-03 收款人展示 |
| `OutpatientChargeAppService` | IF-REF-05 半退分支：pre+settle（**委托**，不重复 IF-OPC-06/07 编排） |
| `OutpatientTcmDecoctionRefundDoctorStationSync` | IF-REF-05 步骤 11a：代煎退款终态同步医生站（**BR-REF-025**） |
| `OutpatientRefundMapper` | Request/Response/DTO 装配 |
| `ThirdPartyPaymentExtension` | **可选**（`-Pwith-unionpay-bpc`）：IF-REF-05 扫码原路退；未装配时为 NoOp |
| `ThirdPartyPaymentRefundsupport | IF-REF-05：`clientRefundResult` 挂接（§3.6.1） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 收费明细状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §3.1 `status_enum`；**BR-REF-003** | 队列 **REFUNDING**；明细 **REFUNDING** / 保留 **PLANNED** |
| 支付主单状态 | `com.zxplat.domain.share.enums.PaymentStatus` | `DM-FIN-002` §2.3 / §3.1 `status_enum`；**BR-REF-005** | 原单恒 **SUCCESS**；逆向单 **REFUND_PROCESSING** → **REFUNDED**（失败 → **CANCELLED**） |
| 账户类型 | `com.zxplat.domain.share.enums.AccountTypeCode` | `DM-ADM-009`；**BR-REF-010** | IF-REF-06 |
| 支付结果 | `com.zxplat.domain.share.enums.PaymentOutcome` | `DM-FIN-002` §3.1 | `completeOutpatientRefund` 回写 |
| 表名常量 | `com.zxplat.domain.share.constants.TableNameConstants.ADM_HEALTHCARE_SERVICE` | `DM-ADM-011` **BR-CHG-001** | 队列/明细排除挂号锚点 |
| 队列页签 | `com.zxplat.domain.share.constants.OutpatientRefundQueueTab` | **AS** IF-REF-02；**BR-REF-002** | `ALL` / `PENDING` / `SETTLED` |
| 应用语义码 | `com.zxplat.domain.share.constants.OutpatientRefundConstants` | **AS** §5 **EX-REF-*** | 编排层业务消息码前缀 |
| 业务异常 | `com.zxplat.domain.share.constants.ErrorCodeConstants` | **AS** §5；各 DM §6 | 映射 **EX-REF-*** |

### 2.4 领域方法与 Mapper 白名单

**领域查询**

| 领域 / 聚合 | 类型 | 方法 | 用于 IF |
| --- | --- | --- | --- |
| Encounter | `EncounterQueryService` | `mustGetEncounter` | IF-REF-02～06 |
| ChargeItem | `ChargeItemQueryService` | `pageEncounterIdsForOutpatientRefundQueue` | IF-REF-02 |
| ChargeItem | `ChargeItemQueryService` | `loadChargeItemsByIds` | IF-REF-03～05 |
| ChargeItem | `ChargeItemQueryService` | `listChargeItemsByEncounterId` | IF-REF-03 / IF-REF-06 |
| ChargeItem | `ChargeItemQueryService` | `filterOutSupersededSettledLines` | IF-REF-03 |
| ChargeItem | `ChargeItemQueryService` | `mapPreservedNewChargeItemIdBySourceId` | IF-REF-03 / IF-REF-05 |
| ChargeItem | `ChargeItemQueryService` | `resolveChargeItemIdsForOutpatientRefundBillCompletion` | IF-REF-05 |
| PaymentReconciliation | `PaymentReconciliationQueryService` | `findPaymentsByEncounter` | IF-REF-03 |
| PaymentReconciliation | `PaymentReconciliationQueryService` | `mustGetPayment` | IF-REF-05 |
| PaymentReconciliation | `PaymentReconciliationQueryService` | `listPaymentRecDetailsByPaymentId` | IF-REF-05（逆向明细账户回填） |
| PaymentReconciliation | `PaymentReconciliationQueryService` | `getChargeItemIdsByPayments` | IF-REF-03 / IF-REF-05 |
| MedicationRequest | `MedicationRequestQueryService` | `listByRefundMedicineIdIn` | IF-REF-04 |
| DeviceRequest | `DeviceRequestQueryService` | `listByRefundDeviceIdIn` | IF-REF-04 |
| Account | `AccountQueryService` | `getMedicalInsuranceAccount`、`getSelfPayAccount` | IF-REF-05（账户回落）/ IF-REF-06 |
| Account | `AccountQueryService` | `getAccountListByEncounter` | IF-REF-05（解析 `accountCode`） |
| 就诊上下文 | `EncounterContextReader` | `listOutpatientContextsByEncounterIdsOrdered` | IF-REF-02 |
| 收费/退费队列 | `OutpatientChargeEncounterQueueLoader` | `pageEncounterIdsForOutpatientRefundQueue` | IF-REF-02 |
| BillingStatement | `PaymentReconciliationQueryService` | `findPaymentsByEncounter` | IF-REF-02 |
| ChargeItemDefinition | `ChargeItemDefinitionQueryService` | `findAllDefinitionsByIdIn` | IF-REF-03 |
| ProductDefinition | `ProductDefinitionBatchLoader` | `loadAsMap` | IF-REF-03 |

**领域写**

| 领域 / 聚合 | 类型 | 方法 | 用于 IF |
| --- | --- | --- | --- |
| PaymentReconciliation | `PaymentReconciliationDomainService` | `requestOutpatientRefund` | IF-REF-05 |
| PaymentReconciliation | `PaymentReconciliationDomainService` | `completeOutpatientRefund` | IF-REF-05 |
| PaymentReconciliation | `PaymentReconciliationDomainService` | `abortOutpatientRefundAttempt` | IF-REF-05 失败回滚 |
| ServiceRequest | `ServiceRequestDomainService` | `softDeleteStoppedServiceRequestAfterDecoctionRefunded` | IF-REF-05（代煎 **BR-REF-025**） |
| MedicationRequest | `MedicationRequestDomainService` | `clearSufferingFlagAfterDecoctionVoided` | IF-REF-05（代煎 **BR-REF-025**） |
| ChargeItem | `ChargeItemDomainService` | `switchChargeItemAccount` | IF-REF-06 |

**应用层委托（非领域白名单，编排序列显式出现）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientChargeAppService` | `preSettleOutpatientCharges` | IF-REF-05（半退） |
| `OutpatientChargeAppService` | `confirmOutpatientChargeSettlement` | IF-REF-05（半退） |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientRefundMapper` | `toLoadOutpatientRefundPageInitResponse` | IF-REF-01 |
| `OutpatientRefundMapper` | `toPageOutpatientRefundQueueResponse` | IF-REF-02 |
| `OutpatientRefundMapper` | `toOutpatientRefundPaymentGroupDto` 等 | IF-REF-03 |
| `OutpatientRefundMapper` | `toVerifyRefundableBeforeExecutionResponse` | IF-REF-04 |
| `OutpatientRefundMapper` | `toRequestOutpatientRefundCommand` 等 | IF-REF-05 |
| `OutpatientRefundMapper` | `toExecuteOutpatientBillRefundByPaymentResponse` | IF-REF-05 |
| `OutpatientRefundMapper` | `toSwitchPreservedChargeItemsAccountResponse` | IF-REF-06 |

### 2.5 白名单双向一致性复核

| 复核项 | 结论 |
| --- | --- |
| 正向 | PASS（含目标态查询方法，§1.1 已标注） |
| 反向 | PASS |

---

## 3. 按接口 ID 命名展开

§3 导语：**Gate / Pre 任一失败不得进入 Cmd**；记法 `Type#method`。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-REF-01 | `loadOutpatientRefundPageInit` | 加载退费页初始化 | `POST /api/app/outpatientRefund/loadOutpatientRefundPageInit` |
| IF-REF-02 | `pageOutpatientRefundQueue` | 分页查询待退费患者队列 | `POST /api/app/outpatientRefund/pageOutpatientRefundQueue` |
| IF-REF-03 | `loadOutpatientRefundExecutionDetail` | 加载就诊待执行退费明细 | `POST /api/app/outpatientRefund/loadOutpatientRefundExecutionDetail` |
| IF-REF-04 | `verifyRefundableBeforeExecution` | 执行前可退门禁 | `POST /api/app/outpatientRefund/verifyRefundableBeforeExecution` |
| IF-REF-05 | `executeOutpatientBillRefundByPayment` | 按原支付执行门诊账单退费 | `POST /api/app/outpatientRefund/executeOutpatientBillRefundByPayment` |
| IF-REF-06 | `switchPreservedChargeItemsToInsurance` | 半退保留行转医保 | `POST /api/app/outpatientRefund/switchPreservedChargeItemsToInsurance` |
| IF-REF-06 | `switchPreservedChargeItemsToSelfPay` | 半退保留行转自费 | `POST /api/app/outpatientRefund/switchPreservedChargeItemsToSelfPay` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数非空、就诊/支付键合法 | 停止 |
| **Pre** | 只读装载与 BR 门禁 | 抛异常，不进 Cmd |
| **Cmd** | 领域写 / 委托收费再结算 | 域事务回滚 |
| **Asm** | Mapper 装配 Response | — |

---

### 3.2 IF-REF-01 `loadOutpatientRefundPageInit`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientRefund/loadOutpatientRefundPageInit` |

**Request `LoadOutpatientRefundPageInitRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| — | — | — | 空体或占位 |

**Response `LoadOutpatientRefundPageInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chargeItemStatusOptions` | `List<OptionDto>` | 筛选项：`REFUNDING`、`REFUNDED` 等（**不含**收银 `PLANNED`/`SETTLED` 口径） |
| `paymentChannelOptions` | `List<OptionDto>` | 用户可选支付渠道（**ADR-PAY-001**；`PaymentChannelOptionProvider`） |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Pre | `CodeSystemQueryService#getOptionListByEnum(ChargeItemStatus.class)` | 过滤为退费窗用语义子集 |
| 2 | Pre | `PaymentChannelOptionProvider#loadUserSelectableOptions` | 与门诊收费/挂号口径一致 |
| 3 | Asm | `OutpatientRefundMapper#toLoadOutpatientRefundPageInitResponse` | 装配状态下拉与支付渠道 |

**失败码**：EX-REF-101 → 装配失败。

---

### 3.3 IF-REF-02 `pageOutpatientRefundQueue`

**Request `PageOutpatientRefundQueueRequest`**（extends `AbstractPageQuery` 字段：`pageNo`、`pageSize`、`keyword`、`startDate`、`endDate`；**无** `organizationId`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `queueTabCode` | String | Y | `ALL` / `PENDING` / `SETTLED`（`OutpatientRefundQueueTab`） |

**Response `PageOutpatientRefundQueueResponse`**（extends `PagedResult`，**B.10.11**）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<OutpatientRefundQueueRowDto>` | 当前页数据 |
| `totalCount` | long | 总条数 |
| `pageNo` | int | 1-based |
| `pageSize` | int | 页大小 |

**DTO `OutpatientRefundQueueRowDto`（核心字段）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊 |
| `patientName` | String | 展示 |
| `queueLabelCode` | String | `PENDING` / `SETTLED`（**BR-REF-002**） |
| `chargeItemStatusSummary` | String | 可选摘要 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | — | `pageNo`/`pageSize` 合法 |
| 2 | Pre | **`OutpatientChargeEncounterQueueLoader#pageEncounterIdsForOutpatientRefundQueue`** | **BR-REF-001**；`OutpatientChargeQueueEncounterQuery` + `queueTabCode` |
| 3 | Pre | **`EncounterContextReader#listOutpatientContextsByEncounterIdsOrdered`** | 本页 encounterIds 顺序列表 |
| 4 | Pre | `PaymentReconciliationQueryService#findPaymentsByEncounter` | 按就诊打 **BR-REF-002** 标签 |
| 5 | Pre | — | 按 `queueTabCode` 过滤当前页（应用层） |
| 6 | Asm | `OutpatientRefundMapper#toPageOutpatientRefundQueueResponse` | — |

**失败码**：EX-REF-102。

---

### 3.4 IF-REF-03 `loadOutpatientRefundExecutionDetail`

**Request**

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `encounterId` | Long | Y |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊主键 |
| `paymentGroups` | `List<OutpatientRefundPaymentGroupDto>` | 按原支付分组 |

**DTO `OutpatientRefundPaymentGroupDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentId` | Long | 原支付主单 |
| `paymentBusNo` | String | 展示 |
| `paymentStatusEnum` | String | `PaymentStatus#getCode()` |
| `paymentStatusEnumText` | String | 展示文案 |
| `entererUserId` | Long | 原支付 `enterer_id`（收款人系统用户） |
| `collectorName` | String | 收款人姓名（`PractitionerQueryService#findByUserId`） |
| `halfRefundOnPayment` | boolean | 同支付下既有拟退又有保留 |
| `refundingLines` | `List<OutpatientRefundLineItemDto>` | `REFUNDING` |
| `preservedLines` | `List<OutpatientRefundLineItemDto>` | 保留再生 `PLANNED` |
| `refundableAmount` | BigDecimal | 应退展示口径（原支付可退合计） |

**DTO `OutpatientRefundLineItemDto`（核心）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chargeItemId` | Long | 主键 |
| `paymentId` | Long | 锚点 |
| `chargeItemStatusEnum` | String | 状态 code |
| `chargeItemStatusEnumText` | String | 状态展示（`EnumUtil`） |
| `itemDisplayName` | String | 与 IF-OPC-03 回落一致 |
| `lineTotalAmount` | BigDecimal | 行金额 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `encounterId` 非空 | — |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | — |
| 3 | Pre | `PaymentReconciliationQueryService#findPaymentsByEncounter` | 筛选 `SUCCESS` / `PARTIAL_REFUNDED` |
| 4 | Pre | `getChargeItemIdsByPayments` + `loadChargeItemsByIds` | 装载快照行 |
| 5 | Pre | — | 筛 `REFUNDING`；`filterOutSupersededSettledLines`（**BR-REF-020**） |
| 6 | Pre | `mapPreservedNewChargeItemIdBySourceId` | 装配保留 `PLANNED`；缺失行 **批量** `loadChargeItemsByIds` |
| 7 | Pre | `PractitionerQueryService#findByUserId` | 原支付 `enterer_id` → `collectorName` |
| 8 | Asm | `OutpatientRefundMapper` 分组装配 | `halfRefundOnPayment` 计算 |

**失败码**：EX-REF-103。

---

### 3.5 IF-REF-04 `verifyRefundableBeforeExecution`

**Request**

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `encounterId` | Long | Y |
| `refundingChargeItemIds` | `List<Long>` | Y |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `allowed` | boolean | 是否可继续执行 |
| `rejectReasonCode` | String | 如 `EX-REF-201` |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 非空 | — |
| 2 | Pre | `loadChargeItemsByIds` + 就诊一致 | — |
| 3 | Pre | 解析药/耗 `serviceId` 列表 | — |
| 4 | Pre | `MedicationRequestQueryService#listByRefundMedicineIdIn` | 若存在关联退药单且状态为 **CANCELLED** 未完成冲减语义 → **EX-REF-201** |
| 5 | Pre | `DeviceRequestQueryService#listByRefundDeviceIdIn` | 耗材对称 → **EX-REF-202** |
| 6 | Asm | `toVerifyRefundableBeforeExecutionResponse(true, null)` | 通过 |

> **IF-REF-05** 须在步骤 3 **内联重复**或先调本 IF（默认 **内联**）。

---

### 3.6 IF-REF-05 `executeOutpatientBillRefundByPayment`（核心写）

**Request `ExecuteOutpatientBillRefundByPaymentRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊 |
| `originalPaymentId` | Long | Y | 原支付主单（单笔） |
| `refundingChargeItemIds` | `List<Long>` | Y | 本笔拟退行（须为 `REFUNDING`） |
| `preservedNewChargeItemIds` | `List<Long>` | N | 半退保留再生行；全退可空 |
| `refundPaymentDetails` | `List<RefundPaymentDetailSubmitDto>` | Y | 渠道实退拆分（**BR-REF-009**） |
| `billDate` | String | N | 账单日；默认当前；格式 `GeneralSharedConstants#DATE_TIME_PATTERN`（`yyyy-MM-dd HH:mm:ss`），亦兼容 ISO `T` 分隔 |

**DTO `RefundPaymentDetailSubmitDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payChannelCode` | String | Y | 退款渠道：原扫码段 **`WECHAT`/`ALIPAY`/`BANK_CARD`**（逆向明细）；现金 **`CASH`** |
| `amount` | BigDecimal | Y | 本渠道实退额 |

> **账户**：请求**不含** `accountId`/`accountCode`。步骤 **7a** 由应用层自原支付明细按单账户共用或按 `payEnum` 分桶写回 `PaymentDetailItemCommand`；仍缺失时按拟退收费行推断唯一账户；失败 **EX-REF-401**（`OutpatientRefundConstants.MESSAGE_CODE_ACCOUNT_NOT_FOUND`）。领域落库前须满足 **PaymentRecDetail** 账户非空自检。

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 成功 |
| `reversePaymentId` | Long | 逆向支付单 |
| `originalPaymentId` | Long | 原单 |
| `halfRefundExecuted` | boolean | 是否执行了再结算分支 |

**领域编排序列（严格顺序，BR-REF-006）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | — | 字段非空；`refundingChargeItemIds` 非空 |
| 1a | Pre | `CurrentOperatorResolver#requireOperatorId` | — | 写路径 `operatorId`（步骤 7～12） |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | — |
| 3 | Pre | `PaymentReconciliationQueryService#mustGetPayment(originalPaymentId)` | 就诊一致；原单可受理（**BR-REF-005/008**） |
| 4 | Pre | `loadChargeItemsByIds(refundingChargeItemIds)` | 均为 **REFUNDING** |
| 5 | Pre | IF-REF-04 门禁逻辑 | 不通过 → **EX-REF-201/202** |
| 6 | Pre | — | 校验 `refundPaymentDetails` 合计与应退现金（**BR-REF-009**） |
| 6a | Gate | — | **BR-REF-021**：同一支付全部 `REFUNDING` 须同批执行；`PARTIAL_REFUNDED` 拒绝（**EX-REF-305**） |
| 6b | Gate | — | **BR-REF-011**：渠道拆分须与原支付明细一致（**EX-REF-306**） |
| 6c | Gate | `listPaymentRecDetailsByPaymentId` + 收费项合计勾稽 | **BR-REF-024**：原支付明细合计须等于挂载收费项合计；失败 **EX-REF-307**（对齐 **BR-OPC-024**） |
| 7a | Pre | `PaymentReconciliationQueryService#listPaymentRecDetailsByPaymentId` + `OutpatientRefundAppService#attachOriginalPaymentAccountsToRefundPaymentDetails` | Mapper 仅渠道/金额；写回每行 `accountId`/`accountCode` 后再调域 **requestOutpatientRefund** |
| 8 | Cmd | `PaymentReconciliationDomainService#requestOutpatientRefund` | 得 **reversePaymentId**（`REFUND_PROCESSING`+`relation_id`）；原单保持 **SUCCESS** |
| 9 | Cmd | `PaymentReconciliationDomainService#completeOutpatientRefund` | **逆向单**完成，**唯一** `OutpatientRefundCompletedEvent` |
| 10 | Pre | `ChargeItemQueryService#resolveChargeItemIdsForOutpatientRefundBillCompletion` | 快照 + 拟退 + 被替代源 **SETTLED** |
| 11 | Cmd | `ChargeItemDomainService#completeOutpatientBillRefund` | `BatchChargeItemIdsCommand`（`operatorId`+`billDate` → ChargeItem `enterer_*`，**BR-REF-023** / **BR-OPC-016**） |
| 11a | Cmd | `OutpatientTcmDecoctionRefundDoctorStationSync#syncAfterDecoctionChargesRefunded` | 本批含代煎时：软删 **STOPPED** companion + 清方内 `suffering_flag`（**BR-REF-025** / **BR-TCM-RX-009**） |
| 12 | Cmd | **若** `preservedNewChargeItemIds` 非空：`OutpatientChargeAppService#preSettleOutpatientCharges` | **BR-REF-007**（产品半退） |
| 13 | Cmd | 同上：`OutpatientChargeAppService#confirmOutpatientChargeSettlement` | 渠道与预结算现金对齐 |
| 14 | Asm | `toExecuteOutpatientBillRefundByPaymentResponse` | **EX-REF-410** |
| — | Rollback | `abortOutpatientRefundAttempt(reversePaymentId)` | 步骤 8～13 任一步失败时调用 |

#### 3.6.1 扩展：BPC 扫码原路退（IF-REF-05 · 可选 Module）

> **真源**：`AS-OP-FIN-003` §8.1 · [zxplat-module-unionpay-bpc/03 §3.11](../../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md)

**Request 扩展**（`ExecuteOutpatientBillRefundByPaymentRequest` 顶层）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `clientRefundResult` | `ClientRefundResultDto` | 原扫码段退费时 Y | Win refund 回传；`refundAmountYuan` 须等于原扫码段；见 03 §3.11 |

**前端时序（IF-REF-05 之前，`submitScanRefund`）**：

1. `resolveScanRefundPath`：查 BPC；有 SUCCESS 消费单 → 须 Win R；消费单已 **REFUNDED** → `already_refunded`（跳过 Win，合成凭证）；无扫码单 → 普通退。
2. **金额**：一律取原单 `txnAmtFen`（元），**忽略**账单应退总额 / 页面展示合计（防混合支付误退全额）。
3. sessionStorage 缓存成功 `clientRefundResult`；命中时须与当前 BPC **merTradeNo + 扫码段金额**一致，否则清缓存重走。
4. Win 回报明确「已全额退款 / 订单已退货」→ 视为成功凭证；头寸/超额时仅当查单文案同样明确已退才幂等收口（禁止裸匹配「已退」）。
5. Win R（或幂等）成功后再提交 IF-REF-05；院内成功后 `clearCachedClientRefundResult`。

原支付经银联被扫时，在步骤 **6 与 7 之间**（与步骤 **7～12 同一事务传播**）：

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 6b | Pre | `clientRefundResult.respCode=00` | 非 00 → 整体失败 |
| 6c | Cmd | `ThirdPartyPaymentRefundSupport#applyRefundBeforeLocal` | `Extension#applyClientRefundResult`：退货分须等于原 `txnAmtFen`；原单已 REFUNDED 幂等（无 R 行则补记） |
| 6d | — | 6c 失败 | **不**进入步骤 7 |

**失败码**：EX-REF-301～304、**EX-REF-401**（无法解析结算账户）、EX-REF-399；金额不符 → `BPC_REFUND_AMOUNT_MISMATCH`。

---

### 3.7 IF-REF-06 `switchPreservedChargeItemsToInsurance` / `switchPreservedChargeItemsToSelfPay`

**Request**（两接口结构相同，目标账户不同）

| 字段 | 类型 | 必填 |
| --- | --- | --- |
| `encounterId` | Long | Y |

**Response `SwitchPreservedChargeItemsAccountResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 成功 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `encounterId` | — |
| 2 | Pre | `mustGetEncounter` | — |
| 3 | Pre | `listChargeItemsByEncounterId` + 筛 **PLANNED** 且 `refund_id` 非空 | 保留再生集合 |
| 3a | Pre | `OutpatientTcmDecoctionChargeItemRecognizer` 排除代煎保留行 | **BR-REF-010** / **BR-OPC-022**；仅代煎时幂等成功 |
| 4 | Gate | — | **若无行**（排除代煎后仍无）→ **EX-REF-403**（**AS v1.0.2**）；仅代煎保留行则幂等成功不抛 |
| 5 | Pre | `AccountQueryService` 解析目标账户 | 失败 **EX-REF-401** |
| 6 | Cmd | `ChargeItemDomainService#switchChargeItemAccount` | 仅非代煎保留行 id 集合 |
| 7 | Asm | `toSwitchPreservedChargeItemsAccountResponse` | — |

---

## 4. QueryService 与事件刷新

| 事件（语义，以 DM §5 为准） | 触发 IF | QueryService / 消费方 | 更新动作（摘要） |
| --- | --- | --- | --- |
| `OutpatientRefundRequestedEvent` | IF-REF-05 | 对账 | 逆向单建立（`REFUND_PROCESSING`） |
| `OutpatientRefundCompletedEvent`（**逆向单**） | IF-REF-05 | **日结/交班**、账单 | **仅计一次** |
| `OutpatientBillRefundCompletedEvent` | IF-REF-05 | OutpatientEncounterContextQueryModel、BillingStatement | 拟退 **REFUNDED** |
| 收费预结算/结算事件族 | IF-REF-05 半退 13～14 | 待收、发票 | 保留行 **SETTLED** |

> 原收费单保持 **SUCCESS**；收退展示以 `SUCCESS`（正）/ `REFUNDED`（负）区分（**AS-OP-FIN-005**）。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序（IF-REF-05）

| 序号（§3.6） | Domain 方法 | 聚合写入摘要 |
| --- | --- | --- |
| 7a | （应用 Pre，无独立 Cmd） | 逆向明细 `account_id` 回填 |
| 8 | `requestOutpatientRefund` | 新建逆向单 `REFUND_PROCESSING` + `relation_id` + 明细 |
| 9 | `completeOutpatientRefund` | 逆向单 → `REFUNDED` + 事件 |
| 11 | `completeOutpatientBillRefund` | 拟退/源行 → `REFUNDED` + `enterer_*`（**BR-REF-023**） |
| 11a | `softDeleteStoppedServiceRequestAfterDecoctionRefunded` + `clearSufferingFlagAfterDecoctionVoided` | 代煎行：STOPPED companion 软删 + 方头 `suffering_flag=false`（**BR-REF-025**） |
| 12～13 | 委托 `OutpatientChargeAppService` | 保留行 pre+settle + 新支付成功 |
| Rollback | `abortOutpatientRefundAttempt` | 逆向单 → `CANCELLED`，清空 `relation_id` |

### 5.2 事务边界

| IF | 约定 |
| --- | --- |
| IF-REF-05 | App **无** `@Transactional`；步骤 8～11 由域服务传播；12～13 与收费域同事务策略以 `OutpatientChargeAppService` 为准（建议与 OPC-07 同域默认传播，实现时保持单笔支付原子）；失败时 `abortOutpatientRefundAttempt` |
| IF-REF-06 | `switchChargeItemAccount` 单域写 |

---

## 6. 非功能性需求实现（承诺表）

| 承诺 | 落地要点 | 关联 |
| --- | --- | --- |
| 幂等 | 原单已有未删 `REFUND_PROCESSING`/`REFUNDED` 逆向单拒绝；`requestOutpatientRefund` 对原单悲观锁 + `uk_fin_payment_rec_active_unpay_relation` 兜底并发双写（**BR-REF-008** / **BR-PMR-008**）；编排失败逆向单 `CANCELLED`（清空 `relation_id`）后可重试 | IF-REF-05 |
| 并发 | `existsActiveOutpatientRefundByRelationId` 防双逆向单 | IF-REF-05 |
| 审计 | `operatorId` 来自 `CurrentOperatorResolver`；日志记 encounter/payment/行数 | 全写 IF |
| 性能 | IF-REF-02 ≤1500ms；IF-REF-03 ≤1200ms；IF-REF-05 ≤8000ms | **AS** §7 |

---

## 7. 测试要点

| 编号 | 场景 | 预期 | 优先级 |
| --- | --- | --- | --- |
| F-01 | 全退单笔支付 | 原单+逆向 **REFUNDED**；拟退 **REFUNDED**；日结事件 1 次 | P0 |
| F-02 | 半退 | 步骤 13～14 执行；保留 **SETTLED** | P0 |
| F-03 | IF-REF-04 退药未完成 | **EX-REF-201** | P0 |
| F-04 | 双支付分两次 IF-REF-05 | 两笔独立逆向单与事件 | P0 |
| F-05 | IF-REF-06 无保留行 | **EX-REF-403** | P1 |
| F-06 | BR-REF-020 | 明细无源 **SETTLED** 双行；收口含源行 | P0 |
| F-07 | IF-REF-05 仅提交渠道/金额 | 逆向明细带原支付 `account_id` 落库；无法解析时 **EX-REF-401** | P0 |
| F-08 | IF-REF-05 原支付含银联被扫 | `clientRefundResult` + applyClientRefundResult 与步骤 7～12 **同事务** | P0 |
| F-09 | IF-REF-05 BPC R 失败 | 步骤 7 不执行；IF-REF-05 整体失败、全回滚 | P0 |
| F-10 | 医保+扫码混合退费 | Win R 金额 = BPC `txnAmtFen`（非账单合计）；`applyClientRefund` 等额通过 | P0 |
| F-11 | Win R 成功、院内失败后重试 | 跳过重复 Win（缓存/已 REFUNDED）；带原凭证收口；成功清缓存 | P0 |
| F-12 | `clientRefundResult` 金额 ≠ 原扫码段 | `BPC_REFUND_AMOUNT_MISMATCH`；本地不退 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0.16 | 2026-09-06 | IF-REF-05 增步骤 **11a**：`OutpatientTcmDecoctionRefundDoctorStationSync`（**BR-REF-025**）；对齐 **AS v1.0.20** |
| v1.0.15 | 2026-08-26 | IF-REF-05 增步骤 **6c**：**BR-REF-024** / **EX-REF-307** 原支付明细与收费项勾稽；对齐 **AS v1.0.19** |
| v1.0.14 | 2026-08-05 | IF-REF-05：`requestOutpatientRefund` 并发防重（原单锁 + 部分唯一索引）；对齐 **AS v1.0.18** / **DM-FIN-002 v1.4.14** |
| v1.0.13 | 2026-08-04 | IF-REF-05：`completeOutpatientBillRefund` 写 ChargeItem `enterer_*`（**BR-REF-023**）；对齐 **AS v1.0.17** / **DM-ADM-011 v1.6.14** |
| v1.0.12 | 2026-08-02 | **IF-REF-06 / BR-REF-010**：半退保留行转账户排除门诊代煎；对齐 **AS v1.0.16**、**AS-OP-FIN-002 · BR-OPC-022** |
| v1.0.11 | 2026-08-01 | §3.6.1：扫码退货金额=BPC `txnAmtFen`；前端半成功缓存校验；`applyClientRefund` 等额/幂等；增 F-10～F-12；对齐 **AS v1.0.15** |
| v1.0.10 | 2026-07-09 | **BR-REF-021** 口径修正：分次退门禁；恢复产品半退步骤 12～13；`PARTIAL_REFUNDED` 拒绝 |
| v1.0.9 | 2026-07-09 | **BR-REF-021/011**：分次退门禁（**EX-REF-305**）；退费原路返回（**EX-REF-306**）；§3.6 增步骤 6a/6b |
| v1.0.8 | 2026-06-25 | IF-REF-01 响应增 `paymentChannelOptions`；Pre 调 `PaymentChannelOptionProvider`（**ADR-PAY-001**） |
| v1.0.7 | 2026-06-23 | §3.6.1：clientRefundResult + ThirdPartyPaymentRefundSupport；对齐 **AS v1.0.8** |
| v1.0.6 | 2026-06-23 | §3.6.1 BPC 原路退（P0、同事务 R）；payChannelCode 220100/220200/220300；对齐 **AS v1.0.7** |
| v1.0.5 | 2026-05-28 | IF-REF-02：**`OutpatientChargeEncounterQueueLoader`** + **`EncounterContextFacade`** 编排序列（替代直连 `ChargeItemQueryService`/`EncounterQueryService` 分页回补叙述） |（已迁至 EncounterContextReader / application.shared.encounter）
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0.4 | 2026-05-18 | **IF-REF-05** 增步骤 **7a**：`listPaymentRecDetailsByPaymentId` + `attachOriginalPaymentAccountsToRefundPaymentDetails`；DTO 明确请求不传账户；失败码补 **EX-REF-401**；对齐 **AS v1.0.4**。 |
| v1.0.0 | 2026-05-18 | 首版：对齐 **AS-OP-FIN-003 v1.0.2**；**IF-REF-01～06**；**IF-REF-05** 支付链 **BR-REF-006** + 半退委托 **IF-OPC-06/07**；**不**维护 SDD Gap 表。 |
| v1.0.2 | 2026-05-18 | 应用层包路径收口为 `outpatient/financial/refund`；§1.1 实现阶段改为**已生成**；编排常量迁至 `domain.share.constants`（**B.10.2**）。 |
| v1.0.3 | 2026-05-18 | 对齐实现：分页字段 `content`/`totalCount`；IF-REF-03 补 `entererUserId`/`collectorName` 与编排步骤；半退 `outcomeEnum` 缺失门禁。 |
