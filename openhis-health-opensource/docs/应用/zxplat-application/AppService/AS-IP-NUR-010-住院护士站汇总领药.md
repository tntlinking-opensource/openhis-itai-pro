# 住院护士站 · 汇总领药 · 应用服务设计（AS）

> **文档职责**：工作台右侧「汇总领药」Tab 契约——查询**待汇总**药品发放明细、生成汇总领药单、查询已生成汇总单及详情，以及按患者**只读**查看取消执行产生的退药；支撑病区护士向药房批量请领与核对退药。**ChargeItem 只读**取价分组，**不写**账单；**不写**退药确认（确认在 **AS-IP-PHA-003**）。
>
> **页面边界**：**本 Tab**——中西药 / **待汇总·汇总单·退药** 切换、截止时间、长临嘱筛选、查询、勾选时间点、**汇总领药**、打印。**患者上下文**在 **AS-IP-NUR-001**；**医嘱执行**在 **AS-IP-NUR-004**（执行后产生 `PREPARATION` 发放行）；**取消执行写路径及汇总重建**不在本文（见 **AS-IP-NUR-012**）；**药房汇总核发**在 **AS-IP-PHA-002**；**药房确认退药**在 **AS-IP-PHA-003**。
>
> **覆盖声明**：契约以 **§2 IF-IP-SUM-01～10** 为准；汇总分组键为 **`发放药房 location_id + itemId + ChargeItem.unit_price`**（同药房、同药、同价合并；**跨药房拆单**）；Supply 汇总行 **`lot_number` 可空**；写 Supply 时应用层**仅**落 **`source_*`（发放药房）**，`purpose_*` **NULL**；汇总详情**不展示批号**；审批链对齐库房单据（**PENDING_APPROVAL → submitApproval → APPROVAL → 药房 agree/reject**）；**IF-IP-SUM-09** 摆药单查询按患者分组、同 **`medReqId`** 合并数量；**IF-IP-SUM-10** 按就诊（单患者）只读退药清单（仅取消执行产物）。
>
> **编码**：AS-IP-NUR-010 · **版本**：v1.20 · **日期**：2026-10-16

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | IF-IP-SUM-01 待汇总明细列表；IF-IP-SUM-02 汇总单头列表；IF-IP-SUM-03 汇总单详情（药品关键信息 + 单价 + 总价，**无批号**）；IF-IP-SUM-09 **摆药单查询**（按患者分组，同 **medReqId** 合并数量，含剂量/用法/频次与 **displayQuantityText**）；IF-IP-SUM-10 **取消执行退药只读清单**（按就诊/单患者分组；仅 **AS-IP-NUR-012** 已核发取消写出的退药单）；IF-IP-SUM-04 **汇总保存**（按药房拆单 → 按 **itemId+账单单价** 合并 → 写 SupplyRequest 初态 **PENDING_APPROVAL** + 发放行 **SUMMARIZED**，**不创建 SupplyDelivery**）；IF-IP-SUM-05 **发送**（`submitApproval` → **APPROVAL**，写 `apply_time`）；IF-IP-SUM-06/07 打印（读侧/客户端，无写接口）；IF-IP-SUM-08 **撤销汇总**（仅 **PENDING_APPROVAL** 待发送单：`cancelMedicationDispenseSummary` 释放发放行 + `removeByBusNo` 逻辑删除 Supply 汇总单） |
| **在外** | 左侧患者树/列表/详情（**AS-IP-NUR-001**）；医嘱执行（**AS-IP-NUR-004**）；**取消执行写路径**及执行后汇总重建（**AS-IP-NUR-012**）；逐行住院发药（**AS-IP-PHA-001**）；药房汇总核发/驳回（**AS-IP-PHA-002**，含 **SupplyDelivery 创建、receiverId、库存扣减**）；**药房确认退药**（**AS-IP-PHA-003**）；库存扣减、追溯码、医保；**ChargeItem 任何写操作** |
| **涉及域** | **Encounter**；**MedicationRequest**；**MedicationDispense**；**MedicationDefinition**；**ChargeItem**（只读）；**Procedure**；**SupplyRequest**；**SupplyDelivery** |
| **编排权威** | 以本文 **§2、§4** 为准；写侧编排 **MedicationDispense 汇总态** 与 **Supply 汇总单（type 27）** 同事务 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **病区护士** | 在 **AS-IP-NUR-001** 勾选患者 → 切换中西药 / **待汇总·汇总单·退药**、设截止时间/长临嘱 → **查询** → 明细 Tab 勾选待发时间点 → **汇总保存** → 汇总 Tab **发送**（或对待发送单 **撤销汇总** 后回明细 Tab 重选）→ 查看/打印已生成汇总单；**退药 Tab** 按已选患者核对取消执行产生的退药 |
| **系统** | 装载已执行且 **PREPARATION** 的发放行 → 只读关联 **ChargeItem** 取价 → 按药房与 **itemId+单价** 合并数量（**拆零单位累加**）→ 保存汇总单（PENDING_APPROVAL）并绑定 `summaryNo`；发送时推进 **APPROVAL**；撤销时释放发放行并逻辑删除待发送 Supply 汇总单；退药 Tab 只读装载 **REFUND + CANCEL Procedure** 发放行 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **待汇总明细** | `MedicationDispense.status_enum = PREPARATION` 且已挂 `procedure_id`（**AS-IP-NUR-004** 执行产物）；对应 **MedicationRequest** 为已校对完成或已停嘱（`VERIFIED` / `STOPPED`，见 DOC-CONFLICT）；**排除** `therapy_enum=DISCHARGE_MEDICATION`（出院带药，**BR-IP-SUM-016**） |
| **汇总单（头）** | 同一 **`bus_no` / `summaryNo`** 下、同一发放药房的多条 Supply 明细；**主状态**以 **SupplyRequest.status_enum** 展示（待发送/待核发/已核发/已驳回）；药房核发后补充 **SupplyDelivery** 发药时间与领药人 |
| **汇总分组键（写侧）** | **先按发放药房**（`MedicationDispense.location_id`）拆单（**BR-IP-SUM-005**）；**同一汇总单号、同一发放药房内**合并键为 **`location_id` + `item_id` + `ChargeItem.unit_price`**（账单与发放行 **1:1** 只读）；**不同发放药房不得合并为一张汇总单**；**同药房内**同 itemId 不同单价必须拆行；**同药房、同药、同价**合并为一 Supply 行 |
| **批号（汇总行）** | **SupplyRequest / SupplyDelivery** 汇总明细行 **`lot_number` 允许为空**；底层 **MedicationDispense** 仍保留各行真实批号供药房核发扣库（**AS-IP-PHA-002**） |
| **单位槽（unitBands）** | 来自 **ProductDefinition / MedicationDefinition** 目录读模型：采购 / 包装（`PACKAGE`）/ 拆零（`SPLIT`）/ 库存（`STOCK`）等槽位；每槽含 `unit_code` 与 **相对拆零倍数（ratioToSplit）**（该槽位 1 单位 = `ratioToSplit` 拆零单位；拆零槽 `SPLIT` 恒为 1）。**本文不使用 legacy「拆零比 / part_percent」口径** |
| **拆零单位（split_unit_code）** | 药品目录拆零计量单位；与 **InventoryItem**、**SupplyRequest.item_quantity** 账面单位一致（对齐 **DM-WKF-006 BR-SR-012**、**AS-INV-WHS-002**） |
| **汇总数量（写 Supply 行）** | 合并组内各发放行：按该行 **`MedicationDispense.unit_code`** 在单位槽中解析 **ratioToSplit**，贡献量 = `quantity × ratioToSplit`（拆零单位）；组内贡献量**相加**后写入 Supply 行 **`item_quantity`**，**`unit_code = split_unit_code`**（见 **BR-IP-SUM-007～008**）。**禁止**写库后再按 `unit_code` 对 `item_quantity` 二次换算 |
| **汇总数量（明细 Tab 展示）** | 各发放行按 **MedicationDispense 持久化的原单位、原数量**展示，**不做**合并 |
| **汇总详情展示** | **不展示批号**；展示药品关键信息（名称、规格等）、**单价**（ChargeItem）、**总价**（见 **BR-IP-SUM-009**）；数量优先 **`displayQuantityText`**（行单位直显，见 **BR-IP-SUM-015**）；同时返回持久化拆零量 **`itemQuantity`/`unitCode`**（供审计/打印数据源，**禁止**前端用其直接拼界面数量）；合并行按药品拼音首字母序（**BR-IP-SUM-020**）；单头回填 **申领科室**（病区所属科室，**BR-IP-SUM-020**） |
| **账单单价 / 总价（只读）** | 执行阶段已生成 **ChargeItem**；本 Tab **只读查询**，**禁止**创建/修改/激活/重算 ChargeItem |
| **按药房拆单** | 一次「汇总保存」若选中行涉及多个 `location_id`（发放药房），**按药房拆成多张汇总单**（各药房独立 `summaryNo`） |
| **领药人（receiverId）** | **不在护士站保存**；由 **AS-IP-PHA-002 药房核发** 时写入 **SupplyDelivery.receiver_id** |
| **审批链** | 对齐库房单据：**保存** → `PENDING_APPROVAL`；**发送** → `submitApproval` → `APPROVAL`（写 `apply_time`）；**撤销汇总**（护士，仅待发送）→ 发放行回 `PREPARATION`（清 `summaryNo`）+ Supply 汇总单逻辑删除；**药房 agree** → `AGREE` + 发放行 `COMPLETED` + 创建 **SupplyDelivery**；**药房 reject** → `REJECT` + 发放行回退 `PREPARATION`（清 `summaryNo`，**护士无需再撤销**，回明细 Tab 重选即可） |
| **截止时间（queryDeadline）** | 仅 **IF-IP-SUM-01** 列表过滤：`planned_dispense_time ≤ queryDeadline`。执行侧已对符合 **BR-IP-EXE-035** 的长嘱按整包装落一行，**禁止**再对盒内未执行频次做「整盒补齐」或 bypass 截止（原 **BR-IP-SUM-017** 已**退役**） |
| **计划发药时间点（勾选）** | 读 `MedicationDispense.planned_dispense_time`；该字段由 **AS-IP-NUR-004** 执行写入，**等于频次计划槽位 planTime**（与 Procedure.`occurrence_time` 同轨）。护士在执行页调整的 **actualTime / recorded_time 不写入本字段**，故改执行时分**不改变**汇总领药勾选时间点与截止过滤（**BR-IP-EXE-023**） |
| **领药时间列（展示）** | **双轨**：勾选键/截止/归组仍锚 `plannedDispenseTime`；页面「领药时间」优先展示挂接 Procedure 的 **`recorded_time`**（列表回传 `recordedTime`），无则回落 `plannedDispenseTime`（承接 **AS-IP-NUR-004** 展示=执行时间、逻辑=计划时间） |
| **取消执行退药（只读）** | **IF-IP-SUM-10**：仅展示 **AS-IP-NUR-012** 取消**已核发**执行时 `createPendingRefundDispense` 产物——`dispense_enum=REFUND` + `status_enum ∈ {PENDING_REFUND, REFUNDED}` + `procedure_id` 挂接 **EventStatus.CANCEL**；按就诊（单患者）分组；**不含**未发软删、汇总剔除重建路径；**不含**药房确认写 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | IF-IP-SUM-01～10 |
| **非目标** | 页面初始化枚举专用接口（老 `summary-init`，新 UI 无）；**西药口服/注射**子筛选；**取消执行写路径**及汇总单自动重建（写在 **AS-IP-NUR-012**）；药房核发/作废/确认退药；库存/追溯/医保；ChargeItem 写路径；修改发放行批号/数量/库房；**已发送/已驳回/已核发**汇总单的护士侧撤销（药房驳回后由系统自动释放发放行，不提供「撤销汇总」按钮） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **Encounter** | 患者/就诊可见性锚点 | `DM-ADM-002-Encounter-就诊.md` |
| **MedicationRequest** | 医嘱长临、停嘱、中药标识等列表过滤 | `DM-MED-003-MedicationRequest-药品申请.md` |
| **MedicationDispense** | 待汇总/已汇总/已发放状态；`summary_no` 绑定 | `DM-MED-002-MedicationDispense-药品发放.md` |
| **MedicationDefinition / ProductDefinition** | 单位槽（unitBands）、相对拆零倍数、拆零单位、展示字段 | `DM-MED-001-MedicationDefinition-药品定义.md` |
| **ChargeItem** | **只读** `unit_price` / `total_price`（分组与详情展示） | `DM-ADM-011-ChargeItem-收费明细.md` |
| **Procedure** | 执行事实；待发清单要求已挂接 | `DM-CLN-004-Procedure-执行记录.md` |
| **SupplyRequest** | 药品汇总单（type **27**）明细行 | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **SupplyDelivery** | 汇总发放行；领药人、发放态 | `DM-WKF-005-SupplyDelivery-供应发放.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-IP-SUM-001** | **明细/写路径**：入参就诊须落在 **AS-IP-NUR-001** 已选且当前用户可见的住院就诊集合内。**汇总单头列表/详情/发送/撤销（IF-02/03/05/08/09）**：仅可见发放行 `encounter_id` 曾挂靠当前操作者**管辖病区**（WARD 轨迹，含已收口）的汇总单；反查路径为管辖病区 → 就诊 → `MedicationDispense.summary_no` → Supply `busNoIn`；**不**改按药房拆单 | 01～05、08、09 |
| **BR-IP-SUM-002** | **IF-IP-SUM-01** 基线：药品发放行 `PREPARATION` + `procedure_id` 非空；药品申请 `status_enum ∈ {VERIFIED, STOPPED}` 且非退药衍生；可按 `encounterIds`、中药标识 `tcmFlag`、长临嘱 `therapyEnum`、`queryDeadline`（`planned ≤ queryDeadline`）过滤；**另见 BR-IP-SUM-016** 排除出院带药；**另见 BR-IP-SUM-018** 麻醉申请范围 | 01 |
| **BR-IP-SUM-018** | **麻醉申请范围**：IF-IP-SUM-01 入参 `anesthesiaApplyOnly`；`true` 仅保留申请单号 **AA 前缀** 的待汇总药品；`false`/空排除之。**无**类目 25 / 同单号池兼容。实现：`InpatientNurseStationAnesthesiaApplyScopeSupport`；与校对/执行默认隐藏对齐 | 01 |
| **BR-IP-SUM-003** | **禁止**汇总已 `SUMMARIZED` / `COMPLETED` 等非待汇总态行；重复汇总整批拒绝 | 04 |
| **BR-IP-SUM-004** | ~~生成汇总须指定领药人~~ **已废弃**：领药人改由 **AS-IP-PHA-002** 核发时指定 | — |
| **BR-IP-SUM-005** | **跨药房不可混单**：先按 `MedicationDispense.location_id` 分组，每组生成独立 `summaryNo` | 04 |
| **BR-IP-SUM-006** | **同药房内合并键**：`location_id`（发放药房）+ `item_id` + `ChargeItem.unit_price`（只读）；**不同发放药房**即使 itemId 与单价相同也**不得**合并为同一汇总单/同一合并行；同药房内同 itemId **不同** `unit_price` **不得**合并 | 04 |
| **BR-IP-SUM-007** | 汇总前须能装配该药品完整 **unitBands**（至少含拆零槽 `SPLIT` 且 `ratioToSplit = 1`）；合并组内**每条**发放行的 `unit_code` 均须能在单位槽中解析出 **`ratioToSplit > 0`**；否则拒绝汇总 | 04 |
| **BR-IP-SUM-008** | **数量换算（写 Supply，拆零单位）**：对合并组内**每条**发放行，令 `ratioToSplit` = 该行 `unit_code` 在单位槽中的相对拆零倍数，贡献量 = `dispense.quantity × ratioToSplit`（精度与 **AS-IP-NUR-005 / AS-INV-WHS-002** 同族，HALF_UP）；组内贡献量**相加**得 Supply 行 `item_quantity`，`unit_code = split_unit_code`。**必须逐行**按各自 `unit_code` 解析倍数；**禁止**用组内首行单位代替全组；**禁止**写库后再对 `item_quantity` 按 `unit_code` 做二次倍数换算 | 04 |
| **BR-IP-SUM-009** | **汇总详情价格**：`unit_price` 取组内 ChargeItem（组内须一致）；`total_price` = 组内各发放行关联 **ChargeItem.total_price 之和**（只读累加，**禁止**用单价×汇总数量重算定价） | 03 |
| **BR-IP-SUM-010** | Supply 汇总行 **`lot_number` 置空**（或不写）；不在汇总详情展示批号 | 03、04 |
| **BR-IP-SUM-011** | **汇总保存**后：参与行 → `SUMMARIZED` 并写入对应 `summaryNo`；SupplyRequest 初态 **PENDING_APPROVAL**（type 27，**不写 apply_time**）；**不创建 SupplyDelivery**。**发送**后：`submitApproval` → **APPROVAL**（写 `apply_time`）。**撤销汇总**（仅 **PENDING_APPROVAL**）：`cancelMedicationDispenseSummary` + `removeByBusNo`。药房核发见 **AS-IP-PHA-002** | 04、05、08 |
| **BR-IP-SUM-012** | 写操作批量：**同一事务**；任一分组校验失败 **整批回滚** | 04、05、08 |
| **BR-IP-SUM-013** | 本 AS **不读不写** ChargeItem 写模型；**不处理**取消执行、药房核发、库存扣减 | 01～08 |
| **BR-IP-SUM-014** | **撤销汇总**仅允许 **PENDING_APPROVAL**（待发送）type **27** 汇总单；**已发送**（`APPROVAL`）、**已驳回**（`REJECT`）、**已核发**（`AGREE`）拒绝；撤销成功后发放行回 **PREPARATION** 且无 `summaryNo`，护士须回 **明细 Tab** 重新勾选后保存 | 08 |
| **BR-IP-SUM-015** | **汇总详情数量展示（读侧）**：`itemQuantity`/`unitCode` 保持 Supply 持久化**拆零**口径不变；界面数量优先展示 **`displayQuantityText`**：**跟着行上单位直显**（数量+单位文案，如 15 支 → `15支`；摆药 IF-09 为发放**操作单位**累加）。**禁止**满包整数+拆零余量拼接（如禁止 `1盒5支`）；无单位文案时仅数字。**禁止**改写 `itemQuantity`；**禁止**用展示量×单价重算 `totalPrice`（仍 **BR-IP-SUM-009** 只读累加；价格合并算、数量显示不合并） | 03、07、09 |
| **BR-IP-SUM-016** | **出院带药不进汇总**：`MedicationRequest.therapy_enum=DISCHARGE_MEDICATION` 的发放行——**IF-IP-SUM-01** 待汇总列表**一律剔除**；**IF-IP-SUM-04** 勾选含此类行 → **EX-IP-SUM-207**。执行后仅走药房**按患者逐行发药（AS-IP-PHA-001）**（承接 **AS-IP-DOR-003 BR-IP-ORD-029⑥**） | 01、04 |
| **BR-IP-SUM-017** | **（退役/删除）** 原「住院长嘱不可拆 · 待汇总整盒补齐」：执行写路径已按 **BR-IP-EXE-035** 整包装落一行，IF-IP-SUM-01 **仅** `planned ≤ queryDeadline`，**禁止**再引用本规则 | — |
| **BR-IP-SUM-019** | **取消执行退药只读口径（IF-IP-SUM-10）**：仅 `MedicationDispense.dispense_enum=REFUND` 且 `status_enum ∈ {PENDING_REFUND, REFUNDED}` 且 `procedure_id` 非空，且挂接 Procedure **`status_enum=CANCEL`**（**BR-IP-CXL-010** 产物）；按入参就诊分组（单患者维度）；中西药 Tab 按原药嘱 `tcmFlag` 收窄；**禁止**本 IF 调用药房确认退药写能力 | 10 |
| **BR-IP-SUM-020** | **汇总领药单打印/详情/列表展示**：① **申领科室** `applicantDepartmentName` = 发放行就诊所属**病区**主挂靠科室名（`Location.organizationId`）；病区未挂科室时回落就诊当前科室名；多病区多科室顿号拼接。加载须按就诊主键直查（`listInpatientContextsByEncounterIdsOrdered`），**禁止**走会话组织/管辖病区收窄查询（否则药房打印/跨管辖空值）。② **合并明细行序**按药品目录拼音检索码 `pyStr` 首字母升序，缺拼音回落药品名；打印模板 `options.field` 须为 `applicantDepartmentName`（scenario `ip-dispenseSummary-print`） | 02、03、07、09 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-IP-SUM-01 | 明细 Tab：切换中西药/长临嘱/截止时间、点查询 | 分页查询**待汇总**药品发放明细（含可勾选时间点、只读单价；**不含出院带药**；默认排除麻醉申请药品） | 读 | BR-IP-SUM-001、002、**016**、**018**、013 | 无 | MedRequest + MedDispense + ChargeItem + MedDefinition | **MedicationDispenseQueryService#pageMedicationDispensePickupPending** |
| IF-IP-SUM-02 | 汇总 Tab：点查询 | 分页查询**已生成汇总单头**列表 | 读 | BR-IP-SUM-001、013 | 无 | SupplyRequest + SupplyDelivery | **SupplyRequestQueryService#pageMedicationSummarySlips** |
| IF-IP-SUM-03 | 汇总 Tab：点详情 | 查询指定汇总单**合并后明细**（无批号；含单价/总价；数量 **displayQuantityText**；**申领科室**；明细拼音序） | 读 | BR-IP-SUM-009、010、013、**015**、**020** | 无 | SupplyRequest + MedDefinition + ChargeItem | **SupplyRequestQueryService#listMedicationSummarySlipLinesByBusNo** |
| IF-IP-SUM-09 | 汇总 Tab：点摆药单查询 | 查询指定汇总单**按患者分组的摆药明细**（同 **medReqId** 合并数量；含剂量/用法/频次；数量 **displayQuantityText**；无批号） | 读 | BR-IP-SUM-010、013、**015** | 无 | MedDispense + MedDefinition + Encounter | **MedicationDispenseQueryService#listMedicationDispensesBySummaryNo** |
| IF-IP-SUM-10 | 退药 Tab：选患者、切中西药、点查询 | 按就诊查询**取消执行产生的退药清单**（待退/已退；单患者分组） | 读 | BR-IP-SUM-001、**019**、013 | 无 | MedDispense + Procedure + MedRequest + MedDefinition | **MedicationDispenseQueryService#listCancelExecuteRefundDispensesByEncounterIds** + Procedure CANCEL 校验 |
| IF-IP-SUM-04 | 明细 Tab：勾选时间点 → 汇总保存 | **汇总保存**（按药房拆单、按 itemId+单价合并、**拆零单位**写 SupplyRequest 初态 PENDING_APPROVAL + 发放行 SUMMARIZED；**不创建 SupplyDelivery**；**拒绝出院带药**） | 写 | BR-IP-SUM-003～012、**016**、013 | PREPARATION→SUMMARIZED；新建 Supply 汇总单 **PENDING_APPROVAL** | MedDispense + SupplyRequest | — |
| IF-IP-SUM-05 | 汇总 Tab：勾选单头 → 发送 | **提交审批**（`submitApproval` → **APPROVAL**，写 `apply_time`） | 写 | BR-IP-SUM-011～012、013 | PENDING_APPROVAL→APPROVAL | SupplyRequest | — |
| IF-IP-SUM-06 | 明细 Tab：打印明细 | **客户端打印**待汇总明细（无独立写接口） | 读 | BR-IP-SUM-013 | 无 | 同 IF-IP-SUM-01 | 同 IF-IP-SUM-01 |
| IF-IP-SUM-07 | 汇总 Tab：打印 | **客户端打印**汇总单（须走 IF-03；含申领科室与拼音序） | 读 | BR-IP-SUM-013、**020** | 无 | 同 IF-IP-SUM-03 | 同 IF-IP-SUM-03 |
| IF-IP-SUM-08 | 汇总 Tab：勾选待发送单 → 撤销汇总 | **撤销汇总**（`cancelMedicationDispenseSummary` + `removeByBusNo`） | 写 | BR-IP-SUM-011、012、014 | SUMMARIZED→PREPARATION；Supply 汇总单逻辑删除 | MedDispense + SupplyRequest | — |

---

## 3. 缺口跟踪（Gap）

### 3.1 缺口分类说明

| 分类 | 含义 | 本文处理 |
| --- | --- | --- |
| **领域层 Gap** | DM §6 已定义或 AS 读侧必需，但 **QueryService / QueryModel 未实现** | §3.2 三条 P0 已关闭 |
| **应用层未实现** | AppService、Controller、DTO 装配、前端页面 | **不算 Gap**；应用层 **`NurseStationDispenseSummaryAppService`** 已按 **AS-SDD** 落地；前端 `dispense-summary` 待联调 |
| **应用层编排（非 Gap）** | IF-IP-SUM-04 的 itemId+单价分组、unitBands 拆零换算、按药房拆单 | 在应用层实现；写侧领域能力已存在 |

### 3.2 领域层 Gap 清单

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-IP-SUM-001 | QueryService 能力 | `DM-MED-002` §6.2.17 | ~~缺待汇总领药明细分页查询~~ | `pageMedicationDispensePickupPending` + `MedicationDispensePickupPendingQueryModel` 已实现 | IF-IP-SUM-01、05 | P0 | **已关闭** |
| GAP-IP-SUM-002 | QueryService 能力 | `DM-WKF-006` §6.2.10 | ~~缺药品汇总单头分页查询~~ | `pageMedicationSummarySlips` + `MedicationSummarySlipQueryModel` 已实现 | IF-IP-SUM-02、06 | P0 | **已关闭** |
| GAP-IP-SUM-003 | QueryService 能力 | `DM-WKF-006` §6.2.11 | ~~缺汇总单合并行详情查询~~ | `listMedicationSummarySlipLinesByBusNo` + `MedicationSummarySlipLineQueryModel` 已实现 | IF-IP-SUM-03、06 | P0 | **已关闭** |

> **说明**：写侧 **MedicationDispense#summarizeMedicationDispenses**、**#cancelMedicationDispenseSummary**、**SupplyRequest#createSummarySupplyRequest**（初态 **PENDING_APPROVAL**）、**#submitApproval**、**#removeByBusNo** 等领域能力已存在；**SupplyDelivery#createSummarySupplyDelivery** 由 **AS-IP-PHA-002** 药房核发调用。**应用层**后端已落地：`com.zxplat.domain.application.inpatient.nursestation.dispensesummary` 包下 **`NurseStationDispenseSummaryAppService`** 六写读接口 + Mapper/Assembler/DTO；HTTP 前缀 **`nurseStationDispenseSummary`**。前端 Tab 待联调，**不记入 §3.2**。

---

## 4. 接口编排说明书（业务步骤）

### IF-IP-SUM-01 待汇总明细列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **AS-IP-NUR-001** 已选至少一名患者；用户设置中西药 Tab、长临嘱、**queryDeadline** 或点**查询** |
| **主路径步骤** | 校验可见性（BR-IP-SUM-001）→ 预解析执行药嘱并**剔除出院带药**（BR-IP-SUM-016）→ 按就诊集合分页查询待发发放行（BR-IP-SUM-002，`planned ≤ queryDeadline`）→ 关联医嘱展示字段（组号、用法、剂量等）→ **只读**关联 **ChargeItem**（与发放分段 1:1）回填 **unit_price**（供前端展示与后续分组预览）→ 按就诊分组；同一医嘱下挂多个可勾选**计划发药时间点**（`dispenseId` + `procedureId` + 时刻） |
| **关键分支** | 中药/西药 Tab 过滤 `tcmFlag`；长临嘱过滤 `therapyEnum`（`all`/`long`/`temp` **不含**出院带药）；仅 **PREPARATION** 且已执行的时间点可勾选；出院带药执行后走 **AS-IP-PHA-001** 按患者发药 |
| **失败分支** | 未选患者或无权（EX-IP-SUM-101）；查询失败（EX-IP-SUM-102） |
| **查询侧说明** | **MedicationDispensePickupPendingQueryModel**（`MedicationDispenseQueryService#pageMedicationDispensePickupPending`）；ChargeItem **只读** |

### IF-IP-SUM-02 汇总单头列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户切至**汇总** Tab 并点**查询**（**不**依赖左侧勾选患者；按当前操作者**管辖病区**收窄，**BR-IP-SUM-001**） |
| **主路径步骤** | ① 解析管辖启用病区 → ② `EncounterQueryService#listEncounterIdsByWardLocationIds` → ③ `MedicationDispenseQueryService#listDistinctSummaryNosByEncounterIds` 得 `busNoIn`（空则空页，禁止回落全院）→ ④ 可选 `purposeLocationId` 显式收窄药房 → ⑤ 分页查询 type **27** 单头（`busNoIn` 门控）→ 展示单据号、**汇总药房**（`purposeLocationName`）、申请时间（发送后）、发药时间（药房核发后）、申请人、领药人（核发后）、**审批态**（`supplyStatusEnum`） |
| **关键分支** | 待发送：`PENDING_APPROVAL`；待核发：`APPROVAL`；已核发：`AGREE`；已驳回：`REJECT`；无管辖病区或病区下无汇总发放行 → **空列表** |
| **失败分支** | 查询失败（EX-IP-SUM-102） |
| **查询侧说明** | **MedicationSummarySlipQueryModel**（`pageMedicationSummarySlips` + 发放表反查 `busNoIn`）；病区门控不改写 Supply 单头字段 |

### IF-IP-SUM-03 汇总单详情

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在汇总 Tab 点击某单**详情** |
| **主路径步骤** | 按 `summaryNo` 加载汇总单 **合并后明细行** → 装配药品关键信息 → 每行：持久化 **`itemQuantity`（拆零）**、**`displayQuantityText`（行单位直显，BR-IP-SUM-015）**、**unit_price**、**total_price**（BR-IP-SUM-009）→ 合并行按拼音首字母序（**BR-IP-SUM-020**）→ 单头回填 **申领科室**（病区所属科室，**BR-IP-SUM-020**）→ **不返回批号**（BR-IP-SUM-010） |
| **关键分支** | 无 |
| **失败分支** | 单号不存在（EX-IP-SUM-103） |
| **查询侧说明** | **MedicationSummarySlipLineQueryModel**（`SupplyRequestQueryService#listMedicationSummarySlipLinesByBusNo`）；价格来自 ChargeItem **只读累加**；申领科室经发放行就诊→病区主挂靠科室解析 |

### IF-IP-SUM-09 摆药单查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在汇总 Tab 某单行操作点击**摆药单查询**（任意审批/发药态均可） |
| **主路径步骤** | 按 `summaryNo` 加载发放行 → 按 **就诊（患者）** 分组 → 组内按 **`medReqId`** 合并数量 → 装配**病房名+床号**/姓名 → 剂量/用法/频次优先取 **MedicationRequest**（`dose+doseUnit` / `methodCode` / `rateCode`），发放行作回落 → 数量写入 **`displayQuantityText`（操作单位直显，BR-IP-SUM-015）** → **不返回批号**（BR-IP-SUM-010） |
| **关键分支** | 与 IF-03 对照：03 看「整单领了哪些药（药品+单价合并）」；09 看「领回的药分别给了哪些患者（患者 + 医嘱合并）」 |
| **失败分支** | 单号无发放行或不存在（EX-IP-SUM-103）；加载失败（EX-IP-SUM-102） |
| **查询侧说明** | **MedicationDispenseSummaryItemQueryModel**（`MedicationDispenseQueryService#listMedicationDispensesBySummaryNo`，含 `medReqId`/剂量/用法/频次）；床号姓名来自 **EncounterContextReader** |

### IF-IP-SUM-10 取消执行退药清单（按患者）

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **AS-IP-NUR-001** 已选至少一名患者；用户切到 **退药 Tab**、切换中西药或点**查询** |
| **主路径步骤** | 校验可见性（BR-IP-SUM-001）→ 域查询 REFUND + 待退/已退 + `procedure_id` 非空 → 批量校验 Procedure 为 **CANCEL**（BR-IP-SUM-019）→ 按中西药 Tab 收窄原药嘱 → 按就诊分组装配药品名/规格/数量/状态/药房/取消执行人/总价/取消执行时间点 |
| **关键分支** | 多选患者时按就诊各自成组（单患者维度展示）；无取消执行退药时返回空分组；**不**暴露确认退药操作 |
| **失败分支** | 未选患者或无权（EX-IP-SUM-101）；查询失败（EX-IP-SUM-102） |
| **查询侧说明** | `MedicationDispenseQueryService#listCancelExecuteRefundDispensesByEncounterIds`；CANCEL 校验 `ProcedureQueryService#listByIds` |

### IF-IP-SUM-04 汇总保存

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 明细 Tab 已勾选至少一个待发时间点 |
| **主路径步骤** | ① 按入参 `dispenseId` 加载发放行，校验 **PREPARATION** 且未汇总（BR-IP-SUM-003）→ ①b 校验**非出院带药**（BR-IP-SUM-016）→ ② **只读**加载各行关联 **ChargeItem**，取 `unit_price` → ③ 按 **location_id**（发放药房）拆药房组（BR-IP-SUM-005），每组生成新 **summaryNo** → ④ 组内再按 **`location_id + item_id + unit_price`** 合并（BR-IP-SUM-006）→ ⑤ 加载药品 **ProductDefinition** 读模型并装配 **unitBands**，校验 **BR-IP-SUM-007** → ⑥ 对每合并组按 **BR-IP-SUM-008** 逐行解析 `ratioToSplit`、折算拆零数量并累加 → ⑦ 调用 **MedicationDispense** 汇总写能力，批量绑定 **summaryNo** 并推进 **SUMMARIZED** → ⑧ 调用领域创建 **SupplyRequest**（type 27，初态 **PENDING_APPROVAL**，**lot_number 空**，**不写 apply_time**；应用层**仅**写 **`source_*`（发放药房）**，类型由 **`LocationQueryService#requireSupplyRequestTypeForLocationId`** 推导；`purpose_*` **NULL**）→ ⑨ **不创建 SupplyDelivery** → ⑩ 同事务提交（BR-IP-SUM-012） |
| **关键分支** | 单次操作跨多药房 → 返回多个 `summaryNo`；保存后须用户在汇总 Tab **发送**（IF-IP-SUM-05） |
| **失败分支** | 已汇总（EX-IP-SUM-201）；单位槽不完整或发放行 `unit_code` 无法解析 **ratioToSplit**（EX-IP-SUM-202）；ChargeItem 缺失导致无法取价（EX-IP-SUM-203）；部分行校验失败整批回滚（EX-IP-SUM-204）；勾选含出院带药（EX-IP-SUM-207） |
| **查询侧说明** | 写前强加载发放行与 ChargeItem（只读） |

### IF-IP-SUM-05 发送汇总单

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 汇总 Tab 勾选至少一张 **PENDING_APPROVAL** 状态的汇总单 |
| **主路径步骤** | 校验单号存在且 type **27** → 循环调用 **SupplyRequestDomainService#submitApproval**（同 `bus_no` 全行 → **APPROVAL**，写 `apply_time` 与 `applicant_id`）→ 同事务提交 |
| **关键分支** | 支持批量发送多张汇总单（≤50）；已发送/已核发/已驳回单拒绝 |
| **失败分支** | 单号不存在（EX-IP-SUM-103）；状态不允许（EX-IP-SUM-205） |
| **查询侧说明** | 写前 `getSupplyByBusNo` 校验 type 27 |

### IF-IP-SUM-08 撤销汇总单

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 汇总 Tab 勾选至少一张 **PENDING_APPROVAL**（待发送）状态的汇总单 |
| **主路径步骤** | ① 校验单号存在且 type **27**、状态 **PENDING_APPROVAL**（**BR-IP-SUM-014**）→ ② `cancelMedicationDispenseSummary`（发放行 **SUMMARIZED→PREPARATION**，清 `summary_no`）→ ③ `removeByBusNo`（同 `bus_no` 汇总 Supply 行逻辑删除）→ ④ 同事务提交 |
| **关键分支** | 支持批量撤销（≤50）；**已发送/已驳回/已核发**单拒绝；撤销后护士须回 **明细 Tab** 重新勾选时间点再保存 |
| **失败分支** | 单号不存在（EX-IP-SUM-103）；状态不允许（EX-IP-SUM-206） |
| **查询侧说明** | 写前 `getSupplyByBusNo` 校验 type 27 与 **PENDING_APPROVAL** |

### IF-IP-SUM-06 打印待汇总明细

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 明细 Tab 已有 **IF-IP-SUM-01** 查询结果 |
| **主路径步骤** | 前端基于当前列表数据打开打印模板；必要时重新调用 **IF-IP-SUM-01** 刷新 |
| **关键分支** | 无后端写操作 |
| **失败分支** | 无数据可打印（前端提示） |
| **查询侧说明** | 复用 IF-IP-SUM-01 |

### IF-IP-SUM-07 打印汇总单

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户在汇总 Tab 点击某单**打印**（手动） |
| **主路径步骤** | 调用 **IF-IP-SUM-03** 取合并明细 + 头信息（须含 **申领科室**；明细已拼音序）→ 客户端打印（PrintData 键 `applicantDepartmentName` / `drugRows`） |
| **关键分支** | 发送（IF-IP-SUM-05）成功后可按本机「发送后自动打印领药单」开关自动打；**产品默认开启**；本机 localStorage 可改；**院点**可经前端 `#/` override `dispense-summary-auto-print.config` 改未设置时的默认（如国基默认关，见院点 README） |
| **失败分支** | 单号无效（EX-IP-SUM-103） |
| **查询侧说明** | 复用 IF-IP-SUM-03（**禁止**仅用 IF-02 列表缓存打印，以免缺申领科室与行序） |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示语义 | 可重试 |
| --- | --- | --- | --- |
| EX-IP-SUM-101 | 未选患者或无权 | 请先选择患者或无权访问 | 是 |
| EX-IP-SUM-102 | 列表查询失败 | 加载失败，请稍后重试 | 是 |
| EX-IP-SUM-103 | 汇总单不存在 | 汇总单不存在或已失效，请刷新 | 是 |
| EX-IP-SUM-201 | 重复汇总 | 所选药品已汇总，请勿重复操作 | 刷新后重试 |
| EX-IP-SUM-202 | 单位槽/换算缺失 | 药品单位槽不完整，或发放行单位无法换算为拆零数量，请联系管理员 | 否 |
| EX-IP-SUM-203 | 账单缺失 | 缺少执行账单，无法汇总，请确认医嘱已执行 | 否 |
| EX-IP-SUM-204 | 批量校验失败 | 汇总保存失败，请检查所选明细状态 | 刷新后重试 |
| EX-IP-SUM-205 | 发送失败/状态不允许 | 发送失败，请检查汇总单状态 | 刷新后重试 |
| EX-IP-SUM-206 | 撤销失败/状态不允许 | 撤销失败，仅待发送汇总单可撤销 | 刷新后重试 |
| EX-IP-SUM-207 | 勾选含出院带药 | 出院带药不可汇总领药，请到药房按患者发药 | 否 |

---

## 6. 事件语义与消费方

| 事件（语义级） | 触发点 | 消费方 / 意图 | 幂等语义 |
| --- | --- | --- | --- |
| **MedicationDispenseSummarizedEvent** | IF-IP-SUM-04 汇总绑定成功 | 发药/汇总读模型刷新；**AS-IP-PHA-002** 待发汇总队列 | 以 `summaryNo` + 发放行 ID |
| **SupplyRequestCreatedEvent** | IF-IP-SUM-04 创建 type 27 汇总明细成功 | Supply 读侧、汇总单列表 | 以 `bus_no` + 行 ID |
| **SupplyRequestSubmittedEvent** | IF-IP-SUM-05 发送成功 | 汇总 Tab 审批态刷新；**AS-IP-PHA-002** 待核发队列 | 以 `bus_no` |
| **MedicationDispenseSummaryCancelledEvent** | IF-IP-SUM-08 撤销成功 | 待汇总明细列表刷新 | 以 `summaryNo` + 发放行 ID |
| **SupplyRequestDeletedEvent** | IF-IP-SUM-08 撤销成功 | 汇总 Tab 列表刷新 | 以 `bus_no` |

> **药房驳回**（**AS-IP-PHA-002** IF-IPS-04）亦发布 **MedicationDispenseSummaryCancelledEvent** / **SupplyRequestRejectedEvent**；护士站**不提供**对已驳回单再次「撤销汇总」，发放行已由药房释放，须回明细 Tab 重选。

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **一致性** | IF-IP-SUM-04/05/08 与发放行状态、Supply 单据 **同一事务**提交 |
| **幂等** | 同一批 `dispenseId` 重复提交须被 **SUMMARIZED** 门控拒绝；已发送单重复发送须被 **assertSubmittable** 拒绝 |
| **性能** | 单次汇总支持病区批量勾选（建议 ≤500 发放行）；读列表分页 |
| **审计** | 记录汇总申请人、`apply_time`（发送时）、`summaryNo`；领药人由药房核发写入 |
| **ChargeItem** | 全 Tab **只读**；禁止本场景触发定价或状态迁移 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 同药房、同 itemId、同 ChargeItem 单价、不同批号的多条 PREPARATION 行 → **合并为一行** Supply；`lot_number` 空；发放行均 SUMMARIZED |
| **单价分组** | 同 itemId、**不同 unit_price** → **两行**汇总明细；详情各自单价/总价正确 |
| **数量换算** | 组内混有包装（`PACKAGE`）与拆零（`SPLIT`）等不同 `unit_code` 的发放行 → 按 **BR-IP-SUM-008** 逐行取 **ratioToSplit** 折算后累加；Supply `item_quantity` 为拆零单位 |
| **单位槽边界** | 仅拆零单位开立（ratio=1）；仅包装单位开立（ratio=包装→拆零倍数）；组内混单位 |
| **跨药房** | 选中两个药房的发放行 → 生成 **两个 summaryNo** |
| **门控** | 已 SUMMARIZED/COMPLETED 行拒绝；未执行（无 procedure）不出现在 IF-01 |
| **价格只读** | 汇总前后 ChargeItem 条数与金额不变；详情 total_price = 组内 ChargeItem.total_price 之和 |
| **详情** | 汇总详情**无批号**字段；含单价、总价、药品名/规格；数量 **`displayQuantityText`**（如 15 支 → `15支`；禁止 `1盒5支`） |
| **汇总单头列表** | 汇总 Tab 表格展示 **`purposeLocationName`（汇总药房）** 列 |
| **打印** | IF-06/07 不触发写库；发送后自动打印由本机开关控制（**产品默认开**；院点可 override 未设置时的默认） |
| **发送** | 保存后 PENDING_APPROVAL → 发送后 APPROVAL；非 PENDING_APPROVAL 拒绝 EX-IP-SUM-205 |
| **撤销** | 仅 PENDING_APPROVAL 可撤销；撤销后发放行回 PREPARATION、Supply 逻辑删除；已发送/驳回/核发拒绝 EX-IP-SUM-206 |
| **并发** | 两人同时汇总同一 dispenseId → 其一成功、其一 EX-IP-SUM-201 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 | 关系 |
| --- | --- |
| `AS-IP-NUR-001-住院护士站患者管理.md` | 上游：患者/就诊上下文 |
| `AS-IP-NUR-004-住院护士站医嘱执行.md` | 上游：执行产生 PREPARATION 发放行与 ChargeItem |
| `AS-IP-NUR-012-住院护士站取消执行.md` | 上游写：已核发取消 → 待退药单（**BR-IP-CXL-010**）；本 AS IF-10 只读展示 |
| `AS-IP-PHA-001-住院发药.md` | 下游：出院带药等**无 `summary_no`** 发放行按患者逐行发药（**BR-IP-SUM-016**） |
| `AS-IP-PHA-002-住院汇总发药.md` | 下游：按 summaryNo 批量核发/驳回 |
| `AS-IP-PHA-003-住院退药.md` | 下游：药房确认退药（护士站 IF-10 **不**调用） |
| `DM-MED-002-MedicationDispense-药品发放.md` | 汇总/取消汇总状态机 |
| `DM-WKF-006-SupplyRequest-供应请求.md` | type 27 汇总单 |
| `DM-WKF-005-SupplyDelivery-供应发放.md` | 汇总发放行 |
| `DM-ADM-011-ChargeItem-收费明细.md` | 只读取价 |
| `AS-INV-WHS-002-药库进货单.md` | 同族：**Supply/库存数量为拆零单位**、**unitBands + ratioToSplit** 口径 |

### 9.2 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v1.20 | 2026-10-16 | **BR-IP-SUM-020**：汇总详情/打印回填 **申领科室**（病区所属科室）；合并明细按药品拼音首字母序；打印模板 `applicantDepartmentName`；代码先行回写 |
| v1.19 | 2026-10-15 | **IF-IP-SUM-10** / **BR-IP-SUM-019**：汇总领药 Tab 增「退药」只读视图——按患者展示取消执行产生的退药单（REFUND + CANCEL Procedure）；确认写仍在 **AS-IP-PHA-003**；代码先行回写 |
| v1.18 | 2026-10-13 | **IF-IP-SUM-02/03/05/08/09 + BR-IP-SUM-001**：汇总单列表/详情/发送/撤销按**管辖病区**收窄（发放行 encounter→WARD 轨迹→summaryNo→`busNoIn`）；拆单仍按药房；无管辖病区空页 |
| v1.17 | 2026-10-13 | **BR-IP-SUM-015**：数量展示改为行/操作单位直显（禁止满包+余量如 `1盒5支`）；金额仍 **BR-IP-SUM-009** 只读累加；对齐药房 **AS-IP-PHA-002** / SDD |
| v1.16 | 2026-10-13 | **BR-IP-SUM-018 纠偏**：麻醉识别改为申请单号 **AA 前缀**；对齐 **AS-SDD v1.14** / **AS-IP-NUR-003 v1.31** |
| v1.15 | 2026-10-13 | **BR-IP-SUM-018**：IF-IP-SUM-01 `anesthesiaApplyOnly`（默认排除麻醉申请）；对齐 **AS-SDD v1.13** / **AS-IP-NUR-003 v1.30** |
| v1.14 | 2026-10-12 | **UI**：发送后自动打印领药单——产品默认仍开启；默认值可经 `#/` `dispense-summary-auto-print.config` 院点覆盖（国基 L2 默认关，见院点 README）；本机 localStorage 显式偏好优先 |
| v1.13 | 2026-08-05 | **BR-IP-SUM-017 退役**：废止待汇总「整盒补齐」；IF-IP-SUM-01 仅 `planned ≤ queryDeadline`（执行已按 **BR-IP-EXE-035** 整包装写一行）；对齐 **AS-SDD v1.11** |
| v1.12 | 2026-08-04 | **BR-IP-SUM-017**：长嘱不可拆时 IF-IP-SUM-01 待汇总按包装整盒补齐时点（对齐 **BR-IP-EXE-035**）；写路径不变（**v1.13 废止本规则**） |
| v1.11 | 2026-07-18 | 新增 **IF-IP-SUM-09 摆药单查询**：按患者分组、同 **medReqId** 合并数量；数量 **displayQuantityText** 与 IF-03 同口径；覆盖声明扩展至 01～09；摆药姓名展示含**病房+床号**；剂量/用法/频次优先 **MedicationRequest** |
| v1.10 | 2026-07-17 | **BR-IP-SUM-016** / **EX-IP-SUM-207**：出院带药（`DISCHARGE_MEDICATION`）不进待汇总、禁止汇总保存；仅走 **AS-IP-PHA-001** 按患者发药；承接 **BR-IP-ORD-029⑥** |
| v1.9 | 2026-07-17 | §1.3 **领药时间列双轨**：展示优先 `recordedTime`（Procedure.recorded_time），勾选/截止仍锚 `plannedDispenseTime`；对齐 **AS-IP-NUR-004** 展示=执行、逻辑=计划 |
| v1.8 | 2026-07-16 | §1.3 增补：**计划发药时间点**=`planned_dispense_time`=上游 **AS-IP-NUR-004** 的 planTime；执行页 actualTime **不**改写本字段（与 **BR-IP-EXE-023** 双轨对齐） |
| v1.7 | 2026-06-17 | 汇总 Tab 单头列表展示 **`purposeLocationName`（汇总药房）**；汇总详情新增 **`displayQuantityText`**（**BR-IP-SUM-015**：满包整数 DOWN + 拆零余量，如 `1盒1片`）；`itemQuantity` 仍为拆零持久量 |
| v1.6 | 2026-06-11 | IF-IP-SUM-02：`purposeLocationId` 仅显式传入时收窄；移除用户可操作药房自动门控 |
| v1.5 | 2026-06-06 | 新增 **IF-IP-SUM-08 撤销汇总**（仅 PENDING_APPROVAL：`cancelMedicationDispenseSummary` + `removeByBusNo`）；**BR-IP-SUM-014**；**EX-IP-SUM-206**；澄清药房驳回后护士回明细 Tab 重选、无需再撤销 |
| v1.4 | 2026-06-06 | **审批链对齐库房**：IF-04 改为汇总保存（PENDING_APPROVAL，不创建 Delivery）；新增 IF-05 发送（submitApproval）；领药人/扣库/Delivery 下沉 **AS-IP-PHA-002**；单头展示 **supplyStatusEnum** |
| v1.3 | 2026-06-06 | 应用层 **`NurseStationDispenseSummaryAppService`** 成码闭环；§2 查询服务列对齐域 QueryService；§3 说明应用层后端已落地 |
| — | — | **DOC-CONFLICT（仍有效，代码已按此实现）**：**BR-IP-SUM-002** 药嘱状态文案 **COMPLETED/STOPPED**；待发查询实现采用 **VERIFIED/STOPPED**（与 **AS-IP-NUR-004** 执行列表一致） |
| v1.1 | 2026-06-06 | **BR-IP-SUM-007/008** 改为 **unitBands + ratioToSplit + split_unit_code** 口径；剔除 legacy「拆零比/part_percent」表述；与 **AS-INV-WHS-002**、**BR-SR-012** 对齐 |
| v1.0 | 2026-06-06 | 首版：汇总分组键 **itemId+ChargeItem.unit_price**；Supply 批号可空；详情无批号；ChargeItem 只读；取消汇总/取消执行/口服注射筛选为非目标 |
