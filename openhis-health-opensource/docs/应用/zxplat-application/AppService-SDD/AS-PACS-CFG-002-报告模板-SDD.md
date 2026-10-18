# AS-PACS-CFG-002 报告模板 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-CFG-002-SDD · **版本**：v1.0 · **日期**：2026-09-22  
> **对应 AS**：AS-PACS-CFG-002-报告模板.md（v1.0）  
> **实现阶段口径**：目标态全量契约（序号 43）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application` · `pacs/config/template` |
| **边界** | 模板 CRUD；套用在 REP AppService |
| **方法权威** | DM-PACS-004 §5 |
| **实现方式** | `PacsReportTemplateConfigAppService`；HTTP 一律 POST |

---

## 2. 代码结构

```
health-application/.../pacs/config/template/
├── service/PacsReportTemplateConfigAppService.java
├── request/SearchReportTemplatesRequest.java
├── request/SaveReportTemplateRequest.java
├── request/ReportTemplateIdRequest.java
├── response/SearchReportTemplatesResponse.java
├── response/SaveReportTemplateResponse.java
├── dto/PacsReportTemplateRowDto.java
└── mapper/PacsReportTemplateConfigMapper.java
```

| 注入 | 用途 |
|------|------|
| `PacsReportTemplateDomainService` | 写/查 |
| `CurrentPractitionerResolver` | 操作人 |
| `PageWindowGate` | 分页 |
| `PacsReportTemplateConfigMapper` | DTO |

### 2.2 领域方法白名单

| 方法 | IF |
|------|-----|
| `search` | CFG-02a |
| `save` | CFG-02b |
| `disable` | CFG-02c |
| `delete` | CFG-02d |
| `getById` | REP-04 Pre |

### 2.3 枚举与值集

| 类别 | 代码锚点 | DM |
|------|----------|-----|
| enabledFlag | `Boolean` | DM-PACS-004 §3 |
| modalityCode | `String` | 与 capability 同码 |

---

## 3. 按接口展开

### 3.1 方法表

| IF | 方法 | HTTP |
|----|------|------|
| IF-PACS-CFG-02a | `searchReportTemplates` | `POST /api/app/pacsReportTemplateConfig/searchReportTemplates` |
| IF-PACS-CFG-02b | `saveReportTemplate` | `POST /api/app/pacsReportTemplateConfig/saveReportTemplate` |
| IF-PACS-CFG-02c | `disableReportTemplate` | `POST /api/app/pacsReportTemplateConfig/disableReportTemplate` |
| IF-PACS-CFG-02d | `deleteReportTemplate` | `POST /api/app/pacsReportTemplateConfig/deleteReportTemplate` |

### 3.2 IF-PACS-CFG-02a `searchReportTemplates`

**Request**：pageNo、pageSize、modalityCode（否）、enabledOnly（否）  
**Response**：total、records（id/name/modalityCode/enabledFlag/sortOrder/ownerPractitionerId）

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Gate | PageWindowGate |
| 2 | Pre | DomainService#search |
| 3 | Asm | Mapper |

### 3.3 IF-PACS-CFG-02b `saveReportTemplate`

**Request**：id（否）、name、modalityCode、findings、conclusion、enabledFlag、sortOrder、ownerPractitionerId  
**Response**：id、name、enabledFlag

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Gate | requireOperatorId |
| 2 | Cmd | save |

### 3.4 IF-PACS-CFG-02c / 02d

**Request**：id  
**Cmd**：disable / delete

---

## 4. QueryService 与领域事件

无事件；读侧即时。

---

## 5. 写库顺序

| IF | Cmd | 表 |
|----|-----|-----|
| 02b | save | pacs_report_template |
| 02c | disable | 同上 |
| 02d | delete | 软删 |

---

## 6. NFR

幂等：同 id 更新覆盖；无额外锁。

---

## 7. 测试要点

| 场景 | 优先级 |
|------|:------:|
| 保存后列表可见 | P0 |
| 停用后 getById 仍可读、套用拒绝 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-22 | D-11；驱动序号 43 |
