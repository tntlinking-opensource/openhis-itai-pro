# DM-PEIS-003 PeisCommonResult · 常见结果模板

> **表名**：`peis_common_result`  
> **版本**：v1.1 · **日期**：2026-06-27

---

## 1. 概述

分科诊台 **常见结果** 模板库；DES-03 按小项/科室套用。

---

## 2. 持久化形态

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| observation_definition_id | Long | 可选；绑定小项 |
| dept_org_id | Long | 可选；科室 |
| display | String | 模板名称 |
| value_string | String | 默认结果值 |
| interpretation_code | String | H/L/N 等 |
| sort_num | Integer | 排序 |
| status_enum | String | active / inactive |
| tenant_id / org_id | Long | 租户/院区 |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `PeisCommonResult` |
| **标识** | `id` |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | PK |
| observationDefinitionId | Long | 否 | 绑定小项 |
| deptOrgId | Long | 否 | 科室 |
| display | String | 是 | 名称 |
| valueString | String | 否 | 默认值 |
| interpretationCode | String | 否 | 解读码 |
| sortNum | Integer | 是 | 排序 |
| statusEnum | String | 是 | active/inactive |
| tenantId | Long | 是 | 租户 |
| orgId | Long | 否 | 院区 |

---

## 5. 领域事件

无。模板变更由 AppService 可选发布 `PeisCommonResultChangedEvent`（P2）。

---

## 6. 领域服务

### 6.1 写侧 — `PeisCommonResultDomainService`

| 方法 | 说明 |
|------|------|
| `save(SavePeisCommonResultCommand)` | 新建/更新 |
| `deactivate(DeactivatePeisCommonResultCommand)` | 停用 |

### 6.2 读侧 — `PeisCommonResultQueryService`

| 接口签名 | 场景 |
|----------|------|
| `listByOdId(odId): List<PeisCommonResultModel>` | DES-03 |
| `search(PeisCommonResultQuery, Pageable): Page<PeisCommonResultModel>` | CFG 维护 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-PCR-01** | `display` 同 tenant+od+dept 下唯一（active） |
| **BR-PCR-02** | 停用仅改 status，不物理删 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 补 §3/§6.1 写方法 |
| v1.0 | 2026-06-27 | 首版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PeisCommonResultDomainService`

| 方法 |
|------|
| `save` |
| `deactivate` |

### QueryService：`PeisCommonResultQueryService`

| 方法 |
|------|
| `findById` |
| `listByOdId` |
| `search` |

