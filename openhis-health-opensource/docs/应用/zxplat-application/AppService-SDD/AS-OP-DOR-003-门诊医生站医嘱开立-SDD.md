# 门诊医生站 · 医嘱开立 · 应用服务详细设计（SDD）

> **编码**：AS-OP-DOR-003-SDD  
> **版本**：v3.62 | **日期**：2026-10-13 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界


| 项 | 内容 |
| --- | --- |
| **模块** | 门诊医生站 · 医嘱开立应用服务（`outpatient/doctorstation/advice`） |
| **边界** | 当前就诊医嘱统一列表、开立页初始化、目录分页、绑定、IF-DOR-19/20、批次暂存/签发/签退/组号；**检验/检查/治疗申请单**（**IF-DOR-21/22**，**`AR` 号**，编辑**取新号**）；**门诊皮试联动**（**BR-ORD-025**：药品 `skinTestFlag` → 联动「皮试检查」SR + 操作费草稿，下游 **AS-OP-NUR-001 §1.7**）；扩展 **IF-DOR-18**（部位/样本）、**IF-DOR-11**（`categoryEnum` + 可选 `typeEnum`）；费用性质见 **IF-DOR-01** |
| **编排权威** | `AS-OP-DOR-003-门诊医生站医嘱开立.md`（**v1.62**）§2 + §4 + **§1.8** |
| **方法权威** | `DM-MED-003` §6 医嘱写路径；`DM-MED-002` §6 发放；`DM-WKF-003`/`DM-WKF-007` §6；`DM-WKF-004` §6；`DM-ADM-011` §6 收费（含 **`BR-CHG-001`** 门诊发放锚点扩展说明）；`DM-MED-001`/`DM-WKF-001`/`DM-ADM-008` 目录主数据；**`ProductDefinition` QueryService 文档**（IF-DOR-11 分页真源；DM §6 查询契约已废弃，见 QueryModel + application.shared / feature support 迁移）；**`DM-CUS-006`** 取药/发材科室模板；`DM-ADM-002` 就诊上下文；费用性质主数据以 **`DM-ADM-011`** 与财务域合同/费用性质 DM（如 **`DM-FIN-002`**，以仓库定稿文件名准）交叉锚点为准 |
| **严格对齐** | IF 编号 **IF-DOR-10～23** 与 AS 一致（**IF-DOR-13** 废止） |
| **实现阶段口径** | **首版已落地**（`zxplat` → `application.outpatient.doctorstation.advice`）。**IF-DOR-10** 列表 DTO/装配已与 **§3.2** 主体对齐（上游 **G-DOR-003-10** 已关闭主体）；**残余可空栏位**：`prescriptionCategoryCode`/`prescriptionCategoryName`、`signedById`（DTO 已声明、装配未赋值）。本 SDD 与代码不一致时以 **Java 真源** 为准，经 **§2.6**、§8 消差。 |


### 1.2 术语口径


| 项 | 约定 |
| --- | --- |
| **字段口径** | 入出参与 AS、DM 一致；持久化枚举字段存 **String code**（与领域实体 `*_enum` 列对齐） |
| **QueryService口径** | 写前门控与患者一致性：`EncounterContextReader#mustGetOutpatientContextByEncounterId`（`OutpatientEncounterContextQueryModel` `EncounterQueryService`文档 §3 `Q-OPI-003`）；医嘱列表 AS 允许过渡期直连聚合读侧——本 SDD以 **域查询服务 + 应用装配** 组合实现，与 AS §2 IF-DOR-10「QueryService」列「过渡期允许」表述一致 |
| **事务口径** | 《代码生成通用规范》**B.8**：**领域** `*DomainService`/`*QueryService` **不**声明 `@Transactional`。**IF-DOR-14**（`saveDoctorStationAdviceDraftBatch`）、**IF-DOR-15**（`signDoctorStationAdviceBatch`）已在 `DoctorStationAdviceAppService` 声明 `@Transactional(rollbackFor = Exception.class)`。**IF-DOR-16/17** 目标态仍待补注解；只读 IF-DOR-10～12、18、19 无事务注解。**IF-DOR-15** 外发为 `NoopLisPacsApplyGateway` 占位，不阻断本地签发。 |
| **计费服务锚点（门诊药/材）** | **`ChargeItem.service_table` + `ChargeItem.service_id`** 在已存在发放物理行场景下**须锚定** `med_medication_dispense` / `wkf_device_dispense`（实现期物理表名以常量为准）**行主键**，与 **AS `BR-ORD-016`**、**`DM-ADM-011`** 表下说明一致；**禁止**仅以请求表主键作为门诊拆库拆批场景下的唯一计费锚点 |
| **金额权威** | **`ChargeItem`** 之 `unit_price` / `total_price` 等金额字段以 **`ChargeItemDefinition`**（含 **`LOT_NUMBER_PRICE`** 条件价）后端重算落库为准；应用请求体中的展示金额**不得**作为入账权威（与 AS **`BR-ORD-016`** 一致） |
| **HTTP 接入口径** | `AppServiceControllerRegistrar` 动态注册：**`POST /api/app/doctorStationAdvice/{methodName}`**，请求体 JSON 与方法形参一致（`serviceName` 由 `DoctorStationAdviceAppService` → `doctorStationAdvice`） |
| **业务异常码口径** | 实现层统一 `BusinessException`，`errorCode` 取 `ErrorCodeConstants.ErrorCode`；AS 中 `EX-ORD-*` 为语义别名，与本 SDD §3 各 IF「失败码口径表」对照 |


#### 1.2.1 文档语义码与 `ErrorCodeConstants.ErrorCode` 对照（实现口径）


| 文档语义码 | 实现 `ErrorCode`（典型触发） |
| --- | --- |
| EX-ORD-101 | （**废止**）原「定价来源未配置」 |
| EX-ORD-102 / EX-ORD-103 | `CHARGE_ITEM_RULE_CONFLICT` / `CHARGE_ITEM_INVALID_ARGUMENT`（已结算不可删/不可撤） |
| EX-ORD-104 | `MEDICATION_REQUEST_INVALID_ARGUMENT` / `DEVICE_REQUEST_INVALID_ARGUMENT` / 库存聚合侧业务码（以库存校验实现为准） |
| EX-ORD-105 / EX-ORD-205 | 外发网关 `RuntimeException` 或专用 `IntegrationException`（严格模式）；忽略模式不抛出 |
| EX-ORD-106 | `CHARGE_ITEM_NOT_FOUND` 或应用层固定文案包装 |
| EX-ORD-201 | `ENCOUNTER_NOT_FOUND` / `SERVICE_REQUEST_INVALID_ARGUMENT` / 查询参数非法等只读失败 |
| EX-ORD-207 | 持久化/乐观锁冲突等 `BusinessException` 或数据访问异常包装 |


---

## 2. 代码结构

### 2.1 包路径（类级清单）

```
health-application/src/main/java/com/openhis/health/application/
└── outpatient/
    └── doctorstation/
        └── advice/
            ├── DoctorStationAdviceConstants.java
            ├── service/
            │   └── DoctorStationAdviceAppService.java
            ├── assembler/
            │   ├── DoctorStationAdviceListAssembler.java
            │   ├── DoctorStationAdviceCatalogAssembler.java
            │   ├── OutpatientPrescriptionSplitAssembler.java
            │   ├── DoctorStationAdviceFifoAllocationAssembler.java
            │   └── ServiceApplyFormAssembler.java
            ├── port/
            │   ├── LisPacsApplyGateway.java
            │   ├── LisPacsAdviceApplyCommand.java
            │   └── NoopLisPacsApplyGateway.java
            ├── query/
            │   └── DoctorStationAdviceOrderBindQueryService.java
            ├── request/
            │   ├── ListDoctorStationAdviceRequest.java
            │   ├── PageDoctorStationAdviceCatalogRequest.java
            │   ├── GetDoctorStationAdviceOrderBindInfoRequest.java
            │   ├── ResolveDoctorStationAdviceDraftBindsRequest.java
            │   ├── InitDoctorStationAdviceRequest.java
            │   ├── ListActivityExecutionLocationsRequest.java
            │   ├── SaveDoctorStationAdviceDraftBatchRequest.java
            │   ├── SignDoctorStationAdviceBatchRequest.java
            │   ├── WithdrawDoctorStationAdviceBatchRequest.java
            │   ├── UpdateDoctorStationMedicationAdviceGroupsRequest.java
            │   ├── PageDoctorStationAdviceRequest.java
            │   ├── SaveDoctorStationServiceApplyFormRequest.java
            │   └── ListDoctorStationServiceApplyFormsRequest.java
            ├── response/
            │   ├── ListDoctorStationAdviceResponse.java
            │   ├── PageDoctorStationAdviceCatalogResponse.java
            │   ├── GetDoctorStationAdviceOrderBindInfoResponse.java
            │   ├── ResolveDoctorStationAdviceDraftBindsResponse.java
            │   ├── InitDoctorStationAdviceResponse.java
            │   ├── ListActivityExecutionLocationsResponse.java
            │   ├── ActivityExecutionLocationRuleRowDto.java
            │   ├── SaveDoctorStationAdviceDraftBatchResponse.java
            │   ├── SignDoctorStationAdviceBatchResponse.java
            │   ├── WithdrawDoctorStationAdviceBatchResponse.java
            │   ├── UpdateDoctorStationMedicationAdviceGroupsResponse.java
            │   ├── PageDoctorStationAdviceResponse.java
            │   ├── SaveDoctorStationServiceApplyFormResponse.java
            │   └── ListDoctorStationServiceApplyFormsResponse.java
            ├── dto/
            │   ├── DoctorStationAdviceRowDto.java
            │   ├── ServiceApplyFormLineRequest.java
            │   ├── ServiceApplyFormDetailItemRequest.java
            │   ├── ServiceApplyFormHeaderDto.java
            │   ├── ServiceApplyFormLineDto.java
            │   ├── ServiceRequestDetailSummaryDto.java
            │   ├── DoctorStationAdviceCatalogRowDto.java
            │   ├── PrepickedProductKeyDto.java
            │   ├── DoctorStationAdviceInventoryRowDto.java
            │   ├── DoctorStationAdviceOrderBindRowDto.java
            │   ├── DoctorStationAdviceDraftBindItemDto.java
            │   ├── DoctorStationAdviceDraftLineRequest.java
            │   └── DoctorStationAdviceResourceKeyDto.java
            └── mapper/
                ├── DoctorStationAdviceMapper.java
                └── DoctorStationAdviceMapperImpl.java
└── querysupport/
    ├── encounter/EncounterContextReader.java
    └── product/
        ├── ProductDefinitionCatalogPageLoader.java
        └── ProductDefinitionBatchLoader.java
zxplat/health-application/src/main/java/com/openhis/health/application/
    ├── outpatient/doctorstation/advice/support/
    │   └── OutpatientMedicationSignChargeOrchestrator.java  ← IF-DOR-15 药线 / IF-DOR-37 共用（FIFO 分段挂费）
    ├── shared/advice/
    │   ├── DoctorStationSkinTestSyncOrchestrator.java   ← IF-DOR-14/15/16 + IF-IP-DOR-14/15/16（**BR-ORD-025** / **BR-IP-ORD-024**）
    │   └── ServiceApplyFormOrchestrationSupport.java            ← IF-DOR-21/22、IF-IP-DOR-23/24 共享
    └── common/assembler/advice/model/
        ├── SkinTestMedicationDraftSpec.java
        └── AdviceSignTargetSpec.java
```

**皮试编排边界**：`DoctorStationAdviceAppService` / `InpatientDoctorStationAdviceAppService` 私有方法 `toSkinTestSpec` / `toSignTargetSpecs` / `fromSignTargetSpecs` 完成 HTTP DTO ↔ Spec 映射；**禁止** `application.shared` / feature `support` import `application.*` Request/Response。

**共享（产品目录单位槽，IF-DOR-11 与 IF-WHS-IN-11 复用）**

- **`com.zxplat.domain.module.common.dto.product.ProductDefinitionCatalogUnitBandDto`**：`DoctorStationAdviceCatalogRowDto#unitBands` 与进货选品 **`ProductDefinitionItemDto#unitBands`** 元素类型（JSON 字段 **`bandKind`/`unitCode`/`unitName`/`ratioToSplit`/`unitPrice`**）。
- **`com.zxplat.domain.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler`**：五槽（医生站）/ 四槽（进货选品）装配；多槽 **`unitPrice`** 在能解析包装槽（医生站/进货均为 **`BAND_PACKAGE`**↔QueryService **`package_unit_ratio`**）时以**每包装单位价**为锚做线性分配（**`P_slot = P_pkg × R_slot / R_pkg`**；**禁止**先舍入拆零单价再乘回各槽；**禁止**以拆零/其它槽单位为锚再往回乘倍数，见 **DM-ADM-010 §1.6**）。定价换算锚：**`#resolvePackageAnchoredPricingUnitCode`** / **`#resolveDoctorStationBandPricingUnitCode`**（**`PURCHASE`/`SALE`/`LOT_NUMBER_*`**，**仅**读 **`package_unit_code`**，未维护返回 null 并报错，**禁止**回落其它槽）。**`#resolveCatalogPrimaryUnitCode`** / **`#resolveCatalogPrimaryUnitName`**：**仅**读 **`package_unit_code`** 及其字典列（医生站目录默认开立单位=包装单位；**禁止** `defEncounterUnitCode`→`packageUnitCode` 跨槽回落或 dictText 错位）。**单位展示名（IF-DOR-10/11/12，B.10.8.1）**：**`#resolveUnitDisplayName`** / **`#resolveDoseUnitDisplayName`**——术语预热映射（`UNIT_CODE` / `DOSE_UNIT`）优先，未命中时**仅**匹配与 code 同槽的产品 DTO 字典列；**`#resolveUnitDisplayNameFromProduct`** 供纯产品侧按 code 精确匹配。**医生站目录库存列**：预减后在**拆零单位**内加总，再经 **`convertDoctorStationCatalogAvailableQuantity`** 换算为 **`resolveDoctorStationCatalogDisplayUnitCode`**（= **`#resolveCatalogPrimaryUnitCode`**，包装单位）后写入 **`availableQuantity`**；**`inventoryList[].quantity`** 仍为拆零单位供 FIFO/批号对齐（与库房小单位口径一致）；**`inventoryList[]`** 另下发 **`unitCode`/`unitName`**（拆零单位，来自 **`split_unit_code*`**）。
- **`com.zxplat.domain.module.common.assembler.product.DoctorStationAdviceInventoryUnitQuantityHelper`**：医生站 IF-DOR-11 占用预减与 IF-DOR-15 FIFO 共享——业务单位 ↔ 拆零换算（委托域 **`MedicationProductQuantityToSplitUnitConverter`**，与药房发药扣减同口径）；**`mergeMedicationOccupancyInSplitUnits` / `mergeDeviceOccupancyInSplitUnits`**（占用行须含 **`unitCode`**）；**`copyOccupancyRemaining` / `consumeLocationOccupancyForLot`**（同库位多批时按 FIFO 批次顺序**消耗一次**库位级预占，**禁止**对每一批都减整库占用，见 §3.3 规则 **D**）；**`allocateFifoSegmentsInDoctorBusinessUnit`**（拆零内部比对、分段 **`quantity` 回写为医嘱开立单位**，整包装开立禁止小数段，见 §3.3 规则 **H**）；**`tryAllocateSingleLotCoveringTotalInDoctorBusinessUnit`**（门诊皮试 **BR-ORD-034** 单批整量试分配）。
- **`DoctorStationAdviceFifoSegmentDraft`** / **`DoctorStationAdviceFifoAllocationResult`**：FIFO 分段草稿与分配结果（`locationId` / `lotNumber` / `quantity`）。
- **`com.zxplat.domain.share.constants.ProductDefinitionCatalogUnitBandConstants`**：`bandKind` 语义常量（**`BAND_PURCHASE`** 等，字符串值与 **`PURCHASE`** 等 JSON 字面量一致）。
- **`com.zxplat.domain.module.common.assembler.advice.ServiceAdviceDraftCommandsupport**：诊疗草稿 **CREATE** 用 **`requireQuantity`** / **`requireUnitCode`**（显式载荷 → `childOverridesJson` → **`packageUnitCode`**；均缺失抛 **`SERVICE_REQUEST_INVALID_ARGUMENT`**，**禁止**默认 `1` 或默认计量码）；**UPDATE** 用 **`resolveQuantityForModify`** / **`resolveUnitCodeForModify`**（未传则不覆盖存量）。子 JSON 解析见 **`AdviceExtensionJsonSupport#parseServiceChildOverrides`**。
- **`com.zxplat.domain.module.common.assembler.advice.ProductDefinitionOrderChargePricingsupport**：医嘱暂存/签发分段/护士执行新建 **`ChargeItem`** 时写入 **`quantity_value`/`quantity_unit`/`unit_price`/`total_price`**（入参：`ProductDefinitionDisplayDto` + `ChargeItemDefinition` + 业务 `quantity`/`unitCode`）；**`quantityUnit` 须来自请求**，**禁止**回落目录主单位；**`price_value` 为每包装单位价**，经 **`#resolvePackageAnchoredPricingUnitCode`** + **`#buildDoctorStationBands`** 写各槽后按业务 **`unit_code`** 取槽单价（**仅包装→目标单位**；包装单位未维护抛 **`MSG_CHARGE_PACKAGE_UNIT_CODE_REQUIRED`**；**禁止**拆零锚往回算，**DM-ADM-010 §1.6**）；**禁止**默认 1/「次」。**Mapper**：`DoctorStationAdviceMapperImpl#toCreateChargeItemDraftItemFromOrder`；**AppService**：`createChargeIfPossible`；**Support**：`OutpatientMedicationSignChargeOrchestrator#createDispenseSegmentChargeDrafts` 须传入 **`quantityUnit` + `productRow`**。
- **`com.zxplat.domain.application.shared.advice.AdviceExtensionJsonsupport**：库表 `content_json` → DTO **`requestExtensions`** / **`packageChildren`**（`parseRequestExtensions`、`toPackageChildItems` 等）；**禁止**对外下发 `contentJson` 原始串；**IF-DOR-10** 与 **IF-DOR-14** 共用。
- **`com.zxplat.domain.application.shared.advice.ServiceApplyFormOrchestrationsupport**（**IF-DOR-21/22、IF-IP-DOR-23/24 共享**）：申请单保存（删旧取新 **`AR` 号**、行/Detail 校验、整单 org/诊断透传、`createServiceRequestDraft`）与列表分组（`prescription_no` 前缀过滤、`mapDetailListModelsByServiceRequestIds`）；门诊/住院 AppService 仅 Gate + 费用回调注入。
- **`com.zxplat.domain.module.common.assembler.advice.model.*`**：`ServiceApplyFormLineSpec` / `ServiceApplyFormHeaderModel` 等 support 入参/出参模型；应用层 **`ServiceApplyFormAssembler`** 负责 HTTP DTO ↔ Spec/Model（**禁止**在 `mapper/` 落申请单 DTO 互转）。
- **`com.zxplat.domain.application.outpatient.doctorstation.advice.assembler.ServiceApplyFormAssembler`**：**`@Component`**；`toLineSpecs` / `toHeaderDtos` / `toDetailSummaryDtos`；住院 IF-IP-DOR-23/24 **复用**本类与门诊 DTO。

### 2.2 依赖注入表


| 注入类型 | 类名（FQCN） | 职责 | 来源 |
| --- | --- | --- | --- |
| 应用服务 | `DoctorStationAdviceAppService` | IF-DOR-10～20 编排入口 | 本模块 |
| Mapper | `DoctorStationAdviceMapper` + `DoctorStationAdviceMapperImpl` | Request/Command/Response 装配 | 本模块 |
| Facade | `EncounterContextReader` | 门诊就诊上下文门控（`mustGetOutpatientContextByEncounterId`） | `application.shared.encounter` |
| QueryService | `EncounterQueryService` | 薄就诊查询（主档存在性等） | `domain.administration.encounter` |
| Loader | `ProductDefinitionCatalogPageLoader` | IF-DOR-11 目录分页（`pageProductDefinition`） | `application.shared.product` |
| 域查询 | `MedicationRequestQueryService` | 药品申请列表读（`listMedicationRequestsByEncounterForDoctorStation`，按 `generate_source_enum` 过滤医生站来源） | `domain.medication.medicationrequest` |
| 域查询 | `ServiceRequestQueryService` | 诊疗申请列表；**IF-DOR-10/22**：`mapDetailListModelsByServiceRequestIds` 批量明细 + 展示名；**IF-DOR-14/15/16 皮试联动**：`findByBasedOnTableAndBasedOnId(med_medication_request, medicationRequestId)` 解析 companion 皮试检查 SR | `domain.workflow.servicerequest` |
| 域查询 | `ActivityDefinitionQueryService` | **IF-DOR-14 皮试联动**：`requireIdByExactName(GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME)`（固定名 **「皮试检查」**） | `domain.workflow.activitydefinition` |
| 域查询 | `DeviceRequestQueryService` | 耗材申请列表读（`listDeviceRequestsByEncounterForDoctorStation`，按生成来源过滤） | `domain.workflow.devicerequest` |
| 域查询 | `ChargeItemQueryService` | 费用行装载、按就诊/服务锚点检索 | `domain.administration.chargeitem` |
| Loader | `ProductDefinitionBatchLoader` | IF-DOR-10/11/12：`loadAsMap` 批量目录展示 | `application.shared.product` |
| 域查询 | `ServiceLocationTemplateQueryService` | IF-DOR-11：接诊/开立科室 + 物品表 + **目录 `categoryCode`（= 模板 `distribution_category_code`）** + 具体项 + 用法 → 适用候选库房（`listApplicableDistributionLocationIds`，calcScore≥0）；**默认库位**（`matchDefaultDistributionLocation`）；科室内全量候选（`listCandidateDistributionLocationIds`）及模板行降级（`listEffectiveTemplateRowsForDispensing`） | `domain.custom.servicelocationtemplate` |
| 域查询 | `ChargeItemDefinitionQueryService` | IF-DOR-11：`findPriceLinesByProjects` 等批量取价；**IF-DOR-14**：药耗经 **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`**（`clientResolvedDispenseLocationId` 非空时 FIFO 直接在该库位取批号，**不**二次 match 模板；诊疗仍 **`SALE`** 择优）；**批次暂存/更新**：定义主键已给出却无命中行时 **`DoctorStationAdviceAppService`** 抛 **`CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT`**（不静默跳过）；**签发分段费用**：经 **`OutpatientMedicationSignChargeOrchestrator#createDispenseSegmentChargeDrafts`**，每段按 FIFO **`lotNumber`** 调用 **`selectExecutionPriceLineForChargeDraft`** 匹配 **`LOT_NUMBER_PRICE`**（**禁止** `selectFirstPriceLineForChargeDraft` 跨批号 priority 落账） | `domain.administration.chargeitemdefinition` |
| 域查询 | `CodeSystemQueryService` | **IF-DOR-18**：术语编码系统下概念全量转 **`OptionDto`** 下拉（`rate_code` 用药频次、`method_code` 给药途径/用法）；白名单见 `TerminologyCodeSystemNameConstants` | `domain.terminology` **已落地** |
| 域查询 | `BodyStructureQueryService` | **IF-DOR-18**：身体部位**树形**下拉；**IF-DOR-22/10**：Detail `target_table=dia_body_structure` 展示名解析 | `domain.diagnosis.bodystructure` **已落地** |
| 应用装配 | `BodyStructureOptionAssembler` | **IF-DOR-18**：`getBodyStructureTree` → 仅 **ACTIVE** 的 `OptionDto` 树（含 `children`） | `module.common.assembler.option` **已落地** |
| 域查询 | `SpecimenDefinitionQueryService` | **IF-DOR-18**：样本定义下拉；**IF-DOR-22/10**：Detail `target_table=adm_specimen_definition` 展示名解析 | `domain.administration.specimendefinition` **目标态** |
| 域查询 | `ConditionDefinitionQueryService` | **IF-DOR-10**：由三类医嘱请求上 **`encounter_diagnosis_ids`** 有序 ids 串解析首位（及展示所需）`adm_encounter_diagnosis.id` → `condition_definition_id`，批量解析 **`ConditionDefinition`**，装配列表行 **`conditionDefinitionName`**（与 **`getConditionDefinitionListByIds`** 对齐） | `domain.clinical.conditiondefinition` **已落地** |
| 域查询 | `InventoryItemQueryService` | IF-DOR-11：`findAvailableCatalogRowsForOrder`（本页多物品×多候选库位可用库存批量读）；其它库存检索仍服务 IF-DOR-14 等 | `domain.workflow.inventoryitem` |
| 域查询 | `MedicationDispenseQueryService` | IF-DOR-11：`listMedicationDispensesForOrderCatalogOccupancy`（候选库位上药品发放预占批量读，**不限就诊**；`DRAFT`/`PREPARATION` 且 `now < limitTime`；占用模型含 **`unitCode`**）；**IF-DOR-10/16**：`listMedicationDispenseIdsByMedicationRequest`（按请求聚齐发放主键，列表 FIFO 库位 / 签退费用锚点） | `domain.medication.medicationdispense` |
| 域查询 | `DeviceDispenseQueryService` | IF-DOR-11：`listDeviceDispensesForOrderCatalogOccupancy`（候选库位上耗材发放预占批量读，**不限就诊**；`DRAFT`/`PREPARATION` **不过期**，直至执行或取消；与药品 `now < limitTime` 不同）；**IF-DOR-10/16**：`listDeviceDispenseIdsByDeviceRequest` | `domain.workflow.devicedispense` |
| 域写 | `MedicationRequestDomainService` | 药品医嘱草稿/签发/签退/删/组号 | `domain.medication.medicationrequest` |
| 域写 | `MedicationDispenseDomainService` | **IF-DOR-14 不**调用 `#generateDispenseFromTemporaryOrder`；**IF-DOR-15**：`#replaceFifoDraftDispensesAfterDoctorStationSign`（签发 FIFO 建发放）；**IF-DOR-14/16**：`#removeUndispensedDispense` / `#releaseDispenseReserve`（改删/签退释放遗留发放） | `domain.medication.medicationdispense` |
| 域写 | `DeviceRequestDomainService` | 耗材请求草稿/签发/签退/删 | `domain.workflow.devicerequest` |
| 域写 | `DeviceDispenseDomainService` | **IF-DOR-14 不**调用 `#generateDeviceDispenseFromTemporaryPath`；**IF-DOR-15**：`#replaceFifoDraftDispensesAfterDoctorStationSign`（签发 FIFO 建发放）；**IF-DOR-14/16**：`#removeUndispensedDeviceDispense` / `#releaseDeviceDispenseReserve` | `domain.workflow.devicedispense` |
| 域写 | `ServiceRequestDomainService` | 诊疗申请草稿/签发/签退/删 | `domain.workflow.servicerequest` |
| 域写 | `ChargeItemDomainService` | 费用草稿创建；**IF-DOR-15**：诊疗 `#planDraftChargeItemsAfterAdviceSigned`（**`service_*` 请求锚点**）；药/材：签发前 `#voidUnbilledDraftChargeItemsByServiceAnchors`（请求锚点作废）→ 分段草稿后 `#planDraftChargeItemsAfterAdviceSignedByDispenseAnchors`（**`dispense_*` 锚点** `DRAFT`→`PLANNED`）；**IF-DOR-16**：药/材 `#rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors` + 请求锚点 `#rollbackPlannedChargeItemsAfterAdviceSignedOff` → 域签退 → `#voidUnbilledDraftChargeItemsByDispenseAnchors` / `#voidUnbilledDraftChargeItemsByServiceAnchors` + 释放预占；IF-DOR-14 删行 `#voidUnbilledDraftChargeItemsByServiceAnchor` | `domain.administration.chargeitem` |
| 应用装配 | `DoctorStationAdviceListAssembler` | IF-DOR-10：三类医嘱查询结果 + 就诊 `ChargeItem` 聚合 → `DoctorStationAdviceRowDto`；**`assembleRows`** 默认 **`excludeTcmMedication=true`**（**AS `BR-ORD-026`**，剔除 `tcm_flag=true` 饮片行）；**IF-DOR-20** 走 **`assembleAdviceQueryRows(..., false)`** 保留饮片；**`OrderRequestCatalogWarmAssembler#prepareByDefinitionIds`** + **`ProductDefinitionBatchLoader#loadAsMap`** + **`TerminologyOptionLoader#buildLabelMap`** + **`OrderCatalogDisplayAssembler`** / **`ProductDefinitionCatalogUnitBandsAssembler#resolveUnitDisplayName`**（及剂量 **`#resolveDoseUnitDisplayName`**）装配 **`unitName`/`doseUnitName`**（**AS `BR-ORD-022`**）；发药/耗材发放 `service_id` 经域查询映射回申请主键后 rollup 收费态；**药/材**：按请求 id 批量取发放 id，取**发放主键升序首条**之 `location_id`，经 **`LocationQueryService#mapNamesByIds`** 写入 **`fifoDispenseLocationId` / `fifoDispenseLocationName`**（**AS `BR-ORD-019`**）；`reqOpenedTime`/`reqAuthoredTime`/`stopTime` 使用 `GeneralSharedConstants#ISO_LOCAL_DATE_TIME_FORMATTER`；**`sortNumber`** 从各请求实体映射并按 **AS `BR-ORD-018`** 与开立时刻（`reqOpenedTime`，空回落 `reqAuthoredTime`）合并排序；**药/诊/材**行在绑定时携带 **`encounterDiagnosisIds`**（各表 `encounter_diagnosis_ids` 有序 ids 串，主诊 id 第一），`conditionDefinitionName` 经首位 id 关联的 `condition_definition_id` 批量解析；**与 AS `BR-ORD-019` / 本文 §3.2 全量字段对齐的实现增量见 G-DOR-003-10** | 本模块 `assembler/` **已落地（字段补全目标态）** |
| 应用装配 | `DoctorStationAdviceCatalogAssembler` | IF-DOR-11：在 **AppService 已完成QueryService 分页** 的前提下，`#assemblePageContent` 装配**候选库位 ∩ 库存**、**候选库房全局发放占用预减**（**`DoctorStationAdviceInventoryUnitQuantityHelper#merge*OccupancyInSplitUnits`**，占用行 **`unitCode`→拆零**，与 **AS `BR-ORD-021`** 一致）；**定价分流**：**诊疗**（`wkf_activity_definition`）仅在 **`SALE`** 价型内择优 → **`unitPrice`/`chargeItemDefinitionId`**；**药品/耗材**仅在 **`LOT_NUMBER_PRICE`** 内解析——**有库存行**时按 **`inventoryList[].lotNumber`** 与 `adm_charge_item_definition.lot_no` **逐行匹配**回填 **`DoctorStationAdviceInventoryRowDto#price`/`chargeItemDefinitionId`**，汇总价与**首条库存批号**对应定价行一致，**无库存**时在 **`LOT_NUMBER_PRICE`** 内择优/兜底；**无** `priceListJson`；`#baseRow` 透传 **`ProductDefinitionDisplayDto`**；**`unitBands`** 由 **`ProductDefinitionCatalogUnitBandsAssembler#buildDoctorStationBands`** 装配（计价锚 **`#resolveDoctorStationBandPricingUnitCode`** → **包装单位**；**`unitPrice` 为每包装价**，`P_slot = P_pkg × R_slot / R_pkg`，**禁止**拆零/默认开立单位锚再往回乘，**DM-ADM-010 §1.6**）；**诊疗**五槽 DTO **仅 `PACKAGE` 有值**；**`selectFirstPriceLineForChargeDraft`** **仅**供诊疗 **`SALE`** / 药耗无库存无批号兜底（**禁止**签发 FIFO 已有批号时跨批号落账）；**IF-DOR-35**（中医饮片）**复用**本装配器与 **`DoctorStationAdviceCatalogRowDto`** | 本模块 `assembler/` **已落地**（与 v1.7 SDD 中「多步骤方法链」叙述不等价，见 **§2.6**） |
| 共享编排 | `OutpatientMedicationSignChargeOrchestrator` | **IF-DOR-15 药品** / **IF-DOR-37 中药**：`#replaceFifoDispenseAndPlanChargesAfterMedicationSigned`（void 请求锚点暂存费 → FIFO（皮试 **BR-ORD-034** 单批优先） → **BR-ORD-035** 跨批金额确认 → **按分段 `lotNumber`** 建草稿 → PLANNED）；**IF-DOR-15 耗材**：`#assertCrossLotPricingConfirmedOrThrow` + `#createDispenseSegmentChargeDrafts`（须传 `segmentLotNumbers`，单价走 `selectExecutionPriceLineForChargeDraft`）；**BR-ORD-033**：写 ChargeItem 前 `#assertPersistedDispenseLotsMatchFifoSegments` 核对发放行落库批号与分段一致 | `outpatient.doctorstation.advice.support` **已落地** |
| 应用装配 | `DoctorStationAdviceFifoAllocationAssembler` | **IF-DOR-15**：按就诊组织、`InventoryItemQueryService#findAvailableCatalogRowsForOrder` 与模板候选库位求交，**模板库位序 → 效期 → 稳定次序**；扣减**候选库房全局目录占用**（`DRAFT`/`PREPARATION`）并**额外叠加**住院 `SUMMARIZED`（`listMedicationDispensesForInpatientSummarizedOccupancy`，目录「还可开」不读本段）；调用 **`DoctorStationAdviceInventoryUnitQuantityHelper#allocateFifoSegmentsInDoctorBusinessUnit`**（**§3.3 规则 H** / **AS `BR-ORD-021`**）；皮试另 **`#tryAllocateSingleLotCoveringTotalInDoctorBusinessUnit`**（**BR-ORD-034**）；**`#resolvePrimaryMedicationDispenseLocationId`** 供 **BR-ORD-023** 分方 FIFO 首段库房；输出 **`ReplaceFifo*DispenseDraftsCommand.Segment`** 供域重写发放 | 本模块 `assembler/` **已落地** |
| 应用装配 | `OutpatientPrescriptionSplitAssembler` | **IF-DOR-15 / BR-ORD-023**：签发前加载参与集 = **本批待签西药/中成药 DRAFT** ∪ **已 ACTIVE 未 SETTLED 的非中药**（**禁止**其它未签发草稿入方；**禁止** `tcmFlag=true` 中药饮片入方——中药由 IF-DOR-37 取号），装配 `PrescriptionSplitLineContext`（含 **`encounterDiagnosisIds` 有序 ids 串** → **`diagnosisSetKey`（规范化，装箱按有交集同方）** + 首位 id 解析 **`med_type`**、**`pharmacologyCategoryCode`（药品性质）**、**`medicationCategoryCode`（西药/中成药分轨）**、金额装箱用费用合计、FIFO 主库房），调用 **`MedicationRequestDomainService#splitPrescription`** | 本模块 `assembler/` **已落地** |
| 应用装配 | `ServiceApplyFormAssembler` | **IF-DOR-21/22**：HTTP DTO ↔ `ServiceApplyFormOrchestrationsupport Spec/Model；列表/外发明细摘要 **`toDetailSummaryDtos`** 供 **IF-DOR-10** 申请单行与 **LisPacsApplyGateway** 复用 | 本模块 `assembler/` **已落地** |
| 共享编排 | `ServiceApplyFormOrchestrationsupport | **IF-DOR-21/22**（住院 **IF-IP-DOR-23/24** 同构）：`saveApplyForm` / `listApplyForms`；`isRequisitionApplicationPrescriptionNo`；`toDetailSummaryModels` | `application.shared.advice` **已落地** |
| 设置读取 | `OutpatientPrescriptionAmountLimitSettingsupport | 读 **`SettingDefinitionEnum.OUTPATIENT_PRESCRIPTION_AMOUNT_LIMIT_YUAN`**（默认 500；99999 关闭金额拆方） | `module.common.validator` **已落地** |
| 共享常量 | `PrescriptionSplitConstants` | 分方轨道、221 药品性质码、355 处方类别码、`MAX_WESTERN_PATENT_MEDICATION_KINDS_PER_PRESCRIPTION=5` | `domain.share.constants` **已落地** |
| 应用查询 | `DoctorStationAdviceOrderBindQueryService` | IF-DOR-12：`ServiceDeviceTemplate` + 批量展示名；**IF-DOR-23**：按暂存变更行解析直接绑定（含触发源与 `based_on` 锚点）；**`unitName`** 经 **`ProductDefinitionCatalogUnitBandsAssembler#resolveUnitDisplayName`**（术语 + 目标 **`ProductDefinitionDisplayDto`**，**BR-ORD-022**） | 本模块 **已落地** |
| 外发端口 | `LisPacsApplyGateway` + `NoopLisPacsApplyGateway` | IF-DOR-15：检验/检查外发占位；`LisPacsAdviceApplyCommand` 为入参载体（**须含** `serviceRequestDetails[]`：`targetTable`/`targetId`/展示名，**BR-REQ-008**） | 本模块 **已落地（占位网关）** |
| 应用共享 | `EncounterChargeAccountOpener` | IF-DOR-14/**IF-DOR-15**：按行费用性质合同匹配就诊账户 `contract_no`→`account_id`；无合同双账户默认医保户、单户个人现金；选自费合同回落优先个人现金。门禁：就诊须至少有一可用挂账账户 | `application.shared.financial` **已落地** |
| 域查询 | `AccountQueryService` | 双账户探测（`hasDualChargeAccounts`）、医保/个人现金账户实体读；挂账解析编排见上 `EncounterChargeAccountOpener` | `domain.administration.account` **已落地** |
| 域查询 | `LocationQueryService` | **IF-DOR-10**：`mapNamesByIds` 解析 **`fifoDispenseLocationId`** 对应库位展示名；**IF-DOR-19**：规则行上执行科室组织 id、执行地点 id 批量解析展示名 | `domain.administration.location` **已落地** |
| 域查询 | `ActivityExecutionLocationQueryService` | **IF-DOR-19**：`listExecutionRulesForActivityResolution(activityDefinitionId, activityCategoryCode, activityTypeCode)` 返回单品 + 匹配类型层 + 大类默认可执行科室规则（与 WH-EXE001 配置同源） | `domain.custom.activityexecutionlocation` **已落地** |
| 基础设施 | `com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService` | IF-DOR-15：**处方号**由 **`splitPrescription`** → `getSeqByDay(COMPOSITE_PRESCRIPTION)`（**`CP`**）；**IF-DOR-21**：申请单号 **`getSeqByDay(REQUISITION_APPLICATION)`**（**`AR`**，`AggregateTypeConstants#REQUISITION_APPLICATION`）；**`bus_no`** 仍为 **`MR`**（**禁止**混用前缀） | zxplat-framework **已落地** |
| 应用常量 | `DoctorStationAdviceConstants` | 行操作 `CREATE`/`UPDATE`/`DELETE`、成功应答文案、术语码表名等（**生成来源**已收口 **`GenerateSource` 枚举**） | 本模块 **已落地** |
| 共享常量 | `com.zxplat.domain.share.constants.ProductDefinitionCatalogUnitBandConstants` | IF-DOR-11 / IF-WHS-IN-11：`unitBands[].bandKind` 语义（**`BAND_PURCHASE`** / **`BAND_SPLIT`** / **`BAND_PACKAGE`** / **`BAND_STOCK`** / **`BAND_DOSE`**；**`BAND_SALES` 已废弃**） | domain-shared **已落地** |
| 共享常量 | `com.zxplat.domain.share.constants.GeneralSharedConstants` | IF-DOR-10 列表 `reqOpenedTime`/`reqAuthoredTime`/`stopTime` 使用 **`ISO_LOCAL_DATE_TIME_FORMATTER`**（ISO-8601 local）；IF-DOR-11 库存/预占内存键分隔符等同接口内 `PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR` | domain-shared **已落地** |
| 共享常量 | `com.zxplat.domain.share.constants.FieldNameConstants#SORT_NUMBER` | 药品草稿编辑事件变更摘要中与实体属性 `sortNumber` 对齐（**B.10.10**） | domain-shared **已落地** |
| 共享常量 | `FieldNameConstants`：`DOSE`、`DISPENSE_PER_DURATION`、`GROUP_ID`、`ENCOUNTER_DIAGNOSIS_IDS`、`SKIN_TEST_FLAG`、`DOSE_UNIT_CODE` | 药品/诊疗/耗材草稿 **编辑**事件变更摘要与实体属性对齐（**IF-DOR-14** 扩展字段；**B.10.10**） | domain-shared **已落地** |
| 操作人 | `CurrentOperatorResolver` | 写路径 `operatorId`；列表 `onlyMine` 过滤（**B.6.5**） | `module.common.validator` **已落地** |
| 共享常量 | `com.zxplat.domain.share.constants.ChargeItemDmConstants` | 收费上下文 `CONTEXT_*`、默认数量单位等（**生成来源**已收口 **`GenerateSource` 枚举**） | domain-shared |
| 共享常量 | `com.zxplat.domain.share.constants.TableNameConstants` | 目录项表名、库存匹配 `itemTable` | domain-shared |
| 共享常量 | `com.zxplat.domain.share.constants.DoctorStationOrderCatalogConstants` | IF-DOR-11：目录预减时纳入占用的发放状态集合（`DispenseStatus` 列表，药/耗查询共用） | domain-shared |


### 2.3 枚举、字典与值集（DM 与代码可追溯）


| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 就诊账户类型（落库） | `com.zxplat.domain.share.enums.AccountTypeCode`（`getCode()` → `adm_account.type_code`） | **`DM-ADM-009`** §4.3 / §6.2；挂号默认户见 **`AS-OP-FIN-001`** IF-REG-07 | IF-DOR-14 费用草稿账户锚点为 **`PERSONAL_CASH_ACCOUNT`**；**`REG`** 非门诊 `adm_account` 主路径 |
| 药品/耗材/诊疗请求状态 | `com.zxplat.domain.share.enums.RequestStatus` | `DM-MED-003` §5 状态机；`DM-WKF-003`/`DM-WKF-004` 请求状态 | IF-DOR-10/14/15/16 列表与写路径门控 |
| 收费明细状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §5 / §3 `status_enum` | `DRAFT` 草稿、`PLANNED` 对应签发后待收费、`SETTLED` 已收费门控 |
| 医嘱生成来源（药/材/诊疗） | `GenerateSource`（`com.zxplat.domain.share.enums`） | `DM-MED-003` §4.5；`DM-WKF-003` §4.2；`DM-WKF-004` §4.6 | 门诊医生站暂存/列表/费用草稿：**`GenerateSource.DOCTOR_PRESCRIPTION.getCode()`** → `doctor-prescription`；写入 `MedicationRequest`/`DeviceRequest`/`ServiceRequest.generate_source_enum` |
| 耗材生成来源 | `DeviceRequest.generateSourceEnum`（实体字段） | 同上 | IF-DOR-10 与药品**同一** `doctor-prescription` 过滤（**药/材/诊疗对称**；**AS `BR-ORD-010`**） |
| 医生站列表展示排序号 | `MedicationRequest.sortNumber`；`ServiceRequest.sortNumber`；`DeviceRequest.sortNumber`（JPA 列 `sort_number`） | `DM-MED-003` §3 药品申请持久化列（既有）；`DM-WKF-004` / `DM-WKF-003` 诊疗与耗材请求表经 **`zxplat/.../db/migration/health/V35__Add_sort_number_to_service_and_device_request.sql`** 增补同名列 | **AS `BR-ORD-018`**：IF-DOR-14 批次行可选写入；IF-DOR-10 列表 DTO **`sortNumber`** 回显；**UPDATE** 时命令字段非空方更新（避免误清空） |
| 收费生成来源 | `GenerateSource.DOCTOR_PRESCRIPTION.getCode()` | `DM-ADM-011` §4.2 | 费用草稿 `createDraftChargeItemFromOrder` / `ChargeItemDomainService#fillGenerateAndContext` 医嘱草稿路径 |
| 目录表名筛选（应用请求） | `PageDoctorStationAdviceCatalogRequest.productTableNames` | AS IF-DOR-11；`DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` §2 | 可选；**空或 null** 表示三表全量；**非空**时每个元素须为QueryService约定表名之一（`med_medication_definition` / `adm_device_definition` / `wkf_activity_definition`），用于收窄本次分页的 `product_table_name` 范围；与行锚点 **`product_definition_id` + `product_table_name`** 一致，不再使用 `MED`/`SRV`/`DEV` 间接编码 |
| 划价标记 | `PageDoctorStationAdviceCatalogRequest.pricingFlag` | AS IF-DOR-11 | `pricingFlag=1` 时**不返回药品目录行**（实现上等价于药品分支查询条件恒假）；诊疗行按 `pricing_flag` 与请求值等值过滤 |
| 统一目录查价 | `ClinicalCatalogPriceResolver | AS **BR-ORD-001** | 诊疗 **SALE**；药耗 **FIFO 批号→LOT_NUMBER_PRICE** |
| 诊疗活动类型展示 | `DoctorStationAdviceCatalogRowDto.activityTypeCode` | `DM-WKF-001` §3；QueryService `ProductDefinitionDisplayDto.activityType` | IF-DOR-11 诊疗行 |
| 发布/启用 | `ProductDefinitionPageQuery.activeFlag` + QueryService `active_flag` | `DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` §2；目录仅已启用/可售口径 | IF-DOR-11 默认仅启用/已发布 |
| 发放预占状态（目录） | `DoctorStationOrderCatalogConstants#DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG`（仅 `DRAFT`/`PREPARATION`） | `DM-MED-002` | IF-DOR-11「还可开」预减 |
| 发放预占状态（FIFO 额外） | `#DISPENSE_OCCUPANCY_STATUSES_FOR_FIFO_INPATIENT_SUMMARY_EXTRA`（仅 `SUMMARIZED`；住院 `INPATIENT`） | `DM-MED-002` §6.2.16a | IF-DOR-15 选批叠加，不进目录 |
| 批次定价条件码 | `ChargeItemDefinitionQueryCommand` / 仓储查询条件 | `DM-ADM-010` 定价明细条件维度；批次价使用约定条件码 **`LOT_NUMBER_PRICE`**（与收费项定义子表 `condition_code` 一致） | IF-DOR-11 批次售价 |
| 库存业务类型（预留） | `com.zxplat.domain.share.enums.InventoryLedgerBusinessType` | `DM-WKF-Inventory`（若独立 DM）或 `DM-MED-002` | IF-DOR-11/14 库存占用口径 |
| 失败码映射 | `ErrorCodeConstants` 各枚举项 | AS §5 `EX-ORD-*` | 见 §1.2.1 |
| IF-DOR-10 列表行处方类别 | `DoctorStationAdviceRowDto.prescriptionCategoryCode` | `DM-MED-003` §3 `rx_type_code` | 药品行 |
| IF-DOR-10 列表行用药说明编码 | `DoctorStationAdviceRowDto.dosageInstructionCode` | `DM-MED-003` §3 `dosage_instruction` | 药品行 |
| IF-DOR-10 列表行滴速编码 | `DoctorStationAdviceRowDto.speedCode` | `DM-MED-003` §3 `speed_code` | 药品行 |
| IF-DOR-10 列表行请求扩展 | `DoctorStationAdviceRowDto.requestExtensions` | `DM-MED-003` §3 `content_json` 等经 **`AdviceExtensionJsonSupport#parseRequestExtensions`**；**禁止**下发原始 JSON 串 | 三资源行 |
| IF-DOR-10 皮试结果 | `DoctorStationAdviceRowDto.skinTestOutcomeCode` | `DM-CLN-001-AllergyIntolerance-过敏与不耐受.md` §3 `clinical_status_enum`（与皮试衍生 **`ServiceRequest`** 联接口径以实现为准） | 药品行 |
| IF-DOR-10 诊疗嘱托与子项 | `DoctorStationAdviceRowDto.entrustContent`；`DoctorStationAdviceRowDto.packageChildren` | `DM-WKF-004` §3 嘱托列；套餐子项经 **`#toPackageChildItems`**（**禁止** `activityChildrenJson`） | 诊疗行 |


### 2.4 领域方法白名单（聚合/方法 → IF）


| 域服务#方法（Command 形态） | 用于 IF |
| --- | --- |
| `MedicationRequestDomainService#createMedicationOrderDraft(CreateMedicationOrderDraftCommand)` | IF-DOR-14 |
| `MedicationRequestDomainService#changeMedicationOrderDraft(ChangeMedicationOrderDraftCommand)` | IF-DOR-14 |
| `MedicationRequestDomainService#removeMedicationOrderDraft(RemoveMedicationOrderDraftCommand)` | IF-DOR-14 |
| `MedicationRequestDomainService#signMedicationOrder(SignMedicationOrderCommand)` | IF-DOR-15 |
| `MedicationRequestDomainService#signOffMedicationOrder(SignOffMedicationOrderCommand)` | IF-DOR-16 |
| `MedicationRequestDomainService#adjustMedicationOrderGroup(AdjustMedicationOrderGroupCommand)` | IF-DOR-17 |
| `MedicationDispenseDomainService#replaceFifoDraftDispensesAfterDoctorStationSign(ReplaceFifoMedicationDispenseDraftsCommand)` | IF-DOR-15（药品签发后 FIFO 建立/重写草稿发放；与 **BR-ORD-020** / **DM-MED-003 BR-MRQ-028** 一致） |
| `MedicationDispenseDomainService#removeUndispensedDispense(RemoveUndispensedDispenseCommand)` | IF-DOR-14（改/删前释放**遗留**发放，非暂存新建） |
| `MedicationDispenseDomainService#releaseDispenseReserve(ReleaseDispenseReserveCommand)` | IF-DOR-16（药品签退释放预占） |
| `DeviceRequestDomainService#createDeviceOrderDraft(CreateDeviceOrderDraftCommand)` | IF-DOR-14；**绑定派生**透传 **`basedOnTable`/`basedOnId`/`packageId`**（**AS `BR-ORD-031`**） |
| `DeviceRequestDomainService#changeDeviceOrderDraft(ChangeDeviceOrderDraftCommand)` | IF-DOR-14 |
| `DeviceRequestDomainService#removeDeviceOrderDraft(RemoveDeviceOrderDraftCommand)` | IF-DOR-14 |
| `DeviceRequestDomainService#signDeviceOrder(SignDeviceOrderCommand)` | IF-DOR-15 |
| `DeviceRequestDomainService#signOffDeviceOrder(SignOffDeviceOrderCommand)` | IF-DOR-16 |
| `DeviceDispenseDomainService#replaceFifoDraftDispensesAfterDoctorStationSign(ReplaceFifoDeviceDispenseDraftsCommand)` | IF-DOR-15（耗材签发后 FIFO 建立/重写草稿发放；**IF-DOR-14 不**调用 `#generateDeviceDispenseFromTemporaryPath`） |
| `DeviceDispenseDomainService#releaseDeviceDispenseReserve(ReleaseDeviceDispenseReserveCommand)` | IF-DOR-16（耗材签退释放预占） |
| `DeviceDispenseDomainService#removeUndispensedDeviceDispense(RemoveUndispensedDeviceDispenseCommand)` | IF-DOR-14 |
| `ServiceRequestDomainService#createServiceRequestDraft(CreateServiceRequestDraftCommand)` | IF-DOR-14；**皮试联动**与**绑定派生**须透传 **`basedOnTable`/`basedOnId`**（见 **§3.6.1** / **AS `BR-ORD-031`**） |
| `ServiceRequestDomainService#modifyServiceRequestDraft(ModifyServiceRequestDraftCommand)` | IF-DOR-14 |
| `ServiceRequestDomainService#removeServiceRequestDraft(RemoveServiceRequestDraftCommand)` | IF-DOR-14 |
| `ServiceRequestDomainService#signServiceRequest(SignServiceRequestCommand)` | IF-DOR-15 |
| `ServiceRequestDomainService#withdrawSignedServiceRequest(WithdrawSignedServiceRequestCommand)` | IF-DOR-16（**每单条调用**，见 §3.9） |
| `ChargeItemDomainService#createDraftChargeItemFromOrder(CreateChargeItemCommand)` | IF-DOR-14 |
| `ChargeItemDomainService#planDraftChargeItemsAfterAdviceSigned(Long encounterId, String serviceTable, Long serviceId)` | IF-DOR-15 · **诊疗路径**：签发成功后按**请求表锚点**将 `DRAFT`→`PLANNED` |
| `ChargeItemDomainService#planDraftChargeItemsAfterAdviceSignedForAnchors(Long encounterId, String serviceTable, List<Long> serviceIds)` | IF-DOR-15 · **药品/耗材路径**：按**发放表主键列表**批量 `DRAFT`→`PLANNED`（内部委托 `#activateChargeItemAfterExecution`） |
| `ChargeItemDomainService#activateChargeItemAfterExecution(BatchChargeItemIdsCommand)` | 执行域/批量 ID 路径；医生站药耗签发升位由 **`planDraftChargeItemsAfterAdviceSignedForAnchors`** 聚合调用 |
| `ChargeItemDomainService#rollbackPlannedChargeItemsAfterAdviceSignedOff(Long encounterId, String serviceTable, Long serviceId)` | IF-DOR-16 · **诊疗/请求锚点**：`PLANNED`→`DRAFT` |
| `ChargeItemDomainService#rollbackPlannedChargeItemsAfterAdviceSignedOffForAnchors(Long encounterId, String serviceTable, List<Long> serviceIds)` | IF-DOR-16 · **发放锚点批量**：药耗签退先发回收 **`PLANNED`** |
| `ChargeItemDomainService#cancelExecutionRollbackChargeItem(BatchChargeItemIdsCommand)` | 执行回滚等批量 ID 路径；**非**医生站签退主路径（签退回退见上条） |
| `ChargeItemDomainService#voidUnbilledDraftChargeItemsByServiceAnchor(VoidUnbilledDraftChargeItemsByServiceAnchorCommand)` | IF-DOR-14（更新/删除行前作废未计费草稿费用；**已**由 `DoctorStationAdviceAppService` 调用） |
| `ChargeItemDomainService#voidUnbilledDraftChargeItemsByServiceAnchors(Long encounterId, String serviceTable, List<Long> serviceIds)` | IF-DOR-15/**IF-DOR-16**：按锚点列表批量作废未计费草稿（签发前置清空**请求锚点**费；签退清理发放/请求锚点费） |


**查询白名单**


| 查询服务#方法 | 用于 IF |
| --- | --- |
| `EncounterContextReader#mustGetOutpatientContextByEncounterId` | IF-DOR-10/14/15/16/17/18/19/20 |
| `MedicationRequestQueryService#listMedicationRequestsByEncounterForDoctorStation` | IF-DOR-10/20 |
| `MedicationRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` | IF-DOR-20（历史 Tab） |
| `ServiceRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` | IF-DOR-20（历史 Tab） |
| `DeviceRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` | IF-DOR-20（历史 Tab） |
| `MedicationRequestQueryService#loadFifoSignContextOrThrow(Long medReqId)` | IF-DOR-15（药品签发 FIFO 上下文） |
| `ServiceRequestQueryService#listServiceRequestsByGenerateSource` | IF-DOR-10/20 |
| `DeviceRequestQueryService#listDeviceRequestsByEncounterForDoctorStation` | IF-DOR-10/20 |
| `DeviceRequestQueryService#loadFifoSignContextOrThrow(Long deviceReqId)` | IF-DOR-15（耗材签发 FIFO 上下文） |
| `ChargeItemQueryService#listChargeItemsByEncounterId` | IF-DOR-10/14/15/16 |
| `ChargeItemQueryService#listChargeItemsByServiceIds` | IF-DOR-15/16 |
| `ProductDefinitionCatalogPageLoader#pageProductDefinition(ProductDefinitionPageQuery)` | IF-DOR-11 |
| `MedicationDefinitionQueryService#pageForOrder(ProductDefinitionPageQuery)` | IF-DOR-11（薄封装，与上同语义；药分支可显式调用） |
| `ChargeItemDefinitionQueryService#findSaleItemByInstanceId(ChargeItemDefinitionQueryCommand)` | IF-DOR-11（主表/零售价侧定价行） |
| `ChargeItemDefinitionQueryService#findSalePriceByInstanceIdAndLot(ChargeItemDefinitionQueryCommand)` | IF-DOR-11（按批号的条件价行；条件码 `LOT_NUMBER_PRICE`） |
| `ChargeItemDefinitionQueryService#findPriceLinesByProjects(ChargeItemDefinitionQueryCommand)` | IF-DOR-11（当前页批量取价） |
| `InventoryItemQueryService#findAvailableInventories` | IF-DOR-14（IF-DOR-11 目录经 **`#findAvailableCatalogRowsForOrder`**，见装配器实现） |
| `DoctorStationAdviceOrderBindQueryService#listBindingsBySourceItemKey` | IF-DOR-12 |
| `DoctorStationAdviceOrderBindQueryService#listDraftBindItems` | IF-DOR-23 |
| `ConditionDefinitionQueryService#getConditionDefinitionListByIds` | IF-DOR-10（**药/诊/材**列表行 `conditionDefinitionName` 批量解析：`encounter_diagnosis_ids` 有序 ids 串 → 首位 `adm_encounter_diagnosis.condition_definition_id` 并集） |
| `MedicationDispenseQueryService#listMedicationDispenseIdsByMedicationRequest(Long medReqId)` | IF-DOR-10（首选库位解析）/ IF-DOR-16（签退费用锚点） |
| `DeviceDispenseQueryService#listDeviceDispenseIdsByDeviceRequest(Long deviceReqId)` | IF-DOR-10 / IF-DOR-16 |
| `LocationQueryService#mapNamesByIds(Collection<Long> locationIds)` | IF-DOR-10（`fifoDispenseLocationName`） |
| `ActivityExecutionLocationQueryService#listExecutionRulesForActivityResolution(Long activityDefinitionId, String activityCategoryCode, String activityTypeCode)` | IF-DOR-19 |
| `ProductDefinitionBatchLoader#loadAsMap`（解析诊疗大类 `categoryCode`） | IF-DOR-19 |


### 2.5 Mapper、装配器与外发端口白名单（v2.2 与仓库一致）

> **说明**：v1.7 及以前本表所列 `DoctorStationAdviceCatalogAssembler#resolvePricingSourceOrThrow` 等多步骤方法链为**目标态叙述**；**首版实现**将分页与QueryService调用收拢在 `DoctorStationAdviceAppService`，目录装配以 `#assemblePageContent` 为对外入口，详见 **§2.6**。**§3.3 IF-DOR-11** 编排序列已在 **v2.1** 与 `assemblePageContent` 真源对齐；若其它 IF 小节仍残留旧方法名，以本表与 `DoctorStationAdviceMapper.java` 为准续修。

| 类型#方法 | 用于 IF |
| --- | --- |
| `DoctorStationAdviceMapper#validatePatientEncounter` | IF-DOR-10/11/14/15/16/17/18/20（请求 `patientId` 与门诊QueryService `patientId` 一致性） |
| `DoctorStationAdviceMapper#toProductDefinitionPageQuery` | IF-DOR-11 |
| `DoctorStationAdviceMapper#resolveOrderBindSourceItemKey` | IF-DOR-12 |
| `DoctorStationAdviceOrderBindQueryService#listBindingsBySourceItemKey` | IF-DOR-12 |
| `DoctorStationAdviceOrderBindQueryService#listDraftBindItems` | IF-DOR-23 |
| `DoctorStationAdviceMapper` 各 `to*Command` / `toVoidDraftChargeAnchor` / `toCreateChargeItemDraftFromOrder` / `toCreateChargeItemDraftItemFromOrder` / `toCreateChargeItemDraftItemForFifoDispenseSegment`（含 **`quantity`/`quantityUnit`/`productRow`** → 经 **`ProductDefinitionOrderChargePricingSupport#applyOrderChargePricing`** 写金额；**`productDefinitionTable` + `productDefinitionId` → `product_*`**） | IF-DOR-14/15/16/17（完整签名见接口源文件） |
| `DoctorStationAdviceListAssembler#assembleRows` | IF-DOR-10 |
| `DoctorStationAdviceListAssembler#assembleAdviceQueryRows` | IF-DOR-20（当前 Tab） |
| `DoctorStationAdviceListAssembler#assemblePatientHistoryAdviceRows` | IF-DOR-20（历史 Tab） |
| `PagedResultBuilder#buildFromFullList` | IF-DOR-20（内存分页） |
| `DoctorStationAdviceCatalogAssembler#assemblePageContent` | IF-DOR-11（入参含已分页的 `Page<ProductDefinitionDisplayDto>`，**不在**装配器内二次分页） |
| `DoctorStationAdviceFifoAllocationAssembler#allocateMedicationFifo` / `#allocateDeviceFifo` | IF-DOR-15（药品可带皮试单批优先 / `confirmCrossLotSkinTest`，**BR-ORD-034**；耗材仍普通 FIFO；**规则 H**） |
| `DoctorStationAdviceInventoryUnitQuantityHelper#tryAllocateSingleLotCoveringTotalInDoctorBusinessUnit` | IF-DOR-15 / **BR-ORD-034**（皮试单批试分配） |
| `DoctorStationAdviceInventoryUnitQuantityHelper#allocateFifoSegmentsInDoctorBusinessUnit` | IF-DOR-15 / IF-DOR-11 占用汇总（拆零口径 FIFO 真源） |
| `OutpatientMedicationSignChargeOrchestrator#replaceFifoDispenseAndPlanChargesAfterMedicationSigned` | IF-DOR-15 药品 / IF-DOR-37 中药（void 暂存费 → FIFO → 分段草稿 → PLANNED） |
| `OutpatientMedicationSignChargeOrchestrator#createDispenseSegmentChargeDrafts` | IF-DOR-15 药/材（按发放段批量创建 **`ChargeItem` 草稿**，锚 `med_medication_dispense` / `wkf_device_dispense`） |
| `ProductDefinitionCatalogUnitBandConstants#BAND_*` | IF-DOR-11（`unitBands[].bandKind` 五类单位槽语义；与 IF-WHS-IN-11 进货选品子集同源） |
| `DoctorStationAdviceOrderBindQueryService#listBindingsBySourceItemKey` | IF-DOR-12 |
| `DoctorStationAdviceOrderBindQueryService#listDraftBindItems` | IF-DOR-23 |
| `LisPacsApplyGateway#submitIfLabOrImaging`（默认实现 `NoopLisPacsApplyGateway`） | IF-DOR-15；入参 `LisPacsAdviceApplyCommand` |
| `CurrentOperatorResolver#requireOperatorId` / `#findOperatorIdOrNull` | IF-DOR-10/14/15/16/17 |


### 2.6 实现对齐（首版代码真源，与 v1.7 文档差异一次收口）

| 主题 | 文档原述（v1.7） | 仓库实现（真源） |
| --- | --- | --- |
| 包路径 | `DoctorStationAdviceCatalogAssembler` 挂在 `query/` | 装配器统一在 **`advice/assembler/`**（`List` / `Catalog`） |
| 列表响应装配 | `Mapper#toListDoctorStationAdviceResponse` | **无**：`AppService` 直接 new `ListDoctorStationAdviceResponse` 并 `setRows` |
| 目录分页 | `CatalogAssembler` 内调 `pageProductDefinition` | **`AppService`** 调 `ProductDefinitionCatalogPageLoader#pageProductDefinition`，再交 `CatalogAssembler#assemblePageContent` |
| IF-DOR-13（废止）与费用性质下拉 | 医嘱模块内 `ContractResolver` / `getEncounterAdviceDefaultContract` | **已移除**：全量 **`feeNatureOptions`** 由 **就诊管理** `DoctorStationEncounterManageAppService#pageOutpatientEncounterContextsForDoctorStation`（**AS-OP-DOR-001 · IF-DOR-01**）装配；领域侧 `ContractQueryService#listAllContractsByOrgId` 等见 **AS-OP-DOR-001-SDD** |
| IF-DOR-14 删改费用 | `voidUnbilledDraftChargeItemsByServiceAnchor` 标「目标态待生成」 | **已调用**（更新/删除行前作废未计费草稿费用） |
| 药/材列表过滤 | 「目标态」仓储过滤 | **`listMedicationRequestsByEncounterForDoctorStation`** / **`listDeviceRequestsByEncounterForDoctorStation`** 已提供 |
| 时间字符串 | 「ISO-8601」泛述 | `DoctorStationAdviceRowDto.reqOpenedTime`/`reqAuthoredTime`/`stopTime` 使用 **`GeneralSharedConstants.ISO_LOCAL_DATE_TIME_FORMATTER`**（与 `DATE_TIME_FORMATTER` 区分见该常量 JavaDoc） |
| 资源/行操作字面量 | 散落于实现 | 收口 **`DoctorStationAdviceConstants`**（`RESOURCE_TYPE_*`、`LINE_OP_*` 等） |
| IF-DOR-11 目录行 | `priceListJson` / `defaultLotNo` / `adviceType`、平铺五类单位列 | **`unitBands` + `unitPrice`**；类型锚点仅 **`productTableName`**；批号仅在 **`inventoryList`**；定价分流 **诊疗 `SALE` / 药耗 `LOT_NUMBER_PRICE`+行级 `price`**（见 §3.3 规则 **F**） |
| 事务 | 目标态：写方法声明 `@Transactional` | **IF-DOR-14/15 已声明**；**IF-DOR-16/17 待补**（见 **§1.2**）；域层单 Cmd 自洽 |
| IF-DOR-15/16 与门诊预结算 | 曾表述为仅 `#planDraftChargeItemsAfterAdviceSigned` / `#rollbackPlannedChargeItemsAfterAdviceSignedOff`（请求锚点） | **药耗**：签发先 **`voidUnbilledDraftChargeItemsByServiceAnchors`（请求）**→ FIFO → **`replaceFifo*`** → **`OutpatientMedicationSignChargeOrchestrator#createDispenseSegmentChargeDrafts`** → **`planDraftChargeItemsAfterAdviceSignedByDispenseAnchors`（发放）**（药品整段收拢为 **`#replaceFifoDispenseAndPlanChargesAfterMedicationSigned`**）；签退先 **`rollbackPlannedChargeItemsAfterAdviceSignedOffByDispenseAnchors`（发放）** 再请求锚点回退，再域签退并 **`voidUnbilledDraftChargeItemsByDispenseAnchors`/`voidUnbilledDraftChargeItemsByServiceAnchors`** + **`release*Reserve`**。**诊疗**仍单锚点 `plan`/`rollback`。详见 **§3.7 / §3.8** |
| IF-DOR-15 中西医隔离 | 未写 Gate | **BR-TCM-ISO-001**：`signDoctorStationAdviceBatch` 药品分支签发前 **`tcm_flag=true` → 拒绝**（提示转中医 Tab） |
| IF-DOR-10 列表残余栏位 | §3.2 全量字段均已装配 | **`prescriptionCategoryCode`/`Name`、`signedById`**：DTO 有字段、ListAssembler **未赋值**（可 `null`） |
| IF-DOR-14 暂存库存 | BR-ORD-003 暂存前库存校验 | **首版** `saveDoctorStationAdviceDraftBatch` **未**调用库存汇总校验；IF-DOR-11 预减 + IF-DOR-15 FIFO 不足拒绝已落地 |
| IF-DOR-14 皮试联动 | **BR-ORD-025** 暂存/签发/签退联动皮试检查 SR | **已落地**：`DoctorStationSkinTestSyncOrchestrator` + `DoctorStationAdviceAppService` IF-DOR-14/15/16 接线；`CreateServiceRequestDraftItemCommand` 已支持 **`basedOnTable`/`basedOnId`** |


---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）


| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-DOR-10 | `listDoctorStationAdvice` | 加载当前就诊医嘱统一列表 | `POST /api/app/doctorStationAdvice/listDoctorStationAdvice` |
| IF-DOR-11 | `pageDoctorStationAdviceCatalog` | 分页检索可开立医嘱项目目录 | `POST /api/app/doctorStationAdvice/pageDoctorStationAdviceCatalog` |
| IF-DOR-12 | `getDoctorStationAdviceOrderBindInfo` | 查询医嘱绑定信息 | `POST /api/app/doctorStationAdvice/getDoctorStationAdviceOrderBindInfo` |
| IF-DOR-23 | `resolveDoctorStationAdviceDraftBinds` | 暂存前绑定解析（纯读） | `POST /api/app/doctorStationAdvice/resolveDoctorStationAdviceDraftBinds` |
| ~~IF-DOR-13~~ | ~~`getEncounterAdviceDefaultContract`~~ | **废止**（费用性质下拉并入 **IF-DOR-01** `feeNatureOptions`，见 **§3.5**） | — |
| IF-DOR-14 | `saveDoctorStationAdviceDraftBatch` | 暂存医嘱变更批次 | `POST /api/app/doctorStationAdvice/saveDoctorStationAdviceDraftBatch` |
| IF-DOR-15 | `signDoctorStationAdviceBatch` | 签发医嘱批次 | `POST /api/app/doctorStationAdvice/signDoctorStationAdviceBatch` |
| IF-DOR-16 | `withdrawDoctorStationAdviceBatch` | 签退医嘱批次 | `POST /api/app/doctorStationAdvice/withdrawDoctorStationAdviceBatch` |
| IF-DOR-17 | `updateDoctorStationMedicationAdviceGroups` | 更新药品医嘱组号 | `POST /api/app/doctorStationAdvice/updateDoctorStationMedicationAdviceGroups` |
| IF-DOR-18 | `initDoctorStationAdvice` | 医嘱开立页初始化（**用药频次 / 给药途径 / 中药用法** 术语下拉） | `POST /api/app/doctorStationAdvice/initDoctorStationAdvice` |
| IF-DOR-19 | `listActivityExecutionLocations` | 按诊疗活动定义查询可执行科室规则（含执行科室/地点展示名） | `POST /api/app/doctorStationAdvice/listActivityExecutionLocations` |
| IF-DOR-20 | `pageDoctorStationAdvice` | 分页查询患者全类型医嘱（当前医嘱/历史 Tab） | `POST /api/app/doctorStationAdvice/pageDoctorStationAdvice` |
| IF-DOR-21 | `saveDoctorStationServiceApplyForm` | 保存检验/检查/治疗申请单（整张） | `POST /api/app/doctorStationAdvice/saveDoctorStationServiceApplyForm` |
| IF-DOR-22 | `listDoctorStationServiceApplyForms` | 查询申请单列表（按 categoryEnum） | `POST /api/app/doctorStationAdvice/listDoctorStationServiceApplyForms` |


#### 3.1.1 编排约定（应用层如何实现 AS §4）


| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限/租户/参数门禁；签发批次签署码等 | 不再调用后续步骤 |
| **Pre** | 写前只读：加载 `OutpatientEncounterContextQueryModel`、库存可用量、费用锚点、外发前置条件 | 抛业务异常，**禁止进入 Cmd** |
| **Cmd** | 领域写：各 `*DomainService` + 持久化 + 领域事件 | 事务回滚见 §5 |
| **Asm** | `DoctorStationAdviceMapper` 纯 DTO 装配 | — |


**记法**：`MedicationRequestDomainService#createMedicationOrderDraft(CreateMedicationOrderDraftCommand)` 表示对注入 Bean 调用该方法并传入括号内命令类型实例。

**重要说明**：先读完各 IF 的「领域编排序列」，按序号从上到下执行。

---

### 3.2 IF-DOR-10 `listDoctorStationAdvice`（加载当前就诊医嘱统一列表）

本接口为开立 Tab **医嘱主表**的权威读路径：返回行为 **`MEDICATION`/`DEVICE`/`SERVICE`** 统一行。其中**药/材**须与 IF-DOR-14/15 **同一** `generate_source`（医生站常量）过滤；**IF-DOR-10** 另在药品侧**排除** `tcm_flag=true` 饮片行（**AS `BR-ORD-026`**，中医 Tab 由 **AS-OP-DOR-010** 承载）；**收费汇总**不得假设「一行请求 = 一行 `ChargeItem`」——须按 **AS `BR-ORD-016`** 识别 **`ChargeItem.service_table`+`service_id` → 发放物理行**，再按 `med_req_id` / `device_req_id`（或等价 JOIN）**归并**到列表行推导 `chargeStatusEnum` 等（与 **`DM-ADM-011`** **`BR-CHG-001`** 一致）。**药/材**另装配 **FIFO 首选发放库位** **`fifoDispenseLocationId` / `fifoDispenseLocationName`**（**AS `BR-ORD-019`**：多条发放时取发放主键升序首条所在库位；无发放事实或未签发可为空）。**展示排序号** `sortNumber` 与 **AS `BR-ORD-018`**：自三类请求实体映射至 DTO，装配末尾按「`sortNumber` 升序（空在后）→ 开立时刻（`reqOpenedTime`，空回落 `reqAuthoredTime`）降序」合并排序。除收费归并与 FIFO 库位外，每行还须满足 **AS `BR-ORD-019`**：**§3.2** 全量字段及 **§3.2.1** 与既有医嘱请求行信息的迁移对照。


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#listDoctorStationAdvice` |
| **HTTP** | `POST /api/app/doctorStationAdvice/listDoctorStationAdvice` |


#### Request 字段表（`ListDoctorStationAdviceRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识；与QueryService `OutpatientEncounterContextQueryModel.patientId` 一致性校验 |
| onlyMine | Boolean | 否 | `true` 时仅保留当前登录医师开立行（比较 `MedicationRequest`/`ServiceRequest`/`DeviceRequest` 之 `recorder`/`authoredBy` 等作者字段——以实体定稿为准） |


#### Response 字段表（`ListDoctorStationAdviceResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| rows | List\<DoctorStationAdviceRowDto\> | 统一列表行 |
| totalCount | Integer | 行总数 |


#### DTO 字段表（`DoctorStationAdviceRowDto`）

下列为 **IF-DOR-10 列表行契约**（与 **AS `BR-ORD-019`**、**`BR-ORD-022`** 对齐）。字典类 `*_code` 的展示名由 **`TerminologyOptionLoader#buildLabelMap`** 预热后，经 **`ProductDefinitionCatalogUnitBandsAssembler#resolveUnitDisplayName`** / **`#resolveDoseUnitDisplayName`** 回落产品QueryService字典文本；**禁止**仅返回 `unitCode`/`doseUnitCode` 而无对应 `*Name` 列。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| listRowKey | String | 稳定行键：`requestId` + **`GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR`**（ASCII SOH，U+0001）+ `productTableName`，与 IF-DOR-11 目录复合键同碰撞安全策略（**全量规范 A.9.1**：**禁止**使用 `\|`、`#` 等可碰撞分隔符）；对应既有 **`uniqueKey`** |
| requestId | Long | 药品/耗材/诊疗请求聚合主键 |
| encounterId | Long | 就诊标识 |
| patientId | Long | 患者标识 |
| productDefinitionId | Long | 与开立目录 **IF-DOR-11** 对齐的目录主键（药品/诊疗/耗材定义 id；无则 `null`） |
| productTableName | String | 与 **`product_definition_id`** 成对的物理表名（`med_medication_definition` / `wkf_activity_definition` / `adm_device_definition` 之一） |
| adviceDisplayName | String | **医嘱展示名**（药品/耗材/诊疗定义名称；对应既有 **`adviceName`**） |
| specificationText | String | **规格/大小**展示（药品包装规格或耗材 `SIZE` 等；对应既有 **`volume`**） |
| quantity | BigDecimal | 请求数量（对应既有 **`quantity`**；类型统一为 `BigDecimal`） |
| unitCode | String | 请求单位编码（持久化 `code`，B.10.8.1） |
| unitName | String | 请求单位展示名（**BR-ORD-022**：术语 `UNIT_CODE` 映射优先，未命中则产品QueryService `*_dict_text`；对应既有 **`unitCode_dictText`**） |
| dose | BigDecimal | 单次剂量（药品；非药品可 `null`） |
| doseUnitCode | String | 剂量单位编码 |
| doseUnitName | String | 剂量单位展示名（**BR-ORD-022**：术语 `DOSE_UNIT` 映射优先，未命中则产品 **`dose_unit_code_dict_text`**） |
| methodCode | String | 给药途径/用法编码（药品） |
| methodName | String | 用法展示名 |
| rateCode | String | 用药频次编码 |
| rateName | String | 频次展示名 |
| lotNumber | String | 产品批号（药品/耗材有则填） |
| dispensePerDuration | Integer | 用药天数（药品） |
| partPercent | BigDecimal | 拆零比（定义或请求侧） |
| performLocationId | Long | 执行/发药（材）**位置 id**（对应既有 **`positionId`**：`perform_location` 或执行科室组织 id 等，以实现映射为准） |
| performLocationName | String | 位置展示名（对应既有 **`positionName`**） |
| fifoDispenseLocationId | Long | **药品/耗材行**：签发后 FIFO 发放事实上**首选库位 id**（多条发放时取 **`med_medication_dispense` / `wkf_device_dispense` 主键升序**第一条的 `location_id`；无发放或未签发为 `null`）；**诊疗行**为 `null` |
| fifoDispenseLocationName | String | **药品/耗材行**：首选库位展示名（由 **`LocationQueryService#mapNamesByIds`** 解析）；**诊疗行**为 `null` |
| conditionDefinitionName | String | 绑定**诊断定义**展示名（由 **`encounterDiagnosisIds`** 首位 id 关联的 `condition_definition_id` 经 **`ConditionDefinitionQueryService`** 解析；无绑定时为 `null`） |
| encounterDiagnosisIds | String | 绑定**就诊诊断**主键有序串（`adm_encounter_diagnosis.id`，**主诊第一**；与各类暂存载荷 `*Payload.encounterDiagnosisIds` 及库表 `encounter_diagnosis_ids` 一致；编辑回填原样） |
| skinTestFlag | Boolean | 是否皮试（药品；`Integer` 0/1 在装配层转布尔） |
| skinTestFlagText | String | 皮试标志展示文案（对应既有 **`skinTestFlag_enumText`**） |
| injectionFlag | Boolean | 是否注射/输液相关（对应既有 **`injectFlag`**） |
| injectionFlagText | String | 注射标志展示文案 |
| skinTestOutcomeCode | String | 皮试结果编码（对应既有 **`clinicalStatusEnum`** 持久化值之字符串形态） |
| skinTestOutcomeText | String | 皮试结果展示文案（对应既有 **`clinicalStatusEnum_enumText`**） |
| prescriptionNo | String | 处方号（药品；未签发可空） |
| prescriptionCategoryCode | String | 处方类别编码（`DM-MED-003` `rx_type_code`；**首版装配未赋值**，可 `null`） |
| prescriptionCategoryName | String | 处方类别展示名（**首版装配未赋值**，可 `null`） |
| groupId | Long | 药品成组号（非药品可 `null`） |
| chineseHerbsDoseQuantity | Integer | 中药付数（对应 **`DM-MED-003` §3**；非中药行 `null`） |
| decoctionDelegatedFlag | Boolean | 代煎标识（对应既有 **`sufferingFlag`** 0/1 语义；命名与域字段对齐后以代码为准） |
| dosageInstructionCode | String | 用药说明编码（对应 **`dosage_instruction`** / `DM-MED-003` §3 `dosage_instruction`） |
| dosageInstructionText | String | 用药说明展示名 |
| speedCode | String | 滴速编码（`DM-MED-003` §3 `speed_code`） |
| speedName | String | 滴速展示名 |
| basedOnServiceRequestId | Long | 皮试等关联的**前置服务请求** id（对应既有 **`basedOnId`**） |
| remark | String | 备注 |
| serviceRequestDetails | List\<`ServiceRequestDetailSummaryDto`\> | **申请单行**（`prescriptionNo` 以 **`AR`** 前缀标识）：由 **`ServiceRequestQueryService#mapDetailListModelsByServiceRequestIds`** 映射为 `targetTable`/`targetId`/`targetDisplayName`/`remark`；非申请单行可 `null` 或空；**前端**医嘱内容 extension 区展示（有值才展示，口径同 **BR-NUR-016**） |
| entrustContent | String | 嘱托内容（诊疗等；对应既有 **`entrustContent`**） |
| packageChildren | List\<AdvicePackageChildItemDto\> | 诊疗套餐子项（子表事实；**禁止**对外下发 `children_json` 原始串） |
| requestExtensions | AdviceRequestExtensionSnapshotDto | 由库表 `content_json` 解析的结构化扩展（`attributes` / `entries`）；**禁止**对外下发 `contentJson` 原始串（**B.6.2**） |
| primaryChargeItemId | Long | **主展示用**收费明细 id（多 `ChargeItem` 时取归并规则下的代表行，如最新草稿或最高优先级态；对应既有单条 LEFT JOIN 的 **`chargeItemId`**） |
| chargeItemIds | List\<Long\> | 同一请求行关联的全部收费明细 id（可选；用于调试或多行展示） |
| lineDisplayUnitPrice | BigDecimal | 行级展示单价（代表 `ChargeItem.unit_price`，6 位小数；**仅展示**） |
| lineDisplayTotalPrice | BigDecimal | 行级展示总价（由关联 **`ChargeItem`** 汇总或取代表行；**仅展示**，入账权威仍 **AS `BR-ORD-016`**） |
| lineAmountByUnitAndQuantity | BigDecimal | `quantity × lineDisplayUnitPrice` 推算金额（多收费行时仅参考） |
| displayStatusEnum | String | 列表「状态」列 code：退费态优先于 `statusEnum` |
| displayStatusEnumText | String | 列表「状态」列展示文案 |
| catalogCategoryCode | String | 产品目录 `category_code` |
| catalogCategoryName | String | 目录分类展示名 |
| activityTypeCode | String | 诊疗活动类型 code（仅 `wkf_activity_definition` 行） |
| activityTypeName | String | 诊疗活动类型展示名 |
| chargeStatusEnum | String | 行级收费汇总态 code（**BR-ORD-016** 归并后） |
| chargeStatusEnumText | String | 收费状态展示文案 |
| statusEnum | String | 请求状态 code |
| statusEnumText | String | 请求状态展示文案 |
| authoredById | Long | 开立者（对应药品 **`recorder`/practitioner** 或诊疗耗材 **`requester`**） |
| authoredByName | String | 开立者姓名 |
| isAuthoredByCurrentUser | Boolean | 是否当前登录医师开立（对应既有 **`bizRequestFlag`**=`1`/`0`；与 **`onlyMine`** 过滤语义一致） |
| reqOpenedTime | String | 开立时间：ISO-8601 **local**（`GeneralSharedConstants#ISO_LOCAL_DATE_TIME_FORMATTER`）。**持久化来源**：三类 Request 的 `req_opened_time`（首次草稿/创建写入，签发不覆盖；空则装配可回落 `req_authored_time`） |
| stopTime | String | 停嘱时间 ISO-8601 local（`stop_time`；无则 `null`） |
| signedById | Long | 签署人（若已签发；**首版装配未赋值**，可 `null`） |
| reqAuthoredTime | String | 签发时间 ISO-8601 local（`req_authored_time`；**仅系统追溯**；列表/主表**默认不展示**；草稿未签发可 `null`） |
| contractId | Long | 费用性质主键（**药品行**：`med_medication_request.contract_id`；非药品可 `null`） |
| feeNatureText | String | 费用性质分类展示文案（**`ContractCategory`** → 自费/公费/省医保/市医保/职工医保）；由 **`ContractExpenseNatureAssembler#mapCategoryTextByContractIdIn`** 批量解析；处方笺展示；无合同时为空，前端回落「自费」 |
| sortNumber | Integer | **医生站主表展示排序号**（**AS `BR-ORD-018`**） |
| contentSummary | String | **窄列摘要**（可由 `adviceDisplayName`+剂量+单位拼接；**不得**作为唯一医嘱内容来源） |

#### 3.2.1 既有医嘱主表行信息 → ZXPlat 列表行字段（迁移对照）

> 下列「既有展示项」指既有门诊医生站**医嘱主表列表**在就诊维度由 SQL 合并药品/耗材/诊疗请求并投影为**统一行 DTO**的字段（应用层再补充 `*_enumText` 字典文案）。**新栏位名为 IF-DOR-10 目标态契约**，实现时装配来源为各域查询模型 / `ChargeItem` / 术语服务，不以本表为 SQL 列名。

| 既有展示项（DTO 属性） | ZXPlat `DoctorStationAdviceRowDto` 目标栏位 | 备注 |
| --- | --- | --- |
| `adviceType`（1 药 / 2 材 / 3 诊） | `productDefinitionId` + `productTableName`（三张定义表名之一） | **对外不下发** `resourceType` / `MEDICATION`/`SERVICE`/`DEVICE` 等间接类型码；旧整数枚举勿外泄 HTTP |
| `uniqueKey` | `listRowKey` | 语义：`requestId` + SOH + `productTableName`（与目录复合键分隔符一致） |
| `requestId` | `requestId` | 一致 |
| `prescriptionNo` | `prescriptionNo` | 一致 |
| `requesterId` | `authoredById` | 药品用 `practitioner_id`、诊疗/耗材用 `requester_id` 源列 |
| `requesterId_dictText` | `authoredByName` | 由医务人员查询或快照装配 |
| `requestTime` | `reqOpenedTime` | 格式由 `Date` 改为 **ISO local 字符串**；药/材/诊取 `req_opened_time`（勿仅用审计 `create_time` 冒充开立时间） |
| `endTime` | `stopTime` | 停嘱 `stop_time`；同上 ISO 字符串 |
| `bizRequestFlag` | `isAuthoredByCurrentUser` | `'1'`→`true` |
| `contentJson` | `requestExtensions` | 结构化扩展快照（**B.6.2** 禁止原始串） |
| `conditionDefinitionName` | `conditionDefinitionName` | 一致 |
| （就诊诊断 ids，用于编辑回填） | `encounterDiagnosisIds` | 与暂存对应载荷 **`medicationPayload` / `servicePayload` / `devicePayload`** 之 **`encounterDiagnosisIds`** 及实体 **`encounter_diagnosis_ids`** 对齐（有序 ids 串，主诊第一） |
| `groupId` | `groupId` | 一致 |
| `chargeItemId` | `primaryChargeItemId`（及可选 `chargeItemIds`） | 新框架多发药行锚 **`ChargeItem`** 时须归并；**禁止**假设单行费用 |
| `adviceName` | `adviceDisplayName` | 一致 |
| `volume` | `specificationText` | 一致 |
| `lotNumber` | `lotNumber` | 一致 |
| `quantity` | `quantity` | 类型升为 `BigDecimal` |
| `unitCode` / `unitCode_dictText` | `unitCode` / `unitName` | 一致 |
| `statusEnum` / `statusEnum_enumText` | `statusEnum` / `statusEnumText` | **ZXPlat 存 String code**；装配时由整型持久化迁移则须兼容转换 |
| `chargeStatus` / `chargeStatus_enumText` | `chargeStatusEnum` / `chargeStatusEnumText` | 同上 **String code** 口径 |
| `skinTestFlag` / `skinTestFlag_enumText` | `skinTestFlag` / `skinTestFlagText` | 布尔 + 文案 |
| `injectFlag` / `injectFlag_enumText` | `injectionFlag` / `injectionFlagText` | 命名对齐英文列 |
| `clinicalStatusEnum` / `clinicalStatusEnum_enumText` | `skinTestOutcomeCode` / `skinTestOutcomeText` | 语义：皮试结果 |
| `methodCode` / `methodCode_dictText` | `methodCode` / `methodName` | 一致 |
| `rateCode` / `rateCode_dictText` | `rateCode` / `rateName` | 一致 |
| `dose` / `doseUnitCode` / `doseUnitCode_dictText` | `dose` / `doseUnitCode` / `doseUnitName` | 一致 |
| `totalPrice` | `lineDisplayTotalPrice` | **展示用**；与 **BR-ORD-016** 一致 |
| `positionId` / `positionName` | `performLocationId` / `performLocationName` | 发药房/耗材房/执行科室依资源类型映射 |
| （签发后 FIFO 首选发药/发材库位） | `fifoDispenseLocationId` / `fifoDispenseLocationName` | **AS `BR-ORD-019`**；药/材由发放事实解析，诊疗通常空 |
| `dispensePerDuration` | `dispensePerDuration` | 一致 |
| `partPercent` | `partPercent` | 一致 |
| `chineseHerbsDoseQuantity` | `chineseHerbsDoseQuantity` | 中药场景 |
| `sufferingFlag` | `decoctionDelegatedFlag` | 代煎；若工程常量仍用 `suffering` 命名以实现为准 |
| `sortNumber` | `sortNumber` | 耗材/诊疗在既有 SQL 中常固化为 99，新框架以实体真实 `sort_number` 为准（见 **`DM-WKF-003`/`DM-WKF-004`**） |
| `dosageInstruction` / `dosageInstruction_dictText` | `dosageInstructionCode` / `dosageInstructionText` | 字典装配 |
| `basedOnId` | `basedOnServiceRequestId` | 语义显式化 |
| `speedCode` / `speedCode_dictText` | `speedCode` / `speedName` | 一致 |
| `remark` | `remark` | 一致 |
| `entrustContent` | `entrustContent` | 主要诊疗用 |
| `childrenJson` | `packageChildren` | 诊疗套餐子项列表 |
| `rxTypeCode` / `rxTypeCode_dictText` | `prescriptionCategoryCode` / `prescriptionCategoryName` | 药品 |
| （SQL 未投影但 DTO 存在） | `encounterId` / `patientId` | ZXPlat 显式返回，便于前端缓存校验 |
| （目录编辑衔接） | `productDefinitionId` / `productTableName` | 与 **IF-DOR-11** 预选键对齐，减少仅靠 `contentJson` 推断 |

#### 关键校验

- `encounterId`/`patientId` 必填；与 `OutpatientEncounterContextQueryModel` 快照不一致 → Gate 失败 EX-ORD-201
- **AS `BR-ORD-010`**：③⑤ 须使用与写路径一致的医生站 `generate_source` 过滤（药/材对称）
- **AS `BR-ORD-016`**：⑥ 装载的 `ChargeItem` 参与归并时**不得**误用请求表主键替代发放锚点推导行级收费态
- **AS `BR-ORD-018`**：`sortNumber` 仅回显与排序，**不**改变请求业务状态；缺省未携带排序号的行排在已携带行之后
- **AS `BR-ORD-019`**：列表装配**禁止**以「药品申请 + `busNo`」或「诊疗申请 + id」等占位摘要代替 **`adviceDisplayName`** 与剂量/用法等核心列；`content_json` 须解析为 **`requestExtensions`**（及诊疗 **`packageChildren`**），**禁止**原样下发 JSON 串；**药/材**须装配 **`fifoDispenseLocationId`/`fifoDispenseLocationName`**（发放主键序首条库位，见 **`MedicationDispenseQueryService` / `DeviceDispenseQueryService`**）

#### 失败码口径表


| 失败码 | 场景 | 用户提示方向 |
| --- | --- | --- |
| EX-ORD-201 | QueryService或聚合装载失败 | 稍后重试 |

#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId)` + `DoctorStationAdviceMapper#validatePatientEncounter(patientId, snapshot.patientId)` | Request | 快照不存在抛 `ENCOUNTER_NOT_FOUND`；患者不一致抛 `ENCOUNTER_INVALID_ARGUMENT` |
| ② | Pre | `MedicationRequestQueryService#listMedicationRequestsByEncounterForDoctorStation(encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode())` | Request + `GenerateSource` | 医生开立来源过滤（仓储侧） |
| ③ | Pre | `ServiceRequestQueryService#listServiceRequestsByGenerateSource(encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode())` | 同上 | 与药品同一生成来源 |
| ④ | Pre | `DeviceRequestQueryService#listDeviceRequestsByEncounterForDoctorStation(encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode())` | 同上 | 与药品对称 |
| ⑤ | Pre | `ChargeItemQueryService#listChargeItemsByEncounterId(ChargeItemQueryCommand)` | encounterId | 本就诊 `ChargeItem` 全集，供装配器 rollup |
| ⑥ | Asm | `DoctorStationAdviceListAssembler#assembleRows(encounterId, patientId, onlyMine, currentUserId)` | ②～⑤ + `CurrentOperatorResolver#findOperatorIdOrNull` | 合并三类行、收费态归并、`reqOpenedTime`/`reqAuthoredTime`/`stopTime` 格式化、`sortNumber` 映射；**药/材**：批量解析发放 id → 首选库位 id → **`LocationQueryService#mapNamesByIds`** 填充 **`fifoDispenseLocation*`**；按 **BR-ORD-019** 装配 **§3.2** 全量展示列（含 `adviceDisplayName`、`requestExtensions`/`packageChildren`、用法/剂量/位置/诊断名/皮试结果等——可增 **Pre** 批量查定义名、医务人员姓名、术语展示名，但以装配器入口不变为优先）；按 **BR-ORD-018** 合并排序；**仅读路径** |


#### Mapper 接口方法签名（与实现一致）

```java
void validatePatientEncounter(Long patientId, Long encounterPatientId);
```

> 列表行装配见 `DoctorStationAdviceListAssembler#assembleRows`；`ListDoctorStationAdviceResponse` 由 `AppService` 直接填充。


---

### 3.3 IF-DOR-11 `pageDoctorStationAdviceCatalog`（分页检索可开立医嘱项目目录）

> **v3.1 实现提示**（**v3.3** 起 **`unitBands`** 类型与装配见 **§2.1** 共享说明）：QueryService 分页在 **`DoctorStationAdviceAppService`** 内调用 `ProductDefinitionCatalogPageLoader#pageProductDefinition`；`DoctorStationAdviceCatalogAssembler#assemblePageContent` 对**当前页**逐行：`#baseRow` 透传QueryService；**`unitBands`** 由 **`ProductDefinitionCatalogUnitBandsAssembler#buildDoctorStationBands`** 装配；药/材再拉候选库位、物理库存、发放占用、预减写 `inventoryList` / `availableQuantity` / `defaultDispenseLocationId`；**定价**：**诊疗**仅在 **`SALE`** 价型内择优写汇总 **`unitPrice`**；**药耗**在 **`LOT_NUMBER_PRICE`** 内解析——**有库存行**时按批号对齐回填 **`inventoryList[].price`/`chargeItemDefinitionId`**，汇总价与首条库存批号一致，**无库存**时在 **`LOT_NUMBER_PRICE`** 内择优；**IF-DOR-14 暂存**经 **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`**（前端回传 `dispenseLocationId` 时不再 match 模板）；**IF-DOR-15/37 签发分段**经 **`OutpatientMedicationSignChargeOrchestrator`** 按 FIFO **`lotNumber`** → **`selectExecutionPriceLineForChargeDraft`**（**禁止** `selectFirstPriceLineForChargeDraft` 跨批号 priority）。与旧稿「①～⑭ 全在 Assembler 的具名步骤链」**不等价**，消差说明见 **§2.6**。

本接口为医生站开立页「药/诊/材」三合一目录的**权威读路径**：**目录主数据**来自 **`ProductDefinition` QueryService**（`ProductDefinitionCatalogPageLoader`，契约见 `DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` **v3.9+**）。药/诊/材三类目录行以 **`product_definition_id` + `product_table_name`** 为唯一业务锚点（**不再**下发 `adviceType`），请求预选、库存关联与下游开立须携带**成对**字段，禁止仅用 Long 型 id 跨表推断类型。**库存剩余量**在展示前须完成**待发占用预减**；**默认发药/发材库房**由 **`DM-CUS-006#matchDefaultDistributionLocation`**（开立科室 + 物品表 + **`categoryCode`** + 具体项 + 用法 + 时段，calcScore 择优）解析，**不**取预减后首条正库存；**批号**仅在 **`inventoryList`** 各行。**目录展示定价**：QueryService **`price`/`charge_item_definition_id` 列**为QueryService 侧 **UNIT 等口径快照**，**不作为** IF-DOR-11 行级展示权威；**诊疗**展示单价来自 **`ChargeItemDefinition`** 中 **`SALE`**；**药耗**来自 **`LOT_NUMBER_PRICE`** 且 **`lot_no`** 与库存批号对齐（见 §3.3 规则 **F**）。上述过滤条件、枚举码与算法分支以本文 §3.3 规则及 AS **BR-ORD-001** 为准，表名/列名以实现期物理模型为准映射。

**药/材对称（门诊）**：`med_medication_definition` 与 `adm_device_definition` 在本接口读路径下**同构**——共享目录分页、`inventoryList` 预减、`defaultDispenseLocationId`（`DM-CUS-006` + 降级）、**目录侧**仅 **`LOT_NUMBER_PRICE`** 批号售价（明细行 **`inventoryList[].price`**，汇总 **`unitPrice`** 对齐首条库存批号）。**暂存/签发/计费锚点**与 **§3.6 IF-DOR-14**、**§3.7 IF-DOR-15** 中药品路径一致，由 **`MedicationRequest`/`MedicationDispense`** 与 **`DeviceRequest`/`DeviceDispense`** 双写路径对称实现；请求级不落最终发放库房、**`ChargeItem` 与每条发放物理行 1:1** 等域约束见 **`DM-MED-003`**、**`DM-MED-002`**、**`DM-WKF-003`**、**`DM-WKF-007`**、**`DM-ADM-011`**（**`BR-CHG-001`**）。


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#pageDoctorStationAdviceCatalog` |
| **HTTP** | `POST /api/app/doctorStationAdvice/pageDoctorStationAdviceCatalog` |


#### Request 字段表（`PageDoctorStationAdviceCatalogRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| organizationUnitId | Long | 否 | 可选机构/组织单元上下文：传入 **`DoctorStationAdviceCatalogAssembler#assemblePageContent`**，非空时**优先于**门诊快照 `organizationId`，用于 **`ServiceLocationTemplate`** / 候选发药·发材库房解析；**不**下推至 `ProductDefinitionPageQuery`，QueryService目录**不按** `med_medication_definition / adm_device_definition / wkf_activity_definition（主表）.org_id` WHERE 过滤 |
| pharmacyLocationId | Long | 否 | 可选药房/库房 id；传入 `loadAdvicePhysicalInventory` 收窄库房维度；**若**为空则不在库房维度上追加等值条件 |
| productTableNames | List\<String\> | 否 | 收窄 **`product_table_name`**；**申请单路径**须仅含 `wkf_activity_definition` |
| excludeMedicationCategoryCodes | List\<String\> | 否 | 排除药品 **`med_category_code`**（**BR-ORD-027**）；如开立西药医嘱传 `["4"]` 排除中草药；**仅** `med_medication_definition` 检索；与 `categoryEnum` 无关 |
| （服务端恒定）`excludeCategoryCodes` | List\<String\> | — | **BR-ORD-032**：Mapper 恒写入 `["52"]`，活动定义分页排除嘱托类目，并双锚排除名「嘱托」；**禁止**门诊目录返回嘱托 |
| categoryEnum | String | 否 | **申请单选品**：按诊疗**目录分类**过滤（`activity_category_code`）；非空时与 `productTableNames` 联合收窄为对应检查/检验/治疗 |
| typeEnum | String | 否 | **申请单分类 chips**：按诊疗**业务类型** `type_enum`（术语 `activity_type_code`）精确过滤；空=不限；与 `categoryEnum` 独立；LIS/PACS 外发不读本字段 |
| keyword | String | 否 | 名称、拼音、五笔、编码等检索列的模糊条件；具体列以 `ProductDefinitionPageQuery` 与QueryService投影为准 |
| pageNo | Integer | 是 | 页码（**1-based**；Assembler 调 `ProductDefinitionBatchLoader` 时转换为 Spring `Page` 的 0-based `page`） |
| pageSize | Integer | 是 | 页大小 |
| pricingFlag | Integer | 否 | **划价标记**：`pricingFlag == 1` 时不返回药品行；诊疗行按 `pricing_flag` 与请求值等值过滤 |
| prepickedProductKeys | List\<PrepickedProductKeyDto\> | 否 | 预选目录项；每项含 **`productDefinitionId` + `productTableName`**，与QueryService多态唯一键一致；Assembler 转为 `(product_definition_id, product_table_name)` 复合条件收窄分页结果（实现可用 OR 拼接或仓储支持的 `(id,table) IN`） |
| sortMode | String | 否 | 关键字命中排序；具体谓词与 `ORDER BY` 以 `ProductDefinitionPageQuery` / QueryService仓储实现为准（**当前** Assembler **不再**二次调用 `sortCatalogRowsByKeyword`） |


#### DTO 字段表（`PrepickedProductKeyDto`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| productDefinitionId | Long | 是 | 与QueryService `product_definition_id` 一致 |
| productTableName | String | 是 | 与QueryService `product_table_name` 一致（`med_medication_definition` / `adm_device_definition` / `wkf_activity_definition` 之一） |


#### Response 字段表（`PageDoctorStationAdviceCatalogResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| rows | List\<DoctorStationAdviceCatalogRowDto\> | 当前页行；药/材行须带 `inventoryList`（预减后，**批号在明细**）；药/诊/材目录展示单价见各行 **`unitPrice`** + `chargeItemDefinitionId`（**无** `priceListJson`） |
| totalCount | Long | 总命中数 |
| pageNo | Integer | 当前页 |
| pageSize | Integer | 页大小 |


#### DTO 字段表（`DoctorStationAdviceCatalogRowDto`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| productDefinitionId | Long | **目录锚点之一**：与QueryService `product_definition_id` 一致 |
| productTableName | String | **目录锚点之二**：与QueryService `product_table_name` 一致（药/诊/材类型锚点；**无** `adviceType`） |
| busNo | String | 业务编码（`product_bus_no`） |
| name | String | 展示名称 |
| pyCode | String | 名称拼音检索码 |
| wbCode | String | 名称五笔检索码 |
| categoryCode | String | 目录类别编码 |
| categoryCodeDictText | String | 目录类别展示名 |
| unitCode | String | **目录主单位编码**（**仅** **`package_unit_code`**，由 **`ProductDefinitionCatalogUnitBandsAssembler#resolveCatalogPrimaryUnitCode`** 解析；**禁止**跨槽回落；**与采购单位无关**） |
| unitName | String | 与 **`unitCode` 同槽**之单位展示名（**禁止** dictText 跨槽 `firstNonBlank`） |
| unitBands | List\<ProductDefinitionCatalogUnitBandDto\> | 五类单位槽（采购/拆零/**包装**/库存/剂量）及相对拆零倍数 `ratioToSplit`；**诊疗仅 `PACKAGE` 槽有值**；`bandKind` 与 **`ProductDefinitionCatalogUnitBandConstants#BAND_*`** 对齐；元素类型见 **§2.1** 共享说明 |
| rateCode / rateCodeDictText | String | 药品默认用药频次编码 / 展示名（QueryService投影） |
| methodCode / methodCodeDictText | String | 药品默认用法（给药途径）编码 / 展示名 |
| doseFormCode / doseFormCodeDictText | String | 药品剂型编码 / 展示名 |
| restrictedFlag | Boolean | 是否限制使用 |
| restrictedScope | String | 限制使用范围说明 |
| restrictedEnum / restrictedEnumDictText | String | 限制用药等级编码 / 展示名 |
| partAttributeEnum / partAttributeEnumDictText | String | 拆零属性码 / 术语展示名 |
| thoPartAttributeEnum / thoPartAttributeEnumDictText | String | 住院临时拆分属性码 / 术语展示名 |
| totalVolume | String | 包装规格 |
| activityTypeCode | String | 诊疗活动类型 code（字符串化整型） |
| skinTestFlag | Boolean | 皮试标志（药品） |
| injectFlag | Boolean | 注射相关标志（药品） |
| defaultPerformOrgId | Long | 默认执行科室 id（QueryService `location_id` 诊疗语义）；**前端选品预填**仅用本字段（及 `defaultPerformOrgName`），**禁止**回落当前登录科室（**BR-ORD-024**） |
| defaultPerformOrgName | String | 默认执行科室名称；与 `defaultPerformOrgId` 成对用于选品预填 |
| defaultDispenseLocationId | Long | 默认发药/发材库房：`matchDefaultDistributionLocation`（开立科室 + 物品表 + **`categoryCode`** + 具体项 + 用法）择优结果；无命中时为 null |
| defaultDispenseLocationName | String | 默认库房名称（预留） |
| availableQuantity | BigDecimal | 药/材：候选库位下预减后可开总量，**展示单位**为**包装单位**（与 **`unitCode`** 一致）；由拆零净量经 **`ProductDefinitionCatalogUnitBandsAssembler#convertDoctorStationCatalogAvailableQuantity`** 换算；**非**拆零小单位直出；诊疗可为 `null`。**前端**（`advice-list.ts`）：库存列单位名取 **`unitCode`/`unitName`** 或 **`unitBands` 中 `BAND_PACKAGE`** 槽展示名（二者应一致） |
| inventoryList | List\<DoctorStationAdviceInventoryRowDto\> | **仅药/材**：预减后的库房/批号明细；**批号仅在此** |
| unitPrice | BigDecimal | 目录行汇总展示单价（元）；**诊疗**：仅在 **`SALE`** 价型内择优；**药耗**：与**首条**预减后 **`inventoryList`** 批号在 **`LOT_NUMBER_PRICE`** 下命中之 **`amount`** 一致（无库存时在 **`LOT_NUMBER_PRICE`** 内择优/兜底）；QueryService **`price` 列不作为**本字段权威 |
| chargeItemDefinitionId | Long | 与 **`unitPrice`** 同属一条 **`ChargeItemDefinition`** 主键；**药耗有库存**时与首条库存批号对应定价行一致；无命中时可回落QueryService携带 id |

#### DTO 字段表（`ProductDefinitionCatalogUnitBandDto`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| bandKind | String | 槽语义：`PURCHASE` / `SPLIT` / **`PACKAGE`** / `STOCK` / `DOSE`（与 **`ProductDefinitionCatalogUnitBandConstants`** 一致；**`SALES` 已废弃**） |
| unitCode | String | 该槽位单位编码 |
| unitName | String | 该槽位单位展示名 |
| ratioToSplit | BigDecimal | 相对拆零单位的倍数；拆零槽为 1 |


#### DTO 字段表（`DoctorStationAdviceInventoryRowDto`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| itemTable | String | 实例表名（药/材定义表） |
| itemId | Long | 库存项实例 id；药/材与目录行 **`productDefinitionId`** 在同名 **`itemTable`** 下一致（即来源定义表主键） |
| categoryCode | String | 目录类别 |
| inventoryId | Long | 库存行主键 |
| quantity | BigDecimal | **预减后**剩余量（小单位） |
| unitCode | String | 小单位编码（拆零单位，与 **`split_unit_code`** 一致） |
| unitName | String | 小单位展示名（QueryService **`split_unit_code_dict_text`**） |
| lotNumber | String | 批号；无库存合并分组后可为 `null` |
| locationStoreId | Long | 库位 id（若物理模型有） |
| locationId | Long | 库房/药房 id |
| locationName | String | 库房名称 |
| price | BigDecimal | **批号售价**（元）：价型 **`LOT_NUMBER_PRICE`**，`adm_charge_item_definition.lot_no` 与本行 **`lotNumber`** 匹配之 **`amount`**；无匹配时可空 |
| chargeItemDefinitionId | Long | 与 **`price`** 对应的 **`adm_charge_item_definition.id`** |
| displayOrder | Integer | 取药科室配置展示顺序（当本行同时用于配置投影时；纯库存行可空） |


#### 业务规则与查询口径（须在 `DoctorStationAdviceCatalogAssembler` 或等价只读查询中落地）

**A. 目录分页（`ProductDefinitionPageQuery` + `ProductDefinitionCatalogPageLoader`）**

- 仅返回已启用/可售目录行：`active_flag` 或QueryService约定启用条件。
- **QueryService `org_id`**：列仍由投影维护（溯源/展示）；**不得**作为 `ProductDefinitionCatalogPageLoader` 分页 Specification 的机构可见性 WHERE（与 **`ProductDefinition` QueryService v3.9** 一致）。
- **产品形态**：按 Request **`productTableNames`** 收窄 `product_table_name`（空=三表；非空=列表内表名之并）；不得再依赖 `MED`/`SRV`/`DEV` 等间接类型码。
- **多表「全部」页大小（硬约束）**：`productTableNames` 空时，`ProductDefinitionCatalogPageLoader` 对药/诊/材**各表均按请求完整 `pageSize` 做 DB 分页**，再按表序合并并截到本页 `pageSize`；**禁止**按表数均分 `pageSize`（旧实现 `pageSize/表数` 会在关键字仅命中单表时少返回结果，表现为「全部搜不到、切耗材/药品 Tab 才有」）。`totalCount` 为各表命中数之和；合并行序随表序拼接，**非**跨表全局精确排序分页（**DOC-CONFLICT**，与历史 RM 一致）。
- **预选回查**：`prepickedProductKeys` 非空时每表仍用完整 `pageSize`，合并上限放宽为 `pageSize×表数`，避免同表多项被截断。
- **划价**：行为同 Request 表 `pricingFlag` 说明。
- **关键字**：QueryService已投影的检索列 OR 模糊匹配。
- **预选**：`prepickedProductKeys` → 复合条件 **`(product_definition_id, product_table_name)`** 命中收窄（禁止仅用 Long id 跨表匹配）。

**B. 物理库存（`loadAdvicePhysicalInventory`）**

- 在规则 **E** 已解析出**候选发药/发材库房 `locationId` 集合**的前提下，本条返回的库存行**须**满足 `locationId ∈ 候选集合`（与全量物理库存**求交**）；**不得**将非候选库房数量纳入规则 **C/D** 的占用汇总与预减结果（与 AS **§1.7** / **BR-ORD-011** 一致）。若 Request 另带 `pharmacyLocationId` 收窄，须在候选集合内再次收窄。
- 仅包含**药房**形态库位（库存域/位置维度的「药房」形态枚举，以实现常量为准）。
- 须包含：效期、可售状态、药/材 `item_table` 集合、**当前页**药/材行的 **`product_definition_id`**（与 `item_table`/`product_table_name` 一致）集合等过滤；具体列以实现期库存QueryService/SQL 为准，**不得删减**已在本 SDD 列出的业务含义条件。

**C. 待发占用（`loadOccupancyMap` + `MedicationDispenseQueryService#listMedicationDispensesForOrderCatalogOccupancy` / `DeviceDispenseQueryService#listDeviceDispensesForOrderCatalogOccupancy`）**

- 汇总发放处于 **`DoctorStationOrderCatalogConstants#DISPENSE_OCCUPANCY_STATUSES_FOR_ORDER_CATALOG`** 口径的占用量（以实现常量为准：`DRAFT`/`PREPARATION`）。
- 覆盖药品与耗材两类实例表；占用查询模型须回填发放行 **`unitCode`**（与 `MedicationDispense#unitCode` / `DeviceDispense#unitCode` 一致）。
- **时间窗分流（AS `BR-ORD-011`）**：**药品**读侧须 `now < limitTime`（草稿约 2h、待发约 24h）；**耗材**读侧**不**按 `limitTime` 过期，待发一直预占至护士执行或签退/取消释放（**DM-WKF-007 · BR-DD-QRY-003**）。
- **预减加总前**须调用 **`DoctorStationAdviceInventoryUnitQuantityHelper#mergeMedicationOccupancyInSplitUnits` / `#mergeDeviceOccupancyInSplitUnits`**：将各行 **`quantity` 按 `unitCode` 换算为拆零单位**后再按 `itemTable + definitionId + locationId` 复合键汇总（与 **AS `BR-ORD-021`**、药房扣减 **`MedicationProductQuantityToSplitUnitConverter`** 同口径）。

**D. 预减可用量（`subtractInventoryNetAvailable` / `consumeLocationOccupancyForLot`）**

- 定价由 **`ClinicalCatalogPriceResolver** 统一解析（**BR-ORD-001**）。
- **待发占用聚合键**（规则 **C** 汇总后）= `itemTable + itemId + locationId`（**不含批号**）；物理库存按**批号行**返回（`findAvailableCatalogRowsForOrder`）。
- **同库位多批预减（强制）**：遍历候选库位下物理批号行（FIFO 序与库存读侧一致）时，须 **`copyOccupancyRemaining`** 复制库位级预占映射，再对每一批调用 **`consumeLocationOccupancyForLot`**——本批净可用 = `min(本批账面, 该库位剩余预占)` 消化后余额；**剩余预占留给后续批次**。**禁止**对每一批都减去整库预占总量（否则两批 10+5、预占 9 会误算为 1 而非 6）。
- **数值例**：同库位批 A=10、批 B=5（拆零），全局待发预占 9 → 批 A 净 1、批 B 净 5 → **`availableQuantity` 合计 6**（包装单位换算前拆零加总）。
- **批次售价定价读路径**（`LOT_NUMBER_PRICE` / FIFO 首条批号）：仍按批号行净可用 > 0 取首条；占用扣减口径与上款相同（预占不按批号拆分，仅在批次遍历中顺序消耗）。
- 扣减结果写入 `inventoryList[].quantity`（拆零）；行级 **`availableQuantity`** = 各批净可用拆零**算术加总**，再经 **`ProductDefinitionCatalogUnitBandsAssembler#convertDoctorStationCatalogAvailableQuantity`** 换算为**包装单位**展示（与 **`unitCode`** 一致），与 AS **BR-ORD-011** 之「还可开多少」及目录 **`unitCode`/`unitPrice`** 配套；签发相位之多发放拆分见 **§3.3 规则 H** / IF-DOR-15。

**E. 发药/发材路由配置（`loadMedLocationConfigs`）**

- 规则主数据为 **`ServiceLocationTemplate`（`DM-CUS-006`）**：按**接诊科室**（与 **`MedicationRequest`/`DeviceRequest` 之 `org_id` 开立语义**、`Encounter` 上下文一致，见 AS **§1.7**）解析**候选**发药/发材位置集合（`locationId` 列表）；**药/材对称**消费。
- 机构上下文取自 Request **`organizationUnitId`**（非空则覆盖门诊快照 `organizationId`）或快照机构 id，**仅**供本规则与物理库存求交；**与** `ProductDefinitionPageQuery` **无字段传递关系**。
- 返回含机构位置、时段、`display_order`、`category_code` 等；用于：① **适用候选发药/发材库房 id 集合**（`listApplicableDistributionLocationIds`：calcScore≥0，与物理库存**求交**）；② 目录默认发点由 **`matchDefaultDistributionLocation`** 解析（**不**再取预减后首条正库存）。**批号**不在行级重复：仅 **`inventoryList[].lotNumber`**。**签发**时由发放子域在 req+开立科室+FIFO 下生成 **`MedicationDispense`/`DeviceDispense`** 之实际 `location_id`（与 **`DM-MED-002`** 示例一致）。

**F. 定价（`ChargeItemDefinition` 批量装载 + 价型分流）**

- 装配器 **`#loadPricingGrouped`**：按当前页各行的 `product_table_name` + `product_definition_id` 构建复合键，调用 `ChargeItemDefinitionQueryService` **批量**拉取该实例下**全量**定价行集合。
- **诊疗**（`product_table_name = wkf_activity_definition`）：仅在 **`PriceType.SALE`**（零售价，持久化码 `SALE`）价型内按装配器既有优先级择优（`#selectBestSalePriceLine` / `#selectCatalogPriceLine`）→ 写 **`unitPrice`** / **`chargeItemDefinitionId`**；**无** `inventoryList`。
- **药品/耗材**：仅在 **`PriceType.LOT_NUMBER_PRICE`** 内解析；将批号售价行按规范化批号建索引后，对 **`inventoryList` 每一行**用 **`lotNumber`** 匹配 **`lot_no`** → 写该行 **`price`** / **`chargeItemDefinitionId`**；目录汇总 **`unitPrice`** / **`chargeItemDefinitionId`** 取**首条**库存明细命中之定价行（与 **`defaultDispenseLocationId`** 首条正库存次序一致）。**无库存**时：在 **`LOT_NUMBER_PRICE`** 内按 **`#selectCatalogPriceLine`**（或等价）择优写 **`unitPrice`**，行明细无 **`price`**。
- **不下发** `priceListJson`。
- **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`**：**IF-DOR-14 暂存**药耗费用草稿定价（`clientResolvedDispenseLocationId` + `methodCode`；非空库位时 **不**二次 match 取药科室模板）；诊疗仍 **`SALE`** 择优。
- **`ClinicalCatalogPriceResolver#selectExecutionPriceLineForChargeDraft`**：**IF-DOR-15/37 签发分段**与护士执行挂费真源——药耗按 FIFO/执行 **`lotNumber`** 匹配 **`LOT_NUMBER_PRICE`**；批号非空未命中返回 null（调用方抛业务异常）；**禁止**跨批号 priority 回落。
- **`ClinicalCatalogPriceResolver#selectFirstPriceLineForChargeDraft`**：**仅**诊疗 **`SALE`**，或药耗**无库存/无批号**兜底；**禁止**用于已有 FIFO 批号的签发落账。
- **IF-DOR-11 仅目录展示**：无命中价型时 `unitPrice` / 行级 **`price`** 可为 `null`（不阻断分页检索），`chargeItemDefinitionId` 可回落QueryService已带 id（若有）。
- **IF-DOR-14 暂存/签发**（与上表「域查询」行一致）：定义主键已给出时**必须**在分流下命中适用定价行，否则应用层抛 **`CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT`**，**不得**以空费用草稿落库。

**G. 关键字排序**

- 以 **`ProductDefinitionBatchLoader`** 分页 SQL 与 `ProductDefinitionPageQuery`（含 `keyword` / `sortMode`）为准；**当前** **`DoctorStationAdviceCatalogAssembler` 不再**提供 `sortCatalogRowsByKeyword` 二次排序。

**H. 签发 FIFO 分段单位（`DoctorStationAdviceInventoryUnitQuantityHelper#allocateFifoSegmentsInDoctorBusinessUnit`）**

- **真源换算**：业务单位 ↔ 拆零委托域 **`MedicationProductQuantityToSplitUnitConverter`**（与 **`OutpatientPharmacyDispenseAppService` / `InpatientPharmacyDispenseAppService`** 扣减库存同口径）；**禁止**在 FIFO 内用 `拆零量 ÷ ratio` 回写发放段导致 **0.6 盒** 等小数（**AS `BR-ORD-021`**）。
- **占用叠加（药品）**：`DoctorStationAdviceFifoAllocationAssembler#buildMedicationOccupancy` = 规则 **C** 目录占用（`DRAFT`/`PREPARATION`）**+** `listMedicationDispensesForInpatientSummarizedOccupancy`（住院 `SUMMARIZED`）；**目录 IF-DOR-11「还可开」仍只走规则 C**，不读汇总额外段。
- **内部比对**：批次可用量、候选库房全局占用、剩余待分配均在**拆零单位**上计算；排序键：**模板库位序 → 效期 → 库存主键**（与 **`DoctorStationAdviceFifoAllocationAssembler#sortInventoryRowsForTemplateFifo`** 一致）。
- **分段回写**：每段 **`DoctorStationAdviceFifoSegmentDraft#quantity`** 为**医嘱开立单位**下的数量；**`ReplaceFifo*DispenseDraftsCommand#unitCode`** 取自 **`MedicationRequest#unitCode` / `DeviceRequest#unitCode`**（**不得改单位**）。
- **拆零开立**（开立单位相对拆零倍数 **`ratioToSplit = 1`**）：允许跨批次按拆零量部分切分（例：开立 10 片，A 批 6 片 + B 批 4 片 → 两段 quantity 分别为 6、4，单位仍为片）。
- **非拆零开立**（**`ratioToSplit > 1`**，如盒/采购/剂量单位）：每段 **`quantity` 须为整数个开立单位**；单批次预减后净可用拆零量 **`floor(netSplit / ratioToSplit) ≤ 0`** 时**跳过该批次**继续 FIFO（例：开立 3 盒、1 盒=10 片，A 批 25 片→2 盒，B 批 6 片→跳过，C 批 10 片→1 盒）；**禁止** 0.6 盒 + 0.4 盒凑 1 盒。
- **规模门控**：分配后 **`remainingSplitQuantity > 0`** → 业务异常「候选库位可用库存不足以覆盖医嘱数量，拒绝签发」（与 **`DoctorStationAdviceConstants`** / 实现对齐）。


#### 关键校验

- **定价**：由 **`ClinicalCatalogPriceResolver** 统一解析（**BR-ORD-001**）；**不再**使用租户定价来源或 `pricingSourceCode` 门控。
- 目录启用/发布过滤与QueryService「仅可售」口径一致。
- `pricingFlag` 为 Integer；比较语义与 §3.3 规则 A 一致。

#### 失败码口径表


| 失败码 | 场景 |
| --- | --- |
| EX-ORD-101 | （**废止**） |
| EX-ORD-201 | `ProductDefinition` 分页、库存/待发 SQL、定价装载失败 |


#### 领域编排序列（严格顺序；与 §2.5 Mapper/装配器白名单一致）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `EncounterContextReader#mustGetOutpatientContextByEncounterId` + `DoctorStationAdviceMapper#validatePatientEncounter` | Request + 门诊快照 | 快照门控；请求 `patientId` 与快照不一致时目录返回空行（Assembler 内早退） |
| ② | Pre | `DoctorStationAdviceMapper#toProductDefinitionPageQuery` | `PageDoctorStationAdviceCatalogRequest` | 组装 `ProductDefinitionPageQuery`（**不含**机构单元字段；目录 SQL 不按QueryService `org_id` 过滤）；**恒写** `excludeCategoryCodes=["52"]`（**BR-ORD-032**） |
| ③ | Pre | `ProductDefinitionCatalogPageLoader#pageProductDefinition` | ② | QueryService 分页，得 `Page<ProductDefinitionDisplayDto>` |
| ④ | Asm | `DoctorStationAdviceCatalogAssembler#assemblePageContent` | Request + ③ + 快照 | `#baseRow` + 药/材库存/占用/预减 + `#loadPricingGrouped` + 价型分流（**诊疗 `SALE`** / **药耗 `LOT_NUMBER_PRICE`+批号对齐**）→ **`unitBands`/`unitPrice`/`inventoryList`**；**仅读路径** |
| ⑤ | Asm | `DoctorStationAdviceAppService` 内 `new PageDoctorStationAdviceCatalogResponse` + `setRows/setTotalCount/...` | ④ | 响应封装（**无** Mapper `toPageDoctorStationAdviceCatalogResponse`） |


#### Mapper 接口方法签名（IF-DOR-11 相关摘录）

```java
void validatePatientEncounter(Long patientId, Long encounterPatientId);
ProductDefinitionPageQuery toProductDefinitionPageQuery(PageDoctorStationAdviceCatalogRequest request);
```


---

### 3.4 IF-DOR-12 `getDoctorStationAdviceOrderBindInfo`（查询医嘱绑定信息）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#getDoctorStationAdviceOrderBindInfo` |
| **HTTP** | `POST /api/app/doctorStationAdvice/getDoctorStationAdviceOrderBindInfo` |


#### Request 字段表（`GetDoctorStationAdviceOrderBindInfoRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| bindType | String | 是 | `USAGE`（用法来源） / `ACTIVITY`（诊疗来源）；与 `ServiceDeviceTemplateTypeCode` 及模板 `type_code` 同码；**不限制目标类型** |
| itemNo | String | 是 | **用法**：`method_code` 术语 code；**诊疗**：活动定义主键字符串 |
| itemId | Long | 否 | **仅诊疗绑定**可选，与数字形态 `itemNo` 二选一 |
| organizationUnitId | Long | 否 | 绑定范围过滤 |


#### Response 字段表（`GetDoctorStationAdviceOrderBindInfoResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| bindRows | List\<DoctorStationAdviceOrderBindRowDto\> | 绑定明细 |


#### DTO 字段表（`DoctorStationAdviceOrderBindRowDto`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| bindType | String | 绑定类型回显 |
| sourceItemId | Long | 源定义主键；**用法绑定为空**（来源键为 method_code），诊疗为活动定义主键 |
| sourceItemName | String | 源展示名 |
| targetItemId | Long | 目标定义主键 |
| targetItemName | String | 目标展示名 |
| quantityNumerator | BigDecimal | 数量分子 |
| quantityDenominator | BigDecimal | 数量分母 |
| unitCode | String | 单位编码 |
| unitName | String | 单位展示名（**BR-ORD-022**；绑定目标产品QueryService回落） |
| priority | Integer | 展示/应用顺序 |
| activeFlag | Boolean | 是否有效 |


#### 失败码口径表


| 失败码 | 场景 |
| --- | --- |
| EX-ORD-201 | 绑定查询失败 |

#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `DoctorStationAdviceMapper#resolveOrderBindSourceItemKey(bindType, itemNo, itemId)` | Request | 用法：`itemNo`=method_code；诊疗：主键字符串；无法解析 → `SERVICE_REQUEST_INVALID_ARGUMENT` |
| ② | Pre | `DoctorStationAdviceOrderBindQueryService#listBindingsBySourceItemKey(bindType, sourceItemKey, organizationUnitId)` | ① | `ServiceDeviceTemplateQueryService#listBindingsByRangeTypeAndSource`（`rangeCode=OUTPATIENT` + `USAGE`/`ACTIVITY`）；展示名批量装载；上限 `ORDER_BIND_LIST_MAX_ROWS` |
| ③ | Asm | `DoctorStationAdviceAppService` 构造 `GetDoctorStationAdviceOrderBindInfoResponse` 并 `setBindRows` | ② | 无 Mapper 装配方法；**仅读路径** |


#### Mapper 接口方法签名

```java
String resolveOrderBindSourceItemKey(String bindType, String itemNo, Long itemId);
```


---

### 3.4.1 IF-DOR-23 `resolveDoctorStationAdviceDraftBinds`（暂存前绑定解析）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#resolveDoctorStationAdviceDraftBinds` |
| **HTTP** | `POST /api/app/doctorStationAdvice/resolveDoctorStationAdviceDraftBinds` |


#### Request 字段表（`ResolveDoctorStationAdviceDraftBindsRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识，门控与 IF-DOR-14 一致 |
| patientId | Long | 是 | 患者标识，须与就诊一致 |
| lines | List\<DoctorStationAdviceDraftLineRequest\> | 是 | 与 IF-DOR-14 同构暂存变更行；签发前由前端传入本次待签触发行（组套套用行应剔除，**BR-ORD-038**） |
| skipAutoBind | Boolean | 否 | `true` 时经 Gate 后返回空 `bindItems`，不再查用法/诊疗/合管绑定（组套套用签发，**BR-ORD-038**）；缺省/`false` 保持解析 |


#### Response 字段表（`ResolveDoctorStationAdviceDraftBindsResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| bindItems | List\<DoctorStationAdviceDraftBindItemDto\> | 绑定明细；**服务端不去重、不级联**；前端展示确认后再 IF-DOR-14 |


#### DTO 字段表（`DoctorStationAdviceDraftBindItemDto`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| bindType | String | `USAGE` / `ACTIVITY` |
| sourceItemId | Long | 源定义主键；用法绑定为空 |
| sourceItemName | String | 源展示名 |
| targetItemId | Long | 绑定目标定义主键 |
| targetItemName | String | 绑定目标展示名 |
| targetProductTableName | String | `wkf_activity_definition` / `adm_device_definition` |
| quantityNumerator | BigDecimal | 模板默认数量（**不乘**主医嘱数量，**AS `BR-ORD-029`**） |
| quantityDenominator | BigDecimal | 固定 1 |
| unitCode | String | 单位编码 |
| unitName | String | 单位展示名（**BR-ORD-022**） |
| priority | Integer | 模板顺序 |
| activeFlag | Boolean | 模板是否启用 |
| triggerLineIndex | Integer | 触发变更行下标（与 `lines` 同序，从 0 起） |
| triggerLineOp | String | 触发行 `lineOp`（CREATE / UPDATE） |
| triggerProductTableName | String | 触发主医嘱产品表 |
| triggerDefinitionId | Long | 触发主医嘱定义主键 |
| triggerRequestId | Long | 触发主医嘱请求主键；CREATE 暂存前可为空 |
| basedOnTable | String | 派生行 `based_on_table`：`med_medication_request` / `wkf_service_request` |
| basedOnRequestId | Long | 派生行 `based_on_id`；与 `triggerRequestId` 同值 |


#### 失败码口径表


| 失败码 | 场景 |
| --- | --- |
| EX-ORD-201 | 就诊/患者门控失败或绑定查询失败 |


#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `EncounterContextReader#mustGetOutpatientContextByEncounterId` + `DoctorStationAdviceMapper#validatePatientEncounter` | Request | 与 IF-DOR-14 同门控 |
| ② | Pre | `DoctorStationAdviceOrderBindQueryService#listDraftBindItems(lines, organizationId)` | ① | 跳过 `DELETE`；药品行按 `methodCode`→`USAGE`；诊疗行按 `activityDefinitionId`→`ACTIVITY`；**仅直接模板**，**禁止**用法→诊疗→耗材级联 |
| ③ | Asm | 构造 `ResolveDoctorStationAdviceDraftBindsResponse` 并 `setBindItems` | ② | **纯读**；去重与派生行落库由前端 IF-DOR-14 前处理 |


#### Mapper 接口方法签名

无新增 Mapper 方法。

---

### 3.5 IF-DOR-13（废止）与费用性质下拉归属

**IF-DOR-13** `getEncounterAdviceDefaultContract` **已废止**：工程**无**对应 HTTP，本模块**无** `DoctorStationAdviceContractResolver`、`GetEncounterAdviceDefaultContractRequest` / `GetEncounterAdviceDefaultContractResponse`。

**费用性质（合同）下拉**由医嘱 **`initDoctorStationAdvice`** 经 **`ContractFeeNatureOptionLoader#loadOptionsForEncounter`** 按就诊**已开立账户**装配（展示名为账户 `contract_no` 对应合同名；`value`=合同主键）：双账户时医保户排首位（默认），单自费户仅一项（前端无下拉只读展示）。列表页 **IF-DOR-01** 的 `feeNatureOptions` 可复用同口径；行内 **`contractDisplay`** 供患者卡片展示。

**IF-DOR-14** 等写路径中行级 **`contractId`** 仍为业务主键赋值，与上述下拉数据来源解耦。

---

### 3.5.1 IF-DOR-18 `initDoctorStationAdvice`（医嘱开立页初始化）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#initDoctorStationAdvice` |
| **HTTP** | `POST /api/app/doctorStationAdvice/initDoctorStationAdvice` |


#### Request 字段表（`InitDoctorStationAdviceRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识；门控同 IF-DOR-10 |
| patientId | Long | 是 | 患者标识；须与门诊QueryService `patientId` 一致 |


#### Response 字段表（`InitDoctorStationAdviceResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| medicationRateOptions | List\<`OptionDto`\> | 用药频次下拉 |
| medicationRouteOptions | List\<`OptionDto`\> | 给药途径（用法）下拉 |
| tcmMethodCodeOptions | List\<`OptionDto`\> | 中药用法下拉（术语 `tcm_method_code`） |
| bodyStructureOptions | List\<`OptionDto`\> | 身体部位树形下拉（`getBodyStructureTree` + `BodyStructureOptionAssembler.toActiveOptionTree`，仅 **ACTIVE**；含 `children`；节点透出 **`pyStr`/`wbStr`**，供申请单检查行前端模糊过滤） |
| specimenDefinitionOptions | List\<`OptionDto`\> | 样本定义下拉（`SpecimenDefinitionQueryService#searchByKeyWord`，默认 **ACTIVE**，上限 500；项透出 **`pyStr`/`wbStr`**，供申请单检验行前端模糊过滤） |


#### 失败码口径表


| 失败码 | 场景 |
| --- | --- |
| EX-ORD-201 | 就诊快照缺失、患者不一致；或术语编码系统未配置导致下拉为空（实现以 `CodeSystemQueryService` 为准，通常返回空列表不抛错） |


#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `EncounterContextReader#mustGetOutpatientContextByEncounterId` + `DoctorStationAdviceMapper#validatePatientEncounter` | Request | 同 IF-DOR-10 |
| ② | Pre | `CodeSystemQueryService#getOptionListByCodeSystemName(RATE_CODE)` | — | 频次 `OptionDto` 列表 |
| ③ | Pre | `CodeSystemQueryService#getOptionListByCodeSystemName(METHOD_CODE)` | — | 西医给药途径 |
| ③b | Pre | `CodeSystemQueryService#getOptionListByCodeSystemName(TCM_METHOD_CODE)` | — | 中药用法（与 `method_code` 分离） |
| ④ | Pre | `BodyStructureQueryService#getBodyStructureTree` → `BodyStructureOptionAssembler#toActiveOptionTree` | — | 部位**树形**下拉（仅 ACTIVE，含 `children` 与 **`pyStr`/`wbStr`**） |
| ⑤ | Pre | `SpecimenDefinitionQueryService#searchByKeyWord` → `OptionDtoAssembler#toSearchableOptionsWithSortFallback` | — | 样本下拉（含 **`pyStr`/`wbStr`**） |
| ⑥ | Asm | 构造 `InitDoctorStationAdviceResponse` | ②～⑤ | **仅读路径** |


#### Mapper 接口方法签名

> 本 IF **无**新增 Mapper 方法；请求体字段与 IF-DOR-10 门控字段一致（`encounterId`、`patientId`），可独立 `InitDoctorStationAdviceRequest` 类型承载以便 OpenAPI 生成。


---

### 3.5.2 IF-DOR-19 `listActivityExecutionLocations`（按诊疗定义查询可执行科室规则）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#listActivityExecutionLocations` |
| **HTTP** | `POST /api/app/doctorStationAdvice/listActivityExecutionLocations` |


#### Request 字段表（`ListActivityExecutionLocationsRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识；门控同 IF-DOR-10 |
| patientId | Long | 是 | 患者标识；须与门诊QueryService一致 |
| activityDefinitionId | Long | 是 | 诊疗活动定义主键 |


#### Response 字段表（`ListActivityExecutionLocationsResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| rules | List\<`ActivityExecutionLocationRuleRowDto`\> | 规则行列表（空表示无配置） |


#### `ActivityExecutionLocationRuleRowDto`（独立契约类）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 规则主键 |
| activityDefinitionId | Long | 可为 null | 诊疗活动定义；**大类默认规则行为 null** |
| chargeItemDefinitionId | Long | 关联收费项定义 |
| activityCategoryCode | String | 诊疗大类编码 |
| executionOrgId / executionOrgName | Long / String | 执行科室组织 id / 展示名 |
| executionLocationId / executionLocationName | Long / String | 执行地点 id / 展示名 |
| startTime / endTime | LocalTime | 日内生效时段 |
| priority / displayOrder | Integer | 优先级与展示序 |
| remark | String | 备注 |


#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `EncounterContextReader#mustGetOutpatientContextByEncounterId` + `DoctorStationAdviceMapper#validatePatientEncounter` | Request | 同 IF-DOR-10 |
| ② | Pre | `ProductDefinitionBatchLoader#loadAsMap`（`wkf_activity_definition`） | `activityDefinitionId` | 解析诊疗大类 `categoryCode` 与业务类型 `activityTypeCode` |
| ③ | Pre | `ActivityExecutionLocationQueryService#listExecutionRulesForActivityResolution` | `activityDefinitionId` + ② 的 `categoryCode` + `activityTypeCode` | 单品 + 匹配类型层 + 大类默认规则列表 |
| ④ | Pre | `OrganizationQueryService#mapNamesByIds` + `LocationQueryService#mapNamesByIds` | 规则中出现的组织 id、地点 id 集合 | 批量解析展示名 |
| ⑤ | Asm | 装配 `ListActivityExecutionLocationsResponse` / `ActivityExecutionLocationRuleRowDto` | ③④ | **仅读路径** |


#### 前端执行科室下拉（`BR-ORD-024`，门诊/住院同构）

| 步骤 | 触发 | 行为 |
| --- | --- | --- |
| ① 选品预填 | IF-DOR-11 目录选中 `wkf_activity_definition` | 写入 `performOrgId`/`performOrgName` **仅当**目录行携带 `defaultPerformOrgId`/`defaultPerformOrgName`；**禁止**用当前登录科室或「当前登录机构」占位 |
| ② 拉取配置 | 编辑区选定诊疗且 `encounterId`/`patientId`/`activityDefinitionId` 齐备 | `POST …/listActivityExecutionLocations`（门诊 `doctorStationAdvice`；住院 `inpatientDoctorStationAdvice`） |
| ③ 配置命中 | Response `rules` 非空 | 按 `executionOrgId` 去重构建下拉；仅一条规则时可自动选中；多条且当前值不在列表内时可预选首条 |
| ④ 无配置回落 | Response `rules` 为空或接口失败 | 调用 **`POST /api/app/executeLocation/init`**，将 `executionOrgOptions` 机构树**扁平化**为全院科室下拉（与基础数据「执行科室」维护页同源）；**不**强制预选 |
| ⑤ 回显补全 | 已持久化 `performOrgId` 不在当前下拉集合 | 将当前 id/name **补入**下拉首项，避免历史医嘱编辑丢选项 |

> 实现落点（前端）：`views/doctor-station/advice-shared/perform-org-options.service.ts`；门诊/住院 **`AdviceDeviceServiceFormPanel`**、**`AdviceEditCard`** 诊疗选品分支。


---

### 3.5.3 IF-DOR-20 `pageDoctorStationAdvice`（患者全类型医嘱分页查询）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#pageDoctorStationAdvice` |
| **HTTP** | `POST /api/app/doctorStationAdvice/pageDoctorStationAdvice` |
| **读口径真源** | 与 **IF-DOR-10** 同构：药品 + 诊疗 + 耗材统一列表；行 DTO 字段表见 **§3.2** `DoctorStationAdviceRowDto` |
| **与 IF-DOR-10 差异** | ① 分页响应（`PagedResult`）；② **`adviceScopeTabCode`** 单接口分 Tab（`CURRENT_ADVICE` / `HISTORY`）；③ 历史 Tab 按 `patientId` 跨就诊聚合并排除当前 `encounterId`；④ 可选 **`orderingDepartmentId`** / **`requestStatusEnumIn`** 过滤；⑤ **是否按处方形式展示由前端处理** |


#### Request 字段表（`PageDoctorStationAdviceRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 当前工作站就诊主键；当前医嘱 Tab 查询锚点；历史 Tab 排除锚点 |
| patientId | Long | 是 | 患者主键；须与门诊快照一致；历史 Tab 查询锚点 |
| adviceScopeTabCode | String | 否 | `CURRENT_ADVICE`（默认）当前医嘱；`HISTORY` 患者历史医嘱（排除当前就诊） |
| orderingDepartmentId | Long | 否 | 开单科室组织主键（`adm_organization.id`）；药品/诊疗行按请求 `org_id` 过滤；耗材行无组织字段时不参与科室收窄；空表示不按科室过滤 |
| requestStatusEnumIn | List\<String\> | 否 | `RequestStatus` code 集合；空表示不按状态过滤 |
| pageNo | Integer | 是 | 页码（1-based） |
| pageSize | Integer | 是 | 页大小 |


#### Response 字段表（`PageDoctorStationAdviceResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| content | List\<`DoctorStationAdviceRowDto`\> | 全类型医嘱统一列表行（与 IF-DOR-10 同构；是否按处方展示由前端处理） |
| totalCount | Long | 总条数 |
| pageNo | Integer | 当前页（1-based） |
| pageSize | Integer | 页大小 |


**失败码口径表**


| 业务码 | 说明 |
| --- | --- |
| EX-ORD-201 | 就诊不存在（`ENCOUNTER_NOT_FOUND`） |
| EX-ORD-101 | 患者与就诊不一致（`ENCOUNTER_INVALID_ARGUMENT`） |


#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `EncounterContextReader#mustGetOutpatientContextByEncounterId` + `DoctorStationAdviceMapper#validatePatientEncounter` | Request | 同 IF-DOR-10 |
| ②a | Pre | `DoctorStationAdviceListAssembler#assembleAdviceQueryRows` | `encounterId` + 可选过滤 | **当前医嘱** Tab：仅当前就诊全类型医嘱 |
| ②b | Pre | `MedicationRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` + `ServiceRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` + `DeviceRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` → `DoctorStationAdviceListAssembler#assemblePatientHistoryAdviceRows` | `patientId` + `excludeEncounterId` + 可选过滤 | **历史医嘱** Tab：患者跨就诊全类型医嘱，按开立时间倒序 |
| ③ | Asm | `PagedResultBuilder#buildFromFullList` | ② 全量行 + `pageNo`/`pageSize` | 内存分页装配 |


---

### 3.5.4 IF-DOR-21 `saveDoctorStationServiceApplyForm`（保存申请单·整张）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#saveDoctorStationServiceApplyForm` |
| **HTTP** | `POST /api/app/doctorStationAdvice/saveDoctorStationServiceApplyForm` |
| **事务** | `@Transactional(rollbackFor = Exception.class)`（目标态） |


#### Request 字段表（`SaveDoctorStationServiceApplyFormRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识 |
| categoryEnum | String | 是 | 申请单类型：`ActivityDefCategory` code（检查 `TEST`/23、检验 `PROOF`/22、治疗 `TREATMENT`/21） |
| orgId | Long | 是 | **整单**执行科室（写入全部 `ServiceRequest.org_id`） |
| executionCount | Integer | 否 | **整单**执行次数（写入全部 `ServiceRequest.execution_count`；空或非法编排层回落 1） |
| encounterDiagnosisIds | String | 否 | **整单**绑定就诊诊断 ids 有序串（写入全部 `ServiceRequest.encounter_diagnosis_ids`；与 IF-DOR-14 诊疗草稿同源；**主诊 id 第一**；前端整单多选后传入） |
| previousPrescriptionNo | String | 条件 | **编辑**时传入待替换的旧 **`AR`** 申请单号；**保存成功后该号下 DRAFT 行全部软删** |
| lines | List\<`ServiceApplyFormLineRequest`\> | 是 | 明细行，至少 1 行 |


#### 嵌套：`ServiceApplyFormLineRequest`


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| activityDefinitionId | Long | 是 | 诊疗定义主键 |
| quantity | BigDecimal | 是 | **账单计价总量**（前端 **BR-ORD-036**：单次执行数量 × 整单 `executionCount`） |
| unitCode | String | 是 | 单位 |
| remark | String | 否 | **纯备注**，落库 **`wkf_service_request.remark`** |
| detailItems | List\<`ServiceApplyFormDetailItemRequest`\> | 否 | 检查/治疗：可选 `dia_body_structure`；检验：可选 `adm_specimen_definition`；空表示本行无部位/样本明细（**BR-REQ-005**） |


#### 嵌套：`ServiceApplyFormDetailItemRequest`


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| targetTable | String | 是 | `TableNameConstants#DIA_BODY_STRUCTURE` 或 `ADM_SPECIMEN_DEFINITION` |
| targetId | Long | 是 | 目标主键 |
| remark | String | 否 | 与该目标同行的补充说明（**非**纯备注行） |


#### Response 字段表（`SaveDoctorStationServiceApplyFormResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| prescriptionNo | String | **新**申请单号（`AR` + 按日序号） |
| requestIds | List\<Long\> | 本单新建 `ServiceRequest` 主键列表（顺序与 `lines` 一致） |


#### 失败码口径表


| 业务码 | 说明 |
| --- | --- |
| EX-REQ-101 | 旧号不存在或含非 DRAFT 行 |
| EX-REQ-102 | 类别/Detail/行校验失败 |
| EX-ORD-101 | 定价缺失 |
| EX-ORD-207 | 持久化失败 |


#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| ① | Gate | `DoctorStationAdviceAppService`：就诊/患者门控 + 默认账户 | 同 IF-DOR-14 |
| ② | Pre | `ServiceApplyFormAssembler#toLineSpecs` | HTTP 行 → `ServiceApplyFormLineSpec` |
| ③ | Cmd | `ServiceApplyFormOrchestrationSupport#saveApplyForm`（内含：删旧 → `getSeqByDay(AR)` → 逐行 `createServiceRequestDraft`（**整单** `orgId`/`encounterDiagnosisIds` 透传）+ **`ChargeDraftCreator`** 挂费） | **编辑删旧取新号**（**BR-REQ-001/002**） |
| ④ | Asm | 返回 `prescriptionNo` + `requestIds` | — |


---

### 3.5.5 IF-DOR-22 `listDoctorStationServiceApplyForms`（申请单列表）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#listDoctorStationServiceApplyForms` |
| **HTTP** | `POST /api/app/doctorStationAdvice/listDoctorStationServiceApplyForms` |


#### Request 字段表（`ListDoctorStationServiceApplyFormsRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识 |
| categoryEnum | String | 是 | 检查/检验/治疗 category code |


#### Response 字段表（`ListDoctorStationServiceApplyFormsResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| forms | List\<`ServiceApplyFormHeaderDto`\> | 按 `prescriptionNo` 分组 |


#### DTO：`ServiceApplyFormHeaderDto`（摘要）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| prescriptionNo | String | 申请单号（`AR` 前缀） |
| categoryEnum | String | 类别 code |
| orgId | Long | 执行科室 |
| orgName | String | 执行科室展示名 |
| reqOpenedTime | String | 首行开立时间（`req_opened_time`） |
| statusSummary | String | 组内状态摘要（如全草稿/含已签发） |
| lines | List\<`ServiceApplyFormLineDto`\> | 明细摘要（含 `requestId`、诊疗名、数量、`remark`、`details[]`） |


#### 领域编排序列


| 序号 | 阶段 | 说明 |
| --- | --- | --- |
| ① | Gate | `DoctorStationAdviceAppService`：同 IF-DOR-10 |
| ② | Pre + Asm | `ServiceApplyFormOrchestrationSupport#listApplyForms` → `ServiceApplyFormAssembler#toHeaderDtos`；内部调用 **`mapDetailListModelsByServiceRequestIds`**（**DM-WKF-004 §6.2.24**） | 按 `encounterId` + **`AR` 前缀** + `category_enum` 过滤、分组并装配展示名 |

---

### 3.6 IF-DOR-14 `saveDoctorStationAdviceDraftBatch`（暂存医嘱变更批次）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#saveDoctorStationAdviceDraftBatch` |
| **HTTP** | `POST /api/app/doctorStationAdvice/saveDoctorStationAdviceDraftBatch` |


#### Request 字段表（`SaveDoctorStationAdviceDraftBatchRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识 |
| lines | List\<DoctorStationAdviceDraftLineRequest\> | 是 | 批次行；不得为空 |


#### DTO 字段表（`DoctorStationAdviceDraftLineRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| lineOp | String | 是 | `CREATE` / `UPDATE` / `DELETE` |
| productTableName | String | 是 | 与 IF-DOR-11 一致的三张**定义表**物理名：`med_medication_definition` / `wkf_activity_definition` / `adm_device_definition`（实现层与 `TableNameConstants` 忽略大小写比对） |
| requestId | Long | 条件 | `UPDATE`/`DELETE` 必填 |
| sortNumber | Integer | 否 | **医生站主表展示排序号**（**AS `BR-ORD-018`**）：`CREATE`/`UPDATE` 且本字段**非空**时写入对应请求聚合；**空或省略**表示创建时不强制写入、更新时不改写既有排序号（避免误清空） |
| basedOnTable | String | 条件 | **绑定派生行**（**AS `BR-ORD-031`**）：`based_on_table`；与 `basedOnId` 成对；手工开立可空 |
| basedOnId | Long | 条件 | **绑定派生行**来源锚主键；成组西药可任取组内一条 MR id；非成组即触发行 requestId |
| packageId | Long | 否 | **绑定派生耗材**组套标识；成组时与主药 `package_id`/`group_id` 对齐；诊疗行忽略（无列） |
| medicationPayload | SaveMedicationAdviceDraftLinePayload | 条件 | **`productTableName`** 为药品定义表且 `lineOp` 非 `DELETE` 时必填 |
| servicePayload | SaveServiceAdviceDraftLinePayload | 条件 | **`productTableName`** 为诊疗定义表且 `lineOp` 非 `DELETE` 时必填 |
| devicePayload | SaveDeviceAdviceDraftLinePayload | 条件 | **`productTableName`** 为耗材定义表且 `lineOp` 非 `DELETE` 时必填 |


#### 嵌套：`SaveMedicationAdviceDraftLinePayload`


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| medicationDefinitionId | Long | 是 | 药品定义 |
| quantity | BigDecimal | 是 | 数量 |
| unitCode | String | 是 | 单位 |
| methodCode | String | 否 | 用法 |
| rateCode | String | 否 | 频次 |
| groupId | Long | 否 | 组号；落库时 **`package_id` 与 `group_id` 同源**（**AS `BR-ORD-031`**） |
| dose | BigDecimal | 否 | 单次用量；映射 `MedicationRequest.dose`，与 **§3.2** `dose` 列表回显一致 |
| doseUnitCode | String | 否 | 剂量单位编码；映射 `dose_unit_code` |
| dispensePerDuration | Integer | 否 | 用药天数；映射 `dispense_per_duration` |
| skinTestFlag | Boolean | 否 | 是否皮试；映射 `skin_test_flag` |
| dispenseLocationId | Long | 否 | 目录 IF-DOR-11 已解析发药库位；IF-DOR-14 暂存定价 FIFO 用，**不落库**到 `MedicationRequest` |
| encounterDiagnosisIds | String | 否 | **绑定就诊诊断 ids 有序串**（落库 `med_medication_request.encounter_diagnosis_ids`；**主诊 id 第一**；与 **§3.2** `encounterDiagnosisIds` / `conditionDefinitionName` 一致） |
| contractId | Long | 否 | 费用性质覆盖 |

> **库房语义（门诊）**：`dispenseLocationId` **不得**被解释为「已在 `MedicationRequest` 上持久化最终发放药房」的权威事实；**实际发放库房**以 **`MedicationDispense.location_id`**（及签发 FIFO 拆段）为准。该字段由 **IF-DOR-11** 目录下拉/组套套用/历史行 hydration 解析后回传；**IF-DOR-14** 写路径经 **`resolveDraftChargePriceLine`** 在该库位取 FIFO 批号定价，**不再**二次 match 取药科室模板。

#### 嵌套：`SaveServiceAdviceDraftLinePayload`


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| activityDefinitionId | Long | 是 | 诊疗定义 |
| quantity | BigDecimal | 条件 | **CREATE**：经 **`#requireQuantity`**（显式载荷；**禁止**回落 `executionCount`；否则 **`MSG_SERVICE_QUANTITY_REQUIRED`**）；**UPDATE**：**`#resolveQuantityForModify`**（可空=不改） |
| unitCode | String | 条件 | **CREATE**：经 **`#requireUnitCode`**（显式 → 子 JSON → **`packageUnitCode`**；否则 **`MSG_SERVICE_UNIT_CODE_REQUIRED`**）；**UPDATE**：**`#resolveUnitCodeForModify`** |
| executionCount | Integer | 条件 | **CREATE**：经 **`#requireExecutionCount`**（临嘱必填 **>0**；门诊恒为临嘱）；**UPDATE**：**`#resolveExecutionCountForModify`** |
| performOrgId | Long | 否 | 执行科室 |
| childOverridesJson | String | 否 | 套餐子项覆盖 JSON（`AdviceExtensionJsonSupport#parseServiceChildOverrides`） |
| encounterDiagnosisIds | String | 否 | 绑定就诊诊断 ids 有序串（落库 `wkf_service_request.encounter_diagnosis_ids`，与药品同源语义；**主诊 id 第一**） |
| contractId | Long | 否 | 费用性质覆盖 |

> **诊疗与诊断绑定**：与药品一致，可选绑定多个本次就诊 **`adm_encounter_diagnosis.id`**（有序 ids 串 **`encounterDiagnosisIds`**，主诊第一）；经 Mapper → **`CreateServiceRequestDraftCommand` / `ModifyServiceRequestDraftCommand`** → **`ServiceRequestDomainService`** 落库。
>
> **诊疗数量/单位/执行次数**：`quantity`/`unitCode` 供**账单计价**；`executionCount` 落库 `execution_count` 供**门诊护士站**计划执行次数（**BR-NUR-010**）。HTTP 层 CREATE 经 **`requireQuantity`/`requireUnitCode`/`requireExecutionCount`**；UPDATE 未传则不覆盖。与住院 IF-IP-DOR-14 同 **`ServiceAdviceDraftCommandHelper`** 口径。
>
> **前端录入（BR-ORD-036）**：门诊编辑卡 / 申请单 / **组套套用确认弹窗** UI 录入 **单次执行数量** 与 **执行次数**（组套确认改数量仅改单次量，落库前须 × 预组装 `executionCount`）；提交前 **`quantity` = 单次 × 次数**；列表/编辑回显再按总量 ÷ 次数还原单次量。后端契约字段不变，报表仍认落库总量。


#### 嵌套：`SaveDeviceAdviceDraftLinePayload`


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| deviceDefinitionId | Long | 是 | 耗材定义 |
| quantity | BigDecimal | 是 | 数量 |
| unitCode | String | 是 | 单位 |
| performOrgId | Long | 否 | 执行科室 |
| dispenseLocationId | Long | 否 | 目录 IF-DOR-11 已解析发药库位；IF-DOR-14 暂存定价 FIFO 用 |
| encounterDiagnosisIds | String | 否 | 绑定就诊诊断 ids 有序串（落库 `wkf_device_request.encounter_diagnosis_ids`，与药品同源语义；**主诊 id 第一**） |

> **药/材对称**：耗材行**不**在请求上固化最终发放库房；与药品一致经 **`DeviceDispense`** 承载库房/批号事实；目录侧默认发材点仍走 IF-DOR-11 之 **`ServiceLocationTemplate`** 与库存预减口径（**AS `BR-ORD-017`**）；诊断绑定为 **`encounter_diagnosis_ids`** 有序 ids 串，与药品语义一致。

#### Response 字段表（`SaveDoctorStationAdviceDraftBatchResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 是否成功 |
| newMedicationRequestIds | List\<Long\> | 本次 **新建** 药品请求主键列表（供前端回写） |
| message | String | 提示 |


#### 关键校验

- BR-ORD-003：仅暂存路径做非诊疗库存校验（见步骤 ④）
- BR-ORD-004：删行前关联费用不得 `SETTLED`
- BR-ORD-015：写顺序 **药品 → 诊疗 → 耗材**（步骤 ⑤～⑦ 为块内顺序）
- **AS `BR-ORD-016`**：`createDraftChargeItemFromOrder` 写入的 **`ChargeItem`** 与 **一条** `MedicationDispense` / `DeviceDispense` **发放行** **1:1**（诊疗暂存锚请求表）；`serviceTable`+`serviceId` **锚发放物理表主键**（**`DM-ADM-011`** **`BR-CHG-001`**）；金额经 **`ProductDefinitionOrderChargePricingSupport#applyOrderChargePricing`**（**`quantity`/`quantityUnit`/`ProductDefinitionDisplayDto`/`ChargeItemDefinition`** 均必填语义），**拒绝**默认 1/「次」及前端传入金额作为权威落库
- **AS `BR-ORD-017`**：药品与耗材在本 IF 内**同构**处理（请求草稿 / 发放草稿 / 费用草稿链路对称）
- **AS `BR-ORD-018`**：`DoctorStationAdviceDraftLineRequest#sortNumber` 经 `DoctorStationAdviceMapper` 写入 **`CreateMedicationOrderDraftCommand` / `ChangeMedicationOrderDraftCommand`** 及诊疗、耗材侧**嵌套 ItemCommand**；各 `*DomainService` 草稿写路径**仅当入参排序号非空**时落库/更新，避免 **UPDATE** 误清空既有值
- **AS `BR-ORD-025`**：**禁止**前端在 `lines` 中手工提交「皮试检查」`servicePayload` 行；皮试联动由 **`DoctorStationSkinTestSyncOrchestrator`** 在药品 Cmd 完成后执行（见 **§3.6.1**）
- **药品扩展字段**：`dose`、`doseUnitCode`、`dispensePerDuration`、`skinTestFlag`、`encounterDiagnosisIds`、`groupId` 经 Mapper 写入 **`CreateMedicationOrderDraftCommand` / `ChangeMedicationOrderDraftCommand`**，由 **`MedicationRequestDomainService`** 在草稿创建/修改时落库；**编辑**路径变更写入 **`MedicationOrderDraftEditedEvent`** 变更摘要时与 **`FieldNameConstants`**（`DOSE`、`DOSE_UNIT_CODE`、`DISPENSE_PER_DURATION`、`SKIN_TEST_FLAG`、`ENCOUNTER_DIAGNOSIS_IDS`、`GROUP_ID` 等）对齐
- **诊疗/耗材扩展字段**：`servicePayload` / `devicePayload` 之 **`encounterDiagnosisIds`** 经 Mapper 写入 **`CreateServiceRequestDraftCommand` / `ModifyServiceRequestDraftCommand`**、**`CreateDeviceOrderDraftCommand` / `ChangeDeviceOrderDraftCommand`**，由 **`ServiceRequestDomainService`** / **`DeviceRequestDomainService`** 落库；编辑事件变更摘要含 **`ENCOUNTER_DIAGNOSIS_IDS`**（与 **`FieldNameConstants`** 对齐）

#### 失败码口径表


| 失败码 | 场景 |
| --- | --- |
| EX-ORD-101 | 定价缺失 |
| EX-ORD-102 | 删行遇已收费 |
| EX-ORD-104 | 库存不足 |
| EX-ORD-207 | 持久化失败 |

#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `DoctorStationAdviceMapper#validateSaveDoctorStationAdviceDraftBatchRequest(SaveDoctorStationAdviceDraftBatchRequest)` | Request | 批次非空、行字段组合合法 |
| ①b | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 未登录抛 `ENCOUNTER_INVALID_ARGUMENT`；作为后续所有 Command 之 `operatorId` |
| ② | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId)` | Request | 患者一致性 |
| ③ | Pre | `DoctorStationAdviceCatalogAssembler#resolvePricingSourceOrThrow(...)` | Request | 同 IF-DOR-11 |
| ④ | Pre | **若** 批次含 `MEDICATION`/`DEVICE` 的 `CREATE`/`UPDATE` → 对涉及行调用 `InventoryItemQueryService#findAvailableInventories` 汇总校验；**否则**跳至 ⑤ | lines | 失败 EX-ORD-104；**签发相位不调用本步骤**（本 IF 为暂存） |
| ⑤ | Cmd-药-删前释放 | **若** 存在「药品 UPDATE 且带旧发放」行 → 对解析出的 `dispenseIds` 调用 `MedicationDispenseDomainService#removeUndispensedDispense(RemoveUndispensedDispenseCommand)`；**否则**跳至 ⑥ | Mapper 解析结果 | 对齐 AS「先释放再校验」 |
| ⑥ | Cmd-材-删前释放 | **若** 存在「耗材 UPDATE 且带旧发放」行 → `DeviceDispenseDomainService#removeUndispensedDeviceDispense(RemoveUndispensedDeviceDispenseCommand)`；**否则**跳至 ⑦ | 同上 | 互斥：仅命中 UPDATE 药品/耗材分支 |
| ⑦ | Cmd-药 | 对 Mapper 输出的药品命令列表按行类型迭代：`createMedicationOrderDraft` / `changeMedicationOrderDraft` / `removeMedicationOrderDraft`；更新/删除前 **`voidUnbilledDraftChargeItemsByServiceAnchor`**；费用草稿经 `createDraftChargeItemFromOrder`（**`service_*` 锚请求表**，**`product_*` 写 `med_medication_definition` + 药品定义主键**；**`createChargeIfPossible`** 传 **`medicationPayload.quantity`/`unitCode` + `ProductDefinitionBatchLoader` 读模型** → **`ProductDefinitionOrderChargePricingsupport**；**不**调用 `generateDispenseFromTemporaryOrder`，**BR-ORD-020**） | `CreateMedicationOrderDraftCommand` 等 | **写库点** `med_medication_request` / `adm_charge_item`；**若** 无药品行 → 跳过整步 |
| ⑦a | **皮试联动** | **`DoctorStationSkinTestSyncOrchestrator#syncAfterMedicationDraftLine`**（**§3.6.1**）：药品 Cmd 完成后按 **`skinTestFlag`** 创建/维持/软删 companion 皮试检查 SR + 锚 SR 费用草稿 | ⑦ 输出的药品 id + payload | **与 ⑦ 同一 `@Transactional`** |
| ⑧ | Cmd-诊 | 对诊疗行迭代：`createServiceRequestDraft` / `modifyServiceRequestDraft` / `removeServiceRequestDraft` + 费用草稿命令（**`product_*` 写 `wkf_activity_definition` + 活动定义主键**；**`requireQuantity`/`requireUnitCode` + `productRow`** 同 **`ServiceAdviceDraftCommandsupport**） | 各类 Command | **写库点** `wkf_service_request`；套餐子项展开在 `modify`/`create` 内由领域服务解释；**若无诊疗行** → 跳过 |
| ⑨ | Cmd-材 | 对耗材行迭代：`createDeviceOrderDraft` / `changeDeviceOrderDraft` / `removeDeviceOrderDraft`；更新/删除前 **`voidUnbilledDraftChargeItemsByServiceAnchor`**；费用草稿经 `createDraftChargeItemFromOrder`（**`service_*` 锚请求表**，**`product_*` 写 `adm_device_definition` + 耗材定义主键**；**`devicePayload.quantity`/`unitCode` + 产品读模型** → **`ProductDefinitionOrderChargePricingsupport**；**不**调用 `generateDeviceDispenseFromTemporaryPath`，**BR-ORD-020**） | 各类 Command | **写库点** `wkf_device_request` / `adm_charge_item`；签发后费用锚 `wkf_device_dispense.id`；**若无耗材行** → 跳过 |
| ⑩ | Asm | `DoctorStationAdviceMapper#toSaveDoctorStationAdviceDraftBatchResponse(...)` | ⑦ 新建 id 列表 | 返回 `newMedicationRequestIds` |


#### 3.6.1 门诊皮试联动（`DoctorStationSkinTestSyncOrchestrator`）

> **AS `BR-ORD-025`** / **AS §1.8**；下游 **AS-OP-NUR-001 §1.7.2** / **STK-SDD**。住院皮试见 **AS-IP-DOR-003 · BR-IP-ORD-024** / **AS-IP-NUR-011 v1.1**（**不在**本 Orchestrator 收敛）。

**触发**：步骤 **⑦** 药品 Cmd 全部完成后、步骤 **⑧** 诊疗 Cmd **之前**（避免与普通 `servicePayload` 行混淆）。

| 药品行 `lineOp` + `skinTestFlag` | 动作 |
| --- | --- |
| CREATE/UPDATE + `true` | ① `ActivityDefinitionQueryService#requireIdByExactName(SKIN_TEST_INSPECTION_ACTIVITY_NAME)` → `skinTestActivityId` ② `ServiceRequestQueryService#findByBasedOnTableAndBasedOnId(med_medication_request, medReqId)` → 已有 companion SR 则 **modify**（同步诊断/执行科室/费用性质/数量=1），否则 **create** ③ `createServiceRequestDraft`：`activityId=skinTestActivityId`；`therapyEnum=TEMPORARY`；`generateSourceEnum=DOCTOR_PRESCRIPTION`；**`basedOnTable=med_medication_request`**；**`basedOnId=medReqId`**；`orgId`/`encounterDiagnosisIds`/`contractId` **继承皮试药同行**；`quantity=1`（或与皮试药同行，以实现为准）④ `createDraftChargeItemFromOrder`：锚 **`wkf_service_request`** + **`wkf_activity_definition`**；定价 **SALE**（**BR-ORD-016**） |
| UPDATE + `false` 或 DELETE | 查 companion SR（同上 based_on）→ **`removeServiceRequestDraft`** + **`voidUnbilledDraftChargeItemsByServiceAnchor(wkf_service_request, srId)`**（**BR-ORD-004** 门控） |

**幂等**：同一 `medReqId` 下 doctor-prescription + 「皮试检查」SR **至多一条**。

**列表展示（`SkinTestCompanionDisplaySupport`）**：companion 行 `adviceDisplayName` 经 **`formatLinkedMedicationLabel(药名, specificationText)`** 拼关联标签（有规格时空格拼接）→ **`appendLinkedMedicationHint`**，形如 **「皮试检查（关联：氯化钠注射液 0.9%*250ml）」**；已含「（关联：」不重复追加。

**域命令扩展（目标态）**：`CreateServiceRequestDraftCommand.CreateServiceRequestDraftItemCommand` 增补 **`basedOnTable`** / **`basedOnId`**；`ServiceRequestDomainService#createServiceRequestDraft` 须落库至 `wkf_service_request.based_on_*`（当前 Java 真源**尚未**映射，成码阶段与 **DM-WKF-004** 对齐）。

**首版实现差异**：见 **§2.6**「IF-DOR-14 皮试联动」行——**已落地**。


#### Mapper 接口方法签名

```java
void validateSaveDoctorStationAdviceDraftBatchRequest(SaveDoctorStationAdviceDraftBatchRequest request);
SaveDoctorStationAdviceDraftBatchResponse toSaveDoctorStationAdviceDraftBatchResponse(
    boolean success, List<Long> newMedicationRequestIds, String message);
// 以及将 DoctorStationAdviceDraftLineRequest 装配为各领域 *Command 的方法族（命名与入参以代码生成阶段按 DM 字段全量落地为准），须覆盖：
// MedicationRequest / MedicationDispense / ServiceRequest / DeviceRequest / DeviceDispense / ChargeItem 之 create/change/remove 全路径。
```


---

### 3.7 IF-DOR-15 `signDoctorStationAdviceBatch`（签发医嘱批次）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#signDoctorStationAdviceBatch` |
| **HTTP** | `POST /api/app/doctorStationAdvice/signDoctorStationAdviceBatch` |


#### Request 字段表（`SignDoctorStationAdviceBatchRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识 |
| signTargets | List\<DoctorStationAdviceResourceKeyDto\> | 是 | 待签发资源键列表 |
| confirmCrossLotSkinTest | Boolean | 否 | **BR-ORD-034**：医生已确认皮试药可跨批号开立（应对 **EX-ORD-309**）；皮试跨批确认已在 IF-DOR-14 暂存完成，签发相位通常忽略 |
| confirmCrossLotPricing | Boolean | 否 | **BR-ORD-035**：医生已确认药耗 FIFO **跨批号**取用及分段金额（应对 **EX-ORD-310**）；首次签发 FIFO **去重批号 ≥ 2** 且未传或 `false` → **EX-ORD-310**；确认后带 `true` 重试本批不再提示；单批号（含同批多库位多段）忽略 |


#### DTO 字段表（`DoctorStationAdviceResourceKeyDto`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| productTableName | String | 是 | 同上三张定义表名之一，与暂存行、列表 **`listRowKey`** 锚点一致 |
| requestId | Long | 是 | 聚合主键 |


#### Response 字段表（`SignDoctorStationAdviceBatchResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 是否成功 |
| prescriptionNoByMedicationRequestId | Map\<String, String\> | 药品请求 id → 处方号 |
| message | String | 提示 |


#### 关键校验

- **AS `BR-ORD-023`**：批次含药品时，**先** `OutpatientPrescriptionSplitAssembler#splitAndAssignPrescriptions`，再进入 §3.7.1 药品分支；分方失败或目标药品无映射处方号 → 业务异常阻断签发
- **AS `BR-ORD-025`**：签发循环**之前**，对 `signTargets` 中每条皮试药 **`DoctorStationSkinTestSyncOrchestrator#expandSignTargetsWithLinkedSkinTestServiceRequests`**：若存在锚定该药的 doctor-prescription 皮试检查 SR 且未在 `signTargets` 中，**追加**至待签发集合（不要求医生单独勾选皮试行）
- **AS `BR-ORD-014` / `BR-ORD-020` / `BR-ORD-021`**：签发依赖 IF-DOR-14 已暂存（药/材：**Request + 请求锚点费用**，**无**暂存发放行）；**药品/耗材**在签发迭代内：**域 `sign*` 之后**作废仍锚在**请求表**上的未计费草稿费 → 按 **规则 H** FIFO **建立/重写发放分段**（**`unitCode` 与请求一致**）→ 再按**发放行**批量重建草稿费并 **`DRAFT`→`PLANNED`**（与 **BR-ORD-016** 一致）
- **AS `BR-ORD-034`（门诊皮试单批优先）**：`skinTestFlag=true` 时 `DoctorStationAdviceFifoAllocationAssembler#allocateMedicationFifo(..., preferSingleLot=true, confirmCrossLotSkinTest)`——按 FIFO 序找第一个单批够量整量落批；无一单批够量且未确认 → **EX-ORD-309**；已确认则允许普通跨批 FIFO。分方 `resolvePrimaryMedicationDispenseLocationId` 皮试行亦优先单批库位，试算不抛 EX-ORD-309
- **AS `BR-ORD-035`（跨批次金额确认）**：FIFO **去重批号 ≥ 2**（同批号多库位多段不弹窗）且未 `confirmCrossLotPricing` → **`OutpatientMedicationSignChargeOrchestrator#assertCrossLotPricingConfirmedOrThrow`** 抛 **EX-ORD-310**（文案按批号汇总各批数量/金额与合计，金额口径同落账 `OrderChargePricingAssembler`）；确认后重试落发放。药品在 `replaceFifoDispenseAndPlanChargesAfterMedicationSigned` 内门控；耗材在 `allocateDeviceFifo` 之后、写发放之前门控
- **AS `BR-ORD-015`**：建议 `signTargets` 按 **药品 → 诊疗 → 耗材** 排序提交；实现按列表顺序迭代，不在应用层强制重排
- **AS `BR-ORD-016`**：药耗分段后 **`ChargeItem.service_table`+`service_id`** 须锚 **`med_medication_dispense` / `wkf_device_dispense` 主键**；金额由后端 **`ChargeItemDefinition`**（含 **`LOT_NUMBER_PRICE`**）重算
- **AS `BR-ORD-017`**：药/材签发与费用迁移规则对称
- **挂账账户**：经 `EncounterChargeAccountOpener#resolveChargeAccountId` 解析；药线按行 `MedicationRequest.contractId`，耗材/默认无合同时双账户优先医保户；无可用账户则拒绝签发（与 IF-DOR-14 同口径）

#### 失败码口径表


| 失败码 | 场景 |
| --- | --- |
| EX-ORD-105 / EX-ORD-205 | 外发失败（模式相关） |
| EX-ORD-207 | 持久化失败 |
| EX-ORD-310 | **BR-ORD-035**：药耗 FIFO 跨批次，医生尚未确认分段数量与金额 |

#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `DoctorStationAdviceMapper#validateSignDoctorStationAdviceBatchRequest(SignDoctorStationAdviceBatchRequest)` | Request | `signTargets` 非空 |
| ①b | Gate | `CurrentOperatorResolver#requireOperatorId` | — | 未登录抛业务异常 |
| ② | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId)` + `DoctorStationAdviceMapper#validatePatientEncounter` | Request |  |
| ②b | Pre | `EncounterChargeAccountOpener#resolveChargeAccountId(encounterId, null, preferInsurance=true)` | Request | 解析就诊**默认**挂账账户（双账户=医保户）；无可用账户 → 业务异常。药线循环内再按行 `contractId` 覆盖 |
| ③ | **Split** | 若 `signTargets` 含药品：`OutpatientPrescriptionSplitAssembler#splitAndAssignPrescriptions(encounterId, orgId, operatorId, signingMedicationRequestIds)` → **`MedicationRequestDomainService#splitPrescription`** | **BR-ORD-023**；参与行 = 本批待签西药/中成药 ∪ 已 ACTIVE 未 SETTLED 非中药（禁止其它 DRAFT 写号；禁止 `tcmFlag=true`）；回写药品请求 `prescription_no`/`rx_type_code`（**不**预写收费行；结算快照见 **BR-OPC-026**） | 须在签发循环**之前** |
| ③b | **Expand** | **`DoctorStationSkinTestSyncOrchestrator#expandSignTargetsWithLinkedSkinTestServiceRequests(signTargets)`** | **BR-ORD-025** | 对批次内皮试药追加 companion 皮试检查 SR 至待签发集合 |
| ④ | **Loop** | **对 `signTargets` 每条**按 `productTableName` 路由 | 见下 **§3.7.1** | 药品 **`prescriptionNo`** 取自分方 Map；缺失则 **`MEDICATION_REQUEST_INVALID_ARGUMENT`** |
| ⑤ | Asm | `DoctorStationAdviceMapper#toSignDoctorStationAdviceBatchResponse(...)` | 处方号 Map |  |

##### 3.7.1 签发单条目标（`signTargets` 内一项）

**药品**（按序执行）

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| M① | Gate | `MedicationRequestQueryService#getListModelById` | **`tcm_flag=true` → 拒绝**（**BR-TCM-ISO-001**，`MEDICATION_REQUEST_INVALID_ARGUMENT`） |
| M② | Cmd | `MedicationRequestDomainService#signMedicationOrder` | 请求生效；**`prescriptionNo` 参数**须为步骤 ③ 分方 Map 中值（域内 `draftToActive` 再次写入） |
| M③ | Cmd | `OutpatientMedicationSignChargeOrchestrator#replaceFifoDispenseAndPlanChargesAfterMedicationSigned` | **`accountId`** 取自本行 `MedicationRequest.contractId`→`EncounterChargeAccountOpener`（禁止整批默认账户覆盖选自费）；void 请求锚点暂存费 → FIFO → **BR-ORD-035 跨批金额门控** → `#createDispenseSegmentChargeDrafts` → `planDraftChargeItemsAfterAdviceSignedByDispenseAnchors` |
| M④ | Cmd | **`DoctorStationSkinTestSyncOrchestrator#signLinkedSkinTestServiceRequestIfPresent(medReqId)`** | 若存在锚定该药的 companion 皮试检查 SR：`signServiceRequest` + `planDraftChargeItemsAfterAdviceSigned`（**BR-ORD-025**；可与 M② 同批或随 expand 已在 Loop 中处理——以实现统一为准） |

**诊疗**

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| S① | Cmd | `ServiceRequestDomainService#signServiceRequest` |  |
| S② | Cmd | `LisPacsApplyGateway#submitIfLabOrImaging` | 占位；忽略/严格模式见 §5.2 |
| S③ | Cmd | `ChargeItemDomainService#planDraftChargeItemsAfterAdviceSigned(encounterId, wkf_service_request, requestId)` | 请求锚点升位 |

**耗材**（与药品对称，无单独处方号）

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| D① | Cmd | `DeviceRequestDomainService#signDeviceOrder` |  |
| D② | Cmd | `ChargeItemDomainService#voidUnbilledDraftChargeItemsByServiceAnchors(encounterId, wkf_device_request, [requestId])` |  |
| D③ | Pre | `DeviceRequestQueryService#loadFifoSignContextOrThrow(requestId)` | FIFO 输入（含 **`quantity` / `unitCode`**） |
| D④ | Asm | `DoctorStationAdviceFifoAllocationAssembler#allocateDeviceFifo(...)` → **`#allocateFifoSegmentsInDoctorBusinessUnit`** | **规则 H**（与药品同构） |
| D④b | Gate | `OutpatientMedicationSignChargeOrchestrator#assertCrossLotPricingConfirmedOrThrow` | **BR-ORD-035**：跨批号（去重批号 ≥ 2）未确认 → **EX-ORD-310** |
| D⑤ | Cmd | `DeviceDispenseDomainService#replaceFifoDraftDispensesAfterDoctorStationSign` | **`unitCode`** 与请求一致；分段 **开立单位 `quantity`** |
| D⑥ | Cmd | `OutpatientMedicationSignChargeOrchestrator#createDispenseSegmentChargeDrafts` |  |
| D⑦ | Cmd | `ChargeItemDomainService#planDraftChargeItemsAfterAdviceSignedForAnchors(encounterId, wkf_device_dispense, dispenseIds)` |  |


#### Mapper 接口方法签名

```java
void validateSignDoctorStationAdviceBatchRequest(SignDoctorStationAdviceBatchRequest request);
SignDoctorStationAdviceBatchResponse toSignDoctorStationAdviceBatchResponse(
    boolean success, Map<Long, String> prescriptionNoByMedicationRequestId, String message);
```


---

### 3.8 IF-DOR-16 `withdrawDoctorStationAdviceBatch`（签退医嘱批次）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#withdrawDoctorStationAdviceBatch` |
| **HTTP** | `POST /api/app/doctorStationAdvice/withdrawDoctorStationAdviceBatch` |


#### Request 字段表（`WithdrawDoctorStationAdviceBatchRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识 |
| targets | List\<DoctorStationAdviceResourceKeyDto\> | 是 | 待签退资源 |


#### Response 字段表（`WithdrawDoctorStationAdviceBatchResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 是否成功 |
| message | String | 提示 |


#### 失败码口径表


| 失败码 | 场景 |
| --- | --- |
| EX-ORD-103 | 已收费不可撤（域/仓储门控） |
| EX-ORD-106 | 签退未找到可处理的关联费用（**AS** 语义；当前工程实现**未**在批次入口做统一的「全就诊费用预检」，而以各 Cmd 门控为准——若产品要强校验需在应用层增补） |
| EX-ORD-207 | 更新失败 |

#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `DoctorStationAdviceMapper#validateWithdrawDoctorStationAdviceBatchRequest(WithdrawDoctorStationAdviceBatchRequest)` | Request | `targets` 非空 |
| ①b | Gate | `CurrentOperatorResolver#requireOperatorId` | — |  |
| ② | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId)` + `validatePatientEncounter` | Request |  |
| ②b | Pre | **`DoctorStationSkinTestSyncOrchestrator#collectCompanionSkinTestServiceRequestIds(medicationRequestIds)`** | 本批皮试药 id | 同批勾选药+companion 去重：显式诊疗目标若已在 companion 集合则 **跳过**，避免二次签退（**BR-ORD-025**） |
| ③ | **Loop** | **对 `targets` 每条**按 `productTableName` 路由 | 见 **§3.8.1** | 与 `DoctorStationAdviceAppService#withdrawDoctorStationAdviceBatch` 一致 |
| ④ | Asm | `DoctorStationAdviceMapper#toWithdrawDoctorStationAdviceBatchResponse(boolean, String)` |  |  |

##### 3.8.1 签退单条目标（`targets` 内一项）

**药品**

| 序号 | 调用 | 说明 |
| --- | --- | --- |
| WM① | `MedicationDispenseQueryService#listMedicationDispenseIdsByMedicationRequest(requestId)` | 当前请求下发放主键 |
| WM② | `ChargeItemDomainService#rollbackPlannedChargeItemsAfterAdviceSignedOffForAnchors(encounterId, med_medication_dispense, ids)` | 发放锚点 **`PLANNED`→`DRAFT`** |
| WM③ | `ChargeItemDomainService#rollbackPlannedChargeItemsAfterAdviceSignedOff(encounterId, med_medication_request, requestId)` | 请求锚点 |
| WM④ | `MedicationRequestDomainService#signOffMedicationOrder` | 请求回草稿 |
| WM⑤ | `ChargeItemDomainService#voidUnbilledDraftChargeItemsByServiceAnchors(encounterId, med_medication_dispense, ids)` | 清理发放锚点草稿费 |
| WM⑥ | `ChargeItemDomainService#voidUnbilledDraftChargeItemsByServiceAnchors(encounterId, med_medication_request, [requestId])` | 清理请求锚点草稿费 |
| WM⑦ | `MedicationDispenseDomainService#releaseDispenseReserve` | `medReqId`；**始终调用**；无可释放行时域层幂等 |
| WM⑧ | **`DoctorStationSkinTestSyncOrchestrator#withdrawLinkedSkinTestServiceRequestIfPresent(requestId)`** | 签退/删除皮试药时对称处理 companion 皮试检查 SR + 费用（**BR-ORD-025**）；**仅 `status=ACTIVE` 签退**；草稿/已签退 **跳过** |

**诊疗**

| 序号 | 调用 | 说明 |
| --- | --- | --- |
| WS① | `ServiceRequestDomainService#withdrawSignedServiceRequest` | 单条 Batch；**已是 DRAFT 幂等返回**（皮试联动签退后同批显式勾选不报错，**DM-WKF-004** §6.1.5） |
| WS② | `ChargeItemDomainService#rollbackPlannedChargeItemsAfterAdviceSignedOff(encounterId, wkf_service_request, requestId)` |  |

**耗材**

| 序号 | 调用 | 说明 |
| --- | --- | --- |
| WD① | `DeviceDispenseQueryService#listDeviceDispenseIdsByDeviceRequest(requestId)` |  |
| WD② | `ChargeItemDomainService#rollbackPlannedChargeItemsAfterAdviceSignedOffForAnchors(encounterId, wkf_device_dispense, ids)` |  |
| WD③ | `ChargeItemDomainService#rollbackPlannedChargeItemsAfterAdviceSignedOff(encounterId, wkf_device_request, requestId)` |  |
| WD④ | `DeviceRequestDomainService#signOffDeviceOrder` |  |
| WD⑤ | `ChargeItemDomainService#voidUnbilledDraftChargeItemsByServiceAnchors(encounterId, wkf_device_dispense, ids)` |  |
| WD⑥ | `ChargeItemDomainService#voidUnbilledDraftChargeItemsByServiceAnchors(encounterId, wkf_device_request, [requestId])` |  |
| WD⑦ | `DeviceDispenseDomainService#releaseDeviceDispenseReserve` | `idListOrRequestKey` = WD① 发放主键**逗号拼接**；无发放事实时回落 `deviceReqId`；**始终调用**（与 WM⑦ 对称）；域层未命中或已逻辑删除时**幂等静默**（见 **DM-WKF-007 v1.2.8** §6.1.10） |


#### Mapper 接口方法签名

```java
void validateWithdrawDoctorStationAdviceBatchRequest(WithdrawDoctorStationAdviceBatchRequest request);
WithdrawDoctorStationAdviceBatchResponse toWithdrawDoctorStationAdviceBatchResponse(boolean success, String message);
```


---

### 3.9 IF-DOR-17 `updateDoctorStationMedicationAdviceGroups`（更新药品医嘱组号）


| 项 | 内容 |
| --- | --- |
| **App** | `DoctorStationAdviceAppService#updateDoctorStationMedicationAdviceGroups` |
| **HTTP** | `POST /api/app/doctorStationAdvice/updateDoctorStationMedicationAdviceGroups` |


#### Request 字段表（`UpdateDoctorStationMedicationAdviceGroupsRequest`）


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊标识 |
| patientId | Long | 是 | 患者标识 |
| clearGroupIds | List\<Long\> | 否 | 需要拆组（`groupId=null`）的药品 `requestId` 列表 |
| assignGroup | AssignMedicationGroupPayload | 否 | 写入同一组号的一批药品 |


#### 嵌套：`AssignMedicationGroupPayload`


| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| groupId | Long | 是 | 目标组号 |
| medicationRequestIds | List\<Long\> | 是 | 药品请求主键列表 |


#### Response 字段表（`UpdateDoctorStationMedicationAdviceGroupsResponse`）


| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 是否成功 |
| message | String | 提示 |


#### 领域编排序列（严格顺序）


| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `DoctorStationAdviceMapper#validateUpdateDoctorStationMedicationAdviceGroupsRequest(UpdateDoctorStationMedicationAdviceGroupsRequest)` | Request |  |
| ①b | Gate | `CurrentOperatorResolver#requireOperatorId` | — |  |
| ② | Pre | `EncounterContextReader#mustGetOutpatientContextByEncounterId(encounterId)` | Request |  |
| ③ | Cmd | **若** `clearGroupIds` 非空 → `MedicationRequestDomainService#adjustMedicationOrderGroup(AdjustMedicationOrderGroupCommand)`（`groupId=null`）；**否则**跳至 ④ | clearGroupIds | 拆组 |
| ④ | Cmd | **若** `assignGroup` 非空 → `MedicationRequestDomainService#adjustMedicationOrderGroup(AdjustMedicationOrderGroupCommand)`（`groupId=assignGroup.groupId`）；**否则**跳至 ⑤ | assignGroup | 并组；**若** ③④均未命中 → Gate 失败（请求语义空） |
| ⑤ | Asm | `DoctorStationAdviceMapper#toUpdateDoctorStationMedicationAdviceGroupsResponse(boolean, String)` |  |  |


#### Mapper 接口方法签名

```java
void validateUpdateDoctorStationMedicationAdviceGroupsRequest(UpdateDoctorStationMedicationAdviceGroupsRequest request);
AdjustMedicationOrderGroupCommand toClearGroupCommand(List<Long> clearGroupIds, Long operatorId);
AdjustMedicationOrderGroupCommand toAssignGroupCommand(AssignMedicationGroupPayload payload, Long operatorId);
UpdateDoctorStationMedicationAdviceGroupsResponse toUpdateDoctorStationMedicationAdviceGroupsResponse(
    boolean success, String message);
```


---

## 4. QueryService 与事件刷新

### 4.1 QueryService查询语义


| QueryService / 只读来源 | 查询服务 | 用于 IF | 用途 |
| --- | --- | --- | --- |
| OutpatientEncounterContextQueryModel | `EncounterContextReader#mustGetOutpatientContextByEncounterId` | IF-DOR-10/14/15/16/17/18/19/20 | 就诊快照、患者一致性 |
| 医嘱事实（过渡期） | `MedicationRequestQueryService` / `ServiceRequestQueryService` / `DeviceRequestQueryService` | IF-DOR-10 | 列表装载 |
| 收费明细行 | `ChargeItemQueryService` | IF-DOR-10/14/15/16 | IF-DOR-10：按 **发放锚点**（**`BR-CHG-001`/`BR-ORD-016`**）归并到请求行展示收费汇总；IF-DOR-14/15/16：状态迁移与删退 |
| ProductDefinition（药/诊/材合一） | `ProductDefinitionCatalogPageLoader#pageProductDefinition` 等 | IF-DOR-11 | 目录分页主数据；**`unitBands`** 由 **`ProductDefinitionCatalogUnitBandsAssembler`** 装配；`#baseRow` 透传其余药品展示列；字段以 `DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` **v3.9+** 为准 |
| 术语概念（编码系统） | `CodeSystemQueryService#getOptionListByCodeSystemName` | IF-DOR-18 | `rate_code` / `method_code` 下拉 |
| 定价主数据/批次价 | `ChargeItemDefinitionQueryService` | IF-DOR-11 | **`#loadPricingGrouped`** 批量取价行；装配器按 **诊疗 `SALE` / 药耗 `LOT_NUMBER_PRICE`** 分流写 **`unitPrice`** 与 **`inventoryList` 行级售价**；**IF-DOR-14** 经 **`resolveDraftChargePriceLine`**；**IF-DOR-15/37** 经 **`selectExecutionPriceLineForChargeDraft(分段 lotNumber)`** |
| 库存可用量 / 发放占用 | `InventoryItemQueryService#findAvailableCatalogRowsForOrder`；`MedicationDispenseQueryService` / `DeviceDispenseQueryService`（目录预占） | IF-DOR-11 / IF-DOR-14 等 | IF-DOR-11：Assembler 内组合候选库位、物理库存与**候选库房全局**发放占用预减；IF-DOR-14 等仍可用 `findAvailableInventories` 等入口 |
| 费用性质（全量下拉） | **`ContractQueryService`**（经 **`DoctorStationEncounterManageAppService`**） | **IF-DOR-01**（**AS-OP-DOR-001**） | 响应 **`feeNatureOptions`**；与本应用服务无直接依赖 |
| 诊疗可执行科室规则（域查询） | `ActivityExecutionLocationQueryService#listExecutionRulesForActivityResolution`；名称：`OrganizationQueryService#mapNamesByIds` / `LocationQueryService#mapNamesByIds` | IF-DOR-19 | 按 `activityDefinitionId` + 大类 + 业务类型合并单品、类型层与大类默认；批量解析组织/地点展示名 |


### 4.2 QueryService 查询语义（事件 → QueryService更新）


| 领域事件（示例名，以各 DM §5 为准） | 来源聚合 | 投影目标 | 更新动作 |
| --- | --- | --- | --- |
| `MedicationOrderDraftCreatedEvent` / `MedicationOrderSignedEvent` / `MedicationOrderSignOffEvent` | MedicationRequest | OutpatientEncounterContextQueryModel 金额/摘要列（见 `OutpatientEncounterContextQueryModel` `EncounterQueryService` §4.2 收费/医嘱类事件） | UPDATE 费用摘要或异步重算 |
| `OrderDraftChargeItemCreatedEvent` / `ChargeItemActivatedEvent` / `EXECUTION_CANCELLED` 对应事件 | ChargeItem | OutpatientEncounterContextQueryModel | UPDATE `total_amount` 等列口径 |
| `ServiceRequestSignedEvent` / `ServiceRequestWithdrawnEvent` | ServiceRequest | 门诊信息/护士执行列表（以DM §6.2为准） | UPDATE/路由 |
| 耗材对称事件 | DeviceRequest / DeviceDispense | 同上 | UPDATE/路由 |


> 字段级赋值以 `DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` §4.2 为唯一真源；本 SDD 不展开列级映射。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览（与 §3 序号对照）


| IF | App 方法 | Cmd 块（顺序） | 聚合写入顺序 | 事务策略 |
| --- | --- | --- | --- | --- |
| IF-DOR-14 | `saveDoctorStationAdviceDraftBatch` | 门控→按 `lines` 顺序 CREATE/UPDATE/DELETE→每行 Request 域 Cmd→`createChargeIfPossible`（请求锚点） | **仅** MedicationRequest / ServiceRequest / DeviceRequest + ChargeItem（**不**建 Dispense，**BR-ORD-020**） | `@Transactional(rollbackFor = Exception.class)` |
| IF-DOR-15 | `signDoctorStationAdviceBatch` | 按 `signTargets` 顺序逐条：域 sign*→药/材 FIFO+分段费用→`plan*` | Request + Dispense + ChargeItem + `LisPacsApplyGateway` 占位 | `@Transactional(rollbackFor = Exception.class)` |
| IF-DOR-16 | `withdrawDoctorStationAdviceBatch` | 按 `targets` 顺序：费用 rollback→域 signOff/withdraw→void 草稿→release 预占 | ChargeItem + Request + Dispense | 同上 |
| IF-DOR-17 | `updateDoctorStationMedicationAdviceGroups` | clearGroupIds→assignGroup | 仅 MedicationRequest.group_id | 同上 |


### 5.2 事务边界与失败回滚


| 接口 | 事务边界 | 失败回滚策略 |
| --- | --- | --- |
| IF-DOR-14 | `#saveDoctorStationAdviceDraftBatch` 已声明 `@Transactional` | 任一 Cmd 抛业务异常 → 整批回滚 |
| IF-DOR-15 | `#signDoctorStationAdviceBatch` 已声明 `@Transactional`；外发为 **Noop** | 本地签发与费用迁移已落地；**tcm_flag** Gate 见 **§3.7.1 M①** |
| IF-DOR-16 | 目标态：签退方法 `@Transactional`；**首版无注解** | 费用回退与域签退顺序见 **§3.8.1** |
| IF-DOR-17 | 目标态：组号方法 `@Transactional`；**首版无注解** | `adjustMedicationOrderGroup` 两段调用 |


### 5.3 幂等落点


| 接口 | 幂等策略 | 关联步骤 |
| --- | --- | --- |
| IF-DOR-14 | 依赖请求主键 + 草稿态门控 + DB 唯一约束 | §3.6 ⑦～⑨ |
| IF-DOR-15 | 签发重复提交：`SignMedicationOrderCommand` 内状态机拒绝非法迁移 | §3.7 ④～⑥ |
| IF-DOR-16 | 签退重复：`withdrawSignedServiceRequest` **已是 DRAFT 幂等返回**；药/耗材 `signOff` 仍按域状态机 | §3.8 WS① / WM⑧ |
| IF-DOR-17 | 组号批量更新可安全重放 | §3.9 ③④ |


---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性


| 场景 | 承诺 | 实现方式 | 关联步骤 |
| --- | --- | --- | --- |
| 批次暂存重复提交 | 呈现可理解冲突或覆盖语义 | 行级主键 + 草稿态校验 | §3.6 |
| 批次签发重复提交 | 非法状态迁移拒绝 | 领域 `ensureSignable` | §3.7 ④～⑥ |


### 6.2 并发控制


| 场景 | 承诺 | 实现方式 | 关联步骤 |
| --- | --- | --- | --- |
| 双终端同开医嘱 | 以后写者为准或显式冲突 | 乐观锁/version（以实体为准） | §3.6～§3.7 |


### 6.3 重试与容错


| 场景 | 承诺 | 实现方式 | 关联步骤 |
| --- | --- | --- | --- |
| 外发失败 | 忽略模式不阻断本地签发事实 | 网关捕获异常+审计 | §3.7 ⑨ |
| 只读失败 | 可重试提示 | EX-ORD-201 | §3.2～§3.5、§3.3（目录/库存/定价） |


### 6.4 数据脱敏


| 场景 | 承诺 | 实现方式 |
| --- | --- | --- |
| 患者姓名/证件展示 | 按机构策略脱敏 | Asm 步骤统一处理 |


### 6.5 审计日志


| 场景 | 承诺 | 实现方式 | 关联步骤 |
| --- | --- | --- | --- |
| 签发/签退 | 处方号、签署批次、外发结果可追溯 | 领域事件 + 应用审计日志 | §3.7～§3.8 |


---

## 7. 测试要点

### 7.1 功能测试


| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| IF-DOR-10 空列表 / 有数据 / onlyMine | 单元测试 | P0 |
| IF-DOR-10 列表行字段与 **§3.2.1** 迁移表一致（至少校验 `adviceDisplayName`、`requestExtensions`、`packageChildren`、`methodCode`、`quantity`、`lineDisplayTotalPrice`、`isAuthoredByCurrentUser`） | 集成测试 | P0 |
| IF-DOR-10 药/材 `generate_source` 过滤与 **`ChargeItem`** 发放锚归并（一单多发放多费用→一行汇总态） | 集成测试 | P0 |
| IF-DOR-11 `ProductDefinition` 分页 + `pricingFlag=1` 排除药品 + 诊疗 `pricing_flag` 过滤 | 集成测试 | P0 |
| IF-DOR-11 `productTableNames` 空=三表全量；非空仅命中列出表名 | 单元测试 | P0 |
| IF-DOR-11「全部」多表：各表完整 `pageSize`、禁止按表数均分；关键字仅命中耗材时结果条数不低于单表「耗材」Tab 本页 | 集成/手工 | P0 |
| IF-DOR-11 库存查询：`InventoryItemQueryService#findAvailableCatalogRowsForOrder` 与候选库位求交、当前页 `product_definition_id` 药/材子集与 §3.3 规则 B 一致 | 集成测试 | P0 |
| IF-DOR-11 发放占用预减：候选库房全局药/材发放草稿+待发占用聚合键（**不含批号**）；占用行 **`unitCode`→拆零** 后再预减（**规则 C** / **AS `BR-ORD-021`**）；**同库位多批**须顺序消耗预占、**禁止**每批重复扣整库占用（**规则 D** / **`consumeLocationOccupancyForLot`**） | 单元测试 | P0 |
| IF-DOR-11 **`availableQuantity` 为包装/主计价单位、`inventoryList[].quantity` 仍为拆零** | 单元测试 | P0 |
| IF-DOR-11 候选库位 ∩ 库存后 `defaultDispenseLocationId` 与 `inventoryList[].lotNumber` 明细一致（**无**行级 `defaultLotNo`） | 单元测试 | P0 |
| IF-DOR-11 定价分流：诊疗仅 **`SALE`**；药耗 **`LOT_NUMBER_PRICE`** + 库存批号对齐行级 **`price`**，汇总 **`unitPrice`** 与首条库存批号一致；**无** `priceListJson` | 单元测试 | P0 |
| IF-DOR-11 `unitBands`：`bandKind` 与 **`ProductDefinitionCatalogUnitBandConstants#BAND_*`** 对齐、`ratioToSplit` 拆零槽为 1 | 单元测试 | P1 |
| IF-DOR-11 空 keyword 不抛 EX-ORD-101 | 单元测试 | P2 |
| IF-DOR-14 药/诊/材增删改混合批次 | 集成测试 | P0 |
| IF-DOR-14 **皮试联动**（`skinTestFlag=true` → companion SR + 双份费用草稿；改 false/删药清理；幂等） | 集成测试 | P0 |
| IF-DOR-15 **签发皮试药自动扩**联动皮试检查 SR | 集成测试 | P0 |
| IF-DOR-15 签发后费用 `DRAFT→PLANNED` 范围仅限批次锚点 | 集成测试 | P0 |
| IF-DOR-15 药耗：`voidUnbilledDraftChargeItemsByServiceAnchors`（请求）→ `replaceFifo*` → `planDraftChargeItemsAfterAdviceSignedForAnchors`（发放）；诊疗仍为单锚点 `planDraftChargeItemsAfterAdviceSigned` | 集成测试 | P0 |
| IF-DOR-15 FIFO **规则 H**：盒开立 3 盒跨批→2+1 整盒分段（**禁止** 0.6+0.4）；片开立 10 片→6+4；`unitCode` 与请求一致 | 单元测试 | P0 |
| IF-DOR-14/15 **`ChargeItem.service_id`** 锚 **`MedicationDispense`/`DeviceDispense`** 主键 + 前端金额不参与权威落库（**`LOT_NUMBER_PRICE`** 后端重算） | 集成测试 | P0 |
| IF-DOR-16 已收费拒绝 + 无费用失败 | 集成测试 | P0 |
| IF-DOR-17 拆组后并组 | 单元测试 | P0 |


### 7.2 非功能测试


| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| IF-DOR-11 性能阈值（AS §7） | 性能测试 | P1 |
| IF-DOR-15 外发忽略/严格两种配置 | 集成测试 | P0 |


### 7.3 QueryService一致性


| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 暂存/签发后 OutpatientEncounterContextQueryModel 金额列刷新 | 集成测试 | P0 |


### 7.4 集成测试


| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 医生站：载入→检索→暂存→签发→列表金额 | 端到端 | P0 |
| 与 ChargeItem 聚合联调（草稿/激活） | 集成测试 | P0 |


### 7.5 测试缺口跟踪


| 缺口 ID | 缺口描述 | 计划关闭时间 |
| --- | --- | --- |
| G-DOR-003-01 | ~~`voidUnbilledDraftChargeItemsByServiceAnchor` 未实现~~ | **已关闭**：IF-DOR-14 更新/删除路径已调用 |
| G-DOR-003-02 | ~~按来源过滤列表查询~~ | **已关闭**：`listMedicationRequestsByEncounterForDoctorStation` / `listDeviceRequestsByEncounterForDoctorStation` |
| G-DOR-003-03 | `LisPacsApplyGateway` 真实连通与错误模型 | **仍开**：当前默认 `NoopLisPacsApplyGateway` |
| G-DOR-003-10 | IF-DOR-10 列表行 DTO 与 **§3.2** 目标态不一致 | **仍开**：与上游 AS **G-DOR-003-10** 同步关闭 |


---

## 8. 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v3.63 | 2026-10-14 | **BR-ORD-021 / IF-DOR-15**：FIFO 占用 = 目录 DRAFT/PREPARATION + 住院 SUMMARIZED；常量表拆分目录/FIFO 两行；上游 **AS v1.78** / **DM-MED-002 v1.3.17** |
| v3.62 | 2026-10-13 | **BR-ORD-038**：IF-DOR-23 `ResolveDoctorStationAdviceDraftBindsRequest.skipAutoBind`；组套套用签发跳过绑定/合管带出；上游 **AS v1.77** / **AS-BAS-MST-017 v1.7** |
| v3.61 | 2026-10-12 | **BR-ORD-036**：组套套用确认弹窗诊疗行 `quantity=单次×executionCount`；`buildDraftLinesFromConfirmedTemplateRows` 禁止把弹窗单次量直接写入 `servicePayload.quantity`；上游 **AS v1.76** |
| v3.60 | 2026-10-11 | **IF-DOR-21 / BR-REQ-005**：`detailItems` 改为选填（检查部位 / 检验样本 / 治疗部位均可空）；`ServiceApplyFormOrchestrator#validateApplyFormLine` 仅校验 Detail 白名单与 `targetId`；上游 **AS v1.75** |
| v3.59 | 2026-10-07 | **IF-DOR-19**：执行科室叠加业务类型层；`listExecutionRulesForActivityResolution` 传入 `activityTypeCode`；上游 **AS v1.73** / **DM-CUS-001 v1.5.0** |
| v3.58 | 2026-10-06 | **IF-DOR-11**：`PageDoctorStationAdviceCatalogRequest.typeEnum` 下推 `type_enum`（术语 `activity_type_code`）；申请单分类 chips；上游 **AS v1.72** / **DM-WKF-001 v1.0.17** |
| v3.57 | 2026-09-06 | **BR-ORD-023 / BR-OPC-026**：分方仅回写药品请求；收费行处方号改结算快照；上游 **AS v1.70** |
| v3.56 | 2026-08-28 | **BR-ORD-036**：门诊前端诊疗/申请单录入单次执行数量 × 执行次数 → 落库 `quantity` 总量 + `executionCount`；§3.5.4 / §3.6 叙事同步；上游 **AS v1.69**；后端字段契约不变 |
| v3.55 | 2026-08-26 | **BR-ORD-035**：药耗 FIFO 跨批次签发确认 **EX-ORD-310** / `confirmCrossLotPricing`；`assertCrossLotPricingConfirmedOrThrow`；上游 **AS v1.68** |
| v3.54 | 2026-08-24 | **BR-ORD-011**：药品待发预占 **12h→24h**；上游 **AS v1.67** / `DISPENSE_RESERVE_PREPARATION_LIMIT_HOURS` |
| v3.53 | 2026-08-24 | **BR-ORD-011 / 规则 C**：耗材目录预占不过期（执行或取消才释放）；药品仍 `now < limitTime`；上游 **AS v1.66** / **DM-WKF-007 v1.2.14** |
| v3.52 | 2026-08-22 | **BR-ORD-034**：门诊皮试药签发 FIFO 单批优先；`MedicationRequestFifoSignContext.skinTestFlag`；`confirmCrossLotSkinTest` / **EX-ORD-309**；`tryAllocateSingleLotCoveringTotalInDoctorBusinessUnit`；上游 **AS v1.65** |
| v3.51 | 2026-08-22 | **BR-ORD-033**：`OutpatientMedicationSignChargeOrchestrator` 挂费前核对发放行 `lot_number` 与 FIFO 分段一致；`DoctorStationAdviceFifoAllocationAssembler` / 药品 `replaceFifo` 拒空批；上游 **AS v1.64** / **BR-OPC-023** |
| v3.50 | 2026-08-20 | **BR-ORD-023 / DM BR-MRQ-033**：分方单元含精二药品性质时回写 `rx_type_code=8`；上游 **AS v1.63** / **DM-MED-003 v1.3.31** |
| v3.49 | 2026-08-18 | **IF-DOR-11 同库多批预减**：§3.3 规则 **D** 明确库位级预占在 FIFO 批次遍历中**顺序消耗一次**（**`copyOccupancyRemaining` / `consumeLocationOccupancyForLot`**）；**禁止**每批重复扣整库占用；§2.1 Helper 与 §7.1 测试矩阵同步；数值例 10+5−9=6；上游 **AS v1.62** |
| v3.48 | 2026-08-12 | **BR-ORD-023**：`OutpatientPrescriptionSplitAssembler` 参与集排除 `tcmFlag=true`；西药签发不得改写中药处方号。对应 **AS v1.61** |
| v3.47 | 2026-08-02 | **挂账双账户**：`EncounterChargeAccountOpener` 替代签发/暂存一律 `getSelfPayAccount`；`ContractFeeNatureOptionLoader` 按就诊账户装配；IF-DOR-15 药线按行 `contractId` 重建费用。对齐 **AS v1.60** / **DM-ADM-009 v1.3** |
| v3.46 | 2026-08-02 | **IF-DOR-18 部位/样本模糊检索**：`OptionDto` 增补可选 **`pyStr`/`wbStr`**；部位树与样本下拉透出检索码；申请单检查 `ElTreeSelect` / 检验 `ElSelect` 前端按名称/编码/拼音/五笔过滤；对齐 **DM-DIA-001 v1.0.3** / **AS v1.58** |
| v3.45 | 2026-08-02 | **IF-DOR-11 多表分页**：§3.3 规则 A 明确「全部」各表用完整 `pageSize`、**禁止**按表数均分；对齐 `ProductDefinitionCatalogPageLoader` 成码（修复关键字仅命中耗材时「全部」少结果） |
| v3.44 | 2026-07-31 | **文档回更**：§2.1 包树 / §2.2 DI 表 `OutpatientMedicationSignChargeOrchestrator` 落点 `outpatient.doctorstation.advice.support`；废止「签发仍经 `#firstPriceLine`」残留；对齐 **DM-ADM-010 v1.1.11** / **DM-ADM-011 v1.6.11** |
| v3.43 | 2026-07-31 | **签发 FIFO 分段挂费**：`OutpatientMedicationSignChargeOrchestrator` 按分段 `lotNumber` → `selectExecutionPriceLineForChargeDraft`；废止签发路径 `selectFirstPriceLineForChargeDraft` 跨批号 priority；上游 **AS v1.56** / **BR-ORD-016** |
| v3.42 | 2026-07-26 | **皮试伴生对齐成码**：正文类名统一 **`DoctorStationSkinTestSyncOrchestrator`**；§3.6.1 **`SkinTestCompanionDisplaySupport`** 关联文案含规格；§3.8 Pre **`collectCompanionSkinTestServiceRequestIds`** 同批去重；WM⑧ companion **仅 ACTIVE** 签退；IF-DOR-16 `withdrawSignedServiceRequest` **已是 DRAFT 幂等返回**；§5.3 幂等落点同步；上游 **AS v1.55** / **DM-WKF-004 v1.1.27** |
| v3.41 | 2026-07-18 | **费用性质默认**：`ContractFeeNatureOptionLoader` 有医保账户优先合同排首位；§3.5 与 **AS-OP-DOR-001 BR-DOR-010** / SDD v1.3.5 对齐（列表不再附带全量 `feeNatureOptions`） |
| v3.40 | 2026-07-18 | **DD §11.7 诊断绑定**：`encounterDiagnosisIds` 有序 ids 串（`adm_encounter_diagnosis.id`，主诊第一）替代 `conditionDefinitionId`/`encounterDiagnosisId` 双字段；§2.1 分方 `diagnosisSetKey`+`med_type`；§3.2/§3.6/§3.5.4/皮试继承对齐 |
| v3.39 | 2026-07-18 | **BR-ORD-032 门诊禁开嘱托**：IF-DOR-11 `excludeCategoryCodes=52` + 名「嘱托」；IF-DOR-14 `InpatientEntrustOrderGateSupport#assertNotEntrustForOutpatient` → **EX-ORD-308**；上游 **AS v1.53** |
| v3.38 | 2026-07-17 | **BR-ORD-031 绑定派生关联**：IF-DOR-14 草稿行 `basedOnTable`/`basedOnId`/`packageId`；`CreateDeviceOrderDraft` 落 `based_on`+`package_id`；成组药嘱 `package_id`=`group_id`；列表回显 `packageId`/`basedOnMedicationRequestId`（耗材）；上游 **AS v1.52** |
| v3.37.1 | 2026-07-15 | **IF-DOR-10** 列表行增补 **`contractId`/`feeNatureText`**（`ContractExpenseNatureAssembler`，处方笺展示） |
| v3.37 | 2026-07-13 | **IF-DOR-16 耗材签退释占**：WD⑦ 与 WM⑦ 对称——`releaseDeviceDispenseReserve` 对 DRAFT/PREPARATION **逻辑删除**（非 STOPPED）；交叉 **DM-WKF-007 v1.2.8**；修复签退后再签发被历史发放行拦截。 |
| v3.36 | 2026-06-25 | **BR-ORD-019** 前端：IF-DOR-10 列表 extension 展示申请单 `serviceRequestDetails` + `remark`（口径同 **BR-NUR-016**） |
| v3.35 | 2026-06-24 | **IF-DOR-18 身体部位树形下拉**：新增 `BodyStructureOptionAssembler`；`bodyStructureOptions` 改树形 ACTIVE 过滤；`getBodyStructureTree` 读侧排除软删；前端申请单 `ElTreeSelect`；对齐 **DM-DIA-001 v1.0.2** / **AS v1.48** |
| v3.34 | 2026-06-23 | **IF-DOR-23 命名收敛**：`resolveDoctorStationAdviceDraftBinds` / `listDraftBindItems` / `DoctorStationAdviceDraftItemDto`；用语「暂存变更行/暂存前绑定解析」（避免与药品批号「批次」混淆）；上游 **AS v1.47** |
| v3.33 | 2026-06-23 | **IF-DOR-23 暂存前绑定解析**：新增 IF-DOR-23；**§3.4.1** 纯读、不去重、不级联、模板默认数量；含触发源与 `based_on` 锚点；上游 **AS v1.47** **`BR-ORD-029`** |
| v3.32 | 2026-06-20 | **orchestration 落点对齐**：皮试/申请单/药签发挂费上收 `module.orchestration.advice`；皮试合并 `DoctorStationSkinTestSyncOrchestrationsupport + AppService `DTO↔Spec` 边界；对齐 **代码生成通用规范 §2.2.1** |（已迁至 application.shared.advice）
| v3.31 | 2026-06-20 | **support 落点对齐**（已被 v3.32 取代）：三类 Support 曾暂落 `advice/support |
| v3.30 | 2026-06-18 | **门诊皮试联动（BR-ORD-025）**：新增 **`DoctorStationSkinTestSyncOrchestrationsupport**；**§3.6.1** IF-DOR-14 步骤 **⑦a** 皮试 companion SR + 操作费草稿；**§3.7** 签发前 **expand** + **M④**；**§3.8** 签退 **WM⑧**；注入 **`ActivityDefinitionQueryService#requireIdByExactName`**；`CreateServiceRequestDraftItemCommand` 目标态 **`basedOnTable`/`basedOnId`**；交叉 **AS-OP-NUR-001-STK-SDD**；上游 **AS v1.44** |
| v3.29 | 2026-06-17 | **BR-ORD-024** IF-DOR-19 执行科室下拉回落；**BR-ORD-026** IF-DOR-10 排除饮片；**BR-ORD-027** `excludeMedicationCategoryCodes`；**BR-ORD-028** init 下拉 `OptionDtoAssembler#sortBySortAsc`；对齐 **AS v1.43** |
| v3.28 | 2026-06-17 | **IF-DOR-11 目录单位**：**`#resolveCatalogPrimaryUnitCode`** 仅 **`package_unit_code`**；删除 **`defEncounterUnit*`/`defInhospitalUnit*`** 出参；**`#resolveCatalogPrimaryUnitName`** 禁止 dictText 跨槽回落；**`ProductDefinitionOrderChargePricingsupport** 禁止请求 `unitCode` 回落目录；诊疗 **`requireUnitCode`** 回落 **`packageUnitCode`**；对齐 **AS v1.42** |
| v3.27 | 2026-06-17 | 新增 **`OutpatientMedicationSignChargeOrchestrator**（IF-DOR-15 药线 / **IF-DOR-37** 共用）；废止 `DoctorStationAdviceAppService` private 分段挂费；**BR-TCM-ISO-001** Gate；**IF-DOR-14/15** `@Transactional`；**IF-DOR-35** 复用 `DoctorStationAdviceCatalogRowDto`；§3.7.1 药品签发收拢为 M①～M③；上游 **AS v1.41** / **AS-OP-DOR-010 v1.4** |
| v3.26 | 2026-06-16 | **IF-DOR-16 耗材签退释放预占**：§3.8.1 **WD⑦** `idListOrRequestKey` 优先 WD① 发放主键逗号列表、**始终调用** `releaseDeviceDispenseReserve`（与 **WM⑦** 对称）；交叉 **DM-WKF-007 v1.2.3** §6.1.10 幂等。**IF-DOR-21 整单诊断绑定**：`SaveDoctorStationServiceApplyFormRequest` 增补 **`conditionDefinitionId`/`encounterDiagnosisId`**（与 **`orgId` 同级**，写入该单全部 `ServiceRequest`）；`ServiceApplyFormOrchestrationSupport#saveApplyForm` 透传至 `CreateServiceRequestDraftCommand`；住院 **IF-IP-DOR-23** 同构复用。 |
| v3.25 | 2026-06-16 | **成码对齐**：`ServiceApplyFormAssembler`（`assembler/`，**非** `mapper/`）；`ServiceApplyFormOrchestrationsupport + `model/*`；§3.5.4/§3.5.5 编排序列与实现一致 |
| v3.24 | 2026-06-16 | **申请单 IF-DOR-21/22**：`saveDoctorStationServiceApplyForm` / `listDoctorStationServiceApplyForms`；**`AR` 号**（`AggregateTypeConstants#REQUISITION_APPLICATION`）；编辑**删旧取新号**；**IF-DOR-18** 增补部位/样本；**IF-DOR-11** `categoryEnum`；**ServiceRequestDetail** `target_table`/`target_id`/`remark`；上游 **AS v1.37** |
| v3.20 | 2026-06-11 | **§3.2 / §3.2.1** 列表行增补 **`encounterDiagnosisId`**（与 `conditionDefinitionId` 成对回显，供编辑回填）；**`SaveMedicationAdviceDraftLinePayload`** 增补 **`encounterDiagnosisId`**；§3.6 药品/诊疗/耗材诊断绑定叙事对齐双字段落库 |
| v3.24 | 2026-07-15 | **西药/中成药分方分轨（DM BR-MRQ-032 / AS BR-ORD-023）**：`PrescriptionSplitLineContext.medicationCategoryCode`；`rx_type_code` 中成药 `10`/`11`/`12`；上游 **DM-MED-003 v1.3.22** |
| v3.19 | 2026-06-11 | **废止 `sign_code` / `signBatchCode`**：IF-DOR-15 不再生成或返回签发批次；**诊疗/耗材**暂存载荷增补 **`encounterDiagnosisId`** 落库 `encounter_diagnosis_id`（与药品对称）；对齐 **DM v1.3.11** / **DM-WKF-004 v1.1.13** |
| v3.23 | 2026-06-12 | **IF-DOR-20 读口径收敛为全类型医嘱**：`pageDoctorStationAdvice`；行 DTO 与 **IF-DOR-10** 同构（`DoctorStationAdviceRowDto`）；复用 `DoctorStationAdviceListAssembler` + `PagedResultBuilder#buildFromFullList`；移除 slip/药品专用契约；上游 **AS v1.36** |
| v3.22 | 2026-06-12 | **IF-DOR-20 响应收敛**：行 DTO **不含** `inventoryDetailList`（候选库存归 **IF-OPP-03**）；§3.5.3 读口径/Response 表与成码对齐；上游 **AS v1.35** |
| v3.21 | 2026-06-12 | **IF-DOR-20 语义收敛为医嘱查询**：`pageDoctorStationMedicationAdvice`；Tab 码 **`adviceScopeTabCode`**（`CURRENT_ADVICE`/`HISTORY`）；DTO 重命名为 `DoctorStationMedicationAdvice*`；是否按处方展示由前端处理；上游 **AS v1.34** |
| v3.18 | 2026-06-11 | **IF-DOR-20** 增补可选 **`orderingDepartmentId`**：历史处方不按接诊医生收窄；前端传入开单科室时按药嘱 **`org_id`**（`adm_organization.id`）过滤；上游 **AS v1.33** |
| v3.17 | 2026-06-11 | **IF-DOR-20** 增补 **`prescriptionScopeTabCode`**：单接口分 **本次就诊/历史处方** Tab；历史按 `patientId` 查全量并排除当前 `encounterId`；上游 **AS v1.32** |
| v3.16 | 2026-06-11 | 新增 **IF-DOR-20** `pageEncounterMedicationPrescriptionSlips`：复用 `EncounterMedicationDispenseSlipPageLoader`（读口径对齐 **IF-OPP-03**）；§2.1 契约类、§2.2 Loader、§2.4/§2.5 白名单、**§3.5.3** 编排序列；上游 **AS v1.31** |
| v3.16.1 | 2026-06-11 | **IF-DOR-14 暂存定价**：药耗经 **`ClinicalCatalogPriceResolver#resolveDraftChargePriceLine`**；前端回传 `dispenseLocationId`（目录/组套/历史 hydration）时写路径 **不**二次 match 模板；`SaveDeviceAdviceDraftLinePayload` 增补 `dispenseLocationId`；组套套用见 **AS-BAS-MST-017 IF-08** |
| v3.15 | 2026-06-11 | **编号分序列**：签发批次 **ASI**、分方处方 **CP**、药嘱 **bus_no** **MR**；对齐 **DM v1.3.9** / **B.10.9** |
| v3.14 | 2026-06-11 | **门诊处方分方（BR-ORD-023）**：§2.1 `OutpatientPrescriptionSplitAssembler`；§2.2 分方装配/设置/常量；§3.7 步骤 ③b + 处方号取自分方 Map；领域 **`OutpatientPrescriptionSplitPlanner`**；上游 **AS v1.30** / **DM-MED-003 v1.3.8** |
| v3.13 | 2026-05-28 | 六维对账（`zxplat` 真源）：§1.2/§2.6/§5 事务与 IF-DOR-14 写路径；`ChargeItem` 药/材方法名改为 **`ByDispenseAnchors`**；§3.2 补全已实现 DTO 列、标注 `prescriptionCategory*`/`signedBy*` 未装配；上游 **AS v1.28** |
| v3.12 | 2026-05-28 | **BR-ORD-016 成码**：§2.1 增补 **`ProductDefinitionOrderChargePricingsupport**；Mapper 费用三方法签 **`quantityUnit`/`productRow`**；§3.6 ⑦～⑨ 暂存挂费比例换算；对齐 **AS v1.27** / **DM-ADM-011 BR-CHG-008** |
| v3.11 | 2026-05-28 | **诊疗草稿 Gate**：`ServiceAdviceDraftCommandsupport 改为 **`requireQuantity`/`requireUnitCode`**（禁止默认 1）；UPDATE 用 **`resolveQuantityForModify`** |
| v3.10 | 2026-05-28 | **L3 字段对账**：§3.2 列表行 `packageChildren`/`requestExtensions` 替代 `contentJson`/`activityChildrenJson`；§3.6 诊疗载荷 `quantity`/`unitCode`/`contractId` 与 **`ServiceAdviceDraftCommandsupport** **require\*** 语义（CREATE 禁止默认 1） |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v3.9 | 2026-05-27 | **单位 code+展示名成对（AS `BR-ORD-022`）**：§2.1 **`ProductDefinitionCatalogUnitBandsAssembler#resolveUnitDisplayName`** / **`#resolveDoseUnitDisplayName`**；§2.2 IF-DOR-10 列表 **`ProductDefinitionBatchLoader`** + 单位展示装配、IF-DOR-12 **`unitName`**；§3.2 **`unitName`/`doseUnitName`** 解析口径；§3.3 **`DoctorStationAdviceInventoryRowDto#unitName`**；§3.4 绑定行 **`unitName`**；上游 **AS v1.25** |
| v3.8 | 2026-05-27 | **库存拆零与医嘱单位对齐（AS `BR-ORD-021`）**：§2.1 增补 **`DoctorStationAdviceInventoryUnitQuantityHelper`**、**`DoctorStationAdviceFifoSegmentDraft`/`DoctorStationAdviceFifoAllocationResult`**；§2.2 占用查询模型 **`unitCode`**、Catalog/FIFO 装配器叙事；§3.3 规则 **C/D** 占用 **`unitCode`→拆零**预减；新增规则 **H**（FIFO 内部拆零比对、整包装整数分段、禁止小数盒）；§3.7 **§3.7.1** M/D 步签名与 **`allocateFifoSegmentsInDoctorBusinessUnit`**；§7.1 测试矩阵；交叉 **`MedicationProductQuantityToSplitUnitConverter`**、门诊/住院 FIFO 装配器、**`InpatientDoctorStationAdviceCatalogAssembler`**；上游 **AS v1.24** |
| v3.7 | 2026-05-24 | **QueryService 投影**：对齐 **`ProductDefinition` QueryService v3.12**——诊疗新增/定价后须含 **`package_unit_code*`、`def_encounter_unit_code*`、`status_enum`** 等；IF-DOR-11 **`unitCode`/`unitBands`** 依赖上述列，与 **v3.6** 槽语义叠加生效。 |
| v3.6 | 2026-05-24 | **IF-DOR-11 单位槽**：医生站 **`unitBands`** 包装槽由 **`SALES` 改为 `PACKAGE`**；主单位 **`unitCode`** 改为 **`defEncounterUnitCode` → `packageUnitCode`**（**不含** `purchaseUnitCode`）；**诊疗**五槽 DTO **仅 `PACKAGE` 有值**；对齐 **`ProductDefinition` QueryService v3.11**。 |
| v3.5 | 2026-05-21 | **IF-DOR-14/15 与 DM 对齐**：暂存药/材 **不**调用 `generateDispenseFromTemporaryOrder` / `generateDeviceDispenseFromTemporaryPath`（**BR-ORD-020**，交叉 **DM-MED-003 BR-MRQ-028**）；暂存仅 Request + 请求锚点费用；签发 FIFO 建发放并重挂费用；§2.4 白名单、§3.6 编排序列 ⑦/⑨、§3.7 关键校验；上游 **AS v1.22**。 |
| v3.4 | 2026-05-18 | **IF-DOR-10**：列表装配文档化 **`module.common`** 目录预热与术语 Map（`OrderRequestCatalogWarmAssembler` / `TerminologyOptionLoader` / `OrderCatalogDisplayAssembler`），与 IF-NUR-02 / IF-OPP-03 共用 |
| v3.3 | 2026-05-13 | **单位槽共享**：`unitBands` 元素类型迁至 **`module.common.dto.product.ProductDefinitionCatalogUnitBandDto`**；装配迁至 **`ProductDefinitionCatalogUnitBandsAssembler`**（医生站五槽 / 进货选品三槽）；槽语义常量迁至 **`domain.share.constants.ProductDefinitionCatalogUnitBandConstants`**（**`DoctorStationAdviceConstants`** 不再承载 **`CATALOG_UNIT_BAND_*`**）；§2.1 代码树、§2.2、§2.6、§3.3 DTO 表与 §7.1 测试行同步；交叉 **AS-INV-WHS-002-SDD v2.17**（IF-WHS-IN-11）。 |
| v3.2 | 2026-05-10 | **IF-DOR-10 / IF-DOR-14 · 诊疗与耗材诊断定义**：`SaveServiceAdviceDraftLinePayload` / `SaveDeviceAdviceDraftLinePayload` 增补 **`conditionDefinitionId`**；**`Create*ServiceRequest*Command` / `Create*DeviceOrder*Command`** 等经 Mapper 落库 **`wkf_service_request` / `wkf_device_request`.`condition_definition_id`**；**`ServiceRequestListModel` / `DeviceRequestListModel`** 与 **`DoctorStationAdviceListAssembler`** 与药品同源批量解析展示名；§2.4 白名单 **`getConditionDefinitionListByIds`** 叙事与 §2.2 **`FieldNameConstants#CONDITION_DEFINITION_ID`** 事件摘要对齐；**v2.7** 中「诊疗不在 IF-DOR-14 绑定诊断」**已由本版废止**。交叉 **DM-WKF-003**、**DM-WKF-004**、**DM-MED-003** 诊断列统一为 **`condition_definition_id`**。 |
| v3.1 | 2026-05-10 | **IF-DOR-11 目录定价回更**：诊疗目录仅 **`SALE`**；药耗目录仅 **`LOT_NUMBER_PRICE`**，`inventoryList` 按批号对齐行级 **`price`/`chargeItemDefinitionId`**，汇总 **`unitPrice`** 与首条库存批号一致；**`DoctorStationAdviceInventoryRowDto`** 增补 **`chargeItemDefinitionId`**，**`price`** 语义为批号售价；**`selectFirstPriceLineForChargeDraft`** / **`firstPriceLine`** 与目录分流对齐；§2.2、§3.3 规则 **F**、§4.1、§7.1、**`ProductDefinition` QueryService v3.9**；§1.1 编排权威 **AS v1.20**；交叉 **DM-ADM-010 v1.1**。 |
| v3.10.1 | 2026-06-17 | **IF-DOR-19 / DM-CUS-001 v1.2.0**：执行科室支持大类默认规则（`activityDefinitionId` 可空）；IF-DOR-19 改调 `listExecutionRulesForActivityResolution` + `ProductDefinitionBatchLoader` 解析大类；§2.2/§3.5.2/§4.1 同步；上游 **AS v1.27**。 |
| v3.0 | 2026-05-10 | 新增 **IF-DOR-19** `listActivityExecutionLocations`：`ListActivityExecutionLocationsRequest` / `ListActivityExecutionLocationsResponse` / **`ActivityExecutionLocationRuleRowDto`**（独立类）；§2.2 注入 **`ActivityExecutionLocationQueryService`**；§3.1 方法白名单与 **§3.5.2** 契约；§4.1 查询语义；§1.1 编排权威 **AS v1.19**。 |
| v2.9 | 2026-05-09 | **IF-DOR-15/16 · 药耗 FIFO 与双锚点费用**：重写 **§3.7**（§3.7.1 单条签发：请求作废草稿 → FIFO → `replaceFifo*` → `createDispenseSegmentChargeDrafts` → `planDraftChargeItemsAfterAdviceSignedForAnchors`）与 **§3.8**（§3.8.1 签退：发放锚点 rollback → 请求 rollback → 域签退 → void 双锚点 → `release*Reserve`）；§3.2 增补 **`fifoDispenseLocationId`/`fifoDispenseLocationName`** 与 §3.2.1；§2.2/§2.4/§2.5/§2.6 同步 **`DoctorStationAdviceFifoAllocationAssembler`**、`LocationQueryService`、`loadFifoSignContextOrThrow`、批量锚点 ChargeItem 方法；§1.1 编排权威 **AS v1.18**；交叉 **DM-MED-002 v1.3**、**DM-WKF-007 v1.2**、**DM-ADM-011-SDD §5.1.4c/§5.1.4d**。 |
| v2.8 | 2026-05-08 | **IF-DOR-15 / IF-DOR-16 · 收费明细 `DRAFT`↔`PLANNED`**：§2.2 `ChargeItemDomainService` 职责与 §2.4 白名单增补 `#planDraftChargeItemsAfterAdviceSigned` / `#rollbackPlannedChargeItemsAfterAdviceSignedOff`；原 `#activateChargeItemAfterExecution` / `#cancelExecutionRollbackChargeItem` 标明为执行域/批量 ID 主路径，医生站签发/签退以新锚点方法衔接 **`DM-ADM-011`** 预结算门禁；§2.6 实现对齐表增一行；§1.1 编排权威 **AS v1.17**；交叉 **DM-ADM-011 v1.2**。 |
| v2.8.1 | 2026-06-16 | **IF-DOR-18**：Response 增补 **`tcmMethodCodeOptions`**（术语 `tcm_method_code`）；列表/护士站/发药读侧中药行用法展示改 **`TcmMethodCodeDisplaysupport**（`tcm_method_code` 优先，未命中回落 `method_code`） |
| v2.7 | 2026-05-05 | **IF-DOR-10 / IF-DOR-14 药品与诊断**：§3.2 列表行增补 **`conditionDefinitionId`**；**`conditionDefinitionName`** 明确仅药品行由 `condition_id` 解析；§3.2.1 迁移表增补诊断主键列；§3.6 **`SaveMedicationAdviceDraftLinePayload`**：`conditionIds` 废止改为 **`conditionDefinitionId`（单列）**，并增补 `dose` / `doseUnitCode` / `dispensePerDuration` / `skinTestFlag` 与列表回显对齐；**`SaveServiceAdviceDraftLinePayload`** 删除诊断绑定字段（本期诊疗不在 IF-DOR-14 绑定诊断）；§2.2 注入 **`ConditionDefinitionQueryService`**、列表装配器叙事更新；§2.4 查询白名单增补；§2.2 **`FieldNameConstants`** 扩展字段行；关键校验增补药品草稿领域命令落库说明。编排权威对齐 **AS v1.16**。 |
| v2.6 | 2026-05-04 | **类型锚点 / A.9.1**：§3.2 **`DoctorStationAdviceRowDto`** 删除对外 `resourceType` 行；**`listRowKey`** 与 **`§3.2.1`** 迁移表改为 `requestId`+SOH+`productTableName`，与 `GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR` 及实现装配一致（禁止 `\|` 拼接）；**IF-DOR-14/15** 中 **`DoctorStationAdviceDraftLineRequest`**、**`DoctorStationAdviceResourceKeyDto`**（IF-DOR-15/16 共用）字段表由 `resourceType` 改为 **`productTableName`**，与仓库 DTO 一致。 |
| v2.5 | 2026-05-04 | **IF-DOR-10**：重写 **`DoctorStationAdviceRowDto`** 字段表以覆盖既有医嘱主表行全部展示与回填维度；新增 **§3.2.1**「既有统一行 DTO → ZXPlat 栏位」迁移对照；关键校验补充 **AS `BR-ORD-019`**；§2.3 增补列表行字典/JSON/皮试结果/诊疗嘱托等双锚；§2.2 列表装配器职责、§3.2 编排序列 ⑥、§7.1/§7.5 测试与缺口 **G-DOR-003-10**；§1.1 编排权威对齐 **AS v1.14**、实现阶段标明 IF-DOR-10 目标态补全。 |
| v2.4 | 2026-05-04 | **IF-DOR-11 / QueryService对齐**：`ProductDefinitionPageQuery` **移除** `organizationUnitId`；`DoctorStationAdviceMapper#toProductDefinitionPageQuery` 仅接收 Request；§3.3 Request 表、`organizationUnitId` 语义改为**装配器**库位模板上下文；规则 **A/E** 与编排序列 ②、Mapper 摘录删除「QueryService `org_id` 目录过滤」；对齐 **`ProductDefinition` QueryService v3.8** 与 `zxplat` 实现。 |
| v2.3 | 2026-05-04 | **账户口径**：§2.2 `AccountQueryService` 与 **`DM-ADM-009` v1.1** 对齐——IF-DOR-14 锚点为 **`AccountTypeCode.PERSONAL_CASH_ACCOUNT`**（`getSelfPayAccount`）；§2.3 增补 **`AccountTypeCode`** 行；对齐上游 **AS v1.12**。 |
| v2.2 | 2026-05-04 | **IF-DOR-13 废止**：费用性质全量下拉并入 **AS-OP-DOR-001 · IF-DOR-01** `feeNatureOptions`；删除本模块 `DoctorStationAdviceContractResolver` / `getEncounterAdviceDefaultContract` 及 **`InitDoctorStationAdviceResponse.defaultContract`** 叙述；**IF-DOR-18** 仅频次/用法术语；§2.4/§2.5/§2.6/§3.1/§3.5/§3.5.1/§4.1 与 **OutpatientEncounterContextQueryModel** 查询白名单同步；对齐上游 **AS v1.11** 与 **AS-OP-DOR-001-SDD v1.3.0**。 |
| v2.1 | 2026-05-04 | **IF-DOR-11**：`DoctorStationAdviceCatalogRowDto` 去除 `adviceType`/`priceListJson`/`defaultLotNo` 及平铺多单位列；新增 **`unitBands`**（`DoctorStationAdviceCatalogUnitBandDto`）、**`unitPrice`**、药品 **`rate`/`method`/`doseForm`/`restricted`/`skinTest`/`inject`** 等QueryService透传字段；规则 **E/F/G**、§3.3 编排序列、Mapper 摘录与 **§7.1** 用例与 **`assemblePageContent`** 实现对齐；§2.1/§2.2 装配器描述更新；对齐上游 **AS v1.10** 与 **`ProductDefinition` QueryService v3.7** |
| v2.0 | 2026-05-04 | **IF-DOR-18** `initDoctorStationAdvice`：§2.1 `Init*` 契约类、§2.2 `CodeSystemQueryService`、`§3.1` 方法白名单、**§3.5.1** 编排序列与 DTO；**IF-DOR-11** `DoctorStationAdviceCatalogRowDto` 增补单位/换算比等（§3.3）；§1.1/§1.2/§4.1 同步；**IF-DOR-13** Asm 改为复用 `toEncounterAdviceDefaultContractResponse`；对齐上游 **AS v1.9** |
| v1.9 | 2026-05-04 | **BR-ORD-018 / 展示排序号**：§2.2 增补 `FieldNameConstants#SORT_NUMBER`；§2.3 医生站 `sort_number` 三聚合与 **Flyway V35**；`DoctorStationAdviceListAssembler` 与 **§3.2** DTO/编排序列/关键校验对齐；**§3.6** `DoctorStationAdviceDraftLineRequest.sortNumber`；对齐上游 **AS v1.8** |
| v1.8 | 2026-05-03 | **实现对齐**：§2.1 包结构（`assembler/`、`DoctorStationAdviceConstants`、旧命名（已废止） 命令与占位网关）；§1.1/§1.2 首版已落地与**应用服务 `@Transactional`** 口径；§2.2 依赖表（`ListAssembler`、`AccountQueryService`、`BusinessSequenceService`、`GeneralSharedConstants.ISO_LOCAL_DATE_TIME_FORMATTER` 等）；**§2.5/§2.6** 白名单与代码真源差异表；IF-DOR-10/13 编排序列与 Mapper 签名修正；IF-DOR-11 增加 v1.8 实现提示；§5.1/§5.2 事务表与实现一致；§7.5 关闭 G-DOR-003-01/02 |
| v1.7 | 2026-05-03 | **§3.3 IF-DOR-11**：规则 **B** 显式**候选库房与物理库存求交**；规则 **D** 补充行级 **`availableQuantity` 跨库加总**；规则 **E** 改为**接诊科室**、候选集内降级、与签发发放 `location_id` 分工；§2.1 `DoctorStationAdviceCatalogAssembler` 描述同步；**§1.1 编排权威**对齐 **AS v1.7** |
| v1.6 | 2026-05-03 | **IF-DOR-10**：文首业务说明；§2.3 增补**耗材 `generate_source`**；DTO `chargeStatusEnum`、关键校验、编排序列 ⑥、§4.1 收费行用途、§7.1 归并测试；编排权威对齐 **AS v1.5** |
| v1.5 | 2026-05-03 | 对齐 **`AS-OP-DOR-003` v1.4**：§1.1 方法权威含 **`DM-CUS-006`/`ProductDefinition` QueryService**；§1.2 计费锚点/金额权威；§3.3 规则 E 明示模板域；**§3.6 IF-DOR-14** 药/材 Payload 语义、关键校验、`ChargeItem` 锚发放行；**§3.7 IF-DOR-15** 关键校验与步骤 ⑦ 查询口径；§7.1 计费锚点测试 |
| v1.4 | 2026-05-03 | **IF-DOR-11**：显式固化**门诊药/材对称**及与 `DM-MED-003`/`002`、`DM-WKF-003`/`007`、`DM-ADM-011` **`BR-CHG-001`** 交叉锚点；对齐域文档 **`DM-CUS-006` v1.1**、**`ProductDefinition` QueryService v3.3** |
| v1.3 | 2026-05-03 | **IF-DOR-11**：移除 `adviceTypeCodes`；以 **`productTableNames`**（QueryService `product_table_name` 字面值列表）收窄三目录；§2.3/§2.5/§3.3 Gate·④·规则 A、§7.1 同步 |
| v1.2 | 2026-05-03 | **IF-DOR-11 锚点**：目录行与预选统一为 **`productDefinitionId` + `productTableName`**；Request `prepickedProductKeys` + DTO `PrepickedProductKeyDto`；与 `ProductDefinition` QueryService多态唯一键对齐；§2.1/§2.3/§2.5/§3.3/§7.1 同步 |
| v1.1 | 2026-05-03 | **IF-DOR-11 重写**：目录主数据为 `ProductDefinitionBatchLoader` + `ProductDefinition` QueryService；`DoctorStationAdviceCatalogAssembler` 串联药房物理库存、发放草稿/待发占用、`subtractInventoryNetAvailable` 预减、发药路由与降级、`ChargeItemDefinitionQueryService` 主表/批次价组装与 `sortCatalogRowsByKeyword`；新增 `DoctorStationAdviceInventoryRowDto`、`pharmacyLocationId`、`pricingFlag: Integer`；§2.3～§2.5、§3.3、§4.1、§6.3、§7.1 与白名单/编排序列对齐；全文表述为 **ZXPlat 目标态**（不引用他仓实现） |
| v1.0 | 2026-05-03 | 初版：对齐 `AS-OP-DOR-003` v1.1（IF-DOR-10～17）；`DoctorStationAdviceAppService` + HTTP 全 `POST /api/app/doctorStationAdvice/*`；§2.3 双锚；§3 编排序列（Gate→Pre→Cmd→Asm）、Cmd 括号内 Command、互斥/分支说明；§5 写顺序与事务；§7 测试矩阵；**目标态**：`DoctorStationAdviceCatalogAssembler`、`DoctorStationAdviceOrderBindQueryService`、`DoctorStationAdviceContractResolver`、`LisPacsApplyGateway`、`MedicationRequestQueryService`/`DeviceRequestQueryService` 按来源过滤、`ChargeItemDomainService#voidUnbilledDraftChargeItemsByServiceAnchor`（见 §7.5） |


---

**文档负责人**：（待填） · **评审**：（待填）
