# DM-EMR-001 Document 电子病历文书

> **限界上下文**：电子病历（EMR 插件）  
> **代码模块**：`zxplat-plugin/zxplat-module-emr/module-emr-domain`  
> **包**：`com.zxplat.plugin.emr.domain.document`  
> **聚合类型**：多表聚合（文书根 + 类型配置 + 版本快照 + 数据项索引）  
> **版本**：v1.0 · **日期**：2026-09-24 · **模式**：B（代码先行回写）  
> **写侧服务**：`DocumentManager`、`DocumentStructureManager`

---

## 1. 概述

### 1.1 业务定义

**电子病历文书（Document）** 是某患者在某次就诊下、按文书类型持久化的病历正文实例。正文为 **Tiptap/ProseMirror JSON**（`content`）。可选绑定创建时所用模板（`sourceTemplateId`）以支撑首次病程唯一等规则。

它回答：**「这份就诊病历现在写到哪、状态如何、能否改、版本如何回滚？」**

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 文书生命周期 | 创建草稿 → 保存内容 → 状态变更（提交/锁定/归档）→ 软删 |
| 版本 | 保存时可快照；可按版本号/ID 回滚（回滚后再快照） |
| 另存为 | 复制源文书内容为新草稿 |
| 文书类型 | 启用类型列表、按编码取类型、默认模板初始化正文 |
| 结构 | 解析区域/节/数据元；按数据项名批量写 `attrs.value` |

### 1.3 适用范围

| 场景 | 说明 |
| --- | --- |
| 门诊/住院医生站病历 | 创建、打开、保存、列表；见 AS-OP-DOR-011 / AS-IP-DOR-004 |
| EMR 文书 API | `DocumentAppService` → `POST /api/app/document/*` |
| 元数据回填 | `setValuesByNames` 配合 EncounterMetadata |
| PDF/打印 | 应用层读 content 渲染；本聚合不实现 PDF |

### 1.4 不做的事

- 不实现 HTTP / PDF 渲染 / 编辑器图片落盘  
- 不写诊断、医嘱、收费等跨域事实  
- 不维护模板正文（见 DM-EMR-002）  
- 不定义元数据字段目录（见 DM-EMR-003）

### 1.5 产品装配

EMR 为可选插件：`zxplat.modules.emr.enabled=true`，依赖 `module-emr-starter`。

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

| 物理表 | 实体 | 说明 |
| --- | --- | --- |
| `emr_document` | `Document` | 聚合根 |
| `emr_document_type` | `DocumentType` | 文书类型配置（同包维护） |
| `emr_document_version` | `DocumentVersion` | 版本快照子表 |
| `emr_document_data_item_index` | `DocumentDataItemIndex` | 数据项索引（可选检索） |

### 2.2 状态机（`status` 存 String code）

| 状态 | 含义 | 可改内容 |
| --- | --- | --- |
| `DRAFT` | 草稿 | 是 |
| `SUBMITTED` | 已提交 | 是（业务另有只读规则由应用层门控） |
| `LOCKED` | 已锁定 | **否**（`saveContent` 拒绝） |
| `ARCHIVED` | 已归档 | **否** |

常量：`DocumentManager.STATUS_*`。

### 2.3 建议索引

- `(patient_id, encounter_id, document_type_code, created_at DESC)` 列表/打开最新  
- `(encounter_id, source_template_id)` 首次病程唯一校验  
- `emr_document_version(document_id, version_number)`

---

## 3. 实体

### 3.1 Document（聚合根）

| 字段名（Java） | 列 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `id` | Long | 系统 | 主键（基类） |
| `patientId` | `patient_id` | String(64) | 是 | HIS 患者标识 |
| `encounterId` | `encounter_id` | String(64) | 是 | HIS 就诊标识 |
| `documentTypeCode` | `document_type_code` | String(32) | 是 | 文书类型编码 |
| `title` | `title` | String(256) | 否 | 标题 |
| `content` | `content` | TEXT | 否 | Tiptap JSON |
| `status` | `status` | String(32) | 是 | 状态 code |
| `sourceTemplateId` | `source_template_id` | Long | 否 | 创建源模板 |
| 审计/租户/软删 | 基类 | — | — | `BaseAuditedEntity` + `IMultiTenant` |

### 3.2 DocumentType

| 字段名（Java） | 列 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `code` | `code` | String(32) | 是 | 唯一编码 |
| `name` | `name` | String(64) | 是 | 名称 |
| `defaultTemplateId` | `default_template_id` | Long | 否 | 默认模板 |
| `enabled` | `is_enabled` | Boolean | 是 | 是否启用 |
| `sortOrder` | `sort_order` | Integer | 否 | 排序 |
| `categoryCode` | `category_code` | String(32) | 否 | OUTPATIENT/INPATIENT/… |

### 3.3 DocumentVersion

| 字段名（Java） | 列 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `documentId` | `document_id` | Long | 是 | 文书 ID |
| `versionNumber` | `version_number` | Integer | 是 | 同文书内递增 |
| `content` | `content` | TEXT | 是 | 快照 JSON |

### 3.4 DocumentDataItemIndex

| 字段名（Java） | 列 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `documentId` | `document_id` | Long | 是 | 文书 ID |
| `nodeType` | `node_type` | String | 否 | 节点类型 |
| `dataItemName` | `data_item_name` | String | 否 | 数据项名 |

### 3.5 StructureItem（非持久化模型）

领域结构树节点：`name` / `title` / `value` / `nodeType` / 子节点列表；由 `parseStructure` 产出。

---

## 4. 值对象（含枚举）

| 名称 | 存库口径 | 说明 |
| --- | --- | --- |
| 文书状态 | String code | DRAFT / SUBMITTED / LOCKED / ARCHIVED |
| 文书分类 | String `category_code` | OUTPATIENT / INPATIENT / NOTICE / SURGERY / OTHER |
| 首次病程模板码 | 常量 `PN_FIRST` | 同就诊+同源模板唯一 |

无独立 `@Embeddable` 值对象类。

---

## 5. 领域事件

本期 **无** 领域事件发布（`DocumentManager` 未调用 `DomainEventPublisher`）。  
后续若需病历完成/锁定对外通知，再增补事件并回写本章与 SDD §7。

---

## 6. 领域服务

> 已实现方法清单与源码 public 方法对齐（Mode B）。

### 6.1 写侧 — `DocumentManager`

| 方法 | 语义 | 持久化影响 |
| --- | --- | --- |
| `create` | 创建草稿；可选模板初始化正文/标题；`PN_FIRST` 唯一校验 | insert `emr_document` |
| `saveContent` | 写 content；锁定/归档拒绝；可选快照 | update；可选 insert version |
| `createVersionSnapshot` | 版本号=max+1 插入快照 | insert `emr_document_version` |
| `rollbackToVersion` | 按 versionId 或 versionNumber 回写 content 并再快照 | update + insert version |
| `updateStatus` | 设置 status | update |
| `softDelete` | `isDeleted=true` | update |
| `saveAs` | 复制为新草稿 | insert |

#### 写侧 — `DocumentStructureManager`（纯函数式写 JSON，不落库）

| 方法 | 语义 |
| --- | --- |
| `setValuesByNames` | 按 attrs.name/elementId 批量设 value，返回新 JSON 字符串 |

### 6.2 读侧 — `DocumentManager` / `DocumentStructureManager`

| 方法 | 语义 |
| --- | --- |
| `findById` | 未删文书 |
| `findLatestByPatientAndEncounterAndType` | 同患者就诊类型最新一条 |
| `findPage` | 分页；支持单就诊或就诊 ID 集合 IN |
| `getVersionList` / `getVersion` | 版本列表/单条 |
| `getTemplateById` | 读模板（跨 Template 表，本服务内） |
| `getDocumentTypeByCode` / `listDocumentTypes` | 类型 |
| `parseStructure` | 解析结构树 |
| `extractPlainTextByDataItemName` | 按数据项名抽纯文本 |

本期无独立 `*QueryService` / `queries.model`；读方法在 Manager 上。

---

## 7. 不变式

| 规则 ID | 描述 | 绑定方法 |
| --- | --- | --- |
| BR-EMR-DOC-001 | 锁定或归档文书禁止 `saveContent` | `saveContent` |
| BR-EMR-DOC-002 | 同就诊下源模板编码为 `PN_FIRST` 的文书仅允许一份 | `create` |
| BR-EMR-DOC-003 | 软删后对业务查询不可见（`isDeleted=false`） | 读方法 / `softDelete` |
| BR-EMR-DOC-004 | 回滚必须指定 `versionId` 或 `versionNumber` 之一 | `rollbackToVersion` |
| BR-EMR-DOC-005 | 新建默认 `DRAFT`；另存为亦为 `DRAFT` | `create` / `saveAs` |

---

## 8. 上下文映射

| 方向 | 协作方 | 关系 |
| --- | --- | --- |
| 上游 | Template（DM-EMR-002） | 创建时读模板 content / 校验 PN_FIRST |
| 上游 | DocumentType | 默认模板初始化 |
| 上游 | HIS 患者/就诊标识 | 仅存 String ID，不写 Encounter 聚合 |
| 下游 | DocumentAppService / 医生站病历 AS | 编排 CRUD、PDF、元数据回填 |
| 下游 | EncounterMetadata | 解析值后调用 `setValuesByNames` |

---

## 9. 补充

### 9.1 变更历史

| 日期 | 版本 | 说明 |
| --- | --- | --- |
| 2026-09-24 | v1.0 | M30 Mode B：自 `module-emr-domain` 回写九章 |

### 9.2 与模块旧文档对齐

产品/交互细节仍见 `docs/模块/zxplat-module-emr/00～06`；本 DM 为领域契约真源。  
医生站场景 AS 引用本文件替代原 `zxplat-module-emr` 路径。
