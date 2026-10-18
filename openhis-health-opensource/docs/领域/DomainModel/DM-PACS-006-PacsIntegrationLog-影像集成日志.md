---
doc_type: domain_model
package: com.zxplat.plugin.pacs.domain.integrationlog
aggregate: PacsIntegrationLog
dm_code: DM-PACS-006
status: canonical
maturity: minimal
---

# DM-PACS-006 PacsIntegrationLog — 影像集成日志

> **成稿口径**：模式 B（代码先行）· **minimal canonical**（九章齐全、§6 简表 + 附录方法白名单）。  
> 细规冲突以源码为准；扩写完整 `####` 条目时升 `maturity: full`。  
> 聚合说明：[说明.md](./说明.md)

## 1. 概述

| 项 | 说明 |
|----|------|
| **职责** | PACS 出入站集成调用的结果落库与失败查询。 |
| **不做** | 不发送影像消息；不做工单状态机。 |
| **包** | `com.zxplat.plugin.pacs.domain.integrationlog` |
| **源码** | `zxplat-core/zxplat-domain/.../pacs/integrationlog/` |

## 2. 聚合根与持久化形态

| 类型 | 表 | 关键列（节选，完整见实体） |
|------|-----|------------------------------|
| `PacsIntegrationLog` | `pacs_integration_log` | direction_enum, action_code, entity_type, entity_id, outcome_enum, request_body, response_body, error_message |

状态机：以实体 `status_*` / DomainService 校验为准；本 minimal 稿不重复绘制全图。

## 3. 实体

### PacsIntegrationLog

| 列（节选） |
|-----------|
| direction_enum, action_code, entity_type, entity_id, outcome_enum, request_body, response_body, error_message |


## 4. 值对象（含枚举）

本包枚举/码表字段以实体 `*_enum` / `*_code`（String 存 code）为准；专用值对象类若存在见源码 `valueobject/`。

## 5. 领域事件

minimal：未单独建事件类的写操作以事务内状态变更为准。若 `share/events` 下存在本聚合事件，以该类为准并在扩写时补载荷表。

## 6. 领域服务

§6.1 / §6.2 采用 **简表型**：完整方法名见文末「模式 B 已实现方法清单」。关键方法语义以 DomainService JavaDoc 为准。

### 6.1 写侧

见文末附录 DomainService 方法表（无写侧则本节 N/A）。

### 6.2 读侧

见文末附录 QueryService 方法表（无读侧则本节 N/A）。

## 7. 不变式

- **BR-PACSLOG-001**：出入站 direction/outcome 必须落合法枚举。
- **BR-PACSLOG-002**：失败查询仅返回 outbound 失败集（以 listFailedOutbound 为准）。

## 8. 上下文映射

上游 PacsWorkItem；下游集成插件。

## 9. 补充

| 项 | 值 |
|----|-----|
| 原 stub 关闭日 | 2026-09-24 |
| 体例 | [../.../../.cursor/skills/文档代码一致性/SKILL.md](../.../../.cursor/skills/文档代码一致性/SKILL.md) |
| 一致性 | 模式 B；方法白名单 = 附录 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PacsIntegrationLogDomainService`

| 方法 |
|------|
| `record` |
| `listFailedOutbound` |

### QueryService：`PacsIntegrationLogQueryService`

| 方法 |
|------|
| `search` |
| `getById` |
