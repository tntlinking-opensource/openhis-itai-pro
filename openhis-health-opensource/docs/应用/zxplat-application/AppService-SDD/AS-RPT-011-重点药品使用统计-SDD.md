# 重点药品使用统计 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-011-SDD · **版本**：v1.2.3 · **日期**：2026-09-02  
> **编排权威**：`AS-RPT-011-重点药品使用统计.md`

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 重点药品使用统计报表 |
| **边界** | 就诊类型 × 重点药类型矩阵：净人次 + 售价净额 + 使用占比；每就诊类型块末组内合计（= 全部药典药品）；纯读；可导出；无明细；无门诊+住院全局合计 |
| **严格对齐** | IF-RPT-KDU-01～03 |
| **实现阶段口径** | **目标态全量契约** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/keyMedicationUsageReport/{methodName}` |
| **就诊类型** | Request `encounterClassCodes`：`AMB` / `IMP`；空=全部 |
| **重点药类型** | Request `keyDrugTypeCodes`：`BASIC` / `NATIONAL_MONITOR` / `CENTRAL_PURCHASE` / `ANTIBIOTIC`；与药品性质**分框** |
| **药品性质** | Request `medicinePropertyCodes`：`NARCOTIC` / `PSYCH_ONE` / `PSYCH_TWO`（`pharmacology_category_code` 2/4/5）；与重点药类型分框；两框皆空=矩阵七行；仅选一框=只出该框类型行 |
| **净人次** | 该类型药净售价 > 0 的 distinct `encounter_id` |
| **售价** | `ChargeItem.total_price` + 费用明细收退符号；展示结算刻度 |
| **组内合计** | 时间窗内<strong>全部</strong> `med_medication_definition` 产品行净人次/售价（西药/中成药等）；分项之和不必等于合计 |
| **使用占比** | 人次占比=类型人次/**全部药品合计人次**；金额占比=类型金额/**全部药品合计金额**；合计行 100%；多旗时分项占比之和可 >100%；scale=`USAGE_RATIO_PERCENT_SCALE` |
| **组内合计码** | `KeyMedicationUsageReportConstants.SUMMARY_KEY_DRUG_TYPE_TOTAL`（`TOTAL`，写在 `keyDrugTypeCode`） |

---

## 2. 代码结构

### 2.1 包路径

```text
.../application/report/keymedicationusage/
  service/KeyMedicationUsageReportAppService.java
  request/
    LoadKeyMedicationUsageReportPageInitRequest.java
    SummarizeKeyMedicationUsageReportRequest.java
    ExportKeyMedicationUsageReportRequest.java
  response/
    LoadKeyMedicationUsageReportPageInitResponse.java
    SummarizeKeyMedicationUsageReportResponse.java
    ExportKeyMedicationUsageReportResponse.java
  dto/
    KeyMedicationUsageSummaryRowDto.java
  mapper/
    KeyMedicationUsageReportMapper.java
    KeyMedicationUsageReportMapperImpl.java
  support/
    KeyMedicationUsageReportQuerySupport.java

.../domain/share/constants/KeyMedicationUsageReportConstants.java
.../domain/administration/chargeitem/query/KeyMedicationUsageAggQueryModel.java
```

> `ExportKeyMedicationUsageReportRequest` **继承** `SummarizeKeyMedicationUsageReportRequest`（筛选字段同构，禁止平行复制字段）。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 登录 Gate |
| `KeyMedicationUsageReportQuerySupport` | 日期校验；AMB/IMP 费用明细 scope；类型矩阵与组内合计占比编排 |
| `MedicationDefinitionQueryService` | `listIdsByKeyDrugType(code)` |
| `ChargeItemQueryService` | `summarizeKeyMedicationUsageMatrix`；`summarizeAllMedicationUsage` |
| `OutpatientFeeDetailLoader` | 门诊 Payment `bill_date` scope（Support 内） |
| `InpatientFeeDetailReportQuerySupport` | 住院 ChargeItem-only scope（Support 内） |
| `AnnotatedExcelExporter` | 导出 |
| `KeyMedicationUsageReportMapper` | Request/Response 映射 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 重点药类型 | `com.zxplat.domain.share.enums.KeyDrugType` | `DM-MED-001` §3 四旗 + 药品性质 | BASIC / NATIONAL_MONITOR / CENTRAL_PURCHASE / ANTIBIOTIC / NARCOTIC / PSYCH_ONE / PSYCH_TWO |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` Encounter.classEnum | AMB / IMP |
| 目录状态 | `com.zxplat.domain.share.enums.PublicationStatus` | `DM-MED-001` statusEnum | ACTIVE / INACTIVE |
| 请求就诊类型多选 | `SummarizeKeyMedicationUsageReportRequest.encounterClassCodes` | AS-RPT-011 BR-RPT-KDU-002 | 空=全部 |
| 请求重点药多选 | `SummarizeKeyMedicationUsageReportRequest.keyDrugTypeCodes` | AS-RPT-011 BR-RPT-KDU-002 | 空=全部；拆行 |
| 请求目录状态 | `SummarizeKeyMedicationUsageReportRequest.medicationStatusEnum` | AS-RPT-011 BR-RPT-KDU-009 | 空=全部 |
| 药典产品表 | `TableNameConstants.MED_MEDICATION_DEFINITION` | `DM-ADM-011` ChargeItem.productTable | 仅药品 |
| 常量 EX/导出/合计/占比刻度 | `KeyMedicationUsageReportConstants` | AS-RPT-011 §5 | EX-RPT-KDU-*；SUMMARY_KEY_DRUG_TYPE_TOTAL；USAGE_RATIO_PERCENT_SCALE |

### 2.4 领域方法白名单

| 服务 | 方法 | IF |
| --- | --- | --- |
| `MedicationDefinitionQueryService` | `listIdsByKeyDrugType(code, statusEnum)` | 02、03 |
| `ChargeItemQueryService` | `summarizeKeyMedicationUsageMatrix`；`summarizeKeyMedicationUsage`（并集合计） | 02、03 |

编排序列：Gate → 日期校验 → Support 解析类型/状态 → 各类型 listIds + 并集 listIds →（按 AMB/IMP）prepare scope → matrix + 并集 summarize → Asm 类型行 → 以并集合计回填占比 → Asm TOTAL（100%）→ 页脚（仅累加 TOTAL）/Excel。

---

## 3. 按接口 ID 命名展开

### 3.1 IF-RPT-KDU-01 `loadKeyMedicationUsageReportPageInit`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/keyMedicationUsageReport/loadKeyMedicationUsageReportPageInit` |
| **Request** | 空体占位 `LoadKeyMedicationUsageReportPageInitRequest` |
| **Response** | `defaultDateFrom`/`defaultDateTo`（当日 `yyyy-MM-dd`）；`encounterClassOptions`；`keyDrugTypeOptions`；`medicationStatusOptions` |
| **编排序列** | 1 Gate 登录 → 2 组装 OptionDto（EncounterClass；KeyDrugType 四值；PublicationStatus ACTIVE/INACTIVE）→ 3 Asm |
| **异常** | EX-RPT-KDU-101 |

### 3.2 IF-RPT-KDU-02 `summarizeKeyMedicationUsageReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/keyMedicationUsageReport/summarizeKeyMedicationUsageReport` |
| **Request** | `dateFrom`/`dateTo`（必填）；`encounterClassCodes`（可选）；`keyDrugTypeCodes`（可选，四旗）；`medicinePropertyCodes`（可选，麻/精）；`medicationStatusEnum`（可选） |
| **Response** | `rows[]`：`encounterClassCode`/`encounterClassDisplay`/`keyDrugTypeCode`/`keyDrugTypeDisplay`/`encounterNetCount`/`saleAmountTotal`/`usageRatioPercent`/`saleAmountRatioPercent`；`totalEncounterNetCount`；`totalSaleAmount` |
| **编排序列** | 1 Gate → 2 日期校验 → 3 解析类型/状态 → 4 各类型 listIds + 并集 → 5 每就诊类型：prepare → matrix + 并集汇总 → 6 装配类型行 → 7 以并集合计回填占比 → 8 TOTAL（100%）→ 9 页脚（仅累加 TOTAL） |
| **异常** | EX-RPT-KDU-102/103 |

**行序**：先门诊（AMB：类型…+合计）再住院（IMP：类型…+合计）。

### 3.3 IF-RPT-KDU-03 `exportKeyMedicationUsageReport`

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/keyMedicationUsageReport/exportKeyMedicationUsageReport` |
| **Request** | 同 02 筛选字段（`ExportKeyMedicationUsageReportRequest` 继承 `SummarizeKeyMedicationUsageReportRequest`）+ `exportColumns` |
| **Response** | `fileName`/`contentType`/`body`/`rowCount`/`truncated` |
| **编排序列** | 1 Gate → 2 校验 → 3 复用 02 汇总行 → 4 `AnnotatedExcelExporter.exportToXlsxBytes`（DTO=`KeyMedicationUsageSummaryRowDto`） |
| **异常** | EX-RPT-KDU-102/104 |

**Excel 列**（`@Excel`）：就诊类型、药品类型、人次、总金额、人次占比(%)、金额占比(%)。

---

## 4. QueryService 与领域事件

| 方法 | DM | 语义 |
| --- | --- | --- |
| `MedicationDefinitionQueryService#listIdsByKeyDrugType(code, status)` | DM-MED-001 §6.2.14 | 对应旗=true 的未删除药典 id；可选 ACTIVE/INACTIVE |
| `ChargeItemQueryService#summarizeKeyMedicationUsageMatrix` | DM-ADM-011 | 一次遍历按多套 productId 分桶汇总净人次与售价 |
| `ChargeItemQueryService#summarizeKeyMedicationUsage` | DM-ADM-011 | 并集 productId 一次汇总（组内合计与占比分母） |

**无写事件。**

---

## 5. 写库顺序（核心写接口视角）

**本期无写库。**

---

## 6. 非功能性需求实现（收敛为承诺表）

| 承诺 | 实现 |
| --- | --- |
| 纯读 | 无 `@Transactional`；无 DomainService 写 |
| 性能 | 同一就诊类型下矩阵与组内合计共用一次费用明细 scope 与 `cachedChargeRows` |
| 仅药品 | Support 写 `feeCategoryCodes=MEDICATION`；域内再校验 `productTable` |
| 导出 | `KeyMedicationUsageReportConstants.EXPORT_SHEET_NAME`；`exportColumns` 裁剪 |
| 金额 | 售价走 `MonetaryAmountSupport.scaleSettle`；占比刻度走 `USAGE_RATIO_PERCENT_SCALE` |

---

## 7. 测试要点

| 用例 | 期望 |
| --- | --- |
| 默认筛选 | AMB 四类型+合计 + IMP 四类型+合计 |
| 门诊基药全退 | AMB 基药行人次 0；AMB 合计同步 |
| 双旗/同人多类 | 可计入两类型行；合计为人次并集（可小于分项之和） |
| 人次占比 | 并集合计 100、基药 20 → 基药 20%；同人开基药+抗生素时分项占比之和可 >100% |
| 金额占比 | 同理相对并集合计金额；合计行金额占比 100% |
| 仅启用 | 停用目录药品不入对应类型 |
| 导出 | 列：就诊类型、药品类型、人次、总金额、人次占比(%)、金额占比(%) |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.2.2 | 2026-09-01 | 合计=所选类型并集去重；占比分母=并集；页脚只加 TOTAL；承认分项可大于合计 |
| v1.2.1 | 2026-09-01 | 占比分母改为组内合计；拆分人次占比/金额占比；去掉任意药品人次分母 |
| v1.2 | 2026-09-01 | 恢复类型矩阵；组内 TOTAL；medicationStatusEnum 保留；去掉全局就诊 TOTAL |
| v1.1 | 2026-09-01 | （已废）并集分行 + 全局 TOTAL；基药占比 |
| v1.0.1 | 2026-08-21 | Export Request 继承 Summarize；前端 ReportQueryBar 槽位对齐 |
| v1.0 | 2026-08-21 | 首版 |
