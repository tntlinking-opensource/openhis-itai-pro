# 住院预交金 · 应用服务设计（AS）

> **文档职责**：说明住院收费「预交金管理」场景的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：住院收费预交金工作台（`/inpatient/charge/prepay`）——左侧患者队列（**在院 / 不在院 / 全部**；关键字/入院日期/刷新）→ 选中患者展示住院摘要与**账户余额（动态展示口径）** → 办理区收取/退费（仅在院可写）→ 下方**预交金流水**；**登记时首笔预交金**在 **AS-IP-FIN-001** 登记提交内完成，本页负责**续收与退费**及出院/退院后流水查阅。
>
> **覆盖声明**：完整契约以 **§2 IF-IP-PREP-00～04** 为准；与住院预交金管理能力对齐，并按当前 UI 可执行动作组织接口。
>
> **编码**：AS-IP-FIN-002 · **版本**：v2.7 · **日期**：2026-10-13

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | **本期** IF-IP-PREP-01～04：患者队列查询（`stayScope`：**在院**可收退四态 / **不在院** `COMPLETED`+`DISCHARGED`+`CANCELLED` 只读 / **全部**）；选中患者后预交金流水查询；收取预交金（续收）；退预交金（**按账户余额退、不关联原收款单**）；收取/退费后刷新余额与流水 |
| **在外** | 住院登记与首笔预交金（**AS-IP-FIN-001**）；住院结算出院多退少补退预交金流水（**AS-IP-FIN-003** IF-IP-STL-06）；医保账户并行户；电子票据模板与打印服务实现（见 §1.4）；第三方支付协议报文细节 |
| **涉及域** | Encounter（住院就诊状态门禁）；Account（个人现金户余额）；PaymentReconciliation（住院存款流水 PAY/UN_PAY） |
| **编排权威** | 以本文 **§2、§4** 为准 |
| **上游** | 患者须已完成住院登记（`IMP + REGISTERED` 起）；登记提交时**必开**个人现金账户（余额 0，与是否当场收首笔预交金无关，见 **AS-IP-FIN-001** BR-IP-REG-002） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院收费员** | 切换**在院/不在院/全部**检索患者 → 选中患者查看余额与历史流水 →（在院）录入金额与支付方式收取 → **录入退费金额**（可部分退、可改数）提交退费 → 刷新列表 |
| **系统** | 校验就诊可收退状态；落住院存款支付事实；同步账户余额；可选触发扫码/网银收款 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **在院范围 `stayScope`** | IF-IP-PREP-01 请求：`IN_HOSPITAL`（默认，在院）/ `NOT_IN_HOSPITAL`（不在院只读）/ `ALL`（全部）；常量见 `InpatientPrepayStayScope`（已废止历史 `prepayQueueTab`） |
| **队列可见·在院** | `class_enum=IMP` 且 `status_enum` ∈ **`REGISTERED` / `IN_PROGRESS` / `ON_HOLD` / `PENDING`**（可收退四态；**不含**待清床 `COMPLETED`） |
| **队列可见·不在院** | `class_enum=IMP` 且 `status_enum` ∈ **`COMPLETED` / `DISCHARGED` / `CANCELLED`（退院）**，且就诊下**存在已收口预交金流水**（`HOSPITAL_DEPOSIT`/`PRE_DEPOSIT` + `SUCCESS`，与 IF-IP-PREP-02 同口径）；无流水不入队（列表无查阅意义）；**仅查阅**，不可收退 |
| **队列可见·全部** | 在院四态直接入队 ∪（不在院三态且有预交金流水） |
| **UI「在院」** | **含待入科**（`REGISTERED`）、已入科、待出院医嘱（`ON_HOLD`）、待结算（`PENDING`）；**不以是否已分床为可见门禁**；已结算待清床归「不在院」 |
| **收退写门禁** | 仅 **`REGISTERED` / `IN_PROGRESS` / `ON_HOLD` / `PENDING`** 可办理收取/退费；**禁止** `PLANNED` / **`COMPLETED`** / **`DISCHARGED`** / **`CANCELLED`** |
| **禁止收退** | **`PLANNED`**（仅医生申请、未登记；意向预交金 DRAFT 由 **AS-OP-DOR-005** 落库、仅 **AS-IP-FIN-001** 登记确认/作废，**本页队列不可见**）、**`COMPLETED`**（已财务结算，**可查流水**但不可再收退）、**`DISCHARGED`**（已出院，可查流水，不可收退）、**`CANCELLED`**（取消登记/退院，可查历史流水，不可收退） |
| **流水展示** | IF-IP-PREP-02 **不展示** `DRAFT`（含医生意向草稿与扫码预下单临时稿）与 **`CANCELLED`**（历史作废稿加载时软删）；登记拆分按**一渠道一流水**展示（支付方式为该笔渠道名）；仅展示已收口/退费等业务流水 |
| **Account.balance（内部）** | 个人现金户 **账户余额**（预交金收/退与**出院结算冲抵占用 O**、原路退预交金后的库内值）；**不**在 ChargeItem 进入待结算时回写 |
| **展示余额（读侧）** | **`Account.balance` − Σ(`PENDING_SETTLEMENT`.total_price)**；**不**再减 `SETTLED`（冲抵占用已写入 balance）；**不**含 `PLANNED` 待收费 |
| **账户余额（展示）** | 队列/卡片经 **`InpatientAccountBalanceDisplayAssembler`** 输出**单一余额文案**（如 `500.00 元` / `-50.00 元`，正负号区分）；`accountBalance`/`availableBalance` 与展示余额一致；**不**再拆分「可用/待结算」主副文案；护士站/医生站/预交金/结算队列**同口径** |
| **住院存款** | 支付业务类型 `kind_enum = HOSPITAL_DEPOSIT`；收款 `payment_enum = PAY`，退款 `payment_enum = UN_PAY`（金额为**正数**展示，与老系统「负数落库」等价） |
| **退费口径（产品）** | **不关联**原收款支付单；**可退上限 = Account.balance（库内余额）**；须 ≤ 库内余额且 > 0 |
| **收退收口（统一）** | 预交金**收取与退费**均：`DRAFT` 建单 → **`settleInpatientPayment`（SUCCESS）** 后落账 |
| **账户余额变更** | 预交金收/退、结算**冲抵占用（−O）**、出院原路退预交金时由应用编排调 Account 域增减余额 |
| **队列展示** | IF-IP-PREP-01 返回 `lowBalanceWarning` 与 **§1.3 账户余额展示字段**；左侧展示就诊摘要、**费用性质** `contractDisplay` 与 **文案化余额**（与护士站/结算队列同口径） |
| **IMP 状态语义** | 完整状态机见 **`DM-ADM-002` §2.3.1** |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-IP-PREP-01～04；现金/银行卡/微信/支付宝等支付方式录入；单笔收取；单笔退费（金额≤余额，不绑原单） |
| **本期（UI 接通）** | 左侧队列筛选与刷新；患者信息栏；快捷金额；收取预交金（**组合收款多渠道明细**，一渠道一流水）；退预交金（**金额框改数**，流水仅作参考展示）；流水表展示 |
| **非目标** | 登记页首笔预交金（**AS-IP-FIN-001**）；病区维度筛选（老接口可选 `wardLocationId`，新 UI 未设计，本期舍弃）；医保并行户预交金 |
| **非目标（舍弃）** | **打印凭证**：新 UI 有按钮，老预交金 Controller **无**独立打印接口，本期仅前端占位/本地提示，不纳入 IF |
| **非目标（后续）** | 扫码/网银异步回执补单（老逻辑在收款成功且带支付码时调三方；本期契约保留分支，实现可分期） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 门禁：仅允许可收退预交金的住院状态 | `DM-ADM-002-Encounter-就诊.md` |
| **Account** | 个人现金户余额增减；登记时已开立 | `DM-ADM-009-Account-就诊账户.md` |
| **PaymentReconciliation** | 住院存款收/退流水；收/退均经结算收口 | `DM-FIN-002-PaymentReconciliation-支付流水.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-PREP-001** | **队列可见**：`stayScope=IN_HOSPITAL`（默认）→ 在院四态；`NOT_IN_HOSPITAL` → {`COMPLETED`,`DISCHARGED`,`CANCELLED`} **且 EXISTS 已收口预交金流水**（无流水不入队）；`ALL`/空 → 在院四态 ∪（不在院三态且有流水）。**收退写**：仅在院四态可办理；**禁止** `PLANNED` / **`COMPLETED`** / **`DISCHARGED`** / **`CANCELLED`**（与 **DM-ADM-002** §2.3.1 写门禁一致） | IF-IP-PREP-01、03、04 |
| **BR-IP-PREP-002** | 收取/退费前须存在该就诊**个人现金账户**（**AS-IP-FIN-001** 登记完成时已开立，余额可为 0）；不存在则拒绝 | IF-IP-PREP-03、04 |
| **BR-IP-PREP-009** | **`PLANNED`（待登记）** 不得在本页收退；有医生申请须先在登记页完成 **`PLANNED→REGISTERED`**，首笔预交金可选在登记提交内完成（**BR-IP-REG-001**） | IF-IP-PREP-01、03 |
| **BR-IP-PREP-003** | 收取金额须 **> 0**；**须**填写支付明细（渠道/金额分项，对齐 **AS-IP-FIN-001** 首笔预交金）；有扫码时携带支付码 | IF-IP-PREP-03 |
| **BR-IP-PREP-004** | 退费金额须 **> 0** 且 **≤ 当前账户余额**；退费后余额不得低于账户下限（**BR-ACC-001**） | IF-IP-PREP-04 |
| **BR-IP-PREP-005** | 预交金收取（`PAY`）与退费（`UN_PAY`）均：落住院存款支付事实（`DRAFT`）→ **`settleInpatientPayment` 收口（`SUCCESS`）** 后，再更新账户余额（收增加/退减少）。**收取组合收款**：`paymentDetails` 多条时**逐渠道各落一笔**流水（与 **AS-IP-FIN-001 BR-IP-REG-022** 同口径），余额按合计一次调增；扫码渠道至多一条且可复用预下单草稿 | IF-IP-PREP-03、04 |
| **BR-IP-PREP-006** | 退费**不关联**原收款单（**无** `relation_id` 门禁）；须填写支付明细（退现渠道）；落 `UN_PAY` + `HOSPITAL_DEPOSIT`，金额为正数 | IF-IP-PREP-04 |
| **BR-IP-PREP-007** | 登记取消门禁：账户余额 **> 0** 时须在本模块退清后方可取消登记（**AS-IP-FIN-001** BR-IP-REG-009），本页退费能力须满足该联动 | IF-IP-PREP-04 |
| **BR-IP-PREP-008** | 流水查询仅包含该就诊下 `HOSPITAL_DEPOSIT` 类支付主记录，按时间倒序 | IF-IP-PREP-02 |
| **BR-IP-PREP-010** | 支付明细各行金额均 **> 0**，且各行金额之和 **等于** 收取主金额或退费主金额（与 **AS-IP-FIN-001** 登记首笔预交金口径一致） | IF-IP-PREP-03、04 |
| **BR-IP-PREP-011** | 在院队列加载账户余额时须**批量**解析就诊下个人现金户，禁止按页逐行查库 | IF-IP-PREP-01 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-PREP-00 | 进入预交金页 | 加载支付渠道下拉（**ADR-PAY-001**） | 读 | — | 无 | — | `PaymentChannelOptionProvider` |
| IF-IP-PREP-01 | 左侧切换在院范围、输入住院号/姓名/床号、入院日期或点刷新 | 分页查询患者队列（`stayScope`：在院四态 / 不在院须有预交金流水含退院 / 全部） | 读 | BR-IP-PREP-001 | 无 | Encounter / Account | InpatientEncounterContextQueryModel（+ 账户余额组装） |
| IF-IP-PREP-02 | 选中患者卡片（联动加载流水） | 查询该就诊的预交金支付流水 | 读 | BR-IP-PREP-008 | 无 | PaymentReconciliation | 支付查询读口径（按就诊） |
| IF-IP-PREP-03 | 「收取预交金」+ 金额/支付方式 | 续收住院预交金 | 写 | BR-IP-PREP-001～003、005 | 账户余额增加；新增 PAY 流水（DRAFT→SUCCESS） | Account / PaymentReconciliation | 流水刷新；余额刷新 |
| IF-IP-PREP-04 | 「退预交金」+ 退费金额（可改数、可部分退） | 按账户余额退还预交金 | 写 | BR-IP-PREP-001、002、004～006、007 | 账户余额减少；新增 UN_PAY 流水（DRAFT→SUCCESS） | Account / PaymentReconciliation | 流水刷新；余额刷新 |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

> 领域：`DM-FIN-002` 收退 DRAFT+settle；读侧 **`EncounterQueryService#pagePrepayQueue`（Q-IPI-004）** 已落地。应用层 `InpatientPrepayAppService` 由 SDD 成码，**不是** Gap。不采用登记 `pageRegQueue` 拼四态（见 SDD §2.5 领域能力核对）。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-PREP-01 患者队列查询（在院 / 不在院 / 全部）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入预交金页、切换在院范围、修改关键字/入院日期或点击刷新 |
| **主路径步骤** | 按 `stayScope`、关键字（住院号/姓名/床号）、入院日期筛选 → 限定 **`IMP`** 且 **BR-IP-PREP-001 范围可见态**（不在院另 EXISTS 预交金流水）→ 分页返回卡片字段 → **`InpatientAccountBalanceDisplayAssembler`** 组装展示（**库内 `balance` − 待结算** → 单一余额文案；**不**将 `PLANNED` 计入） |
| **关键分支** | 在院范围、关键字、日期、刷新共用本 IF；默认选中第一条时由前端触发 IF-IP-PREP-02；不在院（含退院）仅查阅且无流水不入队 |
| **失败分支** | 查询不可用 → EX-IP-PREP-101 |
| **查询侧说明** | 纯展示；余额变更后由 IF-03/04 触发刷新 |

---

### IF-IP-PREP-02 预交金流水查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 左侧选中一名患者（在院四态可写；不在院 **COMPLETED** / **DISCHARGED** / **CANCELLED** 只读查阅） |
| **主路径步骤** | 按住院就诊标识查询 `HOSPITAL_DEPOSIT` 支付主记录列表 → 倒序展示收费时间、业务类型（收/退）、金额、**支付方式中文**（`payMethodText`）、**操作员姓名**（`operatorName`）、票据号、状态 |
| **关键分支** | 未选患者（`encounterId` 空）时返回空列表；`patientId` **可选**，未传时不做患者门控，仅按就诊查流水 |
| **失败分支** | 就诊不存在或类别非住院 → EX-IP-PREP-102；**仅当**请求携带 `patientId` 且与就诊不一致时 → EX-IP-PREP-102 |
| **查询侧说明** | 纯展示 |

---

### IF-IP-PREP-03 收取预交金（续收）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选患者；金额、支付方式校验通过 |
| **主路径步骤** | 校验就诊状态可收（BR-IP-PREP-001）→ 加载账户（BR-IP-PREP-002）→ 按 `paymentDetails` **逐渠道** `collectAdvanceDeposit`（`PAY`，DRAFT；扫码行可复用预下单）→ **`settleInpatientPayment`（SUCCESS）** → **账户余额 += 合计** → 刷新 |
| **关键分支** | 携带支付码时：结算成功后触发三方收款（异步/同步以实现为准）；未携带则按现金等线下渠道记账 |
| **失败分支** | 未选患者/金额非法 → EX-IP-PREP-001；无账户 → EX-IP-PREP-002；状态不允许 → EX-IP-PREP-003；支付明细缺失 → EX-IP-PREP-008；明细合计≠主金额 → EX-IP-PREP-007；收款或结算失败 → EX-IP-PREP-004 |
| **查询侧说明** | 流水表新增成功记录；患者栏余额更新 |

---

### IF-IP-PREP-04 退预交金

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选患者；退费金额 > 0（前端可改数，支持部分退或一次退清） |
| **主路径步骤** | 校验就诊可退（BR-IP-PREP-001）→ 加载账户（BR-IP-PREP-002）→ 校验 **退费金额 ≤ 当前账户余额**（BR-IP-PREP-004）→ `refundAdvanceDeposit`（`UN_PAY`，`HOSPITAL_DEPOSIT`，**不绑原单**，DRAFT）→ 写支付明细 → **`settleInpatientPayment`（SUCCESS）** → **账户余额 −= 金额**（仍须满足 BR-ACC-001）→ 刷新 |
| **关键分支** | 默认建议退额 = 当前余额，操作员可改小做部分退；流水区仅展示用，**非**必选原单 |
| **失败分支** | 未选患者/金额≤0 → EX-IP-PREP-001；无账户 → EX-IP-PREP-002；状态不允许 → EX-IP-PREP-003；明细合计≠退费金额 → EX-IP-PREP-007；退费金额 > 余额 → EX-IP-PREP-005；退费后低于下限 → EX-IP-PREP-006；退款或结算失败 → EX-IP-PREP-004 |
| **查询侧说明** | 流水新增 UN_PAY 成功记录；患者栏余额减少 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-PREP-001 | 未选患者或金额≤0 | 请选择患者并输入正确金额 | 是 |
| EX-IP-PREP-002 | 个人现金账户不存在 | 请先完成住院登记 | 否 |
| EX-IP-PREP-003 | 就诊状态不允许收退预交金 | 说明当前状态不可办理 | 否 |
| EX-IP-PREP-004 | 收取或结算失败 | 是否已入账以流水为准，引导重试或人工核对 | 视原因 |
| EX-IP-PREP-008 | 收取/退费时未填支付明细 | 补全支付方式/明细后再提交 | 是 |
| EX-IP-PREP-007 | 支付明细合计与主金额不一致 | 调整明细分项或主金额 | 是 |
| EX-IP-PREP-005 | 退费金额大于当前账户余额 | 减小退费金额或核对账户余额 | 是 |
| EX-IP-PREP-006 | 退费后余额低于账户下限 | 调整退费金额 | 是 |
| EX-IP-PREP-101 | 队列查询失败 | 缩小条件或稍后重试 | 是 |
| EX-IP-PREP-102 | 就诊非住院或不存在 | 刷新并重新选择患者 | 是 |

---

## 6. 事件语义与消费方

| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 预交金已收取 / AdvanceDepositCollectedEvent | IF-IP-PREP-03（`collect` 落 DRAFT 后） | 日结押金口径；流水读侧 |
| 预交金已退还 / AdvanceDepositRefundedEvent | IF-IP-PREP-04（`refund` 落 DRAFT 后） | 同上 |
| 住院支付已结算 / InpatientPaymentSettledEvent | IF-IP-PREP-03、04（**`settle` 收口后**） | 流水 SUCCESS；日结 |
| 账户余额已变更 / AccountBalanceEditedEvent | IF-IP-PREP-03、04（**收口成功后**扣减/增加余额） | 患者栏余额 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | 队列查询 ≤1000ms；单笔收/退 ≤2000ms（不含三方异步） |
| **幂等** | 短时重复提交收/退应以支付流水幂等键或业务号防双账；三方回调单独幂等 |
| **并发** | 同一就诊并发收退以后写者余额校验为准 |
| **一致性** | 单笔收/退：支付事实与账户余额同一业务事务内一致；结算失败不得仅留 DRAFT 对外展示为成功 |
| **审计** | 操作员、时间、支付方式、退费金额可追溯（不要求关联原收款单） |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 四态可收退；**`COMPLETED` 待清床**可在在院队列中选中并**只读查流水**（含 **AS-IP-FIN-003** 出院多退 `UN_PAY`）；**已出院页签**可查 `DISCHARGED` 流水；续收后余额与流水增加；登记首笔仅在 **AS-IP-FIN-001** |
| **异常** | `PLANNED` / `DISCHARGED` 不可收退；**`COMPLETED` 不可收退**；无账户；金额≤0；退费 > 余额 |
| **规则** | 收/退均 DRAFT+settle（BR-IP-PREP-005）；退费 ≤ 余额、不绑原单（BR-IP-PREP-004、006）；登记取消须退清（BR-IP-PREP-007） |
| **联动** | 登记后（含直登 `REGISTERED`）本页可续收；**BR-IP-REG-009** 退清后可在登记页取消 `REGISTERED`/`PLANNED`；**AS-IP-FIN-003** 出院全结多退流水在本页 IF-IP-PREP-02 可见直至清床 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `docs/领域/DomainModel/DM-ADM-002-Encounter-就诊.md` |
| `docs/领域/DomainModel/DM-ADM-009-Account-就诊账户.md` |
| `docs/领域/DomainModel/DM-FIN-002-PaymentReconciliation-支付流水.md` |
| `DM §6.2（DM-ADM-002 §6.2 EncounterQueryService（InpatientEncounterContextQueryModel）` |
| `docs/应用/zxplat-application/AppService/AS-IP-FIN-001-住院登记.md |
| `docs/应用/zxplat-application/AppService/AS-IP-FIN-003-住院结算.md |
| `docs/应用/zxplat-application/AppService-SDD/AS-IP-FIN-002-住院预交金-SDD.md` |
| `docs/应用/zxplat-application/AppService/AS-OP-FIN-001-门诊挂号.md`（支付结算节奏参照） |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v2.7 | 2026-10-13 | **BR-IP-PREP-001**：不在院三态须 **EXISTS 已收口预交金流水** 才入队（无流水无查阅意义）；「全部」同口径；收退写不变 |
| v2.6 | 2026-10-13 | **代码先行回写**：IF-IP-PREP-01 以 `stayScope`（`IN_HOSPITAL`/`NOT_IN_HOSPITAL`/`ALL`）替代历史 `prepayQueueTab`；**不在院**含 `COMPLETED`+`DISCHARGED`+**`CANCELLED`（退院）** 只读查阅历史预交金；收退写门禁不变 |
| v2.5 | 2026-10-11 | IF-IP-PREP-03：**组合收款**（多渠道明细）；**一渠道一流水**；扫码至多一条；UI 支付明细行 |
| v2.4 | 2026-10-11 | 对齐登记 **一渠道一流水**：流水行金额/支付方式各对应一笔渠道收款 |
| v2.3 | 2026-10-11 | IF-IP-PREP-02：过滤并软删 `CANCELLED` 预交金；支付方式多明细拼展示；对齐登记意向草稿软删口径 |
| v2.2 | 2026-08-17 | **IF-IP-PREP-01**：队列增 `prepayQueueTab`（`IN_HOSPITAL` / `DISCHARGED`）；已出院可查阅流水不可收退；对齐 `InpatientPrepayQueueTab` 与 Encounter 域筛选 |
| v2.1 | 2026-06-21 | **展示文案简化**：卡片/队列仅输出单一余额（如 `500.00 元` / `-50.00 元`，正负号区分）；**不再**拆分「可用/待结算」主副文案；数值仍为库内 balance − 待结算 |
| v1.14 | 2026-06-20 | **support 落点对齐**：队列展示余额 DTO 回填 **`InpatientAccountBalanceDisplayDtoApplier`** 归 `module.common.assembler.inpatient`（与护士站/医生站/结算同口径）；详见 **AS-IP-FIN-002-SDD** |（已迁至 application.shared.inpatient）
| v1.12 | 2026-05-30 | IF-IP-PREP-02：`patientId` 可选门控；`COMPLETED` 待清床可只读查流水 |
| v2.0 | 2026-06-19 | **账户余额语义**：`balance` 含结算扣已结；展示 = 库内余额 − 待结算（**不**再减 `SETTLED`）；退费上限 = 库内 `balance` |
| v1.11 | 2026-05-30 | **BR-IP-PREP-001** 拆分队列可见（含 `COMPLETED` 待清床）与收退写门禁（四态）；IF-IP-PREP-01 文案对齐 |
| v1.10 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader） |（已迁至 EncounterContextReader / application.shared.encounter）
| v1.0 | 2026-05-19 | 初版：预交金管理 IF-IP-PREP-01～04；对齐 `/inpatient/charge/prepay` UI；可收退状态与 **DM-ADM-002 v1.11** 一致；登记首笔归 **AS-IP-FIN-001** |
| v1.1 | 2026-05-19 | 与聊天/DM 对齐复核：明确 UI「在院患者」含待入科；禁止态补全；登记必开户；新增 BR-IP-PREP-009（`PLANNED` 归登记页） |
| v1.2 | 2026-05-19 | 文档编号 **AS-IP-FIN-003→002**（文件名同步）；BR-IP-PREP-003 补充支付明细必填 |
| v1.3 | 2026-05-19 | 退费：**不绑原单**、≤账户余额、可改数；收/退统一 **DRAFT+settle**；Gap 改为实现落地跟踪 |
| v1.4 | 2026-05-19 | Gap 关闭：`DM-FIN-002` v1.4 + whale `refundAdvanceDeposit` 与事件对齐 |
| v1.5 | 2026-05-19 | 关联 **AS-IP-FIN-002-SDD** v1.0（应用层 IF-01～04 成码输入） |
| v1.6 | 2026-05-19 | 关联 **AS-IP-FIN-002-SDD** v1.2：余额编排落 `AccountDomainService#adjustAdvanceDepositBalance` |
| v1.8 | 2026-05-20 | IF-IP-PREP-01 队列展示回填 + `contractDisplay`；IF-IP-PREP-02 `operatorName`/`payMethodText` 展示闭合（对齐 **AS-IP-FIN-002-SDD** v1.7） |
| v1.7 | 2026-05-19 | IF-IP-PREP-01 队列废止欠费/预交金不足布尔标记（原 `arrearsAlert`）；左侧仅返回 `accountBalance` 等摘要字段 |
| v1.10 | 2026-05-24 | 待结算合计成码：**DM-ADM-011** Criteria **`SUM`+`GROUP BY`**（§6.2.13～6.2.14 / SDD §5.2） |
| v1.9 | 2026-05-24 | 展示余额改为 **预交金净值 − 待结算合计**（`PENDING_SETTLEMENT`）；`PLANNED` 待收费不扣减；依赖 `ChargeItemQueryService` 批量汇总 |
| v1.8 | 2026-05-19 | `accountBalance` 改为展示口径（预交金净值 − 累计费用）；Account.balance 仅内部计算与退费上限；装配器上收 `module.common.assembler.inpatient.InpatientDisplayAccountBalanceAssembler` |（已迁至 application.shared.inpatient）
| v1.9 | 2026-05-20 | **§3 Gap 收口**：删除「应用层已落地」类跟踪表；改为**无待跟踪缺口**（应用层成码非 Gap） |

---

**文档负责人**：（待填） · **评审**：（待填）
