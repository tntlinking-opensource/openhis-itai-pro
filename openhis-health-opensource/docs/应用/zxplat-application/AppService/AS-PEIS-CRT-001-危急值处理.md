# 体检域 · 危急值处理 · 应用服务设计（AS）

> **编码**：AS-PEIS-CRT-001 · **版本**：v1.1 · **日期**：2026-06-27  
> **菜单**：危急值 · **类名**：`PeisCriticalValueDeskAppService` · **路由**：`/peis/critical-value`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 诊台危急值列表、受理（acknowledge）、闭环留痕；异常统计（P2） |
| **在外** | **规则维护** → [AS-PEIS-CFG-002](./AS-PEIS-CFG-002-诊断与表达式.md) **CFG-12**（对应 00 之 CRT-01） |
| **涉及域** | health **DetectedIssue**；`cln_observation` 联动；[DM-PEIS-006](./DM-PEIS-006-PeisAutoExpress-自动诊断表达式.md) 规则引用 |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **分科/总检** | 查看危急值、受理、填写处理结果 |
| **系统** | 可选：小项异常 / LIS 投影后自动建 Issue |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **实例** | **DetectedIssue**（health 通用，**非** PEIS 平行表） |
| **规则 CRT-01** | CFG-12 危急值规则 → `peis_auto_express` / 表达式 |
| **闭环** | acknowledge → closeWithDeal 留痕（BR-CRT-02） |

### 1.4 范围

| P1 | CRT-02（列表+受理）、CRT-03（闭环留痕） |
| P2 | CRT-04 异常统计（学生/教职工） |
| 非目标 | CRT-01 规则 CRUD（CFG-002）；LIS 仪器侧危急值协议（LIS Module） |

### 1.5 领域清单

| 聚合 / 服务 | 职责 | 参考 |
|-------------|------|------|
| DetectedIssue | 危急值实例与状态机 | health clinical |
| ObservationQueryService | 关联小项 / LIS 投影 | DM-CLN-005 |
| PeisAutoExpressQueryService | 规则匹配（CFG-12） | DM-PEIS-006 |

### 1.6 业务规则（BR）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-CRT-01** | 小项 Observation 异常 / 规则命中可 **可选** 自动 `createFromObservation` | CRT-02 |
| **BR-CRT-02** | 处理须留痕：closeWithDeal 写 dealResult | CRT-03 |
| **BR-CRT-03** | 列表仅 **Encounter(PE)** 上下文 Issue | CRT-02 |
| **BR-CRT-04** | 已 closed Issue 不可再 acknowledge | CRT-02 |
| **BR-CRT-05** | close 前须已 acknowledge（院点可配置跳过） | CRT-03 |

---

## 2. 页面与接口总览（契约概览）

| IF | 用户动作 | 接口语义 | 读写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
|----|----------|----------|:--:|----------|----------|----------|----------|
| **IF-PEIS-CRT-02** | 危急值列表 | 分页查询 PE 危急值 | 读 | BR-CRT-03 | — | DetectedIssue | DetectedIssueQueryService |
| **IF-PEIS-CRT-02** | 受理 | acknowledge | 写 | BR-CRT-04 | registered→acknowledged | DetectedIssue | — |
| **IF-PEIS-CRT-03** | 闭环 | 填写处理结果并关闭 | 写 | BR-CRT-02/05 | acknowledged→closed | DetectedIssue | — |
| **IF-PEIS-CRT-04** | 异常统计 | 按人员类型聚合 | 读 | P2 | — | DetectedIssue | DetectedIssueQueryService |

> **CRT-01（规则）**：见 **IF-PEIS-CFG-12**，由 CFG-002 维护，**不在**本 AS IF 表重复编号。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| GAP-PEIS-CRT-01 | **DM 能力** | health DetectedIssue 领域文档 §5 | `DetectedIssueDomainService#acknowledge` / `#closeWithDeal` / `#createFromObservation` PE 语义 | DM + 领域代码实现 | CRT-02/03 | P1 | 待建 |
| GAP-PEIS-CRT-02 | **QueryService 能力** | DetectedIssueQueryService（health） | `#searchPeisCriticalValues` PE 过滤扩展 | QueryService 实现与文档一致 | CRT-02 | P1 | 待建 |

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-PEIS-CRT-02 危急值列表

| 维度 | 说明 |
|------|------|
| **触发** | 危急值菜单打开 / 刷新 |
| **主路径** | `searchPeisCriticalValues`：orgId、deptId、status 分页 |
| **查询侧** | 纯读 QueryService；关联 encounterId、observationId 展示 |

### 4.2 IF-PEIS-CRT-02 受理（handleCriticalValue）

| 步骤 | 说明 |
|------|------|
| 1 | Pre：Issue 存在且非 closed |
| 2 | Cmd：`DetectedIssueDomainService#acknowledge` |
| 3 | 记录 operatorId、comment |

### 4.3 IF-PEIS-CRT-03 闭环留痕（closeCriticalValueDeal）

| 步骤 | 说明 |
|------|------|
| 1 | Pre：status=acknowledged（BR-CRT-05） |
| 2 | Cmd：`closeWithDeal(dealResult)` |
| 3 | AuditLog 全量 dealResult |

### 4.4 IF-PEIS-CRT-04 异常统计（P2）

| 维度 | 说明 |
|------|------|
| **触发** | 按日期、STU/STF 统计 |
| **主路径** | `searchOutlierStatistics` 只读聚合 |

### 4.5 自动建 Issue（可选，异步 P2）

DES-02 保存或 DES-08 LIS 投影后 → 规则引擎匹配 CFG-12 → `createFromObservation`；**失败不阻塞**诊台保存。

---

## 5. 业务异常与用户提示

| 码 | 场景 | 提示 |
|----|------|------|
| `ISSUE_NOT_FOUND` | 无 Issue | 记录不存在 |
| `ISSUE_ALREADY_CLOSED` | 已闭环 | 已处理完成 |
| `ISSUE_NOT_ACKNOWLEDGED` | 未受理即关闭 | 请先受理 |

---

## 6. 事件语义与消费方

| 事件 | 触发 | 消费方 |
|------|------|--------|
| `ObservationAbnormalDetectedEvent`（可选） | DES/LIS 投影 | 自动 createFromObservation |
| `CriticalValueClosedEvent` | CRT-03 | 统计 RPT（P2） |

---

## 7. 非功能性需求

| 项 | 承诺 |
|----|------|
| **审计** | closeWithDeal 全量留痕 |
| **脱敏** | 列表 patient 标识权限 Gate |
| **并发** | Issue 乐观锁；单 Issue 单闭环 |
| **一致性** | 与 DES 小项 interpretation 一致 |

---

## 8. 测试要点（业务视角）

- LIS 投影异常值 → 列表可见（规则命中时）
- acknowledge → 状态变更；重复 acknowledge 拒绝
- closeWithDeal → closed + dealResult 可查
- 非 PE Encounter 的 Issue 不出现在 PE 列表
- CFG-12 规则变更后新 Issue 命中新规则

---

## 9. 关联文档与变更历史

- SDD：[AS-PEIS-CRT-001-SDD](../AppService-SDD/AS-PEIS-CRT-001-危急值处理-SDD.md)
- 规则：[AS-PEIS-CFG-002](./AS-PEIS-CFG-002-诊断与表达式.md)（CFG-12 / CRT-01）
- DES：[AS-PEIS-DES-001](./AS-PEIS-DES-001-分科诊台.md)
- DM：[DM-PEIS-006](./DM-PEIS-006-PeisAutoExpress-自动诊断表达式.md)、[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 补全 §1～§8；CRT-01→CFG-12；DetectedIssue Gap |
| v1.0 | 2026-06-27 | 首版 P1 骨架 |
