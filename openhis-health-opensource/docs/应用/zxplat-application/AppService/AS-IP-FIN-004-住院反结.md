# 住院反结 · 应用服务设计（AS）

> **文档职责**：说明住院收费「出院/在院结算」工作台（`/inpatient/charge/settle`）**已结算侧**的**反结**契约（IF）、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；与 **AS-IP-FIN-003** 正向结算**按单笔支付单对称**。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：与 **AS-IP-FIN-003** 共用 `/inpatient/charge/settle`。**读侧**（已结算队列、结算单查阅）复用 **IF-IP-STL-02、07**；**写侧**本 AS 仅定义 **IF-IP-RVS-01 住院反结**。收银台「已结算」Tab 选中患者 → 选定成功支付单 → **反结**。
>
> **覆盖声明**：完整契约以 **§2 IF-IP-RVS-01** 为准；写侧编排与 **IF-IP-STL-06** 对称（逆向支付单 → ChargeItem **复制再生** → 条件就诊/预交金/发票回退 → **软删出院医嘱**）；**本期无医保结算撤销（2305）**；进销存清床汇总冲销 **3506** 见主路径 afterCommit。
>
> **编码**：AS-IP-FIN-004 · **版本**：v1.8 · **日期**：2026-10-09  
> **文档链路**：AS → `AS-IP-FIN-004-住院反结-SDD.md` v1.10（**已建**）→ App **`inpatient.financial.settle`**（与 **AS-IP-FIN-003** **同包**；**IF-IP-RVS-01** 反结 + **IF-IP-RCL-01** 出院召回）| 对称 **AS-IP-FIN-003** | 召回**仅**收银台已结算历史（**非**护士站）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | **IF-IP-RVS-01**：按**单笔**成功 `INPATIENT_CLINIC` 支付单执行住院反结——逆向支付单、本批 ChargeItem **复制再生**（源 `SETTLED` 保持终态，新行 `PENDING_SETTLEMENT` + `refund_id`）、条件对称回退出院全结项（就诊 **`COMPLETED→IN_PROGRESS`**、清空 `end_time`/`out_way_code`、发票作废/冲红）、**软删出院医嘱**（`DRAFT`/`ACTIVE`/`VERIFIED`/`COMPLETED` → **逻辑删除**、列表不再展示，医生须**重新开立**；**禁止**留 `CANCELLED`）。**IF-IP-RCL-01**：收银台已结算历史对 **`DISCHARGED`** 患者执行**出院召回**——复用反结内核（`allowDischarged=true`）+ 领域 `recallInpatientDischarge`（**`DISCHARGED→IN_PROGRESS`** + INSERT 活动 **WARD**，**无**活动床、`requiresBedAssignment=false`） |
| **在读侧复用（不写 IF）** | **AS-IP-FIN-003** **IF-IP-STL-02**（队列）、**IF-IP-STL-07**（已结算单查阅与切换 `paymentId`；**`reverseEnabled`/`recallEnabled`** 门禁）——写操作前须先定位目标支付单 |
| **在外** | 住院登记（**AS-IP-FIN-001**）；预交金续收/退费（**AS-IP-FIN-002**）；正向预结算/结算（**AS-IP-FIN-003** IF-IP-STL-05/06）；护士站清床（**AS-IP-NUR-002** ATD-06）；**护士站召回写入口已废止**（产品入口迁至本 AS **IF-IP-RCL-01**）；医保预结/正式结/平台撤销（**§1.4 非目标**）；电子发票开具与三类打印；住院日结（另 AS）；第三方支付协议细节 |
| **涉及域** | PaymentReconciliation（逆向单 `UN_PAY`/`REFUNDED`；原正向单保持 `SUCCESS`）；ChargeItem（本批回退）；Encounter（**仅本笔为出院全结时** `COMPLETED→IN_PROGRESS`）；Account（出院全结多退预交金对称回补）；Invoice（作废草稿/冲红已开）；ServiceRequest（出院类诊疗**取消**）；ProductDefinition（读，仅经 FIN-003 费用快照） |
| **编排权威** | 以本文 **§2、§4** 为准；与 **AS-IP-FIN-003** **BR-IP-STL-003/004/006/014** 对称 |
| **调用关系** | **反结不依赖召回**；**召回（仅 `DISCHARGED`）**在**同一 AppService** 内先复用反结内核再调领域召回。**未清床**（`COMPLETED` 仍占床）仅收银台 **IF-IP-RVS-01** 反结；**已清床**（`DISCHARGED`）仅收银台 **IF-IP-RCL-01** 召回 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院收费员** | 在「已结算」队列选中患者 → 查阅结算单（FIN-003 IF-IP-STL-07）→ 选定一笔成功支付单 → **非 `DISCHARGED`** 点 **反结**（IF-IP-RVS-01）→ 患者回**已入科**在院流程（医生须**重新开立**出院医嘱 → 护士再校对/办理出院后方可再结算）；**`DISCHARGED`** 点 **召回**（IF-IP-RCL-01）→ 患者回 **`IN_PROGRESS`**（活动 **WARD**、**无**活动床，**不**须二次分床） |
| **系统** | **IF-IP-RCL-01** 同事务：反结内核（`allowDischarged=true`）+ `EncounterDomainService#recallInpatientDischarge` |
| **系统** | 校验原单门禁；生成本批逆向流水；回退绑定 ChargeItem；条件对称回退出院全结副作用；**取消**出院医嘱并清空业务出院时间；发布取消类领域事件 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **反结** | 业务用语；**禁止**称「取消结算」。按**单笔**成功 `INPATIENT_CLINIC` 支付单整批逆向，**一次结算 ↔ 一次反结** |
| **本批** | 原支付单 `chargeItemIds` 绑定的全部 ChargeItem；反结**不得**跨单、**不得**部分反结 |
| **中途结算反结** | 原单对应 FIN-003 **中途结算**（子集费用）；反结后 ChargeItem 回 **`PENDING_SETTLEMENT`**，**禁止**改 `Encounter.status_enum`（即使就诊已为 `COMPLETED`，因后续可能另有出院全结单） |
| **出院全结反结** | 原单对应 FIN-003 **出院全结**（当次结清全部剩余待结算项并推进 `COMPLETED`）；反结须**对称**回退：**`COMPLETED→IN_PROGRESS`**（清空 `end_time`/`out_way_code`，**保留**活动床/参与者）、发票作废/冲红；**不**自动回到 `PENDING` 待结算窗口 |
| **判定本笔是否出院全结** | 以支付单 **`kind_enum`** 为准：本期 **`INPATIENT_CLINIC` = 出院结算**；中途结算（后续）为独立 `kind_enum`；**禁止**仅用就诊 `COMPLETED` 推断 |
| **账户余额（反结）** | **出院结算反结**（`INPATIENT_CLINIC`）：有冲抵明细 **O>0** 时须 **`adjustAdvanceDepositBalance(+O)`** 回冲个人现金户（**DOC-CONFLICT→已按 DD §3.5 收敛**；禁止再结重复计收）；本轮不强制追回已退预交金 UN_PAY。**中途结算反结**（后续大类）：**回冲**本批已结扣款 `balance += 回退合计` |
| **ChargeItem 再生** | 源 `SETTLED` **保持终态**；插入 `PENDING_SETTLEMENT` 新行并 `refund_id` 链回源行（**BR-IP-RVS-011**）；可再次进入 FIN-003 结算 |
| **逆向支付** | 新建 `UN_PAY`、关联原单的逆向 `INPATIENT_CLINIC` 流水 → 逆向单收口 **`REFUNDED`**；原正向单保持 **`SUCCESS`**；语义见 **DM-FIN-002** §6.1.7～§6.1.7a |
| **软删出院医嘱** | 出院类诊疗（`category_enum=34`）**逻辑删除**（**DM-WKF-004** `softDeleteDischargeServiceRequestForReverseSettle`；含草稿/已发送/已校对/已完成）；**不**迁 `CANCELLED`；清空就诊 `end_time`/`out_way_code`；业务出院时间解析**不再认**软删/草稿嘱（仅生效态）；医生须**重新开立**；**归反结编排**，**不归**护士站召回 |
| **未清床反结入口** | 就诊 `COMPLETED` 且仍占床 → **仅**收银台已结算历史 **IF-IP-RVS-01**（**`reverseEnabled=true`**） |
| **已清床召回入口** | 就诊 `DISCHARGED` → **禁止** IF-IP-RVS-01 反结（**EX-IP-RVS-003**：已离院请先召回）；须同页 **IF-IP-RCL-01**（**`recallEnabled=true`**） |
| **召回后二次结算** | 召回后 **`IN_PROGRESS` 且无活动床**；再办出院全结时 **`completeInpatientFinancialSettlement`** **直接 `DISCHARGED`**（收口活动 WARD），**跳过**护士清床（见 **AS-IP-FIN-003** IF-IP-STL-06） |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期（契约）** | IF-IP-RVS-01；现金/渠道原路语义沿用原单明细；**取消**出院医嘱纳入反结主路径 |
| **本期（UI 接通）** | 已结算 Tab → 选支付单 →「反结」按钮（UI 文案待与「反结」对齐；当前实现或为「取消结算」） |
| **非目标（产品/UI）** | 部分反结（只反部分 ChargeItem）；跨多支付单合并反结；独立「反结预览」IF |
| **非目标（医保）** | 2303/2304/2305 及平台撤销编排；医保分项展示与费别切换 |
| **非目标（打印/电票）** | 反结后自动重打；`invoiceZYMake` |
| **非目标（老能力）** | 老系统 `inpa-un-pay` 口径（ChargeItem→`PLANNED`、Encounter 旧态码）；按预交金净值**全额**退 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **PaymentReconciliation** | 逆向单、`cancelInpatientSettleAsRefund`、`completeInpatientSettleCancelRefund` | `DM-FIN-002-PaymentReconciliation-支付流水.md` §6.1.7～§6.1.7a |
| **ChargeItem** | 本批 `revertInpatientSettledChargeItems`：源 `SETTLED` 不变，复制插入 `PENDING_SETTLEMENT` + `refund_id` | `DM-ADM-011-ChargeItem-收费明细.md` |
| **Encounter** | **仅出院全结反结**：`revertInpatientFinancialSettlement`（`COMPLETED→IN_PROGRESS`，`recallFromCompletedToInProgress`） | `DM-ADM-002-Encounter-就诊.md` |
| **Account** | 出院全结多退预交金对称回补（`adjustAdvanceDepositBalance`） | `DM-ADM-009-Account-就诊账户.md` |
| **Invoice** | 关联票据草稿作废 / 已开冲红 | `DM-ADM-012-Invoice-票据发票.md` |
| **ServiceRequest** | 出院嘱 `softDeleteDischargeServiceRequestForReverseSettle` | `DM-WKF-004-ServiceRequest-服务申请.md` §6.1.9b |
| **Encounter / ChargeItem / Payment（读）** | 原单加载、门禁、结算单快照 | 同 **AS-IP-FIN-003**；**DM §6.2** QueryService |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-RVS-001** | 仅 **`IMP`** 就诊；原单须 **`INPATIENT_CLINIC` + `SUCCESS`**；`paymentId` / `encounterId` / `patientId` 与请求**一致** | IF-IP-RVS-01 |
| **BR-IP-RVS-002** | **一笔结算 ↔ 一次反结**：仅允许按**单张**原支付单整批反结；**禁止**重复反结（原单已存在逆向关联） | IF-IP-RVS-01 |
| **BR-IP-RVS-003** | 本批绑定 ChargeItem 须均为 **`SETTLED`**；反结后插入 **`PENDING_SETTLEMENT` 再生行**（源行不变），可再次参与 FIN-003 结算 | IF-IP-RVS-01 |
| **BR-IP-RVS-011** | 反结**禁止**改写源 `SETTLED` 行；仅对支付单 `charge_item_ids` 快照再生；新行须复刻源行业务锚点；**多轮**结算/反结可形成多层 `refund_id` 链，已完成轮次支付单封存；读侧/Gate 按 **BR-REF-020** **递归**追溯整条链 | IF-IP-RVS-01 |
| **BR-IP-RVS-004** | 逆向支付单创建并收口为 **`REFUNDED`**（`payment_enum=UN_PAY`）；原正向结算单保持 **`SUCCESS`**；发布 **`InpatientSettleCancelledEvent`** | IF-IP-RVS-01 |
| **BR-IP-RVS-005** | **中途结算反结**：**仅**回退本批支付 + ChargeItem；**禁止**变更 `Encounter.status_enum`、**禁止**动出院全结预交金/发票/就诊财务态 | IF-IP-RVS-01 |
| **BR-IP-RVS-006** | **出院全结反结**（**本笔**为出院全结单）：对称 FIN-003 **BR-IP-STL-003/008**——`COMPLETED→IN_PROGRESS`（清空出院时间/离院方式，**保留**占床）；发票草稿作废或已开冲红；**有冲抵明细 O>0 时须回冲**个人现金户 `balance += O`（**DD §3.5**；无冲抵可不回冲） | IF-IP-RVS-01 |
| **BR-IP-RVS-007** | 若存在可软删**出院类**诊疗（`category_enum=34`，态 ∈ `DRAFT`/`ACTIVE`/`VERIFIED`/`COMPLETED`），反结**须** **`softDeleteDischargeServiceRequestForReverseSettle`** 逻辑删除（医生重新开立；**禁止** `CANCELLED` 留痕）；并清空就诊 `out_way_code` / `end_time`；业务出院时间解析跳过软删/草稿嘱 | IF-IP-RVS-01 |
| **BR-IP-RVS-008** | 就诊 **`DISCHARGED`** **禁止** IF-IP-RVS-01 反结（**EX-IP-RVS-003**：**已离院请先召回**）；须同页 **IF-IP-RCL-01** | IF-IP-RVS-01 |
| **BR-IP-RCL-001** | **出院召回门禁**：就诊 `IMP` 且 **`status_enum = DISCHARGED`**；`COMPLETED`（未清床）**禁止**召回，须 **IF-IP-RVS-01** 反结 | IF-IP-RCL-01 |
| **BR-IP-RCL-002** | **召回内嵌反结**：IF-IP-RCL-01 **须**先复用 **IF-IP-RVS-01** 反结内核（`allowDischarged=true`；按该就诊末次正向 SUCCESS 出院全结单）；**反结成功前禁止**改 `Encounter`/`adm_encounter_location` | IF-IP-RCL-01 |
| **BR-IP-RCL-003** | **召回位置恢复**（反结成功后）：`DISCHARGED→IN_PROGRESS`；末次 `COMPLETED` **WARD** 处 **INSERT** 新 `ACTIVE` **WARD**；**不** INSERT 活动床；**不**恢复参与者；`requiresBedAssignment=false` | IF-IP-RCL-01 |
| **BR-IP-RVS-009** | 反结成功后本批 ChargeItem 为 `PENDING_SETTLEMENT` 再生行，**可再次参与** FIN-003 结算；**不**因仅有待结算费用即入 IF-02「待结算」队列（队列仅 `PENDING`）；须医生**重新开立**出院嘱 → 护士校对/办理出院 → `PENDING` 后方可结算 | IF-IP-RVS-01 |
| **BR-IP-RVS-010** | 判定是否执行 **BR-IP-RVS-006** 须依据**本支付单**是否为出院全结，**不得**因就诊当前为 `COMPLETED` 而对中途结算单误做就诊回退 | IF-IP-RVS-01 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-RVS-01 | 已结算 Tab 选中患者并选定支付单后点「反结」 | 按 `paymentId` 执行住院反结：逆向流水 + 本批 ChargeItem 复制再生 + 条件出院全结对称项 + **软删**出院医嘱 | 写 | BR-IP-RVS-001～011 | 源 ChargeItem 保持 `SETTLED`；插入 `PENDING_SETTLEMENT` 再生行；逆向支付→`REFUNDED`（原单仍 `SUCCESS`）；条件 Encounter→`IN_PROGRESS`；出院嘱→逻辑删除 | PaymentReconciliation / ChargeItem / Encounter / Account / Invoice / ServiceRequest | 原单读侧（FIN-003 IF-IP-STL-07 同源） |
| IF-IP-RCL-01 | 已结算 Tab 选中 **`DISCHARGED`** 患者并选定支付单后点「召回」 | 出院召回：复用反结内核（`allowDischarged=true`）+ `recallInpatientDischarge` | 写 | BR-IP-RCL-001～003 | `DISCHARGED→IN_PROGRESS` + INSERT 活动 **WARD**（**无**活动床）；`requiresBedAssignment=false` | 同 IF-IP-RVS-01 + Encounter / EncounterLocation | 同 IF-IP-STL-07 |

> **读侧不增 IF**：队列、已结算单查阅、切换 `paymentId` 均沿用 **AS-IP-FIN-003 IF-IP-STL-02、07**。

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口。**

> **领域层已关闭（2026-06-18）**：**GAP-IP-RVS-001** — `DM-FIN-002` §6.1.7 与 **DM-ADM-011** 口径统一为 **`PENDING_SETTLEMENT`**；**GAP-IP-RVS-002** — `revertInpatientSettledChargeItems` 已发布 **`InpatientChargeItemsRevertedEvent`**。  
> **应用层（2026-06-18 已落地）**：`reverseInpatientSettle`、IF-IP-STL-07 历史 NET、前端历史行「反结」——由 **AS-IP-FIN-004-SDD** 驱动，**不是** Gap。  
> **2026-07-16**：**IF-IP-RCL-01** `recallInpatientDischarge` 迁入 **`InpatientSettleAppService`**（收银台已结算历史）；废止护士站 ATD-07 写入口与 `RecallReverseSettleSupport`。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-RVS-01 住院反结

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在 **FIN-003 已结算 Tab** 选中 `IMP` 患者；已通过 **IF-IP-STL-07** 加载并选定一笔 **`SUCCESS` + `INPATIENT_CLINIC`** 支付单；`paymentId`、`encounterId`、`patientId` 齐备；就诊**非** `DISCHARGED` |
| **主路径步骤** | ① Gate：操作员登录；请求三主键非空（**BR-IP-RVS-001**）。② Pre：加载原支付单，须 `SUCCESS` + `INPATIENT_CLINIC`；患者/就诊与单头一致；就诊 `IMP`；**不存在**有效逆向关联（**BR-IP-RVS-002**）。③ Pre：解析本批 `chargeItemIds`；逐项须 `SETTLED` 且属本就诊。④ Pre：判定**本笔**为中途结算或出院全结（**BR-IP-RVS-010**）。⑤ Pre：解析可软删出院类诊疗 id（**BR-IP-RVS-007**；态含草稿）。⑥ Cmd：创建逆向支付单（`UN_PAY`、`REFUND_PROCESSING`）并复制原渠道明细语义。⑦ Cmd：本批 ChargeItem **复制再生**（源 `SETTLED` 不变，插入 `PENDING_SETTLEMENT` + `refund_id`，**BR-IP-RVS-003/011**）。⑧ Cmd（**仅本笔出院全结**）：就诊 **`COMPLETED→IN_PROGRESS`**（`revertInpatientFinancialSettlement` / `recallFromCompletedToInProgress`，清空 `end_time`/`out_way_code`）；发票草稿作废或已开冲红（**BR-IP-RVS-006**，对称 **BR-IP-STL-008**）；**中途结算反结跳过本步**（**BR-IP-RVS-005**）。⑨ Cmd：出院类诊疗 **`softDeleteDischargeServiceRequestForReverseSettle`** 逻辑删除（**BR-IP-RVS-007**；医生须重新开立；列表不再展示）。⑩ Cmd：逆向单收口 **`REFUNDED`**，原单保持 **`SUCCESS`**（**BR-IP-RVS-004**）。⑪ afterCommit：若本就诊已有成功清床汇总 3505，异步冲销 **3506**（未清床反结 **no-op**；见 **09** / **AS-IP-NUR-002** **BR-IP-ATD-020**）。⑫ 返回逆向支付单 id 及成功标识 |
| **关键分支** | **本期无中途结算**：一律按出院全结对称项执行 **BR-IP-RVS-006**（**BR-IP-RVS-005/010** 待中期结算上线）。**须存在**可软删出院嘱，否则 **EX-IP-RVS-004**。**`DISCHARGED`**：步骤 ② 拒绝（**EX-IP-RVS-003**：已离院请先召回），须 **IF-IP-RCL-01** |
| **失败分支** | 原单非 SUCCESS / 已反结 / 三主键不一致 → **EX-IP-RVS-001**；本批 ChargeItem 非 SETTLED → **EX-IP-RVS-002**；就诊 DISCHARGED → **EX-IP-RVS-003**；无出院嘱可退而业务要求存在 → **EX-IP-RVS-004**；持久化或编排失败 → **EX-IP-RVS-005**（整单回滚） |
| **查询侧说明** | 成功后刷新 FIN-003 已结算队列；本批费用回到 `PENDING_SETTLEMENT` 再生行；若出院全结反结且未清床，就诊回 **`IN_PROGRESS`（已入科）**，**不**入待结算队列；**待结算 Tab 与已结算 Tab** 均可通过 **IF-IP-STL-07** 只读展示一正一负历史与净额（反结写操作仍限已结算 Tab 且未清床） |

#### 与 IF-IP-STL-06 对称一览

| 正向（IF-IP-STL-06） | 反结（IF-IP-RVS-01） |
| --- | --- |
| 支付 `DRAFT→SUCCESS` | 逆向单 `UN_PAY`→`REFUNDED`；原正向单保持 `SUCCESS` |
| ChargeItem `PENDING_SETTLEMENT→SETTLED` | 反结：源 `SETTLED` 不变；插入 `PENDING_SETTLEMENT` 再生行 |
| 发票草稿创建 | 发票草稿作废 / 已开冲红 |
| 出院全结：`PENDING→COMPLETED` + 多退预交金 | **本笔出院全结单**：`COMPLETED→IN_PROGRESS` + 清空出院时间/离院方式 |
| 中途结算：不改 Encounter | **本笔中途单**：不改 Encounter |
| （结算不撤出院嘱） | **软删出院医嘱**（列表不再展示；医生重新开立） |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-RVS-001 | 原单非成功态、非住院结算单、三主键不一致、已反结过 | 请刷新并重新选择结算单 | 是 |
| EX-IP-RVS-002 | 本批费用非已结算态 | 结算单数据已变化，请刷新 | 是 |
| EX-IP-RVS-003 | 就诊已清床离院（`DISCHARGED`）本页点反结 | **已离院请先召回** | 否 |
| EX-IP-RVS-004 | 须软删出院嘱但未找到可软删医嘱 | 未找到可撤回的出院医嘱 | 否 |
| EX-IP-RVS-005 | 反结持久化失败 | 反结失败，请以流水为准后重试 | 视原因 |
| EX-IP-RVS-101 | 原单加载失败 | 结算单加载失败，请稍后重试 | 是 |
| EX-IP-RCL-001 | 非 `DISCHARGED`（含未清床 `COMPLETED`） | 当前状态不允许出院召回，请刷新 / 未清床患者请直接反结 | 否 |
| EX-IP-RCL-002 | 内嵌反结失败 | 反结未成功，无法召回 | 否 |
| EX-IP-RCL-003 | 反结阶段无出院嘱/不可退回 | 未找到可撤回的出院医嘱 | 否 |
| EX-IP-RCL-004 | 召回持久化失败 | 出院召回失败，请刷新后重试 | 视原因 |

---

### IF-IP-RCL-01 出院召回

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在 **FIN-003 已结算 Tab** 选中 **`DISCHARGED`** 患者；已通过 **IF-IP-STL-07** 加载结算历史；正向 **`SUCCESS`** 且未反结；`encounterId`、`patientId` 齐备 |
| **主路径步骤** | ① Gate：三主键非空（**BR-IP-RCL-001**）。② Pre：就诊 `IMP` 且 **`DISCHARGED`**（`COMPLETED` → **EX-IP-RCL-001** 未清床请直接反结）。③ Pre：解析末次 `COMPLETED` **WARD**（无则 **EX-IP-RCL-004**）。④ Pre：解析末次正向 SUCCESS 出院全结 `paymentId`（请求指定或自动解析；无则 **EX-IP-RCL-002**）。⑤ Cmd：复用 **IF-IP-RVS-01** 反结内核（`allowDischarged=true`；**跳过** `revertInpatientFinancialSettlement`；**BR-IP-RCL-002**）。⑥ Cmd：`EncounterDomainService#recallInpatientDischarge`：`DISCHARGED→IN_PROGRESS` + **INSERT** 活动 **WARD**（**不**占床；**BR-IP-RCL-003**）。⑦ 返回 `targetStatusEnum=IN_PROGRESS`、`requiresBedAssignment=false` |
| **关键分支** | ⑤～⑥ **同事务**；任一步失败整单回滚；**不**须护士 **ATD-02** 二次分床 |
| **失败分支** | 状态/三主键 → **EX-IP-RCL-001**；反结失败 → **EX-IP-RCL-002**；无出院嘱 → **EX-IP-RCL-003**；持久化 → **EX-IP-RCL-004** |
| **查询侧说明** | 成功后刷新 FIN-003 已结算/待结算队列；患者 **`IN_PROGRESS`** 可再办出院→结算；**无活动床**时二次全结直接 **`DISCHARGED`**（**FIN-003** IF-IP-STL-06） |

> **实现备注（非契约）**：应用层已统一使用 **EX-IP-RVS-001～005**（`InpatientSettleConstants`）；不再映射 EX-IP-STL-106/109。

---

## 6. 事件语义与消费方

| 事件语义（DM 英文名以 DM 为准） | 触发相位（IF） | 典型消费方 |
| --- | --- | --- |
| 住院结算已取消 / `InpatientSettleCancelledEvent` | IF-IP-RVS-01（逆向单 `REFUNDED` 收口后） | 支付流水读侧；日结；队列刷新 |
| 出院嘱已取消 / `ServiceRequestCancelledEvent` | IF-IP-RVS-01（**BR-IP-RVS-007**） | 医嘱列表；业务出院时间清空；医生须重新开立 |
| 支付状态已编辑 / `PaymentStatusEditedEvent` | IF-IP-RVS-01（多退预交金流水标 `REFUNDED` 时） | 账户流水读侧 |
| ChargeItem 再生 | IF-IP-RVS-01 | **`InpatientChargeItemsRevertedEvent`**（携带新行主键） | 待结算合计由 QueryService 重算 |
| 就诊财务回退 | IF-IP-RVS-01（仅本笔出院全结） | InpatientEncounterContextQueryModel；FIN-003 队列；预交金页门禁 |
| 出院召回 | IF-IP-RCL-01 步骤 ⑥ | `InpatientDischargeRecalledEvent`（`DISCHARGED→IN_PROGRESS`；`requiresBedAssignment=false`）；001/002 列表刷新 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能（经验目标）** | 单笔反结 ≤ 2000ms（不含后续医保） |
| **幂等** | 同一 `paymentId` 已成功反结后，再次提交须拒绝（**BR-IP-RVS-002**） |
| **并发** | 同一原支付单并发反结以后者拒绝或原单状态门禁为准 |
| **一致性** | 单笔反结：逆向单、ChargeItem 复制再生、条件 Encounter/Account/Invoice/出院嘱须在**同一 App 事务**内成功或整单回滚 |
| **审计** | 须可追溯操作员、时间、原 `paymentId`、逆向 `paymentId`、本批 `chargeItemIds` |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **中途结算反结** | 在院患者两笔 SUCCESS 单；反结较早中途单 → 仅该批 ChargeItem 回 `PENDING_SETTLEMENT`；Encounter 不变；仍可查阅另一笔已结单 |
| **出院全结反结（未清床）** | `COMPLETED` 占床；反结出院全结单 → `IN_PROGRESS`；发票处理；出院嘱回草稿；须重新校对→办理出院→再结算 |
| **禁止** | `DISCHARGED` 本页反结 → EX-IP-RVS-003（已离院请先召回）；重复反结 → EX-IP-RVS-001；非 SETTLED 行 → EX-IP-RVS-002 |
| **召回（IF-IP-RCL-01）** | `DISCHARGED` 已结算历史点召回 → `IN_PROGRESS`+WARD、无活动床；`requiresBedAssignment=false`；二次全结无床直接 `DISCHARGED` |
| **对称** | 反结后本批费用可再次走 FIN-003 预结算/结算；展示余额与 **BR-IP-NUR-006** 口径一致 |
| **非目标** | 无医保撤销；无部分反结 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档（全链路）

| 层级 | 文档 / 代码落点 | 本场景用途 |
| --- | --- | --- |
| **AS** | 本文 | IF-IP-RVS-01、BR、异常、事件 |
| **AS（对称）** | `AS-IP-FIN-003-住院结算.md` | IF-IP-STL-06/07；BR-IP-STL-003/004/014；召回后二次全结 |
| **AS-SDD** | `AS-IP-FIN-004-住院反结-SDD.md` v1.8 | 编排序列、Request/Response、成码锚点 |
| **App** | `health-application/.../inpatient/financial/settle/`（与 FIN-003 **同包**） | `reverseInpatientSettle`、`recallInpatientDischarge`、STL-07 NET |
| **DM·支付** | `DM-FIN-002` §6.1.7 | 逆向单、`InpatientSettleCancelledEvent` |
| **DM·收费** | `DM-ADM-011` | `revertInpatientSettledChargeItems` |
| **DM·就诊** | `DM-ADM-002` §6.1.20a 对称、`revertInpatientFinancialSettlement` | 出院全结反结 |
| **DM·医嘱** | `DM-WKF-004` §6.1.9b | `softDeleteDischargeServiceRequestForReverseSettle` |
| **DM·票据/账户** | `DM-ADM-012`、`DM-ADM-009` | 发票与预交金对称 |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.8 | 2026-10-09 | **BR-IP-RVS-007**：反结出院嘱改为 **`softDeleteDischargeServiceRequestForReverseSettle`** 逻辑删除（列表不再展示；禁止 `CANCELLED` 留痕）；对齐 **AS-SDD v1.11** / **DM-WKF-004** |
| v1.7 | 2026-10-09 | **BR-IP-RVS-007**：反结出院嘱由退回草稿改为 **`cancelServiceRequest`→`CANCELLED`**（医生重新开立）；业务出院时间仅认生效态嘱；对齐 **AS-SDD v1.10** / **DM-WKF-004** |
| v1.6 | 2026-07-19 | **IF-IP-RVS-01 / RCL-01**：afterCommit 冲销清床汇总销售 **3506**（仅已成功清床 3505；未清床 no-op）；对齐 **09** / **AS-IP-NUR-002** |
| v1.5 | 2026-07-16 | **出院召回 redesign**：新增 **IF-IP-RCL-01**（收银台已结算历史）；**BR-IP-RCL-001～003**；**EX-IP-RCL-001～004**；**EX-IP-RVS-003** 改为「已离院请先召回」；召回 **`DISCHARGED→IN_PROGRESS`**+INSERT WARD（无床、`requiresBedAssignment=false`）；废止护士站 ATD-07 写入口与 **`RecallReverseSettleSupport`**；IF-IP-STL-07 **`recallEnabled`/`reverseEnabled`** 分轨 |
| v1.3 | 2026-07-11 | **出院全结反结就诊回退**：`COMPLETED→IN_PROGRESS`（非 `PENDING`）；对称 **BR-IP-RVS-006/009**；与 **FIN-003 BR-IP-STL-011** 待结算队列仅 `PENDING` 对齐 |
| v1.2 | 2026-06-19 | IF-IP-STL-07 反结后仍可只读查历史；待结算 Tab 展示历史 NET；对齐账户余额：出院反结不回冲 balance |
| v1.0 | 2026-06-18 | 初版：**IF-IP-RVS-01**；与 **AS-IP-FIN-003** 对称；一笔结算↔一次反结；撤销出院医嘱归反结；**无医保** |

---

**文档负责人**：（待填） · **评审**：（待填）
