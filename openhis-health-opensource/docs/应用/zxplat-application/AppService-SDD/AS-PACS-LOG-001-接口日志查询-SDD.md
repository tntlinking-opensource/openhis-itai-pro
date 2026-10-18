# AS-PACS-LOG-001 接口日志查询 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-LOG-001-SDD · **版本**：v1.0 · **日期**：2026-09-22  
> **对应 AS**：AS-PACS-LOG-001-接口日志查询.md（v1.0）  
> **实现阶段口径**：目标态全量契约（序号 46）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application` · `pacs/integration/log` |
| **边界** | 只读列表/详情；不写 `record`、不触发 retry |
| **实现方式** | `PacsIntegrationLogAppService`；HTTP 一律 POST |

---

## 2. 代码结构

```
health-application/.../pacs/integration/log/
├── service/PacsIntegrationLogAppService.java
├── request/SearchIntegrationLogsRequest.java
├── request/GetIntegrationLogDetailRequest.java
├── response/SearchIntegrationLogsResponse.java
├── response/GetIntegrationLogDetailResponse.java
├── dto/PacsIntegrationLogRowDto.java
└── mapper/PacsIntegrationLogAppMapper.java
```

| 注入 | 用途 |
|------|------|
| `PacsIntegrationLogQueryService` | search / getById |
| `PageWindowGate` | 分页 |
| `PacsIntegrationLogAppMapper` | DTO |

### 2.2 领域读侧（新建）

`domain/pacs/integrationlog/service/PacsIntegrationLogQueryService.java`

| 方法 | IF |
|------|-----|
| `search(...)` | LOG-01a |
| `getById(id)` | LOG-01b |

读模型：可新建 `PacsIntegrationLogQueryModel`（列表与详情共用，列表 Mapper 省略 body）。

---

## 3. 按接口展开

### 3.1 方法表

| IF | 方法 | HTTP |
|----|------|------|
| IF-PACS-LOG-01a | `searchIntegrationLogs` | `POST /api/app/pacsIntegrationLog/searchIntegrationLogs` |
| IF-PACS-LOG-01b | `getIntegrationLogDetail` | `POST /api/app/pacsIntegrationLog/getIntegrationLogDetail` |

### 3.2 IF-PACS-LOG-01a

**Request**：pageNo、pageSize、directionEnum（否）、actionCode（否）、outcomeEnum（否）、entityType（否）、entityId（否）、createdFrom（否）、createdTo（否）  
**Response**：total、records（id/directionEnum/actionCode/entityType/entityId/outcomeEnum/errorMessage/createdAt）— **不含** request/response body

| 1 Gate PageWindow | 2 Pre QueryService#search | 3 Asm Mapper |

### 3.3 IF-PACS-LOG-01b

**Request**：id  
**Response**：上表字段 + requestBody + responseBody

| 1 Gate id 非空 | 2 Pre getById | 3 Asm |

---

## 4. QueryService 过滤

未软删；可选 direction/action/outcome/entityType/entityId；createdAt ≥ createdFrom、≤ createdTo；默认 `createdAt DESC`。

---

## 5. 写库顺序

无。

---

## 6. NFR

租户隔离走 `SpecificationUtils.tenantFilter()`。

---

## 7. 测试要点

| 场景 | 优先级 |
|------|:------:|
| outcome 过滤 | P0 |
| getById 不存在 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-22 | D-14；驱动序号 46 |
