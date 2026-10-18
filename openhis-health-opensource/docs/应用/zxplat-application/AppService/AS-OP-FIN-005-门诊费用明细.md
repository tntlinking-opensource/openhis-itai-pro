# 门诊费用明细 · 应用服务设计（AS）

> **迁移声明（2026-07-21）**：本页将迁入报表中心并由 **`AS-RPT-008`** 承接（保留现明细，**新增按费用类型净汇总**）；族边界见 `health/设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md`。在 **AS-RPT-008** 成稿前，本文仍为现网门诊费用明细读侧权威。
>
> **文档职责**：说明「门诊费用明细」查询页的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为 **health-application** 门诊财务包实现的编排权威。
>
> **页面边界**：独立查询页——多条件筛选 → **按就诊分组**展示明细与金额 → **分页** → **导出**；**只读**；**纯查询**，不提供跳转收费/退费/就诊详情。（目标态挂报表中心，见迁移声明。）
>
> **编码**：AS-OP-FIN-005 · **版本**：v1.11 · **日期**：2026-10-06（迁移声明 2026-07-21）

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-OP-FDT-01 页初始化；IF-OP-FDT-02 分页查询（按就诊分组 + 明细行 + 就诊小计 + 全局合计）；IF-OP-FDT-03 导出（与查询同口径） |
| **在外** | 门诊收费（**AS-OP-FIN-002**）；门诊退费（**AS-OP-FIN-003**）；门诊日结（**AS-OP-FIN-004**）；报表中心通用引擎（**AS-RPT-CEN-001**）；机构/医生工作量报表；住院费用明细 |
| **涉及域** | **ChargeItem**（费用事实）；**PaymentReconciliation** / **PaymentRecDetail** / **PaymentYbSettlement**（收退时间与实付拆分）；**Encounter**（就诊挂号科室、门诊号）；**Patient**（姓名检索）；**ProductDefinition**（项目名/编码/规格/大类细分类）；**Practitioner**（开单医生、收费人） |
| **编排权威** | 本文 **§2、§4**；纳入费用状态与支付链路口径对齐 **DM-ADM-011**、**DM-FIN-002**；老报表「费用明细列表」业务语义以本文为准重组 |
| **数据锚点** | 明细行以 **`adm_charge_item`** 为事实源；**时间筛选、收费人筛选**以关联 **`fin_payment_reconciliation.bill_date`**、**`enterer_id`** 为准（非 `occurrence_time`） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **收费员 / 财务 / 管理人员** | 设定筛选（日期、科室、大类/细分类、患者、项目、收费人、收退 Tab）→ **查询** → 查看按就诊分组的明细与金额合计 → **导出** |
| **系统** | 租户内按 Payment 收退链路加载账单明细；组装财务金额列；按收费时间倒序分页；**退费冲减** |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **与日结/发药边界** | **日结**=Payment（含分类快照）；**发药/药房日结**=数量×批号单价；**本页费用明细**=ChargeItem 行额 + Payment `bill_date`/收退链路（与日结同轴；写路径退费会改 ChargeItem 态并覆盖 `entered_date`，故时间轴**须**扫 Payment） |
| **纳入明细状态** | ChargeItem **`SETTLED`∪`REFUNDING`∪`REFUNDED`**（写路径退费后原行可能已是 `REFUNDING`/`REFUNDED`；行额正负看 payment 收退，不看行态）；`REFUNDING` 表示退费进行中、原 SUCCESS 快照仍挂该行，**须计入已收净额**直至退费 Payment 落库冲减（与日结 static 对账）；排除待结算等其它过程态 |
| **行粒度** | **一账单一行**；金额权威 `unit_price` / `total_price`（与药品数量×定价同源）；同 chargeItem 在收费/退费 payment 上可各一行 |
| **收退 Tab** | **`CHARGE`**=SUCCESS payment；**`REFUND`**=REFUNDED payment（负号）；**`ALL`**=并集净额（退费冲减） |
| **时间筛选** | Payment **`bill_date`** 日窗（BR-OP-FDT-003；与日结交账窗同轴）；默认**当日** `[dateFrom, dateTo]` |
| **排序** | 明细按收费时间倒序；就诊块按块内最新收费时间倒序 |
| **科室** | 列表与筛选中的「科室」= **就诊挂号科室**（Encounter 挂号组织/科室），**非** ChargeItem.`requesting_org_id` |
| **大类（feeCategory）** | **药品 / 诊疗 / 耗材 / 挂号** 四类；映射 **`service_table` / `context_enum`**（**BR-OP-FDT-006**）；筛选**可多选** |
| **细分类** | 在大类下按定价行 **`fin_type_code`/`type_code`**（及字典译文）进一步筛选 |
| **患者模糊** | 姓名、门诊号 **`busNo`**（Encounter） |
| **项目模糊** | **项目名称** 与 **院内项目编码** 两个条件 **AND**（均非空时同时满足；单填则单条件生效） |
| **开单医生** | 上游三类 Request 且 **`generate_source=doctor-prescription`** 的 **`requester_id`**；护士划价等非医生开立 **不回填**；**非** ChargeItem.`enterer_id`（该字段已为收费人） |
| **执行人** | ChargeItem **`performer_id`**（有则回显）；与开单医生分立 |
| **收费人** | 仅结算/退费完成写入的 ChargeItem **`enterer_id`**（与 **BR-OPC-016** 一致；待结留空） |
| **分页** | **按就诊块**分页，默认 **10** 条/页（10 个就诊块）；块内明细全量返回；越界页钳回末页 |
| **租户** | 仅当前租户；**不**额外做机构/个人数据权限 Gate（系统租户隔离即可） |

#### 1.3.1 行级金额列（财务口径）

> 展示列遵循 **BR-CHG-QRY-003**（人工调价 > 系统优惠 > 原价）；下列为 AS 锁定语义，SDD 字段命名可映射但**不得**改变计算口径。
>
> **报表中心（AS-RPT-008/009）默认展示列**：进价单价 / 进价总价 / 售价单价 / 售价总价；**已配置有效折扣**时另展示 **优惠金额 / 实收金额**（**BR-OP-FDT-018** / **BR-RPT-OFD-008**）。药耗进价锚**批号** `LOT_NUMBER_COST`（批号来自发放单或收费锚点 `LOT_NUMBER_PRICE.lot_no`；缺 COST 时同批号 `LOT_NUMBER_PRICE` 回落；**有批号禁止**回落目录 `PURCHASE`；仅药耗有调价且批号一致时回放调前进价）；**诊疗/挂号无真实进价**——进价统计直接取账单 `unit_price`（**不回放**、不读目录 `PURCHASE`/`SALE`）（**BR-PRICE-SRC-001**）；西药/耗材进售同值，中药院点加价后可不同；售价锚 `unit_price`/`total_price`。原价/应收/折后/医保/自费仍可由 API 回传。

| 展示列 | 业务含义 | 权威与计算 |
| --- | --- | --- |
| **进价单价** | 历史进价单价（与操作单位同阶展示） | 药耗：批号 `LOT_NUMBER_COST`（同上回落/同批号回放），展示单价与 `quantity_unit` 对齐；**诊疗/挂号**：无进价（列空；报表中心口径）；退费取负 |
| **进价总价** | 行进价总额（非「展示进价单价×数量」另算） | 药耗：按操作单位分支——包装单位→`包装价×包装数量`；拆零→满包×包装价+零头（BR-MON-PKG-001 / `ChargeItemPackageUnitRatioSupport`）；**禁止**包装价÷ratio 再×数量；退费取负 |
| **售价单价** | 向患者计费单价 | ChargeItem **`unit_price`**；退费取负 |
| **售价总价** | 向患者计费行额 | ChargeItem **`total_price`**；退费取负 |
| **实收金额** | 该明细折后实收净额 | **`discount_amount`（折后应收）**；缺失回退 **`total_price`**；退费链路取负（**BR-OP-FDT-012**） |
| **原价**（API 兼容） | 项目定价基准 | ChargeItem **`base_amount`** |
| **优惠金额** | 相对售价总价的减免 | 优先 **`system_discount_price`（写路径优惠差额）**；否则 **`|total_price| − |discount_amount|`**（≤0 为 0）。**禁止**用空/0 的 `base_amount` 当被减数（收银折扣后 `total_price` 仍为原价、`discount_amount` 为折后） |
| **应收金额**（API 兼容） | 折扣后应向患者收取的项目金额 | **`discount_amount`**；缺失时回退 **`total_price`** |
| **折后金额** | 与应收同锚 | **`COALESCE(discount_amount, total_price)`**；**禁止**把优惠差额 `system_discount_price` 当成折后价 |
| **单价**（API 兼容） | 账单单价（同售价单价） | **`unit_price`** 原样 |
| **数量 / 单位** | 计费数量 | **`quantity_value`** / **`quantity_unit`**（字典译文） |
| **行金额**（API 兼容） | 账单事实总价（同售价总价） | **`total_price`**；退单行 × **−1** 展示（**BR-OP-FDT-012**） |
| **医保金额**（API 兼容） | 医保记账部分（横向列） | 若 payment 存在**已确认** **`fin_payment_yb_settlement`** 关联：按该 payment 快照内各 chargeItem 的 **`|total_price|` 占比**分摊医保结算凭证中的 **基金 + 个账**（及院内约定的医保侧合计项）；纯自费 payment 为 **0** |
| **自费金额**（API 兼容） | 患者自付部分（横向列） | **`实收金额 − 医保金额`** |
| **折扣率（可选展示）** | 相对原价的折扣比例 | 仅展示型；规则对齐 **AS-OP-FIN-002** **BR-OPC-011**（步进规整），**不影响**上表金额 |

#### 1.3.2 合计口径

| 合计层级 | 口径 |
| --- | --- |
| **就诊小计** | 块内各明细行对上述金额列 **代数求和**（已含退单负号） |
| **全局合计** | 当前查询命中全集（**非仅当前页**）的同列求和；随 IF-OP-FDT-02 一并返回 **`summary`** |
| **统计与展示解耦** | 行级 **单价 / 原价** 不因合计为 0 而改写；合计仅汇总金额列 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-OP-FDT-01～03 |
| **非目标** | 报表中心 RPT 引擎接入；待结算/待收费明细；按医保/自费/整体/退费分 **统计类型 Tab**（老 `StatisticsType` 四 Tab，**舍弃**）；机构/医生工作量报表；跳转收费窗/退费/就诊详情；按 `organizationId` 或收费员个人权限收窄；chargeItem 十四项费用分类轧差；右侧医保清算侧栏 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 职责摘要 | DM 路径 |
| --- | --- | --- |
| **ChargeItem** | 费用事实、状态、金额字段；开单医生见上游 Request `requester_id`（非 `enterer_id`） | `DM-ADM-011-ChargeItem-收费明细.md` |
| **PaymentReconciliation** | `bill_date`、收费人、收退方向、charge_item_ids 快照 | `DM-FIN-002-PaymentReconciliation-支付流水.md` |
| **PaymentRecDetail** | 自付渠道读（自费金额校验辅助，**禁止**从本表汇医保） | `DM-FIN-002` §3.2 |
| **PaymentYbSettlement** | 医保结算关联读（医保金额分摊） | `DM-FIN-002` §3.4 |
| **Encounter** | 门诊号、挂号科室、就诊日期 | `DM-ADM-002` |
| **Patient** | 姓名 | `DM-ADM-001` |
| **ProductDefinition** | 项目名/编码/规格/大类/细分类 | `DM-MED-001` / `DM-WKF-001` / `DM-ADM-008` §6.2 |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-OP-FDT-001** | `dateFrom`、`dateTo` 必填；`dateFrom ≤ dateTo`；默认均为**当日** | 01、02、03 |
| **BR-OP-FDT-002** | 仅 **`SETTLED` / `REFUNDING` / `REFUNDED`** 且关联 payment **`SUCCESS` / `REFUNDED`** 的明细；**排除**待结算/待收费等其它过程态；`REFUNDING` 挂在收费 SUCCESS 上时按正向已收计入 | 02、03 |
| **BR-OP-FDT-003** | 时间过滤、收费人过滤基于 payment **`bill_date`** / **`enterer_id`** 下推，禁止仅用 `occurrence_time` 替代 | 02、03 |
| **BR-OP-FDT-004** | 科室筛选与展示 = **就诊挂号科室** | 02、03 |
| **BR-OP-FDT-005** | 患者关键字仅匹配 **姓名、busNo** | 02、03 |
| **BR-OP-FDT-006** | 大类映射：`med_medication_request`/`med_medication_dispense`→**药品**；`wkf_service_request`→**诊疗**；`wkf_device_request`/`wkf_device_dispense`→**耗材**；`adm_healthcare_service`→**挂号**；未识别 → **整单失败** | 02、03 |
| **BR-OP-FDT-007** | 项目名称与院内编码：双填 **AND**，单填单条件 | 02、03 |
| **BR-OP-FDT-008** | **`feeCategory` 可多选**；细分类筛选在大类命中子集内生效 | 02、03 |
| **BR-OP-FDT-009** | **`tradeDirection`** Tab：`CHARGE` / `REFUND` / `ALL` | 02、03 |
| **BR-OP-FDT-010** | 行级金额列遵循 **§1.3.1** 与 **BR-CHG-QRY-003** | 02、03 |
| **BR-OP-FDT-011** | 医保金额从 **`PaymentYbSettlement` + CHS 凭证**分摊；**禁止**从 `PaymentRecDetail.pay_enum` 推断医保 | 02、03 |
| **BR-OP-FDT-012** | 退费链路明细：金额列（行金额/实收/医保/自费/折后等）**负号展示**；原单保持正号 | 02、03 |
| **BR-OP-FDT-013** | 分页 **按就诊块**，默认 pageSize=**10**；返回 **`summary` 为全量合计** | 02 |
| **BR-OP-FDT-014** | 块内明细按 **`bill_date` 倒序**；块间按块内最新 **`bill_date` 倒序** | 02、03 |
| **BR-OP-FDT-015** | 每条明细须可解析产品展示锚点（`product_table/product_id`；挂号费可回落 `service_table=adm_healthcare_service` + `service_id`）与 **`category_code`**；否则 **整次查询失败** | 02、03 |
| **BR-OP-FDT-016** | 导出与 IF-02 **同筛选、同口径、同排序**；不分页或按实现上限全量导出 | 03 |
| **BR-OP-FDT-017** | 细分类（`fin_type_code`）解析与日结交账口径一致：`DailySettlementFinTypeAmountLoader#mapFinTypeCodeByChargeItemId`；**仅**锚 `charge_item.definition_id` 历史定价行 `type_code`（挂号/号源强制诊察费 `1012`）；**禁止**回落当前目录定价行。硬筛轻量投影**可不含** `unit_price`（性能）；`definition_id` 缺失或锚点无法解析 → **整次查询失败**。单价/总额完整性由落库创建路径与金额展示 IF 保证，**禁止**因投影未选单价误报脏数据 | 02、03 |
| **BR-OP-FDT-018** | 已配置 ACTIVE `cus_charge_discount_config` 时，报表中心明细 Tab/导出展示 **优惠金额、实收金额**；优惠/折后/实收计算见 **§1.3.1**。与收费页相同：配没配有效折扣内容即显隐依据 | 01～03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-OP-FDT-01 | 进入门诊费用明细页 | 下发默认日期、收退 Tab、大类/细分类选项、科室与收费员下拉 | 读 | BR-OP-FDT-001 | 无 | Encounter / ProductDefinition / Practitioner | 主数据 QueryService |
| IF-OP-FDT-02 | 设定筛选 + 收退 Tab + 点「查询」/翻页 | 分页返回**就诊块**（患者头 + 明细行 + 就诊小计）+ **全局 summary** | 读 | BR-OP-FDT-001～017 | 无 | ChargeItem / PaymentReconciliation / Encounter / Patient / ProductDefinition / Practitioner | **`ChargeItemQueryService#pageOutpatientFeeDetailEncounterBlocks`**（**DM-ADM-011** §6.2.25）；`PaymentReconciliationQueryService`（**DM-FIN-002** §6.2.17～6.2.18）；`DailySettlementFinTypeAmountsupport（细分类） |
| IF-OP-FDT-03 | 点「导出」 | 按当前筛选导出 Excel（列含 **§1.3.1** 全部金额列） | 读 | BR-OP-FDT-001～017 | 无 | 同 IF-02 | 同 IF-02 |

> **合并说明**：切换收退 Tab、调整任意筛选项后点查询，均为 **IF-OP-FDT-02**；导出 **IF-OP-FDT-03** 与 02 共用筛选 payload。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-OP-FDT-001 | QueryService 能力 | `DM-ADM-011` §6.2.25～6.2.26、`ChargeItemQueryService` | 缺门诊费用明细专用分页查询：`bill_date` 闭区间 + 挂号科室 + 大类/细分类 + 患者/项目 + 收费人 + 收退方向 + **`SETTLED/REFUNDING/REFUNDED`** + 就诊块分页 | `pageOutpatientFeeDetailEncounterBlocks` 及 QueryCommand 已实现并与本文 BR 一致 | IF-OP-FDT-02、03 | P0 | **已关闭** |
| GAP-OP-FDT-002 | QueryService 能力 | `DM-FIN-002` §6.2.17～6.2.18、`PaymentReconciliationQueryService` | 缺按 chargeItem 批量加载 payment **`bill_date`/`enterer_id`/医保关联** 的读侧 enrichment（供明细行收费人与 **BR-OP-FDT-011** 分摊） | 批量 enrichment 方法已实现并文档对齐 | IF-OP-FDT-02、03 | P0 | **已关闭** |

> **说明**：就诊块组装、医保占比分摊算法、Excel 列映射属**应用层编排**，**不入 Gap**。scope 预解析（payment→科室/患者/项目/细分类）由 **`OutpatientFeeDetailQuerysupport**（`application.outpatient.financial.charge.support`）承担，**非 Gap**。`queryCostDetails`（§6.2.4）为单就诊视图，**不能**替代本场景。

### 3.1 Gap 关闭落点（2026-06-21）

| Gap ID | DM 章节 | Java 落点 | 备注 |
| --- | --- | --- | --- |
| GAP-OP-FDT-001 | `DM-ADM-011` §6.2.25～6.2.26 | `ChargeItemQueryService#pageOutpatientFeeDetailEncounterBlocks`、`#summarizeOutpatientFeeDetailAmounts` | 须先经 `OutpatientFeeDetailQuerySupport#prepareOutpatientFeeDetailQueryScopes` 写入 scope |
| GAP-OP-FDT-002 | `DM-FIN-002` §6.2.17～6.2.18 | `PaymentReconciliationQueryService#listOutpatientFeeDetailChargeItemIdsByPaymentFilter`、`#mapOutpatientFeeDetailPaymentContextByChargeItemIds(OutpatientFeeDetailPageQuery, Collection<Long>)` | `map*` 为两参签名；context 由 list 预载或 map 内补调 list |

---

## 4. 接口编排说明书（业务步骤）

### IF-OP-FDT-01 门诊费用明细页初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入门诊费用明细页 |
| **主路径步骤** | 下发默认 **`dateFrom=dateTo=当日`** → 收退 Tab 枚举（`ALL`/`CHARGE`/`REFUND`）→ 费用大类四选项 + 各细分类字典（药品/诊疗/耗材）→ 挂号科室下拉（本租户门诊挂号组织）→ 收费员下拉（本租户 Practitioner）→ 默认 `tradeDirection=ALL`、默认 pageSize=10 |
| **关键分支** | 细分类随大类联动由前端本地过滤；服务端一次下发全集 |
| **失败分支** | 主数据不可用 → EX-OP-FDT-101 |
| **查询侧说明** | 只读配置与主数据；前端 API 须 `publicRequestClient` 解包 `data`，否则下拉与列表字段读不到 |

### IF-OP-FDT-02 分页查询门诊费用明细

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 有效日期区间；用户点查询或翻页 |
| **主路径步骤** | **①** 校验 **BR-OP-FDT-001、009**。**②** `OutpatientFeeDetailQuerySupport#prepareOutpatientFeeDetailQueryScopes` 预解析 scope。**③** 调用 **`ChargeItemQueryService#pageOutpatientFeeDetailEncounterBlocks`**（**BR-OP-FDT-002～008、003、014**）得当前页就诊 ID 集及块内 chargeItem 行。**④** 批量加载 Encounter/Patient（挂号科室、busNo、姓名）、ProductDefinition（项目名/编码/规格/细分类）、Practitioner（开单医生）。**⑤** 批量 enrichment payment（**bill_date**、收费人、医保关联，**DM-FIN-002** §6.2.18）。**⑥** 按 **§1.3.1** 计算各行原价/优惠/应收/折后/行金额/实收/医保/自费（**BR-OP-FDT-010～012**）；退单负号。**⑦** 组装块头、明细行、**就诊小计**。**⑧** 对**全量命中集**（非当前页）计算 **summary**（**BR-OP-FDT-013**）。**⑨** 返回 `blocks`、`summary`、`pageNo`、`pageSize`、`totalCount`（就诊块数） |
| **关键分支** | 无命中：空 blocks + summary 全 0；`tradeDirection=CHARGE` 仅收费 payment；`REFUND` 仅退费 payment |
| **失败分支** | 日期非法 → EX-OP-FDT-102；`service_table` 无法归类 / 缺 product 或 category → EX-OP-FDT-103；查询失败 → EX-OP-FDT-104 |
| **查询侧说明** | 纯读；条件须查询层下推；禁止 App 全表加载后内存过滤 |

### IF-OP-FDT-03 导出门诊费用明细

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户已设筛选（可与 02 不同步，以导出请求体为准） |
| **主路径步骤** | 与 IF-02 **相同 Gate 与组装**（**BR-OP-FDT-016**）→ 按就诊块平铺或分组块导出 → 列顺序：患者/就诊头 → 明细基础列 → **§1.3.1 全部金额列** → 就诊小计行 → 文件末尾 **全局合计** 行 |
| **关键分支** | 命中为空 → 导出仅含表头 + 合计 0 行（或 EX-OP-FDT-201，由 SDD 定一种） |
| **失败分支** | 同 IF-02 业务失败；文件生成失败 → EX-OP-FDT-105 |
| **查询侧说明** | 只读；数据量超 SDD 上限时拒绝并提示缩小日期 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示 | 可重试 |
| --- | --- | --- | --- |
| EX-OP-FDT-101 | 初始化主数据失败 | 页面配置加载失败，请稍后重试 | 是 |
| EX-OP-FDT-102 | 日期区间非法 | 请选择有效的查询日期 | 是 |
| EX-OP-FDT-103 | 明细缺产品/分类或 service_table 无法归类 | 费用数据不完整，请联系管理员核对收费项目配置 | 否 |
| EX-OP-FDT-104 | 查询执行失败 | 费用明细查询失败，请稍后重试 | 是 |
| EX-OP-FDT-105 | 导出失败 | 导出失败，请稍后重试或缩小查询范围 | 是 |
| EX-OP-FDT-201 | 导出无数据（若采用拒绝策略） | 当前条件下无数据可导出 | 是 |

---

## 6. 事件语义与消费方

| 事件 | 来源 | 对本页的影响 |
| --- | --- | --- |
| `OutpatientPaymentSettledEvent` | **AS-OP-FIN-002** | 新增已收费明细；刷新后可见 |
| `OutpatientRefundCompletedEvent`（或等价退费完成事件） | **AS-OP-FIN-003** / **DM-FIN-002** | 新增已退费明细（负号列）；刷新后可见 |
| `OutpatientRegistrationPaymentSettledEvent` | **AS-OP-FIN-001** | 挂号类明细纳入「挂号」大类 |

> 本页 **不发布**领域事件；消费为**用户手动查询/导出**，不要求实时推送。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 分页在**就诊块**维度下推；ChargeItem、Payment、ProductDefinition、Practitioner **批量 IN**；页脚 **summary** 对同 scope 候选行做**轻量金额加总**（含退负/医保分摊；**明细分页页脚不跑全量历史进价**，`purchaseAmountTotal` 可空，行上进价仍按当前页 enrichment）；行上进价经 `HistoricalChargeItemPurchasePriceSupport`：**批号优先发放单，缺失回落锚点 `lot_no`**；品规无调价记录时只读 ACTIVE 批号进价；**有批号禁止目录 PURCHASE**；有调价品规才批量预取调价历史并**仅同批号**回放进价；**诊疗/挂号直接用账单 `unit_price`，不预取不回放**；禁止 `listHistoricalDefinitionsByInstance` 全量兄弟行；单价与行额同请求只解析一次。无项目关键字/细分类筛选时 prepare **跳过**全量产品 Map 与 fin_type 解析；同请求内候选 ChargeItem **须缓存复用**，禁止 page/summary 重复 IN。科室筛选仅在 payment 已命中的候选就诊上过滤，**禁止**按科室拉历史全部 AMB |
| **一致性** | 只读；以查询时刻持久化事实为准；payment 归档/交账状态**不影响**本页纳入（仅看 payment 成功/已退费） |
| **权限** | 租户隔离；无额外个人/机构 Gate |
| **金额刻度** | 域金额 **6 位小数 HALF_UP**；展示位数由 SDD 定，计算在后端完成 |
| **导出** | 使用框架 **AnnotatedExcelExporter**；AppService 不手写 POI |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **范围** | 含 `SETTLED`+收费 payment、含 `REFUNDING`+收费 payment（退费未完成仍计已收）、含 `REFUNDED`+退费 payment；不含待结算/待收费 |
| **收退 Tab** | `CHARGE`/`REFUND`/`ALL` 切换结果集正确；退单金额列为负 |
| **时间** | 按 `bill_date` 过滤；默认当日；跨日区间 |
| **科室** | 筛选与展示均为**挂号科室**，非开单科室 |
| **大类/细分类** | 四类均可查；挂号费进「挂号」；细分类多选 |
| **患者/项目** | 姓名、busNo 模糊；项目名 **AND** 院内编码 |
| **金额列** | 原价/优惠/应收/折后/行金额/实收/医保/自费与 **§1.3.1** 一致；医保 payment 分摊正确；纯自费 payment 医保=0、自费=实收 |
| **分组与合计** | 就诊块 + 块内小计 + 全局 summary（全量，非仅当前页）；`bill_date` 倒序 |
| **分页** | 10 块/页；翻页后 summary 不变 |
| **导出** | 列齐全；与查询同口径 |
| **异常** | 未知 service_table、缺 category → 整单失败 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| **ChargeItem** | `docs/领域/DomainModel/DM-ADM-011-ChargeItem-收费明细.md |
| **Payment** | `docs/领域/DomainModel/DM-FIN-002-PaymentReconciliation-支付流水.md |
| **上游 AS** | `health/AppService/AS-OP-FIN-001-门诊挂号.md`、`AS-OP-FIN-002-门诊收费.md`、`AS-OP-FIN-003-门诊退费.md` |
| **参照** | `health/AppService/AS-IP-NUR-006-住院护士站费用明细.md`（分组与退单展示） |
| **SDD** | `health/AppService-SDD/AS-OP-FIN-005-门诊费用明细-SDD.md`（**v1.0**） |

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v1.11 | 2026-10-06 | **BR-OP-FDT-018**：优惠/实收列显隐只认是否已配置 ACTIVE 折扣内容 |
| v1.10 | 2026-10-06 | **BR-OP-FDT-018** / §1.3.1：优惠=`|total_price|−|折后应收|`（优先 `system_discount_price` 差额）；实收/折后=`discount_amount`；已配置有效折扣时报表展示优惠/实收列 |
| v1.9 | 2026-08-24 | **BR-OP-FDT-002**：白名单增 `REFUNDING`（退费中仍计已收，与日结 static 对账；避免 SUCCESS 快照挂退费中行被静默丢弃） |
| v1.8 | 2026-08-17 | **DOC-CONFLICT→已收敛**：时间轴/收退恢复 Payment `bill_date`（与日结同轴）；ChargeItem `SETTLED`∪`REFUNDED`；退费冲减；因写路径退费覆盖 `entered_date`/行态，禁止仅靠 ChargeItem 日窗与日结对账 |
| v1.7 | 2026-08-12 | 药耗进价总价按操作单位分支（包装×数量 / 拆零满包+零头）；废止「进价单价×数量」字面；诊疗无进价（对齐 DD v1.2） |
| v1.6 | 2026-08-12 | **BR-OP-FDT-017**：细分类 fin_type 硬筛不要求投影含 `unit_price`；仅 `definition_id`（+ 挂号识别）缺失才整次失败 |
| v1.5 | 2026-08-06 | 诊疗/挂号进价统计改账单 `unit_price`（不回放、不读 PURCHASE/SALE）；药耗进价仅同批号回放 |
| v1.4 | 2026-08-03 | 药耗进价纠偏：批号 `LOT_NUMBER_COST`（发放/`definition.lot_no`）；有批号禁止目录 `PURCHASE`；缺 COST 同批号 `LOT_NUMBER_PRICE` 回落 |
| v1.3 | 2026-06-27 | **BR-OP-FDT-017**：细分类 fin_type 与日结同口径，仅锚 `definition_id`，禁止回落当前目录 |
| v1.2 | 2026-06-24 | 应用层已落地；前端联调须 `publicRequestClient` 解包标准响应 `data` |
| v1.1 | 2026-06-21 | 关闭 **GAP-OP-FDT-001～002**；对齐 `DM-ADM-011` §6.2.25～6.2.26、`DM-FIN-002` §6.2.17～6.2.18 与 `OutpatientFeeDetailQuerysupport |
| v1.0 | 2026-06-21 | 首版：门诊费用明细查询页 IF-OP-FDT-01～03；按就诊分组；收退 Tab；`bill_date` 筛选；财务金额列（原价/优惠/应收/折后/实收/医保/自费）；Gap GAP-OP-FDT-001～002 |
