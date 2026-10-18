# DM-PACS-004 PacsReportTemplate 检查报告模板

> **限界上下文**：检查域（PACS/RIS）· 报告模板配置  
> **聚合类型**：实体聚合（配置型）  
> **版本**：v1.0 · **日期**：2026-09-22  
> **输入**：[PacsAppService/04](../../../../应用/zxplat-application/PacsAppService/04-FHIR领域模型.md)、遗留 `TemplateManager`、AS-PACS-CFG-002  
> **消费方**：CFG-002 维护；REP-04 套用写入 PacsDiagnosticReport 草稿

---

## 1. 概述

### 1.1 业务定义

**PacsReportTemplate** 是影像科报告正文模板（`pacs_report_template`），按模态（可选）维护所见/结论样本文本，供书写台一键套用。

### 1.2 职责

| 职责 | 说明 |
|------|------|
| 模板 CRUD | 名称、模态、所见、结论、启停、软删 |
| 套用读 | 按 id / 模态列表供 REP-04 |

### 1.3 非本聚合

| 不在内 | 归属 |
|--------|------|
| 报告事实正文 | DM-PACS-002 |
| 画布打印版式 | DM-PRT-001 |
| 设备台账 | CFG-003 |

---

## 2. 持久化

**表**：`pacs_report_template`（主键 `id`）

---

## 3. 领域模型

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | Long | 是 | 聚合根 |
| name | String | 是 | 模板名称 |
| modalityCode | String | 否 | 模态过滤；空=通用 |
| findings | String | 否 | 所见样本文本（≈ JCSJ） |
| conclusion | String | 否 | 结论样本文本（≈ JCTS） |
| enabledFlag | Boolean | 是 | 是否启用；默认 true |
| sortOrder | Integer | 否 | 列表排序 |
| ownerPractitionerId | Long | 否 | 个人模板归属；空=科室/院级 |
| tenantId | Long | 是 | 租户 |
| createdBy / createdAt / updatedBy / updatedAt | — | 是 | 审计 |
| isDeleted | Boolean | 是 | 软删 |

---

## 4. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-PRTPL-01** | 名称租户内非空；软删后不参与列表/套用 |
| **BR-PRTPL-02** | 仅 `enabledFlag=true` 且未软删可被 REP-04 套用 |
| **BR-PRTPL-03** | 套用只覆盖报告 `findings`/`conclusion`；不改 status / PDF |
| **BR-PRTPL-04** | 国基 P1 **不做**遗留树形 `PARENT_XH`；平铺列表 |

---

## 5. 领域服务方法

| 方法 | 说明 |
|------|------|
| `save(SavePacsReportTemplateCommand)` | 新增/更新 |
| `disable(id, operatorId)` | 停用 |
| `delete(id, operatorId)` | 软删 |
| `search(modalityCode, enabledOnly, pageable)` | 分页列表 |
| `getById(id)` | 详情（套用前） |

---

## 6. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-22 | D-11 首版；解锁 CFG-002 / 序号 43 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PacsReportTemplateDomainService`

| 方法 |
|------|
| `save` |
| `disable` |
| `delete` |
| `getById` |
| `requireEnabled` |

### QueryService：`PacsReportTemplateQueryService`

| 方法 |
|------|
| `search` |

