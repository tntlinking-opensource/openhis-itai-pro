# DM-PEIS-001 PeisServiceRequestExt · 体检项目执行扩展

> **限界上下文**：health · peis（CUS）  
> **表名**：`peis_service_request_ext`  
> **版本**：v1.1 · **日期**：2026-06-27  
> **关联**：[Peis 04 §4.4](../../../../应用/zxplat-application/PeisAppService/04-FHIR领域模型.md#44-peisservicerequestextcus)、[DM-WKF-004](./DM-WKF-004-ServiceRequest-服务申请.md)

---

## 1. 概述

扩展 **`ServiceRequest`**（体检项目实例）的执行态与外发标志；**不含 barcode**（D6，反查 LIS/PACS WorkItem）。

---

## 2. 持久化形态

| 字段 | 类型 | 说明 |
|------|------|------|
| service_request_id | Long | PK/FK → `wkf_service_request.id` |
| exec_status_enum | String | `pending` / `checked` / `abandoned` / `deferred` |
| lis_dispatch_enum | String | `notSent` / `sent` / `resultReceived` |
| pacs_dispatch_enum | String | 同上 |
| tenant_id / org_id | Long | 租户/院区 |
| created_at / updated_at 等 | — | 框架审计字段 |

---

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `PeisServiceRequestExt`（1:1 `ServiceRequest`） |
| **标识** | `service_request_id`（PK/FK） |
| **生命周期** | 随 SR 创建初始化；随 DES/INT 更新 exec/dispatch 标志 |

### 3.1 聚合根字段（Java 映射）

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|:--:|------|
| serviceRequestId | Long | 是 | PK/FK |
| execStatusEnum | String | 是 | 执行态 code |
| lisDispatchEnum | String | 是 | LIS 外发态 |
| pacsDispatchEnum | String | 是 | PACS 外发态 |
| tenantId | Long | 是 | 租户 |
| orgId | Long | 否 | 院区 |

---

## 5. 领域事件

| 事件 | 最小载荷 | 触发 |
|------|----------|------|
| `PeisServiceRequestExecStatusChangedEvent` | `serviceRequestId, encounterId, execStatusEnum` | `updateExecStatus` |
| `PeisServiceRequestDispatchChangedEvent` | `serviceRequestId, moduleEnum, dispatchEnum` | `updateDispatchFlags` |

包路径：`com.zxplat.domain.share.events.custom.peisservicerequestext`

---

## 6. 领域服务

### 6.1 写侧 — `PeisServiceRequestExtDomainService`

| 方法 | 说明 |
|------|------|
| `initForServiceRequests(InitPeisSrExtCommand)` | REG-02 登记展开后批量初始化 |
| `updateExecStatus(UpdatePeisSrExecStatusCommand)` | DES 确认/弃检/延检 |
| `updateDispatchFlags(UpdatePeisSrDispatchCommand)` | INT-01 / DES-08 |

### 6.2 读侧 — `PeisServiceRequestExtQueryService`

| 接口签名 | 查询场景 |
|----------|----------|
| `findByServiceRequestId(serviceRequestId): PeisServiceRequestExtModel` | DES/INT |
| `listByEncounterId(encounterId): List<PeisServiceRequestExtModel>` | MAS-02 / 进度 |

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-PEIS-SR-01** | 1 SR 最多 1 行 ext |
| **BR-PEIS-SR-02** | **禁止** barcode 字段 |
| **BR-PEIS-SR-03** | `checked` 前须有小项或结论 Observation 或外发 resultReceived（院点策略） |
| **BR-PEIS-SR-04** | `updateExecStatus` 须 SR 归属同一 `encounterId`（命令携带校验） |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-06-27 | 补 §3/§5/§6.1 成 SDD 前置 |
| v1.0 | 2026-06-27 | 首版 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`PeisServiceRequestExtDomainService`

| 方法 |
|------|
| `initForServiceRequests` |
| `removeForServiceRequest` |
| `updateExecStatus` |
| `updateDispatchFlags` |

### QueryService：`PeisServiceRequestExtQueryService`

| 方法 |
|------|
| `findByServiceRequestId` |
| `listByEncounterId` |

