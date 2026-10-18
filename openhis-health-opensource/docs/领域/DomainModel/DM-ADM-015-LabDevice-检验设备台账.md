# DM-ADM-015 LabDevice 检验设备台账

> **限界上下文**：管理域 · 检验/科室设备主数据  
> **聚合类型**：实体聚合（单表 `adm_device`）  
> **版本**：v1.0 · **日期**：2026-08-22

## 1. 概述

检验设备台账维护院内**合管/采血设备**基础档案（非耗材 `adm_device_definition`）。字段含业务编码、名称、检索码、可选归属科室、设备分类术语 `adm_device_category_code`、型号/序列号、生产厂家（`cus_supplier.id`）、生产日期/失效日期、发布状态。

## 2. 持久化

- 表：`adm_device`
- 唯一：`uk_adm_device_bus_no (tenant_id, bus_no) WHERE is_deleted=false`
- `bus_no` 由领域层 `BusinessSequenceService` 生成，前缀 `LAD`

## 3. 状态机

`ACTIVE` ↔ `INACTIVE`（`PublicationStatus.code`）

## 4. 写侧能力

| 能力 | 说明 |
|---|---|
| createLabDevice | 新建，默认 ACTIVE |
| updateLabDevice | 维护，名称变更重算检索码 |
| deleteLabDevice | 逻辑删除 |
| activate/deactivate | 批量启停 |

## 5. 读侧能力

分页（keyword、statusEnum、categoryCode、**orgId**）、详情、关键字检索（默认 ACTIVE）

## 6. 业务规则

- BR-LAD-001：`bus_no` 租户内唯一  
- BR-LAD-002：名称非空，检索码可自动生成  
- BR-LAD-003：启停仅改 `status_enum`

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`LabDeviceDomainService`

| 方法 |
|------|
| `createLabDevice` |
| `updateLabDevice` |
| `deleteLabDevice` |
| `deactivateLabDevices` |
| `activateLabDevices` |

### QueryService：`LabDeviceQueryService`

| 方法 |
|------|
| `pageLabDevice` |
| `listActiveDevices` |
| `getLabDevice` |
| `searchByKeyWord` |

