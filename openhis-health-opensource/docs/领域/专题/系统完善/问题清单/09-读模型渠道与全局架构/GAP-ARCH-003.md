# GAP-ARCH-003 队列读模型分裂

> **问题类型**：读模型渠道与全局架构
> **严重度**：中危
> **影响范围**：门+住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-014](../09-读模型渠道与全局架构/SYS-IMP-014-渠道对账读模型与全局幂等.md)

---

## 1. 问题描述

Encounter 队列读模型与 Charge/Payment 实时态分裂

## 2. 发生场景

`pageOutpatientChargeQueue`：分页 id 来自 `outpatientChargeEncounterQueueLoader`，详情来自 `encounterContextFacade`；标签来自 **实时** `applyQueueLabelCodes`（Charge + Payment）。若 Context 投影滞后，**行展示与标签/写 Gate 不一致**。

## 3. 根因分析

CQRS：写库即时、读模型 eventual；队列未统一 **单一事实源**。

## 4. 业务影响

列表无该就诊但能 loadPending；或仍显示待收已结清。

## 5. 修改方案

1. **短期**：写成功后 API 返回 `invalidateEncounterIds`；前端强制刷新（IMP-007 §8）；
2. **中期**：队列分页 **以 Charge 事实表驱动**（`pageEncounterIdsByChargeStatus`），Context 仅展示字段；
3. **长期**：投影 consumer 监控 lag；超阈值告警。


## 6. 涉及代码

- `OutpatientChargeAppService#pageOutpatientChargeQueue`
- `OutpatientChargeEncounterQueueLoader`
- `EncounterContextReader`
- 住院 `pageInpatientSettleQueue` 对称

## 7. 验收用例

- 结算成功后 5s 内队列 tab=待收 **不含**该 encounter（或标已结）。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
