# DM-LIS-007-AuditEvent-接口日志

> **限界上下文**：检验域 · 接口日志
> **聚合类型**：实体聚合
> **版本**：v1.0 · **日期**：2026-06-22

## 1. 概述

### 1.1 业务定义

AuditEvent 记录 **HIS/仪器 HTTP 报文交互日志**，对标遗留 `WBS_LOG`。仪器遥测（心跳/计数器）用 DeviceMetric。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 接口报文记录 | requestBody/responseBody 存档 |
| 报文追溯 | 按来源/动作/结果查询 |
| 补发 | INT-05 外发失败补发 |
| 保留策略 | 超期自动清理 |

---

## 2. 持久化形态

**表**：`lis_audit_event`

---

## 3. 领域模型

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 标识 |
| typeEnum | String | rest / export |
| action | String | C / R / U / D / E |
| recorded | Instant | 时间 |
| agent | String | 调用方（HIS/仪器/用户） |
| source | String | LIS 节点 |
| entityId | Long | 关联 id |
| entityType | String | 关联类型 |
| outcome | String | success / failure |
| requestBody | Text | 请求报文 |
| responseBody | Text | 响应报文 |

---

## 4. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-AE-01** | requestBody / responseBody 敏感字段脱敏 |
| **BR-AE-02** | 保留天数 → `lis_config.log.retentionDays` |

---

## 5. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-22 | 首版 |
