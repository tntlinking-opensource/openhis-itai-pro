# 药库盘库单 · 应用服务设计（AS）

> **文档职责**：说明「药库盘库单」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：药库业务线 — 药库人员维护 **`CABINET_STOCKTAKE`**（与 **`SupplyType#getCode()`** 一致）盘点申请与审批闭环：列表与详情、新建/编辑/删除、提交/撤回审批、审批同意/驳回。**盘点单为执行单据（免申请）**：审批**同意**后由应用层对**非零盈亏行**编排 **`createStocktakingDelivery`（盘点差异发放）** 与 **`InventoryItem` 按盈亏数量加减**（**不得**用实盘数量覆盖账面）。**单端点库房**：业务库房仅以 **`purpose_location_id` / `purpose_type_enum`** 为真源；落库时 `source_*` 可与 purpose **同值镜像**（满足库表非空，对外契约不暴露第二库房）。
>
> **与入出库对偶**：页面骨架、审批状态机、列表/删改提交流程与 **AS-INV-WHS-003**（药库出库）、**AS-INV-WHS-002**（药库进货）等**库房单据通用细则**一致；**差异**见 **§1.3**（行字段语义、init 仅药库列表、选品按单库房、同意入账用盈亏）。
>
> **覆盖声明**：本稿以当期业务口述与已落地的 **DM-WKF-006 §6.1.7**、领域 **`createStocktake`** 为基线；正式定稿前须与 **`openhis-pro-ui`** 药库盘点/盘库页可执行动作对照片修订 **§1.4、§2、§4、§5**。
>
> **编码**：AS-INV-WHS-007 · **版本**：v0.2 · **日期**：2026-05-28

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 创建 **`CABINET_STOCKTAKE`** 草稿；维护批号级明细（含账面展示、实盘、盈亏数量/金额）；审批至 **同意/驳回/撤回**；可编辑态改单与删单；**同意**后编排 **盘点发放 + 按行盈亏调整库存** |
| **在外** | 药房 **`PHARMACY_STOCKTAKE`**（见 **AS-INV-PHA-006**）；科室盘点；财务月结过账细则；盘点期间全库冻结出库（若产品单列则入 **§1.4 非目标**） |
| **涉及域** | 写侧：**SupplyRequest**、**SupplyDelivery**、**InventoryItem**；主数据：**Location**、**人员**、**SupplyStatus**、入库项目类型（药品/耗材定义表两档）；读侧：**SupplyRequest** 列表真源、**InventoryItem**/**库存QueryService**（选品主表）、**ProductDefinition**（行展示装配） |
| **跨域** | **AGREE** 后：仅 **`item_quantity`（盈亏）≠ 0** 之行产生发放并 **`increaseQuantity` / `decreaseQuantity`**（盘亏允许落负，以 **DM-WKF-008** 为准） |
| **编排权威** | 以本文 **§2、§4** 为准 |
| **前置（摘要）** | 盘点库房 **`purpose_location_id`** 合法且 ∈ 当前用户可选药库集合 |
| **后置（摘要）** | **同意**后库存按盈亏可查；**驳回/撤回** 不产生发放与库存变动 |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **药库制单员** | 查询盘点列表 → 选择盘点药库 → 按批选品录入实盘 → 保存 → 提交审批 →（必要时）撤回 |
| **药库主管/审批人** | 同意 / 驳回 |
| **管理员** | 待审核态修改或逻辑删除 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **药库盘点单** | `type_enum`=`**CABINET_STOCKTAKE**`（**`SupplyType#getCode()`**），**执行单据** |
| **`bus_no` 前缀** | 新建且首行未显式传入 **`busNo`** 时：**`SupplyType.busNoSeqKey`** = **`CST`**，按日流水与其它类型分桶；无法匹配白名单时回退 **`SR`** |
| **盘点库房（单端点）** | 业务真源：**`purpose_location_id` / `purpose_type_enum`**；列表筛选、选品 **`purposeLocationId`**、详情展示「盘点库房」均对齐 purpose；**禁止**以 `source_*` 表达第二业务库房 |
| **行字段（盘点专用）** | **`item_quantity`**：**盈亏数量**（拆零；盈为正、亏为负、可无差异为 **0**）；**`total_quantity`**：**实盘数量**（拆零，≥0）；**`total_price`**：**实盘金额**；**`profit_loss_price`**：**盈亏金额**（盈正亏负）；须**保存与详情回显** |
| **账面数量（展示）** | 选品/录入时展示之「账面/可用量」来自 **`purposeLocationId`** 下 **`InventoryItem`** 当前量（与 **IF-WHS-ST-11** 一致）；**不**写入 `wkf_supply_request` 替代 `item_quantity` |
| **同意入账数量** | 库存变动量 = 行 **`item_quantity`（盈亏）** 做**加法**；**禁止**将库存直接设为 **`total_quantity`（实盘）** |
| **选品主表** | 与 **AS-INV-WHS-003** **IF-WHS-SO-11** 同构：**库存批号行** + **ProductDefinition**；请求须带 **`purposeLocationId`**（= 盘点药库）；每行 **效期**、**可用量/账面量**；**选品 UI 须展示 `lotNumber`（批号）**（批号级展开，非订货/进货产品定义展开） |
| **初始化下拉** | **IF-WHS-ST-01** **仅**返回**药库**位置列表（**`warehouseLocationOptions`** 或等价，**OptionDto**）；**不**返回药房目的列表（与出库 init 之分） |
| **时间与操作者** | 与 **AS-INV-WHS-003** 一致：**`occurrence_time`** 制单时间（前端传）；**`apply_time`** 提交时写入；**`approval_time`** 同意/驳回时写入 |
| **枚举口径** | 凡 **`SupplyType` / `SupplyStatus`** 及库表枚举列，一律 **`getCode()`** 字符串 |
| **DM 路径** | 下文聚合路径均指 `docs/领域/DomainModel/` 下对应文件 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | 药库盘点列表、详情、新建、编辑、提交/撤回、同意/驳回、删除；**单药库** init；批号级选品；行上实盘/盈亏数量与金额持久化与回显；**同意**按盈亏入账 |
| **待评审对齐** | 是否允许保存「盈亏为 0」的明细行仅作记录；列表是否展示盈亏合计/实盘合计；与旧版 **Inventory** 菜单路由合并策略 |
| **非目标** | **批号维定价**补录（**`LOT_NUMBER_COST` / `LOT_NUMBER_PRICE`**）；全盘/抽盘模式差异、盘点锁库暂停它库出库（SRS 级能力另立项） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **SupplyRequest（WKF）** | **`createStocktake`** 建单；审批；逻辑删除 | `DM-WKF-006-SupplyRequest-供应请求.md` |
| **SupplyDelivery（WKF）** | **`createStocktakingDelivery`**（仅非零盈亏行） | `DM-WKF-005-SupplyDelivery-供应发放.md` |
| **InventoryItem（WKF）** | 按盈亏 **`increaseQuantity` / `decreaseQuantity`**（`allowNegative=true` 于盘亏路径） | `DM-WKF-008-InventoryItem-库存项.md` |
| **Location（ADM）** | 药库位置校验与 init 下拉 | `DM-ADM-003-Location-位置.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述（语义锚点） | 绑定 IF |
| --- | --- | --- |
| **BR-WHS-ST-001** | 最小建单必备字段；`type_enum` 须为 **`CABINET_STOCKTAKE`** | IF-WHS-ST-04 |
| **BR-WHS-ST-002** | **`purpose_location_id`** 须为合法药库且 ∈ **IF-WHS-ST-01** 门控集合 —— 对齐 **BR-SR-006** 类位置门控 | IF-WHS-ST-04 / IF-WHS-ST-05 / IF-WHS-ST-11 |
| **BR-WHS-ST-003** | 同一 `bus_no` 状态一致 —— **BR-SR-002** | IF-WHS-ST-05～IF-WHS-ST-10 |
| **BR-WHS-ST-004** | 仅可编辑态修改 —— **BR-SR-003**、**BR-SR-014** | IF-WHS-ST-05 |
| **BR-WHS-ST-005** | 提交审批 —— **BR-SR-004** | IF-WHS-ST-06 |
| **BR-WHS-ST-006** | 同意/驳回追溯 —— **BR-SR-005** | IF-WHS-ST-08 / IF-WHS-ST-09 |
| **BR-WHS-ST-007** | 删除防下游引用 —— **BR-SR-010** | IF-WHS-ST-10 |
| **BR-WHS-ST-008** | **同意**防重复编排（本单已存在盘点发放事实则不得再次同意入账） | IF-WHS-ST-08 |
| **BR-WHS-ST-009** | 保存行：**`item_quantity`** 为盈亏（可负可零）；**`total_quantity`** 若填则 ≥0；**`profit_loss_price`** / **`total_price`** 与 UI 口径一致并回显 | IF-WHS-ST-04 / IF-WHS-ST-05 / IF-WHS-ST-03 |
| **BR-WHS-ST-010** | **同意**路径：仅 **`item_quantity` ≠ 0** 之行调用 **`createStocktakingDelivery`** 并调整库存；**零盈亏行跳过**；**整单全部为零仍同意成功**（不落发放、不调库存） | IF-WHS-ST-08 |
| **BR-WHS-ST-011** | 选品按 **`purposeLocationId`** 汇总库存可用量/账面量；与 **DM-WKF-008** 可用量口径一致 | IF-WHS-ST-11 |
| **BR-WHS-ST-012** | 盈亏数量建议由 UI 按「实盘 − 账面」计算后传入；服务端保存**以请求体为准**（产品可另加一致性校验，细则 **SDD**） | IF-WHS-ST-04 / IF-WHS-ST-05 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-WHS-ST-01 | 进入盘点页面 | 初始化：**仅药库**位置列表（**OptionDto**）；入库项目类型两档；**SupplyStatus**；人员下拉策略同 **IF-WHS-SO-01**（全量 + 岗位收窄） | 读 | — | — | Location / SupplyStatus | — |
| IF-WHS-ST-02 | 查询/刷新列表 | 分页 **`CABINET_STOCKTAKE`**；**`bus_no` 合并一行**；展示盘点库房、状态、制单/审批信息；可含盈亏/实盘汇总（展示层） | 读 | — | — | SupplyRequest | **`wkf_supply_request`** QueryService 列表查询 |
| IF-WHS-ST-03 | 查看详情 | 按 **`bus_no`** 加载头 + 批号级明细；须回显 **账面（展示）**、**实盘数量/金额**、**盈亏数量/金额**、批号、效期、单位、备注；多单位目录提示与 **IF-WHS-SO-03** **同构**（**`PURCHASE`** 参考价） | 读 | **BR-WHS-ST-009** | — | SupplyRequest / InventoryItem / ProductDefinition | 写模型一致口径 + 产品QueryService |
| IF-WHS-ST-04 | 新建盘点单 | **`createStocktake`** 草稿；写入行字段语义（**§1.3**）；**`PENDING_APPROVAL`** | 写 | BR-WHS-ST-001～002 / **009** / **012** | 新 **`bus_no`（`CST`）**；待审核 | SupplyRequest / Location | 列表/详情刷新 |
| IF-WHS-ST-05 | 编辑盘点单 | 可编辑态全量替换明细（软删旧行插新行，与库房单据通用细则一致） | 写 | BR-WHS-ST-004 / **009** | 明细变更 | SupplyRequest | 同上 |
| IF-WHS-ST-06 | 提交审批 | 待审核 → 审核中 | 写 | BR-WHS-ST-005 | `PENDING_APPROVAL` → `APPROVAL` | SupplyRequest | 待办、列表 |
| IF-WHS-ST-07 | 撤回审批 | 审核中撤回 | 写 | — | `APPROVAL` → `WITHDRAW` / `PENDING_APPROVAL`（以 **DM** 为准） | SupplyRequest | 同上 |
| IF-WHS-ST-08 | 审批同意 | **同意**；盘点发放 + 库存按盈亏加减 | 写 | BR-WHS-ST-006 / **008** / **010** | **`AGREE`**；非零盈亏行入账 | SupplyRequest → SupplyDelivery / InventoryItem | 列表/详情/库存 |
| IF-WHS-ST-09 | 审批驳回 | 驳回终态 | 写 | BR-WHS-ST-006 | `REJECT` | SupplyRequest | 同上 |
| IF-WHS-ST-10 | 删除单据 | 逻辑删除 | 写 | BR-WHS-ST-007 | 逻辑删除 | SupplyRequest | QueryService隐藏 |
| IF-WHS-ST-11 | 盘点选品检索 | 分页：库存批号行 + **ProductDefinition**；**`purposeLocationId`** 必填；返回账面/可用量、效期、**`unitBands`**（与 **IF-WHS-SO-11** 同构）；**选品 UI 须展示 `lotNumber`（批号）**（**AS-INV-WHS-003 §1.3**）；列表行价可按批号 **`LOT_NUMBER_COST`**（展示辅助，**不**补录定价表） | 读 | **BR-WHS-ST-011** | — | InventoryItem / ProductDefinition | 库存 + 产品QueryService |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| — | — | — | **2026-05-22** 已关闭盘点领域 Gap：`profit_loss_price` 列、`createStocktake` purpose 口径、盈亏 `item_quantity` 校验、**`createStocktake` 同号修订**（`assertStocktakeLinesRevisable` + 软删重插）、**DM §6.1.7** 与代码对齐 | — | — | — | **已关闭** |

> **说明**：应用层 **AppService / Controller / DTO** 未实现**不属于** Gap，由 **SDD → 应用层成码** 解决。

---

## 4. 接口编排说明书（业务步骤）

### IF-WHS-ST-01 进入盘点页面初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入药库盘点菜单 |
| **主路径步骤** | 加载**药库**位置 **OptionDto**（**`listAllActiveWarehouses`** + **`CurrentManagedLocationIdsResolver`**，同 **IF-WHS-SO-01**）；入库项目类型；**SupplyStatus**；人员经 **`PractitionerRoleScopeLoader#listActivePractitionersForRoleInOrganization`** |
| **关键分支** | **不**加载药房位置列表 |
| **失败分支** | EX-WHS-ST-101 |
| **查询侧说明** | 无 |

### IF-WHS-ST-02 盘点单列表查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 筛选、翻页、刷新 |
| **主路径步骤** | 固定 **`type_enum`=`CABINET_STOCKTAKE`**；按 **`purpose_location_id`**（盘点药库）、日期、状态、关键字筛选；**`bus_no` 聚合一行** |
| **失败分支** | EX-WHS-ST-102 |
| **查询侧说明** | 列表展示 |

### IF-WHS-ST-03 盘点单详情

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 从列表进入 |
| **主路径步骤** | **`getSupplyByBusNo`**；装配头（盘点库房、状态、审批信息）与明细（**盈亏/实盘/金额**、批号、效期、单位、备注）；可选附带当前账面量只读展示（实时查 **InventoryItem** 或保存时快照，以 **SDD** 固定） |
| **失败分支** | EX-WHS-ST-103 |
| **查询侧说明** | 详情展示 |

### IF-WHS-ST-04 新建盘点单

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 已选盘点药库 |
| **主路径步骤** | 调用 **`createStocktake`**；每行写入 **§1.3** 字段；**`purpose_*`** 为盘点库房；初态 **待审核** |
| **关键分支** | 明细为空由产品/领域决定是否拒绝 |
| **失败分支** | EX-WHS-ST-001 / EX-WHS-ST-002 |
| **查询侧说明** | **SupplyRequestCreatedEvent** |

### IF-WHS-ST-05 编辑盘点单

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 可编辑态 |
| **主路径步骤** | **`createStocktake`** 首行带既有 **`busNo`**：领域 **`assertStocktakeLinesRevisable`** 后软删旧行并重插全量明细；字段语义同 **IF-WHS-ST-04** |
| **失败分支** | EX-WHS-ST-004 / EX-WHS-ST-105 |
| **查询侧说明** | 刷新详情 |

### IF-WHS-ST-06～IF-WHS-ST-07、IF-WHS-ST-09～IF-WHS-ST-10

与 **AS-INV-WHS-003** **IF-WHS-SO-06 / 07 / 09 / 10** **同构**（提交、撤回、驳回、删除）；`type_enum` 门控为 **`CABINET_STOCKTAKE`**。

### IF-WHS-ST-08 审批同意

| 维度 | 说明 |
| --- | --- |
| **触发前提** | **审核中**；**BR-WHS-ST-008** 防重 |
| **主路径步骤** | ① 本单 **同意**（**`AGREE`**），记录审批人/时间。② **同一业务事务**内：遍历明细，对 **`item_quantity`（盈亏）≠ 0** 之行：**`createStocktakingDelivery`**（`quantity` = 盈亏，正盈负亏）+ **`InventoryItem` 在 `purpose_location_id` 按批号做加减**（**加法语义**，**不**写实盘覆盖）。③ **零盈亏行**跳过发放与库存；**若整单全部为零**则不调用发放落库，同意仍成功。④ 投影/列表可查库存变更结果 |
| **关键分支** | 任一步失败：对外未生效（**EX-WHS-ST-104**） |
| **查询侧说明** | 库存QueryService、单据状态更新 |

### IF-WHS-ST-11 盘点选品检索

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 新建/编辑打开选品；**`purposeLocationId`** ∈ **IF-WHS-ST-01** 药库集合 |
| **主路径步骤** | 与 **IF-WHS-SO-11** 相同查询与装配（含可选 **`positiveQuantityOnly`**），仅将库存过滤键由 **`sourceLocationId`** 换为 **`purposeLocationId`**（同一药库 id）；返回批号、效期、**账面/可用量**、**`unitBands`** |
| **失败分支** | EX-WHS-ST-102 |
| **查询侧说明** | 无 |

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-WHS-ST-001 | 建单校验失败 | 补全盘点库房与明细 | 是 |
| EX-WHS-ST-002 | 盘点库房非法 | 重新选择药库 | 是 |
| EX-WHS-ST-003 | 状态不允许 | 刷新后重试 | 是 |
| EX-WHS-ST-004 | 禁止编辑 | 说明单据状态 | 否 |
| EX-WHS-ST-005 | 删除被拒绝 | 状态或下游引用 | 否 |
| EX-WHS-ST-101 | 主数据加载失败 | 稍后重试 | 是 |
| EX-WHS-ST-102 | 列表/选品失败 | 缩小条件 | 是 |
| EX-WHS-ST-103 | 详情不可用 | 返回列表 | 视情况 |
| EX-WHS-ST-104 | 同意入账失败 | 联系管理员 | 视原因 |
| EX-WHS-ST-105 | 并发冲突 | 刷新后重试 | 是 |

---

## 6. 事件语义与消费方

| 事件（以 **DM-WKF-006** §5 为准） | 触发相位 | 典型消费方 |
| --- | --- | --- |
| `SupplyRequestCreatedEvent` | IF-WHS-ST-04 | QueryService 列表查询 |
| `SupplyRequestSubmittedEvent` | IF-WHS-ST-06 | 待办 |
| `SupplyRequestWithdrawnEvent` | IF-WHS-ST-07 | 列表 |
| `SupplyRequestAgreedEvent` | IF-WHS-ST-08 | 列表；**盘点发放与库存编排** |
| `SupplyRequestRejectedEvent` | IF-WHS-ST-09 | 列表 |
| `SupplyRequestDeletedEvent` | IF-WHS-ST-10 | QueryService隐藏 |
| SupplyDelivery / Inventory 侧事件 | IF-WHS-ST-08 下游 | 库存QueryService |

---

## 7. 非功能性需求

| 类别 | 要求 |
| --- | --- |
| **性能** | 批号级选品分页可接受；大批量盘点同意可分批落库（**SDD** 定） |
| **幂等** | 同意/提交重复请求幂等 |
| **并发** | 同单乐观锁 |
| **一致性** | **同意**与库存变更对外一致；盈亏为 0 行不产生库存副作用 |
| **审计** | 审批人、盘点库房、批号、盈亏/实盘数量金额可追溯 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **正向** | 选药库 → 选品录入实盘 → 保存盈亏字段 → 提交 → 同意 → **盘盈库存增、盘亏库存减** |
| **零差异** | 盈亏为 0 行保存成功；**同意**后该行**无**发放、**无**库存变动 |
| **负盈亏** | 盘亏行 **`item_quantity` < 0** 可保存；同意后库存减少 |
| **init** | **仅**药库下拉，无药房列表 |
| **详情回显** | **`total_quantity` / `total_price` / `profit_loss_price` / `item_quantity`** 与保存一致 |
| **规则** | **BR-WHS-ST-008** 防重复同意；非法药库拒绝 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `DM-WKF-006-SupplyRequest-供应请求.md` |
| `DM-WKF-005-SupplyDelivery-供应发放.md` |
| `DM-WKF-008-InventoryItem-库存项.md` |
| `DM-ADM-003-Location-位置.md` |
| `AS-INV-WHS-003-药库出库单.md`（库房单据通用细则参照） |
| `AS-INV-PHA-006-药房盘点单.md`（对偶页） |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v0.2 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v0.1 | 2026-05-22 | 初稿：**`CABINET_STOCKTAKE`**；单药库 purpose；盈亏/实盘字段语义；同意按盈亏入账；领域 Gap 已关闭 |

### 9.3 IF→DM 追溯摘要

| IF | 业务语义锚点 |
| --- | --- |
| IF-WHS-ST-01 | **DM-ADM-003** Location（药库） |
| IF-WHS-ST-02～03 | **DM-WKF-006** SupplyRequest QueryService |
| IF-WHS-ST-04 | **DM-WKF-006** **`createStocktake` §6.1.7** |
| IF-WHS-ST-08 | **DM-WKF-005** **`createStocktakingDelivery` §6.1.3**；**DM-WKF-008** 增减 |
| IF-WHS-ST-11 | **DM-WKF-008** 批号库存查询 |

---

**文档负责人**：（待填） · **评审**：（待填）
