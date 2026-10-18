# AS-PACS-EXE-001 检查执行 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-EXE-001-SDD · **版本**：v1.1 · **日期**：2026-09-22  
> **对应 AS**：AS-PACS-EXE-001-检查执行.md（v1.2）  
> **实现阶段口径**：目标态全量契约（序号 24～26）+ 序号 47 EXE-03/04

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application` · `pacs/execution` |
| **边界** | 待摄/已检/他科已检/已作废列表、确认完成；不写 DICOM/PHI；不作废写 |
| **编排权威** | AS-PACS-EXE-001 §2、§4 |
| **方法权威** | DM-PACS-001 §6 |
| **实现方式** | `PacsExamExecutionAppService`；HTTP 一律 POST |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务** | `completeExam` 单事务 |
| **待摄** | `searchPendingExecution` |
| **姓名** | `PatientQueryService#findPatientNamesByIds` |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../pacs/execution/
├── service/PacsExamExecutionAppService.java
├── request/SearchPendingExecutionRequest.java
├── request/SearchCompletedExamRequest.java
├── request/SearchCompletedOtherDeptRequest.java
├── request/SearchEnteredInErrorRequest.java
├── request/CompleteExamRequest.java
├── response/SearchPendingExecutionResponse.java
├── response/SearchCompletedExamResponse.java
├── response/SearchCompletedOtherDeptResponse.java
├── response/SearchEnteredInErrorResponse.java
├── response/CompleteExamResponse.java
├── dto/PacsExecutionRowDto.java
└── mapper/PacsExecutionMapper.java
```

| 注入 | 类型 | 用途 |
|------|------|------|
| `PacsWorkItemQueryService` | 查询 | 待摄/已检 |
| `PacsWorkItemDomainService` | 领域 | completeExam |
| `PatientQueryService` | 查询 | 姓名 |
| `CurrentPractitionerResolver` | 应用 | 操作人 |
| `PageWindowGate` | 应用 | 分页 |
| `PacsExecutionMapper` | Mapper | DTO |

### 2.2 领域方法白名单

| 聚合 | 方法 | IF |
|------|------|-----|
| PacsWorkItem | `searchPendingExecution` | EXE-01 |
| PacsWorkItem | `searchCompleted` | EXE-02 |
| PacsWorkItem | `searchCompletedOtherDept` | EXE-03 |
| PacsWorkItem | `searchEnteredInError` | EXE-04 |
| PacsWorkItem | `completeExam(CompletePacsExamCommand)` | EXE-05 |
| Patient | `findPatientNamesByIds` | EXE-01～04 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| 工单状态 | `PacsWorkItemStatus` | DM-PACS-001 §3.3 | scheduled / completed |
| 行状态 | `PacsExecutionRowDto.statusEnum` | DM-PACS-001 §3.1 | 出站 |

---

## 3. 按接口 ID 命名展开

先读完各 IF 编排序列再执行。

### 3.1 应用服务接口

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| IF-PACS-EXE-01 | `searchPendingExecution` | 待摄列表 | `POST /api/app/pacsExamExecution/searchPendingExecution` |
| IF-PACS-EXE-02 | `searchCompleted` | 已检列表 | `POST /api/app/pacsExamExecution/searchCompleted` |
| IF-PACS-EXE-03 | `searchCompletedOtherDept` | 他科已检 | `POST /api/app/pacsExamExecution/searchCompletedOtherDept` |
| IF-PACS-EXE-04 | `searchEnteredInError` | 已作废列表 | `POST /api/app/pacsExamExecution/searchEnteredInError` |
| IF-PACS-EXE-05 | `completeExam` | 确认完成 | `POST /api/app/pacsExamExecution/completeExam` |

### 3.1.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| Gate | 分页/操作人 | 停止 |
| Pre | 读侧 | 禁止 Cmd |
| Cmd | Domain 写 | 回滚 |
| Asm | Mapper | — |

### 3.2 IF-PACS-EXE-01 `searchPendingExecution`

**Request**：pageNo/pageSize 必填；organizationId、modalityCode、startDate、endDate 可选。  
**Response**：PagedResult\<PacsExecutionRowDto\>。

**PacsExecutionRowDto**：workItemId、accessionNumber、patientId、patientName、encounterId、organizationId、statusEnum、modalityCode、bodySiteCode、scheduledTime、performedTime、orderCount、createdAt。

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | `PageWindowGate#requirePositivePageWindow` | |
| 2 | Pre | `PacsWorkItemQueryService#searchPendingExecution` | 仅读 |
| 3 | Pre | `PatientQueryService#findPatientNamesByIds` | |
| 4 | Asm | `PacsExecutionMapper#toRowDtos` | 仅读路径 |

### 3.3 IF-PACS-EXE-02 `searchCompleted`

复用 EXE-01 编排序列；Pre 改为 `searchCompleted`。

### 3.4 IF-PACS-EXE-03 `searchCompletedOtherDept`

**Request**：pageNo/pageSize；**excludeOrganizationId 必填**（当前科室）；modalityCode、startDate、endDate 可选。  
**语义**：`status=completed` 且（`organizationId IS NULL` 或 `organizationId ≠ excludeOrganizationId`）。

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Gate | PageWindowGate；excludeOrganizationId 非空 |
| 2 | Pre | `searchCompletedOtherDept` |
| 3 | Pre | 姓名批量 |
| 4 | Asm | Mapper |

### 3.5 IF-PACS-EXE-04 `searchEnteredInError`

**Request**：同 EXE-02（organizationId/modality 可选）。  
**语义**：`status=entered-in-error`。

复用 EXE-01 编排序列；Pre 改为 `searchEnteredInError`。

### 3.6 IF-PACS-EXE-05 `completeExam`

**Request**

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| workItemId | Long | 是 | 工单 |
| performedTime | LocalDateTime | 否 | 空=now |
| performerPractitionerId | Long | 否 | 执行人 |
| deviceId | Long | 否 | 设备 |

**Response**：workItemId、accessionNumber、statusEnum、performedTime。

**失败码**：`PACS_WORK_ITEM_NOT_FOUND` / `INVALID_STATUS` / `RULE_CONFLICT`。

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | `CurrentPractitionerResolver#requireOperatorId` | |
| 2 | Cmd | `PacsWorkItemDomainService#completeExam(CompletePacsExamCommand)` | **写库点**；事件 Domain 内发布 |
| 3 | Asm | Mapper → Response | |

---

## 4. QueryService 与领域事件

| 事件 | 读侧 |
|------|------|
| `PacsExamCompletedEvent` | 待摄减少、已检增加；REP 可写 |

---

## 5. 写库顺序

| IF | Cmd | 写入 |
|----|-----|------|
| EXE-05 | completeExam | `pacs_work_item` status/performed_time |

事务：单方法。

---

## 6. 非功能性需求实现

### 6.1 幂等性

已 completed 再 complete → Domain 抛 INVALID_STATUS（拒绝）。

### 6.2 并发控制

单聚合更新。

### 6.3 重试与容错

写失败回滚。

### 6.4 数据脱敏

姓名仅 DTO。

### 6.5 审计日志

updatedBy；关联 §3.4 Cmd。

---

## 7. 测试要点

### 7.1 功能

| 场景 | 优先级 |
|------|:------:|
| 待摄列表含姓名 | P0 |
| complete → completed | P0 |
| 无 accession 拒绝 | P0 |
| 他科已检排除本科室 | P0 |
| 已作废仅 entered-in-error | P0 |

### 7.2～7.5

同 REG：列表性能、事件后列表一致、端到端登记→完成；App 单测可后补。

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-20 | D-5 首版；驱动序号 24～26 |
| v1.1 | 2026-09-22 | 序号 47：EXE-03/04 |
