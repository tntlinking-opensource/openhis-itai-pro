# 科室药房盘点单 · 应用服务详细设计（SDD）

> 编码：AS-INV-DEP-006-SDD  
> 版本：v0.3 | 日期：2026-10-06

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| 模块 | 科室药房盘点单（**`DEPT_STOCKTAKE`**） |
| 边界 | 与 **AS-INV-PHA-006-SDD** 同构；差异：**`DST`/`DEPT_STOCKTAKE`**、**init 仅科室药房**、**经手人=全体在职医务人员**、**HTTP `inventoryDepartmentStocktake`** |
| 编排权威 | `AS-INV-DEP-006-科室盘点单.md` |
| 方法权威 | `DM-WKF-006` §6.1.7 **`createStocktake`**；`DM-WKF-005` §6.1.3 **`createStocktakingDelivery`** |
| 严格对齐 | IF **IF-DEP-ST-01～11** |

### 1.1 实现裁决

| 裁决 ID | 内容 |
| --- | --- |
| **DEP-ST-SDD-R-001** | 同意按 **`item_quantity`（盈亏）** 入账；零盈亏跳过；**整单全部为零仍同意成功**（不落发放）；盘亏 **`allowNegative=true`** |
| **DEP-ST-SDD-R-002** | **`updateStocktake`** → **`createStocktake`** 首行带 **`busNo`** 修订 |
| **DEP-ST-SDD-R-003** | 选品/详情单价：选品 **`LOT_NUMBER_COST`**；详情槽价仅落库行价 |
| **DEP-ST-SDD-R-004** | **IF-DEP-ST-01** 经手人：`PractitionerRoleScopeLoader#listAllActivePractitionersForInventoryDocHandlerSelect`；写入响应 **`pharmacistOptions`**（**`warehouseKeeperOptions`** 空列表）；**OptionDto** 填 **`pyStr`/`wbStr`** |
| **DEP-ST-SDD-R-005** | **`toCreateStocktakeCommand`** 须 **`item.setPractitionerId(request.getPractitionerId())`**（与药库盘点一致）；不得只写 **`applicantId`** |
| **DEP-ST-SDD-R-006** | 详情 **`GetStocktakeDetailResponse.practitionerId`** 来自供应行落库；前端已落库单禁止用登录人覆盖经手人下拉值 |

## 2. 代码结构

```text
com/openhis/health/application/inventory/department/stocktake/
  service/InventoryDepartmentStocktakeAppService.java
  request/   LoadStocktakeFormInitRequest、PageStocktakesRequest …
  response/
  dto/       StocktakeListRowDto、StocktakeLineDto、StocktakeDetailLineDto
  mapper/    InventoryDepartmentStocktakeMapper / MapperImpl
```

### 2.1 依赖注入

| 依赖 | 用途 |
| --- | --- |
| `LocationQueryService#listAllActiveDepartmentPharmacies` | IF-DEP-ST-01 及门控 |
| `CurrentManagedLocationIdsResolver` | **`InventoryDepartmentConstants`** 列表占位 |
| `PractitionerRoleScopeLoader#listAllActivePractitionersForInventoryDocHandlerSelect` | IF-DEP-ST-01 经手人（全体在职） |
| `SupplyRequestDomainService#createStocktake` / 定价写编排 | IF-DEP-ST-04/05 |
| `SupplyDeliveryDomainService#createStocktakingDelivery` | IF-DEP-ST-08 |
| 其余 | 同 **AS-INV-PHA-006-SDD** |

## 3. 应用服务接口

**HTTP**：`POST /api/app/inventoryDepartmentStocktake/{methodName}`

| IF | 方法 | HTTP |
| --- | --- | --- |
| IF-DEP-ST-01 | `loadStocktakeFormInit` | `…/loadStocktakeFormInit` |
| IF-DEP-ST-02 | `pageStocktakes` | `…/pageStocktakes` |
| IF-DEP-ST-03 | `getStocktakeDetail` | `…/getStocktakeDetail` |
| IF-DEP-ST-04 | `createStocktake` | `…/createStocktake` |
| IF-DEP-ST-05 | `updateStocktake` | `…/updateStocktake` |
| IF-DEP-ST-06 | `submitStocktakeApproval` | `…/submitStocktakeApproval` |
| IF-DEP-ST-07 | `withdrawStocktakeApproval` | `…/withdrawStocktakeApproval` |
| IF-DEP-ST-08 | `agreeStocktake` | `…/agreeStocktake` |
| IF-DEP-ST-09 | `rejectStocktake` | `…/rejectStocktake` |
| IF-DEP-ST-10 | `removeStocktake` | `…/removeStocktake` |
| IF-DEP-ST-11 | `pageProductsForStocktake` | `…/pageProductsForStocktake` |

DTO：**`StocktakeLineDto`** 含 **`profitLossQuantity` / `actualQuantity` / `actualAmount` / `profitLossAmount`**。

### 3.1 IF-DEP-ST-01 `loadStocktakeFormInit`（摘要）

| 序号 | 阶段 | 调用 | 说明 |
| --- | --- | --- | --- |
| 1 | Gate | `CurrentManagedOrganizationIdsResolver#requireSessionOrganizationUnitId` | 机构上下文 |
| 2 | Pre | `LocationQueryService#listAllActiveDepartmentPharmacies` + 管辖收窄 | **`pharmacyLocationOptions`** |
| 3 | Pre | `PractitionerRoleScopeLoader#listAllActivePractitionersForInventoryDocHandlerSelect` | 全体在职；**不**按 `ROLE_PHARMACIST`/`ROLE_WAREHOUSE` |
| 4 | Asm | `toOptionDtoListFromPractitioners`（含 **`pyStr`/`wbStr`**）→ **`pharmacistOptions`**；**`warehouseKeeperOptions`=`[]`** | **DEP-ST-SDD-R-004** |

### 3.2 IF-DEP-ST-04/05 经手人落库（摘要）

| 序号 | 阶段 | 说明 |
| --- | --- | --- |
| Gate | `request.practitionerId` 非空 | AS **BR-DEP-ST-002** |
| Asm | `CreateStocktakeItemCommand#setPractitionerId` + `setApplicantId`（会话制单人） | **DEP-ST-SDD-R-005** |

## 4. 写库顺序（IF-DEP-ST-08）

1. **`agreeRequest`**
2. **`createStocktakingDelivery`**（仅非零盈亏行；**整单全零则跳过**）
3. 库存逐行加减（同 **WHS-ST-SDD-R-001**；零盈亏行跳过）

## 5. 应用层交付（已实现）

| 项 | FQCN |
| --- | --- |
| 应用服务 | `com.zxplat.application.inventory.department.stocktake.service.InventoryDepartmentStocktakeAppService` |
| Mapper | `…stocktake.mapper.InventoryDepartmentStocktakeMapper` / `InventoryDepartmentStocktakeMapperImpl` |
| 经手人 Loader | `com.zxplat.application.shared.practitioner.PractitionerRoleScopeLoader#listAllActivePractitionersForInventoryDocHandlerSelect` |

## 6. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.3 | 2026-10-06 | §5：经手人 Loader / AppService FQCN 对齐 `shared.practitioner` 与 `zxplat-application` |
| v0.2 | 2026-08-31 | **DEP-ST-SDD-R-004～006**：经手人全体在职 + 拼音码；建单写入 `practitionerId`；详情回显不以登录人覆盖 |
| v0.1 | 2026-07-01 | 初稿：对齐 `InventoryDepartmentStocktakeAppService` 实现 |

---

**文档负责人**：（待填）
