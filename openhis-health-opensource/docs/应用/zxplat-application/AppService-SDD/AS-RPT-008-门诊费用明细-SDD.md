# 门诊费用明细 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-008-SDD · **版本**：v1.13 · **日期**：2026-10-06  
> **编排权威**：`AS-RPT-008-门诊费用明细.md`（**v1.17**）  
> **前身实现**：明细 IF 门面委托 `outpatient/financial/feedetail`；报表入口迁 `report/feedetail/outpatient`

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊费用明细（汇总+明细） |
| **边界** | 明细复用 OP-FIN-005；汇总 Tab 多维净额（`summaryDimension`） |
| **严格对齐** | IF-RPT-OFD-01～05（含明细分页进价列角 IF-05；DD §2.4） |
| **实现阶段口径** | **应用层已落地**：`OutpatientFeeDetailReportAppService` |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientFeeDetailReport/{methodName}` |
| **明细** | 同 `AS-OP-FIN-005-SDD`（门面委托 `OutpatientFeeDetailAppService`）；ChargeItem 白名单 `SETTLED`/`REFUNDING`/`REFUNDED` |
| **汇总** | 按所选维：**数量**、**进价单价/售价单价**（批号/账单同源，同桶多价不一致留空）、**进价合计/售价合计**（加和）；收退强制 ALL（BR-RPT-OFD-003）。维：`FIN_TYPE`（默认）/ `DEPARTMENT` / `FEE_CATEGORY` / `ITEM`；**不展示行数** |
| **明细金额列** | 进价单价/总价、售价单价/总价；**已配置有效折扣**时另展示优惠金额/实收金额（优惠=`|total_price|−|discount_amount|`，实收=`discount_amount`；**BR-RPT-OFD-008**）；药耗进价=批号 LOT_NUMBER_COST（同批号回放；有批号禁止目录 PURCHASE；**行额按 quantity_unit 分支**→`ChargeItemPackageUnitRatioSupport`）；**诊疗/挂号无进价**（对齐 AS / DD-进售价 v1.2） |
| **时间窗** | Request `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；`ReportDateTimeWindowSupport` 规范为闭区间时刻（仅日期时起补 `00:00:00`、止补 `23:59:59`）；init `defaultDateFrom/To`=当日全日；前端 `datetimerange` |

---

## 2. 代码结构

### 2.1 包路径

```text
.../application/report/feedetail/outpatient/
  service/OutpatientFeeDetailReportAppService.java
  request/Load* / Summarize* / Page* / Export*
  response/ mapper(+Impl)
.../application/report/feedetail/
  dto/FeeDetailReportSummaryRowDto.java
  dto/FeeDetailReportSummaryFooterDto.java
  support/FeeDetailReportFinTypeSummaryAssembler.java
.../domain/share/constants/FeeDetailReportConstants.java
```

> 明细：门面委托既有 `OutpatientFeeDetailAppService`；汇总：本包 + 共享 Assembler。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate |
| `OutpatientFeeDetailAppService` | IF-01/03/04 DETAIL 门面 |
| `OutpatientFeeDetailLoader` | IF-02 scope 预解析 |
| `ChargeItemQueryService` | `summarizeOutpatientFeeDetailByFinType`（definition 分桶 + cachedChargeRows） |
| `PaymentEntererMatchResolver` | 收费人 enterer 白名单（仅 Practitioner 主键，**BR-OPC-016**） |
| `TerminologyOptionLoader` | fin_type 译文（FIN_TYPE 维） |
| `FeeDetailReportFinTypeSummaryAssembler` | `assembleSummaryRows` 多维分桶 + 页脚 |
| `AnnotatedExcelExporter` | 汇总导出 |
| `OutpatientFeeDetailReportMapper` | 映射 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 收退方向 | `PageOutpatientFeeDetailReportRequest.tradeDirection` | AS-OP-FIN-005 | ALL/CHARGE/REFUND（明细）；汇总强制 ALL |
| fin_type | `DailySettlementFinTypeAmountLoader` ← definition.type_code；硬筛投影含 definition_id/generateSource/serviceTable、**不含** unit_price（**BR-RPT-OFD-006** / **BR-OP-FDT-017**） | DM-ADM-010 | FIN_TYPE 维；禁止当前 ACTIVE 目录价；禁止因投影无单价失败 |
| summaryDimension | `FeeDetailReportConstants.SUMMARY_DIMENSION_*`；init `summaryDimensionOptions` | AS-RPT-008 | FIN_TYPE/DEPARTMENT/FEE_CATEGORY/ITEM；非法 → EX-RPT-OFD-102 |
| Tab | `ExportOutpatientFeeDetailReportRequest.presentationMode` | AS-RPT-008 | SUMMARY/DETAIL |
| 异常码 | `OutpatientFeeDetailReportConstants.EX_RPT_OFD_*` | AS-RPT-008 §5 | 101～104 |

### 2.4 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `ChargeItemQueryService` | `summarizeOutpatientFeeDetailByFinType` | 02、04 SUMMARY |
| `OutpatientFeeDetailAppService` | `loadOutpatientFeeDetailPageInit` / `pageOutpatientFeeDetail` / `exportOutpatientFeeDetail` | 01、03、04 DETAIL |
| （经 Loader）`PaymentReconciliationQueryService` | `listOutpatientFeeDetailChargeItemIdsByPaymentFilter` | 02 |
| （经前身）`ChargeItemQueryService` | `pageOutpatientFeeDetailEncounterBlocks` / `summarizeOutpatientFeeDetailAmounts` | 03、04 DETAIL |

---

## 3. 按接口 ID 命名展开

| IF | HTTP methodName |
| --- | --- |
| 01 | `loadOutpatientFeeDetailReportPageInit` |
| 02 | `summarizeOutpatientFeeDetailReport` |
| 03 | `pageOutpatientFeeDetailReport` |
| 04 | `exportOutpatientFeeDetailReport` |

**汇总编排序列**：Gate（登录+时间窗+`summaryDimension` 白名单）→ Mapper（强制 ALL）→ `OutpatientFeeDetailLoader#prepareOutpatientFeeDetailSummarizeScopesFromChargeBill` → `summarizeOutpatientFeeDetailByFinType`（缓存 chargeRows）→ `FeeDetailReportFinTypeSummaryAssembler#assembleSummaryRows` → Asm Response。  
**明细**：Gate → Mapper → 门面委托前身 → Asm 包装 Response。

---

## 4. QueryService 与领域事件

明细/汇总读侧已落地。**无写事件。**

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 非功能性需求实现（收敛为承诺表）

纯读；导出行上限 `OutpatientFeeDetailReportConstants.EXPORT_MAX_ROWS`（50000）。汇总/导出可走全日窗候选路径，不得拖慢明细分页（AS §7）。

---

## 7. 测试要点

明细回归 OP-FIN-005；汇总与明细同筛选代数一致；退费冲减汇总；切换 `summaryDimension` 分桶键与展示名正确。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.13 | 2026-10-06 | 对齐 AS v1.17：IF-03 分页响应同步 `discountFeatureEnabled` |
| v1.12 | 2026-10-06 | 对齐 AS v1.16：折扣相关列显隐只认是否已配置 ACTIVE 折扣内容 |
| v1.11 | 2026-10-06 | 对齐 AS v1.15：**BR-RPT-OFD-008** 明细优惠/实收列；init `discountFeatureEnabled` |
| v1.10 | 2026-09-02 | 时间窗：`dateFrom`/`dateTo` 支持 `yyyy-MM-dd HH:mm:ss`（兼容仅日期）；默认当日 `00:00:00～23:59:59`；对齐 AS v1.14 |
| v1.9 | 2026-08-24 | 对齐 AS v1.13：ChargeItem 白名单含 `REFUNDING`（退费中计已收） |
| v1.8 | 2026-08-12 | 对齐 AS v2.1：进价按 quantity_unit 分支（ChargeItemPackageUnitRatioSupport） |
| v1.7 | 2026-08-12 | 对齐 AS v2.0：**BR-RPT-OFD-006** / 硬筛投影不含 unit_price；`mapFinTypeCodeByChargeItemId` 仅校 definition_id |
| v1.6 | 2026-08-11 | 对齐 AS v1.9 多维汇总（`summaryDimension` / `summaryDimensionOptions`） |
| v1.5 | 2026-08-06 | 诊疗/挂号进价=账单价；对齐 AS v1.5 / AS-OP-FIN-005 v1.5 |
| v1.0 | 2026-07-21 | 首版：加汇总 |
| v1.4 | 2026-08-03 | 药耗进价纠偏：批号 LOT_NUMBER_COST；有批号禁止目录 PURCHASE |
| v1.3 | 2026-08-02 | **费用性质** 行级 `ChargeItem.account_id→Account.contract_no`；筛选同口径；对齐 **AS v1.3** / 双账户转自费 |
| v1.2 | 2026-07-28 | **费用性质** 筛选（`feeNatureContractNo`）；init `feeNatureOptions`；就诊块列 **费用性质、医疗类别** |
| v1.1 | 2026-07-21 | 对齐已落地包路径/依赖/白名单；实现阶段改为已落地 |
