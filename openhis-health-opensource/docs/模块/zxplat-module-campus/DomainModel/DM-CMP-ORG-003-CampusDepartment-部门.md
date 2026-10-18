# DM-CMP-ORG-003 CampusDepartment · 部门/单位

> **表名**：`cmp_campus_department`  
> **版本**：v1.0 · **日期**：2026-07-19  
> **应用服务编号**：AS-CMP-ORG-DEP-001
> **遗留**：`his_gf_person.C_RELATIONUNIT`；old_peis 字典 **`work_unit`**（教职工主迁移源）

---

## 1. 概述

教职工所在人事/行政单位（**非** HIS 临床科室）。可直属校区，或隶属学院。

旧 `peis_customer_teacher.college_id`（注释「科室」）**不得**直接映射为本表或学院；单位以 `work_unit` 洗入本表为准。

---

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| org_id | int8 | 可空 |
| department_code | varchar(64) | |
| department_name | varchar(128) | |
| campus_site_id | int8 | FK 校区，**必填** |
| college_id | int8 | FK 学院，可空=直属校区 |
| dept_type_enum | varchar(32) | 行政/教学/后勤/附属（P1） |
| active_flag | bool | |
| sort_order | int4 | |
| remark | varchar(512) | |
| version / 审计 / 软删 | 标准列 | |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `CampusDepartment` |
| **业务键** | `(tenantId, departmentCode)` 唯一（未删） |

### 3.1 字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | |
| tenantId | Long | 是 | |
| orgId | Long | 否 | |
| departmentCode | String | 是 | |
| departmentName | String | 是 | |
| campusSiteId | Long | 是 | 办公所属校区 |
| collegeId | Long | 否 | 空=直属校区 |
| deptTypeEnum | String | 否 | P1 |
| activeFlag | Boolean | 是 | |
| sortOrder | Integer | 是 | |
| remark | String | 否 | |

---

## 5. 领域事件

| 事件 | 触发 |
|------|------|
| `CampusDepartmentUpsertedEvent` | 增改 |
| `CampusDepartmentStatusChangedEvent` | 停用/启用 |

---

## 6. 领域服务

### 6.1 写侧 — `CampusDepartmentDomainService`

| 方法 | 说明 |
|------|------|
| `create(CreateCampusDepartmentCommand)` | |
| `update(UpdateCampusDepartmentCommand)` | 编码不可改 |
| `changeActive(ChangeCampusDepartmentActiveCommand)` | |
| `reparent(ReparentDepartmentCommand)` | 调整校区/学院归属 |

### 6.2 读侧 — `CampusDepartmentQueryService`

| 接口签名 | 场景 |
|----------|------|
| `page(CampusDepartmentPageQuery)` | DEP-01 |
| `getById(tenantId, id)` | DEP-09 |
| `list(tenantId, collegeId?, campusCode?)` | DEP-08 |
| `tree(tenantId, campusCode?)` | DEP-06：校区→学院→部门 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CMP-DEP-01** | `(tenantId, departmentCode)` 未删唯一 |
| **BR-CMP-DEP-02** | `campusSiteId` 必须启用 |
| **BR-CMP-DEP-03** | `collegeId` 非空时学院须存在；若学院绑了校区，须与 `campusSiteId` 一致或学院为全校通用 |
| **BR-CMP-DEP-04** | 禁止写入临床科室表 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 成码首版 |
