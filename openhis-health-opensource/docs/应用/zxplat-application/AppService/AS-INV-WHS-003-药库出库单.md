# 药库出库单 · 应用服务设计（AS）

> **文档职责**：说明「药库出库单」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：药库业务线 — 药库人员维护 **药库→药房（或下游位置）** 的 **`CABINET_STOCK_OUT`**（与 **`SupplyType#getCode()`** 一致）出库申请与审批闭环：列表与详情、新建/编辑/删除、提交/撤回审批、审批同意/驳回。**出库单为执行单据**：审批**同意**后由应用层编排 **SupplyDelivery（出库发放）**、**源药库 InventoryItem 扣减**、**目的位置 InventoryItem 增加**，并**同步生成**一张 **`PHARMACY_STOCK_IN`**（**`SupplyType#getCode()`** = **`PHARMACY_STOCK_IN`**）药房入库单，其**业务状态直接为 **`AGREE`**（免审终态）**，且 **`original_bus_no`** 记录**本出库单** **`bus_no`**。**本场景不引入批号维定价行**（**不**补录 **`LOT_NUMBER_COST` / `LOT_NUMBER_PRICE`**）。对 **`trace_no` 非空** 之明细行，须按 **DM-CUS-010** 追加 **`cus_trace_number` + `cus_trace_number_flow`**，**`operation_type`** 与 **`SupplyType#CABINET_STOCK_OUT#getCode()`** 一致（与 **`wkf_supply_request.type_enum`** 持久化码一致）。
>
> **与药房入库分工**：**AS-INV-PHA-001** 负责 **`PHARMACY_STOCK_IN`** 独立建单与审批；本 AS 在出库**同意**相位**生成**对侧 **`PHARMACY_STOCK_IN`**（已 **同意**）并建立 **`original_bus_no`** 关联（见 **BR-WHS-SO-012**）；生成壳单之行数据（**item 引用、数量、批号、单价等**）在 **IF-PHA-IN-03** 须经 **ProductDefinition** 读模型富化 **项目名称/规格/厂家**（与 **IF-WHS-SO-03** 同构，见 **AS-INV-PHA-001-SDD** **v1.0.7**）。**IF-WHS-SO-11** 选品列表行价随库存行 **批号** 走 **`LOT_NUMBER_COST`**（与 **IF-WHS-IN-11** 选品列表价类对齐，见 **SDD WHS-SO-SDD-R-004**）；**IF-WHS-SO-03** 详情多单位目录之采购参考价仍为 **`PURCHASE`**，与 **AS-INV-PHA-001** **对偶同构**（互指 **IF-WHS-SO-03** 与 **IF-PHA-IN-03**）。
>
> **覆盖声明**：本稿以**药库↔药房调拨**场景及当期业务口述为基线，并与 **`apps/web-ele` 药库出库页** 可执行动作对齐；**正式定稿前**须用产品/UI 全量清单与实现对照片修订 **§1.4、§2、§4、§5**。
>
> **编码**：AS-INV-WHS-003 · **版本**：v0.14 · **日期**：2026-07-15

---

## 1. 场景概述

### 1.1 目标与边界


| 维度         | 说明                                                                                                                                    |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| **在内**     | 创建 **`CABINET_STOCK_OUT`** 草稿（可带 `original_bus_no` 关联请领等上游，见 **IF-WHS-SO-12**）；维护明细；审批至 **同意/驳回/撤回**；可编辑态改单与删单；**同意**后编排 **出库发放 + 源库扣减 + 目的库增加 + 生成已同意药房入库单 +（条件）追溯轨迹** |
| **在外**     | **AS-INV-PHA-001** 药房入库只读台账（本页**生成**其对侧单据）；财务发票；院外物流                                                                                                                                    |
| **涉及域**    | 写侧：**SupplyRequest**、**SupplyDelivery**、**InventoryItem**、**TraceNumberRecord（`cus_trace_number` + `cus_trace_number_flow`，DM-CUS-010）**；主数据：**Location**、**人员**、**单据状态（SupplyStatus）**、**入库项目类型（药品/耗材定义表）**；读侧：**SupplyRequest** 列表真源、**InventoryItem**/**库存QueryService**（选品主表）、**ProductDefinition**（行展示装配） |
| **跨域**     | **AGREE** 后：出库发放 + 源 **`purpose_location_id` 语义之「源药库」** 与 **「目的药房」** 库存反向同批号扣加；**新建** **`PHARMACY_STOCK_IN`** **AGREE** + **`original_bus_no`**                                                                                                          |
| **编排权威**   | 以本文 **§2、§4** 为准；写侧聚合 **SupplyRequest**；库存与发放由应用层编排 **SupplyDelivery** / **InventoryItem**                                                                                                              |
| **前置（摘要）** | 源药库、目的位置合法且类型匹配业务规则；选品请求须带 **`sourceLocationId`**（与明细扣减位置一致）                                                                                                                              |
| **后置（摘要）** | 出库 **同意** 后源库减少、目的库增加可查；对侧 **`PHARMACY_STOCK_IN`** 已 **同意** 可查；**驳回/撤回** 不产生发放与对侧入库                                                                                                       |


### 1.2 角色与关键动作


| 角色           | 关键动作                                                                       |
| ------------ | -------------------------------------------------------------------------- |
| **药库制单员**   | 查询出库列表 → 新建（可选关联请领）→ 按批选品 → 提交审批 →（必要时）撤回                                     |
| **药库主管/审批人** | 同意 / 驳回                                                                  |
| **管理员**      | 待审核态修改或逻辑删除                                                              |


### 1.3 术语与口径约定


| 术语              | 口径                                                                                                                    |
| --------------- | --------------------------------------------------------------------------------------------------------------------- |
| **出库单**         | `type_enum`=`**CABINET_STOCK_OUT**`（**`SupplyType#getCode()`**），**执行单据**；**同意**后触发 **出库发放** + **源库减** + **目的库增** + **生成 `PHARMACY_STOCK_IN`（`AGREE`）** |
| **`bus_no` 前缀** | 新建且首行未显式传入 **`busNo`** 时：按日流水之**序号键前缀**为 **`SupplyType.busNoSeqKey`**（本页出库 **`CSO`**），与订货、**`PHARMACY_STOCK_IN`** 等其它 **`SupplyType`** **分桶**；**`type_enum` 无法匹配白名单**时回退 **`SR`**（**不含 `SQ`**）                                                                  |
| **`original_bus_no`** | 可选指向**请领单**等上游（**IF-WHS-SO-12**）；**不**用于指向本场景自动生成的药房入库单（对侧单号由系统生成后回写展示属 **QueryService/详情** 范畴）                                                                  |
| **时间与操作者** | 与 **AS-INV-WHS-001/002** 一致：**制单时间**为明细 **`occurrence_time`**（前端传）；**`apply_time`** 仅在提交审批时服务端写入；**`approval_time`** 在同意/驳回时服务端写入；**`applicant_id`** 取会话医务人员；**`approver_id`** 取当前会话操作人；**`practitioner_id`** 须由前端在新建/修订**请求体 `practitionerId`** 传入。 |
| **源/目的位置**     | **`source_location_id`**：扣减库存之药库；**`purpose_location_id`**：实物接收之药房（或产品允许之其它目的位置类型）；列表须展示 **「到何处」**（目的位置展示名）                                                                  |
| **选品主表**       | 以 **库存（InventoryItem 及库存QueryService）** 为**主表**，**一行一批号**（不将同一物品多批汇总为一行）；每行须含 **效期** 与 **可用数量**（按请求 **`sourceLocationId`** 计算「可用量」口径，与 **DM-WKF-008** 一致）。**UI 约束（批号级选品）**：选品下拉/列表**必须展示 `lotNumber`（批号）**，以便同品多批区分；**不得**做成订货/进货（**IF-WHS-PO-11** / **IF-WHS-IN-11**）那种按产品定义一行、不显批号的展开方式。                                                                  |
| **库存自然键与可用量** | 与 **DM-WKF-008 §1.1 / §2.1 / §6.2.3**：`wkf_inventory_item` **业务唯一**为 **四元组** `(location_id, item_table, item_id, lot_number)`；**可用量**以 **ACTIVE**、未删、**`quantity > 0`**、无有效锁、未过期为准；选品/目录装配时**按库存行主键 `wkf_inventory_item.id`（`inventoryItemId`）** 回填 **`availableQuantity`**（**禁止**再按 **`item_id + 归一化批号` 聚合** 导致与保存前断言口径不一致）；**IF-WHS-SO-11** 请求可选 **`positiveQuantityOnly`**（非必传）：**`true`** 仅返回源库账面量 **> 0** 批号行，**`false`/未传** 不过滤数量 |
| **初始化下拉**      | **IF-WHS-SO-01** 返回之药库源、药房目的、入库项目类型、**单据状态（SupplyStatus）** 等，**一律**使用 **OptionDto**（`value` / `label` / `sort`）；**禁止**为本接口自建专用下拉 DTO（与 **IF-PHA-IN-01** 对称，**SDD** 与 **OptionDto** 同源策略） |
| **枚举口径**       | 凡 **`SupplyType` / `SupplyStatus`** 及库表 **`type_enum` / `status_enum`**，一律 **`getCode()`** 字符串（如 **`CABINET_STOCK_OUT`**、**`PHARMACY_STOCK_IN`**、**`AGREE`**）                                                                  |
| **DM 路径**       | 下文聚合路径均指 `docs/领域/DomainModel/` 下对应文件                                                               |
| **供应行 `item_quantity`/`unit_code`** | 同 **AS-INV-WHS-002** **§1.3**「供应行 **`item_quantity`/`unit_code`**」：**同意**路径源扣、目的增及对侧 **`PHARMACY_STOCK_IN`** 行拷贝均以 **`item_quantity`**（拆零）为数量真源；**`unit_code`** 不参与与 **`InventoryItem`** 的二次倍数换算。 |


### 1.4 本期范围与非目标


| 类别           | 内容                                                                                                           |
| ------------ | ------------------------------------------------------------------------------------------------------------ |
| **本期**       | 出库列表（**突出目的位置**）、详情、新建、编辑、提交/撤回、同意/驳回、删除；**库存批号级选品** + 详情/选品返回 **效期**；**同意**生成 **`PHARMACY_STOCK_IN`（`AGREE`）**                                                                                           |
| **待评审对齐**    | 目的位置是否仅限药房；**请领关联**是否强制；列表筛选项与 **openhis-pro-ui** 现网字段（如 `typeEnum` 数值透传）与 **`getCode()`** 对外契约是否双轨由 **SDD** 收口                                                                  |
| **非目标**      | **批号维定价**（**`LOT_NUMBER_COST` / `LOT_NUMBER_PRICE`**）；**医保**对外监管接口；**手工药房入库单**之完整审批（见 **AS-INV-PHA-001**）；**系统自动生成的 `PHARMACY_STOCK_IN`** 不进入药房侧「再提交再审批」队列（生成即为 **`AGREE`**）                                           |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域）                       | 在本场景中的职责（摘要）                                            | DM 路径                                                         |
| ---------------------------- | ------------------------------------------------------- | ------------------------------------------------------------- |
| **SupplyRequest（WKF）**       | 出库建单（**`CABINET_STOCK_OUT`**）；审批；逻辑删除；**同意**相位触发对侧 **`PHARMACY_STOCK_IN`** 创建（业务语义）                         | `DM-WKF-006-SupplyRequest-供应请求.md`                            |
| **SupplyDelivery（WKF）**      | 出库/入库发放事实编排                                           | `DM-WKF-005-SupplyDelivery-供应发放.md`                           |
| **InventoryItem（WKF）**       | 源库扣减、目的库增加（**拆零/最小单位**入账口径以 **DM-WKF-008** 为准）                                           | `DM-WKF-008-InventoryItem-库存项.md`                              |
| **Location（ADM）**            | 源/目的位置校验                                                  | `DM-ADM-003-Location-位置.md`                                     |
| **TraceNumberRecord（CUS）**   | **`trace_no` 非空** 时追加轨迹，**`operation_type`** = **`CABINET_STOCK_OUT`**（**`getCode()`**）                         | `DM-CUS-010-TraceNumberRecord-追溯码记录.md`                      |


### 1.6 业务规则（BR-*）


| 规则 ID               | 描述（语义锚点）                                                                                             | 绑定 IF                                      |
| ------------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| **BR-WHS-SO-001**   | 最小建单必备字段、`type_enum` 须为 **`CABINET_STOCK_OUT`**                                                                                   | IF-WHS-SO-04                               |
| **BR-WHS-SO-002**   | **源**须为药库、**目的**须为业务允许之接收位置；位置合法 —— 对齐 **BR-SR-006** 类位置门控                                                                       | IF-WHS-SO-04 / IF-WHS-SO-05                |
| **BR-WHS-SO-003**   | 若填 `original_bus_no`：所指上游请领须 **`AGREE`**、类型为 **`PHARMACY_REQUISITION`** 或 **`DEPT_REQUISITION`**、**`source_location_id`** 与出库源药库一致，且满足 **BR-PHA-QL-011** / **BR-DEP-QL-011**（未被其它未删除出库单引用）                                                                 | IF-WHS-SO-04 / IF-WHS-SO-05 / IF-WHS-SO-12 / IF-WHS-SO-13                |
| **BR-WHS-SO-004**   | 同一 `bus_no` 状态一致 —— **BR-SR-002**                                                                    | IF-WHS-SO-05～IF-WHS-SO-10                 |
| **BR-WHS-SO-005**   | 仅可编辑态修改 —— **BR-SR-003**、**BR-SR-014**                                                            | IF-WHS-SO-05                               |
| **BR-WHS-SO-006**   | 提交审批 —— **BR-SR-004**                                                                               | IF-WHS-SO-06                               |
| **BR-WHS-SO-007**   | 同意/驳回追溯 —— **BR-SR-005**                                                                            | IF-WHS-SO-08 / IF-WHS-SO-09                |
| **BR-WHS-SO-008**   | 删除防下游引用 —— **BR-SR-010**                                                                           | IF-WHS-SO-10                               |
| **BR-WHS-SO-009**   | **同意**后须 **防重复编排**（已存在本单对应**已完成出库发放**事实则不得再次同意出库）                                                                 | IF-WHS-SO-08                               |
| **BR-WHS-SO-010**   | **选品/明细数量**不得超过所选 **库存批号行**在 **`sourceLocationId`** 下之 **可用量**                                                                 | IF-WHS-SO-04 / IF-WHS-SO-05 / IF-WHS-SO-11 |
| **BR-WHS-SO-011**   | **`trace_no` 非空行 → `cus_trace_number` + `cus_trace_number_flow`**，**`operation_type`** = **`CABINET_STOCK_OUT`**（**`getCode()`**）；**空白** 行**不**生成记录                                                                 | IF-WHS-SO-08                               |
| **BR-WHS-SO-012**   | **同意**路径：在**同一原子业务单元**内完成 **出库发放 + 源库减 + 目的库增 + 新建对侧入库壳单（目的为药房 → **`PHARMACY_STOCK_IN`**；目的为科室药房 → **`DEPT_STOCK_IN`**）且状态 `AGREE` + `original_bus_no`= 本出库 `bus_no`**；**不**执行批号维定价表补录                                                                 | IF-WHS-SO-08                               |
| **BR-WHS-SO-013**   | 明细行须能定位 **批号/效期/库存项**；行上 **`item_quantity`** 与 **`wkf_inventory_item.quantity`** 同为**拆零下持久化**，与可用量比对及同意路径扣加账**均以 `item_quantity` 为准**；**`unit_code`** 仅展示/录入语义，**不得**再参与与库存数量的倍数换算（与 **DM-WKF-008**、**ProductDefinition** 多单位展示口径一致）                                                                 | IF-WHS-SO-03 / IF-WHS-SO-04 / IF-WHS-SO-11 |


---

## 2. 页面与接口总览（契约概览）


| IF 编号             | 用户动作      | 接口语义（业务）                                             | 读/写属性 | 关键规则（BR）               | 关键状态变化（自然语言）                                          | 涉及领域                                                                        | QueryService                                                         |
| ----------------- | --------- | ---------------------------------------------------- | ----- | ---------------------- | ----------------------------------------------------- | --------------------------------------------------------------------------- | ----------------------------------------------------------- |
| IF-WHS-SO-01      | 进入出库页面    | 初始化：**源药库**与**目的药房**位置列表（分列 **`warehouseLocationOptions` / `pharmacyLocationOptions`** 或等价命名，**OptionDto**）；入库项目类型（药品定义表/耗材定义表两档）；**SupplyStatus**；人员类下拉若 UI 需要则与 **IF-PHA-IN-01** 同源策略（全量主数据 + **操作者管辖库位**经 **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** 收窄；**管辖 empty 过渡态不收窄**，该类仍全量） | 读     | —                      | —                                                     | Location / **SupplyStatus**                                         | —                                                           |
| IF-WHS-SO-02      | 查询/刷新列表   | 分页查询 **`CABINET_STOCK_OUT`**；**按 `bus_no` 合并一行**；行内除公共字段外**须展示目的位置（到何处）**及单据状态、制单/审批信息等（其余展示字段与 **IF-PHA-IN-02** **尽量同构**）                     | 读     | —                      | —                                                     | SupplyRequest                                                                 | **`wkf_supply_request`** QueryService 列表查询（与药房入库列表策略一致）                             |
| IF-WHS-SO-03      | 查看详情      | 按 **`bus_no`** 查询出库单；明细**批号级**：名称、规格、厂家合一、数量、单位、批号、**效期**、追溯码、备注等（与 **IF-PHA-IN-03** **同构字段**尽量一致，**效期**为**必选展示**）；**并**须附带与 **IF-PHA-IN-03** 同源之**多单位目录提示**（四槽含包装、换算比、**`PURCHASE`** 采购参考价、各槽展示单价按**行单价+行单位**优先否则**采购参考价+默认采购单位**回填）。**药房入库详情（AS-INV-PHA-001 · IF-PHA-IN-03）**与本 IF **同构对齐**                                  | 读     | **BR-WHS-SO-013**         | —                                                     | SupplyRequest / InventoryItem / ProductDefinition                                                                 | WarehouseDocumentDetail 或等价QueryService                                     |
| IF-WHS-SO-04      | 新建出库单     | 创建 **`CABINET_STOCK_OUT`** 草稿；**`PENDING_APPROVAL`**；可带 `original_bus_no`（请领等）             | 写     | BR-WHS-SO-001～003 / **010** | 新 `bus_no`（**独立前缀**）；待审核                          | SupplyRequest / Location / InventoryItem                                             | 列表/详情刷新                                                    |
| IF-WHS-SO-05      | 编辑出库单     | 可编辑态下改明细（批号级行）                                                                               | 写     | BR-WHS-SO-005 / **010**      | 明细变更                                             | SupplyRequest / InventoryItem                                                                 | 同上                                                          |
| IF-WHS-SO-06      | 提交审批      | 待审核 → 审核中                                              | 写     | BR-WHS-SO-006             | `PENDING_APPROVAL` → `APPROVAL`                         | SupplyRequest                                                                 | 待办、列表刷新                                                     |
| IF-WHS-SO-07      | 撤回审批      | 审核中撤回                                                 | 写     | —                      | `APPROVAL` → `WITHDRAW` / `PENDING_APPROVAL`（以 **DM** 为准） | SupplyRequest                                                                 | 同上                                                          |
| IF-WHS-SO-08      | 审批同意      | 审批通过                                                   | 写     | BR-WHS-SO-007 / **009** / **011** / **012** | **`AGREE`**；**出库发放 + 源库减 + 目的库增**；**新建 `PHARMACY_STOCK_IN` 为 `AGREE` 且 `original_bus_no`= 本单 `bus_no`**                            | SupplyRequest → SupplyDelivery / InventoryItem / **对侧 SupplyRequest** / **TraceNumberRecord**                             | 列表/详情/库存QueryService                               |
| IF-WHS-SO-09      | 审批驳回      | 审批拒绝                                                   | 写     | BR-WHS-SO-007             | `REJECT`                                                | SupplyRequest                                                                 | 同上                                                          |
| IF-WHS-SO-10      | 删除单据      | 逻辑删除                                                   | 写     | BR-WHS-SO-008             | 逻辑删除标记                                                  | SupplyRequest                                                                 | QueryService隐藏                                                       |
| IF-WHS-SO-11      | 出库选品检索    | **分页**：以 **库存批号行** 为主表联接 **ProductDefinition**；**每行一批号**，展示**该批可用量**（按 **`sourceLocationId`**）、**效期**、**批号（`lotNumber`，选品下拉/列表必显）**、物品标识、规格、厂家合一、**`unitBands`**（与 **IF-PHA-IN-11** 一致：**采购 / 包装（`bandKind`=`PACKAGE`，QueryService `package_unit_code`）/ 拆零 / 库存** 四槽；**IF-WHS-SO-11**/**IF-PHA-IN-11** 装配策略须一致）；**不**以「物品维度汇总库存」替代批号行；**列表行展示单价**须随本行 **批号** 取自 **`LOT_NUMBER_COST`**（无批号或无命中可为空），**不得**以实例维 **`PURCHASE`** 冒充该行契约真源（**SDD WHS-SO-SDD-R-004**）                                                                 | 读     | **BR-WHS-SO-010**         | —                                                     | **InventoryItem** / **ProductDefinition**                                                             | 库存QueryService + 产品QueryService                                       |
| IF-WHS-SO-12      | 加载可关联请领列表 | 弹窗分页：本药库、**`AGREE`**、**`PHARMACY_REQUISITION` 与 `DEPT_REQUISITION` 并集**（可由 `requisitionTypeEnum` 收窄）；**按 `bus_no` 聚合一行一单**（`totalAmount`=同行 Σ`totalPrice`）；**排除**已被未删除 **`CABINET_STOCK_OUT`**（`original_bus_no` 指向该请领）引用的单号（**BR-PHA-QL-011** / **BR-DEP-QL-011**） | 读     | **BR-PHA-QL-011** / **BR-DEP-QL-011**（交叉） | —                                                     | SupplyRequest（读口径）                                                           | **`countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter`**                             |
| IF-WHS-SO-13      | 拉取请领预填明细 | 选定请领单号后带出出库草稿头与明细（门禁同 **IF-WHS-SO-12**） | 读     | **BR-WHS-SO-003** / **BR-PHA-QL-011** | —                                                     | SupplyRequest                                                                 | —                                                           |


---

## 3. 缺口跟踪（Gap）


| Gap ID     | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态  |
| ---------- | ---- | ------- | ----------- | ---- | ----- | --- | --- |
| **GAP-WHS-SO-001** | **DM 能力** | `DM-WKF-006-SupplyRequest-供应请求.md` §6 | 缺「**出库同意**时**原子**创建 **`PHARMACY_STOCK_IN`** 且 **`AGREE`**、并声明 **`original_bus_no`** 与防双扣库存之编排约束」的可验收方法/事件语义 | DM 增补该编排与不变式，并与 **DM-WKF-005** / **DM-WKF-008** 对齐 | IF-WHS-SO-08 | 高 | 打开 |
| **GAP-WHS-SO-002** | **DM 能力** | `DM-CUS-010-TraceNumberRecord-追溯码记录.md` §4.4 | 缺 **`operation_type`** 取 **`CABINET_STOCK_OUT`**（**`getCode()`**）之枚举白名单与轨迹写入规则 | DM 增补枚举值与写入规则 | IF-WHS-SO-08 | 中 | 打开 |


---

## 4. 接口编排说明书（业务步骤）

### IF-WHS-SO-01 进入出库页面初始化


| 维度          | 说明                                  |
| ----------- | ----------------------------------- |
| **触发前提**    | 用户进入药库出库菜单                          |
| **主路径步骤**   | 加载**源药库**位置列表与**目的药房**位置列表（分列、**OptionDto**）；入库项目类型两档；**单据状态**；其它人员下拉若页面具备则与 **IF-PHA-IN-01** 同源策略 |
| **关键分支**    | **库位下拉**：**已配置非空管辖**且与主数据有交则收窄；**管辖 empty 过渡态或不交**则仍返回该类型启用态全库                                     |
| **失败分支**    | EX-WHS-SO-101                  |
| **查询侧说明** | 无                                   |


---

### IF-WHS-SO-02 出库单列表查询


| 维度          | 说明                                                  |
| ----------- | --------------------------------------------------- |
| **触发前提**    | 筛选、翻页、刷新                                            |
| **主路径步骤**   | 按 **`CABINET_STOCK_OUT`**、**源药库**、日期/状态/关键字等筛选；**`bus_no` 合并一行**；行内**须含目的位置（到何处）**之展示名；金额/制单/审批人/审批时间等字段与 **IF-PHA-IN-02** **同构** |
| **关键分支**    | 无                                                     |
| **失败分支**    | EX-WHS-SO-102                                       |
| **查询侧说明** | 列表展示                                                |


---

### IF-WHS-SO-03 出库单详情


| 维度          | 说明                               |
| ----------- | -------------------------------- |
| **触发前提**    | 从列表进入或深链                         |
| **主路径步骤**   | 按 **`bus_no`** 加载头与全部明细行；**批号级**列：项目名、规格、厂家合一、数量、单位、批号、**效期**、追溯码、备注；**多单位目录提示**与 **AS-INV-PHA-001** **IF-PHA-IN-03** 详情**同构**（见 **§2 IF-WHS-SO-03**） |
| **关键分支**    | 无                                  |
| **失败分支**    | 不存在、类型不匹配、无权限（EX-WHS-SO-103） |
| **查询侧说明** | 详情展示                             |


---

### IF-WHS-SO-04 新建出库单


| 维度          | 说明                                                                                                                                                                       |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **触发前提**    | 药库上下文有效                                                                                                                                                                 |
| **主路径步骤**   | 生成 **`CABINET_STOCK_OUT`** 草稿；**`bus_no` 使用本类型前缀规则**；写入源/目的位置；**从选品结果写入批号级明细**（数量≤可用量）；可选 `original_bus_no` |
| **关键分支**    | 关联请领：**BR-WHS-SO-003** 不满足则拒绝                                                                                                                                       |
| **失败分支**    | EX-WHS-SO-001 / EX-WHS-SO-002                                                                                                                                   |
| **查询侧说明** | **SupplyRequestCreatedEvent**                                                                                                                                            |


---

### IF-WHS-SO-05 编辑出库单


| 维度          | 说明                         |
| ----------- | -------------------------- |
| **触发前提**    | 可编辑态门控通过                         |
| **主路径步骤**   | 全量替换明细行（软删旧行插新行语义由 **SDD** 与域服务统一）；批号级数量再次校验 **可用量**                  |
| **关键分支**    | 无                              |
| **失败分支**    | EX-WHS-SO-004 / EX-WHS-SO-105   |
| **查询侧说明** | 写成功后刷新                       |


---

### IF-WHS-SO-06 提交审批


| 维度          | 说明                                                |
| ----------- | ------------------------------------------------- |
| **触发前提**    | 待审核                                               |
| **主路径步骤**   | 提交 → 审核中                                        |
| **关键分支**    | 无                                                   |
| **失败分支**    | EX-WHS-SO-003                                       |
| **查询侧说明** | **SupplyRequestSubmittedEvent**                   |


---

### IF-WHS-SO-07 撤回审批


| 维度          | 说明                           |
| ----------- | ---------------------------- |
| **触发前提**    | 审核中                          |
| **主路径步骤**   | 撤回（状态机以 **DM** 为准）           |
| **关键分支**    | 无                              |
| **失败分支**    | EX-WHS-SO-003                  |
| **查询侧说明** | **SupplyRequestWithdrawnEvent** |


---

### IF-WHS-SO-08 审批同意


| 维度          | 说明                                                                                                                                                    |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| **触发前提**    | **审核中**；具备审批权限；**BR-WHS-SO-009** 防重                                                                                                            |
| **主路径步骤**   | ① **本出库单** **同意**（**`AGREE`**），记录审批人/时间。② **同一业务事务**内：**出库发放**；**源药库**按批号/五元组 **扣减**；**目的药房** **增加**（拆零单位换算以 **DM-WKF-008** 为准）。③ **新建** **`PHARMACY_STOCK_IN`**：状态直接 **同意**，**`original_bus_no`** = **本出库 `bus_no`**，明细与出库行**业务对齐**（数量、批号、物品、单位换算一致）。④ **`trace_no` 非空** 行：写 **`cus_trace_number` + `cus_trace_number_flow`**（**BR-WHS-SO-011**）。⑤ 投影收敛为：源/目的库存与两侧单据可查。 |
| **关键分支**    | 任一步失败：**整单回滚**或等价「对外未生效」语义，与 **EX-WHS-SO-104** 对齐 |
| **失败分支**    | EX-WHS-SO-003 / EX-WHS-SO-104                                                                                                                         |
| **查询侧说明** | 出库与新生成之药房入库、库存QueryService更新                                                                                                                               |


---

### IF-WHS-SO-09 审批驳回


| 维度          | 说明                             |
| ----------- | ------------------------------ |
| **触发前提**    | 审核中                            |
| **主路径步骤**   | 驳回终态                           |
| **关键分支**    | 无                                |
| **失败分支**    | EX-WHS-SO-003                  |
| **查询侧说明** | **SupplyRequestRejectedEvent** |


---

### IF-WHS-SO-10 删除单据


| 维度          | 说明                            |
| ----------- | ----------------------------- |
| **触发前提**    | 可删状态门控（与 **DM-WKF-006** 逻辑删除规则一致） |
| **主路径步骤**   | 逻辑删除整单                        |
| **关键分支**    | 无                               |
| **失败分支**    | EX-WHS-SO-005                   |
| **查询侧说明** | **SupplyRequestDeletedEvent** |


---

### IF-WHS-SO-11 出库选品检索


| 维度          | 说明                                                                                                                                                                                                                                                                                                                                                    |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **触发前提**    | 新建/编辑打开选品；请求须含 **`sourceLocationId`**（与出库源库一致且 ∈ **IF-WHS-SO-01** 药库门控集合）；**`productTableName`** 白名单与 **IF-PHA-IN-11** 一致                                                                                                                                                                                          |
| **主路径步骤**   | 分页查询 **库存批号行**（可选入参 **`positiveQuantityOnly`**：非必传；**`true`** → 仅源库 **`quantity > 0`**；**`false`/未传 → 含零账面）；联接 **ProductDefinition** 装配名称/规格/厂家、**`unitBands`**（与 **IF-PHA-IN-11** 四槽一致，含 **`PACKAGE`**）；每行返回 **批号（`lotNumber`）**、**效期**、**可用量**；**选品下拉/列表 UI 必须展示批号列**（§1.3）；**列表行展示单价**（如 **`purchaseUnitPrice`**）按本行 **批号** 解析 **`LOT_NUMBER_COST`**（见 **SDD WHS-SO-SDD-R-004**）；**不**做跨批汇总 |
| **关键分支**    | 未传/否时零账面行仍可返回，由 UI 灰显或禁用；缺少批号列展示则同品多行无法区分，视为实现不合格                                                                                                                                                                                          |
| **失败分支**    | EX-WHS-SO-102                                                                                                                                                                                                                                                                                                                                         |
| **查询侧说明** | 无                                                                                                                                                                                                                                                                                                                                                     |


---

### IF-WHS-SO-12 加载可关联请领列表


| 维度          | 说明                                   |
| ----------- | ------------------------------------ |
| **触发前提**    | 用户点击「关联请领」类入口；须已选**源药库** `sourceLocationId`（**`0`** = 当前用户可见**全部药库**，与进货列表 **`sourceLocationId=0`** 策略同构）                          |
| **主路径步骤**   | 查询 **`PHARMACY_REQUISITION` 与/或 `DEPT_REQUISITION`**（默认并集）且 **`status_enum`=`AGREE`**；**`sourceLocationId` 非 0** 时 **`source_location_id`** = 该药库，**`= 0`** 时展开为可见药库 id 集合（**`InventoryWarehouseConstants.ALL_WAREHOUSES_LIST_FILTER_LOCATION_ID`**）；经 **`SupplyRequestDocumentListQuery` + `countDistinctBusNo` / `listDistinctBusNoPage` / `listByBusNosWithFilter`** **按 `bus_no` 聚合一行一单**（对偶对齐 **IF-WHS-IN-12**）；行内 **`totalAmount`** = 同号各行 **`total_price`** 之和；**排除**已被未删除 **`CABINET_STOCK_OUT`**（`original_bus_no` 指向该请领）引用的单号（**BR-PHA-QL-011** / **BR-DEP-QL-011**）     |
| **关键分支**    | 无符合条件空页 |
| **失败分支**    | EX-WHS-SO-102                        |
| **查询侧说明** | 弹窗展示；**禁止**用 `pageWarehouseDocumentDetail` 明细分页冒充选单（否则同号按品项行拆开）；分页 `total` 在内存剔除已引用单后可能略大于实际可选项（与进货可关联订货列表策略同构）                                 |


---

### IF-WHS-SO-13 拉取请领预填明细


| 维度          | 说明                                   |
| ----------- | ------------------------------------ |
| **触发前提**    | 用户在关联请领弹窗选定单号（或手填 `busNo` 后拉取）                          |
| **主路径步骤**   | 校验请领存在、类型为药房或科室请领、**`AGREE`**、**`source_location_id`** 与请求源药库一致、**BR-PHA-QL-011** / **BR-DEP-QL-011**；拷贝头字段与批号级明细至出库草稿 DTO（`originalBusNo` = 请领 `bus_no`）；目的库位可为药房或科室药房     |
| **关键分支**    | 改单修订时传 `excludeStockOutBusNo` = 本出库 `bus_no`，避免自引用误判 |
| **失败分支**    | EX-WHS-SO-001 / EX-WHS-SO-002                        |
| **查询侧说明** | 禁止绕过本 IF 直接调用请领详情接口带入（避免跳过门禁）                                 |


---

## 5. 业务异常与用户提示


| 语义代码          | 场景        | 用户提示方向        | 可重试 |
| ------------- | --------- | ------------- | --- |
| EX-WHS-SO-001 | 建单/关联校验失败 | 按提示补全或修正来源   | 是   |
| EX-WHS-SO-002 | 上游单不可引用   | 说明原单状态      | 视原因 |
| EX-WHS-SO-003 | 状态不允许操作   | 刷新后重试         | 是   |
| EX-WHS-SO-004 | 禁止编辑      | 说明原因          | 否   |
| EX-WHS-SO-005 | 删除被拒绝     | 下游引用或状态原因     | 否   |
| EX-WHS-SO-101 | 主数据加载失败   | 稍后重试          | 是   |
| EX-WHS-SO-102 | 列表/选品查询失败 | 缩小条件          | 是   |
| EX-WHS-SO-103 | 详情不可用     | 返回列表          | 视情况 |
| EX-WHS-SO-104 | 同意后排仓失败   | 联系管理员/查看库存锁定   | 视原因 |
| EX-WHS-SO-105 | 并发编辑冲突    | 刷新后重试         | 是   |


---

## 6. 事件语义与消费方


| 事件（以 **DM-WKF-006** §5 为准）       | 触发相位（IF）                | 典型消费方                       |
| ------------------------------- | ----------------------- | --------------------------- |
| `SupplyRequestCreatedEvent`     | IF-WHS-SO-04            | QueryService 列表查询、待办                |
| `SupplyRequestSubmittedEvent`   | IF-WHS-SO-06            | 待办、QueryService 列表查询                     |
| `SupplyRequestWithdrawnEvent`   | IF-WHS-SO-07            | QueryService 列表查询                        |
| `SupplyRequestAgreedEvent`      | IF-WHS-SO-08            | QueryService 列表查询；**出库发放与库存编排**；**对侧 `PHARMACY_STOCK_IN` 创建（`AGREE`）**       |
| `TraceNumberRecordAppendedEvent`（及批量等价，以 **DM-CUS-010** §5 为准） | IF-WHS-SO-08 **BR-WHS-SO-011** | 追溯查询、审计 |
| `SupplyRequestRejectedEvent`    | IF-WHS-SO-09            | QueryService 列表查询                        |
| `SupplyRequestDeletedEvent`     | IF-WHS-SO-10            | QueryService隐藏                       |
| SupplyDelivery / Inventory 侧事件 | IF-WHS-SO-08 下游编排成功之后 | 库存QueryService、对侧单据QueryService |


---

## 7. 非功能性需求


| 类别      | 要求                                                  |
| ------- | --------------------------------------------------- |
| **性能**  | 批号级选品分页可接受；同意链路可异步但最终一致                         |
| **幂等**  | 同意/驳回/提交短时重复请求幂等或明确已处理                              |
| **并发**  | 同一单据并发编辑乐观控制                                        |
| **一致性** | **同意**与源扣、目的加、**对侧入库单 `AGREE` 创建**、追溯轨迹对外一致                         |
| **审计**  | 审批人、审批时间、源/目的位置、批号与数量可追溯                                   |


---

## 8. 测试要点（业务视角）


| 类别      | 要点                                                                    |
| ------- | --------------------------------------------------------------------- |
| **正向**  | 手工新建 → 提交 → 同意 → **源库减、目的库增**；**新生成 `PHARMACY_STOCK_IN` 为 `AGREE` 且 `original_bus_no` 正确**；**追溯码非空可查**                                  |
| **列表/详情** | **IF-WHS-SO-02** 展示**到何处**；**IF-WHS-SO-03**/**IF-WHS-SO-11** 含**效期**与**批号级**数量；**IF-WHS-SO-03** 与 **IF-PHA-IN-03** 一致含**多单位目录提示** |
| **规则**  | **BR-WHS-SO-010** 超可用量拒绝；**BR-WHS-SO-009** 防重复同意；**BR-WHS-SO-012** 对侧入库单状态                                   |
| **异常**  | 源/目的位置非法；审核中删除；双开同意                                                           |
| **跨场景** | 与 **AS-INV-PHA-001** 在 **`PHARMACY_STOCK_IN`** 读列表中的展示关系（系统生成单）一致 |


---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档                                                                                       |
| ---------------------------------------------------------------------------------------- |
| `docs/领域/DomainModel/DM-WKF-006-SupplyRequest-供应请求.md`               |
| `docs/领域/DomainModel/DM-WKF-005-SupplyDelivery-供应发放.md`             |
| `docs/领域/DomainModel/DM-WKF-008-InventoryItem-库存项.md`               |
| `docs/领域/DomainModel/DM-ADM-003-Location-位置.md`                       |
| `docs/领域/DomainModel/DM-CUS-010-TraceNumberRecord-追溯码记录.md`          |
| `docs/应用/zxplat-application/AppService/AS-INV-PHA-001-药房入库单.md                          |


| v0.14 | 2026-07-15 | **IF-WHS-SO-11**：请求增可选 **`positiveQuantityOnly`**（非必传；true=源库账面量>0）；与请领/调出/盘点/损益/领用等同构 |
| v0.13 | 2026-07-14 | **IF-WHS-SO-12**：可关联请领列表 **按 `bus_no` 聚合一行一单**（`totalAmount` 合计）；QueryService 改走 **`countDistinctBusNo` 三件套**，禁止明细分页冒充选单；**`sourceLocationId=0`** 展开为当前用户可见全部药库（**`InventoryWarehouseConstants`**，与进货/INV 功能清单 §10.1 同构） |
| v0.12 | 2026-07-14 | **选品主表 / IF-WHS-SO-11**：批号级选品 UI **必须展示 `lotNumber`（批号）**；区别于订货/进货产品定义展开；同构页（请领/调出/盘点/损益/领用等）一律遵循 |
| v0.11 | 2026-05-29 | 开篇分工：对侧 **`PHARMACY_STOCK_IN`** 壳单详情须经 **IF-PHA-IN-03** 与 **IF-WHS-SO-03** 同构富化 **项目名称/规格/厂家**；下游 **AS-INV-PHA-001 v0.11** / **PHA-SDD v1.0.7** |
| v0.10 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v0.1 | 2026-05-14 | 初稿：**`CABINET_STOCK_OUT`** 全 IF；与 **药房入库**对偶差异（列表目的、选品库存主表、同意无定价、生成 **`PHARMACY_STOCK_IN`（`AGREE`）**、追溯 **`operation_type`**） |
| v0.2 | 2026-05-14 | **§1.3 `bus_no` 前缀**：明确 **`SQ_` + `SupplyType#getCode()`** 分桶规则 |
| v0.3 | 2026-05-14 | **`bus_no` 序号键**：**`busNoSeqKey`**（出库 **`CSO`**），**不含 `SQ`**；回退 **`SR`** |
| v0.5 | 2026-05-24 | **IF-WHS-SO-11**：**`unitBands`** 包装槽口径改为QueryService **`package_unit_code`**（**`bandKind`=`PACKAGE`**）；废止「销售单位/SALES 槽」表述 |
| v0.4 | 2026-05-14 | **IF-WHS-SO-11**：**`unitBands`** 与 **IF-PHA-IN-11** 对齐为**四槽**（含 **`PACKAGE`** 包装槽）；§2、§4、§9.3 |
| v0.5 | 2026-05-14 | **IF-WHS-SO-03**：详情明细附带**多单位目录提示**，与 **AS-INV-PHA-001** **IF-PHA-IN-03** **同构**；§2、§4、§8、§9.3 |
| v0.6 | 2026-05-14 | 正文剔除与外部采购 AS/IF 之互指；批号维定价仅写「本场景不补录」；**IF-WHS-SO-01/02/11**、§8、§9.1/§9.3 与 **AS-INV-PHA-001** 对偶对齐 |
| v0.8 | 2026-05-14 | **IF-WHS-SO-11**：选品列表行展示单价随库存行批号走 **`LOT_NUMBER_COST`**（**IF-WHS-SO-03** 仍为 **`PURCHASE`**）；§1、§2、§4、§9.3；下游 **SDD v1.2** |
| v0.9 | 2026-05-24 | **库位门控统一**：初始化与列表/写路径门控与 **`CurrentManagedLocationIdsResolver`**（下拉 **`#narrowActiveLocationsByManaged`**，Gate **`#gatedLocationIdsFromActive`**）对齐；**剔除**岗位分配推导库位；**管辖 empty 过渡态不收窄**；§2 **IF-WHS-SO-01**、§4 **IF-WHS-SO-01**；下游 **SDD v1.7** |


---

### 9.3 IF→DM 追溯摘要（评审用）


| IF               | 业务语义锚点（DM / QueryService章节） |
| ---------------- | ------------------------------ |
| IF-WHS-SO-01     | **DM-ADM-003** **Location**；**SupplyStatus** |
| IF-WHS-SO-02～03 | **DM-WKF-006** **SupplyRequest**；**InventoryItem** 批号级；**IF-WHS-SO-03** 多单位目录提示与 **IF-PHA-IN-03** 同构 |
| IF-WHS-SO-04～10 | **DM-WKF-006** §6 生命周期与删除 |
| IF-WHS-SO-08     | **DM-WKF-006** 执行语义；**DM-WKF-005** / **DM-WKF-008** 出库与库存；**DM-CUS-010** **`CABINET_STOCK_OUT`** 轨迹 |
| IF-WHS-SO-11     | **DM-WKF-008** 可用量；**ProductDefinition** 展示装配（含 **`unitBands`** 之 **`PACKAGE`** 等，与 **IF-PHA-IN-11** 一致）；选品列表行价 **`LOT_NUMBER_COST`**（随 **`lot_number`**） |


---

**文档负责人**：（待填） · **评审**：（待填）
