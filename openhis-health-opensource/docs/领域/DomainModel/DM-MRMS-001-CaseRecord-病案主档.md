# DM-MRMS-001 CaseRecord 病案主档

> **限界上下文**：病案管理（MRMS）  
> **聚合类型**：实体聚合（主档 + 首页 + 子表）  
> **版本**：v1.0

## 1. 概述

### 1.1 业务定义

病案主档（CaseRecord）表示一次住院就诊（Encounter）对应的**病案生命周期锚点**：建档、首页编辑、提交/接收/退回、质控、归档。与 Encounter **1:1**（`tenant_id + encounter_id` 唯一）。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 生命周期 | 草稿 → 提交 → 接收 → 质控 → 归档（可退回） |
| 首页关联 | 懒创建 `CaseRecordHomepage`；归档时冻结 |
| 列表冗余 | 主档冗余姓名、科室、出院时间等供检索 |
| 病案号 | 租户内唯一 `medcasno`（可空至首次保存）；`CR`+yyMM+4 位年流水 |
| 跨域读侧 | 首页自动取值直注入 health `*QueryService` |

### 1.3 不变量

1. `tenant_id + encounter_id`（`is_deleted=false`）唯一。
2. `medcasno` 非空时租户内唯一；主档可提前建档时为空，医生首次保存首页发号。
3. `status=archived` → 首页 `frozen=true`。
4. 仅 `draft` / `returned` 允许医生编辑首页。

## 2. 聚合根与持久化形态

**持久化形态**：主表 + 子表

| 表 | 说明 |
| --- | --- |
| `mrms_case_record` | 病案主档 + 列表冗余字段 |
| `mrms_medcasno_sequence` | 病案号发号 |
| `mrms_case_record_homepage` | 首页标量 + `field_sources_json`；中医信息五列（`clinical_path_code` 等，中医院启用） |
| `mrms_homepage_diagnosis` | 诊断行 |
| `mrms_homepage_procedure` | 手术行 |
| `mrms_homepage_icu` | 重症监护行（4101A `icuinfo` / 4401 `icuinfo`） |
| `mrms_homepage_bld` | 输血行（4101A `bldinfo`） |

**聚合根标识**：`CaseRecordId`（雪花 ID）

## 3. 状态机

```
[—] ──createIfAbsent──> [DRAFT]
[DRAFT/RETURNED] ──submit──> [SUBMITTED]
[SUBMITTED] ──receive──> [RECEIVED]
[SUBMITTED/RECEIVED] ──return──> [RETURNED]
[RECEIVED] ──startQc──> [QCING]        （Wave 2）
[QCING] ──archive──> [ARCHIVED]
[ARCHIVED] ──unarchive──> [RECEIVED]   （Wave 3）
```

| 状态 | code | 说明 |
| --- | --- | --- |
| 草稿 | `draft` | 可编辑首页 |
| 已提交 | `submitted` | 待病案室接收 |
| 已退回 | `returned` | 退回医生修改 |
| 已接收 | `received` | 病案室已接收 |
| 质控中 | `qcing` | Wave 2 |
| 已归档 | `archived` | 首页冻结 |

## 4. 领域服务

| 类 | 职责 |
| --- | --- |
| `CaseRecordDomainService` | 建档（可空号）、状态迁移、提交、摘要冗余刷新；首次保存发号 |
| `CaseRecordHomepageDomainService` | 首页保存、冻结、自动取值合并写侧 |
| `CaseRecordAutoFillQueryService` | 首页自动取值只读（跨聚合 QueryService） |
| `CaseRecordQueryService` | 列表分页读侧（返回 QueryModel） |
| `MedcasnoGenerator` | `CR`+yyMM+4 位年流水；倒序对齐主档最大值 + 年度序列行锁 |

## 5. 领域事件

包路径：`domain.share.events.mrms.caserecord`

| 事件 | 触发 |
| --- | --- |
| `CaseRecordCreatedEvent` | 建档 |
| `CaseRecordSubmittedEvent` | 提交 |
| `CaseRecordReceivedEvent` | 接收 |
| `CaseRecordReturnedEvent` | 退回 |
| `CaseRecordArchivedEvent` | 归档 |

## 6. 读侧 QueryModel

| 类 | 用途 |
| --- | --- |
| `CaseRecordPageQuery` | 列表分页入参 |
| `CaseRecordListItemQueryModel` | 列表行投影 |
| `vo/*Snapshot` | 自动取值中间结构（非 QueryModel） |

## 7. 关联文档

- [MrmsAppService/01-概要设计.md](../../../../应用/zxplat-application/MrmsAppService/01-概要设计.md)
- [模块化开发规范.md §7](../01-架构/模块化开发规范.md)

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`CaseRecordDomainService`

| 方法 |
|------|
| `createIfAbsent` |
| `bootstrapIfAbsent` |
| `requireById` |
| `findByEncounterId` |
| `saveHomepage` |
| `refreshAutoFill` |
| `submit` |
| `transition` |
| `refreshSummary` |
| `ensureArchivedAndFrozenForUpload` |

### DomainService：`CaseRecordHomepageDomainService`

| 方法 |
|------|
| `ensureHomepage` |
| `findOptionalByCaseRecordId` |
| `requireExistingByCaseRecordId` |
| `save` |
| `mergeAutoFillFromHis` |
| `hasFeeSnapshot` |
| `readFeeSnapshot` |
| `refreshFeeSnapshot` |
| `ensureFeeSnapshotSeeded` |
| `fillMissingSelfpayAmt` |
| `listDiagnoses` |
| `listProcedures` |
| `listIcus` |
| `listBlds` |
| `freeze` |
| `unfreeze` |

<!-- MODE-B 2026-10-14：首页 ICU/输血子表；DomainService listIcus/listBlds + save 全量 replace；Flyway V11 -->

### QueryService：`CaseRecordOverdueQueryService`

| 方法 |
|------|
| `findSubmitOverdue` |
| `findArchiveOverdue` |

### QueryService：`CaseRecordQueryService`

| 方法 |
|------|
| `mapByEncounterIds` |
| `queryPage` |
| `queryUploadPage` |
| `findOptionalByMedcasno` |

<!-- MODE-B 2026-09-29：首页手术/麻醉时间窗 BR-MRMS-HP-PR-007/008；落点 MrmsCaseRecordHomepageAppService#assertProcedureTimesWithinStay -->

