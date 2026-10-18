# AS-LIS-AUD-001 结果审核 · 应用服务详细设计（SDD）

> **编码**：AS-LIS-AUD-001-SDD · **版本**：v2.0 · **日期**：2026-06-22
> **对应 AS**：AS-LIS-AUD-001-结果审核.md（v2.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application` |
| **边界** | 危急值上报/处理/确认；结果复核/修正/确认（含自动确认 P2）；报告合并/合成 |
| **编排权威** | AS-LIS-AUD-001 §2、§4 |
| **方法权威** | DM-LIS-005 §4（DetectedIssueDomainService） + DM-LIS-002 §6（ObservationDomainService）+ DM-LIS-003 §5（DiagnosticReportDomainService） |
| **实现阶段口径** | **目标态全量契约**（P0 危急值/复核；P2 自动确认） |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | AppService **不**声明 `@Transactional`；写/读事务分别由 `AppServiceAspect`、`ReadOnlyTransactionAspect` 管理 |
| **危急值标准** | 按检测项目代码+值区间定义（AS §1.4），触及即生成 DetectedIssue |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/lis/audit/
├── LisAuditAppService.java
├── command/
│   ├── ReviewObservationCommand.java
│   ├── FinalizeObservationCommand.java
│   ├── CreateDetectedIssueCommand.java
│   ├── AcknowledgeDetectedIssueCommand.java
│   └── MergeReportCommand.java
├── request/
│   ├── AuditSearchRequest.java
│   └── DetectedIssueSearchRequest.java
├── response/
│   ├── AuditSearchResponse.java
│   └── DetectedIssueSearchResponse.java
├── assembler/
│   └── AuditAssembler.java
└── support/
    └── PanicValueChecker.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `ObservationDomainService` | 领域 | review / finalize |
| `DiagnosticReportDomainService` | 领域 | generateReport / mergeReport |
| `DetectedIssueDomainService` | 领域 | create / acknowledge（危急值） |
| `ObservationQueryService` | 查询 | searchForAudit |
| `DiagnosticReportQueryService` | 查询 | searchReports |
| `PanicValueChecker` | support | 危险值检测（BR-AUD-01~02） |

### 2.2 领域方法白名单

| 聚合 | 方法 | 用于 IF |
|------|------|---------|
| Observation | `review(ReviewObservationCommand)` | AUD-01 |
| Observation | `finalize(FinalizeObservationCommand)` | AUD-02 |
| DiagnosticReport | `generateReport(GenerateReportCommand)` | AUD-03 |
| DiagnosticReport | `mergeReport(MergeReportCommand)` | AUD-04 |
| DetectedIssue | `createDetectedIssue(CreateDetectedIssueCommand)` | AUD-CV-01 |
| DetectedIssue | `acknowledge(AcknowledgeDetectedIssueCommand)` | AUD-CV-02 |
| ObservationQueryService | `searchForAudit(AuditQuery, Pageable)` | AUD-01 |
| DetectedIssueQueryService | `searchDetectedIssues(DetectedIssueQuery, Pageable)` | AUD-CV-01 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|------------|------|
| `ObsStatus` | `com.zxplat.domain.share.enums.ObsStatus` | DM-LIS-002 §4 | preliminary / final / amended / cancelled |
| `ReportStatus` | `com.zxplat.domain.share.enums.ReportStatus` | DM-LIS-003 §3.2 | partial / final / amended / cancelled |
| `DetectedIssueStatus` | `com.zxplat.domain.share.enums.DetectedIssueStatus` | DM-LIS-005 §3.1 | unacknowledged / acknowledged |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，serviceName = `LisAuditAppService` → `lisAudit`

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-AUD-01 | `reviewObservation` | 结果复核 | `POST /api/app/lisAudit/reviewObservation` |
| IF-AUD-02 | `finalizeObservation` | 结果确认 | `POST /api/app/lisAudit/finalizeObservation` |
| IF-AUD-03 | `generateReport | 生成报告 | `POST /api/app/lisAudit/generateReport |
| IF-AUD-04 | `mergeReport | 合并/合成报告 | `POST /api/app/lisAudit/mergeReport |
| IF-AUD-CV-01 | `reportPanicValue` | 危急值上报 | `POST /api/app/lisAudit/reportPanicValue` |
| IF-AUD-CV-02 | `acknowledgePanicValue` | 危急值确认 | `POST /api/app/lisAudit/acknowledgePanicValue` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 参数门禁 | 不再调用 |
| **Pre** | 写前只读：加载快照、BR 校验 | 抛业务异常 |
| **Cmd** | 领域写 | 事务回滚 |
| **Asm** | 事件发布 / 记录 | — |

---

### 3.2 IF-AUD-CV-01 `reportPanicValue`（危急值上报）

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | observationId 非空 |
| 2 | **Pre** | `ObservationQueryService#getDetail(observationId)` | BR-AUD-01：检查 value 在危险区间 |
| 3 | **Pre** | `PanicValueChecker#check(observation)` | BR-AUD-02：危险值确认 |
| 4 | **Cmd** | `DetectedIssueDomainService#createDetectedIssue(CreateDetectedIssueCommand)` | **写库点** |
| 5 | **Asm** | 通知 | 站内信/短信通知 — BR-AUD-03 |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `NOT_PANIC_VALUE` | 测量值不满足危急定义 |

---

### 3.3 IF-AUD-CV-02 `acknowledgePanicValue`（危急值确认）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | BR-AUD-04：issueStatus = unacknowledged | |
| 3 | **Cmd** | `DetectedIssueDomainService#acknowledge(AcknowledgeDetectedIssueCommand)` | **写库点**：unacknowledged → acknowledged |

---

### 3.4 IF-AUD-01 `reviewObservation`（结果复核）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | `ObservationQueryService#getDetail(observationId)` | obsStatus = preliminary |
| 3 | **Cmd** | `ObservationDomainService#review(ReviewObservationCommand)` | **写库点**：reviewerId + resultComment |
| 4 | **Asm** | 事件 | — |

---

### 3.5 IF-AUD-02 `finalizeObservation`（结果确认）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | BR-AUD-07：如工单全部 final → DR 生成 | Preliminary → Final；check all linkedObservations |
| 3 | **Cmd** | `ObservationDomainService#finalize(FinalizeObservationCommand)` | **写库点**：preliminary → final |

---

### 3.6 IF-AUD-03 `generateReport（生成报告）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Pre** | BR-AUD-08~09 | ALL observations final；生成 DR 前检查 |
| 2 | **Cmd** | `DiagnosticReportDomainService#generateReport(GenerateReportCommand)` | **写库点** |

---

### 3.7 IF-AUD-04 `mergeReport（合并报告）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Pre** | BR-AUD-10：校验 sourceReports 非空 | |
| 2 | **Cmd** | `DiagnosticReportDomainService#mergeReport(MergeReportCommand)` | **写库点** |

---

## 4. QueryService 与领域事件

- **领域事件**：`DetectedIssueCreatedEvent` → 通知临床；`ObservationFinalizedEvent` → 触发报告生成。

---

## 5. 写库顺序

| IF | Cmd | 写入 |
|----|-----|------|
| AUD-CV-01 | `DetectedIssueDomainService#createDetectedIssue` | lis_detected_issue |
| AUD-CV-02 | `DetectedIssueDomainService#acknowledge` | lis_detected_issue（statusEnum） |
| AUD-01 | `ObservationDomainService#review` | lis_observation（reviewerId, resultComment） |
| AUD-02 | `ObservationDomainService#finalize` | lis_observation（statusEnum → final） |
| AUD-03 | `DiagnosticReportDomainService#generateReport | lis_diagnostic_report |
| AUD-04 | `DiagnosticReportDomainService#mergeReport | lis_diagnostic_report（merge） |

**事务边界**：单 IF 内同一事务；由 `AppServiceAspect` 管理。

---

## 6. 非功能性需求实现

无特殊。

---

## 7. 测试要点

| 场景 | 测试要点 | 优先级 |
|------|----------|:--:|
| 正向 | 测量值触及危险区间 → DetectedIssue=unacknowledged | P0 |
| 正向 | 临床 confirm → acknowledged | P0 |
| 正向 | preliminary → final | P0 |
| 异常 | final 前有 observation 非 final → 拒绝 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.0 | 2026-06-22 | 重新生成：修正事务口径（AppService 不声明 @Transactional）；修正包路径；补充 §2.3 枚举 |
| v1.0 | 2026-06-22 | 首版 |
