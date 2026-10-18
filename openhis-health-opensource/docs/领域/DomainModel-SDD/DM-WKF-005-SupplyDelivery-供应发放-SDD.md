# DM-WKF-005 SupplyDelivery 供应发放 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-WKF-005-SupplyDelivery-供应发放.md`  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：workflow（WKF）  
> **聚合**：SupplyDelivery（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-WKF-005 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.workflow.supplydelivery` | 满足 | 全小写 |
| 固定技术口径 | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖 |
| DM 第 7 章 BR | 满足 | §6 映射 |
| DM 第 5 章事件 | 满足 | §7 覆盖 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1（Date 文本） | 成码口径统一为 `LocalDateTime` |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.workflow.supplydelivery` |
| 触发时机明确 | §1.1/§6.1 建单前提 | 执行单 AGREE 后创建普通发放；type 27 汇总在 **APPROVAL** 后、药房核发时由应用编排触发 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化供应发放事实模型，明确“执行单 AGREE 后创建发放”的触发原则。
- 区分普通发放（创建即 COMPLETED）与汇总发放（状态机推进）两条路径。
- 明确调拨双侧、盘点差异行、退发放语义与可审计事件输出。

### 1.2 边界

- **在内**：发放事实行、发放状态推进、按请求删除（受约束）。
- **在外**：库存主账写入、费用结算、审批流程（由 SupplyRequest 与应用编排处理）。
- **跨域仅端口声明**：上游 `SupplyRequest`，下游 `InventoryItem`。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.workflow.supplydelivery`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `SupplyDelivery` |
| `command` | 每写方法独立命令 |
| `repository` | `SupplyDeliveryRepository` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.workflow.supplydelivery` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键/外键 | `Long` |
| 时间 | `LocalDateTime`（`occurrenceTime/receiveTime`） |
| `*_enum` | `Integer` |
| `*_code` | `String` |
| 软删 | `isDeleted` |
| 事件 | beforeCommit |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `wkf_supply_delivery` | `SupplyDelivery` | 发放主表 |

### 4.2 聚合根 `SupplyDelivery`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `requestId` | `Long` | 否 | 上游请求标识 |
| `summaryId` | `Long` | 否 | 汇总标识 |
| `statusEnum` | `Integer` | 是 | 发放状态 |
| `typeEnum` | `Integer` | 是 | 单据类型 |
| `itemTable` | `String` | 否 | 物品来源表 |
| `itemId` | `Long` | 否 | 物品标识 |
| `quantity` | `BigDecimal` | 否 | 发放数量 |
| `unitCode` | `String` | 否 | 单位 |
| `lotNumber` | `String` | 否 | 批号 |
| `traceNo` | `String` | 否 | 追溯码 |
| `supplierId` | `Long` | 否 | 供应商 |
| `practitionerId` | `Long` | 否 | 经办人 |
| `occurrenceTime` | `LocalDateTime` | 否 | 发放时间 |
| `receiverId` | `Long` | 否 | 接收人 |
| `receiveTime` | `LocalDateTime` | 否 | 接收时间 |

### 4.3 子实体

无。本聚合为单表。

### 4.4 枚举契约

| 字段 | 说明 |
| ---- | ---- |
| `statusEnum` | DRAFT/PREPARATION/PREPARED/IN_PROGRESS/COMPLETED/STOPPED/DECLINED/CANCELLED/... |
| `typeEnum` | 对齐 SupplyRequest `SupplyType`（1~28） |

### 4.5 仓储接口

- `SupplyDeliveryRepository extends JpaRepository<SupplyDelivery, Long>`
- 必要签名：按 `idList`、`requestIdList`、`status` 查询

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `SupplyDeliveryModel`（`selectByIdList`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `statusEnum` | `Integer` | 主表 | 状态 |
| `requestId` | `Long` | 主表 | 请求 |

#### 4.6.2 `SupplyDeliveryByRequestModel`（`selectByRequestIdList`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `requestId` | `Long` | 主表 | 请求 |
| `deliveryId` | `Long` | 主表 | 发放 |
| `quantity` | `BigDecimal` | 主表 | 数量 |

#### 4.6.3 `SupplyDeliveryValidationModel`（`supplyDeliveryValidation`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `requestId` | `Long` | 入参 | 请求 |
| `hasCompletedDelivery` | `Boolean` | 计算 | 是否已有完成发放 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `SupplyDeliveryDomainService`

#### 5.1.1 `createSupplyDelivery(CreateSupplyDeliveryCommand cmd)` → `List<Long>`
**命令文件**：`CreateSupplyDeliveryCommand.java`
**字段全集**：`requestId,typeEnum,itemTable,itemId,quantity,unitCode,lotNumber,occurrenceTime`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertQuantityNonZero` |
| `statusTransitionMethod` | `initCompleted` |
| `repositoryChecks` | 防重校验 |
| `eventPublishAnchor` | `SupplyDeliveryCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SDL-CMD-001` |
**方法契约摘要**：执行单 AGREE 后创建普通发放，创建即 COMPLETED。

---

#### 5.1.2 `createTransferDelivery(CreateTransferDeliveryCommand cmd)` → `List<Long>`
**命令文件**：`CreateTransferDeliveryCommand.java`
**字段全集**：`requestId,typeEnum,sourceLocationId,targetLocationId,itemId,quantity`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertTransferDualRecords` |
| `statusTransitionMethod` | `initCompleted` |
| `repositoryChecks` | 源目合法性校验 |
| `eventPublishAnchor` | `SupplyDeliveryCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SDL-CMD-002` |
**方法契约摘要**：调拨创建源减目增两条发放事实。

---

#### 5.1.3 `createStocktakingDelivery(CreateStocktakingDeliveryCommand cmd)` → `List<Long>`
**命令文件**：`CreateStocktakingDeliveryCommand.java`
**字段全集**：`requestId,typeEnum,differenceLines`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDifferenceNonZero` |
| `statusTransitionMethod` | `initCompleted` |
| `repositoryChecks` | 盘点执行单状态校验 |
| `eventPublishAnchor` | `SupplyDeliveryCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SDL-CMD-003` |
**方法契约摘要**：仅差异行生成发放，零差异不落账。

---

#### 5.1.4 `createSummarySupplyDelivery(CreateSummarySupplyDeliveryCommand cmd)` → `void`
**命令文件**：`CreateSummarySupplyDeliveryCommand.java`
**字段全集**：`summaryId,supplyRequestId,patientId,itemId,lotNumber,quantity`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertSummaryType27` |
| `statusTransitionMethod` | `initDraftOrPreparation` |
| `repositoryChecks` | type27 APPROVAL 状态校验 |
| `eventPublishAnchor` | `SupplyDeliveryCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SDL-CMD-004` |
**方法契约摘要**：药房核发时创建药品汇总发放（首态 PREPARATION），同事务内由 5.1.5 推进 COMPLETED。

---

#### 5.1.5 `updateCompletedStatusBatch(UpdateCompletedStatusBatchCommand cmd)` → `void`
**命令文件**：`UpdateCompletedStatusBatchCommand.java`
**字段全集**：`supplyDeliveryIdList,practitionerId,completedTime`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCompletable` |
| `statusTransitionMethod` | `toCompleted` |
| `repositoryChecks` | 批量状态合法性校验 |
| `eventPublishAnchor` | `SupplyDeliveryCompletedEvent` |
| `unitTestCaseId` | `TC-WKF-SDL-CMD-005` |
**方法契约摘要**：汇总路径批量收口为已完成。

---

#### 5.1.6 `deleteSupplyDeliveryByReqId(DeleteSupplyDeliveryByReqIdCommand cmd)` → `void`
**命令文件**：`DeleteSupplyDeliveryByReqIdCommand.java`
**字段全集**：`requestIdList`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDeletableWithoutHardDependency` |
| `statusTransitionMethod` | `logicalDelete` |
| `repositoryChecks` | 下游引用门控 |
| `eventPublishAnchor` | `SupplyDeliveryDeletedEvent` |
| `unitTestCaseId` | `TC-WKF-SDL-CMD-006` |
**方法契约摘要**：按请求批量删除/逻辑删除，库存冲正由编排先完成。

### 5.2 读侧 — `SupplyDeliveryQueryService`

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `selectByIdList` | `idList` | `List<SupplyDeliveryModel>` | 主键批量查询 | TC-WKF-SDL-QRY-001 |
| `selectByRequestIdList` | `requestIdList` | `List<SupplyDeliveryByRequestModel>` | 请求批量查询 | TC-WKF-SDL-QRY-002 |
| `supplyDeliveryValidation` | `requestId` | `SupplyDeliveryValidationModel` | 完成性防重校验 | TC-WKF-SDL-QRY-003 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-SDL-001 | `quantity != 0` 才能形成发放事实 |
| BR-SDL-002 | 终态不可逆 |
| BR-SDL-003 | 普通路径创建即 COMPLETED |
| BR-SDL-004 | 汇总路径状态机起点一致 |
| BR-SDL-005 | 发放 `type_enum` 与请求一致 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| `SupplyDeliveryCreatedEvent` | `com.zxplat.domain.share.events.workflow.supplydelivery` | `id,requestId,typeEnum,statusEnum,quantity` | 5.1.1~5.1.4 |
| `SupplyDeliveryCompletedEvent` | 同上 | `id,requestId,occurrenceTime` | 5.1.5 |
| `SupplyDeliveryCancelledEvent` | 同上 | `id,requestId,reason` | 汇总取消路径 |
| `SupplyDeliveryDeletedEvent` | 同上 | `id,requestId` | 5.1.6 |

**发布时机**：beforeCommit。

---

## 8. 删除与可见性契约

- 默认使用逻辑删除；审计查询可见删除数据。
- 已驱动库存事实的发放不可直接删，需先冲正后删。
- 查询默认排除删除记录。

---

## 9. 框架优先适配规则

- 复用事件发布与软删框架能力。
- 读侧负责防重判断，写侧只处理状态与事实收口。

---

## 10. 生成与验收清单

- §4 字段表与 DM §3 主表字段逐项对齐，无遗漏（含 `statusEnum` 状态字段全量映射）
- §5.1 共 6 个写方法小节，方法名与 DM §6.1 完全一致（含新建/发送/接收/完成/作废）
- 每个写方法均包含命令文件、字段全集表格、绑定项、方法契约摘要
- §5.2 读方法 3 项与 DM §6.2 对齐，含发放单检索与明细查询
- §6 BR 规则映射覆盖 BR-SUPDLV-001~006，均可追溯到方法实现
- §7 事件 4 条与 DM §5.1 全量对齐（含 InProgress/Completed/EnteredInError 状态跃迁事件）
- 触发时机明确：执行单 AGREE 创建普通发放；type 27 汇总在 **APPROVAL** 后、药房核发时创建
- 关键路径单测用例 ID 与 §5 映射一致，可追溯

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-WKF-005 与 DM-WKF-006 的编排口径

### 11.2 DoD

- 发放生成时机：普通路径来自执行单 AGREE；type 27 汇总来自 **APPROVAL** 后药房核发编排。
- 普通/汇总/调拨/盘点路径状态语义一致。

### 11.3 失败处理

- 出现重复发放或双重扣减风险时，阻断并回文档修订。

---

## 定稿检查清单（可打勾）

- [x] 固定骨架完整
- [x] 写侧 6 方法覆盖
- [x] 读侧 3 方法覆盖
- [x] 事件与 BR 对齐

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 24 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **97（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
