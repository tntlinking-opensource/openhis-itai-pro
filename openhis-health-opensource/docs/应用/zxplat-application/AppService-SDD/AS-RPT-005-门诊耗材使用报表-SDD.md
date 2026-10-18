# 门诊耗材使用报表 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-005-SDD · **版本**：v1.10 · **日期**：2026-09-02  
> **编排权威**：`AS-RPT-005-门诊耗材使用报表.md`（v1.10）  
> **说明**：两路事实（DeviceDispense ∪ Supply）；汇总维耗材房/品规/类型/科室；筛选对齐发药报表（耗材关键字等）；明细/导出含 **执行人**（发耗材人）；明细分页含 **账批质检**。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊耗材使用报表 |
| **边界** | 患者发放 + 科室领用；纯读 |
| **严格对齐** | IF-RPT-OCU-01～04 |
| **实现阶段口径** | **目标态全量契约**（领域读侧 Gap 已关闭；应用层已成码） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientConsumableUsageReport/{methodName}` |
| **Supply 白名单** | `DEPT_ISSUE` / `DEPT_ISSUE_RETURN`（与 `AS-INV-DEP-004/009` 对齐） |
| **来源码** | `ALL` / `PATIENT_DISPENSE` / `DEPT_ISSUE` |
| **时间窗** | Request `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；`ReportDateTimeWindowSupport`；init `defaultDateFrom/To`=当日 `00:00:00`/`23:59:59`；前端 `datetimerange` |

---

## 2. 代码结构

### 2.1 包路径

```text
.../application/report/consumableusage/
  support/
    ConsumableUsageReportScopeLoader.java
    ConsumableUsageReportFilterLoader.java
    ConsumableUsageReportQueryFilter.java
    ConsumableUsageReportAmountEnricher.java
  outpatient/
    service/OutpatientConsumableUsageReportAppService.java
    request/Load* / Summarize* / Page*Detail / Export*
    response/对应
    dto/OutpatientConsumableUsageSummaryRowDto / DetailRowDto / ExportRowDto（并集；列由 exportColumns 裁剪）
    mapper/OutpatientConsumableUsageReportMapper.java + Impl
    support/OutpatientConsumableUsageReportQuerySupport.java
            OutpatientConsumableUsageSummaryResult.java
            OutpatientConsumableUsageDetailPageResult.java
```

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 登录 Gate |
| `PageWindowGate` | 明细分页窗口 |
| `OutpatientConsumableUsageReportQuerySupport` | 两路预解析、并集、进价/售价 enrich、执行人姓名 |
| `TerminologyOptionLoader` / `PractitionerQueryService` | init：耗材类型、发耗材人下拉 |
| `OrganizationQueryService` / `LocationQueryService` | 初始化科室/库房下拉 |
| `AnnotatedExcelExporter` | 导出 |
| `OutpatientConsumableUsageReportMapper` | 映射 |

QuerySupport 内经：`DeviceDispenseQueryService`、`SupplyRequestQueryService`、`ChargeItemQueryService`（金额）、`PractitionerQueryService`（执行人姓名）、`ConsumableUsageReportScopeLoader`、`ConsumableUsageReportFilterLoader`、`ConsumableUsageReportAmountEnricher` 等。

**账批质检**：明细分页 `enrichPatientDispenseAmounts(lines, true)` → `applyChargeDispenseLotMismatch`；账批 `mapChargeAnchorLotByDispenseId`；退耗 `basedOnDispenseId` 回挂；汇总须 `false`。

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 来源 | `*Request.sourceCode` | AS-RPT-005 §1.3 | PATIENT_DISPENSE / DEPT_ISSUE（`ALL` 拒绝） |
| 汇总维 | `Summarize*Request.summaryDimension` | AS-RPT-005 | LOCATION / ITEM / CATEGORY / DEPARTMENT |
| 记录类型 | `*Request.recordTypeCode` | AS §1.3 | ALL / DISPENSE / REFUND；仅患者侧 |
| 耗材类型 | `*Request.deviceCategoryCodes` | DM-ADM-008 `category_code` | 多选；患者+领用 |
| 耗材关键字 | `*Request.deviceKeyword` | DM-ADM-008 | → `resolvedDeviceDefIds` / `resolvedItemIds` |
| 患者关键字 | `*Request.patientKeyword` | DM-ADM-001/002 | 仅患者侧 encounter 取交 |
| 科室 | `*Request.organizationId` | AS §1.3 | 患者=开立；领用=领用科室 |
| 发耗材人 | `*Request.performerPractitionerId` | DM-WKF-007 | 仅患者侧 `performer_id` |
| 费用性质 | `*Request.feeNatureContractNo` / init `feeNatureOptions` | DM-ADM-009 §6.2.15 | 展示：发放收费行 account→contract；筛：就诊任一账户 bus_no |
| 领用类型 | Supply `type_enum` ∈ DEPT_ISSUE / DEPT_ISSUE_RETURN | DM-WKF-006 | 白名单 |
| 常量 | `ConsumableUsageReportConstants` | AS §5 EX | EX-RPT-OCU-101～104；含 RECORD_TYPE_* |

### 2.4 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `DeviceDispenseQueryService` | `pageConsumableUsageDispenseLines` / `listConsumableUsageDispenseLines` | 03、04 |
| `DeviceDispenseQueryService` | `summarizeConsumableUsageDispense` | 02、04 |
| `SupplyRequestQueryService` | `pageDeptIssueReportLines` / `listDeptIssueReportLines` | 03、04 |
| `SupplyRequestQueryService` | `summarizeDeptIssueReport` | 02、04 |

> 门诊/住院域由应用层预写 `resolvedEncounterScope`（AMB）与科室药房 `resolvedPurposeLocationIds`；`item_table=adm_device_definition`。

---

## 3. 按接口 ID 命名展开

| IF | HTTP methodName |
| --- | --- |
| 01 | `loadOutpatientConsumableUsageReportPageInit` |
| 02 | `summarizeOutpatientConsumableUsageReport` |
| 03 | `pageOutpatientConsumableUsageReportDetail` |
| 04 | `exportOutpatientConsumableUsageReport` |

**编排序列（02/03）**：Gate → 校验时间窗 → Support 预解析（科室/库位/费用性质 encounter 收窄 + **品规/患者关键字/记录类型/执行人**）→ 单源 Query → 标来源 → enrich **费用性质/medType** → Charge 售价 + 批号进价；领用侧批号 LOT_NUMBER_PRICE → Asm。  
**init（01）**：`defaultDateFrom/To`（当日 `yyyy-MM-dd HH:mm:ss`，`00:00:00`/`23:59:59`）；`summaryDimensionOptions`；`sourceCodeOptions`；`organizationOptions`；`locationOptions`；`feeNatureOptions`；**`categoryOptions`**；**`practitionerOptions`**；**`recordTypeOptions`**。  
**Request 筛选（02/03/04）**：`dateFrom`/`dateTo`（`String`；`yyyy-MM-dd HH:mm:ss`，兼容仅日期）、来源、库位、费用性质、**`recordTypeCode`**、**`deviceCategoryCodes`**、**`performerPractitionerId`**、**`deviceKeyword`**、**`patientKeyword`**、**`organizationId`**。  
**明细 Asm（03/04 DETAIL）**：`DetailRowDto.performerId` / `performerName`——患者发放自 `ConsumableUsageDispenseLineQueryModel.performerId`；科室领用自 `DeptIssueReportLineQueryModel.approverId`；姓名经 `PractitionerQueryService#findAllByIds` 批量回填；缺省留空。  
**导出行（04）**：单并集 `ExportRowDto` 作 `@Excel` 白名单；前端 `exportColumns` 裁剪列头（R-EXCEL-4/6）；明细含「执行人」。
**导出（04）**：仅当前 Tab（SUMMARY / DETAIL）；行上限 `EXPORT_MAX_ROWS`；**禁止**为汇总/明细各造一套 Export DTO。

---

## 4. QueryService 与领域事件

目标态两路读；**无写事件**。

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 非功能性需求实现（收敛为承诺表）

纯读；导出上限；两路查询可并行。

| 承诺 | 实现 |
| --- | --- |
| 汇总金额 | `listDetailForSummaryAmounts` 按页拉齐 + `enrichDetailAmountsOnly`（仅 orgId + 进售价）；**禁止** `listDetailForExport` 展示 enrichment；**禁止**单次触顶 `EXPORT_MAX_ROWS` 静默截断页脚 |
| 就诊 scope | `listEncounterIdsForNurseWorkloadScope(class, from, to)` 日窗相交 |
| 并集明细分页 | 两路前缀页（`pageNo×pageSize`）+ `mergeAndSlice`；**禁止**每次拉满 EXPORT_MAX_ROWS |

---

## 7. 测试要点

| 用例 | 期望 |
| --- | --- |
| 仅领用 | 无患者行仍进科室汇总 |
| 汇总维 | 无西药/中药主维 |
| 执行人 | 患者发放回显 performer；科室领用回显 approver；空值不报错 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.10 | 2026-09-02 | 时间窗：`dateFrom`/`dateTo` 支持 `yyyy-MM-dd HH:mm:ss`（兼容仅日期）；`defaultDateFrom/To` 当日全日；对齐 AS v1.10 |
| v1.9 | 2026-09-02 | 账批质检：明细分页 `chargeLotNumber`/`lotMismatch`；对齐 AS-RPT-005 v1.9 |
| v1.8 | 2026-08-28 | 导出改前端 `exportColumns` + 单 ExportRowDto 白名单（R-EXCEL-4/6） |
| v1.7 | 2026-08-28 | 导出拆 Summary/Detail ExportRowDto；汇总 omit 全空列（R-EXCEL-4） |
| v1.6 | 2026-08-24 | 汇总金额按页拉齐；禁止 `EXPORT_MAX_ROWS` 静默截断页脚；对齐 AS-RPT-005 v1.7 |
| v1.5 | 2026-08-12 | 筛选对齐发药报表：FilterLoader + QueryFilter；init/Request 补关键字·类型·记录类型·执行人·科室 |
| v1.4 | 2026-08-03 | 进/售价纠偏：批号 LOT_NUMBER_COST/PRICE；领用侧禁止目录 SALE |
| v1.3 | 2026-08-01 | 明细/导出 **执行人**：患者侧 `performerId`、科室侧 `approverId`；`PractitionerQueryService` 批量姓名 |
| v1.2 | 2026-07-28 | **费用性质** 筛选；init `feeNatureOptions`；明细/导出列 **费用性质、医疗类别** |
| v1.1 | 2026-07-21 | 白名单对齐 AS Gap 关闭方法名；补共用 support 与导出口径 |
| v1.0 | 2026-07-21 | 首版 |
| v1.1 | 2026-07-21 | 白名单对齐 AS Gap 关闭方法名；补共用 support 与导出口径 |
