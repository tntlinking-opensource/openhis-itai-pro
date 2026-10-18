# 住院日结交账 · 应用服务设计（AS）

> **文档职责**：说明「住院收费日结 / 交账」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图。
>
> **与门诊对称（v4.2）**：除 `type_enum = IP_PAYMENT`、纳入 **kind** 及住院特有展示（病区 `wardName`、预交金 `depositAmount`、多退少补 `depositRefundAmount`/`settleSupplementAmount`、`fin_type` 排除废止挂号费 `1011` 与体检、**保留诊察费 `1012`**）外，**IF 语义、漏交栏、历史/撤回/重打、收款人多选软筛、`mountedVoucher`、`statusEnumText`、头表 `total_income` 现算等与 AS-OP-FIN-004 v4.1 一致**。共用 **DM-FIN-003**；交叉引用门诊 AS/SDD 共用章节即可，本文仅展开住院差异。
>
> **页面边界**：住院日结页 — 选择交账科室与日结时间窗 → **日结查询（未交账预览）** → 核对汇总（**日结合计 / 患者分项**双 Tab；含病区/预交金分类）→ **交账明细弹窗**（默认全选、逐笔可取消）→ **确认交账**；**交账历史**分页、**合计/患者分项原单重打**、**待审撤回**；右侧**医保对账侧栏**嵌 CHS **日对账**（3201/3202，复用 `DailyLedgerList` compact，`clrType=21`）；财务审批走 **AS-FIN-CS-001**；不含导出 PDF 占位收口。
>
> **编码**：AS-IP-FIN-005 · **版本**：v4.19 · **日期**：2026-10-14

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-DCL-01～06；`kind ∈ {INPATIENT_CLINIC, HOSPITAL_DEPOSIT}`（含历史 `PRE_DEPOSIT`）；病区维度展示；**患者分项只读块**（`patientSummaries`，**BR-IP-DCL-030**）；交账明细弹窗与 `paymentIds` 勾选提交；交账历史/合计与患者分项重打/待审撤回；实现落点 **health-application / inpatient / financial / dailysettlement**；**FE** 右侧侧栏嵌 CHS **日对账**（`DailySettlementChsReconPanel` → `DailyLedgerList` compact；对账日默认跟日结窗起点；业务类型默认住院 `clrType=21`） |
| **在外** | 住院登记/预交金/结算/反结 AS；门诊日结（**AS-OP-FIN-004**）；交账审批（**AS-FIN-CS-001**）；护士站「日费用清单」打印；医保**月清算申请/状态查询/工伤对账**全量（走 `/chs/reconciliation` 完整页，侧栏仅链入）；按病区拆多张交账单；部分退特殊口径 |
| **涉及域** | **CashierSettlement**；**PaymentReconciliation**；**PaymentRecDetail**；**PaymentYbSettlement**；**Encounter**（读，病区名） |
| **编排权威** | 本文 **§2、§4**；payment 纳入口径 **DM-FIN-003** **BR-CS-001～010** |
| **后置（摘要）** | 交账头 `SUBMITTED`（住院收款类型 `IP_PAYMENT`）；纳入 payment 已交账；`CashierSettlementSubmittedEvent`；撤回后 `WITHDRAWN` 并清挂载；审批通过/驳回由 **AS-FIN-CS-001** |

住院收费员将结账时间窗内**未交账**的住院相关 payment（**出院/中途结算** + **预交金收退**）经日结查询核对后确认交账，回写交账批次标识。预览只读；交账明细弹窗、默认全选、勾选提交、漏交硬/软栏、历史与撤回与 **AS-OP-FIN-004 v4.0** 对称。

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **住院收费员** | 选科室与日结时间窗 → 可选多选收款人软筛 → 日结查询 → 核对（含病区/预交金）→ **确认交账** → 历史查阅/重打/撤回 |
| **财务管理员（会计）** | 可撤回他人待审交账（**BR-IP-DCL-023**）；收款人软筛不限代交账权限（**BR-IP-DCL-013**）；审批在 **AS-FIN-CS-001** |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **纳入 kind** | `INPATIENT_CLINIC`（出院/中途结算）、`HOSPITAL_DEPOSIT`（预交金收退，含历史落库码 `PRE_DEPOSIT`，**BR-CS-005**） |
| **type** | 交账头 `type_enum = IP_PAYMENT`（**非**旧码 `INPATIENT`） |
| **交账状态** | 提交 → `SUBMITTED`（待审）；撤回 → `WITHDRAWN`（已清挂载）；历史列表展示 `statusEnum` + **`statusEnumText`** |
| **病区展示** | 应用层通过 `encounter_id` 关联住院就诊上下文取 `wardName`；`HOSPITAL_DEPOSIT` 固定展示「预交金」 |
| **未交账/日结查询/交账明细/确认交账** | 语义同 **AS-OP-FIN-004** §1.3（含 **BR-OP-DCL-014～018** 住院对称规则 **BR-IP-DCL-014～018**） |
| **交账科室 orgId** | IF-IP-DCL-02/03 **必填**；写入交账头 `org_id`；住院收款**不下推** `locationIds` 过滤 payment（对称门诊 org 范围键，见 **BR-IP-DCL-013**） |
| **收款人软筛 cashierIds** | 多选 `Practitioner` 主键，**可选**；空 = 不按人过滤；经 `PaymentEntererMatchResolver` 归一 `entererMatchIds`；**非**硬门禁代交账 |
| **窗内收费人** | IF-IP-DCL-02 返回 `practitionerIdsInWindow` + **`practitionerOptions`**；前端**仅**据此合并进收款人多选（**不含** `listActiveCashiers` / `defaultCashierIds`） |
| **漏交硬栏** | `gapHardBlockEarliestAt` 非空 → 预览展示文案；**提交**拒绝 **EX-IP-DCL-106**；FE **不**灰「确认交账」、**不**挡打开明细弹窗（对称 **EX-OP-DCL-106** / DD §2.6.5） |
| **漏交软提醒** | `gapSoftWarnCount`：业务时刻晚于 `dateTo` 的未交账条数；仅提醒，不拦提交 |
| **自付渠道汇总** | 自 `fin_payment_rec_detail` 业务渠道汇总；预交金 kind **不参与**渠道小计 |
| **费用性质块 / 医保现金 / 混结** | 同 **AS-OP-FIN-004** §1.3（**BR-OP-DCL-027** / 住院 **BR-IP-DCL-027**）：纯医保现金不动；仅混结按明细账户拆 |
| **医保上传差额** | 同 **AS-OP-FIN-004** §1.3（**BR-OP-DCL-028** / 住院 **BR-IP-DCL-028**）：公共头勾稽项；**不**入总收入/应交现金/医保补偿/预交金；**不**进 3201 |
| **多退少补拆列** | 对齐 DD「住院预交金结算口径纠错」：**`depositRefundAmount`**=窗内 `HOSPITAL_DEPOSIT` 逆向流水绝对值合计（出院多退现金 + 预交金页手工退）；**`settleSupplementAmount`**=住院结算单自付渠道实付代数和（DD 的 C；现金/银联等，**不是**补预交金；排除统筹/个账/冲抵）；二者与 `depositAmount` 同为独立展示，**不**入 `totalIncome`（**BR-IP-DCL-029**） |
| **患者分项（只读）** | 预览/挂载现算返回 `patientSummaries`：与合计同源算法按就诊（优先）拆块；除医保上传差额外各字段加总勾回合计；**不含**医保上传差额；公共头含 **药品合计金额**（与门诊同码集）；FE「日结合计 / 患者分项」双 Tab 与分别打印 + **患者分项 Excel 导出**（**IF-IP-DCL-07**；`AnnotatedExcelExporter` 真 xlsx，对齐门诊费用明细）；**不**改交账提交/挂载/撤回；预载 IO 全窗一次（**BR-IP-DCL-030**） |
| **医保关联** | 自 `fin_payment_yb_settlement` → `settlement_record_id`；**禁止**从子明细医保枚举汇统筹/基金/个账 |
| **侧栏医保对账** | FE 嵌入 CHS **日对账**（3201/3202）；**不**在本 AS 新增 IF；完整月清算等跳转 `/chs/reconciliation` |
| **体检人次** | 后端 `summary.physicalExamCount` 仍可写入；**FE 日结单不展示**该行 |
| **净额** | 交账弹窗 `items.netAmount`：**有**关联 `charge_item` 时 = 带符号 Σ 行额（收费正、退费/反结负，**BR-IP-DCL-026** / 同 **BR-OP-DCL-026**）；**无**明细（预交金）回落主表 `tendered_amount − returned_amount` 带符号。医保全个账头表现金应付可为 0，**禁止**两行均展示 0 |
| **查询维度** | `bill_date` 闭区间（**LocalDateTime** 日结时间窗）+ 可选 `entererMatchIds` + kind/status；展示汇总含已交账；`paymentCount` 另加未交账条件 |
| **原单重打** | IF-IP-DCL-05 按挂载 payment **现算** `mountedVoucher`（`rows`/`summary`/`paymentIds`），不落库快照 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-DCL-01～06：初始化、日结查询（含 enrichment 与漏交栏）、确认交账（`paymentIds`）、交账历史、原单重打、待审撤回；FE 侧栏嵌 CHS 日对账（compact） |
| **已成码** | 后端 **InpatientDailySettlementAppService** + 前端 `views/inpatient/charge/daily-settlement` |
| **非目标** | **药品收入 TAB**（后端 `previewInpatientDailySettlementMedicationRevenue` 存在，前端 TAB 已移除）；按病区拆多张交账单；护士站患者日费用清单；打印 PDF 占位；本页内嵌**月清算申请/工伤对账**全量（侧栏仅日对账）；`yb_settlement_record` 分项展示；部分退口径；chargeItem 十四项轧差；财务审批通过/驳回（域能力预留，本期 UI 未收口）；FE 展示「体检人次」 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 在本场景中的职责 | DM 路径 |
| --- | --- | --- |
| **CashierSettlement** | 住院 `IP_PAYMENT` 交账写/读、历史分页、撤回 | `DM-FIN-003-CashierSettlement-收费员交账.md` |
| **PaymentReconciliation** | 候选与挂载 | `DM-FIN-002-PaymentReconciliation-支付流水.md` |
| **PaymentRecDetail** | 自付渠道读 | `DM-FIN-002` §3.2 |
| **PaymentYbSettlement** | 医保关联读 | `fin_payment_yb_settlement` |
| **Encounter** | 病区名等住院就诊上下文读 | `DM-ADM-002-Encounter-就诊.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-DCL-001** | 查询/提交候选满足 **BR-CS-001～005** 且 `type_enum=IP_PAYMENT` | IF-IP-DCL-02、03 |
| **BR-IP-DCL-002** | 查询 rows 为 **上半区** `fin_type_code` 字典财务类别（快照优先；快照金额=挂载 `charge_item.total_price` **代数和**，**禁止**绝对值汇总，同 **BR-OP-DCL-010**；快照未覆盖时回落 `charge_item`）；**日结展示**再扣减行优惠差额得到**折后**分项，落点 `applyDiscountedFinTypeAmounts`；**仅 INPATIENT_CLINIC** 参与 fin_type；**不含**废止挂号费 `1011`、体检；**保留住院诊察费 `1012`**（床日诊察计入上半区与总收入，禁止按门诊号源口径整码剔除）；**不含**预交金（预交金在 summary **`depositAmount`** 单独列示）；items 含病区/预交金分类标签 | IF-IP-DCL-02、05 |
| **BR-IP-DCL-003** | `orgId` 必填；空候选拒绝提交（EX-IP-DCL-201）；并发/二次确认；提交人须绑定 Practitioner（`submitterId`） | IF-IP-DCL-02、03 |
| **BR-IP-DCL-004** | 预交金与住院结算 payment 可同批次纳入一次交账 | IF-IP-DCL-02、03 |
| **BR-IP-DCL-005** | 渠道与医保读路径同 **BR-OP-DCL-008**；费用性质筛选同 **BR-OP-DCL-009**；预交金 kind 不参与渠道汇总。**勾稽前提**：住院结算 SUCCESS 明细须已落真实渠道（**AS-IP-FIN-003 BR-IP-STL-006a**）；若残留草稿占位 `CASHIER`，日结渠道小计会与收银实收脱节（费用性质「医保现金」仍可能按账户拆出），**禁止**在日结 Mapper 把 `CASHIER` 臆映射为现金 | IF-IP-DCL-02、05 |
| **BR-IP-DCL-006** | 候选行 `netAmount` 口径同 **BR-IP-DCL-026** / **BR-OP-DCL-026**（有收费明细按带符号 charge 行额；预交金回落主表） | IF-IP-DCL-02 |
| **BR-IP-DCL-007** | 上半区按 `fin_type_code` 字典展示住院结算财务类别（排除废止挂号费 `1011` 与体检；**保留诊察费 `1012`**；预交金不参与 fin_type；**工伤合同 payment 不计入上半区**）；**下半区**：公共头（**总收入**=上半区**折后** fin_type 合计且**不含工伤**、**不含**预交金；**折扣合计**见 **BR-IP-DCL-031**；**应收**=总收入+折扣合计；自付渠道+**应交现金**=现金渠道+**医保统筹**=各**省/市医保**费用性质统筹代数和（**不含工伤**）+**医保上传差额**（细则 **BR-IP-DCL-028**）+**`depositAmount`**+**`depositRefundAmount`/`settleSupplementAmount`**（细则 **BR-IP-DCL-029**））+ 费用性质动态块（细则 **BR-IP-DCL-027**；**工伤合同不进费用性质块**）；医保合同再按 `insutype` 拆子行；前端/打印仅认 `summaryRows`/`expenseNatureSummaries`；`depositAmount` 及多退少补两列 **不参与**总收入、渠道、人次、医保分项等加和；**无**省/市硬字段 | IF-IP-DCL-02、05 |
| **BR-IP-DCL-027** | **费用性质分桶**同 **BR-OP-DCL-027**（纯自费/纯医保整笔 `payment.contract_no`；仅混结按 `PaymentRecDetail.account_id→contract_no` 拆；挂自费户→自费，挂医保户 CASH→医保现金）。落点：同共享 `DailySettlementExpenseNatureDetailAccumulator` + `InpatientDailySettlementMapperImpl#buildSummary`；预交金 kind **不参与**费用性质块 | IF-IP-DCL-02、05 |
| **BR-IP-DCL-028** | **零售−上传分差**：同 **BR-OP-DCL-028**（2301 上传口径）；日结公共头列示「医保上传差额」带符号代数和；**不**入 `totalIncome`/应交现金/医保补偿/预交金；**不**入 3201 | IF-IP-DCL-02 |
| **BR-IP-DCL-029** | **多退少补拆列**：`depositRefundAmount`（退预交金）= 窗内 `HOSPITAL_DEPOSIT` 逆向流水绝对值合计；`settleSupplementAmount`（结算补收）= 住院结算单自付渠道带符号合计（DD 的 C；现金/微信/银联等，**不是**补预交金；排除统筹/个账/`DEPOSIT_OFFSET`）；与 `depositAmount` 同为独立展示，**不**入 `totalIncome`/渠道小计；页面与打印经 `summaryRows` 带出 | IF-IP-DCL-02、05 |
| **BR-IP-DCL-030** | **患者分项只读块**：`patientSummaries` 与合计 `summary` **同源**（同费用性质筛、同剔除工伤、同 fin_type/渠道/费用性质/预交金/多退少补算法），按就诊优先分组；块内含医保现金（费用性质表），**不含**医保上传差额；公共头含 **`medicationTotalAmount`（药品合计金额）**=西药+外购+中草药+中成药折后 fin_type 代数和（与门诊同码集）；除上传差额外各金额字段各块加总勾回合计；FE 双 Tab + 分别打印 + **患者分项 Excel 导出**（**IF-IP-DCL-07**；`AnnotatedExcelExporter` 真 xlsx，对齐门诊费用明细）；原单重打挂载现算同出；**禁止**参与提交/挂载写路径；装配须**全窗一次**预载账户/险种/fin_type 快照，禁止按患者重复 Extension IO | IF-IP-DCL-02、05、07 |
| **BR-IP-DCL-031** | **折扣合计**：对称 **BR-OP-DCL-031**。优先 Σ `fin_payment_rec_detail.pay_enum=DISCOUNT`；旁证为 0 时回落 Σ 收费行 `|total_price|−|discount_amount|`（仅命中折扣配置的行）。**不进**自付渠道小计。与折后总收入勾稽：`应收 = 总收入 + 折扣合计`。IF-01 `discountFeatureEnabled` 与收费页同口径（已配置 ACTIVE 折扣内容）；**仅 true 时**展示折扣合计与应收。落点 `resolveDiscountTotalAmount` | IF-IP-DCL-01、02 |
| **BR-IP-DCL-011** | 日结汇总净额/渠道/现金对逆向反结或退费单（`UN_PAY` 或 `relation_id` 非空）取负；一结一反代数和为 0（同 **BR-OP-DCL-011**，含跨窗 UN_PAY 负向统筹/个账仍须计入） | IF-IP-DCL-02、05 |
| **BR-IP-DCL-012** | 初始化 `cashierOptions` 为 **ROLE_CASHIER** 岗池（`PractitionerRoleScopeLoader#pageActivePractitionersForPlatformRole(..., excludePlatformAdmin=false)`）；日结岗池**显式保留**挂款员岗的平台 admin（相对业务选型下拉默认剔除，见 **B.6.3.1**）；**不含** `listActiveCashiers` / `defaultCashierIds`；窗内实际收费人由 IF-IP-DCL-02 **`practitionerOptions`** 返回，前端与款员岗池合并进多选下拉 | IF-IP-DCL-01、02 |
| **BR-IP-DCL-013** | **`cashierIds`** 多选软筛（空=不过滤）；`enterer_id` 匹配经 **`PaymentEntererMatchResolver`** 归一 Practitioner 主键；Snowflake 主键须 **字符串** 传参；住院 preview/submit **不下推** `locationIds`；`dateFrom`/`dateTo` 为 **LocalDateTime** 日结时间窗 | IF-IP-DCL-02、03 |
| **BR-IP-DCL-014** | 同 **BR-OP-DCL-014**：rows/summary 含已交账；`paymentCount`/`items` 仍仅未交账 | IF-IP-DCL-02 |
| **BR-IP-DCL-015** | 同 **BR-OP-DCL-015**：items enrichment（项目行、支付方式前缀按合同 `hasInsurance`、`wardName`/`categoryLabel`） | IF-IP-DCL-02 |
| **BR-IP-DCL-026** | 同 **BR-OP-DCL-026**：交账弹窗行 `netAmount` 有收费明细按带符号 charge 行额回写；医保全个账禁止两行均为 0；预交金无明细时回落主表 | IF-IP-DCL-02 |
| **BR-IP-DCL-016** | 同 **BR-OP-DCL-016**：默认全选；逐笔可取消；PAY/UN_PAY 不强制同批 | IF-IP-DCL-03 |
| **BR-IP-DCL-017** | 同 **BR-OP-DCL-017**：`handoverRows`/`handoverSummary` 与弹窗全选汇总一致；预交金与多退少补写入 `handoverSummary.depositAmount`/`depositRefundAmount`/`settleSupplementAmount` | IF-IP-DCL-02、03 |
| **BR-IP-DCL-018** | 同 **BR-OP-DCL-018**：`paymentIds` 校验；非法 → EX-IP-DCL-105 | IF-IP-DCL-03 |
| **BR-IP-DCL-019** | 同门诊漏交硬栏：截止前窗外存在更早未交账 → 预览返回 `gapHardBlockEarliestAt`/`gapHardBlockMessage`；**提交**抛 **EX-IP-DCL-106** | IF-IP-DCL-02、03 |
| **BR-IP-DCL-020** | 同门诊漏交软提醒：`gapSoftWarnCount` 仅展示，提交响应可回传 | IF-IP-DCL-02、03 |
| **BR-IP-DCL-021** | 交账历史按 `submitted_at` 窗分页；行内 **`statusEnumText`** 由 `CashierSettlementStatus` 字典展示名解析；`canReprintFromMounts` 指示可否 IF-IP-DCL-05 | IF-IP-DCL-04 |
| **BR-IP-DCL-022** | 原单重打：校验交账头 `type_enum=IP_PAYMENT`；按挂载 payment 现算 **`mountedVoucher`**（schemaVersion=1） | IF-IP-DCL-05 |
| **BR-IP-DCL-023** | 撤回：仅 **`SUBMITTED`** 可撤回；须为 **提交人** 或 **会计/管理员**；成功 → **`WITHDRAWN`** 并清 payment 挂载；拒绝 → **EX-IP-DCL-107** | IF-IP-DCL-06 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-DCL-01 | 进入住院日结页 | 下发筛选项、默认结账日、费用性质、款员岗收款人池、`discountFeatureEnabled` | 读 | BR-IP-DCL-012、031 | — | Practitioner / 折扣配置 | `PractitionerRoleScopeLoader`；`ContractQueryService`；`ChargeDiscountApplySupport` |
| IF-IP-DCL-02 | 选科室/时间窗/收款人点「查询」 | **日结查询**：未交账笔数 + 汇总（含已交账）+ items + handover* + 漏交栏 + **practitionerOptions** | 读 | BR-IP-DCL-001～007、011～020 | — | CashierSettlement / PaymentReconciliation / Encounter | `PaymentReconciliationQueryService`；`EncounterContextReader` |
| IF-IP-DCL-03 | 预览后点「确认交账」 | 按 `paymentIds` 提交住院交账并挂载 payment | 写 | BR-IP-DCL-001、003～006、016～020 | 交账头 **SUBMITTED**；勾选 payment 已交账 | CashierSettlement / PaymentReconciliation | 提交前重锁；`CashierSettlementDomainService` |
| IF-IP-DCL-04 | 打开交账历史 | 按提交时刻窗分页历史列表 | 读 | BR-IP-DCL-021 | — | CashierSettlement | `CashierSettlementQueryService#pageHistory` |
| IF-IP-DCL-05 | 历史行「重打」 | 加载 **`mountedVoucher`** 打印体 | 读 | BR-IP-DCL-022 | — | CashierSettlement / PaymentReconciliation | `previewPaymentsByCashierSettlementId` 等 |
| IF-IP-DCL-06 | 历史行「撤回」 | 撤回待审交账 | 写 | BR-IP-DCL-023 | **WITHDRAWN**；payment 挂载清空 | CashierSettlement | `CashierSettlementDomainService#withdrawCashierSettlement` |

> 异常码前缀 **EX-IP-DCL-***，与 **EX-OP-DCL-*** 语义一一对应（见 §5）。

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（`GAP-CS-001` 已关闭；历史/撤回/重打已落地，见 **AS-OP-FIN-004** §3）。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-DCL-01 住院日结页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入住院日结页 |
| **主路径步骤** | 返回默认结账日（当日）；`expensePropertyOptions`（`fin_contract` 有效合同）；**`cashierOptions`** = 款员岗池（**BR-IP-DCL-012**）；`currentCashierId`；**`discountFeatureEnabled`**（**BR-IP-DCL-031**）。**不**返回 `listActiveCashiers` / `defaultCashierIds` / `financialDelegateEnabled` |
| **关键分支** | 未绑定 Practitioner 不阻断整页初始化（与门诊同构） |
| **失败分支** | EX-IP-DCL-101 |
| **查询侧说明** | 只读配置；窗内收费人默认勾选由前端在 IF-IP-DCL-02 后按 **`practitionerOptions`** 驱动 |

### IF-IP-DCL-02 日结查询（未交账预览）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选有效 **日结时间窗** 与 **`orgId`** |
| **主路径步骤** | **①** Gate：`orgId` 必填（**BR-IP-DCL-013**）。**②** 组装 `PreviewCashierSettlementQuery`（`type=IP_PAYMENT`，`locationIds=null`）。**③** 解析 **`cashierIds`** 软筛 → `entererMatchIds`。**④** 漏交扫描 → `gapHardBlock*` / `gapSoftWarnCount`（**BR-IP-DCL-019/020**）。**⑤** 窗内 enterer 归一 → **`practitionerOptions`**。**⑥** 加载未交账候选 + 展示汇总（含已交账，**BR-IP-DCL-014**）。**⑦** fin_type 上半区（仅 INPATIENT_CLINIC，排除 1011/体检、**保留 1012**）；预交金 → **`depositAmount`**；多退少补 → **`depositRefundAmount`/`settleSupplementAmount`**（**BR-IP-DCL-002/007/029**）。**⑧** Encounter 回填 `wardName`。**⑨** items enrichment + **`handoverRows`/`handoverSummary`**。**⑩** 返回完整预览 DTO |
| **关键分支** | 无候选：零行 + `paymentCount=0`；漏交硬栏非空：预览可查、**提交**拒绝；仅预交金或仅结算均可查询 |
| **失败分支** | EX-IP-DCL-102、103 |
| **查询侧说明** | 不写库；对称 **AS-OP-FIN-004** IF-OP-DCL-02 |

### IF-IP-DCL-03 确认交账

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已完成 IF-IP-DCL-02；交账明细弹窗内确认；已勾选 ≥1 笔 `paymentId` |
| **主路径步骤** | **①** Gate：`orgId`、绑定 Practitioner 提交人。**②** 漏交硬栏 → **EX-IP-DCL-106**。**③** 重锁候选；校验 `paymentIds`（**BR-IP-DCL-018**）；空 → EX-IP-DCL-201。**④** 按勾选集调用 `InpatientDailySettlementMapper#resolveSelectedSettlementTotalIncome`（与 `handoverSummary.totalIncome` 同 `buildDisplayBundle`，**不含**预交金）写入头表 `total_income`。**⑤** 写入 `IP_PAYMENT` 交账头（`status=SUBMITTED`）并挂载勾选 payment；快照 `practitionerIds`。**⑥** 发布 `CashierSettlementSubmittedEvent`。**⑦** 返回 `settlementId`/`busNo`/`paymentCount`/`statusEnum`/`gapSoftWarnCount` |
| **关键分支** | 默认全选；分批后剩余可再交账；并发 → EX-IP-DCL-301 |
| **失败分支** | EX-IP-DCL-105、201、106；关闭弹窗不提交 |
| **查询侧说明** | 提交口径**不含** `expenseProperty`；禁止仅信任前端缓存列表 |

### IF-IP-DCL-04 交账历史分页

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户打开历史 Tab 并选择提交时刻窗 |
| **主路径步骤** | 按 `submittedFrom`/`submittedTo` 分页查询 `type=IP_PAYMENT` 交账头；装配 **`statusEnumText`**、`canReprintFromMounts` 等 |
| **关键分支** | 默认 `pageIndex=0`，`pageSize=20`，最大 200 |
| **失败分支** | EX-IP-DCL-102、103 |
| **查询侧说明** | 只读；对称 IF-OP-DCL-04 |

### IF-IP-DCL-05 交账原单重打

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户从历史选择可重打记录 |
| **主路径步骤** | 校验交账头存在且 `type=IP_PAYMENT` → 按挂载 payment 现算 **`mountedVoucher`**（含 `rows`/`summary`/`settlementPeriodText`/`paymentIds`） |
| **关键分支** | 无挂载时仍可返回表头结构 |
| **失败分支** | EX-IP-DCL-102、103 |
| **查询侧说明** | 不落库快照；对称 IF-OP-DCL-05 |

### IF-IP-DCL-06 撤回待审交账

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 历史行 `statusEnum=SUBMITTED`；当前用户须为款员 |
| **主路径步骤** | 权限 Gate → `withdrawCashierSettlement` → 清挂载 → **`WITHDRAWN`** → 发布 `CashierSettlementWithdrawnEvent` |
| **关键分支** | 非待审/无权限 → **EX-IP-DCL-107** |
| **失败分支** | EX-IP-DCL-102、103、107 |
| **查询侧说明** | 对称 IF-OP-DCL-06 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-DCL-101 | 初始化失败 | 页面初始化失败，请稍后重试 | 是 |
| EX-IP-DCL-102 | 参数非法 | 请选择有效的日结时间范围 / 交账科室 / 交账头 | 否 |
| EX-IP-DCL-103 | 查询失败 | 查询失败，请稍后重试 | 是 |
| EX-IP-DCL-105 | 勾选 payment 非法 | 所选流水不在待交账范围内，请刷新后重试 | 否 |
| EX-IP-DCL-106 | 漏交硬栏 | 截止前存在未交账流水，请先补交或扩大时间窗 | 否 |
| EX-IP-DCL-107 | 撤回拒绝 | 仅待审交账可撤回 / 仅款员可撤回 | 否 |
| EX-IP-DCL-201 | 无可交账流水 | 请至少选择一笔待交账流水 | 否 |
| EX-IP-DCL-301 | 并发交账冲突 | 部分流水已被交账，请刷新后重试 | 是 |
| EX-IP-DCL-410 | 交账成功 | 交账成功，单号 {busNo} | — |

---

## 6. 事件语义与消费方

| 事件 | 触发 IF | 典型消费方 | 幂等/一致性语义 |
| --- | --- | --- | --- |
| `CashierSettlementSubmittedEvent` | IF-IP-DCL-03 | 审计 | 载荷 `typeEnum = IP_PAYMENT`；`statusEnum = SUBMITTED` |
| `CashierSettlementWithdrawnEvent` | IF-IP-DCL-06 | 审计 | 载荷含交账头标识；payment 挂载已清 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | IF-IP-DCL-02 ≤2000ms；IF-IP-DCL-03 ≤5000ms |
| **事务** | 提交/撤回单事务 |
| **并发** | 行级未交账校验；冲突整单失败 |
| **一致性** | 查询与提交共用领域筛选口径；重打与预览同汇总管线 |
| **前端 HTTP** | 须 `publicRequestClient`；Snowflake ID 字符串传参 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 预交金 + 住院结算同窗口查询 → 弹窗默认全选 → 确认交账 `SUBMITTED` → 再次查询 paymentCount 减少 |
| **对称** | orgId 必填；`cashierIds` 空/多选；漏交硬栏 106；软提醒计数；历史 `statusEnumText`；重打 `mountedVoucher`；撤回 → `WITHDRAWN` |
| **住院特有** | 上半区无 1011/体检、有诊察费 1012；`depositAmount` 独立；多退少补 `depositRefundAmount`/`settleSupplementAmount`；`totalIncome` 不含预交金及该两列；`wardName`/「预交金」标签 |
| **弹窗** | handoverSummary 与全选弹窗一致 |
| **边界** | 门诊 kind 不纳入；DRAFT 不纳入 |
| **并发** | 双窗口同 payment 提交 → EX-IP-DCL-301 |
| **非目标** | 药品收入 TAB 接口可调用但页面无入口 |

---

## 9. 关联文档与变更历史

| 文档 |
| --- |
| `AppService-SDD/AS-IP-FIN-005-住院收费住院日结-SDD.md` |
| `AppService/AS-OP-FIN-004-门诊收费门诊日结.md`（**v4.4**，共用语义） |
| `AppService/AS-FIN-CS-001-交账审批.md` |
| `AppService-SDD/AS-OP-FIN-004-门诊收费门诊日结-SDD.md` |
| `docs/领域/DomainModel/DM-FIN-003-CashierSettlement-收费员交账.md |
| `docs/领域/DomainModel/DM-FIN-002-PaymentReconciliation-支付流水.md |

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v4.19 | 2026-10-14 | **BR-IP-DCL-012**：日结收款岗池显式 `excludePlatformAdmin=false`；业务选型下拉默认剔 admin 见 **B.6.3.1**；代码先行回写 |
| v4.18 | 2026-10-13 | **IF-IP-DCL-07**：患者分项导出改后端 `AnnotatedExcelExporter` 真 xlsx（对齐门诊费用明细）；代码先行回写 |
| v4.17 | 2026-10-13 | **BR-IP-DCL-030**：FE 患者分项 Excel 导出（一行一患者；SpreadsheetML；不新增后端 IF）；代码先行回写 |
| v4.16 | 2026-10-13 | **BR-IP-DCL-030**：公共头增 **药品合计金额**（与门诊同码集）；代码先行回写 |
| v4.15 | 2026-10-09 | **BR-IP-DCL-002/007**：住院上半区/总收入**保留诊察费 `1012`**（床日诊察）；仅排除废止挂号费 `1011` 与体检；代码先行回写 |
| v4.14 | 2026-10-06 | `discountFeatureEnabled` 明确为「已配置 ACTIVE 折扣内容」 |
| v4.13 | 2026-10-06 | **BR-IP-DCL-002/007/031**：上半区与总收入改为折后；折扣单列；应收=总收入+折扣；读侧 `applyDiscountedFinTypeAmounts` |
| v4.12 | 2026-10-06 | 增 **BR-IP-DCL-031**：下半区/打印展示折扣合计（优先 DISCOUNT 旁证，无旁证回落收费行售价−折后）；IF-01 `discountFeatureEnabled`；对齐 **AS-OP-FIN-004 v4.12～v4.14** |
| v4.11 | 2026-09-09 | **BR-IP-DCL-030**：患者分项只读块 `patientSummaries`（同源按就诊拆、不含上传差额、双 Tab/分别打印、挂载现算重打）；装配全窗一次预载（禁按患者 N 次 Extension）；不影响交账写路径 |
| v4.10 | 2026-08-21 | **BR-IP-DCL-005**：日结渠道勾稽依赖结算 SUCCESS 真实渠道（**BR-IP-STL-006a**）；禁止日结把 `CASHIER` 臆映射为现金；脏数据订正见 ops `fix-zhao-guiying-settle-cashier-to-wechat-pro-20260821.sql` |
| v4.9 | 2026-08-21 | **BR-IP-DCL-029**：下半区增 `depositRefundAmount`（退预交金/多退）、`settleSupplementAmount`（结算渠道实付/少补，非补预交金）；对齐 DD；页面与打印同字段 |
| v4.8 | 2026-08-17 | **BR-IP-DCL-002**：明确 fin_type 快照金额为收费行 **代数和**（禁绝对值）；对齐 **AS-OP-FIN-004 v4.7** / `PaymentFinTypeStaticSnapshotWriter` |
| v4.7 | 2026-08-03 | 合并远端「医保上传差额」：新增 **BR-IP-DCL-028**（原远端误占 027）；**027** 固定为费用性质混结分桶 |
| v4.6 | 2026-08-02 | 增 **BR-IP-DCL-027** / §1.3：费用性质分桶对齐 **AS-OP-FIN-004 v4.5**（纯医保现金不动；仅混结按账户拆） |
| v4.5 | 2026-07-30 | **FE**：右侧侧栏嵌 CHS 日对账（住院 `clrType=21`）；**不展示**体检人次；收款人多选不折叠；对齐 SDD v4.4 |
| v4.4 | 2026-07-30 | **BR-IP-DCL-026** / 修订 **BR-IP-DCL-006**：交账弹窗行净额按带符号 charge 行额回写（同 **AS-OP-FIN-004 v4.3**） |
| v4.3 | 2026-07-29 | **BR-IP-DCL-015**：交账弹窗支付方式前缀按合同 `hasInsurance`（同 **BR-OP-DCL-015** / **AS-OP-FIN-004 v4.2**） |
| v4.2 | 2026-07-28 | IF-IP-DCL-03：头表 `total_income` 强制 `resolveSelectedSettlementTotalIncome`（与 `handoverSummary.totalIncome` 同管线，不含预交金）；对齐 **AS-OP-FIN-004 v4.1**；消解合并冲突 |
| v4.1 | 2026-07-27 | **对齐成码**：`IP_PAYMENT`；`SUBMITTED`/`WITHDRAWN`；IF-IP-DCL-04～06；`orgId`/`cashierIds` 软筛/`practitionerOptions`；漏交 **EX-IP-DCL-106/107**；`mountedVoucher`/`statusEnumText`；与门诊对称；保留住院 **BR-IP-DCL-002/004/007** |
| v4.0 | 2026-07-11 | **BR-IP-DCL-007/017**：预交金仅在 `depositAmount` 独立汇总 |
| v3.9 | 2026-07-11 | 交账明细弹窗、handover*、`paymentIds` 勾选提交 |
| v3.0 | 2026-06-21 | health 应用层重写 |
| v1.0 | 2026-06-19 | 首版（已废止） |
