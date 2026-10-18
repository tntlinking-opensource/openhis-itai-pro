# DM-CLN-005 ObservationDefinition · 临床观察定义（目录）

> **限界上下文**：health · clinical  
> **表名**：`cln_observation_definition`  
> **版本**：v2.0 · **日期**：2026-06-27  
> **关联**：「Peis CFG-03」、实例见 [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)

---

## 1. 概述

**ObservationDefinition（OD）** 为临床观察 **目录**（体检小项、体格项、分科结论项等），对齐 FHIR `ObservationDefinition`。实例事实见 **DM-CLN-007**。

---

## 2. 持久化形态

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| code | String | 院内码 |
| display | String | 显示名 |
| value_type_enum | String | quantity / string / codeable / boolean |
| unit_code | String | 默认单位 |
| category_code | String | exam_item / vitals / **dept_conclusion** / other |
| status_enum | String | active / inactive |
| tenant_id / org_id | Long | 租户/院区 |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `ObservationDefinition` |
| **标识** | `id` |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | PK |
| code | String | 是 | tenant 内唯一 |
| display | String | 是 | |
| valueTypeEnum | String | 是 | |
| unitCode | String | 否 | |
| categoryCode | String | 是 | |
| statusEnum | String | 是 | active/inactive |
| tenantId | Long | 是 | |
| orgId | Long | 否 | |

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `ObservationDefinitionSavedEvent` | `id, code, categoryCode, statusEnum` | `save` |
| `ObservationDefinitionDeactivatedEvent` | `id` | `deactivate` |

包路径：`com.zxplat.domain.share.events.clinical.observationdefinition`

---

## 6. 领域服务

### 6.1 写侧 — `ObservationDefinitionDomainService`

| 方法 | 说明 | IF |
|------|------|-----|
| `save(SaveObservationDefinitionCommand)` | 新建/更新 | CFG-03 |
| `deactivate(DeactivateObservationDefinitionCommand)` | 停用 | CFG-03 |

### 6.2 读侧 — `ObservationDefinitionQueryService`

| 方法 | 说明 |
|------|------|
| `getById(id): ObservationDefinitionModel` | |
| `search(ObservationDefinitionQuery, Pageable): Page<ObservationDefinitionModel>` | CFG-03 列表 |
| `listByCategory(categoryCode): List<ObservationDefinitionModel>` | DES 下拉 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CLN-OD-01** | `(tenantId, code)` 唯一（active） |
| **BR-CLN-OD-02** | 停用前检查在检 Encounter 引用（AS 预检） |
| **BR-CLN-OD-03** | `dept_conclusion` 类 OD 供 DES-04，与小项 OD 分 category |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.1 | 2026-07-19 | 领域成码：`domain.clinical.observationdefinition` |
| v2.0 | 2026-06-27 | **拆分**：原 DM-CLN-005 目录部分；实例迁至 DM-CLN-007 |
| v1.x | — | 见 Git 历史（原合并稿「Observation 临床观察」，已拆至 005 定义 + 007 实例） |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ObservationDefinitionDomainService`

| 方法 |
|------|
| `save` |
| `deactivate` |
| `delete` |

### QueryService：`ObservationDefinitionQueryService`

| 方法 |
|------|
| `getById` |
| `findOptionalById` |
| `search` |
| `listByCategory` |
| `listByIds` |

