# 住院护士站 · 划价 · 应用服务设计（AS）

> **文档职责**：工作台右侧「住院记账」模块契约——**划价确费**（直接划价 + 已划价列表 + **取消划价**）与**医嘱计费**（医嘱补费）两个**独立操作区**；选品、确认划价、多患者批量写路径；**诊疗划价写 Procedure**（耗材仍不写）；**不校验库存**；**不做部分数量冲销**。
>
> **页面边界**：**本模块**——`划价确费` Tab（已划价列表、批量直接划价）、`医嘱计费` Tab（父医嘱列表、补费弹窗）；选品目录两模式共用。**患者上下文**在 **AS-IP-NUR-001**；**医嘱校对/执行**分别在 **AS-IP-NUR-003** / **AS-IP-NUR-004**。**费用明细** Tab 本期不在本文 IF 范围。
>
> **编码**：AS-IP-NUR-005 · **版本**：v2.24 · **日期**：2026-10-17

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-PRC-01 划价选品目录（诊疗+耗材，**无药品**）；IF-IP-PRC-02 **直接划价**确认（可多患者、多行，**禁止**夹带补费行）；IF-IP-PRC-03 **医嘱补费**父医嘱列表（无时间点；**含停嘱**）；IF-IP-PRC-04 **医嘱补费**确认（可多患者、多行，**禁止**夹带直接划价行）；IF-IP-PRC-05 **已划价列表**（直接+补费统一展示；可多患者）；IF-IP-PRC-06 **取消划价**（已划价列表多选整行还原）；IF-IP-PRC-07 **修改划价客户可见时间**（已划价列表多选批量改展示轴） |
| **在外** | 左侧患者树/列表/详情（**AS-IP-NUR-001**）；医嘱校对（**AS-IP-NUR-003**）；医嘱执行（**AS-IP-NUR-004**）；医生开立/签发/签退（**AS-IP-DOR-003**）；**费用明细** Tab（**AS-IP-NUR-006**）；账户余额/费别展示（列表不返回）；**部分数量冲销**（老系统 `deleteInNurseBilling` 冲销分支）；**耗材划价 Procedure**；划价时**库存校验**；批次 **`signCode`**；药品划价 |
| **涉及域** | ServiceRequest；DeviceRequest；DeviceDispense；ChargeItem；ChargeItemDefinition；ProductDefinition；ActivityDefinition；InpatientOrderLookupLoader（住院医嘱列表骨架）；Encounter |
| **编排权威** | 以本文 **§2、§4** 为准；写侧对齐住院护士 `addInNurseBilling` 诊疗语义（**写 Procedure**；去除 signCode、冲销分支）；耗材仍不写 Procedure |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 左侧多选患者 → **划价确费**：查已划价、打开批量划价弹窗选品 → **确认划价**；已划价列表多选 → **冲销**（IF-IP-PRC-06）或 **修改时间**（IF-IP-PRC-07）；**医嘱计费**：查父医嘱 → 选中医嘱 → **补费**；展开补费子行可看**补费人**并对**待结算**子行 **冲销**（同 IF-IP-PRC-06） |
| **系统** | 跳过医生草稿/签发/校对 Tab；诊疗请求 **VERIFIED**、耗材请求 **COMPLETED**；**诊疗**划价同事务写 **COMPLETED Procedure** 并挂接 Charge；费用直接 **PENDING_SETTLEMENT（待结算）**；耗材生成**待发**发放单（**仅锚定 DeviceRequest**，无 Procedure）；补费写 `based_on_*`；带子项诊疗落**父+子项账单**（子行 `parent_id`、价冻死，**BR-IP-PRC-011**）；SR 仍一条父请求；结算/医保**只认**子项账单冻价（无子项账单则拒绝，**禁止**回落 SALE） |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **两种模式（硬分离）** | **直接划价**（IF-IP-PRC-02）与 **医嘱补费**（IF-IP-PRC-04）为**不同 UI 入口、不同写 IF**；**禁止**同一批次混提两种模式 |
| **业务等价** | 语义上等价「医生开**临时**医嘱并完成一次计费落地」：费用 **待结算**、耗材 **待发发放**；**不经**校对 Tab 流程，诊疗创建态即 **VERIFIED**；**诊疗**划价当场写 **COMPLETED Procedure**（对齐老系统 `addInNurseBilling` 与 **BR-RPT-NWL-020**） |
| **护士划价标识** | 新建请求/费用 `generate_source_enum = nurse-pricing`；**诊疗**执行事实由 Procedure 承载；**耗材**仍仅由 DeviceRequest/Dispense 表达 |
| **长临** | 护士新建划价行**一律临时**（`therapy_enum = TEMPORARY`）；父医嘱列表可展示长临，但补费产出仍为临时 |
| **选品范围** | **诊疗**（`wkf_activity_definition`）+ **耗材**（`adm_device_definition`）；**禁止**药品目录行 |
| **选品目录上下文** | **IF-IP-PRC-01** 为目录检索；耗材库存按就诊**住院科室取药模板**解析候选耗材房（建议传 `encounterId`）；**不**按登录人岗位管辖药房；患者/账户上下文仍在**确认划价**（IF-02/04）与**列表**（IF-03/05）使用 |
| **确认划价** | 无暂存/签发；一次提交即落库（Request + Charge；耗材另建 Dispense） |
| **取消划价（UI：冲销）** | 对已划价列表勾选行做**整行冲销留痕**（保留 Request 完成态；原 Charge 保留 + 负向冲销行；`refund_id` 链回原行）；**非**部分数量冲销；仅当费用仍为 **待结算** 且尚未冲销时可操作；**不影响**补费父医嘱；界面按钮文案为 **「冲销」** |
| **直接划价** | 无 `based_on_*`；在 **划价确费** Tab 发起 |
| **医嘱补费** | 每条须父医嘱锚点 `orderType ∈ {MED_REQUEST,SERVICE_REQUEST}` + `requestId`；写入 `based_on_table` + `based_on_id`：`med_medication_request` / `wkf_service_request`；在 **医嘱计费** Tab 发起 |
| **父医嘱列表基线** | `generate_source_enum = doctor-prescription`；`order_type ∈ {MED_REQUEST,SERVICE_REQUEST}`；**含** `VERIFIED`、**含** `STOPPED`、**含**长临；**无**计划时间点/槽位/频次展开；**不含**护士划价来源行作父医嘱 |
| **已划价列表** | `generate_source_enum = nurse-pricing` 的诊疗/耗材请求 + 关联费用态；`based_on_id` 非空视为**补费**，否则**直接划价** |
| **划价客户可见时间** | 列表列 `pricedTime` 与时间区间过滤所用的**展示轴**（**非** `created_at`）：**诊疗**=净剩 COMPLETED Procedure.`recorded_time`；**耗材**=主发放 `dispense_time`（无则 `prepare_time`）。**确认划价**（IF-02/04）可选整批 `pricingTime` 直接写入该展示轴（空则当前时刻）；改时（IF-IP-PRC-07）**只动**该展示轴；**不改** Procedure.`occurrence_time`、Charge.`occurrence_time`、Request 创建/开立时间 |
| **多患者批量** | 列表 IF 支持 `encounterIds` 批量；写 IF 一批可含**多个就诊**多行；**同一 IF 内**整批事务，失败**整批回滚** |
| **诊断** | **本期不支持**划价绑定诊断（写路径不传 `encounterDiagnosisId`） |
| **耗材库房** | 耗材行 **perform_location / 发放库房** 必填（**BR-DR-012**）；划价**不校验**库存可用量 |
| **Dispense 锚点** | 护士划价耗材发放单**仅**关联 **DeviceRequest**；**不**写 `procedure_id` |
| **诊疗套餐子项** | 划价写路径落**父+子** ChargeItem（`ActivityDefinitionPackageChargePersistSupport`；子行挂 `parent_id`，价冻死）；选品 IF-01 回传 `packageChildren` 供展示（**禁止**对外 JSON 串，B.6.2）。父行展示/院内合计；子行结算/医保。展开优先账单子行，禁止回查现行 SALE；子合计须等于父 `totalPrice`。吉林医保经 **`ChsActivityPackageFeeExpandSupport`** |
| **吸氧/心电时长** | 划价确认前对当日累计时长做 Gate（同 **BR-SRQ-018** / 出院开立同类口径） |
| **accountId** | 写路径须携带；取自 **AS-IP-NUR-001** 患者列表挂账账户（**BR-IP-NUR-018**）；服务端仍按医保优先覆盖 |
| **读侧组装** | **方案 A**：InpatientOrderLookupLoader · InpatientOrderListSkeleton 骨架 + 域查询补全明细；**IF-IP-PRC-03** 父医嘱行展示字段委托共享 **`NurseStationOrderViewListAssembler`**（`orderview` 包，见 **AS-IP-NUR-003-SDD §2.5**）；**禁止** App 直查QueryService Repository；**不做**频次展开/槽位 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-PRC-01～07 |
| **非目标** | **费用明细** Tab（见 **AS-IP-NUR-006**）；**部分数量冲销**（老系统 `deleteInNurseBilling` 冲销分支，**本期不做**）；**signCode** 批次号；**耗材划价 Procedure**；划价库存 Gate；药品划价；护士划价行作为补费父医嘱；直接划价与补费**混批**；账户余额/费别列；分页（与其它护士站 Tab 一致，多选患者全量返回） |
| **在外（组套）** | 划价组套列表/预览（仅结构）见 **AS-BAS-MST-017 · IF-BAS-OTL-11、IF-08**；确认仍走本文 IF-IP-PRC-02/04 |
| **与长期执行边界** | 本文 IF-IP-PRC-* 为**护士直接划价/补费**入口；**IF-IP-EXE-02 执行自动带出的诊疗/耗材**按 **BR-IP-EXE-019** 与医嘱补费**同构**落 `nurse-pricing`（based_on 主医嘱），**进入已划价列表并可 IF-IP-PRC-06 冲销**；主药嘱本身仍走 `order-execute`。执行自动带库位/批号在执行写路径现场解析，**不**强制沿用 IF-IP-PRC-01 目录透传 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **ServiceRequest** | 护士划价诊疗建单 **VERIFIED**；补费 `based_on_*` | `DM-WKF-004` §6.1.14 |
| **DeviceRequest** | 护士划价耗材 **COMPLETED** 直建；补费 `based_on_*` | `DM-WKF-003` §6.1.9 / **BR-DR-012** |
| **DeviceDispense** | 耗材待发发放（无 Procedure 锚点）；改时改发放展示时刻 | `DM-WKF-007` §6.1.5～6、§6.1.25 |
| **Procedure** | 诊疗划价 COMPLETED 执行事实；改时仅改 `recorded_time` | `DM-CLN-004` §6.1.7 |
| **ChargeItem** | 护士划价费用 **PENDING_SETTLEMENT** | `DM-ADM-011` §6.1.3 |
| **ChargeItemDefinition** | 定价锚点 | `DM-ADM-010` |
| **ProductDefinition（读）** | 划价选品目录 | 产品QueryService |
| **ActivityDefinition** | 诊疗定义、套餐子项（`listChildrenByActivityId(s)`） | 活动定义域 |
| **InpatientOrderLookupLoader（住院医嘱列表骨架）** | 父医嘱列表 / 已划价列表骨架 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` |
| **Encounter** | 可见性校验 | `DM-ADM-002` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-PRC-001** | 列表/写 `encounterIds` 非空；须落在 **AS-IP-NUR-001** 可见范围（**不含** IF-01 选品目录） | 02、03、04、05、06、07 |
| **BR-IP-PRC-002** | 选品目录：仅**有效**目录行；`pricingFlag=1` **排除药品**；`product_table_name` 仅 **诊疗+耗材** 物理表 | 01 |
| **BR-IP-PRC-003** | **统一目录查价**（同 **BR-ORD-001** / `ClinicalCatalogPriceResolver`）；**废止**租户「医嘱定价来源」及请求字段 `pricingSourceCode` | 01、02、04 |
| **BR-IP-PRC-004** | 直接划价写：每行**不得**带父医嘱锚点；**不得**与补费行混批 | 02 |
| **BR-IP-PRC-005** | 补费写：每行**必须**带父锚点 `orderType`+`requestId`；父须为 **doctor-prescription** 药/诊且可加载；写入 `based_on_table`/`based_on_id`；**不得**与直接划价行混批 | 04 |
| **BR-IP-PRC-006** | 新建划价请求**一律临时**；`generate_source_enum = nurse-pricing` | 02、04 |
| **BR-IP-PRC-007** | **诊疗**划价：请求 **VERIFIED** → **Procedure create→start→挂主执行人→complete**（`category_enum=TEMPORARY`；**`org_id`=登录工作科室**；**`recorder_id`/主执行人=当前登录医务人员**；主执行人须在 COMPLETED 前挂入，对齐 **DM-CLN-004** `assertPerformerAppendable`）→ 费用 **PENDING_SETTLEMENT** 并挂 `procedure_id`（**不进**校对 Tab） | 02、04 |
| **BR-IP-PRC-008** | **耗材**划价：请求 **COMPLETED** + 指定库房 → 生成 Dispense → **`finalizeInpatientTemporaryPathDispense`**（**BR-IP-PRC-019**）：**关**→**PREPARED**（操作人=配药人）；**开**→定批+**COMPLETED**+合并扣库 → 费用 **PENDING_SETTLEMENT**（**仅** `device_req_id`）。定批：写项/`DeviceRequest.lotNumber` **有值**须锁该批，**禁止** FIFO 改批；**空**则按库房效期 FIFO；同事务多行待扣预减账面，禁止重复抢同一库存行 | 02、04 |
| **BR-IP-PRC-019** | **耗材是否自动扣库**（`deviceAutoInventoryDeductSwich`，`SettingDefinitionEnum`）：住院划价/绑定执行/医技划价共用 **`DeviceDispenseNurseWorkflowOrchestrator`**；**开**时按 **BR-IP-PRC-008** 锁批或 FIFO 定批后 COMPLETED+合并扣库；**DeviceRequest** 状态不因护士推进发放而变；**不写 Procedure** | 02、04 |
| **BR-IP-PRC-009** | 划价**不校验**库存；库房为空（耗材）→ 拒绝 | 02、04 |
| **BR-IP-PRC-010** | 吸氧/心电监测类诊疗：确认前校验**当日累计时长**上限（**BR-SRQ-018** 同类读侧累计 + 本次 quantity） | 02、04 |
| **BR-IP-PRC-011** | 带子项诊疗（目录子表，**不是**医嘱组套）：划价写路径落**父 Charge + 子项账单**（`parent_id`=父 id、`bus_no=父.子项id`，金额按落账当时子项 SALE 冻死；SR 仍一条父请求）。**父行**供院内展示/合计（界面不展示子项账单）；**子项账单**供结算/医保。展开只认账单子行（`ActivityDefinitionPackageChildResolver#allocateChargeSlices` / `ChsActivityPackageFeeExpandSupport`），**禁止**回查现行 CID；目录有子项但无子项账单时**拒绝**（**禁止**回落 SALE） | 01（展示）、02、04 |
| **BR-IP-PRC-012** | **废止**：住院划价**不**绑定诊断 | — |
| **BR-IP-PRC-013** | 写路径须 `accountId`（患者列表回传）；服务端按 **BR-IP-NUR-018** 覆盖为挂账账户；批量**同一事务**，失败**整批回滚** | 02、04 |
| **BR-IP-PRC-014** | 父医嘱列表：`doctor-prescription` + 药/诊；**含 VERIFIED 与 STOPPED**；**不含**时间点/槽位；可选长临/关键字/科室筛选；药嘱行**须返回 `groupId`**（`med_medication_request.group_id`，与校对/执行/医生站列表同源，未成组可为 null）；**嘱托诊疗行** `adviceDisplayName` **优先** `entrust_content`（**BR-IP-ORD-028④**；**OrderView** 同源） | 03 |
| **BR-IP-PRC-015** | 已划价列表：仅 `nurse-pricing` 来源诊疗/耗材；可按**划价客户可见时间**区间过滤（展示轴，**非** `created_at`，见 **BR-IP-PRC-030**）；展示费用状态、**`requesterName`（补费/划价操作人）**；区分直接/补费；补费行须返回 **`based_on_table`/`based_on_id`**（与 IF-IP-PRC-03 父行匹配；医嘱计费 Tab 挂父行下并可冲销） | 05 |
| **BR-IP-PRC-016** | 列表不返回余额、费别 | 03、05 |
| **BR-IP-PRC-017** | 选品定价/单位 bands 组装口径同 **BR-ORD-001**（`ClinicalCatalogPriceResolver`）；**耗材以住院科室取药模板（`ServiceLocationTemplate`）候选耗材房库存批号分页驱动**（数量可为 0；总量=库存行数；无库存不出现；**不**按登录人岗位管辖药房）；批号价、默认拆零槽价；诊疗仍产品定义分页；药耗包装锚线性写 bands，**禁止**包装价÷ratio 再展示；目录回传 **`totalVolume`（规格）** | 01 |
| **BR-IP-PRC-018** | 护士划价产出请求 `generate_source_enum=nurse-pricing` **不计入** **AS-IP-NUR-001** `pendingAuditCount`/`pendingExecuteCount`（见 **BR-IP-NUR-004/005**） | 02、04 |
| **BR-IP-PRC-020** | **写路径入账价后端定权威**：IF-02/04 确认划价前须 **`NurseStationInpatientBillingWriteAssembler#reapplyAuthoritativeWritePricing`** 批量重算；耗材有 `lotNumber` 时锁定该批号 `LOT_NUMBER_PRICE` **并**落 `DeviceRequest.lotNumber` 供自动扣库锁批（**BR-IP-PRC-008**），否则预划价 FIFO。**自动扣库开启**时：`finalizeInpatientTemporaryPathDispense` 后再读主发放 `lotNumber`，**二次** `reapplyAuthoritativeWritePricing` 后挂账，保证 `ChargeItem.definitionId` 与实发批号一致（锁批路径实发=选中批；无批号路径实发=FIFO） | 02、04 |
| **BR-IP-PRC-033** | **确认划价可选整批 `pricingTime`**：写入客户可见展示轴——**诊疗** Procedure 创建时 `recorded_time`（与 create.occurrence 同值）；**耗材**发放单生成后 `amendDeviceDispenseDisplayTime` 回写 `dispense_time`/`prepare_time`；空则服务端当前时刻；**不**因本字段改 Charge.`occurrence_time` / Request 创建开立 | 02、04 |
| **BR-IP-PRC-021** | 取消划价写：入参 `items[]` 非空且 ≤50；每行须 `orderType`（`SERVICE_REQUEST`/`DEVICE_REQUEST`）+ `requestId`（与 **IF-IP-PRC-05** 列表行一致）；同批**禁止重复** `(orderType, requestId)` | 06 |
| **BR-IP-PRC-022** | 取消目标须为 **`generate_source_enum=nurse-pricing`** 且可加载的诊疗/耗材请求；非护士划价来源 → 拒绝 | 06 |
| **BR-IP-PRC-023** | **费用 Gate**：按请求锚点关联的 ChargeItem 须全部为 **`PENDING_SETTLEMENT`**；任一 **`SETTLED`/`REFUNDING`/`REFUNDED`** → **整批拒绝**；**但**已被 **BR-REF-020** / **BR-IP-RVS-011** 再生链替代的祖先 `SETTLED`（`ChargeItemRegenerationChainSupport` 递归链）**不参与** Gate | 06 |
| **BR-IP-PRC-024** | 取消划价为**整行冲销留痕**（原正向 Charge 保留 + 插入负向冲销行，`refund_id`→原行；Request **保持完成态不软删**），**非**部分数量冲销；冲销后该行自已划价列表消失（净额为 0）、待结算占用轧差为 0；费用明细可见活动类型「划价」+「冲销」、来源列「护士划价」+「护士冲销」（**AS-IP-NUR-006 BR-IP-CDT-018**）。**反结再生正向**（数量>0 且 `refund_id`→源 SETTLED，见 **DM-ADM-011** §6.1.12a）仍可冲销；仅数量非正的冲销负向行视为已冲（`ChargeItem#isPricingOffsetLine()`） | 06 |
| **BR-IP-PRC-025** | **耗材**取消：按 **BR-IP-PRC-019** 两轨处理 Dispense——**开/已发**（`COMPLETED`）→ **`createPendingRefundDeviceDispense` + `refundDispensedDeviceDispense`** 对冲并合并回补库存；**关/未发**（PREPARATION/PREPARED）→ **`removeUndispensedDeviceDispense`** 软删发放单 → **冲销**关联 Charge（**不**软删 **DeviceRequest**） | 06 |
| **BR-IP-PRC-026** | **诊疗**取消：对关联 **COMPLETED Procedure** 追加 **CANCEL 对冲** → **冲销**关联 **`nurse-pricing` + `PENDING_SETTLEMENT`** Charge（负向行）→ **不**软删 **ServiceRequest**（保持完成态）；带子项诊疗的子项账单与父行同 `service_id`，取消父请求锚点时子项账单一并冲销，**界面不单独勾选子项** | 06 |
| **BR-IP-PRC-027** | 补费行取消**不影响**父医嘱（`based_on_*` 指向的 **doctor-prescription** 药/诊行保持不变） | 06 |
| **BR-IP-PRC-028** | 取消划价写路径**同一事务**整批回滚（对齐 **BR-IP-PRC-013**） | 06 |
| **BR-IP-PRC-029** | **出院结算轨就诊 Gate**：就诊 `status_enum ∈ {PENDING, COMPLETED, DISCHARGED}` 时**禁止** IF-IP-PRC-02/04/06/07 一切划价写（直接划价、补费、取消划价、改展示时间）；**仅** `IN_PROGRESS`/`ON_HOLD` 允许；与 **BR-IP-PRC-023**（费用态 Gate）**正交**；委托 `InpatientPendingSettlementClinicalGateSupport`（**BR-IP-FLW-008**） | 02、04、06、07 |
| **BR-IP-PRC-030** | **划价客户可见时间（展示轴）**：IF-IP-PRC-05 行字段 `pricedTime`——**诊疗**=净剩 COMPLETED Procedure.`recorded_time`；**耗材**=主发放展示时刻（`dispense_time` 优先，否则 `prepare_time`）；列表时间区间过滤按该展示轴（**禁止**按骨架 `created_at`） | 05、07 |
| **BR-IP-PRC-031** | **改时只动展示轴**：IF-IP-PRC-07 **诊疗**仅改净剩 COMPLETED Procedure.`recorded_time`（**不改** `occurrence_time`、Charge.`occurrence_time`、Request 创建/开立）；**耗材**仅改主发放 `dispense_time`（无则改 `prepare_time`）；发放时间=recorded 展示轴 | 07 |
| **BR-IP-PRC-032** | **可改行范围（待结算）**：改时目标须为 `nurse-pricing` 且关联 Charge **全部** `PENDING_SETTLEMENT`（Gate 同冲销 **BR-IP-PRC-022/023**）；`items` 非空且 ≤50、禁止重复键（同 **BR-IP-PRC-021**）；另受可见性与 **BR-IP-PRC-029** 约束 | 07 |
| **BR-IP-PRC-034** | **禁止划价医技执行科诊疗**：确认划价（IF-02/04）诊疗行若活动定义执行科室 `class_enum=TECH` → 拒绝（须医技工作站执行）；**耗材本期不挡**；前端目录可不限 | 02、04 |
| **BR-IP-PRC-035** | **划价/改时不得晚于计划出院**：IF-02/04 整批 `pricingTime`、IF-07 `newTime` 相对 `BusinessDischargeTimeResolver`（生效态出院嘱计划时间优先，回落 `Encounter.endTime`）——有计划出院时业务时刻**不得严格晚于**出院时间；无计划出院则放行。后端 `InpatientDischargeBusinessTimeGate` 硬门禁（补前端缺 `dischargeAt` 跳过）；**EX-IP-PRC-206** | 02、04、07 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-PRC-01 | 划价/补费弹窗：切换耗材/诊疗、关键字、翻页 | 分页划价选品目录（**无药品**；**不**绑定就诊/患者） | 读 | BR-IP-PRC-002～003、017 | 无 | ProductDefinition | — |
| IF-IP-PRC-02 | **划价确费** Tab → 批量划价弹窗 → **确认划价** | 批量**直接划价**确认（可多患者、多行） | 写 | BR-IP-PRC-004、006～011、013、018、019、029、**034**、**035** | 诊 **VERIFIED**；材 COMPLETED；Charge PENDING_SETTLEMENT；Dispense **PREPARED 或 COMPLETED** | Service/Device/Charge/Dispense | — |
| IF-IP-PRC-03 | **医嘱计费** Tab：切换长临/关键字/科室、点查询 | 批量查询**补费父医嘱**列表（无时间点） | 读 | BR-IP-PRC-001、014、016 | 无 | InpatientOrderLookupLoader · InpatientOrderListSkeleton + Med/Service | InpatientOrderLookupLoader · InpatientOrderListSkeleton + 域查询 |
| IF-IP-PRC-04 | **医嘱计费** Tab → 选中父医嘱 → **补费**弹窗 → **确认划价** | 批量**医嘱补费**确认（可多患者、多行） | 写 | BR-IP-PRC-005～013、019、029、**034**、**035** | 同上 + `based_on_*` | Service/Device/Charge/Dispense | — |
| IF-IP-PRC-05 | **划价确费** Tab：时间区间、点查询；或选中父医嘱查看子划价 | 批量查询**已划价**列表（直接+补费） | 读 | BR-IP-PRC-001、015、016 | 无 | InpatientOrderLookupLoader · InpatientOrderListSkeleton + Charge/Dispense 只读 | InpatientOrderLookupLoader · InpatientOrderListSkeleton + 域查询 |
| IF-IP-PRC-06 | **划价确费** Tab → 已划价列表多选 → **冲销** | 批量**取消划价**（整行冲销留痕；UI 文案「冲销」） | 写 | BR-IP-PRC-001、021～029 | Request **保持完成态**；Charge **冲销**（原行保留 + 负向行）；Dispense 回退后软删；补费父医嘱不变 | Service/Device/Charge/Dispense/Inventory | — |
| IF-IP-PRC-07 | **划价确费** Tab → 已划价列表多选 → **修改时间** | 批量**修改划价客户可见时间**（只动展示轴） | 写 | BR-IP-PRC-001、021～023、029～032、**035** | 诊：净剩 COMPLETED Procedure.`recorded_time`；材：主发放 `dispense_time`/`prepare_time`；**不改** occurrence / Charge / Request 创建开立 | Procedure / DeviceDispense | — |

---

## 3. 缺口跟踪（Gap）

> **架构决议（2026-05-24）**：护士划价**诊疗写 Procedure**（耗材仍不写）；`generate_source=nurse-pricing` 标识业务语义；Dispense **仅**锚定 DeviceRequest；直接划价与补费**分 IF、禁止混批**；支持**多患者**批量写；**不做部分数量冲销**、**不保留 signCode**；诊疗划价创建态 **VERIFIED**，**不计入**患者任务条数。

> **架构决议（2026-06-18）**：**取消划价**（IF-IP-PRC-06）初版为 IF-IP-PRC-02/04 的**整行对称还原**；仅 **`PENDING_SETTLEMENT`** 可取消。
>
> **架构决议（2026-07-17）**：**取消划价**改为**整行冲销留痕**：原 Charge 保留 + 负向冲销行（`refund_id` 链回）；**ServiceRequest/DeviceRequest 保持完成态不软删**（其他页面不可见 nurse-pricing、不影响医生开嘱）；费用明细按净额展示「划价/冲销」；**仍非**老系统部分数量冲销分支。
>
> **架构决议（2026-07-20）**：住院记账已划价列表写操作按钮与确认框 UI 文案统一为 **「冲销」**（IF 号/方法名仍为 cancelNurseStationPricing）；费用明细来源列负向行展示 **「护士冲销」**（**AS-IP-NUR-006**）。
>
> **架构决议（2026-08-06）**：**划价客户可见时间**——列表展示与区间过滤用 **recorded / 发放**（**BR-IP-PRC-030**）；IF-IP-PRC-07 改时**只动**该轴（**BR-IP-PRC-031**），**不改** `occurrence_time` / Charge.occurrence / Request 创建开立；费用明细进窗/列同轴（**AS-IP-NUR-006 v1.13**）。

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-PRC-001 | 查询服务 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §3 | 缺 **Q-IP-PRC-001** 已划价列表骨架查询（`encounterIds` + `generate_source=nurse-pricing` + 可选时间区间 + 可选父锚点过滤） | QueryService §3 + `ServiceRequestQueryService#listInpatientNurseStationPricedOrders` 已实现 | IF-IP-PRC-05 | P0 | **已关闭** |
| GAP-IP-PRC-002 | 查询服务 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §3 | 缺 **Q-IP-PRC-002** 补费父医嘱列表骨架（`encounterIds` + `doctor-prescription` + 药/诊 + 状态含 VERIFIED/STOPPED + 可选长临） | QueryService §3 + `ServiceRequestQueryService#listInpatientNurseStationSupplementOrders` 已实现 | IF-IP-PRC-03 | P0 | **已关闭** |
| GAP-IP-PRC-003 | DM 能力 | `DM-WKF-004` §6.1.14 + `ServiceRequestDomainService` | `createFromNursePricing` 现**一次一条**、**无** `based_on_table`/`based_on_id` 入参 | Command 支持批量（或 App 循环但文档化批量契约）+ 补费 `based_on_*` + 领域测试 | IF-IP-PRC-02、04 | P0 | **已关闭** |
| GAP-IP-PRC-004 | DM 能力 | `DM-WKF-003` §6.1.9 + `DeviceRequestDomainService` | `createExecutedDeviceOrder` 现**一次一条**、补费路径**未**写入 `based_on_*` | Command 扩展 `basedOnTable`/`basedOnId` + 批量策略与 DM 一致 | IF-IP-PRC-04 | P0 | **已关闭** |
| GAP-IP-PRC-006 | QueryService / 域事件 | `DM-WKF-003` §5.1.9、`InpatientOrderListSkeleton` §4.2.3、`DM-WKF-004 / DM-MED-003 / DM-WKF-003 §6.2` §3.3 | 护士划价耗材 **`NursePricingDeviceRequestCreatedEvent`** + InpatientOrderLookupLoader | 域层发布事件 + **`各 Request QueryService#onNursePricingDeviceRequestCreated`** 已实现 | IF-IP-PRC-05 | P0 | **已关闭** |
| GAP-IP-PRC-007 | DM 能力 | `DM-WKF-004` §6.1.19 + `ServiceRequestDomainService` | 缺护士划价诊疗**撤销**写命令（`VERIFIED` + `nurse-pricing` 软删；对称 `createFromNursePricing`） | DM 增补 `removeFromNursePricing` + 领域实现 + `NursePricingServiceRequestRemovedEvent` | IF-IP-PRC-06 | P0 | **已关闭** |
| GAP-IP-PRC-008 | DM 能力 | `DM-ADM-011` §6.1.3a + `ChargeItemDomainService` | 缺按请求锚点逻辑删除 **`nurse-pricing` + `PENDING_SETTLEMENT`** 行 | DM 增补 `voidNursePricingChargeItemsByServiceAnchors` + 领域实现 + `NursePricingChargeItemRemovedEvent` | IF-IP-PRC-06 | P0 | **已关闭** |
| GAP-IP-PRC-009 | DM 能力 | `DM-WKF-003` §6.1.10 + `DeviceRequestDomainService` | `revokeExecutedDeviceOrder` 批量 + `nurse-pricing` Gate | Command 批量 + `NursePricingDeviceRequestRemovedEvent` | IF-IP-PRC-06 | P0 | **已关闭** |
| GAP-IP-PRC-010 | QueryService / 域事件 | `InpatientOrderListSkeleton` §4.2/§4.2.3 | 护士划价**移除**事件发布；已划价列表直读 `wkf_*_request` + `is_deleted` 过滤 | 域层发布 `NursePricing*RemovedEvent`；读侧与 GAP-006 同构（主表过滤，无独立 RM 投影 handler） | IF-IP-PRC-05、06 | P1 | **已关闭** |

> **说明**：
> - 选品目录可复用住院医生站 **ProductDefinition** 读查询 + `pricingFlag` 谓词（**BR-IP-PRC-017**），**不入 Gap**。
> - 吸氧/心电累计时长读侧 **DM-ADM-011 §6.2.5** 已存在，App 编排调用，**不入 Gap**。
> - 诊疗套餐子项：读侧 `ActivityDefinitionQueryService#listChildrenByActivityId(s)`（选品展示）；写侧落父 Charge 后经 **`ActivityDefinitionPackageChargePersistSupport`** 补写子账单（`parent_id`）；结算/医保展开优先账单子行（**`ActivityDefinitionPackageChildResolver#allocateChargeSlices`** / **`ChsActivityPackageFeeExpandSupport`**），**不单独入 Gap**。
> - 耗材发放：`generateDeviceDispenseFromTemporaryPath` → **`finalizeInpatientTemporaryPathDispense`**（**BR-IP-PRC-019**）在 **DM-WKF-007** / WorkflowSupport 已有，**不入 Gap**。
> - **取消划价** Dispense 两轨由 **`DeviceDispenseNurseWorkflowSupport#revertInpatientPricingDeviceDispenses`** 编排（已发→待退耗材单对冲；未发→软删），**不入 Gap**（编排层）。
> - **部分数量冲销** 刻意延后，**不入 Gap**；**费用明细 Tab** 见 **AS-IP-NUR-006**。

### 3.1 Gap 关闭落点（代码与文档，2026-05-24）

| Gap ID | QueryService / 域文档 | Java 落点（`zxplat`） | 关键契约 |
| --- | --- | --- | --- |
| GAP-IP-PRC-001 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §3.4.4 | `ServiceRequestQueryService#listInpatientNurseStationPricedOrders` | `nurse-pricing` + 诊/材；可选 `created_at` 谓词（**应用层 IF-05 传 null**，改按展示轴 Asm 后过滤，**BR-IP-PRC-030**）；父锚点 OR 谓词 |
| GAP-IP-PRC-002 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）` §3.4.5 | `ServiceRequestQueryService#listInpatientNurseStationSupplementOrders` | `doctor-prescription` + 药/诊 + `VERIFIED`/`STOPPED` |
| GAP-IP-PRC-003 | `DM-WKF-004` §6.1.14、`DM-WKF-004-SDD` §5.1.14 | `ServiceRequestDomainService#createFromNursePricing` → `List<Long>` | 批量 `saveAll`；`based_on_*` 成对；`TEMPORARY`；创建态 **VERIFIED** |
| GAP-IP-PRC-004 | `DM-WKF-003` §6.1.9、`DM-WKF-003-SDD` §5.1.9 | `DeviceRequestDomainService#createExecutedDeviceOrder` → `List<Long>` | 批量 `saveAll`；补费 `based_on_*`；护士划价库房 Gate |
| GAP-IP-PRC-006 | `DM-WKF-003` §5.3、`InpatientOrderListSkeleton` §4.2.3 | `NursePricingDeviceRequestCreatedEvent` + `各 Request QueryService#onNursePricingDeviceRequestCreated` | 护士划价耗材 InpatientOrderLookupLoader · InpatientOrderListSkeleton 骨架 |
| GAP-IP-PRC-007 | `DM-WKF-004` §6.1.19、`DM-WKF-004-SDD` §5.1.14a | `ServiceRequestDomainService#removeFromNursePricing` + `NursePricingServiceRequestRemovedEvent` | `VERIFIED` + `nurse-pricing` 软删 |
| GAP-IP-PRC-008 | `DM-ADM-011` §6.1.3a、`DM-ADM-011-SDD` §5.1.3a | `ChargeItemDomainService#voidNursePricingChargeItemsByServiceAnchors` + `NursePricingChargeItemRemovedEvent` | 按锚点移除 `PENDING_SETTLEMENT` + `nurse-pricing` |
| GAP-IP-PRC-009 | `DM-WKF-003` §6.1.10、`DM-WKF-003-SDD` §5.1.10 | `DeviceRequestDomainService#revokeExecutedDeviceOrder` 批量 | `NursePricingDeviceRequestRemovedEvent` |

> **应用层（已成码）**：`com.zxplat.domain.application.inpatient.nursestation.inpatientbilling` · `NurseStationInpatientBillingAppService`（IF-IP-PRC-01～07）；**IF-IP-PRC-03** 注入 **`NurseStationOrderViewListAssembler`**，与 **AS-IP-NUR-003/004** 同口径。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-PRC-01 划价选品目录

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在**直接划价**或**补费**弹窗中检索项目（**无需**左侧已选患者） |
| **主路径步骤** | 校验分页 → **耗材**：按就诊住院科室取药模板候选耗材房**库存批号**分页（含数量 0；总量与 content 一致；**不**按登录人管辖）后装配批号价/拆零单位/规格；**诊疗**：产品定义分页 + SALE + 规格；单位 bands 包装锚线性换算（**BR-IP-PRC-017**）→ 返回分页结果 |
| **关键分支** | 切换「耗材/诊疗」仅改变 `product_table_name` 集合；耗材无可用批号仍输出品级一行（**不**做库存 Gate，**BR-IP-PRC-009**）；关键字非空时可做名称/拼音优先重排（与医生站目录同构） |
| **失败分支** | 分页非法 / 目录加载异常 → EX-IP-PRC-101 |
| **查询侧说明** | 纯读 |

### IF-IP-PRC-02 直接划价确认

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **划价确费** Tab 打开批量划价弹窗；用户选品并点**确认划价**；入参一批**仅含直接划价行**（**无**父医嘱锚点） |
| **主路径步骤** | 校验 **BR-IP-PRC-001、004、013**（可见性、非混批、accountId）→ 校验耗材 **`performLocation`** 已由 IF-IP-PRC-01 回传（**BR-IP-PRC-009**，**不**服务端 match 模板）→ **`reapplyAuthoritativeWritePricing`**（可选 `lotNumber` 锁定批号价，否则 FIFO；包装锚→提交 `unitCode` 线性换算，**BR-IP-PRC-020**）→ 解析整批 **`pricingTime`**（空则当前时刻，**BR-IP-PRC-033**）→ **`InpatientDischargeBusinessTimeGate`**（有计划出院则 `pricingTime` 不得晚于出院，**BR-IP-PRC-035**）→ 按行 `product_table_name` 分轨 → **耗材**：`createExecutedDeviceOrder`（落选中 `lotNumber`）→ 生成 Dispense → **`finalizeInpatientTemporaryPathDispense`**（有批号锁批定批，无则 FIFO；同事务待扣预减，**BR-IP-PRC-008/019**）→ **按主发放批号二次重算入账价** → 发放展示轴回写 `pricingTime` → **priceByNurseStation** → **诊疗**：… Procedure/`recorded` 用 `pricingTime` … |
| **关键分支** | 一批含**多就诊**多行：每行自带 `encounterId`；任一行失败**整批回滚**；**诊疗**写 Procedure 并挂 Charge；**耗材**不写 Procedure、**不生成** signCode |
| **失败分支** | 混有父锚点 → EX-IP-PRC-201；库房空 → EX-IP-PRC-202；时长超限 → EX-IP-PRC-203；定价/目录非法 → EX-IP-PRC-204；域写失败 → EX-IP-PRC-205；划价时间晚于计划出院 → **EX-IP-PRC-206** |
| **查询侧说明** | 诊疗：`NursePricingServiceRequestCreatedEvent` → InpatientOrderListSkeleton；耗材：`NursePricingDeviceRequestCreatedEvent` → InpatientOrderListSkeleton；费用：`NursePricingChargeItemCreatedEvent` → InpatientChargeDetail；发放：`DeviceDispenseCreatedEvent`（**不**写 InpatientOrderLookupLoader · InpatientOrderListSkeleton） |

### IF-IP-PRC-03 医嘱补费父医嘱列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **医嘱计费** Tab；左侧已选患者；用户切换长临/关键字/科室或点**查询** |
| **主路径步骤** | 校验 **BR-IP-PRC-001** → **Q-IP-PRC-002** 批量加载骨架（**BR-IP-PRC-014**）→ 批量补全药品/诊疗明细（含药嘱 **`groupId`**）→ 委托 **`NurseStationOrderViewListAssembler`** 组装父医嘱展示行（**禁止**重复实现展示字段）→ **不**做频次展开/槽位 → 按 encounter 分组返回 |
| **关键分支** | **STOPPED** 医嘱仍展示；**护士划价**来源不出现；空列表空态 |
| **失败分支** | EX-IP-PRC-101；EX-IP-PRC-102 |
| **查询侧说明** | 纯展示；补费成功后子划价行不在此列表，而在 **IF-IP-PRC-05**（可按父锚点过滤） |

### IF-IP-PRC-04 医嘱补费确认

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **医嘱计费** Tab 选中父医嘱 → **补费**弹窗选品 → **确认划价**；每行带 **parentRequestAnchor**（`orderType`+`requestId`） |
| **主路径步骤** | 校验 **BR-IP-PRC-001、005、013** → 校验父医嘱存在且为 **doctor-prescription** 药/诊 → 解析 `based_on_table`（药→`med_medication_request`，诊→`wkf_service_request`）→ 余下编排**同 IF-IP-PRC-02**（分轨、Dispense、Charge、子项、时长 Gate；**不含诊断绑定**）→ 域命令 Item 写入成对 **`basedOnTable`/`basedOnId`**（父 `requestId`）→ **同一事务**提交 |
| **关键分支** | 一批可跨**多就诊**；每行父锚点可不同；**禁止**无父锚点行 |
| **失败分支** | 缺父锚点/父不可加载/父为护士划价 → EX-IP-PRC-301；其余同 IF-IP-PRC-02 → EX-IP-PRC-202～205 |
| **查询侧说明** | 同 IF-IP-PRC-02；InpatientOrderLookupLoader `based_on_*` 可供 **IF-IP-PRC-05** 展示补费关联 |

### IF-IP-PRC-05 已划价列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **划价确费** Tab 点**查询**（可选划价客户可见时间区间）；或 **医嘱计费** Tab 选中父医嘱后查看其下已补费行（可选父锚点过滤） |
| **主路径步骤** | 校验 **BR-IP-PRC-001** → **Q-IP-PRC-001** 加载 nurse-pricing 骨架（**不**按 `created_at` 预过滤）→ 批量关联 ChargeItem 状态、Dispense 发放态（耗材）与净剩 Procedure / 主发放时刻 → 组装行：项目名称、数量、单价、总价、**划价客户可见时间**（`pricedTime`，**BR-IP-PRC-030**）、费用状态、**划价模式**（直接/补费）、**`based_on_table`/`based_on_id`**（补费，供前端匹配 IF-IP-PRC-03 父医嘱）→ 按展示轴区间过滤 → 按 encounter 分组返回 |
| **关键分支** | 时间区间为空则返回可见范围内全部已划价行；父锚点过滤仅返回 `based_on_id` 匹配子行；**诊疗** `pricedTime`=`recorded_time`；**耗材** `pricedTime`=发放展示时刻 |
| **失败分支** | EX-IP-PRC-101；EX-IP-PRC-102 |
| **查询侧说明** | 纯展示；IF-IP-PRC-02/04 成功后刷新；IF-IP-PRC-06 成功后剔除已取消行；IF-IP-PRC-07 成功后 `pricedTime` 更新 |

### IF-IP-PRC-06 取消划价（UI：冲销）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **划价确费** Tab → **已划价列表**（**IF-IP-PRC-05**）勾选 ≥1 行 → 点**冲销**；入参为列表行 `orderType` + `requestId` |
| **主路径步骤** | 校验 **BR-IP-PRC-001、021**（可见性、批量键非空且不重复）→ 预加载各行请求并断言 **BR-IP-PRC-022**（`nurse-pricing`）→ 按锚点加载 Charge 并断言 **BR-IP-PRC-023**（全部待结算）→ **分轨还原**（**BR-IP-PRC-025/026**）：**耗材** 按 **BR-IP-PRC-019** 两轨（已发→退耗材单对冲+库存回补；未发→软删 Dispense）→ 逻辑删除 Charge → 软删 DeviceRequest；**诊疗** 逻辑删除 Charge → 软删 ServiceRequest → **同一事务**提交（**BR-IP-PRC-028**） |
| **关键分支** | 直接划价与补费行**同一 IF**、同一 Request 类型；补费父医嘱**不回退**（**BR-IP-PRC-027**）；套餐父/子为独立请求行，**仅取消勾选行**；自动扣库 **开** 且 Dispense 已 **COMPLETED** 时创建待退耗材单（`-R`）确认对冲并合并回补库存；**关** 且 Dispense **PREPARED** 时软删发放单 |
| **失败分支** | 未选/重复键 → EX-IP-PRC-401；非 nurse-pricing/不可加载 → EX-IP-PRC-402；费用已结算/退费中 → EX-IP-PRC-403；发放态不可回退 → EX-IP-PRC-404；域写失败 → EX-IP-PRC-405 |
| **查询侧说明** | 成功后刷新 **IF-IP-PRC-05**；InpatientOrderListSkeleton / InpatientChargeDetail 按移除事件剔除；**诊疗** Procedure 追加 CANCEL 对冲 |

### IF-IP-PRC-07 修改划价客户可见时间

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **划价确费** Tab → **已划价列表**（**IF-IP-PRC-05**）勾选 ≥1 行 → 点**修改时间**；入参为列表行 `orderType` + `requestId` + `encounterId`（同冲销行键）+ 统一 `newTime` |
| **主路径步骤** | 校验 **BR-IP-PRC-001、021、032**（可见性、`items` 非空且 ≤50、禁止重复键、`newTime` 非空）→ 预加载各行请求并断言 **BR-IP-PRC-022**（`nurse-pricing`）→ 按锚点加载 Charge 并断言 **BR-IP-PRC-023**（全部待结算）→ 断言 **BR-IP-PRC-029**（出院结算轨）→ **分轨改展示轴**（**BR-IP-PRC-031**）：**诊疗** `ProcedureDomainService#amendProcedureRecordedTime`（仅净剩 COMPLETED `recorded_time`）；**耗材** `DeviceDispenseDomainService#amendDeviceDispenseDisplayTime`（主发放 `dispense_time` 优先，否则 `prepare_time`）→ **同一事务**提交 |
| **关键分支** | 直接划价与补费行**同一 IF**；**不改** Procedure.`occurrence_time`、Charge.`occurrence_time`、Request 创建/开立；套餐父/子为独立请求行，**仅改勾选行** |
| **失败分支** | 未选/重复键 → EX-IP-PRC-401；非 nurse-pricing/不可加载 → EX-IP-PRC-402；费用已结算/退费中 → EX-IP-PRC-403；就诊待结算轨 → EX-IP-PRC-406；域写失败/无净剩 Procedure/无主发放 → EX-IP-PRC-407 |
| **查询侧说明** | 成功后刷新 **IF-IP-PRC-05**（`pricedTime` 更新）；费用明细进窗/列随 `recorded` 同步（**AS-IP-NUR-006**） |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-PRC-101 | 分页非法 / 目录或列表加载失败 | 请检查分页参数或稍后重试 | 否 |
| EX-IP-PRC-102 | 列表/写操作就诊不可见（**不含** IF-01 选品目录） | 当前患者不可用，请刷新左侧列表 | 是 |
| EX-IP-PRC-201 | 直接划价批次夹带父锚点或补费行 | 直接划价请求格式错误，请使用划价确费入口 | 否 |
| EX-IP-PRC-202 | 耗材未指定发放库房 | 耗材划价失败：请选择发放库房 | 是 |
| EX-IP-PRC-203 | 吸氧/心电当日时长超限 | 吸氧/心电监测时长超出当日上限，请核对数量 | 是 |
| EX-IP-PRC-204 | 目录/定价/数量非法；或诊疗活动执行科室为 TECH（**BR-IP-PRC-034**） | 划价项目无效或价格异常，请重新选品 / 医技执行科室项目请在医技工作站处理 | 是 |
| EX-IP-PRC-205 | 写路径域失败（整批回滚） | 划价失败，请稍后重试 | 是 |
| EX-IP-PRC-206 | 划价/改时晚于计划出院（**BR-IP-PRC-035**） | 划价时间不能晚于出院时间（yyyy-MM-dd HH:mm） | 否 |
| EX-IP-PRC-301 | 补费父医嘱缺失/不可补/来源非法 | 关联医嘱无效，请刷新医嘱列表后重试 | 是 |
| EX-IP-PRC-401 | 取消划价未选行或同批重复 `(orderType, requestId)` | 请选择要取消的划价行 | 是 |
| EX-IP-PRC-402 | 目标非 nurse-pricing 或请求不可加载 | 所选划价行无效，请刷新列表后重试 | 是 |
| EX-IP-PRC-403 | 关联费用已结算或退费中 | 费用已结算，无法取消划价 | 否 |
| EX-IP-PRC-404 | 耗材发放态不允许回退（如已汇总/已核发等护士划价不应出现的状态） | 发放状态不允许取消，请刷新后重试 | 是 |
| EX-IP-PRC-405 | 取消划价域写失败（整批回滚） | 取消划价失败，请稍后重试 | 是 |
| EX-IP-PRC-406 | 就诊已待结算/已结算/已离院 | 患者已办理出院或已结算，不可变更医嘱及费用 | 否 |
| EX-IP-PRC-407 | 修改划价客户可见时间域写失败（整批回滚；含无净剩 Procedure / 无主发放 / `newTime` 非法） | 修改划价时间失败，请稍后重试 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 影响 |
| --- | --- | --- |
| `NursePricingServiceRequestCreatedEvent` | IF-IP-PRC-02/04 诊疗 | InpatientOrderLookupLoader · InpatientOrderListSkeleton 插入/更新 nurse-pricing **诊疗**行（**InpatientOrderListSkeleton §4.2**） |
| `NursePricingChargeItemCreatedEvent` | IF-IP-PRC-02/04 | ChargeItem **PENDING_SETTLEMENT**；InpatientChargeDetail 投影 **PENDING（10）** |
| `DeviceDispenseCreatedEvent` | IF-IP-PRC-02/04 耗材 | 发放读侧待发；**不**写 InpatientOrderLookupLoader · InpatientOrderListSkeleton |
| `NursePricingDeviceRequestCreatedEvent` | IF-IP-PRC-02/04 耗材 | InpatientOrderLookupLoader · InpatientOrderListSkeleton 插入/更新 nurse-pricing **耗材**行（**InpatientOrderListSkeleton §4.2.3**） |
| `NursePricingServiceRequestRemovedEvent` / `NursePricingDeviceRequestRemovedEvent` | （历史） | 旧整行还原软删 Request；**冲销后 IF-06 不再发布** |
| `NursePricingChargeItemOffsetEvent` | IF-IP-PRC-06 | 冲销行落库；待结算占用净额轧差；费用明细可见「冲销」/来源「护士冲销」 |
| `NursePricingChargeItemRemovedEvent` | （历史） | 旧整行还原物理删路径；**冲销后不再发布** |
| `ProcedureRecordCreatedEvent` / `ProcedureRecordCompletedEvent` | IF-IP-PRC-02/04 诊疗 | 护士工作量统计（**AS-RPT-002** **BR-RPT-NWL-020**）；费用明细净 Procedure 时间（**AS-IP-NUR-006**） |
| `ProcedureRecordCancelledEvent` | IF-IP-PRC-06 诊疗 | 取消划价对冲净执行次数 |
| 医生站签发/校对/停嘱事件 | 外部 | **IF-IP-PRC-03** 父医嘱可见性 |

> **刻意不发布 / 不消费**：耗材划价 Procedure 类事件（耗材仍不写执行记录）。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 列表 `encounterIds` 批量 IN；选品分页；写路径一批建议 ≤50 行（含 IF-06/07） |
| **一致性** | IF-IP-PRC-02/04/06/07 **同一事务**整批回滚（首期）；Dispense 与 DeviceRequest 同事务 |
| **权限** | 管理病区在院患者（**IF-03/05 列表与 IF-02/04/06/07 写**）；**IF-01 选品目录**为全局 ProductDefinition 只读检索，**不**校验 encounter 可见性 |
| **时间** | 已划价列表区间过滤用**划价客户可见时间**（展示轴，`LocalDateTime`，**BR-IP-PRC-030**）；服务端另有请求开立/费用 occurrence 时刻（改时不碰） |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **选品** | 仅诊疗+耗材；无药品；pricingFlag 生效；**不传** encounter/patient 亦可检索 |
| **直接划价** | 多患者一批；可选整批 `pricingTime`；诊 **VERIFIED** + **Procedure COMPLETED** + Charge 待结算；材 COMPLETED + Dispense **PREPARED 或 COMPLETED**（**BR-IP-PRC-019**）；无 based_on |
| **补费** | 父 VERIFIED/STOPPED 均可；based_on 药/诊表名正确；与直接划价**分 IF**；可选整批 `pricingTime`（同 IF-02） |
| **药嘱成组** | IF-03 药嘱行含 **groupId**（与校对/执行/医生站同源）；同组一致；未成组 null；诊嘱无 groupId |
| **混批拒绝** | IF-02 带 parentAnchor 拒绝；IF-04 无 parentAnchor 拒绝；02 与 04 **不可**同一请求 |
| **已划价列表** | 直接/补费均可见；**展示轴**时间过滤（诊=recorded、材=发放）；父锚点过滤（医嘱计费右侧） |
| **取消划价** | 多选整行冲销留痕；仅待结算且未冲销可取消；Request 保持完成态；补费父医嘱不变；**诊疗** Procedure CANCEL 对冲；耗材 **BR-IP-PRC-019** 两轨；套餐**仅父行**一条费用，取消时整行冲销；已划价列表按净额剔除 |
| **修改划价时间** | 多选整行改展示轴；仅待结算可改；诊只改 `recorded_time`；材只改发放/拣配时刻；不改 occurrence/Charge/Request 开立；域失败 → **EX-IP-PRC-407** |
| **时长 Gate** | 吸氧/心电超当日上限拒绝 |
| **子项** | 套餐诊疗**不**展开子项费用；展示见 `packageChildren`；拆行见 **`ActivityDefinitionPackageChildResolver`** / 医保 **`ChsActivityPackageFeeExpandSupport`** |
| **诊断** | **废止**（住院划价不绑定诊断；写路径不传 `encounterDiagnosisId`） |
| **库房** | 耗材无库房拒绝；**不**做库存不足拦截 |
| **出院结算轨 Gate** | `ON_HOLD` 可划价/取消划价/改时间；`PENDING`+ → **EX-IP-PRC-406** |
| **非目标** | 部分数量冲销无后端；费用明细见 **AS-IP-NUR-006** |

---

## 9. 关联文档与变更历史

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.25 | 2026-10-18 | **代码先行回写**：**BR-IP-PRC-008/019/020**：目录选中 `lotNumber` 落 `DeviceRequest` 并自动扣库**锁批定批**（禁止 FIFO 改批）；空批号仍 FIFO；同事务多行待扣预减；对齐 **SDD v2.28** / **DM-WKF-003/007** |
| v2.23 | 2026-10-11 | **代码先行回写**：**IF-IP-PRC-05** 增补 **`requesterName`**；医嘱计费展开子行展示补费人，待结算子行可 **冲销**（调 IF-IP-PRC-06） |
| v2.22 | 2026-10-11 | **代码先行回写**：§1.2「与长期执行边界」——**IF-IP-EXE-02** 自动带诊疗/耗材按 **BR-IP-EXE-019** 与医嘱补费同构落 `nurse-pricing`，进已划价并可 **IF-IP-PRC-06** 冲销；主药嘱仍 `order-execute` |
| v2.21 | 2026-10-09 | **代码先行回写**：**BR-IP-PRC-035** / **EX-IP-PRC-206**：IF-02/04/07 后端 `InpatientDischargeBusinessTimeGate`——有计划出院时划价/改时不得晚于出院（补前端缺 `dischargeAt` 跳过） |
| v2.20 | 2026-10-06 | **代码先行回写**：**BR-IP-PRC-011** 取消 SALE 回落、回显只见父项；**BR-IP-PRC-026** 子项账单随父锚点冲销（非独立 SR）；口径「带子项诊疗」≠ 医嘱组套 |
| v2.18 | 2026-09-27 | **BR-IP-PRC-007**：诊疗划价 Procedure 编排改为 **create→start→挂主执行人→complete**（修复 COMPLETED 后再挂执行人触发「当前状态不允许维护执行人」）；对齐 **DM-CLN-004** / **AS-IP-NUR-004** 执行路径；**SDD v2.23** |
| v2.17 | 2026-08-17 | **挂账口径**：IF-IP-PRC-02/04 服务端按 **BR-IP-NUR-018** 覆盖 `accountId`（医保入院挂医保户）；对齐 **AS-IP-NUR-001 v1.35** |
| v2.16 | 2026-08-06 | **IF-IP-PRC-01**：耗材库存改按住院科室取药模板耗材房（**不**跟用户岗位管辖）；UI 去掉货位列；对齐 **SDD v2.21** |
| v2.24 | 2026-10-17 | **代码先行回写**：**BR-IP-PRC-024** 明确反结再生正向（`refund_id` 血缘）仍可冲销；冲销负向用 `isPricingOffsetLine()`；对齐 **DM-ADM-011 v1.6.28** / **SDD v2.27** |
| v2.15 | 2026-08-06 | **IF-IP-PRC-01** 目录增 `totalVolume`/`locationName`（规格/货位）；UI 不单独列类别与单位、单价拼单位；**IF-02/04** 增整批 `pricingTime`（**BR-IP-PRC-033**）直写展示轴；对齐 **SDD v2.20** |
| v2.14 | 2026-08-06 | **IF-IP-PRC-07** `changeNurseStationPricingBusinessTime`：批量改划价客户可见时间；**BR-IP-PRC-030～032**；IF-05 `pricedTime` 展示轴（诊=recorded、材=发放）；**EX-IP-PRC-407**；Gate 同冲销；对齐 **AS-IP-NUR-005-SDD v2.19** / **AS-IP-NUR-006** / **DM-CLN-004** / **DM-WKF-007** |
| v2.13 | 2026-08-05 | **IF-IP-PRC-01**：耗材改为管辖药房/科室药房**库存批号分页驱动**（总量=库存行；废止产品目录分页再过滤）；对齐 **SDD v2.18** |
| v2.12 | 2026-08-05 | **IF-IP-PRC-01 / BR-IP-PRC-017**：耗材选品改为仅登录人管辖**科室药房**有库存记录才展示（数量可为 0；无记录不展示）；废止不限库位回落；对齐 **AS-IP-NUR-005-SDD v2.17** |
| v2.11 | 2026-08-05 | **IF-IP-PRC-01/02/04**：耗材选品按品×批号展开（剩余量/批号价）；默认拆零划价、可切包装；写路径 `lotNumber` 锁定批号价 + 提交单位线性换算；发材模板无候选库位时回落不限库位批次（**BR-IP-PRC-017/020**）；对齐 **AS-IP-NUR-005-SDD v2.16** |
| v2.10 | 2026-07-24 | **BR-IP-PRC-011** 套餐展开口径回更：子项 SALE 计价、不分摊父项；数量=父数量×`children_num`；医保经 **`ChsActivityPackageFeeExpandSupport`** 只传子项；类名对齐 **`ActivityDefinitionPackageChildResolver`** |
| v2.9 | 2026-07-20 | **UI**：IF-IP-PRC-06 按钮/确认框文案「冲销」；**BR-IP-PRC-024** 费用明细展示「冲销 / 护士冲销」；对齐 **AS-IP-NUR-006 v1.11** |
| v2.8 | 2026-07-17 | **IF-IP-PRC-06 / BR-IP-PRC-024～026**：取消划价改为**整行冲销留痕**（Charge 负向行 + `refund_id`；Request 保持完成态不软删）；已划价列表按净额剔除；事件改 `NursePricingChargeItemOffsetEvent`；对齐 **AS-IP-NUR-005-SDD v2.12** |
| v2.7 | 2026-07-16 | **BR-IP-ORD-028④**：IF-IP-PRC-03 父医嘱 `adviceDisplayName` 嘱托行优先 `entrust_content`；**OrderView** 同源；对齐 **AS-IP-NUR-005-SDD v2.11** |
| v2.6 | 2026-07-11 | **BR-IP-PRC-029**：就诊 `PENDING/COMPLETED/DISCHARGED` 禁止 IF-IP-PRC-02/04/06；**EX-IP-PRC-406**；对齐 **BR-IP-FLW-008** |
| v2.5 | 2026-06-25 | **BR-IP-PRC-025**：耗材已发取消路径仅 `COMPLETED`；对齐 **DM-WKF-007 v1.2.4** |
| v2.1 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）

## 9. 域层已实现能力对照（Gap 关闭后）

> **架构决议汇总**（2026-05-24）：① 直接划价与补费**分 IF、禁止混批**；② 护士划价**诊疗写 Procedure**、**耗材不写**、**不生成 signCode**；③ 耗材 Dispense **仅**锚定 `DeviceRequest`；④ 写路径**同一事务**整批回滚；⑤ 列表读侧**方案 A**（InpatientOrderLookupLoader · InpatientOrderListSkeleton 骨架 + 域查询/Assembler）；⑥ 新建划价请求**一律临时**（`TEMPORARY`）；⑦ 诊疗划价创建态 **VERIFIED**，**不计入** **AS-IP-NUR-001** 任务条数。

| 能力 | 域 / QueryService入口 | 文档锚点 | 用途 IF |
| --- | --- | --- | --- |
| 已划价列表骨架 | `ServiceRequestQueryService#listInpatientNurseStationPricedOrders` | InpatientOrderLookupLoader · InpatientOrderListSkeleton **Q-IP-PRC-001** | 05 |
| 补费父医嘱骨架 | `ServiceRequestQueryService#listInpatientNurseStationSupplementOrders` | InpatientOrderLookupLoader · InpatientOrderListSkeleton **Q-IP-PRC-002** | 03 |
| 父医嘱展示组装 | `NurseStationOrderViewListAssembler`（`orderview`） | AS-IP-NUR-003-SDD §2.5 | 03 |
| 诊疗划价建单 | `ServiceRequestDomainService#createFromNursePricing` → `List<Long>` | DM-WKF-004 §6.1.14 | 02、04 |
| 耗材划价建单 | `DeviceRequestDomainService#createExecutedDeviceOrder` → `List<Long>` | DM-WKF-003 §6.1.9 | 02、04 |
| 待发发放 | `DeviceDispenseDomainService#generateDeviceDispenseFromTemporaryPath` → **`DeviceDispenseNurseWorkflowSupport#finalizeInpatientTemporaryPathDispense`**（`application.shared.nursestation-device-dispense`） | DM-WKF-007 / **BR-IP-PRC-019** | 02、04 |
| 护士划价 Procedure 编排 | `NurseStationInpatientBillingProcedureOrchestrator`（`inpatientbilling.support`） | **BR-RPT-NWL-020** | 02、04、06 |
| 护士站计费 | `ChargeItemDomainService#priceByNurseStation`（父项一条；Charge 挂 `procedure_id`） | DM-ADM-011 | 02、04 |
| 取消划价（已落地） | `ServiceRequestDomainService#removeFromNursePricing`；`DeviceRequestDomainService#revokeExecutedDeviceOrder`（批量）；`ChargeItemDomainService#voidNursePricingChargeItemsByServiceAnchors`；**`NurseStationInpatientBillingProcedureOrchestrator#cancelCompletedNursePricingProcedures`**；**`DeviceDispenseNurseWorkflowSupport#revertInpatientPricingDeviceDispenses`** | GAP-IP-PRC-007～010 | 06 |
| 修改划价客户可见时间 | `ProcedureDomainService#amendProcedureRecordedTime`；`DeviceDispenseDomainService#amendDeviceDispenseDisplayTime` | DM-CLN-004 §6.1.7；DM-WKF-007 §6.1.25 | 07 |
| 吸氧/心电时长 Gate | `ChargeItemQueryService#queryOxygenAndECGDuration`（`encounterId`+`activityDefinitionId`+可选 `occurrenceDate`；汇总 `wkf_service_request.quantity`） | DM-ADM-011 §6.2.5 | 02、04 |
| 套餐子项读侧 | `ActivityDefinitionQueryService#listChildrenByActivityId` / `#listChildrenByActivityIds` | DM-WKF-001 §6.2.10～6.2.11 | 01 |
| 套餐子项落账 | `ActivityDefinitionPackageChargePersistSupport#persistPackageChildChargeItemsAfterParent` | `zxplat-application` shared | 02、04 |
| 套餐子项展开 | `ActivityDefinitionPackageChildResolver#listResolvedChildren` / `#allocateChargeSlices`（优先账单子行） | `zxplat-application` shared | 下游（LIS/医保） |
| 套餐子项医保上传展开 | `ChsActivityPackageFeeExpandSupport#expandToUploadSlices` | `module-chs-jilin-bridge` | 2204/2301（及同源 3101 Snapshot） |
| 已划价父医嘱展示 | `MedicationRequestQueryService#resolveMedicationDefinitionId`；`ServiceRequestQueryService#resolveActivityIdByServiceRequestId` | DM-MED-003 §6.2.8；DM-WKF-004 §6.2.14 | 05 |
| 选品目录 | `MedicationDefinitionQueryService` + `pricingFlag` | 同 IF-IP-DOR-11 | 01 |

**批量写契约（域层）**：

| 命令 | 入参形态 | 出参 | 持久化 | 事件 |
| --- | --- | --- | --- | --- |
| `CreateFromNursePricingCommand` | `operatorId` + `items[]`（≥1） | `List<Long>` | `saveAll` | 每行 `NursePricingServiceRequestCreatedEvent` |
| `CreateExecutedDeviceOrderCommand` | `operatorId` + `items[]`（≥1） | `List<Long>` | `saveAll` | 无（与 DM 一致） |

**`based_on_*` 成对规则（域层）**：`basedOnTable` 与 `basedOnId` 必须同时为空（直接划价）或同时非空（补费）；半填 → `SERVICE_REQUEST_INVALID_ARGUMENT` / `DEVICE_REQUEST_INVALID_ARGUMENT`。

**应用层（已成码）**：`NurseStationInpatientBillingAppService` · IF-IP-PRC-01～07 HTTP/DTO/Mapper；写路径可见性 Gate、混批拒绝、账户编排（**不含诊断**）；取消划价见 **IF-IP-PRC-06** / **AS-SDD §3.7**；改展示时间见 **IF-IP-PRC-07** / **AS-SDD §3.8**。

---

## 10. 关联文档与变更历史

| 类型 | 文档 |
| --- | --- |
| 关联 AS | `AS-IP-NUR-001`；`AS-IP-NUR-003`；`AS-IP-NUR-004`；`AS-IP-DOR-003`（IF-IP-DOR-11 目录同构） |
| AS-SDD（参考） | `AS-IP-NUR-003-住院护士站医嘱校对-SDD.md` §2.5；`AS-IP-NUR-004-住院护士站医嘱执行-SDD.md` §9 |
| 查询服务 | `ServiceRequestQueryService / MedicationRequestQueryService 等（DM §6.2）`（§3.4.4～3.4.5）；`DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` **v3.12**（IF-IP-PRC-01 诊疗单位投影） |
| DM | `DM-WKF-004` v1.1.16、`DM-WKF-003` v1.2.8、`DM-WKF-001` v1.0.2、`DM-WKF-007`、`DM-CLN-004`、`DM-ADM-011` v1.5.5、`DM-MED-003` v1.3.4、`DM-ADM-010`、`DM-ADM-002` |
| Domain SDD | `DM-WKF-004-ServiceRequest-服务申请-SDD.md` §5.1.14/§5.1.14a；`DM-WKF-003-DeviceRequest-耗材请求-SDD.md` §5.1.9/§5.1.10；`DM-ADM-011-ChargeItem-收费明细-SDD.md` §5.1.3/§5.1.3a |
| 应用层（已成码） | `inpatient/nursestation/inpatientbilling` · `NurseStationInpatientBillingAppService`（IF-IP-PRC-01～07）；耗材两轨编排 注入 **`application.shared.nursestation-device-dispense`** · `DeviceDispenseNurseWorkflowSupport` |
| **module-support（读侧）** | `application.inpatient.nursestation.{feature}.support` · 划价/补费列表 Loader + `orderview` Assembler |
| UI | `openhis-pro-ui/.../inpatient/nurse-station/inpatient-billing/` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v2.8 | 2026-09-27 | **BR-IP-PRC-007**：诊疗划价 Procedure **create→start→挂主执行人→complete**；编排类名 `NurseStationInpatientBillingProcedureOrchestrator`；对齐 **AS 头 v2.18** / **SDD v2.23** |
| v2.7 | 2026-08-17 | **BR-IP-PRC-020**：耗材 FIFO 实发后按主发放批号二次重算入账价，避免 ChargeItem 与发放 `lot_number` 串批 |
| v2.6 | 2026-07-03 | **BR-IP-PRC-007/026**：诊疗划价写 Procedure（登录人 recorder+主执行人）；取消 CANCEL 对冲；耗材仍不写 |
| v2.4 | 2026-06-20 | **support 落点对齐**：`DeviceDispenseNurseWorkflowSupport` 归 **`module.orchestration.nursestation`**（划价/执行/门诊处置 AppService 注入）；护士站读侧 → `module.querysupport.nursestation`；详见 **AS-IP-NUR-005-SDD** |（已迁至 application.inpatient.nursestation.{feature}.support）（已迁至 application.shared.nursestation-device-dispense）（已迁至 application.shared / feature support（见 DISSOLUTION.md））
| v2.3 | 2026-06-19 | **BR-IP-PRC-025**：取消划价耗材按 **BR-IP-PRC-019** 两轨——已发创建待退耗材单对冲+库存回补；未发软删发放单（`revertInpatientPricingDeviceDispenses`） |
| v2.2 | 2026-06-18 | **应用层** `cancelNurseStationPricing`（IF-IP-PRC-06）已成码 |
| v2.2 | 2026-06-18 | 同步 **DM-WKF-004 v1.1.16**、**DM-WKF-003 v1.2.8**、**DM-ADM-011 v1.5.5** 取消划价域能力（§6.1.19/§6.1.10/§6.1.3a） |
| v2.2 | 2026-06-18 | 关闭 **GAP-IP-PRC-007～010**：域层 `removeFromNursePricing`、`voidNursePricingChargeItemsByServiceAnchors`、`revokeExecutedDeviceOrder` 批量及移除事件 |
| v2.2 | 2026-06-18 | 新增 **IF-IP-PRC-06 取消划价**（已划价列表多选整行还原）；**BR-IP-PRC-021～028**；登记 **GAP-IP-PRC-007～010**；明确与部分数量冲销边界 |
| v2.1 | 2026-05-27 | **IF-IP-PRC-03** 父医嘱列表行补充药嘱 **`groupId`**（`MedicationRequestListQueryModel`，与 **AS-IP-NUR-003/004**、住院医生站列表同源） |
| v2.1 | 2026-06-08 | **BR-IP-PRC-019** 耗材自动扣库开关；**BR-IP-PRC-008** 改为 `finalizeInpatientTemporaryPathDispense` 两轨（PREPARED/COMPLETED+扣库） |
| v2.0 | 2026-05-24 | **诊疗划价**创建态 **VERIFIED**（非 ACTIVE）；**BR-IP-PRC-018** 护士划价不计入 **AS-IP-NUR-001** 任务条数；事件/投影 **`statusEnum`** 对齐 **DM-WKF-004 v1.1.7** |
| v1.9 | 2026-05-24 | 关闭 **GAP-IP-PRC-006**：**`NursePricingDeviceRequestCreatedEvent`** + InpatientOrderLookupLoader · InpatientOrderListSkeleton 耗材投影 |
| v1.8 | 2026-05-24 | **IF-IP-PRC-05** 出参移除 `parentAdviceDisplayName`（父医嘱展示由前端据 `based_on_*` 自行关联） |
| v1.7 | 2026-05-24 | **IF-IP-PRC-05** 出参移除 `parentRequestId`/`parentOrderType`，补费锚点仅 **`basedOnTable`/`basedOnId`** |
| v1.6 | 2026-05-24 | **IF-IP-PRC-05** 已划价行增补 **`basedOnTable`/`basedOnId`**；**BR-IP-PRC-015** 同步 |
| v1.5 | 2026-05-24 | 对齐 **`ProductDefinition` QueryService v3.12** / **`DM-WKF-001` v1.0.2**：诊疗新增后QueryService须含 **`package_unit_code*`、`def_encounter_unit_code*`、`status_enum`**（QueryService聚合快照 + 领域 flush）；IF-IP-PRC-01 **`unitCode`/`unitBands`** 依赖上述列 |
| v1.4 | 2026-05-24 | **IF-IP-PRC-01** 选品目录改为纯 ProductDefinition 检索：**移除** `encounterId`/`patientId` 与 **BR-IP-PRC-001** 可见性 Gate；**BR-IP-PRC-001** 仅绑定 IF-02～05 |
| v1.3 | 2026-05-24 | 套餐展开口径改为 **子表**（`listChildrenByActivityId`）；§9 增补读侧解析与 `queryOxygenAndECGDuration` 契约 |
| v1.2 | 2026-05-24 | 增补 §3.1 Gap 落点表、§9 域层能力对照；IF-02/04 绑定域方法名；DM/SDD/RM 文档回更 |
| v1.1 | 2026-05-24 | **GAP-IP-PRC-001～004 全部关闭**；QueryService **Q-IP-PRC-001/002**；域批量写 + `based_on_*`；**IF-IP-PRC-03** 委托 **orderview** |
| v1.0 | 2026-05-24 | 首版：IF-IP-PRC-01～05；直接划价与医嘱补费分 IF；多患者批量；已划价列表；不写 Procedure；不做冲销/signCode/库存 Gate |
