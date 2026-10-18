# GAP-CLN-PRICE-001 锁价策略未闭合

> **问题类型**：临床规则与主数据
> **严重度**：中危
> **影响范围**：门+住
> **优先级**：P2
> **状态**：待实施
> **父文档**：[SYS-IMP-012](../07-临床规则与主数据/SYS-IMP-012-临床闭环Gate与主数据锁价.md)
> **关联 IF/BR**：**BND-PRICE-***（本文件边界节）

---

## 1. 问题描述

价表变更后旧 Charge 与报表/医保价漂移

## 2. 发生场景

医嘱签发/执行/滚费创建 `ChargeItem` 时写入 `unitPrice/totalPrice`；后续 `ChargeItemDefinition` 调价 **不**回写已存在行；医保上传可能读第三套价。

## 3. 根因分析

产品未定义「何时锁价、何时跟价」。

## 4. 业务影响

结算价与价表不一致；报表 SUM 与定义表对不上。

## 5. 修改方案

1. **产品规则**（须 PM 签字）：
   - **签发锁价**：PLANNED 行价格在签发时冻结；
   - **结算锁价**：preSettle 时刷新一次（可选）；
   - **退费时价**：按原 Charge 快照，不跟新价表。
2. **实现**：ChargeItem 增加 `priceDefinitionVersion` / `pricedAt`；结算 Gate 校验 definition 仍有效（失效则拒绝或按快照）。
3. 医保/报表 **只读 Charge 快照**，不读 live definition。


## 6. 涉及代码

- `ChargeItemDomainService` 各 create 路径
- `ChargeItemDefinitionQueryService`

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
