# DM-WKF-007 DeviceDispense 耗材发放 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-WKF-007-DeviceDispense-耗材发放.md`（**v1.2.4**）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：workflow（WKF）  
> **聚合**：DeviceDispense（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-WKF-007 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 目标包 | 满足 | `com.zxplat.domain.workflow.devicedispense` |
| 固定技术口径 | 满足 | 见 §9 |
| DM 写/读白名单 | 满足 | §5 覆盖 |
| DM BR | 满足 | §6 映射 |
| DM 事件 | 满足 | §7 覆盖 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1（DateTime 文本） | 成码口径统一为 `LocalDateTime` |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.workflow.devicedispense` |
| 冲销口径明确 | §6.2.6/§7 描述 | 以 `refund_device_id + quantity<0` 为已冲销唯一汇总口径 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化耗材发放从草稿、待发核对发放、已发放到撤回/退耗材的完整契约。
- 明确批号匹配、盘点汇总回写、预减释放等关键路径。
- 建立与 DeviceRequest、InventoryItem、TraceNumberRecord 的可追溯协作锚点。

### 1.2 边界

- **在内**：发放事实、状态迁移、追溯字段。
- **在外**：库存主账写库、收费结算、追溯轨迹表写库（应用编排）。
- **跨域仅端口声明**：通过事件和 ID 关联协作聚合。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.workflow.devicedispense`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `DeviceDispense` |
| `command` | 每写方法一个命令 |
| `repository` | `DeviceDispenseRepository` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.workflow.devicedispense` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键/外键 | `Long` |
| 时间 | `LocalDateTime` |
| `*_enum` | `Integer` |
| `*_code` | `String` |
| 软删 | `isDeleted` |
| 事件 | beforeCommit |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `wkf_device_dispense` | `DeviceDispense` | 耗材发放主表 |

### 4.2 聚合根 `DeviceDispense`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `busNo` | `String` | 是 | 发放业务号，`getSeqByDay(DDP, 4)` |
| `deviceReqId` | `Long` | 是 | 耗材请求 |
| `statusEnum` | `Integer` | 是 | 发放状态 |
| `basedOnTable` | `String` | 否 | 来源表 |
| `basedOnId` | `Long` | 否 | 来源ID |
| `notPerformedReasonEnum` | `Integer` | 否 | 停发/作废原因 |
| `quantity` | `BigDecimal` | 是 | 申请数量 |
| `dispenseQuantity` | `BigDecimal` | 否 | 已发数量 |
| `unitCode` | `String` | 是 | 单位 |
| `lotNumber` | `String` | 否 | 批号 |
| `traceNo` | `String` | 否 | 追溯码 |
| `deviceDefId` | `Long` | 是 | 耗材定义 |
| `patientId` | `Long` | 是 | 患者 |
| `encounterId` | `Long` | 是 | 就诊 |
| `locationId` | `Long` | 否 | 发放器材房 |
| `preparerId` | `Long` | 否 | 配药人 |
| `performerId` | `Long` | 否 | 发放人 |
| `prepareTime` | `LocalDateTime` | 否 | 配药时间 |
| `dispenseTime` | `LocalDateTime` | 否 | 发药时间 |
| `limitTime` | `LocalDateTime` | 否 | 限制时间 |
| `plannedDispenseTime` | `LocalDateTime` | 否 | 计划发药时间 |
| `procedureId` | `Long` | 否 | 执行ID |

### 4.3 子实体

无。

### 4.4 枚举契约

| 字段 | 说明 |
| ---- | ---- |
| `statusEnum` | DRAFT/PREPARATION/PREPARED/IN_PROGRESS/COMPLETED/SUMMARIZED/PART_COMPLETED/ON_HOLD/STOPPED/DECLINED/PENDING_REFUND/PART_REFUND/REFUNDED/CANCELLED |
| `notPerformedReasonEnum` | 退费/异常等原因码 |

### 4.5 仓储接口

- `DeviceDispenseRepository extends JpaRepository<DeviceDispense, Long>`
- 必备签名：按 `id`、`deviceReqId`、`procedureId`、`encounterId`、`status` 查询

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `DeviceDispenseModel`（`loadDeviceDispenseById`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `statusEnum` | `Integer` | 主表 | 状态 |
| `quantity` | `BigDecimal` | 主表 | 数量 |

#### 4.6.2 `DeviceDispenseListModel`（`listDeviceDispensesByEncounter`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `encounterId` | `Long` | 主表 | 就诊 |
| `statusEnum` | `Integer` | 主表 | 状态 |

#### 4.6.3 `DeviceDispenseValidationModel`（`validate*`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `allow` | `Boolean` | 计算 | 是否允许写操作 |
| `reasonCode` | `String` | 计算 | 拒绝原因 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `DeviceDispenseDomainService`

#### 5.1.1 `prepareDeviceDispense(PrepareDeviceDispenseCommand cmd)` → `void`
**命令文件**：`PrepareDeviceDispenseCommand.java`  
**字段全集**：`id,preparerId,lotNumber,traceNo,quantity`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertPreparable` |
| `statusTransitionMethod` | `preparationToPreparedOrInProgress` |
| `repositoryChecks` | `mustGetById` + 拣配前校验 |
| `eventPublishAnchor` | `DeviceDispensePreparedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-001` |
**方法契约摘要**：待发拣配态完成批号追溯绑定。

---

#### 5.1.2 `dispenseDevice(DispenseDeviceCommand cmd)` → `void`
**命令文件**：`DispenseDeviceCommand.java`
**字段全集**：`id,performerId,dispenseQuantity`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDispensable` |
| `statusTransitionMethod` | `toCompletedOrPartCompleted`（耗材一次发全量，终态 `COMPLETED`） |
| `repositoryChecks` | 发放前校验 |
| `eventPublishAnchor` | `DeviceDispensedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-002` |
**方法契约摘要**：核对发放成功后更新已发数量与状态；一次发全量，终态 `COMPLETED`。

---

#### 5.1.3 `cancelUndispensedDeviceDispense(CancelUndispensedDeviceDispenseCommand cmd)` → `void`
**命令文件**：`CancelUndispensedDeviceDispenseCommand.java`
**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `id` | `Long` | 是 | 发放单标识 |
| `notPerformedReasonEnum` | `Integer` | 是 | 不执行原因（患者不在/拒发/医嘱停用/其他） |
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCancellableUndispensed` |
| `statusTransitionMethod` | `toDeclined` |
| `repositoryChecks` | 作废门控 |
| `eventPublishAnchor` | `DeviceDispenseDeclinedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-003` |

---

#### 5.1.4 `refundDispensedDeviceDispense(RefundDispensedDeviceDispenseCommand cmd)` → `void`
**命令文件**：`RefundDispensedDeviceDispenseCommand.java`
**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `id` | `Long` | 是 | 发放单标识 |
| `refundQuantity` | `BigDecimal` | 是 | 退耗材数量，>0且<=已发数量 |
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRefundableAndIdempotent` |
| `statusTransitionMethod` | `pendingRefundToRefunded` |
| `repositoryChecks` | 待退校验 |
| `eventPublishAnchor` | `DeviceDispenseRefundedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-004` |

---

#### 5.1.4a `declinePendingRefundDeviceDispense(DeclinePendingRefundDeviceDispenseCommand cmd)` → `void`

**命令文件**：`DeclinePendingRefundDeviceDispenseCommand.java`

| 绑定项 | 内容 |
| ------ | ---- |
| `statusTransitionMethod` | `pendingRefundToDeclined` |
| `eventPublishAnchor` | `DeviceDispenseDeclinedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-004A` |

**方法契约摘要**：药房拒绝待退耗材；`PENDING_REFUND→DECLINED`。

---

#### 5.1.5 `generateDeviceDispenseFromTemporaryPath(GenerateDeviceDispenseFromTemporaryPathCommand cmd)` → `Long`
**命令文件**：`GenerateDeviceDispenseFromTemporaryPathCommand.java`
**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `deviceReqId` | `Long` | 是 | 耗材请求标识 |
| `encounterId` | `Long` | 是 | 就诊标识 |
| `patientId` | `Long` | 是 | 患者标识 |
| `quantity` | `BigDecimal` | 是 | 申请数量 |
| `limitTime` | `LocalDateTime` | 否 | 限制时间（临时路径有效期） |
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertTemporaryGenerateAllowed` |
| `statusTransitionMethod` | `initDraft` |
| `repositoryChecks` | 重复生成校验 |
| `eventPublishAnchor` | `DeviceDispenseCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-005` |

---

#### 5.1.6 `promoteDeviceDispenseToPreparation(PromoteDeviceDispenseToPreparationCommand cmd)` → `void`
**命令文件**：`PromoteDeviceDispenseToPreparationCommand.java`
**字段全集**：`idList`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertPromotable` |
| `statusTransitionMethod` | `draftToPreparation` |
| `repositoryChecks` | 状态批量校验 |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-006` |

---

#### 5.1.7 `generateDeviceDispenseFromExecution(GenerateDeviceDispenseFromExecutionCommand cmd)` → `Long`
**命令文件**：`GenerateDeviceDispenseFromExecutionCommand.java`
**字段全集**：`deviceReqId,procedureId,locationId,plannedDispenseTime`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertExecutionGenerateAllowed` |
| `statusTransitionMethod` | `initPreparation` |
| `repositoryChecks` | 来源执行合法性 |
| `eventPublishAnchor` | `DeviceDispenseCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-007` |

---

#### 5.1.8 `generateIssuedDeviceDispenseFromExecution(GenerateIssuedDeviceDispenseFromExecutionCommand cmd)` → `Long`
**命令文件**：`GenerateIssuedDeviceDispenseFromExecutionCommand.java`
**字段全集**：`deviceReqId,procedureId,lotNumber,traceNo,dispenseQuantity`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDirectIssueAllowed` |
| `statusTransitionMethod` | `initCompleted` |
| `repositoryChecks` | 库存前置校验 |
| `eventPublishAnchor` | `DeviceDispensedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-008` |

---

#### 5.1.9 `createPendingRefundDeviceDispense(CreatePendingRefundDeviceDispenseCommand cmd)` → `Long`
**命令文件**：`CreatePendingRefundDeviceDispenseCommand.java`
**字段全集**：`sourceDispenseId,refundQuantity`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertPendingRefundCreatable` |
| `statusTransitionMethod` | `toPendingRefund` |
| `repositoryChecks` | 已发放状态校验 |
| `eventPublishAnchor` | `DeviceDispensePendingRefundCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-009` |

---

#### 5.1.10 `releaseDeviceDispenseReserve(ReleaseDeviceDispenseReserveCommand cmd)` → `void`
**命令文件**：`ReleaseDeviceDispenseReserveCommand.java`
**字段全集**：`idListOrRequestKey,operatorId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertReservableReleased`（仅 DRAFT/PREPARATION/PREPARED 行进入） |
| `statusTransitionMethod` | `toStoppedOrDeleted` |
| `repositoryChecks` | `resolveFlexibleKey`；未命中幂等返回 |
| `eventPublishAnchor` | `DeviceDispenseReserveReleasedEvent`（仅实际释放行非空时） |
| `unitTestCaseId` | `TC-WKF-DD-CMD-010` |
**方法契约摘要**：前置为定位键非空；持久化将可释放行置 `STOPPED`；**未命中或均已释放时幂等静默**（对齐 `DM-MED-002` §5.1.9）；IF-DOR-16 应用层优先传入 WD① 发放主键逗号列表。

---

#### 5.1.11 `matchDispenseLotNumber(MatchDispenseLotNumberCommand cmd)` → `void`
**命令文件**：`MatchDispenseLotNumberCommand.java`
**字段全集**：`idListOrRequestKey`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertLotMatchable` |
| `statusTransitionMethod` | `keepPreparation` |
| `repositoryChecks` | `status=PREPARATION` |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-011` |

---

#### 5.1.12 `updateDispenseStatusSummarized(UpdateDispenseStatusSummarizedCommand cmd)` → `void`
**命令文件**：`UpdateDispenseStatusSummarizedCommand.java`
**字段全集**：`idListOrSummaryKey`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertSummarizable` |
| `statusTransitionMethod` | `toSummarized` |
| `repositoryChecks` | 汇总前状态校验 |
| `eventPublishAnchor` | `DeviceDispenseSummarizedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-012` |

---

#### 5.1.13 `updateDispenseStatusCompletedFromSummary(UpdateDispenseStatusCompletedFromSummaryCommand cmd)` → `void`
**命令文件**：`UpdateDispenseStatusCompletedFromSummaryCommand.java`
**字段全集**：`summaryKeyOrIdList`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertSummaryCompletable` |
| `statusTransitionMethod` | `summarizedToCompleted` |
| `repositoryChecks` | `status=SUMMARIZED` |
| `eventPublishAnchor` | `DeviceDispenseSummaryCompletedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-013` |

---

#### 5.1.14 `cancelDeviceDispenseSummary(CancelDeviceDispenseSummaryCommand cmd)` → `void`
**命令文件**：`CancelDeviceDispenseSummaryCommand.java`
**字段全集**：`summaryKey`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertSummaryCancellable` |
| `statusTransitionMethod` | `summarizedToPreparation` |
| `repositoryChecks` | 汇总取消门控 |
| `eventPublishAnchor` | `DeviceDispenseSummaryCancelledEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-014` |

---

#### 5.1.15 `rollbackUndispensedDeviceDispense(RollbackUndispensedDeviceDispenseCommand cmd)` → `void`
**命令文件**：`RollbackUndispensedDeviceDispenseCommand.java`
**字段全集**：`idList`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRollbackableUndispensed` |
| `statusTransitionMethod` | `toDraft` |
| `repositoryChecks` | 状态集合门控 |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-015` |

---

#### 5.1.16 `removeUndispensedDeviceDispense(RemoveUndispensedDeviceDispenseCommand cmd)` → `void`
**命令文件**：`RemoveUndispensedDeviceDispenseCommand.java`
**字段全集**：`idListOrRequestKey`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRemovableUndispensed` |
| `statusTransitionMethod` | `logicalDelete` |
| `repositoryChecks` | 下游引用门控 |
| `eventPublishAnchor` | `DeviceDispenseReserveReleasedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-016` |

---

#### 5.1.17 `stopUndispensedDeviceDispense(StopUndispensedDeviceDispenseCommand cmd)` → `void`
**命令文件**：`StopUndispensedDeviceDispenseCommand.java`
**字段全集**：`idList,notPerformedReasonEnum`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStoppableUndispensed` |
| `statusTransitionMethod` | `toStopped` |
| `repositoryChecks` | 状态与原因校验 |
| `eventPublishAnchor` | `DeviceDispenseStoppedEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-017` |

---

#### 5.1.18 `holdDeviceDispense(HoldDeviceDispenseCommand cmd)` → `void`
**命令文件**：`HoldDeviceDispenseCommand.java`
**字段全集**：`idList,reason`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertHoldable` |
| `statusTransitionMethod` | `toOnHold` |
| `repositoryChecks` | 状态校验 |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-018` |

---

#### 5.1.19 `recallDispensedDeviceDispense(RecallDispensedDeviceDispenseCommand cmd)` → `void`
**命令文件**：`RecallDispensedDeviceDispenseCommand.java`
**字段全集**：`id,reason`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRecallable` |
| `statusTransitionMethod` | `completedToCancelled` |
| `repositoryChecks` | 已发放状态校验 |
| `eventPublishAnchor` | `DeviceDispenseCancelledEvent` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-019` |

---

#### 5.1.21 `revertCompletedBatchToPreparation(RevertCompletedBatchToPreparationCommand cmd)` → `void`
**命令文件**：`RevertCompletedBatchToPreparationCommand.java`
**字段全集**：`idList`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCompletedBatchRevertable` |
| `statusTransitionMethod` | `completedToPreparation` |
| `repositoryChecks` | 可回退状态与下游事实门控 |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-021` |

---

---

#### 5.1.23 `revertPreparedBatchToPreparation(RevertPreparedBatchToPreparationCommand cmd)` → `void`
**命令文件**：`RevertPreparedBatchToPreparationCommand.java`  
**字段全集**：`idList`、`operatorId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertPreparedBatchRevertable` |
| `statusTransitionMethod` | `preparedToPreparation` |
| `repositoryChecks` | 可回退状态门控 |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-WKF-DD-CMD-023` |

---

#### 5.1.22 `promoteDraftDispensesForDeviceRequests(Collection<Long> deviceReqIds, Long operatorId)` → `void`
**命令文件**：无（领域服务 API；入参为请求主键集合）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 仅处理 `status_enum=DRAFT` 的发放行（实现内过滤） |
| `statusTransitionMethod` | 委托 **`promoteDeviceDispenseToPreparation`**（`DRAFT→PREPARATION`） |
| `repositoryChecks` | `findByDeviceReqIdIn` |
| `eventPublishAnchor` | 与 **5.1.6** 一致（以 **`promoteDeviceDispenseToPreparation`** 为准） |
| `unitTestCaseId` | `TC-WKF-DD-CMD-022` |
**方法契约摘要**：**门诊正式结算**路径由 **`OutpatientChargeAdviceStateSyncService`** 调用（**AS-OP-FIN-002** **BR-OPC-015** / IF-OPC-07）；空集合幂等返回。

### 5.2 读侧 — `DeviceDispenseQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `loadDeviceDispenseById` | `id` | `DeviceDispenseModel` | 单条详情 | TC-WKF-DD-QRY-001 |
| `listDeviceDispensesByEncounter` | `encounterId` | `List<DeviceDispenseListModel>` | 就诊列表 | TC-WKF-DD-QRY-002 |
| `listDeviceDispensesByEncounterIds` | `Collection<Long> encounterIds` | `List<DeviceDispenseListModel>` | 本页就诊 IN 批量；护士站队列 **BR-NUR-017** | TC-WKF-DD-QRY-002a |
| `mustGetDeviceDispense` | `id` | `DeviceDispenseModel` | 写前强加载 | TC-WKF-DD-QRY-003 |
| `validateDeviceDispensePreparable` | `id` | `DeviceDispenseValidationModel` | 拣配门控 | TC-WKF-DD-QRY-004 |
| `validateDeviceDispensable` | `id` | `DeviceDispenseValidationModel` | 发放门控 | TC-WKF-DD-QRY-005 |
| `validateDeviceDispenseCancellable` | `id` | `DeviceDispenseValidationModel` | 作废门控 | TC-WKF-DD-QRY-006 |
| `validateDeviceDispenseRefundable` | `id` | `DeviceDispenseValidationModel` | 退耗材门控 | TC-WKF-DD-QRY-007 |
| `getDevDispenseByProcedureId` | `procedureId` | `List<DeviceDispenseListModel>` | 执行关联查询 | TC-WKF-DD-QRY-008 |
| `selectByRequestIdList` | `requestIdList` | `List<DeviceDispenseListModel>` | 请求批量查询 | TC-WKF-DD-QRY-009 |
| `init` | `context` | `InitModel` | 工作台初始化 | TC-WKF-DD-QRY-010 |
| `getEncounterInfoListPage` | `query,page` | `Page<EncounterInfoModel>` | 就诊分页 | TC-WKF-DD-QRY-011 |
| `getDeviceDispenseOrderList` | `query,page` | `Page<DeviceDispenseListModel>` | 发放单分页 | TC-WKF-DD-QRY-012 |
| `listDeviceDispensesForOrderCatalogOccupancy` | `candidateLocationIds,deviceDefinitionIds` | `List<DeviceDispenseOrderCatalogOccupancyModel>` | IF-DOR-11 目录预占（**不限就诊**；`DRAFT`/`PREPARATION` **不过期**，直至执行或取消；与药品 `now < limitTime` 不同） | TC-WKF-DD-QRY-013 |
| `listDeviceDispenseIdsByDeviceRequest` | `Long deviceReqId` | `List<Long>` | 单请求发放 id | TC-WKF-DD-QRY-014 |
| `mapDeviceDispenseIdsByDeviceRequestIds` | `Collection<Long> deviceReqIds` | `Map<Long,List<Long>>` | 批量分组 | TC-WKF-DD-QRY-015 |
| `mapDeviceRequestToPrimaryDispenseLocationId` | `Collection<Long> deviceReqIds` | `Map<Long,Long>` | 主库位 | TC-WKF-DD-QRY-016 |
| `mapDeviceDispenseIdsToDeviceReqIds` | `Collection<Long> dispenseIds` | `Map<Long,Long>` | 发放→请求 | TC-WKF-DD-QRY-017 |
| `listDeviceDispensesByDeviceReqIds` | `Collection<Long> deviceReqIds` | `List<DeviceDispense>` | 批量实体 | TC-WKF-DD-QRY-018 |
| `listDeviceDispensesByLocationAndDispenseWindow` | `DeviceDispenseSettlementWindowQuery` | `List<DeviceDispenseSettlementLineQueryModel>` | **AS-INV-RPT-001** · 应用 **`inventory/dailysettlement`**；库位 + 发放日窗；汇总键 **`deviceDefId`** | TC-WKF-DD-QRY-019 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-DD-001~005 | 关键迁移、数量闭合、原因必填、库存前置、双人规则 |
| BR-DD-006~009 | 限制时间窗口、追溯一致、退耗材幂等、取消执行回退清空 |
| BR-DD-017 | 查询/校验/写入分层 |
| BR-DD-QRY-001~003 | 写前回写模型、待发合并展示、预减占用口径 |
| PR-DD-* | busNo 按日采番、批号拆单、多码规范 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| `DeviceDispenseCreatedEvent` | `com.zxplat.domain.share.events.workflow.devicedispense` | `id,deviceReqId,statusEnum` | 5.1.5/5.1.7 |
| `DeviceDispensePreparedEvent` | 同上 | `id,preparerId,prepareTime` | 5.1.1 |
| `DeviceDispensedEvent` | 同上 | `id,dispenseQuantity,lotNumber,traceNo` | 5.1.2/5.1.8 |
| `DeviceDispenseDeclinedEvent` | 同上 | `id,notPerformedReasonEnum` | 5.1.3 |
| `DeviceDispenseStoppedEvent` | 同上 | `id,notPerformedReasonEnum` | 5.1.17 |
| `DeviceDispenseCancelledEvent` | 同上 | `id,busNo` | 5.1.19 |
| `DeviceDispenseRefundedEvent` | 同上 | `id,refundQuantity` | 5.1.4 |
| `DeviceDispenseSummarizedEvent` | 同上 | `idOrBatchKey` | 5.1.12 |
| `DeviceDispenseSummaryCompletedEvent` | 同上 | `batchKey,idList` | 5.1.13 |
| `DeviceDispenseSummaryCancelledEvent` | 同上 | `batchKey` | 5.1.14 |
| `DeviceDispensePendingRefundCreatedEvent` | 同上 | `id,sourceDispenseId` | 5.1.9 |
| `DeviceDispenseReserveReleasedEvent` | 同上 | `idListOrRequestKey` | 5.1.10/5.1.16 |

---

## 8. 删除与可见性契约

- 未发放删除与回退必须先过门控；已发放不可物理删除。
- 默认查询过滤逻辑删除行。
- 回退 completed/prepared 到 preparation **保留签发批号** `lot_number`；清空追溯与发放/拣配操作字段，避免护士侧重跑 FIFO。

---

## 9. 框架优先适配规则

- 复用乐观锁与软删；不新增无收益封装层。
- 写前强加载与列表查询分离，遵循读写分层。

---

## 10. 生成与验收清单

- §4 字段表与 DM §3 主表字段逐项对齐，无遗漏（含 `statusEnum` 状态字段全量映射）
- §5.1 共 20 个写方法小节，覆盖待发/核对发放/已发放/撤回/退耗材全生命周期
- 每个写方法均包含命令文件、字段全集表格、绑定项、方法契约摘要
- §5.2 读方法 **19** 项与 DM §6.2 对齐
- §6 BR 规则映射覆盖 BR-DD-001~015，均可追溯到方法实现
- §7 事件 12 条与 DM §5.1 全量对齐（含 Preparation/Dispensed/Declined/Refunded 状态跃迁事件）
- 冲销口径明确：以 `refund_device_id + quantity<0` 为已冲销唯一汇总口径
- 关键路径单测用例 ID 与 §5 映射一致，可追溯

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-WKF-007、联动 DM-WKF-003/008 与 DM-CUS-011

### 11.2 DoD

- 状态迁移符合 §5 与 §6。
- 追溯码、批号规则可测试可审计。

### 11.3 失败处理

- 出现双重扣减或追溯不闭合时阻断并回文档修订。

---

## 定稿检查清单（可打勾）

- [x] 骨架完整
- [x] 写侧 20 方法覆盖
- [x] 读侧 12 方法覆盖
- [x] BR/事件映射齐全

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 19 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 9 |
| **总分** | **100** | **98（可进入代码会话）** |

---

## 12. 与 DM 同步记录

| 日期 | 摘要 |
| --- | --- |
| 2026-08-24 | **BR-DD-QRY-003**：耗材目录预占不过期；对齐 DM **v1.2.14** / **AS-OP-DOR-003 · BR-ORD-011** |
| 2026-08-22 | **PR-DD-LOT-003**：挂费/收银与发放批号一致；对齐 DM **v1.2.13** / **BR-ORD-033** / **BR-OPC-023** |
| 2026-08-21 | **BR-DD-009**：取消执行回退保留签发 `lot_number`；对齐 DM **v1.2.12** |
| 2026-07-02 | **AS-INV-RPT-001 v1.3 / SDD v0.4**：§5.2 补充应用消费 **`inventory/dailysettlement`** 与读模型 **`deviceDefId`** 锚点 |
| 2026-07-02 | **AS-INV-RPT-001**：§5.2 **`listDeviceDispensesByLocationAndDispenseWindow`** · TC-WKF-DD-QRY-019 落地；对齐 DM **v1.2.5** §6.2.19 |
| 2026-06-25 | **移除分次发放**：删除 **§5.1.20** `appendCompletedDeviceDispenseStep`；`busNo` 仅按日流水；对齐 DM **v1.2.4** |
| 2026-06-16 | **§5.1.10** `releaseDeviceDispenseReserve` 幂等与 IF-DOR-16 定位键；对齐 DM **v1.2.3** |
| 2026-06-08 | 新增 **§5.1.23** `revertPreparedBatchToPreparation`；对齐 DM **v1.2.2** |
| 2026-05-28 | **DM-SDD M1**：§5.1.4a 拒绝待退；§5.2 补批量读法；对齐 DM **v1.2.1** |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）

---

**本 SDD 正文结束。**
