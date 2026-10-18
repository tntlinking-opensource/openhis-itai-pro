# GAP-REG-010 汇总核发追溯一致性

> **问题类型**：监管合规与追溯
> **严重度**：中危
> **影响范围**：住
> **优先级**：P1
> **状态**：待验收
> **父文档**：[SYS-IMP-013](../08-监管合规与追溯/SYS-IMP-013-监管合规链与核发追溯.md)

---

## 1. 问题描述

汇总核发追溯与单行核发口径一致性

## 2. 发生场景

`InpatientPharmacySummaryDispenseAppService` 含 `SummaryDispenseTraceLineSubmitDto`；须验收与单行核发 **同一套** REG-001/002 Gate。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

抽取 `DispenseTraceWriteGatesupport 供单行/汇总共用；回归 IMP-005 [GAP-INV-003](../01-并发双写与条件更新/GAP-INV-003.md) 并发场景。

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
