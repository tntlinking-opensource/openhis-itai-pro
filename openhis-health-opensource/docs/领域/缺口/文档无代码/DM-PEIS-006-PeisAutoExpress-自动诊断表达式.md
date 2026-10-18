# DM-PEIS-006 PeisAutoExpress · 自动诊断表达式

> **表名**：`peis_auto_express`  
> **版本**：v1.2 · **日期**：2026-06-27

---

## 1. 概述

总检/危急值 **表达式规则** 配置与求值；CFG-002 + MAS-06 + CRT 消费。

---

## 2. 持久化形态

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | PK |
| name | String | 规则名 |
| express_script | Text | 表达式（院点引擎） |
| target_condition_def_id | Long | 命中后 ConditionDefinition |
| rule_type_code | String | master_review / critical 等 |
| status_enum | String | active / inactive |
| tenant_id / org_id | Long | 租户/院区 |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `PeisAutoExpress` |
| **标识** | `id` |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| id | Long | 系统 | PK |
| name | String | 是 | 规则名 |
| expressScript | String | 是 | 脚本 |
| targetConditionDefId | Long | 否 | 命中模板 |
| ruleTypeCode | String | 是 | 规则类型 |
| statusEnum | String | 是 | active/inactive |
| tenantId | Long | 是 | 租户 |
| orgId | Long | 否 | 院区 |

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `PeisAutoExpressSavedEvent` | `id, ruleTypeCode, statusEnum` | `save` |
| `PeisAutoExpressStatusChangedEvent` | `id, statusEnum` | `activate` / `deactivate` |

包路径：`com.zxplat.domain.share.events.custom.peisautoexpress`

---

## 6. 领域服务

### 6.1 写侧 — `PeisAutoExpressDomainService`

| 方法 | 说明 | IF |
|------|------|-----|
| `save(SaveAutoExpressCommand)` | 新建/更新规则 | CFG-10/11/12 |
| `activate(ActivateAutoExpressCommand)` | 启用 | CFG-12 |
| `deactivate(DeactivateAutoExpressCommand)` | 停用 | CFG-12 |

### 6.2 读侧 — `PeisAutoExpressQueryService`

| 接口签名 | 场景 |
|----------|------|
| `search(AutoExpressQuery, Pageable): Page<AutoExpressModel>` | CFG-10/12 列表 |
| `listActiveByRuleType(ruleTypeCode): List<AutoExpressModel>` | CRT/MAS 求值前加载 |

### 6.3 求值 — `PeisAutoExpressEvaluationService`（无持久化写）

| 方法 | 说明 |
|------|------|
| `evaluate(EvaluateAutoExpressCommand)` | MAS-06 总检自动诊断 |
| `matchCriticalRules(MatchCriticalRulesCommand)` | CRT 可选自动建 Issue |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-AEX-01** | express_script 保存前须语法校验 |
| **BR-AEX-02** | inactive 规则不参与求值 |
| **BR-AEX-03** | 求值只读 CLN/LIS 投影，不写 WorkItem |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-06-27 | 补 §3/§5；activate/deactivate 独立命令 |
| v1.1 | 2026-06-27 | §5 领域服务 |
| v1.0 | 2026-06-27 | 首版 |
