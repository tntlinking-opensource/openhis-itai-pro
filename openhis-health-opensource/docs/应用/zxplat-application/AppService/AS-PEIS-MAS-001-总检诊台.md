# 体检域 · 总检诊台 · 应用服务设计（AS）

> **编码**：AS-PEIS-MAS-001 · **版本**：v1.1 · **日期**：2026-06-27  
> **菜单**：总检诊台 · **类名**：`PeisMasterReviewDeskAppService` · **路由**：`/peis/master-review`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 待总检列表、汇总分科结果、综述录入、总检完成 |
| **在外** | 报告打印 REP-001；DR index |
| **涉及域** | DiagnosticReport、`cln_observation`、Encounter(PE) |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **总检医生** | 汇总、写结论、审核完成 |
| **系统** | 进度聚合 `PeisEncounterProgressQuery` |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **进度** | **禁止** Encounter 专用 status；见 [04 §3.2](../PeisAppService/04-FHIR领域模型.md#32-体检进度多模型联合不扩展-encounterstatus_enum) |
| **主报告** | 1 Encounter 1 份 DR |

### 1.4 范围

| P0 | MAS-01～04 |
| P1 | MAS-05～07、MAS-06 表达式 |

### 1.6 BR

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-MAS-01** | 待总检：分科 SR ext 均 checked 或 abandoned（院点策略） | MAS-01 |
| **BR-MAS-02** | 汇总读 CLN + 分科结论 | MAS-02 |
| **BR-MAS-03** | 综述写 DR preliminary | MAS-03 |
| **BR-MAS-04** | 完成 → DR `final` + upsert DR index | MAS-04 |
| **BR-MAS-05** | 完成后 Encounter → `finished`（通用） | MAS-04 |
| **BR-MAS-06** | 退回分科须原因 + 解锁 SR | MAS-05 P1 |

---

## 2. 接口总览

| IF | 动作 | 语义 | 读写 | 关键规则 |
|----|------|------|:--:|----------|
| **IF-PEIS-MAS-01** | 待总检列表 | 进度聚合 | 读 | BR-MAS-01 |
| **IF-PEIS-MAS-02** | 汇总结果 | 聚合 CLN/分科 | 读 | BR-MAS-02 |
| **IF-PEIS-MAS-03** | 保存综述 | 写 DR | 写 | BR-MAS-03 |
| **IF-PEIS-MAS-04** | 总检完成 | final + index | 写 | BR-MAS-04/05 |
| **IF-PEIS-MAS-05** | 退回分科 | 解锁 SR + 原因 | 写 | BR-MAS-06 |
| **IF-PEIS-MAS-06** | 自动诊断 | 表达式求值 | 写 | DM-PEIS-006 |
| **IF-PEIS-MAS-07** | 总检人员列表 | 工作量查询 | 读 | — |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|--------|----------|---------|--------|------|
| GAP-PEIS-MAS-01 | QueryService 能力 | [DM-PEIS-008](./DM-PEIS-008-PeisEncounterProgressQuery-体检进度查询.md) §6.2 | `PeisEncounterProgressQuery` 领域实现 | DM §6.2 + 代码单测 | MAS-01 | P0 | ✅ 已关（2026-07-21） |

---

## 4. 编排说明书

### 4.1 IF-PEIS-MAS-01 待总检列表

| 维度 | 说明 |
|------|------|
| **触发** | 打开总检诊台 / 刷新 |
| **主路径** | `PeisEncounterProgressQuery#searchPendingMasterReview` 分页 |
| **规则** | BR-MAS-01：分科 SR 均 checked/abandoned |

### 4.2 IF-PEIS-MAS-02 汇总结果

| 步骤 | 说明 |
|------|------|
| 1 | 读 Encounter + ext |
| 2 | `ObservationQueryService#listByEncounterId`（含 dept_conclusion） |
| 3 | 按科室/项目 SR 分组展示 |

### 4.3 IF-PEIS-MAS-03 保存综述

| 步骤 | 说明 |
|------|------|
| 1 | 校验 DR 非 final |
| 2 | 写 DR 正文 preliminary |
| 3 | 可多次保存草稿 |

### 4.4 IF-PEIS-MAS-04 总检完成

| 步骤 | 说明 |
|------|------|
| 1 | Pre：分科 SR ext 均 checked/abandoned（BR-MAS-01） |
| 2 | **`DiagnosticReportDomainService#finalizeMaster`** → `cln_diagnostic_report.status=final` |
| 3 | **`DiagnosticReportIndexUpsertService#upsertFromPeis`**：`sourceReportId=cln_dr.id`，`moduleEnum=PEIS`，`uniqueKey=PEIS:{id}` |
| 4 | `EncounterDomainService#finishEncounter` → 通用 finished |
| 5 | `PeisEncounterProgressQuery#signalProgressChanged`（可选） |
| 6 | `PeisMasterReviewCompletedEvent` → REP-01 可打印 |

**写库顺序（同事务）**：`cln_diagnostic_report → `cln_diagnostic_report_index` → `adm_encounter`。真源 [DM-CLN-008](./DM-CLN-008-DiagnosticReport-临床总检报告.md) §8。

### 4.5 IF-PEIS-MAS-05 退回分科（P1）

| 步骤 | 说明 |
|------|------|
| 1 | 校验 DR 非 final 或院点允许反退 |
| 2 | 选定科室/SR → ext 回 pending |
| 3 | 记录退回原因 AuditLog |
| 4 | `signalProgressChanged` |

### 4.6 IF-PEIS-MAS-06 自动诊断（P1）

| 步骤 | 说明 |
|------|------|
| 1 | `PeisAutoExpressQueryService#listActiveByRuleType(master_review)` |
| 2 | `PeisAutoExpressEvaluationService#evaluate(encounterId)` |
| 3 | 命中 → 写 DR 建议段或 Condition 引用（院点） |

---

## 5. 业务异常

| 码 | 场景 |
|----|------|
| `DEPT_NOT_COMPLETE` | 分科未完成 |
| `MASTER_ALREADY_FINAL` | 已总检 |

---

## 6. 事件

`PeisMasterReviewCompletedEvent` → REP-001 可打印。

---

## 7. NFR

MAS-02 聚合 P95 < 3s（Encounter 级）。

---

## 8. 测试

- 分科未完成不可 MAS-04
- final 后 DR index module=PEIS/HEALTH
- Encounter finished 通用态

---

## 9. 关联文档

- DES：[AS-PEIS-DES-001](./AS-PEIS-DES-001-分科诊台.md)
- REP：[AS-PEIS-REP-001](./AS-PEIS-REP-001-报告打印.md)
- DM：[DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | P1：MAS-05～07 |
| v1.0 | 2026-06-27 | 首版 P0 总检 |
