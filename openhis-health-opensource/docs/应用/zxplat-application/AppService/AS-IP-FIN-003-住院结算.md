# 住院结算 · 应用服务设计（AS）

> **文档职责**：说明住院收费「出院/在院结算」工作台（`/inpatient/charge/settle`）的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院结算页——检索/刷新**待结算或已结算**患者 → 选中后展示患者头、金额摘要（含预交金与差额**只读提示**）、结算专用费用 Tab（大类汇总/明细清单，**默认全选待结算**）→ **预结算** → 录入支付方式 → **确认结算**；已结算患者可查阅结算单快照。**本期仅出院全结，无中途结算**。
>
> **覆盖声明**：完整契约以 **§2 IF-IP-STL-01～07** 为准；写侧编排对齐既有住院预结算/结算业务节奏（支付草稿 `DRAFT` → 住院支付结算收口 → 收费项结算 → 发票草稿 → **出院全结预交金轧差 P/O/C 与原路退**）；医保平台协议细节在 **CHS 扩展**（见 `docs/模块/zxplat-module-chs-jilin/04`），本 AS 约定扩展钩子下的分项回填口径；**住院反结**见 **AS-IP-FIN-004**（**同 App 包** `inpatient.financial.settle`，**独立 IF**）。
>
> **编码**：AS-IP-FIN-003 · **版本**：v1.22 · **日期**：2026-08-17  
> **文档链路**：AS → `AS-IP-FIN-003-住院结算-SDD.md` → App `inpatient.financial.settle` | DM `DM-ADM-002` §6.1.23 / `DM-ADM-011` §6.1.9b·§6.1.12 | `InpatientEncounterContextQueryModel` **Q-IPI-007** | 领域事件 `InpatientFinancialSettlementCompletedEvent` | **出院召回**见 **AS-IP-FIN-004** **IF-IP-RCL-01**（同 App 包）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | **本期** IF-IP-STL-01～07：页初始化；结算患者队列（**凡有待结算项可见** + **已结算可查阅**）；选中患者结算摘要；结算专用费用查询（明细/大类汇总、可勾选、默认全选待结算）；住院预结算（落 `INPATIENT_CLINIC` 支付草稿）；确认结算（渠道实收对齐、收费项结算、**发票草稿**、条件推进就诊 `COMPLETED`）；已结患者按结算单查阅费用快照 |
| **在外** | 住院登记（**AS-IP-FIN-001**）；预交金续收/退费（**AS-IP-FIN-002**）；护士站费用明细 Tab（**AS-IP-NUR-006**，只读展示口径不同）；**住院反结**（**AS-IP-FIN-004**，已结算 Tab 按支付单操作，**独立 AS、同 App 包**）；医保平台报文/撤销细节与费别切换（**CHS**；本 AS 仅约定扩展钩子金额口径）；电子发票开具与三类打印；住院日结（**AS-IP-FIN-005**）；第三方支付协议细节 |
| **涉及域** | Encounter（出院全结时 `PENDING→COMPLETED`）；ChargeItem（待结算项勾选与 `SETTLED`）；Account（**自费**结算扣本批已结、少补/多退；**医保**不冲抵，原路退清由第二阶段编排）；PaymentReconciliation（`kind_enum=INPATIENT_CLINIC` 草稿与成功流水；自费多退 / 医保 LIFO 原路退 `HOSPITAL_DEPOSIT`）；Invoice（支付后发票草稿）；ProductDefinition / Procedure（费用展示组装，读侧） |
| **编排权威** | 以本文 **§2、§4** 为准 |
| **上游** | 费用行须经护士执行/划价等路径进入 **`PENDING_SETTLEMENT`（待结算）** 方可被选；出院全结前护士须已将就诊推进至 **`PENDING`（待结算）**（**AS-IP-NUR** 出院执行链） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院收费员** | 检索/刷新队列 → 选中患者 → 核对费用（**须全选**待结算项）→ 预结算 → 确认渠道实付 → **出院全结**；对已结患者查阅/重打占位 |
| **系统** | 校验就诊与收费项门禁；落支付草稿与成功流水；批量推进所选 ChargeItem；出院全结时推进就诊财务终态；发布结算类领域事件供QueryService与日结消费 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **结算队列·待结算** | `IMP` 且 **`status_enum = PENDING`**（护士已办理出院）；**召回后 `IN_PROGRESS`（无活动床）**须再办出院后方可结算；**不**再要求二次分床入队 |
| **结算队列·已结算** | `IMP` 且存在 ≥1 条 **`INPATIENT_CLINIC` 结算流水**（`SUCCESS` 或 `REFUNDED`，与 **IF-IP-STL-07** 历史口径一致）；**与在院/床位/清床/召回态无关**（含 `COMPLETED`、`DISCHARGED`、反结后 `IN_PROGRESS`/`REGISTERED` 等）；列表 **按住院号（`bus_no`）去重**，同一住院号多次结算在 **IF-IP-STL-07 / 结算历史** 区查阅 |
| **默认全结** | 打开待结算患者时，费用区**默认勾选**该就诊下**全部** `PENDING_SETTLEMENT` 行；**禁止部分勾选结算** |
| **中途结算** | **本期不做**（非目标）；`settleMode=MID_SETTLE` 或部分勾选均拒绝 |
| **出院全结** | 就诊须为 **`PENDING`**；本次须勾选该就诊**全部**剩余 `PENDING_SETTLEMENT`；成功后就诊 → **`COMPLETED`**（**有活动床**）或 **`DISCHARGED`**（**无活动床**，召回未占床轨；**跳过**护士清床） |
| **住院结算支付** | `kind_enum = INPATIENT_CLINIC`；`payment_enum = PAY`；预结算落 **`DRAFT`**，正式结算 **`DRAFT→SUCCESS`**（与 **AS-IP-FIN-002**、门诊收费节奏一致） |
| **结算专用费用查询** | **不复用** **AS-IP-NUR-006** 的 IF 编号；读侧可共享 ChargeItem 查询与产品QueryService能力；**仅展示** `PENDING_SETTLEMENT`（待结选中）或结算单快照（已结）；**不含** `PLANNED` / `DRAFT`；默认日期区间为**入院日至当日**（闭区间） |
| **金额摘要** | **待结算合计**、**库内账户余额 D**、**本次选中合计**、自费预估 **P/O/C/应退**（医保须预结算后取准）；`balanceHint` 仅调试；展示余额 = 库内余额 − 待结算 |
| **预交金冲抵与多退少补** | **自费=医保同一套**（**DOC-CONFLICT→已按 DD「住院预交金结算口径纠错」收敛**）：结算 payment 主单金额恒为个人现金应付 **P**；明细冲抵 **O**（`pay_enum=DEPOSIT_OFFSET`）+ 渠道实付 **C**（`P=O+C`）；仅 **O>0** 时 `balance −= O`；有余按原路退（可部分退则退 **D−P**，含银联则全退 **D** + 重收 **P**）。**废除**少补收预交金、自费现金占位多退、医保「永不冲抵一律全退」双轨 |
| **发票** | 正式结算成功后，**票面（收费项合计）> 0** 时创建**发票草稿**；真正零元不开票（**BR-INV-008**；医保全报仍开票）；纸质/电子票开具不在本 AS |
| **IMP 状态** | 完整语义见 **`DM-ADM-002` §2.3.1** |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-IP-STL-01～07；现金/微信/银行卡等渠道；**仅出院全结**（PENDING + 全选待结算）；出院 `PENDING` 全结推进 `COMPLETED` |
| **本期（UI 接通）** | 患者关键字检索与刷新；患者头与五格金额摘要（无医保分列）；费用 Tab（大类/明细，可勾选）；预结算 + 确认结算；已结患者查阅 |
| **非目标（产品/UI）** | 「医保校验」按钮；医保关键信息区、医保结算信息 Tab、减免/补助 Tab（Mock 有，本期不纳入 IF）；费别切换 |
| **非目标（老能力舍弃）** | 老系统 `inpa-un-pay` 口径（ChargeItem→`PLANNED`）；**中途结算**（子集勾选、就诊不变态）；**住院反结**（**AS-IP-FIN-004**）；取消预结算独立按钮；住院队列仅按「待出院结算/已结算出院」旧状态码筛选（新态口径见 §1.3）；医保按合同分组预结算/正式结 |
| **非目标（后续/Gap）** | 医保 2303/2304/2305；三类打印与 `invoiceZYMake` 电票；扫码异步回执补单（可保留扩展位） |
| **非目标（占位）** | 打印住院收费票据/费用总清单/医保结算单：UI 可保留按钮，**无**独立 IF，仅本地提示 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 出院全结门禁与 `PENDING→COMPLETED` | `DM-ADM-002-Encounter-就诊.md` |
| **ChargeItem** | 待结算项查询、勾选结算、`PENDING_SETTLEMENT→SETTLED` | `DM-ADM-011-ChargeItem-收费明细.md` |
| **Account** | 库内账户余额读/写（冲抵占用 O、原路退预交金） | `DM-ADM-009-Account-就诊账户.md` |
| **PaymentReconciliation** | 住院结算支付草稿与成功流水 | `DM-FIN-002-PaymentReconciliation-支付流水.md` |
| **Invoice** | 结算成功后发票草稿 | `DM-ADM-012-Invoice-票据发票.md` |
| **ProductDefinition（读）** | 费用行名称、规格、类目 | `DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService` |
| **Procedure（读）** | 医嘱/执行路径业务时间（费用展示，可选） | `DM-CLN-004` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-STL-001** | 仅 **`IMP`** 就诊可办理结算读/写；`PLANNED` / `CANCELLED` 禁止 | IF-IP-STL-02～07 |
| **BR-IP-STL-002** | 可被选入本次结算的 ChargeItem 须为 **`PENDING_SETTLEMENT`**，且归属当前就诊 | IF-IP-STL-04、05、06 |
| **BR-IP-STL-003** | **出院全结**：就诊 `status_enum = PENDING`，且本次 `chargeItemIds` 等于该就诊**全部**剩余 `PENDING_SETTLEMENT`；成功后 → **`COMPLETED`** | IF-IP-STL-06 |
| **BR-IP-STL-004** | **结算写路径门禁**：**仅** `PENDING` + **全选**待结算可结算；`ON_HOLD` / `IN_PROGRESS` / 部分勾选 / `MID_SETTLE` **一律拒绝**（须先 **AS-IP-NUR-002** 办理出院） | IF-IP-STL-05、06 |
| **BR-IP-STL-018** | **出院全结临床 Pre 门禁**（**BR-IP-FLW-008 v1.2**）：`PENDING` 且判定为出院全结时，预结算/正式结算前须通过 **`InpatientDischargeClinicalGateSupport`**（与 ATD-05 同源：长期未停 EX-IP-ATD-301、临时未完结 EX-IP-ATD-302、待发/待退药 EX-IP-ATD-303、长期待执行时点 EX-IP-ATD-307） | IF-IP-STL-05、06 |
| **BR-IP-STL-005** | 预结算/正式结算的 `chargeItemIds` 集合须与支付草稿绑定集合**逐项一致**；草稿须为 **`DRAFT`** | IF-IP-STL-05、06 |
| **BR-IP-STL-006** | 正式结算：渠道明细金额之和须等于轧差计划 **渠道实付 C**（C=0 时明细可空；**禁止**再与「本批费用合计」或旧少补语义交叉校验）；医保正式落账时支付从表明细由后端写入 **统筹 / 个账 / 冲抵 O / 渠道实付 C**（覆盖草稿占位；**禁止** SUCCESS 残留 `CASHIER`） | IF-IP-STL-06 |
| **BR-IP-STL-006a** | **补收渠道落库硬约束（与门诊同源）**：C>0 时须对**即将落库**的 `payReceiptDetails` 调用 `ThirdPartyPaymentSettleOrchestrator#applyScanBeforeSettle`（`SCAN_PAY`→微信/支付宝/银行卡）；**禁止**另拷一份明细做扫码（否则银联已成功、落库仍为草稿占位）。落库前 Gate：患者补收行须为可展示渠道（`CASH`/`WECHAT`/`ALIPAY`/`BANK_CARD` 等），**禁止** `CASHIER`/`SCAN_PAY`；扫码成功可将银联 `trade_no` 写入补收行 `pay_trans_no` 供查阅回显。违规 → **EX-IP-STL-106** | IF-IP-STL-06 |
| **BR-IP-STL-007** | 正式结算前，拟结 ChargeItem **不得**已为 `SETTLED`；同一就诊已成功 `INPATIENT_CLINIC` 流水**不得**重复挂载同一 chargeItemId | IF-IP-STL-06 |
| **BR-IP-STL-007a** | `settleInpatientChargeItems` 须将 `operatorId`（收费人 `adm_practitioner.id`）与 `billDate`（与 payment `bill_date` 同值）写入各收费明细 `enterer_id`/`entered_date`；**禁止**再写 `performer_id` 为收款人（与门诊 **BR-OPC-016** 同源，见 **DM-ADM-011** §6.1.12） | IF-IP-STL-06 |
| **BR-IP-STL-008** | 结算成功后，**票面（收费项合计）> 0** 时**须**创建**发票草稿**；真正零元（含零元出院全结）跳过开票；医保全报现金为 0 仍开票（**BR-INV-008** / `InvoiceAfterPaymentOrchestration`）；打印不在本 AS。票面锚**费用合计**，禁止锚个人现金 P 或渠道实收 C | IF-IP-STL-06 |
| **BR-IP-STL-009** | **`COMPLETED`** 就诊禁止再发起 IF-IP-STL-05/06，仅允许 IF-IP-STL-07 查阅 | IF-IP-STL-02、07 |
| **BR-IP-STL-010** | 同一就诊同时仅一个有效 `INPATIENT_CLINIC` **`DRAFT`**；再次预结算须作废旧草稿；若旧草稿仍挂未退货银联 BPC `SUCCESS`，**禁止**裸作废（同门诊 **BR-OPC-025** / `CANCEL-BIZ-413`），须补结算或先银联退货 | IF-IP-STL-05 |
| **BR-IP-STL-011** | 队列「待结算」：仅 **`status_enum = PENDING`**；**召回后 `REGISTERED` 不入队**（须二次分床→校对→再办出院） | IF-IP-STL-02 |
| **BR-IP-STL-012** | 费用查询默认区间：**入院日～当日**（闭区间）；排除 `service_table = adm_healthcare_service` 挂号锚点行 | IF-IP-STL-04 |
| **BR-IP-STL-013** | **预交金轧差（自费=医保）**：个人现金应付 **P**（医保=`psnCashPay`，自费=本批应收）；库内余额 **D**；含银联按 `mayRequireUnionPayBpcRefund` 粗判。分支 R1～R7 见 DD：冲抵 **O**、渠道实付 **C**、应退预交金；摘要/预结须展示 D/O/C/应退（**禁止**再用 `balanceHint=D−选中合计` 驱动主路径应付文案） | IF-IP-STL-03、05、06 |
| **BR-IP-STL-014** | **出院全结预交金写路径**：结算 payment **仅 1 张**、主单金额=**P**；明细含冲抵行（O>0）与渠道行（C>0）；**仅 O>0** 时 `adjustAdvanceDepositBalance(−O)`；R3/R6 另落 `HOSPITAL_DEPOSIT` `UN_PAY` **现金**退（**一律现金**，不判原收款渠道；**废除** R4 含银联全退重收）；结后个人现金户余额 **0**。**废除** `collectSettleSupplementAdvanceDeposit` 与自费现金占位多退 | IF-IP-STL-06 |
| **BR-IP-STL-015** | **结算单类型**：本期仅出院结算 `kind_enum = INPATIENT_CLINIC`；**无**中途结算支付大类 | IF-IP-STL-06 |
| **BR-IP-STL-016** | IF-IP-STL-05/06 落支付草稿时须写入 `fin_payment_reconciliation.contract_no`（`fin_contract.bus_no`）：请求显式 `contractId` 优先；否则从所选收费项**记账账户**（自费/医保，非预交金登记户）聚合解析；单账户或唯一 contract_no 时必写；自付与医保同时付费且 contract_no 无法唯一确定时可为空（**DM-FIN-002**）；支付从表明细须按收费项记账账户分桶占位，**禁止**一律挂登记个人现金户；IF-IP-STL-06 正式结算前对历史空 contract_no 草稿须补写后再落成功态 | IF-IP-STL-05 / IF-IP-STL-06 |
| **BR-IP-STL-019** | **住院挂账与结算口径（本期）**：医保入院费用默认全挂 `MEDICAL_INSURANCE`（与 **BR-IP-NUR-018** 同构）；医保转自费停用医保户后，未结费用与后续费用挂个人现金户并按自费结算。个人现金户同时承载**预交金余额**（自费结算冲抵/医保出院原路退清仍读此户）。收费项缺 `account_id` 时回落默认挂账账户（医保优先），**禁止**一律回落自费户 | IF-IP-STL-05 / IF-IP-STL-06；上游写路径 **AS-IP-NUR-004/005/013** |
| **BR-IP-STL-017** | 队列「已结算」：**同一住院号（`bus_no`）仅展示一行**；冲突时保留 **`lastSettleTime` 较新**者；分页 **`totalCount` = 不重复住院号数**；多次结算/反结记录在 **IF-IP-STL-07** 历史列表查阅 | IF-IP-STL-02、07 |
| **BR-IP-STL-020** | **未配置有效折扣禁传率**（与门诊 **BR-OPC-027** 同构）：无 ACTIVE 折扣配置时禁止携带 `cashierDiscountRate`；未传率零侵入 | IF-IP-STL-05 / 06 |
| **BR-IP-STL-021** | **打折三门禁**（与门诊 **BR-OPC-028** 同构）：仅「自费 + 已选折扣率 + 命中配置」才折；未选率/医保行不校验不折价不拦结算 | IF-IP-STL-05 / 06 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-STL-01 | 进入结算页 | 下发初始化：队列模式、支付渠道、`presentationMode`（明细/大类）说明 | 读 | — | 无 | — | — |
| IF-IP-STL-02 | 输入住院号/姓名检索、切换「待结算/已结算」、刷新 | 分页查询结算工作台患者队列 | 读 | BR-IP-STL-001、009、011 | 无 | Encounter / ChargeItem | InpatientEncounterContextQueryModel（+ 待结算标记组装） |
| IF-IP-STL-03 | 选中队列患者（待结算） | 加载结算摘要：患者头、待结算合计、**库内余额 D**、选中合计、自费 **P/O/C** 预估（医保挂账户则空，待 IF-05） | 读 | BR-IP-STL-012、013 | 无 | Encounter / Account / ChargeItem | InpatientEncounterContextQueryModel；Account；ChargeItem 汇总 |
| IF-IP-STL-04 | 切换费用 Tab、勾选/全选待结算行、点「刷新费用」 | 查询结算专用费用（`presentationMode` = 明细 \| 大类；含可勾选待结算行） | 读 | BR-IP-STL-002、012 | 无 | ChargeItem / ProductDefinition | ProductDefinition（读）；ChargeItem 业务表 |
| IF-IP-STL-05 | 预结算（结算前核对后触发） | 按所选 `chargeItemIds` 生成住院结算支付草稿并返回预览金额 | 写 | BR-IP-STL-002、005、010 | 新增 `INPATIENT_CLINIC` `DRAFT` 支付单 | PaymentReconciliation / ChargeItem | 支付草稿预览 |
| IF-IP-STL-06 | 「确认结算」+ 支付方式/金额 | 正式结算：校验草稿与渠道金额 → 扫码替换落库明细 → 支付 `SUCCESS` → 收费项 `SETTLED`（写 `enterer_*`，**BR-IP-STL-007a**）→ 发票草稿；条件 `PENDING→COMPLETED` | 写 | BR-IP-STL-003～008、006a、007a | 所选 ChargeItem→`SETTLED`；出院全结时 Encounter→`COMPLETED` | PaymentReconciliation / ChargeItem / Encounter / Invoice | 队列/摘要刷新 |
| IF-IP-STL-07 | 选中「已结算」队列患者 | 按结算单（支付成功 `INPATIENT_CLINIC`）查阅摘要与绑定费用快照 | 读 | BR-IP-STL-009 | 无 | PaymentReconciliation / ChargeItem | 支付查询读口径；ChargeItem 按单 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

> 领域层已关闭：**G-STL-01** `EncounterQueryService#pageSettleQueue`（Q-IPI-007）；**G-STL-02** `EncounterDomainService#completeInpatientFinancialSettlement` + `InpatientFinancialSettlementCompletedEvent`；**G-STL-03** `ChargeItemDomainService#preSettleInpatientChargeItems` 与 `settleInpatientChargeItems` 待结算门禁。应用层 **`InpatientSettleAppService`**（`com.zxplat.domain.application.inpatient.financial.settle`）**已按 SDD 成码**，**不是** Gap。医保、打印/电票仍为 **§1.4 非目标**。**住院反结/召回**见 **AS-IP-FIN-004**（同包 **`reverseInpatientSettle`** / **`recallInpatientDischarge`** + Pre **`InpatientReverseSettlePreSupport`**）。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-STL-01 结算页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入 `/inpatient/charge/settle` |
| **主路径步骤** | 下发支付渠道枚举；队列模式（**有待结算** / **已结算**）；费用 `presentationMode`：`DETAIL`（明细清单，可勾选）与 `SUMMARY`（大类汇总）；默认队列模式为**有待结算** |
| **关键分支** | 字典与渠道与 **AS-IP-FIN-002**、登记首笔支付口径对齐 |
| **失败分支** | 初始化不可用 → EX-IP-STL-101 |
| **查询侧说明** | 无 |

---

### IF-IP-STL-02 结算患者队列查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入页面、修改关键字、切换队列模式或点刷新 |
| **主路径步骤** | 按关键字（住院号/姓名/床号）、可选入院日筛选 → **`IMP`** → 分支：**有待结算**—`status_enum = PENDING`（**BR-IP-STL-011**）；**已结算**—存在 `INPATIENT_CLINIC` 且 `SUCCESS`/`REFUNDED` 流水（**BR-IP-STL-017**）→ **按住院号去重** → 分页返回卡片（患者摘要、床、科室、病区、**主治医生**、就诊态文案、`lastSettleTime` 等；医护/位置展示口径见 **DM-ADM-002 §6.2.18**：活动优先，清床/召回后回落末次 COMPLETED） |
| **关键分支** | 默认选中第一条待结算患者时联动 IF-IP-STL-03、04；选中已结算行仅联动 IF-IP-STL-07 |
| **失败分支** | 查询失败 → EX-IP-STL-101 |
| **查询侧说明** | 纯展示 |

---

### IF-IP-STL-03 结算摘要（待结算患者）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 在「有待结算」队列选中一名患者 |
| **主路径步骤** | 加载住院患者头信息 → 汇总该就诊 **`PENDING_SETTLEMENT` 合计** → 读取个人现金户 **库内余额 D** → 默认 **选中合计 = 待结算全额** → 组装账户展示文案 → **无医保挂账户**时按 Planner 预估 P/O/C（**BR-IP-STL-013**）；有医保户则 P/O/C 留空待预结算 |
| **关键分支** | 用户于 IF-IP-STL-04 改勾选后，前端重算选中合计与差额；服务端在 IF-IP-STL-05/06 以请求 `chargeItemIds` 为准 |
| **失败分支** | 就诊不存在/非住院 → EX-IP-STL-102；无账户 → EX-IP-STL-103 |
| **查询侧说明** | 纯展示 |

---

### IF-IP-STL-04 结算专用费用查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选待结算患者；切换明细/大类 Tab、调整勾选、点「刷新费用」 |
| **主路径步骤** | 按就诊 + **入院日～当日** 加载 **`PENDING_SETTLEMENT`** 行（**BR-IP-STL-012**；排除挂号锚点）→ 关联产品QueryService补全名称/规格/类目 → **明细模式**：一行一 ChargeItem，含勾选态、单价、数量、行合计、费用状态、来源可读文案 → **大类模式**：按费用大类（药品/诊疗/耗材映射）汇总选中或全部待结算行金额 → 返回合计块（与选中集一致） |
| **关键分支** | 首次加载默认 **全选** 待结算行；**禁止**取消勾选做子集结算 |
| **失败分支** | 缺产品类目等展示必填 → EX-IP-STL-104；就诊无权或不存在 → EX-IP-STL-102 |
| **查询侧说明** | 纯展示 |

---

### IF-IP-STL-05 住院预结算

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选患者；`chargeItemIds` 非空；每条满足 **BR-IP-STL-002** |
| **主路径步骤** | 校验就诊非 `COMPLETED`/`DISCHARGED`/`CANCELLED` → 判定出院全结模式 → **出院全结临床 Pre**（**BR-IP-STL-018**，复用 ATD-03/04/05 同源 Gate）→ 校验收费项归属与状态 → 作废旧 `INPATIENT_CLINIC` 草稿（**BR-IP-STL-010**）→ 汇总选中金额 → 落 **`INPATIENT_CLINIC` 支付草稿（`DRAFT`）**（含 **contract_no** 与**按收费项记账账户分桶**的占位明细，见 **BR-IP-STL-016**）→ **自费**回填少补 **`displayAmount`/`prepayNetBalance`**；**医保**扩展开启时走第三方预结算并回填基金分项，**`displayAmount`=全额个人现金**（不冲抵）→ 返回草稿主键、应收合计、预览文案 |
| **关键分支** | **仅** `PENDING` + 全选待结算；**不**支持中途子集；医护站显式 `MID_SETTLE` 仅试算（跳过平台 2402，平台 `mid_setl_flag` 恒 0） |
| **失败分支** | 未选费用 → EX-IP-STL-001；草稿冲突 → EX-IP-STL-105；金额≤0 → EX-IP-STL-001 |
| **查询侧说明** | 供 IF-IP-STL-06 正式结算引用 |

---

### IF-IP-STL-06 确认住院结算

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已完成 IF-IP-STL-05；录入支付渠道明细；草稿仍为 `DRAFT` |
| **主路径步骤** | 校验…→ **出院全结临床 Pre**（**BR-IP-STL-018**）→（医保）本地落账前平台确认 → 组装落账明细（统筹/个账/冲抵 O/渠道 C）→ **C>0** 时对**同一份** `payReceiptDetails` 做银联扫码替换（**BR-IP-STL-006a**；禁止残留 `CASHIER`/`SCAN_PAY`）→ 住院支付 SUCCESS → ChargeItem **SETTLED** → **O>0** 时 `balance −= O` → 发票草稿 → 出院多退（**一律现金**，**BR-IP-STL-014** / DD v1.5）→ 就诊财务终态：**有活动床** → **`COMPLETED`**；**无活动床** → **`DISCHARGED`**（**afterCommit 异步**住院净销售合并 3505） |
| **关键分支** | 出院全结：就诊必须为 `PENDING` 且 chargeItemIds = 全部剩余待结算；**无**中途分支；**召回后无床二次全结**直接 **`DISCHARGED`** |
| **失败分支** | 草稿失效/金额不一致/补收渠道未落成（仍为收银台占位或未完成扫码） → EX-IP-STL-106；出院全结未全选 → EX-IP-STL-107；非 `PENDING` → EX-IP-STL-108；支付失败 → EX-IP-STL-109 |
| **查询侧说明** | 发布住院支付已结算、住院收费项已结算事件；InpatientEncounterContextQueryModel 待结算合计下降；已结队列可查 |

---

### IF-IP-STL-07 已结算单查阅

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 在「已结算」队列选中**存在住院临床结算历史**的患者（**BR-IP-STL-017**；不限当前 `COMPLETED`/`DISCHARGED`） |
| **主路径步骤** | **`InpatientSettleAppService#loadSettledInpatientPaymentView`**（读 **`InpatientSettleReadLoader`**）→ 列出 **`INPATIENT_CLINIC`** 结算/反结流水（**FIN-004** 增量含 **SUCCESS+REFUNDED**、**一正一负**、**`netSettleTotal`**）→ 默认最近正向单 → **以 payment 为主**回显绑定 `charge_item_ids` 快照与本批合计对齐后的费用明细/大类（少补为 0 时本批合计回落收费项合计）→ 医保扩展开启时按支付单回填 2303/2304 基金分项 |
| **关键分支** | 正向 **`SUCCESS`** 且未反结、就诊 **`DISCHARGED`** 时 **`recallEnabled=true`**，可点 **`recallInpatientDischarge`**（**IF-IP-RCL-01**，**AS-IP-FIN-004**）；就诊**非** **`DISCHARGED`** 时 **`reverseEnabled=true`**，可点 **`reverseInpatientSettle`**（**IF-IP-RVS-01**） |
| **失败分支** | 无结算记录 → EX-IP-STL-110 |
| **查询侧说明** | 纯展示 + 反结/召回入口（写见 **AS-IP-FIN-004**；**召回在结算页**，非护士站） |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-STL-001 | 未选费用或金额非法 | 请选择待结算费用并确认金额 | 是 |
| EX-IP-STL-102 | 就诊不存在或非住院 | 刷新并重新选择患者 | 是 |
| EX-IP-STL-103 | 个人现金账户不存在 | 请先完成住院登记 | 否 |
| EX-IP-STL-104 | 费用展示数据不完整 | 联系管理员维护产品类目 | 否 |
| EX-IP-STL-105 | 存在未完成的结算草稿 | 重新预结算或作废草稿后再试 | 是 |
| EX-IP-STL-106 | 草稿失效、渠道金额与 C 不符、或补收渠道未落成（`CASHIER`/`SCAN_PAY` 未替换） | 重新预结算；补收请选现金/微信/银联并完成扫码后再确认 | 是 |
| EX-IP-STL-107 | 出院全结未包含全部待结算项 | 请全选待结算费用 | 是 |
| EX-IP-STL-108 | 就诊未处于待结算态（非 PENDING） | 请先完成护士出院后再结算 | 否 |
| EX-IP-STL-111 | 待出院（`ON_HOLD`）发起结算 | 请先办理护士出院后再结算 | 否 |
| EX-IP-STL-112 | 显式中途结算或产品不支持的中途语义 | 本期不支持中途结算，请办理护士出院后全选待结算费用结算 | 否 |
| EX-IP-STL-109 | 支付结算失败 | 以支付流水为准，引导核对后重试 | 视原因 |
| EX-IP-STL-110 | 已结患者无结算记录 | 提示无历史结算单 | 否 |
| EX-IP-STL-101 | 队列或初始化失败 | 稍后重试 | 是 |

---

## 6. 事件语义与消费方

| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 支付草稿已创建 / PaymentDraftCreatedEvent | IF-IP-STL-05 | 流水读侧 |
| 住院支付已结算 / InpatientPaymentSettledEvent | IF-IP-STL-06（支付 `SUCCESS` 后） | InpatientEncounterContextQueryModel 已付摘要；日结QueryService；账户流水 |
| 住院收费项已结算 / InpatientChargeItemsSettledEvent | IF-IP-STL-06（ChargeItem `SETTLED` 后） | ChargeItemQueryService 查询；待发药等下游（按锚点规则，SDD 展开） |
| 发票草稿已创建（Invoice 域事件以 DM 为准） | IF-IP-STL-06 | 票据读侧 |
| 就诊财务已完成 / `InpatientFinancialSettlementCompletedEvent` | IF-IP-STL-06（仅出院全结） | InpatientEncounterContextQueryModel 状态；预交金页禁止收退 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | 队列 ≤1000ms；单就诊费用查询 ≤1500ms；预结算/正式结算 ≤2000ms（不含三方） |
| **幂等** | 重复提交正式结算以支付业务号/草稿态门禁防双账 |
| **并发** | 同一就诊并发预结算以后者作废前者草稿或拒绝（**BR-IP-STL-010**） |
| **一致性** | 单笔结算：支付 `SUCCESS`、ChargeItem `SETTLED`、发票草稿、就诊态（若适用）同事务一致 |
| **审计** | 结算单可追溯操作员、时间、渠道、绑定 chargeItemIds |

### 7.1 扩展：医保落账与 2601（zxplat-domain）

> **规范真源**：[integration-assembler-orchestration.md](../../../../.cursor/skills/模块代码规范/references/integration-assembler-orchestration.md) · **CHS v3** §3.1

| 维度 | 说明 |
| --- | --- |
| **适用 IF** | **IF-IP-STL-05/06** 预结算与正式结算落账（含医保行） |
| **路径 A** | `settleInpatientPayment` 成功 **afterCommit** → `InsurancePaymentSettlementExtension#onPaymentSettled` → `Op2601Invoker` |
| **health 落点** | 同门诊 **AS-OP-FIN-002** §8.2（`integration/financial/orchestration` + `insurance/settlement/*`） |
| **AppService** | `InpatientSettleAppService` / `InpatientPrepayAppService` 经 **落账 orchestration**；禁止内联 SPI 时序 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 有待结算项且护士已办出院（`PENDING`）可全选预结算+正式结算成功；`PENDING` 全结后 `COMPLETED`；发票草稿存在；**出院多退**：预交 1000、本批结 50 → 退 950、`UN_PAY` 流水、展示余额 0 |
| **异常** | `COMPLETED` 不可再结；出院全结漏选；渠道金额≠应收；无账户；`PLANNED` 费用不可选 |
| **规则** | BR-IP-STL-003/004 分流；BR-IP-STL-006 金额对齐；BR-IP-STL-010 单草稿 |
| **联动** | 出院全结多退流水在预交金页 IF-IP-PREP-02 可见；`COMPLETED` 后预交金禁止收退（**AS-IP-FIN-002**）；清床须 `COMPLETED`（护士站） |
| **非目标** | 无医保分项；**住院反结**（见 **AS-IP-FIN-004**）；打印仅占位 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档（全链路）

| 层级 | 文档 / 代码落点 | 本场景用途 |
| --- | --- | --- |
| **AS** | 本文 | IF-IP-STL-01～07、BR、异常、事件语义 |
| **AS-SDD** | `04-架构设计/.../AS-IP-FIN-003-住院结算-SDD.md` | 包结构、Request/Response、编排序列、成码锚点 |
| **App** | `health-application/.../inpatient/financial/settle/` | `InpatientSettleAppService` + `InpatientSettleReadLoader` + Mapper/DTO（**已成码**） |
| **module-support** | `.../querysupport/financial/` | `InpatientReverseSettlePresupport（反结 Pre）；ATD 对称 **`RecallReverseSettlesupport** |
| **module-support** | `.../module/common/assembler/inpatient/` | `InpatientAccountBalanceDisplayDtoApplier`（结算队列展示余额文案覆盖） |
| **zxplat-domain** | `.../integration/financial/orchestration`、`.../insurance/settlement/` | 落账后医保挂接（2601 路径 A）；目标态 `*Orchestration` / `*Assembler`（见 §7.1） |
| **常量** | `zxplat-domain/.../InpatientSettleConstants`、`InpatientSettleQueueTab` | EX-IP-STL-*、页签、呈现模式 |
| **DM·就诊** | `DM-ADM-002-Encounter-就诊.md` §6.1.23 | 出院全结 `PENDING→COMPLETED` |
| **DM·收费** | `DM-ADM-011-ChargeItem-收费明细.md` §6.1.9b/§6.1.12/§6.2.16 | 预结算/结算、`PENDING_SETTLEMENT` 队列筛选 |
| **DM·支付/账户/票据** | `DM-FIN-002` / `DM-ADM-009` / `DM-ADM-012` | 草稿→成功、预交金展示、发票草稿 |
| **RM** | `DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` **Q-IPI-007** | 结算队列 `pageSettleQueue` |
| **QueryService** | `DM-ADM-002 §6.2 EncounterQueryService` §3.7 | 全结后 `status_enum→COMPLETED` |
| **AS（对称/同页）** | `AS-IP-FIN-004-住院反结.md` | **IF-IP-RVS-01** `reverseInpatientSettle`；IF-IP-STL-07 历史 NET/`reverseEnabled` |
| **AS（召回对称）** | `AS-IP-NUR-002` / **AS-IP-FIN-004-SDD §3.4** | **`RecallReverseSettleSupport#execute`**（**不**调本 AppService） |
| **上游 AS** | `AS-IP-FIN-002`、`AS-IP-NUR-006`、`AS-IP-NUR-002` | 渠道/余额口径、费用入待结算、护士出院→`PENDING` |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.25 | 2026-10-12 | **BR-IP-STL-020/021**：打折三门禁与门诊 **BR-OPC-027/028** 同构；`ChargeDiscountApplySupport` 代码先行回写 |
| v1.24 | 2026-08-31 | **BR-IP-STL-010** 增补：旧草稿挂未退货 BPC SUCCESS 时禁止裸作废（同门诊 **BR-OPC-025**）；对齐 `PaymentDraftHalfSuccessAbortGate` |
| v1.23 | 2026-08-21 | **BR-IP-STL-006a**：补收扫码须作用在即将落库的 `payReceiptDetails`；禁止 SUCCESS 残留 `CASHIER`/`SCAN_PAY`；IF-06/EX-106 同步；**BR-IP-STL-014** 多退一律现金（对齐 DD v1.5）；现场脏数据订正见 ops `fix-zhao-guiying-settle-cashier-to-wechat-pro-20260821.sql` |
| v1.22 | 2026-08-17 | IF-IP-STL-03 摘要主路径改为自费 P/O/C 预估（医保挂账户留空待预结算）；金额摘要文案对齐 DD 读侧 |
| v1.21 | 2026-08-17 | **BR-IP-STL-019**：医保入院费用挂医保户、个人现金户仅预交金；缺 `account_id` 回落默认挂账（医保优先）；对齐 **AS-IP-NUR-001 BR-IP-NUR-018** |
| v1.20 | 2026-08-17 | IF-IP-STL-02/03 出参增 `attendingDoctorName`；结算页抬头绑定该字段（此前前端写死「—」） |
| v1.20 | 2026-08-17 | **远端成码钩子保留**：确认结算后扫码预交金原路退可走第二阶段（`completeInpatientDischargePrepayRefund`）；医保正式结算写统筹/个账/现金明细；IF-07 回填医保分项。预交金轧差口径以本表 v1.19 DOC-CONFLICT / DD 为准（**不再**自费少补收预交金、**不再**医保永不冲抵） |
| v1.19 | 2026-08-17 | IF-IP-STL-02 队列卡片主治/位置展示对齐 **DM-ADM-002 v1.43**：活动参与者/位置优先，清床收口或召回不恢复后回落末次 COMPLETED |
| v1.19 | 2026-08-17 | **DOC-CONFLICT→已按 DD「住院预交金结算口径纠错」收敛**：**BR-IP-STL-006/013/014** 统一自费=医保轧差（P/O/C、冲抵明细 `DEPOSIT_OFFSET`、原路退；废除少补收预交金与医保全额现付后一律退清） |
| v1.18 | 2026-08-08 | IF-IP-STL-06：无床直结 `DISCHARGED` 时 afterCommit 异步住院净销售合并 3505（与清床同 SPI）；有床仍待清床上传 |
| v1.17 | 2026-08-04 | **BR-IP-STL-007a**：住院结算写 ChargeItem `enterer_id`/`entered_date`（收费人/收费时间，与 **BR-OPC-016** 同源）；对齐 **DM-ADM-011 v1.6.14** |
| v1.16 | 2026-07-16 | **BR-IP-STL-008** 收窄：零元结算/零元出院全结跳过开票（**BR-INV-008** / `InvoiceAfterPaymentOrchestration`）；**出院召回 redesign 联动**：IF-IP-STL-06 **无活动床**全结直接 **`DISCHARGED`**（跳过清床）；IF-IP-STL-07 **`recallEnabled`/`reverseEnabled`** 分轨；召回写 **FIN-004 IF-IP-RCL-01**（结算页，非护士站） |
| v1.15 | 2026-07-11 | **反结成码回更**：未清床反结就诊回 **`IN_PROGRESS`**（非 `PENDING`）；**BR-IP-STL-011** 待结算队列仅 `PENDING`（召回后 `REGISTERED` 不入队） |
| v1.14 | 2026-07-11 | **已结算队列口径**：由「仅 `COMPLETED`」改为 **存在 `INPATIENT_CLINIC` SUCCESS/REFUNDED 流水**（与在院/清床/召回无关）；**BR-IP-STL-017** 住院号去重；**BR-IP-STL-011** 待结算队列收窄为仅 `PENDING` |
| v1.13 | 2026-07-10 | **§7.1**：health 侧 Integration assembler/orchestration；住院落账 2601 路径 A；对齐 CHS v3 |
| v1.12 | 2026-07-09 | **明确无中途结算**：**BR-IP-STL-004** 收窄为仅 PENDING 全选；新增 **EX-IP-STL-112**；中途结算移入非目标 |
| v1.13 | 2026-08-16 | **医保出院预交金单边收敛**：确认结算先落账；扫码预交金原路退改第二阶段（`completeInpatientDischargePrepayRefund`），避免确认失败时银联已退 |
| v1.12 | 2026-08-16 | **医保出院预交金**：不冲抵；渠道收全额个人现金；结算后按原收款 LIFO **原路退清**（**BR-IP-STL-013/014** 医保分支） |
| v1.11 | 2026-07-09 | **出院链路硬门禁**：**BR-IP-STL-004** 剔除 `ON_HOLD`（待出院禁止结算）；新增 **EX-IP-STL-111**；队列文案区分「可见不可结」 |
| v1.10 | 2026-06-24 | **BR-IP-STL-016**：IF-IP-STL-05/06 写侧补全 payment `contract_no`；支付占位明细与医保合同解析改按收费项**记账账户**（自费/医保）分桶，不再默认登记个人现金户 |
| v1.9 | 2026-06-21 | **展示文案简化**：结算队列/摘要仅展示单一余额；对齐 **AS-IP-FIN-002** v2.1 |
| v1.8 | 2026-06-20 | **support 落点对齐**：`InpatientAccountBalanceDisplayDtoApplier` → `module.common.assembler.inpatient`；详见 **AS-IP-FIN-003-SDD** |（已迁至 application.shared.inpatient）
| v1.7 | 2026-06-19 | **账户余额语义**：结算扣本批已结；展示 = balance − 待结算；多退 = 扣减后盈余；**BR-IP-STL-015** |
| v1.6 | 2026-06-18 | **FIN-004 对齐**：IF-IP-STL-07 增量与 **`reverseInpatientSettle`**；Pre **`InpatientReverseSettlePresupport**；召回对称 **`RecallReverseSettlesupport** |
| v1.4 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader） |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-26 | 初版：IF-IP-STL-01～07；对齐 `/inpatient/charge/settle`；默认全结+中途子集；队列有待结算/已结；结算专用费用 IF；无医保/无自动多退少补/无取消结算；发票草稿必选；§3 列QueryService队列、Encounter `PENDING→COMPLETED`、ChargeItem 预结算门禁三 Gap |
| v1.1 | 2026-05-26 | §3 Gap 收口：Q-IPI-007、`completeInpatientFinancialSettlement`、`preSettleInpatientChargeItems` 领域已落地；关联 DM/DM §6（*QueryService）已回更 |
| v1.2 | 2026-05-26 | 文档编号 **AS-IP-FIN-004→003**；文件名 `AS-IP-FIN-003-住院结算.md`；SDD 同步为 `AS-IP-FIN-003-住院结算-SDD.md` |
| v1.3 | 2026-05-26 | §9.1 全链路追溯表；应用层已成码回更；修正 §1.3 预交金冲抵引用 |

---

**文档负责人**：（待填） · **评审**：（待填）
