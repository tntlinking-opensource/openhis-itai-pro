# DM-LIS-008-LisConfig-模块参数

> **限界上下文**：检验域 · 配置管理
> **聚合类型**：实体
> **版本**：v1.0 · **日期**：2026-06-22

## 1. 概述

### 1.1 业务定义

`lis_config` 是 LIS 模块自有 KV 参数表，**禁止** 读写 framework `Setting` 表。作用域：`tenantId` 必填，`orgId` 可选。

### 1.2 核心配置键

| config_key | 默认 | 说明 |
|------------|------|------|
| `grouping.mode` | `PROFILE_AND_DEVICE` | 合管模式 |
| `grouping.allowAppendToOpenTube` | `false` | 补项挂未采集工单 |
| `grouping.fallbackWhenNoProfile` | `ONE_SR_ONE_TUBE` | 未配 profile 时降级 |
| `sampling.allowWithoutCharge` | `false` | 未收费是否允许采样 |
| `barcode.prefix` | — | 条码前缀 |
| `barcode.dateFormat` | `yyMMdd` | 条码日期格式 |
| `barcode.seqLength` | `6` | 流水号位数 |
| `tat.timeout.*` | — | TAT 超时阈值 |
| `audit.auto.enabled` | `false` | 自动审核全局开关 |
| `crt.acknowledgeTimeoutMinutes` | `30` | 危急值确认超时 |
| `log.retentionDays` | `90` | 日志保留天数 |
| `review.force_double_sign_activities` | — | 强制双签项目列表 |

---

## 2. 持久化形态

**表**：`lis_config`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 标识 |
| tenantId | Long | 租户（必填） |
| orgId | Long | 院区（可选） |
| configKey | String | 键 |
| configValue | String | 值 |
| remark | String | 说明 |

---

## 3. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-CFG-01** | 禁止读写 framework Setting |
| **BR-CFG-02** | 危险参数变更需二次确认 |
| **BR-CFG-03** | 参数变更记录 AuditLog |
| **BR-CFG-04** | 合管配置变更 → 触发合管分组重算（跨聚合影响） |
| **BR-CFG-05** | TAT 超时变更 → 仅对新工单生效（不回溯已生成工单） |

---

## 4. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-22 | 首版 |
