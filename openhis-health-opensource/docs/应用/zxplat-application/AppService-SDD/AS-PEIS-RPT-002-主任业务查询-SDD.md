# AS-PEIS-RPT-002 主任业务查询 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-RPT-002-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-RPT-002-主任业务查询.md（v1.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis/director` |
| **边界** | 主任非财务查询；业务大屏指标（P3 可选） |
| **编排权威** | AS-PEIS-RPT-002 |
| **方法权威** | DM-PEIS-008、PeisDirectorQueryService（目标态） |
| **实现阶段口径** | **目标态全量契约**（P3） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/peis/director/
├── PeisDirectorDashboardAppService.java
├── request/
│   ├── SearchCheckInfoRequest.java
│   └── GetBigDataMetricsRequest.java
├── response/
│   ├── SearchCheckInfoResponse.java
│   └── GetBigDataMetricsResponse.java
└── mapper/
    ├── PeisDirectorMapper.java
    └── PeisDirectorMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `PeisDirectorQueryService` | 查询 | RPT-05/06 |
| `PeisEncounterProgressQuery` | 查询 | 进度摘要 |

### 2.2 领域方法白名单

| 服务 | 方法 | 用于 IF |
|------|------|---------|
| PeisDirectorQueryService | `searchCheckInfo(DirectorCheckInfoQuery, Pageable)` | RPT-05 |
| PeisDirectorQueryService | `getBigDataMetrics(BigDataMetricsQuery)` | RPT-06 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| `EncounterClass.PE` | `SearchCheckInfoRequest.encounterClassCode` | DM-ADM-002 §4.1 | 固定 PE |
| progressLabel | `CheckInfoRowDto.progressLabel` | DM-PEIS-008 | |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

**HTTP**：`PeisDirectorDashboardAppService` → `peisDirectorDashboard`

| IF | 应用服务方法 | 中文方法名 | HTTP |
|----|-------------|-----------|------|
| IF-PEIS-RPT-05 | `searchCheckInfo` | 体检情况查询 | `POST /api/app/peisDirectorDashboard/searchCheckInfo` |
| IF-PEIS-RPT-06 | `getBigDataMetrics` | 业务大屏指标 | `POST /api/app/peisDirectorDashboard/getBigDataMetrics` |

---

### 3.2 IF-PEIS-RPT-05 `searchCheckInfo`（体检情况查询）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `dateFrom` | `LocalDate` | 否 |
| `dateTo` | `LocalDate` | 否 |
| `keyword` | `String` | 否 | 姓名/工号 |
| `pageNo` | `Integer` | 是 |
| `pageSize` | `Integer` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisDirectorQueryService#searchCheckInfo(DirectorCheckInfoQuery, Pageable)` | **仅读路径** |
| 2 | Asm | Mapper | |

---

### 3.3 IF-PEIS-RPT-06 `getBigDataMetrics`（业务大屏）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `statDate` | `LocalDate` | 是 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `todayCheckInCount` | `Integer` | |
| `pendingMasterReviewCount` | `Integer` | |
| `positiveRatePercent` | `BigDecimal` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisDirectorQueryService#getBigDataMetrics(BigDataMetricsQuery)` | **仅读路径** |
| 2 | Asm | Mapper | |

---

## 4. QueryService 与领域事件

- 大屏指标只读；可缓存 5min（P3 NFR）。

---

## 5. 写库顺序

无写库。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- 只读。

### 6.2 并发控制

- 无。

### 6.3 重试与容错

- getBigDataMetrics 可降级返回部分指标。

### 6.4 数据脱敏

- searchCheckInfo 主任角色 Gate。

### 6.5 审计日志

- 大屏访问记 AuditLog（P3）。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 层级 | 自动化 | 优先级 |
|------|------|------|--------|:--:|
| 情况查询 | 分页 | 功能 | API | P3 |
| 大屏 | 指标非空 | 功能 | API | P3 |

### 7.2～7.4

- 与 RPT-001 口径一致；集成造数后指标变化。

### 7.5 测试缺口跟踪

| 缺口 | PeisDirectorQueryService 目标态 | P3 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版 P3 |
