# 住院费用明细 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-009-SDD · **版本**：v1.12 · **日期**：2026-10-08  
> **编排权威**：`AS-RPT-009-住院费用明细.md`（**v1.14**）  
> **隔离**：禁止直接复用护士站 `nurseStationCostDetail` IF。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院费用明细报表 |
| **边界** | 对齐 AS-RPT-008：就诊块明细 + 多维净汇总 |
| **严格对齐** | IF-RPT-IFD-01～05（含明细分页进价列角 IF-05；DD §2.4） |
| **实现阶段口径** | **应用层已落地**：`InpatientFeeDetailReportAppService` |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/inpatientFeeDetailReport/{methodName}` |
| **块分页** | 按住院 Encounter（IMP）；金额列对齐门诊费用明细（进价/售价；已配置有效折扣时另展示优惠/实收） |
| **lineMergeMode** | `LINE`（默认）/ `BY_DEFINITION`；`BY_DEFINITION` 时 `InpatientFeeDetailReportBlockAssembler` 仅对本批各就诊块内行按 **产品** `product_table+product_id+quantityUnit` 净合并（无产品回落 `definitionId+quantityUnit`；退费数量取负）；装配时同块 `chargeItemId` 去重；**禁止**为合并全窗物化；页脚/汇总/IF-05 与合并无关；`LINE` 数量=落库 `quantity_value`（不强制为 1） |
| **汇总** | 同 AS-RPT-008-SDD：**数量**、进价单价/售价单价（展示）、进价合计/售价合计；收退强制 ALL。维同 AS-RPT-008 |
| **时间窗** | 同 AS-RPT-008-SDD：Request `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；`ReportDateTimeWindowSupport`；默认当日 `00:00:00～23:59:59`；前端 `datetimerange` |

---

## 2. 代码结构

### 2.1 包路径

```text
.../application/report/feedetail/inpatient/
  service/InpatientFeeDetailReportAppService.java
  request/Load* / Summarize* / Page* / Export*
  response/ mapper(+Impl)
  support/InpatientFeeDetailReportQuerySupport.java
  assembler/InpatientFeeDetailReportBlockAssembler.java
.../application/report/feedetail/
  dto/FeeDetailReportSummaryRowDto.java
  dto/FeeDetailReportSummaryFooterDto.java
  support/FeeDetailReportFinTypeSummaryAssembler.java
.../domain/share/constants/FeeDetailReportConstants.java
```

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate |
| `InpatientFeeDetailReportQuerySupport` | scope 预解析（ChargeItem `entered_date` 窗 + 科室/患者/项目；**禁止扫 Payment**） |
| `ChargeItemQueryService` | `pageInpatientFeeDetailEncounterBlocks` / `summarizeInpatientFeeDetailByFinType` |
| `FeeDetailReportFinTypeSummaryAssembler` | `assembleSummaryRows` 多维分桶 + 页脚 |
| `InpatientFeeDetailReportBlockAssembler` | 就诊块 enrichment（金额委托 `OutpatientFeeDetailAssembler`；收费人取结算 `enterer_id`） |
| `OutpatientFeeDetailMapper` | 明细导出行映射复用 |
| `AnnotatedExcelExporter` | 导出 |
| `InpatientFeeDetailReportMapper` | 映射 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊类别 | Support 科室过滤 `EncounterClass.IMP` | DM-ADM-002 | 场景 |
| fin_type | `DailySettlementFinTypeAmountLoader` | DM-ADM-010 | FIN_TYPE 维 |
| summaryDimension | `FeeDetailReportConstants.SUMMARY_DIMENSION_*`；init `summaryDimensionOptions` | AS-RPT-009 | 同门诊四维；非法 → EX-RPT-IFD-102 |
| presentationMode | `ExportInpatientFeeDetailReportRequest` | AS-RPT-009 | SUMMARY/DETAIL |
| 异常码 | `InpatientFeeDetailReportConstants.EX_RPT_IFD_*` | AS-RPT-009 §5 | 101～104 |

### 2.4 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `ChargeItemQueryService` | `pageInpatientFeeDetailEncounterBlocks` | 03、04 DETAIL |
| `ChargeItemQueryService` | `summarizeInpatientFeeDetailByFinType` | 02、04 SUMMARY |

---

## 3. 按接口 ID 命名展开

| IF | HTTP methodName |
| --- | --- |
| 01 | `loadInpatientFeeDetailReportPageInit` |
| 02 | `summarizeInpatientFeeDetailReport` |
| 03 | `pageInpatientFeeDetailReport` |
| 04 | `exportInpatientFeeDetailReport` |

编排序列对齐门诊费用报表 SDD（含 IF-02 Gate 维度白名单 + `assembleSummaryRows`）；场景 IMP；科室下拉用 `OrganizationQueryService#listOrganizationsForInpatientAdmission`。

---

## 4. QueryService 与领域事件

读侧已落地。**无写事件。**

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 非功能性需求实现（收敛为承诺表）

纯读；导出行上限 `InpatientFeeDetailReportConstants.EXPORT_MAX_ROWS`（50000）。

---

## 7. 测试要点

与护士站费用页隔离；金额列对齐门诊；汇总冲减退费；多维 `summaryDimension` 与 AS-RPT-008 同构。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.12 | 2026-10-08 | 对齐 AS v1.14：`BY_DEFINITION` 合并键改为产品品规优先；块内 chargeItemId 去重；数量口径说明 |
| v1.11 | 2026-10-06 | 对齐 AS v1.13：IF-03 分页响应同步 `discountFeatureEnabled` |
| v1.10 | 2026-10-06 | 对齐 AS v1.12：**BR-RPT-IFD-008** 明细优惠/实收列；init `discountFeatureEnabled` |
| v1.9 | 2026-09-09 | `lineMergeMode=BY_DEFINITION`：块内按 definition 合并；对齐 AS v1.11 |
| v1.8 | 2026-09-02 | 时间窗：`dateFrom`/`dateTo` 支持 `yyyy-MM-dd HH:mm:ss`（兼容仅日期）；默认当日 `00:00:00～23:59:59`；对齐 AS v1.10 |
| v1.7 | 2026-08-12 | 纠偏：依赖表去掉 Payment QS；scope 明确 ChargeItem-only、禁止扫 Payment |
| v1.6 | 2026-08-11 | 对齐 AS v1.8 多维汇总（`summaryDimension` / `summaryDimensionOptions`） |
| v1.5 | 2026-08-06 | 诊疗/挂号进价=账单价；对齐 AS v1.5 |
| v1.0 | 2026-07-21 | 首版 |
| v1.2 | 2026-07-28 | **费用性质** 筛选与就诊块列（对齐 AS-RPT-008-SDD v1.2） |
| v1.1 | 2026-07-21 | 对齐已落地包路径/依赖/payment 白名单；实现阶段改为已落地 |
