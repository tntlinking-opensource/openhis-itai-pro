# 体检域 · 报告发放 · 应用服务设计（AS）

> **编码**：AS-PEIS-REP-002 · **版本**：v1.1 · **日期**：2026-06-27  
> **菜单**：报告发放 · **类名**：`PeisReportGrantAppService` · **路由**：`/peis/report/grant`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 发放登记、签收、团体批量打印；邮寄状态（P3） |
| **在外** | 报告渲染 → **PrintTemplate（AS-BAS-PRT）** Client；REP-001 负责单份预览/打印 |
| **涉及域** | `peis_report_grant_ext`、DiagnosticReport、`cln_diagnostic_report_index` |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **报告员** | 登记发放、签收 |
| **团检前台** | 团体批量打印（cliPrint） |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **可发放** | DR.status = **final**（BR-REP2-01） |
| **发放扩展** | `peis_report_grant_ext` 1:1 DR（[DM-PEIS-005](./DM-PEIS-005-PeisReportGrantExt-报告发放扩展.md)） |
| **团打** | 按 Encounter 批量 markPrinted（BR-REP2-02） |

### 1.4 范围

| P1 | REP-05、REP-06 |
| P3 | REP-07 邮寄 |
| 非目标 | 报告内容编辑（MAS）；单份打印 UI（REP-001） |

### 1.5 领域清单

| 聚合 | 职责 | DM |
|------|------|-----|
| PeisReportGrantExt | 发放/签收/打印态 | DM-PEIS-005 |
| DiagnosticReport | final 校验 | DM-CLN-006 |

### 1.6 业务规则（BR）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-REP2-01** | 仅 **final** DR 可发放登记 | REP-05 |
| **BR-REP2-02** | 团检 cliPrint 按 Encounter 批量 | REP-06 |
| **BR-REP2-03** | 签收后 grant_status → signed | REP-05 |
| **BR-REP2-04** | 打印计数 markPrinted 幂等 | REP-06 |

---

## 2. 页面与接口总览（契约概览）

| IF | 动作 | 语义 | 读写 | 关键规则 | 状态变化 | 涉及域 | 查询服务 |
|----|------|------|:--:|----------|----------|----------|----------|
| **IF-PEIS-REP-05** | 发放/签收 | 待发放列表、登记、签收 | 读/写 | BR-REP2-01/03 | pending→signed | grant ext | PeisReportGrantQueryService |
| **IF-PEIS-REP-06** | 团体打印 | 批次 Encounter 批量打印 | 写 | BR-REP2-02/04 | printed | grant ext | PeisEncounterProgressQuery |
| **IF-PEIS-REP-07** | 邮寄 | 邮寄状态（可选） | 写 | P3 | mailed | grant ext | — |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| GAP-PEIS-REP2-01 | **DM 能力** | [DM-PEIS-005](./DM-PEIS-005-PeisReportGrantExt-报告发放扩展.md) §5 | `PeisReportGrantDomainService` 三方法及 Repository | DM §5 与领域代码一致 | REP-05/06 | P1 | **已关闭** |

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-PEIS-REP-05 发放登记与签收

**searchPendingGrant**

| 步骤 | 说明 |
|------|------|
| 1 | QueryService 分页 final DR 且未 signed |
| 2 | 组装 GrantRowDto |

**registerGrant / signGrant**

| 步骤 | 说明 |
|------|------|
| 1 | Pre：DR final（BR-REP2-01） |
| 2 | DomainService registerGrant 或 signGrant |
| 3 | 写 peis_report_grant_ext |

### 4.2 IF-PEIS-REP-06 团体批量打印

| 步骤 | 说明 |
|------|------|
| 1 | 按 groupBatchId 或 encounterId 列表查可打印 DR |
| 2 | ReportRenderPort 批量渲染（可选） |
| 3 | markPrinted 幂等更新 ext |

### 4.3 IF-PEIS-REP-07 邮寄（P3）

更新 grant_status=mailed；非 P1 范围。

---

## 5. 业务异常与用户提示

| 码 | 场景 | 提示 |
|----|------|------|
| `REPORT_NOT_FINAL` | 非 final | 报告未总检完成 |
| `ALREADY_SIGNED` | 重复签收 | 已签收 |
| `GRANT_NOT_FOUND` | 无 ext | 请先登记发放 |

---

## 6. 事件语义与消费方

| 事件 | 消费方 |
|------|--------|
| `PeisReportGrantedEvent` | 统计 RPT（P2） |

---

## 7. 非功能性需求

发放/签收 AuditLog；团打批量 ≤100 份/请求（P1）；ReportRenderPort 超时重试（P2）。

---

## 8. 测试要点

- 非 final DR → registerGrant 拒绝
- signGrant → status signed
- 团检 batch print → 全部 markPrinted
- 与 REP-001 预览同一 DR id

---

## 9. 关联文档与变更历史

- SDD：[AS-PEIS-REP-002-SDD](../AppService-SDD/AS-PEIS-REP-002-报告发放-SDD.md)
- REP-001：[AS-PEIS-REP-001](./AS-PEIS-REP-001-报告打印.md)
- DM：[DM-PEIS-005](./DM-PEIS-005-PeisReportGrantExt-报告发放扩展.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 补全 §1～§8；DM-PEIS-005 Gap |
| v1.0 | 2026-06-27 | 首版 P1 骨架 |
