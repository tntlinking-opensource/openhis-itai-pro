# EMR · 模板管理 · 应用服务详细设计（SDD）

> **编码**：AS-EMR-TPL-001-SDD · **版本**：v1.0 · **日期**：2026-09-24  
> **对应 AS**：`../AppService/AS-EMR-TPL-001-模板管理.md`

---

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| **模块** | `com.zxplat.plugin.emr.application.template` · `TemplateAppService` |
| **实现阶段口径** | **当前已上线范围** |
| **HTTP** | `POST /api/app/template/{methodName}` |
| **Gap 对齐** | Gap-EMR-TPL-001 **已关闭**：写 Cmd 经 `TemplateDomainService` |

---

## 2. 代码结构

### 2.1 包路径

```text
template/service/TemplateAppService.java
template/request/QueryTemplateRequest.java, ListTemplatesForStationRequest.java,
  GetTemplateContentRequest.java, GetTemplateRequest.java,
  CreateTemplateRequest.java, UpdateTemplateRequest.java, DeleteTemplateRequest.java,
  SetTemplateEnabledRequest.java, CopyTemplateRequest.java
template/response/TemplateDto.java, TemplatePageResponse.java
documentType/service/DocumentTypeAppService.java  # IF-10 委托
```

### 2.2 依赖注入

| 依赖 | 用途 |
| --- | --- |
| `TemplateRepository` | CRUD/Specification |
| `DocumentManager` | documentTypeName |
| `DocumentTypeAppService` | getDocumentTypes |
| `ICurrentUser` | 站别可见/医生站 scope |

### 2.2.1 白名单

| 类型 | 方法 | IF |
| --- | --- | --- |
| `TemplateRepository` | findAll(spec, page/sort), findByIdAndIsDeletedFalse, save | 01～09 |
| `DocumentManager` | getDocumentTypeByCode | Asm |
| `DocumentTypeAppService` | getList | 10 |
| `TemplateDomainService` | create/update/delete/setEnabled/copy | 05～09 |
| `TemplateScopeValidator` | normalize/validate（domain） | 05/06 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 范围 | `CreateTemplateRequest.scopeLevel` | DM-EMR-002 §3.1 scope_level | IF-05 |
| 站别 | `ListTemplatesForStationRequest.stationKind` | DM-EMR-002 §4 TemplateStationKind | IF-02 |
| 就诊类别 | `CreateTemplateRequest.encounterClassCode` | DM-EMR-002 §3.1 | AMB/IMP |
| TemplateScopeLevel | `com.zxplat.plugin.emr.domain.enums.TemplateScopeLevel` | DM-EMR-002 §4 | — |
| TemplateStationKind | `com.zxplat.plugin.emr.domain.enums.TemplateStationKind` | DM-EMR-002 §4 | — |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法 | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-EMR-TPL-01 | `getList` | 模板分页列表 | `POST /api/app/template/getList` |
| IF-EMR-TPL-02 | `listForStation` | 医生站模板树 | `POST /api/app/template/listForStation` |
| IF-EMR-TPL-03 | `getContent` | 载入模板正文（简） | `POST /api/app/template/getContent` |
| IF-EMR-TPL-04 | `getTemplate` | 模板详情 | `POST /api/app/template/getTemplate` |
| IF-EMR-TPL-05 | `createTemplate` | 新建模板 | `POST /api/app/template/createTemplate`（别名 `create`→同路径 `create`） |
| IF-EMR-TPL-06 | `updateTemplate` | 更新模板 | `POST /api/app/template/updateTemplate`（别名 `update`） |
| IF-EMR-TPL-07 | `deleteTemplate` | 删除模板 | `POST /api/app/template/deleteTemplate`（别名 `delete`） |
| IF-EMR-TPL-08 | `setTemplateEnabled` | 启用停用 | `POST /api/app/template/setTemplateEnabled` |
| IF-EMR-TPL-09 | `copyTemplate` | 复制模板 | `POST /api/app/template/copyTemplate` |
| IF-EMR-TPL-10 | `getDocumentTypes` | 文书类型下拉 | `POST /api/app/template/getDocumentTypes` |

> 别名 HTTP：`POST /api/app/template/create`、`update`、`delete` 与上表 canonical 方法同名注册。

#### 3.1.1 编排约定

Gate/Pre/Cmd/Asm 四类；Pre 失败不进 Cmd。

### 3.2 IF-EMR-TPL-01 `getList`

**QueryTemplateRequest**：documentTypeCode, scopeLevel, encounterClassCode, organizationId, enabled, searchKey/keyword, pageNo, pageIndex, pageSize

**Response TemplatePageResponse**：content List\<TemplateDto\>（无 content 字段）, totalCount Long

**TemplateDto（列表行）**：id, name, code, documentTypeCode, documentTypeName, scopeLevel, scopeLevelName, organizationId, ownerPractitionerId, encounterClassCode, encounterClassName, encounterClassEnum, encounterTypeEnum, enabled/isEnabled, createdAt, updatedAt, createdBy, updatedBy（**不含 content**）

**序列**：resolvePageNo/Size → buildTemplateSpec → findAll page → toTemplateDto(false)

### 3.3 IF-EMR-TPL-02 `listForStation`

**ListTemplatesForStationRequest**：stationKind*（OUTPATIENT/INPATIENT）, documentTypeCode, enabled

**Response**：`List<TemplateDto>`（无 content）

**序列**：TemplateStationKind.fromCode → 取 practitionerId/organizationId → buildStationSpec → findAll sort name → scope 排序 → toTemplateDto(false)

### 3.4 IF-EMR-TPL-03 `getContent`

**GetTemplateContentRequest**：templateId String

**Response**：TemplateDto **含 content**；无效 id 返回 null

**序列**：若 templateId 空返回 null；否则 **复用 IF-EMR-TPL-04**（构造 GetTemplateRequest）

### 3.5 IF-EMR-TPL-04 `getTemplate`

**GetTemplateRequest**：id String（雪花字符串）

**序列**：parseTemplateId → findByIdAndIsDeletedFalse → toTemplateDto(true)

### 3.6 IF-EMR-TPL-05 `createTemplate`

**CreateTemplateRequest**：name, code, documentTypeCode, scopeLevel, organizationId, ownerPractitionerId, encounterClassCode, encounterTypeEnum, content, enabled, fromDoctorStation

**序列**：
1. 医生站则 App 解析会话写入 `CreateTemplateCommand`；否则组装 Command（含 scope）
2. Cmd **`TemplateDomainService#createTemplate`** 写库点（编码唯一 + 范围校验）
3. Asm toTemplateDto(true)

**失败码**：IllegalArgumentException 编码重复；ValidationException 医生站 scope / 范围组合

### 3.7 IF-EMR-TPL-06 `updateTemplate`

**UpdateTemplateRequest**：id* + 可选字段（同 Create 增量）

**序列**：Request → `UpdateTemplateCommand` → **`TemplateDomainService#updateTemplate`** → toTemplateDto

### 3.8 IF-EMR-TPL-07 `deleteTemplate`

**DeleteTemplateRequest**：id；空 noop

**序列**：parse id → **`TemplateDomainService#deleteTemplate`**（软删）

### 3.9 IF-EMR-TPL-08 `setTemplateEnabled`

**SetTemplateEnabledRequest**：templateId*, enabled

**序列**：→ **`TemplateDomainService#setTemplateEnabled`**

### 3.10 IF-EMR-TPL-09 `copyTemplate`

**CopyTemplateRequest**：sourceTemplateId*, newName, newCode

**序列**：→ **`TemplateDomainService#copyTemplate`** → toTemplateDto

### 3.11 IF-EMR-TPL-10 `getDocumentTypes`

**QueryDocumentTypeRequest**（可选，默认 enabled=true）

**Response**：`List<DocumentTypeItemDto>`（code, name）

**序列**：DocumentTypeAppService#getList → map item（仅读）

---

## 4. QueryService 与领域事件

无 QueryService；无事件。

---

## 5. 写库顺序

| IF | 方法 | 表 emr_template |
| --- | --- | --- |
| 05 | createTemplate | insert |
| 06 | updateTemplate | update |
| 07 | deleteTemplate | soft update |
| 08 | setTemplateEnabled | update |
| 09 | copyTemplate | insert |

---

## 6. 非功能性需求

列表/树默认不含 content；医生站 scope 与会话科室同源（P0）。

---

## 7. 测试要点

P0：listForStation 可见性；fromDoctorStation 仅 PERSONAL/DEPARTMENT；code 唯一。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-24 | Mode B 全 IF |
