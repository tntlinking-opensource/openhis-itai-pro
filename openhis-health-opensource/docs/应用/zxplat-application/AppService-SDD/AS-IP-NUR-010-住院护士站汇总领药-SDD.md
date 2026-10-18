# 住院护士站 · 汇总领药 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-010-SDD  
> 版本：v1.18 | 日期：2026-10-16 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站工作台 · **汇总领药** Tab（明细 Tab 待汇总查询 + 写汇总；汇总 Tab 单头/详情查询；**退药 Tab** 取消执行退药只读；打印为前端复用读接口） |
| 实现阶段口径 | **目标态全量契约**（IF-IP-SUM-01～10 后端；IF-IP-SUM-06/07 无独立写接口）；**领域 QueryService Gap 001～003 已关闭**；**应用层已落地** `inpatient/nursestation/dispensesummary`，AppService **`NurseStationDispenseSummaryAppService`**，HTTP 前缀 **`nurseStationDispenseSummary`** |
| 编排权威 | `AS-IP-NUR-010-住院护士站汇总领药.md`（**v1.20**）§1.3、§4 |
| 方法权威 | `DM-MED-002` §6.1 汇总写能力、§6.2.17、§6.2.4a、§4.6.3；`DM-WKF-006` §6.1.10/§6.2.10～11；`DM-WKF-005` §6.1 汇总发放（**药房 AS-IP-PHA-002 调用**）；`DM-MED-003` §6.2 护士站药嘱列表；`DM-ADM-011` §6.2 发药定价只读 |
| 严格对齐 | IF 编号 **IF-IP-SUM-01～10**（含 08 撤销、09 摆药、10 退药只读）；写路径分组键 **`itemId + ChargeItem.unit_price`**；Supply 汇总行 **`lot_number` 可空**；摆药合并键 **`encounterId + medReqId`**；退药口径 **REFUND + CANCEL Procedure** |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 左侧患者树/列表/详情 | **AS-IP-NUR-001** |
| 医嘱执行 | **AS-IP-NUR-004** |
| 取消执行写路径及汇总重建 | **AS-IP-NUR-012** |
| 药房汇总核发 | **AS-IP-PHA-002** |
| 药房确认退药 | **AS-IP-PHA-003**（IF-10 只读） |
| ChargeItem 任何写操作 | **BR-IP-SUM-013** |
| 口服/注射子筛选 | 非目标 |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001` | 可见 encounter 集合 **BR-IP-SUM-001** |
| `AS-IP-NUR-004` | 上游产生 `PREPARATION` + `procedure_id` 发放行与 ChargeItem |
| `AS-INV-WHS-002` | 同族 unitBands / ratioToSplit / 拆零单位口径 |
| `DM-MED-002-SDD` / `DM-WKF-006-SDD` | QueryModel 与 QueryService 签名 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 状态 / 枚举 code | 持久化与比较一律 **String `code`**（`DispenseStatus`、`SupplyType`、`RequestStatus` 等） |
| `summaryNo` / `busNo` | 同一汇总单业务键；发放行 `summary_no` 与 Supply `bus_no` **必须一致** |
| 汇总分组键（写） | **先按发放药房** `locationId` 拆单；同药房内合并键 **`locationId + medicationId + chargeUnitPrice`**（只读 ChargeItem）；**不同发放药房不得同单** |
| type 27 位置字段（单端点 purpose 真源） | 仅 `purpose_location_id` / `purpose_type_enum`（发放药房）；`source_*` **NULL**（与订货/盘点/损益等单端点一致） |
| 医嘱过滤（读） | 与执行列表同源：`RequestStatus.VERIFIED` + `STOPPED`（AS 文案 COMPLETED/STOPPED 在待发场景映射为 **VERIFIED/STOPPED**，见 §3.2 步骤 5） |
| `medicineCategoryTab` | `west`（西药，`tcmFlag=false`）/ `tcm`（中药，`tcmFlag=true`） |
| `therapyFilter` | 复用 `InpatientNurseStationConstants.THERAPY_FILTER_*` → `MedicationRequest.therapy_enum` 或不过滤 |
| 数量换算 | **`MedicationProductQuantityToSplitUnitConverter`** + `ProductDefinitionCatalogUnitBandsAssembler`；精度 **6 位 HALF_UP**（与 **BR-IP-SUM-008**、**AS-IP-NUR-005** 同族） |
| HTTP | 一律 **`POST`**；Bean **`NurseStationDispenseSummaryAppService`** → 前缀 **`nurseStationDispenseSummary`** |
| 写事务 | **IF-IP-SUM-04/05/08** 全批 **`@Transactional`**；任药房组/合并组/发送/撤销失败 **整批回滚**（**BR-IP-SUM-012**） |
| 写顺序与单号 | 每药房组内：**先** `summarizeMedicationDispenses` 取得域生成 `summaryNo`，**再** `createSummarySupplyRequest(busNo=summaryNo, 初态 PENDING_APPROVAL)`；**不调用** `createSummarySupplyDelivery`（药房核发时创建） |

## 2. 代码结构

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/nursestation/
  dispensesummary/
    service/
      NurseStationDispenseSummaryAppService.java
    request/
      PagePendingDispenseSummaryLinesRequest.java
      PageMedicationSummarySlipHeadersRequest.java
      GetMedicationSummarySlipDetailRequest.java
      GetMedicationSummarySlipPatientDetailRequest.java
      CreateMedicationSummaryRequest.java
      SubmitMedicationSummaryRequest.java
      CancelMedicationSummaryRequest.java
    response/
      PagePendingDispenseSummaryLinesResponse.java
      PageMedicationSummarySlipHeadersResponse.java
      GetMedicationSummarySlipDetailResponse.java
      GetMedicationSummarySlipPatientDetailResponse.java
      CreateMedicationSummaryResponse.java
      SubmitMedicationSummaryResponse.java
      CancelMedicationSummaryResponse.java
    dto/
      NurseStationDispenseSummaryPatientGroupDto.java
      NurseStationDispenseSummaryOrderRowDto.java
      NurseStationDispenseSummaryTimePointDto.java
      NurseStationDispenseSummarySlipHeaderDto.java
      NurseStationDispenseSummarySlipLineDto.java
      NurseStationDispenseSummaryPatientPickupGroupDto.java
      NurseStationDispenseSummaryPatientPickupLineDto.java
      CreateMedicationSummaryDispenseItemDto.java
    assembler/
      NurseStationDispenseSummaryListAssembler.java
      NurseStationDispenseSummaryWriteAssembler.java
    mapper/
      NurseStationDispenseSummaryMapper.java
      NurseStationDispenseSummaryMapperImpl.java

application/shared/advice/
  InpatientStandingOrderSplittableSettingResolver.java   # 长嘱可拆开关（与 IF-IP-EXE-01 共用；汇总 IF-01 不再做整盒补齐）
```

> 包名：`com.zxplat.domain.application.inpatient.nursestation.dispensesummary`

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径操作人（`operatorId`） |
| `CurrentPractitionerResolver` | 写路径申请人（`applicantId`） |
| `CurrentManagedWardLocationIdsResolver` | 护士站管辖病区 → 可见 encounter（**BR-IP-SUM-001**） |
| `PageWindowGate` | 分页 `pageNo`/`pageSize` 必填校验（IF-01/02） |
| `EncounterContextReader` | 可见 encounter 收窄（`CurrentManagedWardLocationIdsResolver` + Facade） |
| `LocationQueryService` | 药房/位置名称；写 Supply 时 `sourceTypeEnum` |
| `MedicationRequestQueryService` | 中西药/长临嘱 → `resolvedMedReqIds` |
| `MedicationDispenseQueryService` | IF-01 分页；IF-04 写前强加载；IF-09 `listMedicationDispensesBySummaryNo` |
| `ChargeItemQueryService` | IF-04 只读取价/分组（**禁止写**） |
| `SupplyRequestQueryService` | IF-02 单头；IF-03 合并行；IF-05 写前校验 type 27 |
| `MedicationDispenseDomainService` | IF-04 `summarizeMedicationDispenses`；IF-08 `cancelMedicationDispenseSummary` |
| `SupplyRequestDomainService` | IF-04 `createSummarySupplyRequest`；IF-05 `submitApproval`；IF-08 `removeByBusNo` |
| `ProductDefinitionBatchLoader` | 药品展示字段 + unitBands 源 |
| `PractitionerQueryService` | 申请人/领药人姓名展示（MapperImpl 批量索引） |
| `NurseStationDispenseSummaryListAssembler` | 待汇总列表分组、汇总 Tab DTO |
| `NurseStationDispenseSummaryWriteAssembler` | 分组键、拆零数量、Command 装配 |
| `NurseStationDispenseSummaryMapper` / `MapperImpl` | Request↔Query/Command、Page→Response |

### 2.2 领域方法白名单

| 类型 | 方法 | IF |
| --- | --- | --- |
| 读 | `MedicationRequestQueryService#listInpatientNurseStationExecuteOrders` | 01（解析 medReqIds） |
| 读 | `MedicationDispenseQueryService#pageMedicationDispensePickupPending` | 01、05 |
| 读 | `SupplyRequestQueryService#pageMedicationSummarySlips` | 02、06 |
| 读 | `EncounterQueryService#listEncounterIdsByWardLocationIds` | 02、03、05、08、09（管辖病区门控） |
| 读 | `MedicationDispenseQueryService#listDistinctSummaryNosByEncounterIds` | 02、03、05、08、09（管辖病区门控） |
| 读 | `SupplyRequestQueryService#listMedicationSummarySlipLinesByBusNo` | 03、06 |
| 读 | `MedicationDispenseQueryService#listMedicationDispensesByIds` | 04 Pre |
| 读 | `MedicationDispenseQueryService#listCancelExecuteRefundDispensesByEncounterIds` | 10 |
| 读 | `ProcedureQueryService#listByIds` | 10（CANCEL 终态校验） |
| 读 | `MedicationRequestQueryService#mapEntitiesByIds` | 10（中西药 Tab） |
| 读 | `ChargeItemQueryService#listChargeItemsForMedicationDispensePricing` | 04 Pre |
| 读 | `SupplyRequestQueryService#getSupplyByBusNo` | 05、08 Pre（校验 type 27 存在与状态） |
| 写 | `MedicationDispenseDomainService#summarizeMedicationDispenses` | 04 |
| 写 | `MedicationDispenseDomainService#cancelMedicationDispenseSummary` | 08 |
| 写 | `SupplyRequestDomainService#createSummarySupplyRequest` | 04 |
| 写 | `SupplyRequestDomainService#submitApproval` | 05 |
| 写 | `SupplyRequestDomainService#removeByBusNo` | 08 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002` §2 状态机 | IF-01 基线 `PREPARATION`；IF-04 后 `SUMMARIZED` |
| 供应类型 | `com.zxplat.domain.share.enums.SupplyType#MEDICATION_SUMMARY_ORDER` | `DM-WKF-006` §4.1.4 值 `27` | IF-02/03/04 汇总单 |
| 供应请求初态 | `com.zxplat.domain.share.enums.SupplyStatus#PENDING_APPROVAL` | `DM-WKF-006` §2.3 / **BR-IP-SUM-011** | IF-04 创建汇总明细后（对齐库房保存草稿） |
| 供应请求发送后 | `SupplyStatus#APPROVAL` | **BR-IP-SUM-011** | IF-05 submitApproval |
| 汇总 Tab 审批态文案 | `SUMMARY_SUPPLY_STATUS_*_DISPLAY` | IF-02 待发送/待核发/已核发/驳回 | `supplyStatusEnumText` |
| 汇总 Tab 发放态文案 | `SUMMARY_DELIVERY_STATUS_*_DISPLAY` | IF-02 药房核发后 | `deliveryStatusEnumText` |
| 药嘱状态过滤 | `com.zxplat.domain.share.enums.RequestStatus#VERIFIED` / `#STOPPED` | `DM-MED-003` §3 `status_enum` | IF-01 Pre 收窄 medReq |
| 长临嘱 | `InpatientNurseStationConstants.THERAPY_FILTER_LONG` / `THERAPY_FILTER_TEMP` / `THERAPY_FILTER_ALL` | `DM-MED-003` §3 `therapy_enum` | `PagePendingDispenseSummaryLinesRequest.therapyFilter` |
| 中药标识 | `InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST` / `MEDICINE_CATEGORY_TAB_TCM` | `DM-MED-003` §3 `tcm_flag` | `PagePendingDispenseSummaryLinesRequest.medicineCategoryTab` |
| 物品表 | `com.zxplat.domain.share.constants.TableNameConstants#MED_MEDICATION_DEFINITION` | `DM-WKF-006` §6.1.10 `item_table` | 写 Supply 行 |
| 供应类别 | `com.zxplat.domain.share.enums.SupplyCategory#STOCK_SUPPLY` | `DM-WKF-006` `category_enum` | 写 Supply 行（**非** `sourceTypeEnum`） |
| 业务异常码 | `com.zxplat.domain.share.constants.InpatientNurseStationConstants.EX_IP_SUM_*` | `AS-IP-NUR-010` §5 | 已实现 |
| 写批上限 | `InpatientNurseStationConstants.SUMMARY_WRITE_BATCH_MAX`（500） | `AS-IP-NUR-010` §7 NFR | IF-04 |
| 拆零精度 | `InpatientNurseStationConstants.SUMMARY_SPLIT_QUANTITY_SCALE`（6） | **BR-IP-SUM-008** | IF-04 WriteAssembler |
| 汇总 Tab 发放态文案 | `SUMMARY_DELIVERY_STATUS_PENDING_DISPLAY` / `SUMMARY_DELIVERY_STATUS_COMPLETED_DISPLAY` | IF-02 待发/已发 | `deliveryStatusEnumText` |
| 单位换算 | `MedicationProductQuantityToSplitUnitConverter#medicationQuantityToSplitUnit` | `DM-MED-001` + **BR-IP-SUM-007/008** | IF-04 WriteAssembler |
| 单位槽 | `ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands` | `DM-MED-001` §3 单位槽 | IF-04 校验 ratioToSplit |

### 2.4 领域层能力核对（非 Gap）

> 领域 Query Gap **GAP-IP-SUM-001～003** 已关闭（**AS §3.2**）。本 SDD 驱动**应用层**成码。

**已落地域能力**：`pageMedicationDispensePickupPending`、`pageMedicationSummarySlips`、`listMedicationSummarySlipLinesByBusNo`、`listEncounterIdsByWardLocationIds`、`listDistinctSummaryNosByEncounterIds`；写侧 `summarizeMedicationDispenses`、`createSummarySupplyRequest`（初态 PENDING_APPROVAL）、`submitApproval`。**`createSummarySupplyDelivery`** 由 **AS-IP-PHA-002** 调用。

**本 SDD 成码交付（已完成）**：`NurseStationDispenseSummaryAppService` 六接口；List/Write Assembler；Request/Response/DTO/Mapper；`InpatientNurseStationConstants` 增补 `EX_IP_SUM_*`（含 **EX-IP-SUM-205**、**EX-IP-SUM-206**、**EX-IP-SUM-207**）、`SUMMARY_SUPPLY_STATUS_*`、`SUMMARY_WRITE_BATCH_MAX` 等。

> **DOC-CONFLICT（仍有效，代码已按此实现）**：AS **BR-IP-SUM-002** 药嘱状态 **COMPLETED/STOPPED** 文案；待发场景实现采用 **VERIFIED/STOPPED**（与 **AS-IP-NUR-004** 执行列表一致）。

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-SUM-01 | `pagePendingDispenseSummaryLines` | 分页查询待汇总明细 | `POST /api/app/nurseStationDispenseSummary/pagePendingDispenseSummaryLines` |
| IF-IP-SUM-02 | `pageMedicationSummarySlipHeaders` | 分页查询汇总单头 | `POST /api/app/nurseStationDispenseSummary/pageMedicationSummarySlipHeaders` |
| IF-IP-SUM-03 | `getMedicationSummarySlipDetail` | 查询汇总单合并明细 | `POST /api/app/nurseStationDispenseSummary/getMedicationSummarySlipDetail` |
| IF-IP-SUM-09 | `getMedicationSummarySlipPatientDetail` | 查询汇总单摆药明细（按患者） | `POST /api/app/nurseStationDispenseSummary/getMedicationSummarySlipPatientDetail` |
| IF-IP-SUM-10 | `listCancelExecuteReturnMedicineLines` | 按患者查询取消执行退药清单 | `POST /api/app/nurseStationDispenseSummary/listCancelExecuteReturnMedicineLines` |
| IF-IP-SUM-04 | `createMedicationSummary` | 汇总保存 | `POST /api/app/nurseStationDispenseSummary/createMedicationSummary` |
| IF-IP-SUM-05 | `submitMedicationSummary` | 发送汇总单 | `POST /api/app/nurseStationDispenseSummary/submitMedicationSummary` |
| IF-IP-SUM-08 | `cancelMedicationSummary` | 撤销汇总单 | `POST /api/app/nurseStationDispenseSummary/cancelMedicationSummary` |
| IF-IP-SUM-06 | — | 打印待汇总明细 | **无独立后端方法**；前端复用 IF-IP-SUM-01 |
| IF-IP-SUM-07 | — | 打印汇总单 | **无独立后端方法**；前端复用 IF-IP-SUM-02、03 |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数、可见性、批上限 | 抛 **`EX-IP-SUM-*`**，不进入 Pre |
| **Pre** | QueryService 只读、ChargeItem 只读、分组/换算内存计算 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | IF-04：Dispense 汇总 → SupplyRequest（PENDING_APPROVAL）；IF-05：`submitApproval`；IF-08：`cancelMedicationDispenseSummary` → `removeByBusNo` | 同事务；失败整批回滚 |
| **Asm** | 按就诊/医嘱分组、产品展示、分页 Response | — |

记法：`MedicationDispenseQueryService#pageMedicationDispensePickupPending` 表示注入 Bean 调用。

### 3.2 IF-IP-SUM-01 `pagePendingDispenseSummaryLines`（分页查询待汇总明细）

**HTTP**：`POST /api/app/nurseStationDispenseSummary/pagePendingDispenseSummaryLines`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterIds` | `List<Long>` | Y | **AS-IP-NUR-001** 已选就诊；非空 |
| `medicineCategoryTab` | `String` | Y | `west` / `tcm`（**InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_***） |
| `therapyFilter` | `String` | Y | `all` / `long` / `temp`（**InpatientNurseStationConstants**） |
| `queryDeadline` | `LocalDateTime` | N | 计划发药截止（含）；空则不过滤 |
| `anesthesiaApplyOnly` | `Boolean` | N | `true`=仅麻醉申请药品（申请单号 AA 前缀）；`false`/空=排除（**BR-IP-SUM-018**；无类目 25 / 同单号池） |
| `pageNo` | `Integer` | Y | 1-based |
| `pageSize` | `Integer` | Y | 分页大小 |

**Response**（继承 `PagedResult`；分页字段 **`pageNo`/`pageSize`/`totalCount`** 经 `PagedResultBuilder` 装配；业务数据在 **`patientGroups`**，发放行维度 total 在 **`totalCount`**）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageNo` | `Integer` | 当前页（1-based，父类） |
| `pageSize` | `Integer` | 页大小（父类） |
| `totalCount` | `Long` | 总条数（发放行维度，父类） |
| `patientGroups` | `List<NurseStationDispenseSummaryPatientGroupDto>` | 按就诊分组（Asm 由扁平行聚合） |

**DTO：`NurseStationDispenseSummaryPatientGroupDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊 |
| `patientId` | `Long` | 患者 |
| `patientName` | `String` | 展示名（EncounterContext 或 PatientQuery） |
| `bedDisplay` | `String` | 床号展示 |
| `orderRows` | `List<NurseStationDispenseSummaryOrderRowDto>` | 医嘱维度 |

**DTO：`NurseStationDispenseSummaryOrderRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `medReqId` | `Long` | 药品申请 |
| `medicationId` | `Long` | 药品定义 |
| `medicationName` | `String` | 药品名称 |
| `specificationText` | `String` | 规格 |
| `groupId` | `Long` | 成组号（可空） |
| `therapyEnum` | `String` | 长临 code |
| `therapyEnumText` | `String` | 长临展示 |
| `dosageInstruction` | `String` | 用药说明 |
| `methodCode` | `String` | 用法 code |
| `methodCodeText` | `String` | 用法展示 |
| `frequencyCode` | `String` | 频次 code |
| `frequencyCodeText` | `String` | 频次展示 |
| `locationId` | `Long` | 发放药房 |
| `locationName` | `String` | 药房展示 |
| `timePoints` | `List<NurseStationDispenseSummaryTimePointDto>` | 可勾选时间点 |

**DTO：`NurseStationDispenseSummaryTimePointDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dispenseId` | `Long` | 发放行主键 |
| `procedureId` | `Long` | 执行记录 |
| `plannedDispenseTime` | `LocalDateTime` | 计划发药时刻（=上游执行 **planTime**；勾选/截止/归组逻辑锚；**非**展示首选） |
| `recordedTime` | `LocalDateTime` | 实际执行时刻（Procedure.`recorded_time`；「领药时间」列展示优先；可空则回落 `plannedDispenseTime`） |
| `quantity` | `BigDecimal` | 原单位数量 |
| `unitCode` | `String` | 原单位 code |
| `unitCodeText` | `String` | 单位展示 |
| `chargeUnitPrice` | `BigDecimal` | 只读账单单价 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-SUM-001** | `encounterIds` 须全部落在护士站可见范围 |
| **BR-IP-SUM-002** | 域 Query 已含 PREPARATION+procedure；App 预解析 medReq + tcm + 长临；`planned ≤ queryDeadline` |
| **BR-IP-SUM-016** | `resolveMedReqIds` **一律剔除** `therapy_enum=DISCHARGE_MEDICATION`（出院带药不进待汇总） |
| **BR-IP-SUM-013** | ChargeItem 只读，不在 App 写账单 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IP-SUM-101** | `encounterIds` 空或均不可见 | 请先选择患者或无权访问 |
| **EX-IP-SUM-102** | 未捕获异常 | 加载失败，请稍后重试 |

**Mapper 接口方法签名**

- `toPickupPendingPageQuery(PagePendingDispenseSummaryLinesRequest request, List<Long> visibleEncounterIds, List<Long> resolvedMedReqIds): MedicationDispensePickupPendingPageQuery`
- `toPagePendingDispenseSummaryLinesResponse(Page<MedicationDispensePickupPendingQueryModel> page, List<NurseStationDispenseSummaryPatientGroupDto> groups): PagePendingDispenseSummaryLinesResponse`

**Assembler 方法签名**

- `resolveMedReqIds(List<Long> encounterIds, String medicineCategoryTab, String therapyFilter): List<Long>`
- `buildPatientGroups(Page<MedicationDispensePickupPendingQueryModel> page, Map<Long, MedicationRequestListQueryModel> medReqById, Map<String, ProductDefinitionDisplayDto> productByKey, Map<Long, InpatientEncounterContextQueryModel> encounterById): List<NurseStationDispenseSummaryPatientGroupDto>`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `encounterIds`、`medicineCategoryTab`、`therapyFilter`、`pageNo`/`pageSize` | 空 encounter → **EX-IP-SUM-101** |
| 2 | Gate | `CurrentManagedWardLocationIdsResolver` + `EncounterContextReader#listInpatientEncounterContexts` | 当前用户管理病区 + `encounterIds` | 收窄可见 id；无交集 → **EX-IP-SUM-101** |
| 3 | Pre | `MedicationRequestQueryService#listInpatientNurseStationExecuteOrders` | ② ids + `therapyFilter` | 状态 **VERIFIED+STOPPED** |
| 4 | Pre | `NurseStationDispenseSummaryListAssembler#resolveMedReqIds` | ③ + `medicineCategoryTab` | 按 `tcmFlag` 过滤；**剔除出院带药**（**BR-IP-SUM-016**）；得 `resolvedMedReqIds` |
| 4b | Pre | `InpatientNurseStationAnesthesiaApplyScopeSupport#filterMedReqIdsByAnesthesiaApplyScope` | ④ + `anesthesiaApplyOnly` | **BR-IP-SUM-018**：默认排除麻醉申请（AA 前缀）药品 |
| 5 | Pre | `NurseStationDispenseSummaryMapper#toPickupPendingPageQuery` | Request + ② + ④b | 填 `queryDeadline`（`planned ≤ queryDeadline`） |
| 6 | Pre | `MedicationDispenseQueryService#pageMedicationDispensePickupPending` | ⑤ Query | **BR-IP-SUM-002** |
| 7 | Pre | `ProductDefinitionDisplayTerminologyAssembler#loadAsMap` | ⑥ 行 `medicationId` | 药品名/规格/包装比 |
| 8 | Pre | `InpatientMedicationDispenseChargePricingLoader` | ⑥ 行 | 只读回填 `chargeUnitPrice` |
| 9 | Asm | `#buildPatientGroups` | ⑥ 扁平行 | 就诊→医嘱→时间点；**不做数量合并** |
| 10 | Asm | `Mapper#toPagePendingDispenseSummaryLinesResponse` | page + ⑨ | 返回分页壳 + 分组 |
| — | — | — | — | **仅读路径** |

### 3.3 IF-IP-SUM-02 `pageMedicationSummarySlipHeaders`（分页查询汇总单头）

**HTTP**：`POST /api/app/nurseStationDispenseSummary/pageMedicationSummarySlipHeaders`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `purposeLocationId` | `Long` | N | 发放药房；**仅当显式传入有效 id** 时收窄 `purposeLocationIds`；0 或空表示**不按药房过滤**（仍受管辖病区 `busNoIn` 门控） |
| `startDate` | `LocalDateTime` | N | 申请时间下界 |
| `endDate` | `LocalDateTime` | N | 申请时间上界 |
| `keyword` | `String` | N | 单号关键字 |
| `pageNo` | `Integer` | Y | 1-based |
| `pageSize` | `Integer` | Y | 分页 |

**Response**（继承 `PagedResult`；**`rows`** 与父类 **`content`** 同源；**`totalCount`** 为总单数）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageNo` | `Integer` | 当前页（1-based，父类） |
| `pageSize` | `Integer` | 页大小（父类） |
| `totalCount` | `Long` | 总单数（父类） |
| `rows` | `List<NurseStationDispenseSummarySlipHeaderDto>` | 汇总单头（与 `content` 一致） |

**DTO：`NurseStationDispenseSummarySlipHeaderDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `busNo` | `String` | 汇总单号 |
| `applyTime` | `LocalDateTime` | 申请/发送时间（submitApproval 后） |
| `dispenseTime` | `LocalDateTime` | 发药时间（药房核发后） |
| `purposeLocationId` | `Long` | 药房 id |
| `purposeLocationName` | `String` | 药房名 |
| `applicantId` | `Long` | 申请人 |
| `applicantName` | `String` | 申请人姓名 |
| `applicantDepartmentName` | `String` | 申领科室（病区主挂靠科室名；**IF-03/09 详情回填**，列表可不填；**BR-IP-SUM-020**） |
| `receiverId` | `Long` | 领药人（药房核发后） |
| `receiverName` | `String` | 领药人姓名 |
| `supplyStatusEnum` | `String` | 审批态 code（SupplyRequest.status_enum） |
| `supplyStatusEnumText` | `String` | 待发送/待核发/已核发/驳回 |
| `deliveryStatusEnum` | `String` | 发放态 code（核发后） |
| `deliveryStatusEnumText` | `String` | 待发/已发 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-SUM-001** | IF-02：**管辖病区门控**——`CurrentManagedWardLocationIdsResolver#listManagedActiveWardsOrEmpty` → `EncounterQueryService#listEncounterIdsByWardLocationIds` → `MedicationDispenseQueryService#listDistinctSummaryNosByEncounterIds` 写入 `busNoIn`；空白名单 → **空页**（禁止回落全院 type 27）。药房：`purposeLocationId` **仅**显式传入时收窄 |
| **BR-IP-SUM-013** | 只读 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IP-SUM-102** | 查询异常 | 加载失败，请稍后重试 |

**Mapper 接口方法签名**

- `toSupplyRequestDocumentListQuery(PageMedicationSummarySlipHeadersRequest request, List<Long> purposeLocationIds): SupplyRequestDocumentListQuery`
- `toSlipHeaderDtos(Page<MedicationSummarySlipQueryModel> page): List<NurseStationDispenseSummarySlipHeaderDto>`
- `toPageMedicationSummarySlipHeadersResponse(Page<MedicationSummarySlipQueryModel> page, List<NurseStationDispenseSummarySlipHeaderDto> rows): PageMedicationSummarySlipHeadersResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | 分页参数 | — |
| 2 | Pre | App `resolveManagedWardVisibleSummaryNos` | 管辖病区 | 病区→就诊→summaryNo；空 → 空页返回 |
| 3 | Pre | App `resolveSummaryPurposeLocationIds` | `purposeLocationId` | 显式 id → 单元素列表；否则 `null`（**不做**用户可操作药房自动门控） |
| 4 | Pre | `Mapper#toSupplyRequestDocumentListQuery` + `setBusNoIn` | Request + ② | 固定 `typeEnum=MEDICATION_SUMMARY_ORDER` + 管辖病区单号白名单 |
| 5 | Pre | `SupplyRequestQueryService#pageMedicationSummarySlips` | ④ Query | 域内关联 SupplyDelivery |
| 6 | Asm | `Mapper#toSlipHeaderDtos` + 批量 Location/Practitioner 名称 | ⑤ | 展示字段 |
| 7 | Asm | `Mapper#toPageMedicationSummarySlipHeadersResponse` | ⑤⑥ | — |
| — | — | — | — | **仅读路径**；拆单仍按药房（IF-04），本 IF 只收窄展示 |

**前端（汇总 Tab 列表）**

| 列/字段 | 数据源 | 说明 |
| --- | --- | --- |
| 汇总药房 | `purposeLocationName` | IF-02 单头 DTO；无则展示 `—` |
| 单据号/状态/时间等 | 同 IF-02 DTO | 见上表；列表已按管辖病区服务端过滤，无需再传病区 id |

### 3.4 IF-IP-SUM-03 `getMedicationSummarySlipDetail`（查询汇总单合并明细）

**HTTP**：`POST /api/app/nurseStationDispenseSummary/getMedicationSummarySlipDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `summaryNo` | `String` | Y | 汇总单号 / busNo |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summaryNo` | `String` | 单号 |
| `header` | `NurseStationDispenseSummarySlipHeaderDto` | 单头（同 IF-02 字段 + **`applicantDepartmentName` 回填**，**BR-IP-SUM-020**） |
| `lines` | `List<NurseStationDispenseSummarySlipLineDto>` | 合并明细；**无 lotNumber**；**按拼音首字母序**（**BR-IP-SUM-020**） |

**DTO：`NurseStationDispenseSummarySlipLineDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `supplyRequestId` | `Long` | 供应请求行 id |
| `itemId` | `Long` | 药品定义 id |
| `medicationName` | `String` | 药品名称 |
| `specificationText` | `String` | 规格 |
| `itemQuantity` | `BigDecimal` | 拆零汇总数量（Supply 持久化；**禁止**前端直接拼界面数量） |
| `unitCode` | `String` | 拆零单位 code |
| `unitCodeText` | `String` | 拆零单位展示 |
| `displayQuantityText` | `String` | 界面数量文案（**BR-IP-SUM-015**）：拆零数量 + 单位直显，如 `15支`；禁止满包+余量；无单位文案时仅数字；前端仍可回退 `itemQuantity`+`unitCodeText` |
| `unitPrice` | `BigDecimal` | 账单单价（**BR-IP-SUM-009**） |
| `totalPrice` | `BigDecimal` | 组内 ChargeItem.total_price 累加 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-SUM-009** | 价格来自域 Query 只读累加 |
| **BR-IP-SUM-010** | Response **不含**批号字段 |
| **BR-IP-SUM-015** | `MedicationPackageSplitQuantityDisplayAssembler#buildPlainQuantityText`：数量+单位直显；**禁止**满包+余量；**不改** `itemQuantity`；**禁止**用展示量重算 `totalPrice` |
| **BR-IP-SUM-020** | 单头 `applicantDepartmentName`（病区主挂靠科室）；`lines` 按 `pyStr` 首字母序 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IP-SUM-103** | 单号无 type 27 行 | 汇总单不存在或已失效，请刷新 |
| **EX-IP-SUM-102** | 其他异常 | 加载失败，请稍后重试 |

**Mapper 接口方法签名**

- `toGetMedicationSummarySlipDetailResponse(String summaryNo, NurseStationDispenseSummarySlipHeaderDto header, List<NurseStationDispenseSummarySlipLineDto> lines): GetMedicationSummarySlipDetailResponse`

**Assembler 方法签名（`NurseStationDispenseSummaryListAssembler`）**

- `buildSlipLineDtos(List<MedicationSummarySlipLineQueryModel> domainLines, Map<String, ProductDefinitionDisplayDto> productByKey): List<NurseStationDispenseSummarySlipLineDto>`（内部调用 **`MedicationPackageSplitQuantityDisplayAssembler#buildPlainQuantityText`**；末尾按 `pyStr` 排序）

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `summaryNo` 非空 | — |
| 2 | Pre | `SupplyRequestQueryService#listMedicationSummarySlipLinesByBusNo` | `summaryNo` | 空 → **EX-IP-SUM-103** |
| 3 | Pre | 单头：复用 IF-02 装配 | `summaryNo` | `getSupplyByBusNo` + Delivery 或二次 `page` 单行 |
| 3b | Pre | `MedicationSummaryApplicantDepartmentResolver#resolveByEncounterIds` | 发放行 encounterIds | 回填 `applicantDepartmentName`（**BR-IP-SUM-020**） |
| 4 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | ② `itemId` | 名称/规格/`pyStr` |
| 5 | Asm | `ListAssembler#buildSlipLineDtos` + `MedicationPackageSplitQuantityDisplayAssembler#buildPlainQuantityText` | ②③④ | **禁止**输出 lotNumber；写入 `displayQuantityText`；**按拼音序** |
| 6 | Asm | `Mapper#toGetMedicationSummarySlipDetailResponse` | ⑤ | — |
| — | — | — | — | **仅读路径** |

### 3.4.1 IF-IP-SUM-09 `getMedicationSummarySlipPatientDetail`（摆药单查询）

**HTTP**：`POST /api/app/nurseStationDispenseSummary/getMedicationSummarySlipPatientDetail`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `summaryNo` | `String` | Y | 汇总单号 / busNo |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summaryNo` | `String` | 单号 |
| `header` | `NurseStationDispenseSummarySlipHeaderDto` | 单头（同 IF-02 字段；外层列表已有时可忽略） |
| `patientGroups` | `List<NurseStationDispenseSummaryPatientPickupGroupDto>` | 按患者分组的摆药行 |

**DTO：`NurseStationDispenseSummaryPatientPickupGroupDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊 |
| `patientId` | `Long` | 患者 |
| `patientName` | `String` | 姓名 |
| `bedDisplay` | `String` | 床号 |
| `lines` | `List<NurseStationDispenseSummaryPatientPickupLineDto>` | 同 medReqId 已合并 |

**DTO：`NurseStationDispenseSummaryPatientPickupLineDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `medReqId` | `Long` | 合并键：一条医嘱产生的多条发药汇总为一行 |
| `medicationId` | `Long` | 药品定义 |
| `medicationName` / `specificationText` | `String` | 药品名 / 规格 |
| `itemQuantity` | `BigDecimal` | 同 medReqId 发放数量累加 |
| `unitCode` / `unitCodeText` | `String` | 单位 |
| `displayQuantityText` | `String` | 与 IF-03 同口径（**BR-IP-SUM-015**） |
| `dosageInstruction` | `String` | 剂量 |
| `methodCode` / `methodCodeText` | `String` | 用法 |
| `frequencyCode` / `frequencyCodeText` | `String` | 频次 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| 合并键 | 同一 `encounterId` 下同一 `medReqId` 数量相加；剂量/用法/频次取组内首条 |
| **BR-IP-SUM-010** | Response **不含**批号 |
| **BR-IP-SUM-015** | `displayQuantityText` 与 IF-03 同装配 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IP-SUM-103** | 单号无发放行 / 单头不存在 | 汇总单不存在或已失效，请刷新 |
| **EX-IP-SUM-102** | 其他异常 | 加载失败，请稍后重试 |

**Mapper 接口方法签名**

- `toGetMedicationSummarySlipPatientDetailResponse(String summaryNo, NurseStationDispenseSummarySlipHeaderDto header, List<NurseStationDispenseSummaryPatientPickupGroupDto> patientGroups): GetMedicationSummarySlipPatientDetailResponse`

**Assembler 方法签名（`NurseStationDispenseSummaryListAssembler`）**

- `buildPatientPickupGroups(List<MedicationDispenseSummaryItemQueryModel> dispenseRows, Map<String, ProductDefinitionDisplayDto> productByKey, Map<Long, InpatientEncounterContextQueryModel> encounterById): List<NurseStationDispenseSummaryPatientPickupGroupDto>`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `summaryNo` 非空 | — |
| 2 | Pre | `MedicationDispenseQueryService#listMedicationDispensesBySummaryNo` | `summaryNo` | 空 → **EX-IP-SUM-103**；QueryModel 须含 `medReqId`/剂量/用法/频次 |
| 3 | Pre | 单头：复用 IF-02 装配 | `summaryNo` | 顺带校验 type 27 单仍存在 |
| 4 | Pre | `EncounterContextReader` + 床位 parent→病房名 | ② 的 encounterIds / bedId | 床号/姓名；`bedDisplay`=病房名+床名 |
| 5 | Pre | `MedicationRequestQueryService#listMedicationRequestListModelsByIds` | ② 的 medReqIds | 剂量/用法/频次真源 |
| 6 | Pre | `ProductDefinitionBatchLoader#loadAsMap` + 用法/频次/剂量单位码表 | ② `medicationId` | 名称/规格；`METHOD_CODE`/`DOSE_UNIT`/`FrequencyOptionLoader` |
| 7 | Asm | `ListAssembler#buildPatientPickupGroups` | ②④⑤⑥ | 患者分组 + medReqId 合并 + `displayQuantityText` + 剂量用法频次回填 |
| 8 | Asm | `Mapper#toGetMedicationSummarySlipPatientDetailResponse` | ⑦ | — |
| — | — | — | — | **仅读路径** |

### 3.4.2 IF-IP-SUM-10 `listCancelExecuteReturnMedicineLines`（取消执行退药清单）

**HTTP**：`POST /api/app/nurseStationDispenseSummary/listCancelExecuteReturnMedicineLines`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterIds` | `List<Long>` | Y | 左侧已选就诊；非空 |
| `medicineCategoryTab` | `String` | Y | `west` / `tcm` |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `patientGroups` | `List<NurseStationCancelExecuteReturnPatientGroupDto>` | 按就诊（单患者）分组 |

**DTO：`NurseStationCancelExecuteReturnPatientGroupDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` / `patientId` | `Long` | 就诊 / 患者 |
| `patientName` / `bedDisplay` | `String` | 姓名 / 床号 |
| `returnLines` | `List<NurseStationCancelExecuteReturnLineDto>` | 退药行 |

**DTO：`NurseStationCancelExecuteReturnLineDto`（摘要）**

| 字段 | 说明 |
| --- | --- |
| `dispenseId` / `medReqId` / `medicationId` | 退药发放 / 原药嘱 / 药品 |
| `medicationName` / `specificationText` | 目录展示 |
| `quantity` / `unitCode` / `unitCodeText` | 退药量（绝对值） |
| `refundStatusEnum` / `refundStatusEnumText` | `PENDING_REFUND` / `REFUNDED` |
| `locationName` / `procedureId` | 药房、CANCEL 行 |
| `cancelOperatorId` / `cancelOperatorName` | 取消执行人 / 退药人（CANCEL.`recorder_id`） |
| `cancelOccurrenceTime` | 取消执行时间点时刻（CANCEL.`occurrence_time`，继承原计划点） |
| `totalPrice` | 原 COMPLETED 槽位账单 `total_price` 绝对值之和（含取消后软删 ChargeItem） |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-SUM-001** | 就诊须落在管辖病区可见集合 |
| **BR-IP-SUM-019** | 仅 `REFUND` + 待退/已退 + Procedure.`CANCEL`；中西药按原药嘱 `tcmFlag` |
| 写禁 | **禁止**调用 `confirmInpatientMedicineReturn` 等药房写能力 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IP-SUM-101** | 未选患者 / Tab 非法 / 无权 | 请先选择患者或无权访问 |
| **EX-IP-SUM-102** | 其他异常 | 加载失败，请稍后重试 |

**Assembler 方法签名（`NurseStationDispenseSummaryListAssembler`）**

- `buildCancelExecuteReturnPatientGroups(...)`
- `matchesMedicineCategoryTab(MedicationRequest, String)`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | encounterIds、medicineCategoryTab | 空/非法 → **EX-IP-SUM-101** |
| 2 | Gate | 管辖病区可见性 | managedWardIds + encounterIds | 无可见 → **EX-IP-SUM-101** |
| 3 | Pre | `MedicationDispenseQueryService#listCancelExecuteRefundDispensesByEncounterIds` | 可见 encounterIds | REFUND + 待退/已退 + procedureId |
| 4 | Pre | `ProcedureQueryService#listByIds` | ③ procedureIds | 仅保留 `status_enum=CANCEL` |
| 5 | Pre | `MedicationRequestQueryService#mapEntitiesByIds` | ④ medReqIds | 中西药 Tab 收窄 |
| 6 | Pre | 产品目录 + Location 批量 | medicationId / locationId | 名称规格药房 |
| 7 | Asm | `ListAssembler#buildCancelExecuteReturnPatientGroups` | ⑤⑥ | 按就诊分组 |
| — | — | — | — | **仅读路径** |

### 3.5 IF-IP-SUM-04 `createMedicationSummary`（汇总保存）

**HTTP**：`POST /api/app/nurseStationDispenseSummary/createMedicationSummary`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseItems` | `List<CreateMedicationSummaryDispenseItemDto>` | Y | 勾选发放行；1～500 |

**DTO：`CreateMedicationSummaryDispenseItemDto`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseId` | `Long` | Y | 发放行 id |
| `encounterId` | `Long` | Y | 就诊（可见性交叉校验） |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summaryNos` | `List<String>` | 本次生成的汇总单号（跨药房可多张） |
| `message` | `String` | 成功提示；多药房时可提示「已按药房拆分」 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-SUM-003** | 全部 PREPARATION 且 `summary_no` 空 |
| **BR-IP-SUM-016** | 勾选行对应药嘱不得为 `DISCHARGE_MEDICATION`；否则 **EX-IP-SUM-207** |
| **BR-IP-SUM-005** | 按 `location_id` 拆单 |
| **BR-IP-SUM-006** | 合并键 locationId（发放药房）+ medicationId + chargeUnitPrice；跨药房禁止合并 |
| **BR-IP-SUM-007/008** | unitBands 完整；逐行 ratioToSplit |
| **BR-IP-SUM-010** | Supply 行 `lot_number=null` |
| **BR-IP-SUM-012** | 同事务整批 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IP-SUM-101** | 就诊不可见 | 请先选择患者或无权访问 |
| **EX-IP-SUM-201** | 已汇总/非 PREPARATION | 所选药品已汇总，请勿重复操作 |
| **EX-IP-SUM-202** | unitBands/ratio 失败 | 药品单位槽不完整，或发放行单位无法换算为拆零数量，请联系管理员 |
| **EX-IP-SUM-203** | ChargeItem 缺失 | 缺少执行账单，无法汇总，请确认医嘱已执行 |
| **EX-IP-SUM-204** | 其他域校验失败 | 汇总保存失败，请检查所选明细状态 |
| **EX-IP-SUM-207** | 勾选含出院带药 | 出院带药不可汇总领药，请到药房按患者发药 |

**Mapper 接口方法签名**

- `toSummarizeMedicationDispensesCommand(Long locationId, List<Long> dispenseIds, Long operatorId): SummarizeMedicationDispensesCommand`
- `toCreateSummarySupplyRequestCommand(String summaryNo, List<SummaryMergeGroup> groups, Long applicantId, Long operatorId): CreateSummarySupplyRequestCommand`
- `toCreateMedicationSummaryResponse(List<String> summaryNos): CreateMedicationSummaryResponse`

**Assembler 方法签名（`NurseStationDispenseSummaryWriteAssembler`）**

- `buildMergeGroupsByLocationAndPrice(List<MedicationDispense> dispenses, Map<Long, ChargeItem> chargeByDispenseId, Map<Long, ProductDefinitionDisplayDto> productByMedId): Map<Long, List<SummaryMergeGroup>>`
- `computeSplitQuantityForDispense(MedicationDispense dispense, ProductDefinitionDisplayDto product): BigDecimal`
- `validateMergeGroupUnitBands(SummaryMergeGroup group): void`

**内部结构 `SummaryMergeGroup`（应用层，非 API 出参）**

| 字段 | 说明 |
| --- | --- |
| `locationId` | 发放药房（`MedicationDispense.location_id`；合并键组成部分） |
| `medicationId` | 药品 |
| `chargeUnitPrice` | 分组单价 |
| `splitQuantitySum` | 组内拆零数量累加 |
| `splitUnitCode` | 写 Supply 的 unit_code |
| `patientId` | 取组内首行 patient（同单通常同病区批次） |
| `dispenseIds` | 组内发放行 id 列表 |

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `dispenseItems` 1～500 | 超上限 → **EX-IP-SUM-204** |
| 2 | Gate | 可见性 | 各 `encounterId` | **EX-IP-SUM-101** |
| 3 | Pre | `MedicationDispenseQueryService#listMedicationDispensesByIds` | dispenseIds | 强加载实体 |
| 4 | Pre | 状态门控 | ③ | 须全 PREPARATION 且无 summaryNo；否则 **EX-IP-SUM-201** |
| 4b | Pre | 出院带药 Gate | ③ `medReqId` → `MedicationRequestQueryService#listByIds` | `therapy_enum=DISCHARGE_MEDICATION` → **EX-IP-SUM-207**（**BR-IP-SUM-016**） |
| 5 | Pre | `ChargeItemQueryService#listChargeItemsForMedicationDispensePricing` | 按 encounter 分批 | 缺行 → **EX-IP-SUM-203**；建 dispenseId→unitPrice |
| 6 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | ③ medicationIds | unitBands |
| 7 | Pre | `WriteAssembler#buildMergeGroupsByLocationAndPrice` | ③⑤⑥ | **BR-IP-SUM-005/006**；组内 **BR-IP-SUM-008** 累加 |
| 8 | Pre | `WriteAssembler#validateMergeGroupUnitBands` | ⑦ 每组 | 失败 → **EX-IP-SUM-202** |
| 9 | Cmd | **循环每个 `locationId` 药房组**（以下 9a～9b 为一组） | ⑦ 子 Map | 多药房 → 多个 summaryNo |
| 9a | Cmd | `MedicationDispenseDomainService#summarizeMedicationDispenses` | 该组全部 dispenseIds + locationId | **写库点 ①**；返回 **summaryNo** |
| 9b | Cmd | `SupplyRequestDomainService#createSummarySupplyRequest` | busNo=**summaryNo**；type 27；初态 **PENDING_APPROVAL**；applyTime **null**；`source_*`/`purpose_*`=发放药房+推导类型 | **写库点 ②** |
| 10 | Asm | `Mapper#toCreateMedicationSummaryResponse` | 各组 summaryNo 列表 | — |

> **事务**：步骤 9a～9b 对所有药房组在同一 `@Transactional` 内；**任一组失败则全部回滚**（**BR-IP-SUM-012**）。**不调用** `createSummarySupplyDelivery`。

### 3.6 IF-IP-SUM-05 `submitMedicationSummary`（发送汇总单）

**HTTP**：`POST /api/app/nurseStationDispenseSummary/submitMedicationSummary`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `summaryNos` | `List<String>` | Y | 待发送汇总单号；1～50 |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summaryNos` | `List<String>` | 已成功发送的汇总单号 |
| `message` | `String` | 成功提示 |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IP-SUM-103** | 单号不存在或非 type 27 | 汇总单不存在或已失效，请刷新 |
| **EX-IP-SUM-205** | 状态不允许/域拒绝 | 发送失败，请检查汇总单状态 |

**Mapper 接口方法签名**

- `toSubmitApprovalCommand(String summaryNo, Long applicantId, Long operatorId): SubmitApprovalCommand`
- `toSubmitMedicationSummaryResponse(List<String> summaryNos): SubmitMedicationSummaryResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `summaryNos` 1～50 非空 | **EX-IP-SUM-205** |
| 2 | Gate | 操作人/applicant | Resolver | **EX-IP-SUM-205** |
| 3 | Cmd | **循环每个 summaryNo** | — | 同事务 |
| 3a | Pre | `getSupplyByBusNo` + type 27 校验 | summaryNo | **EX-IP-SUM-103** |
| 3b | Cmd | `SupplyRequestDomainService#submitApproval` | busNo + applicantId | **PENDING_APPROVAL → APPROVAL** |
| 4 | Asm | `Mapper#toSubmitMedicationSummaryResponse` | ③ 列表 | — |

### 3.7 IF-IP-SUM-08 `cancelMedicationSummary`（撤销汇总单）

**HTTP**：`POST /api/app/nurseStationDispenseSummary/cancelMedicationSummary`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `summaryNos` | `List<String>` | Y | 待撤销汇总单号；1～50；须 **PENDING_APPROVAL** |

**Response**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `summaryNos` | `List<String>` | 已成功撤销的汇总单号 |
| `message` | `String` | 成功提示（引导回明细 Tab 重选） |

**失败码**

| 码 | 场景 | 用户提示 |
| --- | --- | --- |
| **EX-IP-SUM-103** | 单号不存在或非 type 27 | 汇总单不存在或已失效，请刷新 |
| **EX-IP-SUM-206** | 非 PENDING_APPROVAL / 域拒绝 | 撤销失败，仅待发送汇总单可撤销 |

**Mapper 接口方法签名**

- `toCancelMedicationDispenseSummaryCommand(String summaryNo, Long operatorId): CancelMedicationDispenseSummaryCommand`
- `toRemoveByBusNoCommand(String summaryNo, Long operatorId): RemoveByBusNoCommand`
- `toCancelMedicationSummaryResponse(List<String> summaryNos): CancelMedicationSummaryResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `summaryNos` 1～50 非空 | **EX-IP-SUM-206** |
| 2 | Gate | 操作人 | `CurrentOperatorResolver#requireOperatorId` | **EX-IP-SUM-206** |
| 3 | Cmd | **循环每个 summaryNo** | — | 同事务 |
| 3a | Pre | `getSupplyByBusNo` + type 27 + **PENDING_APPROVAL** | summaryNo | **EX-IP-SUM-103/206**（**BR-IP-SUM-014**） |
| 3b | Cmd | `MedicationDispenseDomainService#cancelMedicationDispenseSummary` | summaryNo | **SUMMARIZED→PREPARATION**，清 summary_no |
| 3c | Cmd | `SupplyRequestDomainService#removeByBusNo` | busNo=summaryNo | Supply 汇总行逻辑删除 |
| 4 | Asm | `Mapper#toCancelMedicationSummaryResponse` | ③ 列表 | — |

### 3.8 IF-IP-SUM-06 / IF-IP-SUM-07（打印）

| IF | 约定 |
| --- | --- |
| IF-IP-SUM-06 | 前端基于 IF-IP-SUM-01 已加载数据或再次调用 `pagePendingDispenseSummaryLines`；**无后端写操作** |
| IF-IP-SUM-07 | 前端**须**调用 IF-IP-SUM-03 组装打印（含 `applicantDepartmentName` 与拼音序 `drugRows`）；**禁止**仅用 IF-02 列表缓存；PrintData 键对齐 scenario `ip-dispenseSummary-print`；**无后端写操作**；发送后自动打印由本机开关控制（产品默认开；院点可 `#/` override 未设置默认） |

## 4. QueryService 与领域事件

| 项 | 约定 |
| --- | --- |
| 读侧 | 本 Tab **不**经废弃 ReadModel；一律 **DM §6.2 QueryService** |
| ChargeItem | IF-01 单价由 `pageMedicationDispensePickupPending` 内协作加载；IF-04 写前 App 再读 `listChargeItemsForMedicationDispensePricing`；**全程禁止写** |
| 事件刷新 | IF-IP-SUM-04 发布 `MedicationDispenseSummarizedEvent`、`SupplyRequestCreatedEvent`；IF-IP-SUM-05 发布 `SupplyRequestSubmittedEvent`；IF-IP-SUM-08 发布 `MedicationDispenseSummaryCancelledEvent`、`SupplyRequestDeletedEvent`；IF-02/03 **最终一致**——用户查询刷新即可；**AS-IP-PHA-002** 消费待核发队列 |

```text
IF-04/05/08 Cmd 链 → 域事件 → 汇总 Tab / 待汇总明细 / 药房待核发读侧刷新
ChargeItem 事实表：本 Tab 只读，执行阶段已写入（AS-IP-NUR-004）
SupplyDelivery：本 AS 不创建；药房核发（AS-IP-PHA-002）时创建
```

## 5. 写库顺序（核心写接口视角）

本模块写接口：**IF-IP-SUM-04**（汇总保存）、**IF-IP-SUM-05**（发送）、**IF-IP-SUM-08**（撤销汇总）。

| 顺序 | IF | App 方法 | Cmd 序号 | 领域调用 | 聚合写入 |
| --- | --- | --- | --- | --- | --- |
| 1 | IF-IP-SUM-04 | `createMedicationSummary` | 9a | `MedicationDispenseDomainService#summarizeMedicationDispenses` | `med_medication_dispense` → SUMMARIZED + summary_no |
| 2 | IF-IP-SUM-04 | 同上 | 9b | `SupplyRequestDomainService#createSummarySupplyRequest` | `wkf_supply_request` type 27 多行，**PENDING_APPROVAL** |
| 3 | IF-IP-SUM-05 | `submitMedicationSummary` | 3b | `SupplyRequestDomainService#submitApproval` | `wkf_supply_request` → **APPROVAL** + apply_time |
| 4 | IF-IP-SUM-08 | `cancelMedicationSummary` | 3b | `MedicationDispenseDomainService#cancelMedicationDispenseSummary` | `med_medication_dispense` → PREPARATION，清 summary_no |
| 5 | IF-IP-SUM-08 | 同上 | 3c | `SupplyRequestDomainService#removeByBusNo` | `wkf_supply_request` 逻辑删除 |

**事务边界**

| 项 | 约定 |
| --- | --- |
| 边界 | `createMedicationSummary` / `submitMedicationSummary` / `cancelMedicationSummary` 方法级 **`@Transactional(rollbackFor = Exception.class)`** |
| 失败回滚 | Pre/Gate 抛异常不写库；Cmd 任一步失败 **整批回滚** |
| 幂等 | 重复汇总 **EX-IP-SUM-201**；重复发送依赖域 `assertSubmittable` → **EX-IP-SUM-205**；非 PENDING_APPROVAL 撤销 → **EX-IP-SUM-206** |

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 重复汇总 | 同一 dispenseId 第二次提交返回 **EX-IP-SUM-201** | `NurseStationDispenseSummaryAppService#createMedicationSummary` 步骤 4 | §3.5 序号 4 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 乐观并发 | 依赖发放行状态 PREPARATION 门控；并发双提一行 → 一成功一 **201** | 同上步骤 4 + 域 `applySummaryState` | §3.5 序号 4、9a |

### 6.3 重试与容错

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 读接口 | 失败 **EX-IP-SUM-102** 可重试 | 各读方法 catch 边界 | §3.2～3.4 |
| 写接口 | 域异常统一 **EX-IP-SUM-204** 或细分 201～203 | `createMedicationSummary` | §3.5 |

### 6.4 数据脱敏

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 患者信息 | 仅返回护士站可见 encounter 下患者展示字段 | `ListAssembler#buildPatientGroups` | §3.2 序号 10 |

### 6.5 审计日志

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 汇总审计 | 记录 operatorId、summaryNos（保存）；applyTime（发送时）；领药人由药房核发写入 | 写 Command `operatorId` / Supply `applicantId` | §3.5 序号 9a～9b；§3.6 序号 3b |

## 7. 测试要点

### 7.1 功能测试

| 用例 ID | 场景 | IF | 优先级 | 自动化归属 |
| --- | --- | --- | --- | --- |
| TC-IP-SUM-01-001 | 多就诊待汇总分页 + 中西药/长临嘱过滤 | 01 | P0 | 集成 |
| TC-IP-SUM-01-002 | queryDeadline 截止过滤 | 01 | P0 | 集成 |
| TC-IP-SUM-01-003 | 出院带药执行后不出现在待汇总（**BR-IP-SUM-016**） | 01 | P0 | 集成 |
| TC-IP-SUM-04-003 | 勾选出院带药发放行 → **EX-IP-SUM-207** | 04 | P0 | 集成 |
| TC-IP-SUM-02-001 | 汇总单头分页 + 发放态展示 | 02 | P0 | 集成 |
| TC-IP-SUM-02-002 | 单头 `purposeLocationName` 列表展示 | 02 | P1 | 前端/E2E |
| TC-IP-SUM-03-001 | 合并明细单价/总价；无批号字段 | 03 | P0 | 集成 |
| TC-IP-SUM-03-002 | `displayQuantityText`：15 拆零→`15支`；禁止 `1盒5支`；金额仍只读累加 | 03 | P0 | 集成 |
| TC-IP-SUM-09-001 | 摆药：多患者分组；同 medReqId 数量相加；无批号 | 09 | P0 | 集成 |
| TC-IP-SUM-09-002 | 摆药 `displayQuantityText` 与 IF-03 同口径 | 09 | P0 | 集成 |
| TC-IP-SUM-04-001 | 同药同价合并 + lot 空 + SUMMARIZED | 04 | P0 | 集成 |
| TC-IP-SUM-04-002 | 同 itemId 不同单价两行 | 04 | P0 | 集成 |
| TC-IP-SUM-04-003 | 跨药房两 summaryNo | 04 | P0 | 集成 |
| TC-IP-SUM-04-004 | 混单位 ratioToSplit 累加 | 04 | P0 | 集成 |
| TC-IP-SUM-04-005 | 重复汇总 **201** | 04 | P0 | 集成 |

### 7.2 非功能测试

| 用例 ID | 场景 | 优先级 |
| --- | --- | --- |
| TC-IP-SUM-NFR-001 | 500 行批量汇总性能 | P1 |
| TC-IP-SUM-NFR-002 | 并发双提同一 dispenseId | P0 |

### 7.3 查询结果一致性

| 项 | 验证 |
| --- | --- |
| IF-03 totalPrice | 等于组内 ChargeItem.total_price 之和，≠ unitPrice×itemQuantity |
| IF-03 displayQuantityText | 15 拆零 → `15支`；禁止满包+余量；`itemQuantity` 仍为拆零持久值；`totalPrice` 不重算 |
| IF-04 后 | ChargeItem 条数与金额不变 |

### 7.4 集成测试

| 项 | 验证 |
| --- | --- |
| 与 AS-IP-NUR-004 | 执行后 IF-01 可查到 PREPARATION 行 |
| 与 AS-IP-PHA-002 | 汇总后药房可见待发汇总（事件链） |

### 7.5 测试缺口跟踪

| 项 | 说明 |
| --- | --- |
| E2E 打印 | IF-05/06 前端模板，后端无独立用例 |

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.18 | 2026-10-16 | **BR-IP-SUM-020**：IF-03/09 回填 `applicantDepartmentName`；合并行按 `pyStr` 首字母序；打印须走 IF-03；对齐 **AS v1.20**（代码先行回写） |
| v1.17 | 2026-10-15 | **IF-IP-SUM-10** `listCancelExecuteReturnMedicineLines` + **BR-IP-SUM-019**；域方法 `listCancelExecuteRefundDispensesByEncounterIds`；对齐 **AS v1.19**（代码先行回写） |
| v1.16 | 2026-10-13 | **IF-IP-SUM-02 管辖病区门控**：`listEncounterIdsByWardLocationIds` + `listDistinctSummaryNosByEncounterIds` → `busNoIn`；详情/发送/撤销同口径；对齐 **AS v1.18** |
| v1.15 | 2026-10-13 | **BR-IP-SUM-015**：`displayQuantityText` 改为 `#buildPlainQuantityText` 行/操作单位直显；禁止满包+余量；对齐 **AS v1.17** |
| v1.14 | 2026-10-13 | **BR-IP-SUM-018 纠偏**：麻醉识别改为申请单号 AA 前缀；对齐 **AS v1.16** |
| v1.13 | 2026-10-13 | **BR-IP-SUM-018**：`PagePendingDispenseSummaryLinesRequest.anesthesiaApplyOnly` + `InpatientNurseStationAnesthesiaApplyScopeSupport`；对齐 **AS v1.15** |
| v1.12 | 2026-10-12 | **IF-IP-SUM-07 / UI**：发送后自动打印默认值可院点 `#/` override（产品默认开）；对齐 **AS v1.14** |
| v1.11 | 2026-08-05 | **BR-IP-SUM-017 退役**：移除 `StandingOrderPackageBatchPendingDispenseFilterSupport` 及 IF-01 步骤 6b；待汇总仅 `planned ≤ queryDeadline`；对齐 **AS v1.13** |
| v1.10 | 2026-08-04 | 对齐 AS v1.12：**BR-IP-SUM-017**——不可拆时长嘱 IF-01 整盒补齐；`StandingOrderPackageBatchPendingDispenseFilterSupport`；写路径不变（**v1.11 废止**） |
| v1.9 | 2026-07-18 | 对齐 AS v1.11：新增 **IF-IP-SUM-09** `getMedicationSummarySlipPatientDetail`；`MedicationDispenseSummaryItemQueryModel` 增 `medReqId`/剂量/用法/频次/`dose`；摆药回填 **MedicationRequest** + 病房名；`ListAssembler#buildPatientPickupGroups` |
| v1.8 | 2026-07-17 | 对齐 AS v1.10：**BR-IP-SUM-016** / **EX-IP-SUM-207**——`resolveMedReqIds` 剔除出院带药；IF-04 写前 Gate 拒绝 `DISCHARGE_MEDICATION` |
| v1.7 | 2026-07-17 | TimePoint 增 `recordedTime`；「领药时间」展示优先 recorded，勾选仍锚 `plannedDispenseTime`；对齐 AS v1.9 |
| v1.6 | 2026-07-16 | 对齐 AS v1.8：`plannedDispenseTime` 口径=上游 **AS-IP-NUR-004** planTime（执行 actualTime 不改写） |
| v1.5 | 2026-06-17 | 对齐 AS v1.7：IF-02 前端汇总药房列；IF-03 新增 **`displayQuantityText`** + **BR-IP-SUM-015**（满包 DOWN + 拆零余量）；`ListAssembler#buildPackageSplitDisplayQuantityText` |
| v1.4 | 2026-06-11 | IF-02：移除 `CurrentManagedLocationIdsResolver` 药房自动门控；`purposeLocationId` 仅显式传入时收窄 |
| v1.3 | 2026-06-06 | 对齐 AS v1.5：新增 **IF-IP-SUM-08** `cancelMedicationSummary`（PENDING_APPROVAL 撤销）；**EX-IP-SUM-206**；§5 写库顺序增补 3b/3c |
| v1.2 | 2026-06-06 | 审批链对齐 AS v1.4：IF-04 汇总保存（PENDING_APPROVAL，无 Delivery）；新增 IF-05 发送；打印顺延 IF-06/07 |
| v1.1 | 2026-06-06 | 应用层成码闭环对齐：§2.1 五类 Resolver 替代 `ICurrentUser`；§2.3 常量锚点与 `EX_IP_SUM_*` 已实现；§3.x 分页 **`totalCount`** + `PagedResultBuilder`；§2.4 成码交付状态改为已落地 |
| — | — | **DOC-CONFLICT（仍有效）**：AS **BR-IP-SUM-002** 药嘱状态 **COMPLETED/STOPPED**；待发场景实现采用 **VERIFIED/STOPPED**（与 **AS-IP-NUR-004** 执行列表一致） |
