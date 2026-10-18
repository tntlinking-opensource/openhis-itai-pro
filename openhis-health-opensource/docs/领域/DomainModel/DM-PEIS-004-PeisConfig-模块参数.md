# DM-PEIS-004 PeisConfig · PEIS 模块参数

> **表名**：`peis_config`（key-value，对齐 `lis_config` 模式）  
> **版本**：v1.1 · **日期**：2026-06-27

---

## 1. 概述

PEIS 模块 KV 参数；REG/DES/INT 经 QueryService 读取。

---

## 2. 持久化形态

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| config_key | String | 键 |
| config_value | Text | 值 |
| tenant_id / org_id | Long | 租户/院区 |

---

## 2.1 配置项（P0 示例）

| key | 说明 |
|-----|------|
| `peis.reg.charge_gate` | 未收费是否阻塞分科 |
| `peis.reg.auto_dispatch` | 登记后自动 **INT-01**（本院 LisWorkItem/PacsWorkItem）；金域项由 LIS EXT 接续 |
| `peis.report.template_code` | 默认报告模板 |
| `peis.bus_no.rule` | 体检流水号规则 |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `PeisConfig` |
| **标识** | `id`；业务键 `(tenantId, configKey)` |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | PK |
| configKey | String | 是 | 键 |
| configValue | String | 是 | 值 |
| tenantId | Long | 是 | 租户 |
| orgId | Long | 否 | 院区 |

---

## 5. 领域事件

无。`PeisConfigChangedEvent` 由 AS-PEIS-CFG-003 编排（P1）。

---

## 6. 领域服务

### 6.1 写侧 — `PeisConfigDomainService`

| 方法 | 说明 |
|------|------|
| `save(SavePeisConfigCommand)` | 幂等 upsert 单键 |
| `batchSave(BatchSavePeisConfigCommand)` | 批量保存 |

### 6.2 读侧 — `PeisConfigQueryService`

| 接口签名 | 场景 |
|----------|------|
| `getByKey(tenantId, configKey): PeisConfig` | REG/INT |
| `searchConfigs(tenantId, Pageable): Page<PeisConfigQueryModel>` | CFG-003 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-PEIS-CFG-01** | `(tenantId, configKey)` 唯一 |
| **BR-PEIS-CFG-02** | 禁止与 framework Setting key 前缀冲突 |
| **BR-PEIS-CFG-03** | `auto_dispatch=true` 仅触发 **本院** INT-01，不含金域 EXT |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 对齐 lis_config §6；补 §3 |
| v1.0 | 2026-06-27 | 首版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PeisConfigDomainService`

| 方法 |
|------|
| `save` |
| `batchSave` |

### QueryService：`PeisConfigQueryService`

| 方法 |
|------|
| `getByKey` |
| `findOptionalByKey` |
| `searchConfigs` |

