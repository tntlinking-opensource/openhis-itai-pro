# EMR · 文书类型管理 · 应用服务详细设计（SDD）

> **编码**：AS-EMR-DTYPE-001-SDD · **版本**：v1.0 · **日期**：2026-09-24  
> **对应 AS**：`../AppService/AS-EMR-DTYPE-001-文书类型管理.md`

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | `zxplat-plugin/zxplat-module-emr` · `DocumentTypeAppService` |
| **边界** | IF-EMR-DTYPE-01～05 |
| **编排权威** | 对应 AS §2、§4 |
| **方法权威** | `DM-EMR-001` §3.2；Repository 持久化 |
| **严格对齐** | IF 与 AS 一致 |
| **实现阶段口径** | **当前已上线范围**（Mode B 对齐现有代码） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段口径** | 主键 Long；code 创建后 update 不改 |
| **QueryService 口径** | 无独立 QueryService；Specification + Repository |
| **事务口径** | `@Service` 默认事务；单表写 |
| **HTTP** | `POST /api/app/documentType/{methodName}` |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```text
zxplat-plugin/zxplat-module-emr/module-emr-application/src/main/java/com/zxplat/plugin/emr/application/
  documentType/
    service/DocumentTypeAppService.java
    request/QueryDocumentTypeRequest.java
    request/GetDocumentTypeRequest.java
    request/CreateDocumentTypeRequest.java
    request/UpdateDocumentTypeRequest.java
    request/DeleteDocumentTypeRequest.java
    response/DocumentTypeDto.java
```

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `DocumentTypeRepository` | 类型 CRUD |
| `TemplateRepository` | 删除引用检查、默认模板名 |
| `DocumentRepository` | 删除引用检查 |

### 2.2.1 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `DocumentTypeRepository` | `findAll(Specification, Sort)` | IF-EMR-DTYPE-01 |
| `DocumentTypeRepository` | `findById` | IF-EMR-DTYPE-02～05 |
| `DocumentTypeRepository` | `existsByCode` / `save` / `delete` | IF-EMR-DTYPE-03～05 |
| `TemplateRepository` | `findById` / `count` | IF-EMR-DTYPE-03～05 |
| `DocumentRepository` | `count` | IF-EMR-DTYPE-05 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 分类 code | `DocumentTypeDto.categoryCode` | DM-EMR-001 §3.2 `category_code` | IF-EMR-DTYPE-03/04 |
| 启用 | `CreateDocumentTypeRequest.isEnabled` | DM-EMR-001 §3.2 `enabled` | IF-EMR-DTYPE-03 |

---

## 3. 按接口 ID 命名展开

> 先读完各 IF「领域编排序列」，按序号执行；Gate/Pre 失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-EMR-DTYPE-01 | `getList` | 文书类型列表 | `POST /api/app/documentType/getList` |
| IF-EMR-DTYPE-02 | `get` | 文书类型详情 | `POST /api/app/documentType/get` |
| IF-EMR-DTYPE-03 | `create` | 新建文书类型 | `POST /api/app/documentType/create` |
| IF-EMR-DTYPE-04 | `update` | 更新文书类型 | `POST /api/app/documentType/update` |
| IF-EMR-DTYPE-05 | `delete` | 删除文书类型 | `POST /api/app/documentType/delete` |

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数非空、格式 | 抛 ValidationException |
| **Pre** | 加载实体、引用 count | 抛业务异常 |
| **Cmd** | Repository 持久化 | 事务回滚 |
| **Asm** | `toDto` 装配 | — |

### 3.2 IF-EMR-DTYPE-01 `getList`（文书类型列表）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| enabled | Boolean | 否 | 启用过滤 |
| keyword | String | 否 | code/name 模糊 |

**Response**：`List<DocumentTypeDto>`

**DocumentTypeDto**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 主键 |
| code | String | 编码 |
| name | String | 名称 |
| defaultTemplateId | Long | 默认模板 |
| defaultTemplateName | String | 模板名 |
| isEnabled | Boolean | 启用 |
| sortOrder | Integer | 排序 |
| categoryCode | String | 分类 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

**领域编排序列**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Pre | `SpecificationUtils#tenantFilter` + `notDeleted` + 过滤 | req | 条件下推 |
| 2 | Cmd | `DocumentTypeRepository#findAll` | spec, sort | 仅读路径 |
| 3 | Asm | `DocumentTypeAppService#toDto` | 实体列表 | — |

### 3.3 IF-EMR-DTYPE-02 `get`（文书类型详情）

**Request**：`id` Long 必填。

**Response**：`DocumentTypeDto`（字段同 §3.2）。

**失败码**：id 空 → ValidationException；不存在 → EntityNotFoundException。

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 id 非空 | — |
| 2 | Pre | `DocumentTypeRepository#findById` | 不存在抛异常 |
| 3 | Asm | `toDto` + 可选 `TemplateRepository#findById` 补模板名 | 仅读 |

### 3.4 IF-EMR-DTYPE-03 `create`（新建文书类型）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| code | String | 是 | ≤32 |
| name | String | 是 | ≤64 |
| defaultTemplateId | Long | 否 | 须匹配 type |
| isEnabled | Boolean | 否 | 默认 true |
| sortOrder | Integer | 否 | — |
| categoryCode | String | 否 | — |

**Response**：`DocumentTypeDto`

**失败码**：编码重复；默认模板不存在/未启用/类型不一致。

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | code/name 校验、`existsByCode` | — |
| 2 | Pre | 组装 `DocumentType` | — |
| 3 | Pre | `validateAndApplyDefaultTemplate` | 模板校验 |
| 4 | Cmd | `DocumentTypeRepository#save` | **写库点** insert |
| 5 | Asm | `toDto` | — |

### 3.5 IF-EMR-DTYPE-04 `update`（更新文书类型）

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| id | Long | 是 | — |
| name | String | 否 | — |
| defaultTemplateId | Long | 否 | — |
| clearDefaultTemplate | Boolean | 否 | true 清空默认模板 |
| isEnabled | Boolean | 否 | — |
| sortOrder | Integer | 否 | — |
| categoryCode | String | 否 | — |

**Response**：`DocumentTypeDto`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | id 非空 | — |
| 2 | Pre | `findById` | — |
| 3 | Cmd | 增量 set 字段；若 `clearDefaultTemplate` 则置 null；否则若提供 defaultTemplateId 则 `validateAndApplyDefaultTemplate` | — |
| 4 | Cmd | `save` | **写库点** update |
| 5 | Asm | `toDto` | — |

### 3.6 IF-EMR-DTYPE-05 `delete`（删除文书类型）

**Request**：`id` Long 必填。

**Response**：void

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | id 非空 | — |
| 2 | Pre | `findById` | — |
| 3 | Pre | `assertNotReferenced(code)` | Template/Document count>0 则 ValidationException |
| 4 | Cmd | `DocumentTypeRepository#delete` | **写库点** |

---

## 4. QueryService 与领域事件

无 QueryService；无领域事件（DM-EMR-001 §5）。

---

## 5. 写库顺序（核心写接口视角）

| IF | App 方法 | Cmd 序号 | 聚合写入 |
| --- | --- | --- | --- |
| IF-EMR-DTYPE-03 | create | §3.4-4 | emr_document_type insert |
| IF-EMR-DTYPE-04 | update | §3.5-4 | emr_document_type update |
| IF-EMR-DTYPE-05 | delete | §3.6-4 | emr_document_type delete |

**事务边界**：单 Repository 操作同一事务；失败回滚。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| create | code 唯一约束 | `existsByCode` | §3.4-1 |

### 6.2 并发控制

单表乐观由 DB 约束；无显式版本号。

### 6.3～6.5

审计字段由 `BaseAuditedEntity` 填充；无脱敏。

---

## 7. 测试要点

### 7.1 功能测试（P0）

| 用例 | IF | 自动化 |
| --- | --- | --- |
| 列表过滤 | IF-EMR-DTYPE-01 | 集成 |
| 创建+默认模板 | IF-EMR-DTYPE-03 | 集成 |
| 引用阻止删除 | IF-EMR-DTYPE-05 | 集成 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.0 | 2026-09-24 | Mode B 初版；对齐 DocumentTypeAppService |
