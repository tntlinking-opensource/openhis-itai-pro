# 检验域 · 采样工作台 · 应用服务设计（AS）

> **文档职责**：说明采样工作台的**接口契约（IF）**、业务规则（BR）、编排意图。  
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径。  
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；伪代码。  
> **页面边界**：Tab——待采样列表 | 登记列表(P1) | 拒收列表(P1) | 本地开单(P2)  
> **编码**：AS-LIS-SMP-001 · **版本**：v2.0 · **日期**：2026-06-22

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 待采样列表（按科室/日期/患者）；条码生成/绑定；采样签收；拒收/重采；采样退单；登记列表 |
| **在外** | 仪器结果录入 → REP-001；审核 → AUD-001 |
| **涉及域** | LisWorkItem、LisWorkItemOrder |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
|------|----------|
| **采血护士** | 刷新待采样列表 → 扫码/手工输条码 → 签收采样 → 不合格拒收/重采 |
| **系统** | 按科室+日期过滤待采样工单；条码按 lis_config 规则生成 |

### 1.3 术语与口径约定

| 术语 | 口径 |
|------|------|
| **待采样** | `statusEnum = available` 的 LisWorkItem |
| **签收** | `available → collected`（BR-SMP-01 前置校验状态） |
| **拒收** | `available → unsatisfactactory`；重采新建 LisWorkItem（不复用原 ID） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期** | IF-SMP-01~07：待采样查询、条码生成、签收（单/批）、拒收、重采、退单、详情 |
| **P2 暂缓** | 本地开单（SMP-004） |

### 1.5 领域清单

| 聚合 | 职责 | DM 路径 |
|------|------|---------|
| **LisWorkItem** | 标本工单、条码、状态迁移 | `docs/领域/缺口/文档无代码/DM-LIS-001-LisWorkItem-检验工单.md |

### 1.6 业务规则（BR-*）

| 编号 | 规则 | 触发 IF |
|------|------|---------|
| **BR-SMP-01** | 签收前 statusEnum = available | SMP-03 |
| **BR-SMP-02** | 条码规则 → lis_config.barcode.* | SMP-02 |
| **BR-SMP-03** | 拒收 → unsatisfactactory；recollect 新建工单 | SMP-05 |
| **BR-SMP-04** | 签收后不可编辑 orderLinks | SMP-03 |
| **BR-SMP-05** | 未收费允许采样 → lis_config.sampling.allowWithoutCharge | SMP-03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
|---------|----------|----------|:--:|----------|-------------|----------|----------|
| **IF-SMP-01** | 查看待采样列表 | 分页查询 | 读 | — | — | LisWorkItem | LisWorkItemQueryService |
| **IF-SMP-02** | 生成/绑定条码 | 条码生成 | 写 | BR-SMP-02 | barcode 赋值 | LisWorkItem | — |
| **IF-SMP-03** | 采样签收 | 单条签收 | 写 | BR-SMP-01/04/05 | available→collected | LisWorkItem | — |
| **IF-SMP-03B** | 批量签收 | 批量 | 写 | 同上 | 批量 available→collected | LisWorkItem | — |
| **IF-SMP-05** | 拒收 | 标记不合格 | 写 | BR-SMP-03 | available→unsatisfactactory | LisWorkItem | — |
| **IF-SMP-06** | 重采 | 新建工单 | 写 | BR-SMP-03 | 新建 LisWorkItem | LisWorkItem | — |
| **IF-SMP-07** | 采样退单 | 退回 | 写 | — | collected→entered-in-error | LisWorkItem | — |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**。

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-SMP-03：采样签收

| 维度 | 说明 |
|------|------|
| **触发前提** | 护士扫码/点选工单，status=available |
| **主路径** | ① 校验 statusEnum = available → ② 校验未收费允许（lis_config） → ③ 执行采样签收 → ④ statusEnum→collected + collectionTime |
| **失败分支** | 状态不符 → "标本状态不允许签收"；未收费不允许 → "请先完成收费" |

### 4.2 IF-SMP-05：拒收/重采

| 维度 | 说明 |
|------|------|
| **主路径** | ① 校验 statusEnum = available → ② statusEnum→unsatisfactactory → ③ 若重采 → 新建 LisWorkItem（新 barcode，复用 patientId/encounterId/orderLinks） |

---

## 5. 业务异常与用户提示

| 异常码 | 场景 | 用户提示语义 |
|--------|------|-------------|
| `STATUS_INVALID` | 状态不允许签收 | "标本状态不允许签收" |
| `CHARGE_NOT_ALLOWED` | 未收费不允许采样 | "请先完成收费" |
| `WORKITEM_NOT_FOUND` | 工单不存在 | "未找到对应检验工单" |

---

## 6. 事件语义与消费方

| 事件 | 触发点 | 消费方 |
|------|--------|--------|
| `LisWorkItemCollectedEvent` | SMP-03 签收 | TAT 监控 |

---

## 7. 非功能性需求

| 维度 | 承诺 |
|------|------|
| 批量签收上限 | `lis_config.tat.batchSize` |
| 并发 | 同工单并发签收 → 乐观锁 |

---

## 8. 测试要点（业务视角）

| 场景 | 测试要点 |
|------|----------|
| 正向 | 签收 available 工单 → status=collected |
| 异常 | 签收 collected 工单 → 拒绝 |
| 拒收 | available→unsatisfactactory；重采→新工单 |

---

## 9. 关联文档与变更历史

### 关联文档
- DM：`DM-LIS-001-LisWorkItem-检验工单.md`、`DM-LIS-008-LisConfig-模块参数.md`
- 概要设计：`03-AppService详细设计.md` §二

### 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.0 | 2026-06-22 | 按 9 章骨架重写；删类名+字段明细 |
| v1.0 | 2026-06-22 | 首版 |
