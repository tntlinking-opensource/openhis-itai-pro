# 住院医生站 · 医嘱开立 · 应用服务详细设计（SDD）

> **编码**：AS-IP-DOR-003-SDD  
> **版本**：v1.62 | **日期**：2026-10-15 | **作者**：（待填）

---

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| **模块** | 住院医生站 · 医嘱开立应用服务（`inpatient/doctorstation/advice`） |
| **边界** | 当前住院就诊**医生开立**药品+诊疗统一列表（**TherapyType** 长/临/**出院带药**筛选）；**全类型医嘱分页查询（IF-IP-DOR-22）**；开立页初始化；**仅药品+诊疗**目录分页（含**嘱托**固定项）；绑定规则；可执行科室规则；批次暂存/签发/签退；药品组号；**长期医嘱停嘱 / 取消停嘱**；**改开立/停嘱时间（IF-IP-DOR-26）**；**长期药品改量（IF-IP-DOR-28）**；**出院医嘱**；**检验/检查/治疗申请单（IF-IP-DOR-23/24**，仅临嘱）；**住院皮试联动**（**BR-IP-ORD-024**）；**嘱托医嘱**（**BR-IP-ORD-028**）；**出院带药**（**BR-IP-ORD-029**：`DISCHARGE_MEDICATION`；开立同临嘱；下游发放意图见护士/药房 AS）；**首日用量**（**BR-IP-ORD-030**：仅长期药 `firstDose*`；执行侧槽位意图见 **AS-IP-NUR-004**）；**三时间轴与人名**（**BR-IP-ORD-031**）；**长嘱改量链**（**BR-IP-ORD-032**）；列表口服剩余展示（与 **BR-IP-EXE-038** 同源）；**执行记录 UI**（只读复用 **IF-IP-EXE-01**，见 §1.3） |
| **编排权威** | `AS-IP-DOR-003-住院医生站医嘱开立.md`（**v1.45**）§2 + §4 |
| **方法权威** | `DM-MED-003`/`DM-MED-002` §6；`DM-WKF-004` §6；`DM-WKF-001`；`DM-ADM-011`/`DM-ADM-010`；`DM-CUS-004`；`DM-CUS-006`；`DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService`；`ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）`（**Q-ORD-007**）；`DM-ADM-002 §6.2.19`（`InpatientEncounterContextQueryModel` · `EncounterContextReader`） |
| **严格对齐** | IF **IF-IP-DOR-10～12、18～24、14～17、20～21、25、26、27、28** 与 AS 一致（**IF-IP-DOR-13** 废止，无 HTTP） |
| **实现阶段口径** | **主体已落地**（`InpatientDoctorStationAdviceAppService` 既有 public 方法 ↔ §3.1，含 **IF-IP-DOR-23/24/25/26/27/28**）；**领域 `stopper_id` / 改时命令已落地**（**G-IP-DOR-003-06 Closed**）；写方法**无** App 层 `@Transactional`（见 §1.2）；列表读 **`MedicationRequestQueryService` / `ServiceRequestQueryService`**（**G-IP-DOR-003-01** 已关闭）；**G-IP-DOR-003-03 / G-IP-DOR-003-04 / G-IP-DOR-003-05** 已关闭；**嘱托 / 出院带药应用编排已落地**；**口服剩余 Support 已落地**（与 **AS-IP-NUR-004 BR-IP-EXE-038** 同源） |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| **字段口径** | 持久化枚举存 **String code**（`GenerateSource#getCode()`、`TherapyType#getCode()`、`RequestStatus#getCode()` 等） |
| **QueryService口径** | 写前门控：`EncounterContextReader#mustGetInpatientContextByEncounterId` → `InpatientEncounterContextQueryModel`（**DM-ADM-002 §6.2.19**）；列表：**`MedicationRequestQueryService` / `ServiceRequestQueryService`**（**Q-ORD-007**，不含 `DeviceRequest`）→ `InpatientOrderListMergeAssembler` 合并为 `InpatientOrderListSkeleton` → 列表行 DTO；费用/目录展示经 `ProductDefinitionBatchLoader` 等应用层装配 |
| **长/临/出院带药分轨** | **`TherapyType.LONG_TERM` / `TEMPORARY` / `DISCHARGE_MEDICATION`** → `therapy_enum`；**住院三种 therapy** 暂存/签发**均仅**写 Request（**不**建 `ChargeItem`、**不**建 `Dispense`）；FIFO/费用在护士执行（**AS-IP-NUR-004**）；**均不校验库存**（**BR-IP-ORD-003**）；**门诊临嘱不变** |
| **出院带药** | code **`DISCHARGE_MEDICATION`**；仅住院药品 Request；`quantity` 语义同 **TEMPORARY（总量）**；列表类型文案「出院带药」；皮试/输液**软约定不硬拒**；开立侧**不停嘱**；下游执行出 Dispense **强制不写 `summary_no`**、仅逐行发药——产品意图见 **AS `BR-IP-ORD-029⑥`**（承接护士/药房 AS，本文不扩 Cmd） |
| **嘱托医嘱** | 目录名精确 **「嘱托」**；**`category_code=52`** → `ServiceRequest.category_enum=52`；正文 → `entrust_content`；列表内容列展示 `entrustContent`；长/临均可（**禁止**出院带药 therapy）；`rate_code` **仅展示**；执行侧**不建账单**且**整单只执行一次**见护士 AS（**AS `BR-IP-ORD-028`⑦ / `BR-IP-EXE-042`**） |
| **首日用量（首次用量）** | `first_dose` / `first_dose_unit_code`（**DM-MED-003** / **BR-MRQ-010**）；**仅长期药品**；开立 UI 按计量单位录入，**落库为最小单位**（与 `quantity`/`unit_code` 同口径）；临时/出院带药须空；习惯称「首日」、语义为**首次执行**用量；执行侧相对开嘱裁剪频次点、默认 `actualTime`、仅首次槽换量且与常规长嘱同路径——产品意图见 **AS `BR-IP-ORD-030⑥`**（承接 **AS-IP-NUR-004**，本文不扩执行 Cmd）；**禁止**写死频次时钟 |
| **分次用量不一致** | `dose_schedule` / `quantity_schedule`（**DM-MED-003 · BR-MRQ-035** / **AS BR-IP-ORD-037**）；**仅长期西药/中成药**；计量串+拆零串成对；空=走原 `dose`/`quantity`；有值时段数=频次日次数；`doseUnitCode`/`unitCode` 仍必填；列表/暂存透传字段见 §3.2/§3.8；执行分槽算量见 **AS-IP-NUR-004 · BR-IP-EXE-040**（本文不扩执行 Cmd） |
| **三时间轴** | **开立** `req_opened_time`：业务主轴，可改（IF-IP-DOR-26）；**签发** `req_authored_time`：仅追溯，列表不主展示，**禁止改**；**停嘱** `stop_time`：可改（已停嘱）；见 **AS `BR-IP-ORD-031`** |
| **开立人 / 停嘱人** | 开立人 `requester_id`：开立后不变，展示跟在开立时间后；停嘱人 `stopper_id`：停嘱写入、取消停嘱清空，改停嘱时间不改，展示跟在停嘱时间后；**不**落改时人 |
| **长嘱改量** | **IF-IP-DOR-28** / **BR-IP-ORD-032**：停旧（可改 `stopTime`）或已停开新；新草稿 `basedOnTable=med_medication_request`、`basedOnId=源 id`；`medicationId` 固定；其余字段可覆盖；口服剩余续算见 **AS-IP-NUR-004-SDD BR-IP-EXE-038** / **DD-住院长嘱口服一盒一点剩余计数与改量-2026-08** |
| **医生来源** | 写路径与列表过滤固定 **`GenerateSource.DOCTOR_PRESCRIPTION`**（`doctor-prescription`）；**不展示**护士划价（**BR-IP-ORD-004**） |
| **事务口径** | 领域层不声明 `@Transactional`；**目标态**写方法声明 `@Transactional`。**首版实现（2026-05-28）**：`InpatientDoctorStationAdviceAppService` 写方法**无** `@Transactional` 注解（与门诊 **AS-OP-DOR-003** 同口径） |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/{methodName}`（`AppServiceControllerRegistrar`） |
| **业务异常码** | `BusinessException` + `ErrorCodeConstants`；AS **`EX-IP-ORD-*`** 为语义别名，见各 IF 失败码表 |

### 1.3 执行记录 UI（只读复用护士站 IF-IP-EXE-01）

| 项 | 约定 |
| --- | --- |
| **入口** | 医嘱列表行操作「执行记录」；病案首页临床侧栏嵌入同一医嘱组件时同入口 |
| **交互** | 弹窗 Tab：**全部**（默认）/ **已执行** / **不执行** / **取消执行**（**不含待执行**）；按该行医嘱 `requestId` 收窄（成组含成员）；默认截止=当日末；「全部」= 三终态并行拉取合并 |
| **HTTP** | `POST /api/app/nurseStationOrderExecute/listNurseStationOrderExecute`（**IF-IP-EXE-01**）；西药+中药 `medicineCategoryTab` 双拉合并后扁平时点行 |
| **边界** | **只读调阅**；**不**调用执行/不执行/取消执行写 IF；**不**新增 `InpatientDoctorStationAdviceAppService` 方法 |
| **可见性** | 读列表已支持在科优先 + 就诊主键回落（出院/病案调阅），与护士站写 Gate 分离 |

#### 1.2.1 文档语义码与 `ErrorCodeConstants` 对照

| 文档语义码 | 实现 `ErrorCode`（典型） |
| --- | --- |
| EX-IP-ORD-101 | （**废止**）原「定价来源未配置」 |
| EX-IP-ORD-102 / 103 | `CHARGE_ITEM_RULE_CONFLICT` / `CHARGE_ITEM_INVALID_ARGUMENT` |
| EX-IP-ORD-106 | `CHARGE_ITEM_NOT_FOUND` |
| EX-IP-ORD-201 | `ENCOUNTER_NOT_FOUND` / `ENCOUNTER_INVALID_ARGUMENT` / 查询参数非法 |
| EX-IP-ORD-205 | 外发网关失败（严格模式） |
| EX-IP-ORD-207 | 持久化/乐观锁冲突 |
| EX-IP-ORD-301 | `SERVICE_REQUEST_INVALID_ARGUMENT`（吸氧/心电余量，文案对齐 AS） |
| EX-IP-ORD-302 | `SERVICE_REQUEST_INVALID_ARGUMENT`（重复出院申请） |
| EX-IP-ORD-303 | `SERVICE_REQUEST_INVALID_ARGUMENT`（出院目录未维护） |
| EX-IP-ORD-304 | `MEDICATION_REQUEST_INVALID_STATUS` / `SERVICE_REQUEST_INVALID_ARGUMENT` / `MEDICATION_REQUEST_RULE_CONFLICT`（停嘱非 VERIFIED；取消停嘱：非 STOPPED/非长期/已停嘱校对） |
| EX-IP-ORD-305 | `SERVICE_REQUEST_INVALID_ARGUMENT`（取消停嘱：出院/转科门禁） |
| EX-IP-ORD-306 | `SERVICE_REQUEST_INVALID_ARGUMENT`（嘱托：`entrustContent` 空/空白，**BR-IP-ORD-028**） |
| EX-IP-ORD-307 | `MEDICATION_REQUEST_INVALID_ARGUMENT` / `SERVICE_REQUEST_INVALID_ARGUMENT`（改业务时间：种类非法 / 非已停嘱改停嘱 / 试图改签发） |
| EX-IP-ORD-308 | `MEDICATION_REQUEST_INVALID_ARGUMENT`（同组成组药品 `rate_code` 不一致，**BR-IP-ORD-033**） |

#### 1.2.2 与门诊 SDD 的复用关系

| 住院 IF | 门诊母版 SDD | 住院差异摘要 |
| --- | --- | --- |
| IF-IP-DOR-10 | §3.2 IF-DOR-10 | `InpatientEncounterContextQueryModel` 门控；**InpatientOrderListSkeleton** 列表；**无 DEVICE 行**；`therapyType` 含 **`DISCHARGE_MEDICATION`**；费用/库位来自护士执行后 Charge/Dispense；**嘱托行**装配 `entrustContent`（**BR-IP-ORD-028**）；出院带药行类型文案「出院带药」（**BR-IP-ORD-029**）；**长期药**装配 `firstDose`/`firstDoseUnitCode`（**BR-IP-ORD-030**）及 `doseSchedule`/`quantitySchedule`（**BR-IP-ORD-037**）；**TECH 诊疗**装配医技净执行进度 `tech*`（**BR-IP-ORD-038**） |
| IF-IP-DOR-11 | §3.3 IF-DOR-11 | `productTableNames` 仅 `med_medication_definition` + `wkf_activity_definition`；**含嘱托**固定项；出院带药 Tab 实务仅选药品；**无库存校验** |
| IF-IP-DOR-12 | §3.4 IF-DOR-12 | 同构读；**合管 activity → 空**；供执行侧装载；**开立暂存前绑定解析恒空**（**BR-IP-ORD-036**） |
| IF-IP-DOR-13 | §3.5 | 废止 → **AS-IP-DOR-001** `feeNatureOptions` |
| IF-IP-DOR-18 | §3.5.1（IF-DOR-18） | 同构 |
| IF-IP-DOR-19 | §3.x IF-DOR-19 | 同构 |
| IF-IP-DOR-14 | §3.6 IF-DOR-14 | 无耗材；**长/临/出院带药均仅 Request**；**不**挂费、**不**传 `dispenseLocationId`；**嘱托**必填 `entrustContent`（**BR-IP-ORD-028**）；出院带药 `therapyTypeCode=DISCHARGE_MEDICATION`（**BR-IP-ORD-029**）；**长期药**透传 `firstDose`/`firstDoseUnitCode`（**BR-IP-ORD-030**）及 `doseSchedule`/`quantitySchedule`（**BR-IP-ORD-037**；Gate **EX-IP-ORD-310**）；**同组频次 Gate（BR-IP-ORD-033）** |
| IF-IP-DOR-15 | §3.7 IF-DOR-15 | **住院长/临/出院带药**仅 sign（**不** FIFO / 费用升位） |
| IF-IP-DOR-16 | §3.8 IF-DOR-16 | **住院长/临/出院带药**仅 signOff / withdrawSigned；**不** rollback 签发后发放/费用 |
| IF-IP-DOR-17 | §3.9 IF-DOR-17 | 同构（含出院带药并组）；赋组前 **BR-IP-ORD-033** 频次一致 |
| IF-IP-DOR-20 | — | **住院专有**：长期停嘱（须 VERIFIED；写 `stop_time` + `stopper_id`；清停嘱校对字段） |
| IF-IP-DOR-25 | — | **住院专有**：长期取消停嘱（须未停嘱校对；清停嘱轴 + 停嘱校对字段） |
| IF-IP-DOR-26 | — | **住院专有**：改开立/停嘱时间（禁止改签发） |
| IF-IP-DOR-27 | — | **住院专有**：长期皮试药追加 companion |
| IF-IP-DOR-28 | — | **住院专有**：长期药品改量（停旧+同药草稿 + `based_on`） |
| IF-IP-DOR-21 | — | **住院专有**：出院申请 + 离院里程碑 |
| IF-IP-DOR-22 | §3.5.3 **AS-OP-DOR-003-SDD** IF-DOR-20 | 门诊母版同构；住院仅药品+诊疗（无耗材）；可选 `therapyTypeCode`（含 **`DISCHARGE_MEDICATION`**）；`InpatientOrderListMergeAssembler` + `InpatientDoctorStationAdviceListAssembler` |
| IF-IP-DOR-23 | §3.5.4 **AS-OP-DOR-003-SDD** IF-DOR-21 | 同构；住院 **IMP** 门控；**不**挂 `ChargeItem` 草稿；编辑 **`previousPrescriptionNo` → 删旧 → 取新 `AR` 号** |
| IF-IP-DOR-24 | §3.5.5 **AS-OP-DOR-003-SDD** IF-DOR-22 | 同构；`encounterId + categoryEnum`；Detail 经 **`ServiceRequestQueryService#mapDetailListModelsByServiceRequestIds`** |

---

## 2. 代码结构

### 2.1 包路径（类级清单）

```
health-application/src/main/java/com/openhis/health/application/
└── inpatient/
    └── doctorstation/
        └── advice/
            ├── service/
            │   └── InpatientDoctorStationAdviceAppService.java
            ├── assembler/
            │   ├── InpatientDoctorStationAdviceListAssembler.java
            │   └── InpatientDoctorStationAdviceCatalogAssembler.java
            ├── port/
            │   ├── LisPacsApplyGateway.java
            │   ├── LisPacsAdviceApplyCommand.java
            │   └── NoopLisPacsApplyGateway.java
            ├── query/
            │   └── InpatientDoctorStationAdviceOrderBindQueryService.java
            ├── request/
            │   ├── ListInpatientDoctorStationAdviceRequest.java
            │   ├── PageInpatientDoctorStationAdviceCatalogRequest.java
            │   ├── GetInpatientDoctorStationAdviceOrderBindInfoRequest.java
            │   ├── InitInpatientDoctorStationAdviceRequest.java
            │   ├── ListActivityExecutionLocationsRequest.java
            │   ├── SaveInpatientDoctorStationAdviceDraftBatchRequest.java
            │   ├── SignInpatientDoctorStationAdviceBatchRequest.java
            │   ├── WithdrawInpatientDoctorStationAdviceBatchRequest.java
            │   ├── UpdateInpatientDoctorStationMedicationAdviceGroupsRequest.java
            │   ├── StopLongTermInpatientDoctorStationAdviceRequest.java
            │   ├── CancelStopLongTermInpatientDoctorStationAdviceRequest.java
            │   ├── CreateDischargeInpatientDoctorStationAdviceRequest.java
            │   ├── PageInpatientDoctorStationAdviceRequest.java
            │   ├── SaveInpatientDoctorStationServiceApplyFormRequest.java
            │   └── ListInpatientDoctorStationServiceApplyFormsRequest.java
            ├── response/
            │   ├── ListInpatientDoctorStationAdviceResponse.java
            │   ├── PageInpatientDoctorStationAdviceCatalogResponse.java
            │   ├── GetInpatientDoctorStationAdviceOrderBindInfoResponse.java
            │   ├── InitInpatientDoctorStationAdviceResponse.java
            │   ├── ListActivityExecutionLocationsResponse.java
            │   ├── SaveInpatientDoctorStationAdviceDraftBatchResponse.java
            │   ├── SignInpatientDoctorStationAdviceBatchResponse.java
            │   ├── WithdrawInpatientDoctorStationAdviceBatchResponse.java
            │   ├── UpdateInpatientDoctorStationMedicationAdviceGroupsResponse.java
            │   ├── StopLongTermInpatientDoctorStationAdviceResponse.java
            │   ├── CancelStopLongTermInpatientDoctorStationAdviceResponse.java
            │   ├── CreateDischargeInpatientDoctorStationAdviceResponse.java
            │   ├── PageInpatientDoctorStationAdviceResponse.java
            │   ├── SaveInpatientDoctorStationServiceApplyFormResponse.java
            │   └── ListInpatientDoctorStationServiceApplyFormsResponse.java
            ├── dto/
            │   ├── InpatientDoctorStationAdviceRowDto.java
            │   ├── ServiceApplyFormLineRequest.java
            │   ├── ServiceApplyFormDetailItemRequest.java
            │   ├── ServiceApplyFormHeaderDto.java
            │   ├── ServiceApplyFormLineDto.java
            │   ├── ServiceRequestDetailSummaryDto.java
            │   ├── InpatientDoctorStationAdviceCatalogRowDto.java
            │   ├── InpatientDoctorStationAdviceDraftLineRequest.java
            │   ├── StopLongTermAdviceLineRequest.java
            │   ├── CancelStopLongTermAdviceLineRequest.java
            │   └── InpatientDoctorStationAdviceResourceKeyDto.java
            └── mapper/
                ├── InpatientDoctorStationAdviceMapper.java
                └── InpatientDoctorStationAdviceMapperImpl.java
└── querysupport/
    ├── encounter/
    │   └── EncounterContextReader.java
    ├── product/
    │   ├── ProductDefinitionCatalogPageLoader.java
    │   └── ProductDefinitionBatchLoader.java
    └── order/（列表骨架，经 common 装配）
        └── InpatientOrderListMergeAssembler.java（`module.common.assembler.order`）
zxplat/health-application/src/main/java/com/openhis/health/application/
    └── orchestration/advice/
        ├── DoctorStationSkinTestSyncOrchestrator.java   ← IF-IP-DOR-14/15/16（**BR-IP-ORD-024**，**不**挂费；与门诊共用）
        └── ServiceApplyFormOrchestrationSupport.java            ← IF-IP-DOR-23/24（与门诊 IF-DOR-21/22 共享）
```

**皮试编排边界**：`InpatientDoctorStationAdviceAppService` 私有方法 `toSkinTestSpec` / `toSignTargetSpecs` / `fromSignTargetSpecs` 完成 DTO ↔ `SkinTestMedicationDraftSpec` / `AdviceSignTargetSpec`。

**共享（与门诊医嘱共用）**

- `com.zxplat.domain.module.common.dto.product.ProductDefinitionCatalogUnitBandDto`
- `com.zxplat.domain.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler`
- `com.zxplat.domain.module.common.assembler.product.DoctorStationAdviceInventoryUnitQuantityHelper`（IF-IP-DOR-11 占用预减，**规则 C/D** / **AS `BR-IP-ORD-018`**；含 **`copyOccupancyRemaining` / `consumeLocationOccupancyForLot`** 同库多批顺序消耗预占，母版 **AS-OP-DOR-003-SDD v3.49** §3.3 规则 **D**）
- **`ProductDefinitionCatalogUnitBandsAssembler#resolveUnitDisplayName`** / **`#resolveDoseUnitDisplayName`**（IF-IP-DOR-10/11/12 单位展示名，**AS `BR-IP-ORD-022`** / 门诊 **BR-ORD-022**）
- `com.zxplat.domain.share.constants.ProductDefinitionCatalogUnitBandConstants`
- `com.zxplat.domain.module.common.assembler.advice.ServiceAdviceDraftCommandsupport（**IF-IP-DOR-14**：**`requireQuantity`/`requireUnitCode`**（CREATE）、**`resolveQuantityForModify`/`resolveUnitCodeForModify`**（UPDATE））
- **`com.zxplat.domain.application.shared.advice.ServiceApplyFormOrchestrationsupport** / **`ServiceApplyFormAssembler`**（门诊 `outpatient/.../assembler`，住院 **复用**）：**IF-IP-DOR-23/24** 与门诊 **IF-DOR-21/22** 共享编排与 DTO 互转（见 **AS-OP-DOR-003-SDD v3.25** §2.1/§2.2）
- `com.zxplat.domain.application.shared.advice.AdviceExtensionJsonsupport（**IF-IP-DOR-10**：`InpatientDoctorStationAdviceListAssembler` 解析 `contentJson` / 套餐子项）
- **`com.zxplat.domain.application.shared.advice.DoctorStationSkinTestSyncOrchestrator`**（**BR-IP-ORD-024**；门诊/住院共用；`ChargePolicy` 区分挂费；AppService 边界 `DTO↔SkinTestMedicationDraftSpec`/`AdviceSignTargetSpec`；住院侧**跳过** `createDraftChargeItemFromOrder`）
- **`com.zxplat.domain.application.shared.advice.SkinTestCompanionDisplaySupport`**：列表 companion 展示名追加「（关联：药名 规格）」；`formatLinkedMedicationLabel` / `appendLinkedMedicationHint`
- **`com.zxplat.domain.application.shared.advice.InpatientNursingOrderGate`**（**BR-IP-ORD-026** / **BR-IP-EXE-027**：护理级别仅长期、同就诊唯一有效、护士站禁止执行；**⑥ 开立入口/每日一次为前端约束**，后端透传 `rateCode`）
- **`com.zxplat.domain.application.shared.advice.InpatientEntrustOrderGateSupport`**（**BR-IP-ORD-028/029**：识别 `category_enum=52` / 名称「嘱托」、`entrustContent` 必填；出院带药禁止诊疗行；**`resolveServiceAdviceDisplayName`** 供医生站列表与护士站 **OrderView** 嘱托内容列同源展示）
- **`com.zxplat.domain.application.shared.advice.InpatientMedicationAdviceGroupRateGate`**（**BR-IP-ORD-033**：同 `group_id` 药品 `rate_code` 须一致；IF-IP-DOR-14/17；失败 **EX-IP-ORD-308**）

### 2.2 依赖注入表

| 注入类型 | 类名（FQCN） | 职责 | 来源 |
| --- | --- | --- | --- |
| 应用服务 | `InpatientDoctorStationAdviceAppService` | IF-IP-DOR-10～22 编排入口 | 本模块（**已落地**） |
| Mapper | `InpatientDoctorStationAdviceMapper` + `InpatientDoctorStationAdviceMapperImpl` | Request/Command/Response、住院就诊门控 | 本模块（**已落地**） |
| Facade | `EncounterContextReader` | 住院就诊上下文门控（`mustGetInpatientContextByEncounterId`） | `application.shared.encounter` **已落地** |
| QueryService | `EncounterQueryService` | 薄就诊查询（主档存在性等；**不**承载患者/科室/诊断展示列） | `domain.administration.encounter` **已落地** |
| Loader | `ProductDefinitionCatalogPageLoader` | IF-IP-DOR-11 目录分页（`pageProductDefinition`） | `application.shared.product` **已落地** |
| Loader | `ProductDefinitionBatchLoader` | IF-IP-DOR-10/12 批量目录展示（`loadAsMap`） | `application.shared.product` **已落地** |
| Assembler | `InpatientOrderListMergeAssembler` | IF-IP-DOR-10：药/诊 QueryModel → `InpatientOrderListSkeleton` | `module.common.assembler.order` **已落地** |
| QueryService | `MedicationRequestQueryService` + `ServiceRequestQueryService` | IF-IP-DOR-10：`listMedicationRequestsByEncounterForDoctorStation` + `listServiceRequestsByGenerateSource`（**不含 DeviceRequest**） | `domain.medication.medicationrequest` / `domain.workflow.servicerequest` **已落地** |
| 域查询 | `MedicationRequestQueryService` | 列表费用/过渡装配、`listMedicationRequestsByEncounterForDoctorStation` | `domain.medication.medicationrequest` **已落地** |
| 域查询 | `ServiceRequestQueryService` | 列表/出院重复校验；**皮试联动**：`listServiceRequestsByBasedOnAnchors` / `findByBasedOnTableAndBasedOnId` | `domain.workflow.servicerequest` **已落地** |
| 域查询 | `ActivityDefinitionQueryService` | **IF-IP-DOR-14 皮试联动**：`requireIdByExactName(SKIN_TEST_INSPECTION_ACTIVITY_NAME)` | `domain.workflow.activitydefinition` **已落地** |
| 域查询 | `AllergyIntoleranceQueryService` | **IF-IP-DOR-10** 皮试结果：`getRecordInfo(encounterId)` | `domain.clinical.allergyintolerance` **已落地** |
| 域查询 | `ChargeItemQueryService` | 列表：护士执行后 ORDER_EXECUTE 费用汇总 | `domain.administration.chargeitem` **已落地** |
| 域查询 | `ChargeItemDefinitionQueryService` | 目录定价展示 | `domain.administration.chargeitemdefinition` **已落地** |
| 域查询 | `ServiceLocationTemplateQueryService` | IF-DOR-11：适用候选库位（`listApplicableDistributionLocationIds`）+ 默认库位（`matchDefaultDistributionLocation`）；`distribution_category_code` = 目录 **`categoryCode`**（药品/耗材类别） | `domain.custom.servicelocationtemplate` **已落地** |
| 域查询 | `CodeSystemQueryService` | IF-IP-DOR-18 频次/用法 | `domain.terminology` **已落地** |
| 域查询 | `ActivityExecutionLocationQueryService` | IF-IP-DOR-19 | `domain.custom.activityexecutionlocation` **已落地** |
| 域查询 | `ConditionDefinitionQueryService` | 列表诊断展示名 | `domain.clinical.conditiondefinition` **已落地** |
| 域查询 | `MedicationDispenseQueryService` | 列表：护士执行后发放库位展示 | `domain.medication.medicationdispense` **已落地** |
| 域查询 | `AccountQueryService` | 临时费用草稿账户 | `domain.administration.account` **已落地** |
| 域查询 | `LocationQueryService` | 库位展示名 | `domain.administration.location` **已落地** |
| 域写 | `MedicationRequestDomainService` | 药嘱草稿/签发/签退/组号/停嘱 | `domain.medication.medicationrequest` **已落地** |
| 域写 | `ServiceRequestDomainService` | 诊疗草稿/签发/签退/停嘱/出院 | `domain.workflow.servicerequest` **已落地** |
| 域写 | `EncounterHistoryDomainService` | IF-IP-DOR-21 `recordDischargeMilestone` | `domain.custom.encounterhistory` **已落地** |
| 域写 | `PatientDomainService` | IF-IP-DOR-21 死亡出院 `markPatientDeceased` | `domain.administration.patient` **已落地** |
| 应用装配 | `InpatientDoctorStationAdviceListAssembler` | InpatientOrderListSkeleton + 费用/术语/产品QueryService预热 → 列表行；**`unitName`/`doseUnitName`** 经 **`ProductDefinitionCatalogUnitBandsAssembler#resolveUnitDisplayName`**（**BR-IP-ORD-022**） | 本模块 **已落地** |
| 应用装配 | `InpatientDoctorStationAdviceCatalogAssembler` | IF-IP-DOR-11（无库存门控分支；占用 **`DoctorStationAdviceInventoryUnitQuantityHelper#merge*OccupancyInSplitUnits`**，与门诊 **规则 C** 同构） | 本模块 **已落地** |
| Gate | `InpatientEntrustOrderGateSupport` | IF-IP-DOR-14 嘱托正文必填（**BR-IP-ORD-028**）；出院带药禁诊疗行（**BR-IP-ORD-029**）；**`resolveServiceAdviceDisplayName`** 供医生站/护士站列表嘱托内容列（**BR-IP-ORD-028④**） | `application.shared.advice` **已落地** |
| Gate | `InpatientMedicationAdviceGroupRateGate` | IF-IP-DOR-14/17 同组成组药品频次一致（**BR-IP-ORD-033**；**EX-IP-ORD-308**） | `application.shared.advice` **已落地** |
| 应用查询 | `InpatientDoctorStationAdviceOrderBindQueryService` | IF-IP-DOR-12（`listBindingsBySourceItemKey`：合管空、未合管模板）；**`listDraftBindItems` 恒空**（**BR-IP-ORD-036**） | 本模块 **已落地** |
| 外发 | `LisPacsApplyGateway` | IF-IP-DOR-15 检验检查外发（可选） | 本模块 **已落地（端口占位可接网关）** |
| 基础设施 | `BusinessSequenceService` | **临时药品处方号** → `getSeqByDay(COMPOSITE_PRESCRIPTION)`（**`CP`**）；药嘱 **`bus_no`** 仍为 **`MR`**（**禁止**混用前缀）；**`sign_code` / `ADVICE_SIGN` 已废止** | zxplat-framework **已落地** |
| 操作人 | `CurrentOperatorResolver` | 写路径 `operatorId`；列表 `onlyMine` 过滤（**B.6.5**） | 本模块 **已落地** |

> **刻意不注入（住院本期）**：`DeviceRequestDomainService`、`DeviceDispenseDomainService`、`DeviceRequestQueryService`——与 **BR-IP-ORD-012** 一致。

### 2.3 枚举、字典与值集（DM 与代码可追溯）

| 类别 | 代码实现锚点 | DM 追溯锚点 | 说明 |
| --- | --- | --- | --- |
| 长/临/出院带药 | `com.zxplat.domain.share.enums.TherapyType`（含 **`DISCHARGE_MEDICATION`**，**已落地**；**G-IP-DOR-003-05 Closed**） | `DM-MED-003` §4.2 `therapy_enum`（v1.3.24）；`DM-WKF-004` §4.5（v1.1.23） | IF-IP-DOR-10/14/15/16/17/22；`LONG_TERM`/`TEMPORARY`/`DISCHARGE_MEDICATION`；出院带药 quantity 同 TEMPORARY（**BR-IP-ORD-029**） |
| 医嘱生成来源 | `com.zxplat.domain.share.enums.GenerateSource` | `DM-MED-003` §4.5；`DM-WKF-004` §4.6 | 写/列表固定 `DOCTOR_PRESCRIPTION` |
| 请求状态 | `com.zxplat.domain.share.enums.RequestStatus` | 各 DM §5 状态机 | 草稿/签发/停嘱 |
| 收费状态 | `com.zxplat.domain.share.enums.ChargeItemStatus` | `DM-ADM-011` §3 `status_enum` | IF-IP-DOR-10 列表展示（护士执行后） |
| 服务申请类目（出院/转科/护理级别/嘱托） | `ServiceRequest.category_enum`：`34` 出院、`32` 转科、`26` 护理级别、**`52` 嘱托**（`ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST`；**已落地**） | `DM-WKF-004` §4.3；**BR-SRQ-010** / **BR-IP-ORD-026** / **BR-IP-ORD-028** | IF-IP-DOR-21 / 列表；护理级别 / **嘱托** **IF-IP-DOR-10/14/15** |
| 嘱托正文 | `SaveInpatientServiceAdviceDraftLinePayload.entrustContent`；`InpatientDoctorStationAdviceRowDto.entrustContent` | `DM-WKF-004` §3 `entrust_content`；AS **BR-IP-ORD-028** | IF-IP-DOR-10/14/22；嘱托行医嘱内容展示 |
| 嘱托活动名常量 | `GeneralSharedConstants.ENTRUST_ACTIVITY_NAME`（值 **`嘱托`**；**已落地**） | AS **BR-IP-ORD-028①**；`DM-WKF-001` 目录名 | 与 `category_code=52` 双锚 |
| 列表 therapy 筛选 | `ListInpatientDoctorStationAdviceRequest.therapyTypeCode` | AS **BR-IP-ORD-011** | 映射 `TherapyType#getCode()` → `MedicationRequestQueryService` / `ServiceRequestQueryService` |
| QueryService generate_source | `InpatientOrderListSkeleton.generateSourceEnum` | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §2 `generate_source_enum` | Q-ORD-007 |
| 账户类型 | `com.zxplat.domain.share.enums.AccountTypeCode` | `DM-ADM-009` §4.3 | （住院开嘱写路径**不**使用） |
| 目录表名白名单 | `TableNameConstants.MED_MEDICATION_DEFINITION`；`TableNameConstants.WKF_ACTIVITY_DEFINITION`（常量名以仓库为准） | AS **BR-IP-ORD-012**；`ProductDefinition` QueryService §2 | IF-IP-DOR-11 **禁止** `adm_device_definition` |
| 行操作码 | `DoctorStationAdviceConstants.LINE_OP_*`（**已落地**） | AS IF-IP-DOR-14 批次语义 | CREATE/UPDATE/DELETE |
| 失败码 | `ErrorCodeConstants` | AS §5 `EX-IP-ORD-*` | §1.2.1 |
| 改时种类 | `ChangeInpatientDoctorStationAdviceBusinessTimeRequest.timeKind`（取值 **`OPENED`** / **`STOP`**） | AS **BR-IP-ORD-031**；`DM-MED-003`/`DM-WKF-004` 改时命令 | IF-IP-DOR-26；禁止签发 |
| 停嘱人 | `InpatientDoctorStationAdviceRowDto.stopperId` / `stopperName`（**已落地**） | `DM-MED-003`/`DM-WKF-004` §3 `stopper_id` | IF-IP-DOR-10/20/22/25/26 |
| 出院方式·死亡 | `DoctorStationAdviceConstants.OUT_WAY_CODE_DEATH`（值 **`5`**） | 术语 `out_way_code`；`DM-ADM-001` §6.1.9 `markPatientDeceased` / **BR-PAT-011** | IF-IP-DOR-21；列表主文案「死亡出院」（**BR-IP-ORD-017**） |

### 2.4 领域方法白名单（聚合/方法 → IF）

| 域服务#方法 | 用于 IF |
| --- | --- |
| `MedicationRequestDomainService#createMedicationOrderDraft(CreateMedicationOrderDraftCommand)` | IF-IP-DOR-14 |
| `MedicationRequestDomainService#changeMedicationOrderDraft(ChangeMedicationOrderDraftCommand)` | IF-IP-DOR-14 |
| `MedicationRequestDomainService#removeMedicationOrderDraft(RemoveMedicationOrderDraftCommand)` | IF-IP-DOR-14 |
| `MedicationRequestDomainService#signMedicationOrder(SignMedicationOrderCommand)` | IF-IP-DOR-15 |
| `MedicationRequestDomainService#signOffMedicationOrder(SignOffMedicationOrderCommand)` | IF-IP-DOR-16 |
| `MedicationRequestDomainService#adjustMedicationOrderGroup(AdjustMedicationOrderGroupCommand)` | IF-IP-DOR-17 |
| `MedicationRequestDomainService#stopLongTermMedicationOrder(StopLongTermMedicationOrderCommand)` | IF-IP-DOR-20 |
| `MedicationRequestDomainService#cancelStopLongTermMedicationOrder(CancelStopLongTermMedicationOrderCommand)` | IF-IP-DOR-25 |
| `MedicationRequestDomainService#changeMedicationRequestBusinessTime(ChangeMedicationRequestBusinessTimeCommand)` | IF-IP-DOR-26 |
| `ServiceRequestDomainService#createServiceRequestDraft(CreateServiceRequestDraftCommand)` | IF-IP-DOR-14；**皮试联动**须透传 **`basedOnTable`/`basedOnId`**（见 **§3.8.1**） |
| `ServiceRequestDomainService#modifyServiceRequestDraft(ModifyServiceRequestDraftCommand)` | IF-IP-DOR-14 |
| `ServiceRequestDomainService#removeServiceRequestDraft(RemoveServiceRequestDraftCommand)` | IF-IP-DOR-14 |
| `ServiceRequestDomainService#signServiceRequest(SignServiceRequestCommand)` | IF-IP-DOR-15 |
| `ServiceRequestDomainService#withdrawSignedServiceRequest(WithdrawSignedServiceRequestCommand)` | IF-IP-DOR-16 |
| `ServiceRequestDomainService#stopServiceRequest(StopServiceRequestCommand)` | IF-IP-DOR-20 |
| `ServiceRequestDomainService#cancelStopServiceRequest(CancelStopServiceRequestCommand)` | IF-IP-DOR-25 |
| `ServiceRequestDomainService#changeServiceRequestBusinessTime(ChangeServiceRequestBusinessTimeCommand)` | IF-IP-DOR-26 |
| `ServiceRequestDomainService#createDischargeRequest(CreateDischargeRequestCommand)` | IF-IP-DOR-21 |
| `EncounterHistoryDomainService#recordDischargeMilestone(RecordDischargeMilestoneCommand)` | IF-IP-DOR-21 |
| `PatientDomainService#markPatientDeceased(MarkPatientDeceasedCommand)` | IF-IP-DOR-21（仅 `outWayCode=5`） |

**查询白名单**

| 查询服务#方法 | 用于 IF |
| --- | --- |
| `EncounterContextReader#mustGetInpatientContextByEncounterId` | IF-IP-DOR-10/14/15/16/17/18/19/20/21/25/26 |
| `MedicationRequestQueryService / ServiceRequestQueryService / （住院医嘱列表不查 DeviceRequest）` | IF-IP-DOR-10 |
| `MedicationRequestQueryService#listMedicationRequestsByEncounterForDoctorStation` | IF-IP-DOR-10（过渡装配费用/缺字段） |
| `ServiceRequestQueryService#listServiceRequestsByGenerateSource` | IF-IP-DOR-10（医生来源明细） |
| `ServiceRequestQueryService#mapListModelsByIds` | IF-IP-DOR-10（出院/转科行补全：含未带 `generate_source` 的历史数据） |
| `EncounterHistoryQueryService#listMilestonesByEncounter` | IF-IP-DOR-10（出院行 `outWayCode` 回源） |
| `ServiceRequestQueryService#checkDuplicateTransferOrDischargeRequest` | IF-IP-DOR-21 / IF-IP-DOR-25 |
| `MedicationRequestQueryService#loadMedicationRequestById` | IF-IP-DOR-25（药品长临/状态 Pre） |
| `ChargeItemQueryService#listChargeItemsByEncounterId` | IF-IP-DOR-10（护士执行后费用 rollup） |
| `ProductDefinitionCatalogPageLoader#pageProductDefinition` | IF-IP-DOR-11 |
| `CodeSystemQueryService`（频次/用法，同门诊 IF-DOR-18） | IF-IP-DOR-18 |
| `ActivityExecutionLocationQueryService#listExecutionRulesForActivityResolution` | IF-IP-DOR-19 |
| `ProductDefinitionBatchLoader#loadAsMap`（解析诊疗大类） | IF-IP-DOR-19 |
| `ConditionDefinitionQueryService#getConditionDefinitionListByIds` | IF-IP-DOR-10 |
| `MedicationDispenseQueryService#listMedicationDispenseIdsByMedicationRequest` | IF-IP-DOR-10/16 |
| `AccountQueryService#getSelfPayAccount` | IF-IP-DOR-14 |
| `LocationQueryService#mapNamesByIds` | IF-IP-DOR-10/19 |
| `MedicationRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` | IF-IP-DOR-22（历史 Tab） |
| `ServiceRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` | IF-IP-DOR-22（历史 Tab） |
| `InpatientOrderListMergeAssembler#mergeDoctorStationOrders` | IF-IP-DOR-10/22 |

### 2.5 Mapper / 装配器白名单（目标态）

| 类型#方法 | 用于 IF |
| --- | --- |
| `InpatientDoctorStationAdviceMapper#validateInpatientEncounterPatientGate` | 各 IF 写/读门控 |
| `InpatientDoctorStationAdviceMapper#validateInpatientEncounterClassImp` | 各 IF |
| `InpatientDoctorStationAdviceMapper#validateInpatientEncounterWritableGate` | IF-IP-DOR-14/15/16/17/20/21/25（含 **BR-IP-ORD-027**：`PENDING`/`COMPLETED`/`DISCHARGED` → **EX-IP-ORD-107**） |
| `InpatientEntrustOrderGateSupport#requireEntrustContentIfEntrustActivity`（**已落地**） | IF-IP-DOR-14（**BR-IP-ORD-028**；空正文 → **EX-IP-ORD-306**） |
| `InpatientMedicationAdviceGroupRateGate#assertDraftBatchConsistent` / `#assertAssignGroupConsistent`（**已落地**） | IF-IP-DOR-14/17（**BR-IP-ORD-033**；不一致 → **EX-IP-ORD-308**） |
| `InpatientEntrustOrderGateSupport#assertNotDischargeMedicationForServiceLine`（**已落地**） | IF-IP-DOR-14（**BR-IP-ORD-029**；出院带药禁诊疗行 → `SERVICE_REQUEST_INVALID_ARGUMENT`） |
| `InpatientDoctorStationAdviceMapper#toProductDefinitionPageQuery` | IF-IP-DOR-11 |
| `InpatientDoctorStationAdviceMapper` 各 `to*Command`（Request 草稿/签发/签退/停嘱/取消停嘱/出院） | IF-IP-DOR-14/15/16/17/20/21/25 |
| `InpatientDoctorStationAdviceListAssembler#assembleRows` | IF-IP-DOR-10 |
| `InpatientDoctorStationAdviceListAssembler#assembleAdviceQueryRows` | IF-IP-DOR-22 |
| `PagedResultBuilder#buildFromFullList` | IF-IP-DOR-22（内存分页） |
| `InpatientDoctorStationAdviceCatalogAssembler#assemblePageContent` | IF-IP-DOR-11 |
| `InpatientDoctorStationAdviceOrderBindQueryService#listBindingsBySourceItemKey` | IF-IP-DOR-12（合管空） |
| `InpatientDoctorStationAdviceOrderBindQueryService#listDraftBindItems` | 暂存前绑定解析（**恒空**，**BR-IP-ORD-036**） |
| `LisPacsApplyGateway#submitIfLabOrImaging` | IF-IP-DOR-15（可选） |

---

## 3. 按接口 ID 命名展开

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-IP-DOR-10 | `listInpatientDoctorStationAdvice` | 加载当前住院就诊医嘱统一列表 | `POST /api/app/inpatientDoctorStationAdvice/listInpatientDoctorStationAdvice` |
| IF-IP-DOR-11 | `pageInpatientDoctorStationAdviceCatalog` | 分页检索可开立医嘱项目目录 | `POST /api/app/inpatientDoctorStationAdvice/pageInpatientDoctorStationAdviceCatalog` |
| IF-IP-DOR-12 | `getInpatientDoctorStationAdviceOrderBindInfo` | 查询医嘱绑定信息 | `POST /api/app/inpatientDoctorStationAdvice/getInpatientDoctorStationAdviceOrderBindInfo` |
| — | `resolveInpatientDoctorStationAdviceDraftBinds` | 暂存前绑定解析（**恒空**，**BR-IP-ORD-036**） | `POST /api/app/inpatientDoctorStationAdvice/resolveInpatientDoctorStationAdviceDraftBinds` |
| ~~IF-IP-DOR-13~~ | — | **废止**（费用性质见 **AS-IP-DOR-001**） | — |
| IF-IP-DOR-18 | `initInpatientDoctorStationAdvice` | 医嘱开立页初始化（频次/用法） | `POST /api/app/inpatientDoctorStationAdvice/initInpatientDoctorStationAdvice` |
| IF-IP-DOR-19 | `listActivityExecutionLocations` | 按诊疗定义查询可执行科室规则 | `POST /api/app/inpatientDoctorStationAdvice/listActivityExecutionLocations` |
| IF-IP-DOR-14 | `saveInpatientDoctorStationAdviceDraftBatch` | 暂存医嘱变更批次 | `POST /api/app/inpatientDoctorStationAdvice/saveInpatientDoctorStationAdviceDraftBatch` |
| IF-IP-DOR-15 | `signInpatientDoctorStationAdviceBatch` | 签发医嘱批次 | `POST /api/app/inpatientDoctorStationAdvice/signInpatientDoctorStationAdviceBatch` |
| IF-IP-DOR-16 | `withdrawInpatientDoctorStationAdviceBatch` | 签退医嘱批次 | `POST /api/app/inpatientDoctorStationAdvice/withdrawInpatientDoctorStationAdviceBatch` |
| IF-IP-DOR-17 | `updateInpatientDoctorStationMedicationAdviceGroups` | 更新药品医嘱组号 | `POST /api/app/inpatientDoctorStationAdvice/updateInpatientDoctorStationMedicationAdviceGroups` |
| IF-IP-DOR-20 | `stopLongTermInpatientDoctorStationAdvice` | 停嘱（长期医嘱） | `POST /api/app/inpatientDoctorStationAdvice/stopLongTermInpatientDoctorStationAdvice` |
| IF-IP-DOR-25 | `cancelStopLongTermInpatientDoctorStationAdvice` | 取消停嘱（长期医嘱） | `POST /api/app/inpatientDoctorStationAdvice/cancelStopLongTermInpatientDoctorStationAdvice` |
| IF-IP-DOR-26 | `changeInpatientDoctorStationAdviceBusinessTime` | 改开立/停嘱时间 | `POST /api/app/inpatientDoctorStationAdvice/changeInpatientDoctorStationAdviceBusinessTime` |
| IF-IP-DOR-27 | `appendSkinTestCompanionForLongTermMedication` | 长期皮试药追加皮试检查 companion | `POST /api/app/inpatientDoctorStationAdvice/appendSkinTestCompanionForLongTermMedication` |
| IF-IP-DOR-28 | `changeLongTermMedicationDoseInpatientDoctorStationAdvice` | 长期药品改量（停旧+同药草稿） | `POST /api/app/inpatientDoctorStationAdvice/changeLongTermMedicationDoseInpatientDoctorStationAdvice` |
| IF-IP-DOR-21 | `createDischargeInpatientDoctorStationAdvice` | 开立出院医嘱 | `POST /api/app/inpatientDoctorStationAdvice/createDischargeInpatientDoctorStationAdvice` |
| IF-IP-DOR-22 | `pageInpatientDoctorStationAdvice` | 分页查询患者全类型医嘱（当前医嘱/历史 Tab） | `POST /api/app/inpatientDoctorStationAdvice/pageInpatientDoctorStationAdvice` |
| IF-IP-DOR-23 | `saveInpatientDoctorStationServiceApplyForm` | 保存申请单（整张） | `POST /api/app/inpatientDoctorStationAdvice/saveInpatientDoctorStationServiceApplyForm` |
| IF-IP-DOR-24 | `listInpatientDoctorStationServiceApplyForms` | 查询申请单列表（按 categoryEnum） | `POST /api/app/inpatientDoctorStationAdvice/listInpatientDoctorStationServiceApplyForms` |

#### 3.1.1 编排约定（应用层如何实现 AS §4）

| 阶段 | 含义 | 失败时 |
| --- | --- | --- |
| **Gate** | 权限/租户/参数；住院就诊 IMP 门控；拒绝耗材类型 | 不进入后续 |
| **Pre** | 只读：InpatientEncounterContextQueryModel、InpatientOrderListSkeleton 列表、费用锚点、重复出院校验 | 抛异常，**禁止 Cmd** |
| **Cmd** | 领域写 + 事件 | 事务回滚 §5 |
| **Asm** | Mapper/Assembler 纯装配 | — |

**记法**：`MedicationRequestQueryService / ServiceRequestQueryService / （住院医嘱列表不查 DeviceRequest）(Long encounterId, String therapyEnum)`。

**重要说明**：先读完各 IF「领域编排序列」，按序号执行。

---

### 3.2 IF-IP-DOR-10 `listInpatientDoctorStationAdvice`（加载当前住院就诊医嘱统一列表）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationAdviceAppService#listInpatientDoctorStationAdvice` |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/listInpatientDoctorStationAdvice` |

#### Request（`ListInpatientDoctorStationAdviceRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 住院就诊主键 |
| patientId | Long | 是 | 与 `InpatientEncounterContextQueryModel.patientId` 一致 |
| therapyTypeCode | String | 否 | `TherapyType#getCode()`：`LONG_TERM` / `TEMPORARY` / **`DISCHARGE_MEDICATION`**；空表示不过滤 |

#### Response（`ListInpatientDoctorStationAdviceResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| rows | List\<InpatientDoctorStationAdviceRowDto\> | 统一列表（**仅 MEDICATION + SERVICE**） |
| totalCount | Integer | 行数 |

#### DTO（`InpatientDoctorStationAdviceRowDto`）— 核心字段

> 与门诊 **`DoctorStationAdviceRowDto`**（`AS-OP-DOR-003-SDD` §3.2）同构，并**强制**增补住院字段；**未在下表逐列列出**的栏位（如 `dose`/`rateName`/`lineDisplayUnitPrice`/`displayStatusEnum` 等）以门诊 §3.2 全量字段表为准，住院列表 DTO **不下发** `requestExtensions`（扩展由 `packageChildren` + 窄列字段承载）。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| listRowKey | String | `requestId` + SOH + `productTableName` |
| requestId | Long | 药/诊请求主键 |
| encounterId | Long | 就诊主键 |
| patientId | Long | 患者主键 |
| productDefinitionId | Long | 与 IF-IP-DOR-11 对齐的**定义表**主键 |
| productTableName | String | **`med_medication_definition` / `wkf_activity_definition`**（定义表物理名，与 IF-IP-DOR-11、暂存行锚点一致；**非** `med_medication_request` / `wkf_service_request`） |
| therapyTypeCode | String | **住院**：`LONG_TERM` / `TEMPORARY` / **`DISCHARGE_MEDICATION`** |
| statusEnum | String | `RequestStatus#getCode()` |
| generateSourceEnum | String | 列表仅 `doctor-prescription` |
| sortNumber | Integer | **BR-IP-ORD-011** / **BR-ORD-018**（与门诊同型） |
| packageChildren | List\<AdvicePackageChildItemDto\> | 诊疗套餐子项（子表事实；**禁止**下发 `children_json` 串） |
| reqOpenedTime | String | 开立时间 ISO local（`req_opened_time`）；**主展示**（**BR-IP-ORD-031**） |
| reqAuthoredTime | String | 签发时间 ISO local（`req_authored_time`；未签发可 null）；**仅追溯，列表默认不展示** |
| authoredById | Long | 开立人主键（落库 `requester_id`；DTO 历史字段名 `authoredBy*`）；改开立时间不改 |
| authoredByName | String | 开立人姓名；UI 跟在开立时间后 |
| chargeStatusEnum | String | 护士执行后有 ORDER_EXECUTE 费用时归并展示；未执行可为 null |
| lineDisplayTotalPrice | BigDecimal | 护士执行后费用汇总 |
| stopTime | String | 停嘱时间 ISO local（`stop_time`）；**主展示** |
| stopperId | Long | 停嘱人主键（`stopper_id`；未停 null）；改停嘱时间不改 |
| stopperName | String | 停嘱人姓名；UI 跟在停嘱时间后 |
| conditionDefinitionName | String | 术语/定义展示名（由 **`encounterDiagnosisIds`** 首位 id 解析） |
| encounterDiagnosisIds | String | 绑定就诊诊断 ids 有序串（`encounter_diagnosis_ids`；**主诊 id 第一**；编辑回填） |
| adviceDisplayName | String | 目录展示名；**嘱托行**仍可为「嘱托」（身份），**内容列不以本字段为准**（见 `entrustContent`） |
| activityTypeCode | String | 诊疗 `ActivityType#getCode()`（嘱托种子多为 **`5` 其他**；**不以本字段识别嘱托**） |
| activityTypeName | String | 诊疗活动类型展示名 |
| entrustContent | String | **`wkf_service_request.entrust_content`**；**嘱托行必填回显**；常规诊疗可为备注嘱托 |
| methodCode | String | 给药途径 code |
| methodName | String | 给药途径展示名 |
| rateCode | String | 频次 code |
| rateName | String | 频次展示名 |
| speedCode | String | 输液滴速 code（术语 `speed_code`；可空） |
| speedName | String | 输液滴速展示名 |
| quantity | BigDecimal | 数量 |
| unitCode | String | 单位编码 |
| unitName | String | 单位展示名（**BR-IP-ORD-022**；与门诊 §3.2 同解析链） |
| doseUnitCode | String | 剂量单位编码（药品） |
| doseUnitName | String | 剂量单位展示名（药品） |
| firstDose | BigDecimal | **长期药品**首日/首次用量（`first_dose`；可空；**BR-IP-ORD-030**） |
| firstDoseUnitCode | String | 首日用量单位编码（与 `firstDose` 成对） |
| firstDoseUnitName | String | 首日用量单位展示名（有 `firstDoseUnitCode` 时解析；**BR-IP-ORD-022** 同链） |
| doseSchedule | String | **长期药品**分次剂量序列（计量、逗号分隔；可空；**BR-IP-ORD-037**） |
| quantitySchedule | String | **长期药品**分次用量序列（拆零、逗号分隔；与 `doseSchedule` 同序；**BR-IP-ORD-037**） |
| groupId | Long | 药品组号 |
| tcmFlag | Boolean | 是否中药饮片行（`med_medication_request.tcm_flag`）；病案首页医嘱带入按此合并同方饮片，**禁止**用展示文案判断 |
| chineseHerbsDoseQuantity | Integer | 中药付数（中药行） |
| prescriptionNo | String | 处方号/申请单号（药品 **`CP`**；申请单 **`AR`** 前缀） |
| serviceRequestDetails | List\<`ServiceRequestDetailSummaryDto`\> | **申请单行**：部位/样本 Detail 摘要；非申请单可空 |
| contentSummary | String | **常规**药/诊窄列摘要（可由 `adviceDisplayName` 等拼接）；**嘱托行**建议填 `entrustContent`（或前端直接读 `entrustContent`）；**出院（34）/转科（32）行为 null**（**BR-IP-ORD-017**） |
| serviceCategoryEnum | String | 诊疗服务类目：`34` 出院、`32` 转科、**`52` 嘱托**、`26` 护理级别等；常规诊疗可空 |
| outWayCode | String | 出院方式 code（**EncounterHistory** 里程碑，按 `requestId` 匹配） |
| outWayName | String | 出院方式展示名（术语 `out_way_code`） |
| reasonText | String | 出院/转科原因（`wkf_service_request.reason_text`） |
| plannedDischargeTime | String | 计划出院时间 ISO local（`occurrence_start_time`） |
| techPlannedExecutionCount | Integer | **医技计划次数**（仅执行科 class=TECH 的诊疗行；同医技站 `plannedExecutionCount` / 长嘱强制 1；非 TECH 为 null；**BR-IP-ORD-038**） |
| techCompletedCount | Integer | **医技侧 COMPLETED 次数**（`Procedure.org_id`=SR 执行 TECH 科；非 TECH 为 null） |
| techCancelledCount | Integer | **医技侧取消次数**（同 TECH 轴；CANCEL/历史 ABORTED；非 TECH 为 null） |
| performLocationId | Long | **诊疗行**：执行科室组织主键（优先 `ServiceRequest.org_id`，无则回落 `location_id`；与门诊同口径）；**药品行**：发药/执行库位 |
| performLocationName | String | **诊疗行**：执行科室组织展示名（`OrganizationQueryService#mapNamesByIds`）；**药品行**：库位名；供列表扩展行展示 |

> **B.6.2 说明**：QueryService `content_json` / `med_content_json` **仅QueryService 侧存储**，`InpatientDoctorStationAdviceRowDto` **不得**暴露 `contentJson` 字符串列。

> **出院/转科装配（`InpatientDoctorStationAdviceListAssembler`，已落地）**：`category_enum=34/32` 时不预热 `activity_id≤0` 的占位定义；`adviceDisplayName`：转科固定「转科医嘱」；出院默认「出院医嘱」，里程碑 `outWayCode=5`（死亡）为「死亡出院」（**BR-IP-ORD-017**）；`listRowKey` 仍为 `requestId` + SOH + `wkf_activity_definition`（与 **InpatientOrderListSkeleton** 诊疗行类型锚一致）。护士站 **`NurseStationOrderViewListAssembler#resolveSpecialServiceAdviceDisplayName`** 同源。

> **前端列表映射（`advice-list.ts`，已落地）**：`content` ← `adviceDisplayName`；扩展行拼接 `outWayName`、`plannedDischargeTime`、`reasonText`、医技进度文案与 **`performLocationName`（执行科室）**；**不读** `contentSummary`。

> **嘱托装配（已落地，`InpatientDoctorStationAdviceListAssembler`，**BR-IP-ORD-028**）**：识别 **`serviceCategoryEnum=52`** 或活动名「嘱托」；下发 `entrustContent`、`serviceCategoryEnum`；`contentSummary` 经 **`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`** 优先 `entrustContent`；**护士站**医嘱校对/执行/划价父医嘱列表经 **`NurseStationOrderViewListAssembler#buildServiceDisplayCore`** 写回 **`adviceDisplayName`**（同源方法，**AS-IP-NUR-003-SDD §2.5**）；前端 **医嘱内容列** 建议 `content` ← **`entrustContent`**（有值时优先于目录名）或直接读已解析的 **`adviceDisplayName`**；类型列拼接类目「嘱托」+ 长/临文案；金额列无费用时展示空/「—」。

> **出院带药装配（已落地，**BR-IP-ORD-029**）**：`therapyTypeCode=DISCHARGE_MEDICATION` 的药品行；`therapyTypeText` ← `TherapyType.DISCHARGE_MEDICATION.getDisplay()`（**「出院带药」**，**非**「临时医嘱」）；内容列同常规药品；费用 rollup 同临时（护士执行后）。皮试/输液**不**在列表 Gate 硬拒。

> **首日用量装配（已落地，**BR-IP-ORD-030**）**：`therapyTypeCode=LONG_TERM` 的药品行从 `MedicationRequest` / 读模型下发 `firstDose`/`firstDoseUnitCode`（及 `firstDoseUnitName`）；临时/出院带药行本字段为 null；**不**在列表侧做频次点裁剪（执行侧 **AS-IP-NUR-004**）。

> **分次用量不一致装配（目标态，**BR-IP-ORD-037**）**：长期西药/中成药行下发 `doseSchedule`/`quantitySchedule`；有值时前端剂量列走序列展示；空则剂量展示仍走 `dose`/`doseUnit*`；**不**在列表侧展开频次点。

#### 关键校验

- `encounterId`、`patientId` 非空
- `InpatientEncounterContextQueryModel`：**IMP** + 医生站可见状态（复用诊断模块门控语义）
- 列表数据源：`MedicationRequestQueryService / ServiceRequestQueryService / （住院医嘱列表不查 DeviceRequest）(encounterId, therapyTypeCode)` 已含 **doctor-prescription** 与 **药+诊** 过滤（**Q-ORD-007**）

#### 失败码口径

| 语义码 | 条件 |
| --- | --- |
| EX-IP-ORD-201 | 就诊不存在/不可见、查询失败 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| 1 | Gate | `InpatientDoctorStationAdviceMapper#validateListRequest` | Request | 必填校验 |
| 2 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | encounterId | IMP 门控 |
| 3 | Pre | `InpatientDoctorStationAdviceMapper#validateInpatientEncounterPatientGate` | snapshot, patientId | 患者一致 |
| 4 | Pre | `MedicationRequestQueryService / ServiceRequestQueryService / （住院医嘱列表不查 DeviceRequest）` | encounterId, therapyTypeCode | **Q-ORD-007** 主列表 |
| 5 | Pre | `ChargeItemQueryService#listChargeItemsByEncounterId` | encounterId | 护士执行后费用 rollup |
| 6 | Pre | `MedicationRequestQueryService#listMedicationRequestsByEncounterForDoctorStation` + `ServiceRequestQueryService#listServiceRequestsByGenerateSource` + **`#mapListModelsByIds`**（InpatientOrderListSkeleton 上 SERVICE 行 id） | encounterId | 明细与出院行补全 |
| 7 | Pre | `EncounterHistoryQueryService#listMilestonesByEncounter` | encounterId | 出院 `outWayCode` 索引（`name=discharge` 且 `requestId` 匹配） |
| 8 | Asm | `InpatientDoctorStationAdviceListAssembler#assembleRows` | InpatientOrderListSkeleton + 上列 Pre | 排序：**开立时刻（`reqOpenedTime`，空回落 `reqAuthoredTime`）降序**；成组药聚邻；出院行 **BR-IP-ORD-017**；**皮试药行**：`loadSkinTestOutcomeByMedicationRequestId`（companion **doctor-prescription** 皮试检查 SR → `AllergyIntolerance`，**BR-IP-ORD-024**）；**皮试检查 companion 行**：`SkinTestCompanionDisplaySupport` 追加关联药名+规格（**BR-IP-ORD-024**） |
| 9 | Asm | 构造 `ListInpatientDoctorStationAdviceResponse` | rows, totalCount | 仅读 |

#### Mapper 签名（Asm）

- `List<InpatientDoctorStationAdviceRowDto> assembleRows(Long encounterId, Long patientId, List<InpatientOrderListSkeleton> orderRows, Long currentUserId)`（**已落地**）

---

### 3.3 IF-IP-DOR-11 `pageInpatientDoctorStationAdviceCatalog`（分页检索可开立项目目录）

> **母版**：`AS-OP-DOR-003-SDD` §3.3（**v3.49+**：规则 **D** 同库多批预占顺序消耗；**v3.28+**：目录 **`unitCode`/`unitName`/`availableQuantity`** 均为**包装单位**（`package_unit_code`），**不下发** `defEncounterUnit*`/`defInhospitalUnit*`，**禁止**跨槽单位回落；**v3.8+**：**规则 C/H** 库存拆零与 FIFO 整包装分段，**`DoctorStationAdviceInventoryUnitQuantityHelper`**；**v3.7+**：QueryService **v3.12** 诊疗投影含单位/状态列；**v3.6+**：**`unitBands` 包装槽 `PACKAGE`**、诊疗仅 PACKAGE 有值；**`availableQuantity`** 由拆零净量经 **`ProductDefinitionCatalogUnitBandsAssembler#convertDoctorStationCatalogAvailableQuantity`** 换算，与 **`inventoryList[].quantity`（拆零）** 分工）；**住院差异**：① `productTableNames` 默认仅药品+诊疗两表；② **删除**库存不足 Gate 与 `EX-IP-ORD-104`；③ 目录预减/定价/单位 bands 逻辑可复用门诊装配器，但**不得**因库存为 0 拒绝保存（**BR-IP-ORD-003**）。

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/pageInpatientDoctorStationAdviceCatalog` |

#### Request（`PageInpatientDoctorStationAdviceCatalogRequest`）

继承 `AbstractPageQuery`（`pageNo`/`pageSize`/`keyword`）。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| productTableNames | List\<String\> | 否 | 仅允许 `med_medication_definition`、`wkf_activity_definition`；**禁止** `adm_device_definition` |
| pricingFlag | Integer | 否 | 同门诊：为 1 时排除药品行 |
| prepickedProductKeys | List\<PrepickedProductKeyDto\> | 否 | 目录复合键 |
| categoryEnum | String | 否 | 诊疗目录类别过滤：申请单 **21/22/23**（或 TREATMENT/PROOF/TEST）；**护理级别快捷开立传 `26`**（ActivityDef `category_code`，**BR-IP-ORD-026**；**不**经申请单 21/22/23 映射）。普通医嘱选品下拉**不**依赖本字段排除护理级别——由**前端**按返回行 `categoryCode=26` 过滤（**BR-IP-ORD-026⑥**） |
| typeEnum | String | 否 | **申请单分类 chips**：按诊疗业务类型 `type_enum`（术语 `activity_type_code`）精确过滤；空=不限；与 `categoryEnum` 独立 |

#### Response

`PageInpatientDoctorStationAdviceCatalogResponse` extends `PagedResult<InpatientDoctorStationAdviceCatalogRowDto>`（**B.10.11**）。

#### 编排序列（摘要）

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 `productTableNames` 不含耗材表 | 含则 **BR-IP-ORD-012** 拒绝 |
| 2 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | 门控 |
| 3 | Pre | `InpatientDoctorStationAdviceMapper#toProductDefinitionPageQuery` | **`categoryEnum=26`** 直写 `categoryCode`；其它诊疗类别经 **`ServiceApplyFormOrchestrationSupport#resolveApplyFormCategoryCode`** 映射申请单码 |
| 4 | Pre | `ProductDefinitionCatalogPageLoader#pageProductDefinition` | QueryService 分页 |
| 5 | Asm | `InpatientDoctorStationAdviceCatalogAssembler#assemblePageContent` | 定价/单位 bands；**跳过**库存硬门禁 |

完整步骤、DTO 字段、失败码与门诊 §3.3 同序（无库存失败行）。

---

### 3.4 IF-IP-DOR-12 `getInpatientDoctorStationAdviceOrderBindInfo`（查询医嘱绑定信息）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationAdviceAppService#getInpatientDoctorStationAdviceOrderBindInfo` |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/getInpatientDoctorStationAdviceOrderBindInfo` |

#### Request（`GetInpatientDoctorStationAdviceOrderBindInfoRequest`）

与门诊 **`GetDoctorStationAdviceOrderBindInfoRequest`** 同构：`bindType`、`itemNo`、`itemId`、`organizationUnitId`（可选）。

| bindType | 与领域 `type_code` 同码 | 来源表 `item_table` | 说明 |
| --- | --- | --- | --- |
| `USAGE`（`DoctorStationAdviceConstants#BIND_TYPE_USAGE`） | `ter_concept` | 给药途径 **`method_code`（code 字符串）**；目标可为诊疗或耗材 |
| `ACTIVITY`（`DoctorStationAdviceConstants#BIND_TYPE_ACTIVITY`） | `wkf_activity_definition` | 诊疗活动定义主键字符串；目标可为诊疗或耗材 |

#### 编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `InpatientDoctorStationAdviceMapper#resolveOrderBindSourceItemKey` | 用法：`itemNo`=method_code；诊疗：`itemId` 或数字 `itemNo`；无法解析 → `SERVICE_REQUEST_INVALID_ARGUMENT` |
| 2 | Pre | `InpatientDoctorStationAdviceOrderBindQueryService#listBindingsBySourceItemKey` | **合管已配 activity → 空**；否则 `ServiceDeviceTemplateQueryService#listBindingsByRangeTypeAndSource`（`rangeCode=INPATIENT`，仅 `active`）；批量展示名；**上限** `ORDER_BIND_LIST_MAX_ROWS`（200） |
| 3 | Asm | 构造 `GetInpatientDoctorStationAdviceOrderBindInfoResponse#setBindRows` | 仅读 |

> `organizationUnitId` 预留；绑定查询按 `rangeCode=INPATIENT` 匹配（**非** `HOSPITAL` 历史码），未下推 SQL。
> **开立暂存前绑定**：`resolveInpatientDoctorStationAdviceDraftBinds` → `listDraftBindItems` **恒空**（**BR-IP-ORD-036**）；未合管模板仅护士执行 **BR-IP-EXE-013** 挂载。

---

### 3.5 IF-IP-DOR-13（废止）

费用性质 **`feeNatureOptions`** 由 **`AS-IP-DOR-001`** 患者列表/就诊上下文接口提供（对齐门诊 **IF-DOR-01**）。本模块**无** HTTP。

---

### 3.6 IF-IP-DOR-18 `initInpatientDoctorStationAdvice`（医嘱开立页初始化）

复用 **`AS-OP-DOR-003-SDD` §3.5.1（IF-DOR-18）**；仅将门诊门控替换为 **`EncounterContextReader#mustGetInpatientContextByEncounterId`**。

**响应增补**：`medicationSpeedOptions`（术语 **`speed_code`** 下拉；输液/注射药开立「滴速」；与列表 `speedCode`/`speedName` 同源）。

**`bodyStructureOptions`**：与门诊一致，为 **`getBodyStructureTree` → `BodyStructureOptionAssembler#toActiveOptionTree`** 产出的 **OptionDto 树**（仅 ACTIVE，含 `children` 与 **`pyStr`/`wbStr`**）；申请单检查行前端 **`ElTreeSelect`** 多选，并按名称/编码/拼音/五笔模糊过滤。

**`specimenDefinitionOptions`**：与门诊一致，启用态平面下拉（上限 500），项透出 **`pyStr`/`wbStr`**；申请单检验行前端 **`ElSelect`** 按名称/编码/拼音/五笔模糊过滤。

---

### 3.7 IF-IP-DOR-19 `listActivityExecutionLocations`（按诊疗定义查询可执行科室规则）

复用 **`AS-OP-DOR-003-SDD` IF-DOR-19** 契约与编排序列（含 **单品 + 匹配业务类型层 + 大类默认** 规则合并、`ProductDefinitionBatchLoader` 解析诊疗大类与 `activityTypeCode`）；HTTP：`POST /api/app/inpatientDoctorStationAdvice/listActivityExecutionLocations`。

**前端执行科室下拉**与门诊 **§3.5.2「前端执行科室下拉（BR-ORD-024）」** 同构（住院绑定 **BR-IP-ORD-023**）；实现共用 `perform-org-options.service.ts` 与 **`AdviceDeviceServiceFormPanel`** / **`AdviceEditCard`**。

---

### 3.8 IF-IP-DOR-14 `saveInpatientDoctorStationAdviceDraftBatch`（暂存医嘱变更批次）

> **母版**：`AS-OP-DOR-003-SDD` §3.6；**住院差异**见下表。

| 住院差异 | 实现要点 |
| --- | --- |
| 无耗材 | 批次行 `productTableName` 为 device 表时 **Gate 拒绝** |
| 长/临/出院带药分轨 | **三种 therapy** 均仅写 Request；出院带药 `DISCHARGE_MEDICATION` 仅药品（**BR-IP-ORD-029**） |
| 无费用/发放 | **不**调用 `createDraftChargeItemFromOrder` / FIFO / `generateDispenseFromTemporaryOrder` |
| **皮试联动** | 药品 Cmd 完成后 **`DoctorStationSkinTestSyncOrchestrator#syncAfterMedicationDraftLine`**（**§3.8.1**）：**仅** companion SR，**不**挂费；出院带药软约定不勾皮试（**不硬拒**） |
| **嘱托** | 诊疗 CREATE/UPDATE：若 `category_code=52` 或名「嘱托」，**`InpatientEntrustOrderGateSupport#requireEntrustContentIfEntrustActivity`**（空 → **EX-IP-ORD-306**）；写 `category_enum=52` + `entrustContent`（**BR-IP-ORD-028**；**已落地**） |
| 无库存校验 | **删除**门诊库存不足分支 |
| DELETE | **三种 therapy** 均仅删请求 |
| 来源 | 全部 `generate_source_enum = doctor-prescription` |

#### Request（`SaveInpatientDoctorStationAdviceDraftBatchRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| lines | List\<InpatientDoctorStationAdviceDraftLineRequest\> | 是 | 批次行；不得为空 |

#### `InpatientDoctorStationAdviceDraftLineRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| lineOp | String | 是 | `CREATE` / `UPDATE` / `DELETE` |
| productTableName | String | 是 | `med_medication_definition` / `wkf_activity_definition`（**禁止**耗材表名，**BR-IP-ORD-012**） |
| requestId | Long | 条件 | `UPDATE`/`DELETE` 必填 |
| sortNumber | Integer | 否 | **BR-ORD-018**：非空时写入/更新展示排序 |
| therapyTypeCode | String | 是 | `LONG_TERM` / `TEMPORARY` / **`DISCHARGE_MEDICATION`**（`TherapyType#getCode()`） |
| medicationPayload | SaveInpatientMedicationAdviceDraftLinePayload | 条件 | 药品定义表且 `lineOp`≠`DELETE` |
| servicePayload | SaveInpatientServiceAdviceDraftLinePayload | 条件 | 诊疗定义表且 `lineOp`≠`DELETE` |

#### 嵌套：`SaveInpatientMedicationAdviceDraftLinePayload`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| medicationDefinitionId | Long | 是 | 药品定义 |
| quantity | BigDecimal | 否 | 开立数量（与 `unitCode` 同语义）；**出院带药=总量**（同临时，**BR-IP-ORD-029**） |
| unitCode | String | 否 | 单位编码 |
| methodCode | String | 否 | 用法 |
| rateCode | String | 否 | 频次 |
| speedCode | String | 否 | 输液滴速（术语 `speed_code`；可空；UPDATE 空串可清空；落库 `med_medication_request.speed_code`） |
| groupId | Long | 否 | 组号 |
| dose | BigDecimal | 否 | 单次用量 |
| doseUnitCode | String | 否 | 剂量单位 |
| firstDose | BigDecimal | 否 | **仅长期**：首日/首次用量（`first_dose`；**BR-IP-ORD-030** / **BR-MRQ-010**）；空=首次槽仍用 `quantity`（有 `quantitySchedule` 见 **BR-IP-ORD-037**）；有值须 **>0**；**数量+单位为最小/拆零口径**（开立 UI 计量录入后前端换算） |
| firstDoseUnitCode | String | 条件 | 与 `firstDose` 成对；`firstDose` 非空时建议必填 |
| doseSchedule | String | 否 | **仅长期西药/中成药**：分次剂量序列（计量、逗号分隔；**BR-IP-ORD-037** / **BR-MRQ-035**）；与 `quantitySchedule` 同空或同有值；有值时段数须=频次日次数、每段正数 |
| quantitySchedule | String | 否 | **仅长期西药/中成药**：分次用量序列（拆零、逗号分隔；与 `doseSchedule` 同序同段数；由前端按原计量→拆零换算写出） |
| dispensePerDuration | Integer | 否 | 用药天数 |
| skinTestFlag | Boolean | 否 | 皮试（`med_medication_request.skin_test_flag`）；出院带药**软约定不勾**，后端**不**因 true 拒绝（**BR-IP-ORD-029⑤**） |
| skinTestFlagText | String | 否 | 皮试标志展示「是/否」 |
| skinTestOutcomeCode | String | 否 | 皮试结果编码（`AllergyIntolerance.clinical_status_enum`）；多 companion 取最新 |
| skinTestOutcomeText | String | 否 | 皮试结果展示文案 |
| needAppendSkinTestFlag | Boolean | 否 | 长嘱皮试药：发放批号与最新皮试登记批号不一致时 true（换批提示；**IF-IP-DOR-27**） |
| encounterDiagnosisIds | String | 否 | 绑定就诊诊断 ids 有序串（落库 `encounter_diagnosis_ids`；**主诊 id 第一**） |
| contractId | Long | 否 | 费用性质覆盖 |

> **库位口径（住院）**：开嘱写路径**不**接收 `dispenseLocationId`；药品库位与批号在 **IF-IP-EXE-02 护士执行** 时按库存 Gate + FIFO 现场解析（**BR-IP-EXE-016**）。目录 IF-IP-DOR-11 的 `defaultDispenseLocationId` 仅读侧展示。

#### 嵌套：`SaveInpatientServiceAdviceDraftLinePayload`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| activityDefinitionId | Long | 是 | 诊疗定义 |
| quantity | BigDecimal | 条件 | **CREATE**：**`#requireQuantity`**（显式载荷；禁止回落 `executionCount`；否则 **`MSG_SERVICE_QUANTITY_REQUIRED`**）；**UPDATE**：**`#resolveQuantityForModify`** |
| unitCode | String | 条件 | **CREATE**：**`#requireUnitCode`**（显式 → 子 JSON → 目录主单位；否则 **`MSG_SERVICE_UNIT_CODE_REQUIRED`**）；**UPDATE**：**`#resolveUnitCodeForModify`** |
| executionCount | Integer | 条件 | **CREATE**：**`#requireExecutionCount`**（显式 → 子 JSON；**临嘱**必填 **>0**；**长嘱**可空）；**UPDATE**：**`#resolveExecutionCountForModify`** |
| performOrgId | Long | 否 | 执行科室 |
| childOverridesJson | String | 否 | 套餐子项覆盖 JSON（`AdviceExtensionJsonSupport#parseServiceChildOverrides`） |
| encounterDiagnosisIds | String | 否 | 绑定就诊诊断 ids 有序串（落库 `encounter_diagnosis_ids`；**主诊 id 第一**） |
| rateCode | String | 否 | 频次（**长期诊疗**落库 `wkf_service_request.rate_code`；Gate 规则见 Mapper） |
| entrustContent | String | 条件 | **嘱托行必填**（trim 后非空）；落库 `wkf_service_request.entrust_content`（**BR-IP-ORD-028**）；常规诊疗可选备注嘱托 |
| contractId | Long | 否 | 费用性质覆盖 |

> **诊疗数量/单位/执行次数（已落地）**：`quantity`/`unitCode` 供**账单计价**；`executionCount` 落库 `wkf_service_request.execution_count` 供**护士站拆解**（临嘱必填，长嘱按频次可空）。Mapper 与门诊共用 **`ServiceAdviceDraftCommandsupport**（§2.1）。

> **嘱托数量/单位**：选中「嘱托」时前端默认 `quantity=1`、`unitCode`=目录主单位（次）；`entrustContent` 为医嘱正文（**已落地**写路径），**不**用 `remark` 替代。

> **出院带药（应用编排已落地，**BR-IP-ORD-029**）**：`therapyTypeCode=DISCHARGE_MEDICATION` 时 **仅允许** `medicationPayload`（药品定义表）；**禁止**同批以该 therapy 写嘱托/申请单诊疗行——后端 **`assertNotDischargeMedicationForServiceLine`** 拒绝（`SERVICE_REQUEST_INVALID_ARGUMENT`）；**不**因皮试/用法硬拒。领域枚举 **已落地**（**G-IP-DOR-003-05 Closed**）。

> **首日用量（已落地，**BR-IP-ORD-030**）**：`therapyTypeCode=LONG_TERM` 时 Mapper 将 `firstDose`/`firstDoseUnitCode` 写入 `CreateMedicationOrderDraftCommand` / `ChangeMedicationOrderDraftCommand`（契约要求调用方已换算为最小单位）；非长期须置空（域 **BR-MRQ-010**）；**不**在开立写路径展开频次或改执行槽位。

> **分次用量不一致（目标态，**BR-IP-ORD-037**）**：长期西药/中成药 Mapper 透传 `doseSchedule`/`quantitySchedule`；Gate：两列同空或同有值、段数=频次日次数、每段正数、`doseUnitCode` 非空；失败 → **EX-IP-ORD-310**；非长期/中药须空；清空不一致时显式传空串/null 清列；**不**在开立写路径展开频次点。

#### 失败码口径

| 语义码 | 条件 |
| --- | --- |
| EX-IP-ORD-101 | （**废止**） |
| EX-IP-ORD-102 | 删除行已收费 |
| EX-IP-ORD-207 | 持久化失败 |
| EX-IP-ORD-306 | 嘱托行 `entrustContent` 空或空白（**BR-IP-ORD-028**） |
| EX-IP-ORD-308 | 同组成组药品频次不一致（**BR-IP-ORD-033**） |
| — | 耗材类型行 → `MEDICATION_REQUEST_INVALID_ARGUMENT` / 统一 INVALID（**BR-IP-ORD-012**） |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 Request、批次非空、**拒绝耗材表名** | **BR-IP-ORD-012** |
| 1a | Gate | **`InpatientMedicationAdviceGroupRateGate#assertDraftBatchConsistent`** | 同组 `rate_code` 唯一；失败 **EX-IP-ORD-308**（**BR-IP-ORD-033**） |
| 2 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` + 可写门控 | IMP 在院；含出院带药写路径（**BR-IP-ORD-027**） |
| 3 | Pre | `InpatientDoctorStationAdviceMapper#validateInpatientEncounterPatientGate` | 患者一致 |
| 4 | Cmd | 按 **药品→诊疗** 遍历 lines（**BR-IP-ORD-007**） | 见 5～8 |
| 5 | Cmd | 药：`MedicationRequestDomainService#create/change/remove...(Command)` + 可选 `adjustMedicationOrderGroup` | `therapy_enum` 来自行（含 **`DISCHARGE_MEDICATION`**）；**长期**透传 `firstDose`/`firstDoseUnitCode`（**BR-IP-ORD-030**）及 `doseSchedule`/`quantitySchedule`（**BR-IP-ORD-037**）；**三种 therapy 均仅 Request** |
| 5a | **皮试联动** | **`DoctorStationSkinTestSyncOrchestrator#syncAfterMedicationDraftLine`**（**§3.8.1**） | 药品 Cmd 完成后；**与 5 同一 `@Transactional`**；出院带药若勾皮试仍走联动（软约定不禁） |
| 6 | Pre | 诊 CREATE/UPDATE：**`InpatientEntrustOrderGateSupport#requireEntrustContentIfEntrustActivity`**（**已落地**） | 嘱托正文 Gate；失败 **EX-IP-ORD-306** |
| 7 | Cmd | 诊：`ServiceRequestDomainService#create/modify/remove`（透传 `entrustContent`） | **长临均仅 Request**；嘱托写 `entrust_content`；**出院带药 therapy 不应出现诊疗行** |
| 8 | Cmd | DELETE：仅删请求；删皮试药时对称软删联动 SR | **BR-IP-ORD-008/024** |

#### 3.8.1 住院皮试联动（`DoctorStationSkinTestSyncOrchestrator`）

> **AS `BR-IP-ORD-024`** / **AS §1.8**；下游 **AS-IP-NUR-011 v1.1**（护士锚 doctor SR 计费）。与门诊 **§3.6.1** 同构，**差异**：**不**调用 `createDraftChargeItemFromOrder`；**`therapyEnum` 固定 `TEMPORARY`**（与门诊一致，不继承皮试药长临；无长期皮试）。

| 触发 | 行为 |
| --- | --- |
| CREATE/UPDATE + `skinTestFlag=true` | ① `requireIdByExactName(SKIN_TEST_INSPECTION_ACTIVITY_NAME)` ② 查找**草稿** companion → 有则 **modify**，无草稿则若尚无任何 companion 则 **create**（已有已签发 companion 时暂存**不**自动再建） ③ `createServiceRequestDraft`：`generateSourceEnum=DOCTOR_PRESCRIPTION`；`therapyEnum=TEMPORARY`；`basedOnTable/id` 锚皮试药；诊断/执行科室/费用性质继承皮试药；**不**建 ChargeItem |
| UPDATE + `skinTestFlag=false`（IF-IP-DOR-14） | **`removeDraftCompanionSkinTestServiceRequest`**：软删该药下**全部草稿** companion SR（**无**费用作废） |
| DELETE 皮试药（IF-IP-DOR-14） | 删药前 **`removeDraftCompanionSkinTestServiceRequest`**（同上） |
| 签退皮试药（IF-IP-DOR-16） | **`withdrawLinkedSkinTestServiceRequestIfPresent`**：**仅 `status=ACTIVE`** companion 签退（`withdrawSigned`）；草稿/已签退 **跳过**（**不回滚**护士已划价费用） |
| **追加（IF-IP-DOR-27）** | **`appendCompanionSkinTestServiceRequest`**：长期皮试药；已有草稿则复用；否则新建 companion（`based_on→该药`，不挂费） |

**幂等**：首次勾选皮试时同一 `medReqId` **至多一条草稿** companion；**换批追加**允许同药多条（历史 companion 保留；签发 expand / 签退须全部处理）。

**列表展示（`SkinTestCompanionDisplaySupport`）**：同门诊——关联标签可含规格，形如 **「皮试检查（关联：氯化钠注射液 0.9%*250ml）」**。

**IF-IP-DOR-15 增补**：签发前 **`expandSignTargetsWithLinkedSkinTestServiceRequests`**，将该药下**全部草稿** companion SR 追加至 `signTargets`；批次循环内对诊疗目标执行 **`signServiceRequest`**（**不**挂费、**不** FIFO）。

**IF-IP-DOR-16 增补**：签退前 **`collectCompanionSkinTestServiceRequestIds(medicationRequestIds)`**，同批勾选药+companion **去重**（显式诊疗目标若已在 companion 集合则跳过）；签退皮试药时 **`withdrawLinkedSkinTestServiceRequestIfPresent(medReqId)`**（ACTIVE-only）。

**列表**：长嘱皮试药行可下发 `needAppendSkinTestFlag`（发放批号 ≠ 最新 Allergy `lotNumber`）。

---

### 3.9 IF-IP-DOR-15 `signInpatientDoctorStationAdviceBatch`（签发医嘱批次）

> **母版**：§3.7；**住院长/临/出院带药**均仅 `signMedicationOrder` / `signServiceRequest`（处方号/签署编码），**不** FIFO、**不**挂费。

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate/Pre | 住院门控 + 批次签署码生成；药品逐条取 **`COMPOSITE_PRESCRIPTION`** 处方号 | `BusinessSequenceService` |
| 2 | Pre | **`DoctorStationSkinTestSyncOrchestrator#expandSignTargetsWithLinkedSkinTestServiceRequests(signTargets)`** | **BR-IP-ORD-024** |
| 3 | Cmd | 药品：**长/临/出院带药** `signMedicationOrder`；companion SR 经步骤 2 扩入后走步骤 4 **`signServiceRequest`** | **BR-IP-ORD-005/006/024/029** |
| 4 | Cmd | 诊疗：**长临** `signServiceRequest`（出院带药无诊疗行） | |
| 5 | Cmd | 外发 `LisPacsApplyGateway`（可选） | 同门诊 |

---

### 3.10 IF-IP-DOR-16 `withdrawInpatientDoctorStationAdviceBatch`（签退医嘱批次）

> **母版**：`AS-OP-DOR-003-SDD` §3.8（**IF-DOR-16**）；**住院差异**仅药品长临分轨。

| 资源类型 | 长临 | 编排序列 |
| --- | --- | --- |
| 药品 | **长临** | **Pre**：`collectCompanionSkinTestServiceRequestIds` 同批去重 → `InpatientAdviceRevokeGateSupport#assertMedicationWithdrawAllowed`（**EX-IP-ORD-103**）→ `signOffMedicationOrder` + **`withdrawLinkedSkinTestServiceRequestIfPresent(medReqId)`**（**仅 ACTIVE**；**BR-IP-ORD-024**） |
| 诊疗 | 不分轨 | **Pre**：`InpatientAdviceRevokeGateSupport#assertServiceRequestWithdrawAllowed`（**EX-IP-ORD-103**）→ 若 id 已在 companion 去重集则**跳过**；否则仅 `withdrawSignedServiceRequest`（**不** rollback 费用；**已是 DRAFT 幂等返回**，**DM-WKF-004** §6.1.5） |

**签退 Gate（GAP-STALE-005）**：签退前批量检查目标行无净剩余 `COMPLETED` Procedure；发药侧无在途/`SUMMARIZED`/`PART_COMPLETED`/`PENDING_REFUND`/`PART_REFUND`，且**净已发**（正向 `COMPLETED` 数量减去 `REFUND+REFUNDED` 数量）不大于 0（DM-MED-002 §6.1.4：退药确认不改正向态）；无 `PENDING_SETTLEMENT`/`SETTLED`/`REFUNDING` Charge；否则 **EX-IP-ORD-103**（已执行、已发药或费用不可回退，无法签退）。

---

### 3.11 IF-IP-DOR-17 `updateInpatientDoctorStationMedicationAdviceGroups`（更新药品医嘱组号）

复用 **`AS-OP-DOR-003-SDD` §3.9**；`MedicationRequestDomainService#adjustMedicationOrderGroup(AdjustMedicationOrderGroupCommand)`。

| 阶段 | 调用 | 说明 |
| --- | --- | --- |
| Gate | **`InpatientMedicationAdviceGroupRateGate#assertAssignGroupConsistent`** | 赋组前：待并入行与目标组既有成员 `rate_code` 须唯一；失败 **EX-IP-ORD-308**（**BR-IP-ORD-033**）；拆组跳过 |

---

### 3.12 IF-IP-DOR-20 `stopLongTermInpatientDoctorStationAdvice`（停嘱·长期医嘱）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/stopLongTermInpatientDoctorStationAdvice` |

#### Request（`StopLongTermInpatientDoctorStationAdviceRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| lines | List\<StopLongTermAdviceLineRequest\> | 是 | 待停嘱行 |

#### `StopLongTermAdviceLineRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| requestId | Long | 是 | 药/诊主键 |
| productTableName | String | 是 | 区分药/诊 |
| stopTime | LocalDateTime | 否 | 默认当前时间；写入 `stop_time` |

#### 关键校验

- 每行 **`therapy_enum` 必须为 LONG_TERM**；否则拒绝（**BR-IP-ORD-013**）
- 药品须 **`VERIFIED`**；诊疗须 **`VERIFIED`**（诊侧门诊 **`COMPLETED`** 仍可停，**BR-IP-ORD-013**）；**禁止 ACTIVE 停嘱**
- **禁止**将护士「不执行」Procedure 混入本 IF

#### 失败码

| 语义码 | 条件 |
| --- | --- |
| EX-IP-ORD-304 | 非长期 / 非已开嘱校对（含 ACTIVE） |
| EX-IP-ORD-207 | 停嘱持久化失败 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 lines 非空 | |
| 2 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` + 可写门控 | |
| 3 | Pre | 按行加载 Request，校验 **LONG_TERM** + 已开嘱校对态 | 非法 → EX-IP-ORD-304 |
| 4 | Cmd | 若药：`MedicationRequestDomainService#stopLongTermMedicationOrder(StopLongTermMedicationOrderCommand)` | 写 `stop_time` + `stopper_id`；清停嘱校对两字段；**STOPPED** |
| 5 | Cmd | 若诊：`ServiceRequestDomainService#stopServiceRequest(StopServiceRequestCommand)` | 同上 |
| 6 | — | 投影：停嘱事件 → **InpatientOrderListSkeleton**；护士站进入停嘱待校 | 最终一致 |

#### Mapper 签名

- `StopLongTermMedicationOrderCommand toStopLongTermMedicationOrderCommand(StopLongTermAdviceLineRequest line, Long operatorId): StopLongTermMedicationOrderCommand`（Command 须含 `stopperId`/`operatorId`）
- `StopServiceRequestCommand toStopServiceRequestCommand(StopLongTermAdviceLineRequest line, Long operatorId): StopServiceRequestCommand`

---

### 3.12.1 IF-IP-DOR-25 `cancelStopLongTermInpatientDoctorStationAdvice`（取消停嘱·长期医嘱）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/cancelStopLongTermInpatientDoctorStationAdvice` |

#### Request（`CancelStopLongTermInpatientDoctorStationAdviceRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| lines | List\<CancelStopLongTermAdviceLineRequest\> | 是 | 待取消停嘱行 |

#### `CancelStopLongTermAdviceLineRequest`

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| requestId | Long | 是 | 药/诊主键 |
| productTableName | String | 是 | 区分药/诊（与 IF-IP-DOR-20 同型） |

#### Response（`CancelStopLongTermInpatientDoctorStationAdviceResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 是否成功 |
| message | String | 提示文案 |

#### 关键校验

- 每行 **LONG_TERM** + **STOPPED** + **`stop_performer_check_id` 为空**；否则 **EX-IP-ORD-304**（已停嘱校对须先护士取消停嘱校对）
- **Pre 出院门禁**：同就诊有效转科/出院或待出院 → **EX-IP-ORD-305**（**BR-IP-ORD-025**）
- **禁止**将护士「不执行/取消执行」Procedure 混入本 IF
- **不**校验停嘱时间窗；**不**因已有 Procedure/Dispense 拒绝

#### 失败码

| 语义码 | 条件 |
| --- | --- |
| EX-IP-ORD-304 | 非长期 / 非 STOPPED / 已停嘱校对 |
| EX-IP-ORD-305 | 出院/转科门禁 |
| EX-IP-ORD-207 | 取消停嘱持久化失败 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 lines 非空 | |
| 2 | Pre | 就诊可写门控 | |
| 3 | Pre | 出院/转科门禁 | → EX-IP-ORD-305 |
| 4 | Pre | 按行校验 **LONG_TERM** + **STOPPED** + 未停嘱校对 | → EX-IP-ORD-304 |
| 5 | Cmd | 药：`cancelStopLongTermMedicationOrder` | 恢复 VERIFIED/ACTIVE；清 `stop_time`/`stopper_id`/停嘱校对两字段 |
| 6 | Cmd | 诊：`cancelStopServiceRequest` | 同上 |
| 7 | — | 投影：取消停嘱事件 → **InpatientOrderListSkeleton** | 最终一致 |

#### Mapper 签名

- `CancelStopLongTermMedicationOrderCommand toCancelStopLongTermMedicationOrderCommand(CancelStopLongTermAdviceLineRequest line, Long operatorId): CancelStopLongTermMedicationOrderCommand`
- `CancelStopServiceRequestCommand toCancelStopServiceRequestCommand(CancelStopLongTermAdviceLineRequest line, Long operatorId): CancelStopServiceRequestCommand`

---

### 3.12.2 IF-IP-DOR-26 `changeInpatientDoctorStationAdviceBusinessTime`（改开立/停嘱时间）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationAdviceAppService#changeInpatientDoctorStationAdviceBusinessTime` |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/changeInpatientDoctorStationAdviceBusinessTime` |

#### Request（`ChangeInpatientDoctorStationAdviceBusinessTimeRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 就诊 |
| patientId | Long | 是 | 患者 |
| requestId | Long | 是 | 药/诊主键 |
| productTableName | String | 是 | 区分药/诊 |
| timeKind | String | 是 | **`OPENED`**：改 `req_opened_time`；**`STOP`**：改 `stop_time`；**禁止**签发 |
| newTime | LocalDateTime | 是 | 新业务时间 |

#### Response（`ChangeInpatientDoctorStationAdviceBusinessTimeResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| success | Boolean | 是否成功 |
| message | String | 提示文案 |

#### 关键校验

- 就诊可写门控（**BR-IP-ORD-027**）；结算轨 → **EX-IP-ORD-107**
- `timeKind` 仅允许 `OPENED` / `STOP`；其它或隐含改签发 → **EX-IP-ORD-307**
- `STOP`：须 **LONG_TERM** 且当前 **STOPPED**（或已有 `stop_time`）；否则 **EX-IP-ORD-307**
- **绝不**写 `req_authored_time`；**不**改 `requester_id` / `stopper_id`

#### 失败码

| 语义码 | 条件 |
| --- | --- |
| EX-IP-ORD-107 | 出院结算轨门禁 |
| EX-IP-ORD-307 | 时间种类非法 / 非已停嘱改停嘱 / 试图改签发 |
| EX-IP-ORD-207 | 持久化失败 |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 requestId / timeKind / newTime 非空 | |
| 2 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` + 可写门控 | EX-IP-ORD-107 |
| 3 | Pre | 按行加载 Request；校验 timeKind 与状态 | 非法 → EX-IP-ORD-307 |
| 4 | Cmd | 若药：`MedicationRequestDomainService#changeMedicationRequestBusinessTime(ChangeMedicationRequestBusinessTimeCommand)` | 仅写开立或停嘱列；改停嘱**保留**停嘱校对两字段 |
| 5 | Cmd | 若诊：`ServiceRequestDomainService#changeServiceRequestBusinessTime(ChangeServiceRequestBusinessTimeCommand)` | 同上 |
| 6 | — | 列表刷新（开立时间重排） | 最终一致 |

#### Mapper 签名

- `ChangeMedicationRequestBusinessTimeCommand toChangeMedicationRequestBusinessTimeCommand(...): ChangeMedicationRequestBusinessTimeCommand`
- `ChangeServiceRequestBusinessTimeCommand toChangeServiceRequestBusinessTimeCommand(...): ChangeServiceRequestBusinessTimeCommand`

> **依赖**：**AS G-IP-DOR-003-06**（`stopper_id` 与改时领域命令）。**门诊不提供**本 IF。

---

### 3.12.3 IF-IP-DOR-28 `changeLongTermMedicationDoseInpatientDoctorStationAdvice`（长期药品改量）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/changeLongTermMedicationDoseInpatientDoctorStationAdvice` |
| **AS** | **BR-IP-ORD-032**；剩余续算 **AS-IP-NUR-004 BR-IP-EXE-038** / **DD-住院长嘱口服一盒一点剩余计数与改量-2026-08** |
| **实现阶段** | **已落地**（`InpatientDoctorStationAdviceAppService#changeLongTermMedicationDoseInpatientDoctorStationAdvice`；新嘱 DRAFT 待签发） |

#### Request（`ChangeLongTermMedicationDoseInpatientDoctorStationAdviceRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 住院就诊 |
| patientId | Long | 是 | 患者（可写门控，与其它写 IF 同构） |
| sourceMedicationRequestId | Long | 是 | 源长期药品申请（VERIFIED 或 STOPPED） |
| stopTime | LocalDateTime | 否 | 在用改量时的停嘱时间；空=当前时刻；已停源嘱忽略 |
| medicationPayload | `SaveInpatientMedicationAdviceDraftLinePayload` | 是 | 新嘱临床字段；**禁止**与源 `medicationId` 不一致；可改剂量/频次/首日量/用法/药房/备注等 |

#### Response（`ChangeLongTermMedicationDoseInpatientDoctorStationAdviceResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| newMedicationRequestId | Long | 新建草稿主键 |
| sourceMedicationRequestId | Long | 源嘱（已停或原已停） |
| basedOnId | Long | = 源嘱 id |

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | 校验 encounterId / sourceId / payload 非空；payload.medicationId 与源一致 | 否则 EX-IP-ORD-304 |
| 2 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` + **BR-IP-ORD-027** 可写门控 | EX-IP-ORD-107 |
| 3 | Pre | `MedicationRequestQueryService#loadMedicationRequestById`：同就诊、长期药品、状态 ∈ {VERIFIED, STOPPED} | 否则 EX-IP-ORD-304 |
| 4 | Cmd | 若 VERIFIED：`MedicationRequestDomainService#stopLongTermMedicationOrder`（stopTime 可覆写） | 写停嘱轴；清停嘱校对字段 |
| 5 | Cmd | `MedicationRequestDomainService#createMedicationRequestDraft`（或现行草稿创建命令）：透传临床字段；`therapyEnum=LONG_TERM`；`basedOnTable=med_medication_request`；`basedOnId=源 id`；**`groupId=null`（禁止继承源嘱/载荷组号）**；新 busNo / reqOpenedTime | 仅 Request，不挂费 |
| 6 | — | 返回新草稿 id；前端再调 **IF-IP-DOR-15** 签发 | 本 IF 不签发 |

#### 写库顺序

同事务：可选停嘱 → 建草稿。失败整批回滚。

#### 列表口服剩余字段（IF-IP-DOR-10/22 增补，已落地）

与护士站 **IF-IP-EXE-01** 同源 Support（**BR-IP-EXE-038** / `StandingOrderOralPackageInventorySupport`）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| oralPackageBatchInventoryApplied | Boolean | 是否命中一盒一点口服剩余规则 |
| oralPackageConsumedSplitQuantity | BigDecimal | 已吃（拆零） |
| oralPackageRemainSplitQuantity | BigDecimal | 剩余（拆零） |
| oralPackageSplitUnitDisplay | String | 拆零单位展示名 |
| oralPackageNextClaimDate | LocalDate | 预计下次领盒日；无可空 |
| basedOnId / basedOnTable | Long / String | 改量链回显（可选） |

#### 列表医技执行进度字段（IF-IP-DOR-10/22 增补，已落地）

与医技工作站 **AS-MT-EXE-001** / 出院 **EX-IP-ATD-309** 同 TECH 轴（**BR-IP-ORD-038**）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| techPlannedExecutionCount | Integer | 计划次数；仅 TECH 执行科诊疗有值 |
| techCompletedCount | Integer | 本 TECH 科 COMPLETED 次数 |
| techCancelledCount | Integer | 本 TECH 科取消次数 |
| performLocationId / performLocationName | Long / String | 诊疗执行科室（优先 `org_id`→组织名；与门诊 IF-DOR-10 同口径） |
| （展示）副文案 | — | 前端医嘱内容扩展行：`净≥计划`→「医技已执行」，否则「医技待执行」；**同段拼接** `performLocationName`（示例「医技已执行；检验科」）；非 TECH 诊疗仍可只展示科室名；**不加列**；不改 `displayStatus*` |

装配：`InpatientDoctorStationAdviceListAssembler#enrichMedicalTechExecutionProgress`（批量 `mapProcedureRecordsByRequestAnchorForEncounters`，禁 N+1）；执行科室名在 `mapServiceOrderLine` 由 `organizationQueryService.mapNamesByIds` 回填。

---

### 3.13 IF-IP-DOR-21 `createDischargeInpatientDoctorStationAdvice`（开立出院医嘱）

| 项 | 内容 |
| --- | --- |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/createDischargeInpatientDoctorStationAdvice` |

#### Request（`CreateDischargeInpatientDoctorStationAdviceRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 住院就诊 |
| patientId | Long | 是 | 患者 |
| plannedDischargeTime | LocalDateTime | 否 | 计划出院时间；空则当前时间 |
| outWayCode | String | 是 | 出院方式字典 code；死亡为 **`5`**（`OUT_WAY_CODE_DEATH`） |
| reasonText | String | 否 | 出院原因；空且死亡出院时回落 `deceasedReason` 写入 `reason_text` |
| deceasedDate | LocalDateTime | 条件 | 死亡时间；`outWayCode=5` 时必填，空 → **EX-IP-ORD-311**；回写 `adm_patient.deceased_date` |
| deceasedReason | String | 否 | 死亡原因；`outWayCode=5` 时选填；回写 `adm_patient.deceased_reason` |
| sourceOrgId | Long | 否 | 原科室 |
| sourceLocationId | Long | 否 | 原病区/床位 |
| targetOrgId | Long | 否 | 目标科室（离院场景可空） |
| targetLocationId | Long | 否 | 目标位置 |

#### Response（`CreateDischargeInpatientDoctorStationAdviceResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| serviceRequestId | Long | 出院 `ServiceRequest` 主键 |
| encounterHistoryId | Long | 离院里程碑记录主键 |
| success | Boolean | 是否成功 |
| message | String | 提示文案 |

#### 失败码

| 语义码 | 条件 |
| --- | --- |
| EX-IP-ORD-301 | **BR-SRQ-018** 吸氧/心电余量（实现期在 Pre 或 Cmd 前断言） |
| EX-IP-ORD-302 | `checkDuplicateTransferOrDischargeRequest` 为 true |
| EX-IP-ORD-311 | `outWayCode=5` 且 `deceasedDate` 为空（文案「死亡时间不能为空」） |
| EX-IP-ORD-207 | 同事务写失败 |

> **实现说明（v1.1）**：`createDischargeRequest` 落库 `category_enum=34`、`therapy_enum=TEMPORARY`（code 串，非 sortOrder 数字）、`generate_source_enum=DOCTOR_PRESCRIPTION`、`patient_id` 来自 Request、`activity_id=0` 占位；**不**解析诊疗目录出院 `ActivityDefinition`（**EX-IP-ORD-303** 本期不启用）。

#### 领域编排序列

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | Request 必填校验 | `outWayCode` 必填；`outWayCode=5` 时 `deceasedDate` 必填，空 → EX-IP-ORD-311 |
| 2 | Pre | `EncounterContextReader#mustGetInpatientContextByEncounterId` | 在院 |
| 3 | Pre | `ServiceRequestQueryService#checkDuplicateTransferOrDischargeRequest` | true → EX-IP-ORD-302 |
| 4 | Cmd | `ServiceRequestDomainService#createDischargeRequest` | 写 `reason_text`（优先 `reasonText`，空则 `deceasedReason`）、`occurrence_start_time`；发布 `DischargeRequestCreatedEvent` |
| 5 | Cmd | `EncounterHistoryDomainService#recordDischargeMilestone` | 同事务写 `out_way_code`、原/目标科室位置 |
| 6 | Cmd | `PatientDomainService#markPatientDeceased` | 出院申请签发之后、与创建/里程碑同一事务；仅 `outWayCode=5` 且患者、死亡时间齐全；写 `deceased_date`/`deceased_reason`；发布 `PatientDeceasedMarkedEvent`；非死亡跳过 |
| 7 | Asm | `InpatientDoctorStationAdviceMapper#toCreateDischargeResponse` | |

> **BR-SRQ-018**（吸氧/心电余量 → EX-IP-ORD-301）可在 App Pre 后置，未实现前不阻塞出院开立。

#### Mapper 签名

- `CreateDischargeRequestCommand toCreateDischargeRequestCommand(CreateDischargeInpatientDoctorStationAdviceRequest request, Long operatorId): CreateDischargeRequestCommand`
- `RecordDischargeMilestoneCommand toRecordDischargeMilestoneCommand(Long serviceRequestId, CreateDischargeInpatientDoctorStationAdviceRequest request, Long operatorId): RecordDischargeMilestoneCommand`
- `MarkPatientDeceasedCommand toMarkPatientDeceasedCommand(CreateDischargeInpatientDoctorStationAdviceRequest request): MarkPatientDeceasedCommand`（非死亡或缺少患者/死亡时间时返回 null，不调用域写）

---

### 3.14 IF-IP-DOR-22 `pageInpatientDoctorStationAdvice`（患者全类型医嘱分页查询）

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationAdviceAppService#pageInpatientDoctorStationAdvice` |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/pageInpatientDoctorStationAdvice` |
| **读口径真源** | 与 **IF-IP-DOR-10** 同构：药品 + 诊疗统一列表（**不含耗材**）；行 DTO 字段表见 **§3.2** `InpatientDoctorStationAdviceRowDto` |
| **与 IF-IP-DOR-10 差异** | ① 分页响应（`PagedResult`）；② **`adviceScopeTabCode`** 单接口分 Tab（`CURRENT_ADVICE` / `HISTORY`）；③ 历史 Tab 按 `patientId` 跨就诊聚合并排除当前 `encounterId`；④ 可选 **`therapyTypeCode`** / **`orderingDepartmentId`** / **`requestStatusEnumIn`** 过滤；⑤ **是否按处方形式展示由前端处理** |

#### Request 字段表（`PageInpatientDoctorStationAdviceRequest`）

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| encounterId | Long | 是 | 当前住院就诊；当前医嘱 Tab 查询锚点；历史 Tab 排除锚点 |
| patientId | Long | 是 | 患者主键；须与住院快照一致 |
| adviceScopeTabCode | String | 否 | `CURRENT_ADVICE`（默认）当前医嘱；`HISTORY` 患者历史医嘱（排除当前就诊） |
| therapyTypeCode | String | 否 | therapy 筛选：`LONG_TERM` / `TEMPORARY` / **`DISCHARGE_MEDICATION`**；空表示全部 |
| orderingDepartmentId | Long | 否 | 开单科室组织主键（`adm_organization.id`）；空表示不按科室过滤 |
| requestStatusEnumIn | List\<String\> | 否 | `RequestStatus` code 集合；空表示不按状态过滤 |
| pageNo | Integer | 是 | 页码（1-based） |
| pageSize | Integer | 是 | 页大小 |

#### Response 字段表（`PageInpatientDoctorStationAdviceResponse`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| content | List\<`InpatientDoctorStationAdviceRowDto`\> | 全类型医嘱统一列表行（与 IF-IP-DOR-10 同构；是否按处方展示由前端处理） |
| totalCount | Long | 总条数 |
| pageNo | Integer | 当前页（1-based） |
| pageSize | Integer | 页大小 |

**失败码口径表**

| 业务码 | 说明 |
| --- | --- |
| EX-IP-ORD-201 | 就诊不存在 |
| EX-IP-ORD-101 | 患者与就诊不一致 |

#### 领域编排序列（严格顺序）

| 序号 | 阶段 | 调用 | 输入要点 | 说明 |
| --- | --- | --- | --- | --- |
| ① | Gate | `EncounterContextReader#mustGetInpatientContextByEncounterId` + `InpatientDoctorStationAdviceMapper#validateInpatientEncounterPatientGate` | Request | 同 IF-IP-DOR-10 |
| ②a | Pre | `InpatientOrderListMergeAssembler#mergeDoctorStationOrders` → `InpatientDoctorStationAdviceListAssembler#assembleAdviceQueryRows` | `encounterId` + `therapyTypeCode` + 可选过滤 | **当前医嘱** Tab：仅当前就诊全类型医嘱 |
| ②b | Pre | `MedicationRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` + `ServiceRequestQueryService#listDistinctEncounterIdsByPatientForDoctorStation` → 逐就诊 `mergeDoctorStationOrders` + `assembleAdviceQueryRows` | `patientId` + `excludeEncounterId` + 可选过滤 | **历史医嘱** Tab：患者跨就诊全类型医嘱，按开立时间倒序 |
| ③ | Asm | `PagedResultBuilder#buildFromFullList` | ② 全量行 + `pageNo`/`pageSize` | 内存分页装配 |

---

### 3.15 IF-IP-DOR-23 `saveInpatientDoctorStationServiceApplyForm`（保存申请单·整张）

> **契约真源**：**AS-OP-DOR-003-SDD §3.5.4 IF-DOR-21**（Request/Response/DTO/编排序列 **同构**）。本节仅列住院差异。

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationAdviceAppService#saveInpatientDoctorStationServiceApplyForm` |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/saveInpatientDoctorStationServiceApplyForm` |

#### 住院差异

| 项 | 口径 |
| --- | --- |
| 就诊门控 | `EncounterContextReader#mustGetInpatientContextByEncounterId` → **`InpatientEncounterContextQueryModel`** |
| 长临 | **`therapy_enum=TEMPORARY`**（**BR-IP-REQ-007** 文档口径：长期无申请单入口） |
| 费用 | **不**挂 `ChargeItem`；费用在护士执行落库 |
| 取号 | **`BusinessSequenceService#getSeqByDay(REQUISITION_APPLICATION)`** → **`AR`** 前缀；**新建与编辑均取新号** |
| 编辑 | 传 **`previousPrescriptionNo`**（旧 `AR` 号）→ **`removeServiceRequestDraft`**（连带 Detail）→ **再取新号**重建（**不沿用**旧号，**BR-IP-REQ-002**） |
| 整单诊断 | 请求 **`encounterDiagnosisIds`**（有序 ids 串，主诊 id 第一）与 **`orgId` 同级**；编排透传至该单全部 `ServiceRequest`（契约见门诊 **§3.5.4**） |

#### Request / Response

类名：`SaveInpatientDoctorStationServiceApplyFormRequest` / `SaveInpatientDoctorStationServiceApplyFormResponse`；字段表与嵌套 **`ServiceApplyFormLineRequest`** / **`ServiceApplyFormDetailItemRequest`** 见门诊 **§3.5.4**（含整单 **`encounterDiagnosisIds`**）。

---

### 3.16 IF-IP-DOR-24 `listInpatientDoctorStationServiceApplyForms`（申请单列表）

> **契约真源**：**AS-OP-DOR-003-SDD §3.5.5 IF-DOR-22**。

| 项 | 内容 |
| --- | --- |
| **App** | `InpatientDoctorStationAdviceAppService#listInpatientDoctorStationServiceApplyForms` |
| **HTTP** | `POST /api/app/inpatientDoctorStationAdvice/listInpatientDoctorStationServiceApplyForms` |

类名：`ListInpatientDoctorStationServiceApplyFormsRequest` / `ListInpatientDoctorStationServiceApplyFormsResponse`；`forms[]` 为 **`ServiceApplyFormHeaderDto`** 分组结构；编排序列同门诊 **§3.5.5**（**`mapDetailListModelsByServiceRequestIds`**）。

---

## 4. QueryService 与事件刷新

### 4.1 查询服务与数据源

| 场景 | 查询入口 | IF |
| --- | --- | --- |
| 住院医生站列表 | `MedicationRequestQueryService / ServiceRequestQueryService / （住院医嘱列表不查 DeviceRequest）` | IF-IP-DOR-10 |
| 就诊门控 | `EncounterContextReader#mustGetInpatientContextByEncounterId` | 全部写/读 |
| 目录 | `ProductDefinitionCatalogPageLoader#pageProductDefinition` | IF-IP-DOR-11 |
| 医嘱查询分页 | `InpatientOrderListMergeAssembler` + `InpatientDoctorStationAdviceListAssembler` + `PagedResultBuilder#buildFromFullList` | IF-IP-DOR-22 |
| 申请单列表 | `ServiceRequestQueryService` + Detail 批量读 + 展示名解析 | IF-IP-DOR-24 |

### 4.2 QueryService 查询语义（事件 → InpatientOrderListSkeleton / InpatientEncounterContextQueryModel）

| 领域事件（DM §5） | 触发 IF | 投影目标 | 更新动作 |
| --- | --- | --- | --- |
| `MedicationOrderDraftCreatedEvent` / `Signed` / `SignOff` | 14～16 | 各 Request QueryService 查询主表 | 写后由 QueryService 直查；含 `generate_source_enum` |
| `ServiceRequestDraftCreatedEvent` / `Signed` / `Withdrawn` | 14～16 | `InpatientOrderListSkeleton` | 同上 |
| `LongTermMedicationOrderStoppedEvent` | 20 | `InpatientOrderListSkeleton` | UPDATE 状态/结束时间 |
| `ServiceRequestStoppedEvent` | 20 | `InpatientOrderListSkeleton` | UPDATE |
| `DischargeRequestCreatedEvent` | 21 | `InpatientOrderListSkeleton` | INSERT 出院行 |
| `DischargeMilestoneRecordedEvent` | 21 | `InpatientEncounterContextQueryModel` / 轨迹 | UPDATE（下游展示） |
| `ChargeItem*` 事件 | IF-IP-DOR-10（列表） | 费用摘要（护士执行后） | UPDATE |

字段级映射以 **`ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §4.2** 为唯一真源；本 SDD 不重复列级表。

---

## 5. 写库顺序（核心写接口视角）

### 5.1 Cmd 落库顺序一览

| IF | App 方法 | 聚合写入顺序（摘要） | 事务 |
| --- | --- | --- | --- |
| IF-IP-DOR-14 | `saveInpatientDoctorStationAdviceDraftBatch` | 药/诊 Request（长/临/出院带药均仅 Request） | 目标态 `@Transactional`；**首版无注解** |
| IF-IP-DOR-15 | `signInpatientDoctorStationAdviceBatch` | Request 签发（**不** FIFO / 费用） | 同上 |
| IF-IP-DOR-16 | `withdrawInpatientDoctorStationAdviceBatch` | signOff / withdrawSigned（**不**回滚费用/发放） | 同上 |
| IF-IP-DOR-17 | `updateInpatientDoctorStationMedicationAdviceGroups` | MedicationRequest.group_id | `@Transactional` |
| IF-IP-DOR-20 | `stopLongTermInpatientDoctorStationAdvice` | Med/Service Request STOPPED + `stop_time`/`stopper_id` | `@Transactional` |
| IF-IP-DOR-25 | `cancelStopLongTermInpatientDoctorStationAdvice` | Med/Service Request 恢复 ACTIVE/VERIFIED；清 `stop_time`/`stopper_id` | `@Transactional` |
| IF-IP-DOR-26 | `changeInpatientDoctorStationAdviceBusinessTime` | 仅写 `req_opened_time` 或 `stop_time` | `@Transactional`（目标态） |
| IF-IP-DOR-21 | `createDischargeInpatientDoctorStationAdvice` | ServiceRequest + EncounterHistory +（死亡时）Patient **同事务** | `@Transactional` |
| IF-IP-DOR-23 | `saveInpatientDoctorStationServiceApplyForm` | 删旧（可选）→ 取新 **`AR` 号** → 逐行 ServiceRequest + Detail | 目标态 `@Transactional` |

### 5.2 事务边界与失败回滚

| 接口 | 策略 |
| --- | --- |
| IF-IP-DOR-21 | `createDischargeRequest`、`recordDischargeMilestone` 与（死亡时）`markPatientDeceased` **必须同一事务**；任一失败全回滚（**AS §7 一致性**） |
| IF-IP-DOR-14 | 任一行 Cmd 失败 → 整批回滚 |
| IF-IP-DOR-15 | 外发严格模式：与门诊 §5.2 同策略 |

### 5.3 幂等落点

| IF | 策略 | 关联 §3 序号 |
| --- | --- | --- |
| IF-IP-DOR-14 | 请求主键 + 草稿态 | §3.8 |
| IF-IP-DOR-20 | 领域命令对非长期拒绝 | §3.12 步骤 3 |
| IF-IP-DOR-25 | 非 STOPPED / 出院门禁 Pre 拦截 | §3.12.1 步骤 3～4 |
| IF-IP-DOR-26 | timeKind/状态非法 Pre 拦截；签发列永不写 | §3.12.2 步骤 3～5 |
| IF-IP-DOR-21 | 重复出院 Pre 拦截 | §3.13 步骤 4 |

---

## 6. 非功能性需求实现（收敛为承诺表）

### 6.1 幂等性

| 场景 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 批次重复暂存 | 主键/草稿态门控 | §3.8 |
| 重复停嘱 | 已 STOPPED 拒绝或幂等成功 | §3.12 |
| 重复取消停嘱 | 非 STOPPED 拒绝 | §3.12.1 步骤 4 |
| 重复出院 | Pre 拦截 | §3.13 步骤 4 |

### 6.2 并发控制

| 场景 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 双端编辑同就诊 | 乐观锁/version 或后写覆盖 | §3.8～§3.9 |

### 6.3 重试与容错

| 场景 | 承诺 | 关联步骤 |
| --- | --- | --- |
| 只读失败 EX-IP-ORD-201 | 可重试 | §3.2 |
| 外发失败 | 忽略/严格可配置 | §3.9 |

### 6.4 数据脱敏

按机构策略在 Asm 统一处理患者展示字段。

### 6.5 审计日志

签发批次号、停嘱时间、**取消停嘱恢复态**、出院方式写入领域事件与应用审计；关联 §3.9、§3.12、§3.12.1、§3.13。

---

## 7. 测试要点

### 7.1 功能测试

| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| IF-IP-DOR-10 长/临/**出院带药**筛选 + 无护士划价 + 无耗材行 | 集成 | P0 |
| IF-IP-DOR-10 出院带药行：`therapyTypeCode=DISCHARGE_MEDICATION`；前端类型文案「出院带药」 | 集成 | P0 |
| IF-IP-DOR-10 未执行行无 chargeStatus；执行后有 rollup | 集成 | P0 |
| IF-IP-DOR-11 目录无 device 表 | 单元 | P0 |
| IF-IP-DOR-11 同库位多批预减：库位级预占 FIFO 顺序消耗一次（例 10+5−9=6；**规则 D** / `consumeLocationOccupancyForLot`） | 单元 | P0 |
| IF-IP-DOR-14 长/临/出院带药暂存均仅 Request | 集成 | P0 |
| IF-IP-DOR-14 出院带药：仅药品 + `DISCHARGE_MEDICATION`；皮试勾选不硬拒 | 集成 | P0 |
| IF-IP-DOR-14 DELETE 不 void 费用 | 集成 | P0 |
| IF-IP-DOR-16 长/临/出院带药签退仅 signOff/withdraw（不回滚费用/发放） | 集成 | P0 |
| IF-IP-DOR-14/15 三种 therapy 均不校验库存仍可保存 | 集成 | P0 |
| IF-IP-DOR-15 长/临/出院带药签发无 Dispense/Charge | 集成 | P0 |
| IF-IP-DOR-20 非长期拒绝；长期 STOPPED + stopper_id | 集成 | P0 |
| IF-IP-DOR-26 改开立开立人不变；改停嘱停嘱人不变；拒改签发 | 集成 | P0 |
| IF-IP-DOR-25 STOPPED→VERIFIED/ACTIVE；出院门禁 EX-IP-ORD-305 | 集成 | P0 |
| IF-IP-DOR-21 重复出院/里程碑同事务 | 集成 | P0 |
| IF-IP-DOR-21 死亡出院：`deceasedDate` 空 → **EX-IP-ORD-311**；齐全则同事务回写患者死亡时间/原因 | 集成 | P0 |
| IF-IP-DOR-21 非死亡出院：不调用 `markPatientDeceased` | 集成 | P0 |
| IF-IP-DOR-10 出院行：`outWayCode`/`reasonText`/`plannedDischargeTime` 有值、`contentSummary` 为 null；`outWayCode=5` 时 `adviceDisplayName`=「死亡出院」 | 集成 | P0 |
| IF-IP-DOR-11 目录可检索「嘱托」；`category_code=52`（**BR-IP-ORD-028**） | 集成 | P0 |
| IF-IP-DOR-14 嘱托：`entrustContent` 必填；空 → **EX-IP-ORD-306** | 集成 | P0 |
| IF-IP-DOR-14/17 同组药品频次不一致 → **EX-IP-ORD-308**（**BR-IP-ORD-033**） | 集成 | P0 |
| IF-IP-DOR-10/22 嘱托行：`entrustContent` 回显；前端内容列优先 `entrustContent` | 集成 | P0 |
| IF-IP-DOR-14 长期药：`firstDose`/`firstDoseUnitCode` 透传落库（**BR-IP-ORD-030**） | 集成 | P0 |
| IF-IP-DOR-14 临时/出院带药：首日量须空（域 **BR-MRQ-010**） | 集成 | P0 |
| IF-IP-DOR-10/22 长期药行回显 `firstDose*` | 集成 | P0 |
| IF-IP-DOR-14 长期西药：`doseSchedule`/`quantitySchedule` 段数=日次数落库（**BR-IP-ORD-037**） | 集成 | P0 |
| IF-IP-DOR-14 schedule 段数非法 → **EX-IP-ORD-310** | 集成 | P0 |
| IF-IP-DOR-10/22 有 schedule 时回显序列；空则仍走 `dose` | 集成 | P0 |
| IF-IP-DOR-12/18/19 与门诊同构冒烟 | 集成 | P1 |

### 7.2 非功能测试

| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| AS §7 性能目标（10≤1200ms 等） | 性能 | P1 |

### 7.3 QueryService一致性

| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 暂存/签发/停嘱/出院后 InpatientOrderListSkeleton QueryService | 集成 | P0 |

### 7.4 集成测试

| 测试项 | 自动化归属 | 优先级 |
| --- | --- | --- |
| 住院医生站：列表→目录→暂存→签发→停嘱→**取消停嘱**→**改开立/停嘱时间**→出院 | E2E | P0 |

### 7.5 测试缺口跟踪

| 缺口 ID | 描述 | 计划 |
| --- | --- | --- |
| — | 本 SDD **禁止** Gap 表；应用层未实现属目标态 | 随 `inpatient/doctorstation/advice` 模块落地关闭 |

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.62 | 2026-10-15 | **代码先行回写**：**BR-IP-ORD-028⑦**：嘱托频次仅展示、执行整单一次（**BR-IP-EXE-042**）；§1.1 嘱托边界同步；上游 **AS v1.58** |
| v1.61 | 2026-10-08 | **BR-IP-ORD-038**：§3.2 诊疗行 `performLocation*` 优先 `org_id` 组织名；扩展行「医技已执行/待执行」同段展示执行科室；Assembler `mapServiceOrderLine` 对齐门诊；上游 **AS v1.55** |
| v1.60 | 2026-10-08 | **死亡出院**：§3.13 `deceasedDate`/`deceasedReason`；`outWayCode=5` Gate **EX-IP-ORD-311**；同事务 `PatientDomainService#markPatientDeceased`；列表 `adviceDisplayName`「死亡出院」；上游 **AS v1.54** / **DM-ADM-001** §6.1.9 |
| v1.59 | 2026-10-07 | **IF-IP-DOR-19**：执行科室叠加业务类型层；对齐门诊 **AS-OP-DOR-003-SDD v3.59** / **DM-CUS-001 v1.5.0**；上游 **AS v1.53** |
| v1.58 | 2026-10-06 | **IF-IP-DOR-11**：`PageInpatientDoctorStationAdviceCatalogRequest.typeEnum` 下推业务类型；上游 **AS v1.52** / **DM-WKF-001 v1.0.17** |
| v1.57 | 2026-09-30 | **BR-IP-ORD-038**：§3.2/`InpatientDoctorStationAdviceRowDto` 增 `techPlannedExecutionCount`/`techCompletedCount`/`techCancelledCount`；Assembler 批量回填；上游 **AS v1.51** / **AS-MT-EXE-001** |
| v1.56 | 2026-09-23 | **BR-IP-ORD-037**：§1 术语；§3.2/§3.8 `doseSchedule`/`quantitySchedule`；Gate **EX-IP-ORD-310**；上游 **AS v1.50** / **DM-MED-003 v1.3.38**；执行算量交 **AS-IP-NUR-004 · BR-IP-EXE-040** |
| v1.55 | 2026-09-22 | **BR-IP-ORD-036**：`listDraftBindItems` / `resolveInpatientDoctorStationAdviceDraftBinds` 恒空；IF-IP-DOR-12 合管短路；未合管仅执行挂载；上游 **AS v1.49** / **AS-BAS-CFG-001 §7.7** |
| v1.54 | 2026-09-09 | IF-IP-DOR-10 列表增 `tcmFlag` / `chineseHerbsDoseQuantity`（病案首页同方饮片合并用 `tcmFlag`） |
| v1.53 | 2026-09-03 | **BR-IP-ORD-026⑥**：护理级别仅快捷入口；普通 IF-IP-DOR-11 结果由前端按 `categoryCode=26` 过滤；快捷开立载荷固定长期+`rateCode=QD`，后端透传；上游 **AS v1.45** |
| v1.52 | 2026-09-01 | **BR-IP-ORD-033 / EX-IP-ORD-308**：`InpatientMedicationAdviceGroupRateGate`；IF-IP-DOR-14/17 同组频次一致；上游 **AS v1.44** |
| v1.51 | 2026-08-19 | **改量/展示收口**：IF-IP-DOR-28 前端传可编辑 `stopTime`；`oralPackage*` 仅净领盒后回填；拆零单位字典名；改量新嘱 **`groupId` 强制空**（不与旧嘱并组）；上游 **AS v1.43** / **DD** |
| v1.50 | 2026-08-19 | **IF-IP-DOR-28 / BR-IP-EXE-038 成码落地**：`changeLongTermMedicationDose…`（新嘱 DRAFT 待签发）；`StandingOrderOralPackageInventorySupport`；列表/待执计数同源；Request 增补 `patientId`；上游 **AS v1.42**；**DD** §5 实现锚点同步 |
| v1.49 | 2026-08-19 | **IF-IP-DOR-28 / BR-IP-ORD-032**：长期药品改量契约（停旧+同药草稿、`based_on`）；列表口服剩余字段与 **BR-IP-EXE-038** 同源；上游 **AS v1.41**；**DD-住院长嘱口服一盒一点剩余计数与改量-2026-08** |
| v1.48 | 2026-08-18 | **IF-IP-DOR-11 同库多批预减**：§2.1 Helper 增补 **`copyOccupancyRemaining` / `consumeLocationOccupancyForLot`**；§3.3 母版引用 **AS-OP-DOR-003-SDD v3.49** 规则 **D**；§7.1 测试矩阵同步；数值例 10+5−9=6；上游 **AS v1.40** |
| v1.47 | 2026-08-13 | **IF-IP-DOR-16 签退 Gate**：发药口径改为净已发数量对冲（与 **AS-IP-NUR-003 v1.19** / `InpatientAdviceRevokeGate` 同构；DM-MED-002 §6.1.4） |
| v1.46 | 2026-08-08 | **停嘱校对门禁**：§3.12 停嘱须 VERIFIED（诊侧 COMPLETED 门诊保留）；§3.12.1 取消停嘱须未停嘱校对并清停嘱校对字段；改停嘱时间保留停嘱校对；对齐 **AS v1.39** / **AS-IP-NUR-003 v1.16** |
| v1.45 | 2026-08-04 | **输液滴速**：IF-IP-DOR-10 列表 `speedCode`/`speedName`；IF-IP-DOR-18 `medicationSpeedOptions`；IF-IP-DOR-14 药品载荷 `speedCode` → `Create`/`ChangeMedicationOrderDraftCommand`；对齐 **DM-MED-003** `speed_code` |
| v1.44 | 2026-08-02 | **IF-IP-DOR-18 部位/样本模糊检索**：与门诊 **AS-OP-DOR-003-SDD v3.46** 同构——`OptionDto.pyStr`/`wbStr` 透出；申请单检查/检验下拉前端拼音·五笔过滤；上游 **AS-OP-DOR-003 v1.58** |
| v1.43 | 2026-07-26 | **皮试伴生对齐成码**：正文类名统一 **`DoctorStationSkinTestSyncOrchestrator`**；§3.8.1 / §3.10 **ACTIVE-only** 签退 + **`collectCompanionSkinTestServiceRequestIds`** 去重；**`SkinTestCompanionDisplaySupport`** 关联文案含规格；合并 v1.42 DRAFT 幂等与 **IF-IP-DOR-27**；上游 **AS v1.36** / **DM-WKF-004 v1.1.27** |
| v1.42 | 2026-07-26 | IF-IP-DOR-16：`withdrawSignedServiceRequest` **已是 DRAFT 幂等返回**（皮试联动+同批显式勾选）；上游 **DM-WKF-004 v1.1.27** |
| v1.42 | 2026-07-22 | **IF-IP-DOR-27** 长期皮试药追加 companion；**BR-IP-ORD-024** 多 companion / expand 全部草稿；列表 `needAppendSkinTestFlag`；上游 **AS v1.35** |
| v1.41 | 2026-07-18 | **DD §11.7 诊断绑定**：列表/载荷/申请单整单改为 **`encounterDiagnosisIds`** 有序 ids 串（主诊第一）；对齐门诊 **AS-SDD v3.40** |
| v1.40 | 2026-07-18 | **文档对齐成码**：§2.3 停嘱人「已落地」；§3.2 开立人字段改为实际下发 `authoredById`/`authoredByName`（映射 `requester_id`）；护士站 SDD 同步 `stopper*` |
| v1.39 | 2026-07-18 | **App IF-IP-DOR-26 已落地**：`changeInpatientDoctorStationAdviceBusinessTime` + Mapper；列表/护士站 `stopperId`/`stopperName`；十六 public 方法；上游 **AS v1.33** |
| v1.38 | 2026-07-18 | **G-IP-DOR-003-06 Closed**：领域 `stopper_id`/改时命令已落地；§1.1 实现口径更新；App IF-IP-DOR-26 当时仍为目标态（已被 v1.39 关闭）；上游 **AS v1.32** |
| v1.37 | 2026-07-18 | **三时间轴与改业务时间**：**BR-IP-ORD-031**；**IF-IP-DOR-26** `changeInpatientDoctorStationAdviceBusinessTime`；列表 `requester*`/`stopper*`；停嘱/取消停嘱写清 `stopper_id`；签发不可改；**EX-IP-ORD-307**；依赖 **G-IP-DOR-003-06**；上游 **AS v1.31** |
| v1.36 | 2026-07-17 | **BR-IP-ORD-024**：皮试 companion `therapyEnum` 固定 `TEMPORARY`（对齐门诊 BR-ORD-025；废止继承皮试药长临）；§3.8.1 同步 |
| v1.35 | 2026-07-16 | **首日用量已落地**：§3.2/§3.8「目标态」→「已落地」；执行侧对齐 **AS-IP-NUR-004 v2.34** / **AS-SDD v2.18** |
| v1.34 | 2026-07-16 | **首日用量（BR-IP-ORD-030）**：§1.1/§1.2 术语；§3.2 列表 `firstDose*`；§3.8 药品载荷与 Cmd 透传；§7 测试；上游 **AS v1.29**；执行侧意图交 **AS-IP-NUR-004** |
| v1.33 | 2026-07-16 | **BR-IP-ORD-028④ 展示同源**：`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`；护士站 **OrderView** `adviceDisplayName` 与医生站 `contentSummary` 对齐；§2.1/嘱托装配增补；对齐 **AS-IP-NUR-003-SDD v1.3.4** |
| v1.32 | 2026-07-16 | **嘱托/出院带药 App 编排 Closed**：`InpatientEntrustOrderGateSupport`（`application.shared.advice`）；IF-IP-DOR-14 透传 `entrustContent`/`DISCHARGE_MEDICATION`；列表 `contentSummary`/`therapyTypeText`；对齐 **AS v1.27** |
| v1.31 | 2026-07-16 | **G-IP-DOR-003-05 Closed**：§2.3 `TherapyType.DISCHARGE_MEDICATION` 已落地；对齐 **DM-MED-003 v1.3.24** / **DM-WKF-004 v1.1.23** / **AS v1.26**；App 出院带药编排仍为目标态（已被 v1.32 关闭） |
| v1.30 | 2026-07-16 | **出院带药（BR-IP-ORD-029）**：§1.1/§1.2/`TherapyType.DISCHARGE_MEDICATION`（目标态）；§2.3/§3.2/§3.8/§3.14 `therapyTypeCode`；列表类型文案与暂存仅药品；§7 测试；依赖 **G-IP-DOR-003-05**；上游 **AS v1.25** |
| v1.29 | 2026-07-16 | **纠正**：嘱托改 **`category_code=52`**（撤回 `ActivityType.ENTRUST`）；§2.3/`SERVICE_CATEGORY_ENTRUST`；上游 **AS v1.24** / **DM-WKF-004 v1.1.22** |
| v1.28 | 2026-07-16 | **G-IP-DOR-003-04 Closed**（初版误用 ActivityType，已被 v1.29 纠正） |
| v1.27 | 2026-07-16 | **嘱托医嘱（BR-IP-ORD-028）**：§1.1/§1.2/`ActivityType.ENTRUST`；§2.3/§2.5 Gate；§3.2 列表 `entrustContent`/`activityType*`；§3.8 载荷与编排序列正文必填（**EX-IP-ORD-306**）；§7 测试；上游 **AS v1.22**；依赖 **G-IP-DOR-003-04** |
| v1.26 | 2026-07-06 | **IF-IP-DOR-11**：`categoryEnum=26`（护理级别）直写 ActivityDef `category_code`，**不**经申请单 21/22/23 映射（**BR-IP-ORD-026**）；§3.3 Request/编排序列同步 |
| v1.26 | 2026-07-11 | **BR-IP-ORD-027**：`validateInpatientEncounterWritableGate` 增补 `PENDING`/`COMPLETED`/`DISCHARGED` → **EX-IP-ORD-107**；对齐 **AS v1.21** / **BR-IP-FLW-008** |
| v1.25 | 2026-06-24 | **IF-IP-DOR-18 身体部位树形下拉**：§3.6 与门诊 **AS-OP-DOR-003-SDD v3.35** 对齐；`bodyStructureOptions` 树形 ACTIVE |
| v1.24 | 2026-06-20 | **orchestration 落点对齐**：§2 包树上收 `module.orchestration.advice`；皮试 AppService `DTO↔Spec` 边界；对齐 **代码生成通用规范 §2.2.1** |（已迁至 application.shared.advice）
| v1.23 | 2026-06-20 | **support 落点对齐**（已被 v1.24 细化）：`DoctorStationSkinTestSyncOrchestrationsupport、`ServiceApplyFormOrchestrationsupport → `module.orchestration.advice` |（已迁至 application.shared.advice）
| v1.22 | 2026-06-18 | **IF-IP-DOR-25 成码闭环**：§1.1 实现口径更新（十五方法、**G-IP-DOR-003-03 Closed**）；§2.4/§2.5 查询与 Mapper 白名单补 IF-IP-DOR-25；上游 **AS v1.17** |
| v1.21 | 2026-06-18 | **IF-IP-DOR-25 取消停嘱**：`cancelStopLongTermInpatientDoctorStationAdvice`；**BR-IP-ORD-025**（`performer_check_id`→VERIFIED/ACTIVE）；§2.4 白名单 + §3.12.1 + §5/§6/§7；上游 **AS v1.16** |
| v1.20 | 2026-06-18 | **住院皮试联动（BR-IP-ORD-024）**：新增 **`DoctorStationSkinTestSyncOrchestrationsupport**；**§3.8.1** IF-IP-DOR-14 皮试 companion SR（**不**挂费）；**§3.9/§3.10** 签发 expand / 签退对称；列表 **`skinTestOutcome*`** + `AllergyIntoleranceQueryService`；上游 **AS v1.15** / **AS-IP-NUR-011 v1.1** |
| v1.19 | 2026-06-17 | **收尾**：删除住院 Mapper 费用/FIFO 孤儿方法；移除 `SaveInpatientMedicationAdviceDraftLinePayload.dispenseLocationId`；列表/目录注释对齐护士执行后费用与库位；§2/§3/§5/§7 清除临嘱开嘱挂费残留 |
| v1.18 | 2026-06-17 | **架构决议**：住院长临暂存/签发仅 Request；移除 `createChargeIfPossible`/FIFO 签发路径；对齐 **AS v1.14** / **AS-IP-NUR-004 v2.25** |
| v1.17 | 2026-06-17 | **IF-IP-DOR-19** 引用门诊 **§3.5.2 前端执行科室下拉（BR-ORD-024）**；上游 **AS v1.13** / **AS-OP-DOR-003-SDD v3.29** |
| v1.16 | 2026-06-17 | **IF-IP-DOR-19 / DM-CUS-001 v1.2.0**：对齐门诊 **AS-OP-DOR-003-SDD v3.10**——`listExecutionRulesForActivityResolution` + `ProductDefinitionBatchLoader`；§3.7、§2.4 同步 |
| v1.15 | 2026-06-17 | **IF-IP-DOR-11** 目录单位口径对齐 **AS-OP-DOR-003-SDD v3.28**（包装单位主单位、不下发 defEncounter/defInhospital） |
| v1.14 | 2026-06-16 | **IF-IP-DOR-23 整单诊断绑定**：复用门诊 **AS-OP-DOR-003-SDD v3.26** `SaveDoctorStationServiceApplyFormRequest` 增补字段；§3.15 住院差异增「整单诊断」行 |
| v1.13 | 2026-06-16 | **成码对齐**：复用门诊 **`ServiceApplyFormAssembler`** + **`ServiceApplyFormOrchestrationsupport**；引用 **AS-OP-DOR-003-SDD v3.25** |
| v1.12 | 2026-06-16 | **申请单 IF-IP-DOR-23/24**：`saveInpatientDoctorStationServiceApplyForm` / `listInpatientDoctorStationServiceApplyForms`；**`AR` 号**；编辑**删旧取新号**；复用门诊 **§3.5.4/§3.5.5**；**IF-IP-DOR-18** 增部位/样本；**IF-IP-DOR-11** `categoryEnum`；上游 **AS v1.9** |
| v1.11 | 2026-06-12 | **IF-IP-DOR-22 读口径收敛为全类型医嘱**：`pageInpatientDoctorStationAdvice`；行 DTO 与 **IF-IP-DOR-10** 同构；复用 `InpatientOrderListMergeAssembler` + `InpatientDoctorStationAdviceListAssembler` + `PagedResultBuilder#buildFromFullList`；移除 slip/药品专用契约；上游 **AS v1.8** |
| v1.10 | 2026-06-12 | 增补 **§3.14 IF-IP-DOR-22** 契约与编排序列；响应**不含** `inventoryDetailList`；§2.4/§2.5/§4.1 白名单对齐成码；上游 **AS v1.7** |
| v1.9 | 2026-06-12 | 新增 **IF-IP-DOR-22** `pageInpatientDoctorStationMedicationAdvice`：药品医嘱查询（当前/历史 Tab）；Loader 住院历史范围；对齐门诊 **AS-OP-DOR-003-SDD v3.21**；上游 **AS v1.6** |
| v1.8 | 2026-06-11 | **废止 `sign_code`**；§3.2 列表行与 IF-IP-DOR-14 载荷 **`conditionDefinitionId` + `encounterDiagnosisId` 成对落库/回显**（与门诊对称） |
| v1.7 | 2026-06-11 | **临时药品签发处方号**：`InpatientDoctorStationAdviceAppService#signMedicationTarget` 改 `COMPOSITE_PRESCRIPTION`（`CP`）；`sign_code` 与 `bus_no` 前缀分离；对齐 **DM-MED-003** / **AS-OP-DOR-003-SDD v3.15** |
| v1.6 | 2026-05-28 | 六维对账：§1.1/§1.2/§5 事务口径与 `InpatientDoctorStationAdviceAppService` 对齐；上游 **AS v1.6** |
| v1.5 | 2026-05-28 | **BR-IP-ORD-006/016 成码**：§2.1 **`ProductDefinitionOrderChargePricingsupport**；§3.8 暂存挂费 **`quantityUnit`/`productRow`**；撤销 **`ServiceRequestDomainService` 长期 quantity 强制 1**；对齐 **AS v1.5** / **DM-WKF-004 BR-SRQ-007** |
| v1.4.1 | 2026-05-28 | **诊疗草稿 Gate**：`ServiceAdviceDraftCommandsupport **`requireQuantity`/`requireUnitCode`**（与门诊 v3.11 同口径） |
| v1.4 | 2026-05-28 | **L3 字段对账**：§3.2 列表行 `productTableName`/排序号/套餐子项；§3.8 IF-IP-DOR-14 展开暂存 Request/嵌套 Payload 全表，诊疗 `quantity`/`unitCode` 与 **`ServiceAdviceDraftCommandsupport** 一致 |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.3 | 2026-05-27 | **BR-IP-ORD-022**：§2.1 **`resolveUnitDisplayName`**；§2.2 列表/绑定装配；§3.2 **`unitName`/`doseUnitName`**；母版 **AS-OP-DOR-003-SDD v3.9** |
| v1.2 | 2026-05-27 | **BR-IP-ORD-018 / 门诊 v3.8 对齐**：§2.1 共享 **`DoctorStationAdviceInventoryUnitQuantityHelper`**；§2.2 Catalog/FIFO 装配器；§3.3 母版引用 **规则 C/H**；§3.9 临时药品 FIFO **`unitCode` 与整包装分段**；上游 **AS-IP-DOR-003 v1.2**、**AS-OP-DOR-003-SDD v3.8** |
| v1.0 | 2026-05-21 | 初版：对齐 **AS-IP-DOR-003** v1.0；IF-IP-DOR-10～21（**13 废止**）；**目标态** `InpatientDoctorStationAdviceAppService`；列表 **`MedicationRequestQueryService / ServiceRequestQueryService / （住院医嘱列表不查 DeviceRequest）`（Q-ORD-007，G-IP-DOR-003-01 已关闭）**；住院差异：TherapyType 长临分轨、无耗材、无库存校验、停嘱 IF-20、出院 IF-21；门诊母版 **AS-OP-DOR-003-SDD** 可复用章节显式引用 |
| v1.1 | 2026-05-24 | §3.2 增补出院/转科 DTO 与装配（**BR-IP-ORD-017**、`mapListModelsByIds`、里程碑 `outWayCode`）；§3.13 与 `createDischargeRequest` 实现对齐；前端 `advice-list.ts` 映射说明 |
