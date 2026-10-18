# 住院患者药品使用 · 应用服务详细设计（SDD）

> 编码：AS-IP-PHA-005-SDD  
> 版本：v1.3 | 日期：2026-08-06  
> 编排权威：`AS-IP-PHA-005-住院患者药品使用.md`（**v1.2**）  
> 领域权威：`DM-MED-002`（§2.2 状态机；§6.2.21 `listInpatientPatientMedicationUsageDispenseRows`，**GAP-IP-PMU-001 已关闭**）、`DM-ADM-011`、`DM-ADM-002` §6.2.18、`DM-MED-001`  
> 说明：本 SDD **不维护 Gap 表**；领域读方法与应用层 IF-IP-PMU-01～03 已落地；产品批量读对齐工程 `ProductDefinitionDisplayTerminologyAssembler`。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 发药管理 · **住院患者药品使用**（患者检索 + 明细/汇总 + 合计净额） |
| 实现阶段口径 | **已落地**（IF-IP-PMU-01～03）；领域读 `MedicationDispenseQueryService#listInpatientPatientMedicationUsageDispenseRows`（`DM-MED-002` §6.2.21）；应用层落包 `inpatient/pharmacy/patientmedicationusage`，AppService **`InpatientPharmacyPatientMedicationUsageAppService`**，HTTP 前缀 **`inpatientPharmacyPatientMedicationUsage`** |
| 编排权威 | `AS-IP-PHA-005` §1.3、§2、§4 |
| 方法权威 | `DM-MED-002` §6.2.21（使用行列表）；`DM-ADM-002` §6.2.18；`DM-ADM-011` 发放定价读；`DM-MED-001` 药品展示 |
| 严格对齐 | IF **IF-IP-PMU-01、IF-IP-PMU-02、IF-IP-PMU-03**，不得增删改号 |
| 读侧方案 | **MedicationDispense 业务表**为行锚点 + **ChargeItem** 账单价 + **ProductDefinition/MedicationDefinition** 批量展示；**禁止**以护士站费用明细 Charge 列表冒充本页 |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 发药/退药写路径 | **AS-IP-PHA-001～003** |
| 发退药记录（发药时间轴） | **AS-IP-PHA-004** |
| 护士站费用明细 | **AS-IP-NUR-006** |
| 多患者、导出、库房管辖收窄 | AS §1.4 非目标 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 状态 / 枚举 code | 持久化与比较一律 **String `code`**（`DispenseStatus`、`MedicationDispenseType` 等） |
| 时间轴 | **过滤/排序**：`MedicationDispense.plannedDispenseTime` → Request `dateFrom`/`dateTo` 本地日闭区间。**明细展示**：优先 `Procedure.recorded_time`（`dispense.procedure_id`），回落 planned |
| 展示发药状态 | `usageStatusCode`：`PENDING` / `DISPENSED` / `RETURNED`（文案：待发/已发/已退）；映射见 AS §1.3 |
| `presentationMode` | `DETAIL` / `SUMMARY` |
| 展示 vs 统计 | 明细行 `unitPrice`/`totalPrice` **账单原样**；汇总 `lineTotalAmount` 与合计为 **净额** |
| HTTP | 一律 **`POST`**；Bean → 前缀 **`inpatientPharmacyPatientMedicationUsage`** |
| 事务 | 本模块**只读**，无写事务 |
| 金额换算 | 汇总数量链 **6 位 HALF_UP** |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/pharmacy/patientmedicationusage/
  service/
    InpatientPharmacyPatientMedicationUsageAppService.java
  request/
    LoadInpatientPatientMedicationUsagePageInitRequest.java
    PageInpatientPatientMedicationUsagePatientsRequest.java
    QueryInpatientPatientMedicationUsageRequest.java
  response/
    LoadInpatientPatientMedicationUsagePageInitResponse.java
    PageInpatientPatientMedicationUsagePatientsResponse.java
    QueryInpatientPatientMedicationUsageResponse.java
  dto/
    InpatientPatientMedicationUsagePatientRowDto.java
    InpatientPatientMedicationUsageDetailLineDto.java
    InpatientPatientMedicationUsageSummaryLineDto.java
    InpatientPatientMedicationUsageTotalsDto.java
    InpatientPatientMedicationUsagePresentationOptionDto.java
    InpatientPatientMedicationUsageStatusOptionDto.java
  assembler/
    InpatientPatientMedicationUsageAssembler.java
    InpatientPatientMedicationUsageQuantityAssembler.java
  mapper/
    InpatientPatientMedicationUsageMapper.java
    InpatientPatientMedicationUsageMapperImpl.java
  support/
    InpatientPatientMedicationUsageChargePricingSupport.java
    ChargePricingView.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/medication/medicationdispense/
  service/MedicationDispenseQueryService.java
  query/InpatientPatientMedicationUsageDispenseQuery.java
  queries/model/InpatientPatientMedicationUsageDispenseRow.java

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  InpatientPharmacyPatientMedicationUsageConstants.java
```

> 包名：`com.zxplat.domain.application.inpatient.pharmacy.patientmedicationusage`  
> AppService → HTTP 前缀：`inpatientPharmacyPatientMedicationUsage`

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `PageWindowGate` | IF-02：分页窗口必填校验 |
| `PatientEncounterConsistencyGate` | IF-03：请求 patientId 与就诊归属交叉 |
| `EncounterContextReader` | IF-02：`pageInpatientEncounterContexts`；IF-03：`mustGetInpatientContextByEncounterId`（隐含 IMP） |
| `MedicationDispenseQueryService` | IF-03：`listInpatientPatientMedicationUsageDispenseRows` |
| `ChargeItemQueryService` | IF-03：经 Support 调用 `listChargeItemsForMedicationDispensePricing` |
| `InpatientMedicationDispenseChargePricingLoader` | IF-03：经 Support 调用 `mergePendingSettlementPricingMaps`（与 PHA-001/004 同族） |
| `ProductDefinitionDisplayTerminologyAssembler` | IF-03：`loadAsMap`（替代已消解的 `ProductDefinitionBatchLoader`） |
| `InpatientPatientMedicationUsageMapper` | Request/Response 装配 |
| `InpatientPatientMedicationUsageAssembler` | 明细/汇总/合计/三态映射 |
| `InpatientPatientMedicationUsageQuantityAssembler` | 汇总包装/拆零净数量 |
| `InpatientPatientMedicationUsageChargePricingSupport` | 批量价装载编排 |

> 使用公共库 `OptionDto`（若需要），**不**在本模块 `dto/` 新建。

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002` §2.2 | 纳入白名单与三态映射源 |
| 发放类型 | `com.zxplat.domain.share.enums.MedicationDispenseType` | `DM-MED-002` §3 `dispense_enum` | 正向 `INPATIENT`/`TEMPORARY`；退药 `REFUND` |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3 `class_enum` | IMP |
| 在院态 | `com.zxplat.domain.share.enums.EncounterStatus` | `DM-ADM-002` §5 | IF-02：`IN_PROGRESS`∪`ON_HOLD` |
| 展示模式 | `QueryInpatientPatientMedicationUsageRequest.presentationMode` | `AS-IP-PHA-005` §2 | `DETAIL`/`SUMMARY`；常量 **`InpatientPharmacyPatientMedicationUsageConstants.PRESENTATION_MODE_*`** |
| 展示发药状态 | `InpatientPatientMedicationUsageDetailLineDto.usageStatusCode` | `AS-IP-PHA-005` §1.3 | `PENDING`/`DISPENSED`/`RETURNED`；常量 **`USAGE_STATUS_*`** |
| 应用语义码 | `InpatientPharmacyPatientMedicationUsageConstants.EX_IP_PMU_*` | `AS-IP-PHA-005` §5 | **101～202** |

### 2.4 领域方法与 Mapper 白名单

**领域 / 应用读**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `EncounterContextReader` | `pageInpatientEncounterContexts` | IF-IP-PMU-02 |
| `EncounterContextReader` | `mustGetInpatientContextByEncounterId` | IF-IP-PMU-03 Gate |
| `MedicationDispenseQueryService` | `listInpatientPatientMedicationUsageDispenseRows` | IF-IP-PMU-03 Pre |
| `ChargeItemQueryService` | `listChargeItemsForMedicationDispensePricing` | IF-IP-PMU-03 Pre（经 ChargePricingSupport） |
| `InpatientMedicationDispenseChargePricingLoader` | `mergePendingSettlementPricingMaps` | IF-IP-PMU-03 Pre（经 ChargePricingSupport） |
| `ProductDefinitionDisplayTerminologyAssembler` | `loadAsMap` | IF-IP-PMU-03 Pre |
| `ProductDefinitionKeyUtil` | `buildCompositeKey` | IF-IP-PMU-03 Asm |

**Mapper / Assembler**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `InpatientPatientMedicationUsageMapper` | `buildPageInitResponse` | IF-IP-PMU-01 |
| `InpatientPatientMedicationUsageMapper` | `toInpatientEncounterPageQuery` | IF-IP-PMU-02 |
| `InpatientPatientMedicationUsageMapper` | `toPatientPageResponse` | IF-IP-PMU-02 |
| `InpatientPatientMedicationUsageMapper` | `toUsageDispenseQuery` | IF-IP-PMU-03 |
| `InpatientPatientMedicationUsageMapper` | `toQueryResponse` | IF-IP-PMU-03 |
| `InpatientPatientMedicationUsageAssembler` | `mapUsageStatusCode` | IF-IP-PMU-03 |
| `InpatientPatientMedicationUsageAssembler` | `buildDetailLines` | IF-IP-PMU-03 |
| `InpatientPatientMedicationUsageAssembler` | `buildSummaryLines` | IF-IP-PMU-03 |
| `InpatientPatientMedicationUsageAssembler` | `buildTotals` | IF-IP-PMU-03 |
| `InpatientPatientMedicationUsageQuantityAssembler` | `aggregateNetPackageAndSplitQuantity` | IF-IP-PMU-03 |
| `InpatientPatientMedicationUsageChargePricingSupport` | `loadPricingByDispenseIds` | IF-IP-PMU-03 |

---

## 3. 按接口 ID 命名展开

先读完每个 IF 的「领域编排序列（严格顺序）」，按序号执行；Gate/Pre 失败不得进入后续 Asm。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-PMU-01 | `loadInpatientPatientMedicationUsagePageInit` | 住院患者药品使用页初始化 | `POST /api/app/inpatientPharmacyPatientMedicationUsage/loadInpatientPatientMedicationUsagePageInit` |
| IF-IP-PMU-02 | `pageInpatientPatientMedicationUsagePatients` | 分页检索住院患者 | `POST /api/app/inpatientPharmacyPatientMedicationUsage/pageInpatientPatientMedicationUsagePatients` |
| IF-IP-PMU-03 | `queryInpatientPatientMedicationUsage` | 查询住院患者药品使用 | `POST /api/app/inpatientPharmacyPatientMedicationUsage/queryInpatientPatientMedicationUsage` |

### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 参数、日期、就诊门禁、`presentationMode` | 抛 **`EX-IP-PMU-*`**，不进入 Pre |
| **Pre** | 域查询发放行、账单价、产品定义 | 抛业务异常，**无 Cmd** |
| **Asm** | 三态映射、明细/汇总/合计 DTO | — |

记法：`MedicationDispenseQueryService#listInpatientPatientMedicationUsageDispenseRows` 表示注入 Bean 调用。

---

### 3.2 IF-IP-PMU-01 `loadInpatientPatientMedicationUsagePageInit`（住院患者药品使用页初始化）

**HTTP**：`POST /api/app/inpatientPharmacyPatientMedicationUsage/loadInpatientPatientMedicationUsagePageInit`

**Request**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| （无业务字段） | — | — | 可空体；服务端以 `LocalDate.now()` 计算默认日期 |

**Response：`LoadInpatientPatientMedicationUsagePageInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dateFrom` | `LocalDate` | 默认当日 |
| `dateTo` | `LocalDate` | 默认当日 |
| `presentationOptions` | `List<InpatientPatientMedicationUsagePresentationOptionDto>` | `DETAIL`、`SUMMARY` |
| `usageStatusOptions` | `List<InpatientPatientMedicationUsageStatusOptionDto>` | `PENDING`/`DISPENSED`/`RETURNED` 说明（展示用） |

**DTO：`InpatientPatientMedicationUsagePresentationOptionDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `String` | `DETAIL` / `SUMMARY` |
| `display` | `String` | 明细 / 汇总 |

**DTO：`InpatientPatientMedicationUsageStatusOptionDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `String` | `PENDING` / `DISPENSED` / `RETURNED` |
| `display` | `String` | 待发 / 已发 / 已退 |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-PMU-002** | 默认日期均为服务端当日 |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-PMU-101** | 未预期基础设施异常 |

**Mapper 接口方法签名**

- `buildPageInitResponse(LocalDate today): LoadInpatientPatientMedicationUsagePageInitResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Asm | `InpatientPatientMedicationUsageMapper#buildPageInitResponse` | `LocalDate.now()` | 写死默认日期、presentation、usageStatus 选项 |
| — | — | — | — | **仅读路径**；无 Gate/Pre/Cmd |

---

### 3.3 IF-IP-PMU-02 `pageInpatientPatientMedicationUsagePatients`（分页检索住院患者）

**HTTP**：`POST /api/app/inpatientPharmacyPatientMedicationUsage/pageInpatientPatientMedicationUsagePatients`

**Request：`PageInpatientPatientMedicationUsagePatientsRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | `String` | N | 姓名 / 住院号 / 床号模糊 |
| `pageNo` | `Integer` | Y | 页码，从 1 |
| `pageSize` | `Integer` | Y | 页大小；建议上限 100 |

**Response：`PageInpatientPatientMedicationUsagePatientsResponse`**

> 继承工程统一分页壳 `PagedResult<InpatientPatientMedicationUsagePatientRowDto>`（B.10.11），经 `PagedResultBuilder` 组装。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `content` | `List<InpatientPatientMedicationUsagePatientRowDto>` | 候选患者行（语义同业务「records」） |
| `totalCount` | `Long` | 总行数（语义同业务「total」） |
| `pageNo` | `Integer` | 当前页码（1-based） |
| `pageSize` | `Integer` | 页大小 |

**DTO：`InpatientPatientMedicationUsagePatientRowDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `encounterId` | `Long` | 就诊标识 |
| `patientId` | `Long` | 患者标识 |
| `patientName` | `String` | 姓名 |
| `inpatientNo` | `String` | 住院号 |
| `wardName` | `String` | 病区 |
| `bedName` | `String` | 床号 |
| `statusEnum` | `String` | 就诊状态 code |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-PMU-010** | 默认在院 `IN_PROGRESS`∪`ON_HOLD`；关键字模糊 |
| **BR-IP-PMU-009** | 不按药房收窄 |
| 分页 | `pageNo`/`pageSize` 非法 → **EX-IP-PMU-101** 或参数校验异常 |

**失败码**

| 码 | 场景 |
| --- | --- |
| **EX-IP-PMU-101** | 检索失败 |

**Mapper 接口方法签名**

- `toInpatientEncounterPageQuery(PageInpatientPatientMedicationUsagePatientsRequest request): InpatientEncounterPageQuery`
- `toPatientPageResponse(Page<InpatientEncounterContextQueryModel> page): PageInpatientPatientMedicationUsagePatientsResponse`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `pageNo`、`pageSize` | 非法分页拒绝 |
| 2 | Pre | `InpatientPatientMedicationUsageMapper#toInpatientEncounterPageQuery` | Request | 写入 keyword；`statusEnumIn={IN_PROGRESS,ON_HOLD}` |
| 3 | Pre | `EncounterContextReader#pageInpatientEncounterContexts` | ② Query | **BR-IP-PMU-010**；**不**拼 `location_id` |
| 4 | Asm | `InpatientPatientMedicationUsageMapper#toPatientPageResponse` | ③ Page | 映射候选行 |
| — | — | — | — | **仅读路径**；无 Cmd |

---

### 3.4 IF-IP-PMU-03 `queryInpatientPatientMedicationUsage`（查询住院患者药品使用）

**HTTP**：`POST /api/app/inpatientPharmacyPatientMedicationUsage/queryInpatientPatientMedicationUsage`

**Request：`QueryInpatientPatientMedicationUsageRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `encounterId` | `Long` | Y | 当前选中就诊 |
| `patientId` | `Long` | Y | 与候选行一致，交叉校验 |
| `dateFrom` | `LocalDate` | Y | 计划发药日下界（闭） |
| `dateTo` | `LocalDate` | Y | 计划发药日上界（闭）；须 `dateFrom ≤ dateTo` |
| `presentationMode` | `String` | Y | `DETAIL` 或 `SUMMARY` |

**Response：`QueryInpatientPatientMedicationUsageResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `presentationMode` | `String` | 回显 |
| `detailLines` | `List<InpatientPatientMedicationUsageDetailLineDto>` | `DETAIL` 时填充；否则空列表 |
| `summaryLines` | `List<InpatientPatientMedicationUsageSummaryLineDto>` | `SUMMARY` 时填充；否则空列表 |
| `totals` | `InpatientPatientMedicationUsageTotalsDto` | **两种模式均返回**；净额 **BR-IP-PMU-008** |

**DTO：`InpatientPatientMedicationUsageDetailLineDto`（明细行）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dispenseId` | `Long` | `med_medication_dispense.id` |
| `medicationId` | `Long` | 药品定义标识 |
| `itemName` | `String` | 药品名称 |
| `itemSpec` | `String` | 规格 |
| `quantityValue` | `BigDecimal` | 发放数量（原样；退药行按域符号或负号约定） |
| `quantityUnitCode` | `String` | 单位 code |
| `quantityUnitDisplay` | `String` | 单位展示 |
| `unitPrice` | `BigDecimal` | 账单单价（可空） |
| `totalPrice` | `BigDecimal` | 账单行合计（可空；退药为负） |
| `plannedDispenseTime` | `LocalDateTime` | 计划发药时间（过滤/排序轴） |
| `recordedTime` | `LocalDateTime` | 客户可见执行时间（Procedure.recorded_time；可空，前端回落 planned） |
| `procedureId` | `Long` | 关联执行记录主键；可空 |
| `statusEnum` | `String` | 领域 `DispenseStatus` code |
| `usageStatusCode` | `String` | `PENDING`/`DISPENSED`/`RETURNED` |
| `usageStatusDisplay` | `String` | 待发/已发/已退 |
| `dispenseEnum` | `String` | 发放类型 code |
| `summaryNo` | `String` | 汇总单号（可空；仅展示，不参与汇总分桶） |
| `lotNumber` | `String` | 批号（可空） |

**DTO：`InpatientPatientMedicationUsageSummaryLineDto`（汇总行）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `medicationId` | `Long` | 分组键 |
| `itemName` | `String` | 药品名称 |
| `itemSpec` | `String` | 规格 |
| `unitPrice` | `BigDecimal` | 组内任一条账单单价（可空） |
| `lineTotalAmount` | `BigDecimal` | 组内账单行合计**净额** |
| `packageQuantity` | `Integer` | 满包装净整数数量 |
| `splitUnitQuantity` | `BigDecimal` | 不足一包净小单位数量 |
| `packageUnitCode` | `String` | 包装单位 code |
| `packageUnitDisplay` | `String` | 包装单位展示 |
| `splitUnitCode` | `String` | 小单位 code |
| `splitUnitDisplay` | `String` | 小单位展示 |

**DTO：`InpatientPatientMedicationUsageTotalsDto`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `grandTotal` | `BigDecimal` | 药品使用净金额合计 |
| `lineCount` | `Integer` | 当前模式下行数（明细行数或汇总行数） |

**关键校验**

| 规则 | 说明 |
| --- | --- |
| **BR-IP-PMU-001/011** | 单就诊；encounter/patient 一致且为 IMP |
| **BR-IP-PMU-002/202** | 日期合法 |
| **BR-IP-PMU-003～007** | 时间窗、状态、类型、明细/汇总口径 |
| **BR-IP-PMU-012** | 缺价不整单失败 |

**失败码**

| 码 | 场景 | 用户提示（与 AS 一致） |
| --- | --- | --- |
| **EX-IP-PMU-102** | 就诊不存在 / 非 IMP / patient 不匹配 | 当前患者不可用，请重新选择 |
| **EX-IP-PMU-103** | 域查询/装配未捕获异常 | 药品使用查询失败，请稍后重试 |
| **EX-IP-PMU-201** | `presentationMode` 非法 | 查询参数错误，请刷新页面 |
| **EX-IP-PMU-202** | 日期未传或起止倒置 | 请选择有效的查询日期 |

**领域 Query 契约（已落地，`DM-MED-002` §6.2.21）**

`MedicationDispenseQueryService#listInpatientPatientMedicationUsageDispenseRows(InpatientPatientMedicationUsageDispenseQuery): List<InpatientPatientMedicationUsageDispenseRow>`

| Query 字段 | 说明 |
| --- | --- |
| `encounterId` | 必填 |
| `plannedDispenseDateFrom` / `plannedDispenseDateTo` | 闭区间（转 `LocalDateTime` 日界） |
| `statusEnumIn` | AS §1.3 纳入状态白名单 |
| `dispenseEnumIn` | `INPATIENT`、`TEMPORARY`、`REFUND` |
| `excludeDeleted` | `true` |

| Row 字段（最小） | 说明 |
| --- | --- |
| `dispenseId`、`medicationId`、`encounterId`、`patientId` | 标识 |
| `statusEnum`、`dispenseEnum` | 状态/类型 |
| `quantityValue`、`quantityUnitCode` | 数量 |
| `plannedDispenseTime`、`summaryNo`、`lotNumber` | 时间与展示 |
| `medReqId` | 可选，定价锚点 |

排序：`planned_dispense_time` 倒序、`id` 倒序。

**Mapper 接口方法签名**

- `toUsageDispenseQuery(QueryInpatientPatientMedicationUsageRequest request): InpatientPatientMedicationUsageDispenseQuery`
- `toQueryResponse(String presentationMode, List<InpatientPatientMedicationUsageDetailLineDto> detailLines, List<InpatientPatientMedicationUsageSummaryLineDto> summaryLines, InpatientPatientMedicationUsageTotalsDto totals): QueryInpatientPatientMedicationUsageResponse`

**Assembler / Support 方法签名**

- `mapUsageStatusCode(String statusEnum): String`
- `buildDetailLines(List<InpatientPatientMedicationUsageDispenseRow> rows, Map<String, ProductDefinitionDisplayDto> productByKey, Map<Long, ChargePricingView> pricingByDispenseId, Map<Long, LocalDateTime> recordedTimeByProcedureId): List<InpatientPatientMedicationUsageDetailLineDto>`
- `buildSummaryLines(...): List<InpatientPatientMedicationUsageSummaryLineDto>`
- `buildTotals(...): InpatientPatientMedicationUsageTotalsDto`
- `InpatientPatientMedicationUsageQuantityAssembler#aggregateNetPackageAndSplitQuantity(...): PackageSplitQuantityResult`
- `InpatientPatientMedicationUsageChargePricingSupport#loadPricingByDispenseIds(Long encounterId, List<InpatientPatientMedicationUsageDispenseRow> rows): Map<Long, ChargePricingView>`
- 产品装载：`ProductDefinitionDisplayTerminologyAssembler#loadAsMap`；复合键：`ProductDefinitionKeyUtil#buildCompositeKey`

**领域编排序列（严格顺序）**

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | App 校验 | `encounterId`、`patientId`、`dateFrom`/`dateTo`、`presentationMode` | 缺参/日期倒置 → **EX-IP-PMU-202**；非法 mode → **EX-IP-PMU-201** |
| 2 | Gate | `EncounterContextReader#mustGetInpatientContextByEncounterId` | `encounterId` | 须存在、`class=IMP`、`patientId` 一致；否则 **EX-IP-PMU-102** |
| 3 | Pre | `InpatientPatientMedicationUsageMapper#toUsageDispenseQuery` | Request | 填时间窗与白名单 |
| 4 | Pre | `MedicationDispenseQueryService#listInpatientPatientMedicationUsageDispenseRows` | ③ Query | **BR-IP-PMU-003～005**；仅读路径 |
| 5 | Pre | `InpatientPatientMedicationUsageChargePricingSupport#loadPricingByDispenseIds` | `encounterId` + ④ | 内调 `ChargeItemQueryService#listChargeItemsForMedicationDispensePricing` + `InpatientMedicationDispenseChargePricingLoader#mergePendingSettlementPricingMaps`；缺价可空 **BR-IP-PMU-012** |
| 6 | Pre | `ProductDefinitionDisplayTerminologyAssembler#loadAsMap` | ④ medication 复合键 | 批量 IN |
| 7 | Asm | `InpatientPatientMedicationUsageAssembler#buildTotals` | ④⑤⑥ | 净额合计；**始终执行** |
| 8 | Asm | 若 `presentationMode=DETAIL`：批量 `ProcedureQueryService#listByIds` → `#buildDetailLines` | ④⑤⑥ + recorded 索引 | 三态映射 **BR-IP-PMU-006**；展示时间优先 recorded；含 `summaryNo` 展示但**不**改状态文案 |
| 9 | Asm | 若 `presentationMode=SUMMARY`：`#buildSummaryLines` | ④⑤⑥ + QuantityAssembler | 按 `medicationId` 净额 **BR-IP-PMU-007**；**不**输出 usageStatus |
| 10 | Asm | `InpatientPatientMedicationUsageMapper#toQueryResponse` | 7～9 | 空列表允许；合计可为 0 |
| — | — | — | — | **仅读路径**；**无 Cmd** |

> **互斥说明**：步骤 8 与 9 **互斥**——`DETAIL` 仅执行 8 跳过 9；`SUMMARY` 仅执行 9 跳过 8。步骤 7 **始终执行**。

---

## 4. QueryService 与领域事件

| 项 | 约定 |
| --- | --- |
| 发放事实 | `listInpatientPatientMedicationUsageDispenseRows` 直读 `med_medication_dispense` |
| 账单价 | ChargeItem 发放定价读族 + 住院待结算补充；**不**按结算态过滤展示 |
| 产品主数据 | `ProductDefinitionDisplayTerminologyAssembler#loadAsMap` |
| 事件 | 执行/发药/退药事件后用户**手动重新查询**；本模块不订阅推送 |

```text
护士执行 / 药房发退药写侧 → med_medication_dispense / adm_charge_item
本页查询时刻：listInpatientPatientMedicationUsageDispenseRows + 定价批量 + loadAsMap
```

---

## 5. 写库顺序（核心写接口视角）

本模块 **无写接口**。

| IF | App 方法 | Cmd | 说明 |
| --- | --- | --- | --- |
| IF-IP-PMU-01 | `loadInpatientPatientMedicationUsagePageInit` | — | 只读 |
| IF-IP-PMU-02 | `pageInpatientPatientMedicationUsagePatients` | — | 只读 |
| IF-IP-PMU-03 | `queryInpatientPatientMedicationUsage` | — | 只读 |

| 事务边界 | 说明 |
| --- | --- |
| 无事务 | 只读查询 |

| 失败回滚 | 不适用 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 查询幂等 | 相同 Request 重复调用结果一致（以 DB 当前事实为准） | `InpatientPharmacyPatientMedicationUsageAppService#queryInpatientPatientMedicationUsage` | §3.4 序号 3～10 |

### 6.2 并发控制

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 只读无锁 | 不引入乐观锁写 | 全类 | — |

### 6.3 重试与容错

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 就诊门禁 | 不一致 → **EX-IP-PMU-102** | Gate | §3.4 序号 2 |
| 其它异常 | → **EX-IP-PMU-103** | AppService catch | §3.4 |

### 6.4 数据脱敏

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 最小 PII | 仅姓名/住院号/床号等列表必需字段 | PatientRowDto | IF-02 Asm |

### 6.5 审计日志

| 项 | 承诺 | 代码位置（必须） | 关联步骤 |
| --- | --- | --- | --- |
| 无写路径 | 只读；无写审计强制点 | — | — |

---

## 7. 测试要点

### 7.1 功能测试

| 用例 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 初始化默认当日 | `dateFrom=dateTo=today` | 集成 | P0 |
| 患者关键字命中在院 | 返回分页行 | 集成 | P0 |
| 明细：待发含 SUMMARIZED | `usageStatusCode=PENDING` | 集成 | P0 |
| 明细：COMPLETED→已发 | `DISPENSED` | 集成 | P0 |
| 明细：REFUNDED→已退 | `RETURNED`；金额负 | 集成 | P0 |
| 时间窗按 planned_dispense_time | 边界日命中/未命中 | 集成 | P0 |
| 空计划时间不入窗 | 不出现 | 集成 | P0 |
| 汇总：同药净额含退药冲减 | `lineTotalAmount` 轧差 | 集成 | P0 |
| 汇总无状态字段 | DTO 无 usageStatus | 契约 | P0 |
| 合计两模式一致 | `grandTotal` 相同 | 集成 | P0 |
| 不按库房过滤 | 他库药品可见 | 集成 | P1 |
| patient 不一致 | **EX-IP-PMU-102** | 集成 | P0 |

### 7.2 非功能测试

| 用例 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 单就诊 500+ 行 | 无 N+1 产品/价查询 | 性能 | P1 |

### 7.3 查询结果一致性

| 用例 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| 执行后刷新 | 新待发行出现 | 手工/集成 | P1 |
| 退药后刷新 | 汇总净额下降 | 手工/集成 | P0 |

### 7.4 集成测试

| 用例 | 预期 | 自动化归属 | 优先级 |
| --- | --- | --- | --- |
| HTTP POST 路由 | `POST /api/app/inpatientPharmacyPatientMedicationUsage/queryInpatientPatientMedicationUsage` | 契约 | P0 |

### 7.5 测试缺口跟踪

| 缺口 | 说明 | 计划 |
| --- | --- | --- |
| 应用层集成测 | AppService 已成码；补 IF-IP-PMU-03 集成断言 | 随联调 |

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.3 | 2026-08-06 | 明细展示双轨：`recordedTime`/`procedureId`；`buildDetailLines` 增 recorded 索引；过滤仍 planned |
| v1.2 | 2026-07-15 | 应用层 IF-01～03 已落地；产品批读对齐 `ProductDefinitionDisplayTerminologyAssembler`；IF-02 分页壳对齐 `PagedResult`；补 `ChargePricingView` |
| v1.1 | 2026-07-15 | 领域 `listInpatientPatientMedicationUsageDispenseRows` 已落地；对齐 AS v1.1 / DM §6.2.21；**GAP-IP-PMU-001** 已关闭 |
| v1.0 | 2026-07-15 | 首版：IF-IP-PMU-01～03；发放锚点 + 账单价；明细三态；汇总取净；不收窄库房；对齐 AS v1.0；领域方法待 **GAP-IP-PMU-001** |
