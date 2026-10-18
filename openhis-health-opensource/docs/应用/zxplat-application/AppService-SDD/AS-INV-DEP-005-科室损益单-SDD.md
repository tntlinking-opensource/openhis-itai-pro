# 科室药房损益单 · 应用服务详细设计（SDD）

> 编码：AS-INV-DEP-005-SDD  
> 版本：v0.2 | 日期：2026-08-31

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| 模块 | 科室药房损益单（**`DEPT_GAIN_LOSS`**，`SupplyType#DEPT_GAIN_LOSS`） |
| 边界 | 与 **AS-INV-PHA-004** / 药房 **`InventoryPharmacyGainLossAppService`** 同构；差异：**科室药房位置门控**、**HTTP 前缀 `inventoryDepartmentGainLoss`** |
| 编排权威 | `AS-INV-DEP-005-科室损益单.md` |
| 方法权威 | `DM-WKF-006` §6.1.6 **`createGainLoss`**；`DM-WKF-005` **`createSupplyDelivery`**；`DM-WKF-008` 库存加减 |
| 严格对齐 | IF **IF-DEP-GL-01～11** |

### 1.1 术语

| 项 | 约定 |
| --- | --- |
| **`bus_no`** | 序号键 **`DGL`**（`SupplyType.busNoSeqPrefixForTypeCode`） |
| 损益库房 | **`purposeLocationId`**；形态 **`LocationForm.DEPARTMENT_PHARMACY`** |
| 列表「全部」 | **`purposeLocationId=0`** → `InventoryDepartmentConstants.ALL_DEPARTMENT_PHARMACIES_LIST_FILTER_LOCATION_ID` |
| 同意库存 | 报溢可 **`createInventoryItem`**；报损 **`decreaseQuantity(allowNegative=true)`** |
| 详情槽价 | **BR-PRICE-SRC-001**：仅 **`SupplyRequest.price`**，禁止当前目录价覆盖 |

## 2. 代码结构

```text
com/openhis/health/application/inventory/department/gainloss/
  service/InventoryDepartmentGainLossAppService.java
  request/   LoadGainLossFormInitRequest、PageGainLosssRequest …
  response/
  dto/       GainLossListRowDto、GainLossLineDto、GainLossDetailLineDto
  mapper/    InventoryDepartmentGainLossMapper / MapperImpl
```

### 2.1 依赖注入（与药房损益同构，位置 API 替换）

| 依赖 | 用途 |
| --- | --- |
| `LocationQueryService#listAllActiveDepartmentPharmacies` | IF-DEP-GL-01/02/04/11 科室药房下拉与门控 |
| `PractitionerRoleScopeLoader#listAllActivePractitionersForInventoryDocHandlerSelect` | IF-DEP-GL-01 经手人（全体在职 + 拼音码） |
| `CurrentManagedLocationIdsResolver` | 管辖 Gate（`InventoryDepartmentConstants` 占位 id） |
| `SupplyRequestDomainService#createGainLoss` | IF-DEP-GL-04/05 |
| `SupplyDeliveryDomainService#createSupplyDelivery` | IF-DEP-GL-08（**非**盘点发放） |
| 其余 | 同药房损益：`ProductDefinitionBatchLoader`、`ChargeItemDefinitionQueryService`（选品当前价）等 |

### 2.2 领域方法白名单

同 **AS-INV-PHA-004** 药房损益；`type_enum` 固定 **`DEPT_GAIN_LOSS`**。

## 3. 应用服务接口

**HTTP**：`POST /api/app/inventoryDepartmentGainLoss/{methodName}`

| IF | 方法 | HTTP 路径 |
| --- | --- | --- |
| IF-DEP-GL-01 | `loadGainLossFormInit` | `…/loadGainLossFormInit` |
| IF-DEP-GL-02 | `pageGainLosss` | `…/pageGainLosss` |
| IF-DEP-GL-03 | `getGainLossDetail` | `…/getGainLossDetail` |
| IF-DEP-GL-04 | `createGainLoss` | `…/createGainLoss` |
| IF-DEP-GL-05 | `updateGainLoss` | `…/updateGainLoss` |
| IF-DEP-GL-06 | `submitGainLossApproval` | `…/submitGainLossApproval` |
| IF-DEP-GL-07 | `withdrawGainLossApproval` | `…/withdrawGainLossApproval` |
| IF-DEP-GL-08 | `agreeGainLoss` | `…/agreeGainLoss` |
| IF-DEP-GL-09 | `rejectGainLoss` | `…/rejectGainLoss` |
| IF-DEP-GL-10 | `removeGainLoss` | `…/removeGainLoss` |
| IF-DEP-GL-11 | `pageProductsForGainLoss` | `…/pageProductsForGainLoss` |

### 3.1 药房差异要点（相对 PHA-GL）

| 项 | 科室药房 |
| --- | --- |
| init 库位 | **`listAllActiveDepartmentPharmacies`** + Gate |
| init 经手人 | **`listAllActivePractitionersForInventoryDocHandlerSelect`** → **`pharmacistOptions`**（含 **`pyStr`/`wbStr`**；**`warehouseKeeperOptions`** 可空） |
| 门控 | **`LocationForm.DEPARTMENT_PHARMACY`** + **`DEP_GL_002`** |
| 行 **`typeEnum`** | **`DEPT_GAIN_LOSS`** |
| 同意发放 | **`createSupplyDelivery`**（须财务审批） |

Request/DTO 命名：**`GainLoss*`**（无 Department 前缀）；字段与药房 **`PharmacyGainLoss*`** 同构（**`gainLossQuantity`** 等）。

## 4. 写库顺序（IF-DEP-GL-08）

1. **`agreeRequest`**
2. **`flushPendingChanges`** + 回读 AGREE
3. **`createSupplyDelivery`**
4. 逐行 **`increaseQuantity` / `decreaseQuantity` / `createInventoryItem`**
5. 可选 CHS **`uploadAfterGainLossAgree`**

## 5. 应用层交付（已实现）

| 项 | FQCN |
| --- | --- |
| 应用服务 | `com.zxplat.domain.application.inventory.department.gainloss.service.InventoryDepartmentGainLossAppService` |
| Mapper | `…gainloss.mapper.InventoryDepartmentGainLossMapper` / `InventoryDepartmentGainLossMapperImpl` |
| 常量 | `com.zxplat.domain.share.constants.InventoryDepartmentConstants` |
| 错误码 | `ErrorCodeConstants.DEP_GL_001`～`DEP_GL_105` |

## 6. 变更历史

| 版本 | 日期 | 摘要 |
| --- | --- | --- |
| v0.2 | 2026-08-31 | init 经手人改为全体在职医务人员（含拼音），不限药师/库管 |
| v0.1 | 2026-07-01 | 初稿：对齐 `InventoryDepartmentGainLossAppService` 实现 |

---

**文档负责人**：（待填）
