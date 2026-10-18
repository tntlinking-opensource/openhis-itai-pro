# 检验域 · HIS 接单入口 · 应用服务设计（AS）

> **文档职责**：说明医生站签发检验医嘱后 LIS 侧的业务规则（BR）、合管分组策略、编排意图；作为上游契约供详细设计与实现对齐。
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
> **实现方式**：医生站 `DoctorStationAdviceAppService` 直接注入 `LisWorkItemDomainService`，不走 Extension、不走 HTTP、不走事件（同 zxplat 进程内调用）。
> **编码**：AS-LIS-INT-001 · **版本**：v2.3 · **日期**：2026-06-27

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 接收 health 侧 ServiceRequest 列表 → 执行合管分组 → 创建 LisWorkItem + orderLinks；撤销/作废申请；HIS 查询申请状态；外发失败补发；可检验性校验 |
| **在外** | 医生站开立/签发医嘱 → health AS-OP-DOR-003；收费 → health 收费 AS |
| **涉及域** | LisWorkItem（检验工单）、lis_merge_profile（合管方案）、lis_device_capability（仪器能力）、lis_tube_scheme（试管方案） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
|------|----------|
| **health 医生站** | 签发检验类医嘱 → 直接调用 `LisWorkItemDomainService.createWorkItem()` |
| **系统** | 接收 ServiceRequest 列表 → 可检验性校验 → 合管分组 → 创建工单 + orderLinks → 发布事件 |

### 1.3 术语与口径约定

| 术语 | 口径 |
|------|------|
| **合管分组** | N 个 ServiceRequest → 按试管方案+仪器能力+合管方案三级约束 → 1 个或多个 LisWorkItem |
| **可检验性校验** | 每个 `activity_id` 须在 **`lis_device_capability`** 至少有一条在用配置；缺失则拒绝整批（见 [LisAppService/01 §10.4](../LisAppService/01-概要设计.md#104-项目对照--一体化无-mapd3)） |
| **幂等** | 同一 serviceRequestId 已存在 orderLink → 不重复创建（BR-INT-01） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
|------|------|
| **本期（契约）** | IF-INT-01~06：合管接单、撤单、查询状态、可检验性校验、补发 |
| **非目标** | 患者登记同步（INT-04，P2 暂不实现）；lis-adapter 内部报文转换逻辑 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 职责 | DM 路径 |
|------|------|---------|
| **LisWorkItem** | 标本工单生命周期、orderLink 关联 | `docs/领域/缺口/文档无代码/DM-LIS-001-LisWorkItem-检验工单.md |
| **LisConfig** | 合管降级策略、条码规则 | `docs/领域/缺口/文档无代码/DM-LIS-008-LisConfig-模块参数.md |
| **health ServiceRequest** | 医嘱来源（跨域引用） | `docs/领域/DomainModel/DM-WKF-004-ServiceRequest-服务申请.md |

### 1.6 业务规则（BR-*）

| 编号 | 规则 | 触发 IF |
|------|------|---------|
| **BR-INT-01** | 幂等：同一 serviceRequestId 已存在 LisWorkItemOrder → 不重复创建 | INT-01 |
| **BR-INT-02** | 合管分组：三级约束（试管方案 → 仪器能力 → 合管方案） | INT-01 |
| **BR-INT-03** | 降级：项目未配 merge_profile → 按 `lis_config.grouping.fallbackWhenNoProfile` | INT-01 |
| **BR-INT-04** | 撤单状态校验：LisWorkItem 须为 available 或 collected | INT-02 |
| **BR-INT-05** | 撤单执行：全管 orderLink 均作废才 cancel LisWorkItem | INT-02 |
| **BR-INT-06** | 可检验性校验：逐 `activity_id` 查 `lis_device_capability`；任一缺失 → 拒绝整批 | INT-01、INT-06 |
| **BR-INT-07** | 补发：关联原 eventId | INT-05 |

---

## 2. 页面与接口总览（契约概览）

> 本功能无独立 UI 页面，由医生站签发/撤销/修改医嘱时直接调用领域服务。

| IF 编号 | 用户动作 | 接口语义 | 读写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
|---------|----------|----------|:--:|----------|-------------|----------|----------|
| **IF-INT-01** | HIS 签发检验医嘱 | LIS 接收+合管分组+创建工单 | 写 | BR-INT-01/02/03/06 | 新建 LisWorkItem(status=available) + orderLinks | LisWorkItem, lis_config, lis_device_capability | LisWorkItemQueryService |
| **IF-INT-02** | HIS 签退 | LIS 撤单 | 写 | BR-INT-04/05 | orderLink→cancelled；全管作废→LisWorkItem→entered-in-error | LisWorkItem | — |
| **IF-INT-03** | HIS 查询申请状态 | 返回各 serviceRequestId 对应状态 | 读 | — | — | LisWorkItem | LisWorkItemQueryService |
| **IF-INT-05** | health 外发失败补发 | 重试接单 | 写 | BR-INT-07 | 同 INT-01 | LisWorkItem | — |
| **IF-INT-06** | 可检验性校验 | 独立校验 `activity_id` 是否已配仪器能力 | 读 | BR-INT-06 | — | lis_device_capability | — |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**——当前 LIS 领域模型尚未开始实现，所有领域层能力均处于「待生成」目标态。

---

## 4. 接口编排说明书（业务步骤）

### 4.1 IF-INT-01：HIS 签发检验→LIS 接单

| 维度 | 说明 |
|------|------|
| **触发前提** | health 医生站签发检验类医嘱 |
| **实现** | `DoctorStationAdviceAppService` 直接调用 `LisWorkItemDomainService.createWorkItem()`；合管分组逻辑在 `LisOrderIngressOrchestrator`（support 类）中执行 |
| **主路径** | ① 可检验性校验：逐 `activityId` 查 `lis_device_capability`；任一缺失 → 拒绝整批 → ② 幂等检查：过滤已存在 orderLink 的 serviceRequestId → ③ 合管分组：按试管方案→仪器能力→合管方案 三级约束 group → ④ 为每组创建 LisWorkItem + orderLinks → ⑤ 发布 `LisWorkItemCreatedEvent` |
| **关键分支** | 全部已存在 → 空返回；项目未配 merge_profile → 降级为 ONE_SR_ONE_TUBE |
| **失败分支** | 能力未配 → `BusinessException("DEVICE_CAPABILITY_MISSING")`；barcode 生成失败 → 重试 3 次后 `BusinessException("BARCODE_GEN_FAILED")` |

### 4.2 IF-INT-02：HIS 签退→LIS 撤单

| 维度 | 说明 |
|------|------|
| **触发前提** | health 医生站签退检验医嘱 |
| **实现** | `DoctorStationAdviceAppService` 直接调用 `LisWorkItemDomainService.cancelWorkItem()` |
| **主路径** | ① 按 serviceRequestId 定位 orderLink → ② 校验 LisWorkItem 状态 ∈ {available, collected} → ③ 逐条 cancelOrder → ④ 全管全部作废 → cancel LisWorkItem |
| **失败分支** | 状态不符 → `BusinessException("CANCEL_STATUS_INVALID")` |

---

## 5. 业务异常与用户提示

| 异常码 | 场景 | 用户提示语义 | 可重试 |
|--------|------|-------------|:--:|
| `DEVICE_CAPABILITY_MISSING` | 仪器能力未配 | "项目 {activityId} 未配置检验仪器能力，请在 CFG-08 维护" | 否 |
| `BARCODE_DUPLICATE` | 条码重复 | "条码生成冲突，请重试" | 是 |
| `CANCEL_STATUS_INVALID` | 撤单状态不符 | "该标本已进入核收阶段，无法撤单" | 否 |
| `WORKITEM_NOT_FOUND` | 工单不存在 | "未找到对应检验工单" | 否 |

---

## 6. 事件语义与消费方

| 事件 | 触发点 | 消费方 | 幂等 |
|------|--------|--------|:--:|
| `LisWorkItemCreatedEvent` | IF-INT-01 成功 | SMP-001 采样台刷新、TAT 监控 | 按 workItemId 去重 |

---

## 7. 非功能性需求

| 维度 | 承诺 |
|------|------|
| **幂等** | IF-INT-01 同批次重复调用 → 过滤已存在 orderLink，只处理新增 |
| **并发** | 同 serviceRequestId 并发 ingress → 乐观锁或唯一约束防止重复创建 |
| **一致性** | 整批 serviceRequestId 全成功或全失败（可检验性校验前置） |
| **审计** | 每次 IF-INT-01/02 记录 AuditLog |

---

## 8. 测试要点（业务视角）

| 场景 | 测试要点 |
|------|----------|
| **正向** | 多 ServiceRequest 同管分组 → 验证 1 个 LisWorkItem + N 条 orderLink |
| **正向** | 不同管分组 → 验证多个 LisWorkItem |
| **异常** | 单 activityId 无 device_capability → 拒绝整批 |
| **幂等** | 同一 serviceRequestId 重复调用 → 不重复创建 |
| **降级** | 无 merge_profile → 一管一条 |

---

## 9. 关联文档与变更历史

### 关联文档

- DM：`DM-LIS-001-LisWorkItem-检验工单.md`、`DM-LIS-008-LisConfig-模块参数.md`
- 上下游 AS：`AS-LIS-SMP-001-采样工作台.md`（消费事件）
- 概要设计：`03-AppService详细设计.md` §五

### 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.3 | 2026-06-27 | DEC 回归主文档；引用改 LisAppService/01 §10 |
| v2.2 | 2026-06-27 | 方案 A：BR-INT-06 改 `lis_device_capability` 校验；废止 `lis_item_map` |
| v2.1 | 2026-06-22 | 去 AppService：INT-01 不再作为独立 AppService；医生站直接调用 `LisWorkItemDomainService` |
| v2.0 | 2026-06-22 | 按 9 章骨架重写；删类名+字段明细；补 §1.2~1.6、§3 Gap、§4 编排、§5~8 |
| v1.0 | 2026-06-22 | 首版（简化版） |
