# 库房期初期末明细 · 应用服务设计（AS）

> **文档职责**：说明「库房期初期末明细」场景的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：进销存 **报表式只读页** — 用户**必选单选库房**（`locationId`）、**必选期初备份单号**、可选期末备份单号；**药品 / 耗材分 Tab 各查各**（同一库位不混放药耗）；按品种展示**期初 / 期末 / 期间业务 / 推算期末 / 账平 / 盈亏差**；支持 **数量 + 金额（进价、售价各一套）**、**动态业务列**（随库位形态）、**关键字**、**isBus / isBalance** 筛选、**Excel 导出**。**只读对账**，不锁库、不写账。
>
> **数量与金额锚点（硬约束）**：
> - **账面与汇总计算数量**：一律以 **拆零最小单位** 累加（与 **InventoryItem.quantity**、**SupplyRequest.item_quantity**、发放 **quantity** 一致）。
> - **列表回显数量**：与 **AS-INV-WHS-004** 库存备份一致 — 在最小单位汇总结果上，按 **`package_unit_ratio`** 装配 **包装库存展示串 / 拆零库存展示串**（`packageStockText` / `splitStockText` 语义）；**禁止**仅回显裸最小单位数而不做包装换算展示。
> - **单价口径**：**批号进价 / 批号售价** 均为 **每包装单位** 价（**`LOT_NUMBER_COST` / `LOT_NUMBER_PRICE`** 之 `amount`）；价与 **批号** 绑定。
> - **金额计算（硬门禁）**：**禁止**以「最小单位数量 × 包装单价」作为主导路径（四舍五入会导致金额错误）。**必须**采用与 **BR-INV-DTL-007** 同构的 **满包 × 包装价 + 拆零余量 × 拆零单价**（进价链、售价链**各自完整计算**）；不够一整包的余量单独按拆零单价处理。
> - **品种级汇总金额**：快照期初/期末品种 SUM 时，**优先**用批号行 **`lot_number_cost_amount` / `lot_number_price_amount`（包装单位价）** + 行数量 + **`package_unit_ratio`** 套 **BR-INV-DTL-007** 逐行算额再汇总；**禁止**以「品种总片数 ÷ 品种总进价额」反推单价。批号包装价缺失时**回落**快照冻结 **`line_cost_total` / `line_price_total`**。期间业务行有冻结 **`total_price`** 用之；盘点/损益用 **`profit_loss_price`**；发药无冻结进价时按 **BR-INV-SET-020** 时点价还原后再套 **BR-INV-DTL-007**。
> - **展示列**：期初/期末/各期间业务须**分列**「**包装进价/包装售价**」（每包装单位价）与「**进价额/售价额**」（行总额）；多批号混价时包装单价列置空。
>
> **读侧架构（硬约束）**：库房期初期末明细**不是**新领域聚合；**禁止**新建 `domain.share.queries.inventorysettlement` 等跨域读模型包。读能力**归属各现有聚合**之 `*QueryService`；**能复用则应用层拼装**，仅当单聚合 DM §6.2 **确实缺方法**时在**该聚合 DM** 增补读契约（见 **§3**），**禁止**为报表单独加包。
>
> **与库存备份分工**：期初/期末锚点、备份单号列表依赖 **AS-INV-WHS-004** IF-10/11 与 **DM-DOC-001**；本场景**消费**快照，**不**触发备份。
>
> **覆盖声明**：§2 所列 IF 覆盖「库房期初期末明细」对外能力全集（初始化、分页查询、导出）；备份单号下拉**复用**库存明细 IF-11 语义。
>
> **编码**：AS-INV-RPT-001 · **版本**：v2.0 · **日期**：2026-07-11

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | **初始化**（管辖库房下拉、期初/期末备份单号列表）；**日结分页查询**（品种级数量+金额、动态列、账平判定、筛选）；**Excel 导出**（与列表动态列同构） |
| **在外** | 任何 **SupplyRequest** 写路径、库存增减、备份触发；**货位**；批号级日结；调价制单/审批（只读追溯 **DM-CUS-011**）；壳单/申请单本身作为动账统计项；**跨域日结读模型包 / 新聚合** |
| **涉及域** | 读：**Location**、**MedicationDefinition** / **DeviceDefinition**、**DocInventoryItemSnapshot**、**InventoryItem**（期末实时）、**SupplyRequest**（已同意执行单）、**MedicationDispense** / **DeviceDispense**、**PriceAdjustment**（时点价追溯）、**ChargeItemDefinition**（现价/发药售价锚）；租户上下文 |
| **跨域** | 备份读 **禁止**重算定价；发药金额可关联 **ChargeItem**；历史进价可只读 **调价明细**（**AS-FIN-PRC-002** 口径）；**编排仅在应用层**，读缺口补在**各聚合 DM §6.2** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **库管 / 药库·药房·科室药房人员** | 选库房 → 选期初备份单号 →（可选期末备份）→ 切换药/耗 Tab → 关键字 / isBus / isBalance → 查询 → 核对账平 → 导出 |
| **审计 / 财务** | 只读查询与导出；关注数量与金额双账平及盈亏差 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **日结** | 对账报表：「期初 + 期间业务」推算期末，与**实际期末**比对 |
| **期初** | 必选 **`startBackupBusNo`**；对应备份单号自然日 **D₀** 的 **零点备份**快照（建议每日 **00:05** 执行，**BR-INV-DTL-028**）；数量为 **(D₀−1) 自然日日终**库存（**`ABU`+`yyyyMMdd`+序号**） |
| **期末** | 可选 **`endBackupBusNo`**；**未传** = 查询时刻 **实时库存**（**InventoryItem** 汇总）；选备份时数量为 **(D₁−1) 自然日日终** |
| **期间业务日** | 期初备份日 **D₀** → 业务统计 **D₀ 00:00:00 至期末上界**（**含 D₀ 全天**）。例：期初选 **3/2** 备份（=3/1 日终库存）、期末不选且 **3/5** 白天查 → 业务日 **3/2～3/5（至查询时刻）**。期末选 **3/5** 备份时上界为 **3/4 23:59:59**（该备份 =3/4 日终库存） |
| **时间边界** | 落库筛选为审批/发放时刻 **双闭** `LocalDateTime` 区间；由 **`PeriodBalanceDetailWindowResolver`** 自备份自然日换算 |
| **汇总粒度** | **品种级**（`item_id`）；多批号数量/金额在品种内汇总 |
| **分页主表** | **全量未删**药品定义 **或** 全量未删耗材定义（与当前 Tab 一致）；**禁止**以期初备份品种集做主表；**禁止**按启用/停用（**`PublicationStatus`**）过滤目录；**已软删定义**排除 |
| **展示过滤 `isBus`** | **默认**（是或未传）：仅展示期间**至少一项业务非零**的品种；**`isBus=否`**：展示当前页目录品种**含全 0** |
| **展示过滤 `isBalance`** | 可选：仅账平 / 仅账不平（**盈亏数**与**盈亏差**均为 0 才算账平；售价侧本版不展示、不参与判定） |
| **动账判定** | 先定 **选中库房** → 读单据 **源/目的库位** → 判定对该库为 **增量（+）** 或 **减量（−）**；**仅统计 agree 已落库存** 的执行单 |
| **壳单排除** | **`PHARMACY_STOCK_IN`**、**`DEPT_STOCK_IN`**、**`CABINET_RETURN_STOCK_IN`**、各形态 **`*_TRANSFER_IN`** 等只读壳单**不计**（库存已在源执行单 agree 时变动） |
| **申请单排除** | 请领、订货等 **agree 不动库存** 的类型**不计** |
| **发药统计** | 统一 **MedicationDispense / DeviceDispense**（含住院汇总链；**不**单独列汇总单类型） |
| **盘点 vs 损益** | **分列**；数量、金额均拆 **盈 / 亏** 展示（**方案乙**）；域内 **`item_quantity` / `profit_loss_price` 保留符号** 参与推算 |
| **动态列** | 随 **`Location.formEnum`** 显示适用业务列；不适用列**不展示**（非填 0 占位列） |
| **关键字** | 品种名称、院内编码、拼音、五笔（与旧系统一致） |

#### 1.3.1 库位形态 → 适用业务列（摘要）

| 库位形态 | 增量（+）典型来源 | 减量（−）典型来源 | 备注 |
| --- | --- | --- | --- |
| **药库 `WAREHOUSE`** | 进货 agree；药房/科室**退库** agree（目的=本库）；**调入**（调出 agree 目的=本库） | 出库 agree；退供应商 agree；**调出** agree（源=本库） | 无发药/领用列 |
| **药房 `PHARMACY`** | 药库**出库** agree（目的=本药房）；**调入** agree 落账侧 | **退库** agree；**调出**；发药 completed | 无进货/退供应商列 |
| **科室药房 `DEPARTMENT_PHARMACY`** | 同药房 + **退领** agree | 同药房 + **领用** agree | 领用/退领**仅科室** |

（完整 **`SupplyType` 白名单与源/目的判定**见 **BR-INV-SET-010** 与 **§1.6**。）

#### 1.3.2 数量与金额分工（硬约束）

| 环节 | 数量 | 金额 |
| --- | --- | --- |
| **期初 / 期末（快照）** | 批号行 `quantity`（最小单位）→ 品种汇总 | 批号行 **包装价** + 数量 + 换算比套 **BR-INV-DTL-007** 逐行算额 → 品种汇总；缺价回落 **`line_cost_total` / `line_price_total`** |
| **期初 / 期末（实时）** | **InventoryItem** 最小单位汇总 | 现价按批号走 **BR-INV-DTL-007** 后汇总 |
| **Supply 期间业务** | 行 **`item_quantity`**（最小单位）累加 | 行 **`total_price`**（批号进价口径冻结）；盘点/损益用 **`profit_loss_price`** 及进/售价侧拆分规则 |
| **发药期间业务** | 发放 **`quantity`** | **售价**：优先账单冻结；**进价**：**BR-INV-SET-020** 还原后 **BR-INV-DTL-007** |
| **发药期间数量** | 汇总取 **`dispense_quantity` 为正数时用之**，否则回落 **`quantity`**（**BR-INV-SET-028**；与发放聚合 `completeSummaryDispenseFacts` 一致） | 禁止 `dispense_quantity=0` 时忽略 `quantity` 导致发药列全 0、账平数量少减 |
| **列表回显** | 主表数量列统一 **拆零最小单位**（对齐库存明细「库存(最小)」）；包装展示串仅作辅字段 | **包装进价单价**与 **进价总额**分列；推算期末、盈亏数/盈亏差、是否账平 |
| **账平公式** | 最小单位推算 | 进价总额同构推算；**盈亏数=0 且 盈亏差=0** 为账平 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-INV-SET-01～03 |
| **非目标** | 批号级日结；下钻单据明细 API；打印模板；手工触发备份；修改库存/单据；全库合并报表（必须单库位）；药耗同屏 UNION；**跨域 inventorysettlement 读包** |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Location（ADM）** | 库房形态、管辖 Gate | `DM-ADM-003-Location-位置.md` |
| **MedicationDefinition / DeviceDefinition** | 分页主表、展示 enrich | 主数据 DM |
| **DocInventoryItemSnapshot（DOC）** | 期初/期末（备份）数量与冻结金额 | `DM-DOC-001-InventoryItemSnapshot-库存明细快照.md` |
| **InventoryItem（WKF）** | 期末实时数量 | `DM-WKF-008-InventoryItem-库存项.md` |
| **SupplyRequest（WKF）** | 期间动账单据与冻结行价 | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **MedicationDispense / DeviceDispense** | 发药/退药数量与售价锚 | `DM-MED-002` / `DM-WKF-007` |
| **PriceAdjustment（CUS）** | 历史时点批号价追溯 | `DM-CUS-011-PriceAdjustment-价格调整.md` |
| **ChargeItemDefinition（ADM）** | 现价、发药售价回落 | `DM-ADM-010-ChargeItemDefinition-收费项定价.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述（语义锚点） | 绑定 IF |
| --- | --- | --- |
| **BR-INV-SET-001** | **`locationId` 必填**；须为用户管辖库位；解析 **`formEnum`** 决定动态列 | 01～03 |
| **BR-INV-SET-002** | **`startBackupBusNo` 必填**；须存在且含该库位快照行 | 02～03 |
| **BR-INV-SET-003** | **`endBackupBusNo` 可选**；未传则期末取实时库存 | 02～03 |
| **BR-INV-SET-004** | 库存 **零点备份**（**BR-INV-DTL-028**）：单号日 **D** 的 ABU = **(D−1) 日终**库存；期间业务窗 **含 D₀ 全天**：下界 **D₀ 00:00:00**；上界为实时期末 **查询时刻**，或期末备份日 **D₁** 时 **(D₁−1) 日 23:59:59** | 02～03 |
| **BR-INV-SET-005** | 分页主表 = **全量未删定义**（当前 Tab）；**禁止**备份集/启用状态收窄主表 | 02～03 |
| **BR-INV-SET-006** | 填数后对当前页品种装配期初/期末/业务；**默认 `isBus`** 滤掉全 0 业务行 | 02～03 |
| **BR-INV-SET-007** | **壳单 / 申请单** 不计入期间业务（**§1.3** 清单） | 02～03 |
| **BR-INV-SET-008** | 动账方向：**`source_location_id`=选中库 → 减**；**`purpose_location_id`=选中库 → 增**（单端点单据仅目的侧） | 02～03 |
| **BR-INV-SET-009** | 盘点类：`**item_quantity` 有符号**；展示拆 **盘盈/盘亏** 数量列；金额用 **`profit_loss_price`** 拆进价侧盈/亏列（售价侧无则空） | 02～03 |
| **BR-INV-SET-010** | 损益类：同 **BR-INV-SET-009**；与盘点**分列** | 02～03 |
| **BR-INV-SET-011** | **数量账平公式**：`期初 − 发药 + 退药 + Σ增 − Σ减 + 盘点调整 + 损益调整 = 推算期末`（调整量**带符号**） | 02～03 |
| **BR-INV-SET-012** | **金额账平公式**：与 **BR-INV-SET-011** 同构；各列用**冻结/还原金额**；另加 **本库调价进价盈亏**（`priceAdjustmentCostAmount`，不改变数量） | 02～03 |
| **BR-INV-SET-013** | **账平**：推算期末数量 **且** 推算期末进价总额 均等于实际期末 → `isBalance=是`（售价侧本版不展示、不参与判定） | 02～03 |
| **BR-INV-SET-014** | **盈亏数 / 盈亏差**：`实际期末 − 推算期末`（数量、进价总额各一列）；为 0 则对应维度账平 | 02～03 |
| **BR-INV-SET-015** | 期末为**实时**时，期末金额用**当前批号定价**（**BR-INV-DTL-007**） | 02～03 |
| **BR-INV-SET-016** | 期末为**备份**时，期末金额读快照冻结列，**禁止**重算 | 02～03 |
| **BR-INV-SET-017** | Supply 行金额：以行 **`total_price`**（批号进价口径冻结）为准；进/售总价分列装配规则 **SDD** 定稿 | 02～03 |
| **BR-INV-SET-018** | **禁止**「最小单位总量 × 包装单价」算金额；**必须 BR-INV-DTL-007** | 02～03 |
| **BR-INV-SET-019** | 列表数量展示：**包装/拆零展示串** 与库存备份同构 | 02～03 |
| **BR-INV-SET-020** | **发药历史进价**：无行冻结进价时，按 **`dispense_time`** 与 **`PriceAdjustment.approve_time`** 比对还原该 **批号** 当时进/售价，再算行额；仍缺则回落 **不晚于发药日的最近备份快照** 该批号四价 | 02～03 |
| **BR-INV-SET-021** | 导出列与当前库位形态动态列、当前 Tab **一致** | 03 |
| **BR-INV-SET-022** | 快照品种金额 SUM：**优先**批号 **`lot_number_cost_amount` / `lot_number_price_amount`** + **BR-INV-DTL-007**；**禁止**「品种总量 ÷ 品种总额」反推单价 | 02～03 |
| **BR-INV-SET-023** | 列表与导出须分列 **包装进价/包装售价** 与 **进价额/售价额**；同品种多批号混价时包装单价展示置空 | 02～03 |
| ~~**BR-INV-SET-024**~~ | **已废止**（并入 **BR-INV-SET-004** 零点备份口径） | — |
| **BR-INV-SET-025** | **盈亏差**（`quantityDiff` / `costAmountDiff`）与 **账平** 判定须对金额统一 **`scaleMoney`** 后再比较；禁止出现差值为 0 显示但 `isBalance=否` | 02～03 |
| **BR-INV-SET-026** | 列表/导出/展开区**本版仅展示进价侧**；售价及售价差额隐藏；主表须含推算期末、盈亏数、盈亏差、是否账平；**有本库调价盈亏时主表展示「调价」列**；页面展示账平核对公式 | 02～03 |
| **BR-INV-SET-027** | **账平推算**时 `returnToWarehouse` 列符号随 **`Location.formEnum`**：**药库**为增（退库入库）；**药房 / 科室药房**为减（退库出库）。列内仍存**正数绝对量**；符号在 **`computeBalanceFields`** 落账（与 **BR-INV-SET-008**、§1.6.1～1.6.3 方向表一致） | 02～03 |
| **BR-INV-SET-028** | **药品发药期间数量**：优先取 **正数** `dispense_quantity`；为空或 ≤0 时回落 `quantity`（对齐发放域 `completeSummaryDispenseFacts`） | 02～03 |
| **BR-INV-SET-029** | **药品发退药期间批量读**：各通道须 **`pageMedicationDispenseRecords` 分页循环**至全量（步长 **`EXPORT_PAGE_SIZE`**，总上限 **`EXPORT_MAX_ROWS`**）；**禁止**单次 `listMedicationDispenseRecords`（**5000** 条截断） | 02～03 |
| **BR-INV-SET-030** | **药品发退药分通道读**：正向发药 **`recordTypeCode=DISPENSE`**，门诊/住院各一次（`outpatientMedicationOnly` / `inpatientMedicationOnly`）；退药 **`recordTypeCode=REFUND` 单通道一次**（`toMedicationDispenseRefundRecordQuery`）。**禁止**双路默认 **`RECORD_TYPE_ALL`** 合并导致同一 **`dispense_id` 退药行重复累加**（与住院是否正向发药无关）；三路合并后仍按 **`dispense_id` 去重**（消化 **`TEMPORARY`** 正向双路重复） | 02～03 |
| **BR-INV-SET-031** | **调价进价盈亏**：期间已审批调价、子表 `location_id`=当前库的 `SUM(purchase_impact)` → `priceAdjustmentCostAmount`；**loc 空不计**（历史单/诊疗/未解析库位）；数量侧不加调价；推算进价 `+=` 该额 | 02～03 |

> **数量账平排查（只读）**：期初/期末快照可信而 **盈亏数 ≠ 0** 时，确认备份 Cron 为 **00:05**（**BR-INV-DTL-028**）且期间窗含期初备份日全天（**BR-INV-SET-004**）；展开区「隐藏全零行」选 **否**，按 **BR-INV-SET-011** 逐项核对；大流量药房确认 **BR-INV-SET-029** 分页全量已生效。**退药列约为发药 2 倍**时，优先核对 **BR-INV-SET-030**（非脏数据重复退药单）；仍异常再查库 `med_medication_dispense` 同品种 **`REFUND+REFUNDED`** 行数。

#### 1.6.1 药库 `WAREHOUSE` — 动账 `SupplyType` 白名单（agree 已落账）

| 报表列（语义） | `type_enum` | 对药库方向 |
| --- | --- | --- |
| 进货 | `CABINET_INBOUND_RECEIPT` | +（目的=药库） |
| 退供应商 | `CABINET_RETURN_TO_SUPPLIER` | −（目的=药库） |
| 出库 | `CABINET_STOCK_OUT` | −（源=药库） |
| 退库入库 | `PHARMACY_RETURN_TO_WAREHOUSE` / `DEPT_RETURN` | +（目的=药库） |
| 调出 | `CABINET_TRANSFER_OUT` | −（源=药库） |
| 调入 | （仅 `CABINET_TRANSFER_OUT` 目的=药库） | + |
| 盘点 | `CABINET_STOCKTAKE` | 调整（有符号） |
| 损益 | `CABINET_GAIN_LOSS` | 调整（有符号） |

#### 1.6.2 药房 `PHARMACY` — 补充白名单

| 报表列 | `type_enum` | 方向 |
| --- | --- | --- |
| 入库（请领实收） | `CABINET_STOCK_OUT` | +（目的=本药房） |
| 退库 | `PHARMACY_RETURN_TO_WAREHOUSE` | −（源=本药房） |
| 调出/调入 | `PHARMACY_TRANSFER_OUT` / 调出目的侧 | − / + |
| 盘点/损益 | `PHARMACY_STOCKTAKE` / `PHARMACY_GAIN_LOSS` | 调整 |
| 发药/退药 | **Dispense** completed / refunded | − / + |

#### 1.6.3 科室药房 `DEPARTMENT_PHARMACY` — 在药房基础上增加

| 报表列 | `type_enum` | 方向 |
| --- | --- | --- |
| 领用 | `DEPT_ISSUE` | − |
| 退领 | `DEPT_ISSUE_RETURN` | + |
| 入库/退库/调拨/盘点/损益 | `DEPT_STOCK_IN` **不计**；`DEPT_RETURN`、`DEPT_TRANSFER_OUT` 等 | 同药房对称规则 |

> **动态列裁剪**：`PeriodBalanceDetailAssembler#nullifyInactiveColumns` 对科室药房与药房一致隐藏药库专属列（进货/出库等），**保留**领用/退领列。
>
> **账平退库符号**：`returnToWarehouseQuantity` 等列在 `accumulateSupplyLines` 中一律累加**绝对量**；**药库 / 药房 / 科室**对推算期末的 ± 由 **`computeBalanceFields(dto, locationFormEnum)`** 按 **BR-INV-SET-027** 区分（药库 +，药房/科室 −）。

### 1.7 读侧复用与拼装（应用编排 · 无新聚合）

> **原则**：下列能力优先用**现有** QueryService；品种级 SUM、动态列、账平公式、包装展示串均在**应用层**完成。

| 读需求 | 现有领域读能力（DM §6.2） | 应用层拼装 |
| --- | --- | --- |
| 备份单号列表 | **DOC** `findDistinctBusNoByTenant`（IF-11） | 直接返回 |
| 期初/期末（备份）批号行 | **DOC** `listSnapshotRowsForSum`（`busNo` + `warehouseId` + `itemTable`） | 按 `item_id` **SUM** `quantity`；金额逐行 **BR-INV-DTL-007**（**BR-INV-SET-022**）后汇总 |
| 期初备份存在性 | 同上，有行即可 | Gate **BR-INV-SET-002** |
| 期末（实时）数量 | **WKF-008** `getBatchInventoryByWarehouse` | 按 `item_id` **SUM** `quantity` |
| 期末（实时）金额 | **WKF-008** 批号行 + **ADM-010** 定价读（**BR-INV-DTL-007**） | 应用层按批号算行额再 SUM |
| 目录分页主表 | **药品** `pageMedicationCatalog` / **耗材** `pageDeviceDefinition`（仅 `is_deleted=false`） | 关键字、翻页 |
| 药品正向发药期间行 | **MED-002** `pageMedicationDispenseRecords` **分页循环**（`RECORD_TYPE_DISPENSE`；**BR-INV-SET-029/030**） | 门诊、住院**各拉一次**正向行；有效拆零量 **`resolveMedicationDispenseSplitQuantity`**（**BR-INV-SET-028**） |
| 药品退药期间行 | **MED-002** 同上（`RECORD_TYPE_REFUND`；**BR-INV-SET-030**） | **单通道**拉取全场景已退行；与正向分查后按 `medication_id` 分组；**禁止**在门诊/住院双路 ALL 中重复统计退药 |
| 耗材发退药期间行 | **WKF-007** `listDeviceDispensesByLocationAndDispenseWindow` | 批量拉取后按 `device_def_id` 分组 |
| 供应动账期间行 | **WKF-006** `listAgreedLinesByWarehouseAndApprovalWindow` | 批量拉取后按 `item_id` 分组与 ± 方向 |
| 发药历史进价 | **CUS-011** `findApprovedHistoryByItem`（**当前页** `item_id` 集合） | 按 `dispense_time` 与 `approve_time` 比对批号价；回落 **DOC** 快照批号四价 |
| 本库调价进价盈亏 | **CUS-011** `sumApprovedPurchaseImpactByLocation`（期间窗 + 当前 `locationId`） | 仅 `detail.location_id` 匹配行；loc 空不计；写入 `priceAdjustmentCostAmount` 并入 **BR-INV-SET-012/031** |
| 调价追溯批量优化 | 同上（页内品种数有限） | v1 可接受；**不作为 Gap** |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写属性 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **IF-INV-SET-01** | 进入页面 | 返回管辖**库房下拉**（项携带 **formEnum** 供前端动态列，经 **`OptionDto.busNo`** 下发）；**期初/期末备份单号列表**（降序，可复用库存明细 IF-11 语义） | 读 | BR-INV-SET-001 | — | **Location**、**DocInventoryItemSnapshot** | **LocationQueryService**、**DocInventoryItemSnapshotQueryService**、管辖库位解析 |
| **IF-INV-SET-02** | 查询、翻页、筛选 | **日结分页**：全量定义主表 + 填数 + 账平 + 动态列 | 读 | BR-INV-SET-001～021 | — | §1.5 全部读侧 | 多 QueryService 编排 |
| **IF-INV-SET-03** | 导出 Excel | 同 IF-02 条件导出 | 读 | BR-INV-SET-021 等 | — | 同 IF-02 | 同 IF-02 |

---

## 3. 缺口跟踪（Gap）

> **仅领域层三类缺口**；读能力缺什么补在**对应聚合 DM §6.2**，**禁止**新建跨域读包。

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **GAP-INV-SET-001** | **QueryService 能力** | **DM-WKF-006** · **§6.2.12** `listAgreedLinesByWarehouseAndApprovalWindow` | 无按 **库房 + 审批时间窗 + type 白名单 + AGREE** 批量拉取供应明细；现有 `getSupplyByBusNo*` 无法支撑日结期间业务 | DM §6.2.12 + SDD §5.2 已实现 | IF-02、03 | 高 | **已关闭** |
| **GAP-INV-SET-002** | **QueryService 能力** | **DM-WKF-007** · **§6.2.19** `listDeviceDispensesByLocationAndDispenseWindow` | 现有分页/就诊维度 Query **无** `location_id` + `dispense_time` 窗批量列表，无法替代日结耗材发退药统计 | DM §6.2.19 + SDD §5.2 已实现 | IF-02、03（耗材 Tab） | 高 | **已关闭** |

**以下不算 Gap（应用层拼装即可）**：

- **DOC** 按品种汇总：用 `listSnapshotRowsForSum` + 应用 SUM。
- **WKF-008** 实时期末数量：`getBatchInventoryByWarehouse` + 应用 SUM。
- **MED-002** 药品发药：正向 **`RECORD_TYPE_DISPENSE`** 门诊/住院各查一次；退药 **`RECORD_TYPE_REFUND`** 单查一次；合并后按 **`dispense_id` 去重**（**BR-INV-SET-030**）。
- **CUS-011** 调价：当前页 `findApprovedHistoryByItem` 循环。
- **主数据** 目录：`pageMedicationCatalog` / `pageDeviceDefinition`。

---

## 4. 接口编排说明书（业务步骤）

### IF-INV-SET-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入库房期初期末明细页 |
| **主路径步骤** | ① 管辖范围内**药库 + 药房 + 科室药房**下拉（**formEnum** 写入 **`OptionDto.busNo`**）② 备份单号列表（租户内 **`bus_no` 去重降序**）③ 返回 |
| **关键分支** | 无备份历史 → 单号列表可为空（查询期初时 **BR-INV-SET-002** 再报错） |
| **失败分支** | EX-INV-SET-201 |
| **查询侧说明** | 备份列表与 **AS-INV-WHS-004 IF-11** 语义对齐 |

### IF-INV-SET-02 日结分页查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **`locationId`、`startBackupBusNo`、`itemTable`（药/耗 Tab）** 已选 |
| **主路径步骤** | ① **BR-INV-SET-001/002**：校验库房与期初备份 ② 解析零点备份日窗 **D₀ 00:00 至期末上界**（**BR-INV-SET-004**）③ 按 Tab **分页查询全量未删定义**（**BR-INV-SET-005**）④ 批量加载：期初快照品种量/额；期末（备份或实时）；期间 Supply（**BR-INV-SET-007/008/010** 白名单）；期间 Dispense（正向 **DISPENSE** 门诊/住院双路 + 退药 **REFUND** 单路，**BR-INV-SET-030**）⑤ 按品种汇总**最小单位数量**与**冻结/还原金额**（**BR-INV-SET-017/018/020**）⑥ 拆分盘点/损益盈/亏列（**BR-INV-SET-009**）⑦ **BR-INV-SET-011/012** 推算期末与 **BR-INV-SET-013/014** 账平/盈亏差 ⑧ 数量回显 **BR-INV-SET-019** ⑨ **BR-INV-SET-006** 应用 isBus / isBalance / 关键字 ⑩ 按库位形态裁剪动态列 ⑪ 返回 |
| **关键分支** | 期初备份无该库位行 → **EX-INV-SET-204**；期末实时空库 → 期末为 0 仍计算 |
| **失败分支** | EX-INV-SET-202～204 |
| **查询侧说明** | 禁止 N+1；**§1.7** 编排；期间 Supply/Dispense 批量读已具备 |

### IF-INV-SET-03 导出 Excel

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 同 IF-02 筛选已确定 |
| **主路径步骤** | 复用 IF-02 填数链路；按 **BR-INV-SET-021** 输出动态列；工作表名 **「库房期初期末明细」** |
| **关键分支** | 大数据量 **SDD** 约定上限与 `truncated` 语义 |
| **失败分支** | EX-INV-SET-205 |
| **查询侧说明** | 与列表同条件 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示语义 | 可重试 |
| --- | --- | --- | --- |
| **EX-INV-SET-201** | 初始化失败 | 页面加载失败 | 是 |
| **EX-INV-SET-202** | 未选库房 | 请选择库房 | 是 |
| **EX-INV-SET-203** | 未选期初备份 | 请选择期初备份单号 | 是 |
| **EX-INV-SET-204** | 期初备份无数据 | 该库房没有备份库存数据 | 是 |
| **EX-INV-SET-205** | 导出失败 | 导出失败，请稍后重试 | 视原因 |
| **EX-INV-SET-206** | 库位无权限 | 无权查看该库房 | 否 |

---

## 6. 事件语义与消费方

| 事件（业务名） | 触发点（IF） | 消费方 / 意图 | 幂等语义 |
| --- | --- | --- | --- |
| **（只读无落库事件）** | IF-INV-SET-01～03 | — | 查询幂等 |

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 全量目录分页 + 批量拉取期间单据；禁止对全库品种逐条循环远程调用 |
| **一致性** | 只读；期初/期末快照**不可变**；实时期末与查询时刻一致 |
| **安全** | 租户隔离 + 管辖库房 Gate |
| **精度** | 数量 **numeric(18,2)**；金额 **numeric(18,4)**；舍入策略与 **BR-INV-DTL-007** / 收费域常量对齐（**SDD** 引用） |
| **架构** | **禁止**跨域日结读包；读缺口只补各聚合 DM §6.2 |
| **导出** | 行数上限 **SDD** 约定（建议与库存明细导出量级同档） |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **时间窗** | 期初 3/1 日终 → 3/1 当日业务不计、3/2 起计；左开右闭 |
| **分页主表** | 品种仅期末有库存、期初无 → 仍能出现在全量目录页；**isBus 默认**隐藏、**isBus=否** 可见 |
| **壳单** | 药房入库壳单不计；退库动账只计退库执行单 |
| **药库** | 出库/进货/退供列；无发药列 |
| **药房** | 发药/退药；无进货列 |
| **科室** | 领用/退领列 |
| **数量展示** | 汇总为最小单位；列表有包装/拆零展示串 |
| **金额** | 禁止直乘验算；满包+余量公式；调价后历史发药进价可追溯 |
| **账平** | 数量+进价额+售价额三维；盘点/损益盈/亏分列加总等于调整量 |
| **导出** | 列与屏显动态列一致 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 类型 | 路径 |
| --- | --- |
| **上游 AS** | `AS-INV-WHS-004-库存明细.md`（备份、**BR-INV-DTL-007**） |
| **DM** | `DM-DOC-001-InventoryItemSnapshot-库存明细快照.md` |
| **DM** | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **DM** | `DM-WKF-008-InventoryItem-库存项.md` |
| **DM** | `DM-ADM-003-Location-位置.md` |
| **DM** | `DM-CUS-011-PriceAdjustment-价格调整.md` |
| **DM** | `DM-ADM-010-ChargeItemDefinition-收费项定价.md` |
| **参考** | `AS-FIN-PRC-002-调价明细.md` |
| **功能清单** | `InventoryAppService/INV-inventory-功能清单与改造状态.md` |
| **SDD** | `AppService-SDD/AS-INV-RPT-001-库房期初期末明细-SDD.md`（**v1.1**，与 **`zxplat`** 应用层实现对齐） |
| **实现** | `zxplat/zxplat/health-application/.../inventory/periodbalancedetail/`（详见 SDD §2） |

### 9.2 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| **v2.1** | **2026-07-26** | 本库调价进价盈亏入账平（**BR-INV-SET-031**）；主表「调价」列；金额公式含调价（**BR-INV-SET-012/026**）；关联 SDD **v1.2**；依赖调价子表 `location_id` |
| **v2.0** | **2026-07-11** | 药品发退药分通道读（**BR-INV-SET-030**）：正向 **`DISPENSE`** 门诊/住院双路、退药 **`REFUND` 单路**；修复双路 **`ALL`** 合并致退药数量翻倍；关联 SDD **v1.1** |
| **v1.9** | **2026-07-11** | 期间窗改零点备份口径（**BR-INV-SET-004**）：含期初备份日全天；与 **BR-INV-DTL-028**（00:05 Cron）对齐；废止 **BR-INV-SET-024**；关联 SDD **v1.0** |
| **v1.8** | **2026-07-11** | 药品发退药期间读改分页全量拉取（**BR-INV-SET-029**）；修复单次 5000 条截断漏统计；关联 SDD **v0.9** |
| **v1.7** | **2026-07-11** | 账平推算：`returnToWarehouse` 按库位形态区分 ±（**BR-INV-SET-027**）；发药期间数量 `dispense_quantity≤0` 回落 `quantity`（**BR-INV-SET-028**）；关联 SDD **v0.8** |
| **v1.6** | **2026-07-09** | 本版仅展示进价侧；主表精简为期初/期末/推算/盈亏数/盈亏差/是否账平；账平改为数量+进价二维（**BR-INV-SET-026**）；页面展示核对公式；关联 SDD **v0.7** |
| **v1.5** | **2026-07-09** | 快照金额优先批号包装价+BR-INV-DTL-007（**BR-INV-SET-022**）；分列包装单价与总额（**BR-INV-SET-023**）；同日实时期末期间窗（**BR-INV-SET-024**）；盈亏差与账平金额刻度一致（**BR-INV-SET-025**）；关联 SDD **v0.6** |
| **v1.4** | **2026-07-02** | 科室药房日结行：`PeriodBalanceDetailAssembler#nullifyInactiveColumns` 与药房一致隐藏药库列、保留领用/退领列；关联 SDD **v0.5** |
| **v1.3** | **2026-07-02** | 文档与 **`zxplat`** 应用层实现对齐：IF-01 **formEnum→OptionDto.busNo**；关联 SDD **v0.4** |
| **v1.2** | **2026-07-02** | 关闭 GAP-INV-SET-001/002：WKF-006/007 QueryService 读方法落地 |
| **v1.1** | **2026-07-02** | 读侧：禁止跨域 inventorysettlement 包；§1.7 应用拼装；Gap 收敛为 WKF-006/007 两则 QueryService |
| **v1.0** | **2026-07-02** | 初稿：库房期初期末明细只读对账；单库位+药耗分 Tab；日终备份时间窗；全量目录分页；数量最小单位+包装展示；金额 BR-INV-DTL-007；动态列；双账平；导出 |
