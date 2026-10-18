# GAP-BAT-005 报表 vs 交易事实

> **问题类型**：批处理与报表一致性
> **严重度**：中危
> **影响范围**：门诊
> **优先级**：P2
> **状态**：待实施
> **父文档**：[SYS-IMP-010](../05-批处理与报表一致性/SYS-IMP-010-批处理滚费与日结报表.md)

---

## 1. 问题描述

C 类报表与交账表口径漂移

## 2. 发生场景

`OutpatientDailySettlementReportDatasetService#build` 按 `findPaymentByBillDateRange` 聚合 SUCCESS，**不**过滤 `daily_close_id`、不 exclude 后续反结净额（取决于 query 是否 net）。

## 3. 根因分析

报表为 **实时扫库** 非 **交账快照**。

## 4. 业务影响

打印 C 类报表与出纳交账表不一致；监管检查风险。

## 5. 修改方案

1. **短期**：报表参数 `settlementBatchId` / `dailyCloseId`，只读已交账批次；
2. **长期**：日结生成 **不可变 snapshot 表**（`fin_daily_close_line`），报表只读 snapshot；
3. 文档标注：在 [GAP-BAT-004](../05-批处理与报表一致性/GAP-BAT-004.md) 完成前，C 类报表为 **参考值非法定交账**。


## 6. 涉及代码

- `OutpatientDailySettlementReportDatasetService.java`
- 住院日结对数据集（对称）

## 7. 验收用例

- 日结后对新 SUCCESS payment 退费 → 交账表不变；报表 batch 模式不变。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
