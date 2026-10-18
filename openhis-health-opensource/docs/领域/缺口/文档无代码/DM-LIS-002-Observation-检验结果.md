# DM-LIS-002 Observation 检验结果

> **限界上下文**：检验域 · 结果管理  
> **聚合类型**：实体聚合  
> **版本**：v1.0 · **日期**：2026-06-22

## 1. 概述

### 1.1 业务定义

Observation 表达**单次检验测量的结果值**，语义对齐 FHIR `Observation`。是仪器结果 ingress、手工录入、审核、报告生成的唯一事实源。替代遗留 `LisResult` / `LisResultItem`。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 结果值存储 | 定量/定性/阴阳性等多元值形态 |
| 双锚点关联 | workItemId（标本）+ serviceRequestId（医嘱） |
| 结果生命周期 | preliminary → final → amended → cancelled |
| 参考范围比对 | 自动审核 / Delta Check 读 referenceRange |
| 审核状态驱动 | 审核→发布状态迁移 |

### 1.3 适用范围

| 场景 | 说明 |
|------|------|
| 仪器结果 ingress | RES-01 回传 → 创建/更新 Observation（preliminary） |
| 手工录入 | REP-03 检验师手工录入 |
| 自动审核 | AUD-AUTO 规则引擎读值判定 |
| 人工审核 | AUD-02 审核通过 → final |
| 报告修订 | REP-REVISE 召回 → amended |

---

## 2. 持久化形态

**表**：`lis_observation`（主键：`id`）

---

## 3. 领域模型

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 聚合根标识 |
| workItemId | Long | 关联工单 |
| serviceRequestId | Long | 关联医嘱 |
| codeSystem | String | 编码体系（LOINC / 院内码） |
| code | String | 项目码 |
| valueQuantity | BigDecimal | 定量值 |
| valueString | String | 定性值 / 微生物描述 |
| valueCodeableConcept | String | 阴阳性等编码值 |
| unitCode | String | 单位码 |
| referenceRange | String (JSON) | 参考范围（low/high/text） |
| interpretationCode | String | 判读（N/H/L/HH/LL/A） |
| statusEnum | ObsStatus | preliminary / final / amended / cancelled |
| effectiveTime | Instant | 检测时间 |
| deviceId | Long | 仪器实例 |
| reagentLotNumber | String | 试剂批号（P2） |
| previousId | Long | 修订前版本（REVISE 场景） |
| noteText | String | 备注 |
| parentObservationId | Long | 组合项父引用 |
| tenantId / orgId | Long | 租户/院区 |

---

## 4. 枚举

**ObsStatus**：`preliminary`（待审） / `final`（已发布） / `amended`（已修订） / `cancelled`（已取消）

---

## 5. 领域事件

| 事件 | 触发条件 |
|------|----------|
| ObservationPreliminaryRecorded | 仪器结果首次写入 |
| ObservationFinalized | 审核通过 |
| ObservationAmended | 报告修订 |

---

## 6. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-OBS-01** | 同一 (workItemId, serviceRequestId, code) 已存在 preliminary → 更新值，记录 editLog |
| **BR-OBS-02** | 审核通过：preliminary → final |
| **BR-OBS-03** | 修订版本链：amended 时 `previousId` 指向前版 |
| **BR-OBS-04** | 危急值触发：interpretationCode = HH/LL + 值超过阈值 → 创建 DetectedIssue |

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-22 | 首版 |
