# 住院医保费用明细 · 应用服务详细设计（SDD）

> **编码**：AS-RPT-013-SDD · **版本**：v1.0 · **日期**：2026-09-09  
> **编排权威**：`AS-RPT-013-住院医保费用明细.md`  
> **说明**：与 AS-RPT-012-SDD 同构；`encounter_class=IMP`；科室=入院科室；业务号=住院号。

---

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/inpatientInsuranceFeeDetailReport/{methodName}` |
| **严格对齐** | IF-RPT-IIF-01～03 |
| **共享** | `…/report/insurancefeedetail/support` 与 SPI 查询；门诊/住院仅 `encounterClass` 与科室主数据源不同 |

---

## 2. 代码结构

```text
.../application/report/insurancefeedetail/inpatient/
  service/InpatientInsuranceFeeDetailReportAppService.java
  request|response|mapper/...
```

方法：`loadInpatientInsuranceFeeDetailReportPageInit` / `pageInpatientInsuranceFeeDetailReport` / `exportInpatientInsuranceFeeDetailReport`。

---

## 3. 按接口 ID

| IF | methodName |
| --- | --- |
| IF-RPT-IIF-01 | `loadInpatientInsuranceFeeDetailReportPageInit` |
| IF-RPT-IIF-02 | `pageInpatientInsuranceFeeDetailReport` |
| IF-RPT-IIF-03 | `exportInpatientInsuranceFeeDetailReport` |

异常：`EX-RPT-IIF-101～104`。其余字段与校验对齐 AS-RPT-012-SDD §3。

---

## 4. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-09 | 首版 SDD |
