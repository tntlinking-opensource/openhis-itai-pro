# 门诊收费 · 应用服务设计（AS）

> **文档职责**：说明「门诊收费」工作站的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：门诊收费员工作台——待收患者队列筛选与刷新 → 选择就诊 → 查看待收项目（可选部分勾选）→ 费用性质切换（自费/医保及学生口径）→ 打开结算窗（金额小结、优惠、支付方式）→ 预结算 → 正式收费；与挂号页、医生站医嘱开立、药房/耗材发药等上下游通过收费明细与请求状态联动。
>
> **覆盖声明**：契约以 **§2 IF-OPC-01～08** 为准；**§1.4** 标明本期 UI 占位或舍弃的老能力；队列**分页候选就诊**须存在符合 **BR-OPC-014** 的收费事实，再以 **OutpatientEncounterContextQueryModel** 回补展示字段；队列行「待收费 / 已结算」**标签**仍由 **BillingStatement** 判定（**BR-OPC-012**）；待收明细与批量转账户对 **`adm_healthcare_service` 锚点**及**草稿**的排除与状态白名单见 **BR-OPC-013** / **BR-OPC-010** / **BR-OPC-014**。
>
> **编码**：AS-OP-FIN-002 · **版本**：v1.47 · **日期**：2026-10-16

---

## 1. 场景概述

### 1.1 目标与边界


| 维度 | 说明 |
| --- | --- |
| **在内** | 收费页初始化（收费项状态下拉等）；门诊待收/已结患者分页队列（**先**按 **ChargeItem** 收银口径筛出就诊并分页，见 **BR-OPC-014**；再回补 **OutpatientEncounterContextQueryModel**；关键字/日期与QueryService Q-OPI-001 语义一致；队列**不按**会话机构收窄（**B.6.5**），HTTP **禁止**传机构 ID；查询层与收费事实、EncounterQueryService（OutpatientEncounterContextQueryModel）关联；行内可携带与 IF-OPC-03 同口径的收费明细摘要；**待收费/已结标签**仍由 **BillingStatement** 判定，**BR-OPC-012**）；按就诊加载待收处方/收费项明细（仅 **PLANNED（待收费）** 与 **SETTLED（已结算）**，不含 **DRAFT（草稿）**；含或不含金额扩展；**不含** `service_table=adm_healthcare_service` 挂号费锚点，**BR-OPC-013**；就诊维度自费↔医保及学生口径账户切换（集合口径与 IF-OPC-03 一致，**BR-OPC-010/013**）；门诊预结算与正式结算同前；取消预结算（老实现存在，本期新 UI 未提供入口） |
| **在外** | 医保平台清算细则、第三方支付渠道签约与对账、纸质票据物流、读卡硬件驱动专属协议 |
| **跨域** | Encounter 上下文；Account/Contract 费用性质；ChargeItem 计费明细；PaymentReconciliation 支付流水；医保结算域；MedicationRequest/DeviceRequest/ServiceRequest 执行域；Invoice 票据 |
| **编排权威** | 以本文 **§2、§4** 为准；预结算与正式结算关键校验、防重与金额对齐语义**以既有门诊收费与付款编排实现为准** |
| **前置（摘要）** | 就诊已产生可计费明细且诊断关联完整；账户与合同主数据可用 |
| **后置（摘要）** | 收费成功则收费项进入已计费终态，支付成功，发票进入草稿，相关药/耗/服务请求按规则推进完成与待发药 |


### 1.2 角色与关键动作


| 角色 | 关键动作 |
| --- | --- |
| **门诊收费员** | 筛选/刷新队列 → 点选患者 → 查看/勾选待收项 →（可选）转医保/转自费 → 打开结算窗 → 预结算 → 选择支付方式并确认实付拆分 → 正式收费 |


### 1.3 术语与口径约定


| 术语 | 口径 |
| --- | --- |
| **待收/已结** | UI 队列标签与 **BR-OPC-012** 一致：以 **BillingStatement** 是否仍存在未结清/草稿待支付语义区分；列表过滤选项可与初始化下发的状态选项同源展示 |
| **待收明细（门诊收银）** | IF-OPC-03 仅展示 **`ChargeItemStatus` 为 PLANNED 或 SETTLED** 的明细，**不展示 DRAFT（草稿）**；排除 `service_table=adm_healthcare_service` 挂号锚点后之集合；项目名称/分类展示规则仍按目录→定价回落；**按收费明细事实一行一条**（含 FIFO 跨批分段，不做展示合并）；收银台列表在金额前列示单价 |
| **预结算** | 基于所选收费项、就诊诊断、账户与可选合同锚点，落收费项预结算快照并**落草稿态支付流水主表及从表明细**；从表明细为按收费项记账账户汇总的占位分录（与应收合计一致）。正式结算时领域侧以收银回执为准**整表替换**从表明细；本期请求体仅携带渠道与金额时，**应用层须在结算前将草稿从表上的记账账户并入回执明细**，以满足从表 `account_id` 非空等业务约束 |
| **正式结算** | 在草稿支付流水上完成医保正式结算、自付渠道入账、更新收费项状态、生成发票草稿并推进关联请求状态 |
| **QueryService 能力** | 患者队列主数据为 **OutpatientEncounterContextQueryModel**；**待收费 / 已结算** 须在就诊维度关联 **BillingStatement**（`DM-FIN-002 §6.2 PaymentReconciliationQueryService` §2 支付状态、`kind_enum` 门诊口径等），依据是否存在**未收费/未结清**应收（含预结算产生的**草稿**账单行）决定是否展示在待收侧 |
| **DM 路径** | 下文聚合路径均指 `docs/领域/DomainModel/` 下对应文件 |


### 1.4 本期范围与非目标


| 类别 | 内容 |
| --- | --- |
| **本期（与当前 UI 对齐）** | IF-OPC-01～08：初始化、队列查询与刷新、待收明细、转医保/转自费、预结算、正式结算、收费列表折后价试算；患者信息栏待收/已收（已配置有效折扣时对照原价与折后实收并展示折扣额）；结算窗内金额小结、优惠、支付方式单选 |
| **待接通** | 「电子凭证」「医保卡」按钮仅保留交互占位，介质采集与预结算入参绑定的**独立 IF** 待与医保读卡组件方案一并收口 |
| **非目标** | 学生医保↔学生自费双按钮（老接口存在，当前页面未设计）；取消预结算独立按钮（老链路存在，当前页面未设计）；门诊收费日结单独立页面（见 **AS-OP-FIN-004**）；住院收费 |
| **非目标（口径）** | 结算窗「优惠/折扣」若仅前端试算而未参与预结算草稿现金拆分，**不得**绕过正式结算时「实付合计与预结算现金应付」对齐校验 |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 提供门诊就诊上下文、队列过滤 | `DM-ADM-002-Encounter-就诊.md` |
| **Patient** | 队列展示患者摘要 | `DM-ADM-001-Patient-患者.md` |
| **Account** | 就诊下**个人现金**/**医保**/学生口径账户解析与类型切换落库（UI「自费」与写模型 **`PERSONAL_CASH_ACCOUNT`** 对齐，见 **`DM-ADM-009`**） | `DM-ADM-009-Account-就诊账户.md` |
| **Contract** | 费用性质合同解析、预结算合同锚点 | `DM-FIN-001-Contract-费用性质.md` |
| **ChargeItem** | 待收项查询、账户切换后更新、计费完成置已计费 | `DM-ADM-011-ChargeItem-收费明细.md` |
| **PaymentReconciliation** | 草稿/成功支付流水、明细、静态快照 | `DM-FIN-002-PaymentReconciliation-支付流水.md` |
| **Invoice** | 收费成功后开立发票草稿 | `DM-ADM-012-Invoice-票据发票.md` |
| **MedicationRequest / DeviceRequest / ServiceRequest** | 结算后批量完成请求并按规则置待发药 | `DM-MED-003-MedicationRequest-药品申请.md`、`DM-WKF-003-DeviceRequest-耗材请求.md`、服务请求相关 DM |
| **EncounterDiagnosis（就诊诊断）** | 预结算前按收费行 **`encounterDiagnosisIds`** 加载并校验医疗类型一致 | `DM-ADM-002-Encounter-就诊.md`（`adm_encounter_diagnosis`） |


### 1.6 业务规则（BR-*）


| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-OPC-001** | 预结算所选收费项均须带有效就诊诊断关联 | IF-OPC-06 |
| **BR-OPC-002** | 同一批预结算收费项关联诊断的**医疗类型**须一致 | IF-OPC-06 |
| **BR-OPC-003** | 收费项账户须归属当前就诊且可解析到有效费用性质合同 | IF-OPC-06 |
| **BR-OPC-004** | 正式结算必须指向**草稿态**支付流水，且请求携带的收费项 ID 集合与草稿绑定集合**逐项一致** | IF-OPC-07 |
| **BR-OPC-005** | 正式结算须防重：同一就诊已成功支付流水中**不得**再包含本次拟收的任一收费项 | IF-OPC-07 |
| **BR-OPC-006** | 前台实付渠道拆分金额之和（按金额刻度对齐后）须与预结算结果中的**现金应付部分**一致 | IF-OPC-07 |
| **BR-OPC-007** | 正式结算提交前，拟收收费项**不得**已处于已计费终态 | IF-OPC-07 |
| **BR-OPC-008** | 转自费/转医保（含学生口径变体）须先解析到目标账户，否则拒绝并提示账户缺失语义 | IF-OPC-04 / IF-OPC-05 |
| **BR-OPC-018** | 转自费/转医保**仅**在就诊同时存在默认**自费**与**医保**账户时允许（医保挂号双账户）；纯自费挂号仅单一 `PERSONAL_CASH_ACCOUNT` 时**禁止**切换并提示（**EX-OPC-210**） | IF-OPC-03 / IF-OPC-04 / IF-OPC-05 |
| **BR-OPC-009** | 账户切换失败时返回统一失败语义，成功时返回通用操作成功提示 | IF-OPC-04 / IF-OPC-05 |
| **BR-OPC-010** | 门诊收银 IF-OPC-03 / 04 / 05：收费明细查询**仅包含** `PLANNED`（待收费）与 `SETTLED`（已结算）持久化状态，**不包含 DRAFT（草稿）**；若需展示应收/实收/折扣率，采用带价扩展查询口径（与基础列表同一状态白名单）；并叠加 **BR-REF-020** 排除半退保留链已替代的源 `SETTLED` 行 | IF-OPC-03 / IF-OPC-04 / IF-OPC-05 |
| **BR-OPC-011** | 带价列表返回时对折扣率做离散化规整（按既定步进与阈值修正），仅影响展示型折扣率文案 | IF-OPC-03 |
| **BR-OPC-012** | 待收/已结**队列标签**：对**已进入分页队列的每条就诊**（入选前提见 **BR-OPC-014**），按 **就诊标识** 关联 **BillingStatement** 判定——仍存在未结清或未正式收费成功的应收（含预结算产生的**草稿**账单语义，以该QueryService §2 `status_enum` / `kind_enum` 等字段为准）则该队列行 `queueLabelCode` 为「待收费」；否则为「已结算」 | IF-OPC-02 |
| **BR-OPC-013** | 门诊收银「待收明细加载」「队列明细摘要」「批量转医保/转自费」所依据的收费明细集合**不包含** `adm_charge_item.service_table = adm_healthcare_service` 的锚点行（挂号费等）；与挂号环节金额展示解耦；`service_table <> 排除值` 须在**数据库查询层**下推（与 **BR-OPC-010** 状态条件组合），禁止应用层全量加载后再内存剔除 | IF-OPC-02 / IF-OPC-03 / IF-OPC-04 / IF-OPC-05 |
| **BR-OPC-014** | 门诊收银队列**分页候选就诊**仅包含：在 `adm_charge_item` 上存在至少一条同时满足 **①** `status_enum ∈ {PLANNED, SETTLED}`、**②** `service_table <> adm_healthcare_service` 的明细之就诊；仅有草稿明细或仅有挂号锚点明细的就诊**不得**进入队列；关键字/日期过滤语义与 **OutpatientEncounterContextQueryModel** Q-OPI-001 一致；**`OutpatientChargeEncounterQueueLoader`** 预解析 AMB 就诊范围后委托 `ChargeItemQueryService`，**不按**会话机构收窄（**B.6.5**），**禁止**依赖调用方传入 `organizationId`；须在查询层与EncounterQueryService（OutpatientEncounterContextQueryModel）关联；分页与排序在查询层完成（按就诊内最近命中明细之业务时刻倒序等实现约定）；响应行可携带与 IF-OPC-03 同口径的收费明细摘要以供列表展示 | IF-OPC-02 |
| **BR-OPC-015** | 正式结算成功且收费项已置 **SETTLED** 后，须按收费明细 `service_table`/`service_id` 锚点**幂等**推进关联医嘱（门诊口径）：**MedicationRequest** 由 **ACTIVE→COMPLETED**（不进住院核对 **VERIFIED**）；同批关联 **MedicationDispense** 中仍为 **DRAFT** 的一并 **DRAFT→PREPARATION**；**DeviceRequest** 由 **ACTIVE→COMPLETED**，同请求下 **DeviceDispense** 仍为 **DRAFT** 的一并 **DRAFT→PREPARATION**；**ServiceRequest** 由 **ACTIVE→COMPLETED**（与病区护士「校对」收口语义一致：持久化存 **`RequestStatus#getCode()`** 字符串，如 `ACTIVE`/`COMPLETED`，**勿**与枚举 sortOrder 数字混写）；编排由门诊收费应用在同一事务内**同步调用**各域写方法完成，事件驱动QueryService为投影路径；锚在发放单表上的收费项须先解析回对应请求主键再执行上述迁移 | IF-OPC-07 |
| **BR-OPC-016** | 门诊/挂号 `settleChargeItems` 须将命令 `operatorId`（当前收费人 `adm_practitioner.id`）与 `billDate`（与 payment `bill_date` 同值）写入各收费明细 `enterer_id`/`entered_date`；**禁止**再写 `performer_id` 为收款人；IF-OPC-03 已结算行展示收费人读 `enterer_id` | IF-OPC-07 / IF-OPC-03 |
| **BR-OPC-017** | IF-OPC-06/07 落支付草稿时须写入 `fin_payment_reconciliation.contract_no`（`fin_contract.bus_no`）：请求显式 `contractId` 优先；否则从所选收费项记账账户聚合解析（**BR-OPC-003**）；单账户或唯一 contract_no 时必写；自付与医保同时付费且 contract_no 无法唯一确定时可为空（**DM-FIN-002**）；IF-OPC-07 正式结算前对历史空 contract_no 草稿须补写后再落成功态 | IF-OPC-06 / IF-OPC-07 |
| **BR-OPC-019** | **真正零元不开票**：票面（收费项合计）**≤ 0** 时正式结算成功后**不**创建发票草稿、**不**调度博思；票面 **> 0**（含医保基金/个账全报、现金 `displayAmount=0`）时按 **BR-INV-008** 开票（编排 `InvoiceAfterPaymentOrchestration`） | IF-OPC-07 |
| **BR-OPC-020** | **一张混结 payment**：同批可含**自费**与**同一种**第三方医保费用性质；支付**主表一条**、从表按账户多行；**不**因自费+医保拆成两张 payment（历史「展示合并」同号规则已于 v1.23 撤回，本条为新语义）。日结读侧拆桶见 **AS-OP-FIN-004 · BR-OP-DCL-027**（挂自费户→自费，挂医保户 CASH→医保现金） | IF-OPC-06 / IF-OPC-07 |
| **BR-OPC-021** | **同批至多一种第三方医保合同**：勾选行上第三方医保费用性质合同去重后不得 &gt; 1（省医保与市医保等不得同批）；与自费混结允许；违反 → **EX-OPC-309**。就诊挂号理论仅一种医保合同，本条为防御门禁 | IF-OPC-06 |
| **BR-OPC-022** | **门诊代煎挂个人现金自费户**：签发时账单 `account_id` 指向就诊 `PERSONAL_CASH_ACCOUNT`（与挂号双账户自费户一致）；**禁止**为代煎另开/改写 `Account`；IF-OPC-04/05 转医保/转自费**跳过**代煎账单行；费用性质展示跟账户 `contract_no`（与 **AS-OP-DOR-010 · BR-TCM-RX-009**、**BR-OPC-018** 对齐） | IF-OPC-04 / IF-OPC-05 / IF-DOR-37 |
| **BR-OPC-023** | **正式结算前药耗发放批号已锁定**：IF-OPC-07 在 `OutpatientChargeSettlementGate` 中，对收费行 `dispense_table`/`dispense_id`（及 `service_table` 直接锚发放表时）装载 `MedicationDispense`/`DeviceDispense`，`lot_number` 须非空且非护士占位 `NURSE_STATION_LOT_PENDING`；否则拒绝结算并提示医生改签。与 **BR-ORD-033**（签发挂费一致）、**BR-NUR-006**（执行读发放批号）衔接，禁止「已 SETTLED、护士执行报批号缺失」 | IF-OPC-07 |
| **BR-OPC-026** | **处方号结算快照**：药品业务真源为 `MedicationRequest.prescription_no`；签发挂费**不**预写 `ChargeItem.prescription_no`。IF-OPC-07 在 `settleChargeItems` **之前**须从药品请求（或发放→药品请求）抄写快照；诊疗侧**仅**申请单号（`ServiceRequest.prescription_no` 且前缀 **`AR`**）可盖到收费行（`OutpatientChargePrescriptionSnapshotStamper` → `stampPrescriptionSnapshots`）。**代煎 companion** 不以 SR 方号盖章（关联靠 `based_on`→药嘱，SR 自身处方号应空）。账单打印/统计可读快照；分方/取号/业务判断**禁止**以收费行为真源 | IF-OPC-07 |
| **BR-OPC-024** | **权威个人现金口径（预结算 + 正式结算）**：公式恒为 `Σ psn_cash_pay + max(0, 收费合计−Σ medfee)`（`InsuranceSettlementCashAlignSupport#resolveTargetPatientCash`）。**IF-OPC-06**：混合医保预结算写支付草稿头 `displayAmount` 与占位明细时**必须**用该公式，**禁止**仅用 Extension `totalPsnCashPay`（=组现金+显式 `selfPayRollup`）——挂医保户但未进本次 `medfee` 的院内差额（如未上传明细）须并入待收，否则弹窗/银联少收、落账后再抬高微信。**IF-OPC-07（两阶段勾稽）**：2207 成功后写入基金/个账分项，并以同公式对齐自付渠道与头表 `display`/`tendered`。**阶段 A（医保 merge 后）**：自付渠道合计=权威个人现金，且全明细合计=收费项合计。**阶段 B（终态，扫码/账户拆行之后）**：再次校验全明细合计=收费项合计，覆盖**纯自费**少记渠道与混结拆行扰动；失败 → **EX-OPC-405**。禁止自付沿用过期草稿/前端金额导致「日结四渠道≠总收入」 | IF-OPC-06 / IF-OPC-07 |
| **BR-OPC-025** | **半成功支付草稿禁止裸作废**：同支付仍存在未退货的银联 BPC `SUCCESS` 消费单时，预结算清草稿 / 签退清陈旧草稿**拒绝**作废（`CANCEL-BIZ-413`），须补结算或先银联退货；正式结算成功后清理其它草稿时对该类半成功草稿**跳过保留**（不回滚本笔 settle）。与挂号未收费取消半成功门禁同码。正常 settle / 原路退（先 Win R）不走本门禁 | IF-OPC-06 / IF-OPC-07 |
| **BR-OPC-027** | **未配置有效折扣禁传率**：无 ACTIVE 未删折扣配置时，预结算/正式结算/列表试算**禁止**携带 `cashierDiscountRate`；未传率一律零侵入 | IF-OPC-06 / 07 / 08 |
| **BR-OPC-028** | **打折三门禁**：仅同时满足「自费账户 + 款员已选折扣率 + 命中可打折配置」才走打折；未选折扣率、医保行、未命中配置 → **不校验、不折价、不拦收费**（与正常结算相同）。纯医保批即使传了折扣率也零侵入；自费批传了率却无一命中 → 拒绝 | IF-OPC-06 / 07 / 08 |
| **BR-OPC-030** | **同批可折行有效率须一致**：进入打折路径后，同批可折行有效折扣率不一致须拆单结算 | IF-OPC-06 / 07 |
| **BR-OPC-033** | **收费列表折后价试算只读**：门禁与预结算相同（**BR-OPC-028**）——未选折扣率/医保/未命中 → 折后=原价；进入打折后配置率 ∈ (0,1) 固定优惠优先，空率或 1（10 折）用款员所选率。**不**写收费折扣快照、**不**产生支付草稿 | IF-OPC-08 |
| **BR-OPC-034** | **患者信息栏费用摘要**：未配置 ACTIVE 折扣内容时，「待收/已收」按未结/已结行 **原价** 分列，**不**展示折扣额。已配置时：「待收」=本屏明细 **原价合计**（含已结算行，收费后仍对照折前）；「已收」=已结算行 **折后应收**（`discount_amount`，空则回落原价）；「折扣」=已结算原价合计−已收；未命中配置的行（如未配西药）不产生折扣额 | IF-OPC-03 |
| **BR-OPC-035** | **医保读卡人须等于当前就诊患者**：IF-OPC-06 走第三方医保预结算时，1101 返回的参保人身份证号（优先）或姓名须与就诊患者主档一致；不一致 → **EX-OPC-310**，禁止继续 2201/2206。前端读卡后先拦一层；应用层与 CHS 预结算/2201 再拦（防旁路）。历史错误平台映射与本次读卡 `psn_no` 不一致时须作废错 map 后重建，不得复用串账残留 | IF-OPC-06 |


---

## 2. 页面与接口总览（契约概览）


| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-OPC-01 | 进入门诊收费页 | 下发收费页初始化数据（含收费项状态下拉等） | 读 | — | — | ChargeItem（枚举元数据） | — |
| IF-OPC-02 | 关键字/日期/队列状态筛选或点击刷新 | 分页查询门诊收银队列：**候选就诊**依 **BR-OPC-014** 由收费事实收敛；回补 **OutpatientEncounterContextQueryModel**；行内可含收费明细摘要；再按 **BillingStatement** 打 **BR-OPC-012** 标签并按页签过滤 | 读 | BR-OPC-012 / BR-OPC-013 / BR-OPC-014 | — | ChargeItem / Encounter / Patient / PaymentReconciliation（经 BillingStatement 投影） | OutpatientEncounterContextQueryModel；BillingStatement |
| IF-OPC-03 | 点选队列中某一就诊 | 加载该就诊下收费项行表：**仅 PLANNED 与 SETTLED**（**BR-OPC-010**）；**不含**挂号费锚点（**BR-OPC-013**）；可选带价扩展列（含单价、折后应收）；响应携带 **`accountSwitchEnabled`**（**BR-OPC-018**，供 UI 禁用转账户按钮）；出参按收费明细事实一行一条；患者信息栏费用摘要见 **BR-OPC-034** | 读 | BR-OPC-010 / BR-OPC-011 / BR-OPC-013 / **BR-OPC-018** / **BR-OPC-034** | — | ChargeItem / Encounter / Account | — |
| IF-OPC-04 | 点击「转医保」 | 解析医保账户并将本就诊下收费项记账账户批量切换（明细集合与 IF-OPC-03 同口径：**BR-OPC-010/013**；**排除代煎**见 **BR-OPC-022**）；**须双账户**（**BR-OPC-018**） | 写 | BR-OPC-008 / **BR-OPC-018** / **BR-OPC-022** / BR-OPC-009 / BR-OPC-010 / BR-OPC-013 | 收费项关联账户切至医保（代煎除外） | Account / ChargeItem | 队列与明细刷新 |
| IF-OPC-05 | 点击「转自费」 | 解析自费账户并将本就诊下收费项记账账户批量切换（明细集合与 IF-OPC-03 同口径：**BR-OPC-010/013**；**排除代煎**见 **BR-OPC-022**）；**须双账户**（**BR-OPC-018**） | 写 | BR-OPC-008 / **BR-OPC-018** / **BR-OPC-022** / BR-OPC-009 / BR-OPC-010 / BR-OPC-013 | 收费项关联账户切至自费（代煎除外） | Account / ChargeItem | 同上 |
| IF-OPC-06 | 结算窗内触发预结算（打开窗或确认前一步） | 收费项预结算快照→落**草稿**支付流水**主表与从表明细**（从表按账户汇总占位；自费可与同一种医保混结一张 payment，**BR-OPC-020/021**；**`displayAmount`/占位明细现金=BR-OPC-024 权威公式**）→返回展示模型 | 写 | BR-OPC-001～003 / **BR-OPC-020** / **BR-OPC-021** / **BR-OPC-024** / **BR-OPC-035** | 产生草稿支付流水及至少一条支付明细事实；可伴随医保侧预结算登记语义 | Contract / Account / ChargeItem / PaymentReconciliation / 医保结算 | — |
| IF-OPC-07 | 结算窗「确认收费」 | 校验草稿与收费项集合→医保正式结算→更新流水与明细→收费项置已计费→**同步编排**推进药/耗/服务请求与待发发放单（**BR-OPC-015**）→（实付 > 0 时）发票草稿 | 写 | BR-OPC-004～007、**BR-OPC-015**、**BR-OPC-019**、**BR-OPC-024** | 收费项→已计费；支付流水→成功；药/耗 **req：ACTIVE→COMPLETED**；药/耗 **dis：DRAFT→PREPARATION**；诊疗 **ServiceRequest：ACTIVE→COMPLETED**（`RequestStatus` code）；实付 > 0 时发票草稿创建 | 全 1.5 表列域 | 队列与明细刷新 |
| IF-OPC-08 | 收费列表改折扣或刷新待收明细 | 按当前款员折扣率试算勾选/待收行折后价并回填列表「折后价」列 | 读 | **BR-OPC-033**（命中规则与预结算折扣一致；只读） | 无持久化 | ChargeItem / 收费折扣配置 | — |


---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（队列**入选就诊**由 **ChargeItem** 写模型查询与 **OutpatientEncounterContextQueryModel** 条件关联收敛，见 **BR-OPC-014**；队列**标签**仍以 `DM-FIN-002 §6.2 PaymentReconciliationQueryService` 为准，见 **BR-OPC-012**。不单独维护「门诊收费待收患者队列」专用DM §6 查询契约。）


---

## 4. 接口编排说明书（业务步骤）

### IF-OPC-01 门诊收费页初始化


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 收费员进入门诊收费页 |
| **主路径步骤** | 组装并返回收费页所需初始化数据（至少含收费项状态下拉选项：计划/可计费/已计费等展示码与文案） |
| **关键分支** | — |
| **失败分支** | 初始化数据不可用（EX-OPC-101） |
| **查询侧说明** | 无 |


---

### IF-OPC-02 待收患者队列查询（含筛选与刷新）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 输入姓名关键字、挂号/收费日期、队列状态（全部/待收/已结）或点击刷新 |
| **主路径步骤** | **①** 按 **BR-OPC-014** 分页查询就诊主键：仅保留存在 **PLANNED/SETTLED** 且非 `adm_healthcare_service` 锚点收费明细之就诊；关键字/日期过滤与 **OutpatientEncounterContextQueryModel** Q-OPI-001 语义一致；**不按**会话机构收窄（**B.6.5**）；在查询层与QueryService关联。**②** 按页内就诊顺序回补 **OutpatientEncounterContextQueryModel** 展示字段。**③** 可选：装配与本页就诊同口径的收费明细摘要（供列表区展示）。**④** 对当前页每条就诊按 **BillingStatement** 投影打 **BR-OPC-012** 标签（待收费/已结算）。**⑤** 若 UI 选择页签「待收/已结」，在应用层按标签过滤当前页记录 |
| **关键分支** | 页签过滤仍在应用层执行时，分页 total 与当前页展示条数口径以实现对齐说明为准（与历史行为一致） |
| **失败分支** | 查询失败（EX-OPC-102） |
| **查询侧说明** | 列表依赖 **OutpatientEncounterContextQueryModel** 行；待收/已结标签依赖 **BillingStatement**；IF-OPC-06/07 后两QueryService随支付/收费相关事件刷新（见两QueryService §4.2） |


---

### IF-OPC-03 就诊待收明细加载


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已在队列中选定一条就诊 |
| **主路径步骤** | 按就诊标识查询收费项行：**查询层**同时落实 **BR-OPC-010**（仅 `PLANNED`、`SETTLED`）与 **BR-OPC-013**（排除 `service_table=adm_healthcare_service`）；不包含草稿；装配收费状态展示文案；**项目名称与费用分类展示**优先按 `adm_charge_item.product_table`/`product_id` 解析三大目录QueryService（`query_product_definition`）；若明细未持久化产品锚点，回落 `adm_charge_item_definition.instance_table`/`instance_id`；再回落定价 `charge_name`/`type_code`。**就诊类型**按收费行绑定诊断有序串首位之医保医疗类别（`med_type` / `YbMedType`）装配 code 与展示文案，供收费项目列表「就诊类型」列。**收费人**仅以 `adm_charge_item.enterer_id` 及医务人员主档解析。**若界面需要应收/实收/优惠与折扣率**，带价扩展查询与基础列表同一状态白名单，并在服务端计算折扣率展示（**BR-OPC-011**）；行上同时回填折后应收（收费项 `discount_amount`）供患者信息栏「已收/折扣」（**BR-OPC-034**）与已结算「折后价」列。响应含行级单价供收银台「单价」列（置于金额列前）。出参按收费明细事实一行一条（含 FIFO 跨批分段，不做展示合并）。响应 **`accountSwitchEnabled`**：双账户门禁为 true 时允许 IF-OPC-04/05（**BR-OPC-018**） |
| **关键分支** | 仅展示不含金额列时可走基础明细口径；与带价口径不得混用导致同一列语义漂移 |
| **失败分支** | 就诊无效或无行（EX-OPC-103） |
| **查询侧说明** | 无强依赖QueryService；以写模型联查结果刷新中间区与表格 |


---

### IF-OPC-04 自费转医保


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定就诊 |
| **主路径步骤** | 解析该就诊下医保账户标识；将就诊维度收费项记账账户批量切换为医保账户（参与切换的集合与 IF-OPC-03 同口径：**BR-OPC-010/013**；**排除代煎**，**BR-OPC-022**）。**前置**：**BR-OPC-018** 双账户门禁通过 |
| **关键分支** | 学生场景若产品启用，与学生医保账户解析口径一致（本期 UI 未单列按钮） |
| **失败分支** | 非双账户就诊（**EX-OPC-210**）；未解析到医保账户（EX-OPC-201）；账户切换落库失败（EX-OPC-202） |
| **查询侧说明** | 明细区与队列相关金额/账户展示刷新 |


---

### IF-OPC-05 医保转自费


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定就诊 |
| **主路径步骤** | 解析该就诊下自费账户标识；将**用户勾选**的收费项（请求可带 `chargeItemIds`；空则整批可切换行）记账账户切换为自费账户（参与集合与 IF-OPC-03 同口径：**BR-OPC-010/013**；**排除代煎**，**BR-OPC-022**）。**前置**：**BR-OPC-018** 双账户门禁通过 |
| **关键分支** | 学生场景同理；未勾选时前端应提示先选明细 |
| **失败分支** | 非双账户就诊（**EX-OPC-210**）；未解析到自费账户（EX-OPC-201）；账户切换失败（EX-OPC-202） |
| **查询侧说明** | 同上 |


---

### IF-OPC-06 门诊预结算


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选收费项集合非空；介质类型与用卡信息按机构策略可选传入 |
| **主路径步骤** | 加载收费项→按各行 **`encounterDiagnosisIds`** 加载 **`adm_encounter_diagnosis`** 并校验绑定完整（及同批 **med_type** 一致）→可选费用性质合同锚点→**折扣试算（BR-OPC-027/028/030，未选率/医保零侵入）**→落库收费项预结算快照→汇总应收→**持久化草稿态支付流水主表**（就诊、患者、经办人、收费项 ID 串、展示/实收金额、**contract_no：显式合同或收费项账户 contract_no 聚合，见 BR-OPC-017**）→**按收费项记账账户汇总持久化支付从表明细**（占位渠道与草稿结果态，与应收合计一致）→返回支付草稿标识与弹窗预览（预览行**单价/金额为原价**，打折时另给折后应收与折扣率，不得把折后价写进单价列） |
| **关键分支** | 收费项未显式绑定记账账户时，回落为就诊默认自费账户；多账户并存时从表多行、每行对应一账户金额小计；**未选折扣率或纯医保批不走打折、不拦收费** |
| **失败分支** | 未选收费项；诊断缺失或关联不完整；医疗类型不一致；**同批多种第三方医保合同**（**EX-OPC-309**）；账户与就诊不匹配；**无法解析结算账户（含未开立默认自费账户）**；合同缺失；套餐子项解析或预结算核心失败（**EX-OPC-301～307、EX-OPC-308**） |
| **查询侧说明** | 无；产生草稿支付事实供 IF-OPC-07 消费 |


---

### IF-OPC-07 门诊正式结算（确认收费）


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已存在 IF-OPC-06 产生的草稿支付流水；用户确认实付拆分与介质信息 |
| **主路径步骤** | 按支付流水主键加载头与从表明细→校验头为草稿态→**校验从表明细非空且金额合计与所选收费项一致**→校验请求收费项 ID 串与头中绑定完全一致→扫描该就诊历史成功支付流水，**禁止**任一收费项重复出现在成功链路→按请求中各渠道支付拆分金额在统一刻度下与收费项应收合计比对相等→再次加载收费项并校验未处于已计费态→**若 payment.contract_no 为空则按 BR-OPC-017 从收费项账户补写**→（含扫码时）**先对整笔现金扫码回执挂接并替换为可落库渠道**→**再将草稿现金账户分桶与现金渠道回执对齐（医保基金/个账分项若已带账户则不参与条数比对；多账户合并一笔现金实付时按草稿金额拆行）**→按药品请求汇总追溯码上传计数（按包装单位与拆零比规则进一取整累加）→按账户+交易批次分组调用核心**正式**医保结算→回写结算批次标识与结果态→更新支付头为成功且完结态→以收银回执（已含记账账户）**覆盖**写入渠道支付明细行→可选写入账户余额快照行→**按 BR-OPC-026 从药品请求（或发放→药嘱）抄写收费行处方号快照；诊疗仅 AR 申请单号，代煎 companion 不盖方号**→批量将收费项更新为已计费并**写入收费人/收费时间 `enterer_id`/`entered_date`（BR-OPC-016）**→**按 BR-OPC-015 推进本次结算涉及的 MedicationRequest / MedicationDispense / DeviceRequest / DeviceDispense / ServiceRequest 状态**→持久化付款静态快照→创建发票草稿头→返回成功语义 |
| **关键分支** | 多账户多批次时分组多次调用正式结算；**混结（BR-OPC-020）**下医保分项可先写入回执且已带记账账户；扫码确认须在现金账户拆行之前（保证仅一条扫码明细且金额=整笔现金应付）；草稿为多现金账户而收银仅提交一条合计现金实付时，**仅对缺账户的现金回执行**按草稿各账户金额拆行、渠道与结果态沿用该笔模板（已为扫码解析后的语义码），医保分项原序保留；缺账户现金行条数与草稿现金分桶一致时可按序对齐并校验各行金额 |
| **失败分支** | 头不存在、非草稿、**从表明细空或草稿金额与收费项不一致**、收费项集合不一致、重复收费检测命中、金额不等、已计费重复提交、账户缺失、医保正式结算失败等（EX-OPC-401～410） |
| **查询侧说明** | 队列与明细、票据与对账相关读侧异步或同步刷新 |


---

### IF-OPC-08 收费列表折后价试算


| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已加载待收明细；已配置有效折扣内容；款员可改列表上的折扣 |
| **主路径步骤** | 按就诊校验收费项归属→`ChargeDiscountApplySupport#previewBatch` 按 **BR-OPC-028** 试算：仅自费+已选率+命中配置的行改价；其余行折后=原价→返回行级折后价与优惠合计 |
| **关键分支** | 未选折扣率：全部原价（与正常结算相同）；医保行始终原价；未配置有效折扣不得携带折扣率（**BR-OPC-027**） |
| **失败分支** | 就诊无效、收费项缺失或不属于当前就诊、折扣率越界或未配置有效折扣却传率 |
| **查询侧说明** | **不写库**；正式收费仍走预结算/正式结算折扣入参 |


---

## 5. 业务异常与用户提示


| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-OPC-101 | 初始化失败 | 刷新页面或稍后重试 | 是 |
| EX-OPC-102 | 队列查询失败 | 缩小条件或稍后重试 | 是 |
| EX-OPC-103 | 明细加载失败 | 重选就诊或刷新 | 是 |
| EX-OPC-201 | 目标账户不存在 | 提示核对患者费用性质与账户开立 | 否 |
| EX-OPC-210 | 就诊仅单一记账账户（纯自费挂号等） | 提示当前就诊不支持转医保/转自费 | 否 |
| EX-OPC-202 | 账户切换失败 | 提示稍后重试或联系管理员 | 视原因 |
| EX-OPC-301 | 未选择收费项 | 先勾选待收项 | 是 |
| EX-OPC-302 | 收费项缺少就诊诊断 | 回到医生站补录诊断后再收 | 否 |
| EX-OPC-303 | 就诊诊断数据缺失 | 刷新或核对数据完整性 | 是 |
| EX-OPC-304 | 医疗类型不一致 | 拆分批次或修正诊断医保类型 | 否 |
| EX-OPC-305 | 账户与就诊不匹配 | 重新选择就诊或刷新 | 是 |
| EX-OPC-306 | 费用性质合同缺失 | 核对合同主数据 | 否 |
| EX-OPC-307 | 套餐解析或预结算失败 | 按提示修正后重新预结算 | 视原因 |
| EX-OPC-308 | 预结算无法解析结算账户（收费项未绑账户且就诊未开立默认自费账户等） | 先开立就诊账户或核对收费项记账账户后再预结算 | 否 |
| EX-OPC-309 | 同批勾选了不同第三方医保费用性质合同（**BR-OPC-021**） | 请分次结算，每次只勾选同一医保费用性质（可与自费混结） | 否 |
| EX-OPC-310 | 医保读卡人与当前就诊患者不一致（身份证号优先，否则姓名；**BR-OPC-035**） | 重新读取**当前患者**的医保电子凭证/社保卡/身份证后再收费 | 是 |
| EX-OPC-401 | 支付流水不存在或非草稿 | 重新预结算 | 是 |
| EX-OPC-402 | 支付草稿从表明细缺失或草稿侧金额与收费项不一致 | 重新预结算以重建主从数据；若仍失败联系管理员排查历史脏数据 | 是 |
| EX-OPC-403 | 收费项集合与草稿不一致 | 回到列表刷新后重新预结算 | 是 |
| EX-OPC-404 | 正式结算前检测到：该就诊下 **支付成功** 或 **退款处理中** 流水的 `charge_item_ids` 与本次拟收收费项集合 **存在交集**（`PaymentReconciliationQueryService#findPaySuccessOrRefundingOverlappingChargeItems`，对齐 BR-OPC-005） | 若属重复勾选已结项：刷新列表后勿重复提交；若认定不应重叠：核对 `fin_payment_reconciliation.charge_item_ids` 与收费项主键 | 否 |
| EX-OPC-405 | 实付金额与预结算现金应付不一致 | 调整渠道金额或重新预结算 | 是 |
| EX-OPC-406 | 已计费重复提交 | 刷新页面，勿重复点击 | 否 |
| EX-OPC-407 | 正式结算医保失败 | 按医保返回语义处理；必要时人工 | 视原因 |
| EX-OPC-408 | 账户信息缺失 | 核对就诊账户 | 否 |
| EX-OPC-409 | 通用操作成功（账户切换等） | 成功提示 | — |
| EX-OPC-410 | 收费成功 | 成功提示并可打印票据（另详设） | — |


---

## 6. 事件语义与消费方

> 事件英文名与载荷以各 DM §5 为准；此处仅标收费场景触发相位。


| 事件语义（自然语言 / 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 收费项状态向已计费迁移 / 计费完成类语义 | IF-OPC-07 | **OutpatientEncounterContextQueryModel** 费用摘要、门诊收费队列与明细、对账与报表QueryService |
| 支付草稿 / 流水成功 / 明细结果态更新（含正式结清） | IF-OPC-06 / IF-OPC-07 | **BillingStatement** 行插入或状态更新；**OutpatientEncounterContextQueryModel** 已支付金额等摘要；支付查询、财务对账、票据打印 |
| 发票草稿开立 | IF-OPC-07 | 票据QueryService、开票服务 |
| 药品/耗材/服务请求完成及发放单 **DRAFT→PREPARATION**（门诊收费）；对应领域事件驱动医嘱全景、发药单等QueryService | IF-OPC-07 | 药房/耗材发药队列、医嘱全景与发药单QueryService、执行科室工作台 |
| 账户类型切换成功（无专门领域事件时由写模型变更驱动投影） | IF-OPC-04 / IF-OPC-05 | 收费明细展示、队列费用性质摘要 |


---

## 7. 非功能性需求


| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-OPC-02 ≤1000ms；IF-OPC-03 ≤1200ms；IF-OPC-06 ≤3000ms（含医保 RTT）；IF-OPC-07 ≤4000ms；账户切换 ≤800ms |
| **幂等** | IF-OPC-07 对同一草稿流水重复提交应返回已处理或明确拒绝，不得双记成功支付；IF-OPC-06 短时重复应可合并为同一草稿策略由详设约束 |
| **并发** | 两窗口同一就诊并发收费时，以后到者校验失败为主（重复收费项检测、已计费门控） |
| **重试** | 预结算/正式结算失败由人工确认后重试；禁止无提示自动连发 |
| **一致性** | 正式结算对外以「支付成功 + 收费项已计费 + 关键请求状态推进」为业务成功判定；票据失败策略按机构配置在详设展开 |


---

## 8. 测试要点（业务视角）


| 类别 | 要点 |
| --- | --- |
| **正向** | 筛选队列→点选→加载明细→转自费/转医保→预结算→多渠道拆分→正式收费成功；**正式结算请求仅含渠道与金额、草稿为单账户时仍能落库成功**；药品行追溯码计数边界（整包/拆零） |
| **异常** | 未选收费项预结算；诊断缺失；医疗类型混用；**预结算账户不可解析（EX-OPC-308）**；草稿与正式请求收费项不一致；**草稿从表为空（EX-OPC-402）**；金额不等；重复收费检测；已计费重复提交 |
| **规则** | BR-OPC-004～007 组合；BR-OPC-012（OutpatientEncounterContextQueryModel + **BillingStatement**）；账户缺失提示 |
| **并发** | 双开对同一就诊同时正式收费 |


---

## 8.1 扩展：BPC 扫码支付（可选 Module）

> **真源**：[01-概要设计](../../模块/zxplat-module-unionpay-bpc/01-概要设计.md) · [模块/zxplat-module-unionpay-bpc/03 §3.10～§3.13](../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md) · **成码**：[03 DEP-CHG-01](../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md)

| 维度 | 说明 |
| --- | --- |
| **适用 IF** | **IF-OPC-06/07** 预结算与正式结算 |
| **触发** | chargeDialog **「扫码支付」**；`ThirdPartyPaymentExtension.isEnabled()` |
| **前端（通道 C）** | IF-OPC-06 草稿后：`prepareConsumerScan` → Win consumer-scan → IF-OPC-07 一次提交 |
| **settle 请求** | `paymentDetails` 扫码行 **`SCAN_PAY`** + 顶层 **`clientScanResult`** |
| **编排** | IF-OPC-07 AppService **内** `applyClientScanResult` → 语义码落库 → 领域 settle |
| **双存** | BPC 原值 → `upc_bpc_order`；HIS `pay_enum` **WECHAT/ALIPAY/BANK_CARD**（[05 §6](../../模块/zxplat-module-unionpay-bpc/05-BPC接口对照表.md)） |
| **混合支付** | `CASH` / 手工 `WECHAT`/`ALIPAY` + 银联被扫（BPC 决定渠道）；同一 payment 多明细 |
| **医保** | **先 CHS confirm → 再 Win consumer-scan（自付段）→ settle**；CHS 失败不扫码 |
| **失败** | EX-OPC-4xx：BPC 失败不 settle，可重扫/改现金 |
| **半成功** | BPC SUCCESS + payment DRAFT → 重传 `clientScanResult` 补偿；禁止重复 C；**禁止**预结算/签退路径裸作废该草稿（**BR-OPC-025**），须补结算或先银联退货 |

---

## 8.2 扩展：医保集成编排（zxplat-infrastructure）

> **规范真源**：[integration-assembler-orchestration.md](../../../../.cursor/skills/模块代码规范/references/integration-assembler-orchestration.md) · **Module 样板**：`zxplat-module-chs-jilin` · **CHS v3**：[17-zxplat-module-chs-jilin-架构-v3.md](../../../模块/zxplat-module-chs-jilin/17-zxplat-module-chs-jilin-架构-v3.md) §3.1

| 维度 | 说明 |
| --- | --- |
| **适用 IF** | **IF-OPC-07** 正式收费（含医保混合结算）；部分 confirm 失败时的补偿 |
| **路径 A（落账后 2601）** | 本地 `settleOutpatientPayment` 成功并 **事务提交后** → `InsurancePaymentSettlementExtension#onPaymentSettled` → bridge `Op2601Invoker` |
| **路径 B（收费补偿 2601）** | IF-OPC-07 医保 confirm **部分成功**时 → 对已落库正式结算逐条 2208；2208 失败且存在 2207 msgid → 2601；仍失败 → **INS-013** |
| **health 落点（目标态）** | `zxplat-infrastructure/.../integration/financial/orchestration`（落账编排）；`…/insurance/settlement/assembler` + `…/orchestration`（拼装 + afterCommit）；`…/insurance/outpatient/orchestration`（补偿） |
| **AppService 边界** | 正式结算入口调 **落账 orchestration**；confirm 异常分支调 **补偿 orchestration**；**禁止** AppService 内联 afterCommit / 2601 字段拼装 |
| **存量** | 编排已落 `com.zxplat.domain.infrastructure.integration`（模块 `zxplat-infrastructure`）；命名目标 `*Orchestration` / `*Assembler`（禁止新增 `*ExtensionSupport`） |

---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档 |
| --- |
| `docs/领域/DomainModel/DM-ADM-001-Patient-患者.md` |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `docs/领域/DomainModel/DM-ADM-009-Account-就诊账户.md` |
| `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md` |
| `docs/领域/DomainModel/DM-ADM-012-Invoice-票据发票.md` |
| `docs/领域/DomainModel/DM-FIN-001-Contract-费用性质.md` |
| `docs/领域/DomainModel/DM-FIN-002-PaymentReconciliation-支付流水.md` |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `docs/领域/DomainModel/DM-MED-003-MedicationRequest-药品申请.md` |
| `docs/领域/DomainModel/DM-WKF-003-DeviceRequest-耗材请求.md` |
| `docs/应用/zxplat-application/AppService/AS-OP-FIN-001-门诊挂号.md |
| `docs/模块/zxplat-module-unionpay-bpc/01-概要设计.md`（BPC 扫码支付扩展，§6） |
| `docs/.cursor/skills/模块代码规范/references/integration-assembler-orchestration.md`（health 侧 assembler/orchestration） |
| `docs/模块/zxplat-module-chs-jilin/17-zxplat-module-chs-jilin-架构-v3.md`（Module 样板 v3） |
| `docs/应用/zxplat-application/AppService/AS-OP-DOR-003-门诊医生站医嘱开立.md |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` |
| `DM §6.2（DM-FIN-002 §6.2 PaymentReconciliationQueryService` |


### 9.2 变更历史


| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.47 | 2026-10-16 | **BR-OPC-024** 前移到 IF-OPC-06：预结算 `displayAmount`/草稿占位现金与落账同用 `Σ psn_cash + max(0, 收费合计−Σ medfee)`，修复「医保户未进 medfee 差额」弹窗少收、小票抬高微信；前端待收提示跟 `displayAmount`；代码先行回写 |
| v1.46 | 2026-10-12 | **BR-OPC-027/028/030/033**：打折三门禁——仅自费+已选折扣率+命中配置才折；未选率/医保零侵入不拦收费；代码先行回写 `ChargeDiscountApplySupport` |
| v1.45 | 2026-10-10 | **BR-OPC-035 / EX-OPC-310**：医保读卡人须等于就诊患者（防串账）；IF-OPC-06 请求增 `payerCertno`/`payerPsnName`；队列行回填 `idCard`；代码先行回写 |
| v1.44 | 2026-10-06 | 折扣显隐只认是否已配置 ACTIVE 折扣内容；IF-OPC-08 触发前提去掉「本院启用」表述 |
| v1.43 | 2026-10-06 | **IF-OPC-06**：预览行单价/金额为原价，折后应收单独回填（代码先行回写） |
| v1.42 | 2026-10-06 | **BR-OPC-034 / IF-OPC-03**：行回填折后应收；患者信息栏已配置有效折扣时待收=原价合计、已收=折后实收、展示折扣额（代码先行回写） |
| v1.41 | 2026-10-06 | **BR-OPC-033**：有效率口径纠偏——配置率 ∈ (0,1) 才算固定优惠；空率或 1（10 折）走款员定率；未命中配置的行不得折（代码先行回写） |
| v1.40 | 2026-10-06 | **IF-OPC-08 / BR-OPC-033**：收费列表折后价只读试算（代码先行回写）；命中规则与预结算折扣一致，不写快照 |
| v1.39 | 2026-09-28 | **收费/挂号小票个账余额纠偏**：2207 `balc` 实测为结算后，小票原样展示，**不再**减 `acct_pay`（代码先行回写）；不改落库 `yb_settlement_record.balc` |
| v1.38 | 2026-09-22 | **收费/挂号小票个账余额**：展示本笔扣款后剩余（`InsuranceReceiptAccountBalanceSupport`：当时误按结算前减个账；**v1.39 已纠偏**）；不改落库 `yb_settlement_record.balc` |
| v1.37 | 2026-09-06 | **BR-OPC-026** 纠偏：诊疗侧仅 **`AR`** 申请单号可盖收费快照；代煎 companion **不**从 SR 抄 `CP`/`TCP`；对齐 `OutpatientChargePrescriptionSnapshotStamper` / **AS-OP-DOR-010 · BR-TCM-RX-009** |
| v1.36 | 2026-09-06 | **BR-OPC-026**：正式结算前从药品/诊疗请求抄写 `ChargeItem.prescription_no` 快照；签发挂费不预写；药品真源 `MedicationRequest`；对齐 **DM-ADM-011 v1.6.19** |
| v1.35 | 2026-08-31 | **BR-OPC-025**：半成功（BPC SUCCESS）支付草稿禁止预结算裸作废；结算后清草稿跳过保留；对齐 `PaymentDraftHalfSuccessAbortGate` / unionpay-bpc 半成功补偿 |
| v1.34 | 2026-08-31 | **BR-OPC-024** 增补终态勾稽：IF-OPC-07 在 `applyScanBeforeSettle`、`attachDraftAccountsToPayReceiptDetails` 之后再次校验明细合计=收费项（纯自费/混结共用） |
| v1.33 | 2026-08-26 | **BR-OPC-024**：医保落账勾稽——自付=Σpsn_cash(+自费段)、全明细=收费项合计；对齐 `InsuranceSettlementCashAlignSupport`；防日结渠道虚高（如微信误含 fund） |
| v1.32 | 2026-08-22 | **BR-OPC-023**：正式结算 Gate 校验药/耗发放行签发批号已锁定（含 `dispense_*` 锚点）；对齐 **BR-ORD-033** / **AS-SDD** |
| v1.31 | 2026-08-04 | **BR-OPC-016**：结算写 `enterer_id`/`entered_date`（收费人/收费时间），不再写 `performer_id` 为收款人；对齐 **DM-ADM-011 v1.6.14**；存量库须跑 health Flyway **V15** 回填 |
| v1.25 | 2026-08-02 | **BR-OPC-022 纠偏**：代煎自费仅账单侧（展示覆盖/预结算拆分/跳过转账户），**禁止**动 Account；对齐 **AS-OP-DOR-010 v1.14**、**AS-SDD v1.22** |
| v1.24 | 2026-08-02 | **混结与代煎费用性质**：新增 **BR-OPC-020/021/022**、**EX-OPC-309**（v1.25 纠偏代煎不动 Account） |
| v1.23 | 2026-08-01 | **撤回展示合并**：IF-OPC-03 按收费明细事实一行一条；收银台仅增「单价」列（金额列前）；删除当时「展示合并」语义的 **BR-OPC-020**；对齐 **AS-SDD v1.20** |
| v1.22 | 2026-08-01 | （已撤回）曾尝试 BR-OPC-020 合并键去掉 busNo |
| v1.30 | 2026-08-03 | **IF-OPC-07**：扫码挂接须在现金账户分桶拆行之前（整笔现金校验 + 仅一条扫码明细），再按草稿账户拆行；与 v1.29 混结回填口径配套 |
| v1.29 | 2026-08-03 | **IF-OPC-07**：混结正式结算账户回填口径——仅对齐缺账户的现金渠道回执与草稿现金分桶；已带账户的医保基金/个账分项不参与条数比对（修复代煎自费+医保+银联误报 EX-OPC-405） |
| v1.28 | 2026-08-02 | **BR-OPC-020** 交叉引用日结 **AS-OP-FIN-004 · BR-OP-DCL-027**（混结读侧按账户拆桶） |
| v1.27 | 2026-08-02 | **BR-OPC-022**：代煎签发挂个人现金自费户（对齐双账户/转自费）；去掉收银费用性质硬盖；与 **AS-OP-DOR-010 v1.16** 对齐 |
| v1.26 | 2026-08-02 | **IF-OPC-05**：支持勾选 `chargeItemIds` 转自费；依赖挂号双账户（个人现金自费 + 医保户）；统计/展示仍跟账户 `contract_no`。对齐 **AS-OP-FIN-001 v2.33**、**DM-ADM-009 v1.3** |
| v1.21 | 2026-08-01 | （已撤回）曾尝试 IF-OPC-03 同价同品展示合并 |
| v1.20 | 2026-08-01 | IF-OPC-03：待收明细展示**就诊类型**（`med_type` / `YbMedType`）；对齐 **AS-SDD v1.17** |
| v1.19 | 2026-07-18 | **DD §11.7**：预结算诊断改读 **`ChargeItem.encounterDiagnosisIds`**；移除 **Condition** 协同叙事；对齐 **AS-SDD v1.16** |
| v1.18 | 2026-07-16 | **BR-OPC-019**：零元正式结算跳过开票，对齐 **BR-INV-008** / `InvoiceAfterPaymentOrchestration` |
| v1.17 | 2026-07-10 | **§8.2**：health 侧 Integration `assembler` / `orchestration`；2601 路径 A/B；对齐 `zxplat-module-chs-jilin` v3 |
| v1.16 | 2026-07-04 | **BR-OPC-018**：转医保/转自费须双账户（默认自费+医保）；纯自费挂号拒绝 **EX-OPC-210**；IF-OPC-03 响应 `accountSwitchEnabled` |
| v1.15 | 2026-07-04 | **IF-OPC-05 医保转自费 UI 接通**：门诊收费页「转自费」批量将待收明细 `account_id` 切至就诊默认自费账户，后续预结算/正式结算走自费账户；「转医保」对称 IF-OPC-04；失败语义 EX-OPC-201/202/409 |
| v1.14 | 2026-06-24 | **BR-OPC-017**：IF-OPC-06/07 写侧补全 payment `contract_no`（显式合同或收费项账户聚合；混合结算可为空）；对齐 **DM-FIN-002** 日结费用性质筛选口径 |
| v1.13 | 2026-06-24 | **BR-OPC-016**：`settleChargeItems` 写侧落库 `performer_id`（收款人，与 payment `enterer_id` 同源）；IF-OPC-07 主路径与 **DM-ADM-011** §6.1.6 同步 |
| v1.12 | 2026-06-23 | §8.1：SCAN_PAY + clientScanResult；AppService 内 applyClientScanResult；对齐 03 §3.13 |
| v1.11 | 2026-06-23 | §8.1：「扫码支付」文案；payType 双存；CHS confirm 后 BPC；半成功补偿 |
| v1.10 | 2026-05-28 | 六维对账：七方法与 SDD §3.1 对齐；**BR-OPC-015** 实现为 App 私有 `#syncAdviceStatesAfterChargeSettlement` |
| v1.9 | 2026-05-28 | ReadModel 废止后对齐 zxplat 实现 |
| v1.8 | 2026-05-10 | 队列与 **OutpatientEncounterContextQueryModel** Q-OPI-001 对齐《代码生成通用规范》**B.10.11**：收银队列由 **`OutpatientChargeEncounterQueueLoader`** 分页（**不**按会话机构收窄）；**禁止**将 `organizationId` 作为 IF-OPC-02 HTTP 入参；**BR-OPC-014**、§1.1、§4 IF-OPC-02 主路径同步 |
| v1.7 | 2026-05-09 | **BR-OPC-015** / IF-OPC-07：**ServiceRequest** 对齐实现为 **ACTIVE→COMPLETED**（`RequestStatus` 持久化 **code**，非 sortOrder 数字）；明确收费侧**同步编排**与领域事件载荷关系；IF-OPC-07 契约表「关键状态变化」同步 |
| v1.5 | 2026-05-08 | 门诊收银队列：**BR-OPC-014**（收费事实分页准入）+ **BR-OPC-012** 标签拆分表述；**BR-OPC-010** 收窄为 PLANNED/SETTLED、不含草稿；**BR-OPC-013** 覆盖队列摘要；新增 **BR-OPC-014**；IF-OPC-02/03/04/05、§1、§3、§4 主路径与契约表同步 |
| v1.4 | 2026-05-08 | §1.3「预结算」与 IF-OPC-07：明确正式结算整表替换从表时，须由应用层将草稿明细上的记账账户并入渠道回执（本期 UI 请求可不携带账户）；§8 增补正向要点 |
| v1.3 | 2026-05-08 | IF-OPC-06：明确预结算须落支付从表明细（按账户汇总占位）及失败码 **EX-OPC-308**；IF-OPC-07 / §5：澄清 **EX-OPC-402** 为从表明细缺失或草稿金额与收费项不一致；§1.3 术语「预结算」与 §8 异常用例对齐实现 |
| v1.2 | 2026-05-04 | 与页眉版本对齐：显式 **BillingStatement** 命名；§1.3「待收/已结」、BR-OPC-012、IF-OPC-02 编排及 §8 规则用语与DM §6 查询契约路径一致 |
| v1.1 | 2026-05-04 | IF-OPC-02：待收队列为 **OutpatientEncounterContextQueryModel** + **BillingStatement**（BR-OPC-012）；删除 Gap-OPC-001；§3 无缺口；§6/§9 关联QueryService |
| v1.0 | 2026-05-04 | 首版：对齐门诊收费工作站 UI 与既有门诊收费/付款编排；IF-OPC-01～07 |


---

**文档负责人**：（待填） · **评审**：（待填）
