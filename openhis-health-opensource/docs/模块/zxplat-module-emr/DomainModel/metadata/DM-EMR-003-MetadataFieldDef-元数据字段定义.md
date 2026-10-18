# DM-EMR-003 MetadataFieldDef 元数据字段定义

> **限界上下文**：电子病历（EMR 插件）  
> **代码模块**：`zxplat-plugin/zxplat-module-emr/module-emr-domain`  
> **包**：`com.zxplat.plugin.emr.domain.metadata`  
> **聚合类型**：单表实体聚合  
> **版本**：v1.0 · **日期**：2026-09-24 · **模式**：B（代码先行回写）  
> **写侧**：`MetadataFieldDefDomainService`（Command）；解析仍经 `EncounterMetadataAppService` + bridge `EmrAdmissionContextLoader`

---

## 1. 概述

### 1.1 业务定义

**元数据字段定义（MetadataFieldDef）** 描述病历页眉/芯片等可绑定字段：键、标签、数据类型、取值源（患者/就诊/入院上下文等）、掩码与格式。运行时按就诊解析出键值对，再经 `DocumentStructureManager.setValuesByNames` 写入文书 JSON。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 字段目录维护 | CRUD、启用列表、引用检查后删除 |
| 取值源契约 | `valueSource` + `sourcePath` / `sourceConfig` |
| 引用检查 | `MetadataFieldReferenceChecker` 防删仍被模板引用的字段 |

### 1.3 不做的事

- 不持久化某次就诊的解析结果（运行时计算）  
- 不实现 HIS 读库细节（bridge 实现 Loader）  
- 不直接写文书（应用层编排）

---

## 2. 聚合根与持久化形态

| 物理表 | 实体 |
| --- | --- |
| `emr_metadata_field_def` | `MetadataFieldDef` |

---

## 3. 实体

### 3.1 MetadataFieldDef

| 字段名（Java） | 列 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `fieldKey` | `field_key` | String(64) | 是 | 字段键 |
| `label` | `label` | String(128) | 是 | 显示名 |
| `dataType` | `data_type` | String(16) | 是 | 默认 string |
| `editableDefault` | `editable_default` | Boolean | 是 | 默认是否可编 |
| `valueSource` | `value_source` | String(32) | 是 | 取值源 |
| `sourcePath` | `source_path` | String(256) | 否 | 路径 |
| `sourceConfig` | `source_config` | TEXT | 否 | JSON 配置 |
| `defaultValue` | `default_value` | String(512) | 否 | 默认值 |
| `maskRule` | `mask_rule` | String(32) | 是 | 默认 NONE |
| `formatPattern` | `format_pattern` | String(64) | 否 | 格式 |
| `sortOrder` | `sort_order` | Integer | 是 | 排序 |
| `enabled` | `is_enabled` | Boolean | 是 | 启用 |
| `remark` | `remark` | String(512) | 否 | 备注 |

### 3.2 辅助类型（非表）

| 类型 | 说明 |
| --- | --- |
| `MetadataValueSource` | 取值源枚举/常量 |
| `MetadataResolveRequest` | 解析入参模型 |
| `MetadataFieldReferenceChecker` | 删除前引用检查 |

---

## 4. 值对象（含枚举）

| 名称 | 存库 | 说明 |
| --- | --- | --- |
| `valueSource` | String | 与 `MetadataValueSource` 对齐 |
| `maskRule` | String | NONE 等 |
| `dataType` | String | string 等 |

---

## 5. 领域事件

本期无领域事件。

---

## 6. 领域服务

### 6.1 写侧

| 方法 | 命令 | 说明 |
| --- | --- | --- |
| `create` | `CreateMetadataFieldDefCommand` | fieldKey 格式/唯一；取值源定义 |
| `update` | `UpdateMetadataFieldDefCommand` | fieldKey 不可改 |
| `delete` | `DeleteMetadataFieldDefCommand` | 引用检查后物理删 |

实现类：`MetadataFieldDefDomainService`；删除前门禁 `MetadataFieldReferenceChecker`。

### 6.2 读侧 / 解析

| 逻辑操作 | 落点 |
| --- | --- |
| 列表 / 启用列表 / 按 ID | `MetadataFieldAppService` + Repository |
| 按就诊解析键值 | `EncounterMetadataAppService.resolveValues` / `resolveForDocument` |
| 入院上下文装载端口 | `EmrAdmissionContextLoader`（domain 端口；实现在 bridge） |

---

## 7. 不变式

| 规则 ID | 描述 |
| --- | --- |
| BR-EMR-META-001 | `fieldKey` 业务唯一（同租户） |
| BR-EMR-META-002 | `valueSource` 必填且可识别 |
| BR-EMR-META-003 | 仍被模板/文书结构引用的字段禁止删除（引用检查失败） |
| BR-EMR-META-004 | 解析仅读 HIS/上下文，不写对方聚合 |

---

## 8. 上下文映射

| 方向 | 协作方 | 关系 |
| --- | --- | --- |
| 上游 | Encounter / Patient（经 Loader） | 只读上下文 |
| 下游 | DocumentStructureManager | 解析值 → setValuesByNames |
| 下游 | 元数据维护 AS / 文书 init | |

---

## 9. 补充

| 日期 | 版本 | 说明 |
| --- | --- | --- |
| 2026-09-24 | v1.0 | Mode B 回写；产品细则见 modules `05-元数据字段定义与维护.md` |
| 2026-09-24 | v1.1 | 落地 `MetadataFieldDefDomainService` + Command；关闭 AS Gap-EMR-META-001 |
