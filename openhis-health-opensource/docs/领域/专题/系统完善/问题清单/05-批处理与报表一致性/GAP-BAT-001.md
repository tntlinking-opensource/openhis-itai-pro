# GAP-BAT-001 滚费 Job 幂等 TOCTOU

> **问题类型**：批处理与报表一致性
> **严重度**：中危（重复床费/护理费）
> **影响范围**：住
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-010](../05-批处理与报表一致性/SYS-IMP-010-批处理滚费与日结报表.md)
> **关联 IF/BR**：IF-IP-ARG-08；**BR-IP-ARG-010**（同日同 definition 跳过）

---

## 1. 问题描述

滚费 Job 双跑同日同 definition 重复 Charge

## 2. 发生场景

两 Job 实例或「Job + 手工触发」对同一 `(encounterId, chargeDefinitionId, billDate)` 几乎同时跑；均通过 `existsAutoRollChargeItemForBillDate` 后各写一条。

## 3. 根因分析

读探测 + 插入，无唯一索引/条件 INSERT。

## 4. 业务影响

同日双条 `PENDING_SETTLEMENT` AUTO_ROLL Charge；出院结算多收。

## 5. 修改方案

1. **DB 唯一约束**（核心）：

```sql
-- 示例：按项目实际列名调整
CREATE UNIQUE INDEX uk_auto_roll_charge
ON adm_charge_item (encounter_id, charge_definition_id, bill_date, generate_source)
WHERE generate_source = 'AUTO_ROLL' AND is_deleted = false;
```

2. **插入 catch duplicate** → 计 skipped，不 fail 整 Job。
3. Job 层 **分布式锁**：`(tenantId, billDate)` 单飞（Redis/DB advisory lock）。
4. `ChargeItemDomainService#priceByAutoRoll` 内可选 `@Version` 冲突重试。


## 6. 涉及代码

- `NurseStationAutoRollEncounterProcessor#processEncounter`
- `ChargeItemQueryService#existsAutoRollChargeItemForBillDate`
- `ChargeItemDomainService#priceByAutoRoll`
- DB migration

## 7. 验收用例

- 双线程同 encounter+billDate+definition 滚费 → 仅 1 条 Charge。
- Job 重跑整表 → skipped 计数正确，无 fail。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
