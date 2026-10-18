# 科室医生工作量统计 · 应用服务设计（AS）

> **文档职责**：说明「科室/医生工作量统计」报表页的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为 **health-application** 报表包实现的编排权威。
>
> **页面边界**：单页 **双 Tab**（**汇总** / **明细**，**默认汇总 Tab**）——**门诊/住院单选必选**（**默认门诊**，不可为空）→ **科室可选** → 可选医生 → 日期窗 → **查询**；**未选科室**时统计当前门诊/住院域下**全部科室合并汇总**（**不按科室分块**）；明细 **每行带开单科室**；**导出仅明细**；**只读**。
>
> **编码**：AS-RPT-001 · **版本**：v1.14 · **日期**：2026-10-06

> **DOC-CONFLICT→已按本 DD 收敛**：见 `health/设计决策/DD-报表中心-进售价口径与开立科室-2026-08.md`。本轮：禁 Payment 扫库；金额列售价=账单原价 `total_price`；**已配置有效折扣时另展示优惠**（优惠=`|total_price|−|discount_amount|`）；开立科室=ChargeItem.requesting_org_id；诊疗无进价。

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-RPT-WKL-01 页初始化（**门诊/住院默认门诊**）；IF-RPT-WKL-02 汇总（**fin_type + 挂号号源 + 合计**；未选科室=域内全院合并）；IF-RPT-WKL-03 明细分页（**含开单科室列**）；IF-RPT-WKL-04 导出明细；IF-RPT-WKL-05 **医生卡片墙批量**（仅 `summaryTotals`） |
| **在外** | 门诊收费（**AS-OP-FIN-002**）；门诊退费（**AS-OP-FIN-003**）；门诊日结（**AS-OP-FIN-004**）；门诊费用明细（**AS-OP-FIN-005**）；报表中心通用引擎；待结算/未收费；数据权限 Gate；老系统多 performanceType 绩效报表合并 |
| **涉及域** | **ChargeItem**；**PaymentReconciliation** / **PaymentYbSettlement**（实收分摊）；**ServiceRequest** / **MedicationRequest** / **DeviceRequest**（医嘱锚点）；**Encounter** / **EncounterParticipant**（挂号科室、号源、接诊医生）；**Organization** / **Practitioner**；**HealthcareService**（号源类型）；**ChargeItemDefinition**（fin_type、成本价）；**ProductDefinition**（三大目录展示） |
| **编排权威** | 本文 **§2、§4**；纳入费用与 payment 链路口径对齐 **DM-ADM-011**、**DM-FIN-002**；fin_type 汇总与日结 **BR-OP-DCL-010** 同精神 |
| **数据锚点** | 时间筛选以 **`fin_payment_reconciliation.bill_date`** 闭区间为准；仅 **已结算/已退费** 链路纳入 |
| **产品定位** | 页面名「科室/医生工作量统计」，**实质为医生绩效/收费统计**（基于 payment 快照与 fin_type）；与 **AS-RPT-002 护士工作量**（净执行事实）**不同域**，勿混用口径 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **科室主任 / 财务 / 管理人员** | 选 **门诊/住院**（必选，默认门诊）→ 可选科室 → 可选医生 → 选日期 → **查询** → 默认看 **汇总 Tab** → 切 **明细 Tab** 翻页 → **导出明细** |
| **系统** | 按 **`encounterScope`** 限定数据域并解析科室范围（未选科室 = 该域下全部科室）；汇总 Tab 代数和净额；明细 Tab **按收/退链路逐行带正负号**；退费 **BR-RPT-WKL-011** |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **门诊/住院（必选）** | 页面 **单选**：**门诊** / **住院**；持久化筛选项 **`encounterScope`** = **`OUTPATIENT`** / **`INPATIENT`**；**初始化默认门诊**；**查询时不可为空**；映射 `Encounter.class_enum`：**`AMB`** / **`IMP`**；切换时清空已选科室/医生 |
| **科室（可选）** | **未选** = 该 **`encounterScope`** 下**全部科室**（门诊 → `listOrganizationsForOutpatientRegistration`；住院 → `listOrganizationsForInpatientAdmission`）；**已选** = 仅该科室；所选科室 **`class_enum` 须与 `encounterScope` 一致** |
| **医生（可选）** | 随 **`encounterScope` + 已选科室** 级联收窄；**未选科室** = 该域全部科室下有岗医生；**未选医生** = 科室范围内全员口径 |
| **科室范围解析** | 应用层 **`resolvedOrganizationIds`**：单选科室 →  singleton；未选 → 上述 QueryService 返回之 ACTIVE 科室 id 集合；**禁止**跨 `encounterScope` 混查 |
| **时间筛选** | **`payment.bill_date`** 闭区间；默认 **当日** `[dateFrom, dateTo]` |
| **纳入 ChargeItem 状态** | **`SETTLED`**、**`REFUNDED`**；不含待结算及过程态 |
| **纳入 Payment 状态** | **`SUCCESS`**（收费）、**`REFUNDED`**（退费完成） |
| **科室归属（方案 C）** | **费用/医嘱**：`ChargeItem.requesting_org_id ∈ resolvedOrganizationIds`；**挂号统计**：`Encounter.organization_id ∈ resolvedOrganizationIds`；且 **`Encounter.class_enum`** 与 **`encounterScope`** 一致 |
| **医生归属** | 院点规则「挂到固定人 → 仅该医生接诊」：**选医生时**费用/医嘱 Request **`requester_id`** = 所选医生；挂号统计 **`REGISTRATION_DOCTOR`** 参与者 = 所选医生；未选医生时不按医生收窄（ChargeItem `enterer_id` 为收费人，不参与开单医生筛选） |
| **汇总 Tab** | **单一汇总区**（**不按科室分块**）：**fin_type 表** + **挂号号源分组** + **summaryTotals 合计**。**未选科室** = 当前 **`encounterScope`** 下全部科室 **合并** 为一张 fin_type 表、一张号源表；**已选科室** = 同一结构，数据范围收窄至该科室（响应可附带科室 id/名） |
| **明细 Tab** | **一行 = 一条 payment 快照内的 chargeItem 链路**（收费为正、退费为负，对齐门诊费用明细 **BR-OP-FDT-012**）；必含 **开单科室**；**禁止**按医嘱 request 键合并净额为 0；同一医嘱可有多行（收/退各一行） |
| **目录类型展示** | 按 **三大目录** 回显：**诊疗**（Activity）、**药品**（Medication）、**耗材**（Device）；映射 `service_table` / `product_table`（**BR-RPT-WKL-006**） |
| **号源类型** | **仅分组展示**，**不做**筛选条件；展示名 = **`HealthcareService` / 挂号页 `serviceTypeName`** |
| **排序（明细）** | 医嘱 **开立时间 Request `req_opened_time` 倒序**（缺失时回退 request 创建时间）；**非** ChargeItem.`entered_date`（收费时间） |
| **分页** | 明细 Tab 默认 **20** 条/页；**汇总 Tab 无分页** |
| **导出** | **仅明细 Tab 数据**；与 IF-03 **同筛选、同口径、同排序**；**不含**汇总 sheet |
| **租户** | 仅当前租户；**不**做机构/个人数据权限 Gate |

#### 1.3.1 金额列（汇总 Tab vs 明细 Tab）

> 展示列：**售价始终为账单原价 `total_price`**（带收退符号）；**优惠** = `|total_price|−|discount_amount|`（仅命中折扣配置的行；未命中为 0）。**禁止**把售价改成折后应收。

**汇总 Tab（fin_type 行 + summaryTotals）**

| 展示列 | 业务含义 | 权威与计算 |
| --- | --- | --- |
| **售价** | 账单原价合计 | ChargeItem **`total_price`** 代数 SUM；退费 **负号** |
| **优惠** | 折扣让利合计 | 已配置有效折扣时展示；`|total_price|−|discount_amount|` 代数 SUM（**BR-RPT-WKL-021**）；IF-01 `discountFeatureEnabled` |
| **进价** | 行成本合计 | **药品/耗材**：批号 `LOT_NUMBER_COST`，按 `quantity_unit` 分支（包装×数量 / 拆零满包+零头，**BR-MON-PKG-001** / `ChargeItemPackageUnitRatioSupport`）；**诊疗/挂号**：**无进价**（列空）；退费行 **负号** |
| **净收入** | — | **本期不展示**（汇总/明细/导出均不含净收入列） |
| **挂号笔数 / 挂号人次** | 挂号 **净人次**（一就诊一次） | 对齐 **AS-RPT-007**：同 `encounter_id` 去重；有效挂号 +1，`CANCELLED`/`PLANNED`/`ENTERED_IN_ERROR` 不计入（净 = 挂 − 退）；**禁止**按收费/退费 payment 链路重复计次 |
| **接诊人数** | distinct `encounter_id` | 外显指标；不含 fin_type 金额口径 |
| **处方数量** | distinct 药品请求 `prescription_no` | **仅药品**；同方多味/多行共享处方号计 **1** 张；业务真源为 `MedicationRequest.prescription_no`；应用层 enrich **一律以请求覆盖**收费行快照后再去重；仍无号再回落 `service_id` |
| **诊疗项目总数量** | distinct 诊疗 + 耗材 request | 外显指标 |

**明细 Tab / 导出（逐链路行）**

| 展示列 | 业务含义 | 权威与计算 |
| --- | --- | --- |
| **售价** | 本链路账单原价 | 单条 chargeItem **带符号 `total_price`**；退费 **负号** |
| **优惠** | 本链路折扣让利 | 带符号 `|total_price|−|discount_amount|`；未命中折扣配置为 0（**BR-RPT-WKL-021**） |
| **进价** | 本链路行进价 | 单 chargeItem 批号进价按操作单位分支；**诊疗无进价**（列空）；退费 **负号** |

#### 1.3.2 合计口径

| 合计层级 | 口径 |
| --- | --- |
| **fin_type 行** | 当前筛选条件下、该 **`fin_type_code`** 的售价/优惠/进价 **代数 SUM**（账单行加和；诊疗无进价不加） |
| **挂号号源行** | 当前筛选条件下、该 **`serviceTypeName`** 的挂号 **净人次**（一就诊一次，退号不计入） |
| **summaryTotals（汇总 Tab 页脚）** | 售价/优惠/进价合计 + 挂号净人次 + **外显指标**（接诊/处方按号去重/诊疗项目计数）；**进价合计（DD §2.4 Type B）** = 当次 `finTypeRows[].purchaseAmountTotal` 代数和，与查询同筛选 |
| **明细 Tab** | 不重复 fin_type 分块；页脚可展示与 IF-02 **summaryTotals** 一致 digest |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-RPT-WKL-01～05；双 Tab UI；汇总 + 明细 + 导出明细；卡片墙批量 |
| **非目标** | 汇总 Tab 导出；号源类型 **筛选**；待结算明细；按收费员/患者筛选；跳转医生站/收费窗；老 `report-workload-statistics` 绩效多类型；跨科开单特殊规则（院点声明不存在，仍写 **BR-RPT-WKL-004** 方案 C）；数据权限 Gate |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **ChargeItem** | 费用事实、开单科室（`requesting_org_id`）、service 锚点；开单医生见上游 Request | `DM-ADM-011-ChargeItem-收费明细.md` |
| **PaymentReconciliation** | `bill_date`、charge_item_ids、实收拆分 | `DM-FIN-002-PaymentReconciliation-支付流水.md` |
| **ServiceRequest** | 诊疗医嘱读 | `DM-WKF-004-ServiceRequest-服务申请.md` |
| **MedicationRequest** | 药品医嘱读 | `DM-MED-003-MedicationRequest-药品申请.md` |
| **DeviceRequest** | 耗材医嘱读 | `DM-WKF-005-DeviceRequest-耗材申请.md`（若独立 DM；否则 WKF 耗材域） |
| **Encounter** | 挂号科室、号源、class_enum、退号态 | `DM-ADM-002-Encounter-就诊.md` |
| **Organization** | 科室 class_enum、科室列表 | `DM-ADM-004-Organization-机构科室.md` |
| **Practitioner** | 医生展示 | `DM-ADM-005-Practitioner-医务人员.md` |
| **HealthcareService** | 号源类型名 | `DM-ADM-007-HealthcareService-医疗服务.md` |
| **ChargeItemDefinition** | fin_type、PURCHASE/SALE/LOT_NUMBER_COST | `DM-ADM-010-ChargeItemDefinition-收费项定价.md` |
| **ProductDefinition** | 三大目录名称/规格 | 各目录 DM |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-RPT-WKL-001** | **`encounterScope` 必填**（`OUTPATIENT` / `INPATIENT`）；初始化默认 **`OUTPATIENT`**；`dateFrom`、`dateTo` **必填**且 `dateFrom ≤ dateTo`；默认当日；**`organizationId` 可选** | 01～04 |
| **BR-RPT-WKL-002** | 仅 **`SETTLED`/`REFUNDED`** + payment **`SUCCESS`/`REFUNDED`**；时间以 **`bill_date`** 下推 | 02～04 |
| **BR-RPT-WKL-003** | **`encounterScope`** 映射 `Encounter.class_enum`（`OUTPATIENT`→`AMB`，`INPATIENT`→`IMP`）；**禁止**跨域混查 | 02～04 |
| **BR-RPT-WKL-004** | **方案 C** + **科室范围**：费用/医嘱 **`requesting_org_id ∈ resolvedOrganizationIds`**；挂号 **`Encounter.organization_id ∈ resolvedOrganizationIds`**；未选科室时 **`resolvedOrganizationIds`** = 该域全部 ACTIVE 科室 | 02～04 |
| **BR-RPT-WKL-004a** | 若传 **`organizationId`**，须属于当前 **`encounterScope`** 对应科室列表；否则 **EX-RPT-WKL-106** | 02～04 |
| **BR-RPT-WKL-005** | 选医生时：费用 Request **`requester_id`**；挂号 **`REGISTRATION_DOCTOR`** | 02～04 |
| **BR-RPT-WKL-006** | 明细目录类型：`wkf_service_request`→**诊疗**；`med_medication_request`→**药品**；`wkf_device_request`→**耗材**；无法识别 → **整单失败** | 03、04 |
| **BR-RPT-WKL-007** | fin_type 汇总：**`fin_payment_rec_static` 快照优先**；回落 chargeItem 时 **仅锚 `definition_id` 历史定价行 `type_code`**；号源/挂号费强制 **诊察费 `1012`**（对齐 **BR-OP-DCL-010**）；解析失败 → **整单失败** | 02 |
| **BR-RPT-WKL-008** | 明细 **一行 = 一条 payment 快照内的 chargeItem 链路**（主键 **`chargeItemId + paymentId`**）；**禁止**按 **`service_table + service_id`** 合并净额；同一医嘱可有多行（收费正、退费负）；**禁止**因应收/实收为 0 删行或合并为 0 | 03、04 |
| **BR-RPT-WKL-008a** | 明细 **实收** = 本链路带符号 **`total_price`**（对齐门诊费用明细 **BR-OP-FDT-012**）；**汇总实收**仍走 **AS-OP-FIN-005** tendered 分摊 | 02 vs 03 |
| **BR-RPT-WKL-009** | 挂号统计：**净人次**对齐 **AS-RPT-007**：同一 `encounter_id` 只计 1 次；有效挂号 +1，退号/待挂/错误态不计入；按 **`service_type_id` → serviceTypeName** 分组；**不做**号源筛选；**禁止**按收费/退费链路重复计次 | 02 |
| **BR-RPT-WKL-010** | 成本读价按 **§1.3.1**；批号自 dispense 锚点；**诊疗/挂号**用账单 `unit_price`（不回放） | 02～04 |
| **BR-RPT-WKL-011** | 退费金额列 **负号**；**汇总** fin_type / summaryTotals 为 **代数和**；**明细** 收退 **各一行**、行内带正负 | 02～04 |
| **BR-RPT-WKL-012** | 明细排序：Request **`req_opened_time` DESC**（读模型字段名可仍为 enteredDate） | 03、04 |
| **BR-RPT-WKL-013** | 导出 **仅明细**；与 IF-03 **同筛选、同口径**；**不含**汇总 Tab 内容 | 04 |
| **BR-RPT-WKL-014** | 查询后 **默认激活汇总 Tab**；切明细 Tab 时若未加载则调 IF-03 page=1 | 前端交互；02+03 |
| **BR-RPT-WKL-015** | 汇总 IF 与明细 IF **筛选 payload 一致**；翻明细页 **仅调 IF-03**，不重算汇总 | 02、03 |
| **BR-RPT-WKL-016** | **汇总 Tab 不分科室分块**：IF-02 返回 **`finTypeRows[]`** + **`registrationRows[]`** + **`summaryTotals`**。**未选 `organizationId`**：fin_type 按 **`fin_type_code`** 跨全部 `resolvedOrganizationIds` **合并**；挂号按 **`service_type_id` / serviceTypeName** 跨科室 **合并**。**已选科室**：同一结构，scope 已收窄至该科室。领域 QueryService 可按 org 粒度返回，**应用层负责跨科室 SUM**（**禁止** `departmentBlocks[]` 分块展示） | 02 |
| **BR-RPT-WKL-017** | 明细/导出 **必含开单科室列**（`requesting_org_id` → Organization 展示名） | 03、04 |
| **BR-RPT-WKL-018** | **fin_type / 成本 enrich 须限定当前筛选 scope 内 chargeItem 主键集合**；禁止 payment 链上其它医生/科室脏数据拖垮整单（与 **BR-RPT-WKL-005** 一致） | 02～04 |
| **BR-RPT-WKL-019** | **`summaryTotals`** 除 **应收/实收/成本** 三列 + 挂号净人次外，须回填 **接诊人数**、**处方数量**（**仅药品**；真源 **`MedicationRequest.prescription_no`**，应用层 enrich 写入行旁路字段后按号去重，**禁止**读收费行快照列，同方多行计 1）、**诊疗项目总数量**（**`ChargeItemQueryService#summarizePractitionerWorkloadOverview`**）；**不含净收入** | 02 |
| **BR-RPT-WKL-020** | 明细/导出 **不展示净收入**；DTO 若保留 `netIncomeAmount*` 字段则 **不填充**（兼容旧客户端） | 02～04 |
| **BR-RPT-WKL-021** | **优惠/实收列**：售价保持 `total_price` 原价；优惠=`|total_price|−|discount_amount|`；实收=`discount_amount`（未打折等于售价）。**仅** IF-01 `discountFeatureEnabled=true`（已配置 ACTIVE 折扣内容）时汇总/明细/导出展示优惠与实收；false 时不展示。落点 `ChargeItemDiscountDisplaySupport` | 01～05 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-RPT-WKL-01 | 进入报表页 | **门诊/住院单选**（**默认门诊**，必选）、随域科室（可选）、医生（可选）、默认日期、`discountFeatureEnabled` | 读 | BR-RPT-WKL-001、003、021 | 无 | Organization / Practitioner / 折扣配置 | `OrganizationQueryService#listOrganizationsForOutpatientRegistration` / `#listOrganizationsForInpatientAdmission`；`PractitionerQueryService`；`ChargeDiscountApplySupport` |
| IF-RPT-WKL-02 | 设筛选 + 点「查询」 | 返回 **`finTypeRows[]`** + **`registrationRows[]`** + **`summaryTotals`**（含应收/实收/成本、挂号净笔数、**接诊/处方/诊疗项目计数**；未选科室=域内全院合并；已选=单科室） | 读 | BR-RPT-WKL-001～011、014、016、018、019、020 | 无 | ChargeItem / Payment / Encounter / ChargeItemDefinition | **`ChargeItemQueryService#listPractitionerWorkloadScopeChargeRows`**、**`#summarizePractitionerWorkloadByFinType`**、**`#summarizeRegistrationChargesByServiceType`**、**`#summarizePractitionerWorkloadOverview`**；`EncounterQueryService` §6.2.30～31；`DailySettlementFinTypeAmountsupport；`PractitionerWorkloadCostEnrichmentsupport |
| IF-RPT-WKL-03 | 切明细 Tab / 翻页 | **payment–chargeItem 链路行**分页 + **开单科室** + 应收/实收 + 目录类型（**无净收入**） | 读 | BR-RPT-WKL-001～012、008、008a、017、020 | 无 | 三类 Request / ChargeItem / ProductDefinition / Organization | **`ChargeItemQueryService#pagePractitionerWorkloadDetailByRequest`** |
| IF-RPT-WKL-04 | 点「导出」 | **仅明细** Excel 全量（**含开单科室列**） | 读 | BR-RPT-WKL-013、001～012、017 | 无 | 同 IF-03 | 同 IF-03 |
| IF-RPT-WKL-05 | 卡片墙加载 | 一次扫库后按医生分桶，返回 **`cards[].summaryTotals`**（**不含** fin_type/号源拆分） | 读 | BR-RPT-WKL-001～005、016、018、019 | 无 | 同 IF-02 | 同 IF-02 + `EncounterQueryService#mapActiveRegistrationDoctorByEncounterIds` |

> **交互约定**：点「查询」→ **并行** IF-02（整体合计）+ IF-05（卡片墙）；打开医生弹窗再懒加载 IF-02（单医生）与 IF-03；明细翻页 **仅 IF-03**；导出 **仅 IF-04**。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-RPT-WKL-001 | QueryService 能力 | `DM-ADM-011` §6.2.27、`ChargeItemQueryService` | 缺 **`summarizePractitionerWorkloadByFinType`** | DM §6.2 补齐；领域代码实现 | IF-RPT-WKL-02 | P0 | **已关闭** |
| GAP-RPT-WKL-002 | QueryService 能力 | `DM-ADM-011` §6.2.28、`ChargeItemQueryService` | 缺 **`pagePractitionerWorkloadDetailByRequest`** | DM §6.2 补齐；领域代码实现 | IF-RPT-WKL-03、04 | P0 | **已关闭** |
| GAP-RPT-WKL-003 | QueryService 能力 | `DM-ADM-011` §6.2.29、`ChargeItemQueryService` + `DM-ADM-002` §6.2.30～31 | 缺 **`summarizeRegistrationChargesByServiceType`** 及 Encounter 上下文读 | DM §6.2 补齐；领域代码实现 | IF-RPT-WKL-02 | P0 | **已关闭** |

> **以下不算 Gap**：fin_type 快照 enrich、实收分摊、成本批号解析、三类 request 展示名组装、Excel 列映射 — **应用层** `application/report/practitionerworkload/support` 拼装；scope 预解析 **`PractitionerWorkloadQuerySupport`**（**非 Gap**）。

### 3.1 Gap 关闭落点（2026-07-03）

| Gap ID | DM 章节 | Java 落点 |
| --- | --- | --- |
| GAP-RPT-WKL-001 | `DM-ADM-011` §6.2.27 | `ChargeItemQueryService#summarizePractitionerWorkloadByFinType` |
| GAP-RPT-WKL-002 | `DM-ADM-011` §6.2.28 | `ChargeItemQueryService#pagePractitionerWorkloadDetailByRequest` |
| GAP-RPT-WKL-003 | `DM-ADM-011` §6.2.29；`DM-ADM-002` §6.2.30～31 | `ChargeItemQueryService#summarizeRegistrationChargesByServiceType`；`EncounterQueryService#listEncounterIdsForPractitionerWorkloadScope` 等 |
| — | `DM-FIN-002` §6.2.19 | `PaymentReconciliationQueryService#listPractitionerWorkloadChargeItemIdsByPaymentFilter` |
| — | — | `PractitionerWorkloadQuerySupport#preparePractitionerWorkloadQueryScopes`（`application/report/practitionerworkload/support`，非 Gap） |
| — | — | 汇总 Tab **`finTypeRows` / `registrationRows` / `summaryTotals`** 跨 org 合并与 fin_type enrich（**application 层**，非 Gap；领域层保留 org 粒度读模型） |
| — | `DM-ADM-011` §6.2.30～31 | `ChargeItemQueryService#listPractitionerWorkloadScopeChargeRows`；`#summarizePractitionerWorkloadOverview` |
| — | — | `HistoricalChargeItemPurchasePriceSupport`（药耗：无调价直读批号进价；有调价同批号回放；**诊疗/挂号：账单价、不回放**；**禁止** `listHistoricalDefinitionsByInstance` 无脑全量兄弟扫描） |

---

## 4. 接口编排说明书（业务步骤）

### IF-RPT-WKL-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入科室医生工作量统计页 |
| **主路径步骤** | 下发 **门诊/住院单选**（**默认 `OUTPATIENT`**，**不可取消**）→ 下发当前域 **科室下拉**（可选，含「全部」）→ **默认日期=当日** → 医生下拉待级联；默认 Tab=**汇总** |
| **关键分支** | 切换门诊/住院 → 刷新科室列表并 **清空** 科室/医生；**未选科室可查询**（域内全院合并汇总） |
| **失败分支** | 主数据不可用 → EX-RPT-WKL-101 |
| **查询侧说明** | 只读 Organization / Practitioner；前端 API 须 `publicRequestClient` 解包 `data` |

### IF-RPT-WKL-02 工作量汇总查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选 **`encounterScope`**、有效日期窗；用户点「查询」或停留在汇总 Tab 刷新 |
| **主路径步骤** | **①** 校验 **BR-RPT-WKL-001～005、003、004、004a**（**`encounterScope` 不得为空**）。**②** 解析 **`resolvedOrganizationIds`**。**③** 调 **`listPractitionerWorkloadScopeChargeRows`** 得 scope 内 chargeItem 候选行。**④** 调领域汇总读侧 + fin_type/成本 enrich（**限定 scope chargeItem 主键**，**BR-RPT-WKL-018**）。**⑤** 调 **`summarizePractitionerWorkloadOverview`** 回填外显指标（**BR-RPT-WKL-019**）。**⑥** 按 **BR-RPT-WKL-016** 组装 **`finTypeRows[]`**、**`registrationRows[]`**、**`summaryTotals`**。**⑦** 已选科室时响应附带 `organizationId`/`organizationName` |
| **关键分支** | 未选科室：单一 fin_type 表 + 单一号源表（全院合并）；已选科室：同一结构、范围=该科室；无命中：空行 + summaryTotals 全 0 |
| **失败分支** | 日期非法 → EX-RPT-WKL-102；fin_type/成本锚点无法解析 → EX-RPT-WKL-103；查询失败 → EX-RPT-WKL-104 |
| **查询侧说明** | 纯读；禁止 App 全表内存过滤 |

### IF-RPT-WKL-03 医嘱明细分页查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 与 IF-02 **相同筛选**；用户切明细 Tab 或翻页 |
| **主路径步骤** | **①** 同 IF-02 Gate。**②** 领域分页返回 **payment–chargeItem 链路行**（含 `chargeItemId`、`paymentId`）。**③** enrich request + **开单科室名**（**BR-RPT-WKL-017**）。**④** 按链路装配应收/实收/成本（**BR-RPT-WKL-008/008a**）。**⑤** 排序分页返回 |
| **关键分支** | 无命中：空列表；同一医嘱多次收退 → **多行**（正负各一行）；应收/实收为 0 的行 **仍保留** |
| **失败分支** | 同 IF-02；未知 `service_table` → EX-RPT-WKL-103 |
| **查询侧说明** | 翻页 **不**触发 IF-02（**BR-RPT-WKL-015**） |

### IF-RPT-WKL-04 导出明细 Excel

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点「导出」 |
| **主路径步骤** | 与 IF-03 **相同 Gate 与组装**（**BR-RPT-WKL-013**）→ 全量拉取明细行（不分页或 SDD 步长循环）→ Excel **仅明细 sheet** → 返回文件流 |
| **关键分支** | 无数据 → EX-RPT-WKL-201 或空表导出（SDD 定一种） |
| **失败分支** | 业务失败同 IF-03；超行上限 → EX-RPT-WKL-105 |
| **查询侧说明** | **禁止**附带汇总 Tab 数据 |

### IF-RPT-WKL-05 医生卡片墙批量查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 与 IF-02 **相同筛选**；已拿到医生下拉 id 列表；用户点「查询」后加载卡片墙 |
| **主路径步骤** | **①** 同 IF-02 Gate（不含单医生收窄）。**②** 一次加载 scope charge 行。**③** 挂号医生批量 map 后按 Request **`requester_id`** / **REGISTRATION_DOCTOR** 分桶（**非** ChargeItem.`enterer_id`）。**④** 对入参 `practitionerIds` 逐人组装 **`summaryTotals`**（卡片外显；**跳过成本 enrich**）。**⑤** 返回与入参顺序对齐的 `cards[]` |
| **关键分支** | 某医生无命中：该卡 `summaryTotals` 全 0；无数据科室可短路零值卡 |
| **失败分支** | 同 IF-02 |
| **查询侧说明** | **禁止**卡片墙 N 次 IF-02；fin_type / 号源拆分由弹窗懒加载 IF-02 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-RPT-WKL-101 | 初始化失败 | 页面配置加载失败，请稍后重试 | 是 |
| EX-RPT-WKL-102 | 日期区间非法或未选门诊/住院 | 请选择门诊或住院及有效的查询日期 | 是 |
| EX-RPT-WKL-106 | 所选科室与门诊/住院域不一致 | 所选科室不属于当前门诊/住院范围，请重新选择 | 是 |
| EX-RPT-WKL-103 | fin_type/成本/目录类型无法解析 | 统计数据不完整，请联系管理员核对收费项目配置 | 否 |
| EX-RPT-WKL-104 | 汇总查询失败 | 汇总查询失败，请稍后重试 | 是 |
| EX-RPT-WKL-105 | 导出失败或超上限 | 导出失败，请缩小日期范围后重试 | 是 |
| EX-RPT-WKL-201 | 导出无明细（若采用拒绝策略） | 当前条件下无明细可导出 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 对本页的影响 |
| --- | --- | --- |
| `OutpatientPaymentSettledEvent` / 住院结算完成等价事件 | **AS-OP-FIN-002** / **AS-IP-FIN-003** | 刷新后纳入汇总与明细 |
| `OutpatientRefundCompletedEvent` | **AS-OP-FIN-003** | 负向金额/笔数冲减 |
| `OutpatientRegistrationPaymentSettledEvent` | **AS-OP-FIN-001** | 挂号 fin_type / 号源笔数增加 |

> 本页 **不发布**领域事件；**手动查询**为准。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | IF-02 与 IF-03 **独立 SQL**；ChargeItem、Payment、Request、Product **批量 IN**；导出分批拉取。**就诊 scope** 须叠加 `bill_date` 业务日窗（与就诊时段相交），**禁止**仅按科室拉历史全部 AMB/IMP。Encounter 展示上下文仅对 payment 命中就诊批量加载，**禁止**对科室 scope 内全部就诊拉 context。**IF-02**：payment 快照 chargeItem **一次加载**后复用于 fin_type / 挂号 / 实收分母；payment 筛选在已有 `resolvedEncounterScope` 时须叠加 `encounter_id IN`（**BR-RPT-WKL-003**）；就诊/科室匹配用 HashSet，**禁止**对每条链路线性扫 scope 列表。**IF-05**：卡片墙 **一次**扫库按人分桶，**禁止**「每位医生 × IF-02」；卡片仅 `summaryTotals`，fin_type/号源由弹窗懒加载 IF-02 |
| **一致性** | 只读；查询时刻快照；交账状态 **不影响**纳入 |
| **权限** | 租户隔离；**无**额外 Gate |
| **金额刻度** | 域 **6 位 HALF_UP**；展示两位由 SDD 定 |
| **导出** | **AnnotatedExcelExporter**；**仅明细列**；行数上限 SDD（建议 100000） |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **Tab** | 默认汇总 Tab；明细翻页不调汇总 IF |
| **门诊/住院** | **单选必选**；**默认门诊**；查询时不可为空；切换清空科室/医生 |
| **科室** | 可选；未选=域内全院 **合并** 汇总（不分块）；已选=单科室；与 encounterScope 一致 |
| **汇总分列** | 未选科室：finTypeRows + registrationRows + summaryTotals（**无** departmentBlocks；含 **接诊/处方/诊疗项目计数**）；明细/导出含开单科室列 |
| **医生** | 未选=范围内全员；选=Request **`requester_id`** + **REGISTRATION_DOCTOR** 收窄（**BR-RPT-WKL-005**） |
| **时间** | `bill_date`；仅已结算/已退费 |
| **明细** | 三类 request；**一行 = chargeItemId+paymentId**；收退各一行、金额带正负；目录类型正确；Request **`req_opened_time` 倒序**（**BR-RPT-WKL-012**）；**无净收入列** |
| **金额** | 汇总实收 = tendered 分摊；明细实收 = 带符号 total_price；退费负号；折后为 0 回落 total_price |
| **成本** | 药耗批号 `LOT_NUMBER_COST`（同批号回放）；诊疗/挂号账单 `unit_price`（不回放）；成本 enrich **限定 scope chargeItem**；明细 Tab 默认不展示成本 |
| **导出** | **仅明细**；与明细 Tab 同口径；**无**汇总 sheet |
| **异常** | 缺 fin_type 锚点 → 整单失败 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| **ChargeItem** | `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md |
| **Payment** | `docs/领域/DomainModel/DM-FIN-002-PaymentReconciliation-支付流水.md |
| **Encounter** | `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md |
| **Organization** | `docs/领域/DomainModel/DM-ADM-004-Organization-机构科室.md |
| **定价** | `docs/领域/DomainModel/DM-ADM-010-ChargeItemDefinition-收费项定价.md |
| **参照 AS** | `health/AppService/AS-OP-FIN-004-门诊收费门诊日结.md`（fin_type）；`health/AppService/AS-OP-FIN-005-门诊费用明细.md`（汇总实收分摊 + 明细 **BR-OP-FDT-012**） |
| **并列 RPT** | `health/AppService/AS-INV-RPT-001-库房日结.md`、`AS-INV-RPT-002-单据详情报表.md`；`health/AppService/AS-RPT-002-护士工作量统计.md`（真工作量，勿混口径） |
| **SDD** | `health/AppService-SDD/AS-RPT-001-科室医生工作量统计-SDD.md`（**v1.5**，2026-08-03） |

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.14 | 2026-10-06 | **BR-RPT-WKL-021**：售价保持原价；已配置有效折扣时展示优惠列；init `discountFeatureEnabled` |
| v1.13 | 2026-09-06 | **BR-RPT-WKL-019**：处方数量真源改为药品请求；收费行仅为结算快照（交叉 **AS-OP-FIN-002 · BR-OPC-026**） |
| v1.12 | 2026-08-12 | 药耗进价按操作单位分支（对齐 DD v1.2） |
| v1.11 | 2026-08-06 | 诊疗/挂号成本改账单 `unit_price`（不回放、不读 PURCHASE/SALE）；对齐进价 Support |
| v1.10 | 2026-08-04 | **文档纠偏**：IF-05/验收点「医生」改为 Request `requester_id`（非 ChargeItem.`enterer_id`）；明细排序对齐 **BR-RPT-WKL-012**（`req_opened_time`） |
| v1.9 | 2026-08-03 | **性能**：增 **IF-05** 医生卡片墙批量；前端禁止 N×IF-02；弹窗懒加载 fin_type/号源（口径不变） |
| v1.8 | 2026-08-03 | **性能**：IF-02 chargeItem 一次加载复用；payment 叠加 encounter scope；内存匹配改 HashSet（口径不变） |
| v1.7 | 2026-08-03 | **处方数量**明确为**仅药品**：收费行缺号时应用层回落 **`MedicationRequest.prescription_no`**（同方多味计 1；修复中药按药味虚高） |
| v1.6 | 2026-08-03 | **挂号人次**对齐 **AS-RPT-007**：一就诊一次、退号不计入（**BR-RPT-WKL-009**）；**处方数量**按 `prescription_no` 去重（**BR-RPT-WKL-019**） |
| v1.5 | 2026-07-11 | **明细粒度**：一行 = payment–chargeItem 链路（**BR-RPT-WKL-008**）；收退各一行、退费负号（**BR-RPT-WKL-011**）；明细实收对齐门诊费用明细 **BR-OP-FDT-012**（**BR-RPT-WKL-008a**）；**移除净收入展示**（**BR-RPT-WKL-020**）；禁止 0/0 幽灵行合并 |
| v1.4 | 2026-07-06 | **`summaryTotals` 外显指标**（接诊人数/处方数量/诊疗项目总数量，**BR-RPT-WKL-019**）；scope 内 chargeItem 限定 fin_type/成本 enrich（**BR-RPT-WKL-018**）；挂号成本纳入 PURCHASE→SALE 回落；领域 **`listPractitionerWorkloadScopeChargeRows` / `summarizePractitionerWorkloadOverview`** |
| v1.3 | 2026-07-03 | **口径修正**：未选科室 = 门诊/住院域内 **全院合并汇总**，**禁止**按科室分块（**BR-RPT-WKL-016**）；IF-02 改为 `finTypeRows` + `registrationRows` + `summaryTotals`；**SDD v1.0** 已发布 |
| v1.2 | 2026-07-03 | 关闭 **GAP-RPT-WKL-001～003**；领域读侧 §6.2.27～29 / Encounter §6.2.30～31 / Payment §6.2.19 已落地；明细/导出 **开单科室列**（**BR-RPT-WKL-017**） |
| v1.1 | 2026-07-03 | 门诊/住院必选；科室可选；全科室范围；**BR-RPT-WKL-004a** |
| v1.0 | 2026-07-03 | 首版 AS-RPT-001 |
