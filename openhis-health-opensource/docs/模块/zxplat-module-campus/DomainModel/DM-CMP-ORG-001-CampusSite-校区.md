# DM-CMP-ORG-001 CampusSite · 校区

> **表名**：`cmp_campus_site`  
> **版本**：v1.1 · **日期**：2026-07-19  
> **应用服务编号**：AS-CMP-ORG-CMP-001
> **详细设计编号**：DM-CMP-ORG-001-CampusSite

---

## 1. 概述

多校区主数据真源。支持 **N≥1**；单校区院点仅维护 1 条默认校区。  
遗留：硬编码 `BENBU` / `JINGYUE`。

**边界**：本表是档案/组织用的校区字典。旧 `peis_student_exam_fee_paid.campus_code`（缴费白名单）与号源日程 **仍属 Peis**；学生档案上的 `campus_code`（STU-11）可与白名单对齐，但维护职责分离。

---

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | 租户 |
| org_id | int8 | 院区（可空） |
| campus_code | varchar(64) | 业务编码，租户内唯一 |
| campus_name | varchar(128) | 显示名 |
| short_name | varchar(64) | 简称 |
| address | varchar(512) | 地址 |
| is_primary | bool | 权威/主校区（多节点同步冲突裁决） |
| active_flag | bool | 启用 |
| sort_order | int4 | 排序 |
| remark | varchar(512) | 备注 |
| version / 审计 / 软删 | 标准列 | 同 peis 规范 |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `CampusSite` |
| **业务键** | `(tenantId, campusCode)` 唯一（未删） |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | PK |
| tenantId | Long | 是 | 租户 |
| orgId | Long | 否 | 院区 |
| campusCode | String | 是 | 如 `BENBU`、`MAIN` |
| campusName | String | 是 | |
| shortName | String | 否 | |
| address | String | 否 | |
| isPrimary | Boolean | 是 | 默认 false；租户内建议仅 1 个 true |
| activeFlag | Boolean | 是 | 默认 true |
| sortOrder | Integer | 是 | 默认 0 |
| remark | String | 否 | |

---

## 5. 领域事件

| 事件 | 触发 |
|------|------|
| `CampusSiteUpsertedEvent` | 新增/编辑成功 |
| `CampusSiteStatusChangedEvent` | 启用/停用 |

---

## 6. 领域服务

### 6.1 写侧 — `CampusSiteDomainService`

| 方法 | 说明 |
|------|------|
| `create(CreateCampusSiteCommand)` | 新建；校验编码唯一 |
| `update(UpdateCampusSiteCommand)` | 编辑；编码不可改 |
| `changeActive(ChangeCampusSiteActiveCommand)` | 启用/停用 |
| `setPrimary(SetCampusSitePrimaryCommand)` | 设主校区（清除同租户其他 isPrimary） |

### 6.2 读侧 — `CampusSiteQueryService`

| 接口签名 | 场景 |
|----------|------|
| `page(CampusSitePageQuery): Page<CampusSiteQueryModel>` | CMP-01 |
| `getById(tenantId, id): CampusSiteQueryModel` | 详情 |
| `getByCode(tenantId, campusCode): CampusSiteQueryModel` | CMP-08 |
| `listActive(tenantId): List<CampusSiteQueryModel>` | CMP-07 Client |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CMP-SITE-01** | `(tenantId, campusCode)` 未删唯一 |
| **BR-CMP-SITE-02** | `campusCode` 仅字母数字下划线，长度 2～32 |
| **BR-CMP-SITE-03** | 停用前：无启用学院/部门引用时可硬拦，或仅软拦+警告（院点参数；默认软拦） |
| **BR-CMP-SITE-04** | `setPrimary` 同租户仅保留一条 `isPrimary=true` |
| **BR-CMP-SITE-05** | 单校区院点必须存在至少一条启用校区（SYNC-08） |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 成码首版 |
| v1.1 | 2026-07-19 | 划清与 Peis 缴费白名单/号源边界 |
