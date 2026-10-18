# 单据详情报表 · 应用服务设计（AS）

> **文档职责**：说明「药库 / 药房 / 科室药房 **单据详情报表**」场景的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：进销存 **三域只读报表页**（药库 / 药房 / 科室药房各一屏；后端 **统一** `inventory/documentdetail` 包，由请求 **`domainScope`** 区分域）— 用户按**单据类型**等筛选查看 **明细行平铺** 或 **按品规汇总**（同筛选对 `wkf_supply_request` 行按 `item_table+item_id` 聚合）。**默认固定 `AGREE`**；**期初 `*_OPENING` 例外**可含 **`APPROVAL`（审核中）** 供人工核对；不提供通用状态筛选；**无任何写操作**。
>
> **金额口径（硬约束）**：库房单据仅 **进价**（supply 落库 `price` / `totalPrice`）；**不展示、不计算售价**。
>
> **数量展示（硬约束）**：
> - **库内真源**：`wkf_supply_request.item_quantity`（及盘点/损益类 `total_quantity` 等）一律为**拆零（最小）单位**持久化量；**禁止**在报表读路径改写库值。
> - **操作单位数量**：须按该行 **`unit_code`（操作单位）** 自拆零量**换算为操作单位数量**（与单价同口径）；**禁止**直接把拆零数当作操作单位数展示。
> - **列表双数量列**（与操作单位并存）：**包装单位数量** = 拆零量 ÷ 包装倍数；**最小单位数量** = 拆零持久量；均带单位展示名。
> - **换算失败**（操作单位）：**整次查询/导出失败**（非降级）；异常须精确到 **单据号 + 物品**（名称/编码/itemId）+ **操作单位**（见 **EX-INV-DDC-204**）。
> - **不展示** `productCatalog` / 多单位槽 UI；换算仅在应用层内部使用产品单位槽解析 `ratioToSplit`。
> - **单价/金额**：`price`、`totalPrice` 等仍取 supply **落库值**（单价按当时 `unit_code` 计量），与**操作单位数量**同口径。
>
> **读侧架构**：复用 **`SupplyRequestQueryService#pageWarehouseDocumentDetail`**（查 `wkf_supply_request` 明细行）；应用层批量 enrich 头字段、产品展示、人员/库位/供应商名；**禁止**新建跨域读包。
>
> **编码**：AS-INV-RPT-002 · **版本**：v1.7 · **日期**：2026-09-02

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 三域各 **初始化**、**明细分页**、**筛选全量合计（含按品规汇总行）**、**Excel 导出**（明细全量或按品规汇总；非当前页） |
| **在外** | 任何 SupplyRequest 写路径；单据审批/编辑；权限 Gate（本报表**不做**管辖库位 Gate）；§八 未建应用包类型（调入申请、进货验收、耗材盘点等）；多汇总维（药房/科室等，本期仅品规） |
| **涉及域** | 读：**SupplyRequest**、**Location**、**Supplier**、**Practitioner**、**ProductDefinition**（药品/耗材定义）；租户上下文 |
| **跨域** | 仅应用层 enrich；读缺口补 **DM-WKF-006** §6.2 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **库管 / 药房 / 科室药房人员** | 选单据类型 →（可选）源/目的库位 → 审批时间（默认当月）→ 品名/批号模糊 → 查询 → **汇总 Tab（按品规）/ 明细 Tab** → 看合计 → 导出 |
| **审计 / 财务** | 只读查询与导出 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **单据详情报表** | 按 `type_enum` + 筛选条件，将 **`AGREE`** 单据的 **明细行** 平铺分页；**一行 = 一条 `wkf_supply_request` 行** |
| **单据类型** | `SupplyType#getCode()` 字符串；**可选**；未传时查本域 **§1.6 白名单全部**（见 **BR-INV-DDC-002**） |
| **建议默认类型** | init 下发供**前端**初始选中：药库 **`CABINET_INBOUND_RECEIPT`**；药房 **`PHARMACY_REQUISITION`**；科室 **`DEPT_REQUISITION`**；**后端查询不强制回落** |
| **状态** | 默认固定 **`AGREE`**；**期初例外**见 **BR-INV-DDC-003** |
| **源库位 / 目的库位** | 映射 `source_location_id` / `purpose_location_id`；**均可不选**（不选 = 该维度不过滤） |
| **审批时间** | 筛选 **`approval_time`** 闭区间；**默认当前自然月**（init 下发默认起止供前端回填） |
| **品名/编码关键字** | 独立模糊框；匹配产品 **名称 / 院内编码 / 拼音 / 五笔**（药品+耗材并集，应用层解析为 `item_id` 集合再过滤） |
| **批号关键字** | 独立模糊框；对 `lot_number` **LIKE** 模糊 |
| **展示数量** | **操作单位数量** = 拆零持久化量 ÷ **`ratioToSplit(unit_code)`**（见 **BR-INV-DDC-010**）；列表另给 **包装单位数量 / 最小单位数量**（见 **BR-INV-DDC-013**） |
| **盘点盘前账面** | 库内无独立 `book_quantity` 列；**盘前拆零账面 = `total_quantity − item_quantity`**（同意时冻结；报表**不读现库存** enrich） |
| **头字段摊平** | 同一 `bus_no` 各行的头信息（审批人、源/目的库位名等）**重复出现在每明细行**，便于报表扫读 |
| **按品规汇总** | 同筛选条件下对 supply **明细行**按 **`item_table + item_id`** 分桶；列：**维度（品名）、编码、包装数量、最小数量、进价合计**；**无单价、无售价**；**不另建领域汇总 Query** |
| **类型白名单** | 仅 **§1.6** 已落地应用包对应 `SupplyType`；**不含** §八 未建包 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | 三域各 IF-INV-DDC-{WHS\|PHA\|DEP}-01～04 |
| **非目标** | 按单号聚合分页再展开；单据详情页跳转写操作；管辖库位 Gate；§八 未建类型；`MEDICATION_SUMMARY_ORDER`；发药 Dispense 单据 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **SupplyRequest（WKF）** | 明细行分页真源 | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **Location（ADM）** | 源/目的库位展示名 | `DM-ADM-003-Location-位置.md` |
| **Supplier（CUS）** | 供应商展示名 | 供应商 DM |
| **Practitioner（ADM）** | 制单/审批/经手人展示名 | `DM-ADM-004-Practitioner-人员.md` |
| **MedicationDefinition / DeviceDefinition** | 品名/规格/厂家/编码；单位槽（仅内部换算） | 主数据 DM |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述（语义锚点） | 绑定 IF |
| --- | --- | --- |
| **BR-INV-DDC-001** | 已传 **`typeEnum`** 时须落在本域 **§1.6.1～1.6.3** 白名单内 | 02～04 |
| **BR-INV-DDC-002** | 请求未传 **`typeEnum`** 时按本域白名单 **全部类型** 查询（`typeEnums IN`）；**不**回落单类型；页面初始选中某一类型由**前端**用 init **`defaultTypeEnum`** 自行回填 | 01～04 |
| **BR-INV-DDC-003** | 非期初：固定 **`statusEnum`=`AGREE`** + 筛选 **`approval_time`**。**期初**（请求 **`typeEnum`** 为本域 `CABINET_OPENING`/`PHARMACY_OPENING`/`DEPT_OPENING`）：**`statusEnums IN (AGREE, APPROVAL)`**，时间窗改走 **`apply_time`**（审核中无审批时刻；供人工核对/导出）。草稿 **`PENDING_APPROVAL`** 仍不查。选「全部类型」时仍仅 **`AGREE`**（不含审核中期初）。其它单据类型不受影响 | 02～04 |
| **BR-INV-DDC-004** | **`sourceLocationId` / `purposeLocationId` / `supplierId` 可选**；未传则该维不过滤 | 02～04 |
| **BR-INV-DDC-005** | **`approvalTimeStart` / `approvalTimeEnd` 可选**；均未传时默认**当前自然月** `[月初 00:00:00, 月末 23:59:59]` | 02～04 |
| **BR-INV-DDC-006** | **`productKeyword`、`lotNumberKeyword` 可选**；空则不过滤 | 02～04 |
| **BR-INV-DDC-007** | 分页 **`total`** = **明细行**总数（非 `bus_no` 去重数） | 02 |
| **BR-INV-DDC-008** | **合计**与**导出**须与列表**同一套筛选条件**；统计范围为**全量命中行**（非当前页） | 03～04 |
| **BR-INV-DDC-009** | 返回字段 **≥** 对应类型 **`getXxxDetail` 详情页**（头+行+展示 enrich）；**不含** `productCatalog` | 02～04 |
| **BR-INV-DDC-010** | **操作单位数量**：拆零持久化量（及盘点/损益专用列之拆零源字段）按行 **`unit_code`** 经产品单位槽 **`ratioToSplit`** 换算；**ratio 缺失或 ≤0 → 失败**（**EX-INV-DDC-204**），消息含 **`busNo`、物品标识、`unitCode`** | 02～04 |
| **BR-INV-DDC-011** | 盘点/损益/退领等特殊列：在 **BR-INV-DDC-010** 下对 **账面/实盘/盈亏** 等拆零字段**同样换算**为操作单位数量后展示；**盘点盘前账面拆零源 = `total_quantity − item_quantity`**（非实时库存 enrich） | 02～04 |
| **BR-INV-DDC-012** | 导出列与当前屏显列**同构**（含动态隐藏列策略：前端按 `typeEnum` 控列，导出带完整并集列或当前 Tab 可见列 — **SDD** 与实现对齐 **BR-INV-DDC-012**，默认导出**并集稀疏列**） | 04 |
| **BR-INV-DDC-013** | 列表/导出另给 **包装单位数量**（拆零 ÷ 包装倍数）与 **最小单位数量**（拆零持久量）及单位名；与操作单位数量并存；单价仍按操作单位 | 02～04 |
| **BR-INV-DDC-014** | **按品规汇总**：同筛选全量明细行按 `item_table+item_id` 聚合；数量代数加总包装量/最小量；进价合计 = SUM(`totalPrice`)；**汇总不展示单价**（同桶多价无意义）；**禁止售价列**；导出 `presentationMode=SUMMARY` 与汇总 Tab 同构 | 01、03、04 |

#### 1.6.1 药库域 `SupplyType` 白名单（`domainScope=WAREHOUSE`）

| 建议默认（前端） | `SupplyType` code | 业务 |
| --- | --- | --- |
| ★ | `CABINET_INBOUND_RECEIPT` | 进货单 |
| | `CABINET_PURCHASE_ORDER` | 订货单 |
| | `CABINET_RETURN_TO_SUPPLIER` | 退货单 |
| | `CABINET_STOCK_OUT` | 出库单 |
| | `CABINET_RETURN_STOCK_IN` | 退库入库壳单 |
| | `CABINET_GAIN_LOSS` | 损益单 |
| | `CABINET_STOCKTAKE` | 盘点单 |
| | `CABINET_TRANSFER_IN` | 调入壳单 |
| | `CABINET_TRANSFER_OUT` | 调出单 |
| | `CABINET_OPENING` | 药库期初单（仅 `purpose_*`；筛库房用「目的库位」） |

#### 1.6.2 药房域白名单（`domainScope=PHARMACY`）

| 建议默认（前端） | `SupplyType` code | 业务 |
| --- | --- | --- |
| ★ | `PHARMACY_REQUISITION` | 请领单 |
| | `PHARMACY_STOCK_IN` | 入库壳单 |
| | `PHARMACY_RETURN_TO_WAREHOUSE` | 退库单 |
| | `PHARMACY_GAIN_LOSS` | 损益单 |
| | `PHARMACY_STOCKTAKE` | 盘点单 |
| | `PHARMACY_TRANSFER_IN` | 调入壳单 |
| | `PHARMACY_TRANSFER_OUT` | 调出单 |
| | `PHARMACY_OPENING` | 药房期初单（仅 `purpose_*`；筛库房用「目的库位」） |

#### 1.6.3 科室药房域白名单（`domainScope=DEPARTMENT`）

| 建议默认（前端） | `SupplyType` code | 业务 |
| --- | --- | --- |
| ★ | `DEPT_REQUISITION` | 请领单 |
| | `DEPT_STOCK_IN` | 入库壳单 |
| | `DEPT_RETURN` | 退库单 |
| | `DEPT_GAIN_LOSS` | 损益单 |
| | `DEPT_STOCKTAKE` | 盘点单 |
| | `DEPT_TRANSFER_IN` | 调入壳单 |
| | `DEPT_TRANSFER_OUT` | 调出单 |
| | `DEPT_ISSUE` | 领用单 |
| | `DEPT_ISSUE_RETURN` | 退领单 |
| | `DEPT_OPENING` | 科室药房期初单（仅 `purpose_*`；筛库房用「目的库位」） |

### 1.7 读侧复用与拼装（应用编排）

| 读需求 | 领域读能力 | 应用层拼装 |
| --- | --- | --- |
| 明细行分页 | **`SupplyRequestQueryService#pageWarehouseDocumentDetail`** | 入参含审批时间窗、批号 LIKE、`productKeyword`→`resolved*DefinitionIdsForKeyword`（**InventoryCatalogFilterSupport**） |
| 品名/编码关键字 | **MedicationDefinition** / **DeviceDefinition** `listIdsByInventoryDetailKeyword` + **InventoryCatalogFilterSupport#enrichDocumentDetailKeywordDefinitionIds** | 解析后写入 Query；**无命中 → 空页**（非报错） |
| 产品展示 + 单位槽 | **`ProductDefinitionBatchLoader#loadAsMap`** | enrich 名称/规格/厂家/编码；**内部** `buildInboundSelectionBands` + **`convertSplitQuantityToDisplayUnit`** |
| 库位/供应商/人员 | **LocationQueryService**、**SupplierQueryService**、**PractitionerQueryService** | 批量 ID → 展示名 |
| 合计 | 同筛选条件下 **COUNT/SUM** 或分页拉全量聚合 | **`totalPrice` 之和**；**`itemQuantity`（展示量）之和**（跨单位仅作参考）；并返回 **按品规汇总行** |

---

## 2. 页面与接口总览（契约概览）

> 三域 **IF 语义同构**，由请求 **`domainScope`**（`WAREHOUSE` / `PHARMACY` / `DEPARTMENT`）区分白名单；HTTP 统一 **`/api/app/inventoryDocumentDetail/{methodName}`**。下表以 **药库域** 为例；药房、科室药房见 **§2.1**。

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则 | 涉及域 |
| --- | --- | --- | --- | --- | --- |
| **IF-INV-DDC-WHS-01** | 进入药库单据详情报表 | 单据类型下拉、源/目的库位下拉、**默认审批时间（当月）**、**汇总维度（本期仅按品规）** | 读 | BR-INV-DDC-002、014 | Location、SupplyType |
| **IF-INV-DDC-WHS-02** | 查询、翻页 | 明细分页平铺 | 读 | BR-INV-DDC-001～011、013 | §1.5 |
| **IF-INV-DDC-WHS-03** | 查看合计 / 汇总 Tab | 筛选全量 **金额/数量** 合计 + **按品规汇总行** | 读 | BR-INV-DDC-008、014 | 同 02 |
| **IF-INV-DDC-WHS-04** | 导出 Excel | 筛选全量明细 **或** 按品规汇总导出（`presentationMode`） | 读 | BR-INV-DDC-008、012、014 | 同 02 |

#### 2.1 三域 IF 对照

| 域 | 01 初始化 | 02 分页 | 03 合计 | 04 导出 |
| --- | --- | --- | --- | --- |
| **药库** | IF-INV-DDC-WHS-01～04 | 同上 | 同上 | 同上 |
| **药房** | IF-INV-DDC-PHA-01～04 | 同构 | 同构 | 同构 |
| **科室药房** | IF-INV-DDC-DEP-01～04 | 同构 | 同构 | 同构 |

---

## 3. 缺口跟踪（Gap）

**当前无未关闭 Gap**（领域读侧、错误码与应用层 **`InventoryDocumentDetailAppService`** 已于 **2026-07-03** 落地）。

| Gap ID | 关闭说明 | 落地锚点 |
| --- | --- | --- |
| **GAP-INV-DDC-001** | `WarehouseDocumentDetailPageQuery` / Predicate 已扩展审批时间、批号 LIKE、品名关键字 resolved 字段 | **DM-WKF-006** §6.2.13；`WarehouseDocumentDetailPagePredicatesupport |
| **GAP-INV-DDC-002** | 复用 `listIdsByInventoryDetailKeyword` + **`InventoryCatalogFilterSupport#enrichDocumentDetailKeywordDefinitionIds`** | `health-module-support |
| **GAP-INV-DDC-003** | 读模型已映射 **`totalQuantity` / `profitLossPrice`** | `WarehouseDocumentDetailQueryModel` |

**以下不算 Gap**：

- 头字段摊平、操作单位换算、enrich：**应用层** `inventory/documentdetail` 包拼装。
- 三域差异：仅 **`domainScope`** + 白名单 / init **建议**默认类型（前端回填）；**单一 AppService** 同构编排。

---

## 4. 接口编排说明书（业务步骤）

### IF-INV-DDC-{域}-01 页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入本域单据详情报表页 |
| **主路径** | ① 返回本域 **§1.6** 类型 **`OptionDto`** 列表 ② 返回 **`defaultTypeEnum`**（建议前端初始选中，**非**查询强制）③ 返回 **源库位 / 目的库位** 下拉（租户内活跃 **药库+药房+科室药房** 并集，两下拉**同源候选**）④ 返回 **供应商** 下拉（租户内全部有效供应商）⑤ 返回 **`defaultApprovalTimeStart` / `defaultApprovalTimeEnd`**（当前自然月）⑥ 返回 **`summaryDimensionOptions`**（本期仅 **按品规 / ITEM**） |
| **失败分支** | EX-INV-DDC-201 |

### IF-INV-DDC-{域}-02 明细分页查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点击查询或翻页 |
| **主路径** | ① 解析 **`domainScope`** ② 解析 **`typeEnum`**：已传 → 白名单校验 **BR-INV-DDC-001**；未传 → 本域白名单全部 **BR-INV-DDC-002** ③ 解析时间窗（缺省 **BR-INV-DDC-005**）；期初走申请时间见 **BR-INV-DDC-003** ④ 品名关键字 → resolved id（双空 → 空页）⑤ **`pageWarehouseDocumentDetail`**：状态（默认 `AGREE`；期初显式类型 `AGREE`+`APPROVAL`）+ 类型 + 源/目的库位 + 供应商 + 时间窗 + 批号 LIKE + resolved ⑥ 批量 enrich ⑦ **逐行 BR-INV-DDC-010/011** 换算 ⑧ 摊平头字段 ⑨ 分页返回 |
| **关键分支** | 换算失败 → **整请求失败** **EX-INV-DDC-204**（精确到单号+物品） |
| **失败分支** | EX-INV-DDC-202、203、204、207 |

### IF-INV-DDC-{域}-03 筛选全量合计与按品规汇总

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 与 02 **相同筛选条件**（可不含分页，或 pageSize=导出步长循环） |
| **主路径** | 复用 02 查询链至全量命中行 → SUM **`totalPrice`**、SUM **展示 `itemQuantity`** → 按品规分桶返回 **`records`**（**BR-INV-DDC-014**） |
| **失败分支** | EX-INV-DDC-204、206 |

### IF-INV-DDC-{域}-04 导出 Excel

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户点击导出 |
| **主路径** | 同 03 全量拉取 + 同 02 换算/enrich → 默认明细 Excel（工作表名 **「单据详情」**）；**`presentationMode=SUMMARY`** 时导出按品规汇总行（工作表名 **「单据详情汇总」**） |
| **关键分支** | 超行上限 **SDD** 约定 `truncated` 或失败 **EX-INV-DDC-205** |
| **失败分支** | EX-INV-DDC-204、205 |

---

## 5. 业务异常与用户提示

| 异常 ID | 场景 | 用户提示语义（须含占位） | 可重试 |
| --- | --- | --- | --- |
| **EX-INV-DDC-201** | 初始化失败 | 页面加载失败 | 是 |
| **EX-INV-DDC-202** | 业务域或单据类型非法 | `domainScope` 缺失/无效，或 `typeEnum` 不在本域白名单 | 是 |
| **EX-INV-DDC-203** | 分页查询失败 | 查询失败，请稍后重试 | 是 |
| **EX-INV-DDC-204** | **操作单位换算失败** | **单据 {busNo} 中物品「{productName}」（编码 {productBusNo}）无法按操作单位「{unitCode}」换算数量，请检查产品单位维护** | 否（需主数据修复） |
| **EX-INV-DDC-205** | 导出失败 | 导出失败，请稍后重试 | 视原因 |
| **EX-INV-DDC-206** | 合计失败 | 合计计算失败，请稍后重试 | 是 |
| **EX-INV-DDC-207** | 审批时间区间非法 | 审批时间范围无效 | 是 |

> **EX-INV-DDC-204**：须在服务端日志中额外记录 **`supplyRequestLineId`（`wkf_supply_request.id`）、`itemId`、`itemTable`、拆零量**，便于运维定位。

---

## 6. 事件语义与消费方

| 事件 | 触发点 | 消费方 | 幂等 |
| --- | --- | --- | --- |
| **（只读无落库事件）** | IF-01～04 | — | 查询幂等 |

---

## 7. 非功能性需求

| 维度 | 要求 |
| --- | --- |
| **性能** | 分页 + 批量 enrich；导出分批拉取（步长 **SDD**）；禁止 N+1 |
| **一致性** | 只读；展示为查询时刻快照 |
| **安全** | **租户隔离**；**不做**管辖库位 Gate（按产品裁决） |
| **精度** | 数量展示 **numeric(18,2)** 四舍五入策略与 **`ChargeItemAmountScaleConstants`** 对齐；金额 **numeric(18,4)** |
| **导出** | 行数上限 **SDD**（建议 **100000**，与日结/export 同档） |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **分页** | 同一 `busNo` 多行均出现；`total` 为行数 |
| **筛选** | 非期初仅 `AGREE`；期初显式选类型时含 `APPROVAL`（时间窗=申请时间）；审批时间默认月；源/目的库位、供应商独立可选；**未传 typeEnum 查本域全部类型（仍仅 AGREE）** |
| **换算** | 包装单位录入行：展示数量 = 拆零量 ÷ 包装比；**ratio 缺失报错 204 且带单号+品名** |
| **字段** | 审批人/审批时间/源目的库位名/供应商等不缺失 |
| **合计/导出** | 与列表同条件；明细导出条数 = 筛选全量；汇总导出条数 = 品规桶数 |
| **按品规汇总** | 同筛选；无售价；同桶单价不一致单价空；筛选变更后汇总与明细一致 |
| **三域** | 白名单分别正确；init **`defaultTypeEnum`** 仅为建议；§八 类型不在 init 下拉 |
| **期初单** | 药库/药房/科室分别仅见本域 `*_OPENING`；选「目的库位」可筛；审核中+已同意可同条件导出；草稿不出 |
| **盘点/损益** | 账面/实盘/盈亏列亦按操作单位展示 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 类型 | 路径 |
| --- | --- |
| **功能清单** | `InventoryAppService/INV-inventory-功能清单与改造状态.md` |
| **DM** | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **参考 AS** | 各域单据 AS（进货/请领/盘点等 **IF-*-03 详情**） |
| **SDD** | `AppService-SDD/AS-INV-RPT-002-单据详情报表-SDD.md` |
| **实现** | `zxplat/.../inventory/documentdetail`（**`InventoryDocumentDetailAppService`** + **`domainScope`**） |

### 9.2 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| **v1.0** | **2026-07-03** | 初稿：三域单据详情报表；明细行平铺；操作单位数量换算；换算失败精确报错；全量合计与导出；无权限 Gate |
| **v1.1** | **2026-07-03** | 关闭 GAP-INV-DDC-001～003；补 **BR-INV-DDC-011** 盘点盘前 = `total_quantity − item_quantity` |
| **v1.3** | **2026-07-15** | **BR-INV-DDC-002** 改口径：未传 `typeEnum` 查本域白名单全部；默认类型仅前端用 init `defaultTypeEnum` 回填 |
| **v1.2** | **2026-07-03** | 应用层落地：单包 **`inventory/documentdetail`**、单 **`InventoryDocumentDetailAppService`**；三域由 **`domainScope`** 区分；HTTP **`inventoryDocumentDetail`** |
| **v1.4** | **2026-07-31** | §1.6 白名单纳入三房期初：`CABINET_OPENING` / `PHARMACY_OPENING` / `DEPT_OPENING`；与列表同条件可查可导出（期初筛库房用目的库位） |
| **v1.5** | **2026-07-31** | **BR-INV-DDC-003** 期初例外：显式选 `*_OPENING` 时含 **`APPROVAL`**，时间窗走 **`apply_time`**；其它类型仍仅 `AGREE` |
| **v1.6** | **2026-08-28** | **BR-INV-DDC-013**：列表/导出并列包装单位数量与最小单位数量；操作单位数量保留与单价同口径 |
| **v1.7** | **2026-09-02** | **BR-INV-DDC-014**：汇总 Tab / IF-03 返回按品规汇总行；导出支持 `presentationMode=SUMMARY`；仅进价无售价；聚合自 supply 明细 |
