# AS-LIS-REP-001 报告管理 · 应用服务详细设计（SDD）

> **编码**：AS-LIS-REP-001-SDD · **版本**：v2.0 · **日期**：2026-06-22
> **对应 AS**：AS-LIS-REP-001-报告管理.md（v2.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application` |
| **边界** | 报告查询/打印/预览；报告审核/发布/退回；外部发布(sendOut) |
| **编排权威** | AS-LIS-REP-001 §2、§4 |
| **方法权威** | DM-LIS-003 §5（DiagnosticReportDomainService）+ DM-LIS-002 §6（ObservationDomainService） |
| **实现阶段口径** | **目标态全量契约**（P0） |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | AppService **不**声明 `@Transactional`；写/读事务分别由 `AppServiceAspect`、`ReadOnlyTransactionAspect` 管理 |
| **final** | DM-LIS-003 §3.2 状态 final = 审核通过、可发送状态 |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/lis/report/
├── LisReportAppService.java
├── command/
│   ├── ApproveReportCommand.java
│   ├── RejectReportCommand.java
│   ├── SendOutReportCommand.java
│   └── AmendReportCommand.java
├── request/
│   ├── ReportSearchRequest.java
│   ├── ReportPreviewRequest.java
│   └── ReportPrintRequest.java
├── response/
│   ├── ReportSearchResponse.java
│   └── ReportPreviewResponse.java
└── assembler/
    └── ReportAssembler.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `DiagnosticReportDomainService` | 领域 | approve / reject / publish / amend |
| `DiagnosticReportQueryService` | 查询 | searchReports / getDetail / preview |
| `ApplicationEventPublisher` | 事件 | sendOut → 发布 `DiagnosticReportPublishedEvent`（HIS 侧订阅） |

### 2.2 领域方法白名单

| 聚合 | 方法 | 用于 IF |
|------|------|---------|
| DiagnosticReport | `approve(ApproveReportCommand)` | REP-01 ~ REP-03 |
| DiagnosticReport | `reject(RejectReportCommand)` | REP-04 |
| DiagnosticReport | `publish(PublishReportCommand)` | REP-05 |
| DiagnosticReport | `amend(AmendReportCommand)` | REP-06 |
| DiagnosticReportQueryService | `searchReports(ReportQuery, Pageable)` | REP-01 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|------------|------|
| `ReportStatus` | `com.zxplat.domain.share.enums.ReportStatus` | DM-LIS-003 §3.2 | partial / final / amended / cancelled |
| `ObsStatus` | `com.zxplat.domain.share.enums.ObsStatus` | DM-LIS-002 §4 | preliminary / final / amended / cancelled |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

**HTTP 路径生成规则**：`POST /api/app/{serviceName}/{methodName}`，serviceName = `LisReportAppService` → `lisReport

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|-----------|------|
| IF-REP-01 | `searchReports` | 报告列表查询 | `POST /api/app/lisReport/searchReports` |
| IF-REP-02 | `previewReport | 报告预览 | `POST /api/app/lisReport/previewReport |
| IF-REP-03 | `approveReport | 审核报告 | `POST /api/app/lisReport/approveReport |
| IF-REP-04 | `rejectReport | 退回报告 | `POST /api/app/lisReport/rejectReport |
| IF-REP-05 | `sendOutReport | 外部发布 | `POST /api/app/lisReport/sendOutReport |
| IF-REP-06 | `amendReport | 修正/召回 | `POST /api/app/lisReport/amendReport |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限 / 参数门禁 | 不再调用 |
| **Pre** | 写前只读：加载快照、BR 校验 | 抛业务异常 |
| **Cmd** | 领域写：DiagnosticReportDomainService | 事务回滚 |
| **Asm** | DTO 装配 / 事件发布 / 外部推送 | — |

---

### 3.2 IF-REP-03 `approveReport（审核通过）

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | reportId 非空 |
| 2 | **Pre** | `DiagnosticReportQueryService#getDetail(reportId)` | 加载报告快照 |
| 3 | **Pre** | BR-REP-01：statusEnum = partial | 仅 partial 可审核 |
| 4 | **Pre** | BR-REP-02：所有 Observation 须为 final | 任一个非 final → 拒绝 |
| 5 | **Cmd** | `DiagnosticReportDomainService#approve(ApproveReportCommand)` | **写库点**：partial → final + approverId |
| 6 | **Asm** | 事件发布 | `DiagnosticReportApprovedEvent` |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `REPORT_NOT_PARTIAL` | 状态不合法 |
| `OBS_NOT_FINAL` | 存在非 final 的 Observation |

---

### 3.3 IF-REP-04 `rejectReport（退回）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | BR-REP-03：statusEnum = partial | |
| 3 | **Cmd** | `DiagnosticReportDomainService#reject(RejectReportCommand)` | **写库点**：退回标记+退回原因 |
| 4 | **Asm** | 事件 | — |

---

### 3.4 IF-REP-05 `sendOutReport（外部发布）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | BR-REP-05：statusEnum = final | 仅 final 可发送 |
| 3 | **Cmd** | `DiagnosticReportDomainService#publish(PublishReportCommand)` | **写库点**：设置 publishTime |
| 4 | **Asm** | `applicationEventPublisher.publishEvent(new DiagnosticReportPublishedEvent(report))` | HIS 侧订阅处理报告推送 |

---

### 3.5 IF-REP-06 `amendReport（修正/召回）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 参数门禁 | |
| 2 | **Pre** | statusEnum = final | |
| 3 | **Cmd** | `DiagnosticReportDomainService#amend(AmendReportCommand)` | **写库点**：final → amended + amendReason |

---

### 3.6 IF-REP-01 `searchReports`（报告列表）

| 阶段 | 调用 | 说明 |
|------|------|------|
| **Pre** | `DiagnosticReportQueryService#searchReports(ReportQuery, Pageable)` | **仅读路径**；按 patientId / encounterId / date / status 过滤 |

---

## 4. QueryService 与领域事件

- **领域事件**：`DiagnosticReportApprovedEvent` → 通知临床；`DiagnosticReportAmendedEvent` → 标记旧版本。
- **查询语义**：DiagnosticReportQueryService 按患者/就诊/日期/状态分页。

---

## 5. 写库顺序

| IF | Cmd | 写入 |
|----|-----|------|
| REP-03 | `DiagnosticReportDomainService#approve` | lis_diagnostic_report（statusEnum → final, approverId） |
| REP-04 | `DiagnosticReportDomainService#reject` | lis_diagnostic_report（rejectReason） |
| REP-05 | `DiagnosticReportDomainService#publish` | lis_diagnostic_report（publishTime） |
| REP-06 | `DiagnosticReportDomainService#amend` | lis_diagnostic_report（statusEnum → amended） |

**事务边界**：单 IF 内同一事务；由 `AppServiceAspect` 管理。

---

## 6. 非功能性需求实现

无特殊。

---

## 7. 测试要点

| 场景 | 测试要点 | 优先级 |
|------|----------|:--:|
| 正向 | partial → final（审核通过） | P0 |
| 异常 | 审核 partial 报告但 observation 非 final → 拒绝 | P0 |
| 正向 | final → sendOut → HIS | P0 |
| 异常 | sendOut 非 final 报告 → 拒绝 | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.2 | 2026-06-22 | 去 Extension：`LisReportSendOutExtension` → `DiagnosticReportPublishedEvent` 领域事件（zxplat 内部，不走 Extension 层） |
| v2.0 | 2026-06-22 | 重新生成：修正事务口径（AppService 不声明 @Transactional）；修正包路径；补充 §2.3 枚举 |
| v1.0 | 2026-06-22 | 首版 |
