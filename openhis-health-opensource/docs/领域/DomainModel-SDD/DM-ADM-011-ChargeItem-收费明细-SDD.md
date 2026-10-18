# DM-ADM-011 ChargeItem 收费明细 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md`（**v1.6.6**，含 CHS bridge 读侧与 §9.4 跨模块产品目录）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：Financial（财务域 · 收费明细管理）  
> **聚合**：ChargeItem（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | 已绑定 DM-ADM-011 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.financial.chargeitem` | 满足 | `chargeitem` 全小写 |
| 固定技术口径（ID Long、时间 LocalDateTime、软删、beforeCommit、分页等） | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 全覆盖并给出 Command 字段全集 |
| DM 第 7 章 BR | 满足 | §6 映射并可追溯 |
| DM 第 5 章事件 | 满足 | §7 |

**与 DM 显式差异（采纳依据：DM 优先 + SDD 落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 组套存储 | §2.1 强调多行替代 `children_json` | SDD 固化 `busNo + parentId` 双锚点 |
| 状态与 FHIR 映射 | §2.4 给出映射建议 | SDD 保留本地状态为写侧权威，映射仅用于交换 |
| 事件体系 | §5 同时给业务事件+投影事件 | SDD 主表事件全列并标注投影事件为并列发布 |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 在单一文档内固化 **ChargeItem 聚合** 的持久化模型、领域服务契约、查询契约、事件与不变式实现策略，使下一会话可一次性生成 `zxplat/zxplat-domain` 领域层实现并通过编译。
- 将实现前澄清前置到 SDD：每个写方法对应独立 Command 文件，命令字段全集列出，覆盖开立、激活、结算、退费、账户切换全链路。
- 单次成稿目标：正文无后续补充/待确认措辞，缺口在文档阶段关闭。

### 1.2 边界

- **在内**：收费明细事实创建、状态推进、金额字段、退费链路追溯、组套多行规则。
- **在外**：支付通道执行、医保平台交互、发票平台流程、库存扣减。
- **跨域仅端口声明**：仅声明与 Encounter/Account/ChargeItemDefinition/PaymentReconciliation/Invoice 的关联规则。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- 模块：`zxplat/zxplat-domain`。

### 2.2 包结构（`com.zxplat.domain.financial.chargeitem`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `ChargeItem` |
| `valueobject` | `ChargeItemStatus`、`GenerateSource`、`ChargeItemContext` |
| `enums`（共享） | 复用状态、来源、上下文枚举 |
| `command` | 每个写方法一个 Java 文件 |
| `repository` | `ChargeItemRepository`（仅 `JpaRepository` 接口） |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.financial.chargeitem` |

**存储口径（DM 优先）**：`*_enum` 默认 `String`（存枚举 code）；`*_code` 为 `String`；`_flag` 为 `Boolean`；软删统一 `Boolean isDeleted`。

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键 / 外键 | `Long`；聚合根主键 `id`；核心外键 `patientId/encounterId/accountId/definitionId` |
| 时间 | 业务时间字段统一 `LocalDateTime` |
| `*_enum` | 枚举编码列，默认 `String`（存枚举 code） |
| `*_code` | 字典编码列，`String` |
| `_flag` | `Boolean` |
| 软删 | `Boolean isDeleted`，默认查询过滤 |
| 实体 | `@Entity` + 显式 `@Table`/`@Column` |
| 值对象 | `@Embeddable` |
| 仓储 | `JpaRepository<ChargeItem, Long>` |
| 分页 | `Pageable`，默认 `createdAt DESC` |
| 事务 | 领域服务方法不机械添加 `@Transactional` |
| 事件 | 服务内直接构造并 beforeCommit 发布 |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `adm_charge_item` | `ChargeItem` | 收费明细聚合根单表（组套多行） |

### 4.2 聚合根 `ChargeItem`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 / 约束 |
| -------------- | ---- | ---- | ----------- |
| `id` | `Long` | 系统 | 收费明细标识 |
| `statusEnum` | `String` | 是 | 收费状态（枚举 code） |
| `generateSourceEnum` | `String` | 是 | 生成来源（枚举 code） |
| `contextEnum` | `String` | 是 | 收费上下文（枚举 code） |
| `busNo` | `String` | 否 | 层级业务编码 |
| `parentId` | `Long` | 否 | 父明细标识（组套子行） |
| `prescriptionNo` | `String` | 否 | 结算快照处方号（非业务真源；门诊药品以 MedicationRequest 为准，结算前 `stampPrescriptionSnapshots` 抄写） |
| `patientId` | `Long` | 是 | 患者标识 |
| `encounterId` | `Long` | 是 | 就诊标识 |
| `occurrenceTime` | `LocalDateTime` | 是 | 费用发生时间 |
| `feeConfirmerId` | `Long` | 否 | 医技确费人；空=未确费 |
| `feeConfirmedAt` | `LocalDateTime` | 否 | 医技确费时刻 |
| `requestingOrgId` | `Long` | 否 | 开立科室 |
| `costOrgId` | `Long` | 否 | 成本科室 |
| `quantityValue` | `BigDecimal` | 是 | 数量 |
| `quantityUnit` | `String` | 是 | 数量单位 |
| `unitPrice` | `BigDecimal` | 是 | 单价 |
| `totalPrice` | `BigDecimal` | 是 | 总价 |
| `definitionId` | `Long` | 是 | 定价定义标识 |
| `defDetailId` | `Long` | 否 | 定价明细标识 |
| `baseAmount` | `BigDecimal` | 是 | 原价 |
| `discountAmount` | `BigDecimal` | 是 | 折后金额 |
| `surchargeAmount` | `BigDecimal` | 是 | 附加金额 |
| `systemDiscountPrice` | `BigDecimal` | 否 | 系统优惠金额 |
| `manualAdjustedPrice` | `BigDecimal` | 否 | 人工调价金额 |
| `overrideReasonCode` | `String` | 否 | 改价原因编码 |
| `overrideReasonText` | `String` | 否 | 改价原因说明 |
| `entererId` | `Long` | 否 | 收费人（结算/退费完成写入；开单医生见 Request.requester_id） |
| `enteredDate` | `LocalDateTime` | 否 | 收费时间（与 payment `bill_date` 同值；开立时刻见 Request.`req_opened_time`） |
| `serviceTable` | `String` | 是 | 服务来源表名 |
| `serviceId` | `Long` | 是 | 服务来源主键 |
| `productTable` | `String` | 否 | 产品定义表名 |
| `productId` | `Long` | 否 | 产品定义主键 |
| `procedureId` | `Long` | 否 | 执行记录标识 |
| `dispenseTable` | `String` | 否 | 发放记录表名 |
| `dispenseId` | `Long` | 否 | 发放记录主键 |
| `claimStateEnum` | `Integer` | 否 | 医保结算状态 |
| `printCount` | `Integer` | 否 | 打印次数 |
| `accountId` | `Long` | 是 | 账户标识 |
| `orgId` | `Long` | 是 | 机构标识 |
| `refundId` | `Long` | 否 | 退费关联标识 |
| `encounterDiagnosisIds` | `String` | 否 | 绑定就诊诊断 ids 有序串（`adm_encounter_diagnosis.id`，**主诊 id 第一**；落库列 `encounter_diagnosis_ids`） |
| `tcmFlag` | `Boolean` | 否 | 中医标识 |
| `isDeleted` | `Boolean` | 系统 | 逻辑删除标记 |

**不变式方法（示例命名，实现须一致）**：`assertTraceabilityAnchors`、`assertStatusTransitionAllowed`、`assertAmountFormulaValid`、`assertBundleConsistency`、`assertRefundTrailRequired`。

### 4.3 子实体

无独立子表。组套子项以主表多行表达（父行+子行）。

### 4.4 组套层级契约

| 规则项 | 约束 |
| ------ | ---- |
| 父行编码 | `busNo` 为组内根键，如 `GRP20250418001` |
| 子行编码 | `busNo = 父行.busNo + "." + 子项稳定编码` |
| 父子关联 | 子行 `parentId` 指向父行 `id`（推荐必填） |
| 金额一致 | 子行折后金额和应与父行组套总价口径一致 |
| 联动处理 | 退费/取消时父子行需同批次收口 |

### 4.5 仓储接口

- `ChargeItemRepository extends JpaRepository<ChargeItem, Long>`

**说明**：组套查询可用 `busNo` 前缀或 `parentId` 等值，按场景择优。

### 4.6 查询模型（`queries.model`）

每个读方法一个子节，字段级逐行列清：

#### 4.6.1 `ChargeItemByIdQueryModel`（对应 `loadChargeItemsByIds`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_charge_item.id` | 明细标识 |
| `statusEnum` | `String` | `adm_charge_item.status_enum` | 当前状态 |
| `encounterId` | `Long` | `adm_charge_item.encounter_id` | 就诊标识 |

#### 4.6.2 `ChargeItemByRequestQueryModel`（对应 `listChargeItemsByRequestIds`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_charge_item.id` | 明细标识 |
| `serviceTable` | `String` | `adm_charge_item.service_table` | 来源表 |
| `serviceId` | `Long` | `adm_charge_item.service_id` | 来源主键 |
| `statusEnum` | `String` | `adm_charge_item.status_enum` | 状态 |

#### 4.6.3 `ChargeItemEncounterListQueryModel`（对应 `listChargeItemsByEncounterId`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `id` | `Long` | `adm_charge_item.id` | 明细标识 |
| `encounterId` | `Long` | `adm_charge_item.encounter_id` | 就诊标识 |
| `quantityValue` | `BigDecimal` | `adm_charge_item.quantity_value` | 数量 |
| `totalPrice` | `BigDecimal` | `adm_charge_item.total_price` | 总价 |
| `statusEnum` | `String` | `adm_charge_item.status_enum` | 状态 |

#### 4.6.4 `CostDetailsQueryModel`（对应 `queryCostDetails`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `chargeItemId` | `Long` | 投影/主表 | 明细标识 |
| `amountView` | `BigDecimal` | 规则计算 | 展示金额 |
| `orgId` | `Long` | `adm_charge_item.org_id` | 科室 |
| `statusEnum` | `String` | `adm_charge_item.status_enum` | 状态 |

#### 4.6.5 `InpatientCostDetailsQueryModel`（对应 `queryInpatientCostDetails`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `chargeItemId` | `Long` | 投影/主表 | 明细标识 |
| `inpatientBillNo` | `String` | 住院结算关联/QueryService | 住院结算单号 |
| `amountView` | `BigDecimal` | 规则计算 | 展示金额 |
| `statusEnum` | `String` | `adm_charge_item.status_enum` | 状态 |

#### 4.6.6 计时累计用量（对应 `queryOxygenAndECGDuration`）

**入参**：`ChargeItemQueryCommand`（`encounterId`、`activityDefinitionId` 必填；`occurrenceDate` 可选）。

**出参**：`Long`——已累计小时数（`wkf_service_request.quantity` 求和取整，见 DM §6.2.5）。

| 命令字段（Java） | 类型 | 说明 |
| ---------------- | ---- | ---- |
| `encounterId` | `Long` | 就诊标识（必填） |
| `activityDefinitionId` | `Long` | 诊疗项目定义 id，对应 `wkf_service_request.activity_id`（必填） |
| `occurrenceDate` | `LocalDate` | 非空则仅统计该自然日（按 occurrence/authored/created 日期） |

#### 4.6.7 `ChargeItemDefInfoWithChildrenQueryModel`（对应 `listChargeItemDefInfoWithChildren`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `parentId` | `Long` | `adm_charge_item.parent_id` | 父行标识 |
| `busNo` | `String` | `adm_charge_item.bus_no` | 层级键 |
| `definitionId` | `Long` | `adm_charge_item.definition_id` | 定价标识 |
| `children` | `List<ChargeItemDefInfoWithChildrenQueryModel>` | 组装结果 | 子行集合 |

#### 4.6.8 `ChargeItemsByPaymentBillQueryModel`（对应 `listChargeItemsByPaymentBill`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `chargeItemId` | `Long` | 结算关联表/QueryService | 明细标识 |
| `billNo` | `String` | 结算单关联 | 结算单号 |
| `statusEnum` | `String` | `adm_charge_item.status_enum` | 状态 |

#### 4.6.9 `ChargeItemsForInvoiceQueryModel`（对应 `listChargeItemsForInvoice`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `chargeItemId` | `Long` | `adm_charge_item.id` | 明细标识 |
| `invoiceCandidateAmount` | `BigDecimal` | 规则计算 | 开票金额 |
| `printCount` | `Integer` | `adm_charge_item.print_count` | 打印次数 |

#### 4.6.10 `ChargeItemsByInpatientBillQueryModel`（对应 `listChargeItemsByInpatientBill`）

| 字段名（Java） | 类型 | 来源（表.列） | 说明 |
| -------------- | ---- | ------------- | ---- |
| `chargeItemId` | `Long` | 住院结算关联 | 明细标识 |
| `inpatientBillNo` | `String` | 住院结算关联 | 住院结算单号 |
| `statusEnum` | `String` | `adm_charge_item.status_enum` | 状态 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.0 再生链解析 — `ChargeItemRegenerationChainSupport`

| 方法 | 用途 | 规则 |
| --- | --- | --- |
| `collectSupersededSettledAncestorIds(Long encounterId)` | Gate/读侧：应忽略的祖先 `SETTLED` 主键集合 | **BR-REF-020** / **BR-IP-RVS-011** |
| `mapSettledAncestorToActiveRegenerationLeafId(Long encounterId)` | 祖先 `SETTLED` → 链末端 `PLANNED`/`PENDING_SETTLEMENT` 叶 | 反结 Pre、半退收口 |
| `collectSupersededSettledAncestorIdsFromRows(List<ChargeItem>)` | 同就诊已装载行的链解析（内存） | 与上同口径 |

**契约**：自链末端有效再生叶沿 `refund_id` **向上递归**；支持多轮「结算→反结→再结算」形成的 `SETTLED→…→PENDING_SETTLEMENT` 多层链；`ChargeItemQueryService` 与 `ChargeItemDomainService` 写侧 Gate 均委托本组件。

### 5.1 写侧 — `ChargeItemDomainService`

#### 5.1.1 `createRegistrationChargeItem(CreateChargeItemCommand cmd)` → `Long`

**命令文件**：`CreateChargeItemCommand.java`

**字段全集**：`patientId`,`encounterId`,`accountId`,`definitionId`,`serviceTable`,`serviceId`,`quantityValue`,`unitPrice`,`occurrenceTime`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertRegistrationCreatable` |
| `statusTransitionMethod` | `ChargeItem.initAsDraftOrPlanned` |
| `repositoryChecks` | `ChargeItemRepository.findByBusinessAnchor` |
| `eventPublishAnchor` | `new RegistrationChargeItemCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-001` |

**方法契约摘要**：前置条件为锚点完整且具备幂等识别；持久化影响为创建挂号费明细；失败语义为锚点缺失或重复冲突拒绝；事件触发发布 `RegistrationChargeItemCreatedEvent`。**注释锚点**：锚点校验 -> 幂等判断 -> 写入 -> 事件。

---

#### 5.1.1a `retargetRegistrationFeeDepartment(Long encounterId, Long fromOrgId, Long toOrgId)` → `void`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 号源锚点且科室列等于原科室 |
| `statusTransitionMethod` | 无 |
| `repositoryChecks` | `ChargeItemRepository.findAllByEncounterId` |
| `eventPublishAnchor` | 无 |
| `unitTestCaseId` | — |

**方法契约摘要**：把挂号费行上仍等于原科室的 `org_id`、`requesting_org_id`、`cost_org_id` 改为目标科室。医嘱费用不改。参数不全拒绝；科室相同则空操作。**注释锚点**：参数校验 -> 筛选号源锚点 -> 写入。

---

#### 5.1.2 `createDraftChargeItemFromOrder(CreateDraftChargeItemFromOrderCommand cmd)` → `Long`

**命令文件**：`CreateDraftChargeItemFromOrderCommand.java`

**字段全集**：`patientId`,`encounterId`,`accountId`,`definitionId`,`serviceTable`,`serviceId`,`contextEnum`,`quantityValue`,`unitPrice`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertOrderDraftCreatable` |
| `statusTransitionMethod` | `ChargeItem.transitToDraft` |
| `repositoryChecks` | `ChargeItemRepository.findByServiceAnchor` |
| `eventPublishAnchor` | `new OrderDraftChargeItemCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-002` |

**方法契约摘要**：前置条件为来源锚点与定价锚点完整（**定价行**由应用层 Pre 经 **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`** 解析，**BR-CHG-016**；**签发 FIFO 分段**另经 **`selectExecutionPriceLineForChargeDraft(lotNumber)`**，**BR-CHG-017**）；持久化影响为生成草稿明细；失败语义为来源信息不完整或重复创建冲突；事件触发发布 `OrderDraftChargeItemCreatedEvent`。**注释锚点**：校验 -> 写入 -> 持久化 -> 事件。

---

#### 5.1.3 `priceByNurseStation(PriceByNurseStationCommand cmd)` → `List<Long>`

**命令文件**：`PriceByNurseStationCommand.java`

**字段全集**：`encounterId`,`patientId`,`accountId`,`items`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertNursePricingAllowed` |
| `statusTransitionMethod` | `ChargeItem.transitToPendingSettlement` |
| `repositoryChecks` | `ChargeItemRepository.findExistingByServiceAnchorBatch` |
| `eventPublishAnchor` | `new NursePricingChargeItemCreatedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-003` |

**方法契约摘要**：前置条件为计价锚点完整与金额公式合法；持久化影响为新增/更新护士划价明细并置 `PENDING_SETTLEMENT`；失败语义为金额非法或锚点冲突拒绝；事件触发发布 `NursePricingChargeItemCreatedEvent`。**注释锚点**：批量校验 -> 写入 -> 持久化 -> 事件。

---

#### 5.1.3a `offsetNursePricingChargeItemsByServiceAnchors(OffsetNursePricingChargeItemsByServiceAnchorsCommand cmd)` → `void`

**命令文件**：`OffsetNursePricingChargeItemsByServiceAnchorsCommand.java`

**字段全集**：`operatorId`、`encounterId`、`serviceTable`、`serviceIds`

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | `findAllByEncounterIdAndServiceTableAndServiceIdIn`；结算 Gate：`isNonRollbackableChargeStatus`；跳过已被**冲销负向行**（`ChargeItem#isPricingOffsetLine()`）指向的源；反结再生正向（数量>0 且 `refund_id`→SETTLED）仍可冲 |
| `statusTransitionMethod` | 原正向行保持 `PENDING_SETTLEMENT`；插入负向冲销行（数量/金额取反，`refund_id`→源行） |
| `eventPublishAnchor` | 每行 `saveAll` 后 `NursePricingChargeItemOffsetEvent(offsetId, sourceId, encounterId)` |
| `unitTestCaseId` | `TC-CHG-W-003A-NP` |

**方法契约摘要**：取消护士/医技划价按上游锚点对尚未冲销的 **`PENDING_SETTLEMENT` + (`nurse-pricing`|`medical-tech-pricing`) 正向行**（含反结再生正向）插入冲销行；`refund_id` 双义见 **DM v1.6.28** §3.1；同锚点含已结算/退费链路则整批拒绝（**BR-CHG-002**）；无待冲销行静默；对齐 **AS-IP-NUR-005** IF-IP-PRC-06 / **AS-MT-CHG-001**。

> **历史**：原 `voidNursePricingChargeItemsByServiceAnchors`（物理删除）+ `NursePricingChargeItemRemovedEvent` 已由本方法替代。

---

#### 5.1.2a `voidUnbilledDraftChargeItemsByServiceAnchor(VoidUnbilledDraftChargeItemsByServiceAnchorCommand cmd)` → `void`

**命令文件**：`VoidUnbilledDraftChargeItemsByServiceAnchorCommand.java`（`encounterId`、`serviceTable`、`serviceId`）

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | `findAllByEncounterIdAndServiceTableAndServiceIdAndStatusEnum(..., DRAFT)` |
| `statusTransitionMethod` | 物理删除草稿行（`repository.deleteAll`） |
| `unitTestCaseId` | `TC-CHG-W-003A` |

**方法契约摘要**：**IF-DOR-14** 删医嘱前清理单锚点 `DRAFT` 费用；无行静默。

---

#### 5.1.2b `voidUnbilledDraftChargeItemsByServiceAnchors(Long encounterId, String serviceTable, Collection<Long> serviceIds)` → `void`

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | `findAllByEncounterIdAndServiceTableAndServiceIdInAndStatusEnum(..., DRAFT)` |
| `unitTestCaseId` | `TC-CHG-W-003B` |

**方法契约摘要**：签发重置等多锚点清理 `DRAFT`；入参空集合静默。

---

#### 5.1.4 `activateChargeItemAfterExecution(ActivateChargeItemAfterExecutionCommand cmd)` → `void`

**命令文件**：`ActivateChargeItemAfterExecutionCommand.java`

**字段全集**：`chargeItemIds`,`procedureId`,`performedAt`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertExecutionActivationAllowed` |
| `statusTransitionMethod` | `ChargeItem.transitDraftToPlanned` |
| `repositoryChecks` | `ChargeItemRepository.findAllByIds` |
| `eventPublishAnchor` | `new ChargeItemActivatedByExecutionEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-004` |

**方法契约摘要**：前置条件为仅 `DRAFT` 可推进；持久化影响为批量状态推进到 `PLANNED`（**待收费**；门诊签发及 **`planDraftChargeItemsAfterAdviceSigned`** 委托路径）；**不**用于住院护士站执行入账；失败语义为前态不符拒绝；事件触发发布 `ChargeItemActivatedByExecutionEvent`。**注释锚点**：状态校验 -> 状态推进 -> 持久化 -> 事件。

---

#### 5.1.4a `planDraftChargeItemsAfterAdviceSigned(Long encounterId, String serviceTable, Long serviceId)` → `void`

**命令文件**：无独立 Command；**实现**为就诊与上游医嘱请求表锚点三参（与 `ChargeItemRepository#findAllByEncounterIdAndServiceTableAndServiceIdAndStatusEnum` 对齐）。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 委托 `activateChargeItemAfterExecution` 内既有批量校验 |
| `statusTransitionMethod` | 查询 `DRAFT` 行 → 组装 `BatchChargeItemIdsCommand` → `activateChargeItemAfterExecution` |
| `repositoryChecks` | `ChargeItemRepository.findAllByEncounterIdAndServiceTableAndServiceIdAndStatusEnum(..., DRAFT)` |
| `eventPublishAnchor` | 同 **5.1.4**（`ChargeItemActivatedByExecutionEvent`） |
| `unitTestCaseId` | `TC-CHG-W-004-OP-SIGN`（建议） |

**方法契约摘要**：**AS-OP-DOR-003 · IF-DOR-15** / **AS-IP-DOR-003 · IF-IP-DOR-15（临时）** 签发成功后调用；锚点不完整或无 `DRAFT` 行时静默返回；否则批量 `DRAFT→PLANNED`（**待收费**）；**长期住院医嘱无费用行**；满足 `preSettleChargeItems` 门禁。

---

#### 5.1.4b `rollbackPlannedChargeItemsAfterAdviceSignedOff(Long encounterId, String serviceTable, Long serviceId)` → `void`

**命令文件**：无独立 Command；三参同 **5.1.4a**。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 委托 `cancelExecutionRollbackChargeItem` 内既有批量校验 |
| `statusTransitionMethod` | 查询 `PLANNED` 行 → 组装 `BatchChargeItemIdsCommand` → `cancelExecutionRollbackChargeItem` |
| `repositoryChecks` | `ChargeItemRepository.findAllByEncounterIdAndServiceTableAndServiceIdAndStatusEnum(..., PLANNED)` |
| `eventPublishAnchor` | 同 **5.1.5**（`ChargeItemExecutionCancelledEvent`） |
| `unitTestCaseId` | `TC-CHG-W-005-OP-WITHDRAW`（建议） |

**方法契约摘要**：**IF-DOR-16** 签退成功后调用；将锚点下仍为 `PLANNED` 的明细回退 `DRAFT`；无匹配行时静默返回。

---

#### 5.1.4d `activateChargeByExe(ActivateChargeByExeCommand cmd)` → `void`

**命令文件**：`ActivateChargeByExeCommand.java`（`BatchCommand<Item>`）

**Item 字段全集**：`chargeItemId,procedureId,performedAt`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 仅 `PLANNED` 或 `DRAFT` 可激活 |
| `statusTransitionMethod` | 写 `procedureId` + `PENDING_SETTLEMENT` |
| `repositoryChecks` | `loadMap(chargeItemIds)` |
| `eventPublishAnchor` | `ChargeItemActivatedByExecutionEvent`（含 `procedureId`） |
| `unitTestCaseId` | `TC-CHG-W-004-IP-EXE` |

**方法契约摘要**：**临时**医嘱执行后激活已有费用行（签发后常态 **`PLANNED`**，兼容未升位 **`DRAFT`**）→ **`PENDING_SETTLEMENT`（待结算）**。**消费方**：**IF-IP-EXE-02**。

---

#### 5.1.4e `createPlannedChargeItemFromOrderExecution(CreateChargeItemCommand cmd)` → `Long`

**命令文件**：`CreateChargeItemCommand.java`（`generateSource=ORDER_EXECUTE`，含 `procedureId`、可选 `prescriptionNo`）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 计价锚点完整 |
| `statusTransitionMethod` | 直接 `PENDING_SETTLEMENT` |
| `repositoryChecks` | `createByCommand` |
| `eventPublishAnchor` | `ChargeItemActivatedByExecutionEvent` |
| `unitTestCaseId` | `TC-CHG-W-004-IP-EXE-LT` |

**方法契约摘要**：**长期**医嘱每计划点执行后新建 `PENDING_SETTLEMENT` 费用行；**中药代煎费**可写 `prescriptionNo` 供方级 void/追溯。**消费方**：**IF-IP-EXE-02**、**BR-IP-TCM-008**。

---

#### 5.1.5a `voidOrderExecuteChargeItemsByProcedureIds(VoidOrderExecuteChargeItemsByProcedureIdsCommand cmd)` → `void`

**命令文件**：`VoidOrderExecuteChargeItemsByProcedureIdsCommand.java`

**字段全集**：`procedureIds`,`operatorId`

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | `findAllByProcedureIdIn`；结算 Gate；内存筛选执行族 |
| `statusTransitionMethod` | 逻辑删除 `PENDING_SETTLEMENT` + 执行族（`order-execute` / `anesthesia-execute`，`GenerateSource#isOrderExecuteFamilyCode`） |
| `eventPublishAnchor` | `ChargeItemExecutionCancelledEvent` |
| `unitTestCaseId` | `TC-CHG-W-005-IP-CXL` |

**方法契约摘要**：**AS-IP-NUR-012** 取消执行 Charge 回退（长临统一）；按 `procedureId` 定位；**须**覆盖麻醉执行分码，禁止仅删 `ORDER_EXECUTE`。

---

#### 5.1.5b `voidPendingOrderExecuteChargeItemsByChargeItemIds(Collection<Long> chargeItemIds)` → `void`

**命令文件**：无独立 Command；入参为收费明细主键集合。

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | `findAllByIdIn` |
| `statusTransitionMethod` | 逻辑删除命中 `PENDING_SETTLEMENT` + `ORDER_EXECUTE` 行 |
| `eventPublishAnchor` | `ChargeItemExecutionCancelledEvent`（`deleteAll` 后逐条） |
| `unitTestCaseId` | `TC-CHG-W-005-IP-DECOCTION-VOID` |

**方法契约摘要**：**BR-IP-TCM-008** 代煎费刷新前 void；应用层 Pre 已按就诊+方+`occurrence`+产品收窄 id。**消费方**：`InpatientTcmDecoctionExecuteSupport#voidOrderExecuteDecoctionChargeAtPlanTime`（`application.../nursestation/orderexecute/support）。

---

#### 5.1.4c `planDraftChargeItemsAfterAdviceSignedForAnchors(Long encounterId, String serviceTable, List<Long> serviceIds)` → `void`

**命令文件**：无独立 Command；**就诊 + service_table + 多 service_id** 与门诊医生站**药耗签发后多条发放锚点**对齐。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 同 **5.1.4a**，按 id 列表迭代委托批量校验 |
| `statusTransitionMethod` | 逐锚点查询 `DRAFT` → 组装 `BatchChargeItemIdsCommand` → `activateChargeItemAfterExecution` |
| `repositoryChecks` | 按 encounter + table + id ∈ serviceIds + `DRAFT` |
| `eventPublishAnchor` | 同 **5.1.4** |
| `unitTestCaseId` | `TC-CHG-W-004-OP-SIGN-BATCH`（建议） |

**方法契约摘要**：**AS-OP-DOR-003 · IF-DOR-15** 药品/耗材路径，`replaceFifo*` 之后调用；将多条 **`med_medication_dispense` / `wkf_device_dispense`** 锚点下 **`DRAFT`** 批量 **`DRAFT`→`PLANNED`**；无行则静默返回。

---

#### 5.1.4d `rollbackPlannedChargeItemsAfterAdviceSignedOffForAnchors(Long encounterId, String serviceTable, List<Long> serviceIds)` → `void`

**命令文件**：无独立 Command。

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 同 **5.1.4b**，按 id 列表迭代 |
| `statusTransitionMethod` | 查询 `PLANNED` → `cancelExecutionRollbackChargeItem` |
| `repositoryChecks` | encounter + table + id ∈ serviceIds + `PLANNED` |
| `eventPublishAnchor` | 同 **5.1.5** |
| `unitTestCaseId` | `TC-CHG-W-005-OP-WITHDRAW-BATCH`（建议） |

**方法契约摘要**：**IF-DOR-16** 药耗签退**先于**请求锚点回退：对多条发放锚点 **`PLANNED`→`DRAFT`**；无行静默返回。

---

#### 5.1.4f `voidUnbilledDraftChargeItemsByDispenseAnchors(Long encounterId, String dispenseTable, Collection<Long> dispenseIds)` → `void`

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | `findAllByEncounterIdAndDispenseTableAndDispenseIdInAndStatusEnum(..., DRAFT)` |
| `unitTestCaseId` | `TC-CHG-W-005-DSP-VOID` |

**方法契约摘要**：药/耗 FIFO 分段签退前按 `dispense_*` 清理 `DRAFT`；与 **5.1.2b**（`service_*` 锚点）互补。

---

#### 5.1.4g `planDraftChargeItemsAfterAdviceSignedByDispenseAnchors(Long encounterId, String dispenseTable, Collection<Long> dispenseIds)` → `void`

| 绑定项 | 内容 |
| ------ | ---- |
| `statusTransitionMethod` | 查询 `DRAFT` → `activateChargeItemAfterExecution` |
| `unitTestCaseId` | `TC-CHG-W-004-DSP-SIGN` |

**方法契约摘要**：**IF-DOR-15** 药/耗签发后按发放分段批量 `DRAFT→PLANNED`（现行 Java 方法名）。

---

#### 5.1.4h `rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors(Long encounterId, String dispenseTable, Collection<Long> dispenseIds)` → `void`

| 绑定项 | 内容 |
| ------ | ---- |
| `statusTransitionMethod` | 查询 `PLANNED` → `cancelExecutionRollbackChargeItem` |
| `unitTestCaseId` | `TC-CHG-W-005-DSP-WITHDRAW` |

**方法契约摘要**：**IF-DOR-16** 药/耗签退按发放分段 `PLANNED→DRAFT`。

---

#### 5.1.4i `createChargeItemsLinkingRegeneratedAnchors(CreateChargeItemsLinkingRegeneratedAnchorsCommand cmd)` → `Long`

**命令文件**：`CreateChargeItemsLinkingRegeneratedAnchorsCommand.java`

| 绑定项 | 内容 |
| ------ | ---- |
| `statusTransitionMethod` | 新建 `PLANNED` 行并写 `refund_id` 指向源 `SETTLED` |
| `unitTestCaseId` | `TC-CHG-W-004-REGEN` |

**方法契约摘要**：门诊半退保留链；与 **AS-OP-DOR-004**、**DM-MED-003/WKF-003** 克隆请求联动。

---

#### 5.1.5 `cancelExecutionRollbackChargeItem(CancelExecutionRollbackChargeItemCommand cmd)` → `void`

**命令文件**：`CancelExecutionRollbackChargeItemCommand.java`

**字段全集**：`chargeItemIds`,`reason`,`operatorId`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertExecutionRollbackAllowed` |
| `statusTransitionMethod` | `ChargeItem.rollbackOrRemoveByPolicy` |
| `repositoryChecks` | `ChargeItemRepository.findAllByIds` |
| `eventPublishAnchor` | `new ChargeItemExecutionCancelledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-005` |

**方法契约摘要**：前置条件为目标明细未进入不可回退链路；持久化影响为回退状态或受控移除；失败语义为已收费/已退费链路明细拒绝回退；事件触发发布 `ChargeItemExecutionCancelledEvent`。**注释锚点**：前态校验 -> 回退执行 -> 持久化 -> 事件。

---

#### 5.1.6 `settleChargeItems(SettleChargeItemsCommand cmd)` → `List<Long>`

**命令文件**：`SettleChargeItemsCommand.java`

**字段全集**：`chargeItemIds`,`settledAt`,`settlerId`,`paymentBillNo`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertSettleAllowed` |
| `statusTransitionMethod` | `ChargeItem.transitPlannedToSettled` |
| `repositoryChecks` | `ChargeItemRepository.findAllPlannedByIds` |
| `eventPublishAnchor` | `new ChargeItemSettledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-006` |

**方法契约摘要**：前置条件为全部目标行为 `PLANNED` 可结算；持久化影响为批量置 `SETTLED` 并将 `enterer_id`/`entered_date` 写为 `operatorId`/`billDate`（收费人/收费时间，与 payment `enterer_id`/`bill_date` 同源，**BR-OPC-016**）；**不再**写 `performer_id` 为收款人；失败语义为任一行前态不符或缺少 `operatorId` 触发拒绝；事件触发发布 `ChargeItemSettledEvent`。**注释锚点**：校验 -> 结算推进与收费人落库 -> 持久化 -> 事件。

---

#### 5.1.6a `stampPrescriptionSnapshots(StampChargeItemPrescriptionSnapshotCommand command)` → `void`

**命令文件**：`StampChargeItemPrescriptionSnapshotCommand.java`

**字段全集**：`operatorId`,`items[{chargeItemId,prescriptionNo}]`

| 绑定项 | 内容 |
| ------ | ---- |
| `repositoryChecks` | `ChargeItemRepository` 按 id 装载 |
| `unitTestCaseId` | `TC-CHG-W-006a` |

**方法契约摘要**：结算前盖写 `prescription_no` **快照**（不改状态）；期望号空或已一致则跳过；药品真源 `MedicationRequest`；诊疗侧仅申请单 **`AR`**（代煎 companion 不盖方号；**AS-OP-FIN-002 · BR-OPC-026**）。**注释锚点**：装载 -> 比对 -> saveAll。

---

#### 5.1.7 `returnRegistrationRefundChargeItems(BatchChargeItemIdsCommand cmd)` → `List<Long>`

**命令文件**：`BatchChargeItemIdsCommand.java`

**字段全集**：`chargeItemIds`,`operatorId`（退费操作人）、`billDate`（可选，退费时刻）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertRegistrationRefundAllowed` |
| `statusTransitionMethod` | `ChargeItem.transitToRefunded` |
| `repositoryChecks` | `ChargeItemRepository.findRefundableByIds` |
| `eventPublishAnchor` | `new RegistrationChargeItemRefundedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-007` |

**方法契约摘要**：前置条件为目标明细满足退号退费口径；持久化影响为收口为 `REFUNDED` 并将 `enterer_id`/`entered_date` 写为 `operatorId`/`billDate`（退费操作人覆盖收费人戳，**BR-OPC-016**）；失败语义为金额超限或状态不符拒绝；事件触发发布 `RegistrationChargeItemRefundedEvent`。**注释锚点**：资格校验 -> 状态收口与收费人落库 -> 持久化 -> 事件。

---

#### 5.1.8 `switchChargeItemAccount(SwitchChargeItemAccountCommand cmd)` → `void`

**命令文件**：`SwitchChargeItemAccountCommand.java`

**字段全集**：`chargeItemIds`,`fromAccountId`,`toAccountId`,`operatorId`,`reason`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertAccountSwitchAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemRepository.findSwitchableByIds` |
| `eventPublishAnchor` | `new ChargeItemAccountSwitchedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-008` |

**方法契约摘要**：前置条件为仅未收费态可切换且目标账户有效；持久化影响为批量更新 `accountId`；失败语义为状态越权或账户非法拒绝；事件触发发布 `ChargeItemAccountSwitchedEvent`。**注释锚点**：前态校验 -> 账户更新 -> 持久化 -> 事件。

---

#### 5.1.9 `preSettleChargeItems(BatchChargeItemIdsCommand cmd)` → `void`

**命令文件**：`BatchChargeItemIdsCommand.java`

**字段全集**：`chargeItemIds`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertPreSettleAllowed` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemRepository.findAllByIdIn` |
| `eventPublishAnchor` | `new ChargeItemPreSettledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-009` |

**方法契约摘要**：前置条件为目标明细均处于 `PLANNED`；持久化影响为不改主状态，仅校验并发布预结算事件；失败语义为含非 `PLANNED` 行时拒绝；事件触发发布 `ChargeItemPreSettledEvent`。**注释锚点**：状态校验 -> 持久化确认 -> 事件发布。

---

#### 5.1.9b `preSettleInpatientChargeItems(BatchChargeItemIdsCommand cmd)` → `void`

**命令文件**：`BatchChargeItemIdsCommand.java`

**字段全集**：`chargeItemIds`、`operatorId`（可选）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 逐行 `status_enum = PENDING_SETTLEMENT` |
| `statusTransitionMethod` | `N/A` |
| `repositoryChecks` | `ChargeItemRepository.findAllByIdIn` |
| `eventPublishAnchor` | `new ChargeItemPreSettledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-009B` |

**方法契约摘要**：住院预结算（**AS-IP-FIN-003** IF-IP-STL-05）；状态保持 `PENDING_SETTLEMENT`。**注释锚点**：状态校验 -> saveAll -> 事件。

---

#### 5.1.10 `requestOutpatientBillRefund(RequestOutpatientBillRefundCommand cmd)` → `List<Long>`

**命令文件**：`RequestOutpatientBillRefundCommand.java`

**字段全集**：`billNo`,`chargeItemIds`,`refundAmount`,`operatorId`,`reason`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertOutpatientRefundRequestAllowed` |
| `statusTransitionMethod` | `ChargeItem.transitSettledToRefunding` |
| `repositoryChecks` | `ChargeItemRepository.findSettledByIds` |
| `eventPublishAnchor` | `new OutpatientBillRefundRequestedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-010` |

**方法契约摘要**：前置条件为账单内目标明细已收费且可退费；持久化影响为批量推进 `REFUNDING`；与 **DM-FIN-002** **BR-PMR-017** / **BR-CHG-015** 协同（半退时未选退行后续重开为 `PLANNED`）；失败语义为重复申请按幂等或拒绝策略处理；事件触发发布 `OutpatientBillRefundRequestedEvent`。**注释锚点**：资格校验 -> 状态推进 -> 持久化 -> 事件。

---

#### 5.1.11 `completeOutpatientBillRefund(BatchChargeItemIdsCommand cmd)` → `List<Long>`

**命令文件**：`BatchChargeItemIdsCommand.java`

**字段全集**：`chargeItemIds`、`operatorId`（退费操作人）、`billDate`（可选）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertOutpatientRefundCompleteAllowed` |
| `statusTransitionMethod` | `ChargeItem.transitRefundingToRefunded` |
| `repositoryChecks` | `ChargeItemRepository.findRefundingByIds` |
| `eventPublishAnchor` | `new OutpatientBillRefundCompletedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-011` |

**方法契约摘要**：前置条件为目标处于 `REFUNDING`；持久化影响为收口到 `REFUNDED`、记录退费链路，并将 `enterer_id`/`entered_date` 写为 `operatorId`/`billDate`（**BR-OPC-016**）；半退重开行见 **BR-CHG-015**；失败语义为前态不符拒绝；事件触发发布 `OutpatientBillRefundCompletedEvent`。**注释锚点**：状态校验 -> 结果收口与收费人落库 -> 持久化 -> 事件。

---

#### 5.1.12 `settleInpatientChargeItems(BatchChargeItemIdsCommand cmd)` → `void`

**命令文件**：`BatchChargeItemIdsCommand.java`

**字段全集**：`chargeItemIds`、`operatorId`（收费人，必填）、`billDate`（与 payment `bill_date` 同值）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 逐行 `status_enum = PENDING_SETTLEMENT` |
| `statusTransitionMethod` | `ChargeItem.transitPendingSettlementToSettled`（或等价 batch 推进） |
| `repositoryChecks` | `ChargeItemRepository.findAllByIdIn` |
| `eventPublishAnchor` | `new InpatientChargeItemsSettledEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-012` |

**方法契约摘要**：住院收费结算（**AS-IP-FIN-003** IF-IP-STL-06 Cmd ②）；`PENDING_SETTLEMENT→SETTLED`；并将 `enterer_id`/`entered_date` 写为 `operatorId`/`billDate`（收费人/收费时间，**BR-OPC-016**）；**不再**写 `performer_id` 为收款人；与门诊 `settleChargeItems` 命令形态一致、门禁分离。**注释锚点**：校验 -> 结算推进与收费人落库 -> saveAll -> 事件。

---

#### 5.1.12a `revertInpatientSettledChargeItems(BatchChargeItemIdsCommand cmd)` → `void`

**命令文件**：`BatchChargeItemIdsCommand.java`（与 5.1.12 同形）

**字段全集**：`chargeItemIds`、`operatorId`（可选）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | 逐行 `status_enum = SETTLED` |
| `statusTransitionMethod` | 源行不变；批量 `INSERT` 新行 `PENDING_SETTLEMENT` + `refund_id`；组套子行 `parent_id` 重链 |
| `repositoryChecks` | `ChargeItemRepository.findAllByIdIn` |
| `eventPublishAnchor` | `new InpatientChargeItemsRevertedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-012a` |

**方法契约摘要**：住院反结再生（**AS-IP-FIN-004** IF-IP-RVS-01 / **ATD-07 `RecallReverseSettlesupport`**）；源 `SETTLED` 保持终态，插入 `PENDING_SETTLEMENT` 新行 + `refund_id`（**BR-IP-RVS-011**）；多轮结算/反结可叠多层 `refund_id` 链，读侧/Gate 递归追溯见 **`ChargeItemRegenerationChainSupport`**；支付/就诊/发票由应用层编排。**注释锚点**：SETTLED 门禁 -> 复制落库 -> saveAll -> 事件。

---

#### 5.1.13 `requestInpatientBillRefund(RequestInpatientBillRefundCommand cmd)` → `List<Long>`

**命令文件**：`RequestInpatientBillRefundCommand.java`

**字段全集**：`inpatientBillNo`,`chargeItemIds`,`refundAmount`,`operatorId`,`reason`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertInpatientRefundRequestAllowed` |
| `statusTransitionMethod` | `ChargeItem.transitSettledToRefunding` |
| `repositoryChecks` | `ChargeItemRepository.findSettledByIds` |
| `eventPublishAnchor` | `new InpatientBillRefundRequestedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-013` |

**方法契约摘要**：前置条件为住院账单目标明细已收费且满足退费金额约束；持久化影响为推进 `REFUNDING`；失败语义为状态或金额不满足拒绝；事件触发发布 `InpatientBillRefundRequestedEvent`。**注释锚点**：资格校验 -> 状态推进 -> 持久化 -> 事件。

---

#### 5.1.14 `completeInpatientBillRefund(BatchChargeItemIdsCommand cmd)` → `List<Long>`

**命令文件**：`BatchChargeItemIdsCommand.java`

**字段全集**：`chargeItemIds`、`operatorId`（退费操作人）、`billDate`（可选）

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `ChargeItem.assertInpatientRefundCompleteAllowed` |
| `statusTransitionMethod` | `ChargeItem.transitRefundingToRefunded` |
| `repositoryChecks` | `ChargeItemRepository.findRefundingByIds` |
| `eventPublishAnchor` | `new InpatientBillRefundCompletedEvent(...); DomainEventPublisher.publish(event)` |
| `unitTestCaseId` | `TC-CHG-W-014` |

**方法契约摘要**：前置条件为目标处于 `REFUNDING`；持久化影响为收口至 `REFUNDED`（特定策略可回退），并将 `enterer_id`/`entered_date` 写为 `operatorId`/`billDate`（**BR-OPC-016**）；失败语义为前态不符或链路不完整拒绝；事件触发发布 `InpatientBillRefundCompletedEvent`。**注释锚点**：状态校验 -> 收口与收费人落库 -> 持久化 -> 事件。

### 5.2 读侧 — `ChargeItemQueryService`（`domain.queries`）

所有查询默认 `isDeleted=false`；分页在数据库侧过滤与统计 total。

**待结算金额聚合（与实现对齐）**：`sumPendingSettlementTotalPriceByEncounterId` / `mapPendingSettlementTotalPriceByEncounterIds` **不**经 `ChargeItemRepository` 派生 `sum…`（Spring Data 无法可靠解析 `sumTotalPriceBy…`）；由 `ChargeItemQueryService` 注入既有 **`EntityManager`**，Criteria 下推 **`SELECT encounter_id, SUM(total_price) … GROUP BY encounter_id`**（`status_enum=PENDING_SETTLEMENT` + `encounter_id IN (…)` + **`parent_id IS NULL`**，院内合计只计父行，§9.1）；单就诊复用同一私有分组查询后取 Map 值，无行返回 **0**。**禁止** `findAllByEncounterIdInAndStatusEnumIn` 拉全表明细后内存累加（住院队列批量场景）。

**出参**填 QueryModel 类名（字段定义见 §4.6 对应子节，此处不重复）。

| 方法 | 入参 | 出参（类名，字段见 §4.6） | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ------------------------- | -------- | ---------------- |
| `loadChargeItemsByIds` | `List<Long> chargeItemIds` | `List<ChargeItemByIdQueryModel>` | 明细主键批量读取；**CHS**：`zxplat-module-chs-jilin` bridge `*InsuranceContextLoader` 2204 源数据 | `TC-CHG-R-001` |
| `listChargeItemsByServiceIds` | `RequestChargeItemsQuery` | `List<ChargeItemByRequestQueryModel>` | 按 `service_table`+`service_id` 追溯明细（实现名；DM 历史别名 `listChargeItemsByRequestIds`） | `TC-CHG-R-002` |
| `listChargeItemsByEncounterId` | `Long encounterId` | `List<ChargeItemEncounterListQueryModel>` | 就诊维度明细列表 | `TC-CHG-R-003` |
| `queryCostDetails` / `queryInpatientCostDetails` | `CostDetailsQuery` / `InpatientCostDetailsQuery` | `List<CostDetailsQueryModel>` / `List<InpatientCostDetailsQueryModel>` | 门诊/住院费用明细视图；`excludeSupersededSettledLines=true` 时递归排除反结链历史 `SETTLED`（**BR-REF-020**） | `TC-CHG-R-004` |
| `queryOxygenAndECGDuration` | `ChargeItemQueryCommand`（`encounterId`、`activityDefinitionId` 必填；`occurrenceDate` 可选） | `Long` | **待实现**（2026-05-28：域 QS 尚无同名方法；**BR-IP-PRC-010** Gate 待接通） | `TC-CHG-R-005` |
| `listChargeItemDefInfoWithChildren` | `ChargeItemDefInfoQuery` | `List<ChargeItemDefInfoWithChildrenQueryModel>` | 明细与定价及组套子行关联 | `TC-CHG-R-006` |
| `listChargeItemsByPaymentBill` | `String paymentBillNo` | `List<ChargeItemsByPaymentBillQueryModel>` | 按结算单读取明细 | `TC-CHG-R-007` |
| `listChargeItemsForInvoice` | `InvoiceChargeItemsQuery` | `List<ChargeItemsForInvoiceQueryModel>` | 开票场景明细读取 | `TC-CHG-R-008` |
| `listChargeItemsByInpatientBill` | `String inpatientBillNo` | `List<ChargeItemsByInpatientBillQueryModel>` | 住院结算单明细读取 | `TC-CHG-R-009` |
| `pageEncounterIdsForOutpatientCashierQueue` | `OutpatientChargeQueueEncounterQuery` | `Page<Long>` | 委托 **`OutpatientChargeEncounterQueueLoader`**；域锚点 `pageEncounterIdsForOutpatientChargeQueue` + **`resolvedEncounterScope`** | `TC-CHG-R-010` |
| `listOutpatientCashierChargeItemsForEncounters` | `Collection<Long> encounterIds`, `String excludedServiceTable` | `List<ChargeItem>` | 队列行批量挂载明细 | `TC-CHG-R-011` |
| `pageEncounterIdsForOutpatientRefundQueue` | `OutpatientChargeQueueEncounterQuery` | `Page<Long>` | 同收银队列 Loader + **`resolvedEncounterScope`**；状态白名单按页签（AS-OP-FIN-003） | `TC-CHG-R-012` |
| `sumPendingSettlementTotalPriceByEncounterId` | `Long encounterId` | `BigDecimal` | Criteria **`SUM(total_price)`** + `encounter_id` + `PENDING_SETTLEMENT` + **`parent_id IS NULL`**；无行返回 0 | `TC-CHG-R-013` |
| `mapPendingSettlementTotalPriceByEncounterIds` | `Collection<Long> encounterIds` | `Map<Long, BigDecimal>` | 同上 **GROUP BY encounter_id**；一次 IN 批量（住院展示余额，防 N+1） | `TC-CHG-R-014` |
| `listEncounterIdsWithPendingSettlementCharges` | — | `List<Long>` | `DISTINCT encounter_id`，`status_enum=PENDING_SETTLEMENT`（**Q-IPI-007** / IF-02） | `TC-CHG-R-015` |
| `pageEncounterIdsForOutpatientChargeQueue` | `OutpatientChargeQueueEncounterQuery` | `Page<Long>` | 单表 `adm_charge_item` + `encounter_id IN (resolvedEncounterScope)`；Loader 预解析机构/关键字 | `TC-CHG-R-016` |
| `filterOutSupersededSettledLines` | `encounterId`, `List<ChargeItem>` | `List<ChargeItem>` | **BR-REF-020** / **BR-IP-RVS-011** 读侧过滤；委托 `ChargeItemRegenerationChainSupport` 递归链 | `TC-CHG-R-017` |
| `mapPreservedNewChargeItemIdBySourceId` | `encounterId`, `sourceChargeItemIds` | `Map<Long,Long>` | 祖先 `SETTLED`→链末端再生叶；多轮反结多跳 | `TC-CHG-R-018` |
| `resolveChargeItemIdsForOutpatientRefundBillCompletion` | 见命令对象 | `List<Long>` | IF-REF-05/06 拟结集合 | `TC-CHG-R-019` |
| `outpatientCashierBillableStatusCodes` / `outpatientRefundQueueStatusCodes` | — | `List<String>` | 队列 SQL 白名单 | `TC-CHG-R-020` |
| `listChargeItemsForMedicationDispensePricing` | `encounterId`, `dispenseIds` | `List<ChargeItem>` | 发药划价读侧 | `TC-CHG-R-021` |
| `listRefundRelatedChargeItemsByEncounterIds` | `encounterIds` | `List<ChargeItem>` | 退费队列批量挂载 | `TC-CHG-R-022` |
| `summarizePractitionerWorkloadByFinType` | `PractitionerWorkloadPageQuery` | `List<PractitionerWorkloadFinTypeOrgSummaryQueryModel>` | 域内按 org+definition 聚合；应用层未选科室时 **按 fin_type 跨 org 合并**（**AS-RPT-001** **BR-RPT-WKL-016**） | `TC-CHG-R-023` |
| `summarizeRegistrationChargesByServiceType` | `PractitionerWorkloadPageQuery` | `List<PractitionerWorkloadRegistrationOrgSummaryQueryModel>` | 域内按 org+serviceType **净人次**（一就诊一次，退号不计入；对齐 **AS-RPT-007**）；应用层未选科室时 **跨 org 合并**（**AS-RPT-001** **BR-RPT-WKL-009**） | `TC-CHG-R-024` |
| `pagePractitionerWorkloadDetailByRequest` | `PractitionerWorkloadPageQuery` | `Page<PractitionerWorkloadRequestDetailQueryModel>` | 按 request 键聚合分页（**AS-RPT-001** **BR-RPT-WKL-017**） | `TC-CHG-R-025` |
| `listPractitionerWorkloadScopeChargeRows` | `PractitionerWorkloadPageQuery`, `registrationOnlyMode` | `List<PractitionerWorkloadChargeRowQueryModel>` | payment scope 内候选行；fin_type/成本/overview 统一源（**BR-RPT-WKL-018**） | `TC-CHG-R-026` |
| `summarizePractitionerWorkloadOverview` | `List<PractitionerWorkloadChargeRowQueryModel>` | `PractitionerWorkloadOverviewSummaryQueryModel` | distinct encounter；处方按 `prescription_no` 去重；诊疗+耗材 request 计数（**BR-RPT-WKL-019**） | `TC-CHG-R-027` |

> **工作量统计**：入参 `PractitionerWorkloadPageQuery` 定义于 `chargeitem.query`；scope 预解析见 `PractitionerWorkloadQuerysupport；对齐 **DM-ADM-011** §6.2.27～6.2.31、**DM-FIN-002** §6.2.19、**DM-ADM-002** §6.2.30～6.2.31 与 **AS-RPT-001**。

**读方法注释锚点（统一）**：构建查询条件（含软删过滤）-> 单次 SQL/仓储调用并完成分页统计 -> 组装 QueryModel（无写库）。

**可选 `service_table` 排除（与实现对齐）**：`ChargeItemQueryCommand.excludedServiceTable` 非空且 trim 后非空时，`listChargeItemsByEncounterId` 与 `queryCostDetails`（就诊维度分支）通过 `ChargeItemRepository` 的 `…AndServiceTableNot` 派生方法在 SQL 层附加不等条件；门诊收银场景排除值对齐 `TableNameConstants.ADM_HEALTHCARE_SERVICE`（AS-OP-FIN-002 **BR-OPC-013**）。

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-CHG-001 | 明细必须具备业务来源锚点和定价锚点 |
| BR-CHG-002 | 状态迁移必须符合生命周期，终态不可逆回未收费 |
| BR-CHG-003 | 同语义重复提交幂等处理 |
| BR-CHG-004 | 账户切换仅允许未收费态 |
| BR-CHG-005 | 退费链路必须保留 `refundId` 可追溯 |
| BR-CHG-006 | 业务撤销优先状态表达，避免直接物理删 |
| BR-CHG-007 | 写前先查再改，避免盲写 |
| BR-CHG-008 | 金额公式必须成立；医嘱开立/护士执行路径经 **`ProductDefinitionOrderChargePricingsupport**：**定价 `price_value` 为每包装单位价**，仅以包装单位为锚做**包装→开立单位**槽价换算（**DM-ADM-010 §1.6**；见 **AS-OP-DOR-003 BR-ORD-016**、**AS-IP-NUR-004 BR-IP-EXE-026**） |
| BR-CHG-016 | 暂存/划价草稿 **`ChargeItemDefinition`** 择价在域写前由应用 **`resolveDraftChargePriceLine`** 完成；`clientResolvedDispenseLocationId` 非空时不二次 match 模板；库位不落库 `adm_charge_item` |
| BR-CHG-009 | 组套父子行金额口径一致 |
| BR-CHG-010 | 必须关联有效就诊 |
| BR-CHG-011 | 退费金额不超过原收费 |
| BR-CHG-012 | 同时刻单行状态互斥 |
| BR-CHG-013 | `REFUNDING` 必须被后续动作收口 |
| BR-CHG-014 | 金额展示口径优先级一致 |
| BR-CHG-015 | 半退未选退行以新 `PLANNED` 承接，`refund_id` 链回；**凡重生成的 ChargeItem 及连带请求/发放等须与源原单业务事实一致（复刻）**（与 DM §7 一致） |
| BR-CHG-QRY-001 | 写前校验需先读当前状态与主档 |
| BR-CHG-QRY-002 | 列表/报表优先QueryService |
| BR-CHG-QRY-003 | 查询金额口径需显式声明并统一 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷（字段） | 触发方法 |
| -------- | ------------ | ---------------- | -------- |
| `RegistrationChargeItemCreatedEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemId,patientId,encounterId,definitionId,amount` | `createRegistrationChargeItem` |
| `OrderDraftChargeItemCreatedEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemId,patientId,serviceTable,serviceId` | `createDraftChargeItemFromOrder` |
| `NursePricingChargeItemCreatedEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemId,patientId,definitionId,amount` | `priceByNurseStation` |
| `NursePricingChargeItemOffsetEvent` | `com.zxplat.domain.share.events.administration.chargeitem.ChargeItemDomainEvents` | `offsetChargeItemId,sourceChargeItemId,encounterId` | `offsetNursePricingChargeItemsByServiceAnchors` |
| `NursePricingChargeItemRemovedEvent` | 同上（历史 deprecated） | `chargeItemId,encounterId` | 旧 `voidNursePricing…` 物理删 |
| `ChargeItemActivatedByExecutionEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemId,statusEnum,occurrenceTime` | `activateChargeItemAfterExecution`；**委托** `planDraftChargeItemsAfterAdviceSigned` |
| `ChargeItemExecutionCancelledEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemId,rollbackStatus` | `cancelExecutionRollbackChargeItem`；**委托** `rollbackPlannedChargeItemsAfterAdviceSignedOff` |
| `ChargeItemSettledEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemIds,settledAt,settlerId` | `settleChargeItems` |
| `RegistrationChargeItemRefundedEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemIds,refundId,refundAmount` | `returnRegistrationRefundChargeItems` |
| `ChargeItemAccountSwitchedEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemIds,fromAccountId,toAccountId` | `switchChargeItemAccount` |
| `ChargeItemPreSettledEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemIds,preSettleAmount` | `preSettleChargeItems` / **`preSettleInpatientChargeItems`** |
| `OutpatientBillRefundRequestedEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemIds,refundAmount,billNo` | `requestOutpatientBillRefund` |
| `OutpatientBillRefundCompletedEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemIds,refundResult,refundId` | `completeOutpatientBillRefund` |
| `InpatientChargeItemsSettledEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemIds,batchNo,settledAt` | `settleInpatientChargeItems` |
| `InpatientBillRefundRequestedEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemIds,refundAmount,inpatientBillNo` | `requestInpatientBillRefund` |
| `InpatientBillRefundCompletedEvent` | `com.zxplat.domain.share.events.financial.chargeitem` | `chargeItemIds,refundResult,refundId` | `completeInpatientBillRefund` |

**并列投影事件**：同写路径可并列发布 `ChargeItemCreatedEvent`、`ChargeItemUpdatedEvent`、`ChargeItemDeletedEvent` 供领域事件后查询刷新，业务语义事件仍以上表为准。

**发布时机**：全部 **beforeCommit**；载荷为最小业务键 + 关键快照。

---

## 8. 删除与可见性契约

- 优先状态表达撤销与退费，避免物理删除破坏审计。
- 组套父子行需联动收口，不支持孤立子行越权退费。
- 终态（`REFUNDED`）后禁止逆向回退未收费态。
- 默认查询过滤软删行，历史审计查询需显式放开。
- 开票与退费读取口径分离，均需二次状态校验。

---

## 9. 框架优先适配规则

- 软删、审计字段、事务工作单元、beforeCommit 事件发布遵循框架默认能力。
- 领域服务与查询服务采用直接实现类，不默认接口+Impl 双层。
- 一方法一 Command，禁止命令容器类。
- 列表/报表查询优先QueryService，写模型只承载事实与写前校验查询。
- 跨域支付/医保/票据流程由应用层编排。

---

## 10. 生成与验收清单

- [ ] §4 字段表与 DM §3 对齐（单表字段完整）
- [ ] §5.1 写方法小节数量 = 14，与 DM §6.1 一致
- [ ] 每个写方法均包含命令文件、字段全集、绑定项、方法契约摘要
- [ ] §5.2 读方法覆盖 DM §6.2 全部 13 个方法（含 §6.2.10～§6.2.12 门诊收银/退费队列）
- [ ] §6 BR 映射覆盖 BR-CHG-* 与 BR-CHG-QRY-* 规则
- [ ] §7 事件表覆盖 DM §5 全部 14 条业务事件
- [ ] 组套多行与退费收口规则一致
- [ ] 全文无占位语、无待确认语

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM：`DM-ADM-011-ChargeItem-收费明细.md`
- 本 SDD 文件（通过结构/字段/方法/事件门禁）
- 目标模块：`zxplat/zxplat-domain`
- 共享事件包可用：`com.zxplat.domain.share.events.financial.chargeitem`

### 11.2 DoD

- 写侧 14 个方法全部落地，签名与 Command 命名与 §5 一致。
- 收费状态机、金额字段、组套层级、多域锚点与 §4/§6 一致。
- 事件按 §7 beforeCommit 发布，载荷字段不少于最小要求。
- BR 至少一条绑定一个测试用例 ID，可追溯到 `unitTestCaseId`。
- 读侧 10 个方法与 §4.6 QueryModel 对齐，读写口径一致。

### 11.3 失败处理

- 任一方法契约无法实现时立即停机并回文档会话修订。
- 若 DM 白名单方法/事件/BR 变更，先更新 SDD 再进入代码会话。
- 若退费收口规则与状态机冲突未解，DoD 失败不得进入成码。

---

## 定稿检查清单（可打勾）

- [ ] 主章节结构与金骨架一致（输入检查、1-11、定稿检查、评分、结束语）
- [ ] §5.1 子节数量 = DM 写方法数量（**15**，含 §5.1.9b 住院预结算）
- [ ] §4 字段表覆盖 DM §3 字段，不截断
- [ ] §7 事件表覆盖 DM §5 全部业务事件（14）
- [ ] 每个写方法包含 `entityInvariantMethod` 与 `unitTestCaseId`
- [ ] 全文无 YAML、无 `sdd_status`、无 `dm_code`、无占位措辞

---

## 定稿评分（百分制）

| 维度 | 分值 | 自评 |
| ---- | ---- | ---- |
| 输入完整性 | 20 | 20 |
| 方法白名单对齐 | 25 | 25 |
| 不变式映射 | 20 | 20 |
| 事件契约 | 15 | 15 |
| 软删与框架一致 | 10 | 10 |
| 去重与无歧义 | 10 | 10 |
| **总分** | **100** | **100（可进入代码会话）** |

---

## 12. 文档修订记录（增量）

| 日期 | 摘要 |
| --- | --- |
| 2026-10-16 | §5.1.5a 执行族 void（含 `anesthesia-execute`）；对齐 **DM v1.6.27** / **AS-IP-NUR-012 BR-IP-CXL-008** |
| 2026-10-13 | §5.2 待结算聚合 **`parent_id IS NULL`**；对齐 **DM v1.6.25**（院内展示余额不双计子项账单） |
| 2026-09-06 | §3.1/`prescriptionNo` 结算快照语义；对齐 **DM v1.6.19** / **AS-OP-FIN-002 · BR-OPC-026** |
| 2026-08-04 | **BR-OPC-016**：实体 `enteredDate`=收费时间；§5.1.7/11/12/14 退费完成与住院结算写 `enterer_*`；命令形态对齐 `BatchChargeItemIdsCommand`；对齐 **DM v1.6.14** |
| 2026-08-03 | §5.2 工作量挂号净人次（一就诊一次）与处方 `prescription_no` 去重；对齐 **DM v1.6.12** / **AS-RPT-001 v1.6** |
| 2026-07-18 | **DD §11.7**：§4.2 诊断字段改为 **`encounterDiagnosisIds`**（废止 `conditionId`/`encounterDiagnosisId`） |
| 2026-07-11 | §5.2 `queryInpatientCostDetails` 对齐 `queryCostDetails`：`excludeSupersededSettledLines` 递归过滤；对齐 **DM v1.6.9** |
| 2026-07-11 | **成码回更**：新增 §5.0 **`ChargeItemRegenerationChainSupport`**；**BR-REF-020** / **BR-IP-RVS-011** 再生链改为 `refund_id` **递归**多跳（多轮结算/反结）；§5.2 `filterOutSupersededSettledLines` / `mapPreservedNewChargeItemIdBySourceId` 口径同步；对齐 **DM v1.6.8** |
| 2026-07-11 | **BR-IP-RVS-011**：§5.1.12a 反结复制再生契约；§5.2 `listChargeItemsByServiceIds` / `filterOutSupersededSettledLinesGroupedByEncounter` 读侧去重；对齐 **DM v1.6.7** |
| 2026-07-11 | **CHS bridge**：§5.2 `loadChargeItemsByIds` 消费注记；§5.3 `ProductDefinitionQueryService.batchLoadAsMap`（2204）；对齐 **DM v1.6.6** §6.2.1 / §9.4 |
| 2026-07-06 | **AS-RPT-001 v1.4**：§5.2 增补 `listPractitionerWorkloadScopeChargeRows` / `summarizePractitionerWorkloadOverview`；对齐 **DM v1.6.5** §6.2.30～31 |
| 2026-07-03 | **AS-RPT-001 v1.3**：§5.2 工作量读侧注明未选科室时应用层跨 org 合并（**禁止** departmentBlocks） |
| 2026-07-03 | **工作量读扩展**：§5.2 增补 `summarizePractitionerWorkloadByFinType` / `summarizeRegistrationChargesByServiceType` / `pagePractitionerWorkloadDetailByRequest`；对齐 **DM v1.6.2** §6.2.27～6.2.29 与 **AS-RPT-001** |
| 2026-06-20 | §5.1.4e `prescriptionNo`；§5.1.5a **`voidOrderExecuteChargeItemsByProcedureIds`**；§5.1.5b **`voidPendingOrderExecuteChargeItemsByChargeItemIds`**（**BR-IP-TCM-008**）；对齐 **DM v1.5.7** |
| 2026-10-17 | **代码先行回写**：§5.1.3a 冲销源判定改 `isPricingOffsetLine()`（区分冲销负向 vs 反结再生正向 `refund_id`）；对齐 **DM v1.6.28** |
| 2026-07-17 | §5.1.3a 改为 **`offsetNursePricingChargeItemsByServiceAnchors`** + **`NursePricingChargeItemOffsetEvent`**；对齐 **DM v1.5.6** / **AS-IP-NUR-005 v2.8**（取消划价冲销留痕） |
| 2026-06-18 | §5.1.3a **`voidNursePricingChargeItemsByServiceAnchors`** + **`NursePricingChargeItemRemovedEvent`**；对齐 **DM v1.5.5**（**AS-IP-NUR-005** IF-IP-PRC-06；关闭 **GAP-IP-PRC-008/010**） |
| 2026-06-11 | **BR-CHG-016**：§5.1.2 应用 Pre 择价；§6 不变式映射；对齐 **DM v1.5.4** |
| 2026-05-28 | **DM-SDD M1**：§5.1.2a/b 作废草稿；§5.1.4f～i 发放锚点批量写+半退落库；§5.2 补半退/队列读法；对齐 **DM v1.5.1** |
| 2026-05-28 | **DM-SDD M1**：§5.2 `listChargeItemsByServiceIds`（替代 `listChargeItemsByRequestIds`）；`queryOxygenAndECGDuration` 标待实现 |
| 2026-05-29 | **定价包装锚（DM-ADM-010 §1.6）**：**BR-CHG-008** 明确仅以包装价为锚做包装→开立单位换算 |
| 2026-05-28 | **BR-CHG-008**：§6 不变式映射增补 **`ProductDefinitionOrderChargePricingsupport** 医嘱开立/护士执行路径；对齐 **AS-OP-DOR-003 BR-ORD-016**、**AS-IP-NUR-004 BR-IP-EXE-026** |
| 2026-05-28 | **ReadModel 废止**（DM **v1.4.9**）：废止 **OutpatientInfo** RM；门诊收银/退费队列 EXISTS 直连 `adm_encounter`，展示经 **EncounterContextFacade** |（已迁至 EncounterContextReader / application.shared.encounter）
| 2026-05-26 | **AS-IP-FIN-003** 对齐：§5.1.9b `preSettleInpatientChargeItems`；§5.1.12 改为 `BatchChargeItemIdsCommand` + `PENDING_SETTLEMENT→SETTLED`；§5.2 增补 `listEncounterIdsWithPendingSettlementCharges` |
| 2026-05-24 | 待结算汇总改 **Criteria `SUM`+`GROUP BY`**（`ChargeItemQueryService#sumTotalPriceGroupedByEncounterIdAndStatusEnum`）；废止仓储 `sum…` 派生与 `findAllByEncounterIdInAndStatusEnumIn` 内存累加路径 |
| 2026-05-24 | **`PENDING_SETTLEMENT`**：`priceByNurseStation` / `activateChargeByExe` / `createPlannedChargeItemFromOrderExecution`；**`planDraftChargeItemsAfterAdviceSigned`** 仍 **`DRAFT→PLANNED`（临时签发）**；**`activateChargeByExe`** 接受 **`PLANNED/DRAFT→PENDING_SETTLEMENT`（临时执行）**；读侧新增 **`sumPendingSettlementTotalPriceByEncounterId`** / **`mapPendingSettlementTotalPriceByEncounterIds`**（展示余额） |
| 2026-05-09 | 对齐 **AS-OP-DOR-003-SDD v2.9** / **AS v1.18**：增补 **§5.1.4c** `planDraftChargeItemsAfterAdviceSignedForAnchors`、**§5.1.4d** `rollbackPlannedChargeItemsAfterAdviceSignedOffForAnchors`（门诊医生站药耗**多发放锚点**批量 `DRAFT`↔`PLANNED`）；与 `ChargeItemDomainService` 工程实现一致。 |

---

**本 SDD 正文结束。**

