# 门诊药房 · 退药管理 · 应用服务详细设计（SDD）

> **文档职责**：基于 AS-OP-PHA-002 业务契约，给出可直接指导代码实现的详细设计，包括包路径、接口契约、编排序列、事务边界与测试矩阵。
>
> **来源权威**：AS-OP-PHA-002（业务意图）、DM-MED-002/MED-003（药品发放/申请域能力）、对应DM §6.2。**本页仅药品退药，不含耗材。**
>
> **编码**：AS-OP-PHA-002-SDD · **版本**：v1.5 · **日期**：2026-08-19 · **实现阶段口径**：目标态全量契约（对齐《代码生成通用规范》与 zxplat 实现）

---

## 1. 实现边界

### 1.1 目标与边界

| 维度 | 内容 |
|------|------|
| **模块** | 门诊药房退药管理（OutpatientPharmacyReturnMedication） |
| **边界** | 加载初始化元数据；分页查询退药患者；查询退药明细；确认退药/拒绝退药。医生站退费申请生成的反向发放单（PENDING_REFUND）为本场景入口 |
| **编排权威** | AS-OP-PHA-002 §4 接口编排说明书 |
| **方法权威** | DM-MED-002 §6.1（药品发放域服务）、对应DM §6.2 §3 |
| **范围** | **仅** `MedicationDispense` / `MedicationRequest`；队列分页键仅药品发放表；批号取自发放单 |
| **严格对齐** | 状态机：PENDING_REFUND → REFUNDED/DECLINED；不支持部分退药；reason字段记录退药/拒绝原因 |

### 1.2 术语口径

| 术语 | 字段口径 | 说明 |
|------|----------|------|
| **退药状态** | `status_enum`（String） | 取值：`PENDING_REFUND`（待退药）、`REFUNDED`（已退药）、`DECLINED`（已拒绝） |
| **退药原因** | `reason`（String） | 手写输入，确认退药时可选填，拒绝退药时必填 |
| **退药数量** | `dispense_quantity`（Decimal） | 全量退回时等于 `quantity`，不支持部分退药 |
| **退药时间** | `dispense_time`（DateTime） | 确认退药或拒绝退药的操作时间 |
| **退药人** | `practitioner_id`（Long） | 执行退药/拒绝操作的药师标识 |

---

## 2. 代码结构

### 2.1 包路径

```
com.zxplat.domain.application.outpatient.pharmacy.returnmedicine/
├── service/
│   └── OutpatientPharmacyReturnMedicineAppService.java
├── request/
│   ├── LoadReturnMedicineInitRequest.java
│   ├── PageReturnPatientRequest.java
│   ├── GetReturnMedicineDetailRequest.java
│   ├── ConfirmMedicineReturnRequest.java
│   └── ReturnMedicineItemRequest.java
├── response/
│   ├── LoadReturnMedicineInitResponse.java
│   ├── PageReturnPatientResponse.java
│   ├── ReturnMedicineDetailResponse.java
│   └── ConfirmMedicineReturnResponse.java
├── dto/
│   ├── DepartmentOptionDto.java
│   ├── RefundStatusOptionDto.java
│   ├── ReturnPatientRowDto.java
│   ├── ReturnMedicineDetailRowDto.java
│   └── FailedReturnItemDto.java
└── mapper/
    ├── OutpatientPharmacyReturnMedicineMapper.java
    └── OutpatientPharmacyReturnMedicineMapperImpl.java
```

### 2.2 依赖注入（构造器注入，与仓库实现对齐）

| 类型 | 注入名称 | 来源 |
|------|----------|------|
| `OutpatientPharmacyReturnMedicineAppService` | 本应用服务 | 应用层 |
| `MedicationDispenseDomainService` | medicationDispenseDomainService | DM-MED-002 §6.1 |
| `MedicationDispenseQueryService` | medicationDispenseQueryService | DM-MED-002 读侧（Pre 加载发放快照） |
| `MedicationRequestDomainService` | medicationRequestDomainService | DM-MED-003 §6.1 |
| `InventoryItemDomainService` | inventoryItemDomainService | DM-INV-001 §6.1 |
| `InventoryItemQueryService` | inventoryItemQueryService | DM-INV-001 读侧（批号定位库存项） |
| `TraceNumberDomainService` | TraceNumberDomainService | DM-TRC-001 §6.1 |
| `MedicationDispenseQueryService` | medicationDispenseQueryService | IF-PHA-02-02：`mapAggregatedRefundStatusCodeByEncounterIds`；IF-PHA-02-03 收费汇总读侧 |
| `EncounterContextReader` | encounterContextFacade | IF-PHA-02-02 `listOutpatientContextsByEncounterIdsOrdered`；IF-PHA-02-03 `mustGetOutpatientContextByEncounterId` |
| `PharmacyEncounterQueueLoader` | pharmacyEncounterQueueLoader | IF-PHA-02-02：`pageEncounterIdsForOutpatientPharmacyReturn`（`application.outpatient|inpatient.pharmacy/dispense/support`） |
| `ReturnMedicineDetailLoader` | returnMedicineDetailLoader | IF-PHA-02-03：`listReturnMedicineDetails(ReturnMedicineDetailQuery)` → `List<ReturnMedicineDetailReadRow>`（批量装配名称/单位/状态/追溯码） |
| `OutpatientReturnMedicinePatientQueueAssembler` | outpatientReturnMedicinePatientQueueAssembler | IF-PHA-02-02：就诊上下文 + 聚合退药态 → `ReturnMedicinePatientQueueRow` |
| `EncounterContractDisplayAssembler` | encounterContractDisplayAssembler | IF-PHA-02-02：本页就诊批量回填 `contractDisplay`（与发药队列/医生站卡片同口径） |
| `EncounterQueryService` | encounterQueryService | 薄就诊门控 |
| `ChargeItemQueryService` | chargeItemQueryService | 收费明细读侧（IF-PHA-02-03 按发放单汇总 `totalPrice`，与发药同源） |
| `OrganizationQueryService` | organizationQueryService | 机构读侧（门诊挂号科室列表） |
| `CurrentOperatorResolver` | IF-PHA-02-04 写路径 **`requireOperatorId`**（**B.6.5**） |
| `CurrentPractitionerResolver` | IF-PHA-02-04 可选 **`findPractitionerIdOrNull`**（**B.6.5**） |
| `CurrentManagedLocationIdsResolver` | managedLocationIdsResolver | IF-PHA-02-02 队列 **`locationIds`** 解析（**B.6.5**） |
| `LocationQueryService` | locationQueryService | 库位读侧（明细展示名等；**队列 Gate 不用** `listPharmaciesByOrganization`） |
| `ProductDefinitionBatchLoader` | productDefinitionBatchLoader | 产品定义QueryService（药品退库单位换算） |
| `OutpatientPharmacyReturnMedicineMapper` | returnMedicineMapper | 手写 `*MapperImpl`（**非** MapStruct） |

### 2.3 领域方法白名单（仓库实际符号）

| 聚合（域） | 方法 | 用于 IF | 说明 |
|------------|------|---------|------|
| **MedicationDispenseDomainService** | `refundDispensedMedication(RefundDispensedMedicationCommand)` | IF-PHA-02-04 | 退药确认：仅 REFUND 待退行 → REFUNDED；**退药与原发药单无关联** |
| **MedicationDispenseDomainService** | `declinePendingRefundMedicationDispense(DeclinePendingRefundMedicationDispenseCommand)` | IF-PHA-02-04 | 拒绝退药：状态推进为 DECLINED |
| **MedicationRequestDomainService** | `completeMedicationRequestAfterRefund(Long requestId, Long operatorId)` | IF-PHA-02-04 | 退药完成后负向申请完结 |
| **InventoryItemDomainService** | `increaseQuantities(IncreaseQuantityCommand)` | IF-PHA-02-04 | 库存回补（按发放单批号定位；命令内多 items） |
| **TraceNumberDispenseTraceOrchestrator** | `recordReturnInLines(...)` | IF-PHA-02-04 | 退药 IN：**镜像**原发 OUT 每码实扣（**DD §5** / **BR-TRC-008**）；先退药行 `bus_no`，无则按拷贝 `trace_no` 回落未回冲 OUT；原发未扫跳过 |
| **MedicationDispenseQueryService** | `pageReturnMedicinePatients(PageReturnPatientQuery)` | IF-PHA-02-02 | 分页查询退药患者 |
| **MedicationDispenseQueryService** | `listReturnMedicineDetails(ReturnMedicineDetailQuery)` | IF-PHA-02-03 | 查询退药明细（优先发放事实表，查 MedicationDispense 主表） |
| **MedicationDispenseQueryService** | `pageEncounterIdsForOutpatientPharmacyReturn` | IF-PHA-02-02 | 就诊分页键（`springPageIndex` / `getBoundedPageSize`，上限 100） |
| **MedicationDispenseQueryService** | `mapAggregatedRefundStatusCodeByEncounterIds` | IF-PHA-02-02 | 本页就诊批量聚合 `refundEnum`（`findByEncounterIdIn`，禁止按就诊循环查库） |
| **MedicationDispenseQueryService** | `listOutpatientReturnMedicationDispensesForDetail` | IF-PHA-02-03 | 明细发放事实（与队列同源） |
| **OrganizationQueryService** | `listOrganizationsForOutpatientRegistration()` | IF-PHA-02-01 | 科室下拉（启用诊所分类） |

### 2.4 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
|------|--------------|-------------|------|
| 领域枚举 | `com.zxplat.domain.share.enums.DispenseStatus` | DM-MED-002 §2.2 状态机表 | `PENDING_REFUND`(16)、`REFUNDED`(12)、`DECLINED`(7)；用于退药状态流转 |
| 领域枚举 | `com.zxplat.domain.share.enums.RequestStatus` | DM-MED-003 §4.1 | `COMPLETED`(7)；退药完成后请求状态 |
| Request 字段 | `ConfirmMedicineReturnRequest.reason` | DM-MED-002 §3.1 `reason` 字段（目标态待生成） | 退药/拒绝原因，手写输入，目标态新增字段 |
| Request 字段 | `ConfirmMedicineReturnRequest.items` | AS-OP-PHA-002 §1.3 术语口径 | 退药项列表，每项含发放单ID、请求ID、业务表名 |
| Response 字段 | `ReturnMedicineDetailRowDto.refundEnum` | DM-MED-002 §3.1 `status_enum` | 退药状态展示值 |
| 常量 | `TableNameConstants.MED_MEDICATION_REQUEST` | DM-MED-003 实体表名 | 药品请求表名 |
| 常量 | `TableNameConstants.MED_MEDICATION_REQUEST` | DM-MED-003 | 确认项 `tableName` 固定为此值 |

### 2.5 实现口径（与 AS v1.2 一致）

| 项 | 口径 |
|----|------|
| 科室加载 | `OrganizationQueryService#listOrganizationsForOutpatientRegistration` |
| 退药原因 | 确认 → `RefundDispensedMedicationCommand.reasonText`；拒绝 → `DeclinePendingRefundMedicationDispenseCommand.declineReasonText` |
| 患者队列 | 仅 `MedicationDispenseQueryService#pageEncounterIdsForOutpatientPharmacyReturn`，**无**耗材 UNION；Asm 经 **`EncounterContractDisplayAssembler`** 回填 `contractDisplay` |
| 批号/库存 | 批号/追溯取自待退 REFUND 行；回补按待退行药品+批号+药房库位；**退药与原发药单无关联** |
| 明细 `totalPrice` | `ChargeItemQueryService#listChargeItemsByServiceIds` + 按原药嘱 `service_id`（`refund_medicine_id`）汇总 |
| 成功 `message` | `MedicationCommonConstants.OUTPATIENT_PHARMACY_RETURN_*_SUCCESS_MESSAGE` |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
|----|---------------------|------------|------|
| IF-PHA-02-01 | `loadReturnMedicineInit` | 加载退药初始化元数据 | `POST /api/app/outpatientPharmacyReturnMedicine/loadReturnMedicineInit` |
| IF-PHA-02-02 | `pageReturnPatientQueue` | 分页查询退药患者列表 | `POST /api/app/outpatientPharmacyReturnMedicine/pageReturnPatientQueue` |
| IF-PHA-02-03 | `getReturnMedicineDetail` | 查询退药明细 | `POST /api/app/outpatientPharmacyReturnMedicine/getReturnMedicineDetail` |
| IF-PHA-02-04 | `confirmMedicineReturn` | 确认或拒绝退药 | `POST /api/app/outpatientPharmacyReturnMedicine/confirmMedicineReturn` |

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
|------|------|--------|
| **Gate** | 权限/租户/参数门禁（科室权限、患者就诊权限等） | 抛业务异常，不再调用后续步骤 |
| **Pre** | 写前只读：加载发放单快照、校验状态与 BR（如状态是否为 PENDING_REFUND） | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：调用 DomainService 执行状态推进、库存回补、追溯码入库，持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | Mapper 纯 DTO 装配，无领域不变式 | — |

**记法**：`MedicationDispenseDomainService#refundDispensedMedication(RefundDispensedMedicationCommand)` 表示在 AppService 实现类中对注入的该类型 Bean 调用该方法（初稿类名 `RefundMedicationDispenseCommand` 已废弃，以仓库为准）。

---

### 3.2 IF-PHA-02-01 `loadReturnMedicineInit`（加载退药初始化元数据）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| — | — | — | 无请求体，或仅含当前上下文（租户、机构） |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `departmentOptions` | `List<DepartmentOptionDto>` | 科室下拉选列表 |
| `refundStatusOptions` | `List<RefundStatusOptionDto>` | 退药状态下拉选列表（PENDING_REFUND、REFUNDED） |

**DepartmentOptionDto 字段表**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `departmentId` | Long | 是 | 科室标识 |
| `departmentName` | String | 是 | 科室名称 |

**RefundStatusOptionDto 字段表**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `statusCode` | String | 是 | 状态编码（`PENDING_REFUND`、`REFUNDED`） |
| `statusName` | String | 是 | 状态显示名称 |

#### 关键校验

- 无特殊业务校验，依赖租户/机构上下文加载对应科室列表

#### 失败码口径

| 失败码 | 触发条件 | 用户提示 | 可重试 |
|--------|----------|----------|--------|
| EX-PHA-002-101 | 元数据加载失败 | 数据加载失败，请稍后重试 | 是 |

#### Mapper 接口方法签名

```java
// OutpatientPharmacyReturnMedicineMapper
List<DepartmentOptionDto> mapToDepartmentOptions(List<Organization> organizations);
List<RefundStatusOptionDto> mapToRefundStatusOptions();
```

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | — | — | 库房收窄在 IF-PHA-02-02 使用 **`CurrentManagedLocationIdsResolver`**，本 IF 不强制登录 |
| 2 | Pre | `OrganizationQueryService#listOrganizationsForOutpatientRegistration()` | 启用诊所分类 | 与门诊挂号科室列表同源（见 §2.5） |
| 3 | Asm | `Mapper#mapToDepartmentOptions(...)` | 步骤2结果 | 装配科室选项 |
| 4 | Asm | `Mapper#mapToRefundStatusOptions()` | — | 装配退药状态选项（硬编码枚举） |
| 5 | Asm | 组装 `LoadReturnMedicineInitResponse` | 步骤3、4结果 | 返回初始化数据 |

---

### 3.3 IF-PHA-02-02 `pageReturnPatientQueue`（分页查询退药患者列表）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `departmentId` | Long | 否 | 科室过滤条件 |
| `searchKey` | String | 否 | 模糊查询关键字（姓名/身份证/拼音/五笔） |
| `pageNo` | Integer | 是 | 当前页码（1-based） |
| `pageSize` | Integer | 是 | 每页条数 |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `pageNo` | Integer | 当前页码 |
| `pageSize` | Integer | 每页条数 |
| `totalCount` | Long | 总记录数 |
| `content` | `List<ReturnPatientRowDto>` | 患者行数据列表（`PagedResult` 契约，B.10.11） |

**ReturnPatientRowDto 字段表**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `encounterId` | Long | 是 | 就诊标识 |
| `patientName` | String | 是 | 患者姓名 |
| `genderEnum` | String | 是 | 性别编码（`MALE`/`FEMALE`/`UNKNOWN`） |
| `genderEnumText` | String | 是 | 性别显示文本 |
| `age` | String | 是 | 患者年龄（计算值） |
| `idCard` | String | 否 | 身份证号（脱敏展示） |
| `departmentName` | String | 是 | 就诊科室名称 |
| `refundEnum` | String | 是 | 退药状态编码（`PENDING_REFUND`/`REFUNDED`） |
| `refundEnumText` | String | 是 | 退药状态显示文本 |
| `encounterNo` | String | 是 | 就诊号 |
| `receptionTime` | String | 是 | 就诊日期 |
| `contractDisplay` | String | 否 | 费用性质展示名（就诊优先合同名；与发药队列/医生站卡片同口径；无合同时为空，前端回落「自费」） |

#### 关键校验

- `pageNo` >= 1，否则抛参数校验异常
- `pageSize` 默认 10，最大 100

#### 失败码口径

| 失败码 | 触发条件 | 用户提示 | 可重试 |
|--------|----------|----------|--------|
| EX-PHA-002-101 | 查询执行失败 | 数据加载失败，请稍后重试 | 是 |

#### Mapper 接口方法签名

```java
// OutpatientPharmacyReturnMedicineMapper
PagedResult<ReturnPatientRowDto> mapToReturnPatientPage(Page<OutpatientEncounterContextQueryModel> pageResult);
```

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | 参数校验 | `pageNo`/`pageSize` | 非法分页 → EX-PHA-002-101 |
| 2 | Pre | 参数校验 | `pageNo`、`pageSize` | 校验分页参数合法性 |
| 3 | Pre | **`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty()`** | — | 非空→`PageReturnPatientQuery#locationIds`；empty 不拼谓词 |
| 4 | Pre | **`PharmacyEncounterQueueLoader#pageEncounterIdsForOutpatientPharmacyReturn`** | `PageReturnPatientQuery`（含可选 `locationIds`） | 就诊 id 分页键 |
| 5 | Pre | **`EncounterContextReader#listOutpatientContextsByEncounterIdsOrdered`** + **`MedicationDispenseQueryService#mapAggregatedRefundStatusCodeByEncounterIds`** | 本页 encounterIds | 批量上下文与聚合退药态 |
| 6 | Pre | **`OutpatientReturnMedicinePatientQueueAssembler#buildRows`** | ⑤ 的 contexts + refundCodeMap | 队列行骨架 |
| 7 | Asm | `Mapper#mapToReturnPatientRows` | ⑥ | 映射 `ReturnPatientRowDto` |
| 8 | Asm | **`EncounterContractDisplayAssembler#mapContractDisplayByEncounterIds`** + 逐行写回 `contractDisplay` | ⑦ 本页 encounterIds | 费用性质与发药/医生站卡片同口径；无合同时留空 |
| 9 | Asm | `PagedResultBuilder` | ⑦+⑧ | 返回 `PageReturnPatientResponse` |

---

### 3.4 IF-PHA-02-03 `getReturnMedicineDetail`（查询退药明细）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `encounterId` | Long | 是 | 就诊标识 |
| `refundStatus` | String | 否 | 退药状态过滤（`PENDING_REFUND`、`REFUNDED`；空表示不过滤） |

> **范围**：本 IF **仅药品**退药；Request **无** `itemTable`（与 `GetReturnMedicineDetailRequest.java` 一致）。

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `items` | `List<ReturnMedicineDetailRowDto>` | 退药明细列表 |

**ReturnMedicineDetailRowDto 字段表**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `dispenseId` | Long | 是 | 发放单标识（退药操作的主键） |
| `requestId` | Long | 是 | 请求单标识（负向请求单） |
| `refundMedicineId` | Long | 是 | 原药品请求单标识 |
| `itemId` | Long | 是 | 药品定义标识 |
| `itemName` | String | 是 | 药品名称 |
| `lotNumber` | String | 是 | 批号 |
| `quantity` | BigDecimal | 是 | 应退数量（负向单数量取反后的绝对值） |
| `unitCode` | String | 是 | 单位编码 |
| `unitCodeText` | String | 是 | 单位显示文本 |
| `totalPrice` | BigDecimal | 是 | 按原药嘱 `adm_charge_item.service_id` 汇总应退金额 |
| `doctorName` | String | 是 | 开单医生姓名 |
| `refundEnum` | String | 是 | 退药状态编码 |
| `refundEnumText` | String | 是 | 退药状态显示文本 |
| `reqStatus` | String | 是 | 请求状态编码 |
| `reqStatusText` | String | 是 | 请求状态显示文本 |
| `serviceTable` | String | 是 | 固定 `med_medication_request` |
| `traceNo` | String | 否 | 追溯码（逗号分隔多码） |

#### 失败码口径

| 失败码 | 触发条件 | 用户提示 | 可重试 |
|--------|----------|----------|--------|
| EX-PHA-002-101 | 就诊不存在或无权限 | 数据加载失败，请稍后重试 | 是 |

#### Mapper 接口方法签名

```java
// OutpatientPharmacyReturnMedicineMapper
List<ReturnMedicineDetailRowDto> mapToReturnMedicineDetailRows(List<ReturnMedicineDetailReadRow> rows);
```

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | 参数校验 | `pageNo`/`pageSize` | 非法分页 → EX-PHA-002-101 |
| 2 | Pre | 参数校验 | `encounterId` | 校验必填 |
| 3 | Pre | **`EncounterContextReader#mustGetOutpatientContextByEncounterId`** | `encounterId` | 就诊门控 |
| 4 | Pre | **`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty()`** | — | 非空→`ReturnMedicineDetailQuery#locationIds`；empty 不拼谓词（与 IF-PHA-02-02 同源） |
| 5 | Pre | **`ReturnMedicineDetailLoader#listReturnMedicineDetails`** | `ReturnMedicineDetailQuery`（Mapper 自 Request + ④） | 发放事实 + 定义/请求/医师/单位/追溯码批量装配 |
| 6 | Pre | App `applyChargeTotalPriceByOriginRequestId` | `ChargeItemQueryService#listChargeItemsByServiceIds` | 按原药嘱 `service_id` 汇总 `totalPrice`（**不**查原正向发药单） |
| 7 | Asm | `Mapper#mapToReturnMedicineDetailRows` | ⑤～⑥ | 返回 `ReturnMedicineDetailResponse.items` |

---

### 3.5 IF-PHA-02-04 `confirmMedicineReturn`（确认或拒绝退药）

#### Request

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `items` | `List<ReturnMedicineItemRequest>` | 是 | 退药项列表，至少一项 |
| `confirmType` | String | 是 | 操作类型（`CONFIRM` 确认退药、`DECLINE` 拒绝退药） |
| `reason` | String | 条件必填 | 退药原因/拒绝原因；`DECLINE` 时必填，`CONFIRM` 时可选 |

**ReturnMedicineItemRequest 字段表**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `dispenseId` | Long | 是 | 发放单标识 |
| `requestId` | Long | 是 | 请求单标识（负向请求单） |
| `tableName` | String | 是 | 业务表名（`MED_MEDICATION_REQUEST` 或 `WOR_DEVICE_REQUEST`） |

#### Response

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | Boolean | 操作是否成功 |
| `message` | String | 操作结果提示 |
| `failedItems` | `List<FailedReturnItemDto>` | 失败项列表（如有） |

**FailedReturnItemDto 字段表**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `dispenseId` | Long | 是 | 发放单标识 |
| `errorCode` | String | 是 | 错误码 |
| `errorMessage` | String | 是 | 错误信息 |

#### 关键校验

- `items` 非空，至少包含一项
- `confirmType` 只能是 `CONFIRM` 或 `DECLINE`
- `confirmType=DECLINE` 时 `reason` 必填
- 每项发放单当前状态必须是 `PENDING_REFUND`（BR-PHA-031）
- 幂等校验：排除已 `REFUNDED` 或 `DECLINED` 的记录（BR-PHA-032）

#### 失败码口径

| 失败码 | 触发条件 | 用户提示 | 可重试 |
|--------|----------|----------|--------|
| EX-PHA-002-201 | 状态不允许退药（非 PENDING_REFUND） | 当前状态不允许退药，请刷新后重试 | 否 |
| EX-PHA-002-202 | 重复退药（已 REFUNDED/DECLINED） | 该药品已退药或已拒绝，请勿重复操作 | 否 |
| EX-PHA-002-203 | 库存信息缺失 | 库存信息异常，请联系管理员 | 否 |
| EX-PHA-002-301 | 未选择退药项 | 请选择要退药的药品 | 否 |
| EX-PHA-002-302 | 拒绝原因未填写 | 拒绝退药须填写原因 | 否 |

#### Mapper 与 Command 装配

写侧 **Command 在 `OutpatientPharmacyReturnMedicineAppService` 内联装配**（批量 `RefundDispensedMedicationCommand` / `DeclinePendingRefundMedicationDispenseCommand` 等）；`OutpatientPharmacyReturnMedicineMapper` 仅承担读路径 Request→Query 与读行→DTO（见仓库 `*Mapper` / `*MapperImpl`）。

#### 领域编排序列（严格顺序，与仓库实现对齐）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
|------|------|------|----------|------|
| 1 | Gate | 参数校验 + `CurrentOperatorResolver#requireOperatorId` | `items`、`confirmType`、`reason` | 拒绝须填原因；未登录 → EX-PHA-002-101 |
| 2 | Pre | `MedicationDispenseQueryService#mustGetMedicationDispense` | 每项 `dispenseId` | 加载药品发放快照 |
| 3 | Pre | 状态校验 | 步骤2结果 | BR-PHA-031/032；确认路径收集追溯与库存回补计划 |
| 4 | Cmd | **【第一写库点】** `refundDispensedMedication` / `declinePendingRefundMedicationDispense` | 批量 Command | CONFIRM/DECLINE 互斥 |
| 5 | Cmd | **【第二写库点】** `completeMedicationRequestAfterRefund(id, operatorId)` | `requestId` | 负向药品申请完结 |
| 6 | Cmd | **【第三写库点】** **`TraceNumberDispenseTraceOrchestrator#recordReturnInLines`** | 仅 CONFIRM | 按原 OUT 每码镜像 IN（对冲行新 `bus_no` 时按拷贝 `trace_no` 回落）；**禁止**按当前剩余重分摊；原发未扫跳过 |
| 7 | Cmd | **【第四写库点】** `InventoryItemDomainService#increaseQuantities(IncreaseQuantityCommand)` | 仅 CONFIRM | 按库存项 id 合并增量；增量须为 **操作单位→拆零**（**BR-PHA-034** / **BR-OPP-017**；`MedicationProductQuantityToSplitUnitConverter`） |
| 8 | Asm | 组装 `ConfirmMedicineReturnResponse` | success/message | message 为中文成功文案常量 |

**分支说明**：
- 步骤4：`confirmType=CONFIRM` 走退药命令；`DECLINE` 走拒绝命令（仅药品域）
- 步骤6、7仅 CONFIRM 执行；DECLINE 跳过
- 事务由领域层管理；AppService **无** `@Transactional`（与 ZXPlat 应用层体例一致）

---

## 4. QueryService 与事件刷新

### 4.1 QueryService依赖

| QueryService | 查询方法 | 用于 IF | QueryService 查询语义 |
|--------|----------|---------|----------|
| **ReturnMedicinePatientQueueRow**（读服务装配） | `MedicationDispenseQueryService#pageReturnMedicinePatients` | IF-PHA-02-02 | 药品发放就诊分页键 + 门诊信息QueryService；`refundEnum` 由 `mapAggregatedRefundStatusCodeByEncounterIds` 批量聚合 |
| **ReturnMedicineDetailReadRow**（读服务装配） | `MedicationDispenseQueryService#listReturnMedicineDetails` | IF-PHA-02-03 | 优先药品发放事实装配；slip 仅主表查询 |

### 4.2 事件驱动的投影更新

| 领域事件 | 消费方（QueryService） | 更新动作 |
|----------|------------------|----------|
| `MedicationDispenseRefundedEvent` | ReturnMedicinePatientQueueRow / ReturnMedicineDetailReadRow | 刷新退药状态为 `REFUNDED`，更新退药时间/退药人 |
| `MedicationDispenseDeclinedEvent` | ReturnMedicinePatientQueueRow / ReturnMedicineDetailReadRow | 刷新退药状态为 `DECLINED`，更新拒绝原因 |
| `TraceNumberInboundedEvent` | TraceNumberQueryModel | 追加追溯码入库记录 |

---

## 5. 写库顺序（核心写接口视角）

### 5.1 IF-PHA-02-04 Cmd 落库顺序对照表

| IF | App 方法 | Cmd 序号 | 领域调用 | 聚合写入顺序 |
|----|----------|----------|----------|--------------|
| IF-PHA-02-04 | `confirmMedicineReturn` | 1 | `refundDispensedMedication` / `declinePendingRefundMedicationDispense` | 第一：药品发放单 |
| IF-PHA-02-04 | `confirmMedicineReturn` | 2 | `completeMedicationRequestAfterRefund` | 第二：负向药品申请 |
| IF-PHA-02-04 | `confirmMedicineReturn` | 3 | `recordReturnInLines` | 第三：追溯镜像 IN（仅确认退药） |
| IF-PHA-02-04 | `confirmMedicineReturn` | 4 | `increaseQuantities(IncreaseQuantityCommand)` | 第四：库存（仅确认退药） |

### 5.2 事务边界与失败回滚

| 属性 | 口径 |
|------|------|
| **事务边界** | 领域服务方法各自事务；AppService 不加 `@Transactional`；拒绝退药时仅执行 Cmd 1～2 |
| **失败回滚** | 任一 Cmd 抛出异常时，整单回滚，发放单状态不变更，库存不补，追溯码不入库 |
| **部分失败** | 同一请求内多项退药，任意一项失败则整单失败（全成功或全失败） |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 实现 | 关联步骤 |
|------|----------|
| 数据库层唯一约束：`MedicationDispense.status_enum` 幂等推进（同一 ID 重复推进终态无实际变更） | §3.5 序号 4（Pre 状态校验）、序号 5（Cmd 状态推进） |
| 业务层幂等：Pre 阶段校验状态，已 `REFUNDED`/`DECLINED` 抛异常拒绝 | §3.5 序号 4 |

### 6.2 并发控制

| 实现 | 关联步骤 |
|------|----------|
| 乐观锁：`MedicationDispense` 表 `version` 字段，并发修改时后到者失败重试 | §3.5 序号 5 |
| 用户提示：版本冲突时提示「数据已变更，请刷新后重试」 | §3.5 序号 4 |

### 6.3 重试与容错

| 实现 | 关联步骤 |
|------|----------|
| 无自动重试：业务操作失败需用户手动重新提交 | — |
| 库存回补失败：抛异常整单回滚，提示联系管理员 | §3.5 序号 8 |

### 6.4 数据脱敏

| 实现 | 关联步骤 |
|------|----------|
| 身份证号脱敏：Response 中 `idCard` 字段展示为 `110***********1234`（保留前3后4） | §3.3 Response 字段表 |

### 6.5 审计日志

| 实现 | 关联步骤 |
|------|----------|
| 操作人记录：`MedicationDispense.practitioner_id` 写入当前用户 PractitionerId | §3.5 序号 5 |
| 操作时间记录：`MedicationDispense.dispense_time` 写入当前时间 | §3.5 序号 5 |
| 原因记录：`MedicationDispense.reason` 写入退药/拒绝原因 | §3.5 序号 5 |

---

## 7. 测试要点

### 7.1 功能测试（P0）

| 测试项 | 自动化归属 | 优先级 |
|--------|------------|--------|
| 加载初始化：科室列表、退药状态选项正确渲染 | 单元测试 | P0 |
| 分页查询：按科室过滤、按关键字模糊查询 | 集成测试 | P0 |
| 查询明细：药品明细展示、状态过滤 | 集成测试 | P0 |
| 确认退药：单条药品退药成功，状态变为 REFUNDED | 集成测试 | P0 |
| 确认退药：多条药品退药成功 | 集成测试 | P0 |
| 拒绝退药：填写原因后状态变为 DECLINED | 集成测试 | P0 |
| 追溯码入库：多码正确拆分入库 | 集成测试 | P0 |
| 库存回补：数量正确加回 | 集成测试 | P0 |

### 7.2 非功能测试（P0）

| 测试项 | 自动化归属 | 优先级 |
|--------|------------|--------|
| 幂等测试：重复提交已退药单被拒绝 | 集成测试 | P0 |
| 并发测试：双开同一退药单，后到者失败 | 并发测试 | P0 |
| 事务回滚：库存回补失败时发放单状态不回滚？不，应回滚 | 集成测试 | P0 |

### 7.3 QueryService一致性（P1）

| 测试项 | 自动化归属 | 优先级 |
|--------|------------|--------|
| 退药完成后列表状态刷新为 REFUNDED | 集成测试 | P1 |
| 追溯码入库后可在追溯查询中查到 | 集成测试 | P1 |

### 7.4 集成测试（P0）

| 测试项 | 自动化归属 | 优先级 |
|--------|------------|--------|
| 与医生站退费申请集成：医生站生成 PENDING_REFUND 后，药房可查询到 | 端到端测试 | P0 |
| 与库存系统集成：退药后库存可用量增加 | 端到端测试 | P0 |
| 与追溯码系统集成：退药后追溯码状态为 IN | 端到端测试 | P0 |

### 7.5 测试缺口跟踪

| 缺口 ID | 缺口描述 | 优先级 | 状态 |
|---------|----------|--------|------|
| — | 无待跟踪测试缺口 | — | — |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
|------|------|------|
| v1.6 | 2026-08-20 | **IF-PHA-02-04**：`recordReturnInLines` 对冲行新 `bus_no` 时按拷贝 `trace_no` 回落未回冲 OUT（对齐 AS v1.8 / DD §5） |
| v1.5 | 2026-08-19 | **IF-PHA-02-04**：库存回补增量明确 **操作单位→拆零（五类）**；对齐 **AS v1.7 BR-PHA-034** / 门诊发药 **BR-OPP-017** |
| v1.4 | 2026-07-31 | **IF-PHA-02-02**：队列回填 `contractDisplay`（`EncounterContractDisplayAssembler`，与发药/医生站卡片同口径） |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：`EncounterContextFacade#mustGetOutpatientContextByEncounterId` / `listOutpatientContextsByEncounterIdsOrdered` |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-16 | 首版：四接口完整契约（IF-PHA-02-01～04）；HTTP 统一 POST；领域编排序列含 Gate/Pre/Cmd/Asm；事务边界明确；枚举字典双锚点可追溯；reason 字段目标态待生成 |
| v1.1 | 2026-05-16 | §2.2～2.5 与 zxplat 实现对齐：依赖注入、领域白名单符号、DOC-CONFLICT 表；dto 清单修正 |
| v1.2 | 2026-05-16 | 范围收窄为仅药品退药；§2.5 取消误记 GAP；编排/测试矩阵去除耗材 |
| v1.3 | 2026-07-02 | **IF-PHA-02-04**：追溯入库 **`location_type_enum`** 经 **`requireSupplyRequestTypeForLocationId`**（科室药房与药房等价） |
| v1.1.3 | 2026-06-10 | **退药与原发药单解耦**：明细 `totalPrice` 改按原药嘱 `service_id`；删除 `chargeAnchor` / `*RefundWritesupport |
| v1.1.2 | 2026-05-28 | **L2/L3 对齐**：§2.2 点名 `ReturnMedicineDetailLoader`/`OutpatientReturnMedicinePatientQueueAssembler`；IF-PHA-02-02/03 编排序列与代码一致；移除不存在的 Request `itemTable` |
| v1.1.1 | 2026-05-16 | 合并实现对齐：发放事实优先/slip 回落、`mapAggregatedRefundStatusCodeByEncounterIds` 批量聚合、B.10.11 分页；保留 `ChargeItem` 总价与仓库依赖口径 |
