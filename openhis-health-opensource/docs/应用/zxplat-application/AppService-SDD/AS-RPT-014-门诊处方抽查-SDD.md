# 门诊处方抽查 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-014-SDD · **版本**：v1.1 · **日期**：2026-09-09  
> **编排权威**：`AS-RPT-014-门诊处方抽查.md`

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊处方抽查报表 |
| **边界** | 类型强制二选一；已发正方药品行分页 + 导出；纯读 |
| **严格对齐** | IF-RPT-PSC-01～03 |
| **实现阶段口径** | **目标态全量契约** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/prescriptionSpotCheckReport/{methodName}` |
| **时间窗** | Request `openedDateFrom/openedDateTo`（`yyyy-MM-dd`）；日闭区间转 `00:00:00`～`23:59:59` |
| **报表类型** | `reportType`：`WESTERN_PATENT` / `HERBAL` |
| **性别展示** | 男 / 女（短文案） |
| **就诊类型** | 固定「门诊」 |
| **AMB 预解析** | `EncounterQueryService#listOutpatientAmbEncounterIdsForCashierScope`（就诊 `start_time` 对齐开方窗；与 OPQ 同） |
| **导出预解析** | `PrescriptionSpotCheckReportQuerySupport#resolveCompletedScope` **一次**；翻页 `pageDrugLinesWithScope` 复用 |

---

## 2. 代码结构

### 2.1 包路径

```text
.../application/report/prescriptionspotcheck/
  service/PrescriptionSpotCheckReportAppService.java
  support/PrescriptionSpotCheckReportQuerySupport.java
  support/PrescriptionSpotCheckReportRowAssembler.java
  dto/PrescriptionSpotCheckWesternRowDto.java
  dto/PrescriptionSpotCheckHerbalRowDto.java
  request|response|mapper/...
zxplat-domain/.../PrescriptionSpotCheckReportConstants.java
```

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 登录 Gate |
| `EncounterQueryService` | AMB 就诊 id 预解析；就诊诊断实体 |
| `MedicationRequestQueryService` | 开方窗 medReq id；已签发分页；按方号整方行（方金额） |
| `MedicationDispenseQueryService` | COMPLETED medReqId 预解析；页内发放数量 |
| `MedicationDefinitionQueryService` | 目录批量 |
| `PatientQueryService` | 患者批量 |
| `PractitionerQueryService` | 医师 yb_no |
| `OrganizationQueryService` | 医院/科室 |
| `ConditionDefinitionQueryService` | 诊断定义 |
| `ChargeItemQueryService` | 方金额 |
| `TerminologyOptionLoader` / `FrequencyOptionLoader` | 用法、频次、剂型、单位、特殊煎法文案 |
| `AnnotatedExcelExporter` | 导出 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 报表类型 | `PrescriptionSpotCheckReportConstants.REPORT_TYPE_WESTERN_PATENT` / `HERBAL` | AS-RPT-014 BR-RPT-PSC-001 | 必选 |
| 发放状态 | `DispenseStatus.COMPLETED` | DM-MED-002 | 已发 |
| 就诊类 | `EncounterClass.AMB` | DM-ADM-002 | 门诊 |
| 处方类别 | `PrescriptionSplitConstants.RX_TYPE_*` | DM-MED-003 `rx_type_code` | 类型 IN 集合 |
| 性别 | `AdministrativeGender` | DM-ADM-001 | 展示男/女 |
| 中医来源 | `DoctorStationDiagnosisConstants.SOURCE_TCM_DX` / `SOURCE_TCM_SYNDROME` | DM-CLN-002 | 饮片病/证列 |

### 2.4 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `MedicationRequestQueryService` | `listOutpatientSignedMedicationRequestIdsInOpenedWindow` | 02、03 |
| `MedicationRequestQueryService` | `pageOutpatientSignedPrescriptionDrugLines` | 02、03 |
| `MedicationRequestQueryService` | `listMedicationRequestListModelsByPrescriptionNos` | 02、03（方金额） |
| `MedicationDispenseQueryService` | `listDistinctMedicationRequestIdsByStatusEnumAndMedReqIdIn` | 02、03 |
| `OrganizationQueryService` | `listActiveHospitals` | 02、03 |
| `PatientQueryService` | `batchLoadPatientDetails` | 02、03 |

编排序列：Gate → 类型/时间校验 → **resolveCompletedScope（一次）** → PageQuery（rxTypeCodes + excludeRefund）→ 分页 → Asm（整方金额回查）→ 导出循环复用 scope。

---

## 3. 按接口 ID 命名展开

### 3.1 IF-RPT-PSC-01 `loadPrescriptionSpotCheckReportPageInit`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/prescriptionSpotCheckReport/loadPrescriptionSpotCheckReportPageInit` |
| **Request** | 空体占位 |
| **Response** | `defaultReportType`；`defaultOpenedDateFrom/To`；`defaultPageSize`；`reportTypeOptions` |
| **异常** | EX-RPT-PSC-101 |

### 3.2 IF-RPT-PSC-02 `pagePrescriptionSpotCheckReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/prescriptionSpotCheckReport/pagePrescriptionSpotCheckReport` |
| **Request** | `reportType`；`openedDateFrom/To`；`pageNo`/`pageSize`（上限 500） |
| **Response** | 分页：`reportType` + `westernContent` 或 `herbalContent`（互斥按类型填充） |
| **异常** | EX-RPT-PSC-102/103 |

### 3.3 IF-RPT-PSC-03 `exportPrescriptionSpotCheckReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/prescriptionSpotCheckReport/exportPrescriptionSpotCheckReport` |
| **Request** | 同筛选 + `exportColumns` |
| **Response** | 二进制 xlsx |
| **异常** | EX-RPT-PSC-102/104 |
| **性能** | `resolveCompletedScope` 一次；内部分页 size=500；上限 `EXPORT_MAX_ROWS` |

---

## 4. QueryService 与领域事件

扩展 `OutpatientSignedPrescriptionDrugLinePageQuery`：`rxTypeCodes`、`excludeRefundPrescription`。  
**无写事件。** Gap **GAP-RPT-PSC-001** 成码关闭。

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 非功能性需求实现（收敛为承诺表）

| 项 | 承诺 |
| --- | --- |
| 分页 | `PagedResultBuilder`；列表 `MAX_PAGE_SIZE=500` |
| 导出 | `exportColumns` 传入 Exporter；scope 一次复用 |
| 批量 | 页内主键批量装载；方金额按方号整方回查 |
| AMB 窗 | 与 OPQ 同；大月窗可能物化大量 encounter/medReq id（已知代价） |

---

## 7. 测试要点

| 项 | 要点 |
| --- | --- |
| 类型非法 | EX-102 |
| 未发/退方 | 不入集 |
| 列切换 | 西药/饮片导出名与列一致 |
| 方金额 | 同方跨页行金额仍为整方合计 |
| 导出 | 预解析不随翻页重复（日志/耗时可观测） |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-09 | 首版 SDD |
| v1.1 | 2026-09-09 | 导出 scope 复用；整方金额；白名单补 listByPrescriptionNos；性能口径 |
