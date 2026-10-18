# 药房盘点单 · 应用服务设计（AS）

> **文档职责**：说明「药房盘点单」页面的**接口契约（IF）**、业务语义、涉及聚合与 QueryService、规则（BR）、编排意图；作为上游契约供详细设计与实现对齐。
>
> **本文必须写**：IF 编号；用户动作与读写属性；关键校验与状态变化；Gap；异常口径；事件语义；非功能约束；业务向测试要点。
>
> **本文禁止写**：具体类名/应用方法名；Request/Response 字段明细；包路径；Mapper；伪代码；写库顺序；QueryService 字段组装规则。
>
> **页面边界**：药房业务线 — 药房人员维护 **`PHARMACY_STOCKTAKE`**（与 **`SupplyType#getCode()`** 一致）盘点申请与审批闭环：列表与详情、新建/编辑/删除、提交/撤回审批、审批同意/驳回。**盘点单为执行单据（免申请）**：审批**同意**后由应用层对**非零盈亏行**编排 **`createStocktakingDelivery`** 与 **`InventoryItem` 按盈亏数量加减**（**不得**用实盘数量覆盖账面）。**单端点库房**：业务库房仅以 **`purpose_location_id` / `purpose_type_enum`** 为真源。
>
> **与药库盘点对偶**：与 **AS-INV-WHS-007-药库盘库单** **同构**；差异仅为 **`type_enum`**、**init 仅药房列表**、**`bus_no` 前缀 `PST`**。库房操作通用细则（列表聚合、审批状态机、删改提交流程）与 **AS-INV-PHA-001**、**AS-INV-WHS-003** 一致。
>
> **覆盖声明**：本稿以 **AS-INV-WHS-007** 与 **DM-WKF-006 §6.1.7** 为基线；正式定稿前须与前端药房盘点页对照片修订 **§1.4、§2、§4、§5**。
>
> **编码**：AS-INV-PHA-006 · **版本**：v0.2 · **日期**：2026-05-28

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 创建 **`PHARMACY_STOCKTAKE`** 草稿；维护批号级明细；审批闭环；**同意**后按盈亏入账 |
| **在外** | 药库 **`CABINET_STOCKTAKE`**（**AS-INV-WHS-007**）；科室盘点（**AS-INV-DEP-006**）；财务月结；追溯码（默认非目标） |
| **涉及域** | 写侧：**SupplyRequest**、**SupplyDelivery**、**InventoryItem**；主数据：**Location**、**人员**、**SupplyStatus**、入库项目类型；读侧：**SupplyRequest**、**InventoryItem**、**ProductDefinition** |
| **跨域** | **AGREE** 后仅非零盈亏行调整 **`purpose_location_id`** 下库存 |
| **编排权威** | 以本文 **§2、§4** 为准；行字段与 **AS-INV-WHS-007 §1.3** **完全一致** |

### 1.2 角色与关键动作

| 角色 | 关键动作 |
| --- | --- |
| **药房制单员** | 列表 → 选盘点药房 → 选品录入 → 保存 → 提交 →（必要时）撤回 |
| **药房主管/审批人** | 同意 / 驳回 |
| **管理员** | 待审核态修改或删除 |

### 1.3 术语与口径约定

| 术语 | 口径 |
| --- | --- |
| **药房盘点单** | `type_enum`=`**PHARMACY_STOCKTAKE**` |
| **`bus_no` 前缀** | **`PST`**（**`SupplyType.busNoSeqKey`**），按日流水分桶 |
| **盘点库房** | **`purpose_location_id` / `purpose_type_enum`** 为唯一业务真源 |
| **行字段** | 与 **AS-INV-WHS-007 §1.3** 相同：**`item_quantity`** 盈亏（可负可零）、**`total_quantity`** 实盘、**`total_price`** 实盘金额、**`profit_loss_price`** 盈亏金额 |
| **同意入账** | 库存 **+= `item_quantity`（盈亏）**；**≠** 写实盘 |
| **init** | **IF-PHA-ST-01** **仅**药房位置列表（**`pharmacyLocationOptions`**）；**不**返回药库列表 |
| **选品** | **IF-PHA-ST-11**：与 **IF-PHA-IN-11** / **IF-WHS-ST-11** 同构，过滤键为 **`purposeLocationId`**（盘点药房） |
| **枚举口径** | 一律 **`getCode()`** 字符串 |
| **互指** | 药库盘点全文见 **AS-INV-WHS-007**；本节仅列药房差异项 |

### 1.4 本期范围与非目标

| 类别 | 内容 |
| --- | --- |
| **本期** | 药房盘点全生命周期 + 单药房 init + 盈亏/实盘字段 + 同意入账 |
| **待评审对齐** | 与 **WhStocktakeOrder / PhWhStocktakeOrder** 菜单路由合并 |
| **非目标** | 批号维定价补录；药库侧盘点（**AS-INV-WHS-007**） |

### 1.5 领域清单（聚合 + 职责 + DM 路径）

与 **AS-INV-WHS-007 §1.5** 相同（**`createStocktake`** / **`createStocktakingDelivery`** / **InventoryItem** / **Location**）。

### 1.6 业务规则（BR-*）

| 规则 ID | 描述 | 绑定 IF |
| --- | --- | --- |
| **BR-PHA-ST-001** | `type_enum` = **`PHARMACY_STOCKTAKE`** | IF-PHA-ST-04 |
| **BR-PHA-ST-002** | **`purpose_location_id`** 须为合法药房且 ∈ **IF-PHA-ST-01** 门控集合 | IF-PHA-ST-04 / 05 / 11 |
| **BR-PHA-ST-003** | 同单状态一致 **BR-SR-002** | IF-PHA-ST-05～10 |
| **BR-PHA-ST-004** | 可编辑态 **BR-SR-003** | IF-PHA-ST-05 |
| **BR-PHA-ST-005** | 提交 **BR-SR-004** | IF-PHA-ST-06 |
| **BR-PHA-ST-006** | 同意/驳回 **BR-SR-005** | IF-PHA-ST-08 / 09 |
| **BR-PHA-ST-007** | 删除 **BR-SR-010** | IF-PHA-ST-10 |
| **BR-PHA-ST-008** | 同意防重 | IF-PHA-ST-08 |
| **BR-PHA-ST-009** | 行字段语义同 **BR-WHS-ST-009** | IF-PHA-ST-03 / 04 / 05 |
| **BR-PHA-ST-010** | 同意仅非零盈亏行入账；**整单全部为零仍同意成功**（不落发放、不调库存） | IF-PHA-ST-08 |
| **BR-PHA-ST-011** | 选品按 **`purposeLocationId`** | IF-PHA-ST-11 |
| **BR-PHA-ST-012** | 盈亏由 UI 计算、服务端以请求为准 | IF-PHA-ST-04 / 05 |

---

## 2. 页面与接口总览（契约概览）

| IF 编号 | 用户动作 | 接口语义 | 读/写 | 关键规则 | 关键状态变化 | 涉及领域 | 查询服务 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IF-PHA-ST-01 | 进入页面 | init：**仅药房** **OptionDto** + 项目类型 + **SupplyStatus** + 人员策略 | 读 | — | — | Location | — |
| IF-PHA-ST-02 | 列表 | 分页 **`PHARMACY_STOCKTAKE`**，按 **`purpose_location_id`** 筛选 | 读 | — | — | SupplyRequest | **`wkf_supply_request`** |
| IF-PHA-ST-03 | 详情 | 批号级明细 + 盈亏/实盘/金额回显 + 多单位目录（同 **IF-PHA-IN-03**） | 读 | **BR-PHA-ST-009** | — | SupplyRequest / ProductDefinition | 写模型 + QueryService |
| IF-PHA-ST-04 | 新建 | **`createStocktake`**，`type_enum`=`PHARMACY_STOCKTAKE` | 写 | BR-PHA-ST-001～002 / 009 | 新 **`bus_no`（`PST`）** | SupplyRequest | 刷新 |
| IF-PHA-ST-05 | 编辑 | 可编辑态改明细 | 写 | BR-PHA-ST-004 / 009 | 明细变更 | SupplyRequest | 同上 |
| IF-PHA-ST-06 | 提交 | 待审核 → 审核中 | 写 | BR-PHA-ST-005 | → `APPROVAL` | SupplyRequest | 待办 |
| IF-PHA-ST-07 | 撤回 | 审核中撤回 | 写 | — | 撤回态 | SupplyRequest | 同上 |
| IF-PHA-ST-08 | 同意 | 盘点发放 + 库存盈亏加减 | 写 | BR-PHA-ST-006 / 008 / 010 | `AGREE` | SupplyRequest → SupplyDelivery / InventoryItem | 库存 |
| IF-PHA-ST-09 | 驳回 | 驳回 | 写 | BR-PHA-ST-006 | `REJECT` | SupplyRequest | 同上 |
| IF-PHA-ST-10 | 删除 | 逻辑删除 | 写 | BR-PHA-ST-007 | 删 | SupplyRequest | 隐藏 |
| IF-PHA-ST-11 | 选品 | 库存批号行 + **`purposeLocationId`**；**选品 UI 须展示 `lotNumber`（批号）**（**AS-INV-WHS-003 §1.3**） | 读 | BR-PHA-ST-011 | — | InventoryItem / ProductDefinition | 库存 + 产品 |

---

## 3. 缺口跟踪（Gap）

| Gap ID | 缺口类型 | 文件 + 章节 | 缺什么 / 不一致点 | 关闭条件 | 影响 IF | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| — | — | — | 与 **AS-INV-WHS-007** 相同：含 **`createStocktake` 同号修订**；领域 Gap 已于 **2026-05-22** 关闭 | — | — | — | **已关闭** |

---

## 4. 接口编排说明书（业务步骤）

除 **`type_enum`**、**init 仅药房**、异常码前缀 **`EX-PHA-ST-*`** 外，各 IF 编排与 **AS-INV-WHS-007 §4** **同构**。下列仅列药房差异摘要：

| IF | 药房差异要点 |
| --- | --- |
| **IF-PHA-ST-01** | 仅 **`pharmacyLocationOptions`**；门控策略同 **IF-PHA-IN-01** 药房侧 |
| **IF-PHA-ST-02** | 固定 **`PHARMACY_STOCKTAKE`**；列表展示「盘点药房」 |
| **IF-PHA-ST-04** | **`createStocktake`** 行 **`type_enum`** 必须为 **`PHARMACY_STOCKTAKE`** |
| **IF-PHA-ST-08** | 库存在 **盘点药房** `purpose_location_id` 上按盈亏加减 |
| **IF-PHA-ST-11** | **`purposeLocationId`** = 当前盘点药房；可选 **`positiveQuantityOnly`**（非必传；与 **IF-WHS-SO-11** 同构） |

**IF-PHA-ST-06～07、09～10**：同 **AS-INV-WHS-007** 对应 IF。

**IF-PHA-ST-08 审批同意（完整语义）**：① 单 **同意**。② 对 **`item_quantity` ≠ 0** 行：**`createStocktakingDelivery`** + **InventoryItem 加减**（盈亏为负则减）。③ 零盈亏行跳过；**整单全部为零**则不调用发放落库，同意仍成功。④ 失败则 **EX-PHA-ST-104**。

---

## 5. 业务异常与用户提示

| 语义代码 | 场景 | 用户提示方向 | 可重试 |
| --- | --- | --- | --- |
| EX-PHA-ST-001 | 建单失败 | 补全明细 | 是 |
| EX-PHA-ST-002 | 药房非法 | 重选药房 | 是 |
| EX-PHA-ST-003 | 状态不允许 | 刷新 | 是 |
| EX-PHA-ST-004 | 禁止编辑 | 说明状态 | 否 |
| EX-PHA-ST-005 | 删除拒绝 | 说明原因 | 否 |
| EX-PHA-ST-101 | init 失败 | 稍后重试 | 是 |
| EX-PHA-ST-102 | 查询失败 | 缩小条件 | 是 |
| EX-PHA-ST-103 | 详情不可用 | 返回列表 | 视情况 |
| EX-PHA-ST-104 | 同意入账失败 | 联系管理员 | 视原因 |
| EX-PHA-ST-105 | 并发冲突 | 刷新 | 是 |

---

## 6. 事件语义与消费方

与 **AS-INV-WHS-007 §6** 相同（**`SupplyRequest*Event`** + 同意相位库存/发放副作用）。

---

## 7. 非功能性需求

与 **AS-INV-WHS-007 §7** 相同。

---

## 8. 测试要点（业务视角）

| 类别 | 要点 |
| --- | --- |
| **对偶** | 与 **AS-INV-WHS-007** 测试矩阵相同，仅 **`type_enum`** 与 **init 药房列表** 不同 |
| **药房门控** | 非药房位置拒绝建单/选品 |
| **盈亏入账** | 同意后药房库存按盈亏变化，非写实盘 |

---

## 9. 关联文档与变更历史

### 9.1 关联文档

| 文档 |
| --- |
| `DM-WKF-006-SupplyRequest-供应请求.md` |
| `DM-WKF-005-SupplyDelivery-供应发放.md` |
| `DM-WKF-008-InventoryItem-库存项.md` |
| `AS-INV-WHS-007-药库盘库单.md` |
| `AS-INV-PHA-001-药房入库单.md` |

### 9.2 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v0.2 | 2026-05-28 | 读模型废止后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |后对齐 zxplat 实现（EncounterContextFacade / querysupport Loader）。 |（已迁至 EncounterContextReader / application.shared.encounter）
| v0.1 | 2026-05-22 | 初稿：与药库盘点对偶；**`PHARMACY_STOCKTAKE`** |

### 9.3 IF→DM 追溯摘要

与 **AS-INV-WHS-007 §9.3** 相同，**`type_enum`** 替换为 **`PHARMACY_STOCKTAKE`**。

---

**文档负责人**：（待填） · **评审**：（待填）
