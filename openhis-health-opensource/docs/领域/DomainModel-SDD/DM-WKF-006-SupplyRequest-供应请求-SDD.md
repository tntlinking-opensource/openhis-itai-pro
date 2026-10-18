# DM-WKF-006 SupplyRequest 供应请求 · DomainModel-SDD

> **对应 DM**：`docs/领域/DomainModel/DM-WKF-006-SupplyRequest-供应请求.md`（本 SDD 假定该 DM 为当期锁定版本）  
> **目标工程**：`zxplat-core/zxplat-domain`  
> **限界上下文**：workflow（WKF）  
> **聚合**：SupplyRequest（子实体见 §4）

---

## 输入完整性检查结果

| 最小输入项 | 状态 | 说明 |
| ---------- | ---- | ---- |
| DM 路径 | 满足 | DM-WKF-006 |
| SDD 输出路径 | 满足 | 本文件 |
| 目标模块 | 满足 | `zxplat/zxplat-domain` |
| 包规则 `com.zxplat.domain.workflow.supplyrequest` | 满足 | 全小写 |
| 固定技术口径 | 满足 | 见 §9 |
| DM 第 6 章写/读白名单 | 满足 | §5 覆盖 |
| DM 第 7 章 BR | 满足 | §6 映射 |
| DM 第 5 章事件 | 满足 | §7 覆盖 |

**与 DM 显式差异（采纳依据：DM 优先 + 领域落地契约细化）**

| 点 | DM 差异位置 | SDD 采纳 |
| -- | ----------- | -------- |
| 时间类型统一 | §3.1（Date 文本） | 成码口径统一为 `LocalDateTime` |
| 事件包路径 | §5（仅事件名） | 固定到 `com.zxplat.domain.share.events.workflow.supplyrequest` |
| 两阶段编排 | §8.1/§8.2 申请/发放单 | 明确申请单 SEND 后由应用编排生成发放单，不允许申请单 SEND 直接触发扣减 |
| 枚举持久化 | DM v1.1 §3.1 / §4 | `typeEnum`/`statusEnum`/`categoryEnum`/`intentEnum`/`priorityEnum`/`sourceTypeEnum`/`purposeTypeEnum` 均为 **`String`**，与各 **`com.zxplat.domain.share.enums.*#getCode()`** 一致（**B.10.8.1**）；`sourceLocationId`/`sourceTypeEnum` 可空与实体一致 |
| HTTP→命令装配（进货/出库/药房入库/药库订货） | **AS-INV-WHS-002/003、AS-INV-PHA-001、AS-INV-WHS-001** 之 Mapper | 上述四类 **REST 请求体不要求**传 `sourceTypeEnum`/`purposeTypeEnum`；由 **`LocationQueryService#requireSupplyRequestTypeForLocationId(Long)`** 读取 **`adm_location.form_enum`** 映射为 **`SupplyRequestType#getCode()`** 后写入 **`Create*Command`**，与 **B.10.8.1** 一致 |
| 药库订货（type 1） | **AS-INV-WHS-001** · **§6.1.1** | **`CreatePurchaseOrderCommand.expectedRowVersionForRevision`** 修订必填；**`assertPurchaseOrderLinesRevisable`** + 软删重插；**`agreeRequest` 不**调用 **`createInboundReceipt`** |
| 药库退货（type 3） | **AS-INV-WHS-005** · **§6.1.3** | **`CreateReturnToSupplierCommand`** 含 `practitionerId`（可选）、**`invoiceNo`（可选）**、**`price`/`totalPrice`（写价门禁写入）**；`supplierId` **可选**；`originalBusNo` 领域强制非空；修订无 rowVersion；落库前 **`applyReturnToSupplierCommand`**（**BR-SR-019**）；3504 `purc_invo_no`←本行/`进货`/`0` |

---

## 1. 设计目标与边界

### 1.1 设计目标

- 固化供应请求 28 类单据白名单与审批状态机，支持申请单/执行单据两阶段编排。
- 明确 `bus_no` 多行明细、`original_bus_no` 溯源语义与 `type_enum` 不混单约束。
- 统一 AGREE 后跨域编排契约：申请单生成执行单，执行单驱动发放与库存。

### 1.2 边界

- **在内**：建单、提交审批、撤回、同意、驳回、逻辑删除、汇总取消。
- **在外**：SupplyDelivery 创建、InventoryItem 变更、MedicationDispense/DeviceDispense 落库。
- **跨域仅端口声明**：通过事件与 ID/`bus_no` 协作。

---

## 2. 工程落点与包结构

### 2.1 Maven 模块

- `zxplat/zxplat-domain`

### 2.2 包结构（`com.zxplat.domain.workflow.supplyrequest`）

| 相对包 | 内容 |
| ------ | ---- |
| `entity` | `SupplyRequest` |
| `command` | 每写方法一个命令 |
| `repository` | `SupplyRequestRepository` |
| `service` | `*DomainService` 与 `*QueryService`（两个方法集，直接实现类） |
| `queries` | 查询入参对象（如 `*Query`） |
| `queries.model` | 查询出参模型（如 `*QueryModel`） |
| `share.events` | `com.zxplat.domain.share.events.workflow.supplyrequest` |

---

## 3. 类型 / 字段命名 / 注解规则

| 规则 | 说明 |
| ---- | ---- |
| 主键/外键 | `Long` |
| 时间 | `LocalDateTime`（`applyTime/approvalTime/occurrenceTime`） |
| `*_enum`（业务枚举域） | `String`（存 **`EnumInterface#getCode()`**，**B.10.8.1**） |
| `*_code` | `String` |
| 软删 | `isDeleted` |
| 状态 | `status_enum` 对齐 SupplyStatus |

---

## 4. 实体 / 值对象 / 枚举 / 仓储 / 查询契约

### 4.1 表与实体映射

| 逻辑表 | 实体类 | 说明 |
| ------ | ------ | ---- |
| `wkf_supply_request` | `SupplyRequest` | 同表多形态，按 `bus_no` 聚合多行 |

### 4.2 聚合根 `SupplyRequest`（字段级契约）

| 字段名（Java） | 类型 | 必填 | 说明 |
| -------------- | ---- | ---- | ---- |
| `id` | `Long` | 系统 | 主键 |
| `busNo` | `String` | 是 | 单据号 |
| `originalBusNo` | `String` | 否 | 来源单号 |
| `typeEnum` | `String` | 是 | **`SupplyType#getCode()`**（如 `CABINET_PURCHASE_ORDER`；药库进货为 **`CABINET_INBOUND_RECEIPT`**） |
| `statusEnum` | `String` | 是 | **`SupplyStatus#getCode()`** |
| `categoryEnum` | `String` | 是 | **`SupplyCategory#getCode()`** |
| `itemTable` | `String` | 是 | 物品来源表 |
| `itemId` | `Long` | 是 | 物品定义 |
| `itemQuantity` | `BigDecimal` | 是 | 默认执行数量（>0）。**盘点类**：盈亏数量（可正可负可 0）。**损益类**：盈亏数量（正报溢、负报损，禁止 0） |
| `totalQuantity` | `BigDecimal` | 否 | **盘点类**：实盘数量（≥0） |
| `unitCode` | `String` | 是 | 单位 |
| `price` | `BigDecimal` | 否 | 单价 |
| `totalPrice` | `BigDecimal` | 否 | 行总价；**盘点类**为实盘金额 |
| `profitLossPrice` | `BigDecimal` | 否 | **盘点类**：盈亏金额（盈正亏负） |
| `sourceTypeEnum` | `String` | 否 | **`SupplyRequestType#getCode()`**；订货等可无源位置 |
| `sourceLocationId` | `Long` | 否 | 来源位置；同上可空 |
| `purposeTypeEnum` | `String` | 是 | **`SupplyRequestType#getCode()`** |
| `purposeLocationId` | `Long` | 是 | 目标位置 |
| `consumingOrganizationId` | `Long` | 否 | **领用科室**（`consuming_organization_id`）；**`DEPT_ISSUE`** 建改单必填；**`DEPT_ISSUE_RETURN`** 自源领用单继承；历史可空 |
| `summaryId` | `Long` | 否 | 汇总编号 |
| `deliverId` | `Long` | 否 | 发放关联 |
| `patientId` | `Long` | 否 | 汇总单患者 |
| `supplierId` | `Long` | 否 | 供应商 |
| `practitionerId` | `Long` | 否 | 经办人（**药库退货**等可选，前端显式选择） |
| `occurrenceTime` | `LocalDateTime` | 否 | 业务发生/制单时间（**药库退货**应用层建单必填） |
| `lotNumber` | `String` | 否 | 批号（退货行键 **`BR-SR-009`** 参与 normalize） |
| `traceNo` | `String` | 否 | 追溯码逗号串（**TRC-WIDE-R-001**，`VARCHAR(50000)`） |
| `invoiceNo` | `String` | 否 | 采购发票号（库列 **`invoice_no`**）；同 `busNo` 多行共享；进销存 3503/3504 **`purc_invo_no`**；DDL 见 **`V6__wkf_ddl.sql`**（默认 **`0`**）；前端不必填 |
| `reason` | `String` | 否 | 原因 |
| `remake` | `String` | 否 | 行备注 |
| `approverId` | `Long` | 否 | 审批人 |
| `approvalTime` | `LocalDateTime` | 否 | 审批时间 |
| `applicantId` | `Long` | 是 | 申请人 |
| `applyTime` | `LocalDateTime` | 是 | 申请时间 |

> **药库位置语义**：见 **DM §3.1.1**（双端点 `source`/`purpose`；单库业务以 `purpose_*` 为主）。

### 4.3 子实体

无。通过同 `bus_no` 多行表达单据明细。

**聚合静态门控（修订路径）**：

| 方法 | 适用 type | 说明 |
| ---- | --------- | ---- |
| `SupplyRequest#isRevisableSupplyStatusCode` | 通用 | 可修订状态：`PENDING_APPROVAL` / `REJECT` / `WITHDRAW`（**BR-SR-003**） |
| `SupplyRequest#assertPurchaseOrderLinesRevisable` | **1** 药库订货 | 须 **`CABINET_PURCHASE_ORDER`** 且各行可修订态（上列三态） |
| `SupplyRequest#assertInboundReceiptLinesRevisable` | 进货 | 同号修订门控（可修订态同上） |
| `SupplyRequest#assertReturnToSupplierLinesRevisable` | **3** 药库退货 | 须 **`CABINET_RETURN_TO_SUPPLIER`** 且各行可修订态 |
| `SupplyRequest#assertStocktakeLinesRevisable` | 盘点 | 同号修订门控（可修订态同上） |
| `SupplyRequest#assertCabinetTransferOutLinesRevisable` | **9** 药库调出 | 须 **`CABINET_TRANSFER_OUT`** 且各行可修订态 |
| `SupplyRequest#assertPharmacyTransferOutLinesRevisable` | **18** 药房调出 | 须 **`PHARMACY_TRANSFER_OUT`** 且各行可修订态 |
| `SupplyRequest#assertDeptTransferOutLinesRevisable` | **26** 科室调出 | 须 **`DEPT_TRANSFER_OUT`** 且各行可修订态 |
| `SupplyRequest#assertPharmacyRequisitionLinesRevisable` | **12** 药房请领 | 须 **`PHARMACY_REQUISITION`** 且各行可修订态 |
| `SupplyRequest#assertDeptRequisitionLinesRevisable` | **20** 科室请领 | 须 **`DEPT_REQUISITION`** 且各行可修订态 |
| `SupplyRequest#assertPharmacyStockInLinesRevisable` | **13** 药房入库 | 须 **`PHARMACY_STOCK_IN`** 且各行可修订态（应用层手工写仍拒绝） |
| `SupplyRequest#assertCabinetReturnStockInLinesRevisable` | **5** 退库入库单 | **恒拒绝**（壳单不可修订，**`WHS_RSI_006`**） |

### 4.4 枚举契约

| 字段 | 说明 |
| ---- | ---- |
| `typeEnum` | **`SupplyType#getCode()`** 字符串白名单（药库含 **`CABINET_INBOUND_RECEIPT`** / **`CABINET_INBOUND_RECEIPT_INSPECTION`** 等，见 **DM §4.1**） |
| `statusEnum` | **`SupplyStatus#getCode()`**（`PENDING_APPROVAL`/`APPROVAL`/…） |
| `sourceTypeEnum`/`purposeTypeEnum` | **`SupplyRequestType#getCode()`**（`DEPARTMENT`/`CABINET`/`PHARMACY`） |

### 4.5 仓储接口

- `SupplyRequestRepository extends JpaRepository<SupplyRequest, Long>`
- 必要签名：按 `busNo`、`originalBusNo`、`summaryId`、`idList` 查询
- **`flush()`**：继承 **`JpaRepository`** 之 **`flush`**；**仅**由 **`SupplyRequestDomainService#flushPendingChanges`** 在领域内调用；应用在 **`agreeRequest`** 写库后、**`getSupplyByBusNo`** 再读前经领域服务刷盘，使 **AGREE** 态与行主键对读侧可见（**B.2**：应用层**禁止**直调本仓储 **`flush`** 或注入 **`EntityManager`**）

### 4.6 查询模型（`queries.model`）

#### 4.6.1 `SupplyRequestModel`（`getSupplyByBusNo`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `id` | `Long` | 主表 | 主键 |
| `busNo` | `String` | 主表 | 单据号 |
| `typeEnum` | `String` | 主表 | **`SupplyType#getCode()`** |
| `itemQuantity` | `BigDecimal` | 主表 | 行数量；盘点为盈亏 |
| `totalQuantity` | `BigDecimal` | 主表 | 实盘数量（盘点等） |
| `totalPrice` | `BigDecimal` | 主表 | 实盘金额（盘点等） |
| `profitLossPrice` | `BigDecimal` | 主表 | 盈亏金额（盘点） |
| `purposeLocationId` | `Long` | 主表 | 目的库位（单端点真源；订货/进货列表筛选锚点见各 AS） |
| `consumingOrganizationId` | `Long` | 主表 | 领用科室（`consuming_organization_id`）；读侧历史可回落 `Location.organization_id` |
| `version` | `Integer` | 主表 | JPA 乐观锁；详情 **`rowVersion`** / 修订 **`expectedRowVersionForRevision`** 对齐 |
| `invoiceNo` | `String` | 主表 | 采购发票号（**`invoice_no`**）；同号多行共享；进销存 **`purc_invo_no`** |

#### 4.6.2 `SupplyRequestValidationModel`（`supplyRequestValidation`）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `busNo` | `String` | 入参 | 单据号 |
| `hasAgreed` | `Boolean` | 计算 | 是否已同意 |

#### 4.6.3 `SupplyRequestDocumentListQuery`（`countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter`）

| 字段名（Java） | 类型 | 说明 |
| --- | --- | --- |
| `typeEnum` | `String` | 可选；**`SupplyType#getCode()`** 等值过滤（如进货列表固定 **`CABINET_INBOUND_RECEIPT`**）；与 **`typeEnums`** 互斥，本字段非空时优先 |
| `typeEnums` | `List<String>` | 可选；供应类型多选（IN）；用于出库可关联请领默认「药房请领 + 科室请领」并集（**IF-WHS-SO-12**） |
| `sourceLocationId` | `Long` | 可选；与 **`source_location_id`** 对齐 |
| `sourceLocationIds` | `List<Long>` | 可选；`sourceLocationId=0`（全部药库）时由应用层展开为可见药库 id 集合 |
| `purposeLocationId` | `Long` | 可选；与 **`purpose_location_id`** 对齐（订货列表 **IF-WHS-PO-02** 锚点） |
| `purposeLocationIds` | `List<Long>` | 可选；`purposeLocationId=0`（全部药库）时由应用层展开为可见药库 id 集合 |
| `statusEnum` | `String` | 可选；**`SupplyStatus#getCode()`**（**B.10.8.1**） |
| `keyword` | `String` | 可选；单据号/备注类关键字 |
| `tenantId` | `Long` | 可选；租户 |
| `pageNo` / `pageSize` | `Integer` | 继承 **`AbstractPageQuery`**；分页边界 **B.10.11** |
| `startDate` / `endDate` | `LocalDateTime` | 可选；申请时刻窗（含 **`applyTimeFrom`/`applyTimeTo`** 之应用层映射） |

#### 4.6.4 `MedicationSummarySlipQueryModel`（`pageMedicationSummarySlips` · **AS-IP-NUR-010**）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `busNo` | `String` | `wkf_supply_request.bus_no` | 汇总单号 |
| `applyTime` | `LocalDateTime` | `wkf_supply_request.apply_time`（组内 max） | 申请时间 |
| `dispenseTime` | `LocalDateTime` | `wkf_supply_delivery.receive_time` | 已核发发药时间 |
| `sourceLocationId` | `Long` | `wkf_supply_request.source_location_id` | 发放药房 |
| `applicantId` | `Long` | `wkf_supply_request.applicant_id` | 申请人 |
| `receiverId` | `Long` | `wkf_supply_delivery.receiver_id` | 领药人 |
| `deliveryStatusEnum` | `String` | `wkf_supply_delivery.status_enum` | 发放态 |

#### 4.6.5 `MedicationSummarySlipLineQueryModel`（`listMedicationSummarySlipLinesByBusNo` · **AS-IP-NUR-010**）
| 字段名（Java） | 类型 | 来源 | 说明 |
| --- | --- | --- | --- |
| `supplyRequestId` | `Long` | `wkf_supply_request.id` | 明细行主键 |
| `busNo` | `String` | `wkf_supply_request.bus_no` | 汇总单号 |
| `itemId` | `Long` | `wkf_supply_request.item_id` | 药品定义 |
| `itemQuantity` | `BigDecimal` | `wkf_supply_request.item_quantity` | 拆零汇总数量 |
| `unitCode` | `String` | `wkf_supply_request.unit_code` | 拆零单位 |
| `unitPrice` | `BigDecimal` | `adm_charge_item.unit_price`（只读协作） | 账单单价 |
| `totalPrice` | `BigDecimal` | Σ `adm_charge_item.total_price` | 组内总价累加 |

---

## 5. 写侧与读侧白名单（命令字段全集）

### 5.1 写侧 — `SupplyRequestDomainService`

#### 5.1.1~5.1.12 建单方法（均返回 `String busNo` 或 `List<Long>`）

**方法与命令文件映射**：
- `createPurchaseOrder` → `CreatePurchaseOrderCommand.java`
- `createInboundReceipt` → `CreateInboundReceiptCommand.java`（历史别名 `createGoodsReceipt`）
- `createReturnToSupplier` → `CreateReturnToSupplierCommand.java`
- `createStockOut` → `CreateStockOutCommand.java`
- `createReturn` → `CreateReturnCommand.java`
- `createGainLoss` → `CreateGainLossCommand.java`
- `createStocktake` → `CreateStocktakeCommand.java`
- `createTransfer` → `CreateTransferCommand.java`
- `createRequisition` → `CreateRequisitionCommand.java`
- `createSummarySupplyRequest` → `CreateSummarySupplyRequestCommand.java`
- `createDeviceSummarySupplyRequest` → `CreateDeviceSummarySupplyRequestCommand.java`
- `createDeptIssue` → `CreateDeptIssueCommand.java`
- `createDeptIssueReturn` → `CreateDeptIssueReturnCommand.java`
- `createDeptPurchaseRequest` → `CreateDeptPurchaseRequestCommand.java`

**建单字段全集（通用）**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `typeEnum` | `String` | 是 | **`SupplyType#getCode()`**（如 `CABINET_PURCHASE_ORDER`；药库进货 **`CABINET_INBOUND_RECEIPT`** 等，见 **DM §4.1**） |
| `busNo` | `String` | 否 | 业务单号，系统生成 |
| `originalBusNo` | `String` | 条件 | 原单号：退货必填；主动退库可空（自由选品）；壳单必填 |
| `itemTable` | `String` | 是 | 物品来源表名（如 `med_medication_definition`） |
| `itemId` | `Long` | 是 | 物品标识 |
| `itemQuantity` | `BigDecimal` | 是 | 物品数量，>0 |
| `unitCode` | `String` | 是 | 数量单位编码 |
| `sourceTypeEnum` | `String` | 条件 | **`SupplyRequestType#getCode()`**（退库/调拨出库等有源侧时必填） |
| `sourceLocationId` | `Long` | 条件 | 来源库位（出库类等有源侧时必填） |
| `purposeTypeEnum` | `String` | 条件 | **`SupplyRequestType#getCode()`**（入库/调拨入等有目的侧时必填） |
| `purposeLocationId` | `Long` | 条件 | 去向库位（入库类等必填） |
| `categoryEnum` | `String` | 否 | **`SupplyCategory#getCode()`** |
| `supplierId` | `Long` | 条件 | 供应商（**药库退货可选**；采购/进货常见必填） |
| `summaryId` | `Long` | 条件 | 汇总单标识（执行单关联汇总单） |
| `patientId` | `Long` | 条件 | 患者标识（发药/发耗材关联患者） |
| `applicantId` | `Long` | 是 | 申请人标识 |
| `applyTime` | `LocalDateTime` | 否 | 申请时间，默认当前 |
| `occurrenceTime` | `LocalDateTime` | 否 | 业务发生时间 |
| `lotNumber` / `traceNo` / `remake` / `reason` | `String` | 否 | 批号/追溯/备注/原因 |
| `invoiceNo` | `String` | 否 | 采购发票号（进货/退货；同号多行共享；进销存 3503/3504） |

**`createStocktake` 行字段补充（覆盖通用行中冲突项）**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `itemQuantity` | `BigDecimal` | 是 | 盈亏数量（可负、可 0） |
| `totalQuantity` | `BigDecimal` | 否 | 实盘数量（≥0） |
| `totalPrice` | `BigDecimal` | 否 | 实盘金额 |
| `profitLossPrice` | `BigDecimal` | 否 | 盈亏金额 |
| `purposeTypeEnum` | `String` | 是 | 盘点库房类型（**禁止**仅用 `source_*` 表达业务库房） |
| `purposeLocationId` | `Long` | 是 | 盘点库房主键 |

**`createGainLoss` 行字段补充（覆盖通用行中冲突项）**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `itemQuantity` | `BigDecimal` | 是 | 盈亏数量（正报溢、负报损，**禁止 0**） |
| `totalQuantity` | `BigDecimal` | 否 | 实盘数量（≥0，可选） |
| `totalPrice` | `BigDecimal` | 否 | 实盘金额 |
| `profitLossPrice` | `BigDecimal` | 否 | 盈亏金额（随盈亏符号可负） |
| `purposeTypeEnum` | `String` | 是 | 损益库房类型 |
| `purposeLocationId` | `Long` | 是 | 损益库房主键 |

**`createInboundReceipt`（进货）行字段补充**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `typeEnum` | `String` | 是 | 固定 **`CABINET_INBOUND_RECEIPT`**（或验收类型由调用方指定） |
| `invoiceNo` | `String` | 否 | 采购发票号；应用层单头写入各行；前端不必填；空白落库占位 `0`；领域不拦空；进销存 3503 |
| `lotNumber` / `traceNo` | `String` | 条件 | 批号进货常见必填；追溯可选 |

**修订路径（`createStocktake`）**：首行 `busNo` 非空 → `findByBusNo` → `SupplyRequest#assertStocktakeLinesRevisable` → 旧行 `isDeleted=true` 批量落库 → 按命令行 `buildStocktakeEntityFromItem` 全量 `saveAll` 插入；**不**发布 `SupplyRequestCreatedEvent`。

**`createReturnToSupplier`（type **3**）行字段补充**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `typeEnum` | `String` | 是 | 固定 **`CABINET_RETURN_TO_SUPPLIER`** |
| `originalBusNo` | `String` | 是 | 已同意进货单 `bus_no`；**`buildReturnToSupplierEntityFromItem`** 空则 **`SUPPLY_REQUEST_INVALID_ARGUMENT`** |
| `busNo` | `String` | 修订必填 | 新建由系统生成；修订首行须带既有单号 |
| `practitionerId` | `Long` | 否 | **`practitioner_id`**；Mapper 自请求体写入 |
| `supplierId` | `Long` | 否 | 可空 |
| `lotNumber` / `traceNo` | `String` | 否 | 批号参与 **BR-SR-009** 行键 |
| `invoiceNo` | `String` | 否 | 采购发票号；可空；3504 上报优先本值，空则回落进货单 |
| `price` / `totalPrice` | `BigDecimal` | 写价后必填 | 领域落库前由 **`applyReturnToSupplierCommand`** 写入（**BR-SR-019**）；优先同源进货落库价 |
| `occurrenceTime` | `LocalDateTime` | 应用层必填 | 领域可空；**AS-INV-WHS-005** 建单门禁 |
| `reason` / `remake` | `String` | 否 | 行说明 |

**写价前置（`createReturnToSupplier`）**：方法入口调用 **`SupplyRequestWritePricingSupport#applyReturnToSupplierCommand`** → **`buildReturnToSupplierEntityFromItem`** 落 `price`/`total_price`。应用层可先 **`fillReturnToSupplierCommandPricesFromInbound`**（行键对齐进货 `price`）；进货缺价时写价门禁回落批号/目录进价。

**修订路径（`createReturnToSupplier`）**：首行 `busNo` 非空 → `findByBusNo` → **`SupplyRequest#assertReturnToSupplierLinesRevisable`** → 旧行 `isDeleted=true` → 写价前置 → **`buildReturnToSupplierEntityFromItem`** 全量 `saveAll`；**无** `expectedRowVersionForRevision`（与进货/订货修订不同）；**不**发布 **`SupplyRequestCreatedEvent`**。

**`createPurchaseOrder`（type **1**）命令补充**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `expectedRowVersionForRevision` | `Integer` | 修订必填 | 命令根字段；须与同号各行 **`SupplyRequest.version`** 一致 |
| `operatorId` | `Long` | 是 | 操作人（事件与审计） |
| 行 `practitionerId` | `Long` | 应用层必填 | **`practitioner_id`** 经手人 |
| 行 `occurrenceTime` | `LocalDateTime` | 应用层必填 | 制单时刻；草稿 **`applyTime`** 仍为 null |
| 行 `price` / `totalPrice` | `BigDecimal` | 否 | 行价；详情 enrich 锚持久化值（BR-PRICE-SRC-001） |
| 行 `busNo` | `String` | 修订必填 | 新建由领域取号；修订首行须带既有单号 |

**修订路径（`createPurchaseOrder`）**：首行 `busNo` 非空 → `findByBusNo` → **`SupplyRequest#assertPurchaseOrderLinesRevisable`** → 校验 **`expectedRowVersionForRevision`**（缺失或 mismatch → **`WHS_PO_105`**）→ 旧行 `isDeleted=true` → **`buildPurchaseOrderEntityFromItem`** 全量 `saveAll`；**不**发布 **`SupplyRequestCreatedEvent`**。

**`createTransfer`（type 8～10/17～19/25/26）命令补充**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `bootstrapFinalStatusCode` | `String` | 否 | 命令根字段；合法 **`SupplyStatus#getCode()`**；壳单生成时固定 **`AGREE`** |
| 行 `busNo` | `String` | 修订必填 | 新建由领域取号；**`CABINET_TRANSFER_OUT`** 修订首行须带既有单号 |
| 行 `originalBusNo` | `String` | 壳单必填 | 指向已同意调出单；主动调出执行单通常为空 |
| 行 `lotNumber` / `traceNo` | `String` | 否 | 批号/追溯；壳单从调出行拷贝 |
| 行 `supplierId` / `price` / `totalPrice` | 各类型 | 否 | 与进货/出库行同构 |
| 行 `practitionerId` | `Long` | 否 | 经手人 |
| 行 `approverId` / `approvalTime` | `Long` / `LocalDateTime` | 壳单建议 | bootstrap AGREE 时写入（与调出同意人对齐）；**不**走 `approvalToAgree` |

**修订路径（`createTransfer` · 当前实现）**：首行 `busNo` 非空 → `findByBusNo` → 按存量调出 type 分派门控后软删重插；**不**发布 **`SupplyRequestCreatedEvent`**。支持：**`CABINET_TRANSFER_OUT`** → **`assertCabinetTransferOutLinesRevisable`**；**`PHARMACY_TRANSFER_OUT`** → **`assertPharmacyTransferOutLinesRevisable`**；**`DEPT_TRANSFER_OUT`** → **`assertDeptTransferOutLinesRevisable`**。调入壳单等其它 type 仍拒绝修订。

**应用层编排（调出 agree · 三方调拨包）**：

| 调出包 | 主动 type | 调入壳单 type（按目的 LocationForm） | 调入写拒绝码 |
| ------ | --------- | ------------------------------------ | ------------ |
| **AS-INV-WHS-009** `warehouse/transferout` | `CABINET_TRANSFER_OUT` | `CABINET`→**8**；`PHARMACY`→**17**；`DEPARTMENT_PHARMACY`→**25** | **WHS_TI_002** |
| **AS-INV-PHA-008** `pharmacy/transferout` | `PHARMACY_TRANSFER_OUT` | 同上 | **PHA_TI_002** |
| **科室调出** `department/transferout` | `DEPT_TRANSFER_OUT` | 同上；源侧须 **仅** `DEPARTMENT_PHARMACY` | **DEPT_TI_002** |

共性：`agreeRequest` → `createTransferDelivery` + InventoryItem 源减目增 → **`createTransfer`**（`bootstrapFinalStatusCode=AGREE`，`originalBusNo`=调出单号，行带 `approverId`/`approvalTime`）。调入 AppService 写路径一律拒绝（上表错误码）；读侧列表缺审批人时按 `originalBusNo` 只读回填（不写库）。

**方法契约摘要（`createPurchaseOrder`）**：初态 **`PENDING_APPROVAL`**；**不在**本方法内写 SupplyDelivery/InventoryItem；**不在** **`agreeRequest`** 路径自动调用 **`createInboundReceipt`**（**AS-INV-WHS-001** **WHS-SDD-R-001**）。

**方法契约摘要（`createReturnToSupplier`）**：初态 **`PENDING_APPROVAL`**；入口写价（**BR-SR-019**）；**不在**本方法内写 SupplyDelivery/InventoryItem；可退余量与源库可用量在应用层 **AS-INV-WHS-005** 校验（**`ReturnToSupplierInboundLineSupport`**）。

**`createReturn`（type **5** 壳单 / **14** 药房退库 / **22** 科室退库）命令补充**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `bootstrapFinalStatusCode` | `String` | 否 | 命令根字段；壳单生成时固定 **`AGREE`** |
| `typeEnum` | `String` | 是 | **`CABINET_RETURN_STOCK_IN`**（壳单）/ **`PHARMACY_RETURN_TO_WAREHOUSE`** / **`DEPT_RETURN`** |
| `originalBusNo` | `String` | 条件 | 主动退库（14/22）：**可空**（空=自由选品）；非空须为已同意**入库单** `bus_no`。壳单（5）：**必填**，已同意**退库单** `bus_no` |
| `busNo` | `String` | 修订必填 | 壳单（type 5）**禁止**修订；type 14/22 修订首行须带既有单号 |
| 行 `lotNumber` / `traceNo` | `String` | 否 | 批号参与 **BR-SR-009** 行键 |
| 行 `reason` | `String` | 否 | 壳单须从下游退库行拷贝；**禁止**生成时置空 |
| 行 `approverId` / `approvalTime` | `Long` / `LocalDateTime` | 壳单建议 | bootstrap AGREE 时写入（与退库同意人对齐）；**不**走 `approvalToAgree` |

**修订路径（`createReturn`）**：type **5** → **`assertCabinetReturnStockInLinesRevisable`** 恒拒绝（**`WHS_RSI_006`**）；type **14** → **`assertPharmacyReturnToWarehouseLinesRevisable`**；type **22** → **`assertDeptReturnLinesRevisable`**；软删重插；**不**发布 **`SupplyRequestCreatedEvent`**（修订路径）。

**应用层编排（药房退库 agree · P-02）**：`agreeRequest` → `createSupplyDelivery` + InventoryItem 源药房减/目的药库增 → **`createReturn`**（`bootstrapFinalStatusCode=AGREE`，type **5**，`originalBusNo`=退库单号，行带 `reason`/`approverId`/`approvalTime`）。**W-05** 包 **`returnstockin`** 写路径拒绝（**`WHS_RSI_006`**）；读侧 **AS-INV-WHS-006** 列表/详情回显审批人、退货原因、计量带（**BR-WHS-RSI-003～005**）。

**应用层编排（科室退库 agree · `department/returntowarehouse`）**：与上同构；`typeEnum` **`DEPT_RETURN`**（sortOrder **22**）；建单时 **`originalBusNo` 可空**（自由选品）或非空须为已同意 **`DEPT_STOCK_IN`**；有关联时用 **`ReturnToWarehouseStockInLineHelper`** 校验可退余量；错误码 **`DEPT_RTW_*`**；同意成功后生成 type **5** 壳单。

**应用层只读（科室入库壳单 · `department/stockin`）**：`typeEnum` **`DEPT_STOCK_IN`**（sortOrder **21**）；由药库出库 agree 系统生成、初态 **`AGREE`**；应用层写路径一律 **`DEPT_IN_002`**；列表 `purposeLocationId=0` → **`InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID`**；库位须 **`LocationForm.DEPARTMENT_PHARMACY`**；应用服务 **`InventoryDepartmentStockInAppService`**。

**应用层只读（药房入库壳单 · `pharmacy/stockin`）**：对称口径 **`PHA_IN_002`**、**`InventoryPharmacyStockInAppService`**。

**应用层请领申请（type 12 · `pharmacy/requisition`）**：`typeEnum` **`PHARMACY_REQUISITION`**（sortOrder **12**）；`agreeRequest` **仅终态**（**PHA-QL-009**），不编排出库/入库；目的库位 **`PHARMACY`**；应用服务 **`InventoryPharmacyRequisitionAppService`**；错误码 **`PHA_QL_*`**。

**应用层请领申请（type 20 · `department/requisition`）**：`typeEnum` **`DEPT_REQUISITION`**（sortOrder **20**）；`agreeRequest` **仅终态**，不编排出库/入库；`source_location_id` 药库、`purpose_location_id` 科室药房 **`DEPARTMENT_PHARMACY`**；列表 `purposeLocationId=0` → **`InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID`**；应用服务 **`InventoryDepartmentRequisitionAppService`**；错误码 **`DEP_QL_*`**；删除前校验无下游 **`CABINET_STOCK_OUT`**（`original_bus_no` 引用）。

**`createRequisition` 修订路径（type 12/20/13）**：首行 `busNo` 非空 → `findByBusNo` → 按存量 type 分派门控后软删重插；**不**发布 **`SupplyRequestCreatedEvent`**。支持：**`PHARMACY_REQUISITION`** → **`assertPharmacyRequisitionLinesRevisable`**；**`DEPT_REQUISITION`** → **`assertDeptRequisitionLinesRevisable`**；**`PHARMACY_STOCK_IN`** → **`assertPharmacyStockInLinesRevisable`**。**`DEPT_STOCK_IN`** 等其它 type 仍拒绝修订。

**`createRequisition`（type **12/20/13/21**）行字段补充**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `lotNumber` | `String` | 否 | 批号（请领选品行级） |
| `startTime` | `LocalDateTime` | 否 | 生产日期；自选品写入 **`SupplyRequest.start_time`** |
| `endTime` | `LocalDateTime` | 否 | 有效期/失效日期；请领/批号级建单**须**落库（**BR-SR-020**）；与 **InventoryItem** 批号 **`expiration`** 对齐 |
| `price` / `totalPrice` | `BigDecimal` | 否 | 请领申请可选；入库壳单/执行单常见 |

**方法契约摘要（`createRequisition` · 请领 type 12/20）**：初态 **`PENDING_APPROVAL`**；`**buildRequisitionEntityFromItem`** 落 `lotNumber`/`startTime`/`endTime`；**不在**本方法内写 SupplyDelivery/InventoryItem。

**方法契约摘要（`createReturn` · 主动退库）**：初态 **`PENDING_APPROVAL`**；`originalBusNo` **可空**（自由选品）；非空时应用层 **P-02 / `department/returntowarehouse`** 校验可退余量（**`ReturnToWarehouseStockInLineHelper`**）；**不在**本方法内写 SupplyDelivery/InventoryItem。

**`createDeptIssue`（type **29**）命令行补充**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `typeEnum` | `String` | 是 | 固定 **`DEPT_ISSUE`** |
| `purposeLocationId` | `Long` | 是 | 科室药房 Location |
| `consumingOrganizationId` | `Long` | 是 | 领用科室 Organization；落库 **`consuming_organization_id`**（Flyway **health-final V111**） |
| `sourceLocationId` / `sourceTypeEnum` | — | — | **NULL**（单端点 purpose） |

**`createDeptIssueReturn`（type **30**）命令行补充**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `typeEnum` | `String` | 是 | 固定 **`DEPT_ISSUE_RETURN`** |
| `originalBusNo` | `String` | 是 | 源 **`DEPT_ISSUE`** `bus_no` |
| `purposeLocationId` | `Long` | 是 | 与源领用单一致 |
| `consumingOrganizationId` | `Long` | 是 | 自源领用单表头继承（AppService 解析，非 HTTP 入参） |

**建单绑定项（通用）**：
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertTypeAllowedAndDirectionValid` |
| `statusTransitionMethod` | `initPendingApprovalOrSend` |
| `repositoryChecks` | `validateBusNoNotDuplicated`, 原单校验（退库类） |
| `eventPublishAnchor` | `SupplyRequestCreatedEvent` |
| `unitTestCaseId` | `TC-WKF-SR-CMD-001~011` |

**建单方法契约摘要**：  
- 常规/执行单初态 `PENDING_APPROVAL`；type 27 初态 **`PENDING_APPROVAL`**（护士站 **AS-IP-NUR-010** 保存路径；发送后 **APPROVAL**）。  
- 退货 `originalBusNo` 必填并受可退余量门控；**主动退库**（type 14/22）`originalBusNo` **可空**（自由选品），非空时受可退余量门控；退库入库壳单 `originalBusNo` 必填。  
- 不在建单方法内直接写 SupplyDelivery/InventoryItem。

**`createSummarySupplyRequest`（type 27）行字段补充（覆盖通用行中冲突项）**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `typeEnum` | `String` | 是 | 固定 **`MEDICATION_SUMMARY_ORDER`** |
| `busNo` | `String` | 是 | 与 **MedicationDispense.summary_no** 一致（应用层先 `summarizeMedicationDispenses` 再本方法） |
| `itemTable` | `String` | 是 | **`MED_MEDICATION_DEFINITION`** |
| `purposeLocationId` | `Long` | 是 | **单端点 purpose 真源**：发放药房（`MedicationDispense.location_id`） |
| `purposeTypeEnum` | `String` | 是 | 由应用层 `LocationQueryService#requireSupplyRequestTypeForLocationId(purposeLocationId)` 推导 |
| `sourceLocationId` | `Long` | — | **不落库**（NULL） |
| `sourceTypeEnum` | `String` | — | **不落库**（NULL） |
| `summaryId` | `Long` | 否 | 合并组内首条发放行 id（回写关联） |
| `patientId` | `Long` | 否 | 合并组首行患者 |
| `lotNumber` | `String` | 否 | **允许 null**（**BR-IP-SUM-010**） |
| `applyTime` | `LocalDateTime` | 否 | 保存阶段 **null**；**submitApproval** 写入 |
| `statusEnum`（落库） | `String` | 是 | 初态 **`PENDING_APPROVAL`** |

**应用编排（AS-IP-NUR-010 IF-IP-SUM-04）**：合并键 **`locationId + medicationId + chargeUnitPrice`**；跨发放药房拆多张 `busNo`；Mapper **仅**写入 `purposeLocationId` / `purposeTypeEnum`；`source_*` 为 NULL。

**库表（V103）**：`source_*` / `purpose_*` 四列均可 NULL；单端点只写一侧；双端点两侧均非空。

---

#### 5.1.12 `submitApproval(SubmitApprovalCommand cmd)` → `boolean`
**命令文件**：`SubmitApprovalCommand.java`  
**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | `String` | 是 | 业务单号 |
| `applyClock` | `LocalDateTime` | 否 | 提单时间，默认当前 |  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertSubmittable` |
| `statusTransitionMethod` | `toApproval` |
| `repositoryChecks` | `getSupplyByBusNo` |
| `eventPublishAnchor` | `SupplyRequestSubmittedEvent` |
| `unitTestCaseId` | `TC-WKF-SR-CMD-012` |
**方法契约摘要**：`PENDING_APPROVAL` / `REJECT` / `WITHDRAW` 推进到 `APPROVAL`（`assertSubmittable`）。

---

#### 5.1.13 `withdrawApproval(WithdrawApprovalCommand cmd)` → `boolean`
**命令文件**：`WithdrawApprovalCommand.java`  
**字段全集**：`busNo`  
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertWithdrawable` |
| `statusTransitionMethod` | `toWithdraw` |
| `repositoryChecks` | `getSupplyByBusNo` |
| `eventPublishAnchor` | `SupplyRequestWithdrawnEvent` |
| `unitTestCaseId` | `TC-WKF-SR-CMD-013` |
**方法契约摘要**：审批前撤回，不直接处理下游发放。

---

#### 5.1.14 `agreeRequest(AgreeRequestCommand cmd)` → `boolean`
**命令文件**：`AgreeRequestCommand.java`  
**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | `String` | 是 | 业务单号 |
| `approverId` | `Long` | 是 | 审批人标识 |
| `approvalClock` | `LocalDateTime` | 否 | 审批时间，默认当前 |
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertApprovable` |
| `statusTransitionMethod` | `approvalToAgree` |
| `repositoryChecks` | `supplyRequestValidation` |
| `eventPublishAnchor` | `SupplyRequestAgreedEvent` |
| `unitTestCaseId` | `TC-WKF-SR-CMD-014` |
**方法契约摘要**：同意后由应用层分流：申请单生成执行单，执行单驱动发放与库存。

---

#### 5.1.15 `rejectRequest(RejectRequestCommand cmd)` → `boolean`
**命令文件**：`RejectRequestCommand.java`
**字段全集**：

| 字段 | 类型 | 必填 | 说明 |
| ---- | ---- | ---- | ---- |
| `busNo` | `String` | 是 | 业务单号 |
| `approverId` | `Long` | 是 | 审批人标识 |
| `approvalClock` | `LocalDateTime` | 否 | 审批时间，默认当前 |
| `changeReasonCode`（命令）/ 落库 `reason` | `String` | 是（业务上必填；技术可空） | 驳回原因；应用层 `rejectReason`/`changeReason` 写入命令后**同号全行**落实体 `reason`，列表 DTO 以 `rejectReason` 回显 |
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRejectable` |
| `statusTransitionMethod` | `approvalToReject(approverId, approvalTime, rejectReason)` |
| `repositoryChecks` | `getSupplyByBusNo` |
| `eventPublishAnchor` | `SupplyRequestRejectedEvent` |
| `unitTestCaseId` | `TC-WKF-SR-CMD-015` |
**方法契约摘要**：审核中驳回，记录审批追溯信息，并将驳回原因写入各行 `reason`。

---

#### 5.1.16 `removeByBusNo(RemoveByBusNoCommand cmd)` → `boolean`
**命令文件**：`RemoveByBusNoCommand.java`
**字段全集**：`busNo`、`operatorId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | `assertRemovableForSoftDelete`（逐行；**BR-SR-011**：`PENDING_APPROVAL` / `REJECT` / `WITHDRAW` 的 `SupplyStatus#getCode()`） |
| `statusTransitionMethod` | 同号全部行 `is_deleted=true`（底座将 delete 改写为软删更新之工程约定） |
| `repositoryChecks` | `findByBusNo`；下游引用校验见 **BR-SR-010**（应用层，DOC-CONFLICT 已述） |
| `eventPublishAnchor` | `SupplyRequestDeletedEvent` |
| `unitTestCaseId` | `TC-WKF-SR-CMD-016` |
**方法契约摘要**：按单号逻辑删除；状态不满足 **BR-SR-011** 或已下游引用（**BR-SR-010**）则拒绝。  
**应用编排（type 27 · AS-IP-NUR-010 IF-IP-SUM-08）**：应用层**先** `MedicationDispenseDomainService#cancelMedicationDispenseSummary`，**再**本方法（`busNo = summaryNo`）；门控 **PENDING_APPROVAL**；**禁止**与 **§5.1.17** 同事务混用。

---

#### 5.1.17 `cancelSummarySupplyRequest(CancelSummarySupplyRequestCommand cmd)` → `List<Long>`
**命令文件**：`CancelSummarySupplyRequestCommand.java`
**字段全集**：`summaryIdList`、`operatorId`
| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | type **27** 行筛选；无 `assertRemovableForSoftDelete` 同路径 |
| `statusTransitionMethod` | `findBySummaryIdIn` 命中行 `is_deleted=true` |
| `repositoryChecks` | `findBySummaryIdIn` |
| `eventPublishAnchor` | `SummarySupplyRequestCancelledEvent` |
| `unitTestCaseId` | `TC-WKF-SR-CMD-017` |
**方法契约摘要**：按 **`summary_id`** 列表逻辑删除 type **27** 行。**护士站 IF-IP-SUM-08 不使用本方法**——走 **§5.1.16** `removeByBusNo`（`bus_no = summaryNo`）。

---

#### 5.1.18 `flushPendingChanges()` → `void`

| 绑定项 | 内容 |
| ------ | ---- |
| `entityInvariantMethod` | — |
| `statusTransitionMethod` | — |
| `repositoryChecks` | `SupplyRequestRepository#flush` |
| `eventPublishAnchor` | — |
| `unitTestCaseId` | `TC-WKF-SR-CMD-018` |

**方法契约摘要**：将当前持久化上下文中已排队的供应请求变更强制刷入数据库；供应用在 **`agreeRequest`** 等同单元写库后、**`getSupplyByBusNo`** 再读前调用（**B.2**：应用层**禁止**直调 **`SupplyRequestRepository#flush`**）。

### 5.2 读侧 — `SupplyRequestQueryService`

| 方法 | 入参 | 出参 | 关键实现 | `unitTestCaseId` |
| ---- | ---- | ---- | -------- | ---------------- |
| `getSupplyByBusNo` | `busNo` | `List<SupplyRequestModel>` | 单据全行加载 | TC-WKF-SR-QRY-001 |
| `getSupplyByBusNoBatch` | `busNoList` | `List<SupplyRequestModel>` | 批量查询 | TC-WKF-SR-QRY-002 |
| `getSupplyByOriginalBusNo` | `originalBusNo` | `List<SupplyRequestModel>` | 溯源查询；**药库退货**可退余量统计（`original_bus_no = 进货 bus_no` 之全部退单） | TC-WKF-SR-QRY-003 |
| `selectByIdList` | `idList` | `List<SupplyRequestModel>` | 主键查询 | TC-WKF-SR-QRY-004 |
| `getById` | `id` | `SupplyRequestModel` | 单行查询 | TC-WKF-SR-QRY-005 |
| `supplyRequestValidation` | `busNo` | `SupplyRequestValidationModel` | 防重复同意 | TC-WKF-SR-QRY-006 |
| `verifyAbleEdit` | `busNo,itemId` | `Boolean` | 可编辑门控 | TC-WKF-SR-QRY-007 |
| `getItemTable` | `busNo` | `String` | 获取 itemTable | TC-WKF-SR-QRY-008 |
| `getItem` | `busNo` | `List<Long>` | 获取 itemId 列表 | TC-WKF-SR-QRY-009 |
| `countDistinctBusNo` | `SupplyRequestDocumentListQuery` | `long` | 条件内 **`COUNT(DISTINCT bus_no)`**；进货/订货等同号多行列表之 **`total`** | TC-WKF-SR-QRY-010 |
| `listDistinctBusNoPage` | `SupplyRequestDocumentListQuery` | `List<String>` | 当前页 **`bus_no`** 列表（有序，与 **`total`** 及分页参数一致） | TC-WKF-SR-QRY-011 |
| `listByBusNosWithFilter` | `SupplyRequestDocumentListQuery`, `List<String> busNos` | `List<SupplyRequestModel>` | 在 **`busNos`** 集合内按与 **count** 相同之过滤条件拉回全部明细行 | TC-WKF-SR-QRY-012 |
| `pageMedicationSummarySlips` | `SupplyRequestDocumentListQuery` | `Page<MedicationSummarySlipQueryModel>` | **AS-IP-NUR-010 IF-IP-SUM-02**；type 27 汇总单头 + SupplyDelivery | TC-WKF-SR-QRY-013 |
| `listMedicationSummarySlipLinesByBusNo` | `String summaryNo` | `List<MedicationSummarySlipLineQueryModel>` | **AS-IP-NUR-010 IF-IP-SUM-03**；合并行 + ChargeItem 只读价格 | TC-WKF-SR-QRY-014 |
| `listAgreedLinesByWarehouseAndApprovalWindow` | `SupplyRequestSettlementWindowQuery` | `List<SupplyRequestSettlementLineQueryModel>` | **AS-INV-RPT-001** · 应用 **`inventory/dailysettlement`**；`AGREE` + 审批日窗 + 库位 + type 白名单；WHERE **`SupplyRequestSettlementWindowPredicatesupport** | TC-WKF-SR-QRY-015 |

---

## 6. 不变式映射（DM §7）

| BR 编号 | 实现策略摘要 |
| ------- | ------------ |
| BR-SR-001 | 建单最小字段与 type 白名单 |
| BR-SR-002~005 | 同单状态一致、提交/审批追溯 |
| BR-SR-006~007 | 位置合法、调拨源目不同 |
| **BR-SR-008** | **`originalBusNo` 必填**；药库退货：原单 **`CABINET_INBOUND_RECEIPT` + AGREE**；应用 **`validateOriginalInboundReceiptForReturn`**（**AS-INV-WHS-005**） |
| **BR-SR-009** | 行键 `itemTable\|itemId\|normalizeLot`；应用 **`ReturnToSupplierInboundLineSupport#computeReturnableQuantityByLineKey`** / **`assertDraftLinesWithinInboundReturnable`**；占用状态 **`countsTowardReturnCommittedQuantity`**（排除 REJECT/WITHDRAW）；源库量 **`assertReturnToSupplierDraftLinesWithinSourceAvailableInventory`** |
| BR-SR-010~013 | 删除防下游、申请/执行两阶段、审批流门控 |
| BR-SR-014~018 | 可编辑门控、免申请执行单、药库订货限定、请领方向限制、科室虚拟库存 |
| **BR-SR-019** | 药库退货写价：`SupplyRequestWritePricingSupport#applyReturnToSupplierCommand`；优先进货落库价（应用 `fillReturnToSupplierCommandPricesFromInbound`）；实体落 `price`/`total_price`（**BR-PRICE-SRC-001** / **BR-MON-001**） |
| **BR-SR-020** | 请领/批号级建单：`CreateRequisitionItemCommand#startTime`/`endTime` → 实体 `start_time`/`end_time`；`endTime` 须持久化供详情回显 |

---

## 7. 事件契约与触发规则（DM §5）

| 事件类名 | 事件类包路径 | 最小载荷 | 触发方法 |
| -------- | ------------ | -------- | -------- |
| `SupplyRequestCreatedEvent` | `com.zxplat.domain.share.events.workflow.supplyrequest` | `busNo,typeEnum,statusEnum` | 5.1.1~5.1.11 |
| `SupplyRequestSubmittedEvent` | 同上 | `busNo,typeEnum,applyTime` | 5.1.12 |
| `SupplyRequestWithdrawnEvent` | 同上 | `busNo,typeEnum` | 5.1.13 |
| `SupplyRequestAgreedEvent` | 同上 | `busNo,typeEnum,approverId,approvalTime` | 5.1.14 |
| `SupplyRequestRejectedEvent` | 同上 | `busNo,typeEnum,approverId` | 5.1.15 |
| `SupplyRequestDeletedEvent` | 同上 | `busNo,typeEnum` | 5.1.16 |
| `SupplyRequestExpiredEvent` | 同上 | `busNo,typeEnum` | 应用层过期流程 |
| `SummarySupplyRequestCancelledEvent` | 同上 | `summaryIdList` | 5.1.17 |

**发布时机**：beforeCommit。

---

## 8. 删除与可见性契约

- 默认逻辑删除，查询过滤已删数据。
- 已产生下游发放/库存事实的单据不可删除。
- `type_enum` 必须在白名单，禁止历史脏枚举回流。

---

## 9. 框架优先适配规则

- 同一 `bus_no` 多行状态批量一致更新。
- 写侧不内嵌跨域写库，统一由事件+应用编排处理。
- Repository 仅接口，不写默认实现类。

---

## 10. 生成与验收清单

- §4 字段表与 DM §3 主表字段逐项对齐，无遗漏（含 `statusEnum` 状态字段全量映射）
- §5.1 共 17 个写方法小节，覆盖采购/入库/退货/出库/退库/盘点/调拨/领用/汇总全类型
- 每个写方法均包含命令文件、字段全集表格、绑定项、方法契约摘要
- §5.2 读方法 **13** 项与 DM §6.2 对齐，含分页与类型检索及 **`bus_no` 去重列表**三件套与日结窗批量读
- §6 BR 规则映射覆盖 28 类 `type_enum` 白名单与库存规则
- §7 事件 8 类与 DM §5.1 全量对齐（含提单/审批/同意/驳回/作废等跃迁事件）
- 两阶段编排明确：申请单 SEND 后由应用编排生成发放单，禁止申请单 SEND 直接触发扣减
- 关键路径单测用例 ID 与 §5 映射一致，可追溯

---

## 11. 单次会话代码生成门禁（DoD）

### 11.1 前置输入

- 锁定 DM-WKF-006、联动 DM-WKF-005/008

### 11.2 DoD

- 两阶段审批语义正确。
- 执行单 AGREE 后的编排入口清晰且幂等。

### 11.3 失败处理

- 若出现类型混用或重复同意，阻断并修订。

---

## 定稿检查清单（可打勾）

- [x] 骨架完整
- [x] 写侧 17 动作覆盖
- [x] 读侧 12 方法覆盖
- [x] BR/事件覆盖

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
| 2026-09-03 | **修订门控补齐**：§4.3 / §5.1 **`createTransfer`** 开放药房/科室调出修订（**`assertPharmacyTransferOutLinesRevisable`** / **`assertDeptTransferOutLinesRevisable`**）；**`createRequisition`** 开放科室请领修订（**`assertDeptRequisitionLinesRevisable`**），与应用层二次保存同构 |
| 2026-08-11 | 对齐 DM **v1.27**：§5.1 **`createReturn`** 主动退库 **`originalBusNo` 可空**；修订门控 **`assertDeptReturnLinesRevisable`**；**AS-INV-PHA-003 v0.2** / **AS-INV-DEP-003** |
| 2026-07-26 | 对齐 DM **v1.25**：§4.2/§4.6.1 增 **`consumingOrganizationId`**；§5.1 **`createDeptIssue`/`createDeptIssueReturn`** 命令行补充 |
| 2026-07-26 | 对齐 DM **v1.24**：§5.1 **`createTransfer`** 壳单行 **`approverId`/`approvalTime`**；三方调入列表历史壳只读回填 |
| 2026-07-26 | 对齐 DM **v1.23**：§5.1 **`createReturn`** 壳单行 **`reason`/`approverId`/`approvalTime`**；对齐 **AS-INV-WHS-006 v0.2** 列表/详情回显 |
| 2026-07-26 | 对齐 DM **v1.22**：**BR-SR-020**；§5.1 **`createRequisition`** 命令行 **`startTime`/`endTime`** 落实体效期；对齐 **AS-INV-PHA-002** / **AS-INV-DEP-001** 请领列表 enrich 与效期回显 |
| 2026-07-26 | 对齐 DM **v1.21**：**BR-SR-019**；§5.1 **`createReturnToSupplier`** 写价前置 **`applyReturnToSupplierCommand`**、命令行 **`price`/`totalPrice`**；对齐 **AS-INV-WHS-005 v0.4** |
| 2026-07-19 | 对齐 DM **v1.19**：实体/`SupplyRequestModel`/`CreateInboundReceipt`/`CreateReturnToSupplier` 增补 **`invoiceNo`**（`invoice_no`）；进销存 3503/3504 |
| 2026-07-15 | 对齐 DM **v1.15**：§5.1 **`createDeptPurchaseRequest`** · type **31** · **`assertDeptPurchaseRequestLinesRevisable`**；**`createPurchaseOrder`** 可选 **`originalBusNo`**；**AS-INV-DEP-007** |
| 2026-07-14 | §4.6.3 **`SupplyRequestDocumentListQuery`** 增补 **`typeEnums` / `sourceLocationIds`**（出库可关联请领 **IF-WHS-SO-12** 并集收窄） |
| 2026-07-02 | **AS-INV-RPT-001 v1.3 / SDD v0.4**：§5.2 补充应用消费 **`inventory/dailysettlement`** 与 **`SupplyRequestSettlementWindowPredicateSupport`** 锚点 |
| 2026-07-02 | **AS-INV-RPT-001**：§5.2 **`listAgreedLinesByWarehouseAndApprovalWindow`** · TC-WKF-SR-QRY-015 落地；对齐 DM **v1.15** §6.2.12 |
| 2026-05-28 | **对账收口**：与 `zxplat-core/zxplat-domain` 现行实现一致；本聚合**无** InpatientInfo/OutpatientInfo/OrderInfo ReadModel 消费（跨聚合读组装见 **DM-ADM-002** §6.2.18～20 · `EncounterContextFacade`）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| 2026-06-06 | 对齐 **AS-IP-NUR-010 v1.5** / DM **v1.4**：§5.1.16 增补 IF-IP-SUM-08 与 **DM-MED-002** 取消汇总串行；§5.1.17 明确**非**护士站撤销路径 |
| 2026-06-30 | **AS 编号澄清**：**AS-INV-WHS-005** = W-03 退货；**W-05** `returnstockin` AS 待建 |
| 2026-07-01 | 对齐 DM **v1.14**：§5.1 **`createDeptIssueReturn`** · type **30** **`DEPT_ISSUE_RETURN`** · **`assertDeptIssueReturnLinesRevisable`** · **AS-INV-DEP-009** |
| 2026-07-01 | 对齐 DM **v1.13**：§5.1 **`createDeptIssue`** · type **29** **`DEPT_ISSUE`** · **`assertDeptIssueLinesRevisable`** · **AS-INV-DEP-004** |
| 2026-07-01 | 对齐 DM **v1.12**：§5.1 **`createRequisition`** type **12/20** 请领 agree 不自动建执行单；**`department/requisition`** · **`DEP_QL_*`** · **`InventoryDepartmentConstants`** |
| 2026-07-01 | 对齐 DM **v1.11** / **AS-INV-DEP-005/006**：§5.1 **`createGainLoss`/`createStocktake`** 科室 type **23/24** 应用包 **`department/gainloss`**、**`stocktake`**；**`InventoryDepartmentConstants`**；**`DEP_GL_*`/`DEP_ST_*`** |
| 2026-06-30 | 对齐 DM **v1.8** / **P-02·W-05**：§5.1 **`createReturn`** `bootstrapFinalStatusCode`、type **5** 壳单 **`assertCabinetReturnStockInLinesRevisable`** 恒拒、药房退库 agree+退库入库壳单；**`ReturnToWarehouseStockInLinesupport** |
| 2026-06-30 | 对齐 DM **v1.7** / **AS-INV-WHS-008/009**：§5.1 **`createTransfer`** `bootstrapFinalStatusCode`、修订 **`assertCabinetTransferOutLinesRevisable`**、调出 agree+调入壳单编排；§4.3 静态门控表 |
| 2026-06-30 | 对齐 DM **v1.6** / **AS-INV-WHS-001**（W-01）：§5.1 **`createPurchaseOrder`** 修订路径、`expectedRowVersionForRevision`、`assertPurchaseOrderLinesRevisable`；§4.3 静态门控表；§4.6.1 `version`、§4.6.3 `purposeLocationIds` |
| 2026-06-30 | 对齐 DM **v1.5** / **AS-INV-WHS-005**（W-03）：§5.1 **`createReturnToSupplier`** 修订路径、`assertReturnToSupplierLinesRevisable`、`practitionerId`；§6 **BR-SR-008/009** 应用层锚点；§4.2 实体字段与静态门控表 |

---

**本 SDD 正文结束。**
