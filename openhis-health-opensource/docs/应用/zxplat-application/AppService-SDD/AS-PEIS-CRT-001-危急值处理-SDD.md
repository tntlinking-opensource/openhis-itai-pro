# AS-PEIS-CRT-001 危急值处理 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-CRT-001-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-CRT-001-危急值处理.md（v1.1）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis/criticalvalue` |
| **边界** | 诊台危急值列表与处理；闭环留痕；异常统计（P2） |
| **编排权威** | AS-PEIS-CRT-001、AS-PEIS-CFG-002（CFG-12 规则） |
| **方法权威** | health DetectedIssue、DM-PEIS-006（规则引用） |
| **实现阶段口径** | **目标态全量契约**（P1：CRT-02/03；P2：CRT-04） |

### 术语口径

| 项 | 约定 |
|----|------|
| **实例** | health **DetectedIssue**（非 CUS 表） |
| **规则** | CFG-12 → DM-PEIS-006 / PeisAutoExpress |
| **联动** | 小项 Observation 异常可自动建 Issue（BR-CRT-01，可选） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/peis/criticalvalue/
├── PeisCriticalValueDeskAppService.java
├── request/
│   ├── SearchCriticalValueRequest.java
│   ├── HandleCriticalValueRequest.java
│   └── SearchOutlierStatisticsRequest.java
├── response/
│   ├── SearchCriticalValueResponse.java
│   └── HandleCriticalValueResponse.java
├── dto/
│   └── CriticalValueRowDto.java
└── mapper/
    ├── PeisCriticalValueMapper.java
    └── PeisCriticalValueMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `DetectedIssueDomainService` | 领域 | 创建/处理 Issue |
| `DetectedIssueQueryService` | 查询 | CRT-02 列表 |
| `ObservationQueryService` | 查询 | 关联小项 |
| `PeisAutoExpressQueryService` | 查询 | CFG-12 规则匹配 |

### 2.2 领域方法白名单

| 聚合/服务 | 方法 | 用于 IF |
|-----------|------|---------|
| DetectedIssueDomainService | `createFromObservation(CreateIssueFromObsCommand)` | CRT-02（自动，可选） |
| DetectedIssueDomainService | `acknowledge(AcknowledgeIssueCommand)` | CRT-02 |
| DetectedIssueDomainService | `closeWithDeal(CloseIssueWithDealCommand)` | CRT-03 |
| DetectedIssueQueryService | `searchPeisCriticalValues(CriticalValueQuery, Pageable)` | CRT-02 |
| DetectedIssueQueryService | `searchOutlierStatistics(OutlierStatQuery)` | CRT-04 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| DetectedIssue.severity | `CriticalValueRowDto.severityCode` | health DetectedIssue §3 | high/critical |
| DetectedIssue.status | `CriticalValueRowDto.statusCode` | health DetectedIssue §5 | registered/acknowledged/closed |
| 规则表达式 | `PeisAutoExpress.expression` | DM-PEIS-006 §2 | CFG-12 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

**HTTP**：`PeisCriticalValueDeskAppService` → `peisCriticalValueDesk`

| IF | 应用服务方法 | 中文方法名 | HTTP |
|----|-------------|-----------|------|
| IF-PEIS-CRT-02 | `searchCriticalValues` | 危急值列表 | `POST /api/app/peisCriticalValueDesk/searchCriticalValues` |
| IF-PEIS-CRT-02 | `handleCriticalValue` | 处理危急值 | `POST /api/app/peisCriticalValueDesk/handleCriticalValue` |
| IF-PEIS-CRT-03 | `closeCriticalValueDeal` | 闭环留痕 | `POST /api/app/peisCriticalValueDesk/closeCriticalValueDeal` |
| IF-PEIS-CRT-04 | `searchOutlierStatistics` | 异常统计 | `POST /api/app/peisCriticalValueDesk/searchOutlierStatistics` |

---

### 3.2 IF-PEIS-CRT-02 `searchCriticalValues`（危急值列表）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `deptId` | `Long` | 否 |
| `statusCode` | `String` | 否 |
| `pageNo` | `Integer` | 是 |
| `pageSize` | `Integer` | 是 |

#### CriticalValueRowDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `issueId` | `Long` | |
| `encounterId` | `Long` | |
| `observationId` | `Long` | 可空 |
| `severityCode` | `String` | |
| `statusCode` | `String` | |
| `detectedTime` | `Instant` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `DetectedIssueQueryService#searchPeisCriticalValues` | **仅读路径** |
| 2 | Asm | Mapper | |

---

### 3.3 IF-PEIS-CRT-02 `handleCriticalValue`（处理危急值）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `issueId` | `Long` | 是 |
| `actionCode` | `String` | 是 | acknowledge |
| `operatorId` | `Long` | 是 |
| `comment` | `String` | 否 |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `ISSUE_ALREADY_CLOSED` | 已闭环 |
| `ISSUE_NOT_FOUND` | |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `DetectedIssueQueryService#getById(issueId)` | PE 上下文 |
| 2 | Cmd | `DetectedIssueDomainService#acknowledge(AcknowledgeIssueCommand)` | **写库点** |

---

### 3.4 IF-PEIS-CRT-03 `closeCriticalValueDeal`（闭环留痕）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `issueId` | `Long` | 是 |
| `dealResult` | `String` | 是 |
| `operatorId` | `Long` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | status=acknowledged | BR-CRT-02 |
| 2 | Cmd | `DetectedIssueDomainService#closeWithDeal(CloseIssueWithDealCommand)` | **写库点**：留痕 |

---

### 3.5 IF-PEIS-CRT-04 `searchOutlierStatistics`（异常统计，P2）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `personTypeCode` | `String` | 否 | STU/STF |
| `dateFrom` | `LocalDate` | 是 |
| `dateTo` | `LocalDate` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `DetectedIssueQueryService#searchOutlierStatistics` | **仅读路径** |
| 2 | Asm | 聚合 DTO | |

---

## 4. QueryService 与领域事件

- Observation 异常事件（可选）→ 自动 createFromObservation → 列表 eventual 可见。

---

## 5. 写库顺序

| IF | Cmd | 表 |
|----|-----|-----|
| handleCriticalValue | acknowledge | cln_detected_issue |
| closeCriticalValueDeal | closeWithDeal | + deal 扩展 |

---

## 6. 非功能性需求实现

### 6.1 幂等性

- acknowledge 重复 → ISSUE_ALREADY_CLOSED。

### 6.2 并发控制

- Issue 乐观锁 version。

### 6.3 重试与容错

- 自动建 Issue 失败不阻塞 DES 保存（异步 P2）。

### 6.4 数据脱敏

- 列表脱敏患者标识。

### 6.5 审计日志

- closeWithDeal 全量留痕 dealResult，关联 §3.4 序号 2。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 层级 | 自动化 | 优先级 |
|------|------|------|--------|:--:|
| 列表 | PE 过滤 | 功能 | API | P1 |
| 处理 | acknowledge | 功能 | API | P1 |
| 闭环 | closeWithDeal | 功能 | API | P1 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 并发处理 | 单 Issue 单闭环 | P1 |

### 7.3 查询结果一致性

- 与 DES 小项异常标记一致。

### 7.4 集成测试

- LIS 结果投影 → 规则命中 → Issue 列表。

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
|------|------|:--:|
| DetectedIssue PE 过滤 | QueryService 扩展 | P1 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 对齐 AS v1.1；CRT-01→CFG-12 |
| v1.0 | 2026-06-27 | 首版 |
