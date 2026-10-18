# 检验域 · 结果审核 · 应用服务设计（AS）

> **文档职责**：说明结果审核模块的**接口契约（IF）**、自动审核规则、分级双签、业务规则（BR）。  
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；伪代码。  
> **页面边界**：待审核列表 | 自动审核规则配置(子Tab) | 分级双签配置(子Tab)  
> **子模块**：AUD-AUTO 自动审核(P1)、AUD-REVIEW 分级双签(P2)  
> **编码**：AS-LIS-AUD-001 · **版本**：v2.1 · **日期**：2026-06-27

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 待审核列表；单条/批量审核；取消审核；发布回写 health（**index + CLN 观察投影**）；按条码快速检索；自动审核规则(P1)；分级双签(P2) |
| **涉及域** | Observation、DiagnosticReport、LisWorkItem、**`cln_observation`**（经 CLN 投影） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
|------|----------|
| **检验师** | 查看待审核列表 → 单条/批量审核 → 取消审核 → 发布报告 |
| **系统** | 自动审核链：参考范围→Delta Check→仪器异常→危急值；任一拦截→人工 |

### 1.3 术语与口径约定

| 术语 | 口径 |
|------|------|
| **待审核** | Observation.statusEnum = preliminary |
| **审核通过** | preliminary → finalized；linkStatusEnum → published |
| **发布** | 全 orderLink 的 Observation 为 final/cancelled → 生成 DiagnosticReport → upsert **报告 index** + **投影 `cln_observation`** |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期（P0）** | IF-AUD-01~07：审核列表、单/批审核、取消、发布、条码检索 |
| **P1** | AUD-AUTO：自动审核规则 CRUD、Delta Check、日志 |
| **P2** | AUD-REVIEW：分级双签 |

### 1.5 领域清单

| 聚合 | 职责 | DM 路径 |
|------|------|---------|
| **Observation** | 结果值、审核状态 | `docs/领域/缺口/文档无代码/DM-LIS-002-Observation-检验结果.md |
| **DiagnosticReport** | 报告聚合与发布 | `docs/领域/缺口/文档无代码/DM-LIS-003-DiagnosticReport-检验报告.md |
| **DiagnosticReportIndex** | 读侧索引 upsert | `docs/领域/DomainModel/DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md |
| **CLN Observation（投影）** | lis → cln 临床事实 | [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) §6.3 |
| **LisWorkItem** | orderLink 业务状态 | `docs/领域/缺口/文档无代码/DM-LIS-001-LisWorkItem-检验工单.md |

### 1.6 业务规则（BR-*）

| 编号 | 规则 | 触发 IF |
|------|------|---------|
| **BR-AUD-01** | 审核前置：Observation = preliminary，linkStatusEnum = result_pending | AUD-02 |
| **BR-AUD-02** | 审核通过 → Observation→finalized；linkStatusEnum→published | AUD-02 |
| **BR-AUD-04** | 发布：全部 orderLink 的 Observation 为 final 或 cancelled → 生成 DiagnosticReport | AUD-05 |
| **BR-AUD-09** | AUD-05 发布：SR 有 `encounterId` 时 **同事务** 调 `LisResultProjectionService#projectFromLis`（BR-CLN-OBS-04）；再 upsert index | AUD-05 |
| **BR-AUD-06** | 自动审核链：参考范围 → Delta Check → 仪器异常 → 危急值；任一拦截 → 人工 | AUD-AUTO |
| **BR-AUD-08** | 双签：level1→reviewed_level1；level2→published | AUD-REVIEW |

---

## 2. 页面与接口总览（契约概览）

### 2.1 AUD 本体

| IF 编号 | 用户动作 | 接口语义 | 读写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
|---------|----------|----------|:--:|----------|-------------|----------|----------|
| **IF-AUD-01** | 查看待审核列表 | 分页查询 | 读 | — | — | Observation, LisWorkItem | ObservationQueryService |
| **IF-AUD-02** | 单条审核 | 审核通过 | 写 | BR-AUD-01/02 | preliminary→finalized | Observation | — |
| **IF-AUD-03** | 批量审核 | 批量通过 | 写 | 同上 | 批量 preliminary→finalized | Observation | — |
| **IF-AUD-04** | 取消审核 | 撤销 | 写 | — | final→preliminary | Observation | — |
| **IF-AUD-05** | 发布回写 | 生成报告 | 写 | BR-AUD-04/09 | 生成 DiagnosticReport; upsert index; **投影 cln_observation** | DiagnosticReport, CLN | — |
| **IF-AUD-07** | 按条码检索 | 快速定位 | 读 | — | — | Observation, LisWorkItem | LisWorkItemQueryService |

### 2.2 AUD-AUTO（P1，子 Tab）

| IF 编号 | 用户动作 | 接口语义 | 读写 | 关键规则 |
|---------|----------|----------|:--:|----------|
| **IF-AUTO-01** | 自动审核规则 CRUD | 配置保存 | 写 | BR-AUD-06 |
| **IF-AUTO-02** | Delta Check | 历史比对 | 读 | — |
| **IF-AUTO-04** | 自动审核日志 | 分页查询 | 读 | — |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 类型 | 真源 | 缺失能力 | 关闭条件 | IF | P | 状态 |
|--------|------|------|----------|----------|-----|---|------|
| GAP-LIS-AUD-01 | **DM 能力** | [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) §6.3 | `LisResultProjectionService#projectFromLis` | 领域实现 + AUD-05 编排单测 | AUD-05 | P0 | 待建 |

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-AUD-02：单条审核

| 维度 | 说明 |
|------|------|
| **触发前提** | 检验师点选 Observation = preliminary 行 |
| **主路径** | ① 校验 Observation.statusEnum = preliminary → ② 运行自动审核规则（参考范围/DeltaCheck/仪器异常/危急值）→ ③ 任一拦截 → 拒绝，提示具体原因 → ④ 全部通过 → 将 Observation 标记为已审核 → ⑤ statusEnum→finalized；linkStatusEnum→published |
| **失败分支** | 状态不符 → "结果已审核，不可重复操作"；自动审核拦截 → "未通过自动审核：{原因}" |

### 4.2 IF-AUD-05：发布回写 health

| 维度 | 说明 |
|------|------|
| **触发前提** | 本 WorkItem 下全部 orderLink 的 LIS Observation 为 final 或 cancelled |
| **主路径** | ① 生成/更新 LIS `DiagnosticReport（final）→ ② `DiagnosticReportIndexUpsertService#upsertFromLis` → ③ 对每个关联 **ServiceRequest**（有 encounterId）：`LisResultProjectionService#projectFromLis` → upsert **`cln_observation`** + `source_observation_id` → ④ 若存在 PEIS ext：dispatch→`resultReceived` |
| **失败分支** | index 或投影失败 → 同事务回滚；可经 PEIS INT-03 补偿刷新 |

---

## 5. 业务异常与用户提示

| 异常码 | 场景 | 用户提示语义 |
|--------|------|-------------|
| `AUD_STATUS_INVALID` | 重复审核 | "结果已审核，不可重复操作" |
| `AUD_RULE_BLOCKED` | 自动审核拦截 | "未通过自动审核：{规则描述}" |
| `OBS_NOT_FOUND` | 结果不存在 | "未找到对应检验结果" |

---

## 6. 事件语义与消费方

| 事件 | 触发点 | 消费方 |
|------|--------|--------|
| `ObservationFinalizedEvent` | AUD-02/03 | REP 报告生成 |
| `DiagnosticReportCreatedEvent` | AUD-05 | health 报告索引；CLN 投影消费方（PEIS 进度/CRT 可选） |
| `ObservationProjectedFromLisEvent` | AUD-05 投影 | PEIS DES/MAS、CRT 规则（可选） |

---

## 7. 非功能性需求

| 维度 | 承诺 |
|------|------|
| 批量审核上限 | `lis_config.audit.batchSize` |
| 发布超时 | health 回写超时 5s，失败则重试 |

---

## 8. 测试要点（业务视角）

| 场景 | 测试要点 |
|------|----------|
| 正向 | 审核通过 preliminary→finalized |
| 正向 | 批量审核 |
| 异常 | 取消审核 → final→preliminary |
| 规则 | 自动审核链逐个触达 |

---

## 9. 关联文档与变更历史

### 关联文档
- DM：`DM-LIS-002-Observation-检验结果.md`、`DM-LIS-003-DiagnosticReport-检验报告.md`
- 概要设计：`03-AppService详细设计.md` §二

### 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.1 | 2026-06-27 | AUD-05：发布时投影 `cln_observation`（主路径）；GAP-LIS-AUD-01 |
| v2.0 | 2026-06-22 | 按 9 章骨架重写 |
| v1.0 | 2026-06-22 | 首版 |
