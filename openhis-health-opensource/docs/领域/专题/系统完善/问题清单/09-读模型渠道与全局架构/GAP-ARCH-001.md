# GAP-ARCH-001 第三方支付对账缺失

> **问题类型**：读模型渠道与全局架构
> **严重度**：中危
> **影响范围**：门诊
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-014](../09-读模型渠道与全局架构/SYS-IMP-014-渠道对账读模型与全局幂等.md)
> **关联 IF/BR**：IF-OPC-07 支付明细；报表 WECHAT/ALIPAY 汇总

---

## 1. 问题描述

微信/支付宝渠道账单与本地 Payment 无对账

## 2. 发生场景

正式结算写本地 `PaymentReconciliation` SUCCESS + `payEnum=WECHAT/ALIPAY`；**无**拉取微信/支付宝商户对账单与本地逐笔核对、掉单补单/退单流程。

## 3. 根因分析

当前为 **记账型** 渠道码，非 **联机型** 支付。

## 4. 业务影响

用户已付、本地未 SUCCESS；或本地 SUCCESS、渠道无单；日结 C 类报表与渠道实收不符。

## 5. 修改方案

1. **联机支付 Adapter**（若上线扫码付）：预下单 → 回调 → 本地 SUCCESS，回调 **幂等**；
2. **对账 Job**：T+1 拉渠道 bill → 匹配 `merchantOrderNo` ↔ `payment.busNo` → 差异表；
3. **掉单补偿**：渠道有、本地无 → 补记或告警；本地有、渠道无 → 冻结退费直至核实；
4. 与 **IMP-010 BAT-005** 报表 snapshot 共用 `fin_recon_channel_line`。


## 6. 涉及代码

- `PaymentReconciliationDomainService`
- 新建 `ChannelReconciliationAppService` / Job
- `OutpatientDailySettlementReportDatasetService`（读 snapshot）

## 7. 验收用例

- 模拟回调重复 → 1 条 SUCCESS。
- 对账 Job 发现差异 → 进差异表。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
