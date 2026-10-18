# 住院药房 · 退药管理 · 应用服务详细设计（SDD）

> **文档职责**：基于 AS-IP-PHA-003 业务契约，给出可直接指导代码实现的详细设计，包括包路径、接口契约、编排序列、事务边界与测试矩阵。
>
> **来源权威**：AS-IP-PHA-003 v1.5（业务意图）、DM-MED-002 / DM-MED-003 / DM-ADM-002 / DM-CLN-004 / DM-CUS-010、对应 DM §6.2。**本页仅药品退药；无拒绝退药；与汇总发药无页面耦合。**
>
> **编码**：AS-IP-PHA-003-SDD · **版本**：v1.6 · **日期**：2026-08-19 · **实现阶段口径**：**目标态全量契约**（应用层 `InpatientPharmacyReturnMedicineAppService` 及配套类待生成；领域 QueryService / 写侧 **已落地**）

---

## 1. 实现边界

### 1.1 目标与边界

| 维度 | 内容 |
| --- | --- |
| **模块** | 住院药房退药管理（InpatientPharmacyReturnMedicine） |
| **边界** | 加载初始化；分页查询**在院**退药患者；查询**单条**退药发放明细；**确认退药**（仅 REFUND 待退行改态 + 库存回补 + 追溯入库）。上游待退单由 **AS-IP-NUR-012** 生成；**原 COMPLETED 正向行不改态** |
| **编排权威** | AS-IP-PHA-003 §4 |
| **方法权威** | DM-MED-002 §6.1 / §6.2.13；DM-MED-003 §6.2；DM-CLN-004 §6.2；`EncounterContextReader` |
| **严格对齐** | IF 编号 **IF-IPR-01～04**；状态 **PENDING_REFUND → REFUNDED**；**无** `DECLINED`；**不写** ChargeItem；**不**调用 `completeMedicationRequestAfterRefund` |
| **实现阶段口径** | 目标态全量契约；领域层 `pageEncounterIdsForInpatientPharmacyReturn`、`listInpatientReturnMedicationDispensesForDetail`、`mapAggregatedInpatientRefundStatusCodeByEncounterIds`、`createPendingRefundDispense`（批号/追溯拷贝）**已存在** |

### 1.2 术语口径

| 术语 | 字段口径 | 说明 |
| --- | --- | --- |
| **退药状态** | `status_enum`（String） | `PENDING_REFUND`、`REFUNDED`；持久化 `DispenseStatus#getCode()` |
| **待退发放行** | `MedicationDispense` 一行 | `dispense_enum=REFUND`；`procedure_id` 挂 CANCEL 行；`med_req_id` 为原药嘱 |
| **原发药单（统计边界）** | 正向 COMPLETED 行；退药链路**不**读写；发药/退药统计分别计正向行与 REFUND 对冲行 |
| **批号/追溯** | `lot_number` / `trace_no` | 待退行自原单拷贝（DM §6.1.8）；库存/追溯以待退行五元组为准 |
| **队列 Tab** | Request `queueTab` | `PENDING`＝就诊下仍有待退；`COMPLETED`＝仅有已退且无待退（应用层内存过滤，**BR-IPR-001**） |

---

## 2. 代码结构

### 2.1 包路径

```text
com/openhis/health/application/inpatient/pharmacy/returnmedicine/
  service/
    InpatientPharmacyReturnMedicineAppService.java
  request/
    LoadInpatientReturnMedicineInitRequest.java
    PageInpatientReturnPatientRequest.java
    GetInpatientReturnMedicineDetailRequest.java
    ConfirmInpatientMedicineReturnRequest.java
    InpatientReturnMedicineItemRequest.java
  response/
    LoadInpatientReturnMedicineInitResponse.java
    PageInpatientReturnPatientResponse.java
    InpatientReturnMedicineDetailResponse.java
    ConfirmInpatientMedicineReturnResponse.java
  dto/
    InpatientReturnPatientRowDto.java
    InpatientReturnMedicineDetailRowDto.java
  assembler/
    InpatientReturnMedicinePatientQueueAssembler.java
  mapper/
    InpatientPharmacyReturnMedicineMapper.java
    InpatientPharmacyReturnMedicineMapperImpl.java
  support/
    InpatientReturnMedicineDetailLoader.java
```

> **`ReturnMedicineDetailReadRow`** 可复用 `zxplat-domain` 已有读行模型；住院明细 Loader 独立于门诊 `ReturnMedicineDetailLoader`（锚点：原药嘱 + Procedure 链，非负向药嘱）。

### 2.2 依赖注入

| 类型 | 注入名称 | 来源 / 用途 |
| --- | --- | --- |
| `CurrentOperatorResolver` | currentOperatorResolver | Gate：操作人（**B.6.5**） |
| `CurrentPractitionerResolver` | currentPractitionerResolver | 退药审计 `practitionerId` |
| `CurrentManagedLocationIdsResolver` | managedLocationIdsResolver | IF-IPR-02 管辖库房 |
| `LocationQueryService` | locationQueryService | IF-IPR-01 病区候选 |
| `EncounterContextReader` | encounterContextFacade | IF-IPR-02/03 住院上下文 |
| `PharmacyEncounterQueueLoader` | pharmacyEncounterQueueLoader | IF-IPR-02 就诊分页键 |
| `MedicationDispenseQueryService` | medicationDispenseQueryService | IF-IPR-02/03/04 Pre |
| `MedicationDispenseDomainService` | medicationDispenseDomainService | IF-IPR-04 Cmd |
| `InventoryItemDomainService` | inventoryItemDomainService | IF-IPR-04 库存回补 |
| `InventoryItemQueryService` | inventoryItemQueryService | IF-IPR-04 批号定位库存项 |
| `TraceNumberDomainService` | TraceNumberDomainService | IF-IPR-04 追溯入库 |
| `ProductDefinitionBatchLoader` | productDefinitionBatchLoader | IF-IPR-04 单位换算 |
| `InpatientReturnMedicineDetailLoader` | inpatientReturnMedicineDetailLoader | IF-IPR-03 明细跨聚合装配 |
| `InpatientReturnMedicinePatientQueueAssembler` | inpatientReturnMedicinePatientQueueAssembler | IF-IPR-02 队列行 |
| `EncounterContractDisplayAssembler` | encounterContractDisplayAssembler | IF-IPR-02：本页就诊批量回填 `contractDisplay`（与门诊发药/医生站卡片同口径） |
| `InpatientPharmacyReturnMedicineMapper` | returnMedicineMapper | Request→Query / 读行→DTO |
| `InpatientPharmacyReturnMedicineMapperImpl` | — | 手写实现（与门诊退药同族） |

> 下拉 **`OptionDto`** 使用 zxplat-framework 公共库，**不**在本模块 `dto/` 新建。

### 2.3 领域方法白名单

| 聚合（域） | 方法 | 用于 IF | 说明 |
| --- | --- | --- | --- |
| **MedicationDispenseDomainService** | `refundDispensedMedication(RefundDispensedMedicationCommand)` | IF-IPR-04 | 仅 REFUND 待退行 → REFUNDED；**不改**原正向行 |
| **InventoryItemDomainService** | `increaseQuantities(IncreaseQuantityCommand)` | IF-IPR-04 | 按批号+库房回补 |
| **TraceNumberDomainService** | `recordTraceRecords(recordTraceRecordsCommand)` | IF-IPR-04 | 追溯 IN |
| **MedicationDispenseQueryService** | `pageEncounterIdsForInpatientPharmacyReturn(InpatientPharmacyReturnEncounterPageQuery)` | IF-IPR-02 | 在院退药就诊分页 |
| **MedicationDispenseQueryService** | `mapAggregatedInpatientRefundStatusCodeByEncounterIds(Collection<Long>)` | IF-IPR-02 | 队列列展示态 |
| **MedicationDispenseQueryService** | `listInpatientReturnMedicationDispensesForDetail(Long, String)` | IF-IPR-03 | 明细发放事实 |
| **MedicationDispenseQueryService** | `mustGetMedicationDispense(Long)` | IF-IPR-04 | 写前加载 |
| **EncounterContextReader** | `listInpatientContextsByEncounterIdsOrdered(List<Long>)` | IF-IPR-02 | 队列展示 |
| **EncounterContextReader** | `mustGetInpatientContextByEncounterId(Long)` | IF-IPR-03 | 就诊门控 |
| **PharmacyEncounterQueueLoader** | `pageEncounterIdsForInpatientPharmacyReturn(InpatientPharmacyReturnEncounterPageQuery)` | IF-IPR-02 | 在院 scope 预解析 |
| **LocationQueryService** | `listWardsByOrganization(Long)` | IF-IPR-01 | 病区候选（同 IF-IPP-01） |
| **CurrentManagedLocationIdsResolver** | `listManagedActivePharmaciesOrEmpty()` | IF-IPR-01 | 可操作药房 |
| **CurrentManagedLocationIdsResolver** | `listManagedLocationIdsOrEmpty()` | IF-IPR-02 | 队列库房 IN |

### 2.4 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | DM-MED-002 §2.2 | `PENDING_REFUND`、`REFUNDED`；IF-IPR-02～04 |
| 发放类型 | `com.zxplat.domain.share.enums.MedicationDispenseType` | DM-MED-002 §4.5 | 队列/明细：`INPATIENT`、`TEMPORARY`、`REFUND` |
| 住院就诊状态 | `com.zxplat.domain.share.enums.EncounterStatus` | DM-ADM-002 §2.3.1 | 队列在院：`IN_PROGRESS`、`ON_HOLD` |
| 追溯方向 | `com.zxplat.domain.share.enums.TraceDirection` | DM-CUS-010 | 退药入库 **IN** |
| 追溯操作类型 | `com.zxplat.domain.share.enums.TraceNumberRecordOperationType` | DM-CUS-010 | `MEDICATION_RETURN_IN` |
| Request 字段 | `PageInpatientReturnPatientRequest.queueTab` | AS-IP-PHA-003 **BR-IPR-001** | `PENDING` / `COMPLETED`；目标态待生成 |
| Request 字段 | `ConfirmInpatientMedicineReturnRequest.reason` | DM-MED-002 §3.1 `support_info` | 可选退药说明；目标态待生成 |
| 业务表名 | `TableNameConstants.MED_MEDICATION_REQUEST` | DM-MED-003 | 明细 `serviceTable` 固定值 |
| 异常码 | `ErrorCodeConstants.EX_IPR_*` | AS-IP-PHA-003 §5 | 目标态待生成（见 §3 各 IF 失败码） |
| 成功文案 | `MedicationCommonConstants.INPATIENT_PHARMACY_RETURN_CONFIRM_SUCCESS_MESSAGE` | AS-IP-PHA-003 **EX-IPR-410** | 目标态待生成 |

### 2.5 白名单双向一致性复核

| 复核项 | 结论 |
| --- | --- |
| 正向（白名单 → §3 序列） | PASS |
| 反向（§3 序列 → 白名单） | PASS |

---

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 任一失败不得进入 Cmd。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IPR-01 | `loadInpatientReturnMedicineInit` | 加载退药初始化元数据 | `POST /api/app/inpatientPharmacyReturnMedicine/loadInpatientReturnMedicineInit` |
| IF-IPR-02 | `pageInpatientReturnPatientQueue` | 分页查询退药患者列表 | `POST /api/app/inpatientPharmacyReturnMedicine/pageInpatientReturnPatientQueue` |
| IF-IPR-03 | `getInpatientReturnMedicineDetail` | 查询退药明细 | `POST /api/app/inpatientPharmacyReturnMedicine/getInpatientReturnMedicineDetail` |
| IF-IPR-04 | `confirmInpatientMedicineReturn` | 确认退药 | `POST /api/app/inpatientPharmacyReturnMedicine/confirmInpatientMedicineReturn` |

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限 / 参数 / 登录门禁 | 抛业务异常，不再调用后续步骤 |
| **Pre** | 写前只读：加载快照、状态与 BR 门禁 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写 + 库存/追溯协作 | 事务回滚见 §5 |
| **Asm** | Mapper / Assembler 纯 DTO 装配 | — |

**记法**：`MedicationDispenseDomainService#refundDispensedMedication(RefundDispensedMedicationCommand)` 表示 AppService 内注入 Bean 后调用。

---

### 3.2 IF-IPR-01 `loadInpatientReturnMedicineInit`（加载退药初始化元数据）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| — | — | — | 空壳或仅会话上下文 |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pharmacyOrganizationOptions` | `List<OptionDto>` | 管辖内启用药房；`value`=locationId |
| `wardOptions` | `List<OptionDto>` | 病区候选；`value`=ward locationId |
| `refundStatusOptions` | `List<OptionDto>` | `PENDING_REFUND`（待退药）、`REFUNDED`（已退药） |

#### 关键校验

- 依赖当前机构上下文加载病区列表

#### 失败码口径

| 失败码 | 触发条件 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IPR-101 | 元数据加载失败 | 数据加载失败，请稍后重试 | 是 |

#### Mapper 接口方法签名

```java
LoadInpatientReturnMedicineInitResponse toLoadInitResponse(
    List<OptionDto> pharmacyOptions,
    List<OptionDto> wardOptions,
    List<OptionDto> refundStatusOptions);
List<OptionDto> mapToRefundStatusOptions();
```

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 未登录 → EX-IPR-101 |
| 2 | Pre | `CurrentManagedLocationIdsResolver#listManagedActivePharmaciesOrEmpty` | — | 药房下拉（同 IF-IPP-01） |
| 3 | Pre | `LocationQueryService#listWardsByOrganization` | 当前机构 id | 病区候选 |
| 4 | Asm | `Mapper#mapToRefundStatusOptions` + `#toLoadInitResponse` | ②③ | 返回 Response |

---

### 3.3 IF-IPR-02 `pageInpatientReturnPatientQueue`（分页查询退药患者列表）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `wardId` | Long | 否 | 病区过滤（仅传入时生效，同 **BR-IPP-001**） |
| `keyword` | String | 否 | 姓名 / 住院号 / 床号模糊 |
| `queueTab` | String | 否 | `PENDING`（默认）：有待退；`COMPLETED`：仅已退且无待退 |
| `pageNo` | Integer | 是 | 页码，**1-based**（**B.10.11**） |
| `pageSize` | Integer | 是 | 每页条数，默认 10，最大 100 |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageNo` | Integer | 当前页 |
| `pageSize` | Integer | 每页条数 |
| `totalCount` | Long | 总记录数 |
| `content` | `List<InpatientReturnPatientRowDto>` | 患者行（`PagedResult` 契约） |

**InpatientReturnPatientRowDto 字段表**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | 是 | 就诊标识 |
| `patientName` | String | 是 | 患者姓名 |
| `genderEnum` | String | 是 | 性别 code |
| `genderEnumText` | String | 是 | 性别展示 |
| `age` | String | 是 | 年龄文本 |
| `idCard` | String | 否 | 身份证（脱敏） |
| `wardName` | String | 是 | 病区名称 |
| `bedName` | String | 否 | 床位名称 |
| `encounterNo` | String | 是 | 住院号（`encounterBusNo`） |
| `refundEnum` | String | 是 | 聚合退药态：`PENDING_REFUND` / `REFUNDED` / 空 |
| `refundEnumText` | String | 是 | 退药态展示 |
| `contractDisplay` | String | 否 | 费用性质展示名（就诊优先合同名；与门诊发药/医生站卡片同口径；无合同时为空，前端回落「自费」） |

#### 关键校验

- `pageNo` ≥ 1；`pageSize` ∈ [1, 100]

#### 失败码口径

| 失败码 | 触发条件 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IPR-102 | 查询失败 / 分页非法 | 列表加载失败，请稍后重试 | 是 |

#### Mapper 接口方法签名

```java
InpatientPharmacyReturnEncounterPageQuery toPageQuery(PageInpatientReturnPatientRequest request);
InpatientReturnPatientRowDto toReturnPatientRow(InpatientEncounterContextQueryModel ctx, String refundEnumCode);
```

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数校验 + `CurrentOperatorResolver#requireOperatorId` | `pageNo`/`pageSize` | 非法分页 → EX-IPR-102 |
| 2 | Pre | `CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty` | — | 非空 → `InpatientPharmacyReturnEncounterPageQuery#locationIds` |
| 3 | Pre | `Mapper#toPageQuery` | Request | 写入 `wardId`/`keyword`/分页 |
| 4 | Pre | `PharmacyEncounterQueueLoader#pageEncounterIdsForInpatientPharmacyReturn` | ③ Query | 在院就诊 id 分页 |
| 5 | Pre | `EncounterContextReader#listInpatientContextsByEncounterIdsOrdered` | ④ content | 住院上下文 |
| 6 | Pre | `MedicationDispenseQueryService#mapAggregatedInpatientRefundStatusCodeByEncounterIds` | ④ encounterIds | 聚合退药态 |
| 7 | Pre | `InpatientReturnMedicinePatientQueueAssembler#buildRows` | ⑤⑥ + `queueTab` | **若** `queueTab=COMPLETED`：过滤掉仍有 `PENDING_REFUND` 的就诊；**若** `PENDING`：过滤掉无待退的就诊；**否则**不过滤 |
| 8 | Asm | `Mapper#toReturnPatientRow` | ⑦ | 映射 `InpatientReturnPatientRowDto` |
| 9 | Asm | **`EncounterContractDisplayAssembler#mapContractDisplayByEncounterIds`** + 逐行写回 `contractDisplay` | ⑧ 本页 encounterIds | 费用性质与门诊发药/医生站卡片同口径；无合同时留空 |
| 10 | Asm | `PagedResultBuilder` | ⑧+⑨ | 返回 Response |

---

### 3.4 IF-IPR-03 `getInpatientReturnMedicineDetail`（查询退药明细）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | Long | 是 | 就诊标识 |
| `refundStatus` | String | 否 | `PENDING_REFUND` / `REFUNDED`；空=默认子集 |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `items` | `List<InpatientReturnMedicineDetailRowDto>` | 退药明细（**逐发放行**） |

**InpatientReturnMedicineDetailRowDto 字段表**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseId` | Long | 是 | 待退/已退发放行 id（操作主键） |
| `requestId` | Long | 是 | 原药嘱 id（`med_req_id`） |
| `itemId` | Long | 是 | 药品定义 id |
| `itemName` | String | 是 | 药品名称 |
| `lotNumber` | String | 否 | 批号（待退行；创建待退时已从原单复制） |
| `quantity` | BigDecimal | 是 | 应退数量（绝对值） |
| `unitCode` | String | 是 | 单位 code |
| `unitCodeText` | String | 是 | 单位展示 |
| `doctorName` | String | 是 | 开立医生 |
| `refundEnum` | String | 是 | 发放状态 code |
| `refundEnumText` | String | 是 | 状态展示 |
| `serviceTable` | String | 是 | 固定 `med_medication_request` |
| `traceNo` | String | 否 | 追溯码（逗号分隔） |
| `selectable` | Boolean | 是 | 仅 `PENDING_REFUND` 为 true |

#### 关键校验

- `encounterId` 必填；须能加载在院/有效住院上下文

#### 失败码口径

| 失败码 | 触发条件 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IPR-103 | 就诊无效或无权限 | 无法加载退药明细 | 视权限 |

#### Mapper 接口方法签名

```java
ReturnMedicineDetailQuery toDetailQuery(GetInpatientReturnMedicineDetailRequest request);
List<InpatientReturnMedicineDetailRowDto> toDetailRows(List<ReturnMedicineDetailReadRow> rows);
```

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 未登录 → EX-IPR-103 |
| 2 | Pre | 参数校验 | `encounterId` | 空 → EX-IPR-103 |
| 3 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | `encounterId` | 就诊门控 |
| 4 | Pre | `InpatientReturnMedicineDetailLoader#listReturnMedicineDetails` | `ReturnMedicineDetailQuery` | 内部：`listInpatientReturnMedicationDispensesForDetail` + 原药嘱/医师/单位批量装配 |
| 5 | Asm | `Mapper#toDetailRows` | ④ | 返回 `items` |

**InpatientReturnMedicineDetailLoader 内部要点（应用层，非 IF）**：

- 读 `MedicationDispense` 待退/已退行（批号/追溯码取自行自身，`createPendingRefundDispense` 已复制）；
- **`med_req_id`**：`MedicationRequestQueryService#mapEntitiesByIds` 取原药嘱与开立医生。

---

### 3.5 IF-IPR-04 `confirmInpatientMedicineReturn`（确认退药）

#### Request

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `items` | `List<InpatientReturnMedicineItemRequest>` | 是 | 至少一项 |
| `reason` | String | 否 | 退药说明（写入 `support_info`） |

**InpatientReturnMedicineItemRequest 字段表**：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseId` | Long | 是 | 待退发放行 id |

#### Response

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 固定 true |
| `message` | String | 成功文案（EX-IPR-410） |

#### 关键校验

- `items` 非空（**BR-IPR-003**）
- 每项须 **PENDING_REFUND**（**BR-IPR-004** 幂等拒绝已 REFUNDED）
- 全量数量（**BR-IPR-005**）
- **禁止**拒绝退药分支（**BR-IPR-010**）

#### 失败码口径

| 失败码 | 触发条件 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IPR-201 | 非 PENDING_REFUND | 当前状态不允许退药，请刷新后重试 | 否 |
| EX-IPR-202 | 已 REFUNDED 重复 | 该药品已退药，请勿重复操作 | 否 |
| EX-IPR-203 | 批号/库存项/换算缺失 | 库存信息异常，请联系管理员 | 否 |
| EX-IPR-301 | 未选退药项 | 请选择要退药的药品 | 否 |

#### Mapper 与 Command 装配

写侧 Command 在 `InpatientPharmacyReturnMedicineAppService` 内联装配；**禁止**在写路径解析或修改原正向发放行状态（**BR-IPR-007**）。

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | 参数校验 + `CurrentOperatorResolver#requireOperatorId` | `items` 非空 | 空 → EX-IPR-301 |
| 2 | Gate | `CurrentPractitionerResolver#findPractitionerIdOrNull` | operatorId 回落 | 审计用 practitionerId |
| 3 | Pre | `MedicationDispenseQueryService#mustGetMedicationDispense` | 每项 `dispenseId` | 加载待退行 |
| 4 | Pre | 状态校验 | ③ | 非 PENDING_REFUND → EX-IPR-201；已 REFUNDED → EX-IPR-202 |
| 5 | Pre | App 内联 | ③ | 按待退行 `lot_number`/数量收集追溯项与库存回补计划（批号空 → EX-IPR-203）；回补量 = **`unit_code` 操作单位→拆零**（**BR-IPR-006** / **BR-OPP-017**）；追溯 **`location_type_enum`** = **`requireSupplyRequestTypeForLocationId`** |
| 6 | Cmd | **【第一写库点】** `MedicationDispenseDomainService#refundDispensedMedication(RefundDispensedMedicationCommand)` | 批量 Item | 仅 REFUND 待退行 → REFUNDED |
| 7 | Cmd | **【第二写库点】** `TraceNumberDomainService#recordTraceRecords(recordTraceRecordsCommand)` | ⑤ traceItems | 有码时执行 |
| 8 | Cmd | **【第三写库点】** `InventoryItemDomainService#increaseQuantities(IncreaseQuantityCommand)` | ⑤ 合并增量 | 按库存项 id 回补拆零量（**禁止**用 `trace_unit_ratio` 覆盖五类同码单位） |
| 9 | Asm | 组装 `ConfirmInpatientMedicineReturnResponse` | success/message | **不**调用 `completeMedicationRequestAfterRefund` |

**事务说明**：AppService **无** `@Transactional`；Cmd 7～9 建议同一 Spring 事务（`@Transactional` 置于 AppService 方法或协作 Facade，以实现为准）；任一步失败整批回滚。

---

## 4. QueryService 与领域事件

### 4.1 QueryService 依赖

| QueryService / Loader | 方法 | 用于 IF | 说明 |
| --- | --- | --- | --- |
| `MedicationDispenseQueryService` | `pageEncounterIdsForInpatientPharmacyReturn` | IF-IPR-02 | 就诊分页键 + 在院 scope |
| `MedicationDispenseQueryService` | `mapAggregatedInpatientRefundStatusCodeByEncounterIds` | IF-IPR-02 | 队列 `refundEnum` |
| `MedicationDispenseQueryService` | `listInpatientReturnMedicationDispensesForDetail` | IF-IPR-03 | 明细发放事实 |
| `InpatientReturnMedicineDetailLoader` | `listReturnMedicineDetails` | IF-IPR-03 | 跨聚合装配读行 |

### 4.2 事件驱动的读侧刷新

| 领域事件 | 触发 IF | 消费方 / 刷新 |
| --- | --- | --- |
| `MedicationDispensePendingRefundCreatedEvent` | AS-IP-NUR-012（上游） | IF-IPR-02 队列增量 |
| `MedicationDispenseRefundedEvent` | IF-IPR-04 | 队列/明细状态 → REFUNDED；出院门禁 **BR-IP-ATD-011** 待退消除 |
| `TraceNumberInboundedEvent`（若定义） | IF-IPR-04 | 追溯 QueryService |

> **费用**：取消执行已发布 `ChargeItemExecutionCancelledEvent`；本模块 **不**重复冲费。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 IF-IPR-04 Cmd 落库顺序对照表

| IF | App 方法 | Cmd 序号 | 领域调用 | 聚合写入顺序 |
| --- | --- | --- | --- | --- |
| IF-IPR-04 | `confirmInpatientMedicineReturn` | 6 | `refundDispensedMedication` | 第一：仅 `med_medication_dispense` REFUND 待退行 |
| IF-IPR-04 | `confirmInpatientMedicineReturn` | 7 | `recordTraceRecords` | 第二：追溯码（有码时） |
| IF-IPR-04 | `confirmInpatientMedicineReturn` | 8 | `increaseQuantities` | 第三：库存项 |

### 5.2 事务边界与失败回滚

| 属性 | 口径 |
| --- | --- |
| **事务边界** | IF-IPR-04 序号 6～8 **同一事务**（建议） |
| **失败回滚** | 任一步异常 → 发放状态不变、库存不补、追溯不入库 |
| **部分失败** | 同一请求多项退药：**整批成功或整批失败** |
| **幂等** | Pre 阶段状态门禁（**BR-IPR-004**） |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 实现 | 关联步骤 |
| --- | --- |
| Pre 校验仅 `PENDING_REFUND` 可确认；已 `REFUNDED` 抛 EX-IPR-202 | §3.5 序号 4 |

### 6.2 并发控制

| 实现 | 关联步骤 |
| --- | --- |
| 同一 `dispenseId` 并发确认：后到者 Pre 状态失败 | §3.5 序号 4 |
| 乐观锁：依赖 `MedicationDispense` 版本字段（若实体启用） | §3.5 序号 6 |

### 6.3 重试与容错

| 实现 | 关联步骤 |
| --- | --- |
| 无自动重试；失败由用户刷新后重提 | — |
| 库存/追溯失败整单回滚 | §3.5 序号 7～8 |

### 6.4 数据脱敏

| 实现 | 关联步骤 |
| --- | --- |
| 队列 `idCard` 脱敏展示（保留前3后4） | §3.3 Response |

### 6.5 审计日志

| 实现 | 关联步骤 |
| --- | --- |
| `practitioner_id`、`dispense_time` 写入待退行确认路径 | §3.5 序号 6 |
| 可选 `support_info` 写入退药说明 | §3.5 序号 6 |

---

## 7. 测试要点

### 7.1 功能测试（P0）

| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 初始化：药房/病区/退药状态下拉 | 单元测试 | P0 |
| 队列：在院过滤、病区、关键字、待退/已退 Tab | 集成测试 | P0 |
| 明细：单条待退/已退行展示；批号/追溯取自待退行 | 集成测试 | P0 |
| 确认退药：待退 REFUND 行 REFUNDED，原正向仍 COMPLETED | 集成测试 | P0 |
| 库存回补与追溯 IN | 集成测试 | P0 |
| **无**拒绝退药 API | 契约测试 | P0 |

### 7.2 非功能测试（P0）

| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 重复确认幂等（EX-IPR-202） | 集成测试 | P0 |
| 并发双提同一待退行 | 并发测试 | P0 |
| 库存失败整单回滚 | 集成测试 | P0 |

### 7.3 查询结果一致性（P1）

| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 确认后队列待退 Tab 不再展示该就诊（或聚合态变 REFUNDED） | 集成测试 | P1 |
| 出院门禁：确认后 **BR-IP-ATD-011** 不再阻塞 | 端到端 | P1 |

### 7.4 集成测试（P0）

| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 取消执行产生待退 → 药房队列可见 → 确认闭环 | 端到端 | P0 |
| 与 **AS-IP-PHA-001/002** 无页面耦合（单条发放） | 回归 | P1 |

### 7.5 测试缺口跟踪

| 缺口 ID | 缺口描述 | 优先级 | 状态 |
| --- | --- | --- | --- |
| — | 无待跟踪测试缺口 | — | — |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.6 | 2026-08-19 | **IF-IPR-04**：回补量 **操作单位→拆零（五类）**；对齐 **AS v1.5 BR-IPR-006** |
| v1.5 | 2026-07-31 | **IF-IPR-02**：队列回填 `contractDisplay`（`EncounterContractDisplayAssembler`，与门诊发药/医生站卡片同口径） |
| v1.4 | 2026-07-02 | **IF-IPR-04**：追溯入库 **`location_type_enum`** 经 **`LocationQueryService#requireSupplyRequestTypeForLocationId`** |
| v1.3 | 2026-06-10 | **BR-IPR-007 扩口径**：退药与原发药单无读写关联；读侧仅待退行+原药嘱 |
| v1.2 | 2026-06-10 | **读侧简化**：明细不再回溯原正向发放行；删除 `originForwardDispenseId` / `ProcedureQueryService` 依赖 |
| v1.1 | 2026-06-10 | **BR-IPR-007 修订**：确认退药不改原 COMPLETED 正向行；删除写侧 `originForwardDispenseId` / `*RefundWritesupport |
| v1.0 | 2026-06-10 | 首版：IF-IPR-01～04 目标态契约；住院在院队列+Procedure 链原发放只读定位；仅确认退药；HTTP 统一 POST；领域 QueryService 已落地；`EX_IPR_*` / 成功文案常量目标态待生成 |
