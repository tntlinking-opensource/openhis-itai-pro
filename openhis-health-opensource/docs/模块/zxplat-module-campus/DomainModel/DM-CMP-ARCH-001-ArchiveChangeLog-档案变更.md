# DM-CMP-ARCH-001 CampusArchiveChangeLog · 档案变更记录

> **表名**：`cmp_archive_change_log`  
> **版本**：v1.0 · **日期**：2026-07-19  
> **AS**：STU/STF 详情 Tab + AS-CMP-ARCH-001

---

## 1. 概述

学生/教职工档案 **字段级** 变更历史（改前/改后、操作人、来源）。替代遗留仅保留 `C_EDITUSER/C_EDITTIME`。

---

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| subject_type_enum | varchar(32) | `STUDENT` / `STAFF` |
| subject_id | int8 | → `cmp_patient_student.id` / `cmp_patient_staff.id` |
| change_type_enum | varchar(32) | CREATE/UPDATE/DELETE/IMPORT/SYNC |
| field_name | varchar(128) | 字段名；整单可写 `*` 或 JSON 多行多条 |
| old_value | text | |
| new_value | text | |
| operator_id | int8 | |
| operator_name | varchar(128) | |
| change_time | timestamptz | |
| change_source_enum | varchar(32) | MANUAL/IMPORT/SYNC/PEIS/SYSTEM |
| campus_code | varchar(64) | 可空 |
| remark | varchar(512) | |
| created_at 等审计 | 标准列 | 本表通常不软删 |

> **落库策略**：一次 UPDATE 多字段 → **多行**（每字段一行），同一 `batch_id`（varchar）关联。

增补列：`batch_id varchar(64)`。

---

## 3. 实体

| 字段名 | 类型 | 必填 |
|--------|------|:--:|
| id | Long | 系统 |
| tenantId | Long | 是 |
| subjectTypeEnum | String | 是 |
| subjectId | Long | 是 |
| changeTypeEnum | String | 是 |
| fieldName | String | 是 |
| oldValue / newValue | String | 否 |
| operatorId | Long | 否 |
| operatorName | String | 否 |
| changeTime | LocalDateTime | 是 |
| changeSourceEnum | String | 是 |
| campusCode | String | 否 |
| batchId | String | 是 |
| remark | String | 否 |

---

## 5. 领域事件

无（审计表；由调用方事务内写入）。

---

## 6. 领域服务

### 6.1 写侧 — `ArchiveChangeLogDomainService`

| 方法 | 说明 |
|------|------|
| `append(AppendArchiveChangeCommand)` | 单条 |
| `appendBatch(AppendArchiveChangeBatchCommand)` | 同 batch 多字段 |

### 6.2 读侧 — `ArchiveChangeLogQueryService`

| 接口签名 | 场景 |
|----------|------|
| `page(ArchiveChangePageQuery)` | STU-18 / STF-16 / ARCH-09 |
| `listByBatch(tenantId, batchId)` | STU-19 详情 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CMP-LOG-01** | 档案写路径必须同事务写变更（禁止静默改） |
| **BR-CMP-LOG-02** | CREATE：old 空；DELETE：new 空 |
| **BR-CMP-LOG-03** | 保留策略（条数/年限）P2，默认不删 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 成码首版 |
