# AS-LIS-INT-001 HIS接单入口 · 应用服务详细设计（SDD）

> **编码**：AS-LIS-INT-001-SDD · **版本**：v2.2 · **日期**：2026-06-27
> **对应 AS**：AS-LIS-INT-001-HIS接单入口.md（v2.2）

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application`（医生站 `outpatient` / `inpatient` 子包） |
| **边界** | 医生站签发/撤销/修改检验医嘱时直接调用 `LisWorkItemDomainService`；合管分组逻辑收敛在 `LisOrderIngressOrchestrator` |
| **编排权威** | AS-LIS-INT-001 §2、§4 |
| **方法权威** | DM-LIS-001 §6（LisWorkItemDomainService） |
| **实现阶段口径** | **目标态全量契约**（P0） |
| **实现方式** | 无视窗面 AppService、无 HTTP 端点；医生站 AppService 直接注入 `LisWorkItemDomainService` |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务口径** | 医生站 `DoctorStationAdviceAppService.sign()` 事务内调用 `LisWorkItemDomainService.createWorkItem()`，共享同一 UoW |
| **合管分组** | 收敛为 `LisOrderIngressOrchestrator`（support 类），医生站和 LIS 侧均可复用 |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/src/main/java/com/openhis/health/application/outpatient/doctorstation/
│
├── DoctorStationAdviceAppService.java    # 签发时注入 LisWorkItemDomainService
└── support/
    └── LisOrderIngressOrchestrator.java   # 合管分组编排（如有 lis_* 配置表则执行分组；无则 ONE_SR_ONE_TUBE）
```

| 依赖注入位置 | 类型 | 用途 |
|-------------|------|------|
| **DoctorStationAdviceAppService** | — | 签发检验医嘱时注入以下 LIS 领域服务 |
| `LisWorkItemDomainService` | 领域 | createWorkItem / cancelWorkItem（直接调用） |
| `DeviceCapabilityQueryService` | 查询 | INT-06 / BR-INT-06：可检验性校验 |
| **LisOrderIngressOrchestrator** | support | 合管分组编排（三级约束） |
| `MergeProfileQueryService` | 查询 | 读取合管方案 |
| `DeviceCapabilityQueryService` | 查询 | 读取仪器能力 |
| `LisWorkItemQueryService` | 查询 | 幂等检查 findByOrderLink |

### 2.2 领域方法白名单

| 聚合 | 方法 | 用于 IF |
|------|------|---------|
| LisWorkItem | `createWorkItem(CreateLisWorkItemCommand)` | INT-01 |
| LisWorkItem | `cancelWorkItem(CancelWorkItemCommand)` | INT-02 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | 说明 |
|------|-------------|------|
| `LisWorkItemStatus` | `com.zxplat.domain.share.enums` | available / collected / received / entered-in-error |

---

## 3. 按接口 ID 命名展开

### 3.1 编排约定（无 AppService，医生站方法体内）

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 检验类医嘱类型判断 | 非检验类跳过 |
| **Pre** | 对照校验 + 幂等 + 合管分组 | 抛业务异常 |
| **Cmd** | 领域写：LisWorkItemDomainService | 事务回滚 |
| **Asm** | 事件发布 | — |

---

### 3.2 IF-INT-01 签发检验医嘱 → 创建工单

#### DoctorStationAdviceAppService#sign() 内嵌编排（严格顺序）

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 类型判断：`serviceRequest.typeEnum = LAB` | 仅检验类医嘱进入 |
| 2 | **Pre** | `DeviceCapabilityQueryService#validateCapability(activityIds)` | BR-INT-06：可检验性校验 |
| 3 | **Pre** | `LisOrderIngressOrchestrator#group(serviceRequestIds)` | BR-INT-01/02/03：合管分组 |
| 4 | **Cmd** | `LisWorkItemDomainService#createWorkItem(CreateLisWorkItemCommand)` | **写库点**：批量创建工单 + orderLinks |
| 5 | **Asm** | 领域事件 | `LisWorkItemCreatedEvent` → 采样台感知 |

#### CreateLisWorkItemCommand 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `patientId` | `Long` | 是 | |
| `encounterId` | `Long` | 是 | |
| `specimenDefinitionId` | `Long` | 是 | |
| `barcode` | `String` | 否 | 不传自动生成 |
| `tenantId` | `Long` | 是 | |
| `orgId` | `Long` | 是 | |
| `orderLinks` | `List<CreateOrderLinkItem>` | 是 | serviceRequestId + activityId |

#### 失败码

| 失败码 | 场景 |
|--------|------|
| `DEVICE_CAPABILITY_MISSING` | 仪器能力未配 |
| `DUPLICATE_ORDER` | 已存在（幂等返回已有工单） |

---

### 3.3 IF-INT-02 签退 → 撤单

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | **Gate** | 类型判断 | |
| 2 | **Pre** | `LisWorkItemQueryService#findByServiceRequestId(srId)` | 定位 orderLink |
| 3 | **Cmd** | `LisWorkItemDomainService#cancelWorkItem(CancelWorkItemCommand)` | **写库点** |

---

## 4. QueryService 与领域事件

- **领域事件**：`LisWorkItemCreatedEvent`（签发成功后发布，采样台监听）；`LisWorkItemCancelledEvent`（撤单后发布）。
- **查询语义**：LisWorkItemQueryService 按 serviceRequestId 反查幂等。

---

## 5. 写库顺序

| IF | Cmd | 写入 |
|----|-----|------|
| INT-01 | `LisWorkItemDomainService#createWorkItem` | lis_work_item + lis_work_item_order（批量） |
| INT-02 | `LisWorkItemDomainService#cancelWorkItem` | lis_work_item（statusEnum）+ lis_observation（物理删除 preliminary） |

**事务边界**：共享医生站 sign() 事务；由 `AppServiceAspect` 管理。

---

## 6. 非功能性需求实现

### 6.1 幂等

- INT-01：按 orderLink (workItemId, serviceRequestId) 唯一约束；重复 → 返回已有。

---

## 7. 测试要点

| 场景 | 测试要点 | 优先级 |
|------|----------|:--:|
| 正向 | 签发检验医嘱 → LisWorkItem ×N，合管正确 | P0 |
| 幂等 | 重发同一医嘱 → 返回已有 | P0 |
| 异常 | activity 无 `lis_device_capability` → 拒绝 | P0 |
| 正向 | 签退 → 工单 entered-in-error | P0 |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v2.1 | 2026-06-22 | 去 AppService：不建独立 `LisIntegrationAppService`；医生站直接注入 `LisWorkItemDomainService`；合管分组收敛为 `LisOrderIngressOrchestrator`（support 类） |
| v2.0 | 2026-06-22 | 重新生成：修正事务口径；修正包路径；补充 §2.3 枚举 |
| v1.0 | 2026-06-22 | 首版 |
