# AS-PACS-INT-001 HIS接单入口 · 应用服务详细设计（SDD）

> **编码**：AS-PACS-INT-001-SDD · **版本**：v1.2 · **日期**：2026-09-20  
> **对应 AS**：AS-PACS-INT-001-HIS接单入口.md（v1.1）  
> **接线定稿（D-3）**：**直调 Domain**；不实现 `PacsAdviceApplyExtensionImpl` 建工单

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| **模块** | `zxplat/health-application`（门诊/住院医生站 + `shared.advice`） |
| **边界** | 签发检查医嘱时直调 `PacsWorkItemDomainService`；分组与 Command 组装在 `PacsOrderIngressOrchestrator` |
| **编排权威** | AS-PACS-INT-001 §2、§4 |
| **方法权威** | DM-PACS-001 / Domain SDD §6 |
| **实现阶段口径** | **目标态全量契约**；序号 10 落地 INT-01（一 SR 一工单）；同组合并增强可后续补齐 |
| **实现方式** | 无 HTTP；医生站 AppService 注入 Orchestrator；**禁止**经 Extension 创建工单 |
| **Extension** | `PacsAdviceApplyExtension` 保留 NoOp Bean；签发路径**不再调用** `submitIfImaging` |

### 术语口径

| 项 | 约定 |
|----|------|
| **事务** | `DoctorStationAdviceAppService.sign*` / 住院同构方法事务内调用 `createWorkItem` |
| **检查类 Gate** | `ServiceRequestCategoryConstants.SERVICE_CATEGORY_TEST`（`"23"`） |
| **分组（P0）** | 一签发 SR → 一 `CreatePacsWorkItemCommand`（单 orderLink）；`groupCode` 可空 |
| **分组（目标）** | 同批同 modality + 同 group 合并为一个 WorkItem（BR-INT-02） |

---

## 2. 代码结构

### 2.1 包路径

```
health-application/.../shared/advice/
└── PacsOrderIngressOrchestrator.java

health-application/.../outpatient/doctorstation/advice/service/
└── DoctorStationAdviceAppService.java          # dispatch → Orchestrator

health-application/.../inpatient/doctorstation/advice/service/
└── InpatientDoctorStationAdviceAppService.java # 同构
```

| 注入 | 类型 | 用途 |
|------|------|------|
| `PacsWorkItemDomainService` | 领域 | create / cancel |
| `ModalityCapabilityQueryService` | 查询 | 解析 modalityCode；create 内再 assert |
| `ServiceRequestQueryService` | 查询 | `mustGetListModelById` |
| `PacsOrderIngressOrchestrator` | support | Gate + 组装 + create |
| `PacsWorkItemQueryService` | 查询 | 幂等/撤单（INT-02） |

### 2.2 领域方法白名单

| 聚合 | 方法 | IF |
|------|------|-----|
| PacsWorkItem | `createWorkItem(CreatePacsWorkItemCommand)` | INT-01 |
| PacsWorkItem | `cancelWorkItem` / `cancelOrder` | INT-02 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|-------------|-------------|------|
| WorkItem 状态 | `PacsWorkItemStatus` | DM-PACS-001 §3.3 | registered / scheduled / … |
| 诊疗类目 | `ServiceRequestCategoryConstants.SERVICE_CATEGORY_TEST` | DM-WKF-004 | `"23"` 检查 |

---

## 3. 按接口 ID 命名展开

### 3.1 编排约定

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | categoryEnum = 23 | 跳过（非检查） |
| **Pre** | 读 SR；capability / modality | 业务异常 → 回滚签发 |
| **Cmd** | `PacsWorkItemDomainService#createWorkItem` | 回滚 |
| **Asm** | 领域事件（Domain 内发布） | — |

### 3.2 IF-PACS-INT-01（序号 10）

**调用入口**：

| 类 | 方法 | 说明 |
|----|------|------|
| `DoctorStationAdviceAppService` | `dispatchLabOrImagingAdvice` | 门诊签发诊疗后 |
| `InpatientDoctorStationAdviceAppService` | `dispatchLabOrImagingAdvice` | 住院同构 |
| `PacsOrderIngressOrchestrator` | `acceptSignedImagingServiceRequest` | 唯一建工单入口 |

| 序号 | 阶段 | 调用 | 说明 |
|------|------|------|------|
| 1 | Gate | `ServiceRequestQueryService#mustGetListModelById` + category=23 | 非 23 直接 return |
| 2 | Pre | 校验 patient/encounter/activity/org | 缺参抛 `PACS_WORK_ITEM_INVALID_ARGUMENT` |
| 3 | Pre | `ModalityCapabilityQueryService#resolveModalityCode` | BR-INT-03；缺失抛 `PACS_MODALITY_CAPABILITY_MISSING` |
| 4 | Pre | 组装 `CreatePacsWorkItemCommand` + 单条 orderLink | P0：一 SR 一单 |
| 5 | Cmd | `PacsWorkItemDomainService#createWorkItem` | **写库点**；内置幂等 |
| 6 | Asm | `PacsWorkItemCreatedEvent` | Domain 内 beforeCommit |

#### CreatePacsWorkItemCommand 字段映射

| 字段 | 来源 |
|------|------|
| patientId / encounterId | 签发上下文（与 SR 校验一致） |
| organizationId | `ServiceRequestListModel.orgId`；空则回落签发科室 orgId |
| tenantId | `TenantUtils.getCurrentTenantId()` |
| modalityCode | capability 解析 |
| operatorId | 签发 operatorId |
| orderLinks[0].serviceRequestId | SR.id |
| orderLinks[0].activityId | SR.activityId |
| orderLinks[0].sortNumber | SR.sortNumber |

| 失败码 | 场景 |
|--------|------|
| `PACS_MODALITY_CAPABILITY_MISSING` | 能力未配 |
| `PACS_WORK_ITEM_INVALID_ARGUMENT` | 参数不全 |

### 3.3 IF-PACS-INT-02

**调用入口**：门诊/住院 `withdraw*AdviceBatch` 诊疗分支；皮试 companion 签退（`DoctorStationSkinTestSyncOrchestrator`）。

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Pre | `PacsWorkItemQueryService#findOptionalByServiceRequestId`（无则跳过） |
| 2 | Cmd | `PacsWorkItemDomainService#cancelOrder` |
| 3 | Cmd | 无剩余 active link → `cancelWorkItem`（BR-INT-05） |

### 3.4 IF-PACS-INT-03

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Pre | — |
| 2 | Read | `PacsOrderIngressOrchestrator#findStatusByServiceRequestId` → `Optional<PacsWorkItemDetailQueryModel>` |

### 3.5 IF-PACS-INT-07

| 序号 | 阶段 | 调用 |
|------|------|------|
| 1 | Pre | `PacsOrderIngressOrchestrator#assertActivitiesExecutable` 或签发路径内 `resolveModalityCode` |
| 2 | 失败 | `PACS_MODALITY_CAPABILITY_MISSING`（**拒绝**，非仅告警） |

> 序号 11+ 成码；本 SDD 保留契约。
---

## 4. QueryService 与领域事件

- `PacsWorkItemCreatedEvent` / `PacsWorkItemCancelledEvent`
- 幂等：`createWorkItem` 内按 `serviceRequestId` 查 orderLink
- 读侧列表租户：`SpecificationUtils.tenantFilter()`（不在 SearchQuery 上传 tenantId）

---

## 5. 写库顺序

| IF | 写入 |
|----|------|
| INT-01 | `pacs_work_item` + `pacs_work_item_order` |
| INT-02 | orderLink cancelled；必要时 WorkItem entered-in-error |

事务：共享医生站 sign UoW。

---

## 6. 非功能性需求实现

### 6.1 幂等

orderLink 唯一约束 + Domain 幂等返回已有工单。

### 6.2 与 Extension 关系

| 路径 | 国基首版 |
|------|----------|
| 建工单 | Orchestrator → Domain |
| `PacsAdviceApplyExtension.submitIfImaging` | **不调用** |
| NoOp Bean | 保留，避免缺 Bean |

---

## 7. 测试要点

| 场景 | 优先级 |
|------|:--:|
| category=23 签发 → WorkItem registered | P0 |
| category=22/21 → 不建工单 | P0 |
| 无 capability 拒绝 | P0 |
| 幂等重复签发 | P0 |
| 签退撤单 | P1（后续序号） |

---

## 8. 变更历史

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-27 | 首版；对齐 AS-LIS-INT-001-SDD 结构 |
| v1.1 | 2026-09-20 | D-3：锁定直调 Domain；序号 10 入口与 Command 映射；废止 ExtensionImpl 建工单 |
| v1.2 | 2026-09-20 | 序号 11～14：INT-02/03/07 入口与联调脚本 |
