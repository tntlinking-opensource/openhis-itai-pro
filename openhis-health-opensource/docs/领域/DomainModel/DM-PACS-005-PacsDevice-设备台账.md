# DM-PACS-005 PacsDevice 检查设备台账

> **限界上下文**：检查域（PACS/RIS）· 设备台账配置  
> **聚合类型**：实体聚合（配置型）  
> **版本**：v1.0 · **日期**：2026-09-22  
> **输入**：[PacsAppService/04](../../../../应用/zxplat-application/PacsAppService/04-FHIR领域模型.md)、遗留 `GGZ_BASIC_EQUIPMENT_INFOR` / `BasicInfo`、AS-PACS-CFG-003  
> **消费方**：CFG-003 维护；MWL/执行侧按模态读启用设备（只读引用，本期不强制外键）

---

## 1. 概述

### 1.1 业务定义

**PacsDevice** 是影像科设备台账（`pacs_device`），对应遗留设备主档（≈ `SBID`/`SBMC`），维护名称、模态、DICOM AE Title 与启停，供登记/执行/MWL 按模态选用。

### 1.2 职责

| 职责 | 说明 |
|------|------|
| 设备 CRUD | 编码、名称、模态、AE Title、启停、软删 |
| 列表读 | 按模态/启停分页 |

### 1.3 非本聚合

| 不在内 | 归属 |
|--------|------|
| 可执行项目 capability | DM-PACS-001 / `pacs_modality_capability`（CFG-001） |
| 报告正文模板 | DM-PACS-004 |
| 报告打印版式/主副标题等 | PRT / 打印模板 |
| 设备 EAV 参数表（CSM/CSZ…） | 国基首版不做（遗留 `EquipmentParameter`） |

---

## 2. 持久化

**表**：`pacs_device`（主键 `id`）

---

## 3. 领域模型

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | Long | 是 | 聚合根 |
| deviceCode | String | 是 | 业务编码（≈ SBID）；租户内未软删唯一 |
| name | String | 是 | 设备名称（≈ SBMC） |
| modalityCode | String | 是 | 模态码（≈ MODALITY / SBLX） |
| aeTitle | String | 否 | DICOM AE Title（≈ MODAET） |
| stationName | String | 否 | 机房/室名 |
| enabledFlag | Boolean | 是 | 是否启用；默认 true |
| sortOrder | Integer | 否 | 列表排序 |
| remark | String | 否 | 备注 |
| tenantId | Long | 是 | 租户 |
| createdBy / createdAt / updatedBy / updatedAt | — | 是 | 审计 |
| isDeleted | Boolean | 是 | 软删 |

---

## 4. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-DEV-01** | `deviceCode`、`name`、`modalityCode` 非空；软删后不参与默认列表 |
| **BR-DEV-02** | 同租户未软删 `deviceCode` 唯一 |
| **BR-DEV-03** | 停用/软删后不可作为默认选用目标（列表 `enabledOnly=true` 过滤） |
| **BR-DEV-04** | 国基 P1 **不做**遗留 EAV 参数行、报告版式字段混入本表 |

---

## 5. 领域服务方法

| 方法 | 说明 |
|------|------|
| `save(SavePacsDeviceCommand)` | 新增/更新 |
| `disable(id, operatorId)` | 停用 |
| `delete(id, operatorId)` | 软删 |
| `search(modalityCode, enabledOnly, keyword, pageable)` | 分页列表（keyword 匹配名称/编码） |
| `getById(id)` | 详情 |

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-22 | D-12 首版；解锁 CFG-003 / 序号 44 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PacsDeviceDomainService`

| 方法 |
|------|
| `save` |
| `disable` |
| `delete` |
| `getById` |

### QueryService：`PacsDeviceQueryService`

| 方法 |
|------|
| `search` |

