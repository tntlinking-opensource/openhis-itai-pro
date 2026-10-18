# DM-CUS-013 ActivityObservationLink · 项目↔临床小项绑定

> **限界上下文**：health · custom（CUS）· 目录组合  
> **表名**：`cus_activity_observation_link`（CUS 全院目录，非 `peis_*`）  
> **版本**：v1.0 · **日期**：2026-06-27  
> **说明**：WKF **ActivityDefinition（项目）** ↔ CLN **ObservationDefinition（小项 OD）** 多对多绑定。  
> **关联**：[DM-WKF-001](./DM-WKF-001-ActivityDefinition-诊疗项目定义.md)、[DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md)、[DM-CLN-007 §6.3](./DM-CLN-007-Observation-临床观察实例.md)、[Peis 04 §3](../../../../应用/zxplat-application/PeisAppService/04-FHIR领域模型.md)、[Lis 01 D10](../../../../应用/zxplat-application/LisAppService/01-概要设计.md)

---

## 1. 概述

维护项目 AD 与小项 OD 的绑定、排序、必检标志；P0 **写入口** 为 PEIS [CFG-04](../AppService/AS-PEIS-CFG-001-体检目录.md)，**领域层归属 CUS 本地扩展**（`domain.custom`，非 PEIS 独占）。

| 维度 | 结论 |
|------|------|
| **领域包** | `com.zxplat.domain.custom.activityobservationlink` |
| **P0 写** | PEIS CFG-04 → `ActivityObservationLinkDomainService#saveLinks` |
| **P0 读** | DES-02；LIS **AUD-05** `projectFromLis`（只读 AOL）；INT-03 补偿；MAS |
| **LIS** | **不维护** 本表；AUD-05 投影内只读 `listByActivityId` |

```text
ActivityDefinition（项目）
  │ cus_activity_observation_link（本 DM，custom 包）
  └── ObservationDefinition（小项 OD）

LIS AUD-05 → projectFromLis（只读 AOL）→ cln_observation
```

---

## 2. 持久化形态

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| activity_definition_id | Long | FK → 项目 AD（WKF） |
| observation_definition_id | Long | FK → `cln_observation_definition` |
| sort_num | Integer | 显示序 |
| required_flag | Boolean | 是否必检小项 |
| tenant_id / org_id | Long | 租户/院区 |

UNIQUE (`activity_definition_id`, `observation_definition_id`)

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `ActivityObservationLink`（按 `activityDefinitionId` 边界） |
| **标识** | 行级 `id`；业务键 `(activityDefinitionId, observationDefinitionId)` |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | PK |
| activityDefinitionId | Long | 是 | 项目 AD |
| observationDefinitionId | Long | 是 | 小项 OD |
| sortNum | Integer | 是 | 排序 |
| requiredFlag | Boolean | 是 | 必检 |
| tenantId | Long | 是 | 租户 |
| orgId | Long | 否 | 院区 |

---

## 4. 消费者矩阵（P0）

| 模块 | 读/写 | 场景 |
|------|:-----:|------|
| PEIS CFG-04 | 写 | 项目维护 `saveLinks` |
| PEIS DES-02 | 读 | 分科小项列表 |
| LIS AUD-05 / CLN-007 | 读 AOL | `projectFromLis` |
| PEIS INT-03 | 读 + 投影 CLN | 补偿 |
| PEIS MAS | 读 | 完成度（可选） |

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `ActivityObservationLinksChangedEvent` | `activityDefinitionId, linkCount` | `saveLinks` |

包路径：`com.zxplat.domain.share.events.custom.activityobservationlink`

---

## 6. 领域服务

### 6.1 写侧 — `ActivityObservationLinkDomainService`

| 方法 | 说明 |
|------|------|
| `saveLinks(SaveActivityObservationLinksCommand)` | 全量对齐 + 软删（CFG-04） |

### 6.2 读侧 — `ActivityObservationLinkQueryService`

| 接口签名 | 场景 |
|----------|------|
| `listByActivityId(activityId): List<ActivityObservationLinkModel>` | CFG-04 / DES-02 / 投影 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-AOL-01** | CFG-04 写入时：项目 AD 须为目录允许的 type_enum（P0 体检项目类） |
| **BR-AOL-02** | 停用 OD 前检查在检 Encounter 引用 |
| **BR-AOL-03** | `saveLinks` 全量对齐 + 软删旧行 |
| **BR-AOL-04** | LIS 不维护本表；`lis_observation`→CLN 对齐在 [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) `projectFromLis` |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-07-19 | 领域成码；`listByActivityId` OD 展示由 AppService enrich（B.6.4） |
| v1.0 | 2026-06-27 | 自 **DM-CLN-009** 迁入 **DM-CUS-013**；包 `domain.custom.activityobservationlink` |
| v2.0 | 2026-06-27 | （CLN-009）自 DM-PEIS-002 迁入；包曾用 `domain.clinical` |
| v1.x | — | 见 Git 历史 `DM-PEIS-002-ActivityObservationLink-项目小项绑定.md` |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ActivityObservationLinkDomainService`

| 方法 |
|------|
| `saveLinks` |

### QueryService：`ActivityObservationLinkQueryService`

| 方法 |
|------|
| `listByActivityId` |
| `countDistinctActivitiesByObservationDefinitionIds` |
| `mapByActivityIds` |

