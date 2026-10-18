---
doc_type: domain_model
package: com.zxplat.domain.clinical.proceduredefinition
aggregate: ProcedureDefinition
dm_code: DM-CLN-010
status: canonical
maturity: minimal
---

# DM-CLN-010 ProcedureDefinition — 诊疗项目执行定义

> **成稿口径**：模式 B（代码先行）· **minimal canonical**（九章齐全、§6 简表 + 附录方法白名单）。  
> 细规冲突以源码为准；扩写完整 `####` 条目时升 `maturity: full`。  
> 聚合说明：[说明.md](./说明.md)

## 1. 概述

| 项 | 说明 |
|----|------|
| **职责** | 诊疗/手术等执行类目录定义的维护、启停与目录导入。 |
| **不做** | 不编排医生站开立；不替代加收/划价用 ActivityDefinition。术式开立真源在 SR.procedure_definition_id，**不要求**挂靠 AD。 |
| **包** | `com.zxplat.domain.clinical.proceduredefinition` |
| **源码** | `zxplat-core/zxplat-domain/.../clinical/proceduredefinition/` |

## 2. 聚合根与持久化形态

| 类型 | 表 | 关键列（节选，完整见实体） |
|------|-----|------------------------------|
| `ProcedureDefinition` | `cln_procedure_definition` | source_enum, procedure_code, name, py_str, wb_str, type_code, description, yb_flag, yb_no, yb_match_flag, clinic_code, status_enum |

状态机：以实体 `status_*` / DomainService 校验为准；本 minimal 稿不重复绘制全图。

## 3. 实体

### ProcedureDefinition

| 列（节选） |
|-----------|
| source_enum, procedure_code, name, py_str, wb_str, type_code, description, yb_flag, yb_no, yb_match_flag, clinic_code, status_enum |


## 4. 值对象（含枚举）

本包枚举/码表字段以实体 `*_enum` / `*_code`（String 存 code）为准；专用值对象类若存在见源码 `valueobject/`。

## 5. 领域事件

minimal：未单独建事件类的写操作以事务内状态变更为准。若 `share/events` 下存在本聚合事件，以该类为准并在扩写时补载荷表。

## 6. 领域服务

§6.1 / §6.2 采用 **简表型**：完整方法名见文末「模式 B 已实现方法清单」。关键方法语义以 DomainService JavaDoc 为准。

### 6.1 写侧

见文末附录 DomainService 方法表（无写侧则本节 N/A）。

### 6.2 读侧

见文末附录 QueryService 方法表（无读侧则本节 N/A）。

## 7. 不变式

- **BR-PROCDEF-001**：启停/删除不得破坏已被医嘱引用的最小约束（当前以代码校验为准）。
- **BR-PROCDEF-002**：目录导入行须带合法编码，重复策略以 DomainService 为准。
- **BR-PD-006**：国临（`clinical_proc_v3`）与医保手术（`general_op`）对照为**多对多互指**（多条医保可同 `clinic_code`，多条国临可同 `yb_no`）；保存/导入**不拆除**其它行对照。国临行 `yb_no` 为 4401 **缺省上传码**，仅空时回写。医保导入只以 1308 第 16 列临床版码为国临真源；空或平台占位（字符串 `null`/`--`）时不补建国临、不把第 11 列医保码写成国临码，也不清已有对照（权威对照允许两边编码相同）。已有对照不覆盖。
- **BR-PD-011**：`activity_definition_id`（挂靠计费 AD）为**可选特殊维护**；住院手术开立**不**以挂靠 AD 为门禁。临床身份以开立后的 `wkf_service_request.procedure_definition_id` 为准。

## 8. 上下文映射

上游术语/医保码；下游医嘱 Activity/Procedure。

## 9. 补充

| 项 | 值 |
|----|-----|
| 原 stub 关闭日 | 2026-09-24 |
| 体例 | [../.../../.cursor/skills/文档代码一致性/SKILL.md](../.../../.cursor/skills/文档代码一致性/SKILL.md) |
| 一致性 | 模式 B；方法白名单 = 附录 |
| 回写 | 2026-10-07：附录 Query 补 `searchActiveByKeyword` / `pageActiveByKeyword`（术式选品空关键字可浏览） |
| 回写 | 2026-10-07：BR-PD-011 挂靠 AD 非开立门禁；术式真源落 SR.procedure_definition_id（住院手术口径 v0.5） |
| 回写 | 2026-10-07：BR-PD-006 第 16 列为空时不补建、不覆盖已有对照（含同码）。2026-10-06：多对多互指；读 1308 临床版列并可补建国临 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ProcedureDefinitionDomainService`

| 方法 |
|------|
| `add` |
| `edit` |
| `stop` |
| `delete` |
| `start` |
| `importClinicalProcV3Catalog` |
| `importGeneralOpCatalog` |

### QueryService：`ProcedureDefinitionQueryService`

| 方法 |
|------|
| `page` |
| `getOne` |
| `mapSearchItemsByIds` |
| `searchClinicalProcV3ByKeyword` |
| `searchActiveByKeyword` |
| `pageActiveByKeyword` |
| `findActiveClinicalByActivityDefinitionId` |
| `mapActiveClinicalByActivityDefinitionIds` |
| `mapYbNoByClinicCodes` |
