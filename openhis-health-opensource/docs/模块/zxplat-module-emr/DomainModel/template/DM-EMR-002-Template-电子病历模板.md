# DM-EMR-002 Template 电子病历模板

> **限界上下文**：电子病历（EMR 插件）  
> **代码模块**：`zxplat-plugin/zxplat-module-emr/module-emr-domain`  
> **包**：`com.zxplat.plugin.emr.domain.template`  
> **聚合类型**：单表实体聚合  
> **版本**：v1.0 · **日期**：2026-09-24 · **模式**：B（代码先行回写）  
> **写侧**：`TemplateDomainService`（Command）；应用层解析会话后落 Command

---

## 1. 概述

### 1.1 业务定义

**电子病历模板（Template）** 是可复用的病历正文骨架（Tiptap JSON），按 **范围**（个人/科室/全院）与 **就诊类别/站别** 控制医生站可见性。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 模板 CRUD | 创建、更新、软删、启用/停用、复制 |
| 范围 | PERSONAL（owner）/ DEPARTMENT（organizationId）/ HOSPITAL |
| 站别可见 | `TemplateStationKind` + `encounterClassCode` 过滤 |
| 关联文书类型 | `documentTypeCode` |

### 1.3 不做的事

- 不创建文书实例（见 DM-EMR-001）  
- 不解析元数据芯片（见 DM-EMR-003）  
- 不实现 PDF

---

## 2. 聚合根与持久化形态

| 物理表 | 实体 | 说明 |
| --- | --- | --- |
| `emr_template` | `Template` | 聚合根单表 |

**唯一**：`code` 列 unique。

---

## 3. 实体

### 3.1 Template

| 字段名（Java） | 列 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `id` | Long | 系统 | 主键 |
| `name` | `name` | String(128) | 否 | 名称 |
| `code` | `code` | String(64) | 是 | 唯一编码 |
| `documentTypeCode` | `document_type_code` | String(32) | 否 | 文书类型 |
| `scopeLevel` | `scope_level` | String(16) | 是 | PERSONAL/DEPARTMENT/HOSPITAL |
| `ownerPractitionerId` | `owner_practitioner_id` | Long | 条件 | PERSONAL 必填 |
| `organizationId` | `organization_id` | Long | 条件 | DEPARTMENT 必填 |
| `encounterClassCode` | `encounter_class_code` | String(16) | 否 | AMB/IMP/OBSENC |
| `encounterClassEnum` | `encounter_class_enum` | Integer | 否 | 遗留 |
| `encounterTypeEnum` | `encounter_type_enum` | Integer | 否 | 子类型；空=全部 |
| `content` | `content` | TEXT | 否 | 模板 JSON |
| `enabled` | `is_enabled` | Boolean | 是 | 启用 |
| 审计/租户/软删 | 基类 | — | — | |

---

## 4. 值对象（含枚举）

| 名称 | 存库 | 说明 |
| --- | --- | --- |
| `TemplateScopeLevel` | String code | PERSONAL / DEPARTMENT / HOSPITAL |
| `TemplateStationKind` | 应用层过滤用枚举 | 门诊/住院等站别 |
| 就诊类别 | String `encounter_class_code` | 与 EncounterClass.code 对齐 |

---

## 5. 领域事件

本期无领域事件。

---

## 6. 领域服务

### 6.1 写侧

| 方法 | 命令 | 说明 |
| --- | --- | --- |
| `createTemplate` | `CreateTemplateCommand` | 编码唯一；`TemplateScopeValidator` 范围组合 |
| `updateTemplate` | `UpdateTemplateCommand` | 增量更新；范围变更重验 |
| `deleteTemplate` | `DeleteTemplateCommand` | 软删 |
| `setTemplateEnabled` | `SetTemplateEnabledCommand` | 启用切换 |
| `copyTemplate` | `CopyTemplateCommand` | 继承源模板范围与内容 |

实现类：`com.zxplat.plugin.emr.domain.template.service.TemplateDomainService`。  
医生站「另存为」的会话 practitioner/org 解析在 `TemplateAppService`，再写入 Command。

### 6.2 读侧

| 逻辑操作 | 实现落点 |
| --- | --- |
| 分页/列表 | `TemplateRepository` + Specification |
| 站别可见列表 | `TemplateAppService.listForStation` |
| 按 ID 取正文 | `getContent` / `getTemplate` |
| 经 DocumentManager | `DocumentManager.getTemplateById` |

---

## 7. 不变式

| 规则 ID | 描述 |
| --- | --- |
| BR-EMR-TPL-001 | `code` 租户内唯一 |
| BR-EMR-TPL-002 | PERSONAL 须 `ownerPractitionerId`；DEPARTMENT 须 `organizationId` |
| BR-EMR-TPL-003 | 医生站仅见 `enabled=true` 且范围/站别匹配的模板 |
| BR-EMR-TPL-004 | 软删不可见 |

---

## 8. 上下文映射

| 方向 | 协作方 | 关系 |
| --- | --- | --- |
| 下游 | Document.create | 读 content 初始化文书 |
| 下游 | 医生站病历 / 模板管理 AS | 列表与维护 |
| 并列 | DocumentType | `documentTypeCode` 关联 |

---

## 9. 补充

| 日期 | 版本 | 说明 |
| --- | --- | --- |
| 2026-09-24 | v1.0 | Mode B 回写 |
| 2026-09-24 | v1.1 | 落地 `TemplateDomainService` + Command；关闭 AS Gap-EMR-TPL-001 |
