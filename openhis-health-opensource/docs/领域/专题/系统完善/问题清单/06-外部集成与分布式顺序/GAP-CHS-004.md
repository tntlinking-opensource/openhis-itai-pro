# GAP-CHS-004 预结算 refresh 与 confirm 竞态

> **问题类型**：外部集成与分布式顺序
> **严重度**：中危
> **影响范围**：门+住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-006](../06-外部集成与分布式顺序/SYS-IMP-006-医保平台与本地结算顺序.md)
> **关联 IF/BR**：SYS-IMP-002 BND-FIN-005、`refreshPaymentDraftPreSettleAmounts`

---

## 1. 问题描述

预结算 refresh 与 confirm 竞态

## 2. 发生场景

线程 A refresh 2206 作废旧 ACTIVE 建新 ACTIVE；线程 B 对旧 preId confirm。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

refresh 条件作废 `ACTIVE`；confirm 绑定 `preId` + `version`；前端 confirm 仅带最新 preId。

## 6. 工作量

2 人天

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
