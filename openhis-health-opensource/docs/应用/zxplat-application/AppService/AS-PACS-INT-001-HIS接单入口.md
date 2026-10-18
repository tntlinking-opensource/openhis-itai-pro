# 检查域 · HIS 接单入口 · 应用服务设计（AS）

> **文档职责**：医生站签发检查医嘱后 PACS 侧 BR、合并分组与编排意图。  
> **实现方式（D-3 定稿）**：与 LIS 目标态对齐——`DoctorStationAdviceAppService` / 住院同构服务 **同进程直调** `PacsWorkItemDomainService`；分组与命令组装由 `PacsOrderIngressOrchestrator` 负责；**不建**独立 AppService、**不走** ExtensionImpl / HTTP 创建工单。  
> **Extension 留存口径**：`PacsAdviceApplyExtension` / NoOp 仅保留为可选厂商/外院适配占位；**国基首版建工单路径不经过 Extension**。  
> **编码**：AS-PACS-INT-001 · **版本**：v1.1 · **日期**：2026-09-20

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 接收 ServiceRequest（检查类）→ 可执行性校验 → 分组 → 创建 PacsWorkItem + orderLinks；撤单；查询状态；补发 |
| **在外** | 医嘱开立/签发 → AS-OP-DOR-003 / AS-IP-DOR-003；登记/执行/报告 → REG/EXE/REP AS；DICOM → zxplat-module-pacs-imaging |
| **涉及域** | PacsWorkItem、`pacs_modality_capability`、`pacs_config` |

### 1.2 角色与关键动作

| 角色 | 动作 |
|------|------|
| **health 医生站** | 签发检查类医嘱（`categoryEnum=23`）→ `PacsOrderIngressOrchestrator` → `PacsWorkItemDomainService.createWorkItem()` |
| **PEIS INT-01** | 登记后外发检验/检查 → 同编排（见 AS-PEIS-INT-001） |
| **系统** | 可执行性校验 → 分组 → 创建工单 → 发布事件 |

### 1.3 术语与口径约定

| 术语 | 口径 |
|------|------|
| **检查类医嘱** | `ServiceRequest.categoryEnum = 23`（`SERVICE_CATEGORY_TEST`）；检验 `22` 不进本编排 |
| **合并分组** | N 个 SR → 按 groupCode + `pacs_modality_capability` → 1 个或多个 PacsWorkItem |
| **可执行性校验** | 每个 `activity_id` 须在 **`pacs_modality_capability`** 可覆盖 |
| **幂等** | 同一 serviceRequestId 已有 orderLink → 不重复创建 |
| **接线定稿** | **直调 Domain**（废止「实现 PacsAdviceApplyExtensionImpl 建工单」方案） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期** | IF-PACS-INT-01～03、05、07；序号 10 接通签发→createWorkItem |
| **非目标** | INT-04 患者登记同步（P2）；INT-06 报告路径（adapter）；现场 PACS adapter 内码表；经 Extension HTTP 建工单 |

### 1.5 领域清单

| 聚合 | 职责 | DM |
|------|------|-----|
| **PacsWorkItem** | 检查工单、orderLinks | [DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md) |
| **health ServiceRequest** | 医嘱来源 | DM-WKF-004 |

### 1.6 业务规则（BR-*）

| 编号 | 规则 | IF |
|------|------|-----|
| **BR-INT-01** | 幂等：已存在 PacsWorkItemOrder → 跳过 | INT-01 |
| **BR-INT-02** | 合并分组：`PacsOrderIngressOrchestrator` + modality capability | INT-01 |
| **BR-INT-03** | 可执行性：逐 activityId 查 capability；缺失拒绝整批 | INT-01、INT-07 |
| **BR-INT-04** | 撤单：WorkItem ∈ {registered, scheduled} | INT-02 |
| **BR-INT-05** | 全 orderLink 作废才 cancel WorkItem | INT-02 |
| **BR-INT-06** | 补发关联原 eventId | INT-05 |
| **BR-INT-07** | 仅检查类（category=23）进入本编排；治疗/检验跳过 | INT-01 |

---

## 2. 页面与接口总览（契约概览）

> 无独立 UI；由医生站签发/签退时触发。

| IF | 用户动作 | 语义 | 读写 | 关键规则 | 状态变化 | 涉及域 |
|----|----------|------|:--:|----------|----------|--------|
| **IF-PACS-INT-01** | 签发检查医嘱 | 分组+创建工单 | 写 | BR-01/02/03/07 | registered + orderLinks | PacsWorkItem, capability |
| **IF-PACS-INT-02** | 签退 | 撤单 | 写 | BR-04/05 | entered-in-error | PacsWorkItem |
| **IF-PACS-INT-03** | 查询状态 | 读 | 读 | — | — | PacsWorkItem |
| **IF-PACS-INT-05** | 补发 | 重试 INT-01 | 写 | BR-06 | 同 INT-01 | PacsWorkItem |
| **IF-PACS-INT-07** | 可执行性校验 | 独立校验 capability | 读 | BR-03 | — | modality_capability |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**——领域写侧与读侧能力见 [DM-PACS-001](./DM-PACS-001-PacsWorkItem-检查工单.md) / [Domain SDD](../DomainModel-SDD/DM-PACS-001-PacsWorkItem-检查工单-SDD.md)；接线方式已于 D-3 定稿为直调 Domain。

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-PACS-INT-01

| 维度 | 说明 |
|------|------|
| **触发** | 门诊/住院医生站签发检查类医嘱 |
| **主路径** | ① Gate：category=23 → ② 可执行性校验 → ③ 幂等过滤 → ④ 分组 → ⑤ 创建 WorkItem + orderLinks → ⑥ 发布 `PacsWorkItemCreatedEvent` |
| **失败** | `PACS_MODALITY_CAPABILITY_MISSING` |

### 4.2 IF-PACS-INT-02

| 维度 | 说明 |
|------|------|
| **主路径** | 定位 orderLink → 状态校验 → cancelOrder → 必要时 cancel WorkItem |
| **失败** | `PACS_WORK_ITEM_INVALID_STATUS` / 规则冲突 |

---

## 5. 业务异常与用户提示

| 异常码 | 场景 | 提示语义 | 可重试 |
|--------|------|----------|:--:|
| `PACS_MODALITY_CAPABILITY_MISSING` | 模态能力未配 | 项目未配置检查能力，请在 CFG 维护 | 否 |
| `PACS_WORK_ITEM_INVALID_STATUS` | 撤单状态不符 | 检查已进入执行/报告阶段，无法撤单 | 否 |

---

## 6. 事件语义与消费方

| 事件 | 触发 | 消费方 |
|------|------|--------|
| `PacsWorkItemCreatedEvent` | INT-01 | REG-001 待登记列表 |
| `PacsWorkItemCancelledEvent` | INT-02 | REG/adapter |

---

## 7. 非功能性需求

| 维度 | 承诺 |
|------|------|
| **幂等** | INT-01 重复调用过滤已有 orderLink |
| **一致性** | 可执行性校验前置，与医生站 sign 同事务 |
| **审计** | INT-01/02 记 AuditLog（随医生站签发审计） |

---

## 8. 测试要点（业务视角）

| 场景 | 要点 |
|------|------|
| 正向 | 检查类签发 → 库中有 PacsWorkItem + orderLink |
| 跳过 | 检验/治疗签发 → 不创建检查工单 |
| 异常 | activity 无 capability → 拒绝签发事务 |
| 幂等 | 重复 serviceRequestId → 不重复创建 |
| 撤单 | registered 可撤；completed 不可撤（INT-02，后续序号） |

---

## 9. 关联文档与变更历史

- SDD：[AS-PACS-INT-001-SDD](../AppService-SDD/AS-PACS-INT-001-HIS接单入口-SDD.md)
- 对照 LIS：[AS-LIS-INT-001](./AS-LIS-INT-001-HIS接单入口.md)
- 工作顺序：[PACS建议工作顺序](../../other/PACS建议工作顺序.md) D-3 / 序号 10
- 03：[PacsAppService/03](../PacsAppService/03-AppService详细设计.md) §五

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版；9 章骨架；与 LIS INT 对齐 |
| v1.1 | 2026-09-20 | D-3：锁定直调 Domain；明确废止 ExtensionImpl 建工单；补充 BR-INT-07 |
