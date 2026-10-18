# AS-PEIS-DES-001 分科诊台 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-DES-001-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-DES-001-分科诊台.md（v1.1）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application/peis/deptdesk` |
| **边界** | 待检列表；小项录入；常见结果套用；分科结论（dept_conclusion Observation）；LIS/PACS 投影 |
| **编排权威** | AS-PEIS-DES-001 §2、§4 |
| **方法权威** | DM-CLN-005、DM-PEIS-001、DM-PEIS-008 |
| **实现阶段口径** | **目标态全量契约**（P0） |

### 术语口径

| 项 | 约定 |
|----|------|
| **分科结论** | `cln_observation` + OD.`categoryCode=dept_conclusion`（DM-CLN-005 §4.1） |
| **DES-08** | 调用 `LisResultProjectionService`（AS §3 Gap-PEIS-DES-01 关闭前 SDD Cmd 不得单独实现投影 IF） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../peis/deptdesk/
├── PeisDeptExamDeskAppService.java
├── request/
│   ├── SearchPendingRequest.java
│   ├── SaveObservationRequest.java
│   ├── ApplyCommonResultRequest.java
│   ├── SaveDeptConclusionRequest.java
│   └── ProjectResultsRequest.java
├── response/
│   ├── PendingDeskRowResponse.java
│   └── ObservationListResponse.java
└── mapper/
    ├── PeisDeptDeskMapper.java
    └── PeisDeptDeskMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `ObservationDomainService` | 领域 | create/update/finalize |
| `ObservationQueryService` | 查询 | 按 SR 列表 |
| `PeisEncounterProgressQuery` | 查询 | DES-01 列表 |
| `PeisServiceRequestExtDomainService` | 领域 | 执行态/ dispatch |
| `PeisCommonResultQueryService` | 查询 | DES-03 |
| `LisResultProjectionService` | 领域 | DES-08（Gap 关闭后） |

### 2.2 领域方法白名单

| 聚合/服务 | 方法 | 用于 IF |
|-----------|------|---------|
| ObservationDomainService | `createObservation(CreateObservationCommand)` | DES-02/03/04 |
| ObservationDomainService | `updateObservation(UpdateObservationCommand)` | DES-02/03/04 |
| ObservationQueryService | `listByServiceRequestId(Long)` | DES-02 加载 |
| PeisEncounterProgressQuery | `searchPendingDeptDesk(DeptDeskQuery, Pageable)` | DES-01 |
| PeisServiceRequestExtDomainService | `updateDispatchFlags(...)` | DES-08 |
| LisResultProjectionService | `projectFromLis(Long srId)` | DES-08 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| `ObsStatus` | `com.zxplat.domain.clinical.enums.ObsStatus` | DM-CLN-005 §4 `status_enum` | preliminary/final |
| `PeisExecStatus` | `com.zxplat.plugin.peis.domain.enums.PeisExecStatus` | DM-PEIS-001 §2 | checked 等 |
| dept_conclusion | `SaveDeptConclusionRequest.definitionId` | DM-CLN-005 §4.1 | 分科结论 OD |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法 | 中文方法名 | HTTP |
|----|-------------|-----------|------|
| IF-PEIS-DES-01 | `searchPending` | 待检列表 | `POST /api/app/peisDeptExamDesk/searchPending` |
| IF-PEIS-DES-02 | `saveObservation` | 保存小项结果 | `POST /api/app/peisDeptExamDesk/saveObservation` |
| IF-PEIS-DES-03 | `applyCommonResult` | 套用常见结果 | `POST /api/app/peisDeptExamDesk/applyCommonResult` |
| IF-PEIS-DES-04 | `saveDeptConclusion` | 保存分科结论 | `POST /api/app/peisDeptExamDesk/saveDeptConclusion` |
| IF-PEIS-DES-08 | `projectResults` | 拉取 LIS/PACS 结果 | `POST /api/app/peisDeptExamDesk/projectResults` |

### 3.1.1 编排约定

同 REG SDD §3.1.1。

---

### 3.2 IF-PEIS-DES-02 `saveObservation`（保存小项结果）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `serviceRequestId` | `Long` | 是 | |
| `definitionId` | `Long` | 是 | 小项 OD |
| `valueQuantity` | `BigDecimal` | 否 | |
| `valueString` | `String` | 否 | |
| `interpretationCode` | `String` | 否 | |
| `finalizeFlag` | `Boolean` | 否 | true→final |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `OBS_DEFINITION_INACTIVE` | OD 停用 |
| `SR_NOT_IN_DEPT` | 非本科室 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | 参数门禁 | |
| 2 | Pre | `ObservationQueryService#findBySrAndDefinition(srId, defId)` | 幂等 BR-DES-03 |
| 3 | Pre | 科室权限校验 | BR-DES-01 |
| 4 | Cmd | 若不存在：`ObservationDomainService#createObservation(CreateObservationCommand)`；否则 `#updateObservation` | **写库点** cln_observation |
| 5 | Cmd | 若 finalizeFlag：`#finalizeObservation` | |
| 6 | Asm | `ObservationFinalizedEvent`（可选） | |

---

### 3.3 IF-PEIS-DES-04 `saveDeptConclusion`（保存分科结论）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `serviceRequestId` | `Long` | 是 | |
| `definitionId` | `Long` | 是 | dept_conclusion OD |
| `valueString` | `String` | 是 | 结论文本 |
| `conditionDefinitionId` | `Long` | 否 | 模板 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1～3 | Gate/Pre | 同 DES-02 | |
| 4 | Cmd | `ObservationDomainService#create/update` | **写库点**；禁止 source_observation_id 手工填 LIS 值 |

---

### 3.4 IF-PEIS-DES-08 `projectResults`（拉取 LIS/PACS 结果）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | ext.dispatch=sent | BR-DES-06 |
| 2 | Cmd | `LisResultProjectionService#projectFromLis(serviceRequestId)` | **写库点**（Gap 关闭后） |
| 3 | Cmd | `PeisServiceRequestExtDomainService#updateDispatchFlags(..., resultReceived)` | |

---

### 3.5 IF-PEIS-DES-01 `searchPending`（待检列表）

| 阶段 | 调用 | 说明 |
|------|------|------|
| Pre | `PeisEncounterProgressQuery#searchPendingDeptDesk(DeptDeskQuery, Pageable)` | **仅读** |

---

## 4. QueryService 与领域事件

- `ObservationFinalizedEvent` → MAS 列表刷新（P1）。
- `PeisResultProjectedEvent` → 诊台 UI。

---

## 5. 写库顺序

| IF | 写入 |
|----|------|
| DES-02/04 | `cln_observation` upsert |
| DES-08 | `cln_observation` + `peis_service_request_ext` |

---

## 6. 非功能性需求实现

### 6.1 幂等性

- DES-02 同 SR+definitionId upsert 幂等。

### 6.2 并发控制

- 同 SR+definitionId 乐观锁（§3.2 序号 4）。

### 6.3 重试与容错

- INT-03 投影失败不 rollback 手工录入（最终一致 P1）。

### 6.4 数据脱敏

- 诊台列表 patientName 权限 Gate。

### 6.5 审计日志

- DES-02/04/08 记 AuditLog。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 要点 | 层级 | 自动化 | 优先级 |
|------|------|------|--------|:--:|
| 录入 | 手工无 source | 功能 | API | P0 |
| 结论 | dept_conclusion 行 | 功能 | API | P0 |
| 投影 | LIS→CLN source 链 | 功能 | 集成 | P0 |
| 权限 | 他科室拒绝 | 功能 | API | P0 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| 并发录入 | 乐观锁冲突提示 | P0 |

### 7.3 查询结果一致性

- DES-01 列表与 PeisEncounterProgressQuery 待检口径一致。

### 7.4 集成测试

- INT-01 外发 → DES-08 投影可见。

### 7.5 测试缺口跟踪

| 缺口 | LisResultProjectionService | P0 |

---

## 8. 变更历史

| v1.0 | 2026-06-27 | 首版全量 SDD |
