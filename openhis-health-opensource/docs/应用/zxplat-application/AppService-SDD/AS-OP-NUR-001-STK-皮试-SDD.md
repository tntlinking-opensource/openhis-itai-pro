# 门诊护士站 · 皮试 · 应用服务详细设计（SDD）

> **编码**：AS-OP-NUR-001-STK-SDD  
> **版本**：v1.0.0 | **日期**：2026-06-10  
> **上级**：`AS-OP-NUR-001-门诊护士站-SDD.md` 原 §8 · `AS-OP-NUR-001-门诊护士站.md` §1.7、§4（IF-OP-STK-01～04）

---

## 1. 边界

| 项 | 内容 |
| --- | --- |
| **UI** | 皮试 Tab（未落地）；左侧患者 **复用 PAT / IF-NUR-01** |
| **IF** | **IF-OP-STK-01～04** |
| **费用** | 护士保存**仅** `AllergyIntolerance`；**禁止** SR/Charge 写侧 |
| **列表门控** | 皮试检查 SR **`COMPLETED`**（门诊已收费） |

## 2. 代码与 HTTP

```text
com.zxplat.domain.application.outpatient.nursestation.skintest/
  service/OutpatientNurseStationSkinTestAppService.java
  mapper/OutpatientSkinTestMapper.java
  assembler/OutpatientSkinTestInitAssembler.java
  assembler/OutpatientSkinTestListAssembler.java
  request/ response/ dto/
```

| IF | 方法 | HTTP |
| --- | --- | --- |
| IF-OP-STK-01 | `loadOutpatientSkinTestInit` | `POST /api/app/outpatientNurseStationSkinTest/loadOutpatientSkinTestInit` |
| IF-OP-STK-02 | `listOutpatientSkinTestRows` | `POST /api/app/outpatientNurseStationSkinTest/listOutpatientSkinTestRows` |
| IF-OP-STK-03 | `saveOutpatientSkinTestRecord` | `POST /api/app/outpatientNurseStationSkinTest/saveOutpatientSkinTestRecord` |
| IF-OP-STK-04 | `deleteOutpatientSkinTestRecord` | `POST /api/app/outpatientNurseStationSkinTest/deleteOutpatientSkinTestRecord` |

## 3. 依赖

| 依赖 | 用途 |
| --- | --- |
| `EncounterQueryService` / `PatientEncounterConsistencyGate` | 就诊可见与患者一致（**未**注入 `EncounterContextReader`） |
| `ActivityDefinitionQueryService#requireIdByExactName` | 皮试检查 activityId |
| `ServiceRequestQueryService` | 列表/保存 Pre：`COMPLETED` + `doctor-prescription` |
| `MedicationRequestQueryService` / `MedicationDispenseQueryService` | 皮试药信息与库房 |
| `AllergyIntoleranceQueryService` / `AllergyIntoleranceDomainService` | 读列表 / 写保存删除 |
| `InventoryItemQueryService` / `ProductDefinitionBatchLoader` | 批号候选、药品名 |
| `PractitionerQueryService` | 开立医生/双签护士展示名 |
| `CurrentOperatorResolver` | IF-OP-STK-04 删除操作人 |
| `OutpatientSkinTestInitAssembler` / `OutpatientSkinTestListAssembler` | 枚举与列表装配 |

**禁止注入**：`ServiceRequestDomainService`、`ChargeItemDomainService`（保存路径）。

## 4. 与住院皮试差异

| 检查项 | 门诊（本节） | 住院（`AS-IP-NUR-011`） |
| --- | --- | --- |
| 列表过滤 | SR `COMPLETED` | MR `VERIFIED` |
| 首次保存 | 仅 Allergy | SR + Charge + Allergy |

完整编排序列、DTO 字段、测试要点见上级 SDD 原 **§8.5～§8.11**（正文仍保留于 `AS-OP-NUR-001-门诊护士站-SDD.md` §8，本文件为子域索引）。

---

**变更**：v1.0.0 自 **AS-OP-NUR-001-SDD** §8 拆出皮试子域索引。
