# 住院结算 · 应用服务详细设计（SDD）

> 编码：AS-IP-FIN-003-SDD  
> 版本：v1.17 | 日期：2026-08-17 | 作者：（待填）
> 来源契约：`AS-IP-FIN-003-住院结算.md` v1.22

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院结算应用服务（住院收费「出院/在院结算」页 `/inpatient/charge/settle`） |
| 边界 | 编排 Encounter / ChargeItem / Account / PaymentReconciliation / Invoice 及 **InpatientEncounterContextQueryModel**、**ProductDefinition**（读）查询；不在应用层发明聚合不变式 |
| 编排权威 | `AS-IP-FIN-003-住院结算.md`（**v1.22**）§2、§4 |
| 方法权威 | `DM-ADM-002` §6.1.23、`DM-ADM-011` §6.1.9b/§6.1.12/§6.2、`DM-FIN-002` §6、`DM-ADM-012` §6；`DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §3 |
| 严格对齐 | IF 编号 **IF-IP-STL-01～07** |
| 实现阶段口径 | **目标态全量契约**（IF-01～07）；领域 **G-STL-01～03 已落地**；应用层 `com.zxplat.domain.application.inpatient.financial.settle` **已成码**（`InpatientSettleAppService` + Request/Response/DTO/Mapper；IF-IP-STL-07 历史 NET/`reverseEnabled` 与 **IF-IP-RVS-01** 见 **AS-IP-FIN-004-SDD**） |

**Out of scope（重复 AS §1.4，SDD 不得扩写）**

| 类别 | 内容 |
| --- | --- |
| 医保平台报文 | 230x/240x 报文细节、平台撤销与费别切换（**CHS**）；本 SDD 仅写扩展钩子下的 `displayAmount`/分项回填、支付从表明细与原路退第二阶段 |
| 自动多退少补 | **已按 DD 重写**：自费=医保同一套 R1～R7；结算主单=**P**；冲抵明细 **O**（`DEPOSIT_OFFSET`）+ 渠道 **C**；仅 O>0 调余额；有余原路退（含银联全退+重收）；**废除**少补 `collectAdvanceDeposit` |
| 取消住院结算 | 老 `inpa-un-pay` |
| 打印/电票 | 三类打印、`invoiceZYMake`；UI 按钮仅占位 |
| 扫码异步回执 | 可保留扩展位，本期不编排 |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-FIN-002-住院预交金.md` | 渠道、支付草稿→结算节奏、展示余额口径 |
| `AS-OP-FIN-002-门诊收费.md` | `createPaymentDraftFromPreSettle` / 正式结算编排参照 |
| `AS-IP-NUR-006-住院护士站费用明细.md` | 费用行进入 `PENDING_SETTLEMENT` 的上游；**不复用**其 IF 编号 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | Request/Response/DTO 枚举与状态字段一律 **String code**（`EncounterStatus#getCode()`、`ChargeItemStatus`、`PaymentKind` 等） |
| QueryService口径 | 队列 **`EncounterContextReader#pageInpatientSettleQueue`**（**Q-IPI-007**）；待结算合计 **`ChargeItemQueryService#mapPendingSettlementTotalPriceByEncounterIds`** / **`#sumPendingSettlementTotalPriceByEncounterId`** |
| 反结（同包） | **IF-IP-RVS-01** `InpatientSettleAppService#reverseInpatientSettle`；Pre **`InpatientReverseSettlePresupport**；读侧 **`InpatientSettleReadLoader`**（IF-IP-STL-07 增量见 **AS-IP-FIN-004-SDD §3.2**） |
| 召回内嵌（跨 AS） | **AS-IP-NUR-002** `RecallReverseSettleSupport#execute`（**不**调 `InpatientSettleAppService`；见 **AS-IP-FIN-004-SDD §3.4**） |
| 展示余额 | **`InpatientDisplayAccountBalanceAssembler#computeDisplayAccountBalance(account, pending)`**（**库内余额 − 待结算**）；文案 **`InpatientAccountBalanceDisplayAssembler`**（单一余额，如 `500.00 元`） |
| 队列页签 | `settleQueueTab`：`InpatientSettleQueueTab.PENDING_SETTLE` / `SETTLED` |
| 费用呈现 | `presentationMode`：`DETAIL`（明细可勾选）/ `SUMMARY`（大类汇总） |
| 分页 | `pageNo` **1-based**；出参 **`PagedResult`** 经 **`PagedResultBuilder`**（B.10.11） |
| 事务口径 | IF-IP-STL-06 **产品期望**单业务事务；**成码口径**见 §5.2 **DOC-CONFLICT**（App **不**声明 `@Transactional`） |
| QueryService一致性 | 写接口成功后队列/摘要以领域事件 **最终一致** 刷新；禁止 App 同步写 ``adm_encounter` + `InpatientEncounterContextQueryModel`` |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/financial/settle/
  service/
    InpatientSettleAppService.java
  request/
    LoadInpatientSettlePageInitRequest.java
    PageInpatientSettleQueueRequest.java
    LoadInpatientSettleSummaryRequest.java
    QueryInpatientSettleChargesRequest.java
    PreSettleInpatientChargesRequest.java
    ConfirmInpatientSettleRequest.java
    LoadSettledInpatientPaymentViewRequest.java
  response/
    LoadInpatientSettlePageInitResponse.java
    PageInpatientSettleQueueResponse.java
    LoadInpatientSettleSummaryResponse.java
    QueryInpatientSettleChargesResponse.java
    PreSettleInpatientChargesResponse.java
    ConfirmInpatientSettleResponse.java
    LoadSettledInpatientPaymentViewResponse.java
  dto/
    InpatientSettleQueueItemDto.java
    InpatientSettleChargeLineDto.java
    InpatientSettleCategorySummaryDto.java
    InpatientSettlePaymentDetailDto.java
    SettledPaymentBriefDto.java
  mapper/
    InpatientSettleMapper.java
    InpatientSettleMapperImpl.java
    InpatientSettleReadMapper.java

zxplat/health-module-support/.../querysupport/financial/
  InpatientSettleReadLoader.java              # IF-IP-STL-03/04/07 读路径
  InpatientSettleReadAssembler.java
  InpatientReverseSettlePreSupport.java       # 反结 Pre（与 ATD `RecallReverseSettlesupport 共用）
  dto/SettledPaymentBriefDto.java             # IF-IP-STL-07 增量（FIN-004 §3.2）
```

> 包名：`com.zxplat.domain.application.inpatient.financial.settle`  
> HTTP：`AppServiceControllerRegistrar` → `POST /api/app/inpatientSettle/{methodName}`  
> 编排常量：`com.zxplat.domain.share.constants.InpatientSettleConstants`（**已落地**）；队列页签 `InpatientSettleQueueTab`（**已落地**）；**禁止**在 `health-application` 下建 `constants` 子包  
> 公共库：`OptionDto`（`zxplat-framework`）、`PagedResult` / `PagedResultBuilder`（分页装配，**不**列入本模块 `dto/`）

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径操作人（IF-IP-STL-05/06，B.6.1） |
| `PageWindowGate` | 分页入站门控（IF-IP-STL-02） |
| `PaymentChannelGate` | 渠道 code 校验（IF-IP-STL-05/06） |
| `PatientEncounterConsistencyGate` | 患者/就诊一致（写 IF） |
| `EncounterContextReader` | IF-IP-STL-02：`pageInpatientSettleQueue`（`application.shared.encounter`） |
| `EncounterQueryService` | 薄就诊门控、`mustGetEncounterOfClass`（IF-IP-STL-03～07） |
| `InpatientInfoQueueDisplayAssembler` | 队列/摘要展示回填 + `contractDisplay` |
| `InpatientDisplayAccountBalanceAssembler` | 摘要/队列展示余额（IF-IP-STL-02/03） |
| `EncounterQueryService` | `mustGetEncounterOfClass`（`IMP`）与出院全结门禁 |
| `AccountQueryService` | 个人现金户、`mapRegistrationAccountsByEncounterIds` |
| `ChargeItemQueryService` | 待结算明细/合计、出院全结集合校验 |
| `ChargeItemDomainService` | `preSettleInpatientChargeItems`、`settleInpatientChargeItems` |
| `PaymentReconciliationDomainService` | 草稿创建/作废/住院结算 |
| `PaymentReconciliationQueryService` | 草稿加载、流水列表、重叠收费项校验 |
| `InvoiceAfterPaymentOrchestration` | `createDraftAndScheduleIssueIfPayable`（**BR-IP-STL-008** / **BR-INV-008**） |
| `InvoiceDomainService` | `issueElectronicInvoice`（博思回写） |
| `EncounterDomainService` | `completeInpatientFinancialSettlement`（出院全结） |
| `ProductDefinitionBatchLoader` | 费用行产品名称/规格/类目（IF-IP-STL-04/07） |
| `PractitionerQueryService` | 已结单操作员展示名（IF-IP-STL-07） |
| `ContractQueryService` | 队列 `contractDisplay`（IF-IP-STL-02） |
| `PaymentChannelDisplayAssembler` | 渠道中文展示（IF-IP-STL-07） |
| `InpatientSettleMapper` | Request/Command/DTO 装配 |
| `InpatientSettleReadLoader` / `InpatientSettleReadMapper` | IF-IP-STL-03/04/07 读路径（07 增量装配见 FIN-004） |
| `InpatientReverseSettlePresupport | **IF-IP-RVS-01** Pre（与 **ATD `RecallReverseSettlesupport** 共用） |

**下游 / 关联 AS**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-FIN-004-住院反结-SDD.md` v1.2 | 同包 **IF-IP-RVS-01**；IF-IP-STL-07 **`reverseEnabled`/`netSettleTotal`** 增量 §3.2 |
| `AS-IP-NUR-002-住院护士站入出转管理-SDD.md` | ATD-07 **`RecallReverseSettlesupport** 对称内嵌反结（非本 AppService 跨 AS 调用） |

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `EncounterContextReader` | `pageInpatientSettleQueue` | IF-IP-STL-02 |
| `EncounterQueryService` | `mustGetInpatientContextByEncounterId` | IF-IP-STL-03/04/07 |
| `EncounterQueryService` | `mustGetEncounterOfClass` | IF-IP-STL-03～07 |
| `EncounterQueryService` | `mapPatientIdByEncounterIds` | IF-IP-STL-02（`patient_id` 兜底） |
| `AccountQueryService` | `getRegistrationAccount` | IF-IP-STL-03 |
| `AccountQueryService` | `mapRegistrationAccountsByEncounterIds` | IF-IP-STL-02 |
| `ChargeItemQueryService` | `sumPendingSettlementTotalPriceByEncounterId` | IF-IP-STL-03 |
| `ChargeItemQueryService` | `mapPendingSettlementTotalPriceByEncounterIds` / `mapSettledTotalPriceByEncounterIds` | IF-IP-STL-02/03 待结算与已结清文案 |
| `ChargeItemQueryService` | `listEncounterIdsWithPendingSettlementCharges` | IF-IP-STL-02 Loader 预筛 |
| `PaymentReconciliationQueryService` | `mapSettleAutoSupplementAdvanceByEncounterIds` | IF-IP-STL-02/03 展示余额少补扣减 |
| `ChargeItemQueryService` | `listChargeItemsByEncounterId` | IF-IP-STL-04/06（待结算过滤 + 全结校验） |
| `ChargeItemQueryService` | `loadChargeItemsByIds` | IF-IP-STL-05/06/07 |
| `PaymentReconciliationQueryService` | `mustGetPayment` | IF-IP-STL-06 |
| `PaymentReconciliationQueryService` | `listPaymentRecDetailsByPaymentId` | IF-IP-STL-06 |
| `PaymentReconciliationQueryService` | `findPaymentsByEncounter` | IF-IP-STL-05/07 |
| `PaymentReconciliationQueryService` | `findPaySuccessOrRefundingOverlappingChargeItems` | IF-IP-STL-06（**BR-IP-STL-007**） |
| `PaymentReconciliationQueryService` | `mapFirstPayEnumByPaymentIds` | IF-IP-STL-07 |
| `PaymentReconciliationDomainService` | `abortPreSettlementBeforePayment` | IF-IP-STL-05 |
| `PaymentReconciliationDomainService` | `createPaymentDraftFromPreSettle` | IF-IP-STL-05 |
| `PaymentReconciliationDomainService` | `settleInpatientPayment` | IF-IP-STL-06 |
| `ChargeItemDomainService` | `preSettleInpatientChargeItems` | IF-IP-STL-05 |
| `ChargeItemDomainService` | `settleInpatientChargeItems` | IF-IP-STL-06 |
| `EncounterDomainService` | `completeInpatientFinancialSettlement` | IF-IP-STL-06（出院全结分支） |
| `InvoiceAfterPaymentOrchestration` | `createDraftAndScheduleIssueIfPayable`（**BR-IP-STL-008** / **BR-INV-008**） | IF-IP-STL-06 |
| `InvoiceDomainService` | `issueElectronicInvoice`（博思回写） | IF-IP-STL-06 |
| `ProductDefinitionBatchLoader` | `loadAsMap` | IF-IP-STL-04/07 |
| `PractitionerQueryService` | `mapDisplayNameByEntererIds` | IF-IP-STL-07 |
| `ContractQueryService` | `mapContractNameByBusNoIn` | IF-IP-STL-02 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3.1 `adm_encounter.class_enum` | 本场景 **`IMP`** |
| 就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §2.3.1 IMP 状态机 | 全结 `PENDING`→`COMPLETED`；**本期无中途结算**；**BR-IP-STL-003/004** |
| 收费项状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §2 状态机 | 可选 **`PENDING_SETTLEMENT`**；结 **`SETTLED`**；**BR-IP-STL-002** |
| 支付业务类型 | `com.zxplat.domain.share.enums.PaymentKind` | `DM-FIN-002` §3.1 `kind_enum` | **`INPATIENT_CLINIC`** |
| 收付方向 | `com.zxplat.domain.share.enums.PaymentType` | `DM-FIN-002` §3.1 `payment_enum` | 结算收取 **`PAY`** |
| 支付流水状态 | `com.zxplat.domain.share.enums.PaymentStatus` | `DM-FIN-002` §3.1 `status_enum` | 草稿 **`DRAFT`** → **`SUCCESS`** |
| 支付渠道 | `com.zxplat.domain.share.enums.PaymentChannel` | `DM-FIN-002` 明细 `pay_enum` | IF-05 草稿占位可用 **`CASHIER`**；IF-06 **SUCCESS 禁止**残留 `CASHIER`/`SCAN_PAY`（**BR-IP-STL-006a**） |
| 结算队列页签 | `InpatientSettleQueueTab.PENDING_SETTLE` / `SETTLED` | `InpatientEncounterContextQueryModel` §3 **Q-IPI-007** | `PageInpatientSettleQueueRequest.settleQueueTab` |
| 费用呈现模式 | `InpatientSettleConstants.PRESENTATION_MODE_DETAIL` / `SUMMARY` | `AS-IP-FIN-003` §1.3 | `QueryInpatientSettleChargesRequest.presentationMode` |
| 挂号锚点排除 | `com.zxplat.domain.share.constants.TableNameConstants.ADM_HEALTHCARE_SERVICE` | `DM-ADM-011` + **BR-IP-STL-012** | `ChargeItemQueryCommand.excludedServiceTable`（IF-04 Mapper；IF-06 出院全结待结算全集查询） |
| 编排消息码 | `InpatientSettleConstants.EX_IP_STL_*` | `AS-IP-FIN-003` §5 | 与 `EX-IP-STL-001`～`110` 一一对应；成功语义 `MESSAGE_CODE_SETTLE_SUCCESS` |

### 2.4 白名单双向一致性复核

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 正向（白名单 → §3 序列） | PASS | 白名单各方法均出现在至少一个 IF 编排序列 |
| 反向（§3 序列 → 白名单） | PASS | 编排序列调用均可于 §2.2 定位；`Mapper#*` 不列入领域白名单 |

### 2.5 成码前已闭合契约（领域层，2026-05-26）

| 主题 | 影响 IF | 约定（成码必须遵守） |
| --- | --- | --- |
| 结算队列 | IF-IP-STL-02 | **`pageInpatientSettleQueue`** 已实现；`PENDING_SETTLE` 页签：仅 **`status_enum = PENDING`**（召回后 `REGISTERED` 不入队）；`SETTLED` 页签：存在 **`INPATIENT_CLINIC` + SUCCESS/REFUNDED** 流水（**`applySettleQueueFilter` EXISTS**）；**Asm 按 `bus_no` 去重** + **`countInpatientSettleQueueDistinctBusNo`** |
| 住院预结算门禁 | IF-IP-STL-05 | **`preSettleInpatientChargeItems`** 仅接受 **`PENDING_SETTLEMENT`**；**零元出院全结**允许 `chargeItemIds=[]`（就诊 `PENDING` 且无待结算行）并**跳过**步骤 8 |
| 出院全结临床 Pre | IF-IP-STL-05/06 | 出院全结判定后调用 **`InpatientDischargeClinicalGateSupport#assertDischargeClinicalGatePassed`**（与 ATD-05 同源；**BR-IP-STL-018** / **BR-IP-FLW-008 v1.2**） |
| 出院财务终态 | IF-IP-STL-06 | **`completeInpatientFinancialSettlement`** 仅 `IMP+PENDING`；**有活动床** → `COMPLETED`；**无活动床**（召回未占床轨）→ **`DISCHARGED`** 并收口活动 WARD（**跳过** ATD-06 清床）；**零元全结**允许空 `chargeItemIds` |
| 队列 `patient_id` 兜底 | IF-IP-STL-02 | 与预交金同：`EncounterQueryService#mapPatientIdByEncounterIds` |
| 支付明细合计 | IF-IP-STL-05/06 | **`sum(paymentDetails)` = 轧差计划渠道实付 C**（自费=医保同一套；C=0 可空）；医保正式落账后从表明细由后端写入统筹/个账/冲抵/渠道；SUCCESS 补收行禁止 `CASHIER`/`SCAN_PAY`（**BR-IP-STL-006a**）；不等或渠道未落成 → **EX-IP-STL-106** |
| 展示余额 | IF-IP-STL-02/03 | **`InpatientDisplayAccountBalanceAssembler`**（库内余额 − **仅** `PENDING_SETTLEMENT`）；**`InpatientAccountBalanceDisplayAssembler`** 文案；`mapSettledTotalPriceByEncounterIds` 供「已结清」主文案 |
| 结算少补 | IF-IP-STL-05/06 | **已废除**少补收预交金；不够时 C=`P−D` 写入结算渠道明细（DD R1） |
| 出院全结多退 / 原路退 | IF-IP-STL-06 | 冲抵后按库内余额原路退（R3=`D−P`；R4/R6=`D`）；扫码可挂起第二阶段 `completeInpatientDischargePrepayRefund`（自费/医保共用） |

### 2.6 文档与代码追溯（一表）

| IF | App 方法 | 主要领域写 | 主要读侧 | / 投影 |
| --- | --- | --- | --- | --- |
| IF-01 | `loadInpatientSettlePageInit` | — | — | — |
| IF-02 | `pageInpatientSettleQueue` | — | `pageInpatientSettleQueue`、待结算合计 Map | **Q-IPI-007** |
| IF-03 | `loadInpatientSettleSummary` | — | Account、ChargeItem 合计 | InpatientEncounterContextQueryModel 头 |
| IF-04 | `queryInpatientSettleCharges` | — | `listChargeItemsByEncounterId`、ProductDefinition | — |
| IF-05 | `preSettleInpatientCharges` | `preSettleInpatientChargeItems` + 支付草稿 | — | `ChargeItemPreSettledEvent` |
| IF-06 | `confirmInpatientSettle` | （医保）平台确认 → 支付 SUCCESS（冲抵 O + 渠道 C + 医保统筹/个账/现金）→ ChargeItem SETTLED → **仅 O>0** `balance −= O` → 发票草稿 → 原路退（可第二阶段）→ `completeInpatientFinancialSettlement` | 草稿/重叠校验 | `InpatientPaymentSettledEvent` / `InpatientFinancialSettlementCompletedEvent` |
| IF-07 | `loadSettledInpatientPaymentView` | — | 成功流水 + 费用快照 | — |

**包路径**：`com.zxplat.domain.application.inpatient.financial.settle` · **HTTP 前缀**：`POST /api/app/inpatientSettle/{methodName}`

### 2.7 共享常量（`zxplat-domain`）

| 常量 | 值 / 说明 | 用于 |
| --- | --- | --- |
| `InpatientSettleQueueTab.PENDING_SETTLE` | 有待结算 | IF-02 缺省页签 |
| `InpatientSettleQueueTab.SETTLED` | 已结算（存在住院临床结算流水；列表按住院号去重） | IF-02 |
| `PRESENTATION_MODE_DETAIL` | `DETAIL` | IF-01/04 |
| `PRESENTATION_MODE_SUMMARY` | `SUMMARY` | IF-01/04 |
| `EX_IP_STL_001`～`110` | 对齐 AS §5 | Gate/Pre 业务异常 |
| `MESSAGE_CODE_SETTLE_SUCCESS` | 正式结算成功语义 | IF-06 Response |
| `SETTLE_AMOUNT_COMPARE_SCALE` | `2` | IF-05/06 金额 `compareTo` |
| `INVOICE_TYPE_INPATIENT` | 发票草稿类型 | IF-06 Cmd ③ |

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败都不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-STL-01 | `loadInpatientSettlePageInit` | 结算页初始化 | `POST /api/app/inpatientSettle/loadInpatientSettlePageInit` |
| IF-IP-STL-02 | `pageInpatientSettleQueue` | 结算患者队列查询 | `POST /api/app/inpatientSettle/pageInpatientSettleQueue` |
| IF-IP-STL-03 | `loadInpatientSettleSummary` | 结算摘要（待结算患者） | `POST /api/app/inpatientSettle/loadInpatientSettleSummary` |
| IF-IP-STL-04 | `queryInpatientSettleCharges` | 结算专用费用查询 | `POST /api/app/inpatientSettle/queryInpatientSettleCharges` |
| IF-IP-STL-05 | `preSettleInpatientCharges` | 住院预结算 | `POST /api/app/inpatientSettle/preSettleInpatientCharges` |
| IF-IP-STL-06 | `confirmInpatientSettle` | 确认住院结算 | `POST /api/app/inpatientSettle/confirmInpatientSettle` |
| IF-IP-STL-07 | `loadSettledInpatientPaymentView` | 已结算单查阅 | `POST /api/app/inpatientSettle/loadSettledInpatientPaymentView` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数门禁 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`*DomainService` 持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | `InpatientSettleMapper` 纯 DTO 装配 | — |

记法：`ChargeItemDomainService#preSettleInpatientChargeItems(BatchChargeItemIdsCommand)` 表示注入 Bean 调用。

### 3.2 IF-IP-STL-01 `loadInpatientSettlePageInit`（结算页初始化）

**HTTP**：`POST /api/app/inpatientSettle/loadInpatientSettlePageInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 占位请求体，与门诊收费初始化同形态 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `settleQueueTabOptions` | `List<OptionDto>` | 队列页签：有待结算 / 已结算（`InpatientSettleQueueTab` code + 中文 label） |
| `presentationModeOptions` | `List<OptionDto>` | 费用 Tab：明细清单 / 大类汇总 |
| `paymentChannelOptions` | `List<OptionDto>` | 用户可选支付渠道（**ADR-PAY-001**；`PaymentChannelOptionProvider`；与预交金/门诊收费对齐） |
| `defaultSettleQueueTab` | String | 缺省 **`PENDING_SETTLE`** |
| `defaultPresentationMode` | String | 缺省 **`DETAIL`** |

**关键校验**

- 初始化加载异常 → **EX-IP-STL-101**。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-STL-101 | 初始化失败 | 稍后重试 |

**Mapper 接口方法签名**

- `toLoadInpatientSettlePageInitResponse(List<OptionDto> queueTabs, List<OptionDto> presentationModes, List<OptionDto> paymentChannels): LoadInpatientSettlePageInitResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | Request | 无额外参数；仅读路径 |
| 2 | Pre | App 装配 `settleQueueTabOptions` | `InpatientSettleQueueTab` 常量 | 中文 label 在 Mapper/Asm |
| 3 | Pre | App 装配 `presentationModeOptions` | `InpatientSettleConstants` | `PRESENTATION_MODE_DETAIL` / `SUMMARY` |
| 4 | Pre | `PaymentChannelOptionProvider#loadUserSelectableOptions` | — | 477 ∩ 枚举 ∩ 模块 Gate（**ADR-PAY-001**） |
| 5 | Asm | `InpatientSettleMapper#toLoadInpatientSettlePageInitResponse` | 上步选项 | 纯展示 |

---

### 3.3 IF-IP-STL-02 `pageInpatientSettleQueue`（结算患者队列查询）

**HTTP**：`POST /api/app/inpatientSettle/pageInpatientSettleQueue`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | N | 住院号/姓名/床号模糊 |
| `admissionDate` | LocalDate | N | 入院日期（对齐 `InpatientEncounterContextQueryModel.start_time` 日期部分） |
| `settleQueueTab` | String | N | `PENDING_SETTLE`（缺省）/ `SETTLED` |
| `pageNo` | Integer | Y | 页码（1-based） |
| `pageSize` | Integer | Y | 页大小 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<InpatientSettleQueueItemDto>` | 队列卡片 |
| `totalCount` | Long | 总条数；**`SETTLED` 页签** = **不重复住院号（`bus_no`）** 数量 |
| `pageNo` | Integer | 当前页（1-based） |
| `pageSize` | Integer | 页大小 |

**DTO 字段表：InpatientSettleQueueItemDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |
| `encounterBusNo` | String | Y | 住院号 |
| `patientName` | String | Y | 患者姓名 |
| `genderEnum` | String | N | 性别 code |
| `genderEnumText` | String | N | 性别展示 |
| `ageText` | String | N | 年龄展示 |
| `organizationName` | String | N | 科室 |
| `wardName` | String | N | 病区 |
| `bedName` | String | N | 床号 |
| `attendingDoctorName` | String | N | 主治医生（`InpatientEncounterContextQueryModel` 展示口径：活动优先，清床/召回后末次 COMPLETED） |
| `statusEnum` | String | Y | 就诊状态 code |
| `statusEnumText` | String | Y | IMP 文案（`InpatientEncounterStatusDisplayUtil` 口径） |
| `startTime` | LocalDateTime | N | 入院时间 |
| `pendingSettlementTotal` | BigDecimal | N | **待结算页签**：该就诊 `PENDING_SETTLEMENT` 合计；已结算页签可 null |
| `lastSettleTime` | LocalDateTime | N | **已结算页签**：最近成功 `INPATIENT_CLINIC` 的 `bill_date` |
| `contractDisplay` | String | N | 费用性质名称（账户 `contract_no`→合同名） |
| `accountBalance` | BigDecimal | N | **展示可用余额**（库内余额 − 待结算；§2.5） |

**关键校验**

- `pageNo`≥1，`pageSize`≥1（`PageWindowGate`）。
- 查询异常 → **EX-IP-STL-101**。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-STL-101 | 队列查询失败 | 缩小条件或稍后重试 |

**Mapper 接口方法签名**

- `toInpatientEncounterPageQuery(PageInpatientSettleQueueRequest): InpatientEncounterPageQuery`
- `toInpatientSettleQueueItemDto(InpatientEncounterContextQueryModel, Account, InpatientQueueDisplayOverlay, BigDecimal pendingSettlementTotal, LocalDateTime lastSettleTime): InpatientSettleQueueItemDto`
- `toPageInpatientSettleQueueResponse(List<InpatientSettleQueueItemDto>, Page<InpatientEncounterContextQueryModel>): PageInpatientSettleQueueResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `PageWindowGate` | Request | 分页非法 → EX-IP-STL-101 |
| 2 | Pre | `InpatientSettleMapper#toInpatientEncounterPageQuery` | keyword、admissionDate、settleQueueTab | 置 `settleQueue=true`；缺省 tab=`PENDING_SETTLE` |
| 3 | Pre | **`EncounterContextReader#pageInpatientSettleQueue`** | `InpatientEncounterPageQuery` | **Q-IPI-007**；`PENDING_SETTLE`：仅 **`PENDING`**；`SETTLED`：**EXISTS** `INPATIENT_CLINIC` 且 `SUCCESS`/`REFUNDED`（排除 `CANCELLED`/`PLANNED`） |
| 4 | Pre | `EncounterQueryService#mapPatientIdByEncounterIds` | 当前页 `encounterId` | **若**QueryService `patient_id` 为空则回填（§2.5） |
| 5 | Pre | `AccountQueryService#mapRegistrationAccountsByEncounterIds` | 当前页 encounter 集合 | **批量**；禁止 N+1 |
| 6 | Pre | `ChargeItemQueryService#mapPendingSettlementTotalPriceByEncounterIds` | 当前页 encounter 集合 | 待结算合计 |
| 6b | Pre | `ChargeItemQueryService#mapSettledTotalPriceByEncounterIds` | 当前页 encounter 集合 | 已结清文案 |
| 6c | Pre | `PaymentReconciliationQueryService#filterReversedPendingResettleEncounterIds` | 当前页 encounter 集合 | 反结后展示模式 |
| 7 | Pre | `ContractQueryService#mapContractNameByBusNoIn` | 账户 `contract_no` 集合 | `contractDisplay` |
| 8 | Pre | App 解析最近结算时间 | **若** tab=`SETTLED`：`findPaymentsByEncounter` 过滤 `INPATIENT_CLINIC`+`SUCCESS` 取 max `bill_date` | **若** tab=`PENDING_SETTLE`：**跳过**本步 |
| 9 | Pre | `InpatientInfoQueueDisplayAssembler#buildOverlay` | QueryService行 + 合同 Map | 展示回填 |
| 10 | Asm | `InpatientSettleMapper#toInpatientSettleQueueItemDto` + `InpatientAccountBalanceDisplayDtoApplier`（`application.shared.inpatient`） | 账户 + pending/settled + overlay | `accountBalance` 文案化展示 |
| 11 | Asm | App `#dedupeSettledQueueItemsByEncounterBusNo` | 当前页 DTO 列表 | **若** tab=`SETTLED`：按 **`encounterBusNo` 去重**，保留 **`lastSettleTime` 较新**行（**BR-IP-STL-017**） |
| 12 | Pre | **`EncounterContextReader#countInpatientSettleQueueDistinctBusNo`** | 同步骤 2 筛选条件 | **若** tab=`SETTLED`：装配 **`totalCount`**（`EncounterQueryService#countDistinctBusNoEncounterEntities`） |

---

### 3.4 IF-IP-STL-03 `loadInpatientSettleSummary`（结算摘要（待结算患者））

**HTTP**：`POST /api/app/inpatientSettle/loadInpatientSettleSummary`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID（一致性校验） |
| `selectedChargeItemIds` | `List<Long>` | N | 前端已勾选 ID；**空**则服务端按「全部待结算」计选中合计 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊 ID |
| `patientId` | Long | 患者 ID |
| `encounterBusNo` | String | 住院号 |
| `patientName` | String | 患者姓名 |
| `organizationName` | String | 科室 |
| `wardName` | String | 病区 |
| `bedName` | String | 床号 |
| `attendingDoctorName` | String | 主治医生（展示口径同队列） |
| `statusEnum` | String | 就诊状态 |
| `statusEnumText` | String | 状态展示 |
| `pendingSettlementTotal` | BigDecimal | 该就诊全部 `PENDING_SETTLEMENT` 合计 |
| `prepayNetBalance` | BigDecimal | 个人现金户**库内余额 D**（`adm_account.balance`） |
| `selectedTotal` | BigDecimal | 本次选中费用合计 |
| `balanceHint` | BigDecimal | **调试保留**：库内余额 − 选中合计；**禁止**驱动主路径应付文案（**BR-IP-STL-013**） |
| `personalCashPayable` | BigDecimal | **个人现金应付 P 预估**（自费=选中合计经 Planner；**有医保挂账户时为空**，须 IF-05） |
| `depositOffsetAmount` | BigDecimal | **冲抵 O 预估**（同上；医保摘要为空） |
| `channelPayable` | BigDecimal | **渠道实付 C 预估**（收银应付；医保摘要为空） |
| `prepayRefundAmount` | BigDecimal | 应退预交金预估（自费；医保摘要为空） |
| `prepayFullRefund` | Boolean | 是否全额退 D 预估 |
| `depositSettleRuleCode` | String | 轧差分支 R1～R7（自费预估） |
| `displayAccountBalance` | BigDecimal | 展示可用余额（库内余额 − 全部待结算合计） |
| `accountBalanceSummaryText` | String | 主展示文案（`InpatientAccountBalanceDisplayAssembler`） |

**关键校验**

- 就诊非 `IMP` 或不存在 → **EX-IP-STL-102**。
- `COMPLETED` 就诊 → **BR-IP-STL-009**；应走 IF-IP-STL-07，本 IF 拒绝或提示切换页签。
- 无个人现金户 → **EX-IP-STL-103**。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-STL-102 | 就诊不存在或非住院 | 刷新并重新选择 |
| EX-IP-STL-103 | 账户不存在 | 请先完成住院登记 |

**Mapper 接口方法签名**

- `InpatientSettleReadMapper#toSummaryResponse(InpatientSettleSummaryResult): LoadInpatientSettleSummaryResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | encounterId、patientId 非空 | — |
| 2 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | encounterId, `IMP` | → EX-IP-STL-102 |
| 3 | Pre | App 状态门禁 | `status_enum` | **若** `COMPLETED` → 拒绝（**BR-IP-STL-009**） |
| 4 | Pre | `PatientEncounterConsistencyGate` | patientId | — |
| 5 | Pre | `InpatientSettleContextQueryService#mustLoadSettleContext` | encounterId, patientId | 患者头 + 登记户 + 待结合计 |
| 6 | Pre | App 计算 `selectedTotal` | **若** `selectedChargeItemIds` 非空：`loadChargeItemsByIds` 求和；**否则** = 待结全额 | 默认全选语义 |
| 7 | Asm | `InpatientSettleReadAssembler#toInpatientSettleSummaryResult` | 上步金额 + 账户展示 | `balanceHint` 调试保留 |
| 8 | Asm | App `applySelfPayDepositPlanPreviewOnSummary` | D + selectedTotal + 含银联 | **无医保挂账户**时回填 P/O/C；有医保户则跳过 |

---

### 3.5 IF-IP-STL-04 `queryInpatientSettleCharges`（结算专用费用查询）

**HTTP**：`POST /api/app/inpatientSettle/queryInpatientSettleCharges`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |
| `presentationMode` | String | Y | `DETAIL` / `SUMMARY` |
| `chargeDateFrom` | LocalDate | N | 费用业务日起；缺省 = 入院日 |
| `chargeDateTo` | LocalDate | N | 费用业务日止；缺省 = 当日（闭区间，**BR-IP-STL-012**） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊 ID |
| `presentationMode` | String | 回显呈现模式 |
| `lines` | `List<InpatientSettleChargeLineDto>` | **DETAIL** 模式行列表（可勾选） |
| `categories` | `List<InpatientSettleCategorySummaryDto>` | **SUMMARY** 模式大类汇总 |
| `pendingSettlementTotal` | BigDecimal | 当前条件下待结算合计 |
| `defaultSelectAll` | Boolean | 首次加载缺省 **true**（默认全选待结算） |

**DTO 字段表：InpatientSettleChargeLineDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `chargeItemId` | Long | Y | 收费明细 ID |
| `itemName` | String | N | 项目名称（产品QueryService / `charge_name` 回落） |
| `specText` | String | N | 规格展示 |
| `categoryCode` | String | N | 费用大类 code |
| `categoryDisplay` | String | N | 费用大类中文 |
| `unitPrice` | BigDecimal | N | 单价 |
| `quantityValue` | BigDecimal | N | 数量 |
| `quantityUnit` | String | N | 单位 |
| `lineAmount` | BigDecimal | Y | 行合计 `total_price` |
| `statusEnum` | String | Y | 典型 `PENDING_SETTLEMENT` |
| `statusEnumText` | String | N | 状态展示 |
| `occurrenceTime` | LocalDateTime | N | 业务发生时间 |
| `sourceText` | String | N | 来源可读文案（划价/医嘱等） |
| `selected` | Boolean | Y | 是否默认勾选（待结算默认 true） |

**DTO 字段表：InpatientSettleCategorySummaryDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `categoryCode` | String | Y | 大类 code |
| `categoryDisplay` | String | Y | 大类名称 |
| `amount` | BigDecimal | Y | 该大类下待结算金额合计 |
| `lineCount` | Integer | Y | 明细条数 |

**关键校验**

- 仅返回 **`PENDING_SETTLEMENT`**（**BR-IP-STL-002**）；排除 `service_table = adm_healthcare_service`（**BR-IP-STL-012**）。
- 产品类目缺失导致无法展示 → **EX-IP-STL-104**（按实现策略：缺省文案或硬失败）。
- 就诊无效 → **EX-IP-STL-102**。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-STL-102 | 就诊无效 | 重新选择患者 |
| EX-IP-STL-104 | 展示数据不完整 | 联系管理员维护产品类目 |

**Mapper 接口方法签名**

- `toChargeItemQueryCommand(QueryInpatientSettleChargesRequest, LocalDate admissionDate): ChargeItemQueryCommand`
- `toInpatientSettleChargeLineDtoList(List<ChargeItem>, Map<String, ProductDefinitionDisplayDto>): List<InpatientSettleChargeLineDto>`
- `toInpatientSettleCategorySummaryDtoList(List<InpatientSettleChargeLineDto>): List<InpatientSettleCategorySummaryDto>`
- `toQueryInpatientSettleChargesResponse(Long encounterId, String mode, List lines, List categories, BigDecimal total, boolean defaultSelectAll): QueryInpatientSettleChargesResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | encounterId、presentationMode | 非法 mode → 参数错误 |
| 2 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `IMP` | EX-IP-STL-102 |
| 3 | Pre | `PatientEncounterConsistencyGate` | patientId | — |
| 4 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | encounterId | 取 `start_time` 作缺省 `chargeDateFrom` |
| 5 | Pre | `InpatientSettleMapper#toChargeItemQueryCommand` | 入院日～`chargeDateTo` | `statusEnumIn=[PENDING_SETTLEMENT]`；`excludedServiceTable=ADM_HEALTHCARE_SERVICE` |
| 6 | Pre | `ChargeItemQueryService#listChargeItemsByEncounterId` | Command | 仅读；App 按业务日过滤行（闭区间） |
| 7 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 目录锚点批量 | 避免 N+1 |
| 8 | Asm | Mapper 行/大类装配 | presentationMode | **若** `SUMMARY`：按 `categoryCode` 分组汇总；**若** `DETAIL`：输出 `lines` |
| 9 | Asm | `toQueryInpatientSettleChargesResponse` | — | `defaultSelectAll=true` |

---

### 3.6 IF-IP-STL-05 `preSettleInpatientCharges`（住院预结算）

**HTTP**：`POST /api/app/inpatientSettle/preSettleInpatientCharges`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |
| `chargeItemIds` | `List<Long>` | Y | 所选待结算明细；**零元出院全结**可为空数组（须就诊 `PENDING` 且无待结算行） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentDraftId` | Long | `INPATIENT_CLINIC` 支付草稿 ID |
| `receivableTotal` | BigDecimal | 应收合计（选中 `total_price` 之和） |
| `displayAmount` | BigDecimal | **渠道实付 C**（确认 `paymentDetails` 合计须等于本值；**不再**表示自费少补/医保全额双语义） |
| `personalCashPayable` | BigDecimal | **个人现金应付 P**（结算主单金额） |
| `depositOffsetAmount` | BigDecimal | **冲抵额 O** |
| `prepayRefundAmount` | BigDecimal | 应退预交金 |
| `prepayFullRefund` | Boolean | 是否全额退 D（R4/R6 含银联） |
| `depositSettleRuleCode` | String | 分支 R1～R7 |
| `prepayNetBalance` | BigDecimal | 预结算时点库内预交金余额 D（登记个人现金户） |
| `insurancePreSettled` | Boolean | 是否已完成医保预结算 |
| `ybMedfeeSumamt` / `ybFundPaySumamt` / `ybAcctPay` / `ybPsnCashPay` | BigDecimal | 医保 setlinfo 主金额（只读） |
| `ybHifpPay` / `ybOthPay` / `ybActPayDedc` / `ybCvlservPay` | BigDecimal | 2303 基金分项（只读展示） |
| `ybCvlservFlag` | String | 公务员标志（只读） |
| `previewText` | String | 预览文案（如「患者实付：…」或「预交金冲抵，应收合计：…」） |

**DTO 字段表：InpatientSettlePaymentDetailDto**（预填正式结算，可选随 Response 返回草稿渠道占位）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `payChannelCode` | String | Y | 支付渠道 code |
| `amount` | BigDecimal | Y | 明细金额 |

**关键校验**

- `chargeItemIds` 为 null → **EX-IP-STL-001**；**空数组**仅当就诊 `PENDING` 且无待结算行（零元出院全结）；否则 **EX-IP-STL-001**。
- 非零元路径：合计 ≤0 → **EX-IP-STL-001**。
- 就诊 `COMPLETED`/`CANCELLED`/`DISCHARGED` → 拒绝。
- 含非 `PENDING_SETTLEMENT` 或非本就诊 → **EX-IP-STL-001** / 领域拒绝。
- 作废旧草稿失败 → **EX-IP-STL-105**。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-STL-001 | 未选费用或金额非法 | 请选择待结算费用 |
| EX-IP-STL-102 | 就诊无效 | 重新选择患者 |
| EX-IP-STL-105 | 草稿冲突 | 重新预结算 |

**Mapper 接口方法签名**

- `toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId): BatchChargeItemIdsCommand`
- `toCreatePaymentDraftFromPreSettleCommand(PreSettleInpatientChargesRequest, BigDecimal receivableTotal, List<InpatientSettlePaymentDetailDto> draftDetails, Long operatorId): CreatePaymentDraftFromPreSettleCommand`
- `toPreSettleInpatientChargesResponse(Long paymentDraftId, BigDecimal receivableTotal): PreSettleInpatientChargesResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | encounterId、patientId、`chargeItemIds` 非 null | null → EX-IP-STL-001 |
| 2 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `IMP` | **若** `COMPLETED`/`CANCELLED` → 拒绝 |
| 3 | Pre | `PatientEncounterConsistencyGate` | patientId | — |
| 4 | Pre | App 零元全结判定 | `chargeItemIds.isEmpty()` | **若** 非 `PENDING` 或仍有待结算行 → EX-IP-STL-001 |
| 5 | Pre | `ChargeItemQueryService#loadChargeItemsByIds` | ids（非零元） | 归属就诊 + `PENDING_SETTLEMENT`（**BR-IP-STL-002**） |
| 6 | Pre | App 汇总 `receivableTotal` | 步骤 5 | ≤0 → EX-IP-STL-001 |
| 7 | Pre | `PaymentReconciliationQueryService#findPaymentsByEncounter` | encounterId | 过滤 `INPATIENT_CLINIC` + `DRAFT` |
| 8 | Cmd | `PaymentReconciliationDomainService#abortPreSettlementBeforePayment` | `AbortPreSettlementBeforePaymentCommand` | **对步骤 7 每条**执行；**写库点 ①** 作废旧草稿（**BR-IP-STL-010**） |
| 9 | Cmd | `ChargeItemDomainService#preSettleInpatientChargeItems` | `BatchChargeItemIdsCommand` | **非零元**才执行；**写库点 ②** 预结算事件 |
| 10 | Pre | App 装配草稿占位 `paymentDetails` | 单行 CASHIER 占位 | 合计= `receivableTotal`（零元时为 0） |
| 11 | Cmd | `PaymentReconciliationDomainService#createPaymentDraftFromPreSettle` | `CreatePaymentDraftFromPreSettleCommand` | **写库点 ③** 新 `DRAFT` |
| 12 | Pre | App `applyInsurancePreSettleIfNeeded` / `applySelfPayChannelPayableOnPreSettle` | P/D/含银联 + 2303 | 回填 `personalCashPayable`/`depositOffsetAmount`/`displayAmount=C`/`prepayRefund*`/`depositSettleRuleCode`/`prepayNetBalance`/`previewText`；医保另回填基金分项 |
| 13 | Asm | `InpatientSettleMapper#toPreSettleInpatientChargesResponse` | draftId、合计 | — |

---

### 3.7 IF-IP-STL-06 `confirmInpatientSettle`（确认住院结算）

**HTTP**：`POST /api/app/inpatientSettle/confirmInpatientSettle`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |
| `paymentDraftId` | Long | Y | IF-IP-STL-05 返回的草稿 ID |
| `chargeItemIds` | `List<Long>` | Y | 须与草稿绑定集合一致（**BR-IP-STL-005**）；**零元出院全结**可为空数组 |
| `paymentDetails` | `List<InpatientSettlePaymentDetailDto>` | Y | 渠道实付明细；**预交金充足/零元全结**可为空数组 |
| `billDate` | LocalDateTime | N | 结算时间；缺省服务端当前时刻 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentId` | Long | 成功支付主键 |
| `invoiceDraftId` | Long | 发票草稿 ID；零元结算时为 `null` |
| `dischargeFullSettle` | Boolean | 是否本次为出院全结 |
| `advanceRefundPaymentId` | Long | 出院多退预交金支付单 ID；无多退时为 null |
| `successMessageCode` | String | 成功语义码 `InpatientSettleConstants.MESSAGE_CODE_SETTLE_SUCCESS` |

**关键校验**

- 草稿非 `DRAFT` / 非 `INPATIENT_CLINIC` → **EX-IP-STL-106**。
- `chargeItemIds` 与草稿不一致 → **EX-IP-STL-106**。
- 渠道合计 ≠ **轧差计划 C** → **EX-IP-STL-106**（**BR-IP-STL-006**）；`C>0` 且明细为空 → **EX-IP-STL-106**「请录入支付明细」。
- 拟结项已 `SETTLED` 或与成功流水 chargeItem 交集 → **EX-IP-STL-106** / 领域拒绝（**BR-IP-STL-007**）。
- 出院全结：就诊 `PENDING` 且选中集 = 全部剩余待结算 → 否则 **EX-IP-STL-107/108**。
- 支付失败 → **EX-IP-STL-109**。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-STL-106 | 草稿/金额/集合不一致 | 重新预结算并核对 |
| EX-IP-STL-107 | 出院全结未全选 | 请全选待结算费用 |
| EX-IP-STL-108 | 非 PENDING 态提交 | 请先完成护士出院后再结算 |
| EX-IP-STL-111 | 待出院 ON_HOLD | 请先办理护士出院后再结算 |
| EX-IP-STL-112 | 中途结算 | 本期不支持中途结算 |
| EX-IP-STL-109 | 支付结算失败 | 以流水为准重试 |

**Mapper 接口方法签名**

- `toSettleInpatientPaymentCommand(ConfirmInpatientSettleRequest, Long operatorId): SettleInpatientPaymentCommand`
- `toBatchChargeItemIdsCommand(List<Long> chargeItemIds, Long operatorId): BatchChargeItemIdsCommand`
- `toCreateDraftInvoiceCommand(PaymentReconciliationQueryModel payment, Long paymentId): CreateDraftInvoiceCommand`
- `toCompleteInpatientFinancialSettlementCommand(Long encounterId, LocalDateTime settlementTime, Long operatorId): CompleteInpatientFinancialSettlementCommand`
- `toDischargeDepositRefundCommand(Long encounterId, Long patientId, Long accountId, BigDecimal refundAmount, Long operatorId): RefundAdvanceDepositCommand`
- `toSettleSupplementCollectCommand(...): CollectAdvanceDepositCommand`（**已废除**；保留签名防残留引用）
- `toConfirmInpatientSettleResponse(Long paymentId, Long invoiceDraftId, boolean dischargeFullSettle, Long advanceRefundPaymentId): ConfirmInpatientSettleResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | paymentDraftId、chargeItemIds、paymentDetails 非 null | 缺参拒绝 |
| 2 | Gate | `PaymentChannelGate` | paymentDetails | 渠道非法拒绝（冲抵/基金行由服务端写，不经本门控） |
| 3 | Pre | `PaymentReconciliationQueryService#mustGetPayment` | paymentDraftId | 非 `DRAFT` 或非 `INPATIENT_CLINIC` → EX-IP-STL-106 |
| 4 | Pre | `PaymentReconciliationQueryService#listPaymentRecDetailsByPaymentId` | draftId | 从表空 → EX-IP-STL-106 |
| 5 | Pre | App 集合一致性 | 头 `charge_item_ids` vs 请求 | **BR-IP-STL-005** |
| 6 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `IMP` | 加载 `status_enum` |
| 7 | Pre | `ChargeItemQueryService#loadChargeItemsByIds` | chargeItemIds（非空） | 任一条非 `PENDING_SETTLEMENT` → EX-IP-STL-106 |
| 8 | Pre | `PaymentReconciliationQueryService#findPaySuccessOrRefundingOverlappingChargeItems` | encounterId、chargeItemIds | 有交集 → EX-IP-STL-106（**BR-IP-STL-007**） |
| 9 | Pre | App `resolveDepositSettlementPlanForConfirm` | P/D/含银联 | 轧差计划 R1～R7（`InpatientDepositSettlementPlanner`） |
| 10 | Pre | App 金额对齐 | `sum(paymentDetails)` vs 计划 **C** | 不等 → EX-IP-STL-106；**废除**少补收预交金 |
| 11 | Pre | App 判定 `dischargeFullSettle` | 同 AS **BR-IP-STL-003** | EX-IP-STL-107/108/112 |
| 12 | Pre | `PatientEncounterConsistencyGate` | patientId | — |
| 13 | Cmd | App `buildDischargeSettlePayReceiptDetails` | 冲抵 O + 渠道 C + 医保基金 | 主单金额保持 **P**；医保可含统筹/个账/现金明细 |
| 13b | Pre | `ThirdPartyPaymentSettleOrchestrator#applyScanBeforeSettle` | **同一份** `settleReceiptDetails` + `clientScanResult` | **仅 C>0**；`SCAN_PAY`→微信/支付宝/银行卡；**禁止**另拷一份明细（否则落库仍为占位） |
| 13c | Gate | App `assertSettleSelfPayChannelsPersistable` | 补收行 | 禁止 `CASHIER`/`SCAN_PAY` → EX-IP-STL-106（**BR-IP-STL-006a**） |
| 13d | Asm | App `attachClientScanTradeNoToSelfPayLines` | 银联 `trade_no` | 补空 `pay_trans_no` 供 IF-07 回显 |
| 14 | Cmd | `settleInpatientPayment`（含 payReceiptDetails） | `SettleInpatientPaymentCommand` | **写库点 ①** `DRAFT→SUCCESS`；医保 `mergeInsurancePaymentDetailsIntoSettleCommand` |
| 15 | Cmd | `ChargeItemDomainService#settleInpatientChargeItems` | `BatchChargeItemIdsCommand`（含 `operatorId` + `billDate`） | **写库点 ②** → `SETTLED` + `enterer_id`/`entered_date`（**BR-IP-STL-007a**） |
| 16 | Cmd | `AccountDomainService#adjustAdvanceDepositBalance` | **−O**（仅 O>0） | **写库点 ②b** 冲抵占用；禁止按本批费用全额扣 |
| 17 | Cmd | `InvoiceAfterPaymentOrchestration#createDraftAndScheduleIssueIfPayable` | 票面=`receivableTotal`（费用合计） | **写库点 ③**；禁止改成 P/C；零元跳过 |
| 18 | Cmd | App `refundDischargeAdvanceDepositAsCash` | 仅出院全结 | **写库点 ④** 多退**一律现金**（DD v1.5；废除原路/Win 挂起） |
| 19 | Cmd | `EncounterDomainService#completeInpatientFinancialSettlement` | 退款未挂起时 | **写库点 ⑤**：**有活动床** `PENDING→COMPLETED`；**无活动床** `PENDING→DISCHARGED`（收口活动 WARD） |
| 20 | Asm | `InpatientSettleMapper#toConfirmInpatientSettleResponse` | paymentId、invoiceId、flag、advanceRefundPaymentId | — |
| 21 | — | QueryService | — | 投影由事件异步刷新 |

---

### 3.8 IF-IP-STL-07 `loadSettledInpatientPaymentView`（已结算单查阅）

> **FIN-004 增量（已成码）**：历史条 **`recordType`/`signedAmount`/`reverseEnabled`/`recallEnabled`/`netSettleTotal`**、流水过滤 **SUCCESS+REFUNDED**、收银台 **「反结」/「召回」** 写 **IF-IP-RVS-01** / **IF-IP-RCL-01** — 权威 **`AS-IP-FIN-004-SDD §3.2～§3.4`**。本节保留 FIN-003 基线读路径；成码经 **`InpatientSettleReadLoader`** + **`InpatientSettleReadMapper`** 委托 **`InpatientSettleAppService#loadSettledInpatientPaymentView`**。

**HTTP**：`POST /api/app/inpatientSettle/loadSettledInpatientPaymentView`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 住院就诊 ID |
| `patientId` | Long | Y | 患者 ID |
| `paymentId` | Long | N | 指定结算单；缺省取最近正向 `INPATIENT_CLINIC` 结单（`relation_id` 为空） |
| `presentationMode` | String | N | `DETAIL`/`SUMMARY`；缺省 `DETAIL` |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | Long | 就诊 ID |
| `payments` | `List<SettledPaymentBriefDto>` | 该就诊成功结算单列表（倒序） |
| `activePaymentId` | Long | 当前展示的单据 ID |
| `settleTime` | LocalDateTime | 结算时间 |
| `operatorName` | String | 操作员 |
| `receivableTotal` | BigDecimal | 当前单展示合计：优先 payment `tendered`/`display`（**个人现金 P**）；P≤0 时费用行对齐回落收费项合计（票面，非渠道 C） |
| `payMethodText` | String | 渠道展示（优先用户实收渠道，跳过冲抵/基金占位；首条明细回落） |
| `lines` | `List<InpatientSettleChargeLineDto>` | 费用快照（**不可勾选**，`selected` 固定 false；数量单位输出中文展示名） |
| `categories` | `List<InpatientSettleCategorySummaryDto>` | 大类快照 |

**DTO 字段表：SettledPaymentBriefDto**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paymentId` | Long | Y | 支付流水 ID |
| `paymentNo` | String | N | 支付业务单号（`fin_payment_reconciliation.payment_no`） |
| `settleTime` | LocalDateTime | Y | `bill_date` |
| `receivableTotal` | BigDecimal | Y | 本批合计（优先 tendered/display=**P**） |
| `personalCashPayable` | BigDecimal | N | **个人现金应付 P**（与主单 display 一致） |
| `depositOffsetAmount` | BigDecimal | N | **冲抵 O**（明细 `DEPOSIT_OFFSET` 合计；无则 0） |
| `channelPayable` | BigDecimal | N | **渠道实收 C**（现金/微信等；不含冲抵与医保基金） |
| `signedAmount` | BigDecimal | N | 带符号展示（认 P；逆向为负） |
| `payMethodText` | String | N | 渠道展示 |
| `insuranceSettled` | Boolean | N | 是否已关联医保正式结算回执 |
| `ybMedfeeSumamt` / `ybFundPaySumamt` / `ybHifpPay` / `ybOthPay` / `ybAcctPay` / `ybPsnCashPay` / `ybActPayDedc` / `ybCvlservPay` | BigDecimal | N | 2303/2304 基金分项（只读回填） |

**关键校验**

- 就诊须住院 `IMP`，且 **`status_enum ∉ { CANCELLED, PLANNED }`**（**v1.3 / FIN-004 §3.2**：含反结后 `IN_PROGRESS`、召回后 `REGISTERED`、已清床 `DISCHARGED` 等）；**队列入选**见 **BR-IP-STL-017**（按流水存在性 + 住院号去重，**非**仅 `COMPLETED`）。
- 无 `INPATIENT_CLINIC` 且 SUCCESS/REFUNDED 流水 → **EX-IP-STL-110**（待结算 Tab 前端对无历史静默为空）。

**失败码口径**

| 语义码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-IP-STL-102 | 就诊无效 | 重新选择 |
| EX-IP-STL-110 | 无结算记录 | 无历史结算单 |

**Mapper 接口方法签名**

- `toSettledPaymentBriefDtoList(List<PaymentReconciliationQueryModel>, Map<Long, String> payMethodTexts): List<SettledPaymentBriefDto>`
- `toLoadSettledInpatientPaymentViewResponse(...): LoadSettledInpatientPaymentViewResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | encounterId | — |
| 2 | Pre | `EncounterQueryService#mustGetEncounterOfClass` | `IMP` | — |
| 3 | Pre | App 状态 | 非 `CANCELLED`/`PLANNED` 的 IMP | **FIN-004 v1.3**：反结/召回后仍可读历史；**IF-02 已结算队列**按流水存在性，见 **BR-IP-STL-017** |
| 4 | Pre | `PatientEncounterConsistencyGate` | patientId | — |
| 5 | Pre | `PaymentReconciliationQueryService#findPaymentsByEncounter` | encounterId | App 过滤 `INPATIENT_CLINIC` + `SUCCESS`（**基线**）；**FIN-004** 扩展为 **SUCCESS/REFUNDED** 见 **FIN-004-SDD §3.2** |
| 6 | Pre | — | 步骤 5 为空 | → EX-IP-STL-110 |
| 7 | Pre | App 解析 `activePaymentId` | **若** request.paymentId 非空则用；**否则** 取步骤 5 中 **最近正向结单**（`relation_id` 为空；反结后避免默认落到逆向条） | — |
| 8 | Pre | `PaymentReconciliationQueryService#mustGetPayment` | activePaymentId | — |
| 9 | Pre | `ChargeItemQueryService#loadChargeItemsByIds` | 支付单绑定 chargeItemIds | 快照明细 |
| 10 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 产品锚点 | 展示装配 |
| 11 | Pre | `PractitionerQueryService#mapDisplayNameByEntererIds` + `mapFirstPayEnumByPaymentIds` | entererId、paymentId | 操作员名、渠道文案 |
| 12 | Asm | Mapper | lines/categories | `selected=false`；**IF-IP-STL-07 历史以 payment 为主**：明细锚 `charge_item_ids`，行金额按 payment 本批合计对齐；大类优先 `fin_payment_rec_static` |

## 4. QueryService 与事件刷新

### 4.1 本场景读侧一览

| QueryService / 查询 | 接口 / 事件 | 用于 IF |
| --- | --- | --- |
| **InpatientEncounterContextQueryModel** | `pageInpatientSettleQueue`（Q-IPI-007） | IF-IP-STL-02 |
| **InpatientEncounterContextQueryModel** | `mustGetInpatientContextByEncounterId` | IF-IP-STL-03/04/07 |
| **ChargeItem**（`ChargeItemQueryService`） | 待结算明细/合计 | IF-IP-STL-03/04/05/06 |
| **PaymentReconciliation**（`PaymentReconciliationQueryService`） | 草稿/成功流水 | IF-IP-STL-05/06/07 |
| **ProductDefinition**（`ProductDefinitionBatchLoader`） | `loadAsMap` | IF-IP-STL-04/07 |

字段级映射以 `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` §4.2、`DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` 为唯一真源；应用层 **禁止** 手写投影 SQL。

### 4.2 事件 → QueryService（写接口相关）

| 事件 | 触发 IF（Cmd 序号） | 消费语义 |
| --- | --- | --- |
| `PaymentDraftCreatedEvent` | IF-IP-STL-05 ⑩ | 流水读侧 |
| `ChargeItemPreSettledEvent` | IF-IP-STL-05 ⑧ | 预结算快照（可选消费） |
| `InpatientPaymentSettledEvent` | IF-IP-STL-06 ⑫ | `InpatientEncounterContextQueryModel` 已付摘要；日结 |
| `InpatientChargeItemsSettledEvent` | IF-IP-STL-06 ⑬ | ChargeItemQueryService 查询；下游发药等 |
| 发票草稿事件（`DM-ADM-012`） | IF-IP-STL-06 ⑭ | 票据读侧 |
| `InpatientFinancialSettlementCompletedEvent` | IF-IP-STL-06 ⑮（出院全结） | `InpatientEncounterContextQueryModel.status_enum→COMPLETED`；预交金页禁收退 |
| `InpatientSettleCancelledEvent` 等 | **IF-IP-RVS-01** / ATD **`RecallReverseSettlesupport** | 支付/ChargeItem 读侧；**001** 待结算队列（FIN-004） |

QueryService：`EncounterQueryService 全结后状态刷新（`InpatientFinancialSettlementCompletedEvent`）`（`DM-ADM-002 §6.2 EncounterQueryService` §3.7）。

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览（与 §3 对齐）

| IF | App 方法 | Cmd 序号（§3） | 领域调用 | 聚合写入顺序（摘要） |
| --- | --- | --- | --- | --- |
| IF-IP-STL-05 | `preSettleInpatientCharges` | 7 | `abortPreSettlementBeforePayment` | 旧草稿 → `CANCELLED`（0～N 条） |
| IF-IP-STL-05 | 同上 | 8 | `preSettleInpatientChargeItems` | ChargeItem 预结算校验 + 事件 |
| IF-IP-STL-05 | 同上 | 10 | `createPaymentDraftFromPreSettle` | PaymentReconciliation + Details（`DRAFT`） |
| IF-IP-STL-06 | `confirmInpatientSettle` | 12 | `settleInpatientPayment` | PaymentReconciliation → `SUCCESS` |
| IF-IP-STL-06 | 同上 | 13 | `settleInpatientChargeItems` | ChargeItem → `SETTLED` + `enterer_*`（**BR-IP-STL-007a**） |
| IF-IP-STL-06 | 同上 | 14 | `InvoiceAfterPaymentOrchestration#createDraftAndScheduleIssueIfPayable` | Invoice 草稿（条件） |
| IF-IP-STL-06 | 同上 | 15 | `completeInpatientFinancialSettlement` | Encounter **`PENDING→COMPLETED`**（有活动床）或 **`PENDING→DISCHARGED`**（无活动床） |

### 5.2 事务边界与失败回滚

| IF | 事务策略 | 失败回滚 |
| --- | --- | --- |
| IF-IP-STL-05 | **产品期望**：序号 7～10 同一业务事务 | 任一步失败则不得留下「已预结算但无有效草稿」或「新草稿但未预结算」 |
| IF-IP-STL-06 | **产品期望**：序号 12～15 同一业务事务 | 任一步失败则不得对外展示支付 SUCCESS 且收费项已 SETTLED 且发票/就诊态不一致 |
| IF-IP-STL-01～04、07 | 无事务 | 只读 |

**DOC-CONFLICT（成码口径）**

| 项 | SDD/AS 期望 | 成码实现 |
| --- | --- | --- |
| App 层 `@Transactional` | §5.2 单事务 | **禁止**（《代码生成通用规范》）；`InpatientSettleAppService` **不**声明事务注解 |
| 实际事务边界 | — | 由各领域 `*DomainService` 写方法承担；跨聚合顺序按 §3.6 序号执行，失败时以前序领域抛错为准 |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 重复提交正式结算：草稿态 + 支付业务号门禁 | `PaymentReconciliationDomainService#settleInpatientPayment` | §3.7 序号 12 |
| 重复 chargeItem 挂载成功流水拒绝 | `PaymentReconciliationQueryService#findPaySuccessOrRefundingOverlappingChargeItems` | §3.7 序号 8 |
| 再次预结算作废旧 `INPATIENT_CLINIC` 草稿 | `abortPreSettlementBeforePayment` | §3.6 序号 7 |

### 6.2 并发控制

| 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- |
| 同就诊并发预结算：后写者作废旧草稿或领域拒绝 | §3.6 序号 6～7 | **BR-IP-STL-010** |
| 同就诊并发正式结算：草稿单态 + 重叠 chargeItem 校验 | §3.7 序号 3、8 | **BR-IP-STL-007** |

### 6.3 重试与容错

| 承诺 | 说明 | 关联步骤 |
| --- | --- | --- |
| 结算失败不得对外成功 | 返回 EX-IP-STL-109；前端以流水为准 | §3.7 |
| QueryService最终一致 | 队列刷新允许QueryService 刷新延迟 | §3.7 序号 17 |

### 6.4 数据脱敏

| 承诺 | 说明 |
| --- | --- |
| 患者姓名/证件 | 与机构脱敏规则一致（`InpatientInfoQueueDisplayAssembler` 可选扩展） |

### 6.5 审计日志

| 承诺 | 关联步骤 |
| --- | --- |
| `operatorId`/`entererId` 写入预结算与结算命令 | §3.6 序号 8、10；§3.7 序号 12～15 |
| 结算单绑定 `chargeItemIds`、渠道明细可追溯 | §3.7 序号 5、12 |

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 优先级 | 测试层级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| 在院有待结算项出现在「有待结算」队列 | `PENDING_SETTLE` 可见 | P0 | 集成 | 后端 |
| 护士已办出院（`PENDING`）零元全结出现在「有待结算」队列 | 无 `PENDING_SETTLEMENT` 行仍可见 | P0 | 集成 | 后端 |
| 预交金充足正式结算 | `paymentDetails=[]`；C=0；冲抵 O=P | P0 | 集成 | 后端 |
| R1 少补正式结算 | 渠道实付=C；**禁止**另开 HOSPITAL_DEPOSIT 少补收款 | P0 | 集成 | 后端 |
| 默认全选待结算费用 | IF-04 `defaultSelectAll=true` | P0 | 集成 | 后端 |
| 预结算 + 正式结算成功（实付 > 0） | `DRAFT`→`SUCCESS`；发票草稿存在 | P0 | 集成 | 后端 |
| 零元出院全结 | `DRAFT`→`SUCCESS`；**无**发票草稿（**BR-INV-008**） | P0 | 集成 | 后端 |
| 待出院/非 PENDING 结算 | 写侧拒绝 EX-IP-STL-108/111 | P0 | 集成 | 后端 |
| `PENDING` 全结 | 就诊 `COMPLETED`；预交金禁收退 | P0 | 集成 | 后端 |
| `COMPLETED` 不可再结 | IF-05/06 拒绝 | P0 | 集成 | 后端 |
| 出院全结漏选 | EX-IP-STL-107 | P0 | 单元 | 后端 |
| 渠道金额≠应收 | EX-IP-STL-106 | P0 | 单元 | 后端 |
| 已结患者查阅 | IF-07 快照只读 | P1 | 集成 | 后端 |
| 出院全结多退只退盈余 | 预交 1000、本批结 1000 → 扣减后 `balance=0`、无多退；预交 1000、本批 50 → 多退 950 | P0 | 集成 | 后端 |
| 出院多退流水预交金页可见 | IF-IP-PREP-02 可查 `COMPLETED` 患者 `UN_PAY` 流水 | P1 | E2E | 手工 |

### 7.2 非功能测试

| 用例 | 指标 | 优先级 | 测试层级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| 队列查询 | ≤1000ms（AS §7） | P1 | 性能 | 后端 |
| 单就诊费用查询 | ≤1500ms | P1 | 性能 | 后端 |
| 预结算/正式结算 | ≤2000ms（不含三方） | P1 | 性能 | 后端 |

### 7.3 QueryService一致性

| 用例 | 预期 | 优先级 | 测试层级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| 出院全结后 `InpatientEncounterContextQueryModel.status_enum` | `COMPLETED`（领域事件载荷） | P0 | 集成 | 后端+投影 |
| 结算后待结算合计下降 | 队列 `pendingSettlementTotal` 更新 | P1 | 集成 | 后端 |

### 7.4 集成测试

| 用例 | 预期 | 优先级 | 测试层级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| 召回后二次全结无床 | `completeInpatientFinancialSettlement` 直接 `DISCHARGED`；**不**走 ATD-06 | P0 | E2E | 手工 |
| 结算后护士站清床门禁 | 须 `COMPLETED`（**AS-IP-NUR-002**）；**无床全结**跳过 | P1 | E2E | 手工 |
| 未 `COMPLETED` 仍可收预交金 | **AS-IP-FIN-002** 联动 | P1 | 集成 | 后端 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
| --- | --- | --- |
| 扫码/async 回执 | AS §1.4 非目标；无自动化 | P2 |
| 医保分项只读 | IF-05/07 已回填；日结/电票勾稽联调待补 | P1 |
| 打印占位 | 无 IF | — |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.18 | 2026-08-21 | **BR-IP-STL-006a**：IF-06 编排序列 13b～13d（`applyScanBeforeSettle` 作用同一份落库明细 + 禁 `CASHIER`/`SCAN_PAY` + 挂 `trade_no`）；步骤 18 多退一律现金；对齐 AS v1.23 / DD v1.6 |
| v1.17 | 2026-08-17 | **DD 预交金轧差读侧**：IF-03 透出自费 P/O/C 预估（医保挂账户不臆造）；IF-05/06 与 Planner/冲抵明细对齐；IF-07 `SettledPaymentBriefDto` 回填 O/C；废除少补收预交金编排序列 |
| v1.16 | 2026-08-17 | IF-IP-STL-02/03 透出 `attendingDoctorName`；前端结算抬头绑定队列/摘要字段（对齐 **DM-ADM-002 v1.43**） |
| v1.16 | 2026-08-17 | **远端成码钩子保留**：IF-05 `prepayNetBalance` + 2303 分项；IF-06 支付明细写统筹/个账/现金；IF-07 医保分项回填；扫码原路退可第二阶段。预交金轧差以 v1.17 / DD 为准 |
| v1.15 | 2026-08-17 | IF-IP-STL-02 队列主治/位置展示口径对齐 **DM-ADM-002 v1.43** / **AS v1.19**（活动优先，末次 COMPLETED 回落） |
| v1.14 | 2026-08-04 | IF-IP-STL-06：`settleInpatientChargeItems` 写 `enterer_id`/`entered_date`（**BR-IP-STL-007a** / **BR-OPC-016**）；对齐 **AS v1.17** / **DM-ADM-011 v1.6.14** |
| v1.13 | 2026-07-16 | IF-IP-STL-06：**BR-IP-STL-008** 零元不开票；步骤 18 改 `InvoiceAfterPaymentOrchestration`（**BR-INV-008**）；**出院召回 redesign**：`completeInpatientFinancialSettlement` **无活动床→DISCHARGED**；IF-07 指向 FIN-004 **`recallEnabled`**；对齐 **AS v1.16** / **FIN-004 v1.8** |
| v1.12 | 2026-07-11 | **成码回更**：未清床反结就诊 **`COMPLETED→IN_PROGRESS`**；IF-07 只读门禁补充；与 **FIN-004 v1.6** 对齐 |
| v1.11 | 2026-07-11 | **IF-IP-STL-02 已结算队列**：`SETTLED` 改为 **INPATIENT_CLINIC SUCCESS/REFUNDED EXISTS**（与在院/清床/召回无关）；**住院号去重** + **`countInpatientSettleQueueDistinctBusNo`**；`PENDING_SETTLE` 收窄为仅 **`PENDING`**；新增 **BR-IP-STL-017** |
| v1.10 | 2026-06-25 | IF-IP-STL-01：`paymentChannelOptions` 真源明确为 `PaymentChannelOptionProvider`（**ADR-PAY-001**） |
| v1.9 | 2026-06-20 | **support 落点对齐**：`InpatientAccountBalanceDisplayDtoApplier` 落点明确为 `module.common.assembler.inpatient`；对齐 **AS v1.8** |（已迁至 application.shared.inpatient）
| v1.8 | 2026-06-19 | **成码回更**：零元出院全结；结算少补自动收预交金 + `channelPayable` 校验；展示余额三因子（占用+少补扣减）；IF-02 队列 OR（待结算费用∪`PENDING`）；IF-05/06 编排序列重排 |
| v1.7.1 | 2026-06-19 | **账户余额语义**：展示 = 库内 balance − 待结算；结算扣本批已结；多退 = 扣减后盈余；IF-06 编排序列同步 |
| v1.7 | 2026-06-18 | **FIN-004 对齐**：§1.2/§2 补充 `InpatientSettleReadLoader`、`InpatientReverseSettlePresupport；§3.8 指向 FIN-004 §3.2 增量；§4.2 反结事件；关联 **ATD `RecallReverseSettlesupport** |
| v1.6 | 2026-05-30 | IF-IP-STL-06 步骤 12/16：**BR-IP-STL-014** 多退按「预交金净值 −（结算前已结占用 + 本批合计）」；§2.5 补出院多退与展示余额占用口径 |
| v1.5 | 2026-05-28 | **六维对账**：`InpatientSettleAppService` 七方法与 IF-IP-STL-01～07 对齐；写路径无 App `@Transactional`（§8 DOC-CONFLICT）；自动多退少补/医保/取消结算仍为 AS §1.4 非目标 |
| v1.4.1 | 2026-05-28 | IF-IP-STL-02：**`EncounterContextFacade#pageInpatientSettleQueue`**（§1.2/§2.1/§2.4/§3.3 编排序列） |（已迁至 EncounterContextReader / application.shared.encounter）
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-26 | 初版骨架：7 IF 概要；领域 Gap 已关 |
| v1.1 | 2026-05-26 | 按应用服务 SDD 技能模板全量展开：§3.1 中文方法名、7 IF 完整 Request/Response/DTO/编排序列/Mapper；§2.3 双锚；§5 Cmd 对照；§6.1～6.5、§7.1～7.5；白名单与 `findPaymentsByEncounter` 等实现对齐 |
| v1.2 | 2026-05-26 | 文档编号 **AS-IP-FIN-004→003**；文件名 `AS-IP-FIN-003-住院结算-SDD.md`；来源契约对齐 `AS-IP-FIN-003-住院结算.md` |
| v1.3 | 2026-05-26 | 应用层已成码回更：`InpatientSettleConstants`/`InpatientSettleQueueTab` 已落地；§2.3 编排消息码锚点 `EX_IP_STL_*`；IF-06 出院全结待结算全集查询补充 `excludedServiceTable`；§1.1 实现阶段标记已成码 |
| v1.4 | 2026-05-26 | §2.6 IF→领域→RM 追溯表；§2.7 共享常量一览；来源 AS v1.3 |
