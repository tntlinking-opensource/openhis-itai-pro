# AS-PEIS-REC-001 复查管理 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-REC-001-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-REC-001-复查管理.md（v1.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis/recheck` |
| **边界** | 复查申请/列表；复查项目确认；复查完成与报告关联 |
| **编排权威** | AS-PEIS-REC-001、Peis 04 §4.5 |
| **方法权威** | WKF Task、DM-PEIS-001、DM-PEIS-007 |
| **实现阶段口径** | **目标态全量契约**（P1） |

### 术语口径

| 项 | 约定 |
|----|------|
| **复查建模** | Task(for=Encounter) + 复查 SR 子集（方案 A） |
| **进度** | 未关闭 Task → 展示「复查中」（BR-REC-01） |
| **计费** | 复查 SR 独立走 health 收费 Client（BR-REC-02） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/peis/recheck/
├── PeisRecheckManageAppService.java
├── request/
│   ├── SearchRecheckRequest.java
│   ├── CreateRecheckRequest.java
│   ├── ConfirmRecheckItemsRequest.java
│   └── CompleteRecheckRequest.java
├── response/
│   ├── SearchRecheckResponse.java
│   └── CreateRecheckResponse.java
├── dto/
│   └── RecheckRowDto.java
└── mapper/
    ├── PeisRecheckMapper.java
    └── PeisRecheckMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `TaskDomainService` | 领域 | 复查 Task |
| `TaskQueryService` | 查询 | REC-01 列表 |
| `PeisRegistrationDomainService` | 领域 | 复查 SR 展开 |
| `PeisEncounterProgressQuery` | 查询 | 进度含复查态 |
| `DiagnosticReportQueryService` | 查询 | REC-03 关联 DR |

### 2.2 领域方法白名单

| 聚合/服务 | 方法 | 用于 IF |
|-----------|------|---------|
| TaskDomainService | `createRecheckTask(CreateRecheckTaskCommand)` | REC-01 |
| TaskDomainService | `completeTask(CompleteTaskCommand)` | REC-03 |
| TaskQueryService | `searchRecheckTasks(RecheckTaskQuery, Pageable)` | REC-01 |
| PeisRegistrationDomainService | `addRecheckServiceRequests(AddRecheckSrCommand)` | REC-02 |
| DiagnosticReportQueryService | `linkRecheckReport(LinkRecheckReportCommand)` | REC-03 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| Task.for | `CreateRecheckRequest.encounterId` | Peis 04 §4.5 | Encounter 级 Task |
| `PeisExecStatus.recheck` | `com.zxplat.plugin.peis.domain.enums.PeisExecStatus.RECHECK` | DM-PEIS-001 §2 | 复查 SR；目标态待生成 |
| Task.status | `TaskStatus` | DM-WKF-Task §5 | requested/in-progress/completed |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

**HTTP**：`PeisRecheckManageAppService` → `peisRecheckManage`

| IF | 应用服务方法 | 中文方法名 | HTTP |
|----|-------------|-----------|------|
| IF-PEIS-REC-01 | `searchRecheck` | 复查列表 | `POST /api/app/peisRecheckManage/searchRecheck` |
| IF-PEIS-REC-01 | `createRecheck` | 复查申请 | `POST /api/app/peisRecheckManage/createRecheck` |
| IF-PEIS-REC-02 | `confirmRecheckItems` | 复查项目确认 | `POST /api/app/peisRecheckManage/confirmRecheckItems` |
| IF-PEIS-REC-03 | `completeRecheck` | 复查完成 | `POST /api/app/peisRecheckManage/completeRecheck` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 参数 / 权限 | 不再调用 |
| **Pre** | Encounter 状态、原 DR | 抛业务异常 |
| **Cmd** | Task / SR 写 | 事务回滚 |
| **Asm** | Mapper | — |

---

### 3.2 IF-PEIS-REC-01 `createRecheck`（复查申请）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `encounterId` | `Long` | 是 | 原体检 |
| `reason` | `String` | 是 | |
| `operatorId` | `Long` | 是 | |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | `Long` | |
| `encounterId` | `Long` | |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `ENCOUNTER_NOT_FINISHED` | 原检未完成 |
| `RECHECK_ALREADY_OPEN` | 已有未关闭复查 Task |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `EncounterQueryService#mustGetEncounter(encounterId)` | class=PE, finished |
| 2 | Pre | `TaskQueryService#hasOpenRecheckTask(encounterId)` | 无 open Task |
| 3 | Cmd | `TaskDomainService#createRecheckTask(CreateRecheckTaskCommand)` | **写库点**：wkf_task |
| 4 | Asm | Mapper | |

---

### 3.3 IF-PEIS-REC-01 `searchRecheck`（复查列表）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `orgId` | `Long` | 是 |
| `statusCode` | `String` | 否 |
| `pageNo` | `Integer` | 是 |
| `pageSize` | `Integer` | 是 |

#### RecheckRowDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `taskId` | `Long` | |
| `encounterId` | `Long` | |
| `busNo` | `String` | |
| `statusCode` | `String` | |
| `progressLabel` | `String` | 含「复查中」 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `TaskQueryService#searchRecheckTasks` | **仅读路径** |
| 2 | Pre | `PeisEncounterProgressQuery#getProgressSummary` | 逐行或 join |
| 3 | Asm | Mapper | |

---

### 3.4 IF-PEIS-REC-02 `confirmRecheckItems`（复查项目确认）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `taskId` | `Long` | 是 |
| `activityIds` | `List<Long>` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `TaskQueryService#getById(taskId)` | open |
| 2 | Cmd | `PeisRegistrationDomainService#addRecheckServiceRequests(AddRecheckSrCommand)` | **写库点**：复查 SR + ext |
| 3 | Cmd | `TaskDomainService#startTask(StartTaskCommand)` | in-progress |

---

### 3.5 IF-PEIS-REC-03 `completeRecheck`（复查完成）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `taskId` | `Long` | 是 |
| `diagnosticReportId` | `Long` | 是 | 复查 DR |
| `operatorId` | `Long` | 是 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `DiagnosticReportQueryService#getById(diagnosticReportId)` | final |
| 2 | Cmd | `TaskDomainService#completeTask(CompleteTaskCommand)` | **写库点** |
| 3 | Cmd | `DiagnosticReportQueryService#linkRecheckReport | 关联原 encounter |

---

## 4. QueryService 与领域事件

- Task 完成事件 → PeisEncounterProgressQuery 读侧刷新，去掉「复查中」。

---

## 5. 写库顺序

| IF | Cmd | 写入 |
|----|-----|------|
| createRecheck | 3 | wkf_task |
| confirmRecheckItems | 2～3 | SR ext + task status |
| completeRecheck | 2～3 | task + DR link |

---

## 6. 非功能性需求实现

### 6.1 幂等性

- createRecheck：hasOpenRecheckTask 门禁。

### 6.2 并发控制

- 同 encounter 单 open Task。

### 6.3 重试与容错

- 无外部强依赖。

### 6.4 数据脱敏

- searchRecheck 患者信息权限。

### 6.5 审计日志

- createRecheck/completeRecheck 记 operatorId。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 层级 | 自动化 | 优先级 |
|------|------|------|--------|:--:|
| 申请复查 | Task 创建 | 功能 | API | P1 |
| 确认项目 | SR 子集 | 功能 | 集成 | P1 |
| 完成闭环 | DR 关联 | 功能 | 集成 | P1 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 重复申请 | 拒绝 | P1 |

### 7.3 查询结果一致性

- progressLabel 与 MAS 列表一致。

### 7.4 集成测试

- REC-02 → INT-01 外发复查 SR。

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 优先级 |
|------|------|:--:|
| Task DM §6 | 复查专用方法名待 WKF 对齐 | P1 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版；Task + 复查 SR 方案 A |
