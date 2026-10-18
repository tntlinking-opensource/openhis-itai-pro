# 体检域 · 复查管理 · 应用服务设计（AS）

> **编码**：AS-PEIS-REC-001 · **版本**：v1.1 · **日期**：2026-06-27  
> **菜单**：复查管理 · **类名**：`PeisRecheckManageAppService` · **路由**：`/peis/recheck`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 复查申请/列表；复查项目确认；复查完成与报告关联 |
| **在外** | 复查计费 → **health 收费 Client**；原 DR 只读 |
| **涉及域** | WKF `Task`、ServiceRequest、`peis_service_request_ext`、DiagnosticReport |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **总检/分科** | 发起复查、确认复查项目 |
| **登记** | 复查项目计费（health） |
| **系统** | Task 未关闭 → 进度「复查中」 |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **复查建模（方案 A）** | `Task(for=Encounter)` + 复查 SR 子集（[Peis 04 §4.5](../PeisAppService/04-FHIR领域模型.md#45-复查--task--复查-sr)） |
| **不改 Encounter status** | 复查态由 Task + 进度 Query 表达 |
| **复查 SR** | 新增 SR + ext；可标记 exec 复查态 |

### 1.4 范围

| P1 | REC-01～03 |
| 非目标 | 复查专用 Encounter；PeisRecheckExt 平行表（方案 B） |

### 1.5 领域清单

| 聚合 | 职责 | 参考 |
|------|------|------|
| Task | 复查生命周期 | Peis 04 §4.5 |
| ServiceRequest | 复查项目实例 | DM-WKF-004 |
| PeisServiceRequestExt | 执行态 | DM-PEIS-001 |
| DiagnosticReport | 复查完成关联 | DM-CLN-006 |

### 1.6 业务规则（BR）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-REC-01** | 未关闭复查 Task → 进度展示「复查中」 | REC-01 |
| **BR-REC-02** | 复查 SR 独立计费（health Client） | REC-02 |
| **BR-REC-03** | 原 Encounter 须已总检 final 或院点允许策略 | REC-01 |
| **BR-REC-04** | completeRecheck 关闭 Task 并关联新/修订 DR | REC-03 |

---

## 2. 页面与接口总览（契约概览）

| IF | 动作 | 语义 | 读写 | 关键规则 | 状态变化 | 涉及域 | 查询服务 |
|----|------|------|:--:|----------|----------|----------|----------|
| **IF-PEIS-REC-01** | 列表/申请 | 复查 Task 查询与创建 | 读/写 | BR-REC-01/03 | Task requested | Task | TaskQueryService |
| **IF-PEIS-REC-02** | 项目确认 | 追加复查 SR | 写 | BR-REC-02 | 新 SR + ext | SR | — |
| **IF-PEIS-REC-03** | 复查完成 | 关闭 Task、关联 DR | 写 | BR-REC-04 | Task completed | Task、DR | DiagnosticReportQueryService |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| GAP-PEIS-REC-01 | **DM 能力** | [Peis 04 §4.5](../PeisAppService/04-FHIR领域模型.md#45-复查--task--复查-sr) | `TaskDomainService#createRecheckTask` / `completeTask` 复查语义 | **已关闭**：`peis_recheck_task` 本地承载 Task 语义（`PeisRecheckDomainService`），待 WKF Task 迁入 | REC-01/03 | P1 | **已关闭** |
| GAP-PEIS-REC-02 | **DM 能力** | [DM-PEIS-007](./DM-PEIS-007-PeisEncounterExt-就诊扩展.md) §8 | `addRecheckServiceRequests` 应用编排 | **已关闭**：`PeisRegistrationOrchestrator#addRecheckServiceRequests` | REC-02 | P1 | **已关闭** |

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-PEIS-REC-01 复查列表与申请

**searchRecheck**

| 步骤 | 说明 |
|------|------|
| 1 | TaskQueryService 分页查复查 Task |
| 2 | 可选 PeisEncounterProgressQuery 补充进度 label |

**createRecheck**

| 步骤 | 说明 |
|------|------|
| 1 | 校验原 Encounter 可复查（BR-REC-03） |
| 2 | TaskDomainService 创建 Task(for=encounterId) |
| 3 | 返回 taskId |

### 4.2 IF-PEIS-REC-02 复查项目确认

| 步骤 | 说明 |
|------|------|
| 1 | 校验 Task 未 completed |
| 2 | `addRecheckServiceRequests`：追加 SR + ext |
| 3 | 提示 health 收费（Client，不写 ChargeItem） |

### 4.3 IF-PEIS-REC-03 复查完成

| 步骤 | 说明 |
|------|------|
| 1 | 校验复查 SR 均已 resultReceived 或院点策略 |
| 2 | completeTask |
| 3 | 关联 DiagnosticReport（新 DR 或 amended 链） |

---

## 5. 业务异常与用户提示

| 码 | 场景 | 提示 |
|----|------|------|
| `ENCOUNTER_NOT_ELIGIBLE` | 不可复查 | 当前状态不可申请复查 |
| `TASK_ALREADY_CLOSED` | Task 已关 | 复查已结束 |
| `RECHECK_ITEMS_EMPTY` | 无复查项 | 请先确认复查项目 |

---

## 6. 事件语义与消费方

| 事件 | 消费方 |
|------|--------|
| `PeisRecheckCreatedEvent` | 进度 Query 刷新（P1） |
| `PeisRecheckCompletedEvent` | REP 可打印列表（P1） |

---

## 7. 非功能性需求

复查 Task + SR 同事务；列表分页；AuditLog on create/complete。

---

## 8. 测试要点

- 总检后 createRecheck → Task 打开 → 进度「复查中」
- confirmRecheckItems → 新 SR 可 INT-01 外发
- completeRecheck → Task 关、DR 关联
- 复查计费仅 health Client

---

## 9. 关联文档与变更历史

- SDD：[AS-PEIS-REC-001-SDD](../AppService-SDD/AS-PEIS-REC-001-复查管理-SDD.md)
- [Peis 04 §4.5](../PeisAppService/04-FHIR领域模型.md#45-复查--task--复查-sr)
- MAS：[AS-PEIS-MAS-001](./AS-PEIS-MAS-001-总检诊台.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 补全 §1～§8；Task/SR Gap |
| v1.0 | 2026-06-27 | 首版 P1 骨架 |
