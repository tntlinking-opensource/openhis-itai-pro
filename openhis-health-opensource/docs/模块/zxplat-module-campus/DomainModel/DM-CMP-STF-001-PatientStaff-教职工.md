# DM-CMP-STF-001 PatientStaff · 教职工档案

> **表名**：`cmp_patient_staff`  
> **版本**：v1.1 · **日期**：2026-07-19  
> **应用服务编号**：AS-CMP-STF-001
> **遗留**：`peis_customer_teacher`（工号主键）、`his_gf_person`（老师，医疗证）

---

## 1. 概述

教职工档案与组织归属；绑定 health `Patient`。公费卡/医疗证细节见 `PublicExpenseProfile`（可同事务维护）。

**主键语义（对齐 old_peis）**

| 键 | 优先级 | 说明 |
|----|--------|------|
| `employee_no` | **P0 必填** | ← `work_id`；微信绑定/检索主业务键 |
| `medical_code` | 可空；有则唯一 | ← HIS/nenu 医疗证；旧 peis **无此列**，迁入时多为空 |

---

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| org_id | int8 | 可空 |
| patient_id | int8 | FK → `adm_patient.id`，可空 |
| name | varchar(128) | 快照姓名 |
| py_str / wb_str | varchar(64) | |
| gender_enum | varchar(32) | |
| id_card | varchar(32) | |
| phone | varchar(32) | |
| employee_no | varchar(64) | **工号，租户内唯一** |
| medical_code | varchar(64) | 医疗证号，可空；非空则租户内唯一 |
| department_id | int8 | FK → `cmp_campus_department`（← 旧 `work_unit` 字典洗数，**非**旧 teacher.college_id） |
| campus_site_id | int8 | FK 办公校区 |
| staff_type_enum | varchar(32) | ← 旧 `customer_type` / 在编离退休（P1） |
| valid_from / valid_to | timestamptz | 医疗证有效期（无证可空） |
| job_status_enum | varchar(32) | ← `work_status`：ACTIVE/LEFT/RETIRED/OTHER |
| active_flag | bool | |
| version / 审计 / 软删 | 标准列 | |

> **不迁入 Campus**：旧 `check_status`（体检检查态）→ 归 Peis 订单/扩展。

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `PatientStaff` |
| **业务键** | `(tenantId, employeeNo)` 唯一（未删）；`(tenantId, medicalCode)` 在 medicalCode 非空时唯一 |

### 3.1 字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | |
| tenantId | Long | 是 | |
| patientId | Long | 否 | |
| name | String | 是 | |
| employeeNo | String | **是** | ← work_id |
| medicalCode | String | **否** | 有则唯一；同步 pe_profile.medical_code |
| departmentId | Long | 是 | |
| campusSiteId | Long | 是 | |
| idCard | String | 否 | |
| phone | String | 否 | |
| validFrom / validTo | LocalDateTime | 否 | |
| jobStatusEnum | String | 是 | 默认 `ACTIVE`；映射 0/1/2/3 |
| staffTypeEnum | String | 否 | P1 |
| activeFlag | Boolean | 是 | |

### 3.2 `job_status_enum` 映射

| 码 | 旧 work_status |
|----|----------------|
| `ACTIVE` | 0 在职 |
| `LEFT` | 1 离职 |
| `RETIRED` | 2 退休 |
| `OTHER` | 3 其他 |

---

## 5. 领域事件

| 事件 | 触发 |
|------|------|
| `PatientStaffUpsertedEvent` | 增改 |
| `PatientStaffBoundEvent` | 绑定 Patient |
| `PatientStaffDisabledEvent` | 停用 |

---

## 6. 领域服务

### 6.1 写侧 — `PatientStaffDomainService`

| 方法 | 说明 |
|------|------|
| `create(CreatePatientStaffCommand)` | 校验工号唯一；部门/校区启用；medicalCode 若填则唯一 |
| `update(UpdatePatientStaffCommand)` | |
| `bindPatient(BindStaffPatientCommand)` | |
| `disable(DisablePatientStaffCommand)` | |

### 6.2 读侧 — `PatientStaffQueryService`

| 接口签名 | 场景 |
|----------|------|
| `page` / `getById` | STF |
| `findByEmployeeNo` | 微信绑定、登记读卡 |
| `findByMedicalCode` / `findByPatientId` | ARCH / 收银 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CMP-STF-01** | `(tenantId, employeeNo)` 未删唯一 |
| **BR-CMP-STF-02** | `medicalCode` 非空时 `(tenantId, medicalCode)` 唯一 |
| **BR-CMP-STF-03** | `departmentId`、`campusSiteId` 须启用 |
| **BR-CMP-STF-04** | 绑定后 `idCard` 须与 Patient 一致 |
| **BR-CMP-STF-05** | 变更写 ChangeLog；医疗证变更同步 pe_profile |
| **BR-CMP-STF-06** | 旧 `teacher.college_id`（注释「科室」）**不得**直接当 CampusCollege FK；单位走 `work_unit`→Department |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 成码首版 |
| v1.1 | 2026-07-19 | 工号 P0 必填；医疗证可空；对齐 peis_customer_teacher |
