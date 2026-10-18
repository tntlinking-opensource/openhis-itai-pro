# 基础资料 · 打印设计 · 应用服务详细设计（SDD）

> **对应 AS**：`AS-BAS-MST-019-打印设计.md`（v1.2）  
> **编码**：AS-BAS-MST-019-SDD · **版本**：v1.2 · **日期**：2026-06-22

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模板服务** | `PrintTemplateAppService` · IF-BAS-PRT-11～17 |
| **绑定服务** | `PrintBindingAppService` · IF-BAS-PRT-21～22 |
| **编排权威** | `AS-BAS-MST-019` §2、§4 |
| **方法权威** | `DM-PRT-001-SDD` Manager；`DM-PRT-003-SDD` Resolver |
| **实现阶段口径** | **目标态全量契约**（publish 插行、Binding 新建、busNo 改名） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **HTTP 模板** | `POST /api/app/printTemplate/{methodName}` |
| **HTTP 绑定** | `POST /api/app/printBinding/{methodName}` |
| **权限** | `Print:design`；本期不写 @RequirePermission |
| **事务** | AppService 不 `@Transactional`；`publish` 原子性靠 `PrtTemplateDomainService#publish` `@Transactional` |
| **archived** | publish 产生 archived；列表**默认不含** archived，传 `status=archived` 可查历史 |
| **Web 设计器** | `openhis-pro-ui/apps/web-ele/src/views/basic/print-template/designer/index.vue` · **vue-plugin-hiprint** |

---

## 2. 代码结构

### 2.0 Web 前端（设计器）

```text
openhis-pro-ui/apps/web-ele/src/
  views/basic/print-template/
    index.vue              # 列表 IF-11～16 入口
    designer/index.vue     # hiprint 画布 IF-13/14/15/ensureDesignDraft
  hiprint/                 # sv-print / hiprint 设计器组件
  api/print/print-template.ts   # busNo / PrintJson / ensureDesignDraft / saveDraft / publish
  utils/print/             # 运行时 print pipeline
```

| 步骤 | 前端 |
| ---- | ---- |
| 进入设计 | 列表点「设计」→ 路由 `?busNo=` → `getByBusNo` |
| 编辑 | `hiprint.PrintTemplate({ template })` → `design('#hiprint-design-container')` |
| 保存 | `getJson()` → 补 `engine`/`schemaVersion` → `saveDraft` |
| 预览 | `preview(samplePrintData)`（IF-17） |

> PrintJson 根结构与 hiprint 模板 JSON 兼容；存库前须符合 DM-PRT-001 §3.2。

### 2.1 包路径（类级清单）

```text
health-application/src/main/java/com/openhis/health/application/print/
  printtemplate/service/PrintTemplateAppService.java
  printbinding/service/PrintBindingAppService.java
zxplat-domain/src/main/java/com/openhis/health/domain/print/
  printtemplate/service/PrtTemplateDomainService.java
  printtemplate/service/PrtTemplateQueryService.java
  printbinding/service/PrtPrintBindingDomainService.java
health-module-support/.../PrintOperatorResolver.java
```

> 类名迁移：`GetByPrintCodeRequest` → `GetByBusNoRequest`；`getByPrintCode` → `getByBusNo`。

### 2.2 依赖注入

| Bean | 用途 |
| ---- | ---- |
| `PrtTemplateManager` | 模板读写 |
| `PrintJsonValidator` | JSON 校验 |
| `PrintBindingResolver` | 绑定 resolve/save/list |
| `TenantContext` | 租户 |
| `PrintOperatorResolver` | organizationId（draft 可选） |

### 2.3 领域方法白名单

| 聚合 / 服务 | 方法 | 用于 IF |
| ----------- | ---- | ------- |
| `PrtTemplateManager` | `listMergedForTenant` | 11、21 |
| `PrtTemplateManager` | `resolveForDesign` | 13 |
| `PrtTemplateManager` | `findTenantDraft` | 12、14、15 |
| `PrtTemplateManager` | `saveDraft(SaveDraftCommand)` | 12、14 |
| `PrtTemplateManager` | `publish(PublishTemplateCommand)` | 15 |
| `PrtTemplateManager` | `forkDraftFromPublished(ForkDraftFromPublishedCommand)` | 15、16 |
| `PrtTemplateManager` | `findPublished` | 22（校验） |
| `PrintBindingResolver` | `listDefinitions` | 21 |
| `PrintBindingResolver` | `listOverridesForTenant` | 21 |
| `PrintBindingResolver` | `saveTenantBindings(SaveTenantBindingsCommand)` | 22 |
| `PrintJsonValidator` | `validate` / `validateCollect` | 12、14、15 |

### 2.4 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| ---- | ------------ | ----------- | ---- |
| 模板状态 | `PrintTemplateStatus`（目标态：`com.zxplat.domain.custom.constants.PrintTemplateStatus`） | DM-PRT-001 §2.3 `draft`/`published`/`archived` | `PrtTemplateQueryRequest.status`；目标态待生成 |
| 业务键 | `GetByBusNoRequest.busNo` | DM-PRT-001 §3.1 `bus_no` | 全面改名 busNo |
| 版式 JSON | `SaveDraftRequest.PrintJson` | DM-PRT-001 §3.1 `print_json` | 设计器载荷 |
| 绑定码 | `SaveBindingConfigRequest.bindings` key | DM-PRT-003 §3.1 `binding_code` | Map bindingCode→busNo |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| -- | -------------------- | ---------- | ---- |
| IF-BAS-PRT-11 | `queryPage` | 模板分页列表 | `POST /api/app/PrintTemplate/queryPage` |
| IF-BAS-PRT-12 | `createBlankDraft` | 新建空白模板 | `POST /api/app/PrintTemplate/createBlankDraft` |
| IF-BAS-PRT-13 | `getByBusNo` | 按 busNo 加载 | `POST /api/app/PrintTemplate/getByBusNo` |
| IF-BAS-PRT-14 | `saveDraft` | 保存草稿 | `POST /api/app/PrintTemplate/saveDraft` |
| IF-BAS-PRT-15 | `publish` | 发布 | `POST /api/app/PrintTemplate/publish` |
| IF-BAS-PRT-16 | `forkDraft` | 复制草稿 | `POST /api/app/PrintTemplate/forkDraft` |
| IF-BAS-PRT-17 | — | 预览（Web 组合） | 无独立 HTTP（客户端复用 IF-13 + OpenHIS.Win） |
| IF-BAS-PRT-21 | `loadConfig` | 加载绑定配置 | `POST /api/app/PrintBinding/loadConfig` |
| IF-BAS-PRT-22 | `saveConfig` | 保存绑定配置 | `POST /api/app/PrintBinding/saveConfig` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
| ---- | ---- | ------ |
| **Gate** | 租户 / busNo / JSON 门禁 | ValidationException |
| **Pre** | Manager 只读 | BusinessException PRT-404/422 |
| **Cmd** | Manager 写 / Resolver save | 事务见 §5 |
| **Asm** | DTO 装配 | — |

---

### 3.2 IF-BAS-PRT-11 `queryPage`（模板分页列表）

#### Request `PrtTemplateQueryRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `pageNo` | Integer | 否 | 默认 1 |
| `pageSize` | Integer | 否 | 默认 20 |
| `keyword` | String | 否 | busNo/name 模糊 |
| `status` | String | 否 | 默认不含 archived；传 `archived` 可查历史 |

#### Response 行 `PrtTemplateListItemDto`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | Long | |
| `busNo` | String | |
| `name` | String | |
| `status` | String | |
| `version` | Integer | |
| `schemaVersion` | Integer | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| ---- | ---- | ---- | ---- |
| 1 | Gate | `TenantContext#requireTenantId` | |
| 2 | Pre | `PrtTemplateManager#listMergedForTenant` | includeArchived 由 status 参数决定 |
| 3 | Asm | 内存 filter keyword/status + 分页 | **仅读** |

---

### 3.3 IF-BAS-PRT-12 `createBlankDraft`（新建空白模板）

#### Request `CreateBlankDraftRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | String | 是 | |
| `name` | String | 否 | |

#### Response `PrtTemplateDto`

同 §3.4 Response。

#### 失败码

busNo 空 DES-201；JSON DES-202。

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| ---- | ---- | ---- | ---- |
| 1 | Gate | busNo trim 非空 | DES-201 |
| 2 | Pre | `PrintJsonValidator#validate` | 默认空白 JSON |
| 3 | Pre | `findTenantDraft` | **若** 已有 draft → 跳至 5 更新语义 |
| 4 | Cmd | `saveDraft(SaveDraftCommand)` | **写库点** 新建 draft |
| 5 | Cmd | `saveDraft` | **若** 已有 draft：覆盖 JSON/name |

---

### 3.4 IF-BAS-PRT-13 `getByBusNo`（按 busNo 加载）

#### Request `GetByBusNoRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | String | 是 | |

#### Response `PrtTemplateDto`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `id` | Long | |
| `busNo` | String | |
| `name` | String | |
| `status` | String | |
| `version` | Integer | |
| `PrintJson` | Object | DOC-CONFLICT 模板域例外 |
| `samplePrintData` | Object | 可选 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| ---- | ---- | ---- | ---- |
| 1 | Gate | `TenantContext#requireTenantId` | |
| 2 | Pre | `PrtTemplateManager#resolveForDesign` | 无模板 DES-102；**仅读** |
| 3 | Asm | `PrtTemplateDto` | |

---

### 3.5 IF-BAS-PRT-14 `saveDraft`（保存草稿）

#### Request `SaveDraftRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | String | 是 | |
| `name` | String | 否 | |
| `PrintJson` | Object | 是 | |
| `samplePrintData` | Object | 否 | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| ---- | ---- | ---- | ---- |
| 1 | Gate | 租户；平台 tenant 禁止 draft（BR-PRT-007） | |
| 2 | Pre | `PrintJsonValidator#validate` | DES-202 |
| 3 | Cmd | `saveDraft(SaveDraftCommand)` | **写库点** |

---

### 3.6 IF-BAS-PRT-15 `publish`（发布）

#### Request `PublishTemplateRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | String | 是 | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| ---- | ---- | ---- | ---- |
| 1 | Gate | `TenantContext#requireTenantId` | |
| 2 | Pre | `findTenantDraft` | **若无** draft → `resolveForDesign` |
| 3 | Pre | **若** 当前非 draft → `forkDraftFromPublished` | 互斥：有 draft 则跳过 |
| 4 | Pre | `PrintJsonValidator#validate` | |
| 5 | Cmd | `publish(PublishTemplateCommand)` | **写库点**：插 published + archived + 软删 draft |

---

### 3.7 IF-BAS-PRT-16 `forkDraft`（复制草稿）

#### Request `ForkDraftRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | String | 是 | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| ---- | ---- | ---- | ---- |
| 1 | Pre | `findTenantDraft` | **若** 已有 draft → Asm 直接返回 |
| 2 | Pre | `findPublished` 或按 busNo 取 published | 无 published DES-102 |
| 3 | Cmd | `forkDraftFromPublished(ForkDraftFromPublishedCommand)` | **写库点** |

---

### 3.8 IF-BAS-PRT-17 预览（Web 组合）

**无独立 AppService 方法**；客户端编排：

1. `POST /api/app/PrintTemplate/getByBusNo`（复用 §3.4）
2. 取 `samplePrintData`（或元素 `testData`）作 printData
3. **vue-plugin-hiprint** `PrintTemplate#preview(printData)`（浏览器内）

失败：hiprint 未就绪 → DES-104（前端）。

---

### 3.9 IF-BAS-PRT-21 `loadConfig`（加载绑定配置）

#### Response `BindingConfigDto`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `bindings` | List\<BindingItemDto\> | bindingCode、name、busNo、tenantOverride |
| `candidates` | List\<TemplateCandidateDto\> | published + 租户 draft |

#### `BindingItemDto`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `bindingCode` | String | |
| `name` | String | |
| `busNo` | String | 当前有效 |
| `tenantOverride` | Boolean | |

#### `TemplateCandidateDto`

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `busNo` | String | |
| `name` | String | |
| `status` | String | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| ---- | ---- | ---- | ---- |
| 1 | Gate | `TenantContext#requireTenantId` | |
| 2 | Pre | `PrintBindingResolver#listDefinitions` | 平台 seed |
| 3 | Pre | `listOverridesForTenant` | |
| 4 | Pre | `listMergedForTenant` | 过滤候选 status |
| 5 | Asm | merge bindings + candidates | **仅读** |

---

### 3.10 IF-BAS-PRT-22 `saveConfig`（保存绑定配置）

#### Request `SaveBindingConfigRequest`

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `bindings` | Map\<String,String\> | 是 | bindingCode→busNo 全量 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| ---- | ---- | ---- | ---- |
| 1 | Gate | map 每项 busNo 非空 | BND-201 |
| 2 | Pre | 逐 busNo `findPublished` | 无 published PRT-422 |
| 3 | Cmd | `saveTenantBindings(SaveTenantBindingsCommand)` | **写库点** |
| 4 | Asm | 可选返回最新 `loadConfig` 快照 | |

---

## 4. QueryService 与领域事件

- **无** health QueryService；读经 Manager / Resolver / Repository。
- **无** 领域事件（首期）。

---

## 5. 写库顺序（核心写接口视角）

| IF | App 方法 | Cmd 调用 | 聚合 | 事务 |
| -- | -------- | -------- | ---- | ---- |
| 12/14 | createBlankDraft/saveDraft | `saveDraft` | PrtTemplate | 单事务 insert/update |
| 15 | publish | `publish` | 插 published + archived + 软删 draft | Manager `@Transactional` |
| 16 | forkDraft | `forkDraftFromPublished` | insert draft | 单事务 |
| 22 | saveConfig | `saveTenantBindings` | PrtPrintBinding upsert | 单事务 |

**失败回滚**：publish 中途失败不保留 partial published/archived（Manager 事务）。

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

fork：已有 draft 直接返回（IF-16）。

### 6.2 并发控制

模板 publish 同 busNo 并发：DB UK 兜底。

### 6.3 重试与容错

DES-101/LOG 类可重试查询。

### 6.4 数据脱敏

N/A。

### 6.5 审计

模板/绑定变更走实体审计字段。

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 优先级 |
| ---- | ------ |
| publish version 递增 + archived | P0 |
| 列表默认不含 archived | P0 |
| binding save + resolve | P0 |
| getByBusNo draft 优先 | P1 |

### 7.2～7.5

集成：Web `designer/index.vue` save/publish；hiprint 预览 IF-17。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| ---- | ---- | ---- |
| v1.2 | 2026-06-22 | Web hiprint 设计器 §2.0；IF-17 浏览器预览；取消 exe 设计器 |
| v1.1 | 2026-06-22 | 路径 A 升格；移除 Gap；HTTP/编程序列全量；archived 列表语义 |
| v1.0 | 2026-06-22 | 首版草案 |

---
