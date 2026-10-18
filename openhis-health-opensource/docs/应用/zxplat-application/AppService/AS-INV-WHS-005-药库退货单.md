# 药库退货单 · 应用服务设计（AS）

> **文档职责**：说明「药库退货单（退供应商）」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图。
>
> **页面边界**：药库业务线 — 基于**已同意进货单**（`original_bus_no`）向供应商退货（`SupplyType` **`CABINET_RETURN_TO_SUPPLIER`**）。**禁止自由选品**；明细须为进货行子集，可部分退、可多次退直至可退余量为 0。审批**同意**后编排 **SupplyDelivery（退货发放）** + **源药库 InventoryItem 扣减**（须源库仍有实物；已调拨至药房须先调回）。type **3** 须**财务审批**。
>
> **与进货分工**：**AS-INV-WHS-002** 负责进货入库；本 AS 以进货单为唯一明细来源（`original_bus_no`），对偶于进货增库存、本页减库存。
>
> **编码**：AS-INV-WHS-005 · **版本**：v0.6 · **日期**：2026-08-12

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
|------|------|
| **在内** | 关联已同意进货单建 **`CABINET_RETURN_TO_SUPPLIER`** 草稿；编辑/删单；提交/撤回/同意/驳回；**同意**后退货发放 + 源库扣减 +（条件）追溯出库 + 可选医保 3504（**`purc_invo_no`**←本单/`进货`/`0`，见 **09** §4.5e） |
| **在外** | 进货单维护（**AS-INV-WHS-002**）；药房/科室退库；自由目录选品 |
| **涉及域** | **SupplyRequest**、**SupplyDelivery**、**InventoryItem**、**TraceNumberRecord**；**Location**、**Supplier**、**人员** |

### 1.2 术语

| 术语 | 口径 |
|------|------|
| **退货单** | `type_enum`=`CABINET_RETURN_TO_SUPPLIER`；`original_bus_no` **必填**，指向已 **AGREE** 的 **`CABINET_INBOUND_RECEIPT`** |
| **可退余量** | 按 **`item_table + item_id + 批号`**：`进货量 − Σ(同键待审/审中/已同意退货量)`；修订时排除本单 |
| **源库可用量** | 进货目的药库（= 退货源库）当前 **InventoryItem** 可用量；调拨出库后可能为 0 |
| **经手人** | `practitioner_id` **可选**；`applicant_id` 仍取会话制单人员 |
| **采购发票号** | 单头 **`invoiceNo`** → 各行 **`invoice_no`**（可选，可空）；进销存 3504：优先本单 → 关联进货单 → 占位 **`0`** |
| **追溯码预填** | **当前院内剩余码**（源药库本库净量优先且主档 `IN_HOSPITAL`/剩余&gt;0；本库空则全院同品同批在院剩余）；截取按 `min(可退余量, 源库可用量)`；**不是**进货当时扫的码串。无码批不预填、不校验（**AS-CUS-TRC-001 BR-TRC-022**） |

### 1.3 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
|---------|------|---------|
| **BR-WHS-RT-001** | `original_bus_no` 必填；明细非空；源/目的位置合法 | IF-WHS-RT-04 / 05 |
| **BR-WHS-RT-002** | 来源进货须 **AGREE** 且目的药库与源药库一致（**BR-SR-008**） | IF-WHS-RT-04 / 12 / 13 |
| **BR-WHS-RT-003** | 明细须 ⊆ 进货行；数量 ≤ 可退余量（**BR-SR-009**） | IF-WHS-RT-04 / 05 / 08 |
| **BR-WHS-RT-004** | 源库可用量 ≥ 退货量；不足时提示先调拨回药库 | IF-WHS-RT-04 / 05 / 08 |
| **BR-WHS-RT-005** | **同意**后编排发放 + 源库扣减；防重复同意/重复发放 | IF-WHS-RT-08 |
| **BR-WHS-RT-006** | type **3** 财务审批门控 | IF-WHS-RT-08 |
| **BR-WHS-RT-007** | **禁止**自由选品；明细仅经进货单关联接口带入 | IF-WHS-RT-12/13 |
| **BR-WHS-RT-008** | **IF-WHS-RT-13** 行上 `traceNo`：有码批按源库本库剩余优先（须主档在院且剩余&gt;0，排除流转未扫导致的滞后净量）、空则全院在院剩余预填；截取个数按 `min(可退余量, 源库可用量)` 与每码拆零比 ceil；无码批空串；**禁止**回放进货行历史码串 | IF-WHS-RT-13 |
| **BR-WHS-RT-009** | 写侧行金额：**BR-PRICE-SRC-001** / **BR-MON-001** / **BR-MON-PKG-001**——建改单落库前从来源进货行回填**包装锚单价**，经领域 `SupplyRequestWritePricingSupport#applyReturnToSupplierCommand` 按退货**拆零量**重算 `price`/`total_price`（进货缺价时回落**批号进价** `LOT_NUMBER_COST`，**禁止**目录药典价）；**禁止**信任前端传价 | IF-WHS-RT-04 / 05 |
| **BR-WHS-RT-010** | **IF-WHS-RT-02** 侧栏卡片：列表行须富化 **`supplierName`**、**`creatorName`**（←`applicantId`）、**`approverName`**（←`approverId`，审中可空）、**`totalAmount`/`totalPrice`**（整单合计）；历史未落价单据读侧可按进货行总额比例回补展示（不写库） | IF-WHS-RT-02 |
| **BR-WHS-RT-011** | **IF-WHS-RT-03** 详情：每行须出 **`price`/`totalPrice`**（落库快照；历史缺价按进货比例只读回补）；整单双写 **`totalAmount`/`totalPrice`**；`productCatalog` 槽价锚定行落库价（**BR-PRICE-SRC-001**），禁止当前目录价覆盖；前端按计量带换算展示单价，行额权威以后端为准 | IF-WHS-RT-03 |

---

## 2. 页面与接口总览

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 |
|---------|---------|---------|-------|---------|
| IF-WHS-RT-01 | 进入页面 | 初始化药库、供应商下拉 | 读 | — |
| IF-WHS-RT-02 | 列表 | 按 `bus_no` 聚合分页退货单；行含 **`supplierName`/`creatorName`/`approverName`/`totalAmount`** | 读 | BR-WHS-RT-010 |
| IF-WHS-RT-03 | 详情 | 按 `busNo` 读退货单（含单头 **`invoiceNo`**）；行含 **`price`/`totalPrice`**，头含整单 **`totalAmount`/`totalPrice`** | 读 | BR-WHS-RT-011 |
| IF-WHS-RT-04 | 新建 | 创建退货草稿（可带 **`invoiceNo`**）；落库行金额见 **BR-WHS-RT-009** | 写 | BR-WHS-RT-001～004 / 009 |
| IF-WHS-RT-05 | 编辑 | 可编辑态全量改明细（可改 **`invoiceNo`**）；落库行金额见 **BR-WHS-RT-009** | 写 | BR-WHS-RT-001～004 / 009 |
| IF-WHS-RT-06 | 提交审批 | 待审 → 审中 | 写 | — |
| IF-WHS-RT-07 | 撤回 | 审中撤回 | 写 | — |
| IF-WHS-RT-08 | 同意 | 审批通过 + 扣库编排 | 写 | BR-WHS-RT-003～006 |
| IF-WHS-RT-09 | 驳回 | 驳回 | 写 | — |
| IF-WHS-RT-10 | 删除 | 逻辑删除 | 写 | — |
| IF-WHS-RT-11 | ~~选品~~ | **已删除**（原拒绝桩已下线）；须从进货拉明细 | — | BR-WHS-RT-007 |
| IF-WHS-RT-12 | 选进货单 | 分页可关联已同意进货单（仍有可退余量）；列表行含 **`supplierName`**、**`applyTime`** | 读 | BR-WHS-RT-002 |
| IF-WHS-RT-13 | 拉进货明细 | 按进货 `busNo` 返回可退行及余量/源库可用量；**追溯码按 BR-WHS-RT-008 预填当前剩余** | 读 | BR-WHS-RT-002 / 003 / 008 |

---

## 3. 缺口跟踪（Gap）

| 类型 | 描述 | 状态 |
|------|------|------|
| — | 本轮无领域写能力缺口 | — |

---

## 4. 接口编排说明书（摘要）

### IF-WHS-RT-02 列表

1. Gate：`sourceLocationId`（`0`=全部可见药库）、分页窗口、机构上下文。
2. Pre：按 `bus_no` 聚合分页；Mapper 写出头行 `applicantId`/`approverId`/`supplierId` 与整单 `totalAmount`/`totalPrice`。
3. Asm：批量富化 **`supplierName`/`creatorName`/`approverName`**（**BR-WHS-RT-010**）；合计为 0/空时按来源进货落库总额比例回补展示金额（不写库）。

### IF-WHS-RT-03 详情

1. Gate：`busNo` 非空；类型须为药库退供应商。
2. Pre：按 `bus_no` 装载供应请求行 + 批量产品读模型。
3. Asm：行映射 **`price`/`totalPrice`**（落库快照）；历史缺价按进货行比例只读回补（**不写库**）；`productCatalog` 槽价 `enrichWarehouseDetailCatalogFromPersistedSupplyLinePrice`；头双写整单 **`totalAmount`/`totalPrice`**（**BR-WHS-RT-011**）。

### IF-WHS-RT-04 / 05 建单/改单

1. Gate：机构/药库/必填字段；改单禁止 `rowVersion`。
2. Pre：校验进货 **AGREE**（**BR-SR-008**）；按行键校验 ⊆ 进货且 ≤ 可退余量（**BR-SR-009**）；校验源库可用量（**BR-WHS-RT-004**）；**从来源进货行按行键回填包装单价**到命令行（**BR-WHS-RT-009**）。
3. Cmd：`createReturnToSupplier`（改单同号软删重插；领域写价门禁按拆零量重算 `price`/`total_price`，缺价回落批号进价）；单头 **`invoiceNo`** 写入各行 **`invoice_no`**（可空；后端不拦）。
4. Asm：返回 `busNo`。

### IF-WHS-RT-08 同意

1. Gate：未重复同意；财务审批（type 3）。
2. Pre：再次 **BR-SR-009** + 源库可用量（防草稿至同意间库存被占或调拨）。
3. Cmd：`agreeRequest` → `createSupplyDelivery` → 源库 `decreaseQuantitiesMerged` → 追溯 OUT → 可选 3504（**`purc_invo_no`**：本单 `invoice_no` → 关联进货 → `0`）。
4. Asm：成功响应。

### IF-WHS-RT-13 拉进货明细

1. Gate：`busNo` 非空；进货 **AGREE**。
2. Pre：`getSupplyByBusNo` 进货行 + `getSupplyByOriginalBusNo` 已退占用 + 源库可用量 + 产品读模型。
3. Asm：返回 `originalBusNo`、头字段（含 **`supplierName`**、可回显进货 **`invoiceNo`**）、各行 `returnableQuantity` / `sourceAvailableQuantity` 及 **`productName`/`specification`/`manufacturer`/`unitCodeDictText`/`productCatalog`**（**行价取进货落库价**；槽价锚定该价；**`totalPrice` 按可退拆零量 / 进货拆零量 × 进货行总额**回算预览）。

---

## 5. 业务异常与用户提示

| 错误码 | 场景 |
|--------|------|
| **WHS_RT_001** | 参数/超可退余量/非进货行/源库不足（含调拨提示） |
| **WHS_RT_002** | 进货不可引用 |
| **WHS_RT_104** | 重复同意/扣库失败/源库无库存行 |

---

## 6. 事件语义

- 新建：`SupplyRequestCreatedEvent`（含 `originalBusNo`）
- 同意：`SupplyRequestAgreedEvent` → 应用层发放 + 扣库

---

## 7. 非功能性需求

- 写接口单事务；列表按 `bus_no` 聚合（**WHS-RT-SDD-R-001**）。

---

## 8. 测试要点

1. 进货 2 行 → 退 1 行 / 半量 → 可退余量递减；退完后进货单不出现在 IF-12。
2. 同一进货两次部分退 → 第二次仍成功直至余量 0。
3. 调拨出库后源库 0 → 建单/同意失败并提示调回。
4. 自由选品接口已删除；客户端不得调用已下线路径。
5. 重复 agree 拒绝；财务门控 type 3。
6. 建单后侧栏：**制单人**/供应商有名；**总金额**非 0（有进货落库价时）；审中 **审核人**可空。
7. 详情/草稿明细：每行可见**单价**与**总金额**；页脚**退货金额**与行合计一致；切换盒/片后单价随计量带换算、行额随数量刷新。

---

## 9. 关联文档

- **DM-WKF-006** §6.1.3（**v1.21** **BR-SR-019** 写价；**v1.19** `invoice_no`）、**BR-SR-008/009/019**
- **DM-ADM-011** **BR-PRICE-SRC-001**；金钱刻度 **BR-MON-001** / **BR-MON-PKG-001**
- **AS-INV-WHS-002** 进货单（发票号同源）
- **AS-INV-WHS-005-SDD** 详细设计（**v0.7**）
- **09-医保进销存** §4.5e（`purc_invo_no`）

### 9.1 变更历史

| 版本 | 日期 | 变更摘要 |
|------|------|----------|
| v0.6 | 2026-08-12 | **BR-WHS-RT-011**：IF-03 行/整单金额出参；写价缺价回落明确为批号进价；IF-13 可退行额按可退量比例预览 |
| v0.5 | 2026-08-11 | **BR-WHS-RT-008**：本库在库码须叠加主档在院剩余；截码上限 `min(可退, 源库可用)`，避免已发药码因流转未扫仍被预填 |
| v0.4 | 2026-07-26 | **BR-WHS-RT-009/010**：建改单写价落库；IF-02 侧栏富化制单人/审核人/供应商/总金额（历史未落价读侧按进货比例回补） |
| v0.3 | 2026-07-25 | **BR-WHS-RT-008**：退货追溯码=当前本库/全院剩余，禁止回放进货码串；对齐 **AS-CUS-TRC-001 v1.11** |
| v0.2 | 2026-07-19 | **`invoiceNo`**：详情/建改单可选；3504 解析序本单→进货→`0` |
| v0.1 | 2026-06-30 | 初稿 |