# DM-EMR-001 Document 电子病历文书 · DomainModel-SDD

> **对应 DM**：`docs/模块/zxplat-module-emr/DomainModel/document/DM-EMR-001-Document-电子病历文书.md`（v1.0）  
> **目标工程**：`zxplat-plugin/zxplat-module-emr/module-emr-domain`  
> **限界上下文**：EMR 插件域  
> **聚合**：Document（子实体 DocumentType / DocumentVersion / DocumentDataItemIndex）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-EMR-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `module-emr-domain` |
| 包规则 `com.zxplat.plugin.emr.domain.document` | 满足 | 插件包（非 `com.zxplat.domain`） |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、分页等） | 满足 | 见 §9；`patientId`/`encounterId` 为 String（HIS 标识） |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖 DocumentManager 写方法 + StructureManager.setValuesByNames |
| DM 第 7 章 BR | 满足 | §6 |
| DM 第 5 章事件 | 满足 | 本期无事件 → §7 |

**与 DM 显式差异**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| Command 文件 | DM 方法为多参 | SDD 给出目标态 Command；现码多参直调，成码收敛时对齐 |
| QueryModel | DM 无独立 QueryService | §4.6 / §5.2 以 Manager 返回实体/模型为准 |

---

## 1. 设计目标与边界

### 1.1 设计目标

固化 Document 聚合持久化与 `DocumentManager` / `DocumentStructureManager` 契约，供应用层（`DocumentAppService`）与后续领域收敛对照。Mode B：以现有实现为真源。

### 1.2 边界

- **在内**：`emr_document*` 表、版本、结构 JSON 变换、文书类型读/列表。  
- **在外**：HTTP、PDF 渲染、图片上传、模板维护写、元数据目录维护、诊断医嘱写。  
- **跨域**：仅引用 Template / HIS 标识；不写对方聚合。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat-plugin/zxplat-module-emr/module-emr-domain`

### 2.2 包结构（`com.zxplat.plugin.emr.domain.document`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `Document`、`DocumentType`、`DocumentVersion`、`DocumentDataItemIndex` |
| `model` | `StructureItem` |
| `repository` | `DocumentRepository`、`DocumentTypeRepository`、`DocumentVersionRepository` |
| `service` | `DocumentManager`、`DocumentStructureManager` |
| `access` | `EmrPdfRenderer` 端口（应用层适配；本 SDD 不展开 PDF） |

**存储口径**：状态/分类为 String code；软删 `isDeleted`；多租户 `IMultiTenant`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 | `Long id` |
| HIS 标识 | `patientId` / `encounterId` 为 **String** |
| 时间 | 审计字段 `LocalDateTime`（基类） |
| 软删 | `Boolean isDeleted` |
| 实体 | `@Entity` + `@Table` / `@Column` |
| 仓储 | `JpaRepository` 接口 |
| 事务 | Manager 写方法 `@Transactional(rollbackFor = Exception.class)` |
| 事件 | 本期不发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `emr_document` | `Document` | 聚合根 |
| `emr_document_type` | `DocumentType` | 类型配置 |
| `emr_document_version` | `DocumentVersion` | 版本快照 |
| `emr_document_data_item_index` | `DocumentDataItemIndex` | 数据项索引 |

### 4.2 聚合根 `Document`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `patientId` | `String` | 是 | HIS 患者 |
| `encounterId` | `String` | 是 | HIS 就诊 |
| `documentTypeCode` | `String` | 是 | 文书类型编码 |
| `title` | `String` | 否 | 标题 |
| `content` | `String` | 否 | Tiptap JSON TEXT |
| `status` | `String` | 是 | DRAFT/SUBMITTED/LOCKED/ARCHIVED |
| `sourceTemplateId` | `Long` | 否 | 源模板 |
| `isDeleted` | `Boolean` | 系统 | 软删 |
| `tenantId` | `Long` | 系统 | 租户 |
| `createdAt` / `updatedAt` 等 | `LocalDateTime` | 系统 | 审计 |

### 4.3 子实体 `DocumentType`

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `code` | `String` | 是 | 唯一 |
| `name` | `String` | 是 | 名称 |
| `defaultTemplateId` | `Long` | 否 | 默认模板 |
| `enabled` | `Boolean` | 是 | 启用 |
| `sortOrder` | `Integer` | 否 | 排序 |
| `categoryCode` | `String` | 否 | 分类 |

### 4.4 子实体 `DocumentVersion`

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `documentId` | `Long` | 是 | 文书 |
| `versionNumber` | `Integer` | 是 | 递增 |
| `content` | `String` | 是 | 快照 |

### 4.5 子实体 `DocumentDataItemIndex`

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 主键 |
| `documentId` | `Long` | 是 | 文书 |
| `nodeType` | `String` | 否 | 节点类型 |
| `dataItemName` | `String` | 否 | 数据项名 |

### 4.6 QueryModel / 读出参

#### 4.6.1 `findById` / `findLatest*` / `findPage`

| 字段名（Java） | 类型 | 说明 |
| -------------- | ---- | ---- |
| （同 Document 实体字段） | — | 直接返回实体 / `Page<Document>` |

#### 4.6.2 `getVersionList` / `getVersion`

| 字段名（Java） | 类型 | 说明 |
| -------------- | ---- | ---- |
| （同 DocumentVersion） | — | 列表或 Optional |

#### 4.6.3 `parseStructure` → `StructureItem`

| 字段名（Java） | 类型 | 说明 |
| -------------- | ---- | ---- |
| `name` | `String` | 数据项名 |
| `title` | `String` | 标题 |
| `value` | `Object` | 值 |
| `nodeType` | `String` | 节点类型 |
| `children` | `List<StructureItem>` | 子节点 |

#### 4.6.4 `listDocumentTypes` / `getDocumentTypeByCode`

| 字段名（Java） | 类型 | 说明 |
| -------------- | ---- | ---- |
| （同 DocumentType） | — | |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `DocumentManager` / `DocumentStructureManager`

#### 5.1.1 `create`

**命令文件**：`CreateDocumentCommand`（目标态；现码多参）

**字段全集**

| 字段 | 类型 | 必填 |
| ---- | ---- | ---- |
| `patientId` | String | 是 |
| `encounterId` | String | 是 |
| `documentTypeCode` | String | 是 |
| `templateId` | Long | 否 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertFirstProgressUnique` |
| `unitTestCaseId` | `UT-EMR-DOC-CREATE-01` |

**方法契约摘要**

- 前置：患者/就诊/类型非空；若模板 `code=PN_FIRST` 且同就诊已存在同源模板文书 → 失败。  
- 持久化：insert `emr_document`，status=`DRAFT`，content 来自模板或类型默认模板或空 doc。  
- 失败：`ValidationException`「该就诊已存在首次病程记录」。  
- 事件：无。

#### 5.1.2 `saveContent`

**命令文件**：`SaveDocumentContentCommand`

**字段全集**

| 字段 | 类型 | 必填 |
| ---- | ---- | ---- |
| `documentId` | Long | 是 |
| `content` | String | 是 |
| `saveVersion` | boolean | 是 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertEditableStatus` |
| `unitTestCaseId` | `UT-EMR-DOC-SAVE-01` |

**方法契约摘要**

- 前置：文书存在且未删；status 非 LOCKED/ARCHIVED。  
- 持久化：update content；若 `saveVersion` 则 `createVersionSnapshot`。  
- 失败：`EntityNotFoundException` / `ValidationException`「文书已锁定或归档，不可修改」。  
- 事件：无。

#### 5.1.3 `createVersionSnapshot`

**命令文件**：`CreateDocumentVersionSnapshotCommand`

**字段全集**

| 字段 | 类型 | 必填 |
| ---- | ---- | ---- |
| `document` | Document | 是（已加载） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `nextVersionNumber` |
| `unitTestCaseId` | `UT-EMR-DOC-VER-01` |

**方法契约摘要**

- 前置：document.id 非空。  
- 持久化：insert version，versionNumber=max+1，content 空则 `{}`。  
- 失败：仓储异常。  
- 事件：无。

#### 5.1.4 `rollbackToVersion`

**命令文件**：`RollbackDocumentVersionCommand`

**字段全集**

| 字段 | 类型 | 必填 |
| ---- | ---- | ---- |
| `documentId` | Long | 是 |
| `versionId` | Long | 二选一 |
| `versionNumber` | Integer | 二选一 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertVersionSelector` |
| `unitTestCaseId` | `UT-EMR-DOC-ROLLBACK-01` |

**方法契约摘要**

- 前置：文书存在；versionId 或 versionNumber 至少一个。  
- 持久化：content←版本；再 insert 新快照。  
- 失败：未指定选择器 → `ValidationException`；版本不存在 → `EntityNotFoundException`。  
- 事件：无。

#### 5.1.5 `updateStatus`

**命令文件**：`UpdateDocumentStatusCommand`

**字段全集**

| 字段 | 类型 | 必填 |
| ---- | ---- | ---- |
| `documentId` | Long | 是 |
| `status` | String | 是 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertKnownStatus`（应用层可加强） |
| `unitTestCaseId` | `UT-EMR-DOC-STATUS-01` |

**方法契约摘要**

- 前置：文书存在。  
- 持久化：update status。  
- 失败：`EntityNotFoundException`。  
- 事件：无。

#### 5.1.6 `softDelete`

**命令文件**：`SoftDeleteDocumentCommand`

**字段全集**

| 字段 | 类型 | 必填 |
| ---- | ---- | ---- |
| `documentId` | Long | 是 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `markDeleted` |
| `unitTestCaseId` | `UT-EMR-DOC-DEL-01` |

**方法契约摘要**

- 前置：文书存在且未删。  
- 持久化：`isDeleted=true`。  
- 失败：`EntityNotFoundException`。  
- 事件：无。

#### 5.1.7 `saveAs`

**命令文件**：`SaveAsDocumentCommand`

**字段全集**

| 字段 | 类型 | 必填 |
| ---- | ---- | ---- |
| `sourceDocumentId` | Long | 是 |
| `patientId` | String | 是 |
| `encounterId` | String | 是 |
| `documentTypeCode` | String | 否（空则沿用源） |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `copyAsDraft` |
| `unitTestCaseId` | `UT-EMR-DOC-SAVEAS-01` |

**方法契约摘要**

- 前置：源文书存在。  
- 持久化：insert 新草稿，复制 title/content。  
- 失败：`EntityNotFoundException`。  
- 事件：无。

#### 5.1.8 `setValuesByNames`（DocumentStructureManager，不落库）

**命令文件**：`SetDocumentValuesByNamesCommand`

**字段全集**

| 字段 | 类型 | 必填 |
| ---- | ---- | ---- |
| `contentJson` | String | 是 |
| `nameToValue` | Map<String,Object> | 是 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `mutateAttrsValueByName` |
| `unitTestCaseId` | `UT-EMR-DOC-SETVAL-01` |

**方法契约摘要**

- 前置：无；空 map 则原样返回。  
- 持久化：无（返回新 JSON，由调用方再 `saveContent`）。  
- 失败：JSON 解析失败 → RuntimeException。  
- 事件：无。

### 5.2 读侧 — `DocumentManager` / `DocumentStructureManager`

| 方法 | 入参要点 | 出参 |
| ---- | -------- | ---- |
| `findById` | id | Optional\<Document\> |
| `findLatestByPatientAndEncounterAndType` | patientId, encounterId, type | Optional\<Document\> |
| `findPage` | 过滤 + Pageable；支持 encounterIds IN | Page\<Document\> |
| `getVersionList` | documentId | List\<DocumentVersion\> |
| `getVersion` | documentId + versionId/number | Optional\<DocumentVersion\> |
| `getTemplateById` | templateId | Optional\<Template\> |
| `getDocumentTypeByCode` | code | Optional\<DocumentType\> |
| `listDocumentTypes` | — | List\<DocumentType\> 启用 |
| `parseStructure` | contentJson, firstLevelOnly, nodeTypes | List\<StructureItem\> |
| `extractPlainTextByDataItemName` | contentJson, name | String |

---

## 6. 不变式映射（DM §7）

| BR | 实现落点 |
| -- | -------- |
| BR-EMR-DOC-001 | `saveContent` 状态门禁 |
| BR-EMR-DOC-002 | `create` PN_FIRST exists 检查 |
| BR-EMR-DOC-003 | 读 Spec `isDeleted=false` |
| BR-EMR-DOC-004 | `rollbackToVersion` 选择器校验 |
| BR-EMR-DOC-005 | create/saveAs 默认 DRAFT |

---

## 7. 事件契约与触发规则（DM §5）

| 事件 | 触发方法 | 最小载荷 | 说明 |
| ---- | -------- | -------- | ---- |
| （无） | — | — | 本期不发布领域事件 |

---

## 8. 删除与可见性契约

- 删除：`softDelete` 置 `isDeleted=true`，不物理删版本行。  
- 可见：业务读路径一律 `isDeleted=false`。  
- 锁定/归档：不可 `saveContent`；`updateStatus` 可改状态（解锁等由应用策略决定）。

---

## 9. 框架优先适配规则

- 遵循项目级成码门禁；插件包名保持 `com.zxplat.plugin.emr.*`。  
- PDF 端口 `EmrPdfRenderer` 由 application 适配，领域仅声明 access。  
- `patientId`/`encounterId` 保持 String，禁止强行改 Long。

---

## 10. 生成与验收清单

- [ ] Manager 写方法与 §5.1 一一对应  
- [ ] 读方法与 §5.2 对应  
- [ ] BR-EMR-DOC-001～005 有单测或手工用例 ID  
- [ ] 应用层 AS-EMR-DOC / AS-OP-DOR-011 引用本聚合  

---

## 11. 单次会话代码生成门禁（DoD）

Mode B 现码已通过 compile；本 SDD 用于对照与后续收敛 Command，**非**要求本会话重生成领域层。若新开成码会话：仅改白名单方法、同步 DM、跑模块编译。

---

## 定稿检查清单（可打勾）

- [x] 主章节齐全  
- [x] §5.1 方法数 = DM §6.1 写方法（含 setValuesByNames）  
- [x] §4 字段对齐 DM §3  
- [x] §7 与 DM §5（无事件）一致  
- [ ] 独立 Command 类文件（目标态，现码未拆）  

## 定稿评分（百分制）

| 维度 | 分 | 说明 |
| ---- | -- | ---- |
| 结构门禁 | 25/25 | 章节齐全 |
| 方法覆盖 | 25/25 | 与 Manager 对齐 |
| 字段完整 | 20/20 | 对齐 DM |
| Command 落地 | 10/15 | 目标态声明 |
| 事件/BR | 10/10 | 无事件已声明 |
| **合计** | **90** | Mode B 可对照；Command 收敛前不作「从零成码唯一依据」 |

**本 SDD 正文结束。**
