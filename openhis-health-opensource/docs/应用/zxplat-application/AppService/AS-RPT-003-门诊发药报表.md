# 门诊发药报表 · 应用服务设计（AS）

> **文档职责**：报表中心「门诊发药报表」只读契约（**汇总 / 明细**）；承接并演进原 **AS-OP-PHA-004 门诊发退药记录**（同页改造，**不另建**药品页）。
>
> **页面边界**：筛选 → **汇总 / 明细** Tab（默认汇总）→ 查询 → 导出。事实 = **患者发退药** ∪ **门诊科室药品领用/退领**；汇总净用量/净金额（药房、品规、药品类型、科室等）。**纯读**。
>
> **DOC-CONFLICT→已按本 DD 收敛**：进售价 / 开立科室 / 患者 vs 领用拆分见 `health/设计决策/DD-报表中心-进售价口径与开立科室-2026-08.md`（**v1.2**：患者侧进价按操作单位分支——包装直接×数量、拆零满包+零头；来源禁止「全部」并集；领用仅进价、不算售价；患者侧售价=账单；旧「payment 扫库 / 实收列为本期必出」已收敛，本轮无实收列）。
>
> **编码**：AS-RPT-003 · **版本**：v1.16 · **日期**：2026-09-22  
> **族边界**：`health/设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md`  
> **隔离**：**禁止**用已废止的发药管理「住院患者药品使用」（计划发药时间 / 含待发）口径混入本报表。

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-RPT-OMD-01 初始化；IF-RPT-OMD-02 **汇总**（净）；IF-RPT-OMD-03 **明细分页**；IF-RPT-OMD-04 导出（当前 Tab） |
| **在外** | 门诊发药/退药写路径（**AS-OP-PHA-001/002**）；科室领用制单（**AS-INV-DEP-004/009**）；耗材报表（**AS-RPT-005**）；住院发药报表（**AS-RPT-004**） |
| **涉及域** | **MedicationDispense**；**MedicationRequest**；**MedicationDefinition**；**ChargeItem**；**SupplyRequest** / **SupplyDelivery**（药品领用/退领）；**Encounter** / **Patient**；**Location**；**Practitioner**；**Organization** |
| **编排权威** | 本文 §2、§4；患者侧明细继承 **AS-OP-PHA-004**；科室侧对齐 **AS-INV-DEP-004/009**；汇总净额见 §1.3 |
| **数据锚点** | ① 发放：`med_medication_dispense.dispense_time` + `ChargeItem`<br>② 领用：已同意药品行 `DEPT_ISSUE` / `DEPT_ISSUE_RETURN` 业务时间（SDD 定字段） |
| **权限** | 报表式只读；药房/科室药房下拉不按作业管辖收窄 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **药房 / 科室 / 财务 / 管理** | 设筛选 → 查汇总（药房/品规/类型/科室净量净额）→ 切明细看发退与领用/退领流水 → 导出当前 Tab |
| **系统** | 两路并集；标来源；汇总代数净；退药/退领为负 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **患者发药行** | `dispense_enum ∈ {OUTPATIENT,TEMPORARY}` 且 `COMPLETED` |
| **患者退药行** | `REFUND+REFUNDED`；关联 **AMB** |
| **科室领用行** | 已同意 **`DEPT_ISSUE`** 且行 **`item_table`=药品定义**；门诊相关科室药房（SDD 定） |
| **科室退领行** | 已同意 **`DEPT_ISSUE_RETURN`** 药品行；冲减领用净量 |
| **来源** | 明细必填：`PATIENT_DISPENSE` / `DEPT_ISSUE`（展示「患者发药」「科室领用」） |
| **一发一退 / 一领一退** | 各占明细一行；患者侧 **不** supersede 隐藏已退正向行 |
| **时间窗** | 两路各自业务时间闭区间并入同一筛选窗（SDD 对齐字段）；前端 `datetimerange`；默认当日 `00:00:00～23:59:59` |
| **项目类型筛** | `medication_category_code` 多选（对患者侧与领用药品行均生效） |
| **费用性质筛** | `fin_contract.bus_no`；就诊下**任一**有效账户命中即入集；仅收窄**患者发药** encounter scope |
| **明细** | 两路并集平铺；患者侧 **费用性质** = 发放收费行 `account_id→contract_no`（转自费后跟新账户）；**医疗类别**仍取主诊断 med_type；科室领用无 encounter 留空；金额列 **进价单价/进价总价、售价单价/售价总价**（无实收）；退侧负；**账批质检**：明细分页回填 `chargeLotNumber`/`lotMismatch`（挂账批号 vs 实发批号；**不改正额**；汇总不做） |
| **汇总维度** | ① 药房；② **品规**；③ 药品类型；④ **科室**；汇总列：**数量**（整数展示、品规维拼接包装单位）、**进价合计/售价合计**；**进价单价/售价单价、药品编码、最小数量（拼接最小单位）仅品规维**；**不展示行数** |
| **价格** | 患者侧：售价=ChargeItem 落库价；**退药写路径不生成自身账单（设计）**，读侧禁止 JOIN 退药 `dispense_id`，须补原正向发放售价再取负（**同数量总额绝对值与正向一致**；**汇总与明细同口径**，`PharmacyDispenseRecordChargePricingAssembler#supplementRefundPricingFromOriginMedicationRequests`）；进价=批号 `LOT_NUMBER_COST`（缺则同批号 `LOT_NUMBER_PRICE`；有批号禁止目录 PURCHASE；**行额按 `quantity_unit` 分支**：包装→包装价×数量，拆零→满包+零头，`ChargeItemPackageUnitRatioSupport`；BR-PRICE-SRC-001 / DD）。领用侧：进价=Supply 落库价；售价空（领用不算售价，见 DD §4）；退侧负 |
| **导出** | **仅当前 Tab** |
| **分页** | 明细默认 20；汇总无分页 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-RPT-OMD-01～04；现发退药记录页演进为发药报表（含领用） |
| **非目标** | 写路径；**耗材**领用行；用药明细；住院发放/领用；管辖库房收窄 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合 | 职责 | DM |
| --- | --- | --- |
| **MedicationDispense** | 患者发/退事实 | `DM-MED-002` |
| **MedicationRequest / Definition** | 处方、品规、类型 | `DM-MED-003` / `001` |
| **ChargeItem** | 患者侧金额 | `DM-ADM-011` |
| **SupplyRequest / SupplyDelivery** | 科室药品领用/退领 | `DM-WKF-006` |
| **Encounter / Patient** | 门诊号、姓名 | `DM-ADM-002` / `001` |
| **Location / Practitioner / Organization** | 药房、发药人、领用科室 | `DM-ADM-004` / `005` / `003` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-RPT-OMD-001** | 时间窗必填且 `dateFrom≤dateTo`；前端 `datetimerange`；Request 仍为 `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；仅日期时起补 `00:00:00`、止补 `23:59:59`；默认当日 `00:00:00～23:59:59` | 01～04 |
| **BR-RPT-OMD-002** | 患者侧：已发生发/退；场景 **AMB**；仅药品 | 02～04 |
| **BR-RPT-OMD-003** | 科室侧：仅药品行 `DEPT_ISSUE`/`DEPT_ISSUE_RETURN`；门诊相关科室药房；退领冲减 | 02～04 |
| **BR-RPT-OMD-004** | 明细 = 两路并集；须标来源；患者侧不 supersede | 03、04 |
| **BR-RPT-OMD-005** | 汇总 = **数量**、进价单价/售价单价（展示）、进价合计/售价合计；可含药房/品规/类型/科室维；**品规维另给药品编码、最小数量**（净数量 × 包装倍数）及 **包装/最小单位名**（前端拼到数量后）；数量整数展示；**不展示行数**；汇总进价一律 3 位（`sumThenPurchaseSettle`）；售价一律 2 位 | 02、04 |
| **BR-RPT-OMD-006** | 患者侧售价同 ChargeItem；退药不生成自身账单，须补原正向售价再取负（同数量与发药总额绝对值一致；汇总路径须与明细同补，禁止只冲进价不冲售价）；进价历史定价；退药负；**无实收列** | 02～04 |
| **BR-RPT-OMD-007** | 来源筛：全部 / 仅患者发药 / 仅科室领用 | 02～04 |
| **BR-RPT-OMD-008** | 导出仅当前 Tab | 04 |
| **BR-RPT-OMD-009** | 明细排序：业务时间倒序（两路统一比较字段见 SDD） | 03、04 |
| **BR-RPT-OMD-010** | **禁止**把耗材领用行计入本页 | 02～04 |
| **BR-RPT-OMD-011** | **账批质检**（仅 IF-03 明细分页）：患者侧比对收费锚点批号（`ChargeItem.definition` 的 `LOT_NUMBER_*`.`lot_no`）与实发批号（`MedicationDispense.lot_number`）；两边均有且不等 → `lotMismatch=true` 并回填 `chargeLotNumber`；**不改正额/不改汇总**；科室领用/无账单/任一侧无批号不打标；实现见 `MedicationDispenseReportPricingEnricher#applyChargeDispenseLotMismatch`（额外一次 definitionId IN，禁止再扫发放表） | 03 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-RPT-OMD-01 | 进入页 | 筛选项、默认时间窗、汇总维、来源选项 | 读 | BR-RPT-OMD-001 | 无 | Location / Organization / Practitioner / 术语 | 主数据 QueryService |
| IF-RPT-OMD-02 | 汇总 | 按维净汇总（含领用） | 读 | BR-RPT-OMD-001～007、010 | 无 | MedicationDispense / Supply / ChargeItem | 目标汇总 Query（Gap） |
| IF-RPT-OMD-03 | 明细 | 两路并集分页 | 读 | BR-RPT-OMD-001～004、006～010 | 无 | 同上 | 患者侧既有分页 + 领用目标分页（Gap） |
| IF-RPT-OMD-04 | 导出 | 当前 Tab | 读 | BR-RPT-OMD-008 | 无 | 同上 | 同当前 Tab |

> 点「查询」并行当前 Tab；来源筛可只查一路。

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-RPT-OMD-001 | QueryService 能力 | `DM-MED-002` §6.2.22 | 缺发药报表净汇总（含药房/品规/类型/科室，覆盖发放+领用） | DM 补齐 + 实现 | 02、04 | P0 | **已关闭** |
| GAP-RPT-OMD-002 | QueryService 能力 | `DM-WKF-006` §6.2.15 | 缺门诊相关**药品**领用/退领明细分页（供并集明细） | DM 补齐 + 实现 | 03、04 | P0 | **已关闭** |

### 3.1 Gap 关闭落点（2026-07-21）

| Gap ID | DM 章节 | Java 落点 |
| --- | --- | --- |
| GAP-RPT-OMD-001 | `DM-MED-002` §6.2.22 | `MedicationDispenseQueryService#summarizeMedicationDispenseReport`（患者侧）；科室维/领用净汇总见 Supply |
| GAP-RPT-OMD-002 | `DM-WKF-006` §6.2.15 | `SupplyRequestQueryService#pageDeptIssueReportLines` / `#summarizeDeptIssueReport`（`item_table=med_medication_definition`） |

> 两路并集分页/汇总合并属**应用层**，不算领域 Gap。

患者侧 `pageMedicationDispenseRecords` 已有，**不算** Gap。

---

## 4. 接口编排说明书（业务步骤）

### IF-RPT-OMD-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 进入门诊发药报表 |
| **主路径步骤** | 默认当日时间窗 → 启用药房/科室药房 → 药品类型 → **药品性质（麻醉/精一/精二）** → 发药人 → 记录/来源类型 → 汇总维度（含科室） |
| **关键分支** | 无库位仍可打开 |
| **失败分支** | EX-RPT-OMD-101 |
| **查询侧说明** | 只读主数据 |

### IF-RPT-OMD-02 汇总查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 汇总 Tab |
| **主路径步骤** | 校验 → 按来源筛合并两路净汇总 → 按维返回 → 页脚 |
| **关键分支** | 仅患者 / 仅领用 |
| **失败分支** | EX-RPT-OMD-102/103 |
| **查询侧说明** | 条件下推；禁止全表内存汇总 |

### IF-RPT-OMD-03 明细分页

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 明细 Tab |
| **主路径步骤** | 按来源查患者发放页和/或领用页 → 合并排序标来源 → 价装载 → 返回 |
| **关键分支** | 单路则跳过另一路 |
| **失败分支** | 同 02 |
| **查询侧说明** | 跨聚合批量；禁止 N+1 |

### IF-RPT-OMD-04 导出

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 导出 |
| **主路径步骤** | 当前 Tab 全量 → 映射并集 ExportRowDto → `AnnotatedExcelExporter` + 前端 `exportColumns` 裁剪列头 |
| **导出列** | 列真源在前端（R-EXCEL-4）；单 DTO 作 `@Excel` 白名单；禁止汇总/明细各造一套 Export DTO |
| **关键分支** | 空表 |
| **失败分支** | EX-RPT-OMD-104 |
| **查询侧说明** | 行上限；**禁止**汇总/明细共用并集列头 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-RPT-OMD-101 | 初始化失败 | 页面初始化失败 | 是 |
| EX-RPT-OMD-102 | 日期非法 | 请选择有效时间范围 | 否 |
| EX-RPT-OMD-103 | 查询失败 | 查询失败 | 是 |
| EX-RPT-OMD-104 | 导出失败 | 导出失败 | 是 |

---

## 6. 事件语义与消费方

**本期无领域写操作。**

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 常规日窗 ≤ 2s；导出 ≤ 30s。汇总 Tab 进销价净额：**禁止**为页脚再走完整明细展示 enrichment；数量汇总与定价事实须 **同一次 `findAll`/`WithSlips`**，金额按 `ID_IN_QUERY_CHUNK_SIZE` 分块装配后累加；**禁止**数量汇总后再按页二次扫描同一窗；**禁止**单次 `list*` 触顶 `EXPORT_MAX_ROWS` 静默截断页脚（单日看似正确、跨日合计偏小）。CATEGORY 维：领域先 ITEM 分桶，再用同窗 slip 全量品规解析类型后重分桶，**禁止** `EXPORT_MAX_ROWS` 抽样漏品规。来源=全部明细分页：两路前缀页（`pageNo×pageSize`，封顶导出上限）合并切片，**禁止**每次请求先拉满导出上限再内存切页 |
| **进价列角（DD §2.4 Type B）** | 汇总页脚 `purchaseTotalAmount` = 当次汇总行 `purchaseNetAmount` 代数和（与查询同筛选）；前端 `sumReportPurchaseCornerFromRows` 纠偏；**禁止**页脚另扫/另套定价 |
| **一致性** | 两路同筛选日；净汇总与明细可对账 |
| **租户** | 当前租户 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **两路** | 仅发药、仅领用、混合 |
| **净** | 退药/退领冲减 |
| **类型** | 西药/中药汇总仍可用；耗材行不入 |
| **隔离** | 异于用药明细 |

---

## 9. 关联文档与变更历史

| 类型 | 路径 |
| --- | --- |
| 族 DD | `设计决策/DD-报表中心-发药耗材挂号费用汇总明细族.md` |
| 前身 | `AS-OP-PHA-004-门诊发退药记录.md` |
| 领用 | `AS-INV-DEP-004` / `AS-INV-DEP-009` |
| 对照 | `AS-RPT-004`；`AS-RPT-005`（耗材同骨架异汇总） |
| SDD | `AS-RPT-003-门诊发药报表-SDD.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.15 | 2026-09-02 | **时间窗**：前端 `datetimerange`；Request 仍为 `String dateFrom/dateTo`（`yyyy-MM-dd HH:mm:ss`，兼容仅日期）；默认当日 `00:00:00～23:59:59`（BR-RPT-OMD-001） |
| v1.16 | 2026-09-22 | 筛选增加**药品性质**（麻醉/精一/精二，`KeyDrugType`→`pharmacology_category_code`）；init 下发 `medicinePropertyOptions`；与药品类型求交 |
| v1.14 | 2026-09-02 | **账批质检**：明细分页 `chargeLotNumber`/`lotMismatch`（BR-RPT-OMD-011）；金额口径不变 |
| v1.13 | 2026-08-28 | **导出列**：前端 `exportColumns` + 单 ExportRowDto 白名单（R-EXCEL-4/6） |
| v1.12 | 2026-08-28 | **导出列**：汇总/明细拆独立 ExportRowDto，列头对齐 UI；汇总 omit 全空列（R-EXCEL-4） |
| v1.11 | 2026-08-28 | **品规汇总**：数量/最小数量拼接单位名（`packageUnitName`/`minUnitName`） |
| v1.10 | 2026-08-28 | **品规汇总**：增药品编码、最小数量；数量整数展示；非品规维不展示这两列 |
| v1.9 | 2026-08-24 | **#4M-RFND**：退药不生成自身账单为写路径设计；读侧补原正向且同数量总额与发药一致 |
| v1.8 | 2026-08-24 | **汇总性能**：`WithSlips`/`WithLines` 一次扫描 + IN 分块累加金额；CATEGORY 同窗 slip 全量映射类型（禁 EXPORT 抽样） |
| v1.7 | 2026-08-24 | **汇总金额跨日口径**：`enrichSummaryPurchaseSaleAmounts` 按页拉齐定价明细再加总，禁止 `EXPORT_MAX_ROWS` 静默截断页脚 |
| v1.6 | 2026-08-17 | **汇总退药售价**：`assemblePatientDetailRowsForSummaryPricing` 与明细同走原正向补价再取负，修复汇总进价净额被冲、售价净额不冲 |
| v1.5 | 2026-08-12 | 患者侧进价按操作单位分支（包装×数量 / 拆零满包+零头）；对齐 DD v1.2；领用侧售价空（DD §4） |
| v1.4 | 2026-08-03 | 进/售价纠偏：药耗批号 `LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`；领用侧禁止目录 SALE；对齐费用明细进价 |
| v1.3 | 2026-08-02 | **费用性质**展示改跟发放收费行 `account_id`；筛选改为就诊任一账户 `contract_no`（非优先医保户） |
| v1.2 | 2026-07-28 | 增 **费用性质** 筛选与明细/导出列 **费用性质、医疗类别**（`ReportPatientSettlementContextAssembler`） |
| v1.1 | 2026-07-21 | 并入科室药品领用/退领；来源列；汇总加科室维；Gap-002 |
| v1.0 | 2026-07-21 | 首版：承接发退药记录 + 汇总 |
| v1.14 | 2026-10-08 | **BR-RPT-OMD-005**：汇总进价仅中草药 3 位、其它药品 2 位；售价一律 2 位；对齐 DD-报表 v1.19 |
