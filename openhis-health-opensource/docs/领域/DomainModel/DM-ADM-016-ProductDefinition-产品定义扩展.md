---
doc_type: domain_model
package: com.zxplat.domain.administration.product
aggregate: ProductDefinition
dm_code: DM-ADM-016
status: canonical
maturity: minimal
---

# DM-ADM-016 ProductDefinition — 产品定义扩展（读侧）

> **成稿口径**：模式 B（代码先行）· **minimal canonical**（九章齐全、§6 简表 + 附录方法白名单）。  
> 细规冲突以源码为准；扩写完整 `####` 条目时升 `maturity: full`。  
> 聚合说明：[说明.md](./说明.md)

## 1. 概述

| 项 | 说明 |
|----|------|
| **职责** | 读侧门面：按药品/耗材/诊疗/收费项等路由批量加载产品定义展示信息。 |
| **不做** | 不落独立产品主表；不写目录；展示术语回填在应用 shared。 |
| **包** | `com.zxplat.domain.administration.product` |
| **源码** | `zxplat-core/zxplat-domain/.../administration/product/` |

## 2. 聚合根与持久化形态

| 类型 | 表 | 关键列（节选，完整见实体） |
|------|-----|------------------------------|
| （无本包 JPA 实体） | — | 本包为读侧/支持，持久化在上游定义包 |

状态机：以实体 `status_*` / DomainService 校验为准；本 minimal 稿不重复绘制全图。

## 3. 实体

本包无聚合根实体；读模型见 QueryService / support。

## 4. 值对象（含枚举）

本包枚举/码表字段以实体 `*_enum` / `*_code`（String 存 code）为准；专用值对象类若存在见源码 `valueobject/`。

## 5. 领域事件

minimal：未单独建事件类的写操作以事务内状态变更为准。若 `share/events` 下存在本聚合事件，以该类为准并在扩写时补载荷表。

## 6. 领域服务

本聚合以 **QueryService** 为主；写方法见附录（若无 DomainService 则仅读侧）。§6 细规以源码 JavaDoc 为准，附录为方法白名单。

### 6.1 写侧

见文末附录 DomainService 方法表（无写侧则本节 N/A）。

### 6.2 读侧

见文末附录 QueryService 方法表（无读侧则本节 N/A）。

## 7. 不变式

- **BR-PROD-001**：查询路由必须落到合法定义表，禁止跨表臆造 ID。
- **BR-PROD-002**：本包只读，禁止在此包新增写侧 DomainService 而不改 DM。

## 8. 上下文映射

上游 Medication/Device/Activity/ChargeItemDefinition；下游展示组装。

## 9. 补充

| 项 | 值 |
|----|-----|
| 原 stub 关闭日 | 2026-09-24 |
| 体例 | [../.../../.cursor/skills/文档代码一致性/SKILL.md](../.../../.cursor/skills/文档代码一致性/SKILL.md) |
| 一致性 | 模式 B；方法白名单 = 附录 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### QueryService：`ProductDefinitionQueryService`

| 方法 |
|------|
| `batchLoadByKeys` |
| `batchLoadAsMap` |
