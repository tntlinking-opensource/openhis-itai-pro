# 体检域 · 诊断与表达式 · 应用服务设计（AS）

> **编码**：AS-PEIS-CFG-002 · **版本**：v1.2 · **日期**：2026-06-27  
> **菜单**：诊断与表达式 · **类名**：`PeisDiagnosisConfigAppService` · **路由**：`/peis/config/diagnosis`

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 诊断建议库、常见结果（P0）；自动诊断/危急值 **表达式规则**（P1） |
| **在外** | ConditionDefinition（health）；诊台写 `cln_observation`；CRT 诊台消费 CFG-12 |
| **涉及域** | ConditionDefinition、`peis_common_result`、`peis_auto_express` |

### 1.2 角色

| 角色 | 动作 |
|------|------|
| **管理员** | 维护诊断模板、常见结果、表达式/危急值规则 |
| **分科/总检** | 消费 CFG-08/09（DES）；规则命中 → CRT/MAS |

### 1.3 术语

| 术语 | 口径 |
|------|------|
| **CFG-08** | 诊断建议库 → ConditionDefinition（`sourceEnum=peis_dx`，与院内诊断隔离） |
| **CFG-09** | 常见结果 → `peis_common_result` |
| **CFG-12 / CRT-01** | 危急值/表达式规则 → `peis_auto_express` |
| **CFG-10/11** | 总检自动诊断、快速条件（P1，同 DM-PEIS-006） |

### 1.4 范围

| P0 | CFG-08、CFG-09 |
| P1 | CFG-10～12（**CRT-01** = IF-PEIS-CFG-12） |
| 非目标 | 诊台写结论（DES-001）；DetectedIssue 实例（CRT-001） |

### 1.5 领域清单

| 聚合 | DM |
|------|-----|
| ConditionDefinition | DM-CLN-002 |
| PeisCommonResult | DM-PEIS-003 |
| PeisAutoExpress | DM-PEIS-006 |

### 1.6 业务规则（BR）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-CFG2-01** | 诊断模板 → ConditionDefinition.`peis_dx`（不进医生站候选） | CFG-08 |
| **BR-CFG2-02** | 常见结果 → `peis_common_result` | CFG-09 |
| **BR-CFG2-03** | DES-04 可选引用模板 definitionId | CFG-08 |
| **BR-CFG2-04** | CFG-12 规则 → DM-PEIS-006；CRT/MAS 消费 | CFG-12 |
| **BR-CFG2-05** | 停用规则前检查在检引用（院点） | CFG-10/12 |

---

## 2. 页面与接口总览（契约概览）

| IF | Tab/动作 | 语义 | 读写 | 关键规则 |
|----|----------|------|:--:|----------|
| **IF-PEIS-CFG-08** | 诊断库 | CRUD | 读/写 | BR-CFG2-01/03 |
| **IF-PEIS-CFG-09** | 常见结果 | CRUD | 读/写 | BR-CFG2-02 |
| **IF-PEIS-CFG-10** | 诊断表达式 | CRUD | 读/写 | MAS-06 |
| **IF-PEIS-CFG-11** | 快速诊断条件 | CRUD | 读/写 | DES |
| **IF-PEIS-CFG-12** | 危急值规则（**CRT-01**） | CRUD | 读/写 | BR-CFG2-04 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
|--------|----------|-------------|-------------------|----------|---------|--------|------|
| GAP-PEIS-CFG2-01 | **DM 能力** | [DM-PEIS-006](./DM-PEIS-006-PeisAutoExpress-自动诊断表达式.md) §5 | `PeisAutoExpressDomainService` CRUD + `PeisAutoExpressEvaluationService#evaluate` | DM+代码 | CFG-10/12、MAS-06 | P1 | 待建 |
| GAP-PEIS-CFG2-02 | **DM 能力** | [DM-PEIS-003](./DM-PEIS-003-PeisCommonResult-常见结果.md) §5 | `PeisCommonResultDomainService` / QueryService | DM+代码 | CFG-09 | P0 | ✅ 已关闭（领域 + PeisDiagnosisConfigAppService，2026-07-19） |

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-PEIS-CFG-08 诊断建议库

| 步骤 | 说明 |
|------|------|
| 1 | search：ConditionDefinitionQueryService 分页（固定 `sourceEnum=peis_dx`） |
| 2 | save：校验 code 唯一；写 ConditionDefinition（强制 `peis_dx`） |
| 3 | 停用前在检引用检查 |

### 4.2 IF-PEIS-CFG-09 常见结果

| 步骤 | 说明 |
|------|------|
| 1 | 绑定可选 `observation_definition_id`、科室 |
| 2 | save → `peis_common_result` |
| 3 | DES-03 诊台套用 valueString |

### 4.3 IF-PEIS-CFG-12 危急值规则（CRT-01）

| 步骤 | 说明 |
|------|------|
| 1 | 维护 name、express_script、target_condition_def_id |
| 2 | save → `peis_auto_express`（status active/inactive） |
| 3 | [AS-PEIS-CRT-001](./AS-PEIS-CRT-001-危急值处理.md) 诊台/自动建 Issue 消费 |

### 4.4 IF-PEIS-CFG-10/11（P1，同表 peis_auto_express）

表达式类型区分（rule_type 扩展列或 configKey）；MAS-06 求值 encounterId。

---

## 5. 业务异常与用户提示

| 码 | 场景 | 提示 |
|----|------|------|
| `TEMPLATE_IN_USE` | 模板被在检引用 | 无法停用 |
| `RULE_SCRIPT_INVALID` | 表达式语法错误 | 请检查表达式 |
| `CONDITION_DEF_NOT_FOUND` | 目标诊断不存在 | 无效诊断引用 |

---

## 6. 事件语义与消费方

| 事件 | 消费方 |
|------|--------|
| `PeisCatalogChangedEvent`（P2） | DES 模板缓存失效 |
| 规则 save | CRT 规则引擎热加载（P2） |

---

## 7. 非功能性需求

目录/规则变更 AuditLog；表达式 save 须沙箱校验（P1）。

---

## 8. 测试要点（业务视角）

- DES-03 套用 CFG-09 常见结果
- DES-04 选 CFG-08 诊断模板
- CFG-12 规则命中 → CRT 列表可见 Issue（联调 CRT-001）
- MAS-06 表达式求值（CFG-10）

---

## 9. 关联文档与变更历史

- SDD：[AS-PEIS-CFG-002-SDD](../AppService-SDD/AS-PEIS-CFG-002-诊断与表达式-SDD.md)
- CRT：[AS-PEIS-CRT-001](./AS-PEIS-CRT-001-危急值处理.md)（CFG-12 = CRT-01）
- DM：[DM-PEIS-003](./DM-PEIS-003-PeisCommonResult-常见结果.md)、[DM-PEIS-006](./DM-PEIS-006-PeisAutoExpress-自动诊断表达式.md)

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-06-27 | 全 IF §4；双 Gap；9 章齐 |
| v1.1 | 2026-06-27 | CFG-12/CRT-01 链 |
| v1.0 | 2026-06-27 | 首版骨架 |
