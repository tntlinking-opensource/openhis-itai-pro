# DM-CMP-ORG-002 CampusCollege · 学院

> **表名**：`cmp_campus_college`  
> **版本**：v1.1 · **日期**：2026-07-19  
> **应用服务编号**：AS-CMP-ORG-COL-001
> **遗留（三源合一）**：见 §1.1

---

## 1. 概述

教学院系/部主数据；学生档案 primary 归属；Peis 团检/小程序学院下拉 **统一读** 本表 Client。

### 1.1 旧 peis 三源 → 本表

| 旧源 | 旧用途 | 迁入策略 |
|------|--------|----------|
| `peis_company`（`type=0`） | PC 学生 `college_id` FK、导入校验 | **主迁移源** → `id` 映射或按名称合并 |
| `peis_company_college` | 小程序体检表学院名称下拉 | 按 `company_name`/`company_code` 去重并入；`college_id`(String) → `external_college_id` |
| 字典 `work_unit` | 教职工工作单位（文本 key） | **不进本院系表**；洗入 `CampusDepartment`（见 DEP） |

成码后禁止再维护 `peis_company_college` / 用 `peis_company` 充当学院真源。

---

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| org_id | int8 | 可空 |
| college_code | varchar(64) | 院内编码 |
| college_name | varchar(128) | 名称 |
| external_college_id | varchar(64) | 教务对照键（P1） |
| campus_site_id | int8 | FK → `cmp_campus_site.id`，可空=全校通用 |
| active_flag | bool | |
| sort_order | int4 | |
| remark | varchar(512) | |
| version / 审计 / 软删 | 标准列 | |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `CampusCollege` |
| **业务键** | `(tenantId, collegeCode)` 唯一（未删） |

### 3.1 字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | |
| tenantId | Long | 是 | |
| orgId | Long | 否 | |
| collegeCode | String | 是 | |
| collegeName | String | 是 | |
| externalCollegeId | String | 否 | P1 |
| campusSiteId | Long | 否 | 空=全校 |
| activeFlag | Boolean | 是 | |
| sortOrder | Integer | 是 | |
| remark | String | 否 | |

---

## 5. 领域事件

| 事件 | 触发 |
|------|------|
| `CampusCollegeUpsertedEvent` | 增改 |
| `CampusCollegeStatusChangedEvent` | 停用/启用 |

---

## 6. 领域服务

### 6.1 写侧 — `CampusCollegeDomainService`

| 方法 | 说明 |
|------|------|
| `create(CreateCampusCollegeCommand)` | 校验编码；若绑校区则校区须启用 |
| `update(UpdateCampusCollegeCommand)` | 编码不可改 |
| `changeActive(ChangeCampusCollegeActiveCommand)` | 停用/启用 |
| `bindCampus(BindCollegeCampusCommand)` | 绑定/清空所属校区 |

### 6.2 读侧 — `CampusCollegeQueryService`

| 接口签名 | 场景 |
|----------|------|
| `page(CampusCollegePageQuery)` | COL-01 |
| `getById(tenantId, id)` | COL-09 |
| `listActive(tenantId, campusCode?)` | COL-08 / INT-04 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CMP-COL-01** | `(tenantId, collegeCode)` 未删唯一 |
| **BR-CMP-COL-02** | `campusSiteId` 非空时目标校区须存在且启用 |
| **BR-CMP-COL-03** | 停用学院：已绑学生档案仍可读历史，新建学生不可选 |
| **BR-CMP-COL-04** | 不得与临床 `sys_dept` 混用 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 成码首版 |
| v1.1 | 2026-07-19 | 补三源迁移说明（company / company_college / work_unit） |
