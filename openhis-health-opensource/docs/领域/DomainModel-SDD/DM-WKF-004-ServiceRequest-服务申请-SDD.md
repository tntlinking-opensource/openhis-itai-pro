# DM-WKF-004 ServiceRequest 服务申请 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-WKF-004-ServiceRequest-服务申请.md`（**v1.1.29**）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：workflow（WKF）  
> **聚合**：ServiceRequest（子实体见 §4）  
> **实现阶段口径**：§5.1.8a 取消停嘱 + §5.1.8c/8d **停嘱校对/取消停嘱校对已落地**

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-WKF-004 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.workflow.servicerequest` | 满足 | 全小写 |
| 固定技术口径 | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖 |
| DM 第 7 章 BR | 满足 | §6 映射 |
| DM 第 5 章事件 | 满足 | §7 覆盖 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1（DateTime 文本） | 成码口径统一为 `LocalDateTime` |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.workflow.servicerequest` |
| 两阶段编排 | §6.3/§8.1 申请单/执行单 | 明确申请单 AGREE 后由应用编排生成执行单，执行单 AGREE 后驱动发放 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化非药品医嘱（服务申请）全生命周期：草稿、签发、校对、退回、停嘱、取消。
- 明确申请单与执行单据两阶段语义，确保 `based_on_*`、`generate_source_enum` 可追溯。
- 为后续领域代码生成提供逐方法命令契约与可测 BR 对照。

### 1.2 边界

- **在内**：ServiceRequest 主档与明细、状态机、来源追溯、打印计数、转科出院类请求。
- **在外**：ChargeItem、Procedure、Encounter 状态联动的实际写库（由应用编排）。
- **跨域仅端口声明**：通过事件与 ID 关联，不直写外域聚合主表。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.workflow.servicerequest`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `ServiceRequest`、`ServiceRequestDetail` |
| `command` | 每方法独立命令文件 |
| `repository` | 主仓储 + 明细仓储 |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.workflow.servicerequest` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键/外键 | `Long` |
| 时间 | `LocalDateTime`（含 `reqOpenedTime`/`reqAuthoredTime`/`stopTime`/check/occurrence） |
| `*_enum`（状态/类别/优先级/来源/治疗类型等业务枚举列） | `String`（存 **`getCode()`** 或与 **`ChargeItemDmConstants`** 一致的字符串码） |
| `*_code` | `String` |
| `_flag` | `Boolean` |
| 软删 | `isDeleted` |
| 仓储 | `JpaRepository` 接口 |
| 事件 | beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `wkf_service_request` | `ServiceRequest` | 主表 |
| `wkf_service_request_detail` | `ServiceRequestDetail` | 子表 |

### 4.2 聚合根 `ServiceRequest`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `busNo` | `String` | 否 | 业务号 |
| `originalBusNo` | `String` | 否 | 来源单号 |
| `statusEnum` | `String` | 是 | `RequestStatus#getCode()` |
| `categoryEnum` | `String` | 是 | 诊疗类别 code（如 ActivityDefCategory） |
| `priorityEnum` | `String` | 否 | 优先级 code |
| `activityId` | `Long` | 是 | 诊疗定义；术式/转科出院等无计费锚时存 `0` |
| `procedureDefinitionId` | `Long` | 否 | 国临手术定义（术式行临床真源；加收/麻醉/术前嘱托为空；术前嘱托 basedOn 术式；加收由护士划价） |
| `quantity` | `BigDecimal` | 是 | 账单计价数量 |
| `unitCode` | `String` | 是 | 账单计价单位 |
| `executionCount` | `Integer` | 否 | 计划执行次数（临嘱/门诊护士站拆解；与 quantity 分离） |
| `encounterId` | `Long` | 是 | 就诊 |
| `patientId` | `Long` | 是 | 患者 |
| `encounterDiagnosisIds` | `String` | 否 | 绑定就诊诊断 ids 有序串（`adm_encounter_diagnosis.id`，**主诊 id 第一**；落库列 `encounter_diagnosis_ids`） |
| `requesterId` | `Long` | 是 | 开立人 |
| `reqOpenedTime` | `LocalDateTime` | 否 | 开立时刻（`req_opened_time`）：首次建草稿/直建写一次；签发/改草稿/签退**不覆盖**；住院可由改业务时间覆盖 |
| `reqAuthoredTime` | `LocalDateTime` | 否 | 签发时刻（`req_authored_time`）：**仅**签发（及无草稿直建）写入；草稿暂存**不写**；签退/驳回回草稿清空；**仅系统追溯**，列表/报表**默认不展示**；**禁止**改业务时间写入 |
| `stopTime` | `LocalDateTime` | 否 | 停嘱时刻（`stop_time`）；取消停嘱清空；**不**占用 `occurrenceEndTime`；可由改业务时间覆盖 |
| `stopperId` | `Long` | 否 | 停嘱人（`stopper_id`）；停嘱写入；取消停嘱清空；改停嘱时间**不**改（**已落地**） |
| `stopPerformerCheckId` | `Long` | 否 | 停嘱校对人（`stop_performer_check_id`）；停嘱校对写入；取消停嘱校对/取消停嘱/新停嘱清空；改停嘱时间**保留** |
| `stopCheckTime` | `LocalDateTime` | 否 | 停嘱校对时间（`stop_check_time`）；与上成对 |
| `occurrenceEndTime` | `LocalDateTime` | 否 | 计划结束时刻（非停嘱） |
| `performerId` | `Long` | 否 | 执行人 |
| `performerCheckId` | `Long` | 否 | 校对人 |
| `checkTime` | `LocalDateTime` | 否 | 校对时间 |
| `basedOnTable` | `String` | 否 | 来源表 |
| `basedOnId` | `Long` | 否 | 来源 ID |
| `generateSourceEnum` | `String` | 否 | `"1"`～`"5"`（**`ChargeItemDmConstants`**） |
| `therapyEnum` | `String` | 否 | 长/临时 code（与主数据枚举一致，存字符串） |
| `printCount` | `Integer` | 否 | 打印次数 |

### 4.3 子实体 `ServiceRequestDetail`

| 字段名 | 类型 | 必填 | 说明 |
| ------ | ---- | ---- | ---- |
| `id` | `Long` | 是 | 主键 |
| `serviceReqId` | `Long` | 是 | 外键 |
| `targetTable` | `String` | 是 | 明细目标表名 |
| `targetId` | `Long` | 是 | 明细目标主键 |
| `remark` | `String` | 否 | 备注 |

### 4.4 枚举契约

| 字段 | 说明 |
| ---- | ---- |
| `statusEnum` | `DRAFT`/`ACTIVE`/`VERIFIED`/`HOLD`/`CANCELLED`/`STOPPED`/`COMPLETED`（**`RequestStatus#getCode()`**，见 DM-WKF-004 §2.3） |
| `categoryEnum` | ActivityDefCategory 等（**存字符串 code**；文档表 21~50 为类别对照，**非**落库整型主键） |
| `generateSourceEnum` | `"1"`～`"5"`（**`ChargeItemDmConstants`**） |

### 4.5 仓储接口

- `ServiceRequestRepository`、`ServiceRequestDetailRepository`
- 必要签名：按 `id`、`busNo`、`encounterId`、`patientId`、`basedOnId` 等查询
- `List<ServiceRequest> findByEncounterIdInAndStatusEnum(Collection<Long> encounterIds, String statusEnum)` — **AS-IP-NUR-001** 护士站批量计数（DM §6.2.13）

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `ServiceRequestQueryModel`（`loadServiceRequestById`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `statusEnum` | `String` | 主表 | 状态 code |
| `categoryEnum` | `String` | 主表 | 类别 code |

#### 4.6.2 `ServiceRequestListModel`（`listServiceRequestsByEncounter`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `encounterId` | `Long` | 主表 | 就诊 |
| `therapyEnum` | `String` | 主表 | 长/临时 code |
| `reqOpenedTime` | `LocalDateTime` | 主表 | 开立时刻 |
| `reqAuthoredTime` | `LocalDateTime` | 主表 | 签发时刻；草稿可 null |
| `stopTime` | `LocalDateTime` | 主表 | 停嘱时刻；未停 null |

#### 4.6.3 `ServiceRequestHistoryModel`（`listServiceRequestHistoryByPatient`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `patientId` | `Long` | 主表 | 患者 |
| `encounterId` | `Long` | 主表 | 就诊 |

#### 4.6.4 `OffsetVerificationModel`（`assertOffsetVerification`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `sourceId` | `Long` | 入参 | 原单 |
| `remaining` | `BigDecimal` | 计算 | 剩余可冲销 |

#### 4.6.5 `ServiceRequestDetailListModel`（`mapDetailListModelsByServiceRequestIds`）

| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 子表 | 明细主键 |
| `serviceReqId` | `Long` | 子表 | 外键 |
| `targetTable` | `String` | 子表 | 目标表名 |
| `targetId` | `Long` | 子表 | 目标主键 |
| `targetDisplayName` | `String` | 读侧解析 | `dia_body_structure` / `adm_specimen_definition` 展示名 |
| `remark` | `String` | 子表 | 同行补充说明 |

**应用消费**：`ServiceApplyFormOrchestrationSupport#listApplyForms`、`DoctorStationAdviceListAssembler` / `InpatientDoctorStationAdviceListAssembler` → `ServiceApplyFormAssembler#toDetailSummaryDtos`（**AS-OP-DOR-003-SDD v3.25**）。


### 5.1 写侧 — `ServiceRequestDomainService`

#### 5.1.1 `createServiceRequestDraft(CreateServiceRequestDraftCommand cmd)` → `Long`
**命令文件**：`CreateServiceRequestDraftCommand.java`  
**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `encounterId` | `Long` | 是 | 就诊标识，触发就诊校验 |
| `patientId` | `Long` | 是 | 患者标识，主档校验 |
| `activityId` | `Long` | 是 | 诊疗项目定义标识 |
| `quantity` | `BigDecimal` | 是 | 账单计价数量，>0 |
| `unitCode` | `String` | 是 | 账单计价单位编码 |
| `executionCount` | `Integer` | 条件 | 计划执行次数；**临嘱**必填 >0；**长嘱**可空（按频次执行） |
| `categoryEnum` | `String` | 是 | 申请类别 code（检验/检查/治疗/手术） |
| `therapyEnum` | `String` | 是 | 医嘱类型 code（长期/临时） |
| `requesterId` | `Long` | 是 | 申请医生标识 |
| `detailItems` | `List<DetailItemCommand>` | 否 | 申请明细行集合 |
| `note` | `String` | 否 | 申请备注 |

`DetailItemCommand` 字段：`targetTable`、`targetId`、`remark`（每行一个目标引用）。
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDraftCreatable` |
| `statusTransitionMethod` | `initDraft` |
| `repositoryChecks` | 同就诊同类冲突校验 |
| `eventPublishAnchor` | `ServiceRequestDraftCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-001` |
**方法契约摘要**：创建草稿主表+明细；**同步写入 `reqOpenedTime`（首次暂存时刻）**；失败为必填缺失或规则不满足。

---

#### 5.1.2 `modifyServiceRequestDraft(ModifyServiceRequestDraftCommand cmd)` → `void`
**命令文件**：`ModifyServiceRequestDraftCommand.java`
**字段全集**：`id` + 可编辑字段全集  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDraftEditable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `mustGetById` |
| `eventPublishAnchor` | `ServiceRequestDraftEditedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-002` |
**方法契约摘要**：仅 DRAFT 可改；`quantity`/`unit_code` 按命令项原样持久化（**禁止** `normalizeQuantityForTherapy` 类强制归 1）。

---

#### 5.1.3 `removeServiceRequestDraft(RemoveServiceRequestDraftCommand cmd)` → `void`
**命令文件**：`RemoveServiceRequestDraftCommand.java`
**字段全集**：`id`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDraftRemovable` |
| `statusTransitionMethod` | `remove` |
| `repositoryChecks` | 删除门控查询 |
| `eventPublishAnchor` | `ServiceRequestDraftRemovedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-003` |
**方法契约摘要**：草稿删除主子同事务清理，已收费拒绝。

---

#### 5.1.4 `signServiceRequest(SignServiceRequestCommand cmd)` → `void`
**命令文件**：`SignServiceRequestCommand.java`
**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `id` | `Long` | 是 | 申请单标识 |
| `signCode` | `String` | 是 | 签发码（医生工号或电子签章） |
| `signerId` | `Long` | 是 | 签发人标识 |
| `signTime` | `LocalDateTime` | 否 | 签发时间，默认当前时间 |
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertSignable` |
| `statusTransitionMethod` | `draftToActive` |
| `repositoryChecks` | `mustGetById` |
| `eventPublishAnchor` | `ServiceRequestSignedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-004` |
**方法契约摘要**：签发进入 ACTIVE；**写入 `reqAuthoredTime` 为签发时刻**（**不覆盖** `reqOpenedTime`）；发布 `ServiceRequestSignedEvent`（载荷含 save 后 **`statusEnum`**）；列表读 **`ServiceRequestQueryService`**；检验检查跨系统发送由应用层后置触发。

---

#### 5.1.5 `withdrawSignedServiceRequest(WithdrawSignedServiceRequestCommand cmd)` → `void`
**命令文件**：`WithdrawSignedServiceRequestCommand.java`
**字段全集**：`id`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertWithdrawable` |
| `statusTransitionMethod` | `activeToDraft` |
| `repositoryChecks` | 签退门控查询 |
| `eventPublishAnchor` | `ServiceRequestWithdrawnEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-005` |
**方法契约摘要**：签退回草稿；已收费或已校对拒绝；**已是 `DRAFT` 时幂等返回**（皮试联动签退后同批显式再触达不报错）。

---

#### 5.1.6 `verifyServiceRequest(VerifyServiceRequestCommand cmd)` → `void`
**命令文件**：`VerifyServiceRequestCommand.java`
**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `id` | `Long` | 是 | 申请单标识 |
| `performerCheckId` | `Long` | 是 | 校对人工号/标识 |
| `checkTime` | `LocalDateTime` | 否 | 校对时间，默认当前时间 |
| `checkResult` | `String` | 否 | 校对结果说明 |

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertVerifiable` |
| `statusTransitionMethod` | `activeToVerified` |
| `repositoryChecks` | `mustGetById` |
| `eventPublishAnchor` | `new ServiceRequestVerifiedEvent(id, performerCheckId, statusEnum)`（**`statusEnum`** 为 save 后 **`VERIFIED`** code） |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-006` |
**方法契约摘要**：病区护士校对 **ACTIVE→VERIFIED**；写追溯字段；与门诊收费 **COMPLETED** 路径分离。

---

#### 5.1.6a `completeServiceRequestAfterOutpatientCharge(Long serviceRequestId, Long operatorId)` → `void`
**命令文件**：无（领域服务原子 API；由 **`OutpatientChargeAdviceStateSyncService`** 编排）。  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ensureCompletableAfterOutpatientCharge` |
| `statusTransitionMethod` | `activeToCompleted` |
| `repositoryChecks` | `requireServiceRequest` |
| `eventPublishAnchor` | `new ServiceRequestVerifiedEvent(id, performerCheckId, statusEnum)` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-006a` |
**方法契约摘要**：门诊收费后 **ACTIVE→COMPLETED**；已为 **COMPLETED** 则幂等跳过；**`operatorId`** 写入 **`performerCheckId`**；事件载荷 **必须** 含持久化 **`statusEnum`**（对齐 **各 Request QueryService**）。

---

#### 5.1.7 `rejectServiceRequestToDraft(RejectServiceRequestToDraftCommand cmd)` → `void`
**命令文件**：`RejectServiceRequestToDraftCommand.java`
**字段全集**：`id, reason, performerCheckId, checkTime, operatorId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRejectable` |
| `statusTransitionMethod` | `activeOrCompletedToDraft` |
| `repositoryChecks` | `mustGetById` |
| `eventPublishAnchor` | `new ServiceRequestRejectedToDraftEvent(id, statusEnum)`（**`statusEnum=DRAFT`**） |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-007` |
**方法契约摘要**：退回草稿；**须**写 `performerCheckId`、`checkTime`（**BR-SRQ-022**）。**典型调用方**：**AS-IP-FIN-004** `InpatientSettleAppService#reverseInpatientSettle`、**AS-IP-NUR-002** `RecallReverseSettlesupport（出院类 `category_enum=34` 已校对嘱）。

---

#### 5.1.8 `stopServiceRequest(StopServiceRequestCommand cmd)` → `void`
**命令文件**：`StopServiceRequestCommand.java`
**字段全集**：`id, stopTime, stopperId, reason`（`stopperId` 或由 `operatorId` 映射）
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStoppable`（住院 **VERIFIED**；门诊 **COMPLETED** 保留） |
| `statusTransitionMethod` | `toStopped`（写停嘱轴；清停嘱校对两字段） |
| `repositoryChecks` | `mustGetById` |
| `eventPublishAnchor` | `ServiceRequestStoppedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-008` |
**方法契约摘要**：**BR-SRQ-016**；禁止 ACTIVE 停嘱。

---

#### 5.1.8a `cancelStopServiceRequest(CancelStopServiceRequestCommand cmd)` → `void`
**命令文件**：`CancelStopServiceRequestCommand.java`
**字段全集**：`id, operatorId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCancelStopAllowed`（须未停嘱校对） |
| `statusTransitionMethod` | `fromStopped`（清停嘱轴 + 停嘱校对两字段） |
| `repositoryChecks` | `mustGetById` |
| `eventPublishAnchor` | `ServiceRequestStopCancelledEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-008a` |
**方法契约摘要**：**BR-SRQ-016a**。**已落地**。

---

#### 5.1.8b `changeServiceRequestBusinessTime(ChangeServiceRequestBusinessTimeCommand cmd)` → `void`
**命令文件**：`ChangeServiceRequestBusinessTimeCommand.java`
**字段全集**：`id, timeKind, newTime, operatorId`（`timeKind`=`OPENED`/`STOP`）
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertBusinessTimeChangeAllowed`（**已落地**） |
| `statusTransitionMethod` | —（改停嘱**保留**停嘱校对字段） |
| `repositoryChecks` | `mustGetById` |
| `eventPublishAnchor` | 可选审计事件 |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-008b` |
**方法契约摘要**：**BR-SRQ-016b**。**已落地**。

---

#### 5.1.8c `verifyStopServiceRequest(VerifyStopServiceRequestCommand cmd)` → `void`
**命令文件**：`VerifyStopServiceRequestCommand.java`
**字段全集**：`id, stopPerformerCheckId, stopCheckTime`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStopVerifiable` / `markStopChecked` |
| `statusTransitionMethod` | —（仍 **STOPPED**） |
| `repositoryChecks` | `mustGetById` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-008c` |
**方法契约摘要**：**BR-SRQ-016c**。**已落地**（**IF-IP-AUD-02**）。

---

#### 5.1.8d `cancelVerifyStopServiceRequest(CancelVerifyStopServiceRequestCommand cmd)` → `void`
**命令文件**：`CancelVerifyStopServiceRequestCommand.java`
**字段全集**：`id`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertStopVerifyCancellable` / `clearStopChecked` |
| `statusTransitionMethod` | —（仍 **STOPPED**；本期不校验操作人） |
| `repositoryChecks` | `mustGetById` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-008d` |
**方法契约摘要**：**BR-SRQ-016d**。**已落地**（**IF-IP-AUD-03**）。

---

#### 5.1.9 `cancelServiceRequest(CancelServiceRequestCommand cmd)` → `void`
**命令文件**：`CancelServiceRequestCommand.java`
**字段全集**：`id, cancelReason`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertCancelable` |
| `statusTransitionMethod` | `toCancelled` |
| `repositoryChecks` | `mustGetById` |
| `eventPublishAnchor` | `ServiceRequestCancelledEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-009` |
**方法契约摘要**：取消进入终态并保留审计字段。

---

#### 5.1.9a `createServiceOrderRefund(CreateServiceOrderRefundCommand cmd)` → `Long`
**命令文件**：`CreateServiceOrderRefundCommand.java`
**字段全集**：`operatorId`；行项 `sourceRequestId, refundQuantity`（正数）, `reason`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertServiceRequestRefundableForNegativeOrder` |
| `statusTransitionMethod` | `initAsRefundNegativeOrder` |
| `repositoryChecks` | `mustGetById`；`findByRefundServiceIdIn` 幂等 |
| `eventPublishAnchor` | `new ServiceOrderRefundRequestedEvent(refundId, sourceId)` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-009a` |
**方法契约摘要**：前置为源单 **COMPLETED** 且为正向单；**已有未完结负向单则幂等返回已有主键**；否则持久化生成负向单：全量拷贝头表/明细、`refund_service_id` 关联、**`markAsRefundFromPrescription`（`T`+原号）**、`quantity` 取反、`initAsRefundNegativeOrder`（**`CANCELLED`**）；失败为原单已是负向单或源单非已完成。**典型调用方**：**AS-OP-DOR-004** `DoctorStationRefundApplicationAppService#executeServiceRefundBranch`。

---

#### 5.1.10 `createTransferDepartmentRequest(CreateTransferDepartmentRequestCommand cmd)` → `Long`
**命令文件**：`CreateTransferDepartmentRequestCommand.java`
**字段全集**：`encounterId, targetOrgId, reasonText`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertTransferDischargeNotDuplicated` |
| `statusTransitionMethod` | `initDraft` |
| `repositoryChecks` | 重复转科/出院门控 |
| `eventPublishAnchor` | `TransferDepartmentRequestCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-010` |
**方法契约摘要**：创建转科申请草稿。

---

#### 5.1.11 `createDischargeRequest(CreateDischargeRequestCommand cmd)` → `Long`
**命令文件**：`CreateDischargeRequestCommand.java`
**字段全集**：`encounterId, dischargeTime, reasonText`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertTransferDischargeNotDuplicated` |
| `statusTransitionMethod` | `initDraft` |
| `repositoryChecks` | 重复门控 + 时长余量门控 |
| `eventPublishAnchor` | `DischargeRequestCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-011` |
**方法契约摘要**：创建出院申请草稿。

---

#### 5.1.12 `upsertNursingOrder(UpsertNursingOrderCommand cmd)` → `Long`
**命令文件**：`UpsertNursingOrderCommand.java`
**字段全集**：`encounterId, categoryEnum, content`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertNursingOrderUpsertable` |
| `statusTransitionMethod` | `stopOldThenCreateDraft` |
| `repositoryChecks` | 按就诊+类别查重 |
| `eventPublishAnchor` | `NursingOrderUpsertedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-012` |
**方法契约摘要**：同类幂等维护，内容变更停旧建新。

---

#### 5.1.13 `createExecutedServiceRequest(CreateExecutedServiceRequestCommand cmd)` → `Long`
**命令文件**：`CreateExecutedServiceRequestCommand.java`
**字段全集**：`basedOnTable, basedOnId, generateSourceEnum, activityId, encounterId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertExecutionDerivedCreatable` |
| `statusTransitionMethod` | `initCompleted` |
| `repositoryChecks` | 来源合法性 + 步次去重 |
| `eventPublishAnchor` | `ServiceRequestExecutedCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-013` |
**方法契约摘要**：执行派生单创建即 COMPLETED。

---

#### 5.1.14 `createFromNursePricing(CreateFromNursePricingCommand cmd)` → `List<Long>`
**命令文件**：`CreateFromNursePricingCommand.java`（`BatchCommand` + `operatorId`）
**Item 字段全集**：`encounterId`、`patientId`、`activityId`、`quantity`、`unitCode`、`requesterId`、`performLocation`（应用编排用，域写主表不强制）、`basedOnTable`、`basedOnId`（补费成对；直接划价均为空）
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertDraftCreatable` |
| `statusTransitionMethod` | `initVerified`；`setPerformerCheckId(command.getOperatorId())`；`setCheckTime(now)` |
| `repositoryChecks` | `items` 非空；`basedOnTable`/`basedOnId` 成对；`therapy_enum=TEMPORARY`；`generate_source_enum=nurse-pricing` |
| `batchPersist` | `serviceRequestRepository.saveAll`；**禁止**循环单条 `save` |
| `eventPublishAnchor` | 每行 `saveAll` 后 `NursePricingServiceRequestCreatedEvent(serviceRequestId, encounterId, patientId, activityId, quantity, unitCode, basedOnTable, basedOnId, statusEnum)`（**B.14**） |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-014` |
**方法契约摘要**：护士划价批量建单即 **VERIFIED**（不经校对 Tab）；支持直接划价（无 `based_on_*`）与补费（有 `based_on_*`）；对齐 **AS-IP-NUR-005** / **GAP-IP-PRC-003**。
**写表摘要（非事件载荷）**：`generate_source_enum=nurse-pricing`；`therapy_enum=TEMPORARY`；`category_enum=21`；`performer_check_id`/`check_time`；**不写** `sign_code`。
**读侧**：护士划价行在 **`createFromNursePricing`** 已落库；`generate_source_enum=nurse-pricing`；已划价列表 **`listInpatientNurseStationPricedOrders`** / **`mapListModelsByIds`**。

---

#### 5.1.14a `removeFromNursePricing(RemoveFromNursePricingCommand cmd)` → `void`
**命令文件**：`RemoveFromNursePricingCommand.java`（`BatchCommand` + `operatorId`）
**Item 字段全集**：`id`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertNursePricingRemovable` |
| `statusTransitionMethod` | 主档 + 明细逻辑删除（`setIsDeleted(true)`） |
| `repositoryChecks` | `items` 非空；`findAllById` 批量加载；主键去重保序 |
| `batchPersist` | `serviceRequestRepository.saveAll` + `serviceRequestDetailRepository.saveAll` |
| `eventPublishAnchor` | 每行 `saveAll` 后 `NursePricingServiceRequestRemovedEvent(serviceRequestId, encounterId, basedOnTable, basedOnId)`（**B.14**） |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-019` |
**方法契约摘要**：取消护士划价批量软删 **VERIFIED + nurse-pricing** 诊疗申请；与 **5.1.14** 对称；对齐 **AS-IP-NUR-005** IF-IP-PRC-06 / **GAP-IP-PRC-007**。
**读侧**：软删后列表直读主表 + `is_deleted` 过滤；与 **GAP-IP-PRC-010** 同构，无独立 RM handler。

---

#### 5.1.15 `createFromOutpatientRegistration(CreateFromOutpatientRegistrationCommand cmd)` → `Long`
**命令文件**：`CreateFromOutpatientRegistrationCommand.java`
**字段全集**：`encounterId, activityId, patientId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRegistrationCreateAllowed` |
| `statusTransitionMethod` | `initActive` |
| `repositoryChecks` | 门诊登记上下文校验 |
| `eventPublishAnchor` | `OutpatientRegistrationServiceRequestCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-015` |
**方法契约摘要**：挂号登记建单即 ACTIVE。

---

#### 5.1.16 `increasePrintCount(IncreasePrintCountCommand cmd)` → `void`
**命令文件**：`IncreasePrintCountCommand.java`
**字段全集**：`id, increaseBy`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertPrintCountIncreasable` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `mustGetById` |
| `eventPublishAnchor` | `N/A` |
| `unitTestCaseId` | `TC-WKF-SRQ-CMD-016` |
**方法契约摘要**：打印次数仅允许增量累加。

### 5.2 读侧 — `ServiceRequestQueryService`

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `loadServiceRequestById` | `id` | `ServiceRequestQueryModel` | 单条加载 | TC-WKF-SRQ-QRY-001 |
| `mustGetServiceRequest` | `id` | `ServiceRequestQueryModel` | 写前强加载 | TC-WKF-SRQ-QRY-002 |
| `listServiceRequestsByEncounter` | `encounterId` | `List<ServiceRequestListModel>` | 就诊列表 | TC-WKF-SRQ-QRY-003 |
| `listServiceRequestHistoryByPatient` | `patientId,excludeEncounterId` | `List<ServiceRequestHistoryModel>` | 患者历史 | TC-WKF-SRQ-QRY-004 |
| `validateServiceRequestDeletable` | `id` | `Boolean` | 删除门控 | TC-WKF-SRQ-QRY-005 |
| `validateServiceRequestWithdrawable` | `id` | `Boolean` | 签退门控 | TC-WKF-SRQ-QRY-006 |
| `assertOffsetVerification` | `sourceId,preOffset` | `OffsetVerificationModel` | 冲销校验 | TC-WKF-SRQ-QRY-007 |
| `listServiceRequestsByTherapyType` | `encounterId,therapyEnum` | `List<ServiceRequestListModel>` | 长临分轨 | TC-WKF-SRQ-QRY-008 |
| `listServiceRequestsByGenerateSource` | `encounterId,sourceEnum` | `List<ServiceRequestListModel>` | 来源分轨 | TC-WKF-SRQ-QRY-009 |
| `getServiceRequestByActivityId` | `activityId` | `List<ServiceRequestListModel>` | 按诊疗定义查询 | TC-WKF-SRQ-QRY-010 |
| `checkDuplicateTransferOrDischargeRequest` | `encounterId` | `Boolean` | 转科出院互斥 | TC-WKF-SRQ-QRY-011 |
| `countServiceRequestByBasedOnId` | `basedOnTable,basedOnId` | `Long` | 派生步次统计 | TC-WKF-SRQ-QRY-012 |
| `listByEncounterIdsAndStatusEnum` | `Collection<Long> encounterIds, String statusEnum` | `List<ServiceRequest>` | 护士站批量计数（DM §6.2.13） | TC-WKF-SRQ-QRY-013 |
| `resolveActivityIdByServiceRequestId` | `Long serviceRequestId` | `Long`（可 null） | 主表取 `activity_id`；已删/不存在 → null | TC-WKF-SRQ-QRY-014 |
| `mapListModelsByIds` | `Collection<Long> serviceRequestIds` | `Map<Long, ServiceRequestListModel>` | 批量 IN 主键；IF-IP-PRC-05 回源 | TC-WKF-SRQ-QRY-015 |
| `sumQuantityHoursByEncounterAndActivity` | `encounterId, activityDefinitionId, occurrenceDate?` | `long` | 汇总 `wkf_service_request.quantity` | TC-WKF-SRQ-QRY-016 |
| `listInpatientDoctorStationOrders` | `encounterId, therapyEnum?` | `List<ServiceRequestListModel>` | 住院医生站医嘱 Tab：排除 category 24/25；另排除挂靠术式的术前嘱托（52 + basedOn 术式 SR）；**禁止**一刀切排除全部 52 | TC-WKF-SRQ-QRY-017 |
| `listInpatientNurseStationAuditOrders` | 见 DM §6.2.18 | `List<ServiceRequestListModel>` | 护士校对列表 | TC-WKF-SRQ-QRY-018 |
| `listInpatientNurseStationExecuteOrders` | 见 DM §6.2.19 | `List<ServiceRequestListModel>` | 护士执行列表 | TC-WKF-SRQ-QRY-019 |
| `listInpatientNurseStationSupplementOrders` | 见 DM §6.2.20 | `List<ServiceRequestListModel>` | 补费父医嘱 | TC-WKF-SRQ-QRY-020 |
| `listInpatientNurseStationPricedOrders` | 见 DM §6.2.21 | `List<ServiceRequestListModel>` | 已划价列表 | TC-WKF-SRQ-QRY-021 |
| `getListModelById` / `mustGetListModelById` | `Long id` | `ServiceRequestListModel` | 单条回源 | TC-WKF-SRQ-QRY-022 |
| `mapDetailListModelsByServiceRequestIds` | `Collection<Long> serviceRequestIds` | `Map<Long, List<ServiceRequestDetailListModel>>` | 明细 IN 查询 + 部位/样本展示名批量解析（DM §6.2.24） | TC-WKF-SRQ-QRY-023 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-SRQ-001~005 | 状态迁移、收费门控、来源追溯 |
| BR-SRQ-006~010 | 打印累加；**BR-SRQ-007** 数量/单位由载荷或应用层目录解析（**禁止**域层强制归 1）；来源建单；套餐父子；转科出院互斥 |
| BR-SRQ-011~015 | 明细归属、草稿删除编排、签发后对外发送、冲销非负、执行步次可追溯 |
| BR-SRQ-016~016d / 017~023 | 停嘱/取消停嘱/改时/停嘱校对/取消停嘱校对、取消审计、出院余量、子医嘱联动、护理幂等、药品执行派生、校对追溯、明细不入状态机 |
| BR-SRQ-QRY-001~007 | 读写分层、历史排除、分轨查询、明细批量读与展示名解析 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| `ServiceRequestDraftCreatedEvent` | `com.zxplat.domain.share.events.workflow.servicerequest` | `serviceRequestId,encounterId,patientId` | 5.1.1 |
| `ServiceRequestDraftEditedEvent` | 同上 | `serviceRequestId,changedFields` | 5.1.2 |
| `ServiceRequestDraftRemovedEvent` | 同上 | `serviceRequestId,encounterId` | 5.1.3 |
| `ServiceRequestSignedEvent` | 同上 | `serviceRequestId,signCode,categoryEnum,statusEnum` | 5.1.4 |
| `ServiceRequestWithdrawnEvent` | 同上 | `serviceRequestId,signCode` | 5.1.5 |
| `ServiceRequestVerifiedEvent` | 同上 | `serviceRequestId,performerCheckId,statusEnum` | 5.1.6、5.1.6a |
| `ServiceRequestRejectedToDraftEvent` | 同上 | `serviceRequestId,statusEnum` | 5.1.7 |
| `ServiceRequestStoppedEvent` | 同上 | `serviceRequestId,stopTime,statusEnum` | 5.1.8 |
| `ServiceRequestStopCancelledEvent` | 同上 | `serviceRequestId,statusEnum` | 5.1.8a |
| `ServiceRequestCancelledEvent` | 同上 | `serviceRequestId,cancelReason` | 5.1.9 |
| `ServiceOrderRefundRequestedEvent` | 同上 | `aggregateId(serviceRequestId),refundServiceId` | 5.1.9a |
| `TransferDepartmentRequestCreatedEvent` | 同上 | `serviceRequestId,encounterId` | 5.1.10 |
| `DischargeRequestCreatedEvent` | 同上 | `serviceRequestId,encounterId` | 5.1.11 |
| `NursingOrderUpsertedEvent` | 同上 | `serviceRequestId,categoryEnum` | 5.1.12 |
| `ServiceRequestExecutedCreatedEvent` | 同上 | `serviceRequestId,basedOnTable,basedOnId` | 5.1.13 |
| `NursePricingServiceRequestCreatedEvent` | 同上 | `aggregateId(serviceRequestId),encounterId,patientId,activityId,quantity,unitCode,basedOnTable,basedOnId,statusEnum` | 5.1.14 |
| `NursePricingServiceRequestRemovedEvent` | 同上 | `aggregateId(serviceRequestId),encounterId,basedOnTable,basedOnId` | 5.1.14a |
| `OutpatientRegistrationServiceRequestCreatedEvent` | 同上 | `serviceRequestId,encounterId` | 5.1.15 |

> **读侧**：`generate_source_enum` 在写表路径写死 `nurse-pricing`；**`statusEnum`** 随 **`createFromNursePricing`** 落库；该事件**不含** `categoryEnum`/`requesterId`（写表已在域方法落库）。**非** OrderInfo RM 投影。

**发布时机**：beforeCommit。

---

## 8. 删除与可见性契约

- 草稿删除为逻辑删除或等价移除；已进入执行链路不可删。
- 默认查询过滤已删除数据；审计场景可显式包含。
- 删除/签退前必须通过读侧门控断言。

---

## 9. 框架优先适配规则

- 复用软删、审计、事件基础设施。
- 查询分层：QueryService 列表查询与写前强加载分离。
- 默认直接实现类，不强制接口+Impl 双层。

---

## 10. 生成与验收清单

- §4 字段表与 DM §3 主表/子表字段逐项对齐，无遗漏（含 `statusEnum` 状态字段全量映射）
- §5.1 共 17 个写方法小节，方法名与 DM §6.1 完全一致
- 每个写方法均包含命令文件、字段全集表格、绑定项、方法契约摘要
- §5.2 读方法与 DM §6.2 对齐，含 **`mapDetailListModelsByServiceRequestIds`**（§6.2.24）
- §6 BR 规则映射覆盖申请草稿/签发/签退/执行/校对/停用/拒绝等全生命周期
- §7 事件 16 条与 DM §5.1 全量对齐（含 Draft/Active/Completed 状态跃迁事件）
- 删除语义、软删可见性、分页语义、幂等同步策略均可直接落地
- 无"待确认/后续补充/占位实现"措辞

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-WKF-004
- 本 SDD 与共享事件模块

### 11.2 DoD

- 写读方法签名、状态迁移与事件触发与 §5/§7 一致。
- 关键 BR（收费门控、冲销、分轨、互斥）有测试绑定。

### 11.3 失败处理

- 方法漏实现或事件错位：回退修订并阻断成码。

---

## 定稿检查清单（可打勾）

- [x] 固定骨架完整
- [x] 写侧 17 方法全覆盖
- [x] 读侧 12 方法全覆盖
- [x] BR 与事件覆盖
- [x] 无 YAML / `sdd_status` / `dm_code`

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

## 12. 文档修订记录（增量）

| 日期 | 摘要 |
| --- | --- |
| 2026-10-08 | **代码先行回写**：§5.2 `listInpatientDoctorStationOrders` 排除 24/25 + 挂靠术式术前嘱托（禁止全排 52）；对齐口径 v0.10 |
| 2026-10-08 | **代码先行回写**：§4.2 术前嘱托 basedOn 术式；麻醉平级无 basedOn；对齐 DM v1.1.37 / 住院手术口径 v0.7 |
| 2026-10-08 | **代码先行回写**：§4.2 `procedureDefinitionId` 注明加收由护士划价；对齐 DM v1.1.36 / 住院手术口径 v0.6（开立禁止附加） |
| 2026-10-07 | **代码先行回写**：§4.2 增补 `procedureDefinitionId`；`activityId=0` 术式/无计费锚占位；对齐 DM v1.1.35 / 住院手术口径 v0.5 |
| 2026-08-08 | **停嘱校对**：§4.2 增补 `stopPerformerCheckId`/`stopCheckTime`；§5.1.8/8a/8b/8c/8d；**BR-SRQ-016***；对齐 **DM v1.1.29** / **AS-IP-NUR-003 v1.16** |
| 2026-07-26 | §5.1.5：`withdrawSignedServiceRequest` **已是 DRAFT 幂等返回**；上游 **DM v1.1.27** |
| 2026-07-18 | **DD §11.7**：§4.2 增补 **`encounterDiagnosisIds`**（`encounter_diagnosis_ids` 有序 ids 串，主诊 id 第一） |
| 2026-07-09 | **门诊申请退费（AS-OP-DOR-004 BR-DRA-007）**：§5.1.9a **`createServiceOrderRefund`**；§7 **`ServiceOrderRefundRequestedEvent`**；上游 **DM v1.1.21** |
| 2026-06-18 | **IF-IP-DOR-25 成码闭环**：§5.1.8a 对齐实现（`operatorId`、`fromStopped` 细节）；上游 **DM v1.1.18** / **AS v1.17** |
| 2026-06-18 | **取消停嘱（IF-IP-DOR-25）**：§5.1.8a `cancelStopServiceRequest`；**BR-SRQ-016a**；事件 `ServiceRequestStopCancelledEvent`；**BR-SRQ-019** 与 IF-IP-DOR-20 对齐；上游 **DM** / **AS-IP-DOR-003 v1.16** |
| 2026-06-18 | §5.1.14a **`removeFromNursePricing`** + **`NursePricingServiceRequestRemovedEvent`**；上游 **DM v1.1.16**（**AS-IP-NUR-005** IF-IP-PRC-06；关闭 **GAP-IP-PRC-007/010**） |
| 2026-06-16 | §4.6.5 增补应用消费锚点；上游 **DM v1.1.15**（对齐 **AS-OP-DOR-003-SDD v3.25**） |
| 2026-06-16 | §4.6.5 **`ServiceRequestDetailListModel`**；§5.2 **`mapDetailListModelsByServiceRequestIds`**（TC-WKF-SRQ-QRY-023）；§6 **BR-SRQ-QRY-007**；关闭 **G-DOR-003-11** / **G-IP-DOR-003-02**；上游 **DM v1.1.14** |
| 2026-05-29 | **execution_count**：`quantity`/`unit_code` 专用于账单计价；`execution_count` 供护士站拆解可执行次数（临嘱/门诊）；禁止再用 quantity 冒充执行次数 |
| 2026-05-28 | **BR-SRQ-007**：**禁止**域层将长期医嘱 `quantity` 强制归 1；`quantity`/`unit_code` 由载荷或应用层目录解析；对齐 **DM v1.1.9** / **AS-OP/IP-DOR-003** |
| 2026-05-28 | **ReadModel 废止**（DM **v1.1.8**）：列表读 **`ServiceRequestQueryService`** + **`InpatientOrderListSkeleton`**（**非** OrderInfo RM） |

---

**本 SDD 正文结束。**
