# DM-CMP-ARCH-002 CampusArchiveTransfer · 调动/转校区记录

> **表名**：`cmp_archive_transfer`  
> **版本**：v1.0 · **日期**：2026-07-19  
> **AS**：学生/教职工详情「调动记录」Tab；AS-CMP-STU-001、AS-CMP-STF-001  
> **口径**：**转院** = 学生 **跨校区/跨学院** 调动（非就医转院）；**调岗** = 教职工 **跨部门/跨校区** 调动

---

## 1. 概述

一等业务记录，支持 **登记 + 时间线查看**。与字段级 `CampusArchiveChangeLog` 并存：

| 能力 | ChangeLog | Transfer（本表） |
|------|-----------|------------------|
| 任意字段改前/改后 | ✅ | — |
| 调岗/转校区结构化时间线 | 可筛字段 | ✅ 专用 |
| 生效日、原因、审批单号 | — | ✅ |

写调动时：同事务 **先改档案组织字段 → 写本表一行 → 写 ChangeLog（组织字段 diff）**。

---

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| subject_type_enum | varchar(32) | `STUDENT` / `STAFF` |
| subject_id | int8 | 学生/教职工档案 id |
| patient_id | int8 | 可空 |
| transfer_type_enum | varchar(32) | 见 §2.1 |
| effective_date | date | 生效日（可=操作日） |
| from_campus_code | varchar(64) | 调出校区 |
| to_campus_code | varchar(64) | 调入校区 |
| from_college_id | int8 | 学生：调出学院 |
| to_college_id | int8 | 学生：调入学院 |
| from_department_id | int8 | 教职工：调出部门 |
| to_department_id | int8 | 教职工：调入部门 |
| reason | varchar(512) | 原因/备注 |
| external_ref | varchar(128) | 审批单号/教务文号（可空） |
| transfer_source_enum | varchar(32) | `MANUAL` / `IMPORT` / `SYNC` / `UPDATE_DERIVED` |
| operator_id / operator_name | | |
| operate_time | timestamptz | |
| change_log_batch_id | varchar(64) | 关联同次 ChangeLog batch |
| version / 审计 | 标准列 | 本表一般不软删 |

索引：`(tenant_id, subject_type_enum, subject_id, effective_date DESC)`；`(tenant_id, to_campus_code, effective_date)`。

### 2.1 `transfer_type_enum`

| 码 | 适用 | 含义 |
|----|------|------|
| `CAMPUS` | 学生/教职工 | 仅校区变更 |
| `COLLEGE` | 学生 | 仅学院变更（校区可不变） |
| `CAMPUS_COLLEGE` | 学生 | 校区+学院同时变（转院常见） |
| `DEPARTMENT` | 教职工 | 仅部门变更（调岗） |
| `CAMPUS_DEPARTMENT` | 教职工 | 校区+部门同时变 |
| `OTHER` | 少见 | 其它组织调整 |

---

## 3. 实体字段

| 字段名 | 类型 | 必填 |
|--------|------|:--:|
| id | Long | 系统 |
| tenantId | Long | 是 |
| subjectTypeEnum | String | 是 |
| subjectId | Long | 是 |
| patientId | Long | 否 |
| transferTypeEnum | String | 是 |
| effectiveDate | LocalDate | 是 |
| fromCampusCode / toCampusCode | String | 视类型 |
| fromCollegeId / toCollegeId | Long | 学生场景 |
| fromDepartmentId / toDepartmentId | Long | 教职工场景 |
| reason | String | 否 |
| externalRef | String | 否 |
| transferSourceEnum | String | 是 |
| operatorId / operatorName | | 否 |
| operateTime | LocalDateTime | 是 |
| changeLogBatchId | String | 否 |

---

## 5. 领域事件

| 事件 | 触发 | 消费 |
|------|------|------|
| `CampusArchiveTransferredEvent` | 调动登记成功 | Peis 可选刷新校区；统计 |

---

## 6. 领域服务

### 6.1 写侧 — `ArchiveTransferDomainService`

| 方法 | 说明 |
|------|------|
| `recordStudentTransfer(RecordStudentTransferCommand)` | 校验目标学院/校区启用；更新 `cmp_patient_student` 组织字段后落本表 |
| `recordStaffTransfer(RecordStaffTransferCommand)` | 校验目标部门/校区；更新 staff 后落本表 |
| `recordDerivedFromUpdate(...)` | 普通编辑改了组织字段时自动补一条（`UPDATE_DERIVED`） |

### 6.2 读侧 — `ArchiveTransferQueryService`

| 接口签名 | 场景 |
|----------|------|
| `pageBySubject(tenantId, type, subjectId, page)` | 详情 Tab 时间线 |
| `page(ArchiveTransferPageQuery)` | 管理端：按校区/学院/部门/日期筛调动流水 |
| `getById(tenantId, id)` | 单条详情（含 from/to 名称解析） |

**QueryModel 展示字段**：transferType、effectiveDate、from/to 校区名、学院名或部门名、reason、operatorName、operateTime、externalRef。

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CMP-XFR-01** | from/to 至少一侧组织字段有差异，禁止空调动 |
| **BR-CMP-XFR-02** | 目标校区/学院/部门须存在且启用 |
| **BR-CMP-XFR-03** | 学生转院：`toCollegeId` 若绑了校区，须与 `toCampusCode` 一致（或学院全校通用） |
| **BR-CMP-XFR-04** | 正式调动（MANUAL）须填 `effectiveDate`；默认同日 |
| **BR-CMP-XFR-05** | 同事务写 ChangeLog；`change_log_batch_id` 可关联 |
| **BR-CMP-XFR-06** | 查看权限：有档案详情权即可看本人调动时间线；流水查询需管理角色 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 首版：支持学生转院/教职工调岗登记与查看 |
