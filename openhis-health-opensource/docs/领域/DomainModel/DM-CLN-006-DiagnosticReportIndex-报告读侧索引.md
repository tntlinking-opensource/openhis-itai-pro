# DM-CLN-006 DiagnosticReportIndex · 报告读侧索引

> **限界上下文**：health · clinical（只读聚合，**非**报告事实）  
> **表名**：`cln_diagnostic_report_index`  
> **版本**：v1.0 · **日期**：2026-06-27  
> **关联**：[LisAppService/01 §10.3](../../../../应用/zxplat-application/LisAppService/01-概要设计.md#103-diagnosticreport-统一索引d2)、[DM-LIS-003](./DM-LIS-003-DiagnosticReport-检验报告.md)

---

## 1. 概述

### 1.1 业务定义

**DiagnosticReportIndex** 是 LIS / PACS / PEIS / HEALTH 各模块 **报告事实表** 的统一 **读侧索引**，供医生站、PEIS 总检、CRB、历史报告列表 **优先查询**。各模块保留本域 DR 事实；发布/审核 **final** 时 upsert 本表。

### 1.2 模块事实 vs 索引

| 模块 | 事实表 | index.module_enum |
|------|--------|-------------------|
| LIS | `lis_diagnostic_report | LIS |
| PACS | `pacs_diagnostic_report | PACS |
| PEIS/总检 | **`cln_diagnostic_report** | **`PEIS`**（见 [DM-CLN-008](./DM-CLN-008-DiagnosticReport-临床总检报告.md)） |

---

## 2. 持久化形态

**表**：`cln_diagnostic_report_index`（主键：`id`）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| tenantId / orgId | Long | 租户/院区 |
| **moduleEnum** | String | LIS / PACS / PEIS / HEALTH |
| **sourceReportId** | Long | 模块内 DR 主键 |
| encounterId | Long | 就诊 |
| serviceRequestId | Long | 可空（总检 DR 可空） |
| patientId | Long | 患者 |
| statusEnum | String | preliminary / final / amended / … |
| publishedAt | Instant | 发布时间 |
| title | String | 列表展示标题 |
| categoryCode | String | 报告类别码 |
| pdfStorageKey | String | 对象存储键 |
| externalViewUrl | String | 外部预览 URL |
| amendedFromId | Long | 修订链 |
| uniqueKey | String | 幂等：`moduleEnum + sourceReportId` UNIQUE |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `DiagnosticReportIndex` |
| **标识** | `id`；幂等键 `uniqueKey` = moduleEnum + sourceReportId |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | PK |
| tenantId | Long | 是 | |
| orgId | Long | 否 | |
| moduleEnum | String | 是 | LIS/PACS/PEIS/HEALTH |
| sourceReportId | Long | 是 | 模块 DR PK |
| encounterId | Long | 否 | |
| serviceRequestId | Long | 否 | |
| patientId | Long | 是 | |
| statusEnum | String | 是 | |
| publishedAt | LocalDateTime | 否 | |
| title | String | 否 | |
| categoryCode | String | 否 | |
| pdfStorageKey | String | 否 | |
| externalViewUrl | String | 否 | |
| amendedFromId | Long | 否 | |
| uniqueKey | String | 是 | UNIQUE |

---

## 4. 写入时机

| 触发 | 模块 | 说明 |
|------|------|------|
| LIS AUD-05 发布 / REVISE | LIS | [DM-LIS-003](./DM-LIS-003-DiagnosticReport-检验报告.md)；**同编排** [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) `projectFromLis` |
| PACS 报告审核发布 | PACS | REP-007 passSh |
| PEIS MAS-04 总检 final | PEIS | 总检完成 |

写入方式：**upsert**（同 module + sourceReportId 更新 status/publishedAt）。

---

## 4.1 读侧消费

| 消费方 | 用法 |
|--------|------|
| 医生站报告列表 | 按 patientId / encounterId 查 index → 按需穿透模块 DR |
| PEIS 总检/历史 | 同上 |
| CRB | 订阅 LIS final 事件 + 可选直读 index |
| PEIS DES-02 / MAS | 读 **`cln_observation`**（AUD-05 已投影则无需 INT-03） |
| DES-08 / INT-03 | **补偿**刷新；PACS 摘要可带 index 行 id 作外链 |

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `DiagnosticReportIndexUpsertedEvent` | `id, moduleEnum, sourceReportId, statusEnum` | upsert* |
| `DiagnosticReportIndexCancelledEvent` | `moduleEnum, sourceReportId` | markCancelled |

包路径：`com.zxplat.domain.share.events.clinical.diagnosticreportindex`

---

## 6. 领域服务

### 6.1 写侧 — `DiagnosticReportIndexUpsertService`

| 方法 | 说明 | 调用方 |
|------|------|--------|
| `upsertFromLis(UpsertDrIndexFromLisCommand)` | LIS final/amended | LIS 事件消费 / 适配器 |
| `upsertFromPacs(UpsertDrIndexFromPacsCommand)` | PACS 审核发布 | PACS REP |
| `upsertFromPeis(UpsertDrIndexFromPeisCommand)` | 总检 final | MAS-001 |
| `markCancelled(MarkDrIndexCancelledCommand)` | 撤单/作废 | 各模块 |

### 6.2 读侧 — `DiagnosticReportIndexQueryService`

| 方法 | 说明 |
|------|------|
| `searchByPatient(patientId, query)` | 医生站/PEIS 历史列表 |
| `searchByEncounter(encounterId)` | 单次就诊报告 |
| `getByUniqueKey(moduleEnum, sourceReportId)` | 穿透模块 DR |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-DR-IDX-01** | 仅 `final` / `amended` 写入或更新 index（preliminary 可选，院点策略） |
| **BR-DR-IDX-02** | 撤单/作废 → index status 同步 cancelled 或软删 |
| **BR-DR-IDX-03** | index **不存**报告正文；正文仍在模块 DR 表 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-06-27 | 补 §3/§5 成 SDD 前置 |
| v1.1 | 2026-06-27 | §5 Upsert/Query 方法表 |
| v1.0 | 2026-06-27 | 首版：跨 LIS/PACS/PEIS 读侧索引 |
