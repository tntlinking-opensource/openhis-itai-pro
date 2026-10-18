# 科室药房盘点单 · 应用服务设计（AS）

> **页面边界**：科室药房业务线 — **`DEPT_STOCKTAKE`** 盘点申请与审批闭环；库位须为 **`LocationForm#DEPARTMENT_PHARMACY`**。同意后对非零盈亏行编排 **`createStocktakingDelivery`** 与 **`InventoryItem` 按盈亏加减**。
>
> **与药房对偶**：与 **AS-INV-PHA-006-药房盘点单** 同构；差异为 **`type_enum`**、**init 仅科室药房列表**、**经手人下拉为全体在职医务人员（不限药师/库管）**、**`bus_no` 前缀 `DST`**、错误码前缀 **`DEP_ST_*`**。
>
> **编码**：AS-INV-DEP-006 · **版本**：v0.2 · **日期**：2026-08-31

---

## 1. 场景概述

### 1.1 目标与边界

| 维度 | 说明 |
| --- | --- |
| **在内** | 创建 **`DEPT_STOCKTAKE`** 草稿；审批闭环；**同意**后按盈亏入账 |
| **在外** | 药库 **`CABINET_STOCKTAKE`**；药房 **`PHARMACY_STOCKTAKE`**；科室耗材盘点 **`DEPT_DEVICE_STOCKTAKE`（28）** |
| **库位** | **`purpose_location_id`** 唯一真源；形态 **`DEPARTMENT_PHARMACY`** |
| **审批** | **不须**财务岗位（与损益区分） |
| **经手人** | 与制单人分离：请求体 **`practitionerId`** 必填并落库；详情/回显**以库字段为准**，禁止用当前登录人覆盖已落库单 |

### 1.2 术语

| 术语 | 口径 |
| --- | --- |
| **科室盘点单** | `type_enum`=`**DEPT_STOCKTAKE**`（值 24） |
| **`bus_no` 前缀** | **`DST`** |
| **init** | **IF-DEP-ST-01**：科室药房 **`pharmacyLocationOptions`** + 项目类型 + **SupplyStatus** + **经手人**（字段名仍为 **`pharmacistOptions`**，语义为全体在职医务人员；**`warehouseKeeperOptions`** 可空） |
| **行字段** | 同药库/药房盘点：**`profitLossQuantity`→盈亏**、**`actualQuantity`→实盘** |

### 1.3 业务规则（BR）

| 规则 ID | 内容 | 影响 IF |
| --- | --- | --- |
| **BR-DEP-ST-001** | **`purpose_location_id`** 须为启用中 **`DEPARTMENT_PHARMACY`** 且落在管辖 Gate 内 | IF-DEP-ST-04/05/11 |
| **BR-DEP-ST-002** | 建单/修订 **`practitionerId`（经手人）必填**，写入供应请求行 **`practitioner_id`**；与 **`applicant_id`（制单人，会话医务人员）** 分离 | IF-DEP-ST-04/05 |
| **BR-DEP-ST-003** | **IF-DEP-ST-01** 经手人下拉：全体在职医务人员（**`PractitionerRoleScopeLoader#listAllActivePractitionersForInventoryDocHandlerSelect`**），**不**按药师/库管岗位收窄；**OptionDto** 须带 **`pyStr`/`wbStr`**，前端可拼音/五笔过滤 | IF-DEP-ST-01 |
| **BR-DEP-ST-004** | 详情 **`practitionerId`** 回显落库值（空则可用 **`applicantId`** 兜底展示）；前端**禁止**对已落库单用登录人覆盖经手人 | IF-DEP-ST-03 |
| **BR-DEP-ST-010** | **同意**仅非零盈亏行入账；**整单全部为零仍同意成功**（不落发放、不调库存） | IF-DEP-ST-08 |

## 2. 页面与接口总览

| IF 编号 | 用户动作 | 读/写 | 关键规则 |
| --- | --- | --- | --- |
| IF-DEP-ST-01 | init | 读 | 科室药房 + 经手人（全体在职，含拼音）等；**BR-DEP-ST-003** |
| IF-DEP-ST-02 | 列表 | 读 | **`DEPT_STOCKTAKE`** |
| IF-DEP-ST-03 | 详情 | 读 | 历史槽价 **BR-PRICE-SRC-001**；经手人回显 **BR-DEP-ST-004** |
| IF-DEP-ST-04 | 新建 | 写 | **`createStocktake`**；**BR-DEP-ST-002** |
| IF-DEP-ST-05 | 编辑 | 写 | 可编辑态全量对齐；**BR-DEP-ST-002** |
| IF-DEP-ST-06 | 提交 | 写 | **`submitApproval`** |
| IF-DEP-ST-07 | 撤回 | 写 | **`withdrawApproval`** |
| IF-DEP-ST-08 | 同意 | 写 | 非零盈亏行 **`createStocktakingDelivery`** + 库存；**整单全零仍同意成功** |
| IF-DEP-ST-09 | 驳回 | 写 | **`rejectRequest`** |
| IF-DEP-ST-10 | 删除 | 写 | **`removeByBusNo`** |
| IF-DEP-ST-11 | 选品 | 读 | **`purposeLocationId`** 科室药房；**选品 UI 须展示 `lotNumber`（批号）**（**AS-INV-WHS-003 §1.3**） |

## 3. 缺口跟踪（Gap）

| Gap ID | 描述 | 状态 |
| --- | --- | --- |
| — | 与 **AS-INV-PHA-006** 相同：领域 **`createStocktake` 同号修订** 已落地 | **已关闭** |

## 4. 业务异常（与实现对齐）

| 语义代码 | 场景 |
| --- | --- |
| DEP_ST_001 | 参数/明细校验失败（含经手人为空） |
| DEP_ST_002 | 科室药房位置非法 |
| DEP_ST_003 | 状态不允许 |
| DEP_ST_004 | 禁止编辑 |
| DEP_ST_005 | 删除拒绝 |
| DEP_ST_101 | init 失败 |
| DEP_ST_102 | 查询失败 |
| DEP_ST_103 | 单据不存在 |
| DEP_ST_104 | 同意入账失败 |
| DEP_ST_105 | 并发冲突 |

## 5. 关联文档与变更历史

| 文档 |
| --- |
| `DM-WKF-006-SupplyRequest-供应请求.md` §6.1.7 |
| `AS-INV-PHA-006-药房盘点单.md` |
| `AS-INV-DEP-006-科室盘点单-SDD.md` |

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.2 | 2026-08-31 | **经手人**：init 全体在职医务人员 + **`pyStr`/`wbStr`**；建改单 **`practitionerId` 必填落库**；详情回显以库为准（**BR-DEP-ST-002～004**） |
| v0.1 | 2026-07-01 | 初稿 |

---

**文档负责人**：（待填） · **评审**：（待填）
