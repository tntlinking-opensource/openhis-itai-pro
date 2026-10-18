# AS-PEIS-MAS-001 总检诊台 · 应用服务详细设计（SDD）

> **编码**：AS-PEIS-MAS-001-SDD · **版本**：v1.0 · **日期**：2026-06-27  
> **对应 AS**：AS-PEIS-MAS-001-总检诊台.md（v1.0）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `health-application/peis/masterreview` |
| **边界** | 待总检列表；汇总 CLN（含 dept_conclusion）；综述 DR；总检完成 + DR index |
| **方法权威** | DM-PEIS-008、DM-CLN-006、**DM-CLN-008** |
| **实现阶段口径** | **目标态全量契约**（P0） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../peis/masterreview/
├── PeisMasterReviewDeskAppService.java
├── request/
│   ├── SearchPendingMasterRequest.java
│   ├── LoadSummaryRequest.java
│   ├── SaveMasterDraftRequest.java
│   └── CompleteMasterReviewRequest.java
├── response/
│   ├── MasterPendingRowResponse.java
│   └── MasterSummaryResponse.java
└── mapper/
    ├── PeisMasterReviewMapper.java
    └── PeisMasterReviewMapperImpl.java
```

| 依赖注入 | 类型 | 用途 |
|----------|------|------|
| `PeisEncounterProgressQuery` | 查询 | MAS-01 |
| `ObservationQueryService` | 查询 | MAS-02 含 dept_conclusion |
| `DiagnosticReportDomainService` | 领域 | MAS-03/04 → `cln_diagnostic_report |
| `DiagnosticReportQueryService` | 查询 | MAS-02/03 |
| `DiagnosticReportIndexUpsertService` | 领域 | MAS-04 index |
| `EncounterDomainService` | 领域 | finish Encounter |
| `PeisServiceRequestExtQueryService` | 查询 | 完成度校验 |

### 2.2 领域方法白名单

| 服务 | 方法 | IF |
|------|------|-----|
| PeisEncounterProgressQuery | `searchPendingMasterReview(MasterReviewQuery, Pageable)` | MAS-01 |
| ObservationQueryService | `listByEncounterId(encounterId)` | MAS-02 |
| DiagnosticReportDomainService | `saveMasterDraft(SaveMasterDraftCommand)` | MAS-03 |
| DiagnosticReportDomainService | `finalizeMaster(FinalizeMasterCommand)` | MAS-04 |
| DiagnosticReportIndexUpsertService | `upsertFromPeis(UpsertDrIndexFromPeisCommand)` | MAS-04 |
| EncounterDomainService | `finishEncounter(FinishEncounterCommand)` | MAS-04 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| DR status | `DiagnosticReport.statusEnum` | [DM-CLN-008](./DM-CLN-008-DiagnosticReport-临床总检报告.md) | preliminary/final |
| Encounter finished | `EncounterStatus.FINISHED` | DM-ADM-002 §2.3 | MAS-04 后 |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口

| IF | 方法 | 中文 | HTTP |
|----|------|------|------|
| IF-PEIS-MAS-01 | `searchPendingMaster` | 待总检列表 | `POST /api/app/peisMasterReviewDesk/searchPendingMaster` |
| IF-PEIS-MAS-02 | `loadSummary` | 汇总结果 | `POST /api/app/peisMasterReviewDesk/loadSummary` |
| IF-PEIS-MAS-03 | `saveMasterDraft` | 保存综述 | `POST /api/app/peisMasterReviewDesk/saveMasterDraft` |
| IF-PEIS-MAS-04 | `completeMasterReview` | 总检完成 | `POST /api/app/peisMasterReviewDesk/completeMasterReview` |

### 3.2 IF-PEIS-MAS-04 `completeMasterReview`

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `encounterId` | `Long` | 是 |
| `operatorId` | `Long` | 是 |

#### 失败码

| 码 | 场景 |
|----|------|
| `DEPT_NOT_COMPLETE` | 分科未完成 |
| `MASTER_ALREADY_FINAL` | 已总检 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `PeisServiceRequestExtQueryService#listByEncounterId` | BR-MAS-01 完成度 |
| 2 | Cmd | `DiagnosticReportDomainService#finalizeMaster(FinalizeMasterCommand)` | **写库点 1** `cln_diagnostic_report final |
| 3 | Cmd | `DiagnosticReportIndexUpsertService#upsertFromPeis(UpsertDrIndexFromPeisCommand)` | **写库点 2** index，`moduleEnum=PEIS`，`sourceReportId=dr.id` |
| 4 | Cmd | `EncounterDomainService#finishEncounter(FinishEncounterCommand)` | **写库点 3** |
| 5 | Asm | `PeisMasterReviewCompletedEvent` | |

### 3.3 IF-PEIS-MAS-02 `loadSummary`（汇总结果）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `encounterId` | `Long` | 是 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `encounterId` | `Long` | |
| `deptSections` | `List<DeptSummarySectionDto>` | 含 CLN + dept_conclusion |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | `EncounterQueryService#mustGetEncounter(encounterId)` | class=PE |
| 2 | Pre | `ObservationQueryService#listByEncounterId(encounterId)` | **仅读路径** |
| 3 | Asm | `PeisMasterReviewMapper#toMasterSummaryResponse` | |

### 3.4 IF-PEIS-MAS-03 `saveMasterDraft`（保存综述）

#### Request

| 字段 | 类型 | 必填 |
|------|------|:--:|
| `encounterId` | `Long` | 是 |
| `conclusionText` | `String` | 是 |
| `operatorId` | `Long` | 是 |

#### 失败码

| 码 | 场景 |
|----|------|
| `MASTER_ALREADY_FINAL` | 已 final 不可改 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Pre | DR 非 final | |
| 2 | Cmd | `DiagnosticReportDomainService#saveMasterDraft(SaveMasterDraftCommand)` | **写库点** `cln_diagnostic_report preliminary |

### 3.5 IF-PEIS-MAS-01 `searchPendingMaster`（待总检列表）

| 阶段 | 调用 | 说明 |
|------|------|------|
| Pre | `PeisEncounterProgressQuery#searchPendingMasterReview(...)` | 仅读 |

---

## 4. QueryService 与领域事件

- MAS-04 → REP-001 可打印列表刷新（DR index + final）。

---

## 5. 写库顺序

| IF | 顺序 |
|----|------|
| MAS-04 | cln_diagnostic_report → cln_diagnostic_report_index → adm_encounter.status |

同事务。

---

## 6. 非功能性需求实现

### 6.1 幂等性

- MAS-04 重复提交 final → REPORT_ALREADY_FINAL。

### 6.2 并发控制

- DR final 写 optimistic lock。

### 6.3 重试与容错

- 无。

### 6.4 数据脱敏

- MAS-01 列表按科室权限。

### 6.5 审计日志

- MAS-04 记总检医生、时间。

---

## 7. 测试要点

### 7.1 功能测试

| 场景 | 层级 | 自动化 | 优先级 |
|------|------|--------|:--:|
| 分科未完成拒绝 MAS-04 | 功能 | API | P0 |
| index module=PEIS/HEALTH | 功能 | 集成 | P0 |
| Encounter finished | 功能 | API | P0 |

### 7.2 非功能测试

| 场景 | 要点 | 优先级 |
|------|------|:--:|
| MAS-02 聚合 | P95 < 3s | P0 |

### 7.3 查询结果一致性

- MAS-01 与 DES 完成态一致。

### 7.4 集成测试

- MAS-04 → REP-01 可打印。

### 7.5 测试缺口跟踪

| 缺口 | 无 P0 |

---

## 8. 变更历史

| v1.0 | 2026-06-27 | 首版 |
