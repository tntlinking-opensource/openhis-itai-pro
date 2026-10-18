# 检验域 · 报告管理 · 应用服务设计（AS）

> **文档职责**：说明报告管理模块的**接口契约（IF）**、业务规则（BR）。  
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；伪代码。  
> **页面边界**：报告预览/打印 | 报告明细 | 手工录入 | 批量打印 | 召回修订(P2)  
> **子模块**：REP-REVISE 报告召回与修订(P2)  
> **编码**：AS-LIS-REP-001 · **版本**：v2.0 · **日期**：2026-06-22

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 报告列表/预览/打印；报告结构化明细；手工录入/修改结果；报告删除；批量打印；召回修订(P2) |
| **涉及域** | DiagnosticReport、Observation、LisWorkItem |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
|------|----------|
| **检验师** | 查看报告列表 → 预览/打印 → 手工补齐结果 → 发布报告 |
| **系统** | PDF 走 PrintTemplate（AS-BAS-PRT）；HTML 由 client 渲染；召回修订保留版本链 |

### 1.3 术语与口径约定

| 术语 | 口径 |
|------|------|
| **手工录入** | 仅允许 Observation = preliminary |
| **召回** | final→amended；原版本保留为 previous |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期（P0）** | IF-REP-01~09：预览、明细、手工录入、修改、删除、打印、发布、列表 |
| **P2** | REP-REVISE：召回、修订历史、重新发布 |

### 1.5 领域清单

| 聚合 | 职责 | DM 路径 |
|------|------|---------|
| **DiagnosticReport** | 报告聚合 | `docs/领域/缺口/文档无代码/DM-LIS-003-DiagnosticReport-检验报告.md |
| **Observation** | 结果值 | `docs/领域/缺口/文档无代码/DM-LIS-002-Observation-检验结果.md |

### 1.6 业务规则（BR-*）

| 编号 | 规则 | 触发 IF |
|------|------|---------|
| **BR-REP-01** | PDF 走 PrintTemplate（AS-BAS-PRT）；HTML 由 client 渲染 | REP-01 |
| **BR-REP-02** | 手工录入仅允许 Observation.preliminary | REP-03 |
| **BR-REP-05** | 召回仅 status=final→amended；原版本保留 | REVISE-01 |
| **BR-REP-06** | 修订后走完整审核流程 | REVISE-03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
|---------|----------|----------|:--:|----------|-------------|----------|----------|
| **IF-REP-01** | 报告预览 | PDF/HTML | 读 | BR-REP-01 | — | DiagnosticReport | — |
| **IF-REP-02** | 报告明细 | 结构化数据 | 读 | — | — | DiagnosticReport, Observation | ObservationQueryService |
| **IF-REP-03** | 手工录入 | 修改结果 | 写 | BR-REP-02 | Observation 值更新 | Observation | — |
| **IF-REP-06** | 批量打印 | 批量 PDF | 写 | — | — | DiagnosticReport | — |
| **IF-REP-09** | 发布报告 | finalize | 写 | BR-REP-05 | status→final | DiagnosticReport | — |
| **IF-REP-SEARCH** | 报告列表 | 分页 | 读 | — | — | DiagnosticReport | — |

### 2.1 REP-REVISE（P2）

| IF 编号 | 用户动作 | 接口语义 | 读写 | 关键规则 |
|---------|----------|----------|:--:|----------|
| **IF-REVISE-01** | 召回报告 | final→amended | 写 | BR-REP-05 |
| **IF-REVISE-02** | 修订历史 | 版本链 | 读 | — |
| **IF-REVISE-03** | 重新发布 | amended→final | 写 | BR-REP-06 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**。

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-REP-09：发布报告

| 维度 | 说明 |
|------|------|
| **触发前提** | 该 serviceRequestId 下所有 Observation 为 final/cancelled |
| **主路径** | ① 校验所有 Observation 状态 → ② 生成 DiagnosticReport → ③ 回写 health 报告索引 |
| **失败分支** | 存在 Observation 未审核 → "还有结果未审核完成" |

---

## 5. 业务异常与用户提示

| 异常码 | 场景 | 用户提示语义 |
|--------|------|-------------|
| `REPORT_PENDING_OBS` | 存在未审核结果 | "还有结果未审核完成" |
| `REPORT_STATUS_INVALID` | 重复发布 | "报告已发布，不可重复操作" |

---

## 6. 事件语义与消费方

| 事件 | 触发点 | 消费方 |
|------|--------|--------|
| `DiagnosticReportFinalizedEvent` | REP-09 | health 报告索引 |

---

## 7. 非功能性需求

| 维度 | 承诺 |
|------|------|
| PDF 生成 | 由 PrintTemplate（AS-BAS-PRT） 异步生成 |

---

## 8. 测试要点（业务视角）

| 场景 | 测试要点 |
|------|----------|
| 正向 | 所有 Obs 为 final → 发布报告 |
| 异常 | 存在初步 Obs → 拒绝发布 |

---

## 9. 关联文档与变更历史

### 关联文档
- DM：`DM-LIS-003-DiagnosticReport-检验报告.md`、`DM-LIS-002-Observation-检验结果.md`
- 概要设计：`03-AppService详细设计.md` §二

### 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.0 | 2026-06-22 | 按 9 章骨架重写 |
| v1.0 | 2026-06-22 | 首版 |
