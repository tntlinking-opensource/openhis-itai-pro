# GAP-CHS-002 本地 SUCCESS 但平台 confirm 失败/超时

> **问题类型**：外部集成与分布式顺序
> **严重度**：**高危**
> **影响范围**：门+住
> **优先级**：P0
> **状态**：待实施
> **父文档**：[SYS-IMP-006](../06-外部集成与分布式顺序/SYS-IMP-006-医保平台与本地结算顺序.md)

---

## 1. 问题描述

本地 Payment 已 SUCCESS 但平台 confirm 失败/超时

## 2. 发生场景

编排顺序错误：先 `confirmOutpatientChargeSettlement` 本地 SUCCESS，再调 `confirmSettle` 2207 超时/失败 → 患者已付、医保无结算。

## 3. 根因分析

本地收银与医保扩展 **非原子**；缺少 Saga /  outbox 与 2601 补偿闭环。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

① **推荐顺序**：平台 2207/2304 成功 → 落 `YbSettlementRecord` → 本地 Payment SUCCESS（或同一事务最后一步）；② 若必须本地先扣款：引入 **TCC / 补偿表** 记录 `PENDING_PLATFORM_CONFIRM`，定时 2601 + 本地冲正；③ UI 禁止在 2206 未 ACTIVE 时启用正式结算。


## 6. 涉及代码

`OutpatientChargeAppService` 医保扩展、`ChsOutpatientSettlementFacadeImpl`、前端收银流程

## 7. 工作量

5～8 人天（含联调与演练）

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
