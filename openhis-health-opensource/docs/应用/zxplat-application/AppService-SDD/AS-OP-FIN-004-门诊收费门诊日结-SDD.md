# 门诊日结交账 · 应用服务详细设计（SDD）

> **编码**：AS-OP-FIN-004-SDD  
> **版本**：v4.18 | **日期**：2026-10-14
> **编排权威**：`AS-OP-FIN-004-门诊收费门诊日结.md`（**v4.19**）
> **领域权威**：`DM-FIN-003-CashierSettlement-收费员交账.md`、`DM-FIN-002-PaymentReconciliation-支付流水.md`  
> **说明**：本 SDD **不维护 Gap 表**；IF **IF-OP-DCL-01～06** 为目标态全量契约。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊日结（初始化、日结查询、确认交账、历史、挂载现算重打、待审撤回） |
| **边界** | 编排 **CashierSettlement** + **PaymentReconciliation** 读/写；日结汇总在 AppService/Mapper 组装；审批在 **AS-FIN-CS-001**；**不**依赖 `print_snapshot_json` |
| **编排权威** | `AS-OP-FIN-004` §2 / §4 |
| **方法权威** | `DM-FIN-003` §5；`DM-FIN-002` §6.2 |
| **严格对齐** | IF **IF-OP-DCL-01～06** |
| **后端保留、非 IF** | `previewOutpatientDailySettlementMedicationRevenue`（药品收入 TAB；**FE 本期不暴露**） |
| **已删除契约** | `listOutpatientDailySettlementActiveCashiers`、`defaultCashierIds` |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **type 持久化** | `CashierSettlementType.OP_PAYMENT.getCode()` |
| **提交后状态** | `CashierSettlementStatus.SUBMITTED`（**非** `HANDED_OVER`） |
| **operatorId** | `CurrentOperatorResolver#requireOperatorId`（用户主键，审计） |
| **submitterId** | `CurrentPractitionerResolver#requirePractitionerId`（提交写路径） |
| **cashierIds** | 多选 Practitioner 主键；**空 = 不按 enterer 过滤**（`DailySettlementCashierScopeResolver`） |
| **orgId** | 交账科室；**必填**；写入交账头；**门诊收款读侧不下推** org→location 过滤 payment |
| **费用性质** | `expenseProperty` 仅影响展示 rows/summary/handover* |
| **医保现金** | 挂**医保账户**的 `PaymentRecDetail.pay_enum=CASH`（基金不够补差）；**非**挂自费账户的现金 |
| **混结分桶** | 同笔 payment 明细账户同时含医保与非医保合同 → `DailySettlementExpenseNatureDetailAccumulator` 按账户拆；否则整笔 `payment.contract_no`（**BR-OP-DCL-027**） |
| **漏交硬栏** | `findEarliestUnsettledPaymentBillTimeBeforeWindow`；提交抛 EX-OP-DCL-106；FE **不**灰确认入口、**不**挡弹窗 |
| **漏交软提醒** | `countUnsettledPaymentsAfterWindow` → `gapSoftWarnCount` |
| **FE 收款人下拉** | 仅用 IF-OP-DCL-02 的 `practitionerOptions`；pageInit `cashierOptions` 为岗池兼容；多选 **不** `collapse-tags`（逐项展示） |
| **FE 体检人次** | `summary.physicalExamCount` 可有值；日结单 UI **不渲染**该行 |
| **工伤独立统计** | `summary.workInjurySummary`（**BR-OP-DCL-030**）；不入总收入；FE 仅院点启用工伤 UI 且非空时展示 |
| **FE 侧栏医保对账** | `DailySettlementChsReconPanel`（`clrType=11`）；复用 `DailyLedgerList` compact；**非**本 AS IF；月清算等跳转 `/chs/reconciliation` |
| **重打** | `LoadOutpatientCashierSettlementVoucherResponse.mountedVoucher`（现算，不落库） |
| **HTTP** | `POST /api/app/outpatientDailySettlement/{methodName}` |
| **事务** | `OutpatientDailySettlementAppService` **禁止** `@Transactional`；写事务在领域服务 |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/financial/dailysettlement/
  service/
    OutpatientDailySettlementAppService.java
  request/
    LoadOutpatientDailySettlementPageInitRequest.java
    PreviewOutpatientDailySettlementRequest.java
    SubmitOutpatientDailySettlementRequest.java
    PageOutpatientCashierSettlementHistoryRequest.java
    LoadOutpatientCashierSettlementVoucherRequest.java
    WithdrawOutpatientDailySettlementRequest.java
  response/
    LoadOutpatientDailySettlementPageInitResponse.java
    PreviewOutpatientDailySettlementResponse.java
    SubmitOutpatientDailySettlementResponse.java
    PageOutpatientCashierSettlementHistoryResponse.java
    OutpatientCashierSettlementHistoryItemDto.java
    LoadOutpatientCashierSettlementVoucherResponse.java
    WithdrawOutpatientDailySettlementResponse.java
  dto/
    DailySettlementSummaryDto.java
    DailySettlementChannelRowDto.java
    DailySettlementPaymentItemDto.java
    DailySettlementPaymentChargeLineDto.java
    DailySettlementPaymentPayMethodDto.java
  support/
    DailySettlementPaymentDetailLoader.java
  mapper/
    OutpatientDailySettlementMapper.java
    OutpatientDailySettlementMapperImpl.java

zxplat/health-application/src/main/java/com/openhis/health/application/shared/financial/
  DailySettlementCashierScopeResolver.java
  DailySettlementCashierScope.java
  DailySettlementOrgLocationScopeSupport.java
  PaymentEntererMatchResolver.java
  cashiersettlement/CashierSettlementMountedVoucherDto.java
  medicationrevenue/DailySettlementMedicationRevenueAssembler.java   # 非 IF；药品 TAB 后端保留

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  OutpatientDailySettlementConstants.java
```

> 包名：`com.zxplat.domain.application.outpatient.financial.dailysettlement`。  
> `OptionDto` 使用 zxplat-framework 公共库。

### 2.1.1 前端落点（openhis-pro-ui）

```text
apps/web-ele/src/views/outpatient/charge/daily-settlement/index.vue
apps/web-ele/src/components/charge/
  daily-settlement-summary-panel.vue      # 左区：财务分类 + 收入与渠道网格；不展示体检人次
  daily-settlement-chs-recon-panel.vue    # 右栏：CHS 日对账嵌入
apps/web-ele/src/views/chs/reconciliation/...  # DailyLedgerList（compact / initialReconDate / defaultClrType）
```

| 约定 | 说明 |
| --- | --- |
| 布局 | 左主区日结 + 右栏约 `420–480px` 对账；`auto-content-height` 纵向滚动 |
| 对账日 | 默认跟日结窗 `dateFrom` 的日期部分；业务类型门诊 `clrType=11` |
| 侧栏列 | `# / 险种 / 医疗费 / 状态 / 操作`（精简，抑制横向滚动） |
| 完整能力 | 「打开完整对账」→ `/chs/reconciliation`（月清算等） |

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | operatorId / 登录门禁 |
| `CurrentPractitionerResolver` | IF-OP-DCL-03 提交人 `submitterId` |
| `PaymentReconciliationQueryService` | 候选预览、展示汇总、漏交扫描、enterer 列表、挂载 payment 读、子明细 |
| `DailySettlementPaymentDetailLoader` | items `chargeLines` / `payMethods` enrichment（**BR-OP-DCL-015**）；行 `netAmount` 有收费明细时按带符号 charge 行额回写（**BR-OP-DCL-026**） |
| `CashierSettlementQueryService` | 提交后 `busNo`；历史分页；交账头读 |
| `CashierSettlementDomainService` | IF-OP-DCL-03 提交；IF-OP-DCL-06 撤回 |
| `PractitionerQueryService` | 经办人名称、`findAllByIds` |
| `PractitionerRoleScopeLoader` | IF-OP-DCL-01 岗池 `cashierOptions` |
| `ContractQueryService` | IF-OP-DCL-01 费用性质下拉 |
| `ContractExpenseNatureAssembler` | IF-OP-DCL-02 费用性质判定（payment 级 hasInsurance / 筛选） |
| `DailySettlementExpenseNatureDetailAccumulator` | IF-OP-DCL-02 下半区费用性质分桶（**BR-OP-DCL-027**：混结按账户拆；非混结整笔） |
| `DailySettlementExpenseNatureSummarySupport` | 费用性质 / 险种桶 → `expenseNatureSummaries` / `summaryRows` |
| `AccountQueryService` | （经 Accumulator）`account_id → contract_no` |
| `DailySettlementCashierScopeResolver` | `cashierIds` → `entererMatchIds` 软筛 |
| `PaymentEntererMatchResolver` | enterer ↔ Practitioner 归一（仅医务人员主键，**BR-OPC-016**）；窗内收费人 |
| `DailySettlementOrgLocationScopeSupport` | 漏交文案、`sumSelectedPaymentNetIncome`、`joinPractitionerIds` |
| `TerminologyOptionLoader` | `fin_type_code` 字典 |
| `DailySettlementMedicationRevenueAssembler` | **非 IF**：`previewOutpatientDailySettlementMedicationRevenue` |
| `OutpatientDailySettlementMapper` | Request/Command/Response 装配 |

> **不再使用** `PractitionerCashierOptionSupport`（v3.x 口径）；岗池改由 `PractitionerRoleScopeLoader#pageActivePractitionersForPlatformRole(ROLE_CASHIER, …, excludePlatformAdmin=false)`（日结显式保留挂款员岗的 admin；业务选型下拉默认剔 admin 见 **B.6.3.1**）。

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | 说明 |
| --- | --- | --- |
| 交账类型 | `CashierSettlementType.OP_PAYMENT` | IF-OP-DCL-02～06 固定 |
| 交账状态 | `CashierSettlementStatus` | 提交 `SUBMITTED`；撤回 `WITHDRAWN`；历史 `statusEnumText` ← `getDisplay()` |
| 支付 kind | `PaymentKind.OUTPATIENT_CLINIC` | **BR-CS-004** |
| 支付 status | `PaymentStatus.SUCCESS` / `REFUNDED` | **BR-CS-003** |
| 应用语义码 | `OutpatientDailySettlementConstants` | EX-OP-DCL-101～107、201、301、410 |

### 2.4 领域方法与 Mapper 白名单

**领域读**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PaymentReconciliationQueryService` | `previewUnsettledPaymentsForCashierSettlement` | 02、03 |
| `PaymentReconciliationQueryService` | `previewPaymentsForCashierSettlementSummary` | 02 |
| `PaymentReconciliationQueryService` | `findPaymentQueryModelsForCashierSettlementSummary` | 02 |
| `PaymentReconciliationQueryService` | `findEarliestUnsettledPaymentBillTimeBeforeWindow` | 02、03 |
| `PaymentReconciliationQueryService` | `countUnsettledPaymentsAfterWindow` | 02、03 |
| `PaymentReconciliationQueryService` | `listDistinctEntererIdsInCashierSettlementWindow` | 02 |
| `PaymentReconciliationQueryService` | `listPaymentRecDetailsByPaymentId` | 02、05 |
| `PaymentReconciliationQueryService` | `previewPaymentsByCashierSettlementId` | 05 |
| `PaymentReconciliationQueryService` | `findPaymentQueryModelsByCashierSettlementId` | 05 |
| `CashierSettlementQueryService` | `findBusNoBySettlementId` | 03 |
| `CashierSettlementQueryService` | `pageHistory` | 04 |
| `CashierSettlementQueryService` | `findEntityById` | 05、06 |
| `PractitionerQueryService` | `findByUserId` / `findAllByIds` | 01、02 |

**领域写**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `CashierSettlementDomainService` | `submitCashierSettlement` | 03 |
| `CashierSettlementDomainService` | `withdrawCashierSettlement` | 06 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientDailySettlementMapper` | `toPreviewCashierSettlementQuery` | 02、03 |
| `OutpatientDailySettlementMapper` | `toPreviewOutpatientDailySettlementResponse` | 02、05 |
| `OutpatientDailySettlementMapper` | `buildHandoverSummary` | 02（handoverRows/handoverSummary） |
| `OutpatientDailySettlementMapper` | `toSubmitCashierSettlementCommand` | 03 |
| `OutpatientDailySettlementMapper` | `resolveSelectedSettlementTotalIncome` | 03（头表 total_income） |
| `OutpatientDailySettlementMapper` | `toLoadOutpatientDailySettlementPageInitResponse` | 01 |
| `OutpatientDailySettlementMapper` | `toSubmitOutpatientDailySettlementResponse` | 03 |

---

## 3. 按接口 ID 命名展开

**导语**：Gate/Pre 失败禁止进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-OP-DCL-01 | `loadOutpatientDailySettlementPageInit` | 门诊日结页初始化 | `POST .../loadOutpatientDailySettlementPageInit` |
| IF-OP-DCL-02 | `previewOutpatientDailySettlement` | 门诊日结查询 | `POST .../previewOutpatientDailySettlement` |
| IF-OP-DCL-03 | `submitOutpatientDailySettlement` | 确认门诊交账 | `POST .../submitOutpatientDailySettlement` |
| IF-OP-DCL-04 | `pageOutpatientCashierSettlementHistory` | 门诊交账历史分页 | `POST .../pageOutpatientCashierSettlementHistory` |
| IF-OP-DCL-05 | `loadOutpatientCashierSettlementVoucher` | 交账单挂载现算重打 | `POST .../loadOutpatientCashierSettlementVoucher` |
| IF-OP-DCL-06 | `withdrawOutpatientDailySettlement` | 撤回待审交账 | `POST .../withdrawOutpatientDailySettlement` |

> 前缀：`POST /api/app/outpatientDailySettlement/`  
> **非 IF**：`previewOutpatientDailySettlementMedicationRevenue`（后端保留，页面不暴露）。

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录、参数、权限 | EX-OP-DCL-101/102/107 |
| **Pre** | 只读：候选、漏交、enrichment | 业务异常，**禁止 Cmd** |
| **Cmd** | 领域写 | 事务回滚 |
| **Asm** | Mapper 装配 Response | — |

---

### 3.2 IF-OP-DCL-01 `loadOutpatientDailySettlementPageInit`

**Request `LoadOutpatientDailySettlementPageInitRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| — | — | — | 空体或占位 |

**Response `LoadOutpatientDailySettlementPageInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultDateFrom` | `String` | 当日 00:00:00（`yyyy-MM-dd HH:mm:ss`） |
| `defaultDateTo` | `String` | 当前时刻（≈now） |
| `expensePropertyOptions` | `List<OptionDto>` | 有效 `fin_contract` |
| `cashierOptions` | `List<OptionDto>` | **ROLE_CASHIER 岗池**（**BR-OP-DCL-013**；`excludePlatformAdmin=false`）；FE 查询下拉**不消费** |
| `currentCashierId` | `Long` | 当前操作员 Practitioner 主键 |
| `discountFeatureEnabled` | `Boolean` | 是否已配置 ACTIVE 折扣内容（**BR-OP-DCL-031**） |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | EX-OP-DCL-101 |
| 2 | Pre | `CurrentPractitionerResolver` / `PractitionerQueryService#findByUserId` | `currentCashierId` |
| 3 | Pre | `ContractQueryService#listAllContracts` | 费用性质 |
| 4 | Pre | `buildCashierOptions` | ROLE_CASHIER 岗池（`excludePlatformAdmin=false`） |
| 5 | Asm | `toLoadOutpatientDailySettlementPageInitResponse` | 默认窗 from=当日 00:00，to=now |

---

### 3.3 IF-OP-DCL-02 `previewOutpatientDailySettlement`

**Request `PreviewOutpatientDailySettlementRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dateFrom` | `LocalDateTime` | Y | 日结时间起 |
| `dateTo` | `LocalDateTime` | Y | 日结时间止 |
| `orgId` | `Long` | Y | 交账科室（**BR-OP-DCL-019**） |
| `cashierIds` | `List<Long>` | N | 多选收款人软筛；**空=不按人** |
| `expenseProperty` | `String` | N | 展示过滤：selfPay/insurance/bus_no |

**Response `PreviewOutpatientDailySettlementResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentCount` | `Integer` | 未交账候选条数（**BR-OP-DCL-012**） |
| `items` | `List<DailySettlementPaymentItemDto>` | 候选明细（**BR-OP-DCL-015**）；`payMethods[*].payMethodName` = 合同费用性质前缀（`hasInsurance`→自费/医保）+ `·` + 渠道短名 |
| `rows` | `List<DailySettlementChannelRowDto>` | 上半区（含已交账，**BR-OP-DCL-014**） |
| `summary` | `DailySettlementSummaryDto` | 下半区（含 `expenseNatureSummaries` / `summaryRows`，**BR-OP-DCL-027**；可选 `workInjurySummary`，**BR-OP-DCL-030**；`discountAmount` 折扣合计，**BR-OP-DCL-031**；`medicationTotalAmount` 药品合计金额，**BR-OP-DCL-032**） |
| `patientSummaries` | `List<OutpatientDailySettlementPatientSummaryDto>` | 患者分项只读块（同源按就诊拆；含 `chargeDate` 收费日期 yyyy-MM-dd、是否住院；不含预交金/上传差额；**BR-OP-DCL-032**） |
| `handoverRows` | `List<DailySettlementChannelRowDto>` | 本次待交账合计上半区 |
| `handoverSummary` | `DailySettlementSummaryDto` | 本次待交账合计下半区（费用性质分桶同 `summary`；可含工伤独立块） |
| `gapHardBlockEarliestAt` | `LocalDateTime` | 漏交硬栏最早时刻 |
| `gapHardBlockMessage` | `String` | 漏交硬栏文案 |
| `gapSoftWarnCount` | `Long` | 窗后未交账条数 |
| `practitionerIdsInWindow` | `List<Long>` | 窗内收费人 Practitioner 主键 |
| `practitionerOptions` | `List<OptionDto>` | 窗内收费人下拉（**FE 权威**） |

**DTO 子结构**（`DailySettlementPaymentItemDto` / `ChargeLine` / `PayMethod` / `ChannelRow` / `Summary`）同 v3.9；`dateRange` 为 LocalDateTime 窗文案。

**下半区费用性质装配（Mapper，BR-OP-DCL-027）**

| 步骤 | 落点 | 说明 |
| --- | --- | --- |
| 1 | `DailySettlementExpenseNatureDetailAccumulator#prepare` | 批量 `account_id→contract_no`、合同名、医保标志 |
| 2 | `isMixedSelfPayAndInsurancePayment` | 同笔明细是否同时命中医保合同与非医保合同 |
| 3a | **非混结** → `accumulateSingleNaturePayment` | 整笔 `payment.contract_no`；医保 `CASH` 全进该医保现金（原口径） |
| 3b | **混结** → `accumulateMixedPaymentByDetailAccounts` | 挂自费户净额→自费；挂医保户 `CASH`→医保现金；统筹/个账跟医保户 |
| 4 | `DailySettlementExpenseNatureSummarySupport#toExpenseNatureSummaries` / `#buildSummaryRows` | 出参块与扁平行 |
| 5 | `OutpatientDailySettlementMapperImpl#buildWorkInjurySummary` | 工伤独立块（**BR-OP-DCL-030**；与主汇总互斥） |

**工伤独立统计装配（Mapper，BR-OP-DCL-030）**

| 步骤 | 落点 | 说明 |
| --- | --- | --- |
| 1 | `filterWorkInjuryPaymentItems` | 与主汇总 `excludeWorkInjuryPaymentItems` 互为补集 |
| 2 | `DailySettlementFinTypeAmountLoader#aggregateFinTypeAmounts` | 仅工伤 payment 快照；归并西药中成药/诊疗/耗材/中草药/诊察费 |
| 3 | `accumulateWorkInjuryNaturePayment` | 统一桶；个人自付=自付渠道；统筹/个账同桥表门禁 |
| 4 | `toWorkInjurySummaryDto` | 写入 `summary.workInjurySummary`；空则 null |

**领域编排序列（摘要）**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 登录、日期窗、`orgId` | EX-OP-DCL-102/103 |
| 2 | Pre | `buildPreviewQuery`（`OP_PAYMENT`，`locationIds=null`） | `DailySettlementCashierScopeResolver` 软筛 |
| 3 | Pre | 漏交硬/软扫描 | `DailySettlementOrgLocationScopeSupport#buildGapHardBlockMessage` |
| 4 | Pre | `previewUnsettledPaymentsForCashierSettlement` | 未交账 items |
| 5 | Pre | `previewPaymentsForCashierSettlementSummary` + enrichment | rows/summary/handover*（含 **BR-OP-DCL-027** 分桶） |
| 6 | Pre | `listDistinctEntererIdsInCashierSettlementWindow` + `PaymentEntererMatchResolver` | practitioner* |
| 7 | Asm | `toPreviewOutpatientDailySettlementResponse` | expenseProperty 仅过滤展示 |

**失败码**：EX-OP-DCL-102、103、104

---

### 3.4 IF-OP-DCL-03 `submitOutpatientDailySettlement`

**Request `SubmitOutpatientDailySettlementRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dateFrom` | `LocalDateTime` | Y | 与预览相同 |
| `dateTo` | `LocalDateTime` | Y | 与预览相同 |
| `cashierIds` | `List<Long>` | N | 软筛；空=不按人 |
| `orgId` | `Long` | Y | 交账科室 |
| `locationId` | `Long` | N | 收费窗口（可选） |
| `paymentIds` | `List<Long>` | Y | 勾选挂载（**BR-OP-DCL-018**） |

**Response `SubmitOutpatientDailySettlementResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `settlementId` | `Long` | 交账头 ID |
| `busNo` | `String` | 交账单号 |
| `paymentCount` | `Integer` | 本次挂载条数（= `paymentIds.size()`） |
| `statusEnum` | `String` | 提交后一般为 `SUBMITTED` |
| `gapSoftWarnCount` | `Long` | 窗后未交账提醒 |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toSubmitCashierSettlementCommand` | `toSubmitCashierSettlementCommand(..., List<Long> selectedPaymentIds, ..., BigDecimal totalIncome): SubmitCashierSettlementCommand` |
| `resolveSelectedSettlementTotalIncome` | `resolveSelectedSettlementTotalIncome(List<UnsettledPaymentPreviewItemQueryModel> lockedItems, List<Long> selectedPaymentIds): BigDecimal` |
| `toSubmitOutpatientDailySettlementResponse` | `toSubmitOutpatientDailySettlementResponse(Long settlementId, String busNo, Integer paymentCount): SubmitOutpatientDailySettlementResponse` |

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | operatorId、`submitterId`、`orgId`、日期窗 | EX-OP-DCL-102 |
| 2 | Gate | 漏交硬栏 | 有 → **EX-OP-DCL-106** |
| 3 | Pre | 重锁候选 + `assertPaymentIdsSubsetOfCandidates` | 105/201 |
| 4 | Pre | `resolveSelectedSettlementTotalIncome` | 头表 `total_income` = 勾选集 `handoverSummary.totalIncome` |
| 5 | Pre | `toSubmitCashierSettlementCommand`（`OP_PAYMENT`，`practitionerIds` 快照，`totalIncome`） | `locationIds=null`；**不写** `print_snapshot_json` |
| 6 | Cmd | `submitCashierSettlement` | 写头 + 挂载；`SUBMITTED` |
| 7 | Pre | `findBusNoBySettlementId` | |
| 8 | Asm | `toSubmitOutpatientDailySettlementResponse` + 设 `statusEnum`、`gapSoftWarnCount` | |

**失败码**：102、105、106、201、301

---

### 3.5 IF-OP-DCL-04 `pageOutpatientCashierSettlementHistory`

**Request `PageOutpatientCashierSettlementHistoryRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `submittedFrom` | `LocalDateTime` | Y | 交账提交时刻起 |
| `submittedTo` | `LocalDateTime` | Y | 交账提交时刻止 |
| `pageIndex` | `Integer` | N | 默认 0 |
| `pageSize` | `Integer` | N | 默认 20，最大 200 |

**Response `PageOutpatientCashierSettlementHistoryResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `items` | `List<OutpatientCashierSettlementHistoryItemDto>` | 历史行 |
| `total` | `Long` | 总条数 |
| `pageIndex` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 页大小 |

**`OutpatientCashierSettlementHistoryItemDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `settlementId` | `Long` | 交账头主键 |
| `busNo` | `String` | 单号 |
| `submittedAt` | `LocalDateTime` | 提交时刻 |
| `dateFrom` / `dateTo` | `LocalDateTime` | 业务窗 |
| `practitionerIds` | `String` | 本批人员快照（逗号分隔） |
| `submitterId` | `Long` | 提交人 |
| `paymentCount` | `Integer` | 挂载笔数 |
| `totalIncome` | `BigDecimal` | 列表金额快照 |
| `canReprintFromMounts` | `Boolean` | 是否可挂载现算重打 |
| `statusEnum` | `String` | 状态 code |
| `statusEnumText` | `String` | `CashierSettlementStatus.getDisplay()` |

**编排序列**：Gate 登录+时刻窗 → `pageHistory(OP_PAYMENT, ...)` → 逐行 `resolveCashierSettlementStatusDisplay` → Asm

---

### 3.6 IF-OP-DCL-05 `loadOutpatientCashierSettlementVoucher`

**Request `LoadOutpatientCashierSettlementVoucherRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `settlementId` | `Long` | Y | 交账头主键 |

**Response `LoadOutpatientCashierSettlementVoucherResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `settlementId` | `Long` | 交账头 |
| `busNo` | `String` | 单号 |
| `mountedVoucher` | `CashierSettlementMountedVoucherDto` | 挂载现算打印体 |

**`CashierSettlementMountedVoucherDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `schemaVersion` | `Integer` | 结构版本（默认 1） |
| `cashierName` | `String` | 收款人展示名 |
| `settlementPeriodText` | `String` | 业务窗文案 |
| `rows` | `Object` | 上半区 fin_type 行（运行时 ChannelRow DTO 列表） |
| `summary` | `Object` | 下半区汇总 DTO（含 `medicationTotalAmount`，**BR-OP-DCL-032**） |
| `patientSummaries` | `Object` | 患者分项块列表（运行时 `OutpatientDailySettlementPatientSummaryDto` 列表；挂载现算同出；**BR-OP-DCL-032**） |
| `paymentIds` | `List<String>` | 挂载 payment 主键（string 保精度） |

**编排序列**：Gate → `findEntityById`（type=OP_PAYMENT）→ `rebuildOutpatientMountedVoucherFromMounts`（同预览 Mapper 管线；含就诊/在院预载与 `patientSummaries`）→ Asm

---

### 3.7 IF-OP-DCL-06 `withdrawOutpatientDailySettlement`

**Request `WithdrawOutpatientDailySettlementRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `settlementId` | `Long` | Y | 交账头主键 |

**Response `WithdrawOutpatientDailySettlementResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `settlementId` | `Long` | 交账头 |
| `busNo` | `String` | 单号 |

**编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 登录、`settlementId` | EX-OP-DCL-102 |
| 2 | Gate | `findEntityById`（OP_PAYMENT） | 不存在 → 103 |
| 3 | Gate | `assertWithdrawPermission` | 非 SUBMITTED/非款员 → **107** |
| 4 | Cmd | `withdrawCashierSettlement` | 清挂载 → **WITHDRAWN** |
| 5 | Asm | `WithdrawOutpatientDailySettlementResponse` | |

---

## 4. QueryService 与领域事件

| 事件 | 触发 IF | 读侧刷新 |
| --- | --- | --- |
| `CashierSettlementSubmittedEvent` | IF-OP-DCL-03 | 同窗再查候选减少；历史可见 SUBMITTED |
| `CashierSettlementWithdrawnEvent` | IF-OP-DCL-06 | payment `cashier_settlement_id` 清空；可再交账 |

- **候选口径**：`previewUnsettledPaymentsForCashierSettlement`（**BR-CS-001～006**，`OP_PAYMENT`）。
- **展示汇总**：`previewPaymentsForCashierSettlementSummary`（**BR-CS-012**）。
- **漏交**：`findEarliestUnsettledPaymentBillTimeBeforeWindow` / `countUnsettledPaymentsAfterWindow`。
- **enterer 归一**：`PaymentEntererMatchResolver`（shared financial；仅 `adm_practitioner.id`，**BR-OPC-016**）。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 与 §3 序号对照

| IF | App 方法 | 领域调用 | 聚合写入顺序 |
| --- | --- | --- | --- |
| IF-OP-DCL-03 | `submitOutpatientDailySettlement` | `submitCashierSettlement` | ① INSERT `fin_cashier_settlement`（`SUBMITTED`，含 `total_income`）→ ② UPDATE 勾选 payment |
| IF-OP-DCL-06 | `withdrawOutpatientDailySettlement` | `withdrawCashierSettlement` | ① CLEAR payment 挂载 → ② UPDATE 头 `WITHDRAWN` |

### 5.2 事务边界

| 项 | 约定 |
| --- | --- |
| **事务** | 单事务包裹各 Cmd 全部写操作 |
| **回滚** | 并发挂载冲突 → EX-OP-DCL-301 |
| **事件** | `CashierSettlementSubmittedEvent` 在 save 之后 beforeCommit |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 关联 IF |
| --- | --- | --- |
| 重复提交 | 已挂载 payment 不进候选；并发第二次 → 301 | 03 |
| 重复撤回 | 非 SUBMITTED 拒绝 | 06 |

### 6.2 并发控制

| 项 | 承诺 | 关联 IF |
| --- | --- | --- |
| 行级校验 | update 前 `cashier_settlement_id IS NULL` | 03 |

### 6.3 重试与容错

| 项 | 承诺 |
| --- | --- |
| EX-OP-DCL-301 | 刷新后重试 |
| EX-OP-DCL-106 | 调整 dateFrom 后重试 |

### 6.4 数据脱敏

本模块无 PII 扩展输出。

### 6.5 审计日志

交账头 `submitterId`、`created_by=operatorId`、提交时刻；事件载荷含 paymentCount。

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 优先级 |
| --- | --- |
| 查询 → 弹窗全选 → 提交 SUBMITTED → 历史 statusEnumText 正确 | P0 |
| `cashierIds` 空/非空软筛 | P0 |
| 漏交硬栏 → 106；软提醒不拦提交 | P0 |
| 挂载现算重打 mountedVoucher 与预览同管线 | P0 |
| 款员撤回 SUBMITTED；非款员 → 107 | P0 |
| 分批交账、105/201、301 | P0 |
| expenseProperty 仅改展示 | P0 |

### 7.2 非功能测试

双窗口并发交账同一 payment（P0）。

### 7.3 查询结果一致性

| 用例 | 优先级 |
| --- | --- |
| paymentCount = items.size | P0 |
| 全选 handoverSummary = 弹窗合计 | P0 |
| practitionerOptions ⊆ 窗内 enterer | P1 |

### 7.4 集成测试

混合医保 payment hasInsurance；审批流 **AS-FIN-CS-001** 与 SUBMITTED 衔接（P1）。

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 药品 TAB | 后端方法保留；FE 不暴露，不测本页 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v4.18 | 2026-10-14 | 岗池 **ROLE_CASHIER** + 日结 `excludePlatformAdmin=false`；对齐 AS v4.19 / **B.6.3.1**；代码先行回写 |
| v4.17 | 2026-10-13 | **BR-OP-DCL-032**：FE 患者分项 Excel 导出（一行一患者）；对齐 AS v4.17；代码先行回写 |
| v4.16 | 2026-10-13 | **BR-OP-DCL-032**：`patientSummaries.chargeDate`（收费日期 yyyy-MM-dd）；对齐 AS v4.16；代码先行回写 |
| v4.15 | 2026-10-13 | **BR-OP-DCL-032**：`patientSummaries` + `medicationTotalAmount`；IF-02/05 挂载现算同出；对齐 AS v4.15；代码先行回写 |
| v4.14 | 2026-10-06 | `discountFeatureEnabled` 明确为「已配置 ACTIVE 折扣内容」；对齐 AS v4.14 |
| v4.13 | 2026-10-06 | 上半区/总收入折后；`receivableAmount`=总收入+折扣；`applyDiscountedFinTypeAmounts`；对齐 AS v4.13 |
| v4.12 | 2026-10-06 | **BR-OP-DCL-031**：`summary.discountAmount`（DISCOUNT 旁证优先，否则收费行优惠差额）+ IF-01 `discountFeatureEnabled`；对齐 AS v4.12 |
| v4.5 | 2026-08-02 | **BR-OP-DCL-027**：费用性质混结按账户分桶；DI/`buildSummary` 步骤对齐 `DailySettlementExpenseNatureDetailAccumulator`；对齐 AS v4.5 |
| v4.4 | 2026-07-30 | **FE**：侧栏嵌 CHS 日对账；不展示体检人次；收款人多选不折叠；对齐 AS v4.4 |
| v4.3 | 2026-07-30 | **BR-OP-DCL-026**：交账弹窗行净额按带符号 charge 行额回写；对齐 AS v4.3 |
| v4.2 | 2026-07-29 | **BR-OP-DCL-015**：交账弹窗支付方式前缀按合同 `hasInsurance`；对齐 AS v4.2 |
| v4.1 | 2026-07-28 | IF-OP-DCL-03：`resolveSelectedSettlementTotalIncome`（同 `handoverSummary.totalIncome`）；对齐 AS v4.1；消解合并冲突 |
| v4.0 | 2026-07-27 | **OP_PAYMENT** + **SUBMITTED**；IF-OP-DCL-04～06；`orgId`/LocalDateTime/`cashierIds` 软筛；漏交 106/107；`practitionerOptions`；`mountedVoucher` 重打；岗池 `ROLE_ACCOUNTANT` |
| v3.9 | 2026-07-11 | 交账明细弹窗、handover*、`paymentIds` |
| v3.8 | 2026-07-09 | rows/summary 含已交账 |
| v3.0 | 2026-06-21 | health 日结查询+交账 |
| v1.0 | 2026-06-19 | 首版（已废止） |
