# 住院护士站 · 医嘱执行 · 应用服务详细设计（SDD）

> 编码：AS-IP-NUR-004-SDD  
> 版本：v2.53 | 日期：2026-10-15 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 住院护士站工作台 · 医嘱执行 Tab（列表读 + 执行/不执行写） |
| 实现阶段口径 | **目标态全量契约**（IF-IP-EXE-01/02/04）；**域层 Gap 001～006 已关闭**（见 **AS-IP-NUR-004 §9**）；**应用层已落包** `inpatient/nursestation/orderexecute`，AppService **`NurseStationOrderExecuteAppService`**，HTTP 前缀 **`nurseStationOrderExecute`** |
| 编排权威 | `AS-IP-NUR-004-住院护士站医嘱执行.md`（**v2.68**）§1.3、§4 |
| 方法权威 | `DM-CLN-004` §6.1.6；`DM-MED-002` §6.1.7/7a、§6.2.10；`DM-ADM-011` §6.1.4c/d；`DM-CUS-008` §9.1；`DM-WKF-008` §6.2.12；`InpatientOrderListSkeleton` **Q-IP-EXE-001** |
| 严格对齐 | IF 编号 **IF-IP-EXE-01、02、04**；**IF-IP-EXE-03 本期不做** |

**Out of scope（重复 AS §1.4）**

| 类别 | 内容 |
| --- | --- |
| 左侧患者树 | **AS-IP-NUR-001** |
| 医嘱校对 | **AS-IP-NUR-003** |
| 取消执行 | **IF-IP-EXE-03**（汇总领药前置） |
| 账户 | **`accountId` 仅由 IF-IP-NUR-02 患者列表查出**；执行模块（IF-IP-EXE-01/02）**禁止**注入 `AccountQueryService` 或再查库 |
| UI「首日执行」筛选开关 | 后端不处理（与 **BR-IP-EXE-032 首日用量** 分轨） |

**上游依赖**

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001` | 可见范围 **BR-IP-NUR-003** |
| `AS-IP-NUR-003` | 列表仅 **VERIFIED** 医嘱进入执行 |
| `orderview`（**AS-IP-NUR-003-SDD §2.5**） | 列表行展示字段委托 `NurseStationOrderViewListAssembler` |
| `AS-IP-DOR-003` | bindItems 模板查询语义 |
| `AS-IP-DOR-006` | 住院中医下游：`medicineCategoryTab`、一方一行、整方写扩行、**BR-IP-TCM-008** 代煎费 |
| `InpatientDoctorStationAdviceFifoAllocationAssembler` | FIFO 分配参照实现 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 状态 code | 持久化与比较一律 **String `code`** |
| executeTab | App 白名单：`pending` / `executed` / `skipped` / `cancelled`（**必填**；过滤**计划时间点/槽位**是否回传，**医嘱行不变**，**BR-IP-EXE-010**；**无 `all`**；**仅列表**） |
| therapyType | `all` / `long` / `temp` → `therapyEnum` 或不过滤 |
| executionDeadline | **仅 IF-IP-EXE-01** 列表展示窗口上界（与停嘱取 min 为频次展开 `end`）；一盒一点判定包装批次 N 盒首锚点（**BR-IP-EXE-035**）；拆零下限判定单位批次首槽（**BR-IP-EXE-036**）；Tab 仍用常规 `planTime ≤ deadline`；写接口可留字段但 **Gate 不使用** |
| reqOpenedTimeFrom / reqOpenedTimeTo | **仅 IF-IP-EXE-01 可选**：装载后按行 `reqOpenedTime` 闭区间收窄医嘱行；两端皆空不过滤；同时传须 from ≤ to；缺开嘱时刻行保留（**BR-IP-EXE-037**；打印中心检查/检验/采血贴） |
| 列表频次展开 `start` | 开立 `req_opened_time` 自然日 **00:00:00** 作展开左界（空回落 `req_authored_time`；**BR-IP-EXE-004①**）；展开后对开嘱日相对开嘱时刻裁首次槽（**BR-IP-EXE-004②/032**）；禁止 `effective_dose_start` / `check_time` 作为 `start` |
| 首日用量（首次） | `LongTermFirstExecutionSlotSupport` + `InpatientMedicationRequestExecutionMeasureHelper`；列表 pending 首次槽 `defaultActualTime`=开立+2 分钟（跨日钳开立日末；临嘱单槽同口径）；写侧库存/发药仅首次槽换 `first_dose`（**BR-IP-EXE-032**） |
| 分次用量不一致 | 同 Helper：`quantity_schedule` 空 → 仍读 `quantity`；有值 → 按槽序取段作临床拆零量后再进 035/036（**BR-IP-EXE-040**）；列表行回传 `doseSchedule`/`quantitySchedule`（**BR-IP-EXE-012**） |
| 停嘱日末槽（易用） | 同 Support：已停嘱时停嘱日最后一个 `≤ stop` 的 pending 槽 `defaultActualTime`=停嘱；同槽开嘱优先；停嘱早于当日首点 → 当日无点（**BR-IP-EXE-032⑤ / 030**） |
| 长嘱不可拆（一盒一点） | `InpatientStandingOrderSplittableSettingResolver` + `StandingOrderPackageBatchEligibilitySupport`（含 **`isInfusionCatalog`**：`injectFlag=true` 一律不合格）+ `StandingOrderPackageBatchExpandSupport` + `StandingOrderPackageBatchPlanTimeResolver`；列表 `assemblePatientGroups` 与出院门禁 `listLongTermStoppedPendingPlanTimes`（**BR-IP-ATD-011①′**）同源启用；包装比取 `ProductDefinitionDisplayDto.packageUnitRatio`；列表回传盒首锚点、pending 至多一个（**BR-IP-EXE-035③**）；停嘱硬顶未凑满 / 探测耗尽 → 仅已完整盒首或**空锚点**、**不**降级可拆日点（**BR-IP-EXE-035⑤**）；写路径整包装量（**BR-IP-EXE-035④**）；与 **BR-IP-EXE-036** 正交且优先；**输液目录即使用法误开口服也不切盒** |
| 口服包装剩余（一盒一点） | **BR-IP-EXE-038 PACKAGE** / **DD-住院长嘱口服一盒一点剩余计数与改量-2026-08**：读路径实时；`StandingOrderOralPackageInventorySupport`（`UnitClaimMode.PACKAGE`）沿 `based_on` 链汇总领入/已吃/剩余；按自然日抑制下一盒 pending；空窗补点经 `allowsNextClaimPendingReinjection`（**不执行 STOPPED 与净 COMPLETED 同为终态，禁止只认已执行**）；展示宿主=链尾或观察期已停条；与医生站列表同源；**不**改写路径整盒量；输液不进 PACKAGE；MIN_SPLIT 经 `shouldSkipMinSplitReuseForOpenDiscardInfusion`（皮下注射放行） |
| 拆零单位下限（产品默认） | `StandingOrderMinSplitUnitBatchExpandSupport` + `StandingOrderPackageBatchPlanTimeResolver#tryMinSplitUnitBatchPlanTimes`（经 `shouldSkipMinSplitReuseForOpenDiscardInfusion`：输液默认跳过，**皮下注射放行**）；单槽临床拆零 &lt; 1 时列表按满 1 拆零切批；写路径 `applyMinSplitUnitFloor` 抬到 1 拆零（**BR-IP-EXE-036**）；**剩余/改量/出院**叠加 **BR-IP-EXE-038 MIN_SPLIT**（领入=1；开瓶即弃输液不叠）；待执计数同源 |
| planTime | Procedure **`occurrence_time`**；发药 **`plannedDispenseTime`**；账单 Charge.**`occurrence_time`**（**逻辑**：防重/月封顶/汇总领药勾选时间点）。**临时执行写前强制 = actualTime**（**BR-IP-EXE-006**） |
| actualTime | Procedure **`recorded_time`**；默认：开嘱日首次=开立+2 分钟、已停嘱停嘱日末=停嘱、其余=`planTime`（**BR-IP-EXE-030/032**）；**仅同自然日可改 HH:mm**。**长期**：仅展示，**禁止**改写 plannedDispenseTime / 账单 occurrence。**临时**：与 planTime **永远同值**（改不改时分都一样；默认开立+2 分钟） |
| 写 Gate 时间边界 | **BR-IP-EXE-024**：① `planTime` 晚于停嘱 → 拒绝；② `actualTime` 早于开立（`reqOpenedTime` 空回落 `reqAuthoredTime`；秒级；相等允许；缺字段不拦）→ 拒绝（**EX-IP-EXE-303**）；中药整方 / 西药成组扩行后写前 **`liftExpandedExecuteActualTimesNotBeforeOpened`** 抬齐早于成员开立的共用 `actualTime`（临嘱同步 plan；须同日）；**BR-IP-EXE-030**：`actualTime` 与 `planTime` 非同日 → 拒绝；执行净态 Gate 见 **BR-IP-EXE-008/008R/014**（`isPlanTimeExecutable`：pending / **hedged pending** / 孤立 cancelled）；不执行 Gate 见 **BR-IP-EXE-021**（`isPlanTimePending`：pending / **hedged pending**）；前端「校验执行时间」只读扫与写 Gate②同口径（成组合并行开立取组内最晚，**BR-IP-NUR-004-GRP-001**） |
| hedged pending | 执行-取消对冲净值（**BR-NUR-005**）；`NurseStationOrderExecuteListReadAssembler` 读侧 **pendingSlots + cancelledSlots** 双写；写 Gate 与 pending 同构 |
| 库存 Gate | **仅药嘱**：App 层 `findAvailableCatalogRowsForOrder` − `listDispenseExeReserve`（**全局**）；FIFO dry-run 通过后写 `locationId`+`lotNumber`；**绑定耗材不参与** Gate/扣减 |
| **与开嘱/划价边界** | **IF-IP-EXE-02 长期执行**与 IF-DOR-11/IF-IP-DOR-14/IF-IP-PRC 的「目录解析库位 + 写路径透传」**无关**；长期每次执行在写路径现场 FIFO 定库位/批号/售价（**BR-IP-EXE-016**）。**临嘱**执行挂接签发侧 Dispense/Charge，亦**不**消费开嘱暂存时的 `dispenseLocationId` |
| 发放类别（categoryCode） | `NurseStationExecutionInventoryAssembler` 按 `medicationId` 批量读 `ProductDefinitionDisplayDto`；`distributionCategoryCode = firstNonBlank(demandLine.categoryCode, product.categoryCode, DEFAULT)`；调用 `listCandidateDistributionLocationIds(orgId, med_medication_definition, distributionCategoryCode, null)`；与 `InpatientDoctorStationAdviceFifoAllocationAssembler` **同序回落** |
| 长临数量 | 长期 req=**单次量**；临时=**总量**；须 **拆零单位**换算（`MedicationProductQuantityToSplitUnitConverter`） |
| HTTP | **`POST`** + `AppServiceControllerRegistrar`；Bean **`NurseStationOrderExecuteAppService`** |
| 批量上限 | 写接口单批 **≤50** 槽位 |
| medicineCategoryTab | `west`（默认）/ `tcm`；中药 Tab 仅药嘱、不展示诊疗（**BR-IP-NUR-004-TCM-001**） |
| anesthesiaApplyOnly | `true`=仅麻醉申请（申请单号 AA 前缀）；`false`/空=排除麻醉申请（**BR-IP-NUR-004-ANES-001**；无类目 25 / 同单号池） |

## 2. 代码结构

```text
zxplat/health-application/.../inpatient/nursestation/
  orderexecute/
    service/
      NurseStationOrderExecuteAppService.java
    request/
      ListNurseStationOrderExecuteRequest.java
      ExecuteNurseStationOrdersRequest.java
      SkipNurseStationOrderSlotsRequest.java
    response/
      ListNurseStationOrderExecuteResponse.java
      ExecuteNurseStationOrdersResponse.java
      SkipNurseStationOrderSlotsResponse.java
    dto/
      NurseStationOrderExecutePatientGroupDto.java
      NurseStationOrderExecuteRowDto.java
      NurseStationOrderExecuteSlotDto.java
      NurseStationOrderExecuteBindItemDto.java
      NurseStationOrderExecuteWriteSlotDto.java
    assembler/
      NurseStationOrderExecuteListAssembler.java
      NurseStationExecutionInventoryAssembler.java
    mapper/
      NurseStationOrderExecuteMapper.java
      NurseStationOrderExecuteMapperImpl.java
  orderexecute/
    support/
      InpatientTcmDecoctionExecuteSupport.java      # 仅执行子模块写编排

zxplat/health-application/.../application/shared/advice/
  LongTermFirstExecutionSlotSupport.java            # 开嘱日首次槽裁剪（BR-IP-EXE-032）
  InpatientMedicationRequestExecutionMeasureHelper.java  # 首次槽换量；一盒一点/拆零下限写量（BR-IP-EXE-035④/036）
  InpatientStandingOrderSplittableSettingResolver.java   # 长嘱是否可拆开关
  StandingOrderPackageBatchEligibilitySupport.java       # 一盒一点资格：长期+非输液+包装≠拆零+口服类；isInfusionCatalog（BR-IP-EXE-035②）
  StandingOrderPackageBatchExpandSupport.java            # 切盒取盒首锚点（BR-IP-EXE-035③）
  StandingOrderOralPackageInventorySupport.java          # 单位领用剩余/抑制/领用日补点 Gate（BR-IP-EXE-038；allowsNextClaimPendingReinjection）
  StandingOrderMinSplitUnitBatchExpandSupport.java       # 拆零单位下限切批（BR-IP-EXE-036）
  StandingOrderPackageBatchPlanTimeResolver.java         # 035/036 计划点解析（列表+待执计数）

zxplat/health-application/.../application/shared/nursestation-device-dispense/
  DeviceDispenseNurseWorkflowSupport.java           # 划价/执行/取消/门诊处置共用

zxplat/health-application/.../application/inpatient/nursestation/{feature}/support/
  InpatientNurseStationTcmSupport.java
  NurseStationOrderViewListAssembler.java
  NurseStationOrderExecuteListLoader.java
  NurseStationOrderExecuteListReadAssembler.java
  dto/...
```

### 2.1 依赖注入表

| 依赖 | 用途 |
| --- | --- |
| `CurrentOperatorResolver` | 写路径 `operatorId`（**B.6.5**） |
| `CurrentPractitionerResolver` | 写路径执行人 `performerId`（**B.6.5**） |
| `CurrentManagedWardLocationIdsResolver` | 管理病区可见 encounter（**BR-IP-EXE-001**；**B.6.5**） |
| `LocationQueryService` | 管理病区 |
| `EncounterQueryService` | 可见 encounter（**BR-IP-EXE-001**） |
| `EncounterContextReader` | 住院就诊上下文（门控/列表，`application.shared.encounter`） |
| `InpatientOrderListMergeAssembler` | 药/诊/材 QueryModel → `InpatientOrderListSkeleton` |
| `InpatientOrderLookupLoader` | 列表骨架加载（`application.shared.advice`） |
| `MedicationRequestQueryService / ServiceRequestQueryService` | **Q-IP-EXE-001** 骨架（**不含** DeviceRequest 时与代码一致） |
| `MedicationRequestQueryService` / `ServiceRequestQueryService` | 明细补全 |
| `FrequencyQueryService` | `computeExecutionTimePoints` |
| `ProcedureQueryService` | 槽位 Procedure 批量加载（列表）；写路径 `getProcedureRecords` **按 `orderKey` 去重**；列表 **`mapPrimaryPractitionerIdByProcedureIds` / `listByIds`**（BR-IP-EXE-031） |
| `PractitionerQueryService` | 列表终态槽位 **`operatorName`**（`mapDisplayNameByEntererIds`；BR-IP-EXE-031） |
| `ChargeItemQueryService` | 写路径按就诊预热 `listChargeItemsByEncounterId`（模板/激活定位）；**读路径**长期 bindItems 上次量：批量 `listChargeItemsByServiceIds` 覆盖索引（**BR-IP-EXE-013a**，`ORDER_EXECUTE` 优先） |
| `MedicationDispenseQueryService` | `listDispenseExeReserve`（库存 Gate 预占） |
| `InventoryItemQueryService` | `sumAvailableInventory`、`findAvailableCatalogRowsForOrder` |
| `ServiceLocationTemplateQueryService` | 候选药房顺位（`organization_id` + `item_table` + `distribution_category_code` 精确匹配） |
| `ProductDefinitionBatchLoader` | 库存 Gate 按 `medicationId` 取 `category_code`；**IF-IP-EXE-02** `warmWritePathCatalogCaches` 批量预热 **`ProductDefinitionDisplayDto`** |
| `ProcedureDomainService` | create/start/complete、**skipProcedureRecord** |
| `MedicationDispenseDomainService` | **generateDispenseFromExecution**（长临 FIFO 多段） |
| `ChargeItemDomainService` | **createPlannedChargeItemFromOrderExecution** |
| `ChargeItemDefinitionQueryService` | 长期/绑定新建费用解析 **`ChargeItemDefinition`**；**IF-IP-EXE-02** 批量 `findPriceLinesByProjects` / `findAllDefinitionsByIdIn` 后缓存 |
| **`ProductDefinitionOrderChargePricingsupport** | **BR-IP-EXE-026**：`NurseStationOrderExecuteMapperImpl` 药/诊/绑定项新建 Charge 时按 **`quantity`/`unit_code` + 产品读模型** 比例换算金额（**禁止**复制模板整价或默认 1/「次」） |
| `ServiceRequestDomainService` | 绑定诊疗 **createExecutedServiceRequest** |
| `DeviceRequestDomainService` | 绑定耗材 **createExecutedDeviceOrder** |
| `DeviceDispenseNurseWorkflowsupport | 绑定耗材 **finalizeInpatientTemporaryPathDispense**（`application.shared.nursestation-device-dispense`） |
| `ProcedureExecutionPerformerOrchestrationsupport | IF-IP-EXE-02：`complete` 后登记主执行人至 `cln_procedure_performer`（PRIMARY，1:1） |
| `InventoryItemDomainService` | 绑定耗材批次 **`decreaseQuantitiesMerged`**（开关 **开**） |
| `ServiceLocationTemplateQueryService` | 绑定耗材 **performLocation** 解析（对齐护士划价，**BR-IP-EXE-019**） |
| `InpatientDoctorStationAdviceOrderBindQueryService` | bindItems 模板查询（**#listBindingsBySourceItemKey**，**BR-IP-EXE-013**）；**合管已配 → 空**；**未合管挂住院模板含诊疗与耗材**（**AS-BAS-CFG-001 §7.7**；开立恒空见 **BR-IP-ORD-036**） |
| `NurseStationOrderViewListAssembler` | 共享：明细索引、批量预热、药/诊行 `DisplayCore`（**AS-IP-NUR-003-SDD §2.5**） |
| `PractitionerCheckOrgDisplayResolver` | 校对人岗位病区 → 上级科室展示名（**BR-IP-EXE-029**；B.6.4 跨聚合只读编排） |
| `NurseStationOrderExecuteListAssembler` | 委托 `OrderView` 展示内核 + 槽位四类列表 + bindItems + 写 Gate 辅助方法 |
| `NurseStationExecutionInventoryAssembler` | **药嘱** FIFO Gate + 拆零换算 |
| `NurseStationOrderExecuteListLoader` | IF-IP-EXE-01 列表加载（含 `medicineCategoryTab` 过滤；可选 `reqOpenedTimeFrom`/`reqOpenedTimeTo` 行收窄，**BR-IP-EXE-037**） |
| `InpatientNurseStationTcmsupport | 中西药 Tab 过滤、中药方折叠、写路径整方扩行；西药成组写扩行与自动带代表行挂载（**BR-IP-NUR-004-GRP-001～003**） |
| `InpatientTcmDecoctionExecutesupport | **BR-IP-TCM-008** 执行代煎费 |

### 2.2 领域方法白名单

| 类型 | 方法 | IF |
| --- | --- | --- |
| 读 | `listInpatientNurseStationExecuteOrders` | 01 |
| 读 | `computeExecutionTimePoints` | 01 |
| 读 | `getProcedureRecords`（按 request；**写路径 `orderKey` 去重**） | 01、02 Pre |
| 读 | `sumAvailableInventory` | 02 Gate |
| 读 | `listDispenseExeReserve` | 02 Gate |
| 写 | `createProcedureRecord` → `startProcedureExecution` → `completeProcedureRecord` → **`appendProcedurePerformer`**（经 `ProcedureExecutionPerformerOrchestrationsupport） | 02 |
| 写 | `skipProcedureRecord` | 04 |
| 写 | `generateDispenseFromExecution` | 02 药嘱（长临 FIFO 多段） |
| 写 | `createPlannedChargeItemFromOrderExecution` | 02 药/诊/绑定 |
| 写 | `createExecutedServiceRequest` | 02 绑定诊疗 |
| 写 | `createExecutedDeviceOrder` | 02 绑定耗材 |
| 写 | `generateDeviceDispenseFromTemporaryPath` | 02 绑定耗材（草稿） |
| 写 | `finalizeInpatientTemporaryPathDispense`（WorkflowSupport） | 02 绑定耗材 |
| 写 | `decreaseQuantitiesMerged`（WorkflowSupport 累计，开关 **开**） | 02 绑定耗材 |

### 2.3 枚举与常量

| 类别 | 锚点 | 说明 |
| --- | --- | --- |
| 预占状态 | `NurseStationExecutionConstants.DISPENSE_RESERVE_STATUSES_FOR_EXECUTION_GATE` | 宽于 IF-DOR-11 目录预减（含已配药/汇总等）；**禁止** App 自建状态列表 |
| 业务消息码 | `InpatientNurseStationConstants.EX_IP_EXE_*` | 对齐 AS §5 **EX-IP-EXE-*** |
| Procedure 多计划点 | `skipDuplicateRequestAnchorCheck=true` | **BR-IP-EXE-015** |

### 2.4 领域层能力核对（非 Gap）

> Gap 均已关闭（**AS §3**）。本 SDD 驱动**应用层**成码。

**已落地域写读**：见 **AS-IP-NUR-004 §9** 对照表。

**本 SDD 成码交付**：`NurseStationOrderExecuteAppService` 三接口；`orderview` 共享装配器（展示字段，见 **AS-IP-NUR-003-SDD §2.5**）；ListAssembler（展示委托 + 槽位 + bindItems）；**InventoryAssembler（仅药嘱 Gate）**；Request/Response/DTO/Mapper（含 `MapperImpl`）。

### 2.5 列表行展示与业务装配分工

| 层次 | 类 | 职责 |
| --- | --- | --- |
| 展示内核 | `NurseStationOrderViewListAssembler` | `indexEncounterDetails` → `prepareDisplayContext` → `buildMedicationDisplayCore` / `buildServiceDisplayCore`（字段定义见 **AS-IP-NUR-003-SDD §2.5**） |
| 执行列表 | `NurseStationOrderExecuteListAssembler` | 拷贝 `DisplayCore` → `NurseStationOrderExecuteRowDto`（含 `groupId`）；再叠加频次展开、Procedure 归并、四类槽位、`bindItems`、Tab+deadline 收窄 |
| 写 Gate | `NurseStationOrderExecuteListAssembler` | `isPlanTimeExecutable` / `isPlanTimePending` / `isPlanTimeAfterStopTime`（**仅写路径**；与展示内核无关） |

> **禁止**在 `orderexecute` 包内重复实现 `adviceDisplayName` / `dosageDisplay` / `frequencyDisplay` 等展示字段；新增字段须先扩展 `NurseStationOrderViewDisplayCore` 与各消费方 `applyViewCoreTo*Row`（如 **`performerCheckOrgDisplay`** → 执行列表 `applyViewCoreToExecuteRow`，**BR-IP-EXE-029**）。
>
> **嘱托展示（BR-IP-ORD-028④）**：诊嘱 `adviceDisplayName` 由 **AS-IP-NUR-003-SDD §2.5** `buildServiceDisplayCore` + **`InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`** 统一解析；前端内容列直接读 `adviceDisplayName`，**禁止**只显示目录名「嘱托」。
>
> **嘱托执行（BR-IP-EXE-042 / BR-IP-ORD-028⑦）**：`NurseStationOrderExecuteListReadAssembler#buildSlotBuckets` 命中嘱托时走 **临嘱整单归并**（`buildTemporarySlotBuckets`）；**禁止**按 `rate_code` 长期展开；写路径 `alignTemporaryExecutePlanAndActualTimes` 对嘱托亦强制 `planTime=actualTime`；待执计数同源。

## 3. 按接口 ID 展开

### 3.1 应用服务接口

| IF | 方法 | HTTP |
| --- | --- | --- |
| IF-IP-EXE-01 | `listNurseStationOrderExecute` | 医嘱执行列表查询 | `POST /api/app/nurseStationOrderExecute/listNurseStationOrderExecute` |
| IF-IP-EXE-02 | `executeNurseStationOrders` | 批量执行医嘱槽位 | `POST /api/app/nurseStationOrderExecute/executeNurseStationOrders` |
| IF-IP-EXE-04 | `skipNurseStationOrderSlots` | 批量不执行医嘱槽位 | `POST /api/app/nurseStationOrderExecute/skipNurseStationOrderSlots` |

### 3.2 IF-IP-EXE-01 列表

**Request**：`encounterIds`（Y）、`executeTab`（Y）、`therapyType`、`executionDeadline`（Y）、`medicineCategoryTab`（N，默认 `west`）、`anesthesiaApplyOnly`（N，默认排除麻醉申请，**BR-IP-NUR-004-ANES-001**）、`reqOpenedTimeFrom`/`reqOpenedTimeTo`（N，开嘱日闭区间；打印中心用，医嘱执行页不传）

**Response**：按 encounter 分组；每医嘱行含四类同构槽位 + `bindItems`（可 `[]`）；药嘱行回传 **`groupId`**（`MedicationRequestListQueryModel.groupId`，与医生站列表同源）（**不含** `accountId`，由 IF-IP-NUR-02 左侧患者列表承担）

**编程序列**

1. **Gate**：encounterIds 非空；**executeTab 必填**；Tab/长临/`medicineCategoryTab` 白名单；开嘱日区间同时传时须 from ≤ to（**BR-IP-EXE-037**）  
2. **Pre**：可见性（同 AUD-01）→ **Q-IP-EXE-001**（`VERIFIED` + `STOPPED`）→ 批量药/诊明细 → **InpatientNurseStationTcmSupport** 按 Tab 过滤骨架/明细 → **`InpatientNurseStationAnesthesiaApplyScopeSupport` 按 `anesthesiaApplyOnly` 收窄**（**BR-IP-NUR-004-ANES-001**）→ 剔除无 rateCode 长期  
3. **Asm**：`indexEncounterDetails` + `prepareDisplayContext`（**OrderView**）→ … → bindItems（模板量）→ **`overlayLongTermBindQuantitiesFromLastExecution`（BR-IP-EXE-013a）**：长期医嘱批量反查派生 SR/DR，再以 **`ChargeItemQueryService#listChargeItemsByServiceIds`** 按 `ORDER_EXECUTE` **`quantityValue` 覆盖**（无账单回落派生量；**允许 0**，负量跳过）→ Tab+deadline 收窄槽位 → **`enrichTerminalSlotOperatorNames`（BR-IP-EXE-031）** → `collapseTcmPrescriptionDisplayRows`（**BR-IP-NUR-004-TCM-002**：代表行开立时间取方内最晚）→ **`filterGroupsByReqOpenedTimeRange`（BR-IP-EXE-037）** → 组内排序  

**禁止**：App 内 `plusDays(1)` 硬编码隔日；直查 InpatientOrderListSkeleton Repository。

### 3.3 IF-IP-EXE-02 执行

**Request**：`items[]`——每槽位含 `orderId`、`orderType`、`planTime`、`actualTime`、**`accountId`（必填，仅取自 IF-IP-NUR-02 患者列表，前端缓存后回传）**、可选 `bindItems[]`；**不得**带已有 `procedureId`；`executionDeadline` **预留，Gate 不使用**

**编程序列**

1. **Gate·入参**：≤50 条（**扩行前**）；锚点完整；执行路径 `actualTime` 可空（空则 `applyExecutionActualTimeDefaults`：首次槽=开立+2 分钟、已停嘱停嘱日末=停嘱、其余=planTime）；**`accountId` 非空**；**actualTime 与 planTime 须同一自然日**（**BR-IP-EXE-030**）  
1b. **Gate·就诊**（`InpatientPendingSettlementClinicalGateSupport#assertEncountersAllowOrderClinicalMutation`；**BR-IP-EXE-028**）：本批涉及就诊 `status_enum` 须为 `IN_PROGRESS` 或 `ON_HOLD`；`PENDING`/`COMPLETED`/`DISCHARGED` → **EX-IP-EXE-307**  
1a. **Pre·药嘱写扩行**：`expandExecuteWriteSlotsByTcmPrescription`（**BR-IP-NUR-004-TCM-003** / **BR-IP-NUR-004-GRP-002～003**；优先 `lineRequestIds`，否则中药整方 / 西药 `group_id`；**bindItems 仅挂扩行首行**）  
2. **Pre·写上下文**（`prepareWriteContext` + actualTime 默认 + 临嘱对齐 + `warmWritePathCatalogCaches`；**仅优化读 I/O 与时间对齐，不改变 Gate 语义**）：  
   - **`prepareWriteContext`**：`orderId:orderType`（`orderKey`）**去重**加载 `InpatientOrderLookupLoader` 与 `ProcedureQueryService#getProcedureRecords`（同医嘱多 `planTime` **只查一次** Procedure 历史）；按本批 `encounterId` **预热** `ChargeItemQueryService#listChargeItemsByEncounterId`；药/诊明细按就诊批量索引入 `WriteContextBundle`  
   - **`applyExecutionActualTimeDefaults`**：补齐 actualTime（首次槽=开立+2 分钟 / 已停嘱停嘱日末=停嘱 / 其余=planTime；同槽开嘱优先）；同日 Gate（**BR-IP-EXE-030**）  
   - **`alignTemporaryExecutePlanAndActualTimes`**（**BR-IP-EXE-006**）：临嘱强制 `planTime=actualTime`（occurrence / recorded / 发药 / 账单同值）  
   - **`liftExpandedExecuteActualTimesNotBeforeOpened`**（**BR-IP-NUR-004-GRP-002** / 中药整方同口径）：扩行后若某成员 `actualTime` 早于自身开立则抬到该开立（须与 `planTime` 同自然日；临嘱再同步 plan=actual）；再进入槽位 Gate **BR-IP-EXE-024**  
   - **`warmWritePathCatalogCaches`**（**仅 IF-IP-EXE-02**）：扫描本批 `items` 及 `bindItems` → 批量 `ProductDefinitionBatchLoader#loadAsMap`、按 `instanceTable` 批量 `findPriceLinesByProjects`、批量 `findAllDefinitionsByIdIn`（模板/绑定已带 `chargeItemDefinitionId`）；临时药嘱批量 `listMedicationDispensesByMedReqIds` → 写入 `WriteContextBundle` 缓存  
   - 写循环内 `loadProductDefinition` / `requirePriceLineForExecution` **优先读缓存**；未命中**回落**原单条查询；药嘱长期仍**按槽位** `executionLotNumber` 调 `selectExecutionPriceLineForChargeDraft`（批号售价口径不变）  
3. **Gate·槽位**（`NurseStationOrderExecuteListAssembler` + AppService；**仍逐 `items` 校验**）：可见性；请求仍 **VERIFIED**；**`accountId` 非空**（空 → 与医生站一致的「就诊未解析到自费账户」文案）；**occurrence 防重**——该 `planTime` 无 COMPLETED/STOPPED/CANCEL 终态（**EX-IP-EXE-301**）；**不晚于停嘱**——`planTime ≤ stop_time`（药/诊统一）（**EX-IP-EXE-303**）；**不早于开立**——`actualTime` 秒级 ≥ 开立（`assertActualTimeNotBeforeOpened`；**EX-IP-EXE-303** / `MSG_ACTUAL_TIME_BEFORE_OPENED`）；**禁止**用 `executionDeadline`、当前时刻做 Gate；**禁止**写路径调用 `AccountQueryService` 解析账户  
4. **Pre·库存**（**仅药嘱**，`NurseStationExecutionInventoryAssembler#runGateDryRun`，对齐 **AS BR-IP-EXE-025**）：  
   - **Demand 行**（`buildInventoryGateDemands`）：`orgId` ← `MedicationRequestListQueryModel.orgId`（`med_medication_request.org_id`，开立科室）；`categoryCode` **不在 Request 传入**，由装配器解析  
   - **模板键**（逐药品，可合并候选库位）：`organization_id` = demand 行 `orgId`；`item_table` = `med_medication_definition`；`distribution_category_code` = `ProductDefinitionDisplayDto.category_code`，空则 `MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE`（`DEFAULT`）  
   - `ServiceLocationTemplateQueryService#listCandidateDistributionLocationIds` → 候选药房 A→B 顺位；**无命中** → **EX-IP-EXE-302**「未配置候选药房」  
   - 批量 `findAvailableCatalogRowsForOrder(med_product, …)`  
   - 批量 `listDispenseExeReserve(locationIds, medIds)`（**全局**，不限就诊）  
   - **InventoryAssembler** FIFO：扣全局预占 → 按效期**分段**（跨批多行 Dispense）；长临均用执行量（拆零换算）；**一盒一点** Gate demand=1 包装单位（**BR-IP-EXE-035④**）；**拆零下限** Gate demand=1 拆零单位（**BR-IP-EXE-036**）  
   - 任一不足 → **EX-IP-EXE-302**，整批失败  
   - **绑定耗材不参与**本步骤（库存校验/扣减由耗材发放等其他页面统一处理）
5. **Pre·费用**（长期/绑定新建 Charge，**仅列表阶段已查定价，写路径不查账户**）：  
   - **药/诊/绑定项（BR-IP-EXE-026）**：始终解析 `ChargeItemDefinition`；`ProductDefinitionOrderChargePricingSupport#applyOrderChargePricing` 写入 `quantity_unit`/`unit_price`/`total_price`（与 **`med_medication_request`/`wkf_service_request`/绑定项 `quantity`/`unit_code`** 一致；**禁止**复制签发模板整价或默认 1/「次」）  
   - 模板行仅复制 **`definitionId`/科目/机构** 等非金额字段；金额一律比例重算  
   - **BR-IP-EXE-041**：`findChargeTemplate` **跳过** `parent_id` 非空子项账单；药/诊 `productId`/`productTable` **禁止**被模板覆盖（以医嘱锚点为准）  
   - `accountId` **只取自 Request**，不取自模板、不在执行阶段查库  
6. **Cmd**（同事务，逐槽位）：  
   - Procedure：`create`（`skipDuplicateRequestAnchorCheck=true`）→ `start` → `complete`（occurrence=planTime，**recorded=actualTime 显式写入**；临嘱两者已对齐同值）→ **`ProcedureExecutionPerformerOrchestrationSupport#registerPrimaryPerformer`**（`performerId`=当前医务人员，`functionCode=PRIMARY`，1:1）
   - 药嘱：Dispense `plannedDispenseTime`=**planTime**；Charge occurrence=**planTime**（临嘱与 recorded 同值；长期为频次锚点）；已执行展示读 Procedure `recorded_time`  
   - **药嘱（长临）**：每 FIFO 段 `generateDispenseFromExecution` + `createPlannedChargeItemFromOrderExecution`（**待结算**）；**一盒一点**时数量 `1`、单位 `packageUnitCode`，通常仅一段一行 Dispense/Charge  
   - **诊嘱/绑定新建**：`createPlannedChargeItemFromOrderExecution` → **`PENDING_SETTLEMENT`（待结算）**；**嘱托例外（BR-IP-EXE-033）**：`category_enum=52` / 名「嘱托」时**跳过**建账，仅 Procedure  
   - **bindItems** 非空（**BR-IP-EXE-019** / **BR-IP-EXE-013a**）：
     - 解析 `bindQuantity`；**`≤ 0` 则 skip**（本趟不带出，不建派生 SR/DR/Charge）
     - `activityId`：`createExecutedServiceRequest`（**based_on** 主医嘱 Request）→ 独立 Procedure → `createPlannedChargeItemFromOrderExecution`（**bindItem.quantity/unitCode** + 活动产品读模型）
     - `deviceId`：`resolveBindItemUnitCode`（优先 `bindItem.unitCode` → 耗材目录 `defInhospitalUnitCode`/`splitUnitCode`/`packageUnitCode`/`stockUnitCode`；缺失 → **EX-IP-EXE-304**）→ `createExecutedDeviceOrder`（**based_on** + **performLocation** + **`unitCode`**）→ `generateDeviceDispenseFromTemporaryPath` → **`DeviceDispenseNurseWorkflowSupport#finalizeInpatientTemporaryPathDispense`** → Charge；批次末条件 **`decreaseQuantitiesMerged`**
   - **中药代煎费（BR-IP-TCM-008）**：药嘱执行成功后，若 `tcm_flag=true` 且 `suffering_flag=true`，按 `chinese_herbs_dose_quantity` 调用 `InpatientTcmDecoctionExecuteSupport#replaceOrderExecuteDecoctionChargeIfNeeded`（**先 void 同方同 planTime 未结算代煎费再新建**）；同方同 `planTime` 去重仅挂一次
7. 失败整批回滚（**BR-IP-EXE-022**）
8. **院点后置（BR-IP-EXE-039，不入 health）**：整批成功后，院点 AOP（如朝阳 `H22010402403NurseStationOrderExecuteAspect`）收集本批 `SERVICE_REQUEST` → MedSync `@Async` 外发 LIS/PACS（与门诊收费确认后同管线）；**不阻断**响应；失败只记补传状态

### 3.4 IF-IP-EXE-04 不执行

**Request**：`items[]`——`orderId`、`orderType`、`planTime`；**禁止** `procedureId`；`executionDeadline` **预留，Gate 不使用**

**编程序列**

1. **Gate**：可见性；occurrence 防重（**EX-IP-EXE-401**）；不晚于停嘱（**EX-IP-EXE-403**）；**不做**库存 Gate；**不要求** VERIFIED  
1b. **Gate·就诊**（`InpatientPendingSettlementClinicalGateSupport`；**BR-IP-EXE-028**）：`PENDING`/`COMPLETED`/`DISCHARGED` → **EX-IP-EXE-307**  
1a. **Pre·药嘱写扩行**：`expandExecuteWriteSlotsByTcmPrescription`（**BR-IP-NUR-004-TCM-003** / **BR-IP-NUR-004-GRP-002～003**；优先 `lineRequestIds`，否则中药整方 / 西药 `group_id`；**bindItems 仅挂扩行首行**）  
2. **Cmd**：逐 planTime 调用 `skipProcedureRecord`（`skipDuplicateRequestAnchorCheck=true`，occurrence=planTime，recorded=now）  
3. 同事务提交；不触 Dispense/Charge/Request 停嘱  

## 4. DTO 要点（结构化，禁止 JSON 串）

| DTO | 关键字段 |
| --- | --- |
| `NurseStationOrderExecutePatientGroupDto` | `encounterId` + 组内 `orders[]`（**不含** `accountId`） |
| `NurseStationOrderExecuteRowDto` | 见下表（与 `NurseStationOrderExecuteRowDto.java` 一致） |

#### `NurseStationOrderExecuteRowDto` 字段表（IF-IP-EXE-01）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| listRowKey | String | `orderId` + SOH + `orderType` |
| orderId | Long | 药/诊请求主键 |
| orderType | String | `MED_REQUEST` / `SERVICE_REQUEST` |
| encounterId | Long | 就诊 |
| patientId | Long | 患者 |
| statusEnum / statusEnumText | String | 请求状态 code + 展示 |
| therapyEnum / therapyEnumText | String | 长临 code + 展示 |
| categoryEnum | String | 诊疗类目（嘱托=52 等；药品行 null） |
| orderKindDisplay | String | 类型列：药品/诊疗/出院/转科/护理/**嘱托**（OrderView 同源；**BR-IP-EXE-012**） |
| adviceDisplayName | String | 医嘱摘要；**嘱托行**优先 `entrust_content` 正文（**BR-IP-ORD-028④**；§2.5 `OrderView` 同源） |
| specificationText | String | 规格 |
| dosageDisplay | String | 剂量/数量展示（OrderView 同源；**输液且拆零&lt;1 主显计量单位**，见 **AS-IP-NUR-003-SDD §2.5**） |
| dailyDoseDisplay | String | 日剂量展示（如「每日三片」；长期药；输液拆零&lt;1 跟计量侧） |
| firstDose | BigDecimal | **长期药品**首日/首次用量（OrderView 同源；**BR-IP-EXE-012** / **BR-IP-ORD-030**） |
| firstDoseUnitCode | String | 首日用量单位编码 |
| firstDoseUnitName | String | 首日用量单位展示名（前端剂量列拼「首日…」） |
| doseSchedule | String | **长期药品**分次剂量序列（计量；可空；**BR-IP-EXE-012** / **BR-IP-EXE-040**） |
| quantitySchedule | String | **长期药品**分次用量序列（拆零；可空；与 `doseSchedule` 同序） |
| usageDisplay | String | 用法展示 |
| frequencyDisplay | String | 频次展示 |
| speedCode | String | 输液滴速编码（仅药嘱；`speed_code`；可空） |
| speedDisplay | String | 输液滴速展示名（术语 `speed_code`） |
| reqOpenedTime | LocalDateTime | 开立时间（`req_opened_time`） |
| `reqAuthoredTime` | `LocalDateTime` | 签发时间（`req_authored_time`；**仅系统追溯**；列表/报表**默认不展示**；未签发可 null） |
| stopTime | LocalDateTime | 停嘱时间 |
| requesterName | String | 开立医生；UI 跟在开立时间后（**BR-IP-ORD-031**） |
| stopperId | Long | 停嘱人主键（`stopper_id`；未停 null） |
| stopperName | String | 停嘱人姓名；UI 跟在停嘱时间后（**BR-IP-ORD-031**） |
| performerCheckOrgDisplay | String | 校对科室展示名（执行科室；**BR-IP-EXE-029**） |
| groupId | Long | 药品成组号（仅药嘱） |
| basedOnMedicationRequestId | Long | companion 皮试检查锚定皮试药 id |
| skinTestFlag | Boolean | 是否皮试药（仅药嘱） |
| skinTestOutcomeCode | String | 皮试临床状态 code（已登记时有值） |
| skinTestOutcomeText | String | 皮试临床状态展示（阴/阳性；前端跟在药名后） |
| pendingSlots / executedSlots / skippedSlots / cancelledSlots | List\<NurseStationOrderExecuteSlotDto\> | 四类槽位；**仅当前 `executeTab`** 对应类含 `planTime ≤ executionDeadline` 的点（含一盒一点，**禁止**盒尾 bypass）；**一盒一点 / 拆零下限** pending **同时最多一个锚点**；**hedged pending** 时同一 planTime **同时**出现在 `pendingSlots`（无 `procedureId`）与 `cancelledSlots`（末态 CANCEL 行） |
| bindItems | List\<NurseStationOrderExecuteBindItemDto\> | 绑定耗材/诊疗项 |
| tcmFlag | Boolean | 是否中药 |
| prescriptionNo | String | 处方号 |
| lineRequestIds | List\<Long\> | 方内全部 req id（tcm Tab 折叠后） |
| sufferingFlag | Boolean | 代煎标志 |
| chineseHerbsDoseQuantity | BigDecimal | 付数 |
| oralPackageBatchInventoryApplied | Boolean | 是否命中 **BR-IP-EXE-038** |
| oralPackageConsumedSplitQuantity | BigDecimal | 已吃拆零量；未命中可空 |
| oralPackageRemainSplitQuantity | BigDecimal | 剩余拆零量；未命中可空 |
| oralPackageSplitUnitDisplay | String | 拆零单位展示名 |
| oralPackageNextClaimDate | LocalDate | 预计下次领盒日；无可空 |
| `NurseStationOrderExecuteSlotDto` | `planTime`、`procedureId`（可空）、`statusEnum`、`recordedTime`、**`defaultActualTime`（待执行：首次槽=开立+2 分钟；已停嘱停嘱日末=停嘱，BR-IP-EXE-032）**、**`operatorName`（终态槽；仅 `cln_procedure_performer` PRIMARY，禁止 `recorder_id`；CANCEL 无 PRIMARY 可回落 refund COMPLETED 的 PRIMARY；pending 为空，BR-IP-EXE-031）**；**长期终态** `planTime` = `occurrence_time`；**临嘱已执行** occurrence=recorded 同值，缺 recorded 时读侧回落 planTime；**UI 展示**：待执行优先 `defaultActualTime`，已执行优先 `recordedTime`；勾选键/deadline/写 Gate 仍锚 `planTime`；**hedged pending** 的 pending 槽无 `procedureId`，cancelled 槽含 CANCEL 行 `procedureId`；**BR-IP-EXE-038** 抑制后的 pending 不回传 |
| `NurseStationOrderExecuteBindItemDto` | 见下表；写路径原样回传（数量可改） |
| `NurseStationOrderExecuteWriteSlotDto` | 写路径槽位：`planTime`（逻辑锚点；**临嘱写前强制 = actualTime**）；`actualTime`（→ recorded，**可空**空则默认首次槽=开立+2 分钟/已停嘱停嘱日末=停嘱/其余=planTime）；**`accountId`（执行路径必填）** + 嵌套 bindItems；中药可选 **`lineRequestIds`**、**`prescriptionNo`** |

#### `NurseStationOrderExecuteBindItemDto` 字段表（IF-IP-EXE-01/02）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| bindType | String | `USAGE` / `ACTIVITY`（与模板 `type_code` 同码；目标类型见 `activityId`/`deviceId`） |
| bindingId | Long | 模板主键 `cus_service_device_template.id` |
| activityId | Long | 绑定诊疗（与 deviceId 互斥） |
| deviceId | Long | 绑定耗材（与 activityId 互斥） |
| displayName | String | 目标展示名 |
| busNo | String | 目标业务编码 |
| specificationText | String | 目标规格 |
| unitPrice | BigDecimal | 零售单价 |
| chargeItemDefinitionId | Long | 定价行主键 |
| quantityNumerator / quantityDenominator | BigDecimal | 数量（写路径可改） |
| unitCode / unitCode_dictText | String | 单位 code + 展示 |
| priority | Integer | 展示顺序 |

## 5. 异常与 Gate

| EX ID | 抛出阶段 | 语义（对齐 AS v2.6） |
| --- | --- | --- |
| EX-IP-EXE-101/102/201 | 01 Pre/Asm | 列表加载/可见性/频次 |
| EX-IP-EXE-301 | 02 Gate | 该 **occurrence** 已有终态占用 |
| EX-IP-EXE-302 | 02 Pre·库存 | 模板未命中（开立机构/发放类别与 `cus_service_location_template` 不一致）或药嘱库存不足 |
| EX-IP-EXE-303 | 02 Gate | 不可见 / 非 VERIFIED / **planTime 晚于停嘱** / **actualTime 早于开立** |
| EX-IP-EXE-304 | 02 Cmd / Pre | 域写失败；绑定耗材 **unitCode** 无法解析（「绑定耗材执行失败：缺少单位编码」） |
| EX-IP-EXE-401 | 04 Gate | occurrence 已有终态 |
| EX-IP-EXE-402 | 04 Gate | 携带 procedureId |
| EX-IP-EXE-403 | 04 Gate/Cmd | **晚于停嘱**或域 STOPPED 写失败 |
| EX-IP-EXE-307 | 02/04 Gate | 就诊 `PENDING`/`COMPLETED`/`DISCHARGED`（**BR-IP-EXE-028**） |

**写 Gate 实现锚点**（`NurseStationOrderExecuteListReadAssembler`）：

| 方法 | 用途 |
| --- | --- |
| `isPlanTimeExecutable` | **IF-IP-EXE-02** Gate：occurrence **净态**为 pending、**hedged pending** 或孤立 cancelled 可写；**长期**按 occurrence 分组、组内 id 最大 + `CANCEL.refund_id`；**临时**按净 COMPLETED / 末态；多次往返禁止误读历史 COMPLETED |
| `isPlanTimePending` | **IF-IP-EXE-04** Gate：occurrence 净态须为 **pending 或 hedged pending**（已净执行/不执行/孤立 cancelled 不可再登记不执行） |
| `isPlanTimeAfterStopTime` | 停嘱边界：药/诊统一 `stop_time` |

**禁止**：写路径调用 `buildSlotBuckets` / `computeExecutionTimePoints` 做 Gate；用 `executionDeadline` 拦截写操作。

**库存 Gate 模板解析锚点**（`NurseStationExecutionInventoryAssembler`）：

| 步骤 | 方法/逻辑 | 说明 |
| --- | --- | --- |
| 发放类别 | `resolveCandidateLocationIds` | `loadAsMap` → `category_code`；`firstNonBlank(line.categoryCode, product.categoryCode, DEFAULT)` |
| 候选库位 | `listCandidateDistributionLocationIds` | 与 `DM-CUS-006` / 医生站 FIFO **精确匹配** `distribution_category_code` |
| 失败 | `runGateDryRun` | 候选库位列表空 →「未配置候选药房」；FIFO 后仍不足 →「药品库存不足」 |

常量落点：`InpatientNurseStationConstants` 或 `NurseStationExecutionConstants`；发放类别回落 `MedicationCommonConstants.DEFAULT_SERVICE_LOCATION_DISTRIBUTION_CATEGORY_CODE`（**禁止** `application/*/constants` 新包）。

## 6. 事件与投影（消费意图）

| 事件 | IF | 查询刷新说明 |
| --- | --- | --- |
| `ProcedureRecordCompletedEvent` | 02 | 槽位 executed |
| `ProcedureRecordSkippedEvent` | 04 | 槽位 skipped |
| `MedicationDispenseCreatedEvent` | 02 长期 | 发药待办 |
| `DispenseProcedureLinkedEvent` | 02 临时 | 发药挂 Procedure |
| `ChargeItemActivatedByExecutionEvent` | 02 | 费用可结算行 |

## 7. 测试锚点（业务）

| 场景 | 要点 |
| --- | --- |
| 药嘱成组 | 同 `groupId` 多行列表回传一致组号并聚邻；前端合并括线且**代表行开立取组内最晚**（待执行 `defaultActualTime` 不得早于该最晚）；勾选执行/不执行整组同 planTime 扩行；写前抬齐早于成员开立的 actualTime；组员各自发药/账单；自动带整组共用一份；未成组为 null；诊嘱行无 `groupId` |
| QOD 隔日 | pending 槽位隔一天一个；与 TID 同窗对比 |
| 开嘱日首次槽（已过） | 21 日 12:00 开立、频次含 08/12/18（配置为准）→ 21 日 pending 含 12:00（首次，`defaultActualTime`=12:02）+18:00，不含 08:00 |
| 开嘱日尚无已过点 | 21 日 07:30 开立、首点 08:00 → 21 日全日点保留；首次 `defaultActualTime`=07:32 |
| 开嘱已过末点 | 21 日 23:59 开立、末点 18:00 → 21 日仅 18:00；`defaultActualTime`=23:59:59（+2 跨日钳开立日末） |
| 首日量仅首次槽 | `first_dose` 有值时仅首次槽换量（已是最小单位，与常规长嘱同路径）；同日后续与次日用 `quantity` |
| 长嘱不可拆一盒一点 | `inpatientStandingOrderSplittable=0` + 长期口服 + 包装≠拆零：切盒后 pending 仅盒首锚点（非盒内全部 TID 点）；同时最多一个 pending；执行写 `quantity=1`+包装单位、Dispense/Charge 各一条；上一盒终态后出下一盒锚点；Tab/deadline 常规过滤；非口服/累加无法整除→可拆；停嘱硬顶未凑满/探测耗尽→仅已完整盒首或空锚点、不降级日点；出院门禁 `listLongTermStoppedPendingPlanTimes` 同源 |
| 口服剩余 / 改量续算 | **BR-IP-EXE-038**：盒 10 QD 执行后理论 3 天→已吃 3 剩 7；改量 BID 后剩 7 时前 3 新量日无 pending、第 4 日首点 pending、执行后剩 9；观察期不扣；取消盒执行整盒重计；无 basedOn 不续算；**领用日补点须认不执行终态**（STOPPED 后不得再补假 pending） |
| 不执行后出院 | 剩余 0 + 预计领用日=停嘱日 + 该日首点已不执行 → 列表/待执/ATD-03 **无**该点 pending；办理出院 **不拦** |
| 拆零单位下限 | 长嘱一次 0.03 支 / 半片：列表仅单位首槽、pending 至多一个；执行后 Charge/Dispense/库存均为 1 拆零单位量+价；与 035 互斥时 035 优先（**BR-IP-EXE-036**） |
| 库存 Gate | **药嘱**按 `org_id` + 产品 `category_code` 命中模板；种子 `distribution_category_code=2` 须配 `category_code=2`；全局预占扣减；A 药房不足尝试 B；不足整批失败；绑定耗材不参与 |
| 长临分轨 | 长期新建 Dispense+Charge；临时挂接+激活 |
| 写 Gate | 22 日 8:00/16:00 执行后不可重复；21 日补录、23 日提前可写；晚于停嘱拒绝；**actualTime 早于开立拒绝**；**actualTime 与 planTime 须同日**；不改时分时首次槽默认开立+2 分钟、其余默认 planTime |
| 不执行 | **按勾选 planTime** 写 STOPPED occurrence；同 Request 其它 planTime 仍 pending；Request 不停；无 Dispense/Charge |
| 绑定 | 药嘱 bindItems 按 **method_code** 命中模板；诊嘱按 **activityId**；**合管空、未合管含诊疗与耗材**（**BR-IP-EXE-013** / **§7.7**）；勾选后派生诊疗/耗材；绑定耗材 **`finalizeInpatientTemporaryPathDispense`**（**BR-IP-PRC-019** 两轨）+ Charge；无库存 Gate；**BR-IP-EXE-013a**：长期列表上次量 `ChargeItemQueryService#listChargeItemsByServiceIds` 覆盖（ORDER_EXECUTE 优先，允许 0）；写侧 `bindQuantity ≤ 0` skip |
| 多计划点 | 同 request 多条 Procedure（skipDuplicate=true） |
| 写路径批量性能 | 同医嘱一次执行多 `planTime`：Gate/Cmd 结果与优化前一致；`prepareWriteContext` / `warmWritePathCatalogCaches` 仅减少重复读库 |
| 执行-取消对冲 | 某 planTime 执行→取消后 **hedged pending**：**pending Tab** 与 **cancelled Tab** 均回传该 planTime；两 Tab 勾选均可 IF-IP-EXE-02 再执行；**executed Tab** 不出现 |

## 8. 文档变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v2.55 | 2026-10-16 | **代码先行回写**：**BR-IP-EXE-036/038**：`shouldSkipMinSplitReuseForOpenDiscardInfusion`——输液默认开瓶即弃，用法「皮下注射」放行（胰岛素）；035 仍排除输液；对齐 **AS v2.71** / **DD** |
| v2.54 | 2026-10-15 | **代码先行回写**：**BR-IP-EXE-035/036/038**：`StandingOrderPackageBatchEligibilitySupport#isInfusionCatalog`；输液目录排除一盒一点与拆零下限列表切批/剩余续算；对齐 **AS v2.70** / **DD** |
| v2.53 | 2026-10-15 | **代码先行回写**：**BR-IP-EXE-042**：嘱托整单一次（与临嘱同构）；列表/待执/写双轨；对齐 **AS v2.69** / **AS-IP-DOR-003 v1.58** |
| v2.52 | 2026-10-15 | **代码先行回写**：**BR-IP-NUR-004-GRP-001/002**——成组合并开立取组内最晚；写前 `liftExpandedExecuteActualTimesNotBeforeOpened`；§1.2/§3.3/验收同步；对齐 **AS v2.68** |
| v2.51 | 2026-10-13 | **BR-IP-NUR-004-ANES-001 纠偏**：麻醉识别改为 AA 前缀；麻醉申请不挂 bindItems；对齐 **AS v2.67** |
| v2.50 | 2026-10-13 | **BR-IP-NUR-004-ANES-001**：`anesthesiaApplyOnly` + `InpatientNurseStationAnesthesiaApplyScopeSupport`；对齐 **AS v2.66** |
| v2.49 | 2026-10-12 | **剂量展示**：执行列表 `dosageDisplay`/`dailyDoseDisplay` 随 OrderView——输液拆零&lt;1 主显计量单位；对齐 **AS-IP-NUR-003-SDD v1.3.23** |
| v2.48 | 2026-10-07 | **BR-IP-EXE-041**：`findChargeTemplate` 跳过子项账单；Mapper 药/诊 product 禁模板覆盖；对齐 **AS v2.63**；代码先行回写 |
| v2.47 | 2026-09-29 | **BR-IP-EXE-031**：`enrichTerminalSlotOperatorNames` **仅** `mapPrimaryPractitionerIdByProcedureIds`；废止 `recorder_id` 回落；CANCEL 无 PRIMARY 补查 refund COMPLETED 的 PRIMARY；对齐 **AS v2.63** |
| v2.46 | 2026-09-23 | **BR-IP-EXE-040**：`InpatientMedicationRequestExecutionMeasureHelper` 分槽读 `quantity_schedule`；行 DTO `doseSchedule`/`quantitySchedule`；合并 **BR-IP-EXE-038** `allowsNextClaimPendingReinjection`；对齐 **AS v2.61** / **AS-IP-DOR-003 v1.50** |
| v2.45 | 2026-09-23 | **BR-IP-EXE-038**：`allowsNextClaimPendingReinjection`——预计领用日补点认不执行/净已执行为终态；对齐 **AS v2.60** / **DD** |
| v2.44.1 | 2026-09-22 | **BR-IP-EXE-013 纠偏**：未合管执行挂载住院模板含耗材与诊疗；合管仍空；废止「执行侧过滤 deviceId」；开立 **BR-IP-ORD-036**；对齐 **AS v2.59.1** / **AS-BAS-CFG-001 §7.7** |
| v2.44 | 2026-09-16 | **BR-IP-EXE-012**：`DisplayCore`/`NurseStationOrderExecuteRowDto` 增补 `firstDose*`；`applyViewCoreToExecuteRow` 拷贝；对齐 **AS v2.58** |
| v2.43 | 2026-09-14 | **BR-IP-EXE-038 MIN_SPLIT**：拆零下限剩余/抑制/出院同源；`UnitClaimMode`；对齐 **AS v2.57** / **DD-拆零单位下限 §3** |
| v2.42 | 2026-09-11 | **BR-IP-EXE-035⑤⑦**：`StandingOrderPackageBatchPlanTimeResolver` 硬顶/探测耗尽空收口；出院门禁/ATD-03 改 `listLongTermStoppedPendingPlanTimes`；对齐 **AS v2.56** / **AS-IP-NUR-002 v2.21** |
| v2.41 | 2026-09-02 | **住院不自动带耗材**：`loadBindItems` 合管短路 + 诊嘱过滤 `deviceId`；开立侧见 **AS-BAS-CFG-001 §7.7**；对齐 **AS v2.54** |
| v2.40 | 2026-09-02 | **BR-IP-EXE-002**：`NurseStationOrderExecuteRowDto` 增补 **`skinTestFlag`/`skinTestOutcomeCode`/`skinTestOutcomeText`**；`SkinTestOutcomeByMedicationRequestLoader` 批量预热；对齐 **AS v2.51** |
| v2.39 | 2026-08-31 | **BR-IP-EXE-039**：§3.3 步骤 8 院点 MedSync 异步外发（不入 health）；对齐 **AS v2.53** |
| v2.38 | 2026-08-19 | **BR-IP-EXE-038 成码落地**：`StandingOrderOralPackageInventorySupport`；列表抑制 pending + RowDto 字段；待执计数同源；对齐 **AS v2.51** / **AS-IP-DOR-003-SDD v1.50** / **DD** |
| v2.37 | 2026-08-19 | **BR-IP-EXE-038**：口服一盒一点剩余计数与按天抑制 pending；RowDto 增补剩余字段；对齐 **AS v2.51** / **DD-住院长嘱口服一盒一点剩余计数与改量-2026-08** / **AS-IP-DOR-003 IF-IP-DOR-28** |
| v2.36 | 2026-08-14 | **BR-IP-EXE-037**：`ListNurseStationOrderExecuteRequest`/`ListQuery` 增补 `reqOpenedTimeFrom`/`reqOpenedTimeTo`；`NurseStationOrderExecuteListLoader#filterGroupsByReqOpenedTimeRange`；对齐 **AS v2.50** |
| v2.34 | 2026-08-11 | **BR-IP-EXE-036**：产品拆零单位下限（StandingOrderMinSplitUnitBatchExpandSupport + MeasureHelper 抬量；Resolver 035 优先）；对齐 **AS v2.48** / **DD-住院执行-拆零单位下限取整-2026-08** |
| v2.33 | 2026-08-07 | **BR-IP-EXE-035⑤**：停嘱硬顶未凑满当前盒时 SUCCESS 仅已完整盒首（禁止 FALLBACK 降级可拆日点）；`StandingOrderPackageBatchExpandSupport` + 列表 Asm 硬顶兜底；对齐 **AS v2.47** |
| v2.32 | 2026-08-05 | **BR-IP-EXE-032/030**：首次槽（含临嘱）`defaultActualTime`/写侧默认=开立+2 分钟（跨日钳开立日末）；`resolveFirstSlotDefaultActualTime`；对齐 **AS v2.46** |
| v2.31 | 2026-08-05 | **BR-IP-EXE-035 一盒一点**：废止整盒展满多点；新增 `StandingOrderPackageBatchEligibilitySupport`；列表盒首锚点 + pending 至多一个 + 常规 deadline Tab；写路径整包装量；对齐 **AS v2.45** |
| v2.30 | 2026-08-04 | **输液滴速回显**：执行行 `speedCode`/`speedDisplay`（`DisplayCore` 同源）；前端扩展行展示；对齐 **AS-IP-DOR-003-SDD v1.45** |
| v2.29 | 2026-07-27 | **BR-IP-EXE-024**：`assertActualTimeNotBeforeOpened`（秒级；缺开立不拦；**EX-IP-EXE-303**）；§1.2/§3.3/§6 同步；对齐 **AS v2.44** |
| v2.28 | 2026-07-26 | **BR-IP-EXE-013a**：§3.2 Asm 写明 `ChargeItemQueryService` 批量覆盖索引（ORDER_EXECUTE 优先）；§3.3 写路径 `bindQuantity ≤ 0` skip；对齐 **AS v2.43** |
| v2.27 | 2026-07-22 | **BR-IP-EXE-035**：长嘱不可拆包装切盒（SettingResolver + PackageBatchExpandSupport；仅列表 `assemblePatientGroups`）；对齐 **AS v2.42** |
| v2.26 | 2026-07-22 | **临嘱双轨收敛**：**BR-IP-EXE-006**——写前 `alignTemporaryExecutePlanAndActualTimes` 强制 plan=actual；occurrence/recorded/发药/账单同值；读侧已执行缺 recorded 回落 planTime；对齐 **AS v2.41** |
| v2.25 | 2026-07-22 | **BR-IP-EXE-034**：IF-IP-EXE-02 响应增补 `warnings`（长嘱皮试药跨批软提示）；对齐 **AS v2.40** |
| v2.24 | 2026-07-18 | **BR-IP-ORD-031**：执行列表行增补 `stopperId`/`stopperName`；起止时间展示跟开立人/停嘱人 |
| v2.23 | 2026-07-17 | SlotDto UI 展示：待执行优先 `defaultActualTime`，已执行优先 `recordedTime`；勾选/Gate 仍锚 `planTime`；对齐 **AS v2.39** |
| v2.23 | 2026-07-22 | **BR-IP-EXE-002**：住院不再排除皮试检查 companion SR；对齐 **AS v2.39** / **AS-IP-NUR-011 v1.4** |
| v2.22 | 2026-07-17 | **BR-IP-EXE-002**：读列表/`NurseStationSkinTestExecuteExcludeHelper` **仅排除**皮试检查 companion SR；皮试药进待执行（**v2.23 废止住院排除**） |
| v2.21 | 2026-07-17 | **成组自动带共用（BR-IP-NUR-004-GRP-003）**：扩行时 bindItems 仅代表行；绑定耗材 `CreateExecutedDeviceOrderCommand.packageId` 对齐主药嘱；对齐 **AS v2.37** |
| v2.20 | 2026-07-17 | **西药成组一起执行**：**BR-IP-NUR-004-GRP-001/002**；写路径 `expandMedRequestIdsForNurseWrite`（中药整方 / 西药 `group_id`）；对齐 **AS v2.36** |
| v2.22 | 2026-07-18 | **BR-IP-EXE-033 / BR-IP-ORD-028⑤**：嘱托执行跳过建账；`orderKindDisplay` 下发嘱托；对齐 **AS v2.39** / **AS-IP-NUR-006 BR-IP-CDT-020** |
| v2.19 | 2026-07-17 | **不执行单点 / 双轨**：`toTemporaryTerminalSlotDto` 终态 `planTime`=occurrence（废止 recorded 写入 planTime）；§4/§7 与 **AS v2.35** **BR-IP-EXE-006/021** 对齐 |
| v2.18 | 2026-07-16 | **首日用量执行侧（BR-IP-EXE-004/032）**：`LongTermFirstExecutionSlotSupport` 开嘱日首次槽裁剪；`defaultActualTime`；`InpatientMedicationRequestExecutionMeasureHelper` 首次槽换量；对齐 **AS v2.34** / **BR-IP-ORD-030⑥** |
| v2.17 | 2026-07-16 | **BR-IP-EXE-031**：`NurseStationOrderExecuteSlotDto.operatorName`；列表 Asm `enrichTerminalSlotOperatorNames`（主执行人优先，否则 recorder）；对齐 **AS v2.33** |
| v2.16 | 2026-07-16 | **BR-IP-EXE-030** + 修订 **023**：执行双轨——逻辑字段一律 planTime（含汇总领药 plannedDispenseTime）；展示 recorded=actualTime；同日改时分 Gate；对齐 **AS v2.32** |
| v2.15 | 2026-07-16 | **BR-IP-ORD-028④**：嘱托行 `adviceDisplayName` 优先 `entrust_content`；委托 **AS-IP-NUR-003-SDD §2.5** `InpatientEntrustOrderGateSupport#resolveServiceAdviceDisplayName`；对齐 **AS v2.31** |
| v2.14 | 2026-07-16 | **BR-IP-EXE-029**：`NurseStationOrderExecuteRowDto` 增补 **`performerCheckOrgDisplay`**；新增 `PractitionerCheckOrgDisplayResolver`；`DisplayCore` 扩展后由 `applyViewCoreToExecuteRow` 拷贝；对齐 **AS v2.30** |
| v2.13 | 2026-07-11 | **BR-IP-EXE-028**：§3.3/3.4 增补就诊 Gate；**EX-IP-EXE-307**；对齐 **AS v2.29** / **BR-IP-FLW-008** |
| v2.12 | 2026-06-20 | **support 落点对齐**：`InpatientTcmDecoctionExecutesupport、`DeviceDispenseNurseWorkflowsupport → `module.orchestration.nursestation`；护士站读侧 Loader/Assembler/TcmSupport → `module.querysupport.nursestation` |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared.nursestation-device-dispense）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v2.11 | 2026-06-20 | **AS-IP-DOR-006 下游**：`medicineCategoryTab`、中药方折叠、写路径整方扩行、**InpatientTcmDecoctionExecuteSupport**（**BR-IP-TCM-008**）；对齐 **AS v2.27** |
| v2.10 | 2026-06-18 | **BR-IP-EXE-008/014/021**：**hedged pending** 执行-取消对冲净值；`NurseStationOrderExecuteListReadAssembler` pending+cancelled 双 Tab；Gate 方法口径同步；对齐 **AS v2.26** |
| v2.9 | 2026-06-17 | **长临执行同构**：`NurseStationExecutionInventoryAssembler#prepareMedicationFifoBatchPlan`；移除 `linkDispenseProcedure`/`activateChargeByExe` 写路径；对齐 **AS v2.25** / **AS-IP-DOR-003 v1.14** |
| v2.8 | 2026-06-17 | **BR-IP-EXE-008R**（**已由 v2.9 废止**） |
| v2.8.1 | 2026-06-24 | **IF-IP-EXE-02**：`complete` 后写入 `cln_procedure_performer`（`ProcedureExecutionPerformerOrchestrationsupport，PRIMARY，1:1）；对齐 **DM-CLN-004** §6.1.3 |
| v2.7 | 2026-06-17 | **IF-IP-EXE-02 写路径性能**：§3.3 增补 **Pre·写上下文**（`prepareWriteContext` 医嘱去重 + 就诊费用预热；`warmWritePathCatalogCaches` 产品/定价/临时发放单缓存）；§2.1/§2.2 依赖与白名单同步；**不改变** Gate/Cmd 语义；对齐 **AS v2.22** |
| v2.6 | 2026-06-11 | **BR-IP-EXE-019** 绑定耗材 **`unitCode`** 回落链 + `CreateExecutedDeviceOrderCommand.unitCode`；缺失单位 → **EX-IP-EXE-304** |
| v2.5 | 2026-06-08 | **BR-IP-EXE-019** 绑定耗材改为 **`finalizeInpatientTemporaryPathDispense`**（**BR-IP-PRC-019**）；§2.1/§2.2 增补 WorkflowSupport + 条件扣库；对齐 **AS v2.19** |
| v2.4 | 2026-06-06 | **BR-IP-EXE-013/019**：用法 **method_code** 查模板；绑定耗材 **based_on** + **performLocation** + **promoteDraftDispensesForDeviceRequests**；扩充 `NurseStationOrderExecuteBindItemDto` 字段表；对齐 **AS v2.18** |
| v2.3 | 2026-05-29 | **BR-IP-EXE-006～008**：`buildTemporarySlotBuckets` 整单归并；临时已执行 `planTime`=`recorded_time`；`isPlanTimePending` 临时口径；对齐 **AS v2.17** |
| v2.1 | 2026-05-28 | **L3 字段对账**：§4 展开 `NurseStationOrderExecuteRowDto` 字段表（与 Java 一致） |
| v2.2 | 2026-05-28 | **BR-IP-EXE-026/019**：删除「诊疗复制模板单价」旧叙述；绑定项 **`toCreatePlannedChargeItemFromBindExecutionCommand`** 增 **`quantityUnitCode`/`productRow`** |
| v2.1.1 | 2026-05-28 | **BR-IP-EXE-026** 扩展诊疗/绑定项；统一 `ProductDefinitionOrderChargePricingsupport；对齐 **AS v2.15** |
| v2.0 | 2026-05-28 | **BR-IP-EXE-026**：§3.3 Pre·费用 增补药嘱比例换算；对齐 **AS v2.14** |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v1.9 | 2026-05-27 | **`dosageDisplay`/bindItems 单位展示**：委托 **orderview** + **BR-ORD-022**；`unitCode_dictText` 优先绑定行 **`unitName`**；上游 **AS-OP-DOR-003 v1.25** |
| v1.8 | 2026-05-24 | **BR-IP-EXE-004**：`NurseStationOrderExecuteListAssembler#resolvePlannedOccurrenceTimes` 左边界改为开立自然日 00:00:00；对齐 **AS v2.13** |
| v1.7 | 2026-05-24 | 列表行展示字段委托 **`orderview`** `NurseStationOrderViewListAssembler`（§2.5）；与 **AS-IP-NUR-003-SDD v1.1** 对齐 |
| v1.6 | 2026-05-23 | **executeTab 语义修正**：Tab 过滤槽位/计划点而非医嘱行；`executeTab` 必填；移除 `all`；对齐 **AS v2.10** **BR-IP-EXE-009/010** |
| v1.5 | 2026-05-22 | IF-IP-EXE-01 列表行补充 **`groupId`**（药嘱，来自 `MedicationRequestListQueryModel`） |
| v1.4 | 2026-05-22 | 对齐 **AS v2.8**：`accountId` **仅 IF-IP-NUR-02** 查出；IF-IP-EXE-02 入参必填 `accountId`（执行模块不查账户）；长期/绑定 Charge 补全 `definitionId` |
| v1.3 | 2026-05-22 | 对齐 **AS v2.7**：库存 Gate 发放类别 `category_code` 解析与模板精确匹配；§3.3 Pre·库存 / §5 锚点 / 测试；补充 `ProductDefinitionBatchLoader` |
| v1.2 | 2026-05-22 | 对齐 **AS v2.6**：写 Gate = occurrence 防重 + 停嘱边界；deadline 仅列表；补充 `isPlanTimePending` / `isPlanTimeAfterStopTime` |
| v1.1 | 2026-05-22 | 对齐 **AS v2.5**：库存 Gate 仅药嘱；绑定耗材写路径与占位常量；补充依赖/白名单/MapperImpl |
| v1.0 | 2026-05-22 | 首版：对齐 **AS v2.4** 与已关闭域 Gap；IF-01/02/04 编排 + FIFO InventoryAssembler |

