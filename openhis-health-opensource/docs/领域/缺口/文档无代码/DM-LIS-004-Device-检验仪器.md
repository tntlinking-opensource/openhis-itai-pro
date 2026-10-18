# DM-LIS-004 Device 检验仪器

> **限界上下文**：检验域 · 仪器管理  
> **聚合类型**：实体聚合  
> **版本**：v1.0 · **日期**：2026-06-22

## 1. 概述

### 1.1 业务定义

Device 表达**检验科在用的仪器实例**（生化仪、血球仪、免疫仪等），包含仪器基本属性、通道映射、检验能力配置、双向通讯配置。**不复用** health `DM-ADM-008 DeviceDefinition`（语义为耗材发放，非检验设备）。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 仪器主数据 | 仪器编码、名称、厂商、型号、序列号 |
| 通道映射 | 通道号 → 项目码 → unitCode |
| 检验能力 | 该仪器可检哪些项目 + 接受哪种试管方案 |
| 双向通讯 | C++ 程序连接信息、worklist 格式 |

---

## 2. 持久化形态

**表结构**：
- 主表：`lis_device`（主键：`id`）
- 通道表：`lis_device_channel`（外键：`device_id`，BIDI 扩展字段）
- 能力表：`lis_device_capability`（外键：`device_id`）

---

## 3. 领域模型

### 3.1 Device（主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 聚合根标识 |
| busNo | String | 院内编码 |
| deviceName | String | 显示名 |
| manufacturer | String | 厂商 |
| modelNumber | String | 型号 |
| serialNumber | String | 序列号 |
| locationId | Long | 检验科位置 |
| statusEnum | DeviceStatus | active / inactive |
| bidiConfig | String (JSON) | 双向通讯配置（C++地址/端口/Token/模板） |
| tenantId / orgId | Long | 租户/院区 |

### 3.2 lis_device_channel

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | |
| deviceId | Long | 外键 |
| channelNo | Integer | 通道号 |
| activityId | Long | 项目码 |
| unitCode | String | 单位 |
| dilutionFactor | BigDecimal | 稀释倍数 |

### 3.3 lis_device_capability

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | |
| deviceId | Long | 外键 |
| activityId | Long | 可检项目 |
| tubeSchemeId | Long | 接受的试管方案 |
| mergeProfileId | Long | 关联合管方案（可选） |
| statusEnum | String | active / inactive |

---

## 4. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-DEV-01** | 通道号在同一 deviceId 下唯一 |
| **BR-DEV-02** | capability 每行 (deviceId, activityId, tubeSchemeId) 唯一 |
| **BR-DEV-03** | 禁用仪器前校验：无进行中 LisWorkItem 关联 |
| **BR-DEV-04** | capability 的每个 activityId 须有对应 channel |

---

## 5. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-22 | 首版 |
