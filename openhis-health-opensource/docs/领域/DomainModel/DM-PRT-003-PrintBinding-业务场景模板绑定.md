# DM-PRT-003 PrintBinding 业务场景模板绑定

> **限界上下文**：本地扩展 · 画布打印（Print）  
> **代码模块**：`zxplat/zxplat-*`  
> **聚合类型**：单表配置聚合  
> **版本**：v1.5 · **日期**：2026-06-28  
> **Flyway 真源**：`V74__prt_print.sql`（`prt_print_binding` 表 + 出厂 seed 2 条；与 `prt_template.scenario_code` **并行**，运行时主入口为后者）  
> **决策纪要**：2026-06-22 Print SDD 决策（本期全做、仅租户级、seed 2 条、院点 binding defer）

---

## 1. 概述

### 1.1 业务定义

**业务场景模板绑定（PrintBinding）** 将 **稳定业务入口码 `bindingCode`**（如 `charge.cashier.receipt`）映射为 **`busNo`**，供**管理页**配置场景与模板的对应关系。

> **运行时口径（2026-06-28）**：业务页 **`preparePrint` 主入口为 `prt_template.scenario_code`**（如 `op-charge-print`），**不**经本表解析。本聚合服务于 **PrintBinding 管理页**（IF-21/22）及历史 seed 兼容；请求字段 **`bindingCode`** 在 prepare 中与 **`scenarioCode` 同义**（别名，非查表）。

本文档编码为 **`DM-PRT-003`**；**无 FHIR 标准资源一一映射**（见 §9.2）。

### 1.2 核心职责

| 职责 | 说明 |
| --- | --- |
| 场景 → 模板键 | `bindingCode` → `busNo` |
| 多租户配置 | 平台默认（tenant=0）+ 租户覆盖 |
| preparePrint 入参 | 业务页传 **`scenarioCode`**（推荐）或 **busNo**；`bindingCode` 仅作 scenarioCode 别名 |
| 管理页 | IF-BAS-PRT-21/22 加载与保存租户 binding 映射 |

### 1.3 适用范围

| bindingCode（seed，legacy） | 默认 busNo（seed，可能与现行 PT 编号不一致） |
| --- | --- |
| `charge.cashier.receipt` | `charge_receipt` |
| `outpatient.rx.print` | `outpatient_prescription` |

> 现行出厂模板以 **`scenario_code`** 为准（见 README-PRT）；binding seed 供绑定管理页展示，**新业务接入请使用 scenarioCode**。

### 1.4 与关键上下游及交换边界的关系

| 维度 | 上游 | 本聚合 | 下游 |
| --- | --- | --- | --- |
| 职责 | 产品 / 实施配置场景码 | binding → busNo 映射 | preparePrint 解析 |
| 数据 | bindingCode | `bus_no` | **DM-PRT-001** |
| 引用 | — | — | **DM-PRT-002** 可冗余 `binding_code` |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

| 物理表 | 说明 |
| --- | --- |
| `prt_print_binding` | 聚合根 |

**唯一约束**：`(tenant_id, organization_id, binding_code)`

> **本期**：`organization_id` **恒 NULL**；唯一键等价于 `(tenant_id, binding_code)`。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| 绑定标识 | `PrintBindingId` | 主键 `id`（`Long`） |

---

## 3. 实体

### 3.1 PrintBinding（聚合根）

| 属性 | 字段名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| 绑定标识 | `id` | `Long` | 是 | |
| 租户 | `tenant_id` | `Long` | 是 | `0` = 平台默认 |
| 院点 | `organization_id` | `Long` | 否 | **本期恒 NULL**（列保留，院点 binding deferred） |
| 场景码 | `binding_code` | `String(128)` | 是 | 稳定入口，如 `charge.cashier.receipt` |
| 目标模板键 | `bus_no` | `String(64)` | 是 | 指向 **DM-PRT-001**（API **`busNo`**） |
| 显示名 | `name` | `String(128)` | 否 | 管理列表 / seed |
| 启用 | `enabled` | `Boolean` | 是 | 默认 true |
| 审计 | `created_*` / `updated_*` / `is_deleted` | — | — | |

---

## 4. 值对象与枚举

无独立枚举；`enabled` 为布尔。

---

## 5. 领域事件

首期不发域事件。

---

## 6. 领域服务与应用编排

### 6.1 PrintBindingResolver（domain）

| 方法 | 职责 |
| --- | --- |
| `resolve(bindingCode, tenantId)` | 返回 `busNo`；**租户 → 平台**（本期忽略 organizationId） |
| `listDefinitions` | 出厂 bindingCode 清单（seed + 元数据） |
| `listOverridesForTenant(tenantId)` | 租户 override 行 |
| `saveTenantBindings(tenantId, map)` | upsert 租户级映射 |

**解析顺序（本期）**：

1. `(tenant_id, binding_code)` 且 `enabled=true`  
2. `(0, binding_code)` 平台默认  
3. 无租户 override → fallback 平台默认 **busNo**（seed）；仍无配置 → **BusinessException**（无绑定定义）

### 6.2 PrintBindingAppService（application）

| 方法 | 权限 | 说明 |
| --- | --- | --- |
| `loadConfig` | `Print:design` | IF-BAS-PRT-21：场景清单 + 当前 busNo + 候选模板 |
| `saveConfig` | `Print:design` | IF-BAS-PRT-22：全量提交 bindingCode→busNo |

> 菜单 seed 曾用 `Print.PrintBinding.View`；方法级权限与 **Print:design** 对齐（SDD 定稿）。

### 6.3 preparePrint 扩展（PrintJobAppService）

入参：`bindingCode` **或** `busNo`（**二选一**，互斥校验）；经 Resolver 得到 `busNo` 后走 **DM-PRT-001** `findPublished`。

confirm 时若曾传 `bindingCode`，**DM-PRT-002** 冗余 `binding_code`。

---

## 7. 不变式

| 编号 | 不变式 | 说明 |
| --- | --- | --- |
| BR-PRT-BND-001 | `binding_code` 非空且 trim | |
| BR-PRT-BND-002 | `bus_no` 非空 | 保存时目标须存在 **published** 模板（校验） |
| BR-PRT-BND-003 | `(tenant_id, organization_id, binding_code)` 唯一 | 本期 org 恒 NULL |
| BR-PRT-BND-004 | `enabled=false` 时不参与 resolve | |
| BR-PRT-BND-005 | 解析顺序：**租户 → 平台** | 院点层 deferred（同 **DM-PRT-001** §9.1） |

---

## 8. 上下文映射

### 8.1 上游

| 来源 | 关系 |
| --- | --- |
| 信息科 / 实施 | 维护 binding 配置 |
| 产品出厂 seed（V5） | 平台默认 binding 2 条 |

### 8.2 下游

| 消费方 | 方式 |
| --- | --- |
| PrintJobAppService | preparePrint |
| Vue 业务页 | 传 `bindingCode` |
| DM-PRT-002 PrintLog | 审计冗余 `binding_code` |

---

## 9. 补充

### 9.1 出厂 seed（V5）

| bindingCode | name（示例） | busNo |
| --- | --- | --- |
| `charge.cashier.receipt` | 收费小票 | `charge_receipt` |
| `outpatient.rx.print` | 门诊处方 | `outpatient_prescription` |

平台行：`tenant_id=0`，`organization_id=NULL`。

### 9.2 与 FHIR / 标准的映射说明

本聚合为 **UI/流程入口配置**，无 FHIR 资源对应。

### 9.3 实现状态（目标态）

| 项 | 状态 |
| --- | --- |
| 表 `prt_print_binding` | **本期实现**（V5） |
| PrintBindingResolver | **本期实现** |
| PrintBindingAppService | **本期实现**（IF-21/22） |
| Web 绑定页后端 | **本期实现**（`print-binding/index.vue`） |

---

## 10. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.4 | 2026-06-22 | SDD 决策：本期全做、租户 only、seed 2 条、PrintBindingAppService |
| v1.3 | 2026-06-22 | 交叉引用对齐 PrintTemplate / PrintLog |
| v1.2 | 2026-06-22 | PrintBinding 规划 Flyway V5 |
| v1.1 | 2026-06-22 | 迁入 `health/DomainModel` |
| v1.0 | 2026-06-22 | 初稿 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PrtPrintBindingDomainService`

| 方法 |
|------|
| `saveTenantBindings` |

### QueryService：`PrtPrintBindingQueryService`

| 方法 |
|------|
| `resolve` |
| `listDefinitions` |
| `listOverridesForTenant` |

