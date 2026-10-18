# DM-CLN-007 Observation · 临床观察实例

> **限界上下文**：health · clinical  
> **表名**：`cln_observation`  
> **版本**：v1.2 · **日期**：2026-06-27  
> **关联**：目录 [DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md)、LIS [DM-LIS-002](./DM-LIS-002-Observation-检验结果.md)、[Peis 04 §5.2](../../../../应用/zxplat-application/PeisAppService/04-FHIR领域模型.md)

---

## 1. 概述

**Observation 实例** 存储分科/体检手工录入及 LIS/PACS **投影** 的观察事实。与 LIS 分层：

| 场景 | 表 | 规则 |
|------|-----|------|
| LIS ingress / 审核 | `lis_observation` | LIS 真源 |
| **LIS AUD-05 发布后** | `cln_observation` | **`LisResultProjectionService#projectFromLis`**（P0 主路径）；须 `source_observation_id` |
| PEIS 诊台补偿刷新 | `cln_observation` | INT-03 / DES-08（漏投影或院点关闭自动投影时） |
| 手工录入 | `cln_observation` | 无 source |

**禁止** `peis_observation` 平行表。

### 1.1 分科结论（DES-04）

分科结论与小项共用 **`cln_observation`**；OD.`categoryCode=dept_conclusion`。**不建** `peis_dept_conclusion`。

---

## 2. 持久化形态

见 Flyway health V3 `cln_observation`；字段与 §3.1 一致。

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `Observation` |
| **标识** | `id` |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | PK |
| encounterId | Long | 是 | |
| serviceRequestId | Long | 否 | 体检项目 SR |
| definitionId | Long | 是 | FK → OD |
| code | String | 否 | 冗余 |
| valueQuantity | BigDecimal | 否 | |
| valueString | String | 否 | |
| valueCodeableConcept | String | 否 | |
| interpretationCode | String | 否 | |
| statusEnum | String | 是 | preliminary/final/amended/cancelled |
| effectiveTime | LocalDateTime | 否 | |
| sourceObservationId | Long | 否 | → lis_observation.id |
| sourceModuleEnum | String | 否 | LIS/PACS/PEIS/DEPT |
| performerId | Long | 否 | |
| tenantId | Long | 是 | |
| orgId | Long | 否 | |

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `ObservationCreatedEvent` | `id, encounterId, definitionId` | `createObservation` |
| `ObservationUpdatedEvent` | `id, statusEnum` | `updateObservation` |
| `ObservationFinalizedEvent` | `id, encounterId` | `finalizeObservation` |
| `ObservationProjectedFromLisEvent` | `id, serviceRequestId, sourceObservationId` | `projectFromLis` |

包路径：`com.zxplat.domain.share.events.clinical.observation`

---

## 6. 领域服务

### 6.1 写侧 — `ObservationDomainService`

| 方法 | 说明 | IF |
|------|------|-----|
| `createObservation(CreateObservationCommand)` | 手工录入 | DES-02 |
| `updateObservation(UpdateObservationCommand)` | 改值 | DES-02 |
| `finalizeObservation(FinalizeObservationCommand)` | final | DES-02 |
| `cancelObservation(CancelObservationCommand)` | 作废 | DES-05 |

### 6.2 读侧 — `ObservationQueryService`

| 方法 | 说明 |
|------|------|
| `listByEncounterId(encounterId)` | MAS/DES |
| `listByServiceRequestId(serviceRequestId)` | DES |
| `findBySrAndDefinition(srId, definitionId)` | 幂等 BR-CLN-OBS-02 |

### 6.3 投影 — `LisResultProjectionService`（写 Observation，无独立表）

| 方法 | 说明 | IF / 触发 |
|------|------|-----------|
| `projectFromLis(ProjectFromLisCommand)` | lis_observation → upsert CLN | **P0 主**：LIS **AUD-05** 发布报告后（[AS-LIS-AUD-001](../AppService/AS-LIS-AUD-001-结果审核.md)）；**补偿**：PEIS INT-03 / DES-08 |
| `projectFromPacsSummary(ProjectFromPacsCommand)` | PACS 摘要 | PACS 审核发布 / DES-08 可选 |

**P0 触发链（LIS 审核 → 临床事实，推荐）**：

```text
LIS AUD-05 publishReport（DiagnosticReport final）
  → upsert cln_diagnostic_report_index（DM-CLN-006，已有）
  → 若 SR.encounterId 非空：LisResultProjectionService#projectFromLis
        → upsert cln_observation + source_observation_id（BR-CLN-OBS-01）
  → PEIS ext.dispatch 可置 resultReceived（若存在 peis_service_request_ext）
```

**与 AOL（[DM-CUS-013](./DM-CUS-013-ActivityObservationLink-项目小项绑定.md)）的关系（P0）**：

| 步骤 | 行为 |
|------|------|
| 1 | 由 `serviceRequestId` 解析 `activityDefinitionId` |
| 2 | **只读** `ActivityObservationLinkQueryService#listByActivityId` 得目标 `definitionId` 列表（若为空则 AS 拒绝或走院点默认策略） |
| 3 | 按 `lis_observation` 细项码 / 通道与 OD 对齐，upsert `cln_observation`，写 `source_observation_id`（BR-CLN-OBS-01） |

LIS 模块 **不维护** AOL；仪器侧细项路由见 **`lis_device_channel`**（Lis 01 §10.4 / D10）。

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CLN-OBS-01** | LIS 投影须 `source_observation_id` |
| **BR-CLN-OBS-02** | 同 SR + definitionId 幂等 update |
| **BR-CLN-OBS-03** | final 后改值走 amended（院点） |
| **BR-CLN-OBS-04** | LIS **AUD-05** 发布且 SR 有 encounterId 时 **必须**（或院点配置强制）调用 `projectFromLis`；禁止仅写 index 不写 CLN |
| **BR-CLN-OBS-05** | PACS **REP-07** 审核通过且 SR 有 encounterId 时 **必须**调用 `projectFromPacsSummary`（对称 BR-CLN-OBS-04）；DES-08/INT-03 为补偿 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.3 | 2026-06-27 | **BR-CLN-OBS-05**：PACS REP-07 主路径投影 |
| v1.2 | 2026-06-27 | P0 主路径：LIS AUD-05 发布后投影 CLN；DES-08 降为补偿 |
| v1.1 | 2026-06-27 | §6.3 投影只读 AOL；与 LIS lis_device_* 分工 |
| v1.0 | 2026-06-27 | 自原 DM-CLN-005 拆分实例 + Projection |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ObservationDomainService`

| 方法 |
|------|
| `createObservation` |
| `updateObservation` |
| `finalizeObservation` |
| `cancelObservation` |

### QueryService：`ObservationQueryService`

| 方法 |
|------|
| `listByEncounterId` |
| `listByServiceRequestId` |
| `findBySrAndDefinition` |

