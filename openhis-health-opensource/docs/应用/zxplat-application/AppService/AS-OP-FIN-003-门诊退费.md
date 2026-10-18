# 门诊退费 · 应用服务设计（AS）

> **文档职责**：说明「门诊退费」收费窗工作站的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：门诊收费员「**退费**」工作台——待退费患者队列（关键字/日期/页签/刷新）→ 选择就诊 → 查看**待执行退费**明细（按原支付分组，含拟退行与半退保留再生行）→（半退时可选）保留行费用性质切换 → 打开退费确认窗（应退金额、退款渠道拆分）→ **执行退费**（资金冲正 + 收费收口；半退当场对保留行预结算并正式结算）。
>
> **覆盖声明**：契约以 **§2 IF-REF-01～06** 为准；**执行侧**编排与 **DM-FIN-002** §6.1.4～6.1.6a、**DM-ADM-011** 退费收口对齐；**申请侧**（保留行重生成、拟退行 `REFUNDING`、药/耗/诊）由 **`AS-OP-DOR-004`** 承担，本文**不重复**申请编排。队列/明细读侧叠加 **BR-REF-020**（与 **AS-OP-FIN-002** **BR-OPC-010**、**AS-OP-DOR-004** **BR-DRA-020** 同口径）。
>
> **成码对齐**（ZXPlat 工程落地时）：须遵守《**代码生成通用规范**》**B.10.8.1**（枚举 `getCode()` 持久化）、**B.10.11**（分页 `AbstractPageQuery` + `PagedResult` 出参 `content`/`totalCount`、`pageNo` 1-based）、**B.6.5**（AppService/Loader **禁止**注入 `ICurrentUser`；写路径 `operatorId` 用 **`CurrentOperatorResolver`**）、**B.10.2**（编排语义码与页签码落在 **domain-shared** `constants`，与 **AS-OP-FIN-002** 同构）、**B.14**（事件不早于持久化成功）等。字段级契约、HTTP 与依赖白名单以 **`AS-OP-FIN-003-SDD`**（当前 **v1.0.4**）为准。
>
> **编码**：AS-OP-FIN-003 · **版本**：v1.0.20 · **日期**：2026-09-06

---

## 1. 场景概述

### 1.0 业务意图（读透口径）

医生站已完成「**申请退费**」后，收费员在退费窗对**已进入退费中**的账单行办理**资金与账单收口**：对原支付主单**整单冲退**（产品半退亦对该 `paymentId` 资金整单冲正），拟退收费行进入**已退费**终态；若申请时为**半退**，保留行已在申请侧再生为 **`PLANNED`**，执行成功当须对保留行**当场预结算 + 正式结算**（与门诊收费 **IF-OPC-06/07** 同能力复用），使患者可继续缴费。本文**不写**支付逆向单以外的药/耗/诊申请编排。

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 退费页初始化；**待退费/已结**患者分页队列；按就诊加载**待执行退费**明细（按支付分组）；执行前**可退门禁**（退药/退耗未完成则拒绝）；按**原支付主单**执行退费（逆向单 `REFUND_PROCESSING` → `REFUNDED`、原单保持 `SUCCESS`、失败回滚逆向单 → 收费明细收口）；半退时对**保留再生 `PLANNED` 行**当场预结算与正式结算；半退场景下对保留行集合的**转医保/转自费**（与门诊收费账户切换同口径） |
| **在外** | 医生站**申请退费**（**AS-OP-DOR-004**）；**挂号退号整单退费**（**AS-OP-FIN-001** **IF-REG-10** + `completeRegistrationCancelRefund`，**无半退**）；住院退费；第三方支付渠道签约细则；医保平台清算细则 |
| **涉及域** | Encounter；ChargeItem；PaymentReconciliation；Account / Contract（半退再结算）；MedicationRequest / DeviceRequest（执行前门禁只读）；Invoice（若再结算产生票据语义由收费编排消费） |
| **编排权威** | 以本文 **§2、§4** 为准；支付编排与 **DM-FIN-002** §6.1.4～6.1.6a、§6.1.8（退号隔离）一致；收费收口与 **DM-ADM-011** `completeOutpatientBillRefund`、**BR-REF-020** 一致 |
| **前置（摘要）** | 拟退行已由医生站置 **`REFUNDING`**；原支付主单为**成功**或**部分退**语义且未被其它窗口并发占用；半退保留行已为 **`PLANNED`** 且 `refund_id` 链回源行 |
| **后置（摘要）** | 原支付与逆向支付主单终态 **`REFUNDED`**；拟退行 **`REFUNDED`**；被半退替代的源 **`SETTLED`** 一并收口；日结消费**一次**退费完成事件（逆向单）；半退保留行 **`SETTLED`** 并产生新支付成功事实 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **门诊收费员** | 筛选/刷新待退费队列 → 点选患者 → 核对按支付分组的拟退/保留行 →（半退且需改账户时）转医保/转自费 → 打开退费确认窗 → 确认渠道实退拆分 → **执行退费** |
| **系统** | 队列收敛「存在退费中事实」的就诊；执行前校验药房/耗材退单是否完成；按支付串行资金与账单编排；半退当场再结算 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **申请 vs 执行** | **申请**（**AS-OP-DOR-004**）：保留行账单重生成、拟退行 `REFUNDING`、药/耗/诊过程态；**不写**支付逆向单。**执行**（本文）：支付域创建逆向单并完成收口、原单保持 `SUCCESS`、收费明细 `REFUNDING→REFUNDED`、半退保留行 pre+settle；编排失败时 `abortOutpatientRefundAttempt` 回滚逆向单 |
| **产品「半退」** | 以 **`paymentId`** 为粒度：该笔支付快照下**既有拟退又有保留**；资金侧对该笔支付**整单冲退**；保留行已在申请侧为 **`PLANNED`**，执行侧**当场**再结算（**BR-PMR-017**、**BR-CHG-015**）——**仍属一次 IF-REF-05**，**不是**分次退 |
| **口语「半退」（本期禁止）** | **分次退费**：同一 **`paymentId`** 分两次及以上 IF-REF-05 / 申请；原支付进入 **`PARTIAL_REFUNDED`** 后再退。权威门禁 **BR-REF-021** |
| **原支付终态（半退）** | 产品半退完成后，原支付主单终态为 **`REFUNDED`**（**不**使用 `PARTIAL_REFUNDED` 表达产品半退）；`PARTIAL_REFUNDED` 仅表示支付状态机「仍可分次冲退」余量语义（**BR-REF-021** 拒绝再次受理） |
| **静默收口** | 原支付主单 `REFUND_PROCESSING→REFUNDED` **不发**退费完成事件；**唯一** `OutpatientRefundCompletedEvent` 由**逆向单**完成发出（**DM-FIN-002** §6.1.6a） |
| **待退费队列** | 就诊维度存在至少一条 **`REFUNDING`** 收费明细（门诊账单退费，**不含**挂号锚点 `adm_healthcare_service` 时与收银队列口径一致） |
| **QueryService 能力** | 队列患者主数据 **OutpatientEncounterContextQueryModel**；行标签可参考 **BillingStatement** 是否存在未结清应收（与 **AS-OP-FIN-002** **BR-OPC-012** 展示语义对齐，入选就诊集合不同） |
| **DM 路径** | `docs/领域/DomainModel/` 下对应文件 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（与当前 UI 对齐）** | **IF-REF-01～06**；待退费患者队列；**同一原支付一次收一次退**（禁止分次退费）；退费确认窗渠道拆分须**原路返回** |
| **本期限制（2026-07）** | **禁止分次退费（半退）**：同一 `paymentId` 仅允许**一次** IF-REF-05 执行退清；原支付 **`PARTIAL_REFUNDED`** 不可再次受理（见 **BR-REF-021**）。**非**「多笔支付合并一次退」限制——多笔支付仍须**逐笔**调用 IF-REF-05 |
| **待接通** | 退费确认窗内「电子凭证」「医保卡」独立读卡 IF（与门诊收费结算窗占位一致，随医保组件方案收口） |
| **非目标（老能力/UI 未设计）** | 按日期查询**退费历史清单**（老 `patient-refund`）；独立「重开待收查询」（老 `regenerate_charge`，已并入执行明细）；**耗材执行门禁**（老 `verify-device`，在医生站 **IF-DRA-02**）；**单体**「申请+执行」退费提交（老 `refund-payment` 整段，ZXPlat 已拆分） |
| **非目标（边界）** | **挂号退号退费**；医生站三条申请 IF；住院退费 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 就诊上下文、队列过滤 | `DM-ADM-002-Encounter-就诊.md` |
| **Patient** | 队列展示患者摘要 | `DM-ADM-001-Patient-患者.md` |
| **Practitioner** | **IF-REF-03** 原支付收款人姓名展示（只读，经支付录入人关联） | `DM-ADM-005-Practitioner-医务人员.md` |
| **ChargeItem** | 待执行明细查询；执行收口 `REFUNDING→REFUNDED`；半退保留行 pre+settle 参与集合 | `DM-ADM-011-ChargeItem-收费明细.md` |
| **PaymentReconciliation** | 逆向单申请/完成、失败回滚；原收费单保持 `SUCCESS` | `DM-FIN-002-PaymentReconciliation-支付流水.md` |
| **Account / Contract** | 半退保留行再结算前账户切换 | `DM-ADM-009-Account-就诊账户.md`、`DM-FIN-001-Contract-费用性质.md` |
| **MedicationRequest / DeviceRequest** | **IF-REF-04** 执行前只读门禁（是否已完成退药/退耗） | `DM-MED-003-MedicationRequest-药品申请.md`、`DM-WKF-003-DeviceRequest-耗材请求.md` |
| **Invoice** | 半退再结算成功后票据草稿（由收费编排带出） | `DM-ADM-012-Invoice-票据发票.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-REF-001** | 待退费队列**候选就诊**须存在至少一条 **`REFUNDING`** 且非挂号锚点（`service_table <> adm_healthcare_service`）的收费明细；关键字/日期与 **OutpatientEncounterContextQueryModel** Q-OPI-001 一致；**`OutpatientChargeEncounterQueueLoader`** 按日期窗/关键字筛选，**不按**会话机构收窄（**B.6.5**） | IF-REF-02 |
| **BR-REF-002** | 队列行「待退费 / 已结」标签：对已进入分页的就诊，按 **BillingStatement** 是否仍存在未结清/草稿应收打标；页签过滤在应用层（与 **BR-OPC-012** 展示语义一致，入选集合见 **BR-REF-001**） | IF-REF-02 |
| **BR-REF-003** | 执行明细须按**原支付主单**分组展示：拟退 **`REFUNDING`** 行、半退**保留再生 `PLANNED`** 行（及展示用金额/状态/收款人文案）；各支付分组须展示**原收款人**（支付录入人，无法关联医务人员主档时可为空）；**排除**已被半退替代的源 **`SETTLED`**（**BR-REF-020**，与 **BR-DRA-020** 同口径） | IF-REF-03 |
| **BR-REF-004** | **IF-REF-04**：关联药品/耗材若存在**未完成退药/退耗**（负向药/耗嘱未达 `COMPLETED`），则**拒绝执行资金退费**；该门禁亦为挂号退号 **AS-OP-FIN-001 BR-REG-015** 的药耗前置收口（退号侧不再重复校验发放态） | IF-REF-04 |
| **BR-REF-005** | **IF-REF-05** 仅对状态为 **成功 / 部分退** 语义的**原支付**发起；进入 **退款受理** 后生成**逆向支付单**；**禁止**调用退号专用 `completeRegistrationCancelRefund` | IF-REF-05 |
| **BR-REF-006** | **IF-REF-05** 编排顺序（硬）：申请逆向单（`REFUND_PROCESSING`+`relation_id`）→ **逆向单完成**（**唯一**退费完成事件）→ **收费明细收口**（拟退 `REFUNDING` + 被替代源 `SETTLED` 一并纳入，**BR-REF-020**）；任一步失败 → `abortOutpatientRefundAttempt`；原单恒 `SUCCESS` | IF-REF-05 |
| **BR-REF-007** | **产品半退**：**IF-REF-05** 在支付/账单收口成功后，对申请侧返回的 **`preservedNewChargeItemIds`** **当场**预结算并正式结算（渠道实付与预结算现金应付对齐，复用 **AS-OP-FIN-002** **BR-OPC-006** 类语义）；**UN_PAY 整单冲退金额**为原支付全额，再结算实付按原支付渠道比例拆分保留行应付 | IF-REF-05 |
| **BR-REF-008** | **IF-REF-05** 防重：同一原支付在执行中不得重复进入；拟退行须仍为 **`REFUNDING`**；原支付不得已为 **`REFUNDED`**；**同一原支付至多一条有效逆向 `UN_PAY`（`REFUND_PROCESSING`/`REFUNDED`）**——领域对原单悲观锁 + 库部分唯一索引兜底（对齐 **DM-FIN-002 · BR-PMR-008**），禁止并发双写导致收费日结重复冲减 | IF-REF-05 |
| **BR-REF-009** | 退费确认窗**渠道拆分实退合计**须与应退现金口径一致（按金额刻度对齐） | IF-REF-05 |
| **BR-REF-010** | **IF-REF-06**：账户切换对象仅为半退**保留再生 `PLANNED`** 行集合（与 **IF-OPC-04/05** 明细集合规则一致，状态白名单为 **`PLANNED`**）；其中**门诊代煎**保留行**跳过**账单 `account_id` 切换（**不**改 Account；对齐 **AS-OP-FIN-002 · BR-OPC-022** / **AS-OP-DOR-010 · BR-TCM-RX-009**） | IF-REF-06 |
| **BR-REF-011** | **原路返回（硬）**：`refundPaymentDetails` 的渠道码集合及各渠道实退额须与**原支付明细**（`fin_payment_rec_detail.pay_enum` + 发生额）**完全一致**；禁止改渠道或改分桶；合计仍须满足 **BR-REF-009**。**挂号退号 IF-REG-10** 逆向单渠道拷贝同口径（见 **AS-OP-FIN-001** / **DM-FIN-002** §6.1.5） | IF-REF-05 |
| **BR-REF-020** | 读侧/收口：同就诊存在 `PLANNED` 且 `refund_id` 指向源 `SETTLED` 时，**不得**再展示源行；执行 `completeOutpatientBillRefund` 时须将**源 `SETTLED` + 拟退 `REFUNDING`** 一并纳入 | IF-REF-03 / IF-REF-05 |
| **BR-REF-021** | **禁止分次退费（口语「半退」）**：以 **`paymentId`** 为粒度——**一次收须一次退**；**IF-DRA-03** 申请时该支付快照下全部 `SETTLED` 行须同批提交；**IF-REF-05** 执行时须覆盖该支付下全部 `REFUNDING` 行；原支付 **`PARTIAL_REFUNDED`** 拒绝再次受理。**不等价于**禁止多笔支付逐笔退（几次收分别几次退，仍允许） | IF-DRA-03 / IF-REF-05 |
| **BR-REF-022** | **逆向单经办人归属（日结抵净）**：**IF-REF-05** 生成的逆向 `UN_PAY` 支付单 **`enterer_id` 须继承原正向 `PAY` 的 `enterer_id`（原收费员）**，**不得**写为退费窗口当前操作员；保证 **AS-OP-FIN-004 · BR-OP-DCL-011** 按 `cashierId` 过滤时正负流水成对计入、代数和抵净 | IF-REF-05 |
| **BR-REF-023** | **ChargeItem 收费人戳（退费完成）**：`completeOutpatientBillRefund` 须将命令 `operatorId`/`billDate` 写入拟退行 `enterer_id`/`entered_date`（退费操作人覆盖，**BR-OPC-016** / **DM-ADM-011** §6.1.11）；**与 BR-REF-022 分工**：支付逆向单经办人继承原收费员（日结抵净），收费明细 `enterer_*` 记退费完成人 | IF-REF-05 |
| **BR-REF-024** | **原支付明细勾稽（硬）**：IF-REF-05 原路复制前，原支付 `fin_payment_rec_detail` 合计须等于挂载收费项 `total_price` 合计（结算 2 位）；失败 → **EX-REF-307**。与 **AS-OP-FIN-002 · BR-OPC-024** 同精神，禁止把少记/多记自付复制到逆向单 | IF-REF-05 |
| **BR-REF-025** | **代煎费退款终态同步医生站**：IF-REF-05 收费明细收口为 **`REFUNDED`** 后，若本批含门诊代煎收费行，须清对应中药方内饮片 **`suffering_flag`**，并软删因申请退费而停嘱的代煎 companion（**STOPPED**）；使医生站不再展示已退代煎，并允许再次补充（对齐 **AS-OP-DOR-010 · BR-TCM-RX-009**） | IF-REF-05 |

### 1.7 与上下游衔接

| 维度 | 说明 |
| --- | --- |
| **上游申请** | **AS-OP-DOR-004** **IF-DRA-03** 成功后提供按支付的 `paymentSummaries`（`refundingChargeItemIds`、`preservedNewChargeItemIds`、`halfRefundOnPayment`）；收费窗可据此定位 **IF-REF-05** 入参，亦允许仅凭就诊从库内解析同等事实 |
| **退药** | 已发/部分发药品在申请侧产生 **待退药** 发放事实；**IF-REF-04** 要求退药流程完成后再执行资金退费（与 **AS-OP-PHA-002** 消费方对齐）；退号 **BR-REG-015** 依赖本门禁，不再单独查发放 |
| **再结算能力** | 半退保留行 pre+settle 复用 **AS-OP-FIN-002** **IF-OPC-06/07** 领域语义，不在本文重复字段级契约 |
| **退号** | **禁止**走入本文 **IF-REF-05**；见 **AS-OP-FIN-001** **IF-REG-10**。退号收费全收口（**BR-REG-015**）仅看账单三态；药耗须先经 **IF-REF-04** 才能退费干净 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-REF-01 | 进入门诊退费页 | 下发退费页初始化（收费项/队列筛选项等） | 读 | — | — | ChargeItem（枚举元数据） | — |
| IF-REF-02 | 关键字/日期/页签筛选或刷新队列 | 分页查询**待退费候选就诊**并回补展示；打队列标签并按页签过滤 | 读 | BR-REF-001 / BR-REF-002 | — | ChargeItem / Encounter / Patient | OutpatientEncounterContextQueryModel；BillingStatement |
| IF-REF-03 | 点选队列中某一就诊 | 加载该就诊**待执行退费**明细：按**原支付**分组，含拟退 `REFUNDING`、保留 `PLANNED`、应退金额、原收款人、半退标记 | 读 | BR-REF-003 / BR-REF-020 | — | ChargeItem / PaymentReconciliation / Practitioner | — |
| IF-REF-04 | 执行前校验（可合并进确认退费前） | 校验拟退关联药/耗是否已完成退药/退耗 | 读 | BR-REF-004 | — | MedicationRequest / DeviceRequest | — |
| IF-REF-05 | 退费确认窗「确认退费」 | 按所选**原支付**执行：支付冲正链 + 收费收口；渠道实退拆分须**原路返回**（**BR-REF-011**）；原单明细须与收费项勾稽（**BR-REF-024**）；**同一原支付一次退清**（**BR-REF-021**） | 写 | BR-REF-005～009 / BR-REF-011 / **BR-REF-024** / BR-REF-020 / **BR-REF-021** | 原支付、逆向单→`REFUNDED`；拟退行→`REFUNDED`；产品半退保留行→`SETTLED` + 新支付 | PaymentReconciliation / ChargeItem / Account | 队列与明细刷新 |
| IF-REF-06 | 点击「转医保」/「转自费」 | 将半退**保留 `PLANNED`** 行批量切换记账账户（再结算前） | 写 | BR-REF-010 / BR-OPC-008～010（借用） | 保留行账户变更 | Account / ChargeItem | IF-REF-03 刷新 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（**IF-REF-01～06** 所需 **DM** §6 能力与 **OutpatientEncounterContextQueryModel** / **BillingStatement** DM §6 查询契约均已存在）。

**实现追溯（不记入 Gap）**：应用层 **IF-REF-01～06** 已按 **`AS-OP-FIN-003-SDD` v1.0.3** 落地（门诊财务域 `financial/refund` 模块；编排语义码 **EX-REF-*** / 队列页签码与 **AS-OP-FIN-002** 一样收口在 **domain-shared** `constants`）。**不**将前端待接线（如退费确认窗医保读卡占位）记入 AS Gap。

---

## 4. 接口编排说明书（业务步骤）

### IF-REF-01 门诊退费页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 收费员进入门诊退费页 |
| **主路径步骤** | 返回退费页所需筛选项（至少含收费项过程态/终态：退费中、已退费等展示码与文案；**不含**「仅已结算可筛」的收银口径选项） |
| **关键分支** | — |
| **失败分支** | 初始化不可用（EX-REF-101） |
| **查询侧说明** | 无 |

---

### IF-REF-02 待退费患者队列查询（含筛选与刷新）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 输入姓名关键字、日期、队列页签（全部/待退费/已结）或点击刷新 |
| **主路径步骤** | **①** 按 **BR-REF-001** 分页收敛就诊：存在 **`REFUNDING`** 且非挂号锚点收费明细；关键字/日期与 **OutpatientEncounterContextQueryModel** 一致；**不按**会话机构收窄（**B.6.5**）。**②** 回补患者/就诊展示字段。**③** 对页内就诊按 **BillingStatement** 打 **BR-REF-002** 标签。**④** 按 UI 页签过滤当前页 |
| **关键分支** | 无候选就诊时返回空页 |
| **失败分支** | 查询失败（EX-REF-102） |
| **查询侧说明** | **OutpatientEncounterContextQueryModel** 行；标签依赖 **BillingStatement** |

---

### IF-REF-03 就诊待执行退费明细加载

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已在队列中选定就诊 |
| **主路径步骤** | 解析该就诊下处于 **`REFUNDING`** 的拟退行及关联**原支付**（**成功 / 部分退** 语义）；加载各支付快照下挂载行；**排除**被半退替代的源 **`SETTLED`**（**BR-REF-020**）；装配保留再生 **`PLANNED`** 行；按 **paymentId** 分组；返回应退金额、**原收款人**、半退标记、拟退/保留行锚点（可与医生站申请摘要互证） |
| **关键分支** | 无 `REFUNDING` 行时返回空表或提示「无可执行退费」 |
| **失败分支** | 就诊无效（EX-REF-103） |
| **查询侧说明** | 无；以写模型联查刷新中间区 |

---

### IF-REF-04 执行前可退门禁

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点击「确认退费」前（或独立校验按钮，产品二选一，默认合并进 **IF-REF-05** 前） |
| **主路径步骤** | 对拟退行关联的药品/耗材请求检查：按 `refund_medicine_id` / `refund_device_id` 查询负向单；负向单状态非 **`COMPLETED`**（含 **`CANCELLED` 待退**）则拒绝 |
| **放行条件** | 药嘱：药房退药确认后 `completeMedicationRequestAfterRefund`；耗嘱：退耗发放确认后 `completeDeviceRequestAfterRefund`（`DeviceDispenseNurseWorkflowSupport` 编排） |
| **关键分支** | 无药/耗锚点则直接通过 |
| **失败分支** | 须先退药（EX-REF-201）；须先退耗材（EX-REF-202） |
| **查询侧说明** | 无 |

---

### IF-REF-05 按原支付主单执行门诊账单退费

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选定就诊与**原支付**；建议已通过 **IF-REF-04**；退费确认窗已展示应退金额 |
| **主路径步骤** | **①** 校验原支付可受理、拟退行均为 **`REFUNDING`**（**BR-REF-008**）。**②** 将原支付切入**退款受理**。**③** 自原支付明细（及必要时拟退收费行）**回填**逆向支付明细的**结算账户**（请求体仅渠道与实退额，满足 **DM-FIN-002** 明细 `account_id` 非空，与门诊收费 **IF-OPC-07** 草稿账户对齐语义一致）。**④** 生成**逆向支付单**及负向明细。**⑤** 原支付主单**静默**收口为 **`REFUNDED`**（不发完成事件）。**⑥** 逆向单完成，发布**唯一**门诊退费完成事件。**⑦** 收费明细**按支付快照收口**：拟退行 `REFUNDING→REFUNDED`，半退被替代源 `SETTLED` 纳入收口（**BR-REF-006**、**BR-REF-020**）。**⑦a** 若本批含代煎收费行：同步医生站清方头代煎勾选并收口停嘱代煎 companion（**BR-REF-025** / **BR-TCM-RX-009**）。**⑧** 若该产品半退：对 **`preservedNewChargeItemIds`** 执行预结算 → 正式结算，渠道实退/实付与应付对齐（**BR-REF-007**、**BR-REF-009**）。**⑨** 返回执行成功 |
| **关键分支** | 全退：无步骤 **⑧**；半退：步骤 **⑧** 必执行，且原支付须携带可沿用的**支付结果码**（缺失则拒绝再结算，EX-REF-304）；多笔支付须**按支付逐笔**调用（每笔独立受理与事件）；原支付仅一条明细时各渠道逆向行可共用同一账户；多账户原支付按**渠道码**对齐原单分桶 |
| **失败分支** | 支付状态非法（EX-REF-301）；行状态已变（EX-REF-302）；渠道金额不一致（EX-REF-303）；半退再结算失败（EX-REF-304，含原支付结果码缺失）；原支付明细与收费项不平（EX-REF-307）；无法从原支付明细或拟退行解析结算账户（EX-REF-401）；其它业务失败按域返回 |
| **查询侧说明** | 队列、账单、日结QueryService 消费事件后刷新退费完成事件；半退再结算后待收列表出现保留行 |

---

### IF-REF-06 半退保留行转医保 / 转自费

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已加载明细且存在半退**保留 `PLANNED`** 行；尚未对该笔支付执行 **IF-REF-05** |
| **主路径步骤** | 解析目标账户；将**保留再生行集合**批量切换记账账户（语义同门诊收费 **IF-OPC-04/05**，集合仅限 **`PLANNED`** 保留行；**排除代煎**，**BR-REF-010**） |
| **关键分支** | 无保留 `PLANNED` 行时**拒绝**（EX-REF-403） |
| **失败分支** | 账户缺失（EX-REF-401）；切换失败（EX-REF-402） |
| **查询侧说明** | 刷新 **IF-REF-03** 明细 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-REF-101 | 初始化失败 | 稍后重试 | 是 |
| EX-REF-102 | 队列查询失败 | 稍后重试 | 是 |
| EX-REF-103 | 就诊无效/无明细 | 重选患者 | 是 |
| EX-REF-201 | 须先退药 | 请先完成退药后再退费 | 否 |
| EX-REF-202 | 须先退耗材 | 请先完成退耗材后再退费 | 否 |
| EX-REF-301 | 支付不可退 | 支付状态已变化，请刷新 | 否 |
| EX-REF-302 | 收费行不可退 | 收费明细状态已变化，请刷新 | 否 |
| EX-REF-303 | 实退金额不一致 | 实退合计与应退不符 | 否 |
| EX-REF-304 | 半退再结算失败 | 保留项结算失败，请核对账户与渠道 | 视原因 |
| EX-REF-305 | 分次退费/半退 | 同一笔支付须一次退清，不支持分次退费 | 否 |
| EX-REF-306 | 非原路退 | 退费渠道须与原支付一致 | 否 |
| EX-REF-307 | 原支付明细不平 | 原支付渠道明细合计与收费项不一致，请先修正原单后再退费 | 否 |
| EX-REF-401 | 账户缺失 | 未找到目标医保/自费账户；或执行退费无法从原支付/拟退行解析结算账户 | 否 |
| EX-REF-402 | 账户切换失败 | 切换失败，请稍后重试 | 是 |
| EX-REF-403 | 无半退保留行 | 当前就诊无保留待再结算项 | 否 |
| EX-REF-410 | 执行成功 | 退费成功 | — |
| EX-REF-399 | 其它业务异常 | 按返回语义 | 视原因 |

---

## 6. 事件语义与消费方

| 事件语义（自然语言 / 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 进入退款受理窗口 | IF-REF-05 | 支付状态QueryService、并发控制 |
| 门诊退费申请（逆向单建立） | IF-REF-05 | 对账、审计 |
| 门诊退费完成（**逆向单**，含 `relationId` 指向原单） | IF-REF-05 | **日结/交班**（**仅计一次**）；账单QueryService |
| 门诊按账单退费完成（收费明细收口） | IF-REF-05 | 门诊信息费用摘要、账单QueryService |
| 半退保留行预结算/正式结算（复用收费事件族） | IF-REF-05 半退分支 | 待收列表、发票草稿、发药队列 |

> **注意**：原支付主单静默 `REFUNDED` **不**发布退费完成事件，避免日结双倍（**DM-FIN-002** §6.1.6a）。

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | IF-REF-02 ≤1500ms；IF-REF-03 ≤1200ms；IF-REF-05 ≤8000ms（含支付+半退再结算） |
| **幂等** | 同一原支付重复执行须拒绝或安全 no-op（以支付域门闩为准） |
| **并发** | 同一原支付受理窗口互斥；同就诊多支付可顺序执行 |
| **审计** | 执行须可追溯操作者、就诊、原支付、拟退/保留行集合 |
| **成码规范（ZXPlat）** | **B.10.8.1** / **B.10.11** / **B.10.2** / **B.14**；分页与机构收窄同门诊收费；详细设计见 **AS-OP-FIN-003-SDD** |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 全退单笔支付；半退（拟退+保留）执行后保留行 `SETTLED` 且产生新支付；多笔支付分两次执行 |
| **异常** | IF-REF-04 退药未完成；IF-REF-05 行状态被并发改动；渠道金额不等 |
| **规则** | BR-REF-006 顺序；BR-REF-020 读侧无双行、收口含源 `SETTLED`；日结仅一笔完成事件 |
| **衔接** | 医生站申请后直接进退费窗；无申请时不可执行 |
| **边界** | 退号就诊不得进入本文 IF-REF-05 |

---

## 8.1 扩展：BPC 扫码原路退（P0 · 可选 Module）

> **真源**：[01-概要设计](../../../模块/zxplat-module-unionpay-bpc/01-概要设计.md) · [zxplat-module-unionpay-bpc/03 §3.11](../../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md) · **成码**：[03 DEP-REF-01](../../../模块/zxplat-module-unionpay-bpc/03-详细设计与编码指导.md)

| 维度 | 说明 |
| --- | --- |
| **适用 IF** | **IF-REF-05** 执行退费 |
| **条件** | 原支付经银联被扫（存在 `upc_bpc_order` 且 `pay_enum` 为 **WECHAT/ALIPAY/BANK_CARD**）且 Extension.isEnabled() |
| **退货金额（硬规则）** | Win R / `clientRefundResult.refundAmountYuan` **仅等于**原 BPC 消费单 `txnAmtFen`（扫码实付段）；**禁止**把账单应退总额、医保个账打进银联退货（混合支付尤甚） |
| **前端（通道 C）** | `submitScanRefund`：按 BPC 原单取额 → Win `/api/unionpay/refund` → IF-REF-05 顶层 `clientRefundResult`；session 缓存凭证供半成功重试（须校验 merTradeNo+金额） |
| **编排** | IF-REF-05 AppService **内** `applyClientRefundResult`（等额门禁 + 已 REFUNDED 幂等）→ 本地退费（**同一事务**） |
| **半成功收口** | Win R 已成功、院内未收口：重试**跳过**重复 Win（缓存/`already_refunded`/「已全额退款」），带原凭证再调 IF-REF-05；院内成功后清缓存 |
| **失败** | Win R 或 Extension 任一步失败 → **不**进入本地退费；头寸不足时**禁止**另行人工退货，恢复后本页重试 |
| **退号** | 挂号退号整单仍走 IF-REG-10；扫码段原路退同上（**P0**） |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `docs/应用/zxplat-application/AppService-SDD/AS-OP-FIN-003-门诊退费-SDD.md` |
| `docs/应用/zxplat-application/AppService/AS-OP-DOR-004-门诊医生站申请退费.md |
| `docs/应用/zxplat-application/AppService/AS-OP-FIN-002-门诊收费.md |
| `docs/应用/zxplat-application/AppService/AS-OP-FIN-001-门诊挂号.md |
| `docs/应用/zxplat-application/AppService/AS-OP-PHA-002-门诊退药.md |
| `docs/领域/DomainModel/DM-FIN-002-PaymentReconciliation-支付流水.md` |
| `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md` |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（OutpatientEncounterContextQueryModel）` |
| `DM §6.2（DM-FIN-002 §6.2 PaymentReconciliationQueryService` |
| `docs/模块/zxplat-module-unionpay-bpc/01-概要设计.md`（BPC 扫码原路退 §6.4，P0） |

| v1.0.20 | 2026-09-06 | **BR-REF-025** / IF-REF-05 步骤 **⑦a**：代煎费退款终态同步医生站清 `suffering_flag` 与停嘱 companion；对齐 **AS-OP-DOR-010 · BR-TCM-RX-009** |
| v1.0.19 | 2026-08-26 | **BR-REF-024** / **EX-REF-307**：原支付明细合计须等于收费项合计后方可原路退；对齐 **BR-OPC-024** |
| v1.0.18 | 2026-08-05 | **BR-REF-008**：明确同一原支付至多一条有效逆向 UN_PAY（锁 + 部分唯一索引）；对齐 **DM-FIN-002 v1.4.14** / **SDD v1.0.14** |
| v1.0.17 | 2026-08-04 | **BR-REF-023**：退费完成写 ChargeItem `enterer_*`（与 **BR-OPC-016** / **BR-REF-022** 分工）；对齐 **DM-ADM-011 v1.6.14** |
| v1.0.16 | 2026-08-02 | **BR-REF-010 / IF-REF-06**：半退保留行转账户排除门诊代煎；对齐 **AS-OP-FIN-002 · BR-OPC-022**、**AS-OP-DOR-010 · BR-TCM-RX-009** |
| v1.0.15 | 2026-08-01 | §8.1：扫码退货金额=BPC `txnAmtFen`；半成功凭证缓存与重试收口；`applyClientRefundResult` 等额/幂等；对齐 **SDD v1.0.11**、unionpay-bpc 01/02/03 |
| v1.0.14 | 2026-07-30 | **BR-REF-004** / §1.7：明确 IF-REF-04 为挂号退号 **BR-REG-015** 药耗前置收口；对齐 **AS-OP-FIN-001 v2.31** |
| v1.0.13 | 2026-07-29 | **BR-REF-011**：注明挂号退号 IF-REG-10 逆向单渠道拷贝同口径；对齐 **AS-OP-FIN-001 v2.29**、**DM-FIN-002 v1.4.12** |
| v1.0.12 | 2026-07-09 | **BR-REF-007** 落地：产品半退 UN_PAY 整单冲退（原支付全额），再结算按原渠道比例拆分保留行应付，修复日结 BR-OP-DCL-011 半退代数和 |
| v1.0.11 | 2026-07-09 | **BR-REF-022**：逆向 UN_PAY `enterer_id` 继承原收费员，保证日结 BR-OP-DCL-011 正负流水同 cashier 抵净 |
| v1.0.10 | 2026-07-09 | **BR-REF-021** 口径修正：半退=同一原支付**分次退**（一次收退两次），非禁止产品半退/保留链；拒绝 `PARTIAL_REFUNDED` 再次受理 |
| v1.0.9 | 2026-07-09 | **本期限制**：**BR-REF-021** 分次退门禁（**EX-REF-305**）；**BR-REF-011** 退费须原路返回（**EX-REF-306**） |
| v1.0.8 | 2026-06-23 | §8.1：clientRefundResult + applyClientRefundResult；语义码识别原扫码段 |
| v1.0.7 | 2026-06-23 | §8.1：P0 原路退；同事务 R；条件扩至 220100/220200/220300 |
| v1.0.6 | 2026-05-28 | 六维对账：七方法与 SDD §3.1、`finalizeOriginalOutpatientPayment` 半退链对齐 |
| v1.0.5 | 2026-05-28 | ReadModel 废止后对齐 zxplat 实现 |
| v1.0.4 | 2026-05-18 | **IF-REF-05**：执行退费前自原支付明细回填逆向支付明细 `account_id`（请求仅渠道/金额）；无法解析时 **EX-REF-401**；对齐 **IF-OPC-07** 与 **DM-FIN-002** 明细账户非空。 |
| v1.0.3 | 2026-05-18 | 对齐 **AS-OP-FIN-003-SDD v1.0.3** 与已落地实现：成码追溯、**BR-REF-003** 原收款人展示、**Practitioner** 域、半退 `outcome` 缺失门禁、分页 **PagedResult** 口径；关联 SDD 文档。 |
| v1.0.2 | 2026-05-18 | **IF-REF-06**：无保留 `PLANNED` 行时明确**拒绝**（EX-REF-403）。 |
| v1.0.1 | 2026-05-18 | **§3**：删除不符合 AS Gap 门禁的「应用层未落地」项，改为**无待跟踪缺口**。 |
| v1.0 | 2026-05-18 | 首版：**IF-REF-01～06**；申请/执行分离；支付 **BR-REF-005～009** 与半退当场再结算 **BR-REF-007**；对齐退费页 UI 与老退费窗能力裁剪 |
