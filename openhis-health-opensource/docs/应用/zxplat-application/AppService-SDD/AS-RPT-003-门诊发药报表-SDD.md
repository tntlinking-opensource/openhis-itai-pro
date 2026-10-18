# 门诊发药报表 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-003-SDD · **版本**：v1.15 · **日期**：2026-09-02
> **编排权威**：`AS-RPT-003-门诊发药报表.md`（v1.15）
> **领域权威**：`DM-MED-002`、`DM-MED-001`、`DM-MED-003`、`DM-ADM-011`、`DM-WKF-006`  
> **说明**：纯读；两路 = 患者发退药 ∪ 科室药品领用/退领；**禁止**在本文件写 Gap。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊发药报表（汇总/明细/导出） |
| **边界** | 改造现发退药记录并并入科室**药品**领用/退领；不写发退药/领用制单；不碰已废止用药明细页/耗材行 |
| **编排权威** | AS-RPT-003 §2/§4 |
| **方法权威** | DM-MED-002 §6.2.18 / §6.2.22；DM-WKF-006 §6.2.15（领用）；两路并集应用层 |
| **严格对齐** | IF-RPT-OMD-01～04 |
| **实现阶段口径** | **目标态全量契约** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientMedicationDispenseReport/{methodName}` |
| **事务** | 纯读；AppService **禁止** `@Transactional` |
| **时间** | Request `dateFrom`/`dateTo`（`String`；`yyyy-MM-dd HH:mm:ss`，兼容仅日期）→ `ReportDateTimeWindowSupport` 闭区间时刻；锚点 `dispense_time` / 领用业务时间；Query.`startDate`/`endDate` |
| **场景** | 门诊药品发放；同 AS-OP-PHA-004 场景谓词 |
| **价装载** | 患者侧：售价=`ChargeItem`（退药无自身账单时 `PharmacyDispenseRecordChargePricingAssembler#supplementRefundPricingFromOriginMedicationRequests` 补原正向再取负；**汇总** `assemblePatientDetailRowsForSummaryPricing` 与明细同路径）；进价=`HistoricalChargeItemPurchasePriceSupport` + `ChargeItemPackageUnitRatioSupport#computePurchaseLineAmount`（批号 `LOT_NUMBER_COST`，缺则同批号 `LOT_NUMBER_PRICE`；有批号禁止目录 PURCHASE；按 `quantity_unit` 分支）。领用侧：进价=Supply 落库价；售价空（DD §4）；退侧负。**无实收列** |
| **账批质检** | 仅明细分页：`MedicationDispenseReportPricingEnricher#applyChargeDispenseLotMismatch` → `chargeLotNumber`/`lotMismatch`；账批来自 `HistoricalChargeItemPurchasePriceSupport#mapChargeAnchorLotByDispenseId`（仅 definitionId IN，不读发放表）；**不改正额**；汇总路径禁止调用 |
| **来源码** | `PATIENT_DISPENSE` / `DEPT_ISSUE` |
| **Supply 白名单** | 药品行：`DEPT_ISSUE`、`DEPT_ISSUE_RETURN`（`item_table`=药品定义表）；读方法 `pageDeptIssueReportLines` / `listDeptIssueReportLines` / `summarizeDeptIssueReport` |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/report/medicationdispense/outpatient/
  service/OutpatientMedicationDispenseReportAppService.java
  request/
    LoadOutpatientMedicationDispenseReportPageInitRequest.java
    SummarizeOutpatientMedicationDispenseReportRequest.java
    PageOutpatientMedicationDispenseReportDetailRequest.java
    ExportOutpatientMedicationDispenseReportRequest.java
  response/
    LoadOutpatientMedicationDispenseReportPageInitResponse.java
    SummarizeOutpatientMedicationDispenseReportResponse.java
    PageOutpatientMedicationDispenseReportDetailResponse.java
    ExportOutpatientMedicationDispenseReportResponse.java
  dto/
    OutpatientMedicationDispenseSummaryRowDto.java
    OutpatientMedicationDispenseDetailRowDto.java
    OutpatientMedicationDispenseExportRowDto.java
  mapper/
    OutpatientMedicationDispenseReportMapper.java
    OutpatientMedicationDispenseReportMapperImpl.java
  support/
    OutpatientMedicationDispenseReportQuerySupport.java
```

> 本包已承接报表 IF-01～04；既有 `OutpatientPharmacyDispenseRecordAppService` 仍保留发退药记录页契约，稳定期内并行。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate |
| `LocationQueryService` | 药房下拉 |
| `PractitionerQueryService` | 发药人 |
| `TerminologyOptionLoader` | `medication_category_code` |
| `MedicationDispenseQueryService` | 患者侧明细分页/列表；汇总 |
| `SupplyRequestQueryService` | 科室药品领用/退领分页与汇总 |
| `OutpatientMedicationDispenseReportQuerySupport` | 两路预解析、合并与价装载编排（B.6.4） |
| `PharmacyDispenseRecordChargePricingAssembler` | 患者侧退药原单价补充（经 QuerySupport） |
| `OutpatientMedicationDispenseReportMapper` | 映射 |
| `AnnotatedExcelExporter` | 导出 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 记录类型 | `PharmacyDispenseRecordConstants.RECORD_TYPE_*` | DM-MED-002 发放状态/枚举 | 全部/发药/退药 |
| 药品类型 | `SummarizeOutpatientMedicationDispenseReportRequest.medicationCategoryCodes` | DM-MED-001 `category_code` | 多选筛 |
| 汇总维度 | `SummarizeOutpatientMedicationDispenseReportRequest.summaryDimension` | AS-RPT-003 §1.3 | `PHARMACY`/`ITEM`/`CATEGORY`/`DEPARTMENT`（见 `MedicationDispenseReportConstants`） |
| 来源 | `PageOutpatientMedicationDispenseReportDetailRequest.sourceCode` | AS-RPT-003 §1.3 | ALL / PATIENT_DISPENSE / DEPT_ISSUE |
| 领用类型 | Supply `type_enum` | DM-WKF-006；AS-INV-DEP-004/009 | DEPT_ISSUE / DEPT_ISSUE_RETURN |

### 2.4 领域方法白名单

| 聚合/服务 | 方法 | 用于 IF |
| --- | --- | --- |
| `MedicationDispenseQueryService` | `pageMedicationDispenseRecords` | 03、04 患者侧 |
| `MedicationDispenseQueryService` | `listMedicationDispenseRecords` | 04 患者侧导出 |
| `SupplyRequestQueryService` | `pageDeptIssueReportLines` / `listDeptIssueReportLines`（`item_table`=药品定义；门诊域由应用层预写科室药房 scope） | 03、04 领用侧 |
| `MedicationDispenseQueryService` | `summarizeMedicationDispenseReport` | 02、04 患者侧 |
| `SupplyRequestQueryService` | `summarizeDeptIssueReport`（应用层与患者侧按维键合并） | 02、04 领用侧 |
| `LocationQueryService` | `listAllActivePharmacies` / `listAllActiveDepartmentPharmacies` | 01；领用 scope |
| `ChargeItemQueryService` | `listChargeItemsForMedicationDispensePricing` | 03、04 |

---

## 3. 按接口 ID 命名展开

### 3.1 IF-RPT-OMD-01 `loadOutpatientMedicationDispenseReportPageInit`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientMedicationDispenseReport/loadOutpatientMedicationDispenseReportPageInit` |
| **Request** | 空体或租户上下文 |
| **Response** | `defaultDateFrom/To`（当日 `yyyy-MM-dd HH:mm:ss`，`00:00:00`/`23:59:59`）；`pharmacyOptions`；`categoryOptions`；`practitionerOptions`；`recordTypeOptions`；`summaryDimensionOptions`；`sourceOptions`；**`feeNatureOptions`** |
| **编排序列** | 1 Gate → 2 药房 → 3 术语 → 4 发药人 → 5 Asm |

### 3.2 IF-RPT-OMD-02 `summarizeOutpatientMedicationDispenseReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientMedicationDispenseReport/summarizeOutpatientMedicationDispenseReport` |
| **Request** | `dateFrom`/`dateTo`（`String`；`yyyy-MM-dd HH:mm:ss`，兼容仅日期；`ReportDateTimeWindowSupport`）、药房、类型、发药人、关键字、`summaryDimension`、记录类型、`sourceCode`、**`feeNatureContractNo`** |
| **Response** | `rows[]`（维度键、名称、净数量、进/售价净额；**品规维另含 `medicationCode`、`minUnitQuantity`、`packageUnitName`、`minUnitName`**）；`totalQuantity`；`purchaseTotalAmount` / `saleTotalAmount`（患者侧净金额本期可空，页脚仅累加非空） |
| **编排序列** | 1 Gate → 2 校验时间窗 → 3 Support 预解析 → 4 患者侧 `summarizeMedicationDispenseReport` + 领用侧 `summarizeDeptIssueReport` → 5 合并维键 Asm → 6 Response |

### 3.3 IF-RPT-OMD-03 `pageOutpatientMedicationDispenseReportDetail`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientMedicationDispenseReport/pageOutpatientMedicationDispenseReportDetail` |
| **Request** | 同筛选 + `pageNo`/`pageSize` |
| **Response** | 分页 `content` 明细行（含 **费用性质**、**医疗类别**；科室领用无 encounter 留空） |
| **编排序列** | 1 Gate → 2 校验 → 3 预解析 → 4 按来源：`pageMedicationDispenseRecords` 和/或 `pageDeptIssueReportLines` → 5 合并标来源排序 → 6 价装载 → 7 Asm |

### 3.4 IF-RPT-OMD-04 `exportOutpatientMedicationDispenseReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientMedicationDispenseReport/exportOutpatientMedicationDispenseReport` |
| **Request** | 同筛选 + `presentationMode`=`SUMMARY`\|`DETAIL` |
| **Response** | xlsx 二进制元数据 |
| **编排序列** | 1 Gate → 2 按 mode 走 02 全量或 03 列表上限 → 3 映射并集 ExportRowDto → 4 `AnnotatedExcelExporter` + 前端 `exportColumns` |
| **列口径** | 列真源在前端（R-EXCEL-4）；单 ExportRowDto 作 `@Excel` 白名单；禁止汇总/明细各造一套 Export DTO |

---

## 4. QueryService 与领域事件

| 读方法 | 语义 |
| --- | --- |
| `pageMedicationDispenseRecords` | 门诊发退药明细（已有） |
| `summarizeMedicationDispenseReport` | 患者侧净汇总（数量；金额应用层可选 enrich） |

**无写事件。**

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 非功能性需求实现（收敛为承诺表）

| 承诺 | 实现 |
| --- | --- |
| 纯读 | 无 `@Transactional` |
| 导出上限 | `PharmacyDispenseRecordExportConstants.EXPORT_MAX_ROWS`（与发退药记录对齐） |
| 性能 | 条件下推；禁止全表内存汇总。汇总进销价：**禁止**完整明细展示 enrichment；`summarize*WithSlips`/`WithLines` 一次扫描 + `ID_IN_QUERY_CHUNK_SIZE` 分块定价累加；**禁止**数量汇总后再二次扫描同一窗；**禁止**单次 `list*` 触顶 `EXPORT_MAX_ROWS` 静默截断页脚。CATEGORY：ITEM 分桶后同窗 slip 全量映射类型。来源=全部明细分页：两路前缀页合并切片 |

---

## 7. 测试要点

| 用例 | 期望 |
| --- | --- |
| 明细回归 | 与 AS-OP-PHA-004 一致 |
| 汇总净 | 发−退 |
| 维度 | 药房/品规/类型 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.15 | 2026-09-02 | 时间窗：`dateFrom`/`dateTo` 支持 `yyyy-MM-dd HH:mm:ss`（兼容仅日期）；`defaultDateFrom/To` 当日全日；对齐 AS v1.15 |
| v1.14 | 2026-09-02 | 账批质检：明细分页 `chargeLotNumber`/`lotMismatch`；`mapChargeAnchorLotByDispenseId`；对齐 AS v1.14 |
| v1.13 | 2026-08-28 | 导出改前端 `exportColumns` + 单 ExportRowDto 白名单（R-EXCEL-4/6）；对齐 util |
| v1.12 | 2026-08-28 | 导出拆 Summary/Detail ExportRowDto；汇总 omit 全空列；对齐 R-EXCEL-4 |
| v1.11 | 2026-08-28 | 品规汇总增 `packageUnitName`/`minUnitName`；对齐 AS v1.11 |
| v1.10 | 2026-08-28 | 品规汇总增 `medicationCode`/`minUnitQuantity`；对齐 AS v1.10 |
| v1.9 | 2026-08-24 | 汇总一次扫描 `WithSlips`/`WithLines` + IN 分块累加；CATEGORY 同窗全量映射；对齐 AS v1.8 |
| v1.8 | 2026-08-24 | 汇总金额按页拉齐定价明细；禁止 `EXPORT_MAX_ROWS` 静默截断页脚；对齐 AS v1.7 |
| v1.7 | 2026-08-17 | 汇总退药售价与明细同补原正向再取负；对齐 AS v1.6 / DD v1.10 |
| v1.6 | 2026-08-12 | 患者侧进价按 quantity_unit 分支（ChargeItemPackageUnitRatioSupport）；领用售价空；对齐 AS v1.5 / DD v1.2 |
| v1.5 | 2026-08-03 | 进/售价纠偏：批号 `LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`；领用侧禁止目录 SALE；对齐 AS v1.4 |
| v1.4 | 2026-08-02 | **费用性质**展示跟发放收费行 `account_id`；筛选改为就诊任一账户（对齐 AS v1.3） |
| v1.3 | 2026-07-28 | **费用性质** 筛选（`feeNatureContractNo`）；init `feeNatureOptions`；明细/导出列 **费用性质、医疗类别** |
| v1.2 | 2026-07-21 | 依赖表对齐成码：`ChargePricingAssembler` + QuerySupport；白名单方法名与实现一致 |
| v1.1 | 2026-07-21 | 并入科室药品领用/退领；来源筛；汇总加 DEPARTMENT |
| v1.0 | 2026-07-21 | 首版目标态 |
