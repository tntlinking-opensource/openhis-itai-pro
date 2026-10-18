# GAP-INV-001 进货/出库「同意」读 Gate TOCTOU

> **问题类型**：并发双写与条件更新
> **严重度**：中危（重复入账）
> **影响范围**：药库+药房
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-005](../01-并发双写与条件更新/SYS-IMP-005-进销存单据与汇总发药并发边界.md)
> **关联 IF/BR**：`InventoryWarehouseInboundReceiptAppService#agreeInboundReceipt`；出库/药房入库对称路径

---

## 1. 问题描述

进货/出库「同意」读 Gate TOCTOU

## 2. 发生场景

两窗口对同一 `busNo` 几乎同时「同意」；均读到 `hasAgreed=false`、无完成态 Delivery，均 `agreeRequest` + `createSupplyDelivery` + 加库存。

## 3. 根因分析

① 防重在事务开头只读；② `agreeRequest` 虽用 `@Version`，两事务可能基于同一 version 均通过读 Gate 后顺序提交（第二笔乐观锁失败**若未捕获**仍可能部分编排）；③ 无 `UPDATE wkf_supply_request SET status=AGREE WHERE bus_no=? AND status=PENDING`。

## 4. 业务影响

duplicate 入库、批号价重复、账实不符。

## 5. 修改方案

1. **条件更新 agree**：`SupplyRequestRepository#agreeIfPending(busNo, expectedStatus)`，影响行数≠明细行数 → WHS_IN_104。
2. **Delivery 唯一**：`wkf_supply_delivery (supply_request_id)` 完成态部分唯一索引（`status=COMPLETED`）。
3. **保持现有** `OptimisticLockingFailureException` 映射与 `flushPendingChanges` 回读。
4. **对称改造**：药库出库、药房入库、退库、损益等 `agree*` 路径共用同一 Domain 方法。


## 6. 涉及代码

- `health-application/.../warehouse/inboundreceipt/service/InventoryWarehouseInboundReceiptAppService.java`
- `health-application/.../warehouse/stockout/service/InventoryWarehouseStockOutAppService.java`
- `zxplat-domain/.../supplyrequest/service/SupplyRequestDomainService.java`
- `zxplat-domain/.../supplyrequest/repository/SupplyRequestRepository.java`

## 7. 验收用例

- 两线程同一 busNo 同意：仅 1 次库存增加，另 1 WHS_IN_104/105。
- 同意后重复点同意：WHS_IN_104。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
