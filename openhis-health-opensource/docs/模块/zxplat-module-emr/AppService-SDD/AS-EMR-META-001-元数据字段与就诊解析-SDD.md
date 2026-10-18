# EMR · 元数据字段与就诊解析 · 应用服务详细设计（SDD）

> **编码**：AS-EMR-META-001-SDD · **版本**：v1.0 · **日期**：2026-09-24  
> **对应 AS**：`../AppService/AS-EMR-META-001-元数据字段与就诊解析.md`

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | `MetadataFieldAppService`（`metadataField`）、`EncounterMetadataAppService`（`encounterMetadata`） |
| **边界** | IF-EMR-META-01～09 |
| **实现阶段口径** | **当前已上线范围** |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP** | `POST /api/app/metadataField/*`、`POST /api/app/encounterMetadata/*` |
| **分页** | `QueryMetadataFieldRequest` 继承 `BaseQueryRequest`（pageNo/pageSize/searchKey） |

---

## 2. 代码结构

### 2.1 包路径

```text
com/zxplat/plugin/emr/application/
  metadataField/service/MetadataFieldAppService.java
  metadataField/request/*.java
  metadataField/response/MetadataFieldDto.java, MetadataFieldReferenceDto.java
  encounterMetadata/service/EncounterMetadataAppService.java
  encounterMetadata/request/ResolveEncounterMetadataRequest.java
  document/request/ResolveDocumentMetadataRequest.java
  encounterMetadata/response/MetadataValuesDto.java
  metadata/support/MetadataValueResolver.java
```

### 2.2 依赖注入

| Bean | 用途 |
| --- | --- |
| `MetadataFieldDefRepository` | 字段 CRUD/列表 |
| `MetadataFieldReferenceChecker` | 模板引用 |
| `MetadataValueResolver` | IF-08 解析链 |
| `DocumentManager` | IF-09 反查文书 |
| `ObjectMapper` | sourceConfig JSON |

### 2.2.1 领域方法白名单

| 类型 | 方法 | IF |
| --- | --- | --- |
| `MetadataFieldDefRepository` | findAll/save/delete/findById... | 01～06 |
| `MetadataFieldReferenceChecker` | isReferenced/findReferencingTemplateNames | 06/07 |
| `MetadataValueResolver` | resolve | 08 |
| `DocumentManager` | findById | 09 |
| `EncounterMetadataAppService` | resolveValues | 09 委托 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| valueSource | `CreateMetadataFieldRequest.valueSource` | DM-EMR-003 §3.1 | IF-04/05 |
| maskRule | `CreateMetadataFieldRequest.maskRule` | DM-EMR-003 §3.1 | 默认 NONE |
| MetadataValueSource | `com.zxplat.plugin.emr.domain.metadata.MetadataValueSource` | DM-EMR-003 §4 | 校验用 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法 | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-EMR-META-01 | `getList` | 字段定义分页 | `POST /api/app/metadataField/getList` |
| IF-EMR-META-02 | `getEnabledList` | 启用字段列表 | `POST /api/app/metadataField/getEnabledList` |
| IF-EMR-META-03 | `getById` | 字段详情 | `POST /api/app/metadataField/getById` |
| IF-EMR-META-04 | `create` | 新建字段 | `POST /api/app/metadataField/create` |
| IF-EMR-META-05 | `update` | 更新字段 | `POST /api/app/metadataField/update` |
| IF-EMR-META-06 | `delete` | 删除字段 | `POST /api/app/metadataField/delete` |
| IF-EMR-META-07 | `checkReference` | 引用检查 | `POST /api/app/metadataField/checkReference` |
| IF-EMR-META-08 | `resolveValues` | 解析就诊元数据 | `POST /api/app/encounterMetadata/resolveValues` |
| IF-EMR-META-09 | `resolveForDocument` | 按文书解析 | `POST /api/app/encounterMetadata/resolveForDocument` |

#### 3.1.1 编排约定

同 AS-EMR-DTYPE SDD §3.1.1（Gate/Pre/Cmd/Asm）。

### 3.2 IF-EMR-META-01 `getList`

**Request（QueryMetadataFieldRequest + BaseQueryRequest）**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| pageNo | Integer | 否 | 默认 1（内部 0-based 转换见实现） |
| pageSize | Integer | 否 | 默认 10 |
| searchKey | String | 否 | 与 keyword 二选一参与 keywordLike |
| keyword | String | 否 | fieldKey/label |
| valueSource | String | 否 | 过滤 |
| isEnabled | Boolean | 否 | 过滤 |

**Response**：`PagedResult<MetadataFieldDto>`

**MetadataFieldDto 字段**：id, fieldKey, label, dataType, editableDefault, valueSource, sourcePath, sourceConfig(Map), defaultValue, maskRule, formatPattern, sortOrder, isEnabled, remark

**编排序列**：tenantFilter → notDeleted → 条件 → `findAll` → `toDto`（仅读）

### 3.3 IF-EMR-META-02 `getEnabledList`

**Request**：`keyword` String 可选。

**Response**：`List<MetadataFieldDto>`

**编排序列**：`findByEnabledTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc` → 内存 keyword 过滤 → toDto

### 3.4 IF-EMR-META-03 `getById`

**Request**：`id` Long 必填。

**编排序列**：Gate id → `findByIdAndIsDeletedFalse` → toDto

### 3.5 IF-EMR-META-04 `create`

**CreateMetadataFieldRequest**：fieldKey*, label*, dataType, editableDefault, valueSource*, sourcePath, sourceConfig, defaultValue, maskRule, formatPattern, sortOrder, isEnabled, remark

**编排序列**：Request → `CreateMetadataFieldDefCommand` → **`MetadataFieldDefDomainService#create`** → toDto

> Gap-EMR-META-001 **已关闭**（写侧 DomainService）。

### 3.6 IF-EMR-META-05 `update`

**UpdateMetadataFieldRequest**：id* + 可选各字段（fieldKey 不可改）

**编排序列**：load → 增量 set → validateDefinition → save（写库点）

### 3.7 IF-EMR-META-06 `delete`

**Request**：id*

**编排序列**：load → `referenceChecker#isReferenced` → **`delete`（写库点，物理删）**

### 3.8 IF-EMR-META-07 `checkReference`

**Request**：fieldKey*

**Response MetadataFieldReferenceDto**：referenced boolean, templateNames List

**编排序列**：`findReferencingTemplateNames` → 装配 DTO（仅读）

### 3.9 IF-EMR-META-08 `resolveValues`

**ResolveEncounterMetadataRequest**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| patientId | String | 是 | — |
| encounterId | String | 否 | — |
| documentId | Long | 否 | — |
| fieldKeys | List\<String\> | 否 | 子集 |
| metadataOverrides | Map\<String,String\> | 否 | 覆盖 |

**Response MetadataValuesDto**：values Map, maskedFields List

**编排序列**：Gate patientId → build `MetadataResolveRequest` → **`MetadataValueResolver#resolve`** → toDto（仅读）

### 3.10 IF-EMR-META-09 `resolveForDocument`

**ResolveDocumentMetadataRequest**：documentId*，fieldKeys，metadataOverrides

**编排序列**：Gate documentId → **`DocumentManager#findById`** → 组装 ResolveEncounterMetadataRequest → **复用 IF-EMR-META-08 序列**

---

## 4. QueryService 与领域事件

无 QueryService；无事件。

---

## 5. 写库顺序

| IF | Cmd | 表 |
| --- | --- | --- |
| 04 create | save | emr_metadata_field_def |
| 05 update | save | 同上 |
| 06 delete | delete | 物理删 |

---

## 6. 非功能性需求实现

| 项 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 脱敏 | maskRule 在 Resolver | §3.9-2 |
| 租户 | SpecificationUtils.tenantFilter | §3.2 |

---

## 7. 测试要点

P0：fieldKey 唯一；引用阻止删除；resolveValues patientId 门禁。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-24 | Mode B；metadataField / encounterMetadata 双 serviceName |
