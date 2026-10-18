# 门诊日结交账 · 应用服务设计（AS）

> **文档职责**：说明「门诊收费日结 / 交账」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为 **health-application** 门诊财务包实现的编排权威。
>
> **页面边界**：门诊日结页 — 全局选**交账科室**与**日结时间窗** → **日结查询（未交账预览）** → 核对汇总（**日结合计 / 患者分项**双 Tab）→ **交账明细弹窗**（默认全选、逐笔可取消）→ **确认交账**（提交后 **待审批**）→ **交账历史**（状态展示、**挂载现算重打**、**待审撤回**）；右侧**医保对账侧栏**嵌 CHS **日对账**（3201/3202，复用 `DailyLedgerList` compact）；财务审批走 **AS-FIN-CS-001**，不在本页完成。
>
> **编码**：AS-OP-FIN-004 · **版本**：v4.19 · **日期**：2026-10-14

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-OP-DCL-01～06：初始化、日结查询、确认交账、交账历史分页、交账单挂载现算重打、待审撤回；**交账明细弹窗**与 `paymentIds` 勾选提交；**患者分项只读块**（`patientSummaries`，**BR-OP-DCL-032**；含门诊特有「是否住院」、**不含**预交金列）；下半区公共头含 **药品合计金额**；实现落点 **health-application / outpatient / financial / dailysettlement**；**FE** 右侧侧栏嵌 CHS **日对账**（`DailySettlementChsReconPanel` → `DailyLedgerList` compact；对账日默认跟日结窗起点；业务类型默认门诊 `clrType=11`） |
| **在外** | 门诊收费（**AS-OP-FIN-002**）；门诊退费（**AS-OP-FIN-003**）；住院日结（**AS-IP-FIN-005**）；财务交账审批（**AS-FIN-CS-001**）；医保**月清算申请/状态查询/工伤对账**全量能力（走 `/chs/reconciliation` 完整页，侧栏仅链入）；药品收入 TAB（后端方法保留，**FE 本期不暴露**）；打印 PDF 占位 |
| **涉及域** | **CashierSettlement**（交账写/读/撤回）；**PaymentReconciliation**（候选流水与挂载）；**PaymentRecDetail**（自付渠道读）；**PaymentYbSettlement**（医保关联读）；Practitioner（经办人/提交人） |
| **编排权威** | 本文 **§2、§4**；payment 纳入口径 **DM-FIN-003** **BR-CS-001～010** |
| **后置（摘要）** | 交账头 `status_enum = SUBMITTED`（待审批）；纳入 payment `cashier_settlement_id` 非空；发布 `CashierSettlementSubmittedEvent`；审批通过后由 **AS-FIN-CS-001** 推进终态 |

收费员在当班结束前，将结账时间窗内已完成、**尚未交账**的门诊支付流水，经日结查询核对后**确认交账**。预览仅读库；提交为写路径。点击「确认交账」须弹出**交账明细弹窗**：展示待交账 payment 逐笔明细；**默认全选**，用户可逐笔取消勾选以分批交账。弹窗内汇总须与 `handoverRows`/`handoverSummary` 一致；确认时提示账项正确性，**提交后进入待审流程**（非终态归档）；**待审**仅款员可提交与撤回（**IF-OP-DCL-06**）。

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **门诊收费员** | 选科室、日结时间与收款人（可选）→ 日结查询 → 核对汇总 → **确认交账** → 必要时**撤回待审** |
| **财务管理员（会计/管理员）** | 可代交账场景下提交；可撤回他人**待审**交账；审批在 **AS-FIN-CS-001** |
| **系统** | 查询只读；提交/撤回原子写交账头与 payment 挂载 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **交账类型** | `CashierSettlementType.OP_PAYMENT`（门诊收款日结；**非**历史 `OUTPATIENT` 别名） |
| **交账状态** | 提交后 `SUBMITTED`（待审批）；撤回后 `WITHDRAWN`；审批通过/驳回由 **AS-FIN-CS-001** |
| **未交账** | `fin_payment_reconciliation.cashier_settlement_id IS NULL` |
| **已交账（挂载）** | payment 已回写 `cashier_settlement_id`，指向 `fin_cashier_settlement.id`（含待审 `SUBMITTED` 与已通过 `APPROVED`） |
| **交账科室** | 请求 `orgId` **必填**；写入交账头范围键；**门诊收款候选不按 org→location 白名单过滤 payment**（误筛会把候选扫空） |
| **收款人筛选** | `cashierIds` 多选 **可选软筛**；**空 = 不按 enterer 过滤**（**BR-OP-DCL-003**）；非「默认 enterer=当前操作员」硬筛 |
| **窗内收费人** | 预览返回 `practitionerIdsInWindow` / `practitionerOptions`（窗内有 payment 的 enterer 归一 Practitioner）；**FE 收款人下拉权威**（**BR-OP-DCL-022**） |
| **岗池收款人** | pageInit 返回 `cashierOptions` = `ROLE_CASHIER` 岗池；前端先入下拉，再与窗内有流水的人合并 |
| **日结时间窗** | `dateFrom`/`dateTo` 为 **LocalDateTime**（含时分秒）；默认 `defaultDateFrom`=当日 00:00:00，`defaultDateTo`≈当前时刻 |
| **漏交硬栏** | `gapHardBlockEarliestAt` / `gapHardBlockMessage`：窗**前**存在更早未交账 → **提交 Gate 拒绝**（EX-OP-DCL-106）；FE **不**灰「确认交账」、**不**挡打开明细弹窗 |
| **漏交软提醒** | `gapSoftWarnCount`：业务时间**晚于** `dateTo` 的未交账条数；仅提醒，不拦提交 |
| **日结查询** | 在 **BR-CS-001～006** 口径下：`paymentCount`/`items` 仅未交账候选；**rows/summary** 汇总同日窗内已交账+未交账流水（**BR-OP-DCL-014**）；**不写库** |
| **交账明细弹窗** | 基于 IF-OP-DCL-02 的 `items`；勾选粒度 **payment 整笔**；**默认全选**；「本次待交账合计」以 `handoverSummary` 为权威 |
| **确认交账** | 提交 `paymentIds`（勾选子集）；支持分批（**BR-OP-DCL-006**）；头表落 `total_income`（勾选集上与 `handoverSummary.totalIncome` 同管线现算）；成功后 `SUBMITTED`；**待审可撤回**（**BR-OP-DCL-007**、**023**） |
| **交账历史重打** | IF-OP-DCL-05 返回 `mountedVoucher`：按挂载 payment **现算** rows/summary（**非** `print_snapshot_json`） |
| **净额** | 交账弹窗 `items.netAmount`：**有**关联 `charge_item` 时 = 带符号 Σ 行额（收费正、退费/反结负，**BR-OP-DCL-026**）；**无**明细（预交金等）回落主表 `tendered_amount − returned_amount` 带符号。医保全个账头表现金应付可为 0，**禁止**两行均展示 0 |
| **总收入 / 应收 / 折扣** | **总收入**与上半区各类（西药/化验等）均为**折后**；**折扣合计**单列；**应收** = 总收入 + 折扣合计（**BR-OP-DCL-010 / 031**） |
| **分类快照金额** | `fin_payment_rec_static.amount` = 本笔挂载 `charge_item.total_price` 按 `fin_type` **代数和**（原价；落点 `PaymentFinTypeStaticSnapshotWriter`）。日结展示再扣优惠差额 |
| **渠道 vs 总收入** | 勾稽时须含微信/支付宝/银行卡/现金 + 医保统筹 + 个人账户；**医保上传差额**仅勾稽不入总收入（**BR-OP-DCL-028**）。写侧须保证落账明细合计=收费项（**AS-OP-FIN-002 · BR-OPC-024**，含扫码/拆账户后终态复验），否则会出现「四支付≠合计」 |
| **纳入 kind** | 仅 `OUTPATIENT_CLINIC`（含挂号 + 门诊收费，**BR-CS-004**） |
| **纳入 status** | `SUCCESS`、`REFUNDED`（**BR-CS-003**）；不含 `DRAFT` / `CANCELLED` / `REFUND_PROCESSING` |
| **费用性质筛选** | UI「自费/医保」仅过滤**展示**；判定依据 payment `contract_no` → **`fin_contract`**；**不改变**交账挂载集合 |
| **费用性质块（下半区）** | 动态块：市医保/省医保/自费等；列含人次、**医保现金**、统筹、个人账户、总额（见 **BR-OP-DCL-010** / **BR-OP-DCL-027**） |
| **医保现金** | 走医保结算路径、基金/个账不够时患者用**现金**补差的部分；账单仍挂**医保账户**。**不是**转自费/自费账单的现金 |
| **自费合计** | 挂**个人现金（自费）账户**的账单净额（含本来自费、转自费）；可与医保同张 payment 混结，但日结须拆到自费行 |
| **自费+医保混结** | 一张 `payment` 同时含自费账户与医保账户明细（收费写侧 `contract_no` 可空，见 **AS-OP-FIN-002** BR-OPC-020）；日结**仅此时**按明细账户拆桶 |
| **纯自费 / 纯医保** | 明细账户费用性质单一；日结仍按 `payment.contract_no` **整笔**归集，**不改**原医保现金口径 |
| **自付渠道汇总** | 自 `fin_payment_rec_detail` 按业务支付方式汇总；**不含**医保基金/个账枚举行 |
| **医保关联** | 自 `fin_payment_yb_settlement` 经 `settlement_record_id` 关联；**禁止**从 `fin_payment_rec_detail` 按医保支付枚举汇统筹/基金/个账 |
| **侧栏医保对账** | FE 嵌入 CHS **日对账**（3201 总账 / 3202 明细）；**不**在本 AS 新增 IF；机构/签到/出站走 CHS 既有 API；完整月清算等跳转 `/chs/reconciliation` |
| **医保上传差额** | 公共头勾稽项 `chsRetailUploadDiffAmount`：`retail_medfee_sumamt − upload_medfee_sumamt`（见 **BR-OP-DCL-028**）；**不**入总收入/应交现金/医保补偿；**不**进 3201 |
| **体检人次** | 后端 `summary.physicalExamCount` 仍可由 fin_type=`9999` 金额取整写入；**FE 日结单不展示**该行（与「人次」语义不符，避免误导） |
| **工伤独立统计** | `summary.workInjurySummary`（**BR-OP-DCL-030**）：工伤合同 payment 单独汇总，**不**入总收入/上半区/主费用性质；FE 仅院点启用工伤 UI 且非空时展示 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-OP-DCL-01～06：初始化、日结查询、确认交账、历史分页、挂载现算重打、待审撤回；FE 侧栏嵌 CHS 日对账（compact） |
| **后端保留、页面不暴露** | `previewOutpatientDailySettlementMedicationRevenue`（药品收入 TAB）；**非 IF 契约** |
| **已删除契约** | `listOutpatientDailySettlementActiveCashiers`、`defaultCashierIds`（**不得**在新联调中使用） |
| **非目标** | 本页完成财务审批；打印 PDF 占位；本页内嵌**月清算申请/工伤对账**全量（侧栏仅日对账）；交账作废；`print_snapshot_json` 落库重打；FE 展示「体检人次」 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 在本场景中的职责 | DM 路径 |
| --- | --- | --- |
| **CashierSettlement** | 交账头 insert、历史分页、撤回、未交账预览/计数、提交时批量挂载 payment | `DM-FIN-003-CashierSettlement-收费员交账.md` |
| **PaymentReconciliation** | 被查询与挂载的支付流水主记录 | `DM-FIN-002-PaymentReconciliation-支付流水.md` |
| **PaymentRecDetail** | 自付渠道明细读（日结渠道汇总）；混结费用性质分桶读 `account_id`（**BR-OP-DCL-027**） | `DM-FIN-002` §3.2 |
| **Account** | 明细账户 → `contract_no`（混结分桶） | `DM-ADM-009` |
| **PaymentYbSettlement** | payment 与医保结算凭证关联读 | `fin_payment_yb_settlement` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-OP-DCL-001** | 查询/提交候选 payment 须满足 **DM-FIN-003** **BR-CS-001～005**（`type_enum = OP_PAYMENT`） | IF-OP-DCL-02、03 |
| **BR-OP-DCL-002** | 查询返回 `paymentCount` 与候选条数一致；汇总净额为候选 payment **带符号** `netAmount` 代数和（含逆向 UN_PAY 取负，见 **BR-OP-DCL-011**） | IF-OP-DCL-02 |
| **BR-OP-DCL-003** | **`cashierIds` 多选可选软筛**：空列表 = **不按 enterer 过滤**；传值则经 `PaymentEntererMatchResolver` 解析 `entererMatchIds`；**不按代交账权限拦截**人选勾选；**禁止**回退为「默认 enterer=operator 且不可查他人」硬口径 | IF-OP-DCL-01、02、03 |
| **BR-OP-DCL-004** | 候选集为空时**拒绝提交**（EX-OP-DCL-201） | IF-OP-DCL-03 |
| **BR-OP-DCL-005** | 提交须原子：**BR-CS-008**；并发挂载冲突整单失败 | IF-OP-DCL-03 |
| **BR-OP-DCL-006** | 同一日期窗可多次交账；仅未交账 payment 进入新批次 | IF-OP-DCL-03 |
| **BR-OP-DCL-007** | 提交前须在**交账明细弹窗**内确认（**BR-CS-010** 语义）；提交后交账头 **`SUBMITTED`（待审批）**，**非**不可逆终态；**提交与待审撤回均须款员岗**（**BR-OP-DCL-023**）；提示文案：**提交后进入待审流程**（不再绝对化「交账后归档不可操作」） | IF-OP-DCL-03、06 |
| **BR-OP-DCL-008** | 日结渠道汇总仅取自 **PaymentRecDetail** 业务渠道；医保金额与费用性质不得从子明细医保枚举汇总 | IF-OP-DCL-02 |
| **BR-OP-DCL-009** | 费用性质 UI 筛选仅影响展示；交账提交集合与未筛选时一致 | IF-OP-DCL-02、03 |
| **BR-OP-DCL-010** | **上半区**按 `fin_type_code` 字典展示财务类别金额（**`fin_payment_rec_static` 快照优先**；缺快照抛错，**禁止**回落 `charge_item` 原价当展示；快照金额=挂载 `charge_item.total_price` **代数和**，**禁止**绝对值汇总；**日结展示**再扣减行优惠差额得到**折后**分项，落点 `applyDiscountedFinTypeAmounts`；号源/挂号强制归入诊察费 `1012`；**不展示**已废止挂号费 `1011`；**工伤合同 payment 不计入上半区**）；缺锚点/金额 → EX-OP-DCL-104。**下半区**：公共头（**总收入**=上半区折后 fin_type 代数和且**不含工伤**；**折扣合计**见 **BR-OP-DCL-031**；**应收**=总收入+折扣合计；自付渠道+**应交现金**=现金渠道+**医保统筹**=各**省/市医保**费用性质统筹代数和（**不含工伤** `WORKER_INSURANCE`/`5`）+**医保上传差额**=窗内 `yb_settlement_record.retail_upload_diff_amt` 带符号代数和，细则 **BR-OP-DCL-028**）+ 费用性质动态块（细则 **BR-OP-DCL-027**；**工伤合同不进费用性质块**）；医保合同再按 `yb_settlement_record.insutype` 拆子行（金额口径复用 BUG-1 `shouldCountInsuranceDetail`）；前端/打印主认 `summary.summaryRows` / `expenseNatureSummaries`，**无**省/市硬字段；**FE 不展示** `physicalExamCount`；工伤独立块见 **BR-OP-DCL-030** | IF-OP-DCL-02 |
| **BR-OP-DCL-030** | **工伤独立统计**（`summary.workInjurySummary`）：窗内工伤合同 payment **单独**汇总，**禁止**并入 `totalIncome` / 上半区 fin_type / 主费用性质块 / 医保统筹公共头。分类金额按日结 fin_type 归并——**西药中成药**=`1001`+`2010`+`1002`；**中草药**=`1003`；**耗材**=`2005`；**诊察费**=`1012`（含 `1011` 归并）；**诊疗**=其余非体检 fin_type 代数和；另附**人次**（patientId 去重）与费用性质同构行（**个人自付**=自付渠道含现金/微信/支付宝/银行卡等；**统筹**/`INSURANCE_FUND`；**个人账户**/`INSURANCE_ACCT`；**总额**=统筹+个账）。落点：`OutpatientDailySettlementMapperImpl#buildWorkInjurySummary` + `DailySettlementExpenseNatureDetailAccumulator#accumulateWorkInjuryNaturePayment`。**FE**：仅院点启用工伤 UI（`VITE_ENABLE_WORK_INJURY`）且 `workInjurySummary` 非空时展示；打印可并入渠道区/性质表字段 | IF-OP-DCL-02 |
| **BR-OP-DCL-031** | **折扣合计**：优先 Σ `fin_payment_rec_detail.pay_enum=DISCOUNT`；旁证为 0 时回落 Σ 收费行 `|total_price|−|discount_amount|`（仅命中折扣配置的行）。**不进**自付渠道小计。与折后总收入勾稽：`应收 = 总收入 + 折扣合计`。IF-01 `discountFeatureEnabled` 与收费页同口径（已配置 ACTIVE 折扣内容）；**仅 true 时**展示折扣合计与应收。落点 `resolveDiscountTotalAmount` | IF-OP-DCL-01、02 |
| **BR-OP-DCL-027** | **费用性质分桶（混结修正）**：（1）**纯自费/纯医保** payment：仍按 `payment.contract_no` 整笔归集——医保行上全部 `pay_enum=CASH` 进该医保「医保现金」，自费行用整笔 `netAmount` 进「自费合计」；**不得**改写历史上纯医保单的医保现金。（2）**仅当**同笔 payment 的 `PaymentRecDetail` 经 `account_id→Account.contract_no` 判定同时含医保合同与非医保合同时（混结）：按明细账户拆桶——挂自费账户的各渠道净额→自费；挂医保账户的 `CASH`→该医保「医保现金」（基金不够补差）；挂医保账户的统筹/个账→统筹/个账；**禁止**把挂自费账户的金额计入医保现金。（3）落点：`DailySettlementExpenseNatureDetailAccumulator` + `OutpatientDailySettlementMapperImpl#buildSummary` | IF-OP-DCL-02 |
| **BR-OP-DCL-028** | **医保上传差额**（`chsRetailUploadDiffAmount`）：`retail_medfee_sumamt − upload_medfee_sumamt`（院内 `charge_item.total_price` 合计 − 2204 `pric×cnt` 合计），于 2206/2207 落 `yb_clinic_pre_settle` / `yb_settlement_record`；日结公共头单独列示带符号代数和（逆向 UN_PAY 取负）；**禁止**并入 `totalIncome` / 应交现金 / 医保补偿；**不**进入 3201 日对账口径 | IF-OP-DCL-02 |
| **BR-OP-DCL-011** | 退费 PAY/UN_PAY 成对计入；`netAmount`、子明细渠道、现金与 fin_type 汇总须带符号代数和；`totalIncome` 在存在 fin_type 汇总时始终取分类代数和。**跨窗 UN_PAY**（原单不在本交账窗）：`shouldCountInsuranceDetail` 仍须计入负向统筹/个账，禁止因桥表全 REVOKED 跳过导致本窗医保补偿虚高 | IF-OP-DCL-02 |
| **BR-OP-DCL-029** | **2601 与日结同态**：平台 2601 成功后须（1）软删 `yb_settlement_record`；（2）`fin_payment_yb_settlement`→`REVOKED`；（3）软删该 `setl_id` 对应 `fin_payment_rec_detail` 的 `INSURANCE_FUND`/`INSURANCE_ACCT`。禁止仅软删回执而保留统筹/个账明细（会导致市医保父行虚增、险种子行落入「未分险种」被隐藏）。**不影响**正常 2208+UN_PAY 一收一退。落点：`Op2601Invoker` + `PaymentReconciliationDomainService#softDeleteInsuranceFundAcctDetailsBySetlId`；扫描防误冲：`PaymentSettledCorrectionQueryService`（失败后已有成功 2207 则跳过） | IF-OP-DCL-02、05 |
| **BR-OP-DCL-012** | `paymentCount` = 待归档 **payment 流水条数**（**BR-CS-011**）；**提交挂载条数** = `paymentIds.size()` | IF-OP-DCL-02、03 |
| **BR-OP-DCL-013** | pageInit **`cashierOptions`** = **`ROLE_CASHIER` 岗池**（`buildCashierOptions` / `PractitionerRoleScopeLoader#pageActivePractitionersForPlatformRole(..., excludePlatformAdmin=false)`）；日结岗池**显式保留**挂款员岗的平台 admin（相对业务选型下拉默认剔除，见 **B.6.3.1**）；查询后前端把窗内 `practitionerOptions` 并入同一下拉；**不再**返回 `financialDelegateEnabled` | IF-OP-DCL-01、02 |
| **BR-OP-DCL-014** | **rows/summary** 含已交账；`paymentCount`/`items` 仍仅未交账 | IF-OP-DCL-02 |
| **BR-OP-DCL-015** | `items` enrichment：项目行、支付方式；支付方式展示为「费用性质·渠道」——**前缀**取 payment `contract_no` → `fin_contract` 医保判定（`hasInsurance`），**后缀**取 `pay_enum` 渠道短名；**禁止**仅因现金/微信等自付渠道把前缀写成「自费」；**禁止** charge item 级勾选 | IF-OP-DCL-02 |
| **BR-OP-DCL-026** | 交账弹窗行 `netAmount`：有 `chargeLines` 时按**带符号**收费行额代数和回写（收费正、退费负）；医保全个账头表 `tendered`/`display`=0 **仍**展示账单正负，**禁止**成对流水两行均为 0；无收费明细（预交金等）保留主表 `tendered−returned` 带符号 | IF-OP-DCL-02 |
| **BR-OP-DCL-016** | 弹窗**默认全选**；逐笔可取消分批；PAY/UN_PAY **不强制**同批 | IF-OP-DCL-03 |
| **BR-OP-DCL-017** | `handoverRows`/`handoverSummary` 与弹窗全选汇总一致；主区 rows/summary 含已交账 | IF-OP-DCL-02、03 |
| **BR-OP-DCL-018** | `paymentIds` 必填、非空、去重；须 ⊆ 未交账候选；非法 → EX-OP-DCL-105 | IF-OP-DCL-03 |
| **BR-OP-DCL-019** | 预览/提交 **`orgId` 必填**（交账科室）；缺省 → EX-OP-DCL-102 | IF-OP-DCL-02、03 |
| **BR-OP-DCL-020** | **漏交硬栏**：窗前存在更早未交账 → 返回 `gapHardBlockEarliestAt`/`gapHardBlockMessage`；**提交硬拦** EX-OP-DCL-106；**不**灰确认入口、**不**挡弹窗勾选 | IF-OP-DCL-02、03 |
| **BR-OP-DCL-021** | **漏交软提醒**：返回 `gapSoftWarnCount`（窗后未交账条数）；提交响应亦可带回；**仅提醒** | IF-OP-DCL-02、03 |
| **BR-OP-DCL-022** | 预览返回 **`practitionerIdsInWindow`** 与 **`practitionerOptions`**（enterer 归一 Practitioner）；FE 收款人下拉**仅**使用该列表 | IF-OP-DCL-02 |
| **BR-OP-DCL-023** | **撤回**：仅 `SUBMITTED`；**提交人**或**会计/管理员**（`isFinancialDelegateEnabled` 同口径）→ `WITHDRAWN` 并清挂载；否则 EX-OP-DCL-107 | IF-OP-DCL-06 |
| **BR-OP-DCL-024** | 历史行须返回 **`statusEnum`** + **`statusEnumText`**（`CashierSettlementStatus.getDisplay()`） | IF-OP-DCL-04 |
| **BR-OP-DCL-025** | 重打须返回 **`mountedVoucher`**（按挂载 payment 现算 rows/summary/`patientSummaries`）；**禁止**依赖 `print_snapshot_json` | IF-OP-DCL-05 |
| **BR-OP-DCL-032** | **患者分项只读块**：`patientSummaries` 与合计 `summary` **同源**（同费用性质筛、同剔除工伤、同 fin_type/渠道/费用性质算法），按就诊优先分组；块内含医保现金（费用性质表），**不含**医保上传差额、**不含**预交金相关列；门诊特有 **`hospitalized`/`hospitalizedText`（是否住院）**=患者存在未终态住院就诊（IMP，排除取消/错误录入/完诊/已完成）；身份头含 **`chargeDate`（收费日期，`yyyy-MM-dd`）**=桶内首笔有 `billDate` 的流水本地日；公共头含 **`medicationTotalAmount`（药品合计金额）**=西药+外购+中草药+中成药折后 fin_type 代数和；除上传差额外各金额字段各块加总勾回合计；FE 双 Tab + 分别打印 + **患者分项 Excel 导出**（**IF-OP-DCL-07**；`AnnotatedExcelExporter` 真 xlsx，对齐门诊费用明细；一行一患者）；原单重打挂载现算同出；**禁止**参与提交/挂载写路径；装配须**全窗一次**预载账户/险种/fin_type 快照与在院患者集，禁止按患者重复 Extension IO | IF-OP-DCL-02、05、07 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-OP-DCL-01 | 进入门诊日结页 | 下发默认日结时间、费用性质、岗池 `cashierOptions`、`currentCashierId`、`discountFeatureEnabled` | 读 | BR-OP-DCL-013、031 | — | Practitioner / Contract / 折扣配置 | `PractitionerRoleScopeLoader`；`ContractQueryService` |
| IF-OP-DCL-02 | 选科室/时间/收款人后点「查询」 | **日结查询**：未交账笔数 + 汇总 + items + handover* + 漏交栏 + 窗内收费人 | 读 | BR-OP-DCL-001～003、008～022 | — | CashierSettlement / PaymentReconciliation | `PaymentReconciliationQueryService`（预览/汇总/漏交/enterer） |
| IF-OP-DCL-03 | 预览后点「确认交账」 | 按 `paymentIds` 提交门诊交账并挂载 payment | 写 | BR-OP-DCL-001～007、009、016～021 | 交账头 **SUBMITTED**；勾选 payment 已挂载 | CashierSettlement / PaymentReconciliation | 提交前重锁；`CashierSettlementDomainService#submitCashierSettlement` |
| IF-OP-DCL-04 | 打开「交账历史」 | 按提交时刻窗分页历史；含状态展示 | 读 | BR-OP-DCL-024 | — | CashierSettlement | `CashierSettlementQueryService#pageHistory` |
| IF-OP-DCL-05 | 历史行点「重打」 | 按挂载 payment **现算**交账单打印体 | 读 | BR-OP-DCL-025 | — | CashierSettlement / PaymentReconciliation | `previewPaymentsByCashierSettlementId` 等 |
| IF-OP-DCL-06 | 历史行点「撤回」（待审） | 撤回待审交账并清挂载 | 写 | BR-OP-DCL-023 | **SUBMITTED → WITHDRAWN**；payment 挂载清空 | CashierSettlement / PaymentReconciliation | `CashierSettlementDomainService#withdrawCashierSettlement` |

---

## 3. 缺口跟踪（Gap）

**无待跟踪缺口**（`GAP-CS-001` 已关闭；漏交硬/软栏、审批态、挂载现算重打已在 v4.0 实现）。

---

## 4. 接口编排说明书（业务步骤）

### IF-OP-DCL-01 门诊日结页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入门诊日结页 |
| **主路径步骤** | 返回 `defaultDateFrom`（当日 00:00:00）、`defaultDateTo`（≈当前时刻）；`expensePropertyOptions`（有效 `fin_contract`）；**`cashierOptions`** = `ROLE_CASHIER` 岗池（**BR-OP-DCL-013**）；`currentCashierId`；**`discountFeatureEnabled`**（**BR-OP-DCL-031**） |
| **关键分支** | 未绑定 Practitioner 不阻断整页初始化；**FE 收款人下拉不消费** `cashierOptions`，待 IF-OP-DCL-02 后使用 `practitionerOptions` |
| **失败分支** | 未登录 → EX-OP-DCL-101 |
| **查询侧说明** | 只读配置与主数据 |

### IF-OP-DCL-02 日结查询（未交账预览）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选有效 `dateFrom`/`dateTo` 与 **`orgId`** |
| **主路径步骤** | **①** Gate：`orgId`、日期窗。**②** 组装 `PreviewCashierSettlementQuery`（`type=OP_PAYMENT`；`cashierIds` 软筛；**不下推** org→location payment 过滤）。**③** 漏交扫描：硬栏时刻/文案、软提醒条数。**④** 加载未交账候选与展示汇总（**BR-OP-DCL-014**）。**⑤** fin_type / 渠道 / 合同 enrichment → rows、summary、items、handover*。**⑥** 解析窗内 enterer → `practitionerIdsInWindow`/`practitionerOptions` |
| **关键分支** | 无未交账：`paymentCount=0` 非异常；有硬栏时 FE 提交 Gate（后端提交亦 EX-OP-DCL-106）；`cashierIds` 空 = 全窗不按人 |
| **失败分支** | 参数非法 → EX-OP-DCL-102；查询失败 → EX-OP-DCL-103；fin_type 数据不完整 → EX-OP-DCL-104 |
| **查询侧说明** | 不写库 |

### IF-OP-DCL-03 确认交账

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已完成 IF-OP-DCL-02；弹窗内已勾选 ≥1 笔；**无漏交硬栏** |
| **主路径步骤** | **①** Gate：登录、`submitterId`（Practitioner 必填）、`orgId`、日期窗。**②** 漏交硬栏复扫 → 有则 EX-OP-DCL-106。**③** 重锁候选；校验 `paymentIds` ⊆ 候选且非空（非法 → EX-OP-DCL-105；空 → EX-OP-DCL-201）。**④** 按勾选集调用 `OutpatientDailySettlementMapper#resolveSelectedSettlementTotalIncome`（与 `handoverSummary.totalIncome` 同 `buildDisplayBundle`）写入头表 `total_income`。**⑤** 写交账头（`OP_PAYMENT`、`SUBMITTED`、窗内 `practitionerIds` 快照等）并挂载勾选 payment。**⑥** 发布事件。**⑦** 返回 `settlementId`、`busNo`、`paymentCount`、`statusEnum`、`gapSoftWarnCount` |
| **关键分支** | 分批提交；并发 → EX-OP-DCL-301；软提醒不拦提交 |
| **失败分支** | 关闭弹窗不调用；空/非法 `paymentIds` → EX-OP-DCL-201/105 |
| **查询侧说明** | 禁止仅信任前端缓存列表 |

### IF-OP-DCL-04 交账历史分页

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户打开交账历史 |
| **主路径步骤** | 按 `submittedFrom`/`submittedTo` 与分页参数查询 `type=OP_PAYMENT` 历史；装配 `statusEnum`、`statusEnumText`、`canReprintFromMounts` 等 |
| **关键分支** | 默认 `pageIndex=0`、`pageSize=20`（最大 200） |
| **失败分支** | 时刻窗非法 → EX-OP-DCL-102；未登录/查询失败 → EX-OP-DCL-103 |
| **查询侧说明** | 只读 |

### IF-OP-DCL-05 交账单挂载现算重打

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户从历史选择可重打记录 |
| **主路径步骤** | 校验交账头存在且 `type=OP_PAYMENT`；按挂载 payment 复用预览汇总管线；返回 `mountedVoucher`（rows、summary、settlementPeriodText、paymentIds） |
| **关键分支** | 无挂载时仍返回可打印表头结构 |
| **失败分支** | 头不存在/类型不符 → EX-OP-DCL-103；参数空 → EX-OP-DCL-102 |
| **查询侧说明** | **不落库**；**不读** `print_snapshot_json` |

### IF-OP-DCL-06 撤回待审交账

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 历史行 `statusEnum=SUBMITTED`；操作者须为款员 |
| **主路径步骤** | Gate 权限与状态 → `withdrawCashierSettlement` → 清挂载、改 **WITHDRAWN** |
| **关键分支** | 已审批/已驳回/已撤回 → 拒绝 |
| **失败分支** | 无权限/状态非法 → EX-OP-DCL-107 |
| **查询侧说明** | 写路径单事务 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-OP-DCL-101 | 初始化失败 | 页面初始化失败，请稍后重试 | 是 |
| EX-OP-DCL-102 | 日期/科室/参数非法 | 请选择有效的日结时间范围 / 交账科室 | 否 |
| EX-OP-DCL-103 | 查询/记录不存在 | 查询失败，请稍后重试 / 交账记录不存在 | 是 |
| EX-OP-DCL-104 | 收费明细数据不完整 | 存在收费明细缺少定价锚点或金额，无法汇总财务类别 | 否 |
| EX-OP-DCL-105 | 勾选 payment 非法 | 所选流水不在待交账范围内，请刷新后重试 | 否 |
| EX-OP-DCL-106 | 漏交硬栏 | 存在截止前未交账流水，请调整日结起点至不晚于最早漏交时刻 | 否 |
| EX-OP-DCL-107 | 撤回权限/状态 | 仅待审交账可撤回 / 仅款员可撤回 | 否 |
| EX-OP-DCL-201 | 无可交账流水 | 当前条件下没有未交账流水 | 否 |
| EX-OP-DCL-301 | 并发交账冲突 | 部分流水已被交账，请刷新后重试 | 是 |
| EX-OP-DCL-410 | 交账成功 | 交账成功，单号 {busNo}（待审批） | — |

---

## 6. 事件语义与消费方

| 事件 | 触发 IF | 典型消费方 | 幂等/一致性语义 |
| --- | --- | --- | --- |
| `CashierSettlementSubmittedEvent` | IF-OP-DCL-03 | 审计、**AS-FIN-CS-001** 待办 | 以交账头标识幂等；载荷含 `type=OP_PAYMENT`、提交人、科室、挂载条数 |
| `CashierSettlementWithdrawnEvent` | IF-OP-DCL-06 | 审计 | 载荷含交账头标识；payment 挂载已清 |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | IF-OP-DCL-02 ≤2000ms；IF-OP-DCL-03 ≤5000ms；历史/重打 ≤2000ms |
| **事务** | 提交/撤回单事务：交账头与 payment 挂载同时成功或同时失败 |
| **并发** | 提交时 payment 行级校验仍为未交账；冲突整单失败 |
| **一致性** | 查询与提交共用同一领域筛选口径；重打与预览同汇总管线 |
| **审计** | 交账头记录 `submitterId`、操作人与提交时刻 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 有未交账 → 查询有汇总与 `practitionerOptions` → 弹窗全选 → 提交成功 `SUBMITTED` → 历史可见 → 重打 `mountedVoucher` 与预览同构 |
| **软筛** | `cashierIds` 空 = 全窗；传值仅筛 enterer；不影响 `paymentIds` 校验口径（仍全窗未交账候选） |
| **漏交** | 窗前漏交 → 硬栏文案 + 提交 EX-OP-DCL-106；窗后未交 → `gapSoftWarnCount` 仅提醒 |
| **撤回** | 款员可撤回 SUBMITTED；非款员/非待审 → EX-OP-DCL-107；撤回后 payment 可再次交账 |
| **弹窗/分批** | handoverSummary 一致；分批后剩余可交账 |
| **渠道/费用性质** | 同 v3.9 要点 |
| **并发** | 双窗口同 payment → EX-OP-DCL-301 |

---

## 9. 关联文档与变更历史

| 文档 |
| --- |
| `AppService-SDD/AS-OP-FIN-004-门诊收费门诊日结-SDD.md` |
| `AppService/AS-FIN-CS-001-交账审批.md` |
| `docs/领域/DomainModel/DM-FIN-003-CashierSettlement-收费员交账.md |
| `DomainModel-SDD/DM-FIN-003-CashierSettlement-收费员交账-SDD.md` |
| `docs/领域/DomainModel/DM-FIN-002-PaymentReconciliation-支付流水.md |
| `AppService/AS-OP-FIN-002-门诊收费.md` |
| `AppService/AS-IP-FIN-005-住院收费住院日结.md` |

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v4.19 | 2026-10-14 | **BR-OP-DCL-013**：日结收款岗池显式 `excludePlatformAdmin=false`；业务选型下拉默认剔 admin 见 **B.6.3.1**；代码先行回写 |
| v4.18 | 2026-10-13 | **IF-OP-DCL-07**：患者分项导出改后端 `AnnotatedExcelExporter` 真 xlsx（对齐门诊费用明细）；代码先行回写 |
| v4.17 | 2026-10-13 | **BR-OP-DCL-032**：FE 患者分项 Excel 导出（一行一患者；SpreadsheetML；不新增后端 IF）；代码先行回写 |
| v4.16 | 2026-10-13 | **BR-OP-DCL-032**：患者分项身份头增 **`chargeDate`（收费日期 yyyy-MM-dd）**；代码先行回写 |
| v4.15 | 2026-10-13 | **BR-OP-DCL-032**：患者分项只读块 `patientSummaries`（同源按就诊拆、不含上传差额/预交金、含是否住院、双 Tab/分别打印、挂载现算重打）；公共头增 **药品合计金额**；代码先行回写 |
| v4.14 | 2026-10-06 | `discountFeatureEnabled` 明确为「已配置 ACTIVE 折扣内容」 |
| v4.13 | 2026-10-06 | **BR-OP-DCL-010/031**：上半区与总收入改为折后；折扣单列；应收=总收入+折扣；读侧 `applyDiscountedFinTypeAmounts` |
| v4.12 | 2026-10-06 | 增 **BR-OP-DCL-031**：下半区/打印展示折扣合计（优先 DISCOUNT 旁证，无旁证回落收费行售价−折后；仅命中折扣配置的行计入）；IF-01 `discountFeatureEnabled`；FE `DailySettlementSummaryPanel` |
| v4.11 | 2026-09-09 | 增 **BR-OP-DCL-030**：工伤独立统计块（`workInjurySummary`；分类归并+统筹/个人自付；不入总合计）；FE 按院点工伤开关展示；打印并入渠道/性质字段 |
| v4.10 | 2026-09-02 | 增 **BR-OP-DCL-029**：2601 成功后桥表 REVOKED + 软删统筹/个账明细，与平台净额同态；防失败 2207 误冲成功回执 |
| v4.9 | 2026-08-31 | §1.3：交叉 **BR-OPC-024** 终态复验（扫码/拆账户后明细合计=收费项）；读侧勾稽依赖写侧 Gate |
| v4.8 | 2026-08-26 | §1.3：明确渠道勾稽须含支付宝/银行卡与上传差额；交叉 **BR-OPC-024**（写侧明细合计=收费项） |
| v4.7 | 2026-08-17 | **BR-OP-DCL-010** / §1.3：明确 `fin_payment_rec_static.amount` 为收费行 **代数和**（禁绝对值）；对齐 `PaymentFinTypeStaticSnapshotWriter` |
| v4.6 | 2026-08-03 | 合并远端「医保上传差额」：新增 **BR-OP-DCL-028**（原远端误占 027）；**027** 固定为费用性质混结分桶；公共头纳入上传差额勾稽项 |
| v4.5 | 2026-08-02 | 增 **BR-OP-DCL-027** / §1.3 术语：纯医保现金口径不动；仅自费+医保混结按明细账户拆桶（挂自费户→自费，挂医保户 CASH→医保现金）；落点 `DailySettlementExpenseNatureDetailAccumulator` |
| v4.4 | 2026-07-30 | **FE**：右侧侧栏嵌 CHS 日对账（`DailySettlementChsReconPanel` / `DailyLedgerList` compact）；**不展示**体检人次；收款人多选不折叠 tags；对齐 SDD v4.4 |
| v4.3 | 2026-07-30 | **BR-OP-DCL-026**：交账弹窗行净额按带符号 charge 行额回写，医保全个账禁止两行 0；对齐 `DailySettlementPaymentDetailLoader` |
| v4.2 | 2026-07-29 | **BR-OP-DCL-015**：交账弹窗支付方式前缀按合同 `hasInsurance`，后缀按渠道；对齐 `DailySettlementPaymentDetailLoader` |
| v4.1 | 2026-07-28 | IF-OP-DCL-03：头表 `total_income` 强制 `resolveSelectedSettlementTotalIncome`（与 `handoverSummary.totalIncome` 同管线）；消解合并冲突；对齐成码 |
| v4.0 | 2026-07-27 | **审批态交账**：`OP_PAYMENT` + 提交后 `SUBMITTED`；IF-OP-DCL-04～06；`orgId` 必填；`cashierIds` 软筛；漏交硬/软栏；窗内 `practitionerOptions`；岗池 `ROLE_ACCOUNTANT`；EX-OP-DCL-106/107；删除旧 activeCashiers 契约 |
| v3.9 | 2026-07-11 | 交账明细弹窗、handover*、`paymentIds` 勾选提交（**BR-OP-DCL-015～018**） |
| v3.8 | 2026-07-09 | **BR-OP-DCL-014**：rows/summary 含已交账 |
| v3.0 | 2026-06-21 | health 应用层日结查询+交账重写 |
| v1.0 | 2026-06-19 | 首版（已废止） |
