# GAP-EXE-001 住院库存 Gate TOCTOU

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住院
> **优先级**：P1
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)
> **关联 IF/BR**：IF-IP-EXE-02；BR-IP-EXE-014/016/022

---

## 1. 问题描述

住院库存 Gate TOCTOU（全局预占读时不锁）

## 2. 发生场景

两名护士（或两请求）几乎同时对不同医嘱执行**同一药品、同一药房批次**；两请求 Gate dry-run 均读到「够用」，均新建 `PREPARATION` Dispense；预占之和超过物理库存。

## 3. 根因分析

`NurseStationExecutionInventoryAssembler#prepareMedicationFifoBatchPlan` 在事务内**只读**物理库存 + `listDispenseExeReserve`，无 `SELECT FOR UPDATE`；Gate 与写 Dispense 非原子可见。

## 4. 业务影响

多发药单、多记待结算费；药房核发部分失败；运维需取消执行/作废。

## 5. 修改方案

1. **方案 A（优先，改动适中）**：Gate 前对本批涉及的 `(itemTable, itemId, locationId[, lotNumber])` 调用 `InventoryItemRepository#findByKeysForUpdate`（`SELECT … FOR UPDATE`，按 key 排序防死锁）。
2. 在**已锁定行**上重算：物理量 − `listDispenseExeReserve`（全局，不限就诊）− 本事务已插入 Dispense。
3. FIFO 分段通过后**立即**同循环调用 `generateDispenseFromExecution`（保持 Procedure → Dispense 同事务，BR-IP-EXE-022）。
4. **方案 B（中长期）**：独立预占表 `med_dispense_inventory_reserve`，Gate 通过 insert reserve；`listDispenseExeReserve` 合并 Dispense + reserve 汇总。


## 6. 涉及代码

- `health-application/.../orderexecute/assembler/NurseStationExecutionInventoryAssembler.java`
- `zxplat-domain/.../inventoryitem/repository/InventoryItemRepository.java`（新增 FOR UPDATE 查询）
- `zxplat-domain/.../medicationdispense/service/MedicationDispenseQueryService.java`（若方案 B）

## 7. 验收用例

- 两线程：库存 10 片，各执行需 6 片 → 至多 1 条 Dispense 成功，另一条 `EX-IP-EXE-302` 或核发失败。
- 同批多槽位仍按 working 库存逐行扣减，批内不超分。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | `InventoryItemRepository#findWithLockByIdIn` + Gate 前锁行；状态 → 已实施 |
