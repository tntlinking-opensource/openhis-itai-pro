---
doc_type: domain_model
package: com.zxplat.plugin.peis.domain.group
aggregate: PeisGroup
dm_code: DM-PEIS-009
status: canonical
maturity: minimal
---

# DM-PEIS-009 PeisGroup — 团体体检

> **成稿口径**：模式 B（代码先行）· **minimal canonical**（九章齐全、§6 简表 + 附录方法白名单）。  
> 细规冲突以源码为准；扩写完整 `####` 条目时升 `maturity: full`。  
> 聚合说明：[说明.md](./说明.md)

## 1. 概述

| 项 | 说明 |
|----|------|
| **职责** | 团检单位/合同/批次/名单及可选套餐绑定的维护与到检/缴费状态推进。 |
| **不做** | 不替代个检登记编排；不发 LIS/PACS 外送。 |
| **包** | `com.zxplat.plugin.peis.domain.group` |
| **源码** | `zxplat-core/zxplat-domain/.../peis/group/` |

## 2. 聚合根与持久化形态

| 类型 | 表 | 关键列（节选，完整见实体） |
|------|-----|------------------------------|
| `PeisContractType` | `peis_contract_type` | type_code, type_name, sort_order, enabled_flag |
| `PeisGroupBatch` | `peis_group_batch` | org_id, company_id, contract_id, batch_name, package_activity_id, booking_date, status_code |
| `PeisGroupBatchPackage` | `peis_group_batch_package` | batch_id, package_activity_id, sort_order |
| `PeisGroupCompany` | `peis_group_company` | org_id, parent_id, name, campus_college_id, contact_name, contact_phone |
| `PeisGroupContract` | `peis_group_contract` | org_id, company_id, package_activity_id, contract_name, valid_from, valid_to, contract_amount, pay_status_code, contract_type_code |
| `PeisGroupContractPackage` | `peis_group_contract_package` | contract_id, package_activity_id, sort_order |
| `PeisGroupRoster` | `peis_group_roster` | org_id, batch_id, company_id, patient_id, full_name, id_card_no, student_no, employee_no, college_id, class_name, mobile_phone, encounter_id, selected_package_activity_id, pay_status_code, arrival_status_code |
| `PeisGroupRosterPackage` | `peis_group_roster_package` | roster_id, package_activity_id, sort_order |

状态机：以实体 `status_*` / DomainService 校验为准；本 minimal 稿不重复绘制全图。

## 3. 实体

### PeisContractType

| 列（节选） |
|-----------|
| type_code, type_name, sort_order, enabled_flag |

### PeisGroupBatch

| 列（节选） |
|-----------|
| org_id, company_id, contract_id, batch_name, package_activity_id, booking_date, status_code |

### PeisGroupBatchPackage

| 列（节选） |
|-----------|
| batch_id, package_activity_id, sort_order |

### PeisGroupCompany

| 列（节选） |
|-----------|
| org_id, parent_id, name, campus_college_id, contact_name, contact_phone |

### PeisGroupContract

| 列（节选） |
|-----------|
| org_id, company_id, package_activity_id, contract_name, valid_from, valid_to, contract_amount, pay_status_code, contract_type_code |

### PeisGroupContractPackage

| 列（节选） |
|-----------|
| contract_id, package_activity_id, sort_order |

### PeisGroupRoster

| 列（节选） |
|-----------|
| org_id, batch_id, company_id, patient_id, full_name, id_card_no, student_no, employee_no, college_id, class_name, mobile_phone, encounter_id, selected_package_activity_id, pay_status_code, arrival_status_code |

### PeisGroupRosterPackage

| 列（节选） |
|-----------|
| roster_id, package_activity_id, sort_order |


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

- **BR-GROUP-001**：合同/批次套餐必须落在允许集合内（ssertPackageAllowed*）。
- **BR-GROUP-002**：名单变更不得破坏已关联 encounter 的最小一致性（以代码为准）。

## 8. 上下文映射

上游 Patient/Encounter/ActivityDefinition；下游登记/总检应用。

## 9. 补充

| 项 | 值 |
|----|-----|
| 原 stub 关闭日 | 2026-09-24 |
| 体例 | [../.../../.cursor/skills/文档代码一致性/SKILL.md](../.../../.cursor/skills/文档代码一致性/SKILL.md) |
| 一致性 | 模式 B；方法白名单 = 附录 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PeisGroupCheckDomainService`

| 方法 |
|------|
| `saveCompany` |
| `deleteCompany` |
| `saveContract` |
| `assertPackageAllowedForContract` |
| `assertPackageAllowedForBatch` |
| `listContractPackageIds` |
| `listBatchPackageIds` |
| `saveBatch` |
| `saveRosterWithOptionalPackages` |
| `saveRoster` |
| `importRosterRows` |
| `assignSelectedPackage` |
| `replaceRosterOptionalPackages` |
| `changeRosterBatch` |
| `listRosterOptionalPackageIds` |
| `markPaid` |
| `markContractPaid` |
| `markEnrolled` |
| `confirmArrival` |
| `cancelArrival` |
| `revertEnrollment` |

### QueryService：`PeisGroupCheckQueryService`

| 方法 |
|------|
| `listEnabledContractTypes` |
| `findContractTypeName` |
| `listValidContractsForPatient` |
| `isContractValidOn` |
| `listCompanies` |
| `listRootCompanies` |
| `listChildCompanies` |
| `listDescendantCompanyIds` |
| `requireCompany` |
| `listContracts` |
| `listContractsCoveringCompany` |
| `listContractsByCompanyIds` |
| `listContractPackageIds` |
| `mapContractPackageIds` |
| `requireContract` |
| `listBatches` |
| `listBatchesCoveringCompany` |
| `listBatchesByCompanyIds` |
| `listBatchesByContractId` |
| `requireBatch` |
| `requireBatchByNameUnderContract` |
| `listBatchPackageIds` |
| `mapBatchPackageIds` |
| `listRosterOptionalPackageIds` |
| `mapRosterOptionalPackageIds` |
| `findUniqueActiveRosterByPatientId` |
| `pageRoster` |
| `pageRosterByContract` |
| `listRosterByBatch` |
| `listRosterByPatientId` |
| `requireRoster` |
| `findRosterByEncounterId` |
| `listByIds` |
