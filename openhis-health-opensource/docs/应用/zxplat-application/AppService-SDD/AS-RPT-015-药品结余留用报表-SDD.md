# 药品结余留用报表 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-015-SDD · **版本**：v1.1.3 · **日期**：2026-09-18  
> **编排权威**：`AS-RPT-015-药品结余留用报表.md`

---

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| **模块** | 药品结余留用报表 |
| **边界** | 批次必选 + 签发时间窗；门诊/住院发退药净明细分页与导出；纯读 |
| **HTTP** | `POST /api/app/surplusRetentionReport/{methodName}` |
| **实现阶段口径** | **目标态全量契约** |

---

## 2. 代码结构

```text
.../application/report/surplusretention/
  service/SurplusRetentionReportAppService.java
  support/SurplusRetentionReportQuerySupport.java
  support/SurplusRetentionReportRowAssembler.java
  dto/SurplusRetentionReportRowDto.java
  request|response|mapper/...
zxplat-domain/.../SurplusRetentionReportConstants.java
```

### 2.1 依赖注入

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 登录 Gate |
| `TerminologyOptionLoader` | 批次下拉 |
| `MedicationDefinitionQueryService` | `listIdsBySurplusRetentionBatchCode`；`findAllByIds`（行装配） |
| `MedicationRequestQueryService` | `listIdsByMedicationIdsAndAuthoredTimeBetween`；`mapEntitiesByIds` / `listMedicationRequestListModelsByIds`（行装配/#4M-RFND） |
| `MedicationDispenseQueryService` | `pagePatientDispensesByMedReqIdsAndStatusIn`；`toSlipQueryModel` |
| `EncounterQueryService` / `PatientQueryService` | `listEncounterEntitiesByIds` / `findPatientNamesByIds` |
| `ChargeItemQueryService` + `PharmacyDispenseRecordChargePricingAssembler` | 售价与退药补价（#4M-RFND） |
| `AccountQueryService` + `ContractQueryService` | 发放挂账 → 合同分类（参保类型三值） |
| `OutpatientRegistrationInsuranceExtension` / `InpatientChargeInsuranceExtension` | 就诊平台参保地区划 |
| `InsurancePlatformDictDisplayExtension` | 区划码 → 展示名 |
| `SupplierQueryService` | `mapNamesByIds`（厂家） |
| `AnnotatedExcelExporter` | 导出 |

### 2.2 值集

| 项 | 锚点 |
| --- | --- |
| 批次术语 | `TerminologyCodeSystemNameConstants.SURPLUS_RETENTION_BATCH_CODE` / DM-MED-001 |
| 发放态 | `DispenseStatus.COMPLETED` / `REFUNDED`；`MedicationDispenseType.REFUND` |
| 中选 | `MedicationDefinition.centralPurchaseFlag` |

### 2.3 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `MedicationDefinitionQueryService` | `listIdsBySurplusRetentionBatchCode` | 02、03 |
| `MedicationRequestQueryService` | `listIdsByMedicationIdsAndAuthoredTimeBetween` | 02、03 |
| `MedicationDispenseQueryService` | `pagePatientDispensesByMedReqIdsAndStatusIn`；`toSlipQueryModel` | 02、03 |

---

## 3. 按接口 ID 命名展开

### 3.1 IF-RPT-SRB-01 `loadSurplusRetentionReportPageInit`

**HTTP**：`POST /api/app/surplusRetentionReport/loadSurplusRetentionReportPageInit`  
**编排序列**：1 Gate 登录 → 2 Pre 当月签发窗 + 术语批次 options → 3 Asm Response。

### 3.2 IF-RPT-SRB-02 `pageSurplusRetentionReport`

**HTTP**：`POST /api/app/surplusRetentionReport/pageSurplusRetentionReport`  
**Request**：`surplusRetentionBatchCode`、`authoredDateFrom/To`、`pageNo/pageSize`  
**编排序列**：1 Gate 批次+窗+分页 → 2 Pre 目录 id → 3 Pre 申请 id（签发窗）→ 4 Pre 发放行 COMPLETED/REFUNDED → 5 Asm 分页装配 19 列（类型=合同三值；地区自费=「自费」、医保=平台参保地；门诊入出院=处方日期；产品名=商品名原样）。

### 3.3 IF-RPT-SRB-03 `exportSurplusRetentionReport`

**HTTP**：`POST /api/app/surplusRetentionReport/exportSurplusRetentionReport`  
**Request**：同分页 + `exportColumns`  
**编排序列**：同 02 预解析一次 → 翻页收集至 `EXPORT_MAX_ROWS` → `AnnotatedExcelExporter`。

---

## 4. QueryService 与领域事件

无写事件。读侧仅本聚合 QS；跨聚合由 AppSupport 编排。

---

## 5. 写库顺序

无写。

---

## 6. 非功能性需求实现

| 承诺 | 实现 |
| --- | --- |
| IN 分批 | `IdInQueryChunkSupport` |
| 导出上限 | `SurplusRetentionReportConstants.EXPORT_MAX_ROWS` |
| 退药售价 | 复用发药报表补价路径 |

---

## 7. 测试要点

对齐 AS §8。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.1.4 | 2026-09-18 | 参保地展示对齐挂号历史（`InsuplcAdmdvsDisplaySupport`） |
| v1.1.3 | 2026-09-18 | 参保地区分列：自费写「自费」；医保写 OP/IP 平台 `insuplc_admdvs`（住院预结算回填 SPI） |
| v1.1.2 | 2026-09-18 | 参保两列按合同分类三值；产品名原样；`mapCategoryEnumByBusNoIn` |
| v1.1.1 | 2026-09-18 | 参保两列固定「自费」；产品名「无」回落通用名；去掉费用性质判定依赖 |
| v1.1 | 2026-09-18 | 行装配：自费参保两列「自费」；门诊入出院=处方日期；依赖 `ReportPatientSettlementContextAssembler` |
| v1.0.1 | 2026-09-10 | §2.1 依赖方法与成码对齐（pagePatient / mapEntities / #4M-RFND） |
| v1.0 | 2026-09-10 | 首版 |
