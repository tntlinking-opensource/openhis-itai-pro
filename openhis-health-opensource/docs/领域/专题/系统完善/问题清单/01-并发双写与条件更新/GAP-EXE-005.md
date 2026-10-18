# GAP-EXE-005 门诊签发 FIFO 同就诊 TOCTOU

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：门诊
> **优先级**：P2
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)
> **关联 IF/BR**：IF-DOR-15；BR-ORD-021；BR-ORD-011

---

## 1. 问题描述

门诊签发 FIFO 同就诊 TOCTOU

## 2. 发生场景

双窗口对同一就诊批量签发；占用预减为**同就诊**口径，签发 FIFO 读时不锁；可能签出超量 Dispense。

## 3. 根因分析

`DoctorStationAdviceFifoAllocationAssembler` 与目录预减均为读时校验；无 request 级或库存行锁。

## 4. 业务影响

超量待发单、待收费行增多。

## 5. 修改方案

1. 签发事务内对 FIFO 涉及库存行 `FOR UPDATE`（与 [GAP-EXE-001](../01-并发双写与条件更新/GAP-EXE-001.md) 方案 A 同构）。
2. 或签发前对 `med_medication_request` / `wkf_device_request` 行 `FOR UPDATE`，保证同 request 不重写发放。
3. 与住院全局预占：`listDispenseExeReserve` 为全局，门诊签发占用仅同就诊——**跨就诊抢库存**仍靠 [GAP-EXE-001](../01-并发双写与条件更新/GAP-EXE-001.md)/003 统一。


## 6. 涉及代码

- `health-application/.../outpatient/doctorstation/.../DoctorStationAdviceFifoAllocationAssembler`（或等价装配器）
- `zxplat-domain/.../inventoryitem/repository/InventoryItemRepository.java`

## 7. 验收用例

- 同就诊双签发同一药嘱：仅一套 Dispense 分段生效或第二请求显式冲突。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 签发 FIFO 前 `lockInventoryRowsForCatalogAvailability`；状态 → 已实施 |
