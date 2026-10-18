# 门诊收费 · 应用服务详细设计（SDD）

> 编码：AS-OP-FIN-002-SDD  
> 版本：v1.41 | 日期：2026-10-16 | 作者：（待填）
> 来源契约：`AS-OP-FIN-002-门诊收费.md` v1.45

## 1. 实现边界

### 1.1 目标与边界


| 项      | 内容                                                                                                                                                                                                                                                                      |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 模块     | 门诊收费应用服务（收费员工作站：队列、待收明细、账户切换、预结算、正式结算）                                                                                                                                                                                                                                  |
| 边界     | 编排 Encounter / Patient / Account / Contract / ChargeItem / PaymentReconciliation / Invoice 及 **OutpatientEncounterContextQueryModel**、**BillingStatement** QueryService查询；预结算诊断校验按收费行 **`encounter_diagnosis_ids`**（**不**依赖 Condition）；不在应用层发明聚合不变式                                                                                                |
| 编排权威   | `AS-OP-FIN-002-门诊收费.md` §2、§4、§8.1                                                                                                                                                                                                                                           |
| 方法权威   | 对应 DM §6（`*DomainService` / `*QueryService`）与 **DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）**、**DM-FIN-002 §6.2 PaymentReconciliationQueryService** §3                                                                                                                                                            |
| 严格对齐   | IF 编号严格对齐 AS：`IF-OPC-01`～`IF-OPC-08`                                                                                                                                                                                                                                    |
| 实现阶段口径 | **目标态全量契约**：与既有挂号收费的支付草稿/结算命令形态对齐（`CreatePaymentDraftFromPreSettleCommand` / `SettleOutpatientPaymentCommand`）；收费成功后 **就诊状态迁移语义** 在全门诊场景须与领域 DM **对齐扩展**（现行 `PaymentReconciliationDomainService#settleOutpatientPayment` 事件载荷中带就诊状态：挂号链路专用，门诊收费全场景实现时需按 DM/领域修订收口，见 §8） |


### 1.2 术语口径


| 项     | 约定                                                                                                                                                              |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 字段口径  | Request/Response 字段基于 AS 语义、DM 能力与QueryService §3「接口签名」；字典字段存 **code**（String / 枚举 `getCode()`）                                                                          |
| QueryService口径 | IF-OPC-02：**分页候选就诊**先由 `ChargeItemQueryService#pageEncounterIdsForOutpatientCashierQueue` 按收费事实收敛（**BR-OPC-014**），再 `EncounterQueryService#listOutpatientContextsByEncounterIdsOrdered` 回补展示字段；**队列标签**仍由 `BillingStatement` 按就诊关联判定 **待收费 / 已结算**（**BR-OPC-012**） |
| 待收明细排除锚点与状态白名单 | IF-OPC-02 摘要 / IF-OPC-03 / 04 / 05：`ChargeItemQueryCommand`（及队列批量查询路径）携带 **`excludedServiceTable`** 与 **`statusEnumIn`**（门诊收银固定 `PLANNED`、`SETTLED`，**不含 DRAFT**，**BR-OPC-010**）；`service_table <> adm_healthcare_service` 与 `status_enum in (...)` 在 **`ChargeItemRepository` + JPA `Specification`**（或等价批量查询）中下推，禁止应用层全量加载后内存过滤（**BR-OPC-013**；表名字面值对齐 `TableNameConstants.ADM_HEALTHCARE_SERVICE`） |
| 待收明细展示粒度 | IF-OPC-03 出参按 `ChargeItem` 事实一行一条（含 FIFO 跨批分段）；**不做**同价同品展示合并；前端收银台在「金额」前列示 `unitPrice` |
| 事务口径  | Gate/Pre 失败禁止进入 Cmd；同一正式结算事务默认包含「支付成功态 + 收费项已计费 + **BR-OPC-015 医嘱同步编排** + 票据草稿」；QueryService 由领域事件异步投影，**最终一致**（§4），与事务内同步写域并不矛盾 |
| 分页与队列收窄 | 领域侧 `OutpatientChargeQueueEncounterQuery` **extends `AbstractPageQuery`**；HTTP **`PageOutpatientChargeQueueRequest`** **不含** `organizationId`；**`OutpatientChargeEncounterQueueLoader`** 按日期窗、关键字等筛选，**不按**会话机构收窄（**B.6.5** / **B.10.11**）；写路径 `operatorId` 由 **`CurrentOperatorResolver`** 解析 |


## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/financial/charge/
  service/
    OutpatientChargeAppService.java
    OutpatientChargeAppService.java   # IF-OPC-01～07；**BR-OPC-015** 为 App 内 private `#syncAdviceStatesAfterChargeSettlement`（非独立 Service 类）
  request/
    LoadChargePageInitRequest.java
    PageOutpatientChargeQueueRequest.java
    LoadPendingChargeItemsRequest.java
    SwitchEncounterChargeItemsToInsuranceRequest.java
    SwitchEncounterChargeItemsToSelfPayRequest.java
    PreSettleOutpatientChargesRequest.java
    ConfirmOutpatientChargeSettlementRequest.java
  response/
    LoadChargePageInitResponse.java
    PageOutpatientChargeQueueResponse.java
    LoadPendingChargeItemsResponse.java
    SwitchEncounterChargeItemsAccountResponse.java
    PreSettleOutpatientChargesResponse.java
    ConfirmOutpatientChargeSettlementResponse.java
  dto/
    ChargeQueueRowDto.java
    ChargeQueueChargeItemBriefDto.java
    PendingChargeItemRowDto.java
    PaymentDetailSubmitDto.java
    PreSettleChargeLinePreviewDto.java
  support/
    OutpatientChargeEncounterQueueLoader.java
    ChargeReceiptPrintAssembler.java
    OutpatientChargeSettlementGate.java
    OutpatientPaymentDraftLifecycleOrchestrator.java
    OutpatientChargePrescriptionSnapshotStamper.java
  # 跨收费/挂号小票共用（application.shared.financial）
  # InsuranceReceiptAccountBalanceSupport.java — 小票个账余额=扣后（2207 balc 原样）
  mapper/
    OutpatientChargeMapper.java
    OutpatientChargeMapperImpl.java
```

> 包名：`com.zxplat.domain.application.outpatient.financial.charge`（门诊财务域 **AS-OP-FIN-002**，与 **AS-OP-FIN-001** `financial/registration`、**AS-OP-FIN-003** `financial/refund` 同级）。  
> **编排常量**（《代码生成通用规范》**B.10.2**）：`OutpatientChargeConstants`、`OutpatientChargeQueueTab` 落在 **`zxplat-domain`** → `com.zxplat.domain.share.constants`，**不在** `health-application` 下建 `constants` 子包。  
> **HTTP**：`POST /api/app/outpatientCharge/{methodName}`（类名路由，与 Java 包路径无关）。

### 2.1 依赖注入表


| 依赖                                   | 用途                                                                                         |
| ------------------------------------ | ------------------------------------------------------------------------------------------ |
| `CurrentOperatorResolver`            | 写路径操作人（`operatorId`/`entererId`，B.6.1）                                                      |
| `CodeSystemQueryService`             | IF-OPC-01：收费项状态下拉（`ChargeItemStatus` → `OptionDto`）                                        |
| `EncounterQueryService`              | 就诊存在性/摘要加载（写前 Gate）；IF-OPC-06 按收费行 **`encounterDiagnosisIds`** 有序 ids 串 加载 **`adm_encounter_diagnosis`** 并校验医疗类型一致（**BR-OPC-001/002**） |
| `AccountQueryService`                | 解析医保/自费账户主键（`getMedicalInsuranceAccount` / `getSelfPayAccount` 等，与 **DM-ADM-009** 一致）；IF-OPC-06 另用 `getAccountListByEncounter` 补全占位支付明细 `accountCode` |
| `ContractQueryService`               | 费用性质合同解析（预结算合同锚点）                                                                          |
| `ChargeItemDomainService`            | `preSettleChargeItems`、`switchChargeItemAccount`、`stampPrescriptionSnapshots`、`settleChargeItems`                       |
| `ChargeItemQueryService`             | `loadChargeItemsByIds`、`listChargeItemsByEncounterId`、`queryCostDetails`、`pageEncounterIdsForOutpatientCashierQueue`、`listOutpatientCashierChargeItemsForEncounters` |
| `PaymentReconciliationDomainService` | `createPaymentDraftFromPreSettle`、`settleOutpatientPayment`                                |
| `PaymentReconciliationQueryService`  | `mustGetPayment`、`listPaymentRecDetailsByPaymentId`、`findPaySuccessOrRefundingByEncounter` |
| `InvoiceAfterPaymentOrchestration`   | `createDraftAndScheduleIssueIfPayable`（正式收费后发票草稿，**BR-OPC-019** / **BR-INV-008**） |
| `InvoiceDomainService`               | `issueElectronicInvoice`（博思 Worker 回写）                                                |
| `OutpatientChargeAppService` | IF-OPC-07：私有方法 `#syncAdviceStatesAfterChargeSettlement`（收费项 **SETTLED** 后按锚点推进药/耗/诊申请与发放，**BR-OPC-015**） |
| `EncounterContextReader` | `listOutpatientContextsByEncounterIdsOrdered`（IF-OPC-02 回补展示） |
| `OutpatientChargeEncounterQueueLoader` | IF-OPC-02：`pageEncounterIdsForOutpatientCashierQueue`（`application.outpatient.financial.charge.support`） |
| `PaymentReconciliationQueryService`     | `findPaymentsByEncounter`（按 `encounter_id` 收敛账单行）                      |
| `OutpatientChargeMapper`             | DTO 装配；`**OptionDto`** 使用公共库（不在本模块 dto 声明）                                                 |
| `ThirdPartyPaymentExtension`         | **可选**（`-Pwith-unionpay-bpc`）：IF-OPC-07 扫码；未装配时为 NoOp                              |
| `ThirdPartyPaymentSettleSupport`         | IF-OPC-07：`clientScanResult` 挂接（§3.8.1）；**存量**，目标态 `integration/payment/orchestration` |


### 2.2 领域方法白名单


| 类型                                   | 方法                                                                                         | 用于 IF                         |
| ------------------------------------ | ------------------------------------------------------------------------------------------ | ----------------------------- |
| `CodeSystemQueryService`             | `getOptionListByEnum`（`ChargeItemStatus.class`）                                            | IF-OPC-01                     |
| `EncounterQueryService`              | `mustGetEncounter`；IF-OPC-06：`loadEncounterDiagnosesByIds` / `assertBindableEncounterDiagnosisIds`（由收费行 **`encounterDiagnosisIds`** 有序 ids 串 解析） | IF-OPC-03～07 / **06**                  |
| `EncounterContextReader` | `listOutpatientContextsByEncounterIdsOrdered` | IF-OPC-02 |
| `OutpatientChargeEncounterQueueLoader` | `pageEncounterIdsForOutpatientCashierQueue` | IF-OPC-02 |
| `PaymentReconciliationQueryService`     | `findPaymentsByEncounter` | IF-OPC-02 |
| `ChargeItemQueryService`             | `pageEncounterIdsForOutpatientCashierQueue`、`listOutpatientCashierChargeItemsForEncounters`、`listChargeItemsByEncounterId`、`loadChargeItemsByIds`、`queryCostDetails` | IF-OPC-02 / IF-OPC-03 / 04 / 05 / 06 / 07 |
| `ChargeItemDefinitionQueryService`   | `findAllDefinitionsByIdIn`                                                                 | IF-OPC-03 / 06                |
| `ProductDefinitionBatchLoader`    | `loadAsMap`、`buildProductCompositeKey`                                          | IF-OPC-03 / 06                |
| `PractitionerQueryService`           | `findByUserId`                                                                             | IF-OPC-03                     |
| `AccountQueryService`                | `hasDualChargeAccounts`、`getMedicalInsuranceAccount`、`getSelfPayAccount`、`getAccountListByEncounter`              | IF-OPC-03 / 04 / 05 / **06**       |
| `ContractQueryService`               | `findContractById`                                                                         | IF-OPC-06                     |
| `ChargeItemDomainService`            | `preSettleChargeItems`、`switchChargeItemAccount`、`stampPrescriptionSnapshots`、`settleChargeItems`                       | IF-OPC-04～07                  |
| `PaymentReconciliationQueryService`  | `mustGetPayment`、`listPaymentRecDetailsByPaymentId`、`findPaySuccessOrRefundingOverlappingChargeItems`（**07**）、`findPaySuccessOrRefundingByEncounter` | IF-OPC-06 / 07                |
| `PaymentReconciliationDomainService` | `createPaymentDraftFromPreSettle`、`settleOutpatientPayment`                                | IF-OPC-06 / 07                |
| `InvoiceAfterPaymentOrchestration`   | `createDraftAndScheduleIssueIfPayable`                                                     | IF-OPC-07                     |
| `MedicationRequestDomainService`     | `completeMedicationRequestAfterOutpatientCharge`                                           | IF-OPC-07（经 **App** `#syncAdviceStatesAfterChargeSettlement`） |
| `MedicationDispenseDomainService`    | `promoteToPreparation`                                                                     | IF-OPC-07（同上）               |
| `DeviceRequestDomainService`         | `completeDeviceRequestAfterOutpatientCharge`                                               | IF-OPC-07（同上）               |
| `DeviceDispenseDomainService`        | `promoteDraftDispensesForDeviceRequests`                                                   | IF-OPC-07（同上）               |
| `ServiceRequestDomainService`        | `completeServiceRequestAfterOutpatientCharge`                                              | IF-OPC-07（同上）               |


### 2.3 枚举、字典与值集（DM 与代码可追溯）


| 类别               | 代码实现锚点                                                                                                                                                                             | DM 追溯锚点                                                                  | 说明                                                                                 |
| ---------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| 收费项状态（初始化下拉）     | `com.zxplat.domain.share.enums.ChargeItemStatus`；`LoadChargePageInitResponse.chargeItemStatusOptions` 由 `CodeSystemQueryService#getOptionListByEnum` 装配为 `List<OptionDto>` | `DM-ADM-011-ChargeItem-收费明细` §2.3 状态机；§3.1 `adm_charge_item.status_enum` | IF-OPC-01；展示存 **code**                                                             |
| 就诊状态（队列QueryService行）     | `OutpatientEncounterContextQueryModel.statusEnum`                                                                                                                                               | `DM-ADM-002-Encounter-就诊` §3.1 `status_enum`；`OutpatientEncounterContextQueryModel` `EncounterQueryService` §2     | IF-OPC-02 过滤/展示                                                                    |
| 就诊类别             | `com.zxplat.domain.share.enums.EncounterClass`；`OutpatientEncounterPageQuery` 由 **RM 服务** 固定 `class_enum = AMB`                                                         | `DM-ADM-002` §3.1 `class_enum`；**OutpatientEncounterContextQueryModel** QueryService §3.4               | 与 Q-OPI-001 一致                                                                     |
| 账户类型             | `com.zxplat.domain.share.enums.AccountTypeCode`                                                                                                                            | `DM-ADM-009-Account-就诊账户` §3 / §4.3；BR-OPC-008                           | IF-OPC-04/05 解析目标账户                                                                |
| 支付状态 / 业务类型 / 结果 | `com.zxplat.domain.share.enums.PaymentStatus`；`PaymentKind`；`PaymentOutcome`（`SettleOutpatientPaymentCommand.outcomeEnum` 等）                                               | `DM-FIN-002-PaymentReconciliation-支付流水` §2.3、§3.1                        | 草稿/成功/明细回写                                                                         |
| 账单行支付状态与业务类      | `PaymentReconciliationQueryModel.statusEnum`；`PaymentReconciliationQueryModel.kindEnum`                                                                                                        | `BillingStatement账单-QueryService` §2；BR-OPC-012                                   | IF-OPC-02 待收/已结判定                                                                  |
| 队列筛选状态（应用枚举）     | `PageOutpatientChargeQueueRequest.queueTabCode`                                                                                                                                    | AS §4 IF-OPC-02；BR-OPC-012                                               | 取值：`ALL` / `PENDING` / `SETTLED`（`com.zxplat.domain.share.constants.OutpatientChargeQueueTab`） |
| 门诊收费共享常量       | `com.zxplat.domain.share.constants.OutpatientChargeConstants`                                                                                                                | —                                                                        | 失败/成功消息码、`PAYMENT_CHANNEL_CASHIER`、收银队列汇总 **`MIXED`**（`CASHIER_CHARGE_ROLLUP_MIXED_STATUS_*`）、待收费项名称占位 `PENDING_CHARGE_ITEM_DISPLAY_FALLBACK` 等 |


### 2.4 白名单双向一致性复核


| 复核项             | 结论   |
| --------------- | ---- |
| 正向（白名单 → §3 序列） | PASS |
| 反向（§3 序列 → 白名单） | PASS |


## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）


| IF        | 应用服务方法（英文）                              | 中文方法名         | HTTP                                                                   |
| --------- | --------------------------------------- | ------------- | ---------------------------------------------------------------------- |
| IF-OPC-01 | `loadChargePageInit`                    | 加载收费页初始化      | `POST /api/app/outpatientCharge/loadChargePageInit`                    |
| IF-OPC-02 | `pageOutpatientChargeQueue`             | 分页查询待收/已结队列   | `POST /api/app/outpatientCharge/pageOutpatientChargeQueue`             |
| IF-OPC-03 | `loadPendingChargeItems`                | 加载就诊待收明细      | `POST /api/app/outpatientCharge/loadPendingChargeItems`                |
| IF-OPC-04 | `switchEncounterChargeItemsToInsurance` | 转医保（批量切换记账账户） | `POST /api/app/outpatientCharge/switchEncounterChargeItemsToInsurance` |
| IF-OPC-05 | `switchEncounterChargeItemsToSelfPay`   | 转自费（批量切换记账账户） | `POST /api/app/outpatientCharge/switchEncounterChargeItemsToSelfPay`   |
| IF-OPC-06 | `preSettleOutpatientCharges`            | 门诊预结算         | `POST /api/app/outpatientCharge/preSettleOutpatientCharges`            |
| IF-OPC-07 | `confirmOutpatientChargeSettlement`     | 门诊正式结算（确认收费）  | `POST /api/app/outpatientCharge/confirmOutpatientChargeSettlement`     |
| IF-OPC-08 | `previewOutpatientChargeDiscounts`      | 收费列表折后价试算        | `POST /api/app/outpatientCharge/previewOutpatientChargeDiscounts`      |


### 3.1.1 编排约定（应用层如何实现 AS §4）


| 阶段       | 含义                                                     | 失败时                |
| -------- | ------------------------------------------------------ | ------------------ |
| **Gate** | 权限 / 租户 / 参数门禁                                         | 不再调用后续步骤           |
| **Pre**  | 写前只读：加载快照、状态与 BR 门禁（`*QueryService`、`*QueryService`） | 抛业务异常，**禁止进入 Cmd** |
| **Cmd**  | 领域写：`*DomainService`；持久化 + 领域事件                        | 事务回滚见 §5           |
| **Asm**  | `OutpatientChargeMapper` 纯 DTO 装配                      | —                  |


记法：`ChargeItemQueryService#pageEncounterIdsForOutpatientCashierQueue` 等在 `OutpatientChargeAppService` 中注入并调用。

### 3.2 IF-OPC-01 `loadChargePageInit`（加载收费页初始化）

**HTTP**：`POST /api/app/outpatientCharge/loadChargePageInit`

**Request**


| 字段               | 类型   | 必填  | 说明               |
| ---------------- | ---- | --- | ---------------- |
| `organizationId` | Long | N   | **（遗留字段，忽略）** 勿用于扩大查询范围 |


**Response**


| 字段                        | 类型                | 说明                               |
| ------------------------- | ----------------- | -------------------------------- |
| `chargeItemStatusOptions` | `List<OptionDto>` | 收费项状态下拉（与 `ChargeItemStatus` 同源） |
| `paymentChannelOptions` | `List<OptionDto>` | 用户可选支付渠道（**ADR-PAY-001**；`PaymentChannelOptionProvider`） |


**DTO 字段表：OptionDto**（公共库，非本模块定义）


| 字段         | 类型      | 必填  | 说明           |
| ---------- | ------- | --- | ------------ |
| `value`    | String  | Y   | 选项值（枚举 code） |
| `label`    | String  | Y   | 显示名          |
| `disabled` | Boolean | N   | 是否禁用         |


**关键校验**

- 初始化失败映射 **EX-OPC-101**（装配异常或术语服务不可用）。

**失败码口径表**


| 业务码        | 说明       |
| ---------- | -------- |
| EX-OPC-101 | 初始化数据不可用 |


**Mapper 接口方法签名**

- `toLoadChargePageInitResponse(List<OptionDto> chargeItemStatusOptions, List<OptionDto> paymentChannelOptions): LoadChargePageInitResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                    | 输入要点                     | 说明                 |
| --- | ---- | ----------------------------------------------------- | ------------------------ | ------------------ |
| 1   | Gate | 参数门禁                                                  | Request 结构合法（**忽略**遗留 `organizationId`） | 结构合法               |
| 2   | Pre  | `CodeSystemQueryService#getOptionListByEnum`          | `ChargeItemStatus.class` | 仅读；失败 → EX-OPC-101 |
| 3   | Pre  | `PaymentChannelOptionProvider#loadUserSelectableOptions` | — | 477 ∩ 枚举 ∩ 模块 Gate（**ADR-PAY-001**） |
| 4   | Asm  | `OutpatientChargeMapper#toLoadChargePageInitResponse` | ②③ 结果                     | 无领域写；**仅读路径**      |


### 3.3 IF-OPC-02 `pageOutpatientChargeQueue`（分页查询待收/已结队列）

**HTTP**：`POST /api/app/outpatientCharge/pageOutpatientChargeQueue`

**Request**


| 字段               | 类型              | 必填  | 说明                                    |
| ---------------- | --------------- | --- | ------------------------------------- |
| `keyword`        | String          | N   | 患者姓名/拼音/五笔/就诊业务号（Q-OPI-001 OR 语义）     |
| `startDate`      | `LocalDateTime` | N   | 挂号/收费日期起（过滤 `start_time`）             |
| `endDate`        | `LocalDateTime` | N   | 止                                     |
| `queueTabCode`   | String          | Y   | `ALL` / `PENDING` / `SETTLED`（见 §2.3） |
| `pageNo`         | Integer         | Y   | 页码（从 1 起，**B.10.11**）                |
| `pageSize`       | Integer         | Y   | 页大小                                   |


**Response**（`PageOutpatientChargeQueueResponse` 继承 **`PagedResult<ChargeQueueRowDto>`**）


| 字段        | 类型                        | 说明              |
| --------- | ------------------------- | --------------- |
| `content` | `List<ChargeQueueRowDto>` | 当前页队列行（含待收/已结展示标签） |
| `totalCount` | Long                   | 总条数（过滤后与 `content` 条数对齐，见实现注释） |
| `pageNo` | Integer | 当前页 |
| `pageSize` | Integer | 页大小 |


**DTO 字段表：ChargeQueueRowDto**


| 字段                 | 类型              | 必填  | 说明                                      |
| ------------------ | --------------- | --- | --------------------------------------- |
| `encounterId`      | Long            | Y   | 就诊 ID                                   |
| `encounterBusNo`   | String          | N   | 就诊业务号                                   |
| `patientId`        | Long            | Y   | 患者 ID                                   |
| `patientName`      | String          | Y   | 姓名                                      |
| `genderCode`       | String          | N   | 性别 code                                 |
| `genderEnumText`   | String          | N   | 性别展示（`AdministrativeGender.display`，由 `genderCode` 装配） |
| `ageText`          | String          | N   | 年龄文本                                    |
| `organizationName` | String          | N   | 科室                                      |
| `doctorName`       | String          | N   | 接诊医生                                    |
| `startTime`        | `LocalDateTime` | N   | 就诊开始时间                                  |
| `queueLabelCode`   | String          | Y   | `PENDING`：待收费；`SETTLED`：已结算（BR-OPC-012） |
| `totalAmount`      | `BigDecimal`    | N   | 费用摘要（QueryService 查询列，可选）                           |
| `paidAmount`       | `BigDecimal`    | N   | 已支付摘要                                   |
| `cashierChargeItems` | `List<ChargeQueueChargeItemBriefDto>` | N | 与本就诊 IF-OPC-03 同口径的收费明细摘要（**BR-OPC-014**） |
| `cashierChargeRollupStatusCode` | String | N | 本就诊收银明细 `ChargeItem.status` 汇总码：任一为 `PLANNED`→`PLANNED`；全非 `PLANNED` 且全为 `SETTLED`→`SETTLED`；否则为 **`MIXED`**（非枚举字面量，见 `OutpatientChargeConstants#CASHIER_CHARGE_ROLLUP_MIXED_STATUS_CODE`） |
| `cashierChargeRollupStatusText` | String | N | 汇总展示文案；`MIXED` 时为「费用状态不一致」（`OutpatientChargeConstants#CASHIER_CHARGE_ROLLUP_MIXED_STATUS_TEXT`） |

**DTO 字段表：ChargeQueueChargeItemBriefDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chargeItemId` | Long | Y | 收费明细 ID |
| `statusCode` | String | Y | `ChargeItemStatus` 持久化 code |
| `statusText` | String | N | 展示文案 |
| `totalPrice` | `BigDecimal` | N | 行总金额 |
| `itemLabel` | String | N | 项目名称（目录/定价回落口径与 IF-OPC-03 一致） |

**关键校验**

- 查询失败 **EX-OPC-102**。
- **BR-OPC-014**：分页候选就诊仅来自 `pageEncounterIdsForOutpatientCashierQueue`（入参 `OutpatientChargeQueueEncounterQuery`：`keyword`/`startDate`/`endDate`/`pageNum`/`pageSize`/`excludedServiceTable`；**不含** `organizationId`；**`OutpatientChargeEncounterQueueLoader`** 预解析 AMB 就诊范围后委托 `ChargeItemQueryService`，**不按**会话机构收窄；关键字/日期过滤语义与 Q-OPI-001 对齐）。
- **BR-OPC-012**：对当前页每条 `encounterId` 拉取 `BillingStatement` 行集合，按QueryService `status_enum` / `kind_enum` 等判断是否仍存在未结清或草稿应收；映射 `queueLabelCode`。若 `queueTabCode` 为 `PENDING`/`SETTLED`，在应用层过滤当前页记录（AS §4；total 口径与实现对齐）。

**失败码口径表**


| 业务码        | 说明     |
| ---------- | ------ |
| EX-OPC-102 | 队列查询失败 |


**Mapper 接口方法签名**

- `toChargeQueueRowDtoList(List<OutpatientEncounterContextQueryModel> baseRows, Map<Long, String> queueLabelByEncounter): List<ChargeQueueRowDto>`（`queueLabelCode` 映射）；`cashierChargeItems` 由 `OutpatientChargeAppService#attachCashierChargeItemBriefsToQueueRows` 在 Mapper 之后批量挂载（调用 `listOutpatientCashierChargeItemsForEncounters` 与同口径装配）
- `toPageOutpatientChargeQueueResponse(Page<?> page, List<ChargeQueueRowDto> records): PageOutpatientChargeQueueResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                       | 输入要点                                                    | 说明                                                          |
| --- | ---- | ------------------------------------------------------------------------ | ------------------------------------------------------- | ----------------------------------------------------------- |
| 1   | Gate | 参数门禁                                                                     | `pageNo`/`pageSize`/`queueTabCode` 非空                  | 非法则拒绝                                                       |
| 2   | Pre  | `PageQueryBinder#bind` + 设置 `excludedServiceTable` | Request → `OutpatientChargeQueueEncounterQuery` | 不含 `organizationId`；Loader 按日期窗/关键字预解析就诊范围 |
| 3   | Pre  | **`OutpatientChargeEncounterQueueLoader#pageEncounterIdsForOutpatientCashierQueue`** | ② Query | **BR-OPC-014**；`Page<Long>` 就诊 ID |
| 4   | Pre  | **`EncounterContextReader#listOutpatientContextsByEncounterIdsOrdered`** | ③ 本页 id 列表 | 回补展示字段 |
| 5   | Asm  | `OutpatientChargeMapper#toChargeQueueRowDtoList` + **`attachCashierChargeItemBriefsToQueueRows`** | ④ | 队列行 + 收银摘要 |
| 6   | Asm  | **`applyQueueLabelCodes`**（内部结合 `PaymentReconciliationQueryService` 与收费汇总态） | ⑤ | **BR-OPC-012** `queueLabelCode` |
| 7   | Asm  | 按 `queueTabCode` 过滤 + `toQueuePageResponse` | ⑥ | `totalCount` 与过滤后 `content` 对齐 |


### 3.4 IF-OPC-03 `loadPendingChargeItems`（加载就诊待收明细）

**HTTP**：`POST /api/app/outpatientCharge/loadPendingChargeItems`

**Request**


| 字段                      | 类型      | 必填  | 说明                 |
| ----------------------- | ------- | --- | ------------------ |
| `encounterId`           | Long    | Y   | 就诊 ID              |
| `includePriceExtension` | Boolean | N   | 是否带价扩展（应收/实收/折扣率列） |


**Response**


| 字段            | 类型                              | 说明    |
| ------------- | ------------------------------- | ----- |
| `encounterId` | Long                            | 就诊 ID |
| `items`       | `List<PendingChargeItemRowDto>` | 明细行   |
| `accountSwitchEnabled` | Boolean | **BR-OPC-018**：`AccountQueryService#hasDualChargeAccounts`；纯自费挂号为 false，UI 禁用转医保/转自费 |
| `settlementInsuranceSummary` | `ChargeSettlementInsuranceSummaryDto` | 当前选中/默认的已结算支付医保分摊摘要（患者信息栏；与收费小票结算单口径一致） |
| `settlementInsuranceOptions` | `List<ChargeSettlementInsuranceSummaryDto>` | 本就诊可选已结算支付摘要列表（切换回显） |


**DTO 字段表：ChargeSettlementInsuranceSummaryDto**（`ChargeReceiptPrintAssembler` 装配）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paymentId` | Long | Y | 支付流水主键 |
| `paymentNo` | String | N | 支付业务号 |
| `billDate` | LocalDateTime | N | 账单/结算时间 |
| `cashierName` | String | N | 收款人姓名：支付流水 `enterer_id` → 医务人员主档姓名（与收费小票口径一致） |
| `displayAmount` | BigDecimal | N | 支付主单展示金额（患者应付） |
| `insuranceSettled` | boolean | Y | 是否含医保渠道（统筹/个账） |
| `medfeeSumamt` / `fundPaySumamt` / `acctPay` / `psnCashPay` | BigDecimal | N | 医疗费总额 / 统筹 / 个账 / 个人自付合计 |
| `chargeItemIds` | `List<Long>` | N | 本支付关联收费项主键 |
| `chargeLines` | `List<ChargeSettlementPaymentLineDto>` | N | 本支付关联收费明细行（只读对照） |
| `setlinfo` | `ChargeSettlementSetlinfoDto` | N | 2206/2207 setlinfo 分摊细项（无回执可空） |

> **收费小票个账余额**（`ChargeReceiptSlipDto.accountBalanceText`，`ChargeReceiptPrintAssembler`）：展示**本笔扣款后**剩余。2207 `balc` 落库为平台原值（吉林等地实测为**结算后**）；装配时经 `InsuranceReceiptAccountBalanceSupport`：原样展示 `balc`，**禁止**再减 `acct_pay`（无回执 `balc` 时回落支付明细 `afterBalance`）。

**DTO 字段表：PendingChargeItemRowDto**


| 字段                    | 类型           | 必填  | 说明                               |
| --------------------- | ------------ | --- | -------------------------------- |
| `chargeItemId`        | Long         | Y   | 收费明细 ID                          |
| `serviceTable`        | String       | N   | 上游业务表锚点                          |
| `serviceId`           | Long         | N   | 上游业务 ID                          |
| `itemName`            | String       | N   | 项目名称：优先 `med_medication_definition / adm_device_definition / wkf_activity_definition（主表）.product_name`（目录锚点优先 `adm_charge_item.product_table`/`product_id`，缺省回落 `adm_charge_item_definition.instance_table`/`instance_id`）；再回落 `charge_name`                             |
| `statusCode`          | String       | Y   | `ChargeItemStatus` code          |
| `statusText`          | String       | N   | 展示文案                             |
| `totalPrice`          | `BigDecimal` | N   | `includePriceExtension=true` 时必填 |
| `discountAmount`      | `BigDecimal` | N   | 折后应收，`ChargeItem.discount_amount`；未打折可与 `totalPrice` 相同或空。供 **BR-OPC-034** 患者信息栏已收/折扣及已结算折后价列 |
| `discountRateDisplay` | String       | N   | BR-OPC-011 离散化后的展示折扣率            |
| `accountId`           | Long         | N   | 记账账户                             |
| `busNo` | String | N | 收费明细业务流水号 |
| `unitPrice` | `BigDecimal` | N | 单价 |
| `quantityValue` | `BigDecimal` | N | 数量 |
| `quantityUnit` | String | N | 数量单位 |
| `quantityDisplay` | String | N | 数量展示（**仅数值**；单位见 `quantityUnit`） |
| `chargeCategoryCode` / `chargeCategoryDisplay` | String | N | 费用分类：优先目录 `category_code`/`category_code_dict_text`（锚点同 `itemName`）；否则定价 `type_code` + `ChargeTypeCategory` 展示 |
| `cashierUserId` / `cashierName` | Long / String | N | 仅 `adm_charge_item.enterer_id`（收费人 `adm_practitioner.id`，**BR-OPC-016**）及医务人员主档姓名；不以 `updated_by` / `performer_id` 代替 |
| `contractName` | String | N | 费用性质名称（账户 `contract_no`→费用性质合同名称） |
| `feeNatureDisplay` | String | N | 费用性质展示（与 `contractName` 同源，供前端列 `feeNatureDisplay`） |
| `medTypeCode` | String | N | **就诊类型** code（`YbMedType`；`adm_encounter_diagnosis.med_type`，按收费行 **`encounter_diagnosis_ids` ids 串首位 id** 解析） |
| `medTypeDisplay` | String | N | **就诊类型**展示（`YbMedType#getDisplay()`；供前端收费项目列表「就诊类型」列；未知码可空，前端回落 `medTypeCode`） |
| `ybNo` | String | N | 医保目录编码（产品QueryService `yb_no`） |


**关键校验**

- 就诊无效或无明细 → **EX-OPC-103**。
- BR-OPC-010 / BR-OPC-011：`includePriceExtension` 为 true 时走带价扩展口径并在 Asm 计算折扣率展示。
- **BR-OPC-034**：行始终回填 `discountAmount`（折后应收）；前端患者信息栏在 `discountFeatureEnabled=true` 时待收=原价合计、已收=已结折后应收、折扣=已结原价−已收。
- **BR-OPC-010**：仅返回 `PLANNED`、`SETTLED` 行，不含 `DRAFT`；由 `ChargeItemQueryCommand.statusEnumIn` 与 **BR-OPC-013** 排除条件在 JPA `Specification` / 仓储组合查询中下推。
- **BR-OPC-013**：返回的明细集合**不包含** `adm_charge_item.service_table = adm_healthcare_service` 的行；`excludedServiceTable` 与 `statusEnumIn` 同路径下推，禁止全量加载后内存过滤（表名对齐 `TableNameConstants.ADM_HEALTHCARE_SERVICE`）。
- **BR-OPC-018**：响应 `accountSwitchEnabled` = 同时存在默认 `PERSONAL_CASH_ACCOUNT` 与 `MEDICAL_INSURANCE` 账户。

**失败码口径表**


| 业务码        | 说明          |
| ---------- | ----------- |
| EX-OPC-103 | 明细加载失败或就诊无行 |


**装配说明**

- 待收费明细行由 `OutpatientChargeAppService#loadPendingChargeItems` 在内存装配 `LoadPendingChargeItemsResponse`（当前未下沉至 `OutpatientChargeMapper`）。
- 前端收银台：收费项目列表在「金额」前列示 `unitPrice`（单价）；列表按收费明细事实一行一条，不做展示合并。

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                                                                                | 输入要点                                                        | 说明                       |
| --- | ---- | --------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- | ------------------------ |
| 1   | Gate | 参数门禁                                                                                                                              | `encounterId` 非空                                            | —                        |
| 2   | Pre  | `EncounterQueryService#mustGetEncounter`                                                                                          | `encounterId`                                               | 就诊存在                     |
| 3   | Pre  | 若 `includePriceExtension=true`：`ChargeItemQueryService#queryCostDetails`；否则：`ChargeItemQueryService#listChargeItemsByEncounterId` | `ChargeItemQueryCommand`：`encounterId`；`applyOutpatientCashierChargeItemQueryFilters` 等价设置 `excludedServiceTable` + `statusEnumIn = {PLANNED, SETTLED}`（**BR-OPC-010/013**） | 仅读；条件与 IF-OPC-04/05 切换集合一致 |
| 4   | Pre  | `ChargeItemDefinitionQueryService#findAllDefinitionsByIdIn`（收集 `definition_id`） | ③ 收费项列表 | 批量定价行，避免 N+1 |
| 5   | Pre  | `ProductDefinitionBatchLoader#loadAsMap` | 目录锚点：每行优先明细 `product_*`，否则定价 `instance_*` | 批量三大目录QueryService |
| 6   | Pre  | `PractitionerQueryService#mapDisplayNameByEntererIds`（仅 `enterer_id` 去重集合） | ⑤ 装配收费人姓名（**BR-OPC-016**） | 未关联医务人员则姓名为空 |
| 7   | Asm  | `OutpatientChargeAppService` 内装配 `PendingChargeItemRowDto`（含目录名、分类、单价、`discountAmount` 折后应收、`medTypeCode`/`medTypeDisplay` 就诊类型、`includePriceExtension` 时折扣率文案）；设置 `accountSwitchEnabled` | ③④⑤⑥ | 与 IF-OPC-06 预览同锚点口径；就诊类型按绑定诊断 ids 首位 `med_type` + `YbMedType`；事实一行一条；**BR-OPC-034** |


### 3.5 IF-OPC-04 `switchEncounterChargeItemsToInsurance`（转医保）

**HTTP**：`POST /api/app/outpatientCharge/switchEncounterChargeItemsToInsurance`

**Request**


| 字段            | 类型   | 必填  | 说明    |
| ------------- | ---- | --- | ----- |
| `encounterId` | Long | Y   | 就诊 ID |


**Response**


| 字段            | 类型      | 说明                           |
| ------------- | ------- | ---------------------------- |
| `success`     | Boolean | 是否成功                         |
| `messageCode` | String  | 语义码（成功：`EX-OPC-409` 口径由前端映射） |


**关键校验**

- 未解析到医保账户 → **EX-OPC-201**；非双账户 → **EX-OPC-210**；切换失败 → **EX-OPC-202**（BR-OPC-008/009/**018**）。
- **BR-OPC-010/013**：步骤 ③ 与 IF-OPC-03 同一 `applyOutpatientCashierChargeItemQueryFilters`（`statusEnumIn` + `excludedServiceTable`）。
- **BR-OPC-022**：门诊代煎签发挂个人现金自费户；转账户不入切换集合（仅跳过账单 `account_id`，**不**改 Account）；费用性质展示跟账户 `contract_no`。

**失败码口径表**


| 业务码        | 说明                    |
| ---------- | --------------------- |
| EX-OPC-201 | 目标账户不存在               |
| EX-OPC-210 | 就诊仅单一记账账户（纯自费挂号不可互转） |
| EX-OPC-202 | 账户切换落库失败              |
| EX-OPC-409 | 操作成功（与 AS 表一致，用于成功提示） |


**Mapper 接口方法签名**

- `toSwitchAccountResponse(boolean success, String messageCode): SwitchEncounterChargeItemsAccountResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                    | 输入要点                                                      | 说明                                         |
| --- | ---- | ----------------------------------------------------- | --------------------------------------------------------- | ------------------------------------------ |
| 1   | Gate | 参数门禁                                                  | `encounterId`                                             | —                                          |
| 2   | Gate | `AccountQueryService#hasDualChargeAccounts`              | `encounterId`                                             | false → **EX-OPC-210**，跳至 7 |
| 3   | Pre  | `EncounterQueryService#mustGetEncounter`              | `encounterId`                                             | —                                          |
| 4   | Pre  | `ChargeItemQueryService#listChargeItemsByEncounterId` | `ChargeItemQueryCommand`：`encounterId` + 与 IF-OPC-03 相同收银过滤（**BR-OPC-010/013**）                      | 得到待切换明细；排除代煎（**BR-OPC-022**）                    |
| 5   | Pre  | `AccountQueryService#getMedicalInsuranceAccount`      | `encounterId`                                             | 若返回 null → **EX-OPC-201**，**跳至序号 8 仅返回失败** |
| 6   | Cmd  | `ChargeItemDomainService#switchChargeItemAccount`     | `SwitchChargeItemAccountCommand`（由 ④ 非代煎 ID 列表 + ⑤ 的目标账户装配） | **写库点**；失败 → EX-OPC-202                    |
| 7   | Asm  | `OutpatientChargeMapper#toSwitchAccountResponse`      | 成功：EX-OPC-409                                             | —                                          |
| 8   | Asm  | 失败分支装配                                                | ②/⑤/⑥ 异常                                                    | 同 Mapper                                   |


### 3.6 IF-OPC-05 `switchEncounterChargeItemsToSelfPay`（转自费）

**HTTP**：`POST /api/app/outpatientCharge/switchEncounterChargeItemsToSelfPay`

**Request**：`encounterId`（必填）；`chargeItemIds`（可选，勾选收费项；空则就诊下全部可切换行）。**Response / 失败码** 同 IF-OPC-04（成功码 EX-OPC-409）。

**Mapper 接口方法签名**：同 IF-OPC-04。

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                    | 输入要点                                 | 说明                         |
| --- | ---- | ----------------------------------------------------- | ------------------------------------ | -------------------------- |
| 1   | Gate | 参数门禁                                                  | `encounterId`                        | —                          |
| 2   | Gate | `AccountQueryService#hasDualChargeAccounts`              | `encounterId`                        | false → **EX-OPC-210**，跳至 8 |
| 3   | Pre  | `EncounterQueryService#mustGetEncounter`              | —                                    | —                          |
| 4   | Pre  | `ChargeItemQueryService#listChargeItemsByEncounterId` | `ChargeItemQueryCommand`：`encounterId` + 与 IF-OPC-03 相同收银过滤（**BR-OPC-010/013**）；再按可选 `chargeItemIds` 收窄 | 待切换明细；排除代煎（**BR-OPC-022**） |
| 5   | Pre  | `AccountQueryService#getSelfPayAccount`               | `encounterId`                        | null → **EX-OPC-201**，跳至 8 |
| 6   | Cmd  | `ChargeItemDomainService#switchChargeItemAccount`     | `SwitchChargeItemAccountCommand`     | **写库点**；失败 EX-OPC-202      |
| 7   | Asm  | `OutpatientChargeMapper#toSwitchAccountResponse`      | 成功                                   | —                          |
| 8   | Asm  | 失败装配                                                  | ②/⑤/⑥ 异常                               | —                          |


### 3.7 IF-OPC-06 `preSettleOutpatientCharges`（门诊预结算）

**HTTP**：`POST /api/app/outpatientCharge/preSettleOutpatientCharges`

**Request**


| 字段              | 类型           | 必填  | 说明                    |
| --------------- | ------------ | --- | --------------------- |
| `encounterId`   | Long         | Y   | 就诊 ID                 |
| `chargeItemIds` | `List<Long>` | Y   | 所选收费项 ID 列表           |
| `contractId`    | Long         | N   | 费用性质合同（多账户分组时按领域装配传入） |
| `ybMediumType`  | String       | N   | 介质类型（占位，与医保组件方案对齐前可空） |
| `ybCardInfo`    | String       | N   | 用卡信息（占位）              |
| `psnNo`         | String       | N*  | 1101 人员编号；走医保预结算时必填 |
| `payerCertno`   | String       | N*  | 1101 参保人身份证号（**BR-OPC-035**；医保路径优先与就诊患者 `id_card` 比对） |
| `payerPsnName`  | String       | N*  | 1101 参保人姓名（就诊无证号时与患者姓名比对） |
| `cashierDiscountRate` | `BigDecimal` | N | 款员折扣率 (0,1]；空=不打折零侵入（**BR-OPC-028**）；未配置有效折扣时传值拒绝（**BR-OPC-027**） |


**Response**


| 字段               | 类型                                    | 说明      |
| ---------------- | ------------------------------------- | ------- |
| `paymentDraftId` | Long                                  | 支付草稿主键  |
| `displayAmount`  | `BigDecimal`                          | 展示应收汇总（**BR-OPC-024**：`Σ 组 psn_cash + max(0, 收费合计−Σ medfee)`；**不是** Extension 裸 `totalPsnCashPay`）  |
| `previews`       | `List<PreSettleChargeLinePreviewDto>` | 预结算明细预览 |
| `ybFundPaySumamt` / `ybAcctPay` / `ybPsnCashPay` / `ybMedfeeSumamt` | `BigDecimal` | 医保分摊摘要（2206；`ybPsnCashPay`=医保口径个人现金，**待收以 displayAmount 为准**） |
| `ybActPayDedc` | `BigDecimal` | **只读展示**：实际支付起付线（2206 `act_pay_dedc`；混合结算多组求和；**不**参与本地应收重算、**不**落 `yb_clinic_pre_settle`） |
| `ybCvlservFlag` | String | **只读展示**：公务员标志（2206 `cvlserv_flag`，0/1；混合结算取首笔非空） |
| `ybCvlservPay` | `BigDecimal` | **只读展示**：公务员医疗补助资金支出（2206 `cvlserv_pay`；混合结算多组求和；**不**参与本地应收重算） |


**DTO 字段表：PreSettleChargeLinePreviewDto**


| 字段                 | 类型           | 必填  | 说明    |
| ------------------ | ------------ | --- | ----- |
| `chargeItemId`     | Long         | Y   | 明细 ID |
| `itemLabel`        | String       | N   | 行文案   |
| `unitPrice`        | `BigDecimal` | N   | 单价（`ChargeItem.unitPrice`，结算窗「单价」列，**原价**） |
| `originalAmount`   | `BigDecimal` | N   | 行原价合计（`totalPrice`，结算窗「金额」列） |
| `lineAmount`       | `BigDecimal` | Y   | 行应收（**折后**；未打折=原价；支付草稿口径） |
| `discountedAmount` | `BigDecimal` | N   | 折后应收（结算窗「折后价」列；未打折=原价） |
| `discountRate`     | `BigDecimal` | N   | 本行有效折扣率；未打折空 |
| `quantityDisplay` / `quantityUnit` | String | N | 数量数值 / 单位展示（与 IF-OPC-03 同口径） |
| `chargeCategoryDisplay` | String | N | 费用分类展示（目录优先） |
| `feeNatureDisplay` | String       | N   | 费用性质展示（账户 `contract_no` → `fin_contract` 名称，与 IF-OPC-03 同口径；结算窗「类型」列） |


**关键校验**

- 未选收费项 **EX-OPC-301**；诊断缺失 **EX-OPC-302/303**；医疗类型不一致 **EX-OPC-304**（**BR-OPC-002**）；同批多种第三方医保合同 **EX-OPC-309**（**BR-OPC-021**）；账户与就诊不匹配 **EX-OPC-305**；合同缺失 **EX-OPC-306**；解析/核心失败 **EX-OPC-307**；**无法解析占位支付明细所需结算账户** **EX-OPC-308**（BR-OPC-001～003）。
- **BR-OPC-035**：走第三方医保时 `OutpatientChargeAppService#assertInsurancePayerMatchesEncounterPatient` 比对 `payerCertno`/`payerPsnName` 与就诊患者；CHS `ChsOutpatientPayerPatientMatchGate` 在 enrich/2201 再拦（**INS-C11**）；不一致 → **EX-OPC-310**。
- 自费与同一种第三方医保可混结一张 payment（**BR-OPC-020**）：Extension `preSettleBatch` → 主表一条、从表按账户分桶；自费 rollup 账户取自费行真实 `accountId`（含门诊代煎自费合同户）。
- **BR-OPC-024（预结算）**：`applyMixedInsurancePreSettle` 以 `InsuranceSettlementCashAlignSupport#resolveTargetPatientCash(Σ组psn_cash, Σmedfee, 收费合计)` 写 `displayAmount`，并将占位明细按比例重分到该权威现金后再 `refreshPaymentDraftPreSettleAmounts`；与 IF-OPC-07 落账同口径，避免挂医保户未进 `medfee` 的差额漏进弹窗/扫码。
- **BR-OPC-025**：预结算前 `OutpatientPaymentDraftLifecycleOrchestrator#cancelAllDraftsForEncounter` 作废同就诊 DRAFT；若目标草稿仍挂未退货 BPC `SUCCESS` → **`CANCEL-BIZ-413`**，禁止裸作废；正式结算后清其它草稿改走 `cancelStaleDraftsAfterSuccessfulSettle`（半成功跳过保留）。
- **BR-OPC-027/028/030**：`ChargeDiscountApplySupport#resolveBatch`——未选率/医保/未命中零侵入；仅自费+已选率+命中才折；同批可折行有效率须一致。

**失败码口径表**


| 业务码              | 说明        |
| ---------------- | --------- |
| EX-OPC-301～307   | 见 AS §5   |
| EX-OPC-308        | 见 AS §5（预结算账户门禁） |
| EX-OPC-309        | 见 AS §5（同批多种第三方医保合同） |
| EX-OPC-310        | 见 AS §5（读卡人≠就诊患者，**BR-OPC-035**） |


**Mapper 接口方法签名**

- `toBatchChargeItemIdsCommand(List<Long> chargeItemIds): BatchChargeItemIdsCommand`
- `toCreatePaymentDraftFromPreSettleCommand(...): CreatePaymentDraftFromPreSettleCommand`（**不含** `paymentDetails`；由 `OutpatientChargeAppService` 在领域写前装配）
- `toPreSettleOutpatientChargesResponse(Long paymentDraftId, BigDecimal displayAmount, List<PreSettleChargeLinePreviewDto> previews): PreSettleOutpatientChargesResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                   | 输入要点                                                                               | 说明                                     |
| --- | ---- | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | -------------------------------------- |
| 1   | Gate | 参数门禁                                                                 | `encounterId`、`chargeItemIds` 非空                                                   | 空集合 → EX-OPC-301                       |
| 2   | Pre  | `EncounterQueryService#mustGetEncounter`                             | —                                                                                  | —                                      |
| 3   | Pre  | `ChargeItemQueryService#loadChargeItemsByIds`                        | `ChargeItemQueryCommand.ids`                                                       | 校验选中项归属就诊                              |
| 4   | Pre  | `EncounterQueryService#loadEncounterDiagnosesByIds` / `#assertBindableEncounterDiagnosisIds` | 由 ③ 各收费行 **`encounterDiagnosisIds`** 有序 ids 串 解析之 **`adm_encounter_diagnosis.id`** 集合 | BR-OPC-001/002；失败 → EX-OPC-302/303/304 |
| 4a  | Gate | `OutpatientChargeAppService` 私有：同批 `med_type` 一致、第三方医保合同至多一种 | ③ 收费行账户 → Contract | **BR-OPC-002** / **BR-OPC-021**；多医保合同 → **EX-OPC-309** |
| 4b  | Gate | `OutpatientChargeAppService#assertInsurancePayerMatchesEncounterPatient` | 请求 `payerCertno`/`payerPsnName` + 就诊患者主档 | **BR-OPC-035**；不一致 → **EX-OPC-310** |
| 5   | Pre  | `ContractQueryService#findContractById`                              | 解析合同                                                                               | 失败 → EX-OPC-306                        |
| 5a  | Pre  | 医保 Extension：单账户 `preSettle` 或混结 `preSettleBatch`（医保组 + 自费 rollup）；CHS enrich/`Op2201Invoker` 再执行 `ChsOutpatientPayerPatientMatchGate` | ③ 收费行；自费 rollup.accountId 取行真实挂账 | **BR-OPC-020** / **BR-OPC-035**；一张 payment |
| 5b  | Gate/Pre | `ChargeDiscountApplySupport#assertCashierRateAllowedWhenDiscountConfigured` + `#resolveBatch` | ③ 收费行 + `cashierDiscountRate` | **BR-OPC-027/028/030**；未选率/医保零侵入；仅自费+已选率+命中才折 |
| 6   | Cmd  | `ChargeItemDomainService#preSettleChargeItems`                       | `BatchChargeItemIdsCommand`                                                        | **写库点 ①** 预结算快照                        |
| 7   | Pre  | （应用内私有方法，无独立写库）装配 `CreatePaymentDraftFromPreSettleCommand#paymentDetails`：`ChargeItem.accountId` 分账户汇总 `totalPrice`，列为 `PaymentDetailItemCommand`（`payEnum=CASHIER`，`resultEnum=DRAFT`）；空 `accountId` 回落 `AccountQueryService#getSelfPayAccount`；`accountCode` 尽量由 `getAccountListByEncounter` 映射 | `encounterId`、③ 已加载收费项 | 无法解析账户 → **EX-OPC-308**；若跳过则 **写库点 ②** 从表空行触发 IF-OPC-07 **EX-OPC-402** |
| 8   | Cmd  | `PaymentReconciliationDomainService#createPaymentDraftFromPreSettle` | `CreatePaymentDraftFromPreSettleCommand`（`kindEnum` 等业务字段按 **DM-FIN-002** 与机构策略装配；**含非空** `paymentDetails`） | **写库点 ②** 草稿主表 + `fin_payment_rec_detail` |
| 9   | Asm  | `OutpatientChargeMapper#toPreSettleOutpatientChargesResponse` + 应用内预览行装配（`ChargeItem.accountId` → `Account.contract_no` → `ContractQueryService#mapContractNameByBusNoIn` → `feeNatureDisplay`） | ⑥⑧ 结果、③ 收费项 | 预览名称/费用性质与 IF-OPC-03 同口径 |


### 3.8 IF-OPC-07 `confirmOutpatientChargeSettlement`（门诊正式结算）

**HTTP**：`POST /api/app/outpatientCharge/confirmOutpatientChargeSettlement`

**Request**


| 字段               | 类型                             | 必填  | 说明                       |
| ---------------- | ------------------------------ | --- | ------------------------ |
| `paymentDraftId` | Long                           | Y   | IF-OPC-06 返回的草稿 ID       |
| `encounterId`    | Long                           | Y   | 就诊 ID（用于 Gate 与交叉校验）     |
| `chargeItemIds`  | `List<Long>`                   | Y   | 须与草稿绑定集合逐项一致（BR-OPC-004） |
| `paymentDetails` | `List<PaymentDetailSubmitDto>` | Y   | 渠道拆分实付                   |
| `billDate`       | `LocalDateTime`                | Y   | 结算时间                     |
| `ybSettleIds`    | String                         | N   | 医保结算号串                   |
| `outcomeEnum`    | String                         | Y   | 支付结果枚举 code              |


**Response**


| 字段                   | 类型     | 说明                  |
| -------------------- | ------ | ------------------- |
| `paymentId`          | Long   | 成功支付主键              |
| `invoiceId`          | Long   | 发票草稿 ID             |
| `successMessageCode` | String | **EX-OPC-410** 成功语义 |


**DTO 字段表：PaymentDetailSubmitDto**


| 字段               | 类型           | 必填  | 说明                          |
| ---------------- | ------------ | --- | --------------------------- |
| `payChannelCode` | String       | Y   | 渠道编码：**现金 `CASH`**；扫码 settle **`SCAN_PAY`**（服务端替换）；手工 **`WECHAT`/`ALIPAY`** |
| `amount`         | `BigDecimal` | Y   | 渠道金额（与预结算现金应付对齐：BR-OPC-006） |

> **说明**：DTO **不含** `accountId`。`OutpatientChargeMapper#toSettleOutpatientPaymentCommand` 仅装配渠道、金额、账单日与结果枚举；写库前顺序为：`mergeInsurance…`（可写医保分项，并以 `InsuranceSettlementCashAlignSupport` 按 Σ `psn_cash` 对齐自付；**BR-OPC-024** 勾稽自付合计与全明细合计）→（含 SCAN_PAY 时）`applyScanBeforeSettle` → `attachDraftAccountsToPayReceiptDetails` 用步骤 ③ 草稿**现金**分桶回填缺账户现金行。混结时医保分项已带账户不参与条数比对；扫码须在账户拆行前完成，避免多条 SCAN_PAY。否则领域 `PaymentRecDetail#assertPersistableChildRow` 会因「支付明细账户标识不能为空」或渠道不可落库拒绝。


**关键校验**

- 草稿不存在/非草稿 **EX-OPC-401**；**支付从表明细为空或草稿侧金额合计与收费项不一致** **EX-OPC-402**；收费项与草稿不一致 **EX-OPC-403**；**本次拟收收费项与就诊下已有成功/退款处理中流水的挂载收费项存在交集** **EX-OPC-404**（对齐 BR-OPC-005；无交集时允许同就诊后续增量结算）；金额不等 **EX-OPC-405**；已计费重复 **EX-OPC-406**；医保失败 **EX-OPC-407**；账户缺失 **EX-OPC-408**。

**失败码口径表**


| 业务码            | 说明      |
| -------------- | ------- |
| EX-OPC-401～410 | 见 AS §5 |


**Mapper 接口方法签名**

- `toSettleOutpatientPaymentCommand(ConfirmOutpatientChargeSettlementRequest request): SettleOutpatientPaymentCommand`（**不**设置 `PaymentDetailItemCommand.accountId`；由 AppService 步骤 ⑨ 回填）
- `toBatchChargeItemIdsCommand(List<Long> chargeItemIds): BatchChargeItemIdsCommand`
- `toCreateDraftInvoiceCommand(PaymentReconciliationQueryModel payment, Long paymentId): CreateDraftInvoiceCommand`
- `toConfirmOutpatientChargeSettlementResponse(Long paymentId, Long invoiceId): ConfirmOutpatientChargeSettlementResponse`

**领域编排序列（严格顺序）**


| 序号  | 阶段   | 调用                                                                       | 输入要点                                              | 说明                          |
| --- | ---- | ------------------------------------------------------------------------ | ------------------------------------------------- | --------------------------- |
| 1   | Gate | 参数门禁                                                                     | `paymentDraftId`、`chargeItemIds`、`paymentDetails` | 缺参拒绝                        |
| 1a  | Gate | `OutpatientChargeSettlementGate#assertChargeItemsSettleable` | 拟结算 `ChargeItem` 最新加载 | **BR-OPC-023**：PLANNED + 医嘱 ACTIVE + 药/耗 `dispense_*` 发放行 `lot_number` 非空非占位；否则拒结算 |
| 2   | Pre  | `PaymentReconciliationQueryService#mustGetPayment`                       | `paymentDraftId`                                  | 非草稿 → EX-OPC-401            |
| 3   | Pre  | `PaymentReconciliationQueryService#listPaymentRecDetailsByPaymentId`     | 草稿 ID                                             | 从表空或后续金额校验失败 → **EX-OPC-402** |
| 4   | Pre  | 一致性校验                                                                    | 头中 `charge_item_ids` 与请求集合                        | 不一致 → EX-OPC-403            |
| 5   | Pre  | `PaymentReconciliationQueryService#findPaySuccessOrRefundingOverlappingChargeItems` | `encounterId`、`chargeItemIds`（与请求一致） | 该就诊下 `SUCCESS` 或 `REFUND_PROCESSING` 流水且 `charge_item_ids` 与本次拟收集合 **有交集** 则 **EX-OPC-404**；无交集时不阻断（同就诊可分批结算）；疑脏数据时核对流水 `charge_item_ids` |
| 6   | Pre  | `ChargeItemQueryService#loadChargeItemsByIds`                            | 请求 ID 列表                                          | 任一条已 `SETTLED` → EX-OPC-406 |
| 7   | Pre  | 金额对齐                                                                     | 预结算现金应付 vs `paymentDetails` 汇总                    | 不等 → EX-OPC-405             |
| 8   | Pre/Cmd | `ThirdPartyPaymentSettleOrchestrator#applyScanBeforeSettle`（含 SCAN_PAY 时） | 合并医保分项后的 `payReceiptDetails` + `clientScanResult` | **须在步骤 ⑨ 账户拆行之前**：单条 SCAN_PAY 对整笔现金校验 txnAmt 并替换为 WECHAT/ALIPAY/BANK_CARD；详见 §3.8.1 |
| 9   | Pre  | `OutpatientChargeAppService` 私有方法 `attachDraftAccountsToPayReceiptDetails` | ③ 草稿**现金**分桶 + `payReceiptDetails`（可已含医保分项；扫码行已为可落库语义码） | **仅**对缺 `accountId` 的现金回执行回填；已带账户的医保分项不参与条数比对；多现金账户且仅一条缺账户现金回执时按草稿金额拆行（复制已解析渠道，不得再留 SCAN_PAY）；无法对齐 → **EX-OPC-405** |
| 10  | Cmd  | `PaymentReconciliationDomainService#settleOutpatientPayment`             | `SettleOutpatientPaymentCommand`                  | **写库点 ①** 支付成功态 + `fin_payment_rec_detail` 替换 |
| 11  | Cmd  | `OutpatientChargePrescriptionSnapshotStamper#stampBeforeSettle` → `ChargeItemDomainService#stampPrescriptionSnapshots` | 拟结算收费行 + `operatorId` | **BR-OPC-026**：药品从药嘱/发放抄写；诊疗仅 **`AR`** 申请单号；代煎 companion 不盖方号 |
| 12  | Cmd  | `ChargeItemDomainService#settleChargeItems`                              | `BatchChargeItemIdsCommand`（含 `operatorId` + `billDate`）    | **写库点 ②** 收费项 SETTLED + `enterer_id`/`entered_date` 收费人/收费时间（**BR-OPC-016**） |
| 13  | Cmd  | `OutpatientChargeAppService#syncAdviceStatesAfterChargeSettlement`（private） | 本次结算收费项 + `CurrentOperatorResolver#requireOperatorId` | **BR-OPC-015**：调用各 `*DomainService` 完成/推进发放草稿 |
| 14  | Cmd  | `InvoiceAfterPaymentOrchestration#createDraftAndScheduleIssueIfPayable` | `CreateDraftInvoiceCommand`（`amount=displayAmount`） | **写库点 ③（条件）**：实付 > 0 时发票草稿；零元跳过，`invoiceId=null` |
| 15  | Asm  | `OutpatientChargeMapper#toConfirmOutpatientChargeSettlementResponse`     | ⑩～⑭                                               | 成功码 EX-OPC-410              |


> **说明（AS §6 / §7）**：**BR-OPC-015** 在 **`OutpatientChargeAppService`** 私有方法内同步调用各领域写能力；本 IF 在 **收费项 SETTLED 之后、发票草稿之前** 执行步骤 ⑬。

#### 3.8.1 扩展：BPC 扫码支付（IF-OPC-07 · 可选 Module）

> **真源**：`AS-OP-FIN-002` §8.1 · [模块/zxplat-module-unionpay-bpc/03 §3.10～§3.13](../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md)

**Request 扩展**（`ConfirmOutpatientChargeSettlementRequest` 顶层）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `clientScanResult` | `ClientScanResultDto` | 含扫码行时 Y | 见 03 §3.10 |

**前端时序（IF-OPC-07 之前）**：医保 CHS confirm（若有）→ `prepareConsumerScan` → Win consumer-scan。

**顺序硬约束**：`mergeInsurancePaymentDetailsIntoSettleCommand` → **本扩展（步骤 ⑧）** → `attachDraftAccountsToPayReceiptDetails`（步骤 ⑨）→ settle。  
混结多现金账户时步骤 ⑨ 会按草稿金额拆行并复制 `payEnum`；若先拆再扫，将出现多条 `SCAN_PAY` 且分额无法对齐银联整笔 `txnAmtFen`。

含 **`SCAN_PAY`** 时（`ThirdPartyPaymentExtension#isEnabled()`）：

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 8a | Pre | `ThirdPartyPaymentExtension#isEnabled()` | false → 跳过 |
| 8b | Gate | `clientScanResult.respCode=00` | 非 00 → EX-OPC-4xx |
| 8c | Cmd | `ThirdPartyPaymentSettleOrchestrator#applyScanBeforeSettle` | `Extension#applyClientScanResult`；**仅允许一条** SCAN_PAY，金额=整笔现金应付 |
| 8d | Asm | 替换扫码行 `payEnum` | → WECHAT/ALIPAY/BANK_CARD |
| 8e | Gate | `PaymentChannelGate#requirePersistablePayChannelCode` | 替换后各行 |
| 8f | — | 8b/8c 失败 | 不 settle；可重扫/改 `CASH` |
| 8g | Pre | 步骤 ⑩ 失败且 8c 成功 | 半成功 → 重传 `clientScanResult`（03 §3.12） |

**payType 双存**：BPC 原值在 `upc_bpc_order`；`fin_payment_rec_detail.pay_enum` 写语义码。

#### 3.8.2 扩展：医保落账挂接与 2601 补偿（zxplat-domain）

> **真源**：`AS-OP-FIN-002` §8.2 · [integration-assembler-orchestration.md](../../../../.cursor/skills/模块代码规范/references/integration-assembler-orchestration.md)

**IF-OPC-07 落账（路径 A）**

| 序号 | 阶段 | 调用（目标态） | 说明 |
| --- | --- | --- | --- |
| 9a | Cmd | `PaymentReconciliationSettleOrchestration#settleOutpatientPayment` | 替代 AppService 直调 `DomainService` + 事后挂接 |
| 9b | — | 内部：`PaymentReconciliationDomainService#settleOutpatientPayment` | 写库点 ①（同 §3.8 步骤 ⑩） |
| 9c | Asm | `InsurancePaymentSettledExtensionInAssembler` | `PaymentReconciliation` + yb 行 → `InsurancePaymentSettledExtensionIn` |
| 9d | Cmd | `InsurancePaymentSettlementAfterCommitOrchestration#afterPaymentSettled` | **afterCommit** → `InsurancePaymentSettlementExtension#onPaymentSettled` → bridge `Op2601Invoker` |

**confirm 部分失败（路径 B）**

| 阶段 | 调用（目标态） | 说明 |
| --- | --- | --- |
| Catch | `OutpatientChargeInsuranceCompensationOrchestration#compensateSettlementsByPayment` | 2208 → 失败则 2601；失败 **INS-013** |

**存量类名（迁移前）**：`PaymentReconciliationSettleIntegrationSupport`、`InsurancePaymentSettlementExtensionSupport`、`OutpatientChargeInsuranceExtensionSupport`。

### 3.9 IF-OPC-08 `previewOutpatientChargeDiscounts`（收费列表折后价试算）

**HTTP**：`POST /api/app/outpatientCharge/previewOutpatientChargeDiscounts`

**Request**


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊主键 |
| `chargeItemIds` | `List<Long>` | Y | 拟试算收费项（通常为列表待收行） |
| `cashierDiscountRate` | `BigDecimal` | N | 款员折扣率 (0,1]；空=不打折（零侵入）。未配置有效折扣时传值拒绝（**BR-OPC-027**）；仅自费+已选率+命中才折（**BR-OPC-028**） |


**Response**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊主键 |
| `discountAmountTotal` | `BigDecimal` | 本批优惠合计；无打折为 0 |
| `lines` | `List<PreviewOutpatientChargeDiscountLineDto>` | 按请求主键顺序 |


**DTO 字段表：PreviewOutpatientChargeDiscountLineDto**


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chargeItemId` | Long | 明细主键 |
| `originalAmount` | `BigDecimal` | 原价 |
| `discountedAmount` | `BigDecimal` | 折后价（未打折=原价刻度） |
| `discountGap` | `BigDecimal` | 优惠额 |
| `discountRate` | `BigDecimal` | 有效折扣率；未打折空 |
| `discounted` | Boolean | 本行是否实际打折 |


**领域编排序列（严格顺序）**


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数门禁 | `encounterId`、`chargeItemIds` 非空且不重复 | — |
| 2 | Pre | `EncounterQueryService#mustGetEncounter` | `encounterId` | — |
| 3 | Pre | `ChargeItemQueryService#loadChargeItemsByIds` | `chargeItemIds` | 缺失 → 收费项不存在 |
| 4 | Pre | 归属校验 | 行 `encounterId` 须等于请求 | 跨就诊拒绝 |
| 5 | Pre | `ChargeDiscountApplySupport#previewBatch` | 收费行 + `cashierDiscountRate` | **只读**；门禁与预结算 `resolveBatch` 相同（**BR-OPC-028**）：未选率/医保/未命中 → 折后=原价；进入打折后配置 ∈ (0,1) 固定优惠优先，否则用款员率。不因混医保拒绝整批（**BR-OPC-033**） |
| 6 | Asm | 按请求主键顺序装配 `lines` | ⑤ 行结果 | 无 Cmd / 不写库 |


## 4. QueryService 与事件刷新


| QueryService                  | 订阅来源（摘要）                                                                                   | 更新意图                                |
| -------------------- | ------------------------------------------------------------------------------------------ | ----------------------------------- |
| **EncounterQueryService** | Encounter / ChargeItem / PaymentReconciliation 等（见 `DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` §4.1）       | IF-OPC-06/07 后费用摘要、队列金额异步刷新         |
| **BillingStatement** | PaymentReconciliation / Account / Invoice / ChargeItem（见 `DM-FIN-002 §6.2 PaymentReconciliationQueryService` §4.1） | 草稿插入、结算成功插入/更新；支撑 IF-OPC-02 待收/已结判定 |


字段级映射以各自QueryService **§4.2** 为唯一真源；应用层 **禁止** 手写投影 SQL。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览（与 §3 对齐）


| IF           | App 方法                              | Cmd 序号 | 领域调用                                                                 | 聚合写入顺序（摘要）                      |
| ------------ | ----------------------------------- | ------ | -------------------------------------------------------------------- | ------------------------------- |
| IF-OPC-04/05 | `switchEncounterChargeItemsTo`*     | 1      | `ChargeItemDomainService#switchChargeItemAccount`                    | ChargeItem 账户字段更新 + 事件          |
| IF-OPC-06    | `preSettleOutpatientCharges`        | 1      | `ChargeItemDomainService#preSettleChargeItems`                       | ChargeItem 预结快照                 |
| IF-OPC-06    | `preSettleOutpatientCharges`        | 2      | `PaymentReconciliationDomainService#createPaymentDraftFromPreSettle` | PaymentReconciliation + Details |
| IF-OPC-07    | `confirmOutpatientChargeSettlement` | 1      | `PaymentReconciliationDomainService#settleOutpatientPayment`         | PaymentReconciliation 成功态 + 支付从表按回执替换 |
| IF-OPC-07    | `confirmOutpatientChargeSettlement` | 2      | `ChargeItemDomainService#stampPrescriptionSnapshots` + `#settleChargeItems` | 处方号快照（**BR-OPC-026**）→ ChargeItem SETTLED + `enterer_id`/`entered_date`（**BR-OPC-016**） |
| IF-OPC-07    | `confirmOutpatientChargeSettlement` | 2b     | `OutpatientChargeAppService#syncAdviceStatesAfterChargeSettlement` | 药/耗/诊疗申请与发放草稿状态（**BR-OPC-015**） |
| IF-OPC-07    | `confirmOutpatientChargeSettlement` | 3      | `InvoiceAfterPaymentOrchestration#createDraftAndScheduleIssueIfPayable`（**BR-OPC-019**） | Invoice 草稿（条件）              |


### 5.2 事务边界与失败回滚


| 事务单元         | 边界         | 失败回滚                               |
| ------------ | ---------- | ---------------------------------- |
| IF-OPC-04/05 | 单 Cmd      | `switchChargeItemAccount` 失败则全事务回滚 |
| IF-OPC-06    | ⑥～⑧ 同一事务   | 任一步失败回滚预结快照与支付草稿（含从表）               |
| IF-OPC-07    | ⑩～⑬ 默认同一事务 | 任一步失败整体回滚；幂等与重复提交见 §6              |


## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性


| 承诺                                   | 代码位置（必须）                                      | 关联步骤        |
| ------------------------------------ | --------------------------------------------- | ----------- |
| IF-OPC-07 同一草稿重复提交须拒绝或返回已处理，不得双记成功支付 | `PaymentReconciliation` 聚合状态机 + App Pre ⑤（收费项与历史成功流水挂载交集） | §3.8 序号 5～9 |
| IF-OPC-06 短时重复预结算策略（合并同一草稿）          | App 层可选：查询 `findDraftPaymentByPatient`/就诊维度草稿 | §3.7 序号 6～8（写库点为 ⑥⑧） |


### 6.2 并发控制


| 承诺                        | 代码位置（必须） | 关联步骤        |
| ------------------------- | -------- | ----------- |
| 同就诊并发收费：后到者在校验失败（重复项、已计费） | Pre ⑤⑥   | §3.8 序号 5～7 |


### 6.3 重试与容错


| 承诺                      | 代码位置（必须）       | 关联步骤     |
| ----------------------- | -------------- | -------- |
| 预结算/正式结算失败禁止自动连发；提示人工重试 | App 异常映射 AS §5 | §3.7～3.8 |


### 6.4 数据脱敏


| 承诺                   | 代码位置（必须）        | 关联步骤     |
| -------------------- | --------------- | -------- |
| 队列与明细展示证件/电话脱敏遵循全院策略 | Mapper / DTO 装配 | §3.3～3.4 |


### 6.5 审计日志


| 承诺                          | 代码位置（必须）                                        | 关联步骤     |
| --------------------------- | ----------------------------------------------- | -------- |
| 收费员 `entererId`、支付流水号写入支付聚合 | `CreatePaymentDraftFromPreSettleCommand` / 结算命令 | §3.7～3.8 |


## 7. 测试要点

### 7.1 功能测试


| 用例                                              | 自动化归属 | 优先级 |
| ----------------------------------------------- | ----- | --- |
| 初始化下拉与术语枚举一致                                    | 集成    | P0  |
| 队列：OutpatientEncounterContextQueryModel + BillingStatement 打标与 Tab 过滤 | 集成    | P0  |
| 转医保/转自费账户缺失与成功提示                                | 集成    | P0  |
| 预结算：诊断/医疗类型/合同门禁                                | 集成    | P0  |
| 正式结算：草稿一致性、金额对齐、重复收费；请求无 `accountId` 时从草稿从表回填后再 `settleOutpatientPayment` | 集成    | P0  |
| 混结（医保+代煎自费户）+ 银联 SCAN_PAY：merge→applyScan（整笔现金）→attach 按账户拆行；不得 EX-OPC-405，不得「多条扫码」 | 集成/E2E | P0  |
| 扫码支付：CHS confirm 后 Win C；语义码双存 | 集成    | P0  |
| 半成功：BPC SUCCESS + settle 失败可重传 clientScanResult 补 settle | 集成    | P0  |
| 半成功：**BR-OPC-025** 预结算清草稿遇 BPC SUCCESS → `CANCEL-BIZ-413`；结算后清草稿跳过半成功 | 集成    | P0  |


### 7.2 非功能测试


| 用例                                 | 自动化归属 | 优先级 |
| ---------------------------------- | ----- | --- |
| IF-OPC-02/03/06/07 响应时间经验目标（AS §7） | 性能    | P1  |


### 7.3 QueryService一致性


| 用例                                          | 自动化归属   | 优先级 |
| ------------------------------------------- | ------- | --- |
| 结算后 OutpatientEncounterContextQueryModel/BillingStatement 最终与事件一致 | 集成/异步验收 | P0  |


### 7.4 集成测试


| 用例                       | 自动化归属 | 优先级 |
| ------------------------ | ----- | --- |
| 端到端：筛选队列→明细→转账户→预结算→正式结算 | E2E   | P1  |
| 零元正式结算（**BR-OPC-019**） | 集成   | P0  |
| 扫码支付：CHS confirm 后 Win → clientScanResult → settle | E2E   | P0  |


### 7.5 测试缺口跟踪


| 缺口                             | 说明                          |
| ------------------------------ | --------------------------- |
| 医保介质 IF                        | AS §1.4「待接通」；独立 IF 收口后补 E2E |
| 队列页签过滤与分页 total              | IF-OPC-02 应用层按标签过滤当前页；`total` 与过滤后条数一致性以回归用例锁定 |


## 8. 变更历史


| 版本   | 日期         | 变更摘要                                                                                                                                                                |
| ---- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| v1.41 | 2026-10-16 | **BR-OPC-024** 预结算：`displayAmount`/草稿占位=权威现金公式；对齐 **AS v1.47** / `OutpatientChargeAppService#applyMixedInsurancePreSettle`（代码先行回写） |
| v1.40 | 2026-10-12 | **BR-OPC-027/028/033**：打折三门禁（自费+已选率+命中）；未选率/医保零侵入；对齐 **AS v1.46** / `ChargeDiscountApplySupport`（代码先行回写） |
| v1.39 | 2026-10-10 | **BR-OPC-035 / EX-OPC-310**：IF-OPC-06 读卡人=就诊患者门禁；请求增 `payerCertno`/`payerPsnName`；队列 `idCard`；CHS `ChsOutpatientPayerPatientMatchGate`；对齐 **AS v1.45**（代码先行回写） |
| v1.38 | 2026-10-06 | 折扣显隐口径：未配置有效折扣时不得传折扣率；对齐 **AS v1.44** |
| v1.37 | 2026-10-06 | **IF-OPC-06**：`PreSettleChargeLinePreviewDto` 增单价/原价/折后/数量/分类；结算窗不得把折后写入单价列；对齐 **AS v1.43**（代码先行回写） |
| v1.36 | 2026-10-06 | **IF-OPC-03**：`PendingChargeItemRowDto.discountAmount`；对齐 **AS v1.42 / BR-OPC-034** 患者信息栏待收/已收/折扣（代码先行回写） |
| v1.35 | 2026-10-06 | **IF-OPC-08**：有效率纠偏——配置 ∈ (0,1) 才挡款员率；空率/10 折走款员定率；未命中不得折；对齐 **AS v1.41 / BR-OPC-033**（代码先行回写） |
| v1.34 | 2026-10-06 | **IF-OPC-08**：`previewOutpatientChargeDiscounts` 列表折后价只读试算；`ChargeDiscountApplySupport#previewBatch`；对齐 **AS v1.40 / BR-OPC-033**（代码先行回写） |
| v1.33 | 2026-09-28 | **收费小票** `accountBalanceText`：2207 `balc` 原样（结算后）；纠偏不再减 `acct_pay`；挂号同口径；对齐 **AS v1.39**（代码先行回写） |
| v1.32 | 2026-09-22 | **收费小票** `accountBalanceText`：扣后剩余（当时误按 `balc−acct_pay`；**v1.33 已纠偏**）；挂号小票同口径；对齐 **AS v1.38** |
| v1.31 | 2026-09-06 | **BR-OPC-026** 纠偏：诊疗侧仅 **`AR`** 可盖收费快照；代煎不从 SR 抄方号；对齐 **AS v1.37** |
| v1.30 | 2026-09-06 | **BR-OPC-026**：IF-OPC-07 结算前 `OutpatientChargePrescriptionSnapshotStamper` → `stampPrescriptionSnapshots`；对齐 **AS v1.36** / **DM-ADM-011 v1.6.19** |
| v1.29 | 2026-08-31 | **BR-OPC-025**：预结算清草稿半成功门禁；结算后 `cancelStaleDraftsAfterSuccessfulSettle`；对齐 **AS v1.35** / `PaymentDraftHalfSuccessAbortGate` |
| v1.28 | 2026-08-26 | **BR-OPC-024**：`mergeInsurance…` 后勾稽自付合计与全明细合计；对齐 **AS v1.33** / `InsuranceSettlementCashAlignSupport` |
| v1.27 | 2026-08-22 | **BR-OPC-023**：`OutpatientChargeSettlementGate` 正式结算前校验药/耗发放批号（含 `dispense_table`/`dispense_id`）；对齐 **AS v1.32** / **BR-ORD-033** |
| v1.26 | 2026-08-17 | **IF-OPC-03**：回填 `settlementInsuranceSummary`/`settlementInsuranceOptions`；摘要增 `cashierName`（支付 `enterer_id`→医务人员姓名，与小票一致） |
| v1.25 | 2026-08-03 | **IF-OPC-07**：扫码挂接须在账户拆行之前（merge→applyScan→attach→settle）；避免混结多现金账户拆出多条 SCAN_PAY；对齐 **AS v1.30** |
| v1.24 | 2026-08-03 | **IF-OPC-07** 步骤账户回填：`attachDraftAccountsToPayReceiptDetails` 仅对齐缺账户现金行与草稿现金分桶，医保分项不参与条数比对（混结+银联 EX-OPC-405）；对齐 **AS v1.29** |
| v1.23 | 2026-08-02 | **IF-OPC-05**：请求增补可选 `chargeItemIds`（勾选转自费）；对齐 **AS v1.26**、挂号双账户 **AS-OP-FIN-001 v2.33** |
| v1.23 | 2026-08-02 | **BR-OPC-022**：代煎挂个人现金自费户；去掉展示硬盖；与挂号双账户/转自费对齐；对齐 **AS v1.27** |
| v1.22 | 2026-08-02 | **BR-OPC-022 纠偏**：曾用展示覆盖（已由 v1.23 废止）；同账户代煎+医保强制 preSettleBatch |
| v1.21 | 2026-08-02 | **BR-OPC-020～022 / EX-OPC-309**：混结一张 payment；至多一种第三方医保合同（v1.22 纠偏代煎不动 Account） |
| v1.20 | 2026-08-01 | **撤回展示合并**：删除 `PendingChargeItemDisplayMergeSupport` / `chargeItemIds` / 当时「展示合并」语义的 **BR-OPC-020**；IF-OPC-03 事实一行一条；前端仅保留单价列（金额前）；对齐 **AS v1.23** |
| v1.19 | 2026-08-01 | （已撤回）曾尝试 BR-OPC-020 合并键去掉 busNo |
| v1.18 | 2026-08-01 | （已撤回）曾尝试同价同品展示合并 |
| v1.17 | 2026-08-01 | IF-OPC-03：`PendingChargeItemRowDto` 增补 `medTypeDisplay`；前端收费项目列表在费用性质与收费状态之间展示「就诊类型」列（`medType*`） |
| v1.16 | 2026-07-18 | **DD §11.7**：预结算诊断改读收费行 **`encounter_diagnosis_ids`**；移除 **Condition** 依赖；`medTypeCode` 取 ids 串首位 id 之 `med_type` |
| v1.16 | 2026-08-01 | IF-OPC-06 响应增补 **只读展示** `ybActPayDedc` / `ybCvlservFlag` / `ybCvlservPay`（2206 setlinfo 内存透传，不落预结算表、不改应收）；支付记录 setlinfo 增补 `cvlservFlag` |
| v1.15 | 2026-07-16 | IF-OPC-07：**BR-OPC-019** 零元不开票；步骤 12 改 `InvoiceAfterPaymentOrchestration`；对齐 **BR-INV-008** |
| v1.14 | 2026-07-04 | **BR-OPC-018**：`AccountQueryService#hasDualChargeAccounts`；IF-OPC-03 `accountSwitchEnabled`；IF-OPC-04/05 **EX-OPC-210**；对齐 **AS v1.16** |
| v1.13 | 2026-07-04 | **IF-OPC-04/05 实现回更**：`OutpatientChargeAppService#switchAccount` 仅写库 `accountId≠目标` 的行；前端 `views/outpatient/charge/charge/index.vue` 工具栏「转医保/转自费」+ `outpatient-charge.constants.ts` 语义码映射；对齐 **AS v1.15** |
| v1.12 | 2026-06-25 | IF-OPC-01 响应增 `paymentChannelOptions`；Pre 调 `PaymentChannelOptionProvider`（**ADR-PAY-001**） |
| v1.21 | 2026-08-04 | **BR-OPC-016** 纠偏：IF-OPC-03/07 收费人读/写改为 `enterer_id`/`entered_date`（不再写/读 `performer_id`）；对齐 **AS v1.31**、**DM-ADM-011 v1.6.13** |
| v1.10 | 2026-06-24 | **BR-OPC-016**：`settleChargeItems` 写侧落库 `performer_id`（收款人）；§3.6 IF-OPC-07 步骤⑩、§5.1 写库顺序同步；对齐 `DM-ADM-011` §6.1.6 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：`EncounterContextFacade#listOutpatientContextsByEncounterIdsOrdered`；`OutpatientEncounterContextQueryModel` 替代 OutpatientInfo RM |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.8 | 2026-05-18 | §2 应用层包路径收口为 `outpatient/financial/charge`；补充与 **AS-OP-FIN-001/003** 同级分包说明及 **B.10.2** 常量落点；HTTP 暴露口径不变。 |
| v1.7 | 2026-05-10 | §2.3 增补 `OutpatientChargeConstants`；§3.4 **ChargeQueueRowDto** 对齐实现：`genderEnumText`、`cashierChargeRollupStatus*`；§3.4 **PendingChargeItemRowDto** 对齐：`quantityDisplay` 口径及 `contractName` / `medTypeCode` / `ybNo` |
| v1.11 | 2026-06-23 | §3.8.1：SCAN_PAY + clientScanResult；ThirdPartyPaymentSettleSupport；对齐 **AS v1.12** |
| v1.10 | 2026-06-23 | §3.8.1 BPC「扫码支付」扩展（步骤 8～9 间）；payChannelCode 220100/220200/220300；CHS→BPC 顺序；对齐 **AS v1.11** |
| v1.9 | 2026-05-28 | 六维对账：**BR-OPC-015** 收口为 `OutpatientChargeAppService` 私有方法（删除独立 Service 类叙述） |
| v1.8.1 | 2026-05-28 | **L3/L2**：IF-OPC-02 `pageNo`/`PagedResult#content`；编排序列改 **Loader + EncounterContextFacade + applyQueueLabelCodes**；修正 Loader 方法名 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.6 | 2026-05-10 | 对齐 `AS-OP-FIN-002` v1.8 与 **B.10.11**：§1.2 增补分页/机构收窄；**IF-OPC-02** 移除 Request `organizationId`；**IF-OPC-01** 标注遗留字段忽略；§3.3 关键校验与 Pre 步骤②同步 |
| v1.5 | 2026-05-09 | 对齐 `AS-OP-FIN-002` v1.7：IF-OPC-07 在 `settleChargeItems` 与 `createDraftInvoiceAfterPayment` 之间插入 **`OutpatientChargeAdviceStateSyncService#syncAdviceStatesAfterChargeSettlement`**；§2 依赖与白名单、`OutpatientChargeAdviceStateSyncService.java` 结构；§5.1/§5.2 写库顺序与事务说明；修正「仅靠事件同步推进」与现行实现矛盾表述 |
| v1.4 | 2026-05-08 | 对齐 `AS-OP-FIN-002` v1.5：IF-OPC-02 改为收费事实分页 + `listOutpatientContextsByEncounterIdsOrdered` 回补；**BR-OPC-010/014**；`ChargeQueueChargeItemBriefDto` / `cashierChargeItems`；IF-OPC-03/04/05 `statusEnumIn` + `applyOutpatientCashierChargeItemQueryFilters`；§1.2、§2、§3.3～§3.6、§7.5 同步 |
| v1.3 | 2026-05-08 | 对齐 `AS-OP-FIN-002` v1.4：IF-OPC-07 增补步骤 ⑧ `attachDraftAccountsToPayReceiptDetails`；`PaymentDetailSubmitDto` / Mapper 说明与领域从表 `account_id` 门禁；§5～§7 序号与测试要点同步 |
| v1.1 | 2026-05-08 | 对齐 `AS-OP-FIN-002` v1.3：IF-OPC-06 增补步骤 ⑦（装配非空 `paymentDetails`）与 **EX-OPC-308**；明确 **EX-OPC-402** 为从表明细/草稿金额门禁；`AccountQueryService` 白名单与 Mapper 契约说明 |
| v1.0 | 2026-05-05 | 首版：按 `AS-OP-FIN-002` v1.2 与 SKILL 门禁生成；**目标态**：`outpatientCharge` 应用服务包路径、`OutpatientChargeQueueTab` 常量类；门诊全场景下 `settleOutpatientPayment` 事件内就诊状态语义须与 DM 对齐修订（§1.1） |


---

**文档负责人**：（待填） · **评审**：（待填）