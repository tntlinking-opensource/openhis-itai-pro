# 药库退货单 · 应用服务详细设计（SDD）

> 编码：AS-INV-WHS-005-SDD · 版本：v0.7 | 日期：2026-08-12

## 1. 实现边界

| 项 | 内容 |
| --- | --- |
| 模块 | `inventory/warehouse/returntosupplier` · `CABINET_RETURN_TO_SUPPLIER` |
| 编排权威 | `AS-INV-WHS-005-药库退货单.md`（**v0.6**） |
| 领域权威 | `DM-WKF-006` §6.1.3（**v1.21** **BR-SR-019**）· `DM-WKF-005` · `DM-WKF-008` · `DM-ADM-011` **BR-PRICE-SRC-001** |
| 严格对齐 | IF **IF-WHS-RT-01～13** |

### 实现裁决

| ID | 裁决 |
|----|------|
| **WHS-RT-SDD-R-001** | **IF-WHS-RT-02** 列表真源：`SupplyRequestQueryService#countDistinctBusNo` / `#listDistinctBusNoPage` / `#listByBusNosWithFilter`，Mapper `#toReturnToSupplierListAggregated`（写出 `applicantId`/`approverId`/`supplierId`，整单合计写 **`totalAmount`**） |
| **WHS-RT-SDD-R-002** | **IF-WHS-RT-12**：进货单 `type=CABINET_INBOUND_RECEIPT` + `AGREE` + `purpose_location_id=sourceLocationId`；按 `bus_no` 聚合；过滤 `ReturnToSupplierInboundLineSupport#inboundHasAnyReturnableLine` |
| **WHS-RT-SDD-R-003** | **IF-WHS-RT-13**：`getLinkableGoodsReceiptDetail`；可退余量由 `ReturnToSupplierInboundLineSupport#computeReturnableQuantityByLineKey`；`excludeReturnBusNo` 供改单预填；头可回显进货 **`invoiceNo`**；**追溯码**由 **`ReturnToSupplierRemainingTracePrefiller`** 覆盖（**BR-WHS-RT-008** / **TRC-SDD-R-008**）：本库在库须主档在院剩余&gt;0，截码 `min(可退, 源库可用)`；Mapper 不再采用进货行历史 `trace_no`；Asm 后 **`enrichLinkableReturnableLinePricesForPreview`**：槽价锚进货落库价，**`totalPrice` 按可退拆零量比例回算** |
| **WHS-RT-SDD-R-004** | **IF-WHS-RT-04/05/08**：`assertDraftLinesWithinInboundReturnable` + `assertReturnToSupplierDraftLinesWithinSourceAvailableInventory`；源库不足文案须含「须先调拨回进货药库」 |
| **WHS-RT-SDD-R-005** | **已删除**原 **IF-WHS-RT-11**（`pageProductsForReturnToSupplier`）；退货明细仅经 **IF-WHS-RT-12/13** 从进货单带入 |
| **WHS-RT-SDD-R-006** | **IF-WHS-RT-08**：`agreeRequest` → flush → 防重复发放 → `createSupplyDelivery` → `decreaseQuantitiesMerged`（源库）→ 追溯 OUT → 可选 3504（`purc_invo_no`：本行/`进货`/`0`） |
| **WHS-RT-SDD-R-007** | 修订：`createReturnToSupplier` 携带 `busNo` 软删重插；**禁止** `rowVersion`；`original_bus_no` 自存量快照，客户端不可改 |
| **WHS-RT-SDD-R-008** | `practitioner_id`：请求体可选；落 `CreateReturnToSupplierCommand` 各行 |
| **WHS-RT-SDD-R-009** | **`invoiceNo`**：详情/新建/更新单头可选；Mapper 写入命令各行；领域不拦空；进销存见 **09** §4.5e |
| **WHS-RT-SDD-R-010** | **IF-WHS-RT-02** 列表富化：`enrichReturnToSupplierListDisplayNames`（`supplierName`/`creatorName`/`approverName`）；合计 0/空时 `enrichReturnToSupplierListAmountsFromInboundIfMissing` 按进货行 `total_price × 退货拆零量 / 进货拆零量` 回补展示（**不写库**；**BR-WHS-RT-010**） |
| **WHS-RT-SDD-R-011** | **IF-WHS-RT-04/05** 写价：AppService `fillReturnToSupplierCommandPricesFromInbound`（行键对齐进货 `price`）→ 领域 `createReturnToSupplier` 前置 `SupplyRequestWritePricingSupport#applyReturnToSupplierCommand`（拆零量 × 包装锚单价，**BR-MON-PKG-001**；缺价强制批号进价，禁止目录药典价）→ 实体落 `price`/`total_price`（**BR-WHS-RT-009** / **BR-MON-001**） |
| **WHS-RT-SDD-R-012** | **IF-WHS-RT-03** 详情金额：Mapper `toDetailLine` 写出行 `price`/`totalPrice` 与头整单合计；`enrichReturnToSupplierDetailAmountsFromInboundIfMissing`（历史缺价只读回补）→ `enrichReturnToSupplierDetailCatalogSlotPrices`（`enrichWarehouseDetailCatalogFromPersistedSupplyLinePrice`）→ `refreshReturnToSupplierDetailHeaderTotals`（**BR-WHS-RT-011**） |

## 2. 代码结构

```text
com/openhis/health/application/inventory/warehouse/returntosupplier/
  service/InventoryWarehouseReturnToSupplierAppService.java
  support/ReturnToSupplierInboundLineSupport.java
  request/GetLinkableGoodsReceiptDetailRequest.java
  response/GetLinkableGoodsReceiptDetailResponse.java
  dto/ReturnableInboundLineDto.java
  dto/LinkableGoodsReceiptListRowDto.java
  mapper/InventoryWarehouseReturnToSupplierMapper(Impl).java
```

## 3. 接口映射（HTTP 方法名）

| IF | AppService 方法 |
|----|-----------------|
| RT-01 | `loadReturnToSupplierFormInit` |
| RT-02 | `pageReturnToSuppliers` |
| RT-03 | `getReturnToSupplierDetail` |
| RT-04 | `createReturnToSupplier` |
| RT-05 | `updateReturnToSupplier` |
| RT-06～10 | `submit*` / `withdraw*` / `agree*` / `reject*` / `remove*` |
| RT-12 | `pageLinkableGoodsReceipts` |
| RT-13 | `getLinkableGoodsReceiptDetail` |

> 原 **RT-11**（`pageProductsForReturnToSupplier`）已删除；禁止自由选品，明细仅经 RT-12/13。

## 4. 可退余量算法（与 DM BR-SR-009 一致）

```
行键 K = normalize(item_table) + item_id + normalize(lot_number)
进货量 inbound[K] = Σ 进货单 AGREE 行
已占用 returned[K] = Σ 退货单(original_bus_no=进货 bus_no, status∈{PENDING,APPROVAL,AGREE}, 排除本 bus_no) 
可退 returnable[K] = max(0, inbound[K] − returned[K])
```

## 5. 事务与异常

- 写接口 `@Transactional` 默认；IF-RT-08 单事务覆盖 agree + 发放 + 扣库 + 追溯。
- 库存扣减失败 → `WHS_RT_104` 包装领域消息。

## 5.1 IF-WHS-RT-12 / IF-WHS-RT-13 读侧契约（2026-07-02 对齐）

**IF-WHS-RT-12 `pageLinkableGoodsReceipts`**

| 响应字段（`LinkableGoodsReceiptListRowDto`） | 说明 |
| --- | --- |
| `supplierName` | 应用层批量解析供应商展示名 |
| `applyTime` | 制单/审批时刻展示（与 `approvalTime` 同源） |

**IF-WHS-RT-13 `getLinkableGoodsReceiptDetail`**

| 响应字段 | 说明 |
| --- | --- |
| `supplierName` | 表头供应商展示名 |
| `lines[].productName` / `specification` / `manufacturer` / `unitCodeDictText` | 产品读模型富化 |
| `lines[].productCatalog` | 单位带与参考价（**BR-PRICE-SRC-001**：行价仍取 **`SupplyRequest.price` 落库价**） |

列表 **`sourceLocationId=0`** 时展开为可见药库 id 集合（**`InventoryWarehouseConstants`**，同 **INV 功能清单 §10.1**）。

## 5.2 IF-WHS-RT-02 列表行契约（2026-07-26）

**`ReturnToSupplierListRowDto`（聚合一行一单）**

| 字段 | 说明 |
| --- | --- |
| `applicantId` / `creatorName` | 头行申请人；展示名应用层批量解析 |
| `approverId` / `approverName` | 头行审批人；审中可空 |
| `supplierId` / `supplierName` | 供应商；展示名应用层批量解析 |
| `totalAmount` | 整单合计（侧栏读此字段）；优先落库行 `total_price` 之和 |
| `applyTime` | 优先 `apply_time` 最大，缺省回落 `occurrence_time` |

## 5.3 IF-WHS-RT-03 详情金额契约（2026-08-12）

**`GetReturnToSupplierDetailResponse` / `ReturnToSupplierDetailLineDto`**

| 字段 | 说明 |
| --- | --- |
| `lines[].price` | 包装锚进价快照（落库 `SupplyRequest.price`；历史缺价按进货行 `price` 只读回补） |
| `lines[].totalPrice` | 行合计（落库 `total_price`；历史缺价按进货总额比例回补） |
| `totalAmount` | 整单合计；`refreshReturnToSupplierDetailHeaderTotals` |
| `lines[].productCatalog` | 槽价 `enrichWarehouseDetailCatalogFromPersistedSupplyLinePrice`（**禁止**当前 ACTIVE 目录价） |

## 5.4 IF-WHS-RT-04 / 05 写价链路（2026-07-26）

```
fillReturnToSupplierCommandPricesFromInbound（进货行 price → 命令行）
  → SupplyRequestDomainService#createReturnToSupplier
      → writePricingSupport.applyReturnToSupplierCommand（刻度 + 拆零行额 BR-MON-PKG-001；缺价回落批号进价）
      → buildReturnToSupplierEntityFromItem 落 price/total_price
```

## 6. 关联文档

- `AS-INV-WHS-005-药库退货单.md`（**v0.6**）
- `AS-CUS-TRC-001` / `AS-CUS-TRC-001-SDD`（**BR-TRC-022** / **TRC-SDD-R-008**）
- `DM-WKF-006-SupplyRequest-供应请求.md` §6.1.3（**v1.21** **BR-SR-019**；**v1.19** `invoice_no`）
- `DM-ADM-011` **BR-PRICE-SRC-001**；`monetary-amount-contract.md`（**BR-MON-001** / **BR-MON-PKG-001**）
- `09-医保进销存-详细设计.md` §4.5e

### 6.1 变更历史

| 版本 | 日期 | 变更摘要 |
|------|------|----------|
| v0.7 | 2026-08-12 | **WHS-RT-SDD-R-012**：IF-03 行/整单金额 + 槽价锚落库价；**R-003/011** 可退预览行额与缺价回落批号进价 |
| v0.6 | 2026-08-11 | **WHS-RT-SDD-R-003**：本库在库叠加主档在院剩余；Prefiller 截码 `min(可退, 源库可用)`；编排权威 **AS v0.5** |
| v0.5 | 2026-07-26 | **WHS-RT-SDD-R-010/011**：IF-02 侧栏富化 + 历史金额读侧回补；IF-04/05 进货价写价落库 |
| v0.4 | 2026-07-25 | **WHS-RT-SDD-R-003**：IF-13 追溯码改当前剩余预填（**BR-WHS-RT-008**） |
| v0.3 | 2026-07-19 | **WHS-RT-SDD-R-009** `invoiceNo`；3504 `purc_invo_no` 解析序 |
| v0.2 | 2026-07-02 | IF-12/13 读侧契约 |