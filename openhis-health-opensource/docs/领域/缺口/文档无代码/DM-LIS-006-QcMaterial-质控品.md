# DM-LIS-006-QcMaterial-质控品

> **限界上下文**：检验域 · 质控管理
> **聚合类型**：实体聚合
> **版本**：v1.0 · **日期**：2026-06-22

## 1. 概述

### 1.1 业务定义

QcMaterial 表达**质控品/质控批号**，与 QcRun（质控运行）、QcMeasurement（质控测定值）组成质控领域。不单用 FHIR Observation 承载，因质控品靶值/SD/Westgard 多规则无标准 FHIR 结构。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 质控品主数据 | 批号、水平（level）、靶值、SD |
| 质控运行 | QcRun：日期、仪器、操作者 |
| 质控测定 | QcMeasurement：某项目在某 Run 的测定值 |
| Westgard 判定 | QcEvaluation：1-2s/1-3s/2-2s/R-4s/4-1s/10x |

---

## 2. 持久化形态

**表**：
- `lis_qc_material`（质控品）
- `lis_qc_run`（质控运行）
- `lis_qc_measurement`（质控测定值）

---

## 3. 领域模型

### 3.1 QcMaterial

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 标识 |
| lotNo | String | 批号 |
| levelEnum | String | 水平（normal/abnormal） |
| targetValue | BigDecimal | 靶值 |
| standardDeviation | BigDecimal | SD |

### 3.2 QcRun

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 标识 |
| deviceId | Long | 仪器 |
| materialId | Long | 质控品 |
| runDate | LocalDate | 运行日期 |
| operatorId | Long | 操作者 |
| statusEnum | String | normal / out_of_control |

### 3.3 QcMeasurement

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 标识 |
| runId | Long | 运行 |
| projectCode | String | 项目码 |
| measuredValue | BigDecimal | 测定值 |
| deviation | BigDecimal | 偏差（测量值-靶值） |

---

## 4. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-QC-01** | lotNo + levelEnum 唯一 |
| **BR-QC-02** | Westgard 任一规则失控 → QcRun 标记 out_of_control |

---

## 5. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-22 | 首版 |
