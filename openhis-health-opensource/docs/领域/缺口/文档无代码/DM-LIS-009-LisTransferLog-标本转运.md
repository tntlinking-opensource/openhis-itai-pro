# DM-LIS-009 LisTransferLog 标本转运

> **限界上下文**：检验域 · 标本物流  
> **聚合类型**：实体聚合  
> **版本**：v1.0 · **日期**：2026-06-22

## 1. 概述

### 1.1 业务定义

LisTransferLog 记录标本在病区→检验科→专业组间的每一次物理交接，支撑标本转运的批次管理与签收差量追踪。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 交接记录 | 记录每一次物理交接的源/目标位置、操作人、时间 |
| 批次管理 | 维护送检批次号，关联同一批次标本列表 |
| 差量追踪 | 记录 expectedCount vs verifiedCount 差值 |

### 1.3 适用范围

| 场景 | 说明 |
|------|------|
| 病区→检验科送检 | SAM-TRANS 创建转运批次 |
| 专业组间转运 | 标本从生化组→免疫组流转 |
| 交接签收 | 检验科确认接收 |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：单表

**表结构**：
- 主表：`lis_transfer_log`（主键：`id`）

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|------|------|------|
| transferLogId | Long | 全局唯一标识（雪花 ID） |

---

## 3. 领域模型

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 聚合根标识 |
| sourceLocation | String | 源位置（病区/科室编码） |
| targetLocation | String | 目标位置（检验科/专业组） |
| operatorId | Long | 操作人 |
| transferTime | Instant | 交接时间 |
| workItemIds | String (JSON) | 标本 workItemId 列表 |
| batchNo | String | 送检批次号 |
| verifiedCount | Integer | 实际接收数 |
| expectedCount | Integer | 期望接收数 |
| tenantId | Long | 租户 |
| orgId | Long | 院区 |
| createdBy / createdAt | Long / Instant | 审计 |

---

## 4. 聚合关系

```text
LisTransferLog（聚合根）
  └── workItemIds ──► LisWorkItem
```

---

## 5. 领域事件

| 事件 | 触发条件 | 消费方 |
|------|----------|--------|
| LisTransferLogCreated | 新建转运批次 | SAM-TRANS 刷新 |
| LisTransferConfirmed | 交接签收 | TAT 监控 |

---

## 6. 领域方法

| 方法 | 说明 |
|------|------|
| `confirm(verifiedCount)` | 交接确认，记录实际接收数 |
| `addWorkItem(workItemId)` | 追加标本到批次 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-TRANS-01** | verifiedCount ≤ expectedCount |
| **BR-TRANS-02** | 差量（expectedCount - verifiedCount）→ 标记 missing |
| **BR-TRANS-03** | 同一 batchNo 下 workItemIds 不可重复 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-22 | 首版 |
