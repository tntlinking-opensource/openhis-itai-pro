# DM-LIS-001 LisWorkItem 检验工单

> **限界上下文**：检验域 · 标本执行管理  
> **聚合类型**：实体聚合  
> **版本**：v1.0 · **日期**：2026-06-22

## 1. 概述

### 1.1 业务定义

检验工单（LisWorkItem）是 LIS 系统中最核心的聚合根，表达**一管血/一个标本**在检验科内的完整物理流转与执行事实。它在语义上对齐 FHIR `Specimen`，在代码侧以 `LisWorkItem` 为唯一持久化形态。

**并管核心**：N 个 `ServiceRequest`（医嘱行）→ 1 个 `LisWorkItem`（一管血），通过子实体 `LisWorkItemOrder` 解耦。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 标本物理标识 | 维护条码（barcode）、样本号（sampleNo）、标本类型 |
| 标本生命周期管理 | 管理从接单、采样、核收、上机到废弃的完整状态迁移 |
| 并管关联 | 通过 orderLinks 维护 N 个 ServiceRequest 与 1 个 LisWorkItem 的关联 |
| TAT 时效记录 | 每个状态节点的变更时间戳 |
| 患者/就诊锚点 | 引用 patientId / encounterId，不复制患者数据 |

### 1.3 适用范围

| 场景 | 说明 |
|------|------|
| HIS 接单 | INT-01 合管分组后创建 LisWorkItem + orderLinks |
| 采样签收 | 护士确认采样，状态 available → collected |
| 标本核收 | 检验科接收，状态 collected → received |
| 上机检测 | 标本上仪器，状态 received → processing |
| 拒收/重采 | 不合格标本拒收，新建 LisWorkItem 重新采集 |
| 撤单 | HIS 签退 → LIS 作废工单 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子表

**表结构**：
- 主表：`lis_work_item`（主键：`id`）
- 子表：`lis_work_item_order`（外键：`work_item_id`）
- 关系：1:N

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|------|------|------|
| workItemId | Long | 全局唯一标识（雪花 ID） |

---

## 3. 领域模型

### 3.1 LisWorkItem（聚合根）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 聚合根标识 |
| barcode | String | 条码，唯一 |
| sampleNo | String | 样本号 |
| specimenDefinitionId | Long | 标本类型（引用 health SpecimenDefinition） |
| patientId | Long | 患者标识（引用 health Patient） |
| encounterId | Long | 就诊标识（引用 health Encounter） |
| statusEnum | LisWorkItemStatus | 标本物理态 |
| statusUpdatedAt | Map<String, Instant> (JSON) | 各状态节点时间戳 |
| collectionTime | Instant | 采样时间 |
| receivedTime | Instant | 核收时间 |
| tenantId | Long | 租户 |
| orgId | Long | 院区/科室 |
| createdBy / createdAt | Long / Instant | 审计 |

### 3.2 LisWorkItemOrder（子实体）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 子实体标识 |
| workItemId | Long | 外键 |
| serviceRequestId | Long | 关联的 HIS 医嘱 |
| activityId | Long | 冗余项目标识 |
| linkStatusEnum | LinkStatus | 该项在本管上的业务态 |
| sortNumber | Integer | 排序 |

### 3.3 枚举

**LisWorkItemStatus**：`available` / `collected` / `received` / `processing` / `entered-in-error` / `unsatisfactory`

**LinkStatus**：`pending` / `result_pending` / `testing` / `published` / `cancelled` / `reviewed_level1` / `reviewed_level2`

---

## 4. 聚合关系

```text
LisWorkItem（聚合根）
  │
  └── 1:N ──► LisWorkItemOrder（子实体）
                   │
                   └── serviceRequestId ──► health ServiceRequest
                   └── activityId ──► health ActivityDefinition
```

---

## 5. 领域事件

| 事件 | 触发条件 | 消费方 |
|------|----------|--------|
| LisWorkItemCreated | INT-01 创建工单 | SMP-001 采样台刷新 |
| LisWorkItemCollected | 采样签收 | TAT 监控 |
| LisWorkItemReceived | 检验科核收 | TAT 监控 |
| LisWorkItemProcessingStarted | 上机 | RES-01 结果接收 |
| LisWorkItemCancelled | 撤单 | INT-02 回写 HIS |

---

## 6. 领域方法

| 方法 | 说明 |
|------|------|
| `addOrder(serviceRequestId, activityId)` | 追加 orderLink（并管场景） |
| `cancelOrder(serviceRequestId)` | 撤单项 orderLink |
| `collect(operatorId)` | 采样签收 |
| `receive(operatorId)` | 检验科核收 |
| `startProcessing(deviceId)` | 上机 |
| `cancel(reason)` | 作废（仅全部 order 作废时） |
| `reject(reason)` | 拒收 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-LWI-01** | 并管同一 LisWorkItem 下所有 orderLink 的 `tubeSchemeId` 须一致 |
| **BR-LWI-02** | 采样签收前状态须为 `available` |
| **BR-LWI-03** | 核收前状态须为 `collected` |
| **BR-LWI-04** | 撤单仅允许 statusEnum = `available` 或 `collected` |
| **BR-LWI-05** | 仅所有 orderLink 均作废时方可 cancel LisWorkItem |
| **BR-LWI-06** | 重采须新建 LisWorkItem，不可复用原 workItemId |
| **BR-LWI-07** | 1 LisWorkItem = 1 barcode，barcode 生成规则读 `lis_config.barcode.*` |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-22 | 首版；对齐 04-FHIR领域模型 §二 |
