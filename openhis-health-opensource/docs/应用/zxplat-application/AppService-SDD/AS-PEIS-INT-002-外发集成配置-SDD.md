# AS-PEIS-INT-002 外发集成配置 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-INT-002-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-INT-002-外发集成配置.md（v1.1）  
> **术语**：INT-06=路由策略；**外发 LIS（金域）**= [LisKingMed](../../../模块/zxplat-module-lis-kingmed/) — 见 [`PEIS-LIS-PACS文档口径`](../../../../.cursor/skills/PEIS-LIS-PACS文档口径/SKILL.md)

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis/integration` |
| **边界** | 外发路由策略；外购 LIS/PACS 连接配置；同步日志/重试 |
| **编排权威** | AS-PEIS-INT-002、Peis 01 §7 |
| **方法权威** | adapter 配置（**不进** health Flyway） |
| **实现阶段口径** | **目标态全量契约**（P1：INT-06；P2：INT-07/08） |

### 术语口径

| 项 | 约定 |
|----|------|
| **禁止** | PEIS↔LIS 项目对照表（BR-INT2-02） |
| **一体化院点** | 可无 map，仅 adapter 连接（BR-INT2-01） |
| **配置存储** | adapter 侧或 peis_config 扩展 key，非 CUS 对照表 |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/peis/integration/
├── PeisIntegrationConfigAppService.java
├── request/
│   ├── SaveDispatchRouteRequest.java
│   ├── SaveAdapterConnectionRequest.java
│   ├── SearchSyncLogRequest.java
│   └── RetrySyncRequest.java
├── response/
│   ├── SaveDispatchRouteResponse.java
│   └── SearchSyncLogResponse.java
├── dto/
│   └── SyncLogRowDto.java
└── mapper/
    ├── PeisIntegrationConfigMapper.java
    └── PeisIntegrationConfigMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `PeisIntegrationConfig旧命名（已废止） | Extension | 读写 adapter 配置 |
| `PeisSyncLog`QueryReader`（规划） | Extension | INT-08 日志 |
| `PeisConfigDomainService` | 领域 | 路由策略 key（可选 DM-PEIS-004） |

### 2.2 领域方法白名单

| 服务 | 方法 | 用于 IF |
|------|------|---------|
| PeisIntegrationConfigPort | `saveDispatchRoute(SaveDispatchRouteCommand)` | INT-06 |
| PeisIntegrationConfigPort | `getDispatchRoute(orgId)` | INT-06 |
| PeisIntegrationConfigPort | `saveAdapterConnection(SaveAdapterConnectionCommand)` | INT-07 |
| PeisSyncLogQueryPort | `searchSyncLogs(SyncLogQuery, Pageable)` | INT-08 |
| PeisIntegrationConfigPort | `retrySync(RetrySyncCommand)` | INT-08 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| 路由 target | `SaveDispatchRouteRequest.targetSystemCode` | AS-PEIS-INT-002 BR-INT2-01 | LIS/PACS/adapter |
| AD type PROOF/TEST | `ActivityDefinition.type_enum` | DM-WKF-001 | 路由依据 |
| sync_status | `SyncLogRowDto.statusCode` | adapter 契约 | success/failed/pending |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

**HTTP**：`PeisIntegrationConfigAppService` → `peisIntegrationConfig`

| IF | 应用服务方法 | 中文方法名 | HTTP |
|----|-------------|-----------|------|
| IF-PEIS-INT-06 | `saveDispatchRoute` | 保存外发路由 | `POST /api/app/peisIntegrationConfig/saveDispatchRoute` |
| IF-PEIS-INT-06 | `getDispatchRoute` | 查询外发路由 | `POST /api/app/peisIntegrationConfig/getDispatchRoute` |
| IF-PEIS-INT-07 | `saveAdapterConnection` | 连接配置 | `POST /api/app/peisIntegrationConfig/saveAdapterConnection` |
| IF-PEIS-INT-08 | `searchSyncLogs` | 同步日志 | `POST /api/app/peisIntegrationConfig/searchSyncLogs` |
| IF-PEIS-INT-08 | `retrySync` | 重试同步 | `POST /api/app/peisIntegrationConfig/retrySync` |

---

### 3.2 IF-PEIS-INT-06 `saveDispatchRoute`（保存外发路由）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `orgId` | `Long` | 是 | |
| `targetSystemCode` | `String` | 是 | LIS/PACS |
| `enabled` | `Boolean` | 是 | |
| `endpointUrl` | `String` | 否 | 外购时必填 |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `ROUTE_INVALID` | 禁止项目对照字段 |
| `ENDPOINT_REQUIRED` | 外购无 URL |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 校验 Request **无** itemMap 类字段 | BR-INT2-02 |
| 2 | Cmd | `PeisIntegrationConfigPort#saveDispatchRoute(SaveDispatchRouteCommand)` | **写库点**：adapter/配置存储 |
| 3 | Asm | AuditLog | |

---

### 3.3 IF-PEIS-INT-06 `getDispatchRoute`（查询外发路由）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisIntegrationConfigPort#getDispatchRoute(orgId)` | **仅读路径** |
| 2 | Asm | Mapper | |

---

### 3.4 IF-PEIS-INT-07 `saveAdapterConnection`（连接配置）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `adapterTypeCode` | `String` | 是 |
| `connectionJson` | `String` | 是 | 加密存储 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | connectionJson 敏感字段脱敏日志 | |
| 2 | Cmd | `PeisIntegrationConfigPort#saveAdapterConnection` | **写库点** |

---

### 3.5 IF-PEIS-INT-08 `searchSyncLogs`（同步日志）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `statusCode` | `String` | 否 |
| `dateFrom` | `Instant` | 否 |
| `pageNo` | `Integer` | 是 |
| `pageSize` | `Integer` | 是 |

#### SyncLogRowDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `logId` | `Long` | |
| `serviceRequestId` | `Long` | |
| `targetSystemCode` | `String` | |
| `statusCode` | `String` | |
| `lastError` | `String` | |
| `syncTime` | `Instant` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisSyncLogQueryPort#searchSyncLogs` | **仅读路径** |
| 2 | Asm | Mapper | |

---

### 3.6 IF-PEIS-INT-08 `retrySync`（重试同步）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `logId` | `Long` | 是 |
| `operatorId` | `Long` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisSyncLogQueryPort#getById(logId)` | status=failed |
| 2 | Cmd | `PeisIntegrationConfigPort#retrySync(RetrySyncCommand)` | 触发 INT-01 外发 |
| 3 | Asm | 返回新 logId | |

---

## 4. QueryService 与领域事件

- retrySync 成功后 INT-01 dispatch 事件；日志 Client eventual 更新。

---

## 5. 写库顺序

| IF | 写入位置 |
|----|----------|
| saveDispatchRoute | adapter DB / peis_config |
| saveAdapterConnection | adapter 加密表 |
| retrySync | adapter 日志 + 异步外发 |

**事务边界**：配置写单事务；retrySync 异步，不阻塞 HTTP。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- saveDispatchRoute 按 orgId+target 覆盖。

### 6.2 并发控制

- 配置乐观锁 version。

### 6.3 重试与容错

- retrySync 指数退避（adapter 侧）。

### 6.4 数据脱敏

- connectionJson 响应不回显密钥。

### 6.5 审计日志

- saveAdapterConnection 记变更不含密钥，关联 §3.4 序号 2。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 层级 | 自动化 | 优先级 |
|------|------|------|--------|:--:|
| 路由保存 | 无 itemMap | 功能 | API | P1 |
| 连接配置 | 加密 | 功能 | 集成 | P2 |
| 日志重试 | failed→success | 功能 | 集成 | P2 |

### 7.2～7.4

- 与 INT-01 dispatch 联调；一体化院点空 map。

### 7.5 测试缺口跟踪

| 缺口 | adapter Client Mock | P1 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 对齐 AS v1.1；金域 vs 路由 vs INT-01 |
| v1.0 | 2026-06-27 | 首版；禁止项目对照表 |
