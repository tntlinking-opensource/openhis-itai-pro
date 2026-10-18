# 住院耗材使用报表 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-006-SDD · **版本**：v1.11 · **日期**：2026-09-09  
> **编排权威**：`AS-RPT-006-住院耗材使用报表.md`（v1.11）  
> **说明**：与 AS-RPT-005-SDD 同构；住院域（IMP）；筛选对齐发药报表；明细含住院号/病区/床号、**执行人**（发耗材人）；明细分页含 **账批质检**；可选 **按患者维度合并**。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院耗材使用报表 |
| **严格对齐** | IF-RPT-ICU-01～04 |
| **实现阶段口径** | **目标态全量契约**（领域读侧 Gap 已关闭；应用层已成码） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/inpatientConsumableUsageReport/{methodName}` |
| **来源 / 汇总维** | 单源 `PATIENT_DISPENSE` / `DEPT_ISSUE`（实现拒绝 `ALL` 并集）；汇总维 `LOCATION` / `ITEM` / `CATEGORY` / `DEPARTMENT` |
| **lineMergeMode** | `LINE` / `BY_DEFINITION`；合并仅患者源：`DeviceDispenseQueryService#pageConsumableUsageDispenseMergedByEncounterAndDevice`（Criteria GROUP BY encounter+deviceDef+unit）；金额对本页分组合键回载定价后累加（**先按精确键过滤再定价**，禁止 IN 笛卡尔多余行刷价）；**禁止**全窗物化再 `subList` |
| **时间窗** | 同 AS-RPT-005-SDD：Request `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；`ReportDateTimeWindowSupport`；默认当日 `00:00:00～23:59:59` |

---

## 2. 代码结构

### 2.1 包路径

```text
.../application/report/consumableusage/
  support/（与门诊共用 ScopeLoader / FilterLoader / QueryFilter / AmountEnricher）
  inpatient/
    service/InpatientConsumableUsageReportAppService.java
    request/ response/ dto/ mapper/
    support/InpatientConsumableUsageReportQuerySupport.java
            InpatientConsumableUsageSummaryResult.java
            InpatientConsumableUsageDetailPageResult.java
```

### 2.2 依赖注入表

同门诊耗材（含 `PractitionerQueryService` 执行人姓名）；QuerySupport 预写 **IMP** encounter scope；明细 Asm 含住院号/病区/床号、**执行人**（患者侧 `performerId` / 科室侧 `approverId`）。

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 来源 | `PageInpatientConsumableUsageReportDetailRequest.sourceCode` | AS-RPT-006 | 同门诊 |
| 汇总维 | `SummarizeInpatientConsumableUsageReportRequest.summaryDimension` | AS-RPT-006 | LOCATION / ITEM / CATEGORY / DEPARTMENT |
| 扩展筛 | `deviceKeyword` / `patientKeyword` / `deviceCategoryCodes` / `recordTypeCode` / `performerPractitionerId` / `organizationId` | AS-RPT-005 §1.3 | 同门诊；患者关键字收窄 IMP |
| 常量 | `ConsumableUsageReportConstants` | AS §5 EX | EX-RPT-ICU-101～104 |

### 2.4 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `DeviceDispenseQueryService` | `pageConsumableUsageDispenseLines` / `listConsumableUsageDispenseLines` | 03、04 |
| `DeviceDispenseQueryService` | `summarizeConsumableUsageDispense` | 02、04 |
| `SupplyRequestQueryService` | `pageDeptIssueReportLines` / `listDeptIssueReportLines` | 03、04 |
| `SupplyRequestQueryService` | `summarizeDeptIssueReport` | 02、04 |

> 与门诊共用领域方法；场景差异仅在应用层预写 IMP scope 与住院上下文字段 enrich。

---

## 3. 按接口 ID 命名展开

| IF | HTTP methodName |
| --- | --- |
| 01 | `loadInpatientConsumableUsageReportPageInit` |
| 02 | `summarizeInpatientConsumableUsageReport` |
| 03 | `pageInpatientConsumableUsageReportDetail` |
| 04 | `exportInpatientConsumableUsageReport` |

**明细/导出执行人口径**：同 **AS-RPT-005-SDD §3**（`performerId` / `performerName`；Excel 列「执行人」）。

---

## 4. QueryService 与领域事件

目标态；**无写事件**。

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 非功能性需求实现（收敛为承诺表）

纯读；导出上限。汇总金额按页拉齐定价明细，**禁止** `EXPORT_MAX_ROWS` 静默截断页脚（同 AS-RPT-005-SDD）。

---

## 7. 测试要点

患者发放 + 科室领用；住院号/病区/床号；执行人回显（对齐门诊耗材）。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.11 | 2026-09-09 | `lineMergeMode=BY_DEFINITION`：患者源 GROUP BY 真分页；对齐 AS v1.11 / BR-RPT-ICU-008 |
| v1.10 | 2026-09-02 | 时间窗：`dateFrom`/`dateTo` 支持 `yyyy-MM-dd HH:mm:ss`（兼容仅日期）；默认当日全日；对齐 AS v1.10 |
| v1.9 | 2026-09-02 | 账批质检对齐 AS-RPT-006 v1.9 / AS-RPT-005-SDD v1.9 |
| v1.8 | 2026-08-28 | 导出改前端 `exportColumns` + 单 ExportRowDto 白名单；对齐 AS-RPT-005-SDD v1.8 / R-EXCEL-4/6 |
| v1.7 | 2026-08-28 | 导出拆 Summary/Detail ExportRowDto；汇总 omit 全空列（对齐 AS-RPT-005-SDD v1.7） |
| v1.6 | 2026-08-24 | 汇总金额按页拉齐；禁止 `EXPORT_MAX_ROWS` 静默截断页脚；对齐 AS-RPT-006 v1.7 / AS-RPT-005-SDD v1.6 |
| v1.5 | 2026-08-12 | 筛选对齐 AS-RPT-005-SDD v1.5（FilterLoader / 关键字等） |
| v1.4 | 2026-08-03 | 进/售价纠偏：批号价；对齐 AS-RPT-005-SDD v1.4 |
| v1.3 | 2026-08-01 | 明细/导出 **执行人**（对齐门诊） |
| v1.2 | 2026-07-28 | **费用性质** 筛选与明细/导出列（对齐 AS-RPT-005-SDD v1.2） |
| v1.1 | 2026-07-21 | 白名单对齐 AS Gap 关闭方法名；补住院上下文字段口径 |
| v1.0 | 2026-07-21 | 首版 |
