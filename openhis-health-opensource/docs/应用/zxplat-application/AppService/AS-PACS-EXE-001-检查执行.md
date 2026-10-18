# 检查域 · 检查执行 · 应用服务设计（AS）

> **编码**：AS-PACS-EXE-001 · **版本**：v1.2 · **日期**：2026-09-22  
> **菜单**：检查执行 · **类名**：`PacsExamExecutionAppService` · **路由**：`POST /api/app/pacsExamExecution/{method}`  
> **待摄口径**：主路径 `scheduled`；兼容已有 accession 的 `registered`（DM / Query）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 待摄/已检/他科已检/已作废列表、确认检查完成 |
| **在外** | 登记 REG-001；DICOM → imaging 扩展；页面内作废写（走 REG/INT） |
| **涉及域** | PacsWorkItem、Patient（读侧 JOIN） |

### 1.2 角色与关键动作

| 角色 | 动作 |
|------|------|
| **技师** | 查待摄/已检、标记完成 |
| **系统** | 状态机 → `completed`；发 `PacsExamCompletedEvent` |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **待摄** | `searchPendingExecution`：scheduled，或 registered 且已有 accession |
| **已检** | status=`completed`（本科室可选 organizationId 过滤） |
| **他科已检** | status=`completed` 且 `organizationId` 不等于当前科室 |
| **已作废** | status=`entered-in-error` |
| **ImagingStudy** | 非 P0 阻塞 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期 P0** | EXE-01、EXE-02、EXE-05 |
| **本期 P1（序号 47）** | EXE-03、EXE-04 |
| **非目标** | EXE-06；显式 startExam UI（Domain 有能力，本期不暴露）；本页作废写 |

### 1.5 领域清单

| 聚合 | DM |
|------|-----|
| PacsWorkItem | [DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md) |

### 1.6 业务规则

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-EXE-01** | `completeExam` → `completed` | EXE-05 |
| **BR-EXE-02** | 完成前须 accession；状态 ∈ registered/scheduled/in-progress | EXE-05 |
| **BR-EXE-03** | 列表 JOIN Patient，无 PHI 落库 | EXE-01/02/03/04 |
| **BR-EXE-04** | 他科已检须传 `excludeOrganizationId`（当前科室）；排除该科室 completed | EXE-03 |
| **BR-EXE-05** | 已作废列表仅 `entered-in-error`；只读 | EXE-04 |

---

## 2. 页面与接口总览

| IF | 用户动作 | 语义 | 读写 | 关键规则 | 状态变化 |
|----|----------|------|:--:|----------|----------|
| **IF-PACS-EXE-01** | 待摄列表 | 分页 | 读 | BR-EXE-03 | — |
| **IF-PACS-EXE-02** | 已检列表 | 分页 | 读 | BR-EXE-03 | — |
| **IF-PACS-EXE-03** | 他科已检 | 分页 | 读 | BR-EXE-03/04 | — |
| **IF-PACS-EXE-04** | 已作废列表 | 分页 | 读 | BR-EXE-03/05 | — |
| **IF-PACS-EXE-05** | 确认完成 | 标记已检 | 写 | BR-EXE-01/02 | → completed |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**。

---

## 4. 接口编排说明书

### 4.1 IF-PACS-EXE-01

| 维度 | 说明 |
|------|------|
| **主路径** | `searchPendingExecution` → JOIN Patient 姓名 |
| **查询** | `PacsWorkItemQueryService#searchPendingExecution` |

### 4.2 IF-PACS-EXE-02

| 维度 | 说明 |
|------|------|
| **主路径** | `searchCompleted` → JOIN Patient |
| **查询** | `PacsWorkItemQueryService#searchCompleted` |

### 4.3 IF-PACS-EXE-03

| 维度 | 说明 |
|------|------|
| **主路径** | Gate excludeOrganizationId → `searchCompletedOtherDept` → JOIN Patient |
| **查询** | `PacsWorkItemQueryService#searchCompletedOtherDept` |

### 4.4 IF-PACS-EXE-04

| 维度 | 说明 |
|------|------|
| **主路径** | `searchEnteredInError` → JOIN Patient |
| **查询** | `PacsWorkItemQueryService#searchEnteredInError` |

### 4.5 IF-PACS-EXE-05

| 维度 | 说明 |
|------|------|
| **主路径** | `completeExam` → 发布 `PacsExamCompletedEvent` |
| **写** | `PacsWorkItemDomainService#completeExam` |

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `PACS_WORK_ITEM_INVALID_STATUS` | 状态不可完成 |
| `PACS_WORK_ITEM_RULE_CONFLICT` | 缺检查号等 |
| `PACS_WORK_ITEM_NOT_FOUND` | 不存在 |
| `PACS_WORK_ITEM_INVALID_ARGUMENT` | 分页非法；他科列表缺 excludeOrganizationId |

---

## 6. 事件语义

| 事件 | 触发 | 消费方 |
|------|------|--------|
| `PacsExamCompletedEvent` | EXE-05 | REP 待书写 |

---

## 7. 非功能性需求

列表 P95 < 2s；完成操作事务一致。

---

## 8. 测试要点

- 无 accession 不可 complete
- complete 后 status=completed
- 待摄/已检列表含姓名无 PHI 列

---

## 9. 关联文档

- SDD：[AS-PACS-EXE-001-SDD](../AppService-SDD/AS-PACS-EXE-001-检查执行-SDD.md)
- REG：[AS-PACS-REG-001](./AS-PACS-REG-001-登记工作台.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版 P0 EXE |
| v1.1 | 2026-09-20 | D-5：对齐 Domain 错误码与待摄 Query 口径 |
| v1.2 | 2026-09-22 | 序号 47：EXE-03 他科已检、EXE-04 已作废 |
