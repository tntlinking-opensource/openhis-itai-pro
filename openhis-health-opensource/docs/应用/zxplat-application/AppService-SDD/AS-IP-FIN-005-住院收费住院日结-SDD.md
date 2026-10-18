# 住院日结交账 · 应用服务详细设计（SDD）

> **编码**：AS-IP-FIN-005-SDD  
> **版本**：v4.15 | **日期**：2026-10-14  
> **编排权威**：`AS-IP-FIN-005-住院收费住院日结.md`（**v4.19**）  
> **领域权威**：`DM-FIN-003`（**v3.0**）、`DM-FIN-002`（**v1.4.4**）、`DM-ADM-002`（Encounter 读）  
> **共用**：交账领域层、漏交栏、`mountedVoucher`、历史/撤回、**费用性质混结分桶（BR-IP-DCL-027）** 与 **AS-OP-FIN-004-SDD v4.5** 同构；本文仅展开住院差异（`IP_PAYMENT`、kind、病区、预交金、多退少补拆列、**患者分项 BR-IP-DCL-030**）  
> **说明**：本 SDD **不维护 Gap 表**。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院日结（IF-IP-DCL-01～06） |
| **与门诊差异** | `typeEnum=IP_PAYMENT`；kind=`INPATIENT_CLINIC`+`HOSPITAL_DEPOSIT`（含历史 `PRE_DEPOSIT`）；rows 为 **仅 INPATIENT_CLINIC** 的 fin_type 上半区（**排除**废止挂号费 `1011` 与体检，**保留诊察费 `1012`**）；预交金在 **summary.depositAmount** 独立列示；多退少补拆列 **`depositRefundAmount`/`settleSupplementAmount`**（**BR-IP-DCL-029**）；`totalIncome` 不含预交金及该两列；items 含 `wardName`/预交金标签；preview/submit **不下推** `locationIds`；**`patientSummaries` 患者分项只读块**（**BR-IP-DCL-030**；FE 双 Tab/分别打印 + Excel 一行一患者导出） |
| **与门诊相同** | `orgId` 必填；`cashierIds` 多选软筛；IF-02 返回 **`practitionerOptions`**（前端合并收款人）；漏交硬 **EX-IP-DCL-106**（FE 不灰确认入口）；撤回 **EX-IP-DCL-107**；历史 **`statusEnumText`**；IF-05 **`mountedVoucher`**；提交态 **`SUBMITTED`**；审批在 **AS-FIN-CS-001**；FE 侧栏嵌 CHS 日对账、不展示体检人次、收款人多选不折叠；**日结展示折后** + **折扣合计**（**BR-IP-DCL-031**，对称 **BR-OP-DCL-031**） |
| **边界** | 编排 **CashierSettlement** + **PaymentReconciliation** + **Encounter** 读；**不**含护士站日费用清单 |
| **编排权威** | `AS-IP-FIN-005` §2 / §4 |
| **方法权威** | `DM-FIN-003` §5；`DM-FIN-002` §6.2 |
| **严格对齐** | IF **IF-IP-DCL-01～06** |
| **实现阶段口径** | 后端与前端 **已成码**；药品收入 TAB 后端保留、前端移除（非目标）；侧栏对账复用 CHS（**非**本 AS IF） |
| **HTTP 前缀** | `POST /api/app/inpatientDailySettlement/{methodName}` |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **type** | `CashierSettlementType.IP_PAYMENT.getCode()` |
| **status** | 提交 `CashierSettlementStatus.SUBMITTED`；撤回 `WITHDRAWN` |
| **wardName** | `InpatientEncounterContextQueryModel.wardName`；`HOSPITAL_DEPOSIT` → 「预交金」 |
| **operatorId** | `CurrentOperatorResolver#requireOperatorId`（用户主键，审计） |
| **submitterId** | `CurrentPractitionerResolver#requirePractitionerId`（提交 IF-03 Gate） |
| **cashierIds** | 多选 Practitioner 主键；经 `DailySettlementCashierScopeResolver` → `entererMatchIds`；空=不过滤 |
| **practitionerOptions** | IF-02 由窗内 enterer 解析；**不含** init 的 `listActiveCashiers`/`defaultCashierIds`；FE 多选 **不** collapse-tags |
| **FE 侧栏医保对账** | `DailySettlementChsReconPanel`（`clrType=21`）；复用 `DailyLedgerList` compact；月清算等跳转 `/chs/reconciliation` |
| **FE 体检人次** | `summary.physicalExamCount` 可有值；日结单 UI **不渲染** |
| **混结分桶 / 医保现金** | 同 **AS-OP-FIN-004-SDD v4.5** §1.2 / **BR-IP-DCL-027**（共享 `DailySettlementExpenseNatureDetailAccumulator`） |
| **其余** | 同 **AS-OP-FIN-004-SDD v4.5** §1.2（费用性质筛选、交账明细弹窗、`paymentIds`、handover*、事务、publicRequestClient） |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/financial/dailysettlement/
  service/
    InpatientDailySettlementAppService.java
  request/
    LoadInpatientDailySettlementPageInitRequest.java
    PreviewInpatientDailySettlementRequest.java
    SubmitInpatientDailySettlementRequest.java
    PageInpatientCashierSettlementHistoryRequest.java
    LoadInpatientCashierSettlementVoucherRequest.java
    WithdrawInpatientDailySettlementRequest.java
  response/
    LoadInpatientDailySettlementPageInitResponse.java
    PreviewInpatientDailySettlementResponse.java
    SubmitInpatientDailySettlementResponse.java
    PageInpatientCashierSettlementHistoryResponse.java
    InpatientCashierSettlementHistoryItemDto.java
    LoadInpatientCashierSettlementVoucherResponse.java
    WithdrawInpatientDailySettlementResponse.java
  dto/
    InpatientDailySettlementChannelRowDto.java
    InpatientDailySettlementPaymentItemDto.java
    InpatientDailySettlementSummaryDto.java
    InpatientDailySettlementPatientSummaryDto.java
  mapper/
    InpatientDailySettlementMapper.java
    InpatientDailySettlementMapperImpl.java

zxplat/health-application/src/main/java/com/openhis/health/application/shared/financial/
  DailySettlementCashierScopeResolver.java
  DailySettlementOrgLocationScopeSupport.java
  PaymentEntererMatchResolver.java
  cashiersettlement/CashierSettlementMountedVoucherDto.java
  medicationrevenue/DailySettlementMedicationRevenueAssembler.java  ← 非目标 TAB

zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/financial/dailysettlement/support/
  DailySettlementPaymentDetailLoader.java   ← 共用 enrichment

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  InpatientDailySettlementConstants.java
```

> 包名：`com.zxplat.domain.application.inpatient.financial.dailysettlement`。

### 2.1.1 前端落点（openhis-pro-ui）

```text
apps/web-ele/src/views/inpatient/charge/daily-settlement/index.vue
apps/web-ele/src/components/charge/
  daily-settlement-summary-panel.vue      # 共用；不展示体检人次
  daily-settlement-chs-recon-panel.vue    # 右栏：CHS 日对账（住院 clrType=21）
```

| 约定 | 说明 |
| --- | --- |
| 布局 | 与门诊对称：左主区 + 右栏对账；`auto-content-height` |
| 对账日 | 默认跟日结窗起点；业务类型住院 `clrType=21` |
| 完整能力 | 「打开完整对账」→ `/chs/reconciliation` |

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | operatorId / 登录门禁 |
| `CurrentPractitionerResolver` | IF-IP-DCL-03 submitterId；IF-06 撤回权限 |
| `PaymentReconciliationQueryService` | IF-02/03 候选与展示汇总；IF-05 挂载 payment |
| `ChargeItemQueryService` | （经 `DailySettlementPaymentDetailLoader`）items chargeLines |
| `CashierSettlementQueryService` | IF-03 busNo；IF-04 历史；IF-05/06 交账头读 |
| `CashierSettlementDomainService` | IF-03 提交；IF-06 撤回 |
| `EncounterContextReader` | IF-02/05 病区名 |
| `PractitionerQueryService` | IF-01/02 practitionerOptions |
| `PractitionerRoleScopeLoader` | IF-01 会计岗池；IF-06 会计权限 |
| `ContractQueryService` | IF-01 费用性质下拉 |
| `DailySettlementCashierScopeResolver` | IF-02/03 cashierIds 软筛 |
| `PaymentEntererMatchResolver` | IF-02/03 enterer 归一（仅 Practitioner 主键，**BR-OPC-016**） |
| `DailySettlementOrgLocationScopeSupport` | 漏交文案；practitionerIds 快照；totalIncome |
| `DailySettlementPaymentDetailLoader` | items chargeLines/payMethods；行 netAmount 带符号 charge 回写（**BR-IP-DCL-026**） |
| `ContractExpenseNatureAssembler` | payment 级费用性质判定 / 筛选 |
| `DailySettlementExpenseNatureDetailAccumulator` | 下半区费用性质分桶（**BR-IP-DCL-027**；预交金不参与） |
| `DailySettlementExpenseNatureSummarySupport` | 费用性质 / 险种桶 → summaryRows |
| `TerminologyOptionLoader` | fin_type_code 字典 |
| `DailySettlementMedicationRevenueAssembler` | **非目标** 药品收入 TAB |
| `InpatientDailySettlementMapper` | Request/Command/Response 装配 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 交账类型 | `CashierSettlementType.IP_PAYMENT` | `DM-FIN-003` §2.3 | IF-02～06 |
| 交账状态 | `CashierSettlementStatus.SUBMITTED` / `WITHDRAWN` | `DM-FIN-003` §2.4 | IF-03/04/06 |
| 支付 kind | `INPATIENT_CLINIC` / `HOSPITAL_DEPOSIT` / `PRE_DEPOSIT` | `DM-FIN-002` §4.2 | 住院候选 kind |
| fin_type 排除 | 废止挂号费 `1011` + 体检；**保留**诊察费 `1012` | **BR-IP-DCL-002** | `DailySettlementSharedConstants` |
| 费用性质 | `InpatientDailySettlementConstants.EXPENSE_*` | **BR-IP-DCL-005** | 同门诊 |
| 应用语义码 | `InpatientDailySettlementConstants` | AS §5 | EX-IP-DCL 前缀 |

### 2.4 领域方法与 Mapper 白名单

**领域读**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PaymentReconciliationQueryService` | `previewUnsettledPaymentsForCashierSettlement` | IF-02、03 |
| `PaymentReconciliationQueryService` | `previewPaymentsForCashierSettlementSummary` | IF-02（rows/summary） |
| `PaymentReconciliationQueryService` | `findPaymentQueryModelsForCashierSettlementSummary` | IF-02 |
| `PaymentReconciliationQueryService` | `findEarliestUnsettledPaymentBillTimeBeforeWindow` | IF-02、03 漏交硬栏 |
| `PaymentReconciliationQueryService` | `countUnsettledPaymentsAfterWindow` | IF-02、03 软提醒 |
| `PaymentReconciliationQueryService` | `listDistinctEntererIdsInCashierSettlementWindow` | IF-02 practitionerOptions |
| `PaymentReconciliationQueryService` | `previewPaymentsByCashierSettlementId` | IF-05 |
| `PaymentReconciliationQueryService` | `findPaymentQueryModelsByCashierSettlementId` | IF-05 |
| `PaymentReconciliationQueryService` | `listPaymentRecDetailsByPaymentId` | IF-02、05 |
| `CashierSettlementQueryService` | `findBusNoBySettlementId` | IF-03 |
| `CashierSettlementQueryService` | `pageHistory` | IF-04 |
| `CashierSettlementQueryService` | `findEntityById` | IF-05、06 |
| `EncounterContextReader` | `listInpatientContextsByEncounterIdsOrdered` | IF-02、05 wardName |

**领域写**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `CashierSettlementDomainService` | `submitCashierSettlement` | IF-03 |
| `CashierSettlementDomainService` | `withdrawCashierSettlement` | IF-06 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `InpatientDailySettlementMapper` | `toPreviewCashierSettlementQuery` | IF-02、03 |
| `InpatientDailySettlementMapper` | `toPreviewInpatientDailySettlementResponse` | IF-02、05（挂载现算） |
| `InpatientDailySettlementMapper` | `buildHandoverSummary` | IF-02（handoverRows/handoverSummary） |
| `InpatientDailySettlementMapper` | `toSubmitCashierSettlementCommand` | IF-03 |
| `InpatientDailySettlementMapper` | `resolveSelectedSettlementTotalIncome` | IF-03（头表 total_income） |
| `InpatientDailySettlementMapper` | `toLoadInpatientDailySettlementPageInitResponse` | IF-01 |
| `InpatientDailySettlementMapper` | `toSubmitInpatientDailySettlementResponse` | IF-03 |

---

## 3. 按接口 ID 命名展开

**导语**：Gate/Pre 失败禁止 Cmd；IF-04/05 只读；IF-06 写路径单事务。与 **AS-OP-FIN-004-SDD** 对称处标注「同门诊 §x.x」。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-DCL-01 | `loadInpatientDailySettlementPageInit` | 住院日结页初始化 | `POST .../loadInpatientDailySettlementPageInit` |
| IF-IP-DCL-02 | `previewInpatientDailySettlement` | 住院日结查询 | `POST .../previewInpatientDailySettlement` |
| IF-IP-DCL-03 | `submitInpatientDailySettlement` | 确认住院交账 | `POST .../submitInpatientDailySettlement` |
| IF-IP-DCL-04 | `pageInpatientCashierSettlementHistory` | 住院交账历史分页 | `POST .../pageInpatientCashierSettlementHistory` |
| IF-IP-DCL-05 | `loadInpatientCashierSettlementVoucher` | 住院交账原单重打 | `POST .../loadInpatientCashierSettlementVoucher` |
| IF-IP-DCL-06 | `withdrawInpatientDailySettlement` | 撤回住院待审交账 | `POST .../withdrawInpatientDailySettlement` |

> **非 IF**（非目标 FE TAB）：`previewInpatientDailySettlementMedicationRevenue` — 与日结查询同筛选项，**不在** IF 白名单。

### 3.1.1 编排约定

同 **AS-OP-FIN-004-SDD** §3.1.1（Gate / Pre / Cmd / Asm）。

---

### 3.2 IF-IP-DCL-01 `loadInpatientDailySettlementPageInit`

**Request / Response**：`LoadInpatientDailySettlementPageInitRequest`（空体）；`LoadInpatientDailySettlementPageInitResponse`

| Response 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultDateFrom` / `defaultDateTo` | `String` | 当日 `yyyy-MM-dd` |
| `expensePropertyOptions` | `List<OptionDto>` | `fin_contract` 有效合同 |
| `cashierOptions` | `List<OptionDto>` | **ROLE_CASHIER** 岗池（**BR-IP-DCL-012**；`excludePlatformAdmin=false`） |
| `currentCashierId` | `Long` | 当前 Practitioner |

**编排序列**：Gate 登录 → Pre 会计岗池 + 合同 → Asm Mapper。**不含** `listActiveCashiers`/`defaultCashierIds`。

**失败码**：EX-IP-DCL-101

---

### 3.3 IF-IP-DCL-02 `previewInpatientDailySettlement`

**Request `PreviewInpatientDailySettlementRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dateFrom` | `LocalDateTime` | Y | 日结时间起 |
| `dateTo` | `LocalDateTime` | Y | 日结时间止 |
| `orgId` | `Long` | Y | 交账科室 |
| `cashierIds` | `List<Long>` | N | 多选收款人软筛 |
| `expenseProperty` | `String` | N | 自费/医保/`fin_contract.bus_no`；仅展示 |

**Response `PreviewInpatientDailySettlementResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `paymentCount` | `Integer` | 未交账候选条数 |
| `items` | `List<InpatientDailySettlementPaymentItemDto>` | 含 chargeLines、payMethods、**wardName** |
| `rows` / `summary` | — | 上半区 fin_type + 下半区固定项（含 **`depositAmount`**） |
| `patientSummaries` | — | 患者分项块列表（**BR-IP-DCL-030**；与 summary 同源按就诊拆；不含上传差额；summary 含 `medicationTotalAmount` 药品合计金额；不影响交账） |
| `handoverRows` / `handoverSummary` | — | 本次待交账合计（**BR-IP-DCL-017**） |
| `gapHardBlockEarliestAt` | `LocalDateTime` | 漏交硬栏时刻 |
| `gapHardBlockMessage` | `String` | 漏交硬栏文案 |
| `gapSoftWarnCount` | `Long` | 截止后未交账条数 |
| `practitionerIdsInWindow` | `List<Long>` | 窗内收费人 Practitioner 主键 |
| `practitionerOptions` | `List<OptionDto>` | 窗内收费人下拉（**FE 合并进多选**） |

**DTO `InpatientDailySettlementPaymentItemDto`**：同 v4.0 + `wardName`/`categoryLabel`/`kindEnum`（`INPATIENT_CLINIC`/`HOSPITAL_DEPOSIT`）。

**DTO `InpatientDailySettlementSummaryDto`**：下半区固定项 + **`depositAmount`**（独立，不参与 `totalIncome`）+ **`depositRefundAmount`/`settleSupplementAmount`**（**BR-IP-DCL-029**，多退少补拆列）。

**DTO `InpatientDailySettlementPatientSummaryDto`**：患者身份（姓名/住院号/病区床）+ 嵌套 `summary`（与合计同源；患者分项剔除上传差额，**BR-IP-DCL-030**）。

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 登录、日期窗、`orgId` | EX-IP-DCL-102/103 |
| 2 | Pre | `toPreviewCashierSettlementQuery(IP_PAYMENT, locationIds=null)` | 住院不下推 location |
| 3 | Pre | `DailySettlementCashierScopeResolver` → `entererMatchIds` | cashierIds 软筛 |
| 4 | Pre | 漏交扫描 | gapHard* / gapSoftWarnCount |
| 5 | Pre | `listDistinctEntererIds...` + `PaymentEntererMatchResolver` | practitionerOptions |
| 6 | Pre | `previewUnsettled...` + `previewPaymentsForCashierSettlementSummary` | 未交账 + 展示汇总 |
| 7 | Pre | 子明细、Encounter、fin_type（**仅 INPATIENT_CLINIC**；剔除 1011/体检、保留 1012）、预交金 deposit + 多退少补拆列 | **BR-IP-DCL-002/007/029** |
| 8 | Pre | `DailySettlementPaymentDetailLoader` | items enrichment |
| 9 | Asm | `toPreviewInpatientDailySettlementResponse` | handover*；**`patientSummaries`**（与合计共用收窄行/fin_type 快照；账户·险种全窗一次预载后按就诊拆块，**BR-IP-DCL-030**） |

**失败码**：EX-IP-DCL-102、103

---

### 3.4 IF-IP-DCL-03 `submitInpatientDailySettlement`

**Request `SubmitInpatientDailySettlementRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dateFrom` / `dateTo` | `LocalDateTime` | Y | 与预览相同窗 |
| `orgId` | `Long` | Y | 交账科室 |
| `cashierIds` | `List<Long>` | N | 软筛（与预览一致） |
| `locationId` | `Long` | N | 收费窗口（可选快照） |
| `paymentIds` | `List<Long>` | Y | 勾选挂载（**BR-IP-DCL-018**） |

**Response `SubmitInpatientDailySettlementResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `settlementId` | `Long` | 交账头 ID |
| `busNo` | `String` | 交账单号 |
| `paymentCount` | `Integer` | 挂载条数 |
| `statusEnum` | `String` | 提交后 **`SUBMITTED`** |
| `gapSoftWarnCount` | `Long` | 软提醒 |

**领域编排序列**：同 **AS-OP-FIN-004-SDD** §3.4 IF-OP-DCL-03（含漏交 **EX-IP-DCL-106**、`resolveSelectedSettlementTotalIncome`、`gapSoftWarnCount`）；差异：`IP_PAYMENT`、`locationIds=null`、预交金可与结算同批（**BR-IP-DCL-004**）；**不写** `print_snapshot_json`。

**失败码**：EX-IP-DCL-102、105、106、201、301、410

---

### 3.5 IF-IP-DCL-04 `pageInpatientCashierSettlementHistory`

**Request `PageInpatientCashierSettlementHistoryRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `submittedFrom` | `LocalDateTime` | Y | 提交时刻起 |
| `submittedTo` | `LocalDateTime` | Y | 提交时刻止 |
| `pageIndex` | `Integer` | N | 默认 0 |
| `pageSize` | `Integer` | N | 默认 20，最大 200 |

**Response**：`PageInpatientCashierSettlementHistoryResponse` — `items[]` 含 `settlementId`、`busNo`、`submittedAt`、`dateFrom`/`dateTo`、`practitionerIds`、`submitterId`、`paymentCount`、`totalIncome`、`canReprintFromMounts`、**`statusEnum`**、**`statusEnumText`**.

**编排序列**：Gate 登录 + 时刻窗 → `CashierSettlementQueryService#pageHistory(IP_PAYMENT, ...)` → 逐行 `resolveCashierSettlementStatusDisplay`.

**失败码**：EX-IP-DCL-102、103

---

### 3.6 IF-IP-DCL-05 `loadInpatientCashierSettlementVoucher`

**Request**：`LoadInpatientCashierSettlementVoucherRequest` — `settlementId`（Y）

**Response `LoadInpatientCashierSettlementVoucherResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `settlementId` | `Long` | 交账头 |
| `busNo` | `String` | 单号 |
| `mountedVoucher` | `CashierSettlementMountedVoucherDto` | 现算打印体 |

**`CashierSettlementMountedVoucherDto`**

| 字段 | 说明 |
| --- | --- |
| `schemaVersion` | 固定 1 |
| `settlementPeriodText` | 业务窗文案 |
| `rows` | 上半区（住院 fin_type 口径，含 deposit 排除逻辑） |
| `summary` | 下半区（含 `depositAmount`） |
| `patientSummaries` | 患者分项块（挂载现算；**BR-IP-DCL-030**） |
| `paymentIds` | 挂载 payment 主键（string） |

**编排序列**：校验 `type=IP_PAYMENT` → `rebuildInpatientMountedVoucherFromMounts` → 复用 IF-02 Mapper 管线 + Encounter。

**失败码**：EX-IP-DCL-102、103

---

### 3.7 IF-IP-DCL-06 `withdrawInpatientDailySettlement`

**Request**：`WithdrawInpatientDailySettlementRequest` — `settlementId`（Y）

**Response**：`WithdrawInpatientDailySettlementResponse` — `settlementId`、`busNo`

**编排序列**

| 序号 | 阶段 | 说明 |
| --- | --- | --- |
| 1 | Gate | 登录；交账头存在且 `IP_PAYMENT` |
| 2 | Gate | `status=SUBMITTED`；款员 → 否则 **EX-IP-DCL-107** |
| 3 | Cmd | `withdrawCashierSettlement` → **WITHDRAWN** + 清挂载 + `CashierSettlementWithdrawnEvent` |

**失败码**：EX-IP-DCL-102、103、107

---

## 4. QueryService 与领域事件

| 事件 | 触发 IF | 说明 |
| --- | --- | --- |
| `CashierSettlementSubmittedEvent` | IF-03 | `typeEnum=IP_PAYMENT` |
| `CashierSettlementWithdrawnEvent` | IF-06 | 清挂载 |

住院日结查询额外依赖 `EncounterContextReader` 批量回填病区，**不**写 Encounter 聚合。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 对照

| IF | App 方法 | 领域调用 | 写入 |
| --- | --- | --- | --- |
| IF-03 | `submitInpatientDailySettlement` | `submitCashierSettlement(IP_PAYMENT)` | insert 交账头 `SUBMITTED` → update 勾选 payment |
| IF-06 | `withdrawInpatientDailySettlement` | `withdrawCashierSettlement` | 清 payment 挂载 → 交账头 `WITHDRAWN` |

### 5.2 事务边界

同 **AS-OP-FIN-004-SDD** §5.2；预交金与住院结算 payment **同一批次**原子提交（**BR-IP-DCL-004**）。

---

## 6. 非功能性需求实现

| 小节 | 内容 |
| --- | --- |
| 6.1 幂等 | 已交账 payment 不再进入候选；并发第二次 → EX-IP-DCL-301 |
| 6.2 并发 | update 前 `cashier_settlement_id IS NULL` |
| 6.3 重试 | EX-IP-DCL-301 可刷新重试 |
| 6.4 脱敏 | 无扩展 |
| 6.5 审计 | 交账头 `submitterId`/`submitted_at`；事件载荷 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 优先级 |
| --- | --- |
| orgId 必填；cashierIds 空/多选与 preview 一致 | P0 |
| 漏交硬栏预览可见、提交 EX-IP-DCL-106（不灰确认入口） | P0 |
| 预交金 + 结算同批 → depositAmount 独立、totalIncome 不含预交金 → 交账 SUBMITTED | P0 |
| 历史 statusEnumText；重打合计/患者分项 mountedVoucher；撤回 WITHDRAWN | P0 |
| 患者分项加总勾回合计；不含上传差额；不改交账提交 | P0 |
| practitionerOptions 窗内合并；无 listActiveCashiers | P1 |
| paymentIds 非法 EX-IP-DCL-105；撤回权限 EX-IP-DCL-107 | P0 |
| wardName / 「预交金」分类 | P0 |

### 7.2 非功能 / 一致性 / 集成

| 用例 | 优先级 |
| --- | --- |
| 并发交账 EX-IP-DCL-301 | P0 |
| 提交口径不含 expenseProperty | P0 |
| Encounter 缺失 wardName → 「—」 | P1 |
| 药品收入 API 可调用但 FE 无 TAB | P2（非目标） |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v4.15 | 2026-10-14 | 岗池 **ROLE_CASHIER** + 日结 `excludePlatformAdmin=false`；对齐 AS v4.19 / **B.6.3.1**；代码先行回写 |
| v4.14 | 2026-10-13 | **BR-IP-DCL-030**：FE 患者分项 Excel 导出（一行一患者）；对齐 AS v4.17；代码先行回写 |
| v4.13 | 2026-10-13 | **BR-IP-DCL-030**：summary/`patientSummaries` 增 `medicationTotalAmount`（药品合计金额）；对齐 AS v4.16；代码先行回写 |
| v4.12 | 2026-10-09 | **BR-IP-DCL-002**：住院上半区保留诊察费 `1012`；仅排除 `1011`/体检；代码先行回写，对齐 AS v4.15 |
| v4.11 | 2026-10-06 | **BR-IP-DCL-031**：折扣合计 + 上半区/总收入折后；`discountFeatureEnabled`；对齐 AS v4.14 / AS-OP-FIN-004-SDD v4.14 |
| v4.8 | 2026-09-09 | **BR-IP-DCL-030**：`patientSummaries` + mountedVoucher 同出；FE 双 Tab/分别打印；对齐 AS v4.11；装配约束：fin_type 快照与账户/险种预载全窗一次，禁按患者 N 次 Extension IO |
| v4.7 | 2026-08-21 | **BR-IP-DCL-005**：渠道勾稽前提对齐结算 **BR-IP-STL-006a**（SUCCESS 禁 `CASHIER`）；对齐 AS v4.10 |
| v4.6 | 2026-08-21 | **BR-IP-DCL-029**：summary 增多退少补列；结算补收=结算自付渠道实付（非补预交金、不强制冲抵门控）；对齐 AS v4.9 / DD |
| v4.5 | 2026-08-02 | **BR-IP-DCL-027**：费用性质混结分桶对齐 AS-OP-FIN-004-SDD v4.5 / AS v4.6 |
| v4.4 | 2026-07-30 | **FE**：侧栏嵌 CHS 日对账（`clrType=21`）；不展示体检人次；收款人多选不折叠；对齐 AS v4.5 / AS-OP-FIN-004-SDD v4.4 |
| v4.3 | 2026-07-30 | **BR-IP-DCL-026**：交账弹窗行净额按带符号 charge 行额回写；对齐 AS v4.4 / AS-OP-FIN-004-SDD v4.3 |
| v4.2 | 2026-07-28 | IF-IP-DCL-03：`resolveSelectedSettlementTotalIncome`；对齐 AS v4.2 / AS-OP-FIN-004-SDD v4.1；消解合并冲突 |
| v4.1 | 2026-07-27 | **对齐成码门诊对称**：`IP_PAYMENT`；IF-04～06；`orgId`/`cashierIds`/`practitionerOptions`；漏交 106/107；`mountedVoucher`/`statusEnumText`/`SUBMITTED`/`WITHDRAWN`；保留住院 deposit/ward/fin_type |
| v4.0 | 2026-07-11 | `depositAmount` 独立；`totalIncome` 不含预交金 |
| v3.9 | 2026-07-11 | handover*、`paymentIds`、enrichment |
| v3.0 | 2026-06-21 | health 应用层；病区/预交金 |
| v1.0 | 2026-06-19 | 首版（已废止） |
