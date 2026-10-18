# DM-CMP-STU-001 PatientStudent · 学生档案

> **表名**：`cmp_patient_student`  
> **版本**：v1.1 · **日期**：2026-07-19  
> **应用服务编号**：AS-CMP-STU-001
> **遗留**：`peis_customer_student`、`his_gf_person`（学生）  
> **绑定**：`patient_id` → health `adm_patient.id`（仅主索引；health **无**学籍子表）

---

## 1. 概述

高校学生学籍档案真源，归属 **campus-domain**。与教职工 `cmp_patient_staff` 对称。  
公费卡号/`gf_status` 仍在 `cmp_pe_profile`（subject=STUDENT + 本表 id）。

**迁移口径**：一次性把 peis 学生（`peis_customer_student` 等）/ 遗留 HIS 洗入本表（见 [00-功能清单 §五](./00-功能清单.md)）。health ADM **不再提供** `adm_patient_student`。

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| org_id | int8 | 可空 |
| patient_id | int8 | → `adm_patient.id`，可空（孤儿档案） |
| name | varchar(128) | |
| py_str / wb_str | varchar(64) | |
| gender_enum | varchar(32) | |
| age | int4 | 可空；可派生 |
| birthday | date | P1；可从身份证推 |
| student_no | varchar(64) | **学号，租户内唯一** |
| id_card | varchar(32) | |
| phone | varchar(32) | |
| college_id | int8 | → `cmp_campus_college.id` **必填** |
| college_name | varchar(128) | 学院名快照 |
| campus_code | varchar(64) | **所属校区编码，必填** |
| major | varchar(128) | 专业名称 |
| major_code | varchar(64) | P1 |
| edu_level_enum | varchar(32) | 预科/本/硕/博 |
| enroll_time / leave_time | timestamptz | |
| grade | varchar(32) | 年级 |
| **class_name** | varchar(128) | **行政班**；团检按班级筛名册 |
| school_year | int4 | P1 |
| study_type_enum | varchar(32) | 全日制等 |
| school_status_enum | varchar(32) | 在校/休学/离校 |
| physical_exam_result_enum | varchar(32) | 未检/合格/不合格 |
| native_place | varchar(128) | P1 |
| counselor_name / counselor_phone | varchar(128)/(32) | 辅导员快照 |
| address / doorplate | varchar | P2 |
| active_flag | bool | |
| version / 审计 / 软删 | 标准列 | |

唯一：`(tenant_id, student_no)` 未删；可选 `(tenant_id, id_card)` 非空唯一。

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `PatientStudent`（campus） |
| **业务键** | `(tenantId, studentNo)` |

### 3.1 字段（P0）

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | |
| tenantId | Long | 是 | |
| patientId | Long | 否 | 绑定后业务态建议有 |
| name | String | 是 | |
| studentNo | String | 是 | ← peis `student_id` |
| collegeId | Long | 是 | |
| campusCode | String | 是 | |
| collegeName | String | 否 | 快照 |
| idCard / phone / genderEnum | | 否 | |
| eduLevelEnum | String | 否 | |
| schoolStatusEnum | String | 是 | 默认在校 |
| physicalExamResultEnum | String | 否 | |
| enrollTime / leaveTime | LocalDateTime | 否 | |
| grade / major | String | 否 | |
| counselorName / counselorPhone | String | 否 | |
| activeFlag | Boolean | 是 | |

### 3.2 枚举摘要

| 字段 | 取值（示例） |
|------|----------------|
| schoolStatusEnum | `IN_SCHOOL` / `SUSPENDED` / `LEFT` |
| physicalExamResultEnum | `NOT_DONE` / `FAIL` / `PASS` |
| eduLevelEnum | `PREP` / `BACHELOR` / `MASTER` / `DOCTOR` |

---

## 5. 领域事件

| 事件 | 触发 |
|------|------|
| `PatientStudentUpsertedEvent` | 增改 |
| `PatientStudentBoundEvent` | 绑 Patient |
| `PatientStudentDisabledEvent` | 停用 |
| （转院） | 见 `CampusArchiveTransferredEvent` |

---

## 6. 领域服务

### 6.1 写侧 — `PatientStudentDomainService`（campus）

| 方法 | 说明 |
|------|------|
| `create(CreatePatientStudentCommand)` | 学号唯一；学院/校区启用 |
| `update(UpdatePatientStudentCommand)` | |
| `bindPatient(BindStudentPatientCommand)` | 身份证与 Patient 一致 |
| `disable(DisablePatientStudentCommand)` | |
| `updateOrgForTransfer(...)` | 转院时改 collegeId/campusCode（由 Transfer AS 编排） |

### 6.2 读侧 — `PatientStudentQueryService`

| 接口签名 | 场景 |
|----------|------|
| `page` / `getById` | STU |
| `findByStudentNo` / `findByPatientId` / `findByIdCard` | 绑定、Peis、收银 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CMP-STU-01** | `(tenantId, studentNo)` 未删唯一 |
| **BR-CMP-STU-02** | `collegeId`、`campusCode` 对应组织须启用 |
| **BR-CMP-STU-03** | 学院若绑校区，须与 `campusCode` 一致（或学院全校通用） |
| **BR-CMP-STU-04** | 绑定后 `idCard` 须与 Patient 一致 |
| **BR-CMP-STU-05** | 增删改写 ChangeLog；组织变更写 Transfer |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-07-19 | 明确 health 已移除 `adm_patient_student`；迁移仅 peis → cmp |
| v1.0 | 2026-07-19 | 学籍迁入 campus：`cmp_patient_student` |
