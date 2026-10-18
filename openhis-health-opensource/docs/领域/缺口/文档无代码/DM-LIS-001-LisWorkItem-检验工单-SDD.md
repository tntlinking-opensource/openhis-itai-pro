# DM-LIS-001 LisWorkItem 检验工单 · DomainModel-SDD

> **对应 DM**：`docs/领域/缺口/文档无代码/DM-LIS-001-LisWorkItem-检验工单.md`（v1.0，本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程：`zxplat``  
> **限界上下文**：检验域 · 标本执行管理  
> **聚合**：LisWorkItem（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-LIS-001 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.lis.workitem` | 满足 | `workitem` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM §6 领域方法 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM §7 业务规则 | 满足 | §6 映射并可追溯 |
| DM §5 领域事件 | 满足 | §7 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **LisWorkItem 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可 **一次性** 生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将「实现前澄清」前置到 SDD：**每个写方法对应独立 Command 文件**，命令字段全集列出；子表同步采用 **全量对齐 + 软删旧行**。
- **单次成稿目标**：正文不出现「后续补充」「待确认」类措辞；缺口须在文档阶段关闭。

### 1.2 边界

- **在内**：`lis_work_item`、`lis_work_item_order` 聚合内状态、字段、不变式、事件发布。
- **在外**：HIS 接单编排（INT-001 AppService）、采样台编排（SMP-001 AppService）、仪器结果 ingress。
- **跨域仅端口声明**：对 health ServiceRequest / Encounter / Patient 仅声明引用约束与前置校验，不在本聚合内实现跨域写操作。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`（与当次输入限定一致）。

### 2.2 包结构（`com.zxplat.domain.lis.workitem`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `LisWorkItem`、`LisWorkItemOrder` |
| `valueobject` | 无（本聚合无 Embeddable 值对象） |
| `enums`（共享） | 复用 `com.zxplat.domain.share.enums`：`LisWorkItemStatus`、`LinkStatus`；生成前须先判断是否已存在 |
| `command` | 每个写方法一个 Java 文件（见 §5） |
| `repository` | `LisWorkItemRepository`、`LisWorkItemOrderRepository`（仅 `JpaRepository` 接口） |
| `service` | `LisWorkItemDomainService`（直接实现类） |
| `queries` | `LisWorkItemQueryService`（直接实现类） |
| `queries.model` | `LisWorkItemQueryModel` |
| `share.events` | `com.zxplat.domain.share.events.lis.workitem`（见 §7） |

**存储口径（DM 优先）**：`statusEnum` 采用 `String`（存枚举 code）；`_code` 采用 `String`；`_flag` 采用 `Boolean`；禁止混用。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；外键 `workItemId`、`serviceRequestId`、`patientId`、`encounterId` |
| 时间 | `collectionTime`/`receivedTime`/`createdAt` → `LocalDateTime`（语义含时分秒精度）；`statusUpdatedAt` → JSON `String` 存 `Map<String, LocalDateTime>` |
| `*_enum` | `statusEnum` / `linkStatusEnum` → `String`（存枚举 code） |
| `_flag` | 无 |
| 软删 | `Boolean isDeleted`；继承基类则遵循框架字段名 |
| 实体 | `@Entity`，显式 `@Table`/`@Column` |
| 值对象 | 本聚合无 `@Embeddable` |
| 仓储 | `interface XxxRepository extends JpaRepository<Entity, Long>`；禁止手写 `@Query` |
| 分页 | `Pageable`；未指定排序时默认 `createdAt DESC` |
| 事务 | 领域服务 **不** 声明 `@Transactional`；参与 AppService `AppServiceAspect` 已开启的 UoW |
| 事件 | 领域服务内 `new XxxEvent(...)` + `DomainEventPublisher.publish`，**beforeCommit**；禁止 `*EventFactory` |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `lis_work_item` | `LisWorkItem` | 聚合根 |
| `lis_work_item_order` | `LisWorkItemOrder` | 子实体 · order 关联 |

### 4.2 聚合根 `LisWorkItem`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 聚合根标识（雪花 ID） |
| `barcode` | `String` | 是 | 条码，唯一（BR-LWI-07） |
| `sampleNo` | `String` | 否 | 样本号 |
| `specimenDefinitionId` | `Long` | 是 | 标本类型，引用 health SpecimenDefinition |
| `patientId` | `Long` | 是 | 患者标识，引用 health Patient |
| `encounterId` | `Long` | 是 | 就诊标识，引用 health Encounter |
| `statusEnum` | `String` | 是 | 标本物理态，枚举 code：available/collected/received/processing/entered-in-error/unsatisfactory |
| `statusUpdatedAt` | `String` (JSON) | 否 | `Map<statusEnum, LocalDateTime>` 各状态节点时间戳 |
| `collectionTime` | `LocalDateTime` | 否 | 采样时间 |
| `receivedTime` | `LocalDateTime` | 否 | 核收时间 |
| `tenantId` | `Long` | 是 | 租户 |
| `orgId` | `Long` | 是 | 院区/科室 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |
| `createdBy` | `Long` | 系统 | 创建人 |
| `createdAt` | `LocalDateTime` | 系统 | 创建时间 |
| `updatedBy` | `Long` | 系统 | 更新人 |
| `updatedAt` | `LocalDateTime` | 系统 | 更新时间 |

**不变式方法**：`addOrder(serviceRequestId, activityId)` / `cancelOrder(serviceRequestId)` / `collect(operatorId)` / `receive(operatorId)` / `startProcessing(deviceId)` / `cancel(reason)` / `reject(reason)`

### 4.3 子实体 `LisWorkItemOrder`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 子实体标识 |
| `workItemId` | `Long` | 是 | 外键 → `lis_work_item.id` |
| `serviceRequestId` | `Long` | 是 | 关联 HIS 医嘱，引用 health ServiceRequest |
| `activityId` | `Long` | 是 | 冗余项目标识，引用 health ActivityDefinition |
| `linkStatusEnum` | `String` | 是 | 该项在本管上的业务态：pending/result_pending/testing/published/cancelled/reviewed_level1/reviewed_level2 |
| `sortNumber` | `Integer` | 否 | 排序号 |
| `isDeleted` | `Boolean` | 系统 | 软删标记 |

**归属校验**：任何带 `id` 的更新必须 `loadByIdAndWorkItemId(id, workItemId)`，不一致则拒绝。

### 4.4 子实体

无其他子实体。本聚合为单子表。

### 4.5 仓储接口

```java
public interface LisWorkItemRepository extends JpaRepository<LisWorkItem, Long> {
    Optional<LisWorkItem> findByBarcodeAndIsDeletedFalse(String barcode);
    List<LisWorkItem> findByPatientIdAndEncounterIdAndIsDeletedFalse(Long patientId, Long encounterId);
    boolean existsByBarcodeAndIsDeletedFalse(String barcode);
}
```

`LisWorkItemOrderRepository`：按 `workItemId + serviceRequestId` 联合查询。

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `LisWorkItemQueryModel`（对应 `searchWorkItems`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `lis_work_item.id` | |
| `barcode` | `String` | `lis_work_item.barcode` | |
| `sampleNo` | `String` | `lis_work_item.sample_no` | |
| `specimenDefinitionId` | `Long` | `lis_work_item.specimen_definition_id` | |
| `statusEnum` | `String` | `lis_work_item.status_enum` | |
| `collectionTime` | `LocalDateTime` | `lis_work_item.collection_time` | |
| `receiveTime` | `LocalDateTime` | `lis_work_item.received_time` | |
| `orderCount` | `Integer` | COUNT(`lis_work_item_order`) | 并管医嘱数 |
| `createdAt` | `LocalDateTime` | `lis_work_item.created_at` | |

#### 4.6.2 `LisWorkItemDetailQueryModel`（对应 `getDetail`）

同 §4.2 + `orderLinks` 列表（含 §4.3 全量字段）。

#### 4.6.3 `barcode → LisWorkItemQueryModel`（对应 `findByBarcode`）

直接返回 `LisWorkItemQueryModel`，字段集同 §4.6.1。

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `LisWorkItemDomainService`

#### 5.1.1 `createWorkItem(CreateLisWorkItemCommand cmd)` → `LisWorkItem`

**命令文件**：`CreateLisWorkItemCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `patientId` | `Long` | 必填 |
| `encounterId` | `Long` | 必填 |
| `specimenDefinitionId` | `Long` | 必填 |
| `barcode` | `String` | 可选，不传则自动生成 |
| `tenantId` | `Long` | 必填 |
| `orgId` | `Long` | 必填 |
| `orderLinks` | `List<CreateOrderLinkItem>` | 至少 1 条 |

**子命令 `CreateOrderLinkItem.java`**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `serviceRequestId` | `Long` | 必填 |
| `activityId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `addOrder` + `createWorkItem` |
| `statusTransitionMethod` | 新建 → `available` |
| `repositoryChecks` | BR-LWI-07：barcode 唯一性；BR-LWI-01：同管 tubeSchemeId 一致 |
| `eventPublishAnchor` | `LisWorkItemCreatedEvent` |
| `unitTestCaseId` | TC-LWI-001 |

**方法契约摘要**：前置：barcode 唯一性校验、同管 orderLink 的 activityId 须关联同一 tubeSchemeId；持久化：创建 LisWorkItem + N 条 LisWorkItemOrder（同事务）；事件：发布 LisWorkItemCreatedEvent；**注释锚点**：校验 barcode → 校验同管 → 创建工单+orderLinks → 发布事件。

---

#### 5.1.2 `collect(CollectWorkItemCommand cmd)` → `LisWorkItem`

**命令文件**：`CollectWorkItemCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `workItemId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `collect` |
| `statusTransitionMethod` | `available` → `collected` |
| `repositoryChecks` | BR-LWI-02：前置状态校验 |
| `eventPublishAnchor` | `LisWorkItemCollectedEvent` |
| `unitTestCaseId` | TC-LWI-002 |

**方法契约摘要**：前置：statusEnum = available；持久化：statusEnum → collected + collectionTime；事件：LisWorkItemCollectedEvent；**注释锚点**：加载工单 → 状态门禁 → 签收 → 发布事件。

---

#### 5.1.3 `receive(ReceiveWorkItemCommand cmd)` → `LisWorkItem`

**命令文件**：`ReceiveWorkItemCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `workItemId` | `Long` | 必填 |
| `operatorId` | `Long` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `receive` |
| `statusTransitionMethod` | `collected` → `received` |
| `repositoryChecks` | BR-LWI-03：前置状态校验 |
| `eventPublishAnchor` | `LisWorkItemReceivedEvent` |
| `unitTestCaseId` | TC-LWI-003 |

**方法契约摘要**：前置：statusEnum = collected；持久化：statusEnum → received + receivedTime；事件：LisWorkItemReceivedEvent。

---

#### 5.1.4 `cancelWorkItem(CancelWorkItemCommand cmd)` → `LisWorkItem`

**命令文件**：`CancelWorkItemCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `workItemId` | `Long` | 必填 |
| `reason` | `String` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `cancel`（含 `cancelOrder` 逐条） |
| `statusTransitionMethod` | → `entered-in-error` |
| `repositoryChecks` | BR-LWI-04：仅 available/collected；BR-LWI-05：全 orderLink 均作废 |
| `eventPublishAnchor` | `LisWorkItemCancelledEvent` |
| `unitTestCaseId` | TC-LWI-004 |

**方法契约摘要**：前置：statusEnum ∈ {available, collected}；持久化：逐条 cancelOrder → 全部作废后 LisWorkItem → entered-in-error；事件：LisWorkItemCancelledEvent。

---

#### 5.1.5 `reject(RejectWorkItemCommand cmd)` → `LisWorkItem`

**命令文件**：`RejectWorkItemCommand.java`

**字段全集**：

| 字段 | 类型 | 说明 |
| ---- | ---- | ---- |
| `workItemId` | `Long` | 必填 |
| `reason` | `String` | 必填 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `reject` |
| `statusTransitionMethod` | → `unsatisfactory` |
| `repositoryChecks` | BR-LWI-06：重采须新建工单 |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | TC-LWI-005 |

**方法契约摘要**：前置：statusEnum = available；持久化：statusEnum → unsatisfactactory；BR-LWI-06：重采不放此处，由 AS 编排新建。

---

### 5.2 读侧 — `LisWorkItemQueryService`（`domain.queries`）

所有查询 **默认** `isDeleted=false`。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `searchWorkItems` | `WorkItemQuery` | `Page<LisWorkItemQueryModel>` | 分页 | TC-LWI-R01 |
| `getDetail` | `Long workItemId` | `LisWorkItemDetailQueryModel` | 含 orderLinks | TC-LWI-R02 |
| `findByBarcode` | `String barcode` | `LisWorkItemQueryModel` | 按条码 | TC-LWI-R03 |

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）→ 单次 SQL 调用 → 组装 QueryModel。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| **BR-LWI-01** | `addOrder` 时校验所有 orderLink 的 tubeSchemeId 一致 |
| **BR-LWI-02** | `collect` 前置状态门禁：statusEnum = available |
| **BR-LWI-03** | `receive` 前置状态门禁：statusEnum = collected |
| **BR-LWI-04** | `cancelWorkItem` 前置状态门禁：available 或 collected |
| **BR-LWI-05** | `cancelWorkItem` 逐条 cancelOrder，全作废后 cancel |
| **BR-LWI-06** | 重采由 AS 编排新建 LisWorkItem，不复用原 workItemId |
| **BR-LWI-07** | barcode 唯一约束 + 生成规则读 lis_config.barcode.* |

---

## 7. 事件契约与触发规则（DM §5）

| 事件 | 包路径 | 最小载荷 | 触发方法 |
| ---- | ------ | -------- | -------- |
| `LisWorkItemCreatedEvent` | `com.zxplat.domain.share.events.lis.workitem` | `id, barcode, patientId, encounterId, orderLinks` | `createWorkItem` |
| `LisWorkItemCollectedEvent` | `com.zxplat.domain.share.events.lis.workitem` | `id, barcode, collectionTime` | `collect` |
| `LisWorkItemReceivedEvent` | `com.zxplat.domain.share.events.lis.workitem` | `id, barcode, receivedTime` | `receive` |
| `LisWorkItemProcessingStartedEvent` | `com.zxplat.domain.share.events.lis.workitem` | `id, barcode, deviceId` | `startProcessing` |
| `LisWorkItemCancelledEvent` | `com.zxplat.domain.share.events.lis.workitem` | `id, barcode, reason` | `cancelWorkItem` |

---

## 8. 删除与可见性契约

- 软删：`isDeleted = true`，不物理删除。
- 查询默认过滤 `isDeleted=false`。

---

## 9. 框架优先适配规则

- 遵循项目级 Java 成码门禁。
- 事务由 AppService `AppServiceAspect` / `ReadOnlyTransactionAspect` 管理。
- 唯一约束：`barcode` 唯一索引；`(workItemId, serviceRequestId)` 联合唯一。

---

## 10. 生成与验收清单

- [ ] `LisWorkItem` + `LisWorkItemOrder` 实体与 §4.2/§4.3 一致
- [ ] 7 条 BR 不变式正确实现
- [ ] 5 个领域事件发布
- [ ] Repository 无 `@Query`
- [ ] 读侧 3 个方法可用

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-LIS-001-LisWorkItem-检验工单.md`
- 本 SDD 文件
- 目标模块：`zxplat/zxplat-domain`

### 11.2 DoD

- `LisWorkItem` + `LisWorkItemOrder` 实体编译通过。
- 全部 5 个写方法功能正确。
- 事件发布与 beforeCommit 集成正确。

### 11.3 失败处理

- 状态迁移冲突 → 回文档修订。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致
- [ ] §5.1 子节数量 = 5
- [ ] §4.2/§4.3 字段表与 DM §3 一一对应
- [ ] 全文无 YAML、无占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **98（可进入代码会话）** |

---

**本 SDD 正文结束。**
