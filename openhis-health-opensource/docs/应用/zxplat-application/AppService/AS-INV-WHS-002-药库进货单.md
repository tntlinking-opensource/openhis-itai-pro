# 药库进货单 · 应用服务设计（AS）

> **文档职责**：说明「药库进货单」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：药库业务线 — 药库人员维护 **采购进货**（`SupplyType` 持久化码 **`CABINET_INBOUND_RECEIPT`**）：列表与详情、**从已同意订货单拉取**新建进货单（`original_bus_no` **必填**，禁止无源直建）、补充批号/追溯码/发票等、改单与删单、提交/撤回审批。**同意/驳回**属财务审批族（**BR-SR-013** / **AS-FIN-APR-001**）：本页**不再**作为审批入口（按钮剥离）；须财务角色在财务审批页审驳。**进货单为执行单据**：审批**同意**后编排入库发放与库存增加。
>
> **与订货分工**：**AS-INV-WHS-001** 仅处理订货申请（`SupplyType`：**`CABINET_PURCHASE_ORDER`**）；本 AS 负责 **药库进货单**（**`CABINET_INBOUND_RECEIPT`**）建单与本页审批闭环。**订货审批同意不会自动生成本页单据**，用户在本页或从上订货单「生成进货单」。
>
> **覆盖声明**：本稿为**初稿**——IF 与交互以 common 药库进货业务为基线；**正式定稿前**须用产品/UI 全量操作清单与既有应用服务实现对照修订 **§1.4、§2、§4、§5**。
>
> **编码**：AS-INV-WHS-002 · **版本**：v0.43 · **日期**：2026-08-31

---

## 1. 场景概述

### 1.1 目标与边界


| 维度         | 说明                                                                                                                                    |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| **在内**     | 创建 **`CABINET_INBOUND_RECEIPT`** 进货草稿（**必须**关联已同意订货单 `original_bus_no`）；维护明细与验收字段；审批链至 **同意/驳回/撤回**；**待审核/驳回/已撤回**可改明细后重提（修订重插回落待审核）；列表展示驳回原因；**同意**后触发入库发放与库存增加 |
| **在外**     | 订货申请审批（**AS-INV-WHS-001**）；**进货验收单**（**`CABINET_INBOUND_RECEIPT_INSPECTION`**）独立页面；财务发票核销纵深；院外物流                                                                    |
| **涉及域**    | 写侧：**SupplyRequest**、**SupplyDelivery**、**InventoryItem**、**收费项定义/定价行**（含 **`LOT_NUMBER_COST`** / **`LOT_NUMBER_PRICE`**，与 **`PriceType#getCode()`** 一致）、**TraceNumber / TraceNumberRecord**（**`cus_trace_number` + `cus_trace_number_flow`**，**DM-CUS-010 v2.0.5**）；主数据：**Location**、**Supplier**、**人员（经手人·药师、库管）**、**入库项目类型（药品定义表、耗材定义表）**、**单据状态（SupplyStatus）**、**收费项/项目定价（维护态 **`PURCHASE`/`SALE`** 均为**每包装单位**价；**IF-WHS-IN-11** 选品 **`purchasePrice`** 取 **`PURCHASE`**；**IF-WHS-IN-03** 详情目录参考价亦为 **`PURCHASE`**；**禁止**采购选品取 **`SALE`**）**；读侧投影：**WarehouseDocumentDetail**（**不含 IF-WHS-IN-02 进货分页列表真源**，见 **SDD WHS-IN-SDD-R-010**）、**ProductDefinition**（多单位/单位槽与拆零口径用于选品展示与 **`unit_code`** 展示语义；**`item_quantity` 与库存账面同为拆零下持久化**，同意路径**不得**再按 **`unit_code`** 对 **`item_quantity`** 做入账倍数换算；及库存侧QueryService，以库存域为准） |
| **跨域**     | 审批 **AGREE** 后编排 **SupplyDelivery** + **批号维定价补录** + **InventoryItem**（**DM-WKF-006** §6.1.14 **执行单据**分支 **BR-SR-012**）+ **追溯入库**（**IF-TRC-05**：建主档 + `HOSPITAL_IO` IN 子表流水，**AS-CUS-TRC-001**）                                          |
| **编排权威**   | 以本文 **§2、§4** 为准；写侧聚合 **SupplyRequest**；发放、批号维定价与库存由应用层编排 **SupplyDelivery** / **定价行** / **InventoryItem**                                              |
| **前置（摘要）** | 药库 **Location** 可用；**必须**存在已 **同意** 的订货单（**BR-WHS-IN-003**） |
| **后置（摘要）** | 进货单 **同意**后库存侧可见增加（最终以发放与库存聚合为准）；**驳回/撤回**不产生入库发放                                                                             |


### 1.2 角色与关键动作


| 角色           | 关键动作                                                                       |
| ------------ | -------------------------------------------------------------------------- |
| **药库收货员/制单员** | 查询进货列表 → 新建（或从订货生成）→ 填写批号/数量/金额 → 提交审批 →（必要时）撤回 |
| **财务** | 在 **AS-FIN-APR-001** 财务审批页同意 / 驳回（本页不展示审驳按钮） |
| **管理员** | 待审核态修改或逻辑删除 |


### 1.3 术语与口径约定


| 术语              | 口径                                                                                                                    |
| --------------- | --------------------------------------------------------------------------------------------------------------------- |
| **进货单**         | `type_enum`=`CABINET_INBOUND_RECEIPT`（**`SupplyType#getCode()`**），**执行单据**；**同意**后触发 **SupplyDelivery（入库）** + **批号维定价（条件满足时）** + **InventoryItem** 增加（**账面拆零单位**，**BR-SR-012**） |
| **来源订货单**       | `original_bus_no` 指向 **AS-INV-WHS-001** 订货单 `bus_no`；领域约束关联时原单须 **AGREE**（**DM** §6.1.2）                                  |
| **免申请进货**       | 无 `original_bus_no`，药库直接建 **`CABINET_INBOUND_RECEIPT`**（**DM** §6.1.2「免申请直接创建」）                                                      |
| **新建 `bus_no`**   | 首行未显式传入 **`busNo`** 时由按日流水生成；**序号键前缀**为 **`SupplyType.busNoSeqKey`**（本页进货 **`CIR`**）+ 业务日 + 当日序号，与其它 **`SupplyType`** **分桶**；**`type_enum` 无法匹配白名单**时回退 **`SR`**（**不含**历史 **`SQ`**） |
| **时间与操作者（与订货 AS-001、出库 AS-003 同口径）** | **制单时间**：明细行 **`occurrence_time`** 由前端传入，保存/修订时必填；**申请时间 `apply_time`** 仅在「提交审批」成功时由服务端写入当前时间，草稿阶段为 null；**审批时间 `approval_time`** 在同意/驳回时由服务端写入当前时间。**申请人 `applicant_id`**：取当前会话用户绑定之医务人员（建单与提交审批链路一致，不经请求体代传）；**审批人 `approver_id`**：同意/驳回取当前会话操作人（与订货单实现一致）；**经手人 `practitioner_id`**：**新建/修订请求体 `practitionerId`** 必填，由前端显式选择传入。 |
| **验收字段**        | `lot_number`、`trace_no`、`price`、`total_price`、**效期区间**（**`start_time` / `end_time`**，DTO 为 **`startTime` / `endTime`**）等在进货侧补充（**DM** §6.1.2）；**`trace_no` 持久化/DTO 最大长度**以 **TRC-WIDE-R-001**（**VARCHAR(50000)** 同源口径，见 **DM-WKF-006** / **DM-CUS-010** 与下游 **SDD**）为准                                               |
| **QueryService 能力**         | 列表/明细以QueryService为主；库存结果以 **InventoryItem** / 库存QueryService为准                                                                  |
| **入库项目类型**     | 以**表名**表达且仅两档：**药品定义表**（语义「药品」）、**耗材定义表**（语义「耗材」）；用于进货页初始化及明细行物品来源口径 |
| **供应行 `item_quantity`/`unit_code`** | **`wkf_supply_request.item_quantity`** 与 **`wkf_inventory_item.quantity`** 同为**拆零（最小）单位**下持久化；**`unit_code`** 仅记录录入或展示用计量单位。**审批同意**驱动 **`InventoryItem`** 增减时，数量以行上 **`item_quantity`** 为准，**禁止**再按 **`unit_code`** 与目录倍数对 **`item_quantity`** 做二次换算（若建单或选品阶段按业务单位录入，须在写 **`wkf_supply_request`** 前已换算为拆零数量）。 |
| **明细数量单位下拉（库房族 UI）** | 选品返回之 **`unitBands`** 业务四槽（采购/包装/拆零/库存）供录入；**同单位码且相对拆零倍数相同**的槽在界面**合并为一项**展示，选中映射优先 **包装 → 拆零 → 库存 → 采购**；同码不同倍**不得**合并。**追溯贴码单位**（药典 `trace_unit_*` / TRACE 槽）**不进入**数量单位下拉，仅用于应扫码数等追溯逻辑（**BR-WHS-IN-014** / **BR-WHS-IN-015**）。 |
| **初始化下拉**      | **IF-WHS-IN-01** 返回之供应商、经手人（药师）、库管、药房/药库、入库项目类型、**单据状态（SupplyStatus）** 等，**一律**使用平台统一下拉项 **OptionDto**（`value` / `label` / `sort`）；**禁止**为本接口自建专用下拉 DTO |
| **枚举口径**       | 凡 **`SupplyType` / `SupplyStatus` / `SupplyRequestType` / `SupplyCategory`** 及库表 **`type_enum` / `status_enum` / `category_enum`** 等，本文与 **SDD** 对外表述一律用 **`getCode()` 字符串**（如 **`CABINET_INBOUND_RECEIPT`**、**`AGREE`**、**`CABINET`**、**`STOCK_SUPPLY`**），**不**以 sortOrder 或 DM 表内数字指代，以免与持久化混淆 |
| **DM 路径**       | 下文聚合路径均指 `docs/领域/DomainModel/` 下对应文件                                                               |


### 1.4 本期范围与非目标


| 类别           | 内容                                                                                                           |
| ------------ | ------------------------------------------------------------------------------------------------------------ |
| **本期（初稿假定）** | 进货列表、详情、新建（含从订货带入）、编辑、提交/撤回、同意/驳回、删除；**不包含** **`CABINET_INBOUND_RECEIPT_INSPECTION`** 进货验收独立菜单（若存在另 AS）                           |
| **待评审对齐**    | 与产品/UI 的「从订货生成」入口、列表筛选项、审批入口一致（**发票号**：前端**不必填**；空白落库/上报占位 **`0`**，见 **DM-WKF-006** / **09** §4.5e） |
| **非目标**      | 订货单维护（**AS-INV-WHS-001**）；发药链；**单独**的进货验收（**`CABINET_INBOUND_RECEIPT_INSPECTION`**）若产品有独立页则另 AS；**医保**侧进销存/商品采购等**对外监管接口**（遗留实现中依开关调用者）**不在**本页契约内                                           |


### 1.5 领域清单（聚合 + 职责 + DM 路径）


| 聚合（域）                       | 在本场景中的职责（摘要）                                            | DM 路径                                                         |
| ---------------------------- | ------------------------------------------------------- | ------------------------------------------------------------- |
| **SupplyRequest（WKF）**       | 进货建单（**`CABINET_INBOUND_RECEIPT`**）；审批流；逻辑删除                         | `DM-WKF-006-SupplyRequest-供应请求.md`                            |
| **TraceNumber（CUS）**         | 同意后对非空 `trace_no` 行写主档 + `HOSPITAL_IO` IN 流水（**IF-TRC-05**）           | `DM-CUS-010-TraceNumberRecord-追溯码记录.md` v2.0.4                 |
| **SupplyDelivery（WKF）**      | 入库发放事实（同意后排仓）                                           | `DM-WKF-005-SupplyDelivery-供应发放.md`                           |
| **InventoryItem（WKF）**       | 库存数量增加（同意后排仓；**账面数量为拆零/最小单位**）                                           | `DM-WKF-008-InventoryItem-库存项.md`                              |
| **收费项定义（定价）**            | 同意并发放后，按实例+批号补**批号进价/批号售价**定价行（与遗留「同批号条件去重」语义一致）；并回写实例级**采购/销售参考价**（**`PURCHASE`/`SALE`**，供下次选品默认与目录展示，**非**报表/账单真价）                         | 以 **SDD** 白名单锚定之 **DM**（计费/收费项定义）为准 |
| **Location（ADM）**            | 药库位置校验                                                  | `DM-ADM-003-Location-位置.md`                                     |
| **Supplier（CUS）**            | 供应商主数据                                                  | `DM-CUS-007-Supplier-供应商.md`                                   |
| **订货单（上游）**                 | 校验来源订货状态；带出明细语义                                       | `AS-INV-WHS-001-药库订货单.md` · `DM-WKF-006` §6.1.1                    |


### 1.6 业务规则（BR-*）


| 规则 ID             | 描述（语义锚点）                                                                                             | 绑定 IF                                      |
| ----------------- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| **BR-WHS-IN-001** | 最小建单必备字段、`type_enum` 须为 **`CABINET_INBOUND_RECEIPT`**（**`SupplyType#getCode()`**）—— 对齐 **BR-SR-001**                                                   | IF-WHS-IN-04                               |
| **BR-WHS-IN-002** | 位置合法、药库场景 —— 对齐 **BR-SR-006**                                                                       | IF-WHS-IN-04                               |
| **BR-WHS-IN-003** | 若填 `original_bus_no`：所指订货单须 **AGREE**，且数量/物品拷贝语义符合 **DM** §6.1.2（原单未闭环、非法数量拒绝） —— 关联 **BR-SR-001** + §6.1.2 | IF-WHS-IN-04 / IF-WHS-IN-12 / IF-WHS-IN-13 |
| **BR-WHS-IN-012** | 已 **`AGREE`** 且已被未删除 **`CABINET_INBOUND_RECEIPT`**（**`original_bus_no`** 指向本订货 `bus_no`）引用的订货单，**不得**再次生成进货单（**不区分**占用方进货单审批状态；**已逻辑删除**进货单不再占用） | IF-WHS-IN-04 / IF-WHS-IN-12 / IF-WHS-IN-13 |
| **BR-WHS-IN-004** | 同一 `bus_no` 状态一致 —— **BR-SR-002**                                                                    | IF-WHS-IN-05～IF-WHS-IN-10                  |
| **BR-WHS-IN-005** | 仅可编辑态修改 —— **BR-SR-003**、**BR-SR-014**                                                            | IF-WHS-IN-05                               |
| **BR-WHS-IN-006** | 提交审批 —— **BR-SR-004**                                                                               | IF-WHS-IN-06                               |
| **BR-WHS-IN-007** | 同意/驳回追溯 —— **BR-SR-005**                                                                            | IF-WHS-IN-08 / IF-WHS-IN-09                |
| **BR-WHS-IN-008** | 删除防下游引用 —— **BR-SR-010**                                                                           | IF-WHS-IN-10                               |
| **BR-WHS-IN-009** | 进货单为**执行单据**：**同意**后编排 **SupplyDelivery + 批号维定价（条件满足时）+ InventoryItem** —— **BR-SR-012**                           | IF-WHS-IN-08                               |
| **BR-WHS-IN-018** | **同表同品同批号进价一致性（与供应商无关）**：已存在 ACTIVE **`LOT_NUMBER_COST`** 且与本次归一包装进价**相同** → 允许（写路径跳过）；尚无批号进价 → 允许首次写入。若进价**不同**：仅当（1）全院该 `itemTable+itemId+lotNumber` 未删库存数量合计为 0，（2）该实例+批号定价定义下从未产生过收费行（含已退费；软删草稿除外），（3）无同品同批号发药/发耗行时允许改价（同意写路径先停用旧 ACTIVE **`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`** 再写入）；否则拒绝保存/同意并须换批号。供应单不构成阻塞。跨品种/药耗批号字面值可重复。库存按**五元组**（含供应商）入账，同批号不同供应商可拆多行；批号价仍按「品+批号」 | IF-WHS-IN-04/05/08（**SDD WHS-IN-SDD-R-018**） |
| **BR-WHS-IN-019** | **同意后目录参考价跟随本行包装进/售**：对每一明细行，以归一后**每包装**进价回写实例级 ACTIVE **`PURCHASE`** 与 **`SALE`**（同值；异值先停用再新增；同值跳过）。批号价同价跳过写库时**仍须**回写参考价，避免下次选品默认落后。参考价仅供展示与选品默认，**不**改变批号真价、**不**参与报表/已落库账单回放。同单多行同品时后行覆盖前行。院点中草药加价若抬高批号售价，须再将 **`SALE`** 对齐加价后售价（**不**改 **`PURCHASE`**） | IF-WHS-IN-08（**SDD WHS-IN-SDD-R-019**） |
| **BR-WHS-IN-010** | **同意**路径须**防重复入库编排**（语义对齐遗留：已存在本单对应**已完成发放**事实则不得再次同意入库） | IF-WHS-IN-08                               |
| **BR-WHS-IN-011** | **同意**路径在库存入账成功后：对 **`trace_no` 非空白** 之明细行须调用 **IF-TRC-05**（**`recordTraceRecords`**）：**INSERT** 子表 `HOSPITAL_IO` IN + **UPDATE/INSERT** 主档（`in_hospital_status_enum=IN_HOSPITAL`、`remaining_split_quantity` 按行实入拆零前填、`trace_unit_code`+`trace_unit_ratio` 从**药典**写入快照）；贴码单位**不**从供应行采集（表无此列）；**空白** `trace_no` 行**不**写主/子表；与库存**同事务**（**AS-CUS-TRC-001** **BR-TRC-001/005/011/019**） | IF-WHS-IN-08                               |
| **BR-WHS-IN-013** | **同意**路径追溯 Gate：同一进货单内，**多行 `trace_no` 逗号串不得出现重复码**（须合并到一行或删除重复） | IF-WHS-IN-08                               |
| **BR-WHS-IN-014** | **同意**路径追溯 Gate：**应扫码数 = ceil(本行入库拆零 / 每码药典追溯单位拆零)**（半盒允许；贴码单位取药典 `trace_unit_code`+`trace_unit_ratio`，可游离五类业务单位；**禁止**写死「1 码 = 1 盒」、**禁止**要求行拆零被每码拆零整除） | IF-WHS-IN-08                               |
| **BR-WHS-IN-015** | **库房族明细数量单位 UI**：业务四槽同码且同相对拆零倍数须合并展示；合并映射优先包装→拆零→库存→采购；落库仍为拆零数量 + 录入 `unit_code`；**TRACE/药典追溯单位不得进入数量单位下拉**（与 **BR-WHS-IN-014** 分工） | IF-WHS-IN-03 / IF-WHS-IN-04 / IF-WHS-IN-05 / IF-WHS-IN-11 |


---

## 2. 页面与接口总览（契约概览）


| IF 编号             | 用户动作      | 接口语义（业务）                                             | 读/写属性 | 关键规则（BR）               | 关键状态变化（自然语言）                                          | 涉及领域                                                                        | QueryService                                                         |
| ----------------- | --------- | ---------------------------------------------------- | ----- | ---------------------- | ----------------------------------------------------- | --------------------------------------------------------------------------- | ----------------------------------------------------------- |
| IF-WHS-IN-01      | 进入进货页面    | 初始化进货页：返回供应商列表；经手人（药师）列表；库管人员列表；**药房**与**药库**位置列表**分列**返回（字段 **`pharmacyLocationOptions`** / **`warehouseLocationOptions`**）：主数据为启用态**全库**药房/药库（**`listAllActivePharmacies` / `listAllActiveWarehouses`**，**不按请求 `organizationId` 过滤**；请求体若含该字段由服务端**忽略**），再经**操作者管辖库位**（**`CurrentManagedLocationIdsResolver#listManagedLocationIdsOrEmpty`** / **`#narrowActiveLocationsByManaged`**）**按类**与列表求交收窄——**有交则仅辖内**；**管辖 empty 过渡态不收窄**，该类仍返回**启用态全库**该类型位置（避免无配置时误收成空）；入库项目类型（以表名表达，仅「药品定义表→药品」「耗材定义表→耗材」两档）；**单据状态**（**SupplyStatus**）列表；**上述全部下拉均为 OptionDto，禁止自建专用 DTO** | 读     | —                      | —                                                     | Location / Supplier / 人员（经手人·药师、库管） / **SupplyStatus**                                         | —                                                           |
| IF-WHS-IN-02      | 查询/刷新列表   | 分页查询药库进货单（`type_enum`=`CABINET_INBOUND_RECEIPT`，筛选合并为单一查询能力）；**按业务单号 `bus_no` 合并为一张单据一行**；每行须展示：单据号、供应商、单据状态、制单人、**审批人**（展示名；同源 **`approver_id`**，未同意前可空）、总金额、**审批时间**（`approval_time`）、**申请时间**（`apply_time`，提交审批后才有值）、**制单时间**（明细 `occurrence_time` 聚合展示，见 SDD）                     | 读     | —                      | —                                                     | SupplyRequest                                                                 | **SupplyRequest**（**`wkf_supply_request`** 聚合列表真源，见 **SDD WHS-IN-SDD-R-010**；**禁止**以 **WarehouseDocumentDetail** 替代本列表）                             |
| IF-WHS-IN-03      | 查看详情      | 按传入 **`bus_no`** 查询进货单；**明细行**须含：项目名称、规格、**manufacturer**（生产厂家及产地合一）、采购数量、单位、采购单价、总价、批号、**效期区间**（**`startTime` / `endTime`**，与库 **`wkf_supply_request.start_time` / `end_time`** 及 **SDD** §3.4 **`InboundReceiptDetailLineDto`** 一致）、**追溯码**、备注；**`occurrenceTime`** 仍为期望/业务时点（与效期列语义分离）；**并**须附带与 **IF-WHS-IN-11** 同源之**多单位目录提示**（单位四槽含**包装**、换算比、**采购参考价**（价类 **`PURCHASE`** 真源）、各槽**展示单价**在返回前按**行单价+行单位**优先、否则**采购参考价+默认采购单位**等比回填）。多单位目录提示之装配与回填规则以本 AS §4 与 **SDD** 为收口                                  | 读     | —                      | —                                                     | SupplyRequest                                                                 | WarehouseDocumentDetail                                     |
| IF-WHS-IN-04      | 新建进货单     | 创建 **`CABINET_INBOUND_RECEIPT`** 草稿；须带 `original_bus_no`；初态 **`PENDING_APPROVAL`**；**明细行可选传入追溯码**（落 **`wkf_supply_request.trace_no`**，长度口径 **TRC-WIDE-R-001**）；**明细行可选传入效期**（**`startTime` / `endTime`** → **`start_time` / `end_time`**）             | 写     | BR-WHS-IN-001～003 / **012**        | 新 `bus_no`；`PENDING_APPROVAL`                          | SupplyRequest / Location / Supplier                                             | 列表/详情刷新                                                    |
| IF-WHS-IN-05      | 编辑进货单     | 可编辑态下改明细或验收字段（**含追溯码**；落库 **`wkf_supply_request.trace_no`**，长度口径 **TRC-WIDE-R-001**）；**含效期**（**`startTime` / `endTime`**）                                        | 写     | BR-WHS-IN-005             | 明细/验收字段变更                                             | SupplyRequest                                                                 | 同上                                                          |
| IF-WHS-IN-06      | 提交审批      | 待审核 → 审核中                                              | 写     | BR-WHS-IN-006             | `PENDING_APPROVAL` → `APPROVAL`                         | SupplyRequest                                                                 | 待办、列表刷新                                                     |
| IF-WHS-IN-07      | 撤回审批      | 审核中撤回                                                 | 写     | —                      | `APPROVAL` → `WITHDRAW` / `PENDING_APPROVAL`（以 **DM** 为准） | SupplyRequest                                                                 | 同上                                                          |
| IF-WHS-IN-08      | 审批同意      | 审批通过                                                   | 写     | BR-WHS-IN-007 / BR-WHS-IN-009 / **BR-WHS-IN-010** / **BR-WHS-IN-011** / **013** / **014** | `AGREE`；**触发入库发放 + 批号维定价补录 + 库存增加（拆零单位）**；**`trace_no` 非空行 → IF-TRC-05**                            | SupplyRequest → SupplyDelivery / 定价行 / InventoryItem / **TraceNumber**（**AS-CUS-TRC-001** 编排）                             | WarehouseDocumentDetail / 库存相关QueryService                               |
| IF-WHS-IN-09      | 审批驳回      | 审批拒绝                                                   | 写     | BR-WHS-IN-007             | `REJECT`                                                | SupplyRequest                                                                 | 同上                                                          |
| IF-WHS-IN-10      | 删除单据      | 逻辑删除                                                   | 写     | BR-WHS-IN-008             | 逻辑删除标记                                                  | SupplyRequest                                                                 | QueryService隐藏                                                       |
| IF-WHS-IN-11      | 采购选品检索    | **手动检索**目录分页：以 **ProductDefinition**（**Q-PD-004**）为主体；按 **`product_table_name`** 区分药品/耗材；返回名称、业务编号、规格、**生产厂家及产地**（QueryService **`manufacturer`** 单列合一承载）、默认单位与拆零单位、**物资分类编码及展示名**（**`categoryCode` / `categoryCodeDictText`**）、**单位槽列表 `unitBands`**（**采购 / 包装（`bandKind`=`PACKAGE`，QueryService `package_unit_code`）/ 拆零 / 库存** 四槽；**`ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands`**；与门诊目录 **`unitBands`** 字段同构；**不含**剂量槽；各槽 **`unitPrice`** 以目录 **`PURCHASE`（每包装）** 为锚经 **`#applyInboundWarehouseBandPrices`** 装配）、**采购单价**（JSON 字段名沿用 **`purchasePrice`**；展示值**必须**来自定价表价类 **`PURCHASE`**（**每包装单位进价**），见 **SDD WHS-IN-SDD-R-005**，**禁止**取 **`SALE`**，**不得**以QueryService **`purchase_price`** 列冒充本接口契约真源）；**`availableQuantity`** 在进货选品**不装配**（恒空/`null`，字段仅保留与前端 JSON 兼容）；**从订货单拉取明细**走 **IF-WHS-IN-03/04** 之订货关联读路径，**不要求**与本 IF 合并为一个 HTTP，但**列字段**应与选品行对齐以便前端同构；**进货明细行上单价的后续修改**属 **IF-WHS-IN-04/05**，与选品列表返回解耦 | 读     | —                      | —                                                     | **ProductDefinition** / **定价（选品列表 `PURCHASE` 每包装）**                                                             | **ProductDefinition**；定价由应用层二次装配                                       |
| IF-WHS-IN-12      | 加载可关联订货单列表 | 用于「从订货生成进货」弹窗：分页列出本药库已 **AGREE**、且**未被未删除进货单引用**的订货单（**BR-WHS-IN-012**）；**按 `bus_no` 聚合一行一单** | 读     | **BR-WHS-IN-012**                      | —                                                     | SupplyRequest（读口径）                                                           | **`SupplyRequestQueryService`** / 专用 exists 查询                             |


> **说明**：**IF-WHS-IN-11**、**IF-WHS-IN-12** 为**必选契约**（页面须具备**手动**选品检索与可关联订货单查询能力；**订货行导入**可与详情/建单读路径组合，列集合与 **IF-WHS-IN-11** 对齐）。实现上可将 IF-WHS-IN-12 的查询与 **IF-WHS-IN-04** 同屏合并（例如建单入参直接带订货 `bus_no`），但须保留等价查询语义并在 §4、测试中可追溯。**免申请直建**进货单不调用 IF-WHS-IN-12，仅依赖 **IF-WHS-IN-04** 无 `original_bus_no` 路径（见 §1.1、§1.3「免申请进货」）。


---

## 3. 缺口跟踪（Gap）


| Gap ID     | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态  |
| ---------- | ---- | ------- | ----------- | ---- | ----- | --- | --- |
| **无待跟踪缺口** | —    | —       | —           | —    | —     | —   | —   |


---

## 4. 接口编排说明书（业务步骤）

### IF-WHS-IN-01 进入进货页面初始化


| 维度          | 说明                                  |
| ----------- | ----------------------------------- |
| **触发前提**    | 用户进入药库进货菜单                          |
| **主路径步骤**   | 加载供应商列表；经手人（药师）/库管人员经 **`PractitionerRoleScopeLoader#listActivePractitionersForRoleInOrganization`**（**WhaleRoleLookup** 岗位码 + 会话机构）；**分别**加载**药房**/**药库**位置（**`listAllActivePharmacies`/`listAllActiveWarehouses`** + **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`**）；加载入库项目类型两档与 **SupplyStatus**；**各下拉均为 OptionDto** |
| **关键分支**    | 药房列表、药库列表：**已配置非空管辖**且与主数据**有交**则各自仅返回交集中项；**管辖为空或无交**则各自返回该类型**启用态全库**（不收窄成空）                                     |
| **失败分支**    | 主数据不可用（EX-WHS-IN-101）                  |
| **查询侧说明** | 无                                   |


---

### IF-WHS-IN-02 进货单列表查询


| 维度          | 说明                                                  |
| ----------- | --------------------------------------------------- |
| **触发前提**    | 筛选、翻页、刷新                                            |
| **主路径步骤**   | 按 `type_enum`=`CABINET_INBOUND_RECEIPT`、药库位置、日期/状态/关键字等筛选后，**以 `bus_no` 聚合**：同一业务单号合并为**一条**列表记录；行内展示单据号、供应商、单据状态、制单人、**审批人**（展示名；与 **`approver_id`** 一致，未审批可空）、总金额（各明细行金额汇总）、**审批时间**、**申请时间**、**制单时间**（三列语义与 **§1.3** 术语一致）；分页以**合并后的单据条数**为维度 |
| **关键分支**    | 无                                                     |
| **失败分支**    | EX-WHS-IN-102                                       |
| **查询侧说明** | 列表展示                                                |


---

### IF-WHS-IN-03 进货单详情


| 维度          | 说明                               |
| ----------- | -------------------------------- |
| **触发前提**    | 从列表进入或深链                         |
| **主路径步骤**   | 按 **`bus_no`** 加载该单全部明细行；每行展示：项目名称（**`productName`**）、规格（**`specification`**，QueryService **`total_volume`**）、**`manufacturer`**（生产厂家及产地合一）、采购数量、单位、采购单价、总价、批号、**效期**（**`startTime` / `endTime`**，可空）、**`occurrenceTime`**（期望时点，与效期语义分离）、**追溯码**、备注；**单头**回显 **`invoiceNo`**（采购发票号，同号多行共享，读侧取任一行）；**多单位目录提示**（与 **IF-WHS-IN-11** 同源：四槽含包装、换算比、**`PURCHASE`** 采购参考价、各槽展示单价之回填规则）与 **§2 IF-WHS-IN-03** 表述一致 |
| **关键分支**    | 无                                  |
| **失败分支**    | 不存在、非 **`CABINET_INBOUND_RECEIPT`**、无权限（EX-WHS-IN-103） |
| **查询侧说明** | 详情展示                             |


---

### IF-WHS-IN-04 新建进货单


| 维度          | 说明                                                                                                                                                                       |
| ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **触发前提**    | 药库上下文有效                                                                                                                                                                 |
| **主路径步骤**   | **若**用户指定来源订货单：先校验订货已 **`AGREE`**，再按 **DM-WKF-006** §6.1.2 复制来源明细并生成新进账单号、写入 `original_bus_no`，用户可继续补充批号/**追溯码**/价格/**效期（`startTime`/`endTime`）**/**发票号（单头 `invoiceNo`，同号多行）** 等验收信息；**否则**免申请直建 **`CABINET_INBOUND_RECEIPT`** 草稿；**追溯码**可选录入，持久化 **`wkf_supply_request.trace_no`**（列宽 **TRC-WIDE-R-001**）；效期可选录入，持久化 **`wkf_supply_request.start_time` / `end_time`**；**发票号**前端**不必填**，空白时落库占位 **`0`**（同号共享 **`wkf_supply_request.invoice_no`**）；进销存 3503 同占位 **`0`** |
| **关键分支**    | **若**关联订货：**BR-WHS-IN-003** / **BR-WHS-IN-012** 不满足则拒绝（含「已被其它未删除进货单占用」）                                                                                                                                       |
| **失败分支**    | 校验失败（EX-WHS-IN-001）；原单不可用（EX-WHS-IN-002）                                                                                                                                   |
| **查询侧说明** | **SupplyRequestCreatedEvent**                                                                                                                                            |
| **下游编排意图**  | 仅草稿；**无**库存变动直至审批同意                                                                                                                                                      |


---

### IF-WHS-IN-05 编辑进货单


| 维度          | 说明                         |
| ----------- | -------------------------- |
| **触发前提**    | 单据处于可修订态（**`PENDING_APPROVAL`/`REJECT`/`WITHDRAW`**，**BR-SR-003**），且通过可编辑门控；保存为同号软删重插，新行状态回落 **`PENDING_APPROVAL`** |
| **主路径步骤**   | 更新明细与验收字段（**含追溯码**；**TRC-WIDE-R-001**）；**含效期**（**`startTime`/`endTime`** → **`start_time`/`end_time`**）；**含单头发票号**（**`invoiceNo`** → 各行 **`invoice_no`**）                  |
| **关键分支**    | 无                              |
| **失败分支**    | EX-WHS-IN-004 / EX-WHS-IN-105   |
| **查询侧说明** | 写成功后刷新                       |


---

### IF-WHS-IN-06 提交审批


| 维度          | 说明                                                |
| ----------- | ------------------------------------------------- |
| **触发前提**    | 待审核                                               |
| **主路径步骤**   | 提交审批 → 审核中                                        |
| **关键分支**    | 无                                                   |
| **失败分支**    | EX-WHS-IN-003                                       |
| **查询侧说明** | **SupplyRequestSubmittedEvent**                   |


---

### IF-WHS-IN-07 撤回审批


| 维度          | 说明                           |
| ----------- | ---------------------------- |
| **触发前提**    | 审核中                          |
| **主路径步骤**   | 撤回（状态机以 **DM** 为准）           |
| **关键分支**    | 无                              |
| **失败分支**    | EX-WHS-IN-003                  |
| **查询侧说明** | **SupplyRequestWithdrawnEvent** |


---

### IF-WHS-IN-08 审批同意


| 维度          | 说明                                                                                                                                                    |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| **触发前提**    | 单据处于**审核中**（**`APPROVAL`**，**`SupplyStatus#getCode()`**）；当前操作者具备审批权限；**防重复同意**（含「本单已存在已完成入库发放事实则拒绝再次同意」—— **BR-WHS-IN-010**，语义对齐遗留系统）                                                                                                            |
| **主路径步骤**   | ① **供货请求**聚合进入 **同意**终态（**`AGREE`**），落 **审批人**、**审批时间**（与 **DM-WKF-006** §3.1 **`approver_id` / `approval_time`** 一致）；发布 **SupplyRequestAgreedEvent**。② **同一业务事务内**，应用层按明细行**顺序**编排：**先**形成 **SupplyDelivery（入库发放）** 事实。③ **批号维定价 + 目录参考价**：批量加载 **ProductDefinition** 后，对每一明细行：批号进/售按 **BR-WHS-IN-018** / **SDD WHS-IN-SDD-R-006** 写入或同价跳过；**无论批号是否跳过**，均以本行归一包装进价回写实例级 **`PURCHASE`/`SALE`**（**BR-WHS-IN-019** / **WHS-IN-SDD-R-019**；参考价仅展示与下次选品默认）。**落库单价**均为**每包装单位**价。行 **`price` 缺失**时按零写入。④ **库存**：对 **InventoryItem** 执行「无则建批、有则加量」分支（五元组命中规则以 **DM-WKF-008** 为准）；**入账数量与库存项计量单位**必须为**拆零（最小）单位**——**`item_quantity`** 已为拆零持久化量，**禁止**再按行上 **`unit_code`** 做入账倍数换算；直至本单全部行处理完毕（**BR-WHS-IN-009** / **BR-SR-012**）。⑤ **追溯码 Gate（写库前）**：整单校验 **BR-WHS-IN-013**（跨行码不重复）、**BR-WHS-IN-014**（应扫码数 = ceil(行拆零/每码药典追溯单位拆零)，半盒允许）。⑥ **追溯码写库**：在库存侧写库成功后，对 **`trace_no` 非空白** 之明细行，按 **BR-WHS-IN-011** 调用 **IF-TRC-05**（**`TraceNumberRecordOrchestrator#recordFromSupplyLine`** → **`recordTraceRecordsFromLineItems`**）：**建/更新主档**（写入药典 `trace_unit_code`+`trace_unit_ratio` 快照）+ **子表 `HOSPITAL_IO` IN**（异阶前填实入拆零，见 **AS-CUS-TRC-001** **BR-TRC-019**）；`operation_type` = **`CABINET_INBOUND_RECEIPT`**；贴码单位取药典（**不**读供应行 `trace_unit_code`）。⑦ 投影与QueryService在事件后收敛为**单头已同意 + 定价可查 + 库存已增加 + 详情可见追溯码**的可查状态。 |
| **关键分支**    | **发放、定价或库存任一步失败**：默认**整单回滚**，不得出现「头表已 **同意** 但下游事实不完整」的对外可见不一致；若采用异步重试，须在 **§7** 单独声明并与 **EX-WHS-IN-104** 对齐。 |
| **失败分支**    | EX-WHS-IN-003 / EX-WHS-IN-104                                                                                                                         |
| **查询侧说明** | 单据投影更新；库存侧QueryService随发放/库存事件更新                                                                                                                               |

**审批通过逻辑（摘要）**：本 IF 是**执行单据**闭环的关键相位——**同意**一旦在聚合上提交成功，后续 **入库发放 +（条件满足时）批号维定价 + 拆零单位库存入账 +（`trace_no` 非空行）IF-TRC-05 主子表写入**须视为**同一原子业务单元**的延续；任一行失败，应对外表现为**本次同意未生效**（或进入已约定的补偿态），与 **DM-WKF-006** §6.1.14 执行语义一致。


---

### IF-WHS-IN-09 审批驳回


| 维度          | 说明                             |
| ----------- | ------------------------------ |
| **触发前提**    | 审核中                            |
| **主路径步骤**   | 驳回至 **`REJECT`**；请求体 **`rejectReason`** 经领域写入同号各行 **`reason`**；列表行回显 **`rejectReason`**；单据仍可修订后再提交 |
| **关键分支**    | 无                                |
| **失败分支**    | EX-WHS-IN-003                  |
| **查询侧说明** | **SupplyRequestRejectedEvent** |


---

### IF-WHS-IN-10 删除单据


| 维度          | 说明                            |
| ----------- | ----------------------------- |
| **触发前提**    | 同号全部明细处于 **`PENDING_APPROVAL`** / **`REJECT`** / **`WITHDRAW`**（**`SupplyStatus#getCode()`**）；且满足 **DM-WKF-006** 逻辑删除其余约束（如 **BR-SR-010** 下游引用，由实现侧跨域校验） |
| **主路径步骤**   | 逻辑删除整单                        |
| **关键分支**    | 无                               |
| **失败分支**    | EX-WHS-IN-005                   |
| **查询侧说明** | **SupplyRequestDeletedEvent** |


---

### IF-WHS-IN-11 采购选品检索


| 维度          | 说明                                                                                                                                                                                                                                                                                                                                                    |
| ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **触发前提**    | 编辑/新建时打开**手动**选品；须传入**目的药库** `purposeLocationId`（与进货单 **`purpose_location_id`** 语义一致，用于**药库门禁**（须 ∈ **「当前用户可选药库」** id 集合，与 **IF-WHS-IN-01** **`warehouseLocationOptions`** 同源：**`listAllActiveWarehouses`** + **`CurrentManagedLocationIdsResolver#gatedLocationIdsFromActive`**；**管辖 empty（过渡态）则门控集合为启用态药库全量 id**）；**`productTableName`** 与 **IF-WHS-IN-01** 入库项目类型白名单一致（**`med_medication_definition`** / **`adm_device_definition`** / 空=不限）                                                                                                                                                                                          |
| **主路径步骤**   | ① **Q-PD-004** 分页检索 **ProductDefinition**（契约见 **DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService** §3；`includeInventory` 等QueryService开关与实现一致）；② 按 **SDD WHS-IN-SDD-R-005**，以定价表 **`PURCHASE`**（**每包装单位进价**）写入行上 **`purchasePrice`**，并以该价为包装锚经 **`#applyInboundWarehouseBandPrices`** 写 **`unitBands`[].`unitPrice`**（**禁止**取 **`SALE`**）；③ **不**调用 **`InventoryItemQueryService`** 汇总可用量，行上 **`availableQuantity`** 恒空；④ 组装返回行：名称、业务编号、规格、**`manufacturer`**（生产厂家及产地合一，QueryService **`manufacturer`** 列）、**`categoryCode`/`categoryCodeDictText`**、默认/拆零单位、**`unitBands`**（**采购 / 包装（`PACKAGE`，QueryService `package_unit_code`）/ 拆零 / 库存** 四槽，**`ProductDefinitionCatalogUnitBandsAssembler#buildInboundSelectionBands`**；与门诊目录 **`unitBands`** 字段同构）、各槽展示单价、**`purchaseSplitRatioText`**（服务端拼装**采购最小单位换算比**展示，形如 **`1箱/3桶`**；同单位×1 为 **`1个`**；与库存明细 **`packageSplitRatioText`** 同族，单位取采购槽/拆零槽）。**展开口径**：按**产品定义一行**（非库存批号行），选品列表**不**展示批号（批号在明细录入；批号级选品 UI 必显批号见 **AS-INV-WHS-003 §1.3**） |
| **关键分支**    | **`productTableName` 非空**时须与行数据一致；**免定价**时单价为空与 UI 提示由实现与 **SDD** 统一                                                                                                                                                                                                                                                                                                      |
| **失败分支**    | EX-WHS-IN-102                                                                                                                                                                                                                                                                                                                                         |
| **查询侧说明** | 无                                                                                                                                                                                                                                                                                                                                                     |
| **非目标**     | 本 IF **不**承担「保存进货明细单价修订」；用户于表单中改价属于 **IF-WHS-IN-04/05**                                                                                                                                                                                                                                                                                                      |


---

### IF-WHS-IN-12 加载可关联订货单列表


| 维度          | 说明                                   |
| ----------- | ------------------------------------ |
| **触发前提**    | 用户点击「从订货生成」                          |
| **主路径步骤**   | 传入 **`purposeLocationId`**（订货目的药库，与订货写库 `purpose_location_id` / **IF-WHS-PO-02** 一致）；分页返回已 **AGREE** 订货单，**按 `bus_no` 聚合一行一单**（同 **IF-WHS-PO-02** 列表真源）；**排除**已被未删除 **`CABINET_INBOUND_RECEIPT`**（**`original_bus_no`** 指向该订货）引用的单号（**BR-WHS-IN-012**）；行内含 **`supplierName`** 等展示字段     |
| **关键分支**    | 订货已被占用或不符合 **BR-WHS-IN-012** 时不出现在列表；无符合条件时返回空页 |
| **失败分支**    | EX-WHS-IN-102                        |
| **查询侧说明** | 弹窗展示                                 |
| **合并说明**    | 可与 **IF-WHS-IN-04** 同请求完成，但须满足本 IF 的查询语义（§2 说明） |


---

## 5. 业务异常与用户提示


| 语义代码          | 场景        | 用户提示方向        | 可重试 |
| ------------- | --------- | ------------- | --- |
| EX-WHS-IN-001 | 建单/复制校验失败 | 按提示补全或修正来源订货 | 是   |
| EX-WHS-IN-002 | 订货单不可引用   | 原单未同意、或**已被其他进货单引用**（**BR-WHS-IN-012**）   | 视原因 |
| EX-WHS-IN-003 | 状态不允许操作   | 刷新后重试         | 是   |
| EX-WHS-IN-004 | 禁止编辑      | 说明原因          | 否   |
| EX-WHS-IN-005 | 删除被拒绝     | 下游引用或状态原因     | 否   |
| EX-WHS-IN-101 | 主数据加载失败   | 稍后重试          | 是   |
| EX-WHS-IN-102 | 列表/选品查询失败 | 缩小条件          | 是   |
| EX-WHS-IN-103 | 详情不可用     | 返回列表          | 视情况 |
| EX-WHS-IN-104 | 同意后排仓失败（含发放、**批号维定价**、**拆零换算**、库存入账或**追溯写库**失败）   | 联系管理员/查看库存锁定   | 视原因 |
| EX-WHS-IN-105 | 并发编辑冲突    | 刷新后重试         | 是   |


---

## 6. 事件语义与消费方


| 事件（以 **DM-WKF-006** §5 为准）       | 触发相位（IF）                | 典型消费方                       |
| ------------------------------- | ----------------------- | --------------------------- |
| `SupplyRequestCreatedEvent`     | IF-WHS-IN-04            | 库房单据明细QueryService、待办                |
| `SupplyRequestSubmittedEvent`   | IF-WHS-IN-06            | 待办、QueryService 列表查询                     |
| `SupplyRequestWithdrawnEvent`   | IF-WHS-IN-07            | QueryService 列表查询                        |
| `SupplyRequestAgreedEvent`      | IF-WHS-IN-08            | QueryService 列表查询；**触发入库发放编排**（应用层）       |
| `TraceNumberRecordAppendedEvent`（及批量等价事件，以 **DM-CUS-010** §5 为准） | IF-WHS-IN-08 **BR-WHS-IN-011** 成功相位 | 追溯查询、审计（以 **DM-CUS-010** 消费方为准） |
| `SupplyRequestRejectedEvent`    | IF-WHS-IN-09            | QueryService 列表查询                        |
| `SupplyRequestDeletedEvent`     | IF-WHS-IN-10            | QueryService隐藏                       |
| SupplyDelivery / Inventory 侧事件 | IF-WHS-IN-08 下游编排成功之后 | 库存QueryService、财务/库房统计（以各 DM §5 为准） |


---

## 7. 非功能性需求


| 类别      | 要求                                                  |
| ------- | --------------------------------------------------- |
| **性能**  | 列表/详情可接受；同意后排仓链路可异步但最终一致                         |
| **幂等**  | 同意/驳回/提交短时重复请求幂等或明确已处理                              |
| **并发**  | 同一单据并发编辑乐观控制                                        |
| **一致性** | 审批同意与库存增加、**非空追溯码之轨迹落库**对外可查一致；排仓失败需可审计补偿                         |
| **审计**  | 审批人、审批时间、验收字段变更可追溯                                   |


---

## 8. 测试要点（业务视角）


| 类别      | 要点                                                                    |
| ------- | --------------------------------------------------------------------- |
| **正向**  | 免申请进货 → 提交 → 同意 → 库存增加可查、**`trace_no` 非空行主/子表可查（IF-TRC-05）**；从订货生成 → 同意 → 来源可追溯                                  |
| **列表/详情** | **IF-WHS-IN-02**：同一 **bus_no** 多明细合并为列表一行，总金额=明细价税合计，**审批人**与 **`approver_id`** 一致；**IF-WHS-IN-03**：按 **bus_no** 返回明细列（项目名称、规格、**manufacturer**（生产厂家及产地合一）、采购数量、单位、采购单价、总价、批号、**`startTime`/`endTime`（效期）**、**追溯码**、备注） |
| **规则**  | **BR-WHS-IN-003** 关联订货；**BR-WHS-IN-009** 同意后入库；**BR-WHS-IN-011** 非空 **追溯码** 轨迹                                            |
| **选品** | **IF-WHS-IN-11**：**`productTableName`** 过滤药品/耗材；定价表 **`PURCHASE`**（每包装）为采购单价与槽价锚；**`manufacturer`** 列合一厂家与产地展示 |
| **异常**  | 订货未同意引用；非法数量；不可编辑态修改                                                 |
| **并发**  | 双开同意/删除                                                           |
| **跨场景** | 与 **AS-INV-WHS-001** 订货单号 **一致**；**original_bus_no** 与订货 **bus_no** 对齐 |


---

## 9. 关联文档与变更历史

### 9.1 关联文档


| 文档                                                                                       |
| ---------------------------------------------------------------------------------------- |
| `docs/领域/DomainModel/DM-WKF-006-SupplyRequest-供应请求.md`               |
| `docs/领域/DomainModel/DM-WKF-005-SupplyDelivery-供应发放.md`             |
| `docs/领域/DomainModel/DM-WKF-008-InventoryItem-库存项.md`               |
| `docs/领域/DomainModel/DM-ADM-003-Location-位置.md`                       |
| `docs/领域/DomainModel/DM-CUS-007-Supplier-供应商.md`                     |
| `docs/领域/DomainModel/DM-CUS-010-TraceNumberRecord-追溯码记录.md`          |
| `DM §6.2（DM-WKF-006 §6.2 `SupplyRequestQueryService` / `InventoryItemQueryService``             |
| `DM §6.2（DM-MED-001 / DM-WKF-001 / DM-ADM-008 §6.2 目录 QueryService`                  |
| `docs/应用/zxplat-application/AppService/AS-INV-WHS-001-药库订货单.md                          |
| `docs/应用/zxplat-application/AppService-SDD/AS-INV-WHS-002-药库进货单-SDD.md`（下游实现展开，**v2.33**） |


| v0.41 | 2026-08-24 | **BR-WHS-IN-018**：同批号异价仅在零库存且无收费/发药发耗时允许（同意写路径停用旧 ACTIVE 再写入）；同价跳过；下游 **SDD v2.43** |
| v0.40 | 2026-08-19 | **发票号**：前端**不必填**；空白落库/上报占位 **`0`**（对齐 **09** §4.5e）；撤销「前端暂定必填」 |
| v0.39 | 2026-07-26 | **BR-WHS-IN-015** / §1.3：库房族数量单位下拉同码同倍合并（包装→拆零→库存→采购）；TRACE 不进数量单位下拉；落库口径不变 |
| v0.38 | 2026-07-23 | **追溯贴码单位改药典**：进货单据不采；**删除** `wkf_supply_request.trace_unit_code`；同意 Gate **BR-WHS-IN-014** 改为 ceil；写主档含 `trace_unit_ratio` 快照（对齐 **AS-CUS-TRC-001** v2.0） |
| v0.37 | 2026-07-19 | **发票号**：前端暂定必填；后端仍不拦空；上报空→`0`（对齐 **09** §4.5e）；§1.4 待评审项收敛 |
| v0.36 | 2026-07-19 | **发票号**：单头 **`invoiceNo`**（IF-WHS-IN-03/04/05）；落 **`invoice_no`** 同号多行；进销存 3503/3504 |
| v0.35 | 2026-07-08 | **IF-WHS-IN-08**：引入追溯 Gate **BR-WHS-IN-013/014** 与 IF-TRC-05（**口径已被 v0.38 取代**：现为药典贴码单位 + ceil/前填实入） |
| v0.34 | 2026-07-06 | **追溯目标态对齐**：**IF-WHS-IN-08** 同意路径改 **IF-TRC-05**（主子表 **`recordTraceRecords`**）；**BR-WHS-IN-011** 增补 **`trace_unit_code`**；涉及域改 **DM-CUS-010 v2.0.4**；编排权威 **AS-CUS-TRC-001** / **AS-CUS-TRC-001-SDD** |
| v0.33 | 2026-05-29 | **库房读锚两档**：单据选品/详情槽价仅 **`PURCHASE`** + **`LOT_NUMBER_COST`**；**`LOT_NUMBER_PRICE`** 同意入库仅写库；§4 **IF-WHS-IN-08** ③；下游 **SDD v2.34** |
| v0.32 | 2026-05-29 | **定价包装锚最终版**：维护态 **`PURCHASE`/`SALE`** 与批号 **`LOT_NUMBER_*`** 均为**每包装单位**价；**IF-WHS-IN-11** 以 **`PURCHASE`** 为锚写槽（**禁止 `SALE`**）；**IF-WHS-IN-08** 批号落库经行价归一为每包装；§1.1、§2、§4 **IF-WHS-IN-08/11**、§9.3；下游 **SDD v2.33** |
| v0.31 | 2026-05-29 | **IF-WHS-IN-08**：批号维定价落库单价改为**每 PACKAGE 单位**；供应行价同意时归一后写入 **`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`**（**SDD WHS-IN-SDD-R-006**）；§4 **IF-WHS-IN-08** 主路径 ③ |
| v0.30 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v0.1 | 2026-05-02 | 初稿：药库进货（**`CABINET_INBOUND_RECEIPT`**）全 IF；与 **AS-INV-WHS-001** 分工及 **DM** 执行单同意后入库语义对齐 |
| v0.2 | 2026-05-03 | **IF-WHS-IN-11**、**IF-WHS-IN-12** 由可选改为必选契约；§2 说明与 IF→DM 追溯补充；明确免申请直建不依赖 IF-WHS-IN-12 |
| v0.3 | 2026-05-03 | 对齐《应用服务文档生成提示词模板》：§1.1 增补 **涉及域**；§4 各 IF 补 **关键分支**；去除 §4/追溯表中的代码级方法名与服务名；§9.1 关联下游 **SDD** |
| v0.4 | 2026-05-03 | **IF-WHS-IN-12** 契约参数 **`purposeLocationId`**（对齐实现及 **AS-INV-WHS-002-SDD v2.3**） |
| v0.5 | 2026-05-11 | 规范性：§3「无缺口」表行收敛；**IF→DM 追溯摘要** 移至 **§9.3**（§4 仅保留 IF 编排小节）；§6 事件表格式微调；与下游 **SDD v2.4** 编排阶段用语对齐 |
| v0.6 | 2026-05-11 | 与下游 **SDD v2.5** 对齐：实现侧文档锚点统一为 **`InboundReceipt`** 命名（包路径 **`…/inboundreceipt/…`**，无 **`inbounds`** 段）；**§4 仍不写** Java 类/方法名 |
| v0.7 | 2026-05-13 | **IF-WHS-IN-01**：初始化返回供应商、经手人（药师）、库管、药房/药库（按登录者管辖过滤，无管辖则全量）、入库项目类型（仅药品定义表/耗材定义表两档）；§1.1/§1.3 术语与涉及域同步；下游 **SDD v2.6** |
| v0.8 | 2026-05-13 | **IF-WHS-IN-01**：增补 **SupplyStatus** 单据状态下拉；**全部初始化下拉统一 OptionDto**，禁止自建专用下拉 DTO；§1.1/§1.3、§2、§4 同步；下游 **SDD v2.7** |
| v0.9 | 2026-05-13 | **IF-WHS-IN-02**：列表按 **`bus_no` 合并**为单行，展示单据号/供应商/单据状态/制单人/总金额/审批时间/制单时间；**IF-WHS-IN-03**：详情按 **`bus_no`**，明细列项目名称/规格/厂家产地/采购数量/单位/采购单价/总价/批号/效期/备注；§2、§4 同步；下游 **SDD v2.8** |
| v0.10 | 2026-05-13 | **枚举口径**：类型/状态等一律与代码 **`getCode()`** 对齐（如 **`CABINET_INBOUND_RECEIPT`**、**`CABINET_PURCHASE_ORDER`**、**`AGREE`**），**不**以 sortOrder 或数字指代；修正 **`CABINET_GOODS_RECEIPT`** 笔误；§1.3 增补 **枚举口径** 行；下游 **SDD v2.9** |
| v0.11 | 2026-05-13 | **IF-WHS-IN-11**：选品返回补齐名称/编号/规格/厂家（**`manufacturer`**）/产地/单位与**换算比列表**/目的库**库存汇总**/**采购单价**；采购价**必须**取自**定价表**价类 **`PURCHASE`**，**禁止**以产品QueryService冗余采购价列为契约真源；明确**手动检索**与**订货行导入**关系及与 **IF-WHS-IN-04/05** 改价解耦；§2、§4、§8、§9.3 同步；下游 **SDD v2.10** |
| v0.12 | 2026-05-13 | **IF-WHS-IN-02**：列表行增加**审批人**（展示名，同源 **`approver_id`**）；**IF-WHS-IN-08**：补充**审批通过**业务编排与失败一致性说明；新建/编辑/删除主语义保持 v0.11，传参口径以 **SDD** 微调为准；§2、§4、§8、§9.3 同步；下游 **SDD v2.11** |
| v0.13 | 2026-05-13 | **IF-WHS-IN-08**：按遗留 **`purchaseInventoryApproved`** 语义补全编排——**防重复发放**（**BR-WHS-IN-010**）、**发放 → 批号维定价（`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`，同批号去重）→ 库存拆零单位入账**；§1.1/§1.4/§1.5/§2/§4/§5/§6/§9.1/§9.3；**非目标**明确不含医保上传；下游 **SDD v2.12** |
| v0.14 | 2026-05-13 | **追溯码闭环**：**IF-WHS-IN-03** 详情必含追溯码；**IF-WHS-IN-04/05** 支持追溯码入参（**`wkf_supply_request.trace_no`**）；**IF-WHS-IN-08** 同意后 **`cus_trace_number` + `cus_trace_number_flow`**（**BR-WHS-IN-011**）；**TRC-WIDE-R-001**（**`trace_no` 列 VARCHAR(50000)**）与 **DM-WKF-006** / **DM-CUS-010** / **SDD v2.13** 对齐；§1.1/§1.3/§2/§4/§5/§6/§8/§9.1/§9.3 |
| v0.15 | 2026-05-13 | **IF-WHS-IN-01**：药房/药库**分列**返回（**`pharmacyLocationOptions`** / **`warehouseLocationOptions`**）；**IF-WHS-IN-11** 选品行 **厂家+产地** 合一 **`manufacturer`**；**Gate** 与 **IF-WHS-IN-01** 药库下拉同源；§2/§4/§8/§9.3；下游 **SDD v2.15** |
| v0.16 | 2026-05-13 | **IF-WHS-IN-02** 列表读投影与 **WHS-IN-SDD-R-010**（**`wkf_supply_request`**）一致；**IF-WHS-IN-03** 详情明细 **`InboundReceiptDetailLineDto`** 与 **SDD** §3.4 字段对齐（**`productName`/`specification`/`manufacturer`**）；**效期**暂不契约返回；§1.1/§4/§8/§9.1/§9.3；下游 **SDD v2.16** |
| v0.17 | 2026-05-13 | **IF-WHS-IN-01**：药房/药库下拉主数据为**启用态全库**（**`listAllActivePharmacies` / `listAllActiveWarehouses`**）+ **按类**岗位收窄（**无交则该类仍全量**）；请求 **`organizationId`** 忽略；**IF-WHS-IN-11**：**`unitBands`**（与门诊 **`ProductDefinitionCatalogUnitBandDto`** 同构子集）+ **`categoryCodeDictText`**；§1.1、§4、§9.1；下游 **SDD v2.17**；交叉 **AS-OP-DOR-003-SDD v3.3** |
| v0.18 | 2026-05-13 | **IF-WHS-IN-10**：删除前置 **`SupplyStatus`** 门控——仅 **`PENDING_APPROVAL`** / **`REJECT`** / **`WITHDRAW`**；对齐 **DM-WKF-006** §6.1.16 **BR-SR-011** 与 **SupplyRequestDomainService#removeByBusNo**；§4；下游 **SDD v2.18** |
| v0.19 | 2026-05-14 | **效期行级字段**：**`wkf_supply_request`** 增 **`start_time`/`end_time`**；**IF-WHS-IN-03** 详情与 **IF-WHS-IN-04/05** 写路径承载 **`startTime`/`endTime`**；**SupplyRequestCreatedEvent** / **`query_warehouse_document_detail`** 投影列对齐；同意入库编排以 **`endTime`** 写入库存项 **`expirationDate`**（未填则空）；§2、§4、§8 |
| v0.20 | 2026-05-14 | **文档补全**：**§1.3 验收字段** 显式含效期区间（**`startTime`/`endTime`** ↔ **`start_time`/`end_time`**）；**IF-WHS-IN-04/05** 主路径写明效期写库与新建/编辑一致 |
| v0.21 | 2026-05-14 | **§1.3**：新建 **`bus_no`** 按 **`SupplyType`** 分桶前缀（**`SQ_` + `getCode()`**）；与 **AS-INV-WHS-001** 及 **SDD AS-INV-WHS-002-SDD v2.19** 对齐 |
| v0.22 | 2026-05-14 | **`bus_no` 序号键**：改为 **`busNoSeqKey`** 短码（进货 **`CIR`**），**不含 `SQ`**；回退 **`SR`**；对齐 **SDD v2.20** |
| v0.23 | 2026-05-14 | **IF-WHS-IN-11**：**`unitBands`** 增加**包装槽**（**`bandKind`=`PACKAGE`**，同源QueryService**销售单位**列；与门诊 **`SALES`** 数据源一致、编码区分）；§2、§4、§9.3 同步 |
| v0.24 | 2026-05-14 | **IF-WHS-IN-03**：详情明细附带**多单位目录提示**（与 **IF-WHS-IN-11** 同源；**`PURCHASE`** 参考价与各槽展示单价回填规则）；§2、§4、§9.3 |
| v0.25 | 2026-05-14 | 剔除与药库出库/药房入库 AS 之**业务互指**；**`productCatalog`/`unitBands`** 装配仍以本 AS 与 **SDD** 为唯一契约收口 |
| v0.27 | 2026-05-14 | **IF-WHS-IN-11**：选品列表行 **`purchasePrice`** 展示真源改为 **`LOT_NUMBER_COST`**（目的库可用批次）；**IF-WHS-IN-03** 详情 **`productCatalog`** 仍为 **`PURCHASE`**；§1.1、§2、§4、§9.3；下游 **SDD v2.24** |
| v0.29 | 2026-05-24 | **库位门控统一**：初始化下拉收窄与列表/写路径药库门禁改由 **`CurrentManagedLocationIdsResolver`**（下拉 **`#narrowActiveLocationsByManaged`**，Gate id 集 **`#gatedLocationIdsFromActive`**）；**剔除**岗位分配推导库位之用；**管辖 empty 过渡态不收窄**；§2 **IF-WHS-IN-01**、§4 **IF-WHS-IN-01/11**；下游 **SDD v2.31** |
| v0.32 | 2026-07-04 | **BR-WHS-IN-012**：一张已同意订货单仅允许被一张**未删除**进货单引用（**`original_bus_no`** 指向订货 **`bus_no`**；**不区分**占用方进货单状态）；**IF-WHS-IN-04/12/13** 写/读路径门禁；**EX-WHS-IN-002** 文案；下游 **SDD v2.36** |
| v0.43 | 2026-08-31 | **IF-WHS-IN-11**：选品行增 **`purchaseSplitRatioText`**（采购最小单位换算比展示，如 `1箱/3桶`；同单位×1 为 `1个`；与库存明细 `packageSplitRatioText` 同族）；§4 IF-11；下游 **SDD v2.45** |
| v0.42 | 2026-08-24 | **BR-WHS-IN-019**：同意后以本行包装进价回写实例级 **`PURCHASE`/`SALE`**（批号同价跳过时仍回写）；参考价仅展示/选品默认；院点中草药加价后再对齐 **`SALE`**；§1.5/§1.6/§4 IF-08；下游 **SDD v2.44** |


---

### 9.3 IF→DM 追溯摘要（评审用）


| IF               | 业务语义锚点（DM / QueryService章节） |
| ---------------- | ------------------------------ |
| IF-WHS-IN-01     | **DM-WKF-006** §4.2 **SupplyStatus**；**DM-ADM-003** **Location**；**DM-CUS-007** **Supplier**；初始化下拉 **OptionDto** 口径见 **SDD** §3.2 |
| IF-WHS-IN-02     | **DM-WKF-006** **SupplyRequest**（**`wkf_supply_request`**）列表聚合口径见 **SDD §3.3**（**WHS-IN-SDD-R-010**）；**`approver_id`** 见 **DM-WKF-006** §3.1 |
| IF-WHS-IN-03     | **SupplyRequest** 写模型一致口径；**ProductDefinition**（**`productName`/`specification`/`manufacturer`** 等回显；**多单位目录提示**与 **IF-WHS-IN-11** 同源）；明细字段名与 **SDD** §3.4 **`InboundReceiptDetailLineDto`** 表一致 |
| IF-WHS-IN-04     | **DM-WKF-006** §6.1.2 进货建单与来源订货拷贝；**`CABINET_INBOUND_RECEIPT`**；**BR-WHS-IN-012** 订货→进货 1:1 |
| IF-WHS-IN-06～09 | **DM-WKF-006** §6 审批提交、撤回、同意、驳回 |
| IF-WHS-IN-08     | **DM-WKF-006** §6.1.14 执行单据；**BR-SR-012**；**BR-WHS-IN-010**；**BR-WHS-IN-011**；**DM-WKF-005** / **DM-WKF-008** 入库与库存；**批号维定价**（**`LOT_NUMBER_COST`/`LOT_NUMBER_PRICE`**）；**IF-TRC-05** / **DM-CUS-010 v2.0.4**；**ProductDefinition** 多单位→拆零入账；**审批通过**编排见 **§4** 本 IF |
| IF-WHS-IN-10     | **DM-WKF-006** §6.1.16 逻辑删除；**BR-SR-011**（可删状态）；**BR-SR-010**（下游引用） |
| IF-WHS-IN-12     | **SupplyRequestQueryService** **`existsUndeletedSupplyByOriginalBusNo`**；**BR-WHS-IN-012** 排除已占用订货 |
| IF-WHS-IN-11     | **ProductDefinition** QueryService **Q-PD-004**（含 **`category_code`/`category_code_dict_text`**、销售单位列用于 **`unitBands`** 之 **`PACKAGE`** 槽等）；**`unitBands`** 与 **`ProductDefinitionCatalogUnitBandsAssembler`** / **`ProductDefinitionCatalogUnitBandConstants`**（含 **`PACKAGE`**；**SDD** §3.12）；**项目定价/收费项定价表**（选品 **`purchasePrice`**：**`PURCHASE`** 每包装；**IF-WHS-IN-03** 目录参考价亦为 **`PURCHASE`** 每包装；**禁止**采购选品取 **`SALE`**） |


---

**文档负责人**：（待填） · **评审**：（待填）
