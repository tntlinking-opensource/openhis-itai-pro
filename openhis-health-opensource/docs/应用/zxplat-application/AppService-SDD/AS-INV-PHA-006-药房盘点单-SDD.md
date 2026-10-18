# 药房盘点单 · 应用服务详细设计（SDD）

> 编码：AS-INV-PHA-006-SDD  
> 版本：v0.4 | 日期：2026-05-24 | 作者：（待填）

## 1. 实现边界

### 1.1 目标与边界

| 项 | 内容 |
| --- | --- |
| 模块 | 药房盘点单应用服务（库存 · 药房 · **`PHARMACY_STOCKTAKE`**，与 **`SupplyType#PHARMACY_STOCKTAKE#getCode()`** 一致） |
| 边界 | 与 **AS-INV-WHS-007-SDD** 同构，差异仅：**单据类型 `PST`/`PHARMACY_STOCKTAKE`**、**init 仅药房**、**HTTP 前缀 `inventoryPharmacyStocktake`**、**位置门控为药房** |
| 编排权威 | `AS-INV-PHA-006-药房盘点单.md`（**v0.1**）§2、§4 |
| 方法权威 | 同药库 SDD：`DM-WKF-006` §6.1.7、`DM-WKF-005` §6.1.3、`DM-WKF-008` §3 |
| 严格对齐 | IF **IF-PHA-ST-01～11** |
| 交叉引用 | **字段口径、同意库存编排、选品列族、列表策略、`CurrentManagedLocationIdsResolver` 药房版门控** 以 **`AS-INV-WHS-007-药库盘库单-SDD.md`（≥ v0.3）** 为母版；本文仅写药房差异 |

### 1.2 术语口径

| 项 | 约定 |
| --- | --- |
| 字段口径 | 同 **WHS-ST-SDD**：**`profitLossQuantity`→`item_quantity`**、**`actualQuantity`→`total_quantity`**、**`actualAmount`→`total_price`**、**`profitLossAmount`→`profit_loss_price`** |
| **`bus_no` 生成** | 序号键 **`PST`**（**`SupplyType.busNoSeqPrefixForTypeCode`**）；回退 **`SR`** |
| 盘点库房 | **`purposeLocationId`** / **`purpose_type_enum`** 真源 |
| 同意库存 | **WHS-ST-SDD-R-001** 同等适用（**`PHA-ST-SDD-R-001`** 为别名引用） |

### 1.3 与 AS / DM 的实现裁决（强制）

| 裁决 ID | 内容 |
| --- | --- |
| **PHA-ST-SDD-R-001** | 同 **WHS-ST-SDD-R-001**：同意按 **`item_quantity`（盈亏）** 入账；零盈亏行跳过；**整单全部为零仍同意成功**（不落发放）；盘亏 **`allowNegative=true`** |
| **PHA-ST-SDD-R-002** | 同 **WHS-ST-SDD-R-002**：**`updatePharmacyStocktake`** → **`createStocktake`** 首行 **`busNo`** 修订 |
| **PHA-ST-SDD-R-003** | 同 **WHS-ST-SDD-R-003**：选品 **`LOT_NUMBER_COST`** / 详情 **`PURCHASE`** |
| **PHA-ST-SDD-R-004** | 同 **WHS-ST-SDD-R-004**：盘点行数量校验例外 |
| **PHA-ST-SDD-R-005** | 同 **WHS-ST-SDD-R-005**：**`apply_time` / `practitionerId`** |

## 2. 代码结构

```text
com/openhis/health/application/inventory/pharmacy/stocktake/
  service/
    InventoryPharmacyStocktakeAppService.java
  request/   （LoadPharmacyStocktakeFormInitRequest、PagePharmacyStocktakesRequest …）
  response/
  dto/
    PharmacyStocktakeListRowDto.java
    PharmacyStocktakeLineDto.java
    PharmacyStocktakeDetailLineDto.java
  mapper/
    InventoryPharmacyStocktakeMapper.java
```

### 2.1 依赖注入表

与 **AS-INV-WHS-007-SDD §2.1**（**v0.3+**）一致：**`LocationQueryService#listAllActivePharmacies`** 替代药库侧 **`listAllActiveWarehouses`**；**`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** / **`#gatedLocationIdsFromActive`** 之主数据入参换成**药房**列表；**管辖 empty 不收窄**（**B.6.5**）。其余 **`SupplyRequest` / `SupplyDelivery` / `InventoryItem` / `ProductDefinitionBatchLoader` / `ProductDefinitionSelectionTerminologysupport** 等依赖不变（母版 §2.1 已点名）。

### 2.2 领域方法白名单

| 类型 | 方法 | 用于 IF |
| --- | --- | --- |
| `LocationQueryService` | **`listAllActivePharmacies`** | IF-PHA-ST-01、02、04、05、11 |
| 其余 | 同 **WHS-SDD §2.2** | IF-PHA-ST-02～11 |

### 2.3 枚举、字典与值集

| 类别 | 代码实现锚点 | 说明 |
| --- | --- | --- |
| 单据类型 | **`PHARMACY_STOCKTAKE`** | IF-PHA-ST-04/05 |
| 序号键 | **`PST`** | **`BusinessSequenceService`** |
| 其余 | 同 **WHS-SDD §2.3** | **`SupplyStatus`**、行字段映射等 |

## 3. 按接口 ID 命名展开

**HTTP 路径生成规则**：**`POST /api/app/inventoryPharmacyStocktake/{methodName}`**。

**编排约定**：同 **WHS-SDD §3.1.1**。

### 3.1 应用服务接口（方法白名单）

| IF | 应用服务方法（英文） | 中文方法名 | HTTP |
| --- | --- | --- | --- |
| IF-PHA-ST-01 | `loadPharmacyStocktakeFormInit` | 进入盘点页面初始化 | `POST /api/app/inventoryPharmacyStocktake/loadPharmacyStocktakeFormInit` |
| IF-PHA-ST-02 | `pagePharmacyStocktakes` | 盘点单分页列表 | `POST /api/app/inventoryPharmacyStocktake/pagePharmacyStocktakes` |
| IF-PHA-ST-03 | `getPharmacyStocktakeDetail` | 盘点单详情 | `POST /api/app/inventoryPharmacyStocktake/getPharmacyStocktakeDetail` |
| IF-PHA-ST-04 | `createPharmacyStocktake` | 新建盘点单 | `POST /api/app/inventoryPharmacyStocktake/createPharmacyStocktake` |
| IF-PHA-ST-05 | `updatePharmacyStocktake` | 编辑盘点单 | `POST /api/app/inventoryPharmacyStocktake/updatePharmacyStocktake` |
| IF-PHA-ST-06 | `submitPharmacyStocktakeApproval` | 提交审批 | `POST /api/app/inventoryPharmacyStocktake/submitPharmacyStocktakeApproval` |
| IF-PHA-ST-07 | `withdrawPharmacyStocktakeApproval` | 撤回审批 | `POST /api/app/inventoryPharmacyStocktake/withdrawPharmacyStocktakeApproval` |
| IF-PHA-ST-08 | `agreePharmacyStocktake` | 审批同意 | `POST /api/app/inventoryPharmacyStocktake/agreePharmacyStocktake` |
| IF-PHA-ST-09 | `rejectPharmacyStocktake` | 审批驳回 | `POST /api/app/inventoryPharmacyStocktake/rejectPharmacyStocktake` |
| IF-PHA-ST-10 | `removePharmacyStocktake` | 删除单据 | `POST /api/app/inventoryPharmacyStocktake/removePharmacyStocktake` |
| IF-PHA-ST-11 | `pageProductsForPharmacyStocktake` | 盘点选品检索 | `POST /api/app/inventoryPharmacyStocktake/pageProductsForPharmacyStocktake` |

### 3.2～3.12 各 IF 契约与编排序列

除下表差异外，**Request/Response 字段、DTO 列族、领域编排序列、失败码语义** 与 **AS-INV-WHS-007-SDD §3.2～3.12** 一一对应（将 **`WHS_ST_*`** 替换为 **`PHA_ST_*`**，将 **`inventoryWarehouseStocktake`** 替换为 **`inventoryPharmacyStocktake`**）。

| IF | 药房差异要点 |
| --- | --- |
| IF-PHA-ST-01 | **`pharmacyLocationOptions`**：**`listAllActivePharmacies`** + **`CurrentManagedLocationIdsResolver#narrowActiveLocationsByManaged`** |
| IF-PHA-ST-02 | 筛选 **`typeEnum=PHARMACY_STOCKTAKE`**；**`PharmacyStocktakeListRowDto`** |
| IF-PHA-ST-03 | **`getPharmacyStocktakeDetail`**；类型门控 **`PHARMACY_STOCKTAKE`** |
| IF-PHA-ST-04/05 | **`createPharmacyStocktake` / `updatePharmacyStocktake`**；**`CreateStocktakeCommand`** 行 **`typeEnum=PHARMACY_STOCKTAKE`** |
| IF-PHA-ST-06～10 | 方法名带 **`Pharmacy`** 前缀；领域调用与药库相同 |
| IF-PHA-ST-08 | **`agreePharmacyStocktake`**；**PHA-ST-SDD-R-001**；库存位置为 **`purpose_location_id`（药房）** |
| IF-PHA-ST-11 | Request **`purposeLocationId`**（药房）；**`locationId=purposeLocationId`** |

**DTO 命名**：**`PharmacyStocktakeLineDto`** 字段与 **`StocktakeLineDto`** 相同（**`profitLossQuantity` / `actualQuantity` / `actualAmount` / `profitLossAmount`**）。

---

## 4. QueryService 与事件刷新

同 **WHS-SDD §4**；列表主路径 **`SupplyRequestQueryService`** + **`PHARMACY_STOCKTAKE`** 过滤。

---

## 5. 写库顺序（核心写接口视角）

同 **WHS-SDD §5**；类型码为 **`PHARMACY_STOCKTAKE`**。

---

## 6. 非功能性需求实现（承诺表）

同 **WHS-SDD §6**。

---

## 7. 测试要点

同 **WHS-SDD §7**；补充：**init 不含药库**；**药房位置门控**。

---

## 8. 变更历史

| 版本 | 日期 | 变更摘要 |
| --- | --- | --- |
| v* | 2026-05-28 | **ReadModel 弃用对齐**：§2.1/§2.2 增补 `module.querysupport（`EncounterContextFacade`、`*Loader`）；就诊上下文改 `*EncounterContextQueryModel` + Facade；医嘱列表骨架改 `InpatientOrderListSkeleton` + `InpatientOrderListMergeAssembler`/`InpatientOrderLookupLoader`；§3/§4 序列与 QueryService 口径与 `zxplat` 一致 |（已迁至 application.shared / feature support（见 DISSOLUTION.md））（已迁至 EncounterContextReader / application.shared.encounter）
| v0.1 | 2026-05-22 | 初稿：药房盘点目标态 SDD；编排互指 **AS-INV-WHS-007-SDD** |
| v0.2 | 2026-05-22 | 领域 **`createStocktake` 修订** 已落地（与药库共用） |
| v0.4 | 2026-05-24 | 同步母版 **`AS-INV-WHS-007-SDD` v0.3**：**统一库位门控**（**`CurrentManagedLocationIdsResolver`**）；§1.1、§2.1、§3.2 差异表 |
| v0.3 | 2026-05-23 | 应用层 **`InventoryPharmacyStocktakeAppService`** 及配套 Request/Response/DTO/Mapper 已实现 |

### 8.1 应用层交付（已实现）

| 项 | FQCN |
| --- | --- |
| 应用服务 | **`com.zxplat.domain.application.inventory.pharmacy.stocktake.service.InventoryPharmacyStocktakeAppService`** |
| Mapper | **`…stocktake.mapper.InventoryPharmacyStocktakeMapper`** / **`InventoryPharmacyStocktakeMapperImpl`** |

---

**文档负责人**：（待填） · **评审**：（待填）
