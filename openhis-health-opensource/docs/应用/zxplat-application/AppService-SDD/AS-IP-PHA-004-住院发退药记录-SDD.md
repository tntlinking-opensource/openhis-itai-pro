# 住院发退药记录 · 应用服务详细设计（SDD）

> **编码**：AS-IP-PHA-004-SDD  
> **版本**：v1.0 | **日期**：2026-06-21  
> **编排权威**：`AS-IP-PHA-004-住院发退药记录.md`（**v1.0**）  
> **领域权威**：`DM-MED-002-MedicationDispense-药品发放.md`（§6.2.18）、`DM-MED-003`、`DM-MED-001`、`DM-ADM-011`、`DM-ADM-002` §6.2.18  
> **说明**：本 SDD **不维护 Gap 表**；领域层已对齐；应用层为目标态全量契约。

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院发退药记录（初始化、分页查询、导出） |
| **边界** | 纯读；编排记录查询 + 住院上下文装配；**不**汇总发药/退药写路径；**不**小计 |
| **编排权威** | `AS-IP-PHA-004` §2 / §4 |
| **方法权威** | `DM-MED-002` §6.2.18；`DM-ADM-002` §6.2.18；`DM-ADM-011` §6.2 |
| **严格对齐** | IF **IF-IPR-01～03** |
| **实现阶段口径** | **目标态全量契约**（`inpatient/pharmacy/dispenserecord/**` 待生成） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **时间轴** | `dispense_time`；映射 `MedicationDispenseRecordPageQuery.startDate/endDate` |
| **场景** | `inpatientMedicationOnly=true`；正向 `dispense_enum ∈ {INPATIENT,TEMPORARY}` |
| **退药场景** | 退药行 `REFUND+REFUNDED`；有患者关键字时 `resolvedEncounterIdsForScene` 写入 IMP 就诊集合；无关键字时领域 **EXISTS** 关联 `adm_encounter.class_enum=IMP`（**DM-MED-002** §6.2.18） |
| **住院上下文** | `EncounterContextReader#listInpatientContextsByEncounterIdsOrdered` → 住院号、床号、病区 |
| **价格** | 按就诊分组 `listChargeItemsForMedicationDispensePricing`；住院在院待结算补充 `InpatientMedicationDispenseChargePricingLoader`（与 slip 读侧同族） |
| **前端展示** | 接口平铺；`pharmacy-dispense-record-page.vue`（`scope=inpatient`）默认按处方合并组头列；可切换按就诊/平铺；见 `dispense-record-group-display.ts` |
| **HTTP** | `POST /api/app/inpatientPharmacyDispenseRecord/{methodName}` |
| **事务** | 纯读；禁止 `@Transactional` |

---

## 2. 代码结构

### 2.1 包路径

```text
zxplat/health-application/src/main/java/com/openhis/health/application/inpatient/pharmacy/dispenserecord/
  service/
    InpatientPharmacyDispenseRecordAppService.java
  request/
    LoadInpatientPharmacyDispenseRecordPageInitRequest.java
    PageInpatientPharmacyDispenseRecordsRequest.java
    ExportInpatientPharmacyDispenseRecordsRequest.java
  response/
    LoadInpatientPharmacyDispenseRecordPageInitResponse.java
    PageInpatientPharmacyDispenseRecordsResponse.java
    ExportInpatientPharmacyDispenseRecordsResponse.java
  dto/
    InpatientPharmacyDispenseRecordRowDto.java
    InpatientPharmacyDispenseRecordExportRowDto.java
  mapper/
    InpatientPharmacyDispenseRecordMapper.java
    InpatientPharmacyDispenseRecordMapperImpl.java

zxplat/health-application/src/main/java/com/openhis/health/application/outpatient/pharmacy/dispense/support/
  PharmacyDispenseRecordQuerySupport.java      （与门诊共用；`enrichInpatientRecordFilters`）
  PharmacyDispenseRecordReadAssembler.java     （`assembleInpatientRows`）
  PharmacyDispenseRecordChargePricingSupport.java
  InpatientMedicationDispenseChargePricingLoader （住院 charge 合并，已存在）

zxplat/zxplat-domain/src/main/java/com/openhis/health/domain/share/constants/
  PharmacyDispenseRecordConstants.java
  PharmacyDispenseRecordExportConstants.java
  InpatientPharmacyDispenseRecordConstants.java （目标态：EX-IPR 语义码）
```

> `PharmacyDispenseRecordRowDto` 可复用门诊同结构体于 `module-support 或 application `dto`；住院行 DTO **扩展** `inpatientNo`/`wardName`/`bedName` 字段。

### 2.2 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | Gate |
| `LocationQueryService` | IF-IPR-01 药房 |
| `TerminologyOptionLoader` | 项目类型 |
| `PractitionerQueryService` | 发药人下拉与姓名 |
| `MedicationDispenseQueryService` | 分页/列表 |
| `PharmacyDispenseRecordQuerysupport | 预解析 |
| `MedicationRequestQueryService` | 药嘱批量 |
| `MedicationDefinitionQueryService` | 药品展示与预解析 |
| `ChargeItemQueryService` | 锚点读价 |
| `InpatientMedicationDispenseChargePricingLoader` | 住院待结算价合并 |
| `EncounterContextReader` | 住院上下文 |
| `EncounterQueryService` | 患者关键字；`findAllByIdInAndClassEnum(IMP)` 场景过滤 |
| `PatientQueryService` | 患者关键字 |
| `OrganizationQueryService` | 开单科室 |
| `ProductDefinitionBatchLoader` | 药品展示 |
| `PharmacyDispenseRecordReadAssembler` | 行装配 |
| `AnnotatedExcelExporter` | 导出 |
| `InpatientPharmacyDispenseRecordMapper` | 装配 |

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 发放状态 | `com.zxplat.domain.share.enums.DispenseStatus` | `DM-MED-002` §2.2 | `COMPLETED`/`REFUNDED` |
| 发放类型 | `com.zxplat.domain.share.enums.MedicationDispenseType` | `DM-MED-002` §3.1 | 正向 `INPATIENT`/`TEMPORARY`；退药 `REFUND` |
| 就诊类别 | `com.zxplat.domain.share.enums.EncounterClass` | `DM-ADM-002` §3 `class_enum` | IMP 场景过滤 |
| 记录类型筛 | `PharmacyDispenseRecordConstants.*` | **BR-IPR-004** | 同门诊 |
| 住院行字段 | `InpatientPharmacyDispenseRecordRowDto.inpatientNo` | **BR-IPR-012**；`DM-ADM-002` §6.2.18 | 目标态待生成 |
| 住院行字段 | `InpatientPharmacyDispenseRecordRowDto.bedName` | **BR-IPR-012** | 目标态待生成 |
| 住院行字段 | `InpatientPharmacyDispenseRecordRowDto.wardName` | **BR-IPR-012** | 目标态待生成 |
| 应用语义码 | `InpatientPharmacyDispenseRecordConstants` | **AS** §5 **EX-IPR-*** | 目标态待生成 |

### 2.4 领域方法与 Mapper 白名单

**领域读**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `MedicationDispenseQueryService` | `pageMedicationDispenseRecords` | IF-IPR-02 |
| `MedicationDispenseQueryService` | `listMedicationDispenseRecords` | IF-IPR-03 |
| `LocationQueryService` | `listAllActivePharmacies` | IF-IPR-01 |
| `MedicationDefinitionQueryService` | `listIdsByCategoryCodesIn` | support |
| `MedicationDefinitionQueryService` | `pageForCatalog` | support 药品关键字 |
| `MedicationDefinitionQueryService` | `findAllByIds` | IF-IPR-02/03 |
| `MedicationRequestQueryService` | `listMedicationRequestListModelsByIds` | IF-IPR-02/03 |
| `ChargeItemQueryService` | `listChargeItemsForMedicationDispensePricing` | IF-IPR-02/03 |
| `EncounterQueryService` | `listEncounterIdsByBusNoKeyword` | support |
| `EncounterQueryService` | `listEncounterIdsByPatientIds` | support |
| `EncounterRepository` | `findAllByIdInAndClassEnum` | support IMP 场景过滤（经 EncounterQueryService 封装） |
| `PatientQueryService` | `listPatientIdsByEncounterListKeyword` | support |
| `EncounterContextReader` | `listInpatientContextsByEncounterIdsOrdered` | IF-IPR-02/03 |
| `PractitionerQueryService` | `pageActivePractitionersForPosition` | IF-IPR-01 |

**module-support**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `PharmacyDispenseRecordQuerysupport | `enrichInpatientRecordFilters` | IF-IPR-02/03 |
| `PharmacyDispenseRecordReadAssembler` | `assembleInpatientRows` | IF-IPR-02/03 |
| `InpatientMedicationDispenseChargePricingLoader` | `mergePendingSettlementPricingMaps` | IF-IPR-02/03（按就诊分组循环） |
| `OrganizationQueryService` | `mapNamesByIds` | IF-IPR-02/03 |
| `PractitionerQueryService` | `findAllByIds` | IF-IPR-02/03 |
| `ProductDefinitionBatchLoader` | `loadAsMap` | IF-IPR-02/03 |

**Mapper**

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `InpatientPharmacyDispenseRecordMapper` | `toMedicationDispenseRecordPageQuery` | IF-IPR-02/03 |
| `InpatientPharmacyDispenseRecordMapper` | `toLoadPageInitResponse` | IF-IPR-01 |
| `InpatientPharmacyDispenseRecordMapper` | `toPageResponse` | IF-IPR-02 |
| `InpatientPharmacyDispenseRecordMapper` | `toExportRowDto` | IF-IPR-03 |
| `InpatientPharmacyDispenseRecordMapper` | `toExportResponse` | IF-IPR-03 |
| `InpatientPharmacyDispenseRecordMapper` | `buildRecordTypeOptions` | IF-IPR-01 |

---

## 3. 按接口 ID 命名展开

**导语**：先读完各 IF 的「领域编排序列」，按序号自上而下执行。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IPR-01 | `loadInpatientPharmacyDispenseRecordPageInit` | 住院发退药记录页初始化 | `POST /api/app/inpatientPharmacyDispenseRecord/loadInpatientPharmacyDispenseRecordPageInit` |
| IF-IPR-02 | `pageInpatientPharmacyDispenseRecords` | 分页查询住院发退药记录 | `POST /api/app/inpatientPharmacyDispenseRecord/pageInpatientPharmacyDispenseRecords` |
| IF-IPR-03 | `exportInpatientPharmacyDispenseRecords` | 导出住院发退药记录 | `POST /api/app/inpatientPharmacyDispenseRecord/exportInpatientPharmacyDispenseRecords` |

### 3.1.1 编排约定

同 **AS-OP-PHA-004-SDD** §3.1.1（Gate / Pre / Asm；本期无 Cmd）。

---

### 3.2 IF-IPR-01 `loadInpatientPharmacyDispenseRecordPageInit`（住院发退药记录页初始化）

**Request**：空体（同门诊 IF-OPR-01）。

**Response `LoadInpatientPharmacyDispenseRecordPageInitResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `defaultDispenseDateFrom` | `String` | 默认发药日起 |
| `defaultDispenseDateTo` | `String` | 默认发药日止 |
| `pharmacyOptions` | `List<OptionDto>` | 全部启用药房 |
| `medicationCategoryOptions` | `List<OptionDto>` | 项目类型 |
| `dispenserOptions` | `List<OptionDto>` | 发药人 |
| `recordTypeOptions` | `List<OptionDto>` | 记录类型 |

**编排序列**：同门诊 IF-OPR-01（序号 1～6），失败码 **EX-IPR-101**。

---

### 3.3 IF-IPR-02 `pageInpatientPharmacyDispenseRecords`（分页查询住院发退药记录）

**Request `PageInpatientPharmacyDispenseRecordsRequest`**

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dispenseDateFrom` | `String` | 是 | 发药日起 |
| `dispenseDateTo` | `String` | 是 | 发药日止 |
| `recordTypeCode` | `String` | 否 | `ALL`/`DISPENSE`/`REFUND` |
| `pharmacyLocationId` | `Long` | 否 | 药房 |
| `medicationCategoryCodes` | `List<String>` | 否 | 项目类型多选 |
| `dispenserPractitionerId` | `Long` | 否 | 发药人 |
| `drugKeyword` | `String` | 否 | 药品关键字 |
| `patientKeyword` | `String` | 否 | 患者关键字 |
| `pageNo` | `Integer` | 是 | 页码 |
| `pageSize` | `Integer` | 是 | 页大小 |

**Response `PageInpatientPharmacyDispenseRecordsResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `pageNo` | `Integer` | 当前页 |
| `pageSize` | `Integer` | 每页条数 |
| `totalCount` | `Long` | 总条数 |
| `content` | `List<InpatientPharmacyDispenseRecordRowDto>` | 平铺明细 |

**DTO `InpatientPharmacyDispenseRecordRowDto` 字段表**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dispenseId` | `Long` | 发放单主键 |
| `recordTypeCode` | `String` | `DISPENSE`/`REFUND` |
| `recordTypeText` | `String` | 记录类型展示 |
| `dispenseTime` | `String` | 发药时间 |
| `pharmacyLocationId` | `Long` | 药房主键 |
| `pharmacyLocationName` | `String` | 药房名称 |
| `dispenserPractitionerId` | `Long` | 发药人主键 |
| `dispenserName` | `String` | 发药人姓名 |
| `patientId` | `Long` | 患者主键 |
| `patientName` | `String` | 患者姓名 |
| `encounterId` | `Long` | 就诊主键 |
| `inpatientNo` | `String` | 住院号（`encounterBusNo`） |
| `wardName` | `String` | 病区名称 |
| `bedName` | `String` | 床号 |
| `prescriptionNo` | `String` | 处方号 |
| `medicationId` | `Long` | 药品主键 |
| `medicationCode` | `String` | 药品编码 |
| `medicationName` | `String` | 药品名称 |
| `medicationSpec` | `String` | 规格 |
| `quantity` | `BigDecimal` | 数量 |
| `unitCode` | `String` | 单位 code |
| `unitText` | `String` | 单位展示 |
| `unitPrice` | `BigDecimal` | 单价 |
| `totalAmount` | `BigDecimal` | 金额（退药负） |
| `lotNumber` | `String` | 批号 |
| `traceNo` | `String` | 追溯码 |
| `orderingDepartmentId` | `Long` | 开单科室 |
| `orderingDepartmentName` | `String` | 开单科室名称 |
| `usageText` | `String` | 用法用量 |
| `summaryNo` | `String` | 汇总单号 |
| `medReqId` | `Long` | 药嘱主键 |

**失败码**：EX-IPR-102（时间窗）、EX-IPR-103（查询失败）

**Mapper 方法签名**

- `toMedicationDispenseRecordPageQuery(PageInpatientPharmacyDispenseRecordsRequest request): MedicationDispenseRecordPageQuery`
- `toPageResponse(Page<MedicationDispenseSlipQueryModel> page, List<InpatientPharmacyDispenseRecordRowDto> rows): PageInpatientPharmacyDispenseRecordsResponse`

**领域编排序列**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `CurrentOperatorResolver#requireOperatorId` | EX-IPR-103 |
| 2 | Gate | 时间窗校验 | EX-IPR-102 |
| 3 | Asm | `Mapper#toMedicationDispenseRecordPageQuery` | 映射 Query |
| 4 | Pre | `PharmacyDispenseRecordQuerySupport#enrichInpatientRecordFilters` | `inpatientMedicationOnly=true`；有患者关键字时 IMP 就诊 id 写入 `resolvedEncounterIdsForScene`；无关键字时退药场景由领域 EXISTS（IMP）收窄 |
| 5 | Pre | `MedicationDispenseQueryService#pageMedicationDispenseRecords` | **BR-IPR-001～005**；退药行同窗见 **DM-MED-002** §6.2.18 |
| 6 | Pre | `MedicationRequestQueryService#listMedicationRequestListModelsByIds` | 页内 `medReqId` | 处方号、用法（**仅**当前药嘱；**禁止**读侧回落原药嘱） |
| 7 | Pre | `ProductDefinitionBatchLoader#loadAsMap` | 药品展示 |
| 8 | Pre | `EncounterContextReader#listInpatientContextsByEncounterIdsOrdered` | **BR-IPR-012** 住院号/床号/病区 |
| 9 | Pre | 按就诊分组 `ChargeItemQueryService#listChargeItemsForMedicationDispensePricing` | 正向 dispense 锚点 |
| 10 | Pre | 按就诊分组 `InpatientMedicationDispenseChargePricingLoader#mergePendingSettlementPricingMaps` | 补充 `PENDING_SETTLEMENT` |
| 10b | Pre | `PharmacyDispenseRecordChargePricingSupport#supplementRefundPricingFromOriginMedicationRequests` | 退药未命中步骤 9/10 | **BR-IPR-010** |
| 11 | Pre | `OrganizationQueryService#mapNamesByIds`；`PractitionerQueryService#findAllByIds` | 科室名、发药人名 |
| 12 | Asm | `PharmacyDispenseRecordReadAssembler#assembleInpatientRows` | 退药负金额；处方号/科室/用法**仅**取发放行或当前 `medReqId` 药嘱 |
| 13 | Asm | `Mapper#toPageResponse` | 返回分页 |

---

### 3.4 IF-IPR-03 `exportInpatientPharmacyDispenseRecords`（导出住院发退药记录）

**Request**：筛选字段同 IF-IPR-02（无分页键）。

**Response `ExportInpatientPharmacyDispenseRecordsResponse`**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `fileName` | `String` | 文件名 |
| `contentType` | `String` | MIME |
| `body` | `byte[]` | 字节流 |
| `exportedRowCount` | `Integer` | 行数 |
| `truncated` | `Boolean` | 是否截断 |

**DTO `InpatientPharmacyDispenseRecordExportRowDto` 字段表**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `recordTypeText` | `String` | 记录类型 |
| `dispenseTime` | `String` | 发药时间 |
| `pharmacyLocationName` | `String` | 药房 |
| `dispenserName` | `String` | 发药人 |
| `patientName` | `String` | 患者 |
| `inpatientNo` | `String` | 住院号 |
| `wardName` | `String` | 病区 |
| `bedName` | `String` | 床号 |
| `prescriptionNo` | `String` | 处方号 |
| `medicationCode` | `String` | 药品编码 |
| `medicationName` | `String` | 药品名称 |
| `medicationSpec` | `String` | 规格 |
| `quantity` | `BigDecimal` | 数量 |
| `unitText` | `String` | 单位 |
| `unitPrice` | `BigDecimal` | 单价 |
| `totalAmount` | `BigDecimal` | 金额 |
| `lotNumber` | `String` | 批号 |
| `traceNo` | `String` | 追溯码 |
| `orderingDepartmentName` | `String` | 开单科室 |
| `usageText` | `String` | 用法用量 |
| `summaryNo` | `String` | 汇总单号 |

**编排序列**：复用 IF-IPR-02 序号 1～12（步骤 5 改为 `listMedicationDispenseRecords` + 导出行上限）；序号 13～15 同门诊导出（`toExportRowDto`、`AnnotatedExcelExporter`、`toExportResponse`）。失败码 **EX-IPR-104**。

---

## 4. QueryService 与领域事件

同门诊 SDD §4；住院额外依赖 `InpatientMedicationDispenseChargePricingLoader` 合并待结算 charge。

**本期无写操作、无领域事件。**

---

## 5. 写库顺序（核心写接口视角）

| IF | Cmd | 说明 |
| --- | --- | --- |
| IF-IPR-01～03 | **无** | 纯读 |

---

## 6. 非功能性需求实现（收敛为承诺表）

同门诊 SDD §6 结构；语义码前缀 **EX-IPR-***；关联步骤指向 §3.3/3.4。

---

## 7. 测试要点

### 7.1 功能测试（P0）

| 用例 | 要点 |
| --- | --- |
| 逐行发药/汇总发药 | `COMPLETED` 可见；`summary_no` 展示 |
| 退药行 | `REFUNDED`；金额负 |
| 住院字段 | 住院号、床号、病区 |
| 场景 | 不含门诊 `OUTPATIENT` 正向行 |
| 价格 | 在院待结算 + 已结算锚点均可展示 |

### 7.2～7.5

同门诊 SDD §7.2～7.5（性能、一致性、集成、导出压测）。

---

## 8. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.4 | 2026-07-11 | §1.2 / IF-IPR-02 步骤 4～5：退药行 IMP 同窗；无患者关键字时领域 EXISTS 收窄（对齐 **DM-MED-002** v1.3.10） |
| v1.3 | 2026-06-26 | **BR-IPR-015**：取消读侧原药嘱回落；退药价仍走步骤 10b |
| v1.2 | 2026-06-26 | **`PharmacyDispenseRecordChargePricingsupport** 退药补价；前端一发一退相邻（同 **BR-OPR-013**） |
| v1.0 | 2026-06-21 | 初版：住院发退药记录目标态 SDD（IF-IPR-01～03） |
| v1.1 | 2026-06-24 | 前端按处方/按就诊/平铺展示切换与组头列合并 |
