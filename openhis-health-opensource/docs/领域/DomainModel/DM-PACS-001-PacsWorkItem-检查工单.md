# DM-PACS-001 PacsWorkItem 检查工单

> **限界上下文**：检查域（PACS/RIS）· 检查执行管理  
> **聚合类型**：实体聚合  
> **版本**：v1.1 · **日期**：2026-09-21  
> **输入**：[PacsAppService/04-FHIR领域模型](../../../../应用/zxplat-application/PacsAppService/04-FHIR领域模型.md)、AS-PACS-INT-001、AS-PACS-REG-001、AS-PACS-EXE-001、[PACS建议工作顺序](../../other/PACS建议工作顺序.md) §4 D-1  
> **对称参考**：[DM-LIS-001 LisWorkItem](./DM-LIS-001-LisWorkItem-检验工单.md)  
> **跨模块**：[PacsAppService/01 §7](../../../../应用/zxplat-application/PacsAppService/01-概要设计.md#7-lispacspeis-跨模块架构约定)

---

## 1. 概述

### 1.1 业务定义

检查工单（**PacsWorkItem**）是 PACS/RIS 侧最核心的聚合根，表达**一次检查会话**（登记号/检查号维度）在影像科内的执行事实。医嘱意图仍在 health `ServiceRequest`；本聚合通过子实体 **PacsWorkItemOrder（orderLinks）** 挂接一条或多条检查医嘱，**不复制**患者主数据与收费事实。

**合并检查核心**：N 个 `ServiceRequest`（医嘱行）→ 1 个 `PacsWorkItem`（一次摄片/检查会话），对齐遗留 `GROUPCODE` / 一行 `YGZZ_HZXX`。

与 LIS 对称：LIS 用工单表达「一管标本」；PACS 用工单表达「一次检查」。

### 1.2 核心职责

| 职责 | 说明 |
|------|------|
| 检查业务标识 | 维护检查号 `accessionNumber`（登记确认后生成） |
| 检查生命周期 | 接单 → 登记/待摄 → 执行中 → 已检 → 作废 |
| 医嘱关联 | 通过 orderLinks 维护 N:1 的 ServiceRequest 关联 |
| 执行上下文 | 模态、部位、设备、执行人、计划/实际时间 |
| 媒体标记 | `hasMedia` 供列表/报告引用；**影像实体写入**在 imaging 扩展 |
| 患者/就诊锚点 | 仅 `patientId` / `encounterId`，禁止冗余 PHI |

### 1.3 适用范围

| 场景 | 说明 |
|------|------|
| HIS/医生站接单 | INT-01 分组后创建 PacsWorkItem + orderLinks |
| PEIS 外发检查 | PEIS INT-01 → 同领域 `createWorkItem` |
| 登记工作台 | 编辑登记信息、生成 accessionNumber、待摄 |
| 检查执行 | 待摄 → 已检（`completeExam`） |
| 报告书写/审核 | 依赖 `completed` 工单（报告聚合另见后续 DM-PACS-00x） |
| 撤单/作废 | HIS 签退或登记作废 → orderLink / WorkItem 作废 |
| 影像采集回写 | imaging 扩展写 ImagingStudy/Media 后回写 `hasMedia` |

### 1.4 非本聚合职责

| 不在内 | 归属 |
|--------|------|
| 患者姓名/证件等 PHI | health Patient（列表 JOIN） |
| 医嘱开立与收费 | ServiceRequest / ChargeItem |
| DICOM Study/关键图/视频实体 | ImagingStudy / Media（imaging 扩展写入；索引字段可挂本聚合外键） |
| 报告正文与 PDF | pacs DiagnosticReport（后续 DM） |
| 厂商 PACS 码表 | pacs-adapter 私有对照（**不建** `pacs_item_map`） |
| 阅片 URL 配置 | `pacs_viewer_endpoint`（imaging / IM-CFG） |

---

## 2. 聚合根与持久化形态

### 2.1 持久化形态

**持久化形态**：主表 + 子表

| 表 | 说明 |
|----|------|
| `pacs_work_item` | 聚合根；主键 `id` |
| `pacs_work_item_order` | 子实体 orderLinks；外键 `work_item_id` |

关系：1 : N。

### 2.2 聚合根标识

| 属性 | 类型 | 说明 |
|------|------|------|
| workItemId | Long | 全局唯一标识（雪花 ID） |

### 2.3 包与模块落点（目标态）

| 项 | 口径 |
|----|------|
| 领域包 | `zxplat-domain` · `com.zxplat.domain.pacs…`（与 Lis 对称子包） |
| 装配 | `pacs-starter` / `zxplat.modules.pacs.enabled` |
| Flyway | health 侧 pacs 迁移约定（见 modules-all · PACS） |

---

## 3. 领域模型

### 3.1 PacsWorkItem（聚合根）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | Long | 是 | 聚合根标识 |
| accessionNumber | String | 否* | 检查号；*登记确认后必填；创建时可空 |
| patientId | Long | 是 | 引用 health Patient；**禁止**冗余姓名/证件 |
| encounterId | Long | 是 | 引用 health Encounter |
| organizationId | Long | 是 | 登记/执行科室（≈ REGIRID） |
| statusEnum | PacsWorkItemStatus | 是 | 检查执行态（见 §3.3） |
| modalityCode | String | 否 | 模态（≈ SBLX）；可自 order 汇总或登记写入 |
| bodySiteCode | String | 否 | 检查部位（≈ JCBW） |
| deviceId | Long | 否 | 执行设备（引用设备台账，CFG） |
| performerPractitionerId | Long | 否 | 执行医师/技师 |
| scheduledTime | Instant | 否 | 计划检查时间 |
| performedTime | Instant | 否 | 实际完成时间（≈ JCSJ）；`completeExam` 写入 |
| hasMedia | Boolean | 是 | 是否已有影像/关键图；默认 false；imaging 回写 |
| tenantId | Long | 是 | 租户 |
| createdBy / createdAt | Long / Instant | 是 | 审计 |
| updatedBy / updatedAt | Long / Instant | 是 | 审计 |

### 3.2 PacsWorkItemOrder（子实体）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | Long | 是 | 子实体标识 |
| workItemId | Long | 是 | 外键 |
| serviceRequestId | Long | 是 | 关联 health ServiceRequest；**UNIQUE (work_item_id, service_request_id)** |
| activityId | Long | 是 | 冗余 ActivityDefinition.id，便于 capability 校验与列表 |
| linkStatusEnum | PacsOrderLinkStatus | 是 | 该项在本检查会话上的关联态 |
| groupCode | String | 否 | 合并组号冗余（来源医嘱 GROUPCODE） |
| sortNumber | Integer | 否 | 组内排序 |

### 3.3 枚举

**PacsWorkItemStatus（检查执行态，≈ JCZT）**

| 码 | 含义 | 遗留大致对应 |
|----|------|--------------|
| `registered` | 已接单，待登记确认 | 0 登记 |
| `scheduled` | 已登记（有检查号），待摄 | 1 待摄 |
| `in-progress` | 检查执行中 | （可选中间态） |
| `completed` | 已检，可写报告 | 2/4 已检 |
| `entered-in-error` | 作废 | 作废 |

**PacsOrderLinkStatus（orderLink 态）**

| 码 | 含义 |
|----|------|
| `active` | 有效关联 |
| `cancelled` | 该项已撤/作废 |

### 3.4 状态机

```text
                createWorkItem (INT-01)
                        │
                        ▼
                  registered
                   │         │
     confirmRegistration     │ cancel / 全 order 作废
     (写 accessionNumber)       │
                   ▼         ▼
               scheduled   entered-in-error
                   │
            startExam（可选）
                   ▼
              in-progress
                   │
            completeExam
                   ▼
               completed  ──►（报告态正交，见 DiagnosticReport DM）
```

| 迁移 | 前置 | 结果 | 触发方 |
|------|------|------|--------|
| → registered | — | 新建 | INT-01 `createWorkItem` |
| registered → scheduled | accessionNumber 生成成功 | 待摄 | REG `confirmRegistration` / `updateRegistration` |
| registered\|scheduled → in-progress | 已有 accessionNumber | 执行中 | EXE（可选显式开始） |
| registered\|scheduled\|in-progress → completed | 已有 accessionNumber | 已检 | EXE `completeExam` |
| registered\|scheduled → entered-in-error | 见 BR | 作废 | INT-02 / REG 作废 |
| completed → * | — | **禁止**撤单作废整单（报告链路另规） | — |

报告子状态与检查态**正交**，不在本聚合内展开（见后续报告 DM / AS-PACS-REP-*）。

---

## 4. 聚合关系

```text
PacsWorkItem（聚合根）
  │
  └── 1:N ──► PacsWorkItemOrder
                   ├── serviceRequestId ──► health ServiceRequest
                   └── activityId ──────► health ActivityDefinition

PacsWorkItem 1:0..1 ──► ImagingStudy（表 pacs_imaging_study；写入在 imaging 扩展）
PacsWorkItem 1:0..1 ──► pacs DiagnosticReport（后续 DM；final → cln_diagnostic_report_index）
```

| 关系 | 基数 | 说明 |
|------|------|------|
| ServiceRequest → PacsWorkItem | N : M | 经 orderLinks；合并时 N>1 : 1 |
| PacsWorkItem → ImagingStudy | 1 : 0..1 | P0 可无影像；国基首版 imaging 后补齐 |
| PacsWorkItem → DiagnosticReport | 1 : 0..1 | 主报告；分项索引可挂 SR |

---

## 5. 领域事件

| 事件 | 触发条件 | 消费方（意图） |
|------|----------|----------------|
| `PacsWorkItemCreatedEvent` | INT-01 创建工单 | REG 待登记列表刷新 |
| `PacsWorkItemScheduledEvent` | 登记确认 → scheduled | EXE 待摄列表 |
| `PacsExamCompletedEvent` | `completeExam` | REP 可书写；imaging 可采集 |
| `PacsWorkItemCancelledEvent` | 整单作废 | REG/EXE 列表；adapter 同步 |
| `PacsWorkItemMediaFlagUpdatedEvent` | `hasMedia` 变更 | REP/列表展示 |

---

## 6. 领域服务方法（目标签名意图）

> 实现类：`PacsWorkItemDomainService` / 查询：`PacsWorkItemQueryService`。细节编排见 Domain SDD（D-2）。

| 方法 | 说明 |
|------|------|
| `createWorkItem(cmd)` | INT-01：校验 capability → 幂等 → 建根 + orderLinks；status=`registered` |
| `createLocalWorkItem(cmd)` | REG-04：无 orderLink 本地补登；可选 activityId 校验 capability；status=`registered` |
| `addOrder(workItemId, serviceRequestId, activityId, …)` | 同会话追加医嘱（P2；P0 可仅创建时写入） |
| `cancelOrder(workItemId, serviceRequestId, reason)` | 作废单条 orderLink |
| `cancelWorkItem(workItemId, reason)` | 全部 order 已 cancelled 时作废整单 → `entered-in-error` |
| `updateRegistration(cmd)` | 更新登记字段；空则生成 accessionNumber；registered→scheduled |
| `confirmRegistration(workItemId, …)` | 显式登记确认（可与 update 合并实现） |
| `startExam(workItemId, …)` | 可选 → `in-progress` |
| `completeExam(workItemId, performedTime, …)` | → `completed`；写 performedTime |
| `markHasMedia(workItemId, hasMedia)` | imaging 扩展回写 |

### 6.1a 同包配置写侧（`ModalityCapabilityDomainService`）

> 表 `pacs_modality_capability`；接单覆盖真源（**BR-PWI-03**）。与工单聚合同目录部署，配置台 AS-PACS-CFG-001。

| 方法 | 说明 |
|------|------|
| `save(SaveModalityCapabilityCommand)` | 新增或更新能力行；校验 activity 等 |
| `disable(...)` | 停用能力行 |
| `softDelete(...)` | 逻辑删除能力行 |

**QueryService**：`ModalityCapabilityQueryService`（接单覆盖校验、配置台分页）。

> **变更（2026-09-23 · 模式 B）**：按代码回写补齐模态能力写侧方法名，消除与 `ModalityCapabilityDomainService` 的漂移。

**QueryService（读侧意图 · PacsWorkItem）**：`searchPendingRegistration`、`searchPendingExecution`、`searchCompleted`、`getDetail`、`findByServiceRequestId`、`findByAccessionNumber`（imaging C-STORE 回写；仓储已有按检查号查找，查询服务待暴露）；列表 **JOIN Patient**，禁止从本表读 PHI。

**接单编排（非聚合方法，application/support）**：`PacsOrderIngressOrchestrator` + `PacsGroupingPolicy`（读 `groupCode` + `pacs_modality_capability`）。

---

## 7. 业务规则

| 编号 | 规则 |
|------|------|
| **BR-PWI-01** | **禁止**在 `pacs_work_item` 持久化 patientName、idCard 等 PHI；列表必须 JOIN/`Patient` 读侧 |
| **BR-PWI-02** | 同一 `(workItemId, serviceRequestId)` 唯一；INT-01 已存在 orderLink 则幂等跳过 |
| **BR-PWI-03** | 创建前每个 `activityId` 须被 `pacs_modality_capability` 覆盖，否则整批拒绝（INT-07） |
| **BR-PWI-04** | health **不建** `pacs_item_map`；厂商码表仅 adapter |
| **BR-PWI-05** | 登记确认必须生成或写入非空 `accessionNumber`；成功后 status=`scheduled` |
| **BR-PWI-06** | 仅 `registered` / `scheduled` 可编辑登记信息 |
| **BR-PWI-07** | `completeExam` 前置：已有 accessionNumber，且 status ∈ {registered, scheduled, in-progress} |
| **BR-PWI-08** | 撤单/作废整单：status ∈ {registered, scheduled}；`completed` 不可走 INT-02 整单作废 |
| **BR-PWI-09** | 仅当全部 orderLink 均为 `cancelled` 时方可 `cancelWorkItem` |
| **BR-PWI-10** | `hasMedia` 仅由 imaging 扩展或受信领域命令回写；核心 RIS P0 可不依赖 DICOM |
| **BR-PWI-11** | 合并检查：同组 orderLinks 共享同一 WorkItem；分组策略在 Ingress，不在实体内硬编码 |
| **BR-PWI-12** | 本地补登（REG-04）允许 **零** orderLink；`PacsWorkItemCreatedEvent.serviceRequestIds` 为空列表 |

### 7.1 与既有 AS 的已知差异（成码前对齐）

| 点 | AS 表述 | 本 DM | 处理 |
|----|---------|-------|------|
| 登记后状态 | AS-PACS-REG-001 BR-REG-02：保持 `registered` | 确认后 → `scheduled` | **以本 DM 为准**；D-4 修订 REG AS |
| EXE 待摄 | registered/scheduled 均可 | 主路径待摄=`scheduled`；兼容仍带 accession 的 registered | Query 条件写清 |

---

## 8. 与配置及其他聚合

| 协作 | 说明 |
|------|------|
| `pacs_modality_capability` | 接单可执行性真源（非本聚合表） |
| `pacs_config` | 检查号生成规则等模块参数 |
| ImagingStudy / Media | 外聚合；经 `workItemId` 关联；写入在 `zxplat-module-pacs-imaging` |
| DiagnosticReport | 外聚合；`completed` 后书写；final → [DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md) |
| Observation 投影 | 报告审核 REP-07 → `projectFromPacsSummary`（[DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md)）；**禁止** adapter 直写 |

---

## 9. FHIR 映射（互操作层）

| 本聚合 | FHIR R4（导出时） |
|--------|-------------------|
| PacsWorkItem | 无单一资源；组装 ImagingStudy + basedOn ServiceRequest |
| PacsWorkItemOrder | `ImagingStudy.basedOn` → ServiceRequest |

代码侧**不以** FHIR JSON 为持久化形态。

---

## 10. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-09-20 | 首版（D-1）；自 04-FHIR / INT/REG/EXE AS 收敛为正式 DM；对称 DM-LIS-001 |
| v1.1 | 2026-09-21 | 读侧补充 `findByAccessionNumber`，供 imaging C-STORE 回写 |

<!-- MODE-B-METHOD-INVENTORY 2026-09-23 -->

## 附录 · 模式 B 已实现方法清单（2026-09-23）

> 自 zxplat-domain 对应 *DomainService / *QueryService 抽取 public 方法名，用于关闭方法白名单 DOC-DRIFT。细规仍以正文 §6 与源码为准；本表不替代九章成稿。

### DomainService：`ModalityCapabilityDomainService`

| 方法 |
|------|
| `save` |
| `disable` |
| `softDelete` |

### DomainService：`PacsWorkItemDomainService`

| 方法 |
|------|
| `createWorkItem` |
| `createLocalWorkItem` |
| `addOrder` |
| `cancelOrder` |
| `cancelWorkItem` |
| `updateRegistration` |
| `confirmRegistration` |
| `startExam` |
| `completeExam` |
| `markHasMedia` |

### QueryService：`ModalityCapabilityQueryService`

| 方法 |
|------|
| `search` |
| `assertAllActivitiesCovered` |
| `resolveModalityCode` |

### QueryService：`PacsWorkItemQueryService`

| 方法 |
|------|
| `searchPendingRegistration` |
| `searchPendingExecution` |
| `searchCompleted` |
| `searchCompletedOtherDept` |
| `searchEnteredInError` |
| `getDetail` |
| `findByServiceRequestId` |
| `findOptionalByServiceRequestId` |
| `findByAccessionNumber` |

