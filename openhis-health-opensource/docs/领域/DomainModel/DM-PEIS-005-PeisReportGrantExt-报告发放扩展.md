# DM-PEIS-005 PeisReportGrantExt · 报告发放扩展

> **表名**：`peis_report_grant_ext`  
> **版本**：v1.2 · **日期**：2026-06-27

---

## 1. 概述

`DiagnosticReport 的 **发放/签收/打印** 业务扩展；REP-002 消费。

---

## 2. 持久化形态

| 字段 | 类型 | 说明 |
|------|------|------|
| diagnostic_report_id | Long | PK/FK → DR |
| grant_status_enum | String | pending / printed / signed / mailed |
| grant_time | timestamptz | 发放/签收时间 |
| grant_by | Long | 操作人 |
| recipient_name | String | 签收人（可选） |
| tenant_id / org_id | Long | 租户/院区 |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `PeisReportGrantExt`（1:1 DR） |
| **标识** | `diagnosticReportId` |

### 3.1 聚合根字段

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| diagnosticReportId | Long | 是 | PK/FK |
| grantStatusEnum | String | 是 | 发放态 |
| grantTime | LocalDateTime | 否 | 时间 |
| grantBy | Long | 否 | 操作人 |
| recipientName | String | 否 | 签收人 |
| tenantId | Long | 是 | 租户 |
| orgId | Long | 否 | 院区 |

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `PeisReportGrantRegisteredEvent` | `diagnosticReportId, grantStatusEnum` | `registerGrant` |
| `PeisReportGrantSignedEvent` | `diagnosticReportId, recipientName` | `signGrant` |
| `PeisReportGrantPrintedEvent` | `diagnosticReportId` | `markPrinted` |

包路径：`com.zxplat.domain.share.events.custom.peisreportgrantext`

---

## 6. 领域服务

### 6.1 写侧 — `PeisReportGrantDomainService`

| 方法 | 说明 |
|------|------|
| `registerGrant(RegisterGrantCommand)` | REP-05 发放登记 |
| `signGrant(SignGrantCommand)` | REP-05 签收 |
| `markPrinted(MarkGrantPrintedCommand)` | REP-06 团体打印计数 |

### 6.2 读侧 — `PeisReportGrantQueryService`

| 接口签名 | 场景 |
|----------|------|
| `searchPendingGrant(GrantSearchQuery, Pageable): Page<GrantRowModel>` | REP-05 列表 |
| `getByDiagnosticReportId(drId): PeisReportGrantModel` | REP-05 详情 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-GRANT-01** | 1 DR 最多 1 行 ext |
| **BR-GRANT-02** | `signed` 须 `recipientName` 或院点免填策略 |
| **BR-GRANT-03** | DR 须 `final` 才可 `registerGrant`（应用层 Pre） |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.2 | 2026-06-27 | 补 §3/§5/§7 |
| v1.1 | 2026-06-27 | 增补 §6 领域服务 |
| v1.0 | 2026-06-27 | 首版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PeisReportGrantDomainService`

| 方法 |
|------|
| `registerGrant` |
| `signGrant` |
| `markPrinted` |
| `markMailed` |

### QueryService：`PeisReportGrantQueryService`

| 方法 |
|------|
| `searchPendingGrant` |
| `getByDiagnosticReportId` |

