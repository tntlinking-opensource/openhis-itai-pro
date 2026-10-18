# DM-MRMS-002 质控与缺陷

> **限界上下文**：病案管理（MRMS）  
> **聚合**：质控子域（规则 / 缺陷 / 执行批次 / 必写文书 / 逾期通知）  
> **版本**：v1.0 · **日期**：2026-06-27

## 1. 概述

Wave 2 质控子域与病案主档（DM-MRMS-001）**分表分聚合**，通过 `case_record_id` 关联。

| 表 | 实体 | 说明 |
|----|------|------|
| `mrms_qc_rule` | `QcRule` | 规则配置（租户 0 = 全局默认） |
| `mrms_qc_defect` | `QcDefect` | 缺陷实例 |
| `mrms_qc_run` | `QcRun` | 执行批次 |
| `mrms_required_doc_type` | `RequiredDocType` | 必写文书类型 |
| `mrms_overdue_notify_log` | `OverdueNotifyLog` | 逾期通知幂等 |

## 2. 包结构

```text
domain/mrms/qc/
  entity/ repository/ service/ model/ enums/
health-integration-contracts/
  EmrDocumentExtension, StaffNotificationExtension
```

## 3. 规则执行

- `QcRuleExecutor.execute(caseRecordId, trigger, operatorId)`
- `trigger`：`on_receive` | `before_archive` | `manual` | `scheduled`
- 首期按 `rule_code` 硬编码分支；`expression_json` 仅参数

## 4. 缺陷状态

`open` → `rectified` → `closed`；归档门禁：无 `block` 级 `open`/`rectified` 缺陷。

## 5. 事件

归档仍由主档发布 `CaseRecordArchivedEvent`；EMR 锁定由 `MrmsCaseRecordQcAppService.archive()` + `IntegrationAfterCommitsupport 在事务提交后调用 `EmrDocumentExtension`。

## 6. 追溯

- LLD：[04-归档与质控-详细设计.md](../../../../应用/zxplat-application/MrmsAppService/04-归档与质控-详细设计.md)
- ADR：[模块化开发规范.md §7](../01-架构/模块化开发规范.md) §9

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`OverdueNotifyDomainService`

| 方法 |
|------|
| `alreadyNotified` |
| `recordNotified` |

### DomainService：`QcDefectDomainService`

| 方法 |
|------|
| `listDefects` |
| `listRuns` |
| `assertNoOpenBlockDefects` |
| `addManualDefect` |
| `markRectified` |
| `closeDefect` |
| `upsertOpenDefectFromRule` |
| `closeOpenDefectByRuleIfPresent` |
| `countOpenDefectsBySeverity` |
| `listActiveDefects` |
| `appendQcRun` |

### QueryService：`QcRuleQueryService`

| 方法 |
|------|
| `listEnabledRules` |
| `listRequiredDocTypes` |

