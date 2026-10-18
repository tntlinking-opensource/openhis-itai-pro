# 门诊医生站 · 申请退费 · 应用服务详细设计（SDD）

> **编码**：AS-OP-DOR-004-SDD  
> **版本**：v1.2.1 | **日期**：2026-09-14 | **作者**：（待填）  
> **v1.2.1**：对齐 **AS v1.3.2** **BR-DRA-006**——未发退耗按申请主键全量作废可作废发放行。  
> **v1.2.0**：对齐 **AS v1.3.0**，新增 **IF-DRA-04 撤销退费**（见附录 A）；**IF-DRA-01** 同表返回 `SETTLED`∪`REFUNDING`。  
> **v1.1.2**：退药/退耗/退诊疗幂等与 **AS v1.2.8** / **BR-MRQ-013** 对齐。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 门诊医生站 · **申请退费 / 撤销退费**应用服务（当前就诊上下文；**不含**支付逆向单、原支付资金终态收口） |
| **边界** | 编排 **IF-DRA-01～04**：可申请/可撤销账单行列表、耗材执行门禁、提交退费申请、撤销退费（对称还原药/耗/诊 + `REFUNDING→SETTLED`）；**不**调用支付执行链（与 **AS-OP-DOR-004** §1.3「申请 vs 执行」一致） |
| **编排权威** | `AS-OP-DOR-004-门诊医生站申请退费.md`（**v1.3.0+**）§2 / §4 / §1.6 **BR-DRA-001～027** |
| **方法权威** | `DM-ADM-002`、`DM-ADM-011`、`DM-FIN-002`、`DM-MED-002`、`DM-MED-003`、`DM-WKF-003`、`DM-WKF-004`、`DM-WKF-007` 各文档 **§6** 领域服务与查询服务 |
| **严格对齐** | IF 编号 **IF-DRA-01～04** 与 AS 一致；**BR-DRA-010**：第二段药/耗/诊编排 **仅**使用「本单退费」为 **true** 之 `chargeItemId` 集合；**BR-DRA-023～027** 见附录 A |
| **实现阶段口径** | **目标态全量契约**。**应用层已落地** `DoctorStationRefundApplicationAppService`（含 `cancelOutpatientRefundApplication`）；领域撤销写见附录 A.3。 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段口径** | 枚举/状态持久化 **String `code`**（`getCode()`），与 **《代码生成通用规范》B.10.8.1** 一致 |
| **QueryService口径** | 本三接口**不新增**专用QueryService `*QueryService`（已废弃）；列表与门禁以 **领域 `*QueryService`** 联查为主；**OutpatientEncounterContextQueryModel** / **BillingStatement** 等QueryService 由 **OutpatientBillRefundRequestedEvent** 等事件消费方 **最终一致**（与各 DM §5 对齐） |
| **事务口径** | **IF-DRA-03** 业务语义要求「自首个写库 Cmd 起至 `requestOutpatientBillRefund` 止失败可全回滚」；**工程实现**遵守《**代码生成通用规范**》：**`*AppService` 禁止标注 `@Transactional`**，跨聚合写库的事务传播与边界由各领域 **`DomainService`**（及底座默认传播）承载。若产品强制要求**单入口声明式事务**包裹整段编排，须在任务书单列豁免后再改 App 层（与现行规范冲突时以 **DOC-CONFLICT** 留痕）。 |
| **HTTP 接入口径** | `AppServiceControllerRegistrar`：`POST /api/app/doctorStationRefundApplication/{methodName}`，JSON 体与方法形参一致 |
| **业务异常码口径** | 抛出 `com.zxplat.framework.core.application.exception.BusinessException`；**AS** 语义码 **EX-DRA-*** 与工程常量映射见各 IF「失败码口径」；尚未存在的细码可暂映射 **`ErrorCodeConstants.*_RULE_CONFLICT`** 并在 §8 登记待收敛 |
| **重生成与原单一致（硬，BR-DRA-004 / BR-CHG-015）** | **IF-DRA-03 步骤 7（应用编排 + 领域命令）**：编排侧按保留行先调用各上游聚合 **`clone*ForPreservedChargeRegeneration`**，再调用 **`ChargeItemDomainService#createChargeItemsLinkingRegeneratedAnchors`**；落库所涉一切**新生成**业务根——至少含 **新 `ChargeItem`**、其关联 **药嘱 / 耗材请求 / 诊疗请求**、**药发 / 耗发** 及编排规定的**其它替换侧实体**——在**业务状态与可对齐的业务属性**上须与**源原单行及其请求/发放**在操作时刻的有效事实**保持一致（复刻）**；**仅允许**新主键、审计字段、`refund_id` 链回、以及**再结算入口态**（如新行 `PLANNED` 等约定）等**技术性差异**；**禁止**借「重生成」改写剂量、计价、锚点关系、执行完成事实等与源原单不一致的语义。 |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/doctorstation/refundapplication/
  service/
    DoctorStationRefundApplicationAppService.java
  request/
    ListRefundableChargeLinesForEncounterRequest.java
    VerifyConsumableExecutionBeforeRefundRequest.java
    SubmitOutpatientRefundApplicationRequest.java
  response/
    ListRefundableChargeLinesForEncounterResponse.java
    VerifyConsumableExecutionBeforeRefundResponse.java
    SubmitOutpatientRefundApplicationResponse.java
  dto/
    RefundableChargeLineItemDto.java
    RefundApplicationLineItemDto.java
    OutpatientRefundApplicationPaymentSummaryDto.java
  mapper/
    DoctorStationRefundApplicationMapper.java
    DoctorStationRefundApplicationMapperImpl.java
  constants/
    DoctorStationRefundApplicationConstants.java
```

### 2.2 依赖注入表（构造器注入）

| 依赖 | 用途 |
| --- | --- |
| `EncounterQueryService` | 写前/读前 `mustGetEncounter`（就诊上下文） |
| `PaymentReconciliationQueryService` | 按就诊列支付、解析 `charge_item_ids`、按 `paymentId` 强校验 |
| `ChargeItemQueryService` | `loadChargeItemsByIds`；**BR-DRA-020**：`filterOutSupersededSettledLines`；**BR-DRA-021**：`mapPreservedNewChargeItemIdBySourceId` |
| `ChargeItemDefinitionQueryService` | **IF-DRA-01**：按 `charge_item_definition_id` 批量解析定价名称 |
| `ProductDefinitionBatchLoader` | **IF-DRA-01**：按产品QueryService批量解析 `product_name`（与 **IF-OPC-03** 展示回落一致） |
| `DeviceDispenseQueryService` | **IF-DRA-02**：`selectByRequestIdList` 加载耗材发放 |
| `MedicationDispenseQueryService` | **IF-DRA-03**：`listMedicationDispensesByMedReqIds` |
| `ServiceRequestQueryService` | **IF-DRA-03**：`mustGetServiceRequest`（诊疗路径写前加载） |
| `ChargeItemDomainService` | **IF-DRA-03**：保留行新账单行落库 **`createChargeItemsLinkingRegeneratedAnchors(CreateChargeItemsLinkingRegeneratedAnchorsCommand)`**（**§1.2**）；`requestOutpatientBillRefund(BatchChargeItemIdsCommand)` |
| `MedicationRequestDomainService` | **IF-DRA-03**：保留链 **`cloneMedicationRequestForPreservedChargeRegeneration`**（**域已落地**，见 **DM-MED-003 §6.1.16**）；`createRefundMedicationOrder` / `reopenMedicationOrder` |
| `MedicationDispenseDomainService` | **IF-DRA-03**：`createPendingRefundDispense` / `cancelMedicationDispense`（见 §3.5 序号 10～13） |
| `DeviceRequestDomainService` | **IF-DRA-03**：保留链 **`cloneDeviceRequestForPreservedChargeRegeneration`**（**域已落地**，见 **DM-WKF-003 §6.1.11**）；`createDeviceOrderRefund` |
| `DeviceDispenseDomainService` | **IF-DRA-03**：`createPendingRefundDeviceDispense` / `cancelUndispensedDeviceDispense`（见 §3.5 序号 14） |
| `ServiceRequestDomainService` | **IF-DRA-03**：保留链 **`cloneServiceRequestForPreservedChargeRegeneration`**（**域已落地**，见 **DM-WKF-004 §6.1.17**）；`cancelServiceRequest` / `stopServiceRequest` |
| `DoctorStationRefundApplicationMapper` | Request→领域命令、领域实体/查询模型→Response/DTO |
| `CurrentOperatorResolver` | 写库命令 **`operatorId`**（`requireOperatorId`，**B.6.5**） |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 支付主单状态 | `com.zxplat.domain.share.enums.PaymentStatus` | `DM-FIN-002-PaymentReconciliation-支付流水` §2.3 / §3.1 `status_enum`；**BR-DRA-001** | **IF-DRA-01** Pre：筛选 `SUCCESS`、`PARTIAL_REFUNDED`（**code** 字符串比较） |
| 收费明细状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011-ChargeItem-收费明细` §2.3 / §3.1 `status_enum`；**BR-DRA-008** / **BR-DRA-020** | 拟退行须为 `SETTLED`；**IF-DRA-01/03** 排除 `REFUNDING`/`REFUNDED` 及被半退再生替代的源 `SETTLED`（**BR-REF-020**） |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002-MedicationDispense-药品发放` §2.2；`DM-WKF-007-DeviceDispense-耗材发放` §2.3；**BR-DRA-003** | **IF-DRA-02**：药品 `COMPLETED`/`PART_COMPLETED`、耗材 `COMPLETED`（**getCode()**）命中即拒绝 |
| 收费行业务表锚点 | `RefundableChargeLineItemDto.serviceTable` | `DM-ADM-011` §3.1 `service_table` + **BR-CHG-001** | 与 `TableNameConstants` 比对分流药/耗/诊 |
| 表名常量（实现侧） | `com.zxplat.domain.share.constants.TableNameConstants.MED_MEDICATION_REQUEST` / `WKF_DEVICE_REQUEST` / `WKF_SERVICE_REQUEST` | `DM-ADM-011` **BR-CHG-001**；`TableNameConstants` 注释对齐 DM | **IF-DRA-03** Gate/Pre：`service_table` 分流 |
| 业务异常码（工程） | `com.zxplat.domain.share.constants.ErrorCodeConstants.ErrorCode`（如 `ENCOUNTER_NOT_FOUND`、`CHARGE_ITEM_INVALID_STATUS`、`PAYMENT_RECONCILIATION_RULE_CONFLICT`） | 各 DM §6 门禁与 **AS** §5 异常语义 | **IF** 失败码映射见 §3 |

### 2.4 领域方法与 Mapper 白名单

**领域查询**

| 领域 / 聚合 | 类型 | 方法 | 用于 IF |
| --- | --- | --- | --- |
| Encounter | `EncounterQueryService` | `mustGetEncounter` | IF-DRA-01～03 |
| PaymentReconciliation | `PaymentReconciliationQueryService` | `findPaymentsByEncounter` | IF-DRA-01 |
| PaymentReconciliation | `PaymentReconciliationQueryService` | `mustGetPayment` | IF-DRA-03 |
| PaymentReconciliation | `PaymentReconciliationQueryService` | `getChargeItemIdsByPayments` | IF-DRA-01 |
| ChargeItem | `ChargeItemQueryService` | `loadChargeItemsByIds` | IF-DRA-01 / IF-DRA-03 |
| ChargeItem | `ChargeItemQueryService` | `filterOutSupersededSettledLines` | IF-DRA-01 / IF-DRA-03 |
| ChargeItem | `ChargeItemQueryService` | `mapPreservedNewChargeItemIdBySourceId` | IF-DRA-03 |
| ChargeItemDefinition | `ChargeItemDefinitionQueryService` | 按 id 批量查询（应用层 `buildChargeDefinitionMapByDefinitionId`） | IF-DRA-01 |
| ProductDefinition | `ProductDefinitionBatchLoader` | 批量产品QueryService查询（应用层 `buildProductDefinitionMapByChargeItems`） | IF-DRA-01 |
| DeviceDispense | `DeviceDispenseQueryService` | `selectByRequestIdList` | IF-DRA-02 |
| MedicationDispense | `MedicationDispenseQueryService` | `listMedicationDispensesByMedReqIds` | IF-DRA-03 |
| ServiceRequest | `ServiceRequestQueryService` | `mustGetServiceRequest` | IF-DRA-03 |

**领域写**

| 领域 / 聚合 | 类型 | 方法 | 用于 IF |
| --- | --- | --- | --- |
| ChargeItem | `ChargeItemDomainService` | `createChargeItemsLinkingRegeneratedAnchors(CreateChargeItemsLinkingRegeneratedAnchorsCommand)` | IF-DRA-03（**ChargeItem 侧已落地**，见 §1.1） |
| ChargeItem | `ChargeItemDomainService` | `requestOutpatientBillRefund(BatchChargeItemIdsCommand)` | IF-DRA-03 |
| MedicationRequest | `MedicationRequestDomainService` | `cloneMedicationRequestForPreservedChargeRegeneration(CloneMedicationRequestForPreservedChargeRegenerationCommand)` | IF-DRA-03（**域已落地**） |
| MedicationRequest | `MedicationRequestDomainService` | `createRefundMedicationOrder(CreateRefundMedicationOrderCommand)` | IF-DRA-03 |
| MedicationRequest | `MedicationRequestDomainService` | `reopenMedicationOrder(ReopenMedicationOrderCommand)` | IF-DRA-03 |
| MedicationDispense | `MedicationDispenseDomainService` | `createPendingRefundDispense(CreatePendingRefundDispenseCommand)` | IF-DRA-03 |
| MedicationDispense | `MedicationDispenseDomainService` | `cancelMedicationDispense(CancelMedicationDispenseCommand)` | IF-DRA-03 |
| DeviceRequest | `DeviceRequestDomainService` | `cloneDeviceRequestForPreservedChargeRegeneration(CloneDeviceRequestForPreservedChargeRegenerationCommand)` | IF-DRA-03（**域已落地**） |
| DeviceRequest | `DeviceRequestDomainService` | `createDeviceOrderRefund(CreateDeviceOrderRefundCommand)` | IF-DRA-03 |
| DeviceDispense | `DeviceDispenseDomainService` | `createPendingRefundDeviceDispense(CreatePendingRefundDeviceDispenseCommand)` | IF-DRA-03 |
| DeviceDispense | `DeviceDispenseDomainService` | `cancelUndispensedDeviceDispense(CancelUndispensedDeviceDispenseCommand)` | IF-DRA-03 |
| ServiceRequest | `ServiceRequestDomainService` | `cloneServiceRequestForPreservedChargeRegeneration(CloneServiceRequestForPreservedChargeRegenerationCommand)` | IF-DRA-03（**域已落地**） |
| ServiceRequest | `ServiceRequestDomainService` | `cancelServiceRequest(CancelServiceRequestCommand)` | IF-DRA-03 |
| ServiceRequest | `ServiceRequestDomainService` | `stopServiceRequest(StopServiceRequestCommand)` | IF-DRA-03 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `DoctorStationRefundApplicationMapper` | `toRefundableChargeLineItemDto(ChargeItem, PaymentReconciliationQueryModel, String itemDisplayName)` | IF-DRA-01（Asm；`itemDisplayName` 由 App 批量解析；**Mapper 内** `EnumUtil.fromCode(ChargeItemStatus)` 装配 `chargeItemStatusEnum*` 与 **BR-DRA-002** `refundStatusText`） |
| `DoctorStationRefundApplicationMapper` | `toListRefundableChargeLinesForEncounterResponse(List<RefundableChargeLineItemDto>)` | IF-DRA-01（Asm） |
| `DoctorStationRefundApplicationMapper` | `toVerifyConsumableExecutionBeforeRefundResponse(boolean allowed, String rejectReasonCode)` | IF-DRA-02（Asm） |
| `DoctorStationRefundApplicationMapper` | `toCreateChargeItemsLinkingRegeneratedAnchorsCommand(Long operatorId, List<CreateChargeItemsLinkingRegeneratedAnchorsCommand.Item> anchorItems)` | IF-DRA-03（Asm→Cmd；**anchorItems** 由 App 在 `clone*` 后装配，与 `CreateChargeItemsLinkingRegeneratedAnchorsCommand` 对齐） |
| `DoctorStationRefundApplicationMapper` | `toBatchChargeItemIdsCommand(Collection<Long> chargeItemIds, Long operatorId)` | IF-DRA-03（Asm） |
| `DoctorStationRefundApplicationMapper` | `toCreateRefundMedicationOrderCommand(...)` 等 overload | IF-DRA-03（Asm；按分支选用，签名见 §3.5） |
| `DoctorStationRefundApplicationMapper` | `toSubmitOutpatientRefundApplicationResponse(boolean success, int refundingChargeItemCount, List<OutpatientRefundApplicationPaymentSummaryDto> paymentSummaries)` | IF-DRA-03（Asm；**BR-DRA-021**） |

### 2.5 白名单双向一致性复核

| 复核项 | 结论 | 说明 |
| --- | --- | --- |
| 正向 | PASS | §2.4 各方法均出现在 §3 编排序列或 Mapper 引用链中 |
| 反向 | PASS | §3 序列中 `*QueryService` / `*DomainService` / `Mapper#` 均可回溯 §2.4 |
| 互斥步骤对 | PASS | **IF-DRA-03** 中药品/耗材发放态分支、诊疗完成与否分支均在 §3.5「说明」列写明条件与跳转 |

---

## 3. 按接口 ID 命名展开

§3 导语：**先读完各 IF 的「领域编排序列」**，按序号从上到下执行；**Gate / Pre 任一失败不得进入 Cmd**。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-DRA-01 | `listRefundableChargeLinesForEncounter` | 加载可申请退费账单行 | `POST /api/app/doctorStationRefundApplication/listRefundableChargeLinesForEncounter` |
| IF-DRA-02 | `verifyConsumableExecutionBeforeRefund` | 申请前校验耗材执行 | `POST /api/app/doctorStationRefundApplication/verifyConsumableExecutionBeforeRefund` |
| IF-DRA-03 | `submitOutpatientRefundApplication` | 提交门诊退费申请 | `POST /api/app/doctorStationRefundApplication/submitOutpatientRefundApplication` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 租户 / 参数非空 / 就诊键合法 | 不再调用后续步骤 |
| **Pre** | 只读：加载就诊、支付、收费行、发放快照；**BR-DRA-001 / 009 / 010** 门禁 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：`ChargeItemDomainService` / 药耗诊域服务 | 事务回滚见 §5 |
| **Asm** | `DoctorStationRefundApplicationMapper` 纯装配 | — |

**记法**：`EncounterQueryService#mustGetEncounter` 表示在 `DoctorStationRefundApplicationAppService` 中对注入 Bean 调用该方法。

---

### 3.2 IF-DRA-01 `listRefundableChargeLinesForEncounter`（加载可申请退费账单行）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationRefundApplicationAppService#listRefundableChargeLinesForEncounter` |
| **HTTP** | `POST /api/app/doctorStationRefundApplication/listRefundableChargeLinesForEncounter` |

**Request `ListRefundableChargeLinesForEncounterRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 当前就诊主键（医生站上下文） |

**Response `ListRefundableChargeLinesForEncounterResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `lines` | `List<RefundableChargeLineItemDto>` | 可退候选行；按 **支付**分组排序（**BR-DRA-002**） |

**DTO `RefundableChargeLineItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paymentId` | Long | Y | 支付锚点（**BR-DRA-009**） |
| `paymentBusNo` | String | N | 支付业务号展示 |
| `paymentStatusEnum` | String | Y | `PaymentStatus#getCode()` |
| `chargeItemId` | Long | Y | 收费行锚点 |
| `encounterId` | Long | Y | 就诊 |
| `serviceTable` | String | Y | 业务来源表名（与 `adm_charge_item.service_table` 一致） |
| `serviceId` | Long | Y | 业务来源主键 |
| `chargeItemStatusEnum` | String | Y | `ChargeItemStatus#getCode()` |
| `chargeItemStatusEnumText` | String | N | 收费明细状态展示（`EnumUtil.fromCode` + `ChargeItemStatus#getDisplay()`，与 **B.10.8.1** 一致） |
| `paymentStatusEnumText` | String | N | 支付状态展示（`PaymentStatus#getDisplay()`） |
| `refundStatusText` | String | Y | 收费项退费状态展示文案（**BR-DRA-002**） |
| `itemDisplayName` | String | N | 收费项名称摘要 |
| `quantity` | BigDecimal | N | 计价数量 |
| `lineTotalAmount` | BigDecimal | N | 行金额（**BR-CHG-QRY-003** 口径由 Mapper/查询侧统一） |
| `paymentGroupSortKey` | Long | Y | 用于前端稳定分组排序（典型取 `paymentId`） |

**关键校验**

- `encounterId` 非空；就诊存在且为医生站可见门诊上下文（与 **DM-ADM-002** 门禁一致，由 `mustGetEncounter` 承担）。

**失败码口径**

| AS 语义码 | 场景 | errorCode（工程常量） | HTTP |
| --- | --- | --- | --- |
| EX-DRA-101 | 就诊无效 / 查询失败 | `ErrorCodeConstants.ENCOUNTER_NOT_FOUND` 或 `ENCOUNTER_INVALID_ARGUMENT` | 409/400（以项目全局异常处理器为准） |

**Mapper 接口方法签名**

- `toRefundableChargeLineItemDto(ChargeItem chargeItem, PaymentReconciliationQueryModel payment, String itemDisplayName): RefundableChargeLineItemDto`
- `toListRefundableChargeLinesForEncounterResponse(List<RefundableChargeLineItemDto> lines): ListRefundableChargeLinesForEncounterResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `encounterId` | 非空 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter(Long encounterId)` | ① `encounterId` | 仅读路径；不存在则 **EX-DRA-101** |
| 3 | Pre | `PaymentReconciliationQueryService#findPaymentsByEncounter(Long encounterId)` | ① | 内存过滤：`payment.statusEnum ∈ {PaymentStatus.SUCCESS.getCode(), PaymentStatus.PARTIAL_REFUNDED.getCode()}`（**BR-DRA-001**）；若无命中支付，**lines 返回空表** |
| 4 | Pre | `PaymentReconciliationQueryService#getChargeItemIdsByPayments(List<Long> paymentIds)` | ③ 支付主键列表 | 解析各支付 `charge_item_ids` 快照 |
| 5 | Pre | `ChargeItemQueryService#loadChargeItemsByIds(ChargeItemQueryCommand)` | ④ 扁平化 id 集（`setIds`） | 过滤：`encounterId` 一致且 `ChargeItemStatus.SETTLED`（可申请退费事实） |
| 5a | Pre | `ChargeItemQueryService#filterOutSupersededSettledLines(Long encounterId, List<ChargeItem> rows)` | ⑤ 结果 | **BR-DRA-020** / **BR-REF-020**：排除半退保留链已替代的源 `SETTLED` |
| 5b | Pre | App：`buildChargeDefinitionMapByDefinitionId` / `buildProductDefinitionMapByChargeItems` | 5a 结果 | 批量解析 `itemDisplayName`（与 **IF-OPC-03** 同口径） |
| 6 | Asm | `DoctorStationRefundApplicationMapper#toRefundableChargeLineItemDto` | 5a 每条 + 支付模型 + `itemDisplayName` | 组装 **BR-DRA-002** 与 `chargeItemStatusEnum*` 展示字段 |
| 7 | Asm | `DoctorStationRefundApplicationMapper#toListRefundableChargeLinesForEncounterResponse` | ⑥ 列表 | 按 `paymentId`、`chargeItemId` 排序 |

---

### 3.3 IF-DRA-02 `verifyConsumableExecutionBeforeRefund`（申请前校验耗材执行）

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationRefundApplicationAppService#verifyConsumableExecutionBeforeRefund` |
| **HTTP** | `POST /api/app/doctorStationRefundApplication/verifyConsumableExecutionBeforeRefund` |

**Request `VerifyConsumableExecutionBeforeRefundRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊 |
| `chargeItemIds` | `List<Long>` | Y | 勾选收费项（**非空**） |

**Response `VerifyConsumableExecutionBeforeRefundResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `allowed` | boolean | `true` 表示通过门禁 |
| `rejectReasonCode` | String | 若不通过，填充稳定语义码（如 `EX-DRA-201`） |

**关键校验**

- 从步骤 **3** 加载的 `ChargeItem` 中筛 `service_table = TableNameConstants.WKF_DEVICE_REQUEST`；若无耗材项，`allowed=true`（**BR-DRA-003**）。

**失败码口径**

| AS 语义码 | 场景 | errorCode | 说明 |
| --- | --- | --- | --- |
| EX-DRA-201 | 耗材已执行 | `ErrorCodeConstants.PAYMENT_RECONCILIATION_RULE_CONFLICT`（**目标态**可收敛为专用 `DRA_*`） | 耗材任一行为 `COMPLETED`；药品任一行为 `COMPLETED` / `PART_COMPLETED` |

**Mapper 接口方法签名**

- `toVerifyConsumableExecutionBeforeRefundResponse(boolean allowed, String rejectReasonCode): VerifyConsumableExecutionBeforeRefundResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `encounterId`、`chargeItemIds` | 非空 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter(Long encounterId)` | ① | — |
| 3 | Pre | `ChargeItemQueryService#loadChargeItemsByIds(Collection<Long> ids)` | `chargeItemIds` | 校验均属该就诊 |
| 4 | Pre | — | ③ 结果 | **若** 无 `WKF_DEVICE_REQUEST` 行 → 跳至步骤 8（`allowed=true`）；**否则**继续步骤 5 |
| 5 | Pre | — | 耗材 `serviceId` 列表 | 去重得到 `deviceReqIds` |
| 6 | Pre | `DeviceDispenseQueryService#selectByRequestIdList(List<Long> requestIdList)` | `deviceReqIds` | — |
| 7 | Pre | — | ⑥ 发放行 | **若** 耗材行 `statusEnum = COMPLETED` 或药品行 `statusEnum ∈ {COMPLETED, PART_COMPLETED}` → **EX-DRA-201**；**否则**进入步骤 8 |
| 8 | Asm | `DoctorStationRefundApplicationMapper#toVerifyConsumableExecutionBeforeRefundResponse` | `true` / `false` | 仅读路径 |

---

### 3.4 IF-DRA-03 `submitOutpatientRefundApplication`（提交门诊退费申请）— 总览

| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationRefundApplicationAppService#submitOutpatientRefundApplication` |
| **HTTP** | `POST /api/app/doctorStationRefundApplication/submitOutpatientRefundApplication` |

**Request `SubmitOutpatientRefundApplicationRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | Y | 就诊 |
| `lines` | `List<RefundApplicationLineItemDto>` | Y | 非空；与 **IF-DRA-01** 返回成套 |

**DTO `RefundApplicationLineItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paymentId` | Long | Y | 支付锚点（**BR-DRA-009**） |
| `chargeItemId` | Long | Y | 收费行锚点 |
| `refundForCurrentBill` | Boolean | Y | **本单退费**：`true` 拟退；`false` 保留重开（与 AS「为真纳入退费申请」一致） |

**Response `SubmitOutpatientRefundApplicationResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | 受理成功 |
| `refundingChargeItemCount` | int | 进入 `REFUNDING` 的拟退行数 |
| `paymentSummaries` | `List<OutpatientRefundApplicationPaymentSummaryDto>` | 按支付分组的执行衔接摘要（**BR-DRA-021**）；无拟退行时仍返回（可能仅含保留再生信息） |

**DTO `OutpatientRefundApplicationPaymentSummaryDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `paymentId` | Long | Y | 原支付主单主键 |
| `refundingChargeItemIds` | `List<Long>` | Y | 本单拟退、已进入或即将进入 `REFUNDING` 的收费明细主键 |
| `preservedNewChargeItemIds` | `List<Long>` | Y | 半退保留链再生、待再结算的 `PLANNED` 收费明细主键 |
| `halfRefundOnPayment` | boolean | Y | 该笔支付是否为产品半退（同 payment 下既有拟退又有保留） |

**关键校验**

- 每行 `paymentId` 与 `chargeItemId` 须命中同一支付的 `charge_item_ids` 快照子集（`mustGetPayment` + 解析比对，**BR-DRA-009**）。
- **BR-DRA-020**：提交范围须经 `filterOutSupersededSettledLines`；不可见行、`REFUNDING`/`REFUNDED` 行拒绝（`CHARGE_ITEM_RULE_CONFLICT`）。
- 拟退行（`refundForCurrentBill=true`）`ChargeItem.statusEnum` 须为 `SETTLED`（**BR-DRA-008**）。
- **重生成与原单一致（硬）**：步骤 **7** 写入的**新 `ChargeItem`（账单行）**及本步一并重生成的 **请求 / 发放** 等凡属「由源行派生之替换侧」实体，须与**源原单行**之业务事实**状态一致（复刻）**；口径同 **§1.2**。

**失败码口径（节选）**

| AS 语义码 | 场景 | errorCode（建议） |
| --- | --- | --- |
| EX-DRA-301 | 原药嘱本身已是负向退药单 | `MEDICATION_REQUEST_RULE_CONFLICT`（文案「已申请退药，请勿重复申请」）；**不含**「同批多收费行共锚同一正向药嘱」或「他支付单既有退药」 |
| EX-DRA-302 | 原耗嘱本身已是负向退耗单 | 同上（耗材维度，`DEVICE_REQUEST_RULE_CONFLICT`） |
| EX-DRA-303 | 原诊疗申请本身已是负向退单 | `SERVICE_REQUEST_INVALID_ARGUMENT` 或域等价 |
| EX-DRA-399 | 其它业务失败 | 各域已有常量 |
| EX-DRA-410 | 成功 | — |

**Mapper 接口方法签名（与本 IF 相关，完整）**

- `toCreateChargeItemsLinkingRegeneratedAnchorsCommand(SubmitOutpatientRefundApplicationRequest request, List<ChargeItem> loadedItems, …): com.zxplat.domain.administration.chargeitem.command.CreateChargeItemsLinkingRegeneratedAnchorsCommand`（**目标态**；应用层 Mapper 随 AppService 同期落地）
- `toBatchChargeItemIdsCommand(Collection<Long> chargeItemIds): com.zxplat.domain.administration.chargeitem.command.BatchChargeItemIdsCommand`
- `toCreateRefundMedicationOrderCommand(Long medicationRequestId, Long operatorId): com.zxplat.domain.medication.medicationrequest.command.CreateRefundMedicationOrderCommand`（须传**药品申请**主键，禁止传发放主键）
- `toReopenMedicationOrderCommand(Long medicationRequestId, Long operatorId): com.zxplat.domain.medication.medicationrequest.command.ReopenMedicationOrderCommand`
- `toCreatePendingRefundDispenseCommand(MedicationDispense dispense, Long refundMedicationRequestId): com.zxplat.domain.medication.medicationdispense.command.CreatePendingRefundDispenseCommand`
- `toCancelMedicationDispenseCommand(Long dispenseId, Long operatorId): com.zxplat.domain.medication.medicationdispense.command.CancelMedicationDispenseCommand`
- `toCreateDeviceOrderRefundCommand(ChargeItem chargeItem, Long operatorId): com.zxplat.domain.workflow.devicerequest.command.CreateDeviceOrderRefundCommand`
- `toCreatePendingRefundDeviceDispenseCommand(Long sourceDispenseId, BigDecimal refundQuantity, Long operatorId, Long refundDeviceRequestId): CreatePendingRefundDeviceDispenseCommand`（待退发放 `device_req_id` 锚定负向耗单）
- `toCancelUndispensedDeviceDispenseCommand(Long dispenseId): com.zxplat.domain.workflow.devicedispense.command.CancelUndispensedDeviceDispenseCommand`
- `toCreateServiceOrderRefundCommand(ChargeItem chargeItem, Long operatorId): com.zxplat.domain.workflow.servicerequest.command.CreateServiceOrderRefundCommand`
- `toStopServiceRequestCommand(Long serviceRequestId, Long operatorId): com.zxplat.domain.workflow.servicerequest.command.StopServiceRequestCommand`
- `toSubmitOutpatientRefundApplicationResponse(boolean success, int refundingChargeItemCount, List<OutpatientRefundApplicationPaymentSummaryDto> paymentSummaries): SubmitOutpatientRefundApplicationResponse`

---

### 3.5 IF-DRA-03 — **领域编排序列（严格顺序，含药/耗/诊分支）**

> **BR-DRA-010（硬）**：步骤 **6** 构造的集合 **R** 及步骤 **8** 起药/耗/诊 Cmd **仅为** `refundForCurrentBill=true` 之行。

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | — | `request` | `lines` 非空；每行三字段非空 |
| 2 | Pre | `EncounterQueryService#mustGetEncounter(Long encounterId)` | `request.encounterId` | — |
| 3 | Pre | `ChargeItemQueryService#loadChargeItemsByIds(Collection<Long> ids)` | 全部 `chargeItemId` | 校验行存在且 `encounterId` 一致 |
| 4 | Pre | — | 每行 `paymentId` | 循环：`PaymentReconciliationQueryService#mustGetPayment(Long paymentId)`；并校验 `chargeItemId` 属于该支付 `charge_item_ids` 快照 |
| 4a | Pre | `ChargeItemQueryService#filterOutSupersededSettledLines(Long encounterId, List<ChargeItem>)` | ③ 全量行 | **BR-DRA-020**：得到可见提交集合 |
| 4b | Pre | — | `request.lines` × ③ | 每行须在可见集内；禁止 `REFUNDING`/`REFUNDED`；`refundForCurrentBill=true` 须 `SETTLED` |
| 5 | Cmd | App：`regeneratePreservedChargeLines`（内部：`clone*` + **`ChargeItemDomainService#createChargeItemsLinkingRegeneratedAnchors`**) | `refundForCurrentBill=false` 子集 | **写库点 1**；**§1.2** 复刻一致（**BR-DRA-004**）；无保留行跳过 |
| 6 | Pre | — | `request.lines` | 构造拟退集合 **R**（**BR-DRA-010**：仅 `refundForCurrentBill=true`） |
| 6a | Pre | App：`buildPaymentSummariesForSubmitResponse` | `encounterId` + `request` + **R** | 调用 `mapPreservedNewChargeItemIdBySourceId` 装配 **BR-DRA-021**；**若 R 为空** → 步骤 **6b** Asm 返回（不写药/耗/诊） |
| 6b | Asm | `DoctorStationRefundApplicationMapper#toSubmitOutpatientRefundApplicationResponse` | `success=true`, `refundingCount=0`, `paymentSummaries` | **仅当 R 为空** |
| 7 | Pre | `ChargeItemQueryService#loadChargeItemsByIds(R)` | **R** | 二次加载拟退行事实（防并发漂移） |
| 8 | Pre | — | ⑦ 按 `service_table` 分组 | 得到药品 / 耗材 / 诊疗请求 id 列表 |
| 8a | Pre | `ServiceRequestQueryService#mustGetServiceRequest(Long id)` | ⑧ 每条拟退诊疗 `serviceId` | 写前强校验（**BR-DRA-007**）；无诊疗行则跳过 |
| 9 | Pre | `MedicationDispenseQueryService#listMedicationDispensesByMedReqIds(Collection<Long> medReqIds)` | ⑧ 药品请求 id | 用于发放态判断 |
| 10 | Cmd | **（药品分支，已发/部分发）** `createRefundMedicationOrder` **后紧接** `createPendingRefundDispense`（应用层 `createRefundMedicationOrderAndPendingDispense`） | Mapper | **写库点 2a+2b**（**BR-DRA-005**） |
| 11 | Cmd | `MedicationDispenseDomainService#cancelMedicationDispense` | Mapper | 药-互斥分支 |
| 12 | Cmd | `MedicationRequestDomainService#reopenMedicationOrder` | Mapper | 药-互斥分支 |
| 13 | Cmd | 同步骤 10 两段 Cmd | Mapper | 药-互斥分支 3/3 |
| 14 | Cmd | **（耗材）** `createDeviceOrderRefund`（`sourceRequestId`=解析后的申请主键）+ 未发则**全量** `cancelUndispensedDeviceDispense` / 已发则 `createPendingRefundDeviceDispense` | Mapper | **写库点 3**（**BR-DRA-006**） |
| 15 | Cmd | **（诊疗）** `createServiceOrderRefund`（已完成）**或** `stopServiceRequest`（未完成） | Mapper | **写库点 4**（**BR-DRA-007**） |
| 16 | Cmd | `ChargeItemDomainService#requestOutpatientBillRefund(BatchChargeItemIdsCommand)` | `Mapper#toBatchChargeItemIdsCommand(R, operatorId)` | **写库点 5**：`SETTLED→REFUNDING`（**BR-DRA-008**） |
| 17 | Asm | `DoctorStationRefundApplicationMapper#toSubmitOutpatientRefundApplicationResponse` | `success=true`, `refundingCount`, **6a** 的 `paymentSummaries` | **EX-DRA-410** / **BR-DRA-021** |

**说明（门禁补充）**

- 步骤 **10～15** 为**按业务表类型与行迭代**的 Cmd 族；门闩冲突时 **EX-DRA-301～303**，且不进入步骤 **16**。
- 步骤 **10**（及 **13**）药品已发/部分发路径须**同时**落库退药申请单与 `PENDING_REFUND` 发放单（**AS-OP-PHA-002** 队列/明细入口）。
- 步骤 **6a** `buildPaymentSummariesForSubmitResponse` 为 App 私有方法，**非** HTTP 接口；`halfRefundOnPayment = refunding 非空 ∧ preservedNew 非空`。
- **支付域写命令**不在本序列出现（与 **AS** 非目标一致）。
- **步骤 7 重生成范围与一致**：凡由 **步骤 7** 编排在本用例中**新插入/替换产生**的实体（**含新账单行 `ChargeItem`**，由 **`#createChargeItemsLinkingRegeneratedAnchors`** 落库；不限于请求/发放），**一律**适用 **§1.2「重生成与原单一致」**；不得仅对请求/发放做复刻而放任新账单行与源行在可对齐字段上偏离。

---

## 4. QueryService 与事件刷新

| 事件（业务语义，英文名以各 DM §5 为准） | 触发 IF | QueryService / 消费方 | 更新动作（摘要） |
| --- | --- | --- | --- |
| `OutpatientBillRefundRequestedEvent`（ChargeItem 域） | IF-DRA-03 | **OutpatientEncounterContextQueryModel**、**BillingStatement**、发药/执行队列 | 费用摘要、账单行 `REFUNDING` 展示、队列待办刷新（**最终一致**） |
| 药/耗「退单 / 待退 / 中止」类领域事件 | IF-DRA-03 | 药房、耗材执行、库存相关领域事件 | 以 **DM-MED-002/003**、**DM-WKF-007** §5 为准 |

> 本 SDD **不**展开QueryService 字段组装规则（与 SKILL §5.4 一致）。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览（对照 §3.5）

| IF | App 方法 | Cmd 序号（§3.5） | `Domain*Service#method(Command)` | 聚合写入顺序（摘要） |
| --- | --- | --- | --- | --- |
| IF-DRA-03 | `submitOutpatientRefundApplication` | 7 | **编排**：`clone*ForPreservedChargeRegeneration`（药/耗/诊，**域已落地**）+ `ChargeItemDomainService#createChargeItemsLinkingRegeneratedAnchors(...)` | 新 **ChargeItem**（**已可落库**）及关联请求/发放等**全量**与源原单事实复刻（**§1.2**）；上游请求克隆体由域服务生成 |
| IF-DRA-03 | 同上 | 12～18 | 药/耗/诊多条 | Medication* / Device* / ServiceRequest 写模型 |
| IF-DRA-03 | 同上 | 19 | `ChargeItemDomainService#requestOutpatientBillRefund(BatchChargeItemIdsCommand)` | `adm_charge_item` 拟退行 → `REFUNDING` |

### 5.2 事务边界与失败回滚

| IF | 事务边界 | 失败回滚 |
| --- | --- | --- |
| IF-DRA-01 / IF-DRA-02 | 无写库 | — |
| IF-DRA-03 | **不**在 `DoctorStationRefundApplicationAppService` 上使用 **`@Transactional`**（《代码生成通用规范》）；失败回滚依赖各领域写方法的事务语义与默认传播 | 任一 Cmd 抛错 → 由已参与事务的域服务回滚；**EX-DRA-301～303**（原单已是负向单）须在触碰 **写库点 5**（§3.5 序号 **16**）前拦截；同批共锚同一原请求的未完结负向单走域幂等返回，不视为失败 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 承诺 | 落地要点 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 同批多收费行共锚同一原请求时复用未完结负向单；原单已是负向单才拒 | `createRefundMedicationOrder` / `createDeviceOrderRefund` / `createServiceOrderRefund`：**已有未完结负向单 → 返回已有主键**；`refund_*_id` 非空的原单 → **EX-DRA-301～303** | `MedicationRequestDomainService` / `DeviceRequestDomainService` / `ServiceRequestDomainService`；App 侧本批发放/请求去重 | §3.5 序号 10～15 |
| 拟退行重复进入 `REFUNDING` | `requestOutpatientBillRefund` 侧幂等（**DM-ADM-011** **BR-CHG-003** 同类语义） | `ChargeItemDomainService#requestOutpatientBillRefund` | §3.5 序号 16 |

### 6.2 并发控制

| 承诺 | 落地要点 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 同就诊并发提交 | DB 乐观/悲观锁或「先查再改」由各领域服务实现；以后到者失败 | 各领域 `*DomainService` | §3.5 序号 3～17 |

### 6.3 重试与容错

| 承诺 | 落地要点 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 无自动重试 | 业务异常直出；可重试语义以 **AS** §5 **EX-DRA-** 系列异常码为准（示例：**EX-DRA-101**） | `DoctorStationRefundApplicationAppService` | §3 读路径 IF（IF-DRA-01 / IF-DRA-02） |

### 6.4 数据脱敏

| 承诺 | 落地要点 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 日志不落全量患者隐私 | 仅记录 `encounterId`、操作者、`chargeItemId` 集合规模 | `DoctorStationRefundApplicationAppService`（`submitOutpatientRefundApplication` 成功路径 `log.info`）及可扩展日志切面 | 全部 IF |

### 6.5 审计日志

| 承诺 | 落地要点 | 代码位置 | 关联步骤 |
| --- | --- | --- | --- |
| 申请可追溯 | 记录操作者、就诊、支付/收费行集合摘要 | App 层审计注解或 AOP | §3.5 序号 1～17 |

---

## 7. 测试要点

### 7.1 功能测试

| 编号 | 场景 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- |
| F-01 | IF-DRA-01 无成功/部分退支付 | 空列表 | 单元 | P0 |
| F-02 | IF-DRA-01 多笔支付 | 按 `paymentId` 分组排序 | 集成 | P0 |
| F-03 | IF-DRA-02 无耗材勾选 | `allowed=true` | 单元 | P0 |
| F-04 | IF-DRA-02 耗材已执行 | `EX-DRA-201` | 集成 | P0 |
| F-05 | IF-DRA-03 半退混合真/假 | **假**行不重入药/耗/诊第二段（**BR-DRA-010**） | 集成 | P0 |
| F-06 | IF-DRA-03 全拟退 | 全部进入 `REFUNDING` | 集成 | P0 |
| F-07 | IF-DRA-03 半退 | `paymentSummaries` 含 `halfRefundOnPayment=true` 且 `preservedNewChargeItemIds` 非空 | 集成 | P0 |
| F-08 | IF-DRA-01 半退后刷新 | 源 `SETTLED` 不出现，仅再生 `PLANNED`（**BR-DRA-020**） | 集成 | P0 |

### 7.2 非功能测试

| 编号 | 场景 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- |
| N-01 | IF-DRA-01 响应时延 | ≤1200ms（经验目标，AS §7） | 性能 | P1 |

### 7.3 QueryService一致性

| 编号 | 场景 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- |
| R-01 | 提交后 OutpatientEncounterContextQueryModel 金额摘要 | 异步事件后一致 | 集成 | P1 |

### 7.4 集成测试

| 编号 | 场景 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- | --- |
| I-01 | 端到端：申请成功 → 收费台消费 `paymentSummaries` | 摘要字段与库内 `REFUNDING`/`PLANNED` 一致 | 手工 / E2E | P1 |

### 7.5 测试缺口跟踪

| 编号 | 缺口 | 关闭条件 |
| --- | --- | --- |
| T-GAP-01 | ~~`RegeneratePreservedChargeItemsCommand`~~ 已替换为 **`CreateChargeItemsLinkingRegeneratedAnchorsCommand`**；**ChargeItem 域**与 **DM-ADM-011 §6.1.9a** 已对齐 | **已关闭**（本版） |
| T-GAP-02 | 工程缺少 **DRA_*** 细粒度 errorCode | 常量表补齐并更新 §3 映射 |
| T-GAP-03 | ~~药/耗/诊 **`clone*ForPreservedChargeRegeneration`** 仅占位~~；~~**application** 编排缺口~~ | **已关闭（域 + 应用）**：域三方法已实装（**B.14**）；**`DoctorStationRefundApplicationAppService`** 与 Mapper/契约已落 **`zxplat/health-application`**。（可选）`PreservedLineRegeneratedChargeItemCreatedEvent` 消费方与联调用例另册跟踪。 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.1.2 | 2026-08-01 | **退药/退耗/退诊疗幂等**：§3 EX-DRA-301～303、§5.2、§6.1 与 **AS v1.2.8** / **BR-MRQ-013** 对齐——未完结负向单幂等返回；仅原单已是负向单拒；Mapper `toCreateRefundMedicationOrderCommand(Long, Long)` |
| v1.1.1 | 2026-05-28 | **L3 字段对账**：`RefundableChargeLineItemDto` / `RefundApplicationLineItemDto` / `OutpatientRefundApplicationPaymentSummaryDto` 与 `zxplat` **0 差异**；§2.1 已具名 `ProductDefinitionBatchLoader`（**IF-DRA-01**） |
| v* | 2026-05-28 | 本模块就诊门禁仍为 **`EncounterQueryService#mustGetEncounter`**（**不**经 `EncounterContextFacade`）；事件消费方 **OutpatientEncounterContextQueryModel** 最终一致 |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.1.0 | 2026-05-18 | 对齐 **AS v1.2.2+** 与 `DoctorStationRefundApplicationAppService`：**BR-DRA-020**（`filterOutSupersededSettledLines`）、**BR-DRA-021**（`paymentSummaries` / `OutpatientRefundApplicationPaymentSummaryDto`）；**IF-DRA-01** 项目名称批量解析与 Mapper 三参；**IF-DRA-03** 编排序列 4a～6b、17；§2 依赖与白名单增补。 |
| v1.0.8 | 2026-05-15 | **IF-DRA-01 Mapper 签名与装配**：`toRefundableChargeLineItemDto` 收敛为 `(ChargeItem, PaymentReconciliationQueryModel)`；**BR-DRA-002** / `chargeItemStatusEnum*` 由 Mapper 内 **`EnumUtil.fromCode` + `EnumInterface#getDisplay()`** 装配（**§2.4 / §3.2** 同步；与仓库枚举 API 一致）。 |
| v1.0.7 | 2026-05-15 | **应用层成码对齐仓库**：更新 **§1.1**（`DoctorStationRefundApplicationAppService` 已落地）、**§1.2 事务口径**（与《代码生成通用规范》禁止 App `@Transactional` 对齐）、**§2.1/§2.2/§2.4**（`CurrentOperatorResolver`、`Mapper` 签名、`ChargeItemQueryCommand`）、**§3.2 DTO**（`chargeItemStatusEnumText` / `paymentStatusEnumText`）、**§5.2**、**§6.4**、**§7.5 T-GAP-03** 关闭应用缺口。 |
| v1.0.6 | 2026-05-15 | **域层 IF-DRA-03 `clone*`** 实装对齐 **DM-MED-003 §6.1.16** / **DM-WKF-003 §6.1.11** / **DM-WKF-004 §6.1.17**（**B.14**）；**§1.1、§2.2、§2.4、§3.5、§5.1、§7.5** 去「契约占位」表述；**T-GAP-03** 关闭（域），**application** 编排缺口保留在 **§1.1** 排期。 |
| v1.0.5 | 2026-05-15 | **领域 gap 收敛（本期无 App 成码）**：聚合隔离下 **`createChargeItemsLinkingRegeneratedAnchors`** + `CreateChargeItemsLinkingRegeneratedAnchorsCommand` / 事件码 **8** 与 **DM-ADM-011 §6.1.9a** 对齐；药/耗/诊 **`clone*ForPreservedChargeRegeneration`** 契约占位；**§1.1/§1.2/§2.2/§2.4/§3.5/§5.1/§7.5** 同步；**T-GAP-01** 关闭并增 **T-GAP-03**。 |
| v1.0.4 | 2026-05-15 | **重生成与原单一致（硬）**：§1.2 / §3.4 / §3.5 明确凡**新生成账单行（`ChargeItem`）**及步骤 **7** 牵连之**请求/发放/其它替换根**均须与**源原单**业务事实复刻；§5.1 摘要同步；边界与依赖注入用语改为「保留行账单重生成」。对齐 **AS** **BR-DRA-004**、**DM** **BR-CHG-015**。 |
| v1.0.3 | 2026-05-15 | 保留行领域命名缩短：`reopenPreservedChargeItemsForOutpatientRefundApplication` → `regeneratePreservedChargeItems`；命令 `RegeneratePreservedChargeItemsCommand`；Mapper `toRegeneratePreservedChargeItemsCommand`。 |
| v1.0.2 | 2026-05-15 | 自检修正：**§5.1** `requestOutpatientBillRefund` 对照序号 **19**（原误 **17**）；**§2.4** `getChargeItemIdsByPayments` 仅绑定 **IF-DRA-01**；**§2.2** 去除已移出白名单的 `refundDispensed*` 描述；**IF-DRA-02** 关键校验步骤引用修正；Mapper `toSubmitOutpatientRefundApplicationResponse` 参数名与 Response 字段对齐；**§6.3** 关联步骤泛化为 **§3 读路径（IF-DRA-01/02）**，可重试语义引用 **AS** §5 **EX-DRA-** 系列。 |
| v1.0.1 | 2026-05-15 | **IF-DRA-03** 编排序列：修正 **BR-DRA-010** 脚注步骤号；药品分支拆为 **12～15** 互斥三步并顺延耗材/诊疗/**requestOutpatientBillRefund** 序号；同步 §5～§7 关联步骤引用；移除未列入序列的 `refundDispensed*` 白名单。 |
| v1.0.0 | 2026-05-15 | 首版：对齐 **AS-OP-DOR-004 v1.2**（IF-DRA-01～03）；HTTP 全 **POST**；**BR-DRA-010**；**保留行重开** 目标态方法；§2.3 双锚；`BatchChargeItemIdsCommand` 与现 **whale** `requestOutpatientBillRefund` 对齐。 |

---

## 附录 A · IF-DRA-04 撤销退费（v1.2.0 / 2026-09-06）

> 与 **AS-OP-DOR-004 v1.3.0** **BR-DRA-023～027** 对齐；本附录补全方法白名单与编排序列，供成码对照。

### A.1 应用方法

| IF | 方法 | HTTP |
| --- | --- | --- |
| IF-DRA-04 | `cancelOutpatientRefundApplication` | `POST /api/app/doctorStationRefundApplication/cancelOutpatientRefundApplication` |

### A.2 Request / Response

**Request `CancelOutpatientRefundApplicationRequest`**：`encounterId`；`lines[]`（`paymentId` + `chargeItemId`，须覆盖所选支付下全部 `REFUNDING` 行）。

**Response `CancelOutpatientRefundApplicationResponse`**：`success`；`settledChargeItemCount`。

### A.3 领域写白名单（新增）

| 聚合 | 方法 |
| --- | --- |
| ChargeItem | `cancelOutpatientBillRefund`（`REFUNDING→SETTLED`，事件 `OUTPATIENT_BILL_REFUND_CANCELLED`） |
| MedicationDispense | `voidPendingRefundMedicationDispense`；`restoreDeclinedMedicationDispenseAfterRefundRevoke` |
| MedicationRequest | `voidOpenRefundMedicationOrder` |
| DeviceDispense | `voidPendingRefundDeviceDispense`；`restoreDeclinedDeviceDispenseAfterRefundRevoke` |
| DeviceRequest | `voidOpenRefundDeviceOrder` |
| ServiceRequest | `voidOpenRefundServiceOrder`；`resumeStoppedServiceRequestForOutpatientRefundRevoke` |

### A.4 编排序列（摘要）

1. Gate：行非空、操作人、就诊存在  
2. Pre：行均属就诊且为 `REFUNDING`；支付挂载校验；整单覆盖（EX-DRA-502）；无已退药/已退耗（EX-DRA-501）  
3. Cmd：药/耗/诊对称还原 → `cancelOutpatientBillRefund`  
4. Asm：成功响应  

**诊疗恢复口径**：仅当原申请**无**有效负向退诊疗单且当前为 `STOPPED` 时恢复（对应申请时「未完成 stop」路径）；若存在负向单则仅作废负向单、原完成态不动。

### A.5 IF-DRA-01 变更

候选行状态 = `SETTLED` ∪ `REFUNDING`（**BR-DRA-023**）；`REFUNDED` 仍不返回。与 **BR-DRA-020**（排除被替代 `SETTLED` + 不展示 `REFUNDED`）并存。