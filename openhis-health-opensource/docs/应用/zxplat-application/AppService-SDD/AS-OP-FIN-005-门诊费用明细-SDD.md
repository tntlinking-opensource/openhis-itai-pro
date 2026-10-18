# 门诊费用明细 · 应用服务详细设计（SDD）

> **编码**：AS-OP-FIN-005-SDD  
> **版本**：v1.4 | **日期**：2026-10-06
> **编排权威**：`AS-OP-FIN-005-门诊费用明细.md`（**v1.10**）
> **领域权威**：`DM-ADM-011-ChargeItem-收费明细.md`、`DM-FIN-002-PaymentReconciliation-支付流水.md`、`DM-ADM-002` Encounter  
> **说明**：本 SDD **不维护 Gap 表**；领域读能力见 AS §3（**GAP-OP-FDT-001～002 已关闭**）；**应用层** `outpatient/financial/feedetail/**` **已落地**（2026-06-24）。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊费用明细（初始化、分页查询、导出） |
| **边界** | 只读查询；按就诊分组 + 财务金额列；**不**接报表中心 RPT；**不**跳转收费/退费 |
| **编排权威** | `AS-OP-FIN-005` §2 / §4 |
| **方法权威** | `DM-ADM-011` §6.2；`DM-FIN-002` §6.2 |
| **严格对齐** | IF **IF-OP-FDT-01～03** |
| **实现阶段口径** | **领域读 + querysupport + 应用层** `outpatient/financial/feedetail/**` **均已实现**（2026-06-24） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientFeeDetail/{methodName}` |
| **分页** | `pageNo` **1-based**；`PagedResult` 出参 `content` / `totalCount` |
| **operatorId** | `CurrentOperatorResolver#requireOperatorId`（Gate；**不**做个人数据权限收窄） |
| **前端 HTTP 客户端** | 须使用 `publicRequestClient`（`responseReturn: 'data'`）；分页/初始化/导出均直接消费业务 DTO；**禁止** `requestClient` 未解包 `data` 导致下拉与列表为空 |
| **时间** | Request `dateFrom`/`dateTo` 为 `yyyy-MM-dd`；域查询转 `bill_date` 闭区间 `[startOfDay, endOfDay]` |
| **金额刻度** | 计算 **6 位 HALF_UP**；展示 **2 位**（SDD 装配层 `setScale(2, HALF_UP)`） |
| **退单符号** | `tradeDirection=REFUND` 或 payment `status=REFUNDED` 时，金额列 × **−1**（**BR-OP-FDT-012**） |
| **医保分摊** | payment 存在 **CONFIRMED** `ybSettlements` 时，按快照内 `\|total_price\|` 占比分摊 **基金+个账**；禁止从 `PaymentRecDetail` 汇医保 |
| **事务** | 本模块**只读**，AppService **禁止** `@Transactional` 写事务 |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/financial/feedetail/
  service/
    OutpatientFeeDetailAppService.java
  request/
    LoadOutpatientFeeDetailPageInitRequest.java
    PageOutpatientFeeDetailRequest.java
    ExportOutpatientFeeDetailRequest.java
  response/
    LoadOutpatientFeeDetailPageInitResponse.java
    PageOutpatientFeeDetailResponse.java
    ExportOutpatientFeeDetailResponse.java
  dto/
    OutpatientFeeDetailEncounterBlockDto.java
    OutpatientFeeDetailLineDto.java
    OutpatientFeeDetailAmountSummaryDto.java
    OutpatientFeeDetailExportRowDto.java
  assembler/
    OutpatientFeeDetailAssembler.java
  mapper/
    OutpatientFeeDetailMapper.java
    OutpatientFeeDetailMapperImpl.java

zxplat/health-application/src/main/java/com/openhis/health/application/feedetail/
  OutpatientFeeDetailQuerySupport.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/administration/chargeitem/query/
  OutpatientFeeDetailPageQuery.java
  OutpatientFeeDetailEncounterBlockQueryModel.java
  OutpatientFeeDetailChargeRowQueryModel.java
  OutpatientFeeDetailAmountSummaryQueryModel.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/financial/paymentreconciliation/queries/model/
  OutpatientFeeDetailPaymentContextQueryModel.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  OutpatientFeeDetailConstants.java
```

> 包名：`com.zxplat.domain.application.outpatient.financial.feedetail`  
> `OptionDto` 使用 zxplat-framework 公共库。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | IF-01～03 Gate |
| `ChargeItemQueryService` | IF-02/03：`pageOutpatientFeeDetailEncounterBlocks`、`summarizeOutpatientFeeDetailAmounts` |
| `PaymentReconciliationQueryService` | IF-02/03：`mapOutpatientFeeDetailPaymentContextByChargeItemIds` |
| `EncounterQueryService` | IF-02/03：批量 Encounter（挂号科室、busNo） |
| `PatientQueryService` | IF-02/03：批量 Patient（姓名） |
| `OrganizationQueryService` | IF-01：挂号科室下拉；IF-02/03 科室名展示 |
| `PractitionerQueryService` | IF-01：收费员下拉；IF-02/03 开单医生/收费人姓名 |
| `ProductDefinitionBatchLoader` | IF-02/03：`loadAsMap`（项目名/编码/规格/细分类） |
| `TerminologyOptionLoader` | IF-01：细分类字典；IF-02/03 单位/医保等级译文 |
| `OutpatientFeeDetailQuerysupport | IF-02/03：`prepareOutpatientFeeDetailQueryScopes`（payment→科室/患者/项目 scope 预解析） |
| `OutpatientFeeDetailMapper` | Request ↔ Query / Response 装配 |
| `OutpatientFeeDetailAssembler` | 金额列计算、医保分摊、块小计、summary |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 明细状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §5 | 白名单 `SETTLED`/`REFUNDING`/`REFUNDED` |
| 支付状态 | `com.zxplat.domain.share.enums.PaymentStatus` | `DM-FIN-002` §2.3 | `SUCCESS`/`REFUNDED` |
| 支付 kind | `com.zxplat.domain.share.enums.PaymentKind` | `DM-FIN-002` §4.2 | 门诊 `OUTPATIENT_CLINIC` |
| 收退 Tab | `PageOutpatientFeeDetailRequest.tradeDirection` | **AS-OP-FDT** **BR-OP-FDT-009** | `ALL`/`CHARGE`/`REFUND`；常量 **`OutpatientFeeDetailConstants.TRADE_*`**（目标态待生成） |
| 费用大类 | `PageOutpatientFeeDetailRequest.feeCategoryCodes` | `DM-ADM-011` §4.3 `context_enum`；**BR-OP-FDT-006** | `MEDICATION`/`ACTIVITY`/`DEVICE`/`REGISTER` |
| 细分类 | `PageOutpatientFeeDetailRequest.subCategoryCodes` | `DailySettlementFinTypeAmountLoader` ← `definition_id.type_code`（**BR-OP-FDT-017**）；硬筛投影可不含 `unit_price` | 字典 code 多选（fin_type） |
| 挂号锚点 | `com.zxplat.domain.share.constants.TableNameConstants.ADM_HEALTHCARE_SERVICE` | **BR-OP-FDT-006** | 映射「挂号」大类 |
| 数量单位 | `OutpatientFeeDetailLineDto.quantityUnitDisplay` | `DM-ADM-011` §3 `quantity_unit` | 字典 `unit_code` |
| 医保关联 | `com.zxplat.domain.financial.paymentreconciliation.PaymentYbSettlementStatus` | `DM-FIN-002` §3.4 | `CONFIRMED` 才参与分摊 |
| 业务消息码 | `OutpatientFeeDetailConstants.EX_OP_FDT_*` | **AS-OP-FDT** §5 | **101～105、201**（目标态待生成） |
| 导出上限 | `OutpatientFeeDetailConstants.EXPORT_MAX_ROWS` | **AS-OP-FDT** §7 | 默认 **50000**（目标态待生成） |

### 2.4 领域方法与 Mapper 白名单

**领域读（QueryService）**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `ChargeItemQueryService` | `pageOutpatientFeeDetailEncounterBlocks` | IF-02、03 |
| `ChargeItemQueryService` | `summarizeOutpatientFeeDetailAmounts` | IF-02、03 |
| `PaymentReconciliationQueryService` | `mapOutpatientFeeDetailPaymentContextByChargeItemIds` | IF-02、03 |
| `EncounterQueryService` | `loadEncountersByIds` | IF-02、03 |
| `PatientQueryService` | `loadPatientsByIds` | IF-02、03 |
| `OrganizationQueryService` | `listOutpatientRegistrationOrganizations` | IF-01 |
| `PractitionerQueryService` | `pageActivePractitionersForPosition` | IF-01 |
| `PractitionerQueryService` | `loadPractitionersByIds` | IF-02、03 |
| `ProductDefinitionBatchLoader` | `loadAsMap` | IF-02、03（含 `adm_healthcare_service` 挂号费） |
| `ChargeItemProductLookupsupport | `buildLookupFromChargeItem` | IF-02、03 产品锚点回落 |
| `TerminologyOptionLoader` | `loadOptionsByCodeSystem` | IF-01、02、03 |

**Support**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientFeeDetailQuerysupport | `prepareOutpatientFeeDetailQueryScopes` | IF-02、03 |
| `PaymentYbSettlementsupport | `hasConfirmed` | IF-02、03 分摊 Gate |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientFeeDetailMapper` | `toOutpatientFeeDetailPageQuery` | IF-02、03 |
| `OutpatientFeeDetailMapper` | `toLoadOutpatientFeeDetailPageInitResponse` | IF-01 |
| `OutpatientFeeDetailMapper` | `toPageOutpatientFeeDetailResponse` | IF-02 |
| `OutpatientFeeDetailMapper` | `toExportOutpatientFeeDetailResponse` | IF-03 |
| `OutpatientFeeDetailMapper` | `toExportRowDto` | IF-03 |

**Assembler**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `OutpatientFeeDetailAssembler` | `assembleLineAmounts` | IF-02、03 |
| `OutpatientFeeDetailAssembler` | `assembleEncounterSubtotal` | IF-02、03 |
| `OutpatientFeeDetailAssembler` | `allocateInsuranceAmount` | IF-02、03 |
| `OutpatientFeeDetailAssembler` | `computeDiscountRateDisplay` | IF-02、03（可选列） |

> **领域层能力核对（非 Gap）**：`EncounterQueryService#loadEncountersByIds`、`PatientQueryService#loadPatientsByIds`、`ProductDefinitionBatchLoader#loadAsMap` 等已存在。  
> **已落地（2026-06-21）**：`OutpatientFeeDetailQuerySupport#prepareOutpatientFeeDetailQueryScopes`；`ChargeItemQueryService#pageOutpatientFeeDetailEncounterBlocks`、`#summarizeOutpatientFeeDetailAmounts`（**DM-ADM-011** §6.2.25～6.2.26）；`PaymentReconciliationQueryService#listOutpatientFeeDetailChargeItemIdsByPaymentFilter`、`#mapOutpatientFeeDetailPaymentContextByChargeItemIds(OutpatientFeeDetailPageQuery, Collection<Long>)`（**DM-FIN-002** §6.2.17～6.2.18）。

---

## 3. 按接口 ID 命名展开

**导语**：先读完各 IF 的「领域编排序列」，按序号自上而下执行；本模块无 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-OP-FDT-01 | `loadOutpatientFeeDetailPageInit` | 门诊费用明细页初始化 | `POST /api/app/outpatientFeeDetail/loadOutpatientFeeDetailPageInit` |
| IF-OP-FDT-02 | `pageOutpatientFeeDetail` | 分页查询门诊费用明细 | `POST /api/app/outpatientFeeDetail/pageOutpatientFeeDetail` |
| IF-OP-FDT-03 | `exportOutpatientFeeDetail` | 导出门诊费用明细 | `POST /api/app/outpatientFeeDetail/exportOutpatientFeeDetail` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录、日期/分页/Tab 参数门禁 | 停止，抛 EX-OP-FDT-* |
| **Pre** | 只读：域分页查询、批量 enrichment | 抛业务异常 |
| **Asm** | Mapper/Assembler 装配 Response / Excel | — |

---

### 3.2 IF-OP-FDT-01 `loadOutpatientFeeDetailPageInit`（门诊费用明细页初始化）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientFeeDetail/loadOutpatientFeeDetailPageInit` |

**Request `LoadOutpatientFeeDetailPageInitRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| — | — | — | 空体 |

**Response `LoadOutpatientFeeDetailPageInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultDateFrom` | `String` | 当日 `yyyy-MM-dd` |
| `defaultDateTo` | `String` | 当日 `yyyy-MM-dd` |
| `defaultPageSize` | `Integer` | **10** |
| `tradeDirectionOptions` | `List<OptionDto>` | `ALL`/`CHARGE`/`REFUND` |
| `feeCategoryOptions` | `List<OptionDto>` | 药品/诊疗/耗材/挂号 |
| `medicationSubCategoryOptions` | `List<OptionDto>` | 药品细分类 |
| `activitySubCategoryOptions` | `List<OptionDto>` | 诊疗细分类 |
| `deviceSubCategoryOptions` | `List<OptionDto>` | 耗材细分类 |
| `registrationDepartmentOptions` | `List<OptionDto>` | 挂号科室 |
| `cashierOptions` | `List<OptionDto>` | 收费员（Practitioner） |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | EX-OP-FDT-101 | 未登录拒绝 |
| 2 | Pre | `OrganizationQueryService#listOutpatientRegistrationOrganizations` | 租户 | 挂号科室下拉 |
| 3 | Pre | `PractitionerQueryService#pageActivePractitionersForPosition` | 收费岗位 | 收费员下拉 |
| 4 | Pre | `TerminologyOptionLoader#loadOptionsByCodeSystem` | 各 category 字典 | 细分类选项 |
| 5 | Asm | `OutpatientFeeDetailMapper#toLoadOutpatientFeeDetailPageInitResponse` | 步骤 2～4 | 仅读 |

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-OP-FDT-101 | 未登录或主数据加载失败 |

---

### 3.3 IF-OP-FDT-02 `pageOutpatientFeeDetail`（分页查询门诊费用明细）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientFeeDetail/pageOutpatientFeeDetail` |

**Request `PageOutpatientFeeDetailRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dateFrom` | `String` | 是 | `yyyy-MM-dd` |
| `dateTo` | `String` | 是 | `yyyy-MM-dd` |
| `registrationDepartmentId` | `Long` | 否 | 挂号科室 |
| `feeCategoryCodes` | `List<String>` | 否 | 大类多选 |
| `subCategoryCodes` | `List<String>` | 否 | 细分类多选 |
| `patientKeyword` | `String` | 否 | 姓名/busNo 模糊 |
| `productNameKeyword` | `String` | 否 | 项目名称模糊 |
| `productCodeKeyword` | `String` | 否 | 院内编码模糊；与名称 **AND** |
| `cashierId` | `Long` | 否 | payment.enterer_id |
| `tradeDirection` | `String` | 否 | 默认 `ALL` |
| `pageNo` | `Integer` | 否 | 默认 1 |
| `pageSize` | `Integer` | 否 | 默认 10（就诊块数） |

**Response `PageOutpatientFeeDetailResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<OutpatientFeeDetailEncounterBlockDto>` | 当前页就诊块 |
| `totalCount` | `Long` | 命中就诊块总数 |
| `pageNo` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 页大小 |
| `summary` | `OutpatientFeeDetailAmountSummaryDto` | **全量**命中合计 |

**DTO `OutpatientFeeDetailEncounterBlockDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊主键 |
| `patientName` | `String` | 姓名 |
| `busNo` | `String` | 门诊号 |
| `registrationDepartmentName` | `String` | 挂号科室 |
| `encounterDate` | `String` | 就诊日期展示 |
| `lines` | `List<OutpatientFeeDetailLineDto>` | 块内明细，按 billDate 倒序 |
| `subtotal` | `OutpatientFeeDetailAmountSummaryDto` | 就诊小计 |

**DTO `OutpatientFeeDetailLineDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chargeItemId` | `Long` | 明细主键 |
| `paymentId` | `Long` | 关联 payment |
| `feeCategoryCode` | `String` | 大类 code |
| `feeCategoryDisplay` | `String` | 大类文案 |
| `subCategoryCode` | `String` | 细分类 code |
| `subCategoryDisplay` | `String` | 细分类文案 |
| `productName` | `String` | 项目名称 |
| `productCode` | `String` | 院内编码 |
| `specification` | `String` | 规格 |
| `quantityValue` | `BigDecimal` | 数量 |
| `quantityUnitDisplay` | `String` | 单位 |
| `orderingDoctorId` | `Long` | 开单医生 |
| `orderingDoctorName` | `String` | 开单医生姓名 |
| `cashierId` | `Long` | 收费人 |
| `cashierName` | `String` | 收费人姓名 |
| `billDate` | `String` | 结算时间 `yyyy-MM-dd HH:mm:ss` |
| `tradeDirection` | `String` | `CHARGE`/`REFUND` |
| `feeNatureContractNo` | `String` | 费用性质合同业务号 |
| `feeNatureDisplay` | `String` | 费用性质 |
| `insuranceLevelDisplay` | `String` | 医保等级 |
| `baseAmount` | `BigDecimal` | 原价 |
| `discountAmount` | `BigDecimal` | 优惠金额（`|total_price|−|折后应收|`，优先 `system_discount_price` 差额） |
| `receivableAmount` | `BigDecimal` | 应收金额（折后 `discount_amount`） |
| `discountedAmount` | `BigDecimal` | 折后金额（与应收同锚，非优惠差额） |
| `purchaseUnitPrice` | `BigDecimal` | 进价单价（退负） |
| `purchaseAmount` | `BigDecimal` | 进价总价（退负） |
| `saleUnitPrice` | `BigDecimal` | 售价单价（退负） |
| `saleAmount` | `BigDecimal` | 售价总价（退负） |
| `actualReceivedAmount` | `BigDecimal` | 实收金额（退负） |
| `insuranceAmount` | `BigDecimal` | 医保金额（退负） |
| `selfPayAmount` | `BigDecimal` | 自费金额（退负） |
| `discountRateDisplay` | `String` | 折扣率展示（可选） |

**DTO `OutpatientFeeDetailAmountSummaryDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `baseAmountTotal` | `BigDecimal` | 原价合计 |
| `discountAmountTotal` | `BigDecimal` | 优惠合计 |
| `receivableAmountTotal` | `BigDecimal` | 应收合计 |
| `discountedAmountTotal` | `BigDecimal` | 折后合计 |
| `lineAmountTotal` | `BigDecimal` | 行金额合计 |
| `actualReceivedAmountTotal` | `BigDecimal` | 实收合计 |
| `insuranceAmountTotal` | `BigDecimal` | 医保合计 |
| `selfPayAmountTotal` | `BigDecimal` | 自费合计 |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toOutpatientFeeDetailPageQuery` | `toOutpatientFeeDetailPageQuery(PageOutpatientFeeDetailRequest request): OutpatientFeeDetailPageQuery` |
| `toPageOutpatientFeeDetailResponse` | `toPageOutpatientFeeDetailResponse(Page<OutpatientFeeDetailEncounterBlockDto> blocks, OutpatientFeeDetailAmountSummaryDto summary, PageOutpatientFeeDetailRequest request): PageOutpatientFeeDetailResponse` |

**Assembler 方法签名**

| 方法 | 签名 |
| --- | --- |
| `assembleLineAmounts` | `assembleLineAmounts(OutpatientFeeDetailChargeRowQueryModel row, OutpatientFeeDetailPaymentContextQueryModel paymentCtx, ProductDefinitionDisplayDto product): OutpatientFeeDetailLineDto` |
| `assembleEncounterSubtotal` | `assembleEncounterSubtotal(List<OutpatientFeeDetailLineDto> lines): OutpatientFeeDetailAmountSummaryDto` |
| `allocateInsuranceAmount` | `allocateInsuranceAmount(BigDecimal lineAbsAmount, BigDecimal paymentYbTotal, BigDecimal paymentSnapshotAbsTotal): BigDecimal` |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | EX-OP-FDT-101 | 登录 |
| 2 | Gate | App 校验 `dateFrom`/`dateTo`/`tradeDirection`/`pageNo`/`pageSize` | EX-OP-FDT-102 | **BR-OP-FDT-001、009、013** |
| 3 | Asm | `OutpatientFeeDetailMapper#toOutpatientFeeDetailPageQuery` | Request | 映射 Query |
| 4 | Pre | `OutpatientFeeDetailQuerySupport#prepareOutpatientFeeDetailQueryScopes` | Query | payment/科室/患者/项目 scope |
| 5 | Pre | `ChargeItemQueryService#pageOutpatientFeeDetailEncounterBlocks` | Query | **DM-ADM-011** §6.2.25 |
| 6 | Pre | `PaymentReconciliationQueryService#mapOutpatientFeeDetailPaymentContextByChargeItemIds` | query + chargeItemIds | **DM-FIN-002** §6.2.18；billDate/enterer/医保 |
| 7 | Pre | `EncounterQueryService#loadEncountersByIds` | encounterIds | 挂号科室、busNo |
| 8 | Pre | `PatientQueryService#loadPatientsByIds` | patientIds | 姓名 |
| 9 | Pre | `PractitionerQueryService#loadPractitionersByIds` | entererIds + cashierIds | 医生/收费人 |
| 10 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | productKeys | 项目/细分类 |
| 11 | Pre | `ChargeItemQueryService#summarizeOutpatientFeeDetailAmounts` | 同 Query | **全量 summary**（**BR-OP-FDT-013**） |
| 12 | Asm | `OutpatientFeeDetailAssembler#assembleLineAmounts` | 每行 | **§1.3.1** 金额；**BR-OP-FDT-010～012** |
| 13 | Asm | `OutpatientFeeDetailAssembler#assembleEncounterSubtotal` | 块内 lines | 就诊小计 |
| 14 | Asm | `OutpatientFeeDetailMapper#toPageOutpatientFeeDetailResponse` | blocks + summary | 返回 |

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-OP-FDT-102 | 日期非法 |
| EX-OP-FDT-103 | 缺 product/category 或 service_table 无法归类 |
| EX-OP-FDT-104 | 查询执行失败 |

---

### 3.4 IF-OP-FDT-03 `exportOutpatientFeeDetail`（导出门诊费用明细）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientFeeDetail/exportOutpatientFeeDetail` |

**Request `ExportOutpatientFeeDetailRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dateFrom` | `String` | 是 | 同 IF-02 |
| `dateTo` | `String` | 是 | 同 IF-02 |
| `registrationDepartmentId` | `Long` | 否 | 同 IF-02 |
| `feeCategoryCodes` | `List<String>` | 否 | 同 IF-02 |
| `subCategoryCodes` | `List<String>` | 否 | 同 IF-02 |
| `patientKeyword` | `String` | 否 | 同 IF-02 |
| `productNameKeyword` | `String` | 否 | 同 IF-02 |
| `productCodeKeyword` | `String` | 否 | 同 IF-02 |
| `cashierId` | `Long` | 否 | 同 IF-02 |
| `tradeDirection` | `String` | 否 | 同 IF-02 |

**Response `ExportOutpatientFeeDetailResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | 建议 `门诊费用明细_yyyyMMddHHmmss.xlsx` |
| `contentType` | `String` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| `body` | `byte[]` | xlsx 字节 |
| `rowCount` | `Integer` | 导出行数（含小计/合计行策略见说明） |
| `truncated` | `Boolean` | 是否触达 `EXPORT_MAX_ROWS` 上限 |

**DTO `OutpatientFeeDetailExportRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `patientName` | `String` | @Excel 姓名 |
| `busNo` | `String` | @Excel 门诊号 |
| `registrationDepartmentName` | `String` | @Excel 挂号科室 |
| `feeCategoryDisplay` | `String` | @Excel 费用大类 |
| `subCategoryDisplay` | `String` | @Excel 细分类 |
| `productName` | `String` | @Excel 项目名称 |
| `productCode` | `String` | @Excel 项目编码 |
| `specification` | `String` | @Excel 规格 |
| `quantityValue` | `BigDecimal` | @Excel 数量 |
| `quantityUnitDisplay` | `String` | @Excel 单位 |
| `orderingDoctorName` | `String` | @Excel 开单医生 |
| `cashierName` | `String` | @Excel 收费人 |
| `billDate` | `String` | @Excel 结算时间 |
| `insuranceLevelDisplay` | `String` | @Excel 医保等级 |
| `purchaseUnitPrice` | `BigDecimal` | @Excel 进价单价 |
| `purchaseAmount` | `BigDecimal` | @Excel 进价总价 |
| `saleUnitPrice` | `BigDecimal` | @Excel 售价单价 |
| `saleAmount` | `BigDecimal` | @Excel 售价总价 |
| `actualReceivedAmount` | `BigDecimal` | @Excel 实收金额 |

**Mapper 方法签名**

| 方法 | 签名 |
| --- | --- |
| `toExportRowDto` | `toExportRowDto(OutpatientFeeDetailLineDto line, OutpatientFeeDetailEncounterBlockDto block): OutpatientFeeDetailExportRowDto` |
| `toExportOutpatientFeeDetailResponse` | `toExportOutpatientFeeDetailResponse(byte[] body, int rowCount, boolean truncated): ExportOutpatientFeeDetailResponse` |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | EX-OP-FDT-101 | 登录 |
| 2 | Gate | 同 IF-02 步骤 2 | EX-OP-FDT-102 | 日期/Tab |
| 3 | Asm | `OutpatientFeeDetailMapper#toOutpatientFeeDetailPageQuery` | ExportRequest | 映射 Query；`pageSize=EXPORT_MAX_ROWS` |
| 4 | Pre | **复用 IF-OP-FDT-02 编排序列 4～12** | 全量 blocks | **BR-OP-FDT-016** 同口径 |
| 5 | Gate | 判断行数 `> EXPORT_MAX_ROWS` | EX-OP-FDT-105 或 truncated=true | 触顶策略：截断并 `truncated=true` |
| 6 | Asm | 平铺 blocks→`OutpatientFeeDetailExportRowDto` | Mapper | 块间可插入小计行（实现可选） |
| 7 | Asm | `AnnotatedExcelExporter#exportToXlsxBytes` | exportRows | 框架导出 |
| 8 | Asm | `OutpatientFeeDetailMapper#toExportOutpatientFeeDetailResponse` | body | 返回 |

**失败码**

| 语义码 | 触发 |
| --- | --- |
| EX-OP-FDT-105 | 导出失败 |
| EX-OP-FDT-201 | 无数据且采用拒绝策略（可选） |

---

## 4. QueryService 与领域事件

### 4.1 ChargeItem 读契约（GAP-OP-FDT-001 **已关闭**；**DM-ADM-011** §6.2.25～6.2.26）

**`pageOutpatientFeeDetailEncounterBlocks(OutpatientFeeDetailPageQuery query): Page<OutpatientFeeDetailEncounterBlockQueryModel>`**

| 项 | 约定 |
| --- | --- |
| **过滤** | 须先经 `OutpatientFeeDetailLoader#prepareOutpatientFeeDetailQueryScopes`；scope 内 `SETTLED`/`REFUNDING`/`REFUNDED` + payment 上下文 + 大类（**BR-OP-FDT-006**） |
| **分页** | 按 **encounterId** 分组后分页；块内返回全部命中 charge 行 |
| **排序** | 块内 `bill_date DESC`；块间按块内 max(`bill_date`) DESC |
| **返回** | 每块含 `encounterId`、`patientId`、行列表 `OutpatientFeeDetailChargeRowQueryModel`（含 chargeItem 金额字段、service_table、product 键、entererId） |

**`summarizeOutpatientFeeDetailAmounts(OutpatientFeeDetailPageQuery query): OutpatientFeeDetailAmountSummaryQueryModel`**

| 项 | 约定 |
| --- | --- |
| **语义** | 与 page 查询**相同 WHERE**；SQL 层对金额列聚合（应用层再叠加退负与医保分摊时，summary 以 Assembler 全量重算为准，域层可先返回 `total_price` 代数和） |

### 4.2 Payment enrichment（GAP-OP-FDT-002 **已关闭**；**DM-FIN-002** §6.2.17～6.2.18）

**`listOutpatientFeeDetailChargeItemIdsByPaymentFilter(OutpatientFeeDetailPageQuery query): List<Long>`**

| 项 | 约定 |
| --- | --- |
| **语义** | payment `bill_date`/cashier/tradeDirection 下推；解析 `charge_item_ids`；写入 `query.paymentChargeItemLinks`（一快照一行，同 chargeItem 收退各保留）及兼容用 `paymentContextByChargeItemId` |

**`mapOutpatientFeeDetailPaymentContextByChargeItemIds(OutpatientFeeDetailPageQuery query, Collection<Long> chargeItemIds): Map<Long, OutpatientFeeDetailPaymentContextQueryModel>`**

| 字段 | 说明 |
| --- | --- |
| `chargeItemId` | key |
| `paymentId` | 关联 payment |
| `billDate` | 结算时间 |
| `entererId` | 收费人（`cashierId`） |
| `refundPayment` | 是否退费 payment |
| `paymentSnapshotAbsAmountTotal` | 快照内 `\|total_price\|` 之和（分摊分母） |
| `ybFundAndAcctTotal` | 已确认医保凭证基金+个账合计 |

### 4.3 事件消费

| 事件 | 读侧刷新 |
| --- | --- |
| `OutpatientPaymentSettledEvent` | 用户手动查询可见新收费行 |
| 退费完成事件 | 用户手动查询可见退费负行 |

> 本模块**不订阅**事件；无 QueryService 投影延迟承诺。

---

## 5. 写库顺序（核心写接口视角）

本模块 **IF-01～03 均为只读**，**无 Cmd 写库点**。

| IF | App 方法 | Cmd | 说明 |
| --- | --- | --- | --- |
| IF-OP-FDT-01 | `loadOutpatientFeeDetailPageInit` | — | 只读 |
| IF-OP-FDT-02 | `pageOutpatientFeeDetail` | — | 只读 |
| IF-OP-FDT-03 | `exportOutpatientFeeDetail` | — | 只读 |

**事务边界**：无写事务；只读查询默认 Spring 只读连接（可选 `@Transactional(readOnly=true)` 于 AppService）。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 查询/导出 | 纯读，天然幂等 | `OutpatientFeeDetailAppService` | §3.3/3.4 全序列 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 无写路径 | 不适用 | — | — |

### 6.3 重试与容错

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 域查询异常 | 捕获转 EX-OP-FDT-104/105 | AppService catch | §3.3 序号 4～10 |

### 6.4 数据脱敏

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 本期 | 无额外脱敏（租户内财务查询） | — | — |

### 6.5 审计日志

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 导出 | 记录 operatorId、筛选条件、rowCount | AppService 导出成功路径 | §3.4 序号 7～8 |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 初始化下发选项与默认当日 | 集成 | 后端 | P0 |
| 收费/退费/全部 Tab | 集成 | 后端 | P0 |
| 就诊块分页 10 条 | 集成 | 后端 | P0 |
| 金额列：应收/实收/医保/自费/折后 | 单元+集成 | Assembler | P0 |
| 退单负号 | 单元 | Assembler | P0 |
| summary 全量 vs 当前页 | 集成 | 后端 | P0 |
| 导出列齐全 | 集成 | 后端 | P1 |

### 7.2 非功能测试

| 用例 | 层级 | 自动化 | 优先级 |
| --- | --- | --- | --- |
| 31 天窗口 + 1 万行导出性能 | 性能 | 手工 | P1 |
| EXPORT_MAX_ROWS 截断 | 集成 | 后端 | P1 |

### 7.3 查询结果一致性

| 用例 | 说明 | 优先级 |
| --- | --- | --- |
| 同条件 page 与 export 行集一致 | **BR-OP-FDT-016** | P0 |
| 与日结 payment 集合无交集要求 | 日结看未交账；本页看已结算明细 | P1 |

### 7.4 集成测试

| 用例 | 说明 | 优先级 |
| --- | --- | --- |
| 挂号+收费+退费各一条 | 四类大类均可见 | P0 |
| 医保 payment 分摊 | 医保+自费列非零且和=实收 | P0 |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| 应用层未成码 | IF-01～03 **HTTP 集成测试阻塞**，待 `OutpatientFeeDetailAppService` 落地 |
| 领域读已就绪 | `ChargeItemQueryService` / `PaymentReconciliationQueryService` / `OutpatientFeeDetailQuerysupport 可单测或 Testcontainers 集成 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.4 | 2026-10-06 | **BR-OP-FDT-018** / §1.3.1 优惠/实收/折后口径；对齐 AS v1.10 |
| v1.3 | 2026-08-24 | **BR-OP-FDT-002**：白名单 `SETTLED`/`REFUNDING`/`REFUNDED`；对齐 AS v1.9；Loader 类名纠正 |
| v1.2 | 2026-08-12 | **BR-OP-FDT-017**：细分类硬筛/fin_type 解析不要求投影含 unit_price；对齐 AS v1.6 |
| v1.1 | 2026-06-21 | 对齐 AS v1.1；领域读 **GAP-OP-FDT-001～002 已关闭**；`OutpatientFeeDetailLoader` 更正为 `OutpatientFeeDetailQuerysupport；`map*` 两参签名 |
| v1.0 | 2026-06-21 | 首版 SDD；对齐 AS-OP-FIN-005 v1.0；三 IF；财务金额列 DTO；领域读契约目标态（GAP-OP-FDT-001～002）；应用层 `outpatientFeeDetail` 包 |
