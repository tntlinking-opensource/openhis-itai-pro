# 财务管理 · 价格调整 · 应用服务设计（AS）

> **文档职责**：说明「价格调整」单页的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：**§1.4.0** 菜单归 **一级「财务管理」**（新建）下 **价格调整**；**单页单据闭环**（对齐药库进货单 AS-INV-WHS-002）：列表 + 详情/编辑 + **单据内**提交/撤销；**同意/驳回**迁至 **AS-FIN-APR-001 财务审批**（须 `ROLE_ACCOUNTANT`），本页待审批态详情**只读**、不展示审驳按钮。四 Tab 选品（药/耗/诊疗/服务），同单仅一种 `item_table`。
>
> **覆盖声明**：业务编排对齐初版 `openhis-application` 调价模块（`adjustprice` + `inventorymanage` 审批页）与 **DM-CUS-011 v2.1**；领域写读以已落地 `PriceAdjustmentDomainService` / `PriceAdjustmentQueryService` 为能力边界。新版 UI（`openhis-pro-ui`）**尚未落地**，IF 以初版交互 + DM 状态机归纳。
>
> **编码**：AS-FIN-PRC-001 · **版本**：v0.12 · **日期**：2026-09-16

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 调价单全生命周期：草稿保存、提交待审批、撤销草稿、驳回后修正再提交、审批通过、审批驳回；物流（药品/耗材）批号子表快照、提交前在途互斥与锁预览、提交时全院库存锁、审批后解锁与写定价；诊疗（**仅** `wkf_activity_definition`）一对一调前售价、无库存锁；**套餐父项禁调**、子项审批后**级联重算父 SALE**；诊疗提交拦**待签发草稿**（已落账单不拦）；待审批阻断查询（`existsPendingByItem`）供目录与其它入口消费 |
| **在外** | 限价单独菜单；`forceVoid` 紧急解锁；**医疗服务不调价**；医保进销存上传；目录主数据 CRUD（药品/耗材/诊疗定义维护走各自 BAS AS）；**调价单打印编排 IF 本期不写**（运行时场景码 **`fin-price-adjustment-print`** 已可用，见 **README-PRT** / health-dev **V24**；打印走 **AS-BAS-PRT-001** `preparePrint`） |
| **涉及域** | **写侧**：PriceAdjustment（CUS-011）、InventoryItem 锁（WKF-008）、ChargeItemDefinition 定价写（ADM-010）；**读侧**：PriceAdjustmentQueryService、InventoryItem / ChargeItemDefinition 读、药品/耗材/诊疗活动目录 QueryService、ProductDefinition 展示 |
| **跨域** | 提交（物流）：在途预检 → 锁预览（可选 UI）→ `inventory_lock` → 领域 `submit`；审批通过（物流）：写价 → `unlockByBusNo` → 领域 `approve`；审批驳回（物流）：`unlockByBusNo` → 领域 `reject` |
| **编排权威** | 以本文 **§2、§4** 为准；调价单状态机以 **DM-CUS-011 §2.3** 为准 |
| **前置（摘要）** | 项目定义已发布可引用；物流调价须存在可用库存批号；**操作科室、操作人**由服务端从当前登录会话解析 |
| **后置（摘要）** | `approved` 后：受影响的旧定价行 `status_enum` → **停用（`INACTIVE`）**；按目标价 **新增** 启用（`ACTIVE`）定价行（物流含批号进/售价；诊疗为 `SALE`）；物流 `pending` 期间项目被阻断；驳回/撤销后解锁（物流） |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **药库/物价制单员** | 列表查单 → 新建/编辑 → 保存草稿 → 提交审批；撤销草稿；驳回后改单再提交 |
| **财务** | 在 **AS-FIN-APR-001** 财务审批页筛 `pending` 或打开只读详情 → **同意** / **驳回** |
| **其它模块（消费方）** | 开嘱/发药/划价等入口调用「是否存在 pending 调价」阻断（**BR-PRC-LOCK-007**）；**医生站签发**（门诊 IF-DOR-15/37、住院 IF-IP-DOR-15）经 `PendingPriceAdjustmentSignGate` + `existsPendingByAnyItem` 整批拒绝 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **调价单** | `bus_no` 聚合键；主表 `cus_price_adjustment` 每项目一行；子表 `cus_price_adjustment_detail` |
| **物流调价 `logistics`** | `item_table` = `med_medication_definition` 或 `adm_device_definition`；子表多批号；目标进价/售价至少一侧；提交加锁 |
| **诊疗调价 `clinical`** | `item_table` = **仅** `wkf_activity_definition`（不含医疗服务）；子表一对一，仅调前/目标售价；**禁止套餐父项**；审批后级联重算引用该子项的父套餐 SALE |
| **同单项目表一致** | 同一 `bus_no` 内 `item_table` 必须相同（**BR-PRC-SUB-001**） |
| **草稿不加锁** | `draft` / `rejected` 编辑不占用 `inventory_lock`；仅 `submit → pending`（物流）加锁 |
| **盈亏固化** | 领域 `submit` 时重算子表 `purchase_impact` / `selling_impact`，`pending` 后不变 |
| **初版 Redis 货品锁** | 初版用 Redis `productsKey`；ZXPlat **替换**为 `inventory_lock` + `existsPendingByItem`，**不**保留 Redis 锁 |
| **菜单归属** | **一级「财务管理」**（与电子发票等院级功能并列，**既有/扩展一级**）→ **二级「价格调整」**；**无**再套「financial/财务」三级分组 |
| **前后端路由** | 前端建议 `/finance/price-adjustment`（与 `/finance/einvoice` 同域）；**API 不带** finance 前缀：`POST /api/app/priceAdjustment/{method}` |
| **代码包** | `application.finance.priceadjustment`（院级 **finance** 菜单域）；**不用** `application.financial`（已与 `outpatient.financial` / `inpatient.financial` 门诊住院收费包混淆） |
| **AS 编码 FIN** | `AS-FIN-PRC-001` 中 FIN = 院级财务管理；**≠** `AS-OP-FIN-*` / `AS-IP-FIN-*` 门诊/住院收费 |
| **单据内审批** | **已废止为本页唯一入口**：`pending` 态审驳改走 **AS-FIN-APR-001**；本页详情只读不展示同意/驳回 |
| **审批落价口径** | **停用旧价 + 新增启用价**（非改价覆盖、非物理删除）：匹配 `instance_table + instance_id + price_type_code + lot_no` 的当前 **ACTIVE** 行 → `INACTIVE`；再 **INSERT** 新行 `ACTIVE` + 目标金额 |
| **物流批号价** | 子表每个 `lot_number`：有目标进价则 `LOT_NUMBER_COST`；有目标售价则 `LOT_NUMBER_PRICE` |
| **诊疗售价** | 停用旧 `SALE`（若存在）→ 新增 `ACTIVE` 的 `SALE` 行（目标售价） |
| **QueryService 能力** | 调价单读以 **DM-CUS-011 §6.2** 为准；跨聚合快照/在途/锁预览在 **application.financial.priceadjustment.support**，**不在**领域 PriceAdjustmentQueryService |
| **操作科室** | 制单写路径 `org_id` 由 **`CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId`** 从会话解析；**禁止** Request 传 `orgId`（对齐 B.6.5）。**IF-PRC-02 历史列表不按科室收窄**（`orgId=null`），且前端**不传** `statusEnum`，全状态可见；与调价明细全院已生效口径一致 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **§1.4.0 菜单（本期）** | 新建 **一级「财务管理」**；其下仅 **二级「价格调整」**（单页：列表 + 详情 + 单据内审批） |
| **§1.4.1 页面能力（本期）** | IF-PRC-01～14：初始化、**统一列表**（含按状态筛 `pending`）、详情、选品、快照、锁预览、在途、保存、提交、撤销、同意、驳回、pending 互斥查询 |
| **非目标** | 本页展示同意/驳回（迁 **AS-FIN-APR-001**）；初版 `inventory-examine-page` 分离审批页；`forceVoid`；限价独立菜单；Redis 锁；SupplyRequest 共表路径 |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

| 聚合（域） | 在本场景中的职责（摘要） | DM 路径 |
| --- | --- | --- |
| **PriceAdjustment / PriceAdjustmentDetail** | 调价单草稿、提交、审批、撤销；子表盈亏快照 | `DM-CUS-011-PriceAdjustment-价格调整.md` |
| **InventoryItem / InventoryLock** | 物流提交加锁、审批通过/驳回解锁 | `DM-WKF-008-InventoryItem-库存项.md` §6.1.5～6.1.6 |
| **ChargeItemDefinition** | 审批通过写 `PURCHASE`/`SALE` 及批号价类 | `DM-ADM-010-ChargeItemDefinition-收费项定义.md` |
| **MedicationDefinition / DeviceDefinition** | 物流选品与展示 | `DM-MED-001` / `DM-ADM-008` |
| **ActivityDefinition** | 诊疗选品与调前价读侧；套餐父子反查 | `DM-WKF-001` |
| **MedicationRequest / MedicationDispense 等** | **应用层**在途互斥只读（物流提交前）；诊疗待签发草稿经 ServiceRequest | 各医嘱/发药/服务申请 DM §6.2 |
| **ProductDefinition（读）** | 选品行展示 enrich | application.shared.product |
| **Organization** | 会话操作科室（制单 org_id） | `DM-ADM-004-Organization-组织.md` |

### 1.6 业务规则（BR-*）

| 规则 ID | 描述（语义锚点） | 绑定 IF |
| --- | --- | --- |
| **BR-PRC-001～017** | 单据状态机、必填、同单 `item_table`、盈亏公式等 | 09～14 |
| **BR-PRC-SUB-001** | 同单仅一种 `item_table` | 09 |
| **BR-PRC-SUB-002** | 同项目仅一张 `pending` 调价单（领域互斥 + 应用层预检） | 10、12 |
| **BR-PRC-SUB-003** | **应用层**提交前：物流项目不得存在**硬在途**（已签发医嘱申请 / 在途发放）；诊疗项目不得存在**待签发草稿** ServiceRequest（已落账单不拦） | IF-PRC-08、10 |
| **BR-PRC-SUB-003a** | **未签发草稿（仅本单调价项目）**：开立时刻落在 **48h 内**的未签发 `DRAFT` → 阻断并提示「哪位医生给哪位患者开了什么药/耗」，须医生自行删除；**超过 48h** → 保存草稿（IF-PRC-09）或提交审批（IF-PRC-10）时**静默**迁为 `CANCELLED`（废弃，不软删），不作二次确认；写前再确认仍为未签发 `DRAFT`，**已签发医嘱一律跳过、禁止误伤**；调价单未涉及的项目不动；**无定时任务** | IF-PRC-08、09、10 |
| **BR-PRC-SNAP-001** | `pending` 后子表快照冻结 | 10 |
| **BR-PRC-SNAP-002** | 草稿可刷新快照 | 09 |
| **BR-PRC-LOCK-001～008** | 物流锁范围、冲突整单失败、预览、同事务加锁 | 07、08、10、13、14 |
| **BR-PRC-CLIN-001～004** | 诊疗仅售价、一对一子表、无锁；**仅** `wkf_activity_definition`；**禁止套餐父项**；审批后级联父 SALE | 06、09、10、13 |
| **BR-PRC-APR-001** | 审批通过写价：主行目标价 → 对应价类 + 各批号行（见 APR-003～005） | 13 |
| **BR-PRC-APR-003** | 落价时**禁止物理删除**旧定价行；须将匹配到的当前 **ACTIVE** 行迁为 **`INACTIVE`（停用）** | 13 |
| **BR-PRC-APR-004** | **物流**：主行有 `new_purchase_amount` → 停用旧 `PURCHASE` 并 **新增 ACTIVE `PURCHASE`**；有 `new_selling_amount` → 停用旧 `SALE` 并 **新增 ACTIVE `SALE`**；子表批号：**按 `lot_number` 去重**后，有目标进价 → 停用旧 `LOT_NUMBER_COST` 并 **新增 ACTIVE** 批号进价；有目标售价 → 停用旧 `LOT_NUMBER_PRICE` 并 **新增 ACTIVE** 批号售价（同批多库行不重复写价） | 13 |
| **BR-PRC-APR-005** | **诊疗**：停用旧 `SALE`（若存在）并 **新增 ACTIVE `SALE`**（金额 = 主行 `new_selling_amount`）；不涉及进价与批号价类 | 13 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义（业务） | 读/写 | 关键规则（BR） | 关键状态变化（自然语言） | 涉及领域 | QueryService |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-PRC-01 | 进入价格调整页 | 初始化：单据状态筛选项（含 `pending`）、**三档** `item_table` Tab（药/耗/诊疗）、租户定价策略开关；**调价原因手写，不下发枚举** | 读 | — | — | PriceAdjustment（状态/项目表投影） | — |
| IF-PRC-02 | 列表查询/刷新（含筛待审批） | 分页查询调价单；**按 `bus_no` 聚合一行**；可选状态/时刻窗/关键字；**会话校验操作科室存在，但列表不按科室收窄**；前端默认**不传** `statusEnum`；对齐进货单 **IF-WHS-IN-02** 列表编排 | 读 | — | — | PriceAdjustment | `countDistinctBusNo` + `listDistinctBusNoPage` + `listLinesByBusNosWithFilter` || IF-PRC-03 | 打开单据详情 | 按 `bus_no` 加载主行+子表；**物流子表回显按批号合并**（同批多库数量/盈亏相加，界面仍为全院按批）；`pending` 态展示同意/驳回入口（前端按状态显隐按钮） | 读 | — | — | PriceAdjustment + 目录读 | getDocumentByBusNo + 目录 QS |
| IF-PRC-04 | 制单页：按 Tab 关键字搜选品 | 按 `item_table` + 关键字分页返回可选项目；**目录 enrich** 编码/规格/厂家/种类/单位；**目录当前价**（物流：`PURCHASE`+`SALE`；诊疗：`SALE`）；**不含医疗服务** | 读 | — | — | 各定义聚合 + 定价读 | 各定义 QS + `ChargeItemDefinitionQueryService` |
| IF-PRC-05 | 制单页：选中物流项目后加载批号快照 | 按选中 `item_id` 列表展开全院可用库存批号，并读双侧调前价，组装为草稿 `details` 入参形态 | 读 | BR-PRC-011 | — | InventoryItem + ChargeItemDefinition | InventoryItemQueryService + ChargeItemDefinitionQueryService（querysupport 组装） |
| IF-PRC-06 | 制单页：选中诊疗项目后加载调前售价 | 读当前 `SALE`（或产品指定价类）作为 `oldSellingAmount` 建议值 | 读 | BR-PRC-CLIN-003 | — | ChargeItemDefinition | ChargeItemDefinitionQueryService |
| IF-PRC-07 | 制单页：提交前预览将锁库存行 | 物流单：列出将参与 `inventory_lock` 的库存行及是否已被其它单锁定 | 读 | BR-PRC-LOCK-008 | — | InventoryItem | querysupport · previewSubmitLocks |
| IF-PRC-08 | 制单页：提交前在途预检 | 物流单：硬在途阻断；48h 内未签发草稿返回 `freshUnsignedDraftTips`（医生/患者/项目名） | 读 | BR-PRC-SUB-003、SUB-003a | — | 医嘱/发药等 | querysupport · checkInTransitForSubmit |
| IF-PRC-09 | 制单页：保存草稿 | 整单覆盖主从表；物流：先对本单项目 **>48h 静默废弃** / **≤48h 提示阻断**；子表由 05/06 预组装后随请求提交；**落库前物流按库房+批号展开（`location_id` 可空）**；不加锁 | 写 | BR-PRC-SUB-001、006～011、CLIN、SNAP-002、SUB-003a | 新建或更新 → `draft`；驳回态保存仍为 `draft` | PriceAdjustment | — |
| IF-PRC-10 | 制单页：提交审批 | 物流：09 同口径草稿策略 → 08 硬在途 →07（可选）→加锁→领域 submit；诊疗：待签发草稿硬在途 → 领域 submit；驳回后再提交走同一 IF（先 09 再 10） | 写 | SUB-002、SUB-003、SUB-003a、LOCK-001～005、SNAP-001、CLIN | `draft`/`rejected` → `pending` | PriceAdjustment + InventoryItem | existsPendingByItem |
| IF-PRC-11 | 制单页：撤销草稿 | 仅 `draft` → `deprecated` | 写 | BR-PRC-002 | `draft` → `deprecated` | PriceAdjustment | — |
| IF-PRC-12 | 目录/其它入口：检查项目是否待审批调价 | 按 `item_table` + `item_id` 返回是否存在 `pending` 调价单；签发侧批量消费 `existsPendingByAnyItem` | 读 | BR-PRC-LOCK-007、SUB-002 | — | PriceAdjustment | existsPendingByItem / existsPendingByAnyItem |
| IF-PRC-13 | 单据详情内：审批同意 | `pending` 态：**停用旧 ACTIVE 定价 → 新增 ACTIVE** → unlock（物流）→ 领域 approve | 写 | BR-PRC-APR-001～005、LOCK-006 | `pending` → `approved` | PriceAdjustment + ChargeItemDefinition + InventoryItem | — |
| IF-PRC-14 | 单据详情内：审批驳回 | `pending` 态：物流 unlock → 领域 reject；驳回原因必填 | 写 | BR-PRC-015、LOCK-006 | `pending` → `rejected` | PriceAdjustment + InventoryItem | — |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GAP-PRC-001 | QueryService 能力 | `application.financial.priceadjustment.support` | 在途互斥跨聚合读 | `PriceAdjustmentInTransitChecker` 落地，对齐 BR-PRC-SUB-003（药品/耗材 Request+Dispense） | IF-PRC-08、10 | P0 | **已关闭** |
| GAP-PRC-002 | QueryService 能力 | 同上 | 锁预览读组装 | `PriceAdjustmentLockPreviewLoader` + `InventoryItemQueryService#findActiveInventoryRowsForPriceAdjustmentLockPreview` | IF-PRC-07、10 | P0 | **已关闭** |
| GAP-PRC-003 | QueryService 能力 | 同上 | 物流子表快照组装 | `PriceAdjustmentLogisticsSnapshotAssembler` | IF-PRC-05、09 | P0 | **已关闭** |
| GAP-PRC-004 | DM 能力 | `ChargeItemDefinitionDomainService` §6 | 审批停用+新增 ACTIVE | `deactivateActivePriceLinesByCommand` + 新增行默认 `ACTIVE`；`PriceAdjustmentApprovePricingOrchestration` | IF-PRC-13 | P0 | **已关闭** |

> **说明**：GAP-PRC-001～003 为 querysupport 读组装缺口；GAP-PRC-004 为定价写侧 **停用旧价** 能力缺口。`PriceAdjustmentDomainService` 六写方法与 `InventoryItemDomainService#lockInventory` / `unlockByBusNo` 已落地，**不入 Gap**。

---

## 4. 接口编排说明书（业务步骤）

### IF-PRC-01 初始化

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 用户进入「财务管理 · 价格调整」页 |
| **主路径** | 加载审批状态筛选项、**三档** `item_table` 与展示口径 `adjustmentStyle`；加载租户选项「医嘱定价来源」供 UI 展示统一定价/批号价策略；**调价原因为自由文案（手写），不下发原因枚举**；**不下发**操作科室下拉（由会话解析） |
| **失败分支** | 租户选项缺失时默认按批号售卖语义展示，不阻断页面 |

### IF-PRC-02 调价单列表

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 列表区：**时刻窗**（映射 `startDate`/`endDate`）、**状态**（可选，前端默认不传=全状态）、关键字（单号）、翻页；会话须有操作科室（Gate），**列表不按 `org_id` 收窄** |
| **主路径** | **对齐进货单 IF-WHS-IN-02**：`countDistinctBusNo` → `listDistinctBusNoPage` → `listLinesByBusNosWithFilter`；应用层按 `bus_no` 聚合装配列表行；**列表时刻键** `COALESCE(submit_time, adjustment_time)`（草稿无提交时间时用制单时间）；Query.`orgId=null` |
| **展示列** | 单据号、状态、制单人、提交人、审批人（未审批可空）、**制单时间**（`adjustment_time`）、**申请时间**（`submit_time`）、**审批时间**（`approve_time`）、盈亏汇总等（与 UI 对齐进货单三时刻列） |
| **失败分支** | 会话无操作科室 → 参数错误 |

### IF-PRC-03 单据详情

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 有效 `bus_no`（从列表进入或新建保存后） |
| **主路径** | 加载读模型并 enrich；**行展示**批量回填 `itemName`、`itemCode`、`specification`、`manufacturer`（物流）、**`categoryCodeDictText`（项目种类）、`unitName`（单位，与 IF-PRC-04 同口径）**；子表 `details` 返回受影响批号、数量、调前价与分项盈亏（**物流按批号合并全院展示**；前端主行展开查看）；**按 `status_enum` 控制操作区**：`draft`/`rejected` 可编辑+提交；`pending` 展示同意/驳回（**单据内审批**，对齐药库进货单详情审批） |
| **失败分支** | 单号不存在 |

### IF-PRC-04 选品搜索

| 维度 | 说明 |
| --- | --- |
| **触发前提** | `item_table` 合法；关键字可空（空则限流默认条数） |
| **主路径** | 药/耗/诊疗：`ProductDefinitionCatalogPageLoader#pageProductDefinition` 分页 → 映射 `AdjustableItemRowDto`（**院内编码**、名称、规格、厂家、种类、单位）→ **批量回填目录当前价**（**BR-PRICE-SRC-001** 新开单读路径：物流 `resolveFirstPurchasePriceValue` + `resolveFirstSalePriceValue`；诊疗仅 `resolveFirstSalePriceValue`）→ **物流 Tab 批量回填全院可用库存数量总和**（`quantity`，与 IF-PRC-05 可用口径一致）。**不含医疗服务 Tab** |
| **展示列（业务语义）** | 项目编码、项目名称、规格、厂家/产地（物流）、数量（物流，全院可用量总和）、当前进价（物流）、当前售价、单位、项目种类；物流/诊疗分支由 Tab/`item_table` 隐含，**不**下发 `adjustmentStyle` |
| **与进货选品对齐** | 字段族参照 **IF-WHS-IN-11** `ProductDefinitionItemDto` 子集；**禁止**在选品路径读历史 `ChargeItem` 落库价；定价无行时对应价字段 **null**（非失败） |
| **失败分支** | 非法 `item_table`（含医疗服务） |

### IF-PRC-05 物流批号快照加载

| 维度 | 说明 |
| --- | --- |
| **触发前提** | `item_table` 为物流表；已选 `item_id` 列表 |
| **主路径** | querysupport：按项目查全院 `ACTIVE` 且 `quantity>0` 库存行 → 按批号聚合数量 → 读批号进价/售价调前价 → 返回可编辑 `details` 结构供前端填目标价后随 IF-PRC-09 提交 |
| **失败分支** | 无可用库存批号 → 提示无法调价（与领域提交校验一致） |

### IF-PRC-06 诊疗调前售价加载

| 维度 | 说明 |
| --- | --- |
| **触发前提** | `item_table` 为临床表；已选 `item_id` |
| **主路径** | 读定价 `SALE` 作为建议 `oldSellingAmount`；用户填 `newSellingAmount` 后 IF-PRC-09 落一对一子表 |
| **失败分支** | 无定价行 → 允许用户手填调前价（须非空才能提交） |

### IF-PRC-07 提交锁预览

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 物流调价单已保存或即将提交；有效 `bus_no` |
| **主路径** | querysupport 列出将锁 `inventory_id`、批号、库位、数量及 `lockedByOtherBusNo` |
| **失败分支** | 只读；存在其它单锁时 UI 警示，真正阻断在 IF-PRC-10 |

### IF-PRC-08 在途预检

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 物流单点击提交前（可与 07 串联） |
| **主路径** | ① `PriceAdjustmentInTransitChecker#checkByBusNo`：硬在途（已签发申请/发放）；② 未硬拦时 `PriceAdjustmentUnsignedDraftPolicySupport#listFreshTipsByBusNo`：48h 内未签发草稿 → `blocked=true`、`reasonCode=FRESH_UNSIGNED_ORDER_DRAFT`、`freshUnsignedDraftTips`（医生/患者/项目名） |
| **失败分支** | 阻断提交，保持 `draft`/`rejected`；48h 内草稿须开立医生自行删除（不可确认绕过） |

### IF-PRC-09 保存草稿

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 单据可编辑（`draft`/`rejected` 或新单）；同单 `item_table` 一致；物流行带 05 组装之 `details`；诊疗行带调前售价 |
| **主路径** | 组装 `SavePriceAdjustmentDraftCommand`（`org_id` 由会话解析）→ **物流：对本单项目 >48h 静默废弃未签发 DRAFT；≤48h 阻断并提示开立医生/患者/项目名** → **调前价权威回填** → **物流批号按当前可用库存拆成库房+批号行**（`location_id` 可空；无库存命中则 loc 空）→ 领域 `savePriceAdjustmentDraft`；返回 `bus_no`。界面仍按 IF-PRC-05 **批号聚合**入参，拆库仅服务端落库。 |
| **失败分支** | 状态不可编辑；业务规则不满足；本单物流项目存在 48h 内未签发草稿 |

### IF-PRC-10 提交审批

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 单据 `draft` 或 `rejected`；内容已通过 09 保存（或本次事务内先 09 再提交） |
| **主路径（物流）** | ① 对本单项目：>48h 未签发 DRAFT 静默 CANCELLED；仍有 ≤48h → 阻断（提示医生/患者/项目名）；② 硬在途预检；③（可选）IF-PRC-07 预览；④ 对本单各 `item_id` 下全院可用库存行逐条 `lockInventory`（`bus_no` = 调价单号），遇其它 `bus_no` 已锁则**整单失败**；⑤ 领域 `submitPriceAdjustmentForApproval`（互斥、重算盈亏、子表就绪、`pending`），回填 `lockedInventoryCount` |
| **主路径（诊疗）** | 无锁；直接领域 `submit`（须已有一对一调前子表） |
| **主路径（驳回后再提交）** | 先 IF-PRC-09 修正 → 同物流/诊疗主路径；领域 `resubmitPriceAdjustmentForApproval` |
| **失败分支** | 48h 内草稿/硬在途/锁冲突/互斥/子表未就绪 → 不进入 `pending`；物流失败须回滚本单已加锁行（同事务） |

### IF-PRC-11 撤销草稿

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 单据 `draft` |
| **主路径** | 领域 `withdrawPriceAdjustmentDraft` → `deprecated` |
| **失败分支** | 非草稿态 |

### IF-PRC-12 项目 pending 查询

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 外部模块传入 `item_table` + `item_id` |
| **主路径** | `existsPendingByItem`；供开嘱目录等入口阻断；**医生站签发**批量走 `existsPendingByAnyItem`（`PendingPriceAdjustmentSignGate`） |
| **失败分支** | 参数非法返回 false（领域口径） |

### IF-PRC-13 单据内审批同意

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 详情页单据 `pending`；审批人会话可解析；**审批权限码由平台统一门控（后续批次接入，本期不硬编码）** |
| **主路径** | ① 定价落库（停用 + 新增，BR-PRC-APR-003～005）。② 物流：`unlockByBusNo`。③ 领域 `approvePriceAdjustment` → `approved`；刷新列表/详情 |
| **失败分支** | 非 pending；写价失败整单回滚 |

### IF-PRC-14 单据内审批驳回

| 维度 | 说明 |
| --- | --- |
| **触发前提** | 详情页单据 `pending`；驳回原因非空 |
| **主路径** | 物流：`unlockByBusNo` → 领域 `rejectPriceAdjustment` → `rejected`；用户可编辑后再次提交 |
| **失败分支** | 驳回原因为空；非 pending |

---

## 5. 业务异常与用户提示

| 场景 | 用户提示语义 | 可重试 |
| --- | --- | --- |
| 单号不存在 | 调价单不存在 | 否 |
| 状态不允许操作 | 当前状态不允许该操作 | 否 |
| 同项目已有待审批单 | 该项目存在待审批调价单 | 否 |
| 在途阻断 | 存在在途医嘱/发药，无法提交调价 | 否 |
| pending 调价夹心 | 项目存在待审批调价单，暂不可签发 | 否 |
| 库存锁冲突 | 库存已被其它单据锁定，整单提交失败 | 可修正后重试 |
| 无批号明细 | 无可用库存批号，无法提交 | 否 |
| 诊疗调前价缺失 | 须填写调前售价 | 是 |
| 驳回原因缺失 | 驳回须填写原因 | 是 |
| 写价失败 | 审批写价失败，事务回滚 | 可重试 |

错误码对齐 `ErrorCodeConstants`：`PRICE_ADJUSTMENT_004`～`007` 及 `INVALID_ARGUMENT` / `INVALID_STATUS` / `NOT_FOUND` / `ITEM_PENDING` / `ITEM_TABLE_MISMATCH`。

---

## 6. 事件语义与消费方

| 事件 | 触发 IF | 消费方（语义） |
| --- | --- | --- |
| `PriceAdjustmentDraftSavedEvent` | IF-PRC-09 | 审计、列表刷新 |
| `PriceAdjustmentSubmittedEvent` | IF-PRC-10 | 待办、阻断缓存刷新 |
| `PriceAdjustmentResubmittedEvent` | IF-PRC-10（驳回后再提交） | 同上 |
| `PriceAdjustmentApprovedEvent` | IF-PRC-13 | 对账、成本、审计；**ChargeItemDefinition** `PriceLineAdded` 驱动目录价刷新 |
| `PriceAdjustmentRejectedEvent` | IF-PRC-14 | 制单人可回到详情改单 |
| `PriceAdjustmentWithdrawnEvent` | IF-PRC-11 | 列表刷新 |
| `InventoryLockedEvent` / `InventoryUnlockedEvent` | IF-PRC-10 / 13 / 14 | 库存可用量、发药 Gate |

---

## 7. 非功能性需求

| 维度 | 承诺 |
| --- | --- |
| **事务** | IF-PRC-10 / 13 / 14 须单事务：锁/解锁、写价与领域状态迁移原子 |
| **幂等** | 审批同意/驳回对同一 `bus_no` 重复调用应状态校验失败，不重复写价 |
| **并发** | 提交互斥依赖 DB pending 唯一语义 + 锁冲突预检 |
| **审计** | `operator_id` / `submitter_id` / `approver_id` 落库；解锁用 Command `operatorId` |
| **性能** | 锁预览与快照加载批量 IN 读，禁止按行循环仓储 |

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **物流正向** | 草稿→提交加锁→同意：旧价 `INACTIVE`、新批号/项目价 `ACTIVE`→解锁→`approved` |
| **诊疗正向** | 提交无锁→同意：旧 `SALE` 停用、新 `SALE` 启用 |
| **定价历史** | 审批后库内保留停用行，读价仅消费 `ACTIVE` 行 |
| **驳回** | 物流解锁后可再编辑提交；诊疗同理 |
| **互斥** | 同项目两张 pending 第二张提交失败 |
| **锁冲突** | 其它单已锁库存行导致整单提交失败 |
| **在途** | 有待发药时提交阻断 |
| **撤销** | 仅 draft 可 deprecated |
| **阻断查询** | pending 期间 `existsPendingByItem` 为 true |

---

## 9. 关联文档与变更历史

| 文档 | 路径 |
| --- | --- |
| 领域模型 | `docs/领域/DomainModel/DM-CUS-011-PriceAdjustment-价格调整.md |
| 领域 SDD | `health/DomainModel-SDD/DM-CUS-011-PriceAdjustment-价格调整-SDD.md` |
| 库存锁 | `docs/领域/DomainModel/DM-WKF-008-InventoryItem-库存项.md |
| 定价写 | `docs/领域/DomainModel/DM-ADM-010-ChargeItemDefinition-收费项定价.md |
| 定价停用/新增 | `docs/领域/DomainModel/DM-ADM-010-ChargeItemDefinition-收费项定价.md §2.3 |
| 应用 SDD | `health/AppService-SDD/AS-FIN-PRC-001-价格调整-SDD.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.13 | 2026-09-29 | **IF-PRC-02**：历史列表不按科室收窄（Query.`orgId=null`）；前端默认不传 `statusEnum`，全状态可见 |
| v0.12 | 2026-09-16 | **取消定时任务**；**BR-PRC-SUB-003a**：本单调价项目 **48h** 内提示医生/患者/项目名自删，超过 48h 保存/提交静默 `CANCELLED`；IF-PRC-08 `freshUnsignedDraftTips` |
| v0.11 | 2026-09-16 | **BR-PRC-SUB-003a**（已由 v0.12 取代）：开立满 7 天未签发药/耗/诊 DRAFT→CANCELLED；定时任务；软在途 confirmAbandon |
| v0.10 | 2026-09-16 | **BR-PRC-LOCK-007** 签发消费方落地：`PendingPriceAdjustmentSignGate` + `existsPendingByAnyItem`（IF-DOR-15/37、IF-IP-DOR-15）；**BR-PRC-SUB-003a** 陈旧未签发草稿定时清理（初版 48h/软删，已由 v0.11 取代） |
| v0.9 | 2026-07-26 | 子表可空 `location_id`：IF-PRC-09 落库前按库房+批号展开；IF-PRC-03 按批号合并回显；审批写价按批号去重；供日结 **BR-INV-SET-031** |
| v0.8 | 2026-07-26 | **IF-PRC-03** 行 enrich 补 `categoryCodeDictText`/`unitName`；详情主行展开展示子表批号影响；记录调价单打印场景码 **`fin-price-adjustment-print`** 可用（无本模块打印编排 IF；见 **README-PRT** / V24） |
| v0.7 | 2026-06-27 | **IF-PRC-04** 选品返回物流 `quantity`（全院可用量总和）；移除 `adjustmentStyle` 出参，分支由 `item_table` 判断 |
| v0.6 | 2026-06-27 | **IF-PRC-04** 选品 enrich 编码/规格/厂家/种类/单位 + 目录当前价（PURCHASE/SALE）；**IF-PRC-03** 行 enrich 补 `itemCode`/`specification`/`manufacturer` |
| v0.5 | 2026-06-27 | 操作科室（制单机构 org_id）改由服务端从登录会话解析；IF-PRC-02/04/09 Request 不再传 orgId；IF-PRC-01 不再下发科室下拉 |
| v0.4 | 2026-06-27 | 关闭 Gap-001～004；列表对齐进货单；在途口径 B（医嘱+发放）；审批权限后续统一 |
| v0.3 | 2026-06-27 | 取消独立审批工作台；单页单据内审批；IF-PRC-13/14 为详情同意/驳回；列表 IF-PRC-02 合并待审批筛选 |
| v0.2 | 2026-06-27 | 审批落价停用+新增；菜单财务管理；AS-FIN-PRC-001 |
| v0.1 | 2026-06-27 | 初版：对齐 DM-CUS-011 v2.1 + 初版 openhis 调价/审批双页 |
