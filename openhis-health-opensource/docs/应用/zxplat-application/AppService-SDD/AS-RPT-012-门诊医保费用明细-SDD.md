# 门诊医保费用明细 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-012-SDD · **版本**：v1.2 · **日期**：2026-09-10  
> **编排权威**：`AS-RPT-012-门诊医保费用明细.md`

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊医保费用明细报表 |
| **边界** | CONFIRMED 净结算明细分页 + 四列合计 + 导出；纯读 |
| **严格对齐** | IF-RPT-OIF-01～03 |
| **实现阶段口径** | **目标态全量契约** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientInsuranceFeeDetailReport/{methodName}` |
| **时间窗** | Request `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`）；`ReportDateTimeWindowSupport`；结束时刻按左闭右开 `endExclusive` 入 SPI |
| **费用类别** | `contractCategoryCodes[]`：`PROVINCIAL_INSURANCE` / `MUNICIPAL_INSURANCE`；解析合同 id 时**兼认**库内数字码 `3`/`4`（`ChargeTypeCategory`）；`workInjuryOnly=true` 时忽略类别码，`settleMode=WORK_INJURY` |
| **职工/居民** | `insutypeGroup`：`ALL` / `EMPLOYEE` / `RESIDENT`；EMPLOYEE→insutype∈{310,320,330}；RESIDENT→{390} |
| **科室** | `organizationId` → Encounter 挂号科室预解析 encounterIds |
| **模糊编号** | `setlIdKeyword` / `mdtrtIdKeyword` / `busNoKeyword` / `patientNameKeyword`（姓名经患者主数据展开→就诊白名单；列表姓名同路径 enrich，不读结算回执 `psn_name` 作展示真源） |

---

## 2. 代码结构

### 2.1 包路径

```text
.../application/report/insurancefeedetail/
  constants/InsuranceFeeDetailReportConstants.java   （或 domain-shared）
  dto/InsuranceFeeDetailRowDto.java
  dto/InsuranceFeeDetailFooterDto.java
  support/InsuranceFeeDetailReportQuerySupport.java
  support/InsuranceFeeDetailReportEnricher.java
  outpatient/
    service/OutpatientInsuranceFeeDetailReportAppService.java
    request|response|mapper/...
  inpatient/   （对称 AS-RPT-013）
```

SPI（zxplat-domain）：

```text
InsuranceSettlementRecordLookupExtension
  + pageConfirmedFeeDetailLines(ExtensionIn) → PageExtensionOut
  + summarizeConfirmedFeeDetailAmounts(ExtensionIn) → SummaryExtensionOut
ExtensionIn: InsuranceSettlementFeeDetailQueryExtensionIn
ExtensionOut: InsuranceSettlementFeeDetailPageExtensionOut
              InsuranceSettlementFeeDetailLineExtensionOut
              InsuranceSettlementFeeDetailSummaryExtensionOut
```

CHS：`YbSettlementRecordQueryService#pageConfirmedInsuranceFeeDetail` / `#summarizeConfirmedInsuranceFeeDetail`

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 登录 Gate |
| `ReportDateTimeWindowSupport` | 时间窗 |
| `OrganizationQueryService` | 科室下拉与名称 |
| `ContractQueryService` | 省/市 → contractIds |
| `EncounterQueryService` | 科室/门诊号 → encounterIds；回填 busNo / organizationId |
| `PatientQueryService` | 姓名模糊展开；列表姓名回填 |
| `OrganizationQueryService` | 列表科室名回填 |
| `WorkInjuryModuleExtension` | 工伤选项门控 |
| `InsuranceSettlementRecordLookupExtension` | 结算分页与合计 |
| `InsuranceFeeDetailReportEnricher` | 科室名等 Asm |
| `AnnotatedExcelExporter` | 导出 |

### 2.3 枚举、字典与值集

| 类别 | 代码锚点 | 说明 |
| --- | --- | --- |
| 合同分类 | `ContractCategory.PROVINCIAL_INSURANCE` / `MUNICIPAL_INSURANCE` | 省/市 |
| 工伤 settle_mode | `WORK_INJURY`（与 wi `SCHEME_CODE` 同码） | 工伤独立 |
| 回执状态 | `CONFIRMED` | 净行 |
| 就诊类 | `AMB` | 门诊 |
| 金额 | `MonetaryAmountSupport.scaleSettle` | 结算 2 位 |

### 2.4 领域 / SPI 方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `InsuranceSettlementRecordLookupExtension` | `pageConfirmedFeeDetailLines` | 02、03 |
| `InsuranceSettlementRecordLookupExtension` | `summarizeConfirmedFeeDetailAmounts` | 02 |
| `ContractQueryService` | `listIdsByCategoryEnum` | 02、03 |
| `WorkInjuryModuleExtension` | `isEnabled` | 01 |

编排序列：Gate → 时间/互斥校验 → Support（合同 id / encounterIds）→ SPI page + summarize → Enricher → PagedResultBuilder + footer。

---

## 3. 按接口 ID 命名展开

### 3.1 IF-RPT-OIF-01 `loadOutpatientInsuranceFeeDetailReportPageInit`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientInsuranceFeeDetailReport/loadOutpatientInsuranceFeeDetailReportPageInit` |
| **Request** | 空体占位 |
| **Response** | `defaultDateFrom/To`；`defaultPageSize`；`organizationOptions`；`contractCategoryOptions`；`workInjuryEnabled`；`insutypeGroupOptions` |
| **异常** | EX-RPT-OIF-101 |

### 3.2 IF-RPT-OIF-02 `pageOutpatientInsuranceFeeDetailReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientInsuranceFeeDetailReport/pageOutpatientInsuranceFeeDetailReport` |
| **Request** | 时间窗 + 类别/工伤 + insutypeGroup + organizationId + 各 keyword + pageNo/pageSize |
| **Response** | 分页 `content`（列字段见 AS §1.3）+ `footer` 四列合计 + 笔数 |
| **异常** | EX-RPT-OIF-102/103 |

### 3.3 IF-RPT-OIF-03 `exportOutpatientInsuranceFeeDetailReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/outpatientInsuranceFeeDetailReport/exportOutpatientInsuranceFeeDetailReport` |
| **Request** | 同筛选 + `exportColumns` |
| **Response** | 二进制导出 |
| **异常** | EX-RPT-OIF-102/104 |

---

## 4. QueryService 与领域事件

目标态：CHS `YbSettlementRecordQueryService` 报表读；**无写事件**。Gap **GAP-RPT-OIF-001** 成码关闭。

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-09 | 首版 SDD |
| v1.1 | 2026-09-09 | SPI 出参加 PageExtensionOut；姓名模糊落 psn_name；依赖表去掉 PatientQueryService |
| v1.2 | 2026-09-10 | 省/市合同解析兼认 category_enum 数字码 3/4（ChargeTypeCategory） |
