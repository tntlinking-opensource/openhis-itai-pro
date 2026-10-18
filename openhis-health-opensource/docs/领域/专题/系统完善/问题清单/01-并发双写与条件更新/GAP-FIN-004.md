# GAP-FIN-004 财务实体乐观锁未映射

> **问题类型**：并发双写与条件更新
> **严重度**：中危（跨切面，放大 GAP-FIN-001/002/005）
> **影响范围**：门+住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-002](../01-并发双写与条件更新/SYS-IMP-002-收费结算与账户并发边界.md)

---

## 1. 问题描述

财务实体 DB 有 `version` 但 JPA 未映射乐观锁

## 2. 发生场景

表 `fin_payment_reconciliation`、`adm_charge_item`、`adm_account` 均有 `version` 列及 COMMENT「乐观锁」；对应 JPA 实体**多数未** `@Version`。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

并发写 last-write-wins；与 AS-SDD 承诺不一致。

## 5. 修改方案

1. 为 `PaymentReconciliation`、`ChargeItem`、`Account` 增加 `@Version Integer version`。
2. 捕获 `OptimisticLockException` → 统一业务码「数据已被他人修改，请刷新」。
3. 与 [GAP-FIN-001](../01-并发双写与条件更新/GAP-FIN-001.md) 条件更新二选一或叠加（条件更新更硬，乐观锁作补充）。

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
