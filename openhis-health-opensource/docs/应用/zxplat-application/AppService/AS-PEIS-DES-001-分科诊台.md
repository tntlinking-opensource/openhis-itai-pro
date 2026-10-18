# 体检域 · 分科诊台 · 应用服务设计（AS）

> **编码**：AS-PEIS-DES-001 · **版本**：v1.2 · **日期**：2026-06-27  
> **菜单**：分科诊台 · **类名**：`PeisDeptExamDeskAppService` · **路由**：`/peis/dept-desk`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 本科室待检列表、小项结果录入、常见结果套用、分科诊断、拉取 LIS/PACS 结果投影 |
| **在外** | LIS/PACS 执行与审核；INT-01 外发；总检 MAS-001 |
| **涉及域** | `cln_observation`、`cln_observation_definition`、Encounter(PE)、ServiceRequest |

### 1.2 角色与关键动作

| 角色 | 动作 |
|------|------|
| **分科医生/护士** | 录入体格与小项、套用模板、写分科诊断 |
| **系统** | DES-08 投影 LIS/PACS → CLN；触发 INT-01（可选同屏） |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **CLN 真源** | 分科录入写 **`cln_observation`**（[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)） |
| **LIS 投影** | DES-08 须 `source_observation_id` |
| **外发** | 检验/检查类 SR → [AS-PEIS-INT-001](./AS-PEIS-INT-001-LIS-PACS外发.md) |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期 P0** | DES-01～04、DES-08 |
| **P1** | DES-05～07、DES-09（本 AS 单页扩展） |

### 1.5 领域清单

| 聚合 | DM |
|------|-----|
| Observation | [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) |
| ObservationDefinition | [DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md) |
| PeisServiceRequestExt | 04-FHIR §三 |

### 1.6 业务规则（BR-*）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-DES-01** | 仅本科室权限内 Encounter/SR 可见 | DES-01 |
| **BR-DES-02** | 手工录入写 `cln_observation`；禁止无 source 双写 LIS 定量值 | DES-02 |
| **BR-DES-03** | 同一 SR + definitionId 幂等更新 | DES-02 |
| **BR-DES-04** | 常见结果套用仅改 value/interpretation | DES-03 |
| **BR-DES-05** | 分科诊断写 **cln_observation**（OD.category=dept_conclusion），引用 ConditionDefinition 模板 | DES-04 |
| **BR-DES-06** | DES-08 投影失败保留 ext 标志可重试 | DES-08 |
| **BR-DES-07** | `final` 后改值走 amended 或权限（院点） | DES-02 |
| **BR-DES-08** | 弃检/延检须原因 + 更新 ext.exec_status | DES-05 |

---

## 2. 页面与接口总览

| IF | 用户动作 | 语义 | 读写 | 关键规则 | 状态变化 |
|----|----------|------|:--:|----------|----------|
| **IF-PEIS-DES-01** | 待检列表 | 本科室队列 | 读 | BR-DES-01 | — |
| **IF-PEIS-DES-02** | 保存结果 | 小项录入 | 写 | BR-DES-02/03/07 | CLN preliminary/final |
| **IF-PEIS-DES-03** | 套用常见结果 | 模板填充 | 写 | BR-DES-04 | 同 DES-02 |
| **IF-PEIS-DES-04** | 保存诊断 | 分科结论 | 写 | BR-DES-05 | — |
| **IF-PEIS-DES-08** | 拉取 LIS/PACS | 结果投影 | 写 | BR-DES-06 | ext.resultReceived |
| **IF-PEIS-DES-05** | 弃检/延检/清空 | SR ext 状态 | 写 | BR-DES-08 | exec abandoned/deferred |
| **IF-PEIS-DES-06** | 影像附件 | Binary/DocumentReference | 写 | — | P1 院点可选 |
| **IF-PEIS-DES-07** | 历史对比 | 同学科历史 Encounter | 读 | — | 只读 CLN |
| **IF-PEIS-DES-09** | 检查人员列表 | 分科工作量查询 | 读 | BR-DES-01 | 报表向 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|--------|----------|---------|--------|------|
| GAP-PEIS-DES-01 | DM 能力 | [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) §6.3 | `LisResultProjectionService#projectFromLis` | 领域实现 + 单测 | DES-08 | P0 | 待建 |

> 与 [AS-PEIS-INT-001](./AS-PEIS-INT-001-LIS-PACS外发.md) GAP-PEIS-INT-01 **同一关闭条件**。

---

## 4. 接口编排说明书

### 4.1 IF-PEIS-DES-01

| 维度 | 说明 |
|------|------|
| **触发** | 打开分科诊台 / 刷新 |
| **主路径** | 按科室+日期查 PE Encounter → 过滤待检 SR → 展示项目状态（含外发标志） |
| **查询** | `PeisDeptDeskQueryService#searchPendingByDept` |

### 4.2 IF-PEIS-DES-02

| 维度 | 说明 |
|------|------|
| **主路径** | 加载 SR 下 observation 列表 → 校验 → `ObservationDomainService#create/update` |
| **失败** | `OBS_DEFINITION_INACTIVE`、`SR_NOT_IN_DEPT` |

### 4.3 IF-PEIS-DES-08（与 [04 §5.2](../PeisAppService/04-FHIR领域模型.md#52-des-08--int-01-时序p0) 一致）

| 步骤 | 说明 |
|------|------|
| 1 | 校验 SR 已外发（ext dispatch=sent） |
| 2 | PROOF → `LisResultProjectionService`；TEST → PACS 摘要 + DR index |
| 3 | upsert `cln_observation` + source 引用 |
| 4 | 更新 ext `result_received_enum` |

### 4.4 IF-PEIS-DES-05 弃检/延检/清空（P1）

| 步骤 | 说明 |
|------|------|
| 1 | 校验本科室 SR + 非 final 总检 |
| 2 | `PeisServiceRequestExtDomainService#updateExecStatus` → abandoned/deferred/pending |
| 3 | 可选清空该 SR 下 preliminary CLN（院点策略） |
| 4 | `PeisEncounterProgressQuery#signalProgressChanged` |

### 4.5 IF-PEIS-DES-07 历史对比（P1）

| 步骤 | 说明 |
|------|------|
| 1 | 同 patientId + 同学科/同 OD 集合查历史 Encounter |
| 2 | `ObservationQueryService#listByEncounterId` 并排展示 |

---

## 5. 业务异常与用户提示

| 异常码 | 场景 | 提示语义 | 可重试 |
|--------|------|----------|:--:|
| `SR_NOT_DISPATCHED` | DES-08 未外发 | 请先外发检验/检查 | 否 |
| `PROJECTION_SOURCE_MISSING` | LIS 结果无 source 链 | 结果尚未回传 | 是 |
| `DEPT_ACCESS_DENIED` | 非本科室 SR | 无权限操作该项目 | 否 |

---

## 6. 事件语义与消费方

| 事件 | 触发 | 消费方 |
|------|------|--------|
| `ObservationFinalizedEvent` | DES-02 final | MAS-001 待总检刷新（P1 订阅） |
| `PeisResultProjectedEvent` | DES-08 成功 | 诊台 UI 刷新 |

---

## 7. 非功能性需求

| 维度 | 承诺 |
|------|------|
| **并发** | 同一 SR 录入乐观锁（version） |
| **审计** | DES-02/04/08 记 AuditLog |

---

## 8. 测试要点（业务视角）

| 场景 | 要点 |
|------|------|
| 录入 | 手工 CLN 无 source；保存后 MAS 可读 |
| 投影 | LIS final → DES-08 → CLN 带 source_observation_id |
| 幂等 | 同 SR+definition 重复保存 → 更新一行 |
| 权限 | 他科室 SR 不可见 |

---

## 9. 关联文档与变更历史

- INT：[AS-PEIS-INT-001](./AS-PEIS-INT-001-LIS-PACS外发.md)
- DM：[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)
- 03：[PeisAppService/03](../PeisAppService/03-AppService详细设计.md) §七

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-06-27 | P1：DES-05/06/07/09 IF 与编排 |
| v1.1 | 2026-06-27 | 对齐 DM-CLN-005：`ObservationDomainService` 无 Cln 前缀 |
| v1.0 | 2026-06-27 | 首版 P0 DES-01～04、DES-08 |
