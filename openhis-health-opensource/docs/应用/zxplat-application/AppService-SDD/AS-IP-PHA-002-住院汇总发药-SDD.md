# 住院汇总发药 · 应用服务详细设计（SDD）

> 编码：AS-IP-PHA-002-SDD  
> 版本：v1.12 | 日期：2026-10-16 | 作者：（待填）
> 来源契约：`AS-IP-PHA-002-住院汇总发药.md` v1.10 · DM：`DM-MED-002`、`DM-WKF-005`、`DM-WKF-006`、`DM-MED-001`、`DM-ADM-011` · 上游：`AS-IP-NUR-010` v1.20

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院药房工作站 · **汇总发药** Tab（待核发列表、详情、批量核发、批量驳回；打印复用读接口） |
| 实现阶段口径 | **目标态全量契约**（IF-IPS-01～04 后端；IF-IPS-05/06 无独立写接口）；**领域 Gap GAP-IPS-001～005 已关闭**；**应用层已落地** `inpatient/pharmacy/summarydispense`，AppService **`InpatientPharmacySummaryDispenseAppService`**（4 读写接口），HTTP 前缀 **`inpatientPharmacySummaryDispense`** |
| 编排权威 | `AS-IP-PHA-002-住院汇总发药.md` §1.3、§4 |
| 方法权威 | `DM-MED-002` §6.1.12 完成汇总发药、§6.1.11 取消汇总、§6.2.3 按汇总单号查发放行；`DM-WKF-005` §6.1.4/§6.1.5 汇总发放创建与完成；`DM-WKF-006` §6.1 agree/reject；`DM-MED-001` 目录单位槽；库存/追溯 QueryService 同族 **AS-IP-PHA-001** |
| 严格对齐 | IF 编号 **IF-IPS-01～06**，不得增删改号；库存扣减**仅**按 **MedicationDispense** 行；**禁止** SupplyDelivery COMPLETED 触发扣库 |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 护士站汇总保存/发送 | **AS-IP-NUR-010** |
| 逐行住院发药 | **AS-IP-PHA-001** |
| 汇总单撤回 UI | 非目标 |
| ChargeItem 写操作 | **BR-IPS-011** |
| 医保上传 | 非目标 |

**上游/下游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-010` | 上游：发放行 **SUMMARIZED** + Supply **APPROVAL** |
| `AS-IP-PHA-001` | 扣库/追溯/调配药师规则同族 |
| `AS-IP-NUR-010-SDD` | 合并 Supply 行 QueryModel 与单头 DTO 字段口径 |
| `DM-MED-002-SDD` §4.6.3 | `MedicationDispenseSummaryItemQueryModel` 字段真源 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 状态 / 枚举 code | 持久化与比较一律 **String `code`**（`DispenseStatus`、`SupplyStatus`、`SupplyType` 等） |
| `summaryNo` / `busNo` | 同一汇总单业务键 |
| 待核发 | `SupplyStatus.APPROVAL` + type **27** |
| 扣库真相源 | **MedicationDispense** 行 `location_id + lot_number（可空）+ quantity + unit_code`；先 **操作单位→拆零（五类，BR-OPP-017）** 再 **lot+location 合并** 写库存（同 **AS-IP-PHA-001** `applyInventoryDeductionForDispensedLines`） |
| 核发顺序 | **BR-IPS-008**：库存校验 → 扣库 → `completeSummaryDispense` → `createSummarySupplyDelivery` → Delivery **COMPLETED** → `agreeRequest` → 追溯出库 |
| `receiverId` | **IF-IPS-03 必填**；写入 **SupplyDelivery.receiver_id** |
| HTTP | 一律 **`POST`**；Bean **`InpatientPharmacySummaryDispenseAppService`** → 前缀 **`inpatientPharmacySummaryDispense`** |
| 写事务 | **IF-IPS-03/04** 方法级 **`@Transactional(rollbackFor = Exception.class)`**；批量内任一张失败 **整批回滚** |
| 分页 | Request 继承 **`AbstractPageQuery`**；Response 经 **`PagedResultBuilder`** 装配 **`pageNo` 1-based** |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/pharmacy/
  summarydispense/
    service/
      InpatientPharmacySummaryDispenseAppService.java
    request/
      PagePendingMedicationSummarySlipsRequest.java
      GetMedicationSummaryDispenseDetailRequest.java
      DispenseSummaryMedicationsRequest.java
      RejectSummaryDispenseRequest.java
    response/
      PagePendingMedicationSummarySlipsResponse.java
      GetMedicationSummaryDispenseDetailResponse.java
      DispenseSummaryMedicationsResponse.java
      RejectSummaryDispenseResponse.java
    dto/
      InpatientPharmacySummarySlipHeaderDto.java
      InpatientPharmacySummarySlipLineDto.java
      InpatientPharmacySummaryDispenseItemDto.java
      SummaryDispenseTraceLineSubmitDto.java
    assembler/
      InpatientPharmacySummaryDispenseAssembler.java
    mapper/
      InpatientPharmacySummaryDispenseMapper.java
      InpatientPharmacySummaryDispenseMapperImpl.java
```

> 包名：`com.zxplat.domain.application.inpatient.pharmacy.summarydispense`

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径 `operatorId`（**B.6.5**） |
| `CurrentPractitionerResolver` | 核发发药人 `practitionerId` |
| `CurrentManagedLocationIdsResolver` | IF-01 可操作药房 → `purposeLocationIds` 门控 |
| `PageWindowGate` | 分页必填校验（IF-01） |
| `SupplyRequestQueryService` | IF-01 单头分页；IF-02/03/04 按 busNo 强加载 |
| `MedicationDispenseQueryService` | IF-02 发放行；IF-03/04 写前加载 |
| `SupplyDeliveryQueryService` | IF-03 创建 Delivery 后按 requestId 查 deliveryId |
| `InventoryItemQueryService` | IF-03 库存充足校验 `assertSufficientForMedicationDispense` |
| `InventoryItemDomainService` | IF-03 `allocateAndDecrease`（有批号带 `lotNumber`；无批号跨批 FEFO） |
| `TraceNumberRecordQueryService` | IF-03 `assertCodesNotAlreadyOutbound` |
| `TraceNumberDomainService` | IF-03 核发成功后追溯出库 `recordTraceRecords` |
| `MedicationDispenseDomainService` | IF-03 `completeSummaryDispense`；IF-04 `cancelMedicationDispenseSummary` |
| `SupplyDeliveryDomainService` | IF-03 `createSummarySupplyDelivery`、`updateCompletedStatusBatch` |
| `SupplyRequestDomainService` | IF-03 `agreeRequest`；IF-04 `rejectRequest` |
| `ProductDefinitionBatchLoader` | IF-02/03 药品展示与单位换算 |
| `PractitionerQueryService` | IF-01/02 人员名；IF-03 调配药师≠本人 |
| `LocationQueryService` | IF-01/02 药房名称 |
| `PatientQueryService` | IF-02 患者展示名（发放行维度） |
| `EncounterContextReader` | IF-02 就诊/床号展示（批量 `listInpatientEncounterContexts`） |
| `InpatientPharmacySummaryDispenseAssembler` | IF-02 合并行+发放行装配；IF-03 库存合并键 |
| `InpatientPharmacySummaryDispenseMapper` / `MapperImpl` | Request↔Query/Command、Page→Response |

### 2.2 领域方法白名单

| 类型 | 方法 | IF |
| --- | --- | --- |
| 读 | `SupplyRequestQueryService#pageMedicationSummarySlips` | 01、05 |
| 读 | `SupplyRequestQueryService#listMedicationSummarySlipLinesByBusNo` | 02、03、06 |
| 读 | `SupplyRequestQueryService#getSupplyByBusNo` | 03、04 Pre |
| 读 | `MedicationDispenseQueryService#listMedicationDispensesBySummaryNo` | 02、03 Pre |
| 读 | `MedicationDispenseQueryService#listMedicationDispensesByIds` | 03 Pre（写路径实体） |
| 读 | `SupplyDeliveryQueryService#selectByRequestIdList` | 03 Post-create |
| 读 | `InventoryItemQueryService#assertSufficientForMedicationDispense` | 03 Pre |
| 读 | `InventoryItemQueryService#requireInventoryItemIdForMedicationDispenseBatch` | 03 Cmd 扣库 |
| 读 | `TraceNumberRecordQueryService#assertCodesNotAlreadyOutbound` | 03 Pre |
| 写 | `InventoryItemDomainService#allocateAndDecrease`（可选 `lotNumber`） | 03 |
| 写 | `MedicationDispenseDomainService#completeSummaryDispense` | 03 |
| 写 | `SupplyDeliveryDomainService#createSummarySupplyDelivery` | 03 |
| 写 | `SupplyDeliveryDomainService#updateCompletedStatusBatch` | 03 |
| 写 | `SupplyRequestDomainService#agreeRequest` | 03 |
| 写 | `SupplyRequestDomainService#rejectRequest` | 04 |
| 写 | `MedicationDispenseDomainService#cancelMedicationDispenseSummary` | 04 |
| 写 | `TraceNumberDomainService#recordTraceRecords` | 03 Post |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 供应类型 | `com.zxplat.domain.share.enums.SupplyType#MEDICATION_SUMMARY_ORDER` | `DM-WKF-006` §4.1.4 值 27 | IF-01 固定过滤 |
| 供应审批态 | `com.zxplat.domain.share.enums.SupplyStatus#APPROVAL` / `#AGREE` / `#REJECT` | `DM-WKF-006` §2.3 | IF-01 查 APPROVAL；IF-03→AGREE；IF-04→REJECT |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus#SUMMARIZED` / `#COMPLETED` / `#PREPARATION` | `DM-MED-002` §2 状态机 | IF-02/03 读；IF-03 写 COMPLETED；IF-04 回 PREPARATION |
| Delivery 完成态 | `DispenseStatus#PREPARATION` → `#COMPLETED` | `DM-WKF-005` §2.3 汇总路径 | IF-03 create + updateCompletedStatusBatch |
| 物品表 | `com.zxplat.domain.share.constants.TableNameConstants#MED_MEDICATION_DEFINITION` | `DM-WKF-005` §3 `item_table` | createSummarySupplyDelivery |
| 追溯方向 | `com.zxplat.domain.share.enums.TraceDirection#OUT` | 追溯码 DM 出库语义 | IF-03 recordTraceRecords |
| 业务异常码 | `com.zxplat.domain.share.constants.InpatientPharmacySummaryDispenseConstants.EX_IPS_*` | `AS-IP-PHA-002` §5 | **已落地**（`EX_IPS_101`～`EX_IPS_205`） |
| 批上限 | `InpatientPharmacySummaryDispenseConstants.SUMMARY_DISPENSE_BATCH_MAX`（50） | `AS-IP-PHA-002` §7 NFR | IF-03/04 |
| 列表审批态展示 | `PagePendingMedicationSummarySlipsResponse.rows[].supplyStatusEnumText` | `DM-WKF-006` status_enum | EnumUtil → getInfo() |
| 领药人 | `DispenseSummaryMedicationsRequest.receiverId` | `DM-WKF-005` §3 `receiver_id` | IF-03 必填 |
| 调配药师 | `DispenseSummaryMedicationsRequest.preparerPractitionerId` | **BR-IPS-005** / **BR-IPP-004** | IF-03 必填且≠发药人 |
| 汇总行患者 | `com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel#patientId` | `DM-WKF-006` §3 `patient_id` | IF-03 `createSummarySupplyDelivery` 装配 |

### 2.4 领域层能力核对（非 Gap）

> 领域 Query/写能力 **GAP-IPS-001～003 已关闭**（**AS §3.2**）。**GAP-IPS-004** 由本 SDD IF-01 固定 `statusEnum=APPROVAL` 关闭。**GAP-IPS-005** 由 §3.4 Assembler 关闭。**GAP-IPS-006** 应用层成码已闭环（**AS §3.2**）。

**已就绪域能力**：`pageMedicationSummarySlips`、`listMedicationSummarySlipLinesByBusNo`、`listMedicationDispensesBySummaryNo`（含扣库字段）；`completeSummaryDispense`、`cancelMedicationDispenseSummary`、`createSummarySupplyDelivery`、`updateCompletedStatusBatch`、`agreeRequest`、`rejectRequest`。

**本 SDD 成码交付（v1.1 已闭环）**：`InpatientPharmacySummaryDispenseAppService` 四接口；`InpatientPharmacySummaryDispenseAssembler`；Request/Response/DTO/Mapper（含 `MapperImpl`）；`InpatientPharmacySummaryDispenseConstants`（`zxplat-domain`）；`SupplyRequestModel#patientId` 读模型已补齐供 `createSummarySupplyDelivery` 装配。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IPS-01 | `pagePendingMedicationSummarySlips` | 分页查询待核发汇总单头 | `POST /api/app/inpatientPharmacySummaryDispense/pagePendingMedicationSummarySlips` |
| IF-IPS-02 | `getMedicationSummaryDispenseDetail` | 查询汇总单详情 | `POST /api/app/inpatientPharmacySummaryDispense/getMedicationSummaryDispenseDetail` |
| IF-IPS-03 | `dispenseSummaryMedications` | 批量汇总核发 | `POST /api/app/inpatientPharmacySummaryDispense/dispenseSummaryMedications` |
| IF-IPS-04 | `rejectSummaryDispense` | 批量汇总驳回 | `POST /api/app/inpatientPharmacySummaryDispense/rejectSummaryDispense` |
| IF-IPS-05 | — | 打印待核发列表 | **无独立后端方法**；前端复用 IF-IPS-01 |
| IF-IPS-06 | — | 打印汇总单 | **无独立后端方法**；前端复用 IF-IPS-02 |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 登录、批上限、必填参数 | 抛 **`EX-IPS-*`**，不进入 Pre |
| **Pre** | QueryService 只读、状态/BR 门禁、库存预校验 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 扣库 → 发放完成 → Delivery 创建/完成 → Supply AGREE / REJECT + 释放发放行 | 同事务；失败整批回滚 |
| **Asm** | Mapper/Assembler 纯 DTO 装配 | — |

记法：`SupplyRequestQueryService#pageMedicationSummarySlips` 表示注入 Bean 调用。

### 3.2 IF-IPS-01 `pagePendingMedicationSummarySlips`（分页查询汇总单头：待核对 / 已发药）

**HTTP**：`POST /api/app/inpatientPharmacySummaryDispense/pagePendingMedicationSummarySlips`

**Request**（继承 `AbstractPageQuery`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | `Integer` | Y | 1-based |
| `pageSize` | `Integer` | Y | 分页大小 |
| `keyword` | `String` | N | 汇总单号关键字 |
| `startDate` | `LocalDateTime` | N | 申请时间下界 |
| `endDate` | `LocalDateTime` | N | 申请时间上界 |
| `purposeLocationId` | `Long` | N | 发放药房；0 或空表示当前用户可见全部可操作药房 |
| `summaryDispenseCompletedOnly` | `Boolean` | N | **true**=已发药 Tab（发放表 **COMPLETED**）；false/空=待发核对（**SUMMARIZED**） |

**Response**（继承 `PagedResult<InpatientPharmacySummarySlipHeaderDto>`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageNo` | `Integer` | 当前页（1-based，父类） |
| `pageSize` | `Integer` | 页大小（父类） |
| `totalCount` | `Long` | 总单数（父类） |
| `content` / `rows` | `List<InpatientPharmacySummarySlipHeaderDto>` | 汇总单头行（实现可选 `rows` 别名，须与 `content` 同源） |

**DTO：`InpatientPharmacySummarySlipHeaderDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | `String` | 汇总单号 |
| `applyTime` | `LocalDateTime` | 护士站发送时间 |
| `dispenseTime` | `LocalDateTime` | 药房核发时间（未核发为空） |
| `purposeLocationId` | `Long` | 发放药房 id |
| `purposeLocationName` | `String` | 药房名称 |
| `applicantId` | `Long` | 申请人（护士） |
| `applicantName` | `String` | 申请人姓名 |
| `applicantDepartmentName` | `String` | 申领科室（病区主挂靠科室；**IF-IPS-02 详情回填**，与 **BR-IP-SUM-020** 同源） |
| `receiverId` | `Long` | 领药人（核发后才有值） |
| `receiverName` | `String` | 领药人姓名 |
| `supplyStatusEnum` | `String` | 审批态 code（**APPROVAL** 待核对 / **AGREE** 已核发，仅展示） |
| `supplyStatusEnumText` | `String` | 审批态展示 |
| `summaryDispenseCompleted` | `Boolean` | 是否已发药（发放表同号行全部 **COMPLETED**） |
| `summaryDispenseStatusText` | `String` | 门控展示：待核对 / 已发药 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IPS-001** | 固定 `typeEnum=27`；先按发放表 **`listSummaryNosWhereAllDispenseRowsHaveStatus`** 收窄 `busNoIn`（待发 **SUMMARIZED** / 已发药 **COMPLETED**）；待发另固定 Supply **APPROVAL**（服务端写死）；不含护士站 **PENDING_APPROVAL** 待发送单；解析出的 `purposeLocationIds` **非空**时 `purpose_location_id IN (...)`；**空且非 strict** 时不拼位置谓词（与 **IF-IP-SUM-02** 一致）；**空且 strict** → **EX-IPS-101** |
| **BR-IPS-013** | 前端汇总 Tab：**待核对** / **已发药** 切换；已发药详情只读 |
| **BR-IPS-011** | 只读 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IPS-101** | 未登录或无可操作药房 | 未登录或无权操作该药房 |
| **EX-IPS-102** | 查询异常 | 加载失败，请稍后重试 |

**Mapper 接口方法签名**

- `toSupplyRequestDocumentListQuery(PagePendingMedicationSummarySlipsRequest request, List<Long> purposeLocationIds): SupplyRequestDocumentListQuery`
- `toSummarySlipHeaderDtos(Page<MedicationSummarySlipQueryModel> page): List<InpatientPharmacySummarySlipHeaderDto>`
- `toPagePendingMedicationSummarySlipsResponse(Page<MedicationSummarySlipQueryModel> page, List<InpatientPharmacySummarySlipHeaderDto> rows): PagePendingMedicationSummarySlipsResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `PageWindowGate#requirePositivePageWindow` | `pageNo`/`pageSize` | 与 AS 分页契约一致 |
| 2 | Gate | `CurrentManagedLocationIdsResolver#listManagedActivePharmaciesOrEmpty` | — | 解析可操作药房 id 集合；**empty 且 strict** → **EX-IPS-101**；**empty 且非 strict** → 继续、Mapper **不**写 `purposeLocationIds` |
| 3 | Pre | App 门控 | `purposeLocationId` | 非 0 且非空时须在②集合内（② 非空时）；否则 **EX-IPS-101** |
| 3a | Pre | `MedicationDispenseQueryService#listSummaryNosWhereAllDispenseRowsHaveStatus` | ② ids + **SUMMARIZED**/**COMPLETED** | 发放表门控收窄 `busNoIn`；空则直接空页 |
| 4 | Pre | `Mapper#toSupplyRequestDocumentListQuery` | Request + ② ids | 固定 **`typeEnum=MEDICATION_SUMMARY_ORDER`**；待发 **`statusEnum=APPROVAL`**（写死）；已发药不设 Supply 态；写入 ③a `busNoIn` |
| 5 | Pre | `MedicationSummarySlipQuerySupport#pageMedicationSummarySlips` | ④ Query | **BR-IPS-001** |
| 6 | Asm | `Mapper#toSummarySlipHeaderDtos` + `enrichSummarySlipHeadersWithDispenseGate` | ⑤ | 展示字段 + `summaryDispenseCompleted` |
| 7 | Asm | `Mapper#toPagePendingMedicationSummarySlipsResponse` | ⑤⑥ | **`PagedResultBuilder`** |
| — | — | — | — | **仅读路径** |

### 3.3 IF-IPS-02 `getMedicationSummaryDispenseDetail`（查询汇总单详情）

**HTTP**：`POST /api/app/inpatientPharmacySummaryDispense/getMedicationSummaryDispenseDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `summaryNo` | `String` | Y | 汇总单号 / busNo |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summaryNo` | `String` | 单号 |
| `header` | `InpatientPharmacySummarySlipHeaderDto` | 单头（字段同 IF-IPS-01） |
| `mergedLines` | `List<InpatientPharmacySummarySlipLineDto>` | 合并 Supply 行；**无 lotNumber** |
| `dispenseItems` | `List<InpatientPharmacySummaryDispenseItemDto>` | 底层发放行（含批号/扣库字段） |
| `summaryDispenseCompleted` | `Boolean` | 是否已发药（发放表同号行全部 **COMPLETED**） |

**DTO：`InpatientPharmacySummarySlipLineDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `supplyRequestId` | `Long` | 供应请求行 id |
| `itemId` | `Long` | 药品定义 id |
| `medicationName` | `String` | 药品名称 |
| `specificationText` | `String` | 规格 |
| `itemQuantity` | `BigDecimal` | 拆零汇总数量（持久化；**禁止**前端直接拼界面数量） |
| `unitCode` | `String` | 拆零单位 code |
| `unitCodeText` | `String` | 拆零单位展示 |
| `displayQuantityText` | `String` | 界面数量文案（**BR-IP-SUM-015** / **BR-IPS-012**）：拆零数量 + 单位直显，如 `15支`；禁止满包+余量 |
| `manufacturerName` | `String` | 生产厂家 |
| `purposeLocationName` | `String` | 发放药房（与单头一致） |
| `unitPrice` | `BigDecimal` | 账单单价（**BR-IP-SUM-009** 同族） |
| `totalPrice` | `BigDecimal` | 组内 ChargeItem.total_price 累加 |
| `packageUnitCode` | `String` | 包装单位码（待扫换算辅助） |
| `packageUnitRatio` | `BigDecimal` | 包装相对拆零倍数 |
| `splitUnitCode` | `String` | 拆零单位码 |
| `traceUnitCode` | `String` | 追溯码单位：主档优先，无主档回落药典 |
| `traceUnitCodeText` | `String` | 追溯码单位展示文案（扫码弹窗「追溯码单位」列） |
| `traceUnitRatio` | `BigDecimal` | 追溯码单位→拆零比例：主档优先否则药典（1 追溯码单位 = N 拆零；弹窗「码倍」） |
| `lotHasTraceMaster` | `Boolean` | 主表该品批是否已有码 |
| `representativeLotNumber` | `String` | 代表批号（待扫换算/匹配；合并行禁止输出多批号列表） |

**DTO：`InpatientPharmacySummaryDispenseItemDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dispenseId` | `Long` | 发放行主键 |
| `statusEnum` | `String` | 发放状态 code |
| `statusEnumText` | `String` | 状态展示 |
| `medicationId` | `Long` | 药品 id |
| `medicationName` | `String` | 药品名称 |
| `specificationText` | `String` | 规格 |
| `locationId` | `Long` | 发放药房/库位 |
| `locationName` | `String` | 库位名称 |
| `lotNumber` | `String` | 批号（可空） |
| `quantity` | `BigDecimal` | 数量 |
| `unitCode` | `String` | 单位 code |
| `unitCodeText` | `String` | 单位展示 |
| `encounterId` | `Long` | 就诊 id |
| `patientId` | `Long` | 患者 id |
| `patientName` | `String` | 患者姓名 |
| `bedDisplay` | `String` | 床号展示 |
| `traceNo` | `String` | 已持久化追溯码（可空；供核对） |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IPS-002** | 读路径：存在 type 27 Supply 行（中转展示）；发放行同号且态统一为 **SUMMARIZED**（待核对）或 **COMPLETED**（已发药）；待发详情另断言 Supply **APPROVAL**；**不以 Supply AGREE 判定已发药** |
| **BR-IPS-011** | ChargeItem 只读（价格在合并行由域 Query 已累加） |
| **BR-IPS-012** | 合并行写入 **`displayQuantityText`**（`MedicationPackageSplitQuantityDisplayAssembler#buildPlainQuantityText`，**BR-IP-SUM-015**）、`manufacturerName`、`purposeLocationName`；**禁止**输出 `lotNumber` |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IPS-103** | 单号无效或状态不可读 | 汇总单不存在或已失效，请刷新 |
| **EX-IPS-102** | 其他异常 | 加载失败，请稍后重试 |

**Mapper 接口方法签名**

- `toGetMedicationSummaryDispenseDetailResponse(String summaryNo, InpatientPharmacySummarySlipHeaderDto header, List<InpatientPharmacySummarySlipLineDto> mergedLines, List<InpatientPharmacySummaryDispenseItemDto> dispenseItems): GetMedicationSummaryDispenseDetailResponse`

**Assembler 方法签名**

- `buildMergedLineDtos(List<MedicationSummarySlipLineQueryModel> supplyLines, Map<String, ProductDefinitionDisplayDto> productByKey, String purposeLocationName): List<InpatientPharmacySummarySlipLineDto>`
- `buildDispenseItemDtos(List<MedicationDispenseSummaryItemQueryModel> dispenseRows, Map<String, ProductDefinitionDisplayDto> productByKey, Map<Long, String> patientNameById, Map<Long, String> bedDisplayByEncounterId, Map<Long, String> locationNameById): List<InpatientPharmacySummaryDispenseItemDto>`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `summaryNo` 非空 | — |
| 2 | Pre | `SupplyRequestQueryService#getSupplyByBusNo` | `summaryNo` | 过滤 type 27；空 → **EX-IPS-103** |
| 3 | Pre | `listMedicationDispensesBySummaryNo` + `assertDispenseRowsReadable` | `summaryNo` | 同号发放行态统一 **SUMMARIZED** 或 **COMPLETED**；否则 **EX-IPS-103** |
| 3a | Pre | `assertAllSupplyLinesApproval` | ② | 仅当发放行全部 **SUMMARIZED** 时执行；Supply 非 **APPROVAL** → **EX-IPS-103** |
| 4 | Pre | `listMedicationSummarySlipLinesByBusNo` | `summaryNo` | 合并 Supply 行 |
| 5 | Pre | `enrichSummarySlipHeadersWithDispenseGate` / `applySummaryDispenseGateToHeader` | 发放行 | 回填 `summaryDispenseCompleted`、`summaryDispenseStatusText`、`dispenseTime` |
| 6 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | ④ medicationId | 名称/规格/单位 |
| 7 | Pre | 批量 Patient/Encounter/Location | 发放行 patientId/encounterId/locationId | 展示 |
| 8 | Pre | 单头装配 | ② + IF-01 同构逻辑 | `header`（含 `purposeLocationName`） |
| 9 | Asm | `Assembler#buildMergedLineDtos` | ④⑥⑧ `purposeLocationName` | **禁止** lotNumber；`displayQuantityText`；追溯码仅 **COMPLETED** 时读 Delivery |
| 10 | Asm | `Assembler#buildDispenseItemDtos` | 发放行⑥⑦ | 含批号；不含追溯码 |
| 11 | Asm | `Mapper#toGetMedicationSummaryDispenseDetailResponse` + `summaryDispenseCompleted` | ⑨⑩⑧ | — |
| — | — | — | — | **仅读路径** |

**前端（汇总单明细表 · 对齐按患者发药药品列）**

| 列 | 数据源 | 说明 |
| --- | --- | --- |
| 项目名称 | `medicationName` | 同患者发药 `itemName` |
| 数量 | `displayQuantityText` | 优先后端文案；回退 `itemQuantity`+`unitCodeText` |
| 规格 | `specificationText` | — |
| 批次号 | — | 合并层固定 `—`（**BR-IPS-012**） |
| 金额 | `totalPrice` | — |
| 发放库房 | `purposeLocationName` | 行或单头 |
| 追溯码 | `traceNo` / 扫码暂存 | 合并 Supply 行；**落库 `wkf_supply_delivery.trace_no`**；**dispenseItems 不含追溯码** |
| 厂家 | `manufacturerName` | 产品目录 |

### 3.4 IF-IPS-03 `dispenseSummaryMedications`（批量汇总核发）

**HTTP**：`POST /api/app/inpatientPharmacySummaryDispense/dispenseSummaryMedications`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `summaryNos` | `List<String>` | Y | 待核发汇总单号；1～50 |
| `receiverId` | `Long` | Y | 领药人（**BR-IPS-003**） |
| `preparerPractitionerId` | `Long` | Y | 调配药师（**BR-IPS-005**） |
| `traceLines` | `List<SummaryDispenseTraceLineSubmitDto>` | N | 核发时补录追溯码（**按供应行**，落 SupplyDelivery） |

**DTO：`SummaryDispenseTraceLineSubmitDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `supplyRequestId` | `Long` | Y | **供应请求行** id（**非** dispenseId） |
| `traceNo` | `String` | N | 追溯码串（逗号分隔，同 **AS-IP-PHA-001** 解析） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summaryNos` | `List<String>` | 已成功核发的单号 |
| `message` | `String` | 成功提示 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IPS-002/010** | 每张单 Supply **APPROVAL**；发放行全 **SUMMARIZED** |
| **BR-IPS-003** | `receiverId` 非空 |
| **BR-IPS-004** | 追溯码未出库冲突 |
| **BR-IPS-005** | 调配药师非空且 ≠ 当前发药药师 |
| **BR-IPS-006** | 扣库按发放行 lot+location 合并 |
| **BR-IPS-007** | **不对** SupplyDelivery 扣库 |
| **BR-IPS-008** | Cmd 顺序见编排序列 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IPS-101** | 未登录/无发药人主档 | 未登录或无权操作该药房 |
| **EX-IPS-103** | 单号无效 | 汇总单不存在或已失效，请刷新 |
| **EX-IPS-201** | 非 APPROVAL 或已核发 | 汇总单已核发或状态不允许，请刷新 |
| **EX-IPS-203** | 库存不足 | 按品种/批号提示库存不足 |
| **EX-IPS-204** | 追溯/调配药师 | 追溯码冲突或请选择有效调配药师 |
| **EX-IPS-205** | 其他域失败 | 核发失败，请检查汇总单状态 |

**Mapper 接口方法签名**

- `toCompleteSummaryDispenseCommand(String summaryNo, Long operatorId, Long practitionerId, Long preparerId): CompleteSummaryDispenseCommand`
- `toCreateSummarySupplyDeliveryCommand(Long receiverId, Long operatorId, List<SupplyRequestModel> supplyLines, Map<Long, String> traceBySupplyRequestId): CreateSummarySupplyDeliveryCommand`
- `toUpdateCompletedStatusBatchCommand(List<Long> deliveryIds, Long practitionerId, Long operatorId, LocalDateTime completedTime): UpdateCompletedStatusBatchCommand`
- `toAgreeRequestCommand(String busNo, Long approverId, Long operatorId): AgreeRequestCommand`
- `torecordTraceRecordsCommand(List<SupplyRequestModel> supplyLines, Map<Long, String> traceBySupplyRequestId, String summaryNo, Long operatorId): recordTraceRecordsCommand`
- `toDispenseSummaryMedicationsResponse(List<String> summaryNos): DispenseSummaryMedicationsResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `summaryNos` 1～50；`receiverId`/`preparerPractitionerId` 非空 | 超上限 → **EX-IPS-205** |
| 2 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | **EX-IPS-101** |
| 3 | Gate | `CurrentPractitionerResolver#requirePractitionerId` | 发药人 | **EX-IPS-101** |
| 4 | Gate | `PractitionerQueryService#findById` | `preparerPractitionerId` | 不存在 → **EX-IPS-204** |
| 5 | Gate | App 校验 | preparerId ≠ 发药人 practitionerId | **BR-IPS-005** → **EX-IPS-204** |
| 6 | Cmd | **循环每个 `summaryNo`**（以下 6a～6k 为单张单；全部在同一事务） | — | 任一张失败整批回滚 |
| 6a | Pre | `getSupplyByBusNo` + type 27 | summaryNo | 空 → **EX-IPS-103** |
| 6b | Pre | 状态门控 | 6a | 须全 **APPROVAL**；否则 **EX-IPS-201** |
| 6c | Pre | `listMedicationDispensesBySummaryNo` + `listMedicationDispensesByIds` | summaryNo | 强加载实体；须全 **SUMMARIZED**；否则 **EX-IPS-201** |
| 6d | Pre | 汇总追溯码索引 | Request.traceLines → supplyRequestId | **禁止**合并到 MedicationDispense；校验 OUT/在库 |
| 6e | Pre | `TraceNumberRecordQueryService#assertCodesNotAlreadyOutbound` | 6d 全部码 | **BR-IPS-004** → **EX-IPS-204** |
| 6f | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 6c medicationIds | 单位换算源 |
| 6g | Pre | **循环每条发放行** `assertSufficientForMedicationDispense` | 6c 行 | **BR-IPS-006** 预校验 → **EX-IPS-203** |
| 6h | Cmd | App 内联扣库（同 **AS-IP-PHA-001** 合并逻辑） | 6c + 6f | **`InventoryItemDomainService#allocateAndDecrease`**（有批号同批拆分）；**写库点 ①** |
| 6i | Cmd | `MedicationDispenseDomainService#completeSummaryDispense(CompleteSummaryDispenseCommand)` | summaryNo + 发药人 + 调配药师 | **SUMMARIZED→COMPLETED**；写 preparer/dispenser/时刻；**写库点 ②**（发放表终态真源） |
| 6j | Cmd | `SupplyDeliveryDomainService#createSummarySupplyDelivery(CreateSummarySupplyDeliveryCommand)` | 6a 行 + receiverId | 首态 **PREPARATION**；**写库点 ③** |
| 6k | Pre | `SupplyDeliveryQueryService#selectByRequestIdList` | 6a supplyRequestIds | 取新建 deliveryIds |
| 6l | Cmd | `SupplyDeliveryDomainService#updateCompletedStatusBatch(UpdateCompletedStatusBatchCommand)` | 6k ids | **PREPARATION→COMPLETED**；**写库点 ④** |
| 6m | Cmd | `SupplyRequestDomainService#agreeRequest(AgreeRequestCommand)` | busNo=summaryNo | **APPROVAL→AGREE**；**写库点 ⑤** |
| 6n | Cmd | `TraceNumberDomainService#recordTraceRecords(recordTraceRecordsCommand)` | 6a + 6d | 追溯 OUT（SupplyDelivery 维度）；**写库点 ⑥** |
| 7 | Asm | `Mapper#toDispenseSummaryMedicationsResponse` | 成功 summaryNos | — |

> **库存扣库实现说明**：步骤 6h 在 AppService 内按发放行 **`unit_code` 操作单位→拆零**（`MedicationProductQuantityToSplitUnitConverter`，**BR-IPS-006** / **BR-OPP-017**）后，再按 `medicationId+locationId+lotNumber` 合并调用库存域服务；**禁止**对 SupplyDelivery 行扣库（**BR-IPS-007**）。逻辑与 `InpatientPharmacyDispenseAppService#applyInventoryDeductionForDispensedLines` 同族，可内联复制（**B.5.1** 单 IF 专用，不强制提取 common）。

> **createSummarySupplyDelivery 装配**：每个 `SupplyRequestModel` 行映射一条 Delivery item：`supplyRequestId=id`，`summaryId=summaryId`，`patientId`，`itemId`，`quantity=itemQuantity`，`unitCode`，`lotNumber=null`，`receiverId=Request.receiverId`，**`traceNo=traceBySupplyRequestId.get(id)`**（汇总追溯码真源，**不**写 MedicationDispense）。

### 3.5 IF-IPS-04 `rejectSummaryDispense`（批量汇总驳回）

**HTTP**：`POST /api/app/inpatientPharmacySummaryDispense/rejectSummaryDispense`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `summaryNos` | `List<String>` | Y | 待驳回汇总单号；1～50 |
| `rejectReasonCode` | `String` | N | 驳回原因 code（落 SupplyRequest.reason/changeReason） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summaryNos` | `List<String>` | 已驳回单号 |
| `message` | `String` | 成功提示 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IPS-103** | 单号不存在 | 汇总单不存在或已失效，请刷新 |
| **EX-IPS-202** | 状态不允许 | 当前状态无法驳回，请刷新 |
| **EX-IPS-205** | 域拒绝 | 驳回失败，请检查汇总单状态 |

**Mapper 接口方法签名**

- `toRejectRequestCommand(String busNo, Long approverId, Long operatorId, String rejectReasonCode): RejectRequestCommand`
- `toCancelMedicationDispenseSummaryCommand(String summaryNo, Long operatorId): CancelMedicationDispenseSummaryCommand`
- `toRejectSummaryDispenseResponse(List<String> summaryNos): RejectSummaryDispenseResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `summaryNos` 1～50 非空 | — |
| 2 | Gate | `CurrentOperatorResolver` + `CurrentPractitionerResolver` | 操作人/审批人 | — |
| 3 | Cmd | **循环每个 summaryNo** | — | 同事务 |
| 3a | Pre | `getSupplyByBusNo` + type 27 | summaryNo | **EX-IPS-103** |
| 3b | Pre | 状态门控 | 3a | 须 **APPROVAL**；否则 **EX-IPS-202** |
| 3c | Cmd | `SupplyRequestDomainService#rejectRequest(RejectRequestCommand)` | busNo | **APPROVAL→REJECT**；**写库点 ①** |
| 3d | Cmd | `MedicationDispenseDomainService#cancelMedicationDispenseSummary(CancelMedicationDispenseSummaryCommand)` | summaryNo | **SUMMARIZED→PREPARATION**，清 summary_no；**写库点 ②** |
| 4 | Asm | `Mapper#toRejectSummaryDispenseResponse` | ③ 列表 | **不创建** SupplyDelivery |

### 3.6 IF-IPS-05 / IF-IPS-06（打印）

| IF | 约定 |
| --- | --- |
| IF-IPS-05 | 前端复用 IF-IPS-01 数据或再次调用 `pagePendingMedicationSummarySlips`；**无写操作** |
| IF-IPS-06 | 药房汇总工作台「打印汇总单」：按 `summaryNo` 客户端打印汇总领药单（与护士站 IF-IP-SUM-07 同源模板；待核发/已发药均可）；**无写操作** |

## 4. QueryService 与领域事件

| 项 | 约定 |
| --- | --- |
| 读侧 | 一律经 **DM §6.2 QueryService**；禁止 App 直查 Repository |
| IF-IPS-01 | 先 **`listSummaryNosWhereAllDispenseRowsHaveStatus`**（待发 **SUMMARIZED** / 已发药 **COMPLETED**）写入 `busNoIn`；待发另固定 Supply **APPROVAL**；不含 **REJECT** / **PENDING_APPROVAL** |
| IF-IPS-02 | 合并行来自 **SupplyRequest**；扣库明细来自 **MedicationDispense**（**GAP-IPS-001 已关闭**） |
| 事件（IF-IPS-03） | `MedicationDispenseSummaryCompletedEvent`；`SupplyDeliveryCreatedEvent` + `SupplyDeliveryCompletedEvent`；`SupplyRequestAgreedEvent`；追溯 `TraceNumberRecordAppendedEvent` |
| 事件（IF-IPS-04） | `SupplyRequestRejectedEvent`；`MedicationDispenseSummaryCancelledEvent` |
| 读侧刷新 | 护士站汇总 Tab / 本 Tab 列表：**最终一致**，用户查询刷新即可 |
| ChargeItem | 全 Tab **只读**；核发**不**写账单 |

```text
IF-IPS-03：扣库(med_dispense) → completeSummaryDispense → createSummarySupplyDelivery → updateCompleted → agreeRequest → trace OUT
IF-IPS-04：rejectRequest → cancelMedicationDispenseSummary（无 Delivery）
```

## 5. 写库顺序（核心写接口视角）

| 顺序 | IF | App 方法 | Cmd 序号 | 领域调用 | 聚合写入语义 |
| --- | --- | --- | --- | --- | --- |
| 1 | IF-IPS-03 | `dispenseSummaryMedications` | 6h | 库存扣减 | `wkf_inventory_item` 减 |
| 2 | IF-IPS-03 | 同上 | 6i | `completeSummaryDispense` | `med_medication_dispense` → **COMPLETED** |
| 3 | IF-IPS-03 | 同上 | 6j | `createSummarySupplyDelivery` | `wkf_supply_delivery` 插入 **PREPARATION** |
| 4 | IF-IPS-03 | 同上 | 6l | `updateCompletedStatusBatch` | Delivery → **COMPLETED** |
| 5 | IF-IPS-03 | 同上 | 6m | `agreeRequest` | `wkf_supply_request` → **AGREE** |
| 6 | IF-IPS-03 | 同上 | 6n | `recordTraceRecords` | 汇总 SupplyDelivery 追溯 OUT |
| 7 | IF-IPS-04 | `rejectSummaryDispense` | 3c | `rejectRequest` | Supply → **REJECT** |
| 8 | IF-IPS-04 | 同上 | 3d | `cancelMedicationDispenseSummary` | 发放行 → **PREPARATION**，清 summary_no |

**事务边界**

| 项 | 约定 |
| --- | --- |
| IF-IPS-03 | 单方法 **`@Transactional`**；步骤 6a～6n 全批 summaryNos 同一事务 |
| IF-IPS-04 | 单方法 **`@Transactional`**；步骤 3a～3d 全批同一事务 |
| 失败回滚 | Pre/Gate 抛异常不写库；Cmd 任一步失败 **整批回滚**（含已循环的前几张单） |
| 幂等 | 重复核发依赖 **APPROVAL** 门控 → **EX-IPS-201**；已 AGREE 不可再核发 |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 重复核发 | 非 APPROVAL 拒绝 **EX-IPS-201** | `InpatientPharmacySummaryDispenseAppService#dispenseSummaryMedications` 步骤 6b | §3.4 序号 6b |
| 重复驳回 | 非 APPROVAL 拒绝 **EX-IPS-202** | `#rejectSummaryDispense` 步骤 3b | §3.5 序号 3b |

### 6.2 并发控制

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 双窗口同单核发 | 先成功者 AGREE；后者 **EX-IPS-201** | `#dispenseSummaryMedications` 步骤 6b | §3.4 序号 6b |
| 乐观门控 | 发放行须 **SUMMARIZED**；Supply 须 **APPROVAL** | 步骤 6b/6c | §3.4 |

### 6.3 重试与容错

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 读接口 | **EX-IPS-102** 可重试 | IF-01/02 | §3.2～3.3 |
| 写接口 | 库存/域失败映射 **EX-IPS-203/205** | IF-03/04 | §3.4～3.5 |

### 6.4 数据脱敏

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 患者信息 | 仅返回详情所需展示字段 | `Assembler#buildDispenseItemDtos` | §3.3 序号 10 |

### 6.5 审计日志

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 核发审计 | operatorId、发药人、调配药师、receiverId、summaryNos | Command `operatorId`/`practitionerId`；Delivery.receiverId | §3.4 序号 6i～6m |
| 驳回审计 | operatorId、approverId、summaryNos | Reject/Agree Command | §3.5 序号 3c |

## 7. 测试要点

### 7.1 功能测试

| 用例 ID | 场景 | IF | 优先级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| TC-IPS-01-001 | 护士站发送后列表可见 APPROVAL 单 | 01 | P0 | 集成 |
| TC-IPS-01-002 | 药房/时段/关键字筛选 | 01 | P0 | 集成 |
| TC-IPS-02-001 | 合并行无批号 + 发放行含 lot/quantity | 02 | P0 | 集成 |
| TC-IPS-02-002 | 合并行 `displayQuantityText`：15 拆零→`15支`；禁止 `1盒5支`；含厂家/药房列；金额只读 | 02 | P0 | 集成/前端 |
| TC-IPS-03-001 | 单张核发：扣库+COMPLETED+Delivery+AGREE | 03 | P0 | 集成 |
| TC-IPS-03-002 | 同 lot+location 多行合并扣库 | 03 | P0 | 集成 |
| TC-IPS-03-003 | 无 lot 走 allocateAndDecrease | 03 | P0 | 集成 |
| TC-IPS-03-004 | receiverId 写入 Delivery | 03 | P0 | 集成 |
| TC-IPS-03-005 | 调配药师同人拒绝 **EX-IPS-204** | 03 | P0 | 集成 |
| TC-IPS-03-006 | 批量 2 张 summaryNo 同事务 | 03 | P1 | 集成 |
| TC-IPS-03-007 | 重复核发 **EX-IPS-201** | 03 | P0 | 集成 |
| TC-IPS-04-001 | 驳回后 REJECT + 发放行回 PREPARATION | 04 | P0 | 集成 |
| TC-IPS-04-002 | 非 APPROVAL 驳回 **EX-IPS-202** | 04 | P0 | 集成 |

### 7.2 非功能测试

| 用例 ID | 场景 | 优先级 |
| --- | --- | --- |
| TC-IPS-NFR-001 | 50 张批量核发性能 | P1 |
| TC-IPS-NFR-002 | 并发双窗口同 summaryNo 核发 | P0 |

### 7.3 查询结果一致性

| 项 | 验证 |
| --- | --- |
| IF-02 mergedLines.totalPrice | 与护士站 IF-IP-SUM-03 同口径 |
| IF-02 displayQuantityText | 15 拆零 → `15支`；禁止满包+余量；与 **BR-IP-SUM-015** 一致 |
| IF-02 dispenseItems | 与 `listMedicationDispensesBySummaryNo` 字段一致 |
| IF-03 后 | ChargeItem 条数与金额不变 |

### 7.4 集成测试

| 项 | 验证 |
| --- | --- |
| 与 AS-IP-NUR-010 | 发送后 IF-IPS-01 可见；驳回后护士站待汇总可再勾选 |
| 与 AS-IP-PHA-001 | 扣库/追溯规则同族；**不对 Delivery 扣库** |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| E2E 打印 | IF-IPS-05/06 前端模板，后端无独立用例 |
| 追溯出库明细 | 依赖追溯码主数据配置，联调阶段补 E2E |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.12 | 2026-10-16 | **IF-IPS-02/06**：详情回填 `applicantDepartmentName`；合并行按 `pyStr` 首字母序；对齐 **AS v1.10** / **BR-IP-SUM-020**（代码先行回写） |
| v1.11 | 2026-10-14 | **IF-IPS-03**：有批号扣库改 **`allocateAndDecrease(lotNumber)`** 同批 FEFO 拆分；对齐 **DM-WKF-008 v1.0.9** / **AS-IP-PHA-001-SDD v2.10** |
| v1.10 | 2026-10-13 | **IF-IPS-02**：`displayQuantityText` 改为 `#buildPlainQuantityText` 行单位直显；对齐 **AS v1.9** / **BR-IP-SUM-015** |
| v1.9 | 2026-10-12 | **IF-IPS-06 / UI**：药房汇总工作台接通「打印汇总单」（待核发/已发药）；对齐 **AS v1.8** |
| v1.8 | 2026-08-19 | **IF-IPS-03**：扣库换算收口 **操作单位→拆零（五类）**；对齐 **AS v1.7 BR-IPS-006** / **BR-OPP-017** |
| v1.7 | 2026-08-02 | **IF-IPS-02**：无主档时 `traceUnit*` 回落药典；扫码弹窗去掉处方号、追溯码单位/码倍列收窄 |
| v1.6 | 2026-08-02 | **IF-IPS-02**：`InpatientPharmacySummarySlipLineDto` 文档补 `traceUnitCode`/`traceUnitCodeText`/`traceUnitRatio`/`lotHasTraceMaster`/`representativeLotNumber` 等；批量扫码弹窗药品明细展示贴码单位与码/拆零比 |
| v1.5 | 2026-07-08 | **已发药真源**：IF-IPS-01/02 仅以发放表 **COMPLETED** 判已发药；移除客户端 **`supplyStatusEnumIn`**；待发 Supply **APPROVAL** 服务端写死（**BR-IPS-001/002/013**） |
| v1.4 | 2026-06-17 | 对齐 AS v1.4：IF-IPS-01 **`supplyStatusEnumIn`** 待核对/已发药（**BR-IPS-013**）；IF-IPS-02 已核发只读（**AGREE** + **COMPLETED**） |
| v1.3 | 2026-06-17 | 对齐 AS v1.3：IF-IPS-02 合并行 **`displayQuantityText`/`manufacturerName`/`purposeLocationName`**（**BR-IPS-012**）；共享 **`MedicationPackageSplitQuantityDisplaysupport**；前端明细表列对齐按患者发药 |
| v1.2 | 2026-06-09 | **IF-IPS-01 查询修复**：type 27 单端点 purpose 真源，列表/Request/DTO 由 `sourceLocation*` 更正为 `purposeLocation*`；门控与 **IF-IP-SUM-02** 对齐（`purposeLocationIds` 空且非 strict 不拼谓词）；**BR-IPS-001** 修订 |
