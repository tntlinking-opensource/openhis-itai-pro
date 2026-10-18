# 住院发药报表 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-004-SDD · **版本**：v1.13 · **日期**：2026-09-02
> **编排权威**：`AS-RPT-004-住院发药报表.md`（v1.14）
> **说明**：与 AS-RPT-003-SDD 同构；场景 IMP + 住院科室药品领用。原发药管理「住院患者药品使用」页已废止，勿混其计划发药时间/待发口径。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院发药报表 |
| **边界** | 患者发退 + 科室**药品**领用/退领；时间轴 `dispense_time`；不含待发 |
| **严格对齐** | IF-RPT-IMD-01～04 |
| **实现阶段口径** | **目标态全量契约** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/inpatientMedicationDispenseReport/{methodName}` |
| **场景** | 住院药品；患者发退 + 科室领用（权威见 AS-RPT-004） |
| **价** | `PharmacyDispenseRecordChargePricingAssembler` + `InpatientMedicationDispenseChargePricingLoader`（待结算补充）；领用侧 Supply 落库价 |
| **账批质检** | 同 AS-RPT-003-SDD：明细分页 `chargeLotNumber`/`lotMismatch`；汇总不做 |
| **时间窗** | 同 AS-RPT-003-SDD：Request `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；`ReportDateTimeWindowSupport`；默认当日 `00:00:00～23:59:59` |

---

## 2. 代码结构

### 2.1 包路径

```text
.../application/report/medicationdispense/inpatient/
  service/InpatientMedicationDispenseReportAppService.java
  request/（Load* / Summarize* / Page*Detail / Export*）
  response/（对应）
  dto/（SummaryRow / DetailRow / SummaryExportRow / DetailExportRow）
  mapper/InpatientMedicationDispenseReportMapper.java + Impl
  support/InpatientMedicationDispenseReportQuerySupport.java
```

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate |
| `LocationQueryService` / `PractitionerQueryService` / `TerminologyOptionLoader` | init |
| `MedicationDispenseQueryService` | 患者侧 |
| `SupplyRequestQueryService` | 住院药品领用/退领 |
| `InpatientMedicationDispenseReportQuerySupport` | 两路预解析、合并与价装载编排（B.6.4） |
| `InpatientMedicationDispenseChargePricingLoader` | 住院待结算价补充（经 QuerySupport） |
| `PharmacyDispenseRecordChargePricingAssembler` | 患者侧退药原单价补充（经 QuerySupport） |
| `AnnotatedExcelExporter` | 导出 |
| `InpatientMedicationDispenseReportMapper` | 映射 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 记录类型 | `PharmacyDispenseRecordConstants.RECORD_TYPE_*` | DM-MED-002 | 发/退 |
| 汇总维度 | `SummarizeInpatientMedicationDispenseReportRequest.summaryDimension` | AS-RPT-004 §1.3 | 同门诊四维（`MedicationDispenseReportConstants`） |
| 就诊类别 | `Encounter.class_enum=IMP` | DM-ADM-002 | 场景 |

### 2.4 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `MedicationDispenseQueryService` | `pageMedicationDispenseRecords` / `listMedicationDispenseRecords` | 03、04 |
| `SupplyRequestQueryService` | `pageDeptIssueReportLines` / `listDeptIssueReportLines` / `summarizeDeptIssueReport` | 02～04 领用侧 |
| `MedicationDispenseQueryService` | `summarizeMedicationDispenseReport`（应用层与领用侧合并） | 02、04 |
| `LocationQueryService` | `listAllActivePharmacies` / `listAllActiveDepartmentPharmacies` | 01；领用 scope |
| `ChargeItemQueryService` + `InpatientMedicationDispenseChargePricingLoader` + `HistoricalChargeItemPurchasePriceSupport` + `ChargeItemPackageUnitRatioSupport` | 患者侧售价/进价（批号；按 quantity_unit 分支）；领用侧 Supply 进价、售价空 | 03、04 |

---

## 3. 按接口 ID 命名展开

| IF | HTTP methodName | 要点 |
| --- | --- | --- |
| IF-RPT-IMD-01 | `loadInpatientMedicationDispenseReportPageInit` | 同门诊 init（含 **`feeNatureOptions`**） |
| IF-RPT-IMD-02 | `summarizeInpatientMedicationDispenseReport` | 净汇总；Request 含 **`feeNatureContractNo`** |
| IF-RPT-IMD-03 | `pageInpatientMedicationDispenseReportDetail` | 含住院号床号；明细 **费用性质/医疗类别** |
| IF-RPT-IMD-04 | `exportInpatientMedicationDispenseReport` | `presentationMode` |

编排序列：Gate → 校验 → Support → Query → 价装载 → Asm / Excel。

---

## 4. QueryService 与领域事件

明细复用既有分页；汇总经 `summarizeMedicationDispenseReport` ∪ `summarizeDeptIssueReport` 应用层合并。**无写事件。**

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 非功能性需求实现（收敛为承诺表）

纯读；导出上限；与已废止用药明细页 API **分路径**（本报表走 `inpatientMedicationDispenseReport`）。汇总金额按页拉齐定价明细，**禁止** `EXPORT_MAX_ROWS` 静默截断页脚（同 AS-RPT-003-SDD §6）。

---

## 7. 测试要点

| 用例 | 期望 |
| --- | --- |
| 隔离 | 计划发药日/待发不入本报表 |
| 净汇总 | 退药冲减 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.13 | 2026-09-02 | 时间窗：`dateFrom`/`dateTo` 支持 `yyyy-MM-dd HH:mm:ss`（兼容仅日期）；默认当日全日；对齐 AS v1.14 |
| v1.12 | 2026-09-02 | 账批质检对齐 AS-RPT-004 v1.13 / AS-RPT-003-SDD v1.14 |
| v1.11 | 2026-08-28 | 导出改前端 `exportColumns` + 单 ExportRowDto 白名单；对齐 AS-RPT-003-SDD v1.13 / R-EXCEL-4/6 |
| v1.10 | 2026-08-28 | 导出拆 Summary/Detail ExportRowDto；汇总 omit 全空列；对齐 R-EXCEL-4 / AS-RPT-003-SDD v1.12 |
| v1.9 | 2026-08-28 | 品规汇总增包装/最小单位名；对齐 AS-RPT-004 v1.12 / AS-RPT-003-SDD v1.11 |
| v1.8 | 2026-08-28 | 品规汇总增药品编码/最小数量；对齐 AS-RPT-004 v1.11 / AS-RPT-003-SDD v1.10 |
| v1.7 | 2026-08-24 | 汇总金额按页拉齐；禁止 `EXPORT_MAX_ROWS` 静默截断页脚；对齐 AS-RPT-004 v1.9 / AS-RPT-003-SDD v1.8 |
| v1.6 | 2026-08-17 | 汇总退药售价与明细同补原正向再取负；对齐 AS-RPT-004 v1.8 / AS-RPT-003-SDD v1.7 |
| v1.5 | 2026-08-12 | 对齐 AS-RPT-003 v1.5：进价按操作单位分支；领用售价空 |
| v1.4 | 2026-08-03 | 进/售价纠偏：批号价；领用侧禁止目录 SALE；对齐 AS-RPT-003-SDD v1.5 |
| v1.3 | 2026-07-28 | **费用性质** 筛选与明细/导出列（对齐 AS-RPT-003-SDD v1.3） |
| v1.2 | 2026-07-21 | 依赖表对齐成码：`ChargePricingAssembler` + QuerySupport；去掉过期 Support 类名 |
| v1.1 | 2026-07-21 | 并入科室药品领用/退领 |
| v1.0 | 2026-07-21 | 首版 |
