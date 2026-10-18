# SYS-IMP-010 批处理、滚费与日结报表一致性

> **问题类型**：批处理与报表一致性 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`NurseStationInpatientBillingAppService`、`NurseStationAutoRollEncounterProcessor`、`ChargeItemQueryService#existsAutoRollChargeItemForBillDate`、`OutpatientDailySettlementReportDatasetService`、日结 AS 标注「待成码」）。
>
> **关联 AS**：`AS-IP-NUR-006`（住院滚费/划价）、IF-IP-ARG-08；门诊/住院日结 AS+SDD（C 类报表）
>
> **关联**：`SYS-IMP-002` GAP-FIN-009（日结 payment 挂载）；`SYS-IMP-009` GAP-FLW-002（ON_HOLD 滚费，设计允许）
>
> **状态**：待实施

---

## 1. 背景与结论摘要

ZXPlat 存在三类 **「非交互式写入」**，与 IMP-001～002 的按钮并发不同族，但同样会造成 **重复计费、报表与交易不一致**：

| 类型 | 入口 | 幂等手段 | 风险 |
| --- | --- | --- | --- |
| **住院自动滚费 Job** | `executeInpatientAutoRollJob` | `existsAutoRollChargeItemForBillDate` 读探测 | 双 Job / 手工补跑 TOCTOU |
| **护士手工划价** | `NurseStationInpatientBillingAppService` | 业务规则各异 | 与滚费同日同 definition 叠加 |
| **日结 / C 类报表** | `OutpatientDailySettlementReportDatasetService` | 读 `Payment` SUCCESS 快照 | 日结窗口内仍改流水；报表≠交账表 |

**结论**：

- 滚费 **有** BR-IP-ARG-010 同日同 definition 探测，但 **无 DB 唯一约束**，与 IMP 家族同族 TOCTOU。
- 报表数据集 **直接扫 billDate 范围内 SUCCESS Payment**，**不**感知 `PaymentReconciliation` 日结交账态（GAP-FIN-009 仅部分描述）。
- 日结 AS **待成码** — 属已知重大缺口，本文件固化验收标准。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-BAT-001 | 滚费 Job 双跑同日同 definition 重复 Charge | 中危 | 住 | P1 | 待实施 |
| GAP-BAT-002 | 滚费 Job 与护士划价同日重复 | 中危 | 住 | P1 | 待实施 |
| GAP-BAT-003 | 跨零点 billDate 与 Job 调度竞态 | 低危～中危 | 住 | P2 | 待实施 |
| GAP-BAT-004 | 日结未成码：交账后仍可改挂载 payment | 中危 | 门+住 | P1 | 待实施 |
| GAP-BAT-005 | C 类报表与交账表口径漂移 | 中危 | 门诊 | P2 | 待实施 |
| GAP-BAT-006 | 滚费定价取 live 价表非 Charge 快照 | 低危～中危 | 住 | P2 | 见 SYS-IMP-012 |

---

## 3. 分项：问题与修改方案

### GAP-BAT-001 滚费 Job 幂等 TOCTOU

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（重复床费/护理费） |
| **关联 IF/BR** | IF-IP-ARG-08；**BR-IP-ARG-010**（同日同 definition 跳过） |
| **现象** | 两 Job 实例或「Job + 手工触发」对同一 `(encounterId, chargeDefinitionId, billDate)` 几乎同时跑；均通过 `existsAutoRollChargeItemForBillDate` 后各写一条。 |
| **根因** | 读探测 + 插入，无唯一索引/条件 INSERT。 |
| **业务影响** | 同日双条 `PENDING_SETTLEMENT` AUTO_ROLL Charge；出院结算多收。 |

**修改方案（推荐组合）**

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

**涉及代码**

- `NurseStationAutoRollEncounterProcessor#processEncounter`
- `ChargeItemQueryService#existsAutoRollChargeItemForBillDate`
- `ChargeItemDomainService#priceByAutoRoll`
- DB migration

**验收用例**

- 双线程同 encounter+billDate+definition 滚费 → 仅 1 条 Charge。
- Job 重跑整表 → skipped 计数正确，无 fail。

**工作量**：2～3 人天

---

### GAP-BAT-002 滚费与护士划价重复

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-NUR-005；BR-IP-ARG-010 |
| **现象** | 护士手工划价与滚费规则引用同一 `chargeDefinitionId`，探测键不一致时双条。 |
| **修改方案** | 统一幂等键：手工划价若属「床位/护理日费」类，写入前同样查 `existsAutoRollChargeItemForBillDate` 或共享唯一索引（扩展 generate_source 维度）。 |

**涉及代码**

- `NurseStationInpatientBillingWriteAssembler`

**工作量**：1～2 人天

---

### GAP-BAT-003 跨零点 billDate 竞态

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **现象** | Job 在 23:59 与 00:05 各跑一次，`billDate` 边界与 `BR-IP-ARG-013`（规则创建日）组合可能漏滚/双滚。 |
| **修改方案** | Job 参数显式 `billDate`（非 always today）；调度单实例；日志 audit `(jobRunId, billDate)`。 |

**工作量**：1 人天

---

### GAP-BAT-004 日结交账后仍可改流水

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（审计/稽核） |
| **关联 IF/BR** | 日结 AS（待成码）；**SYS-IMP-002 GAP-FIN-009** |
| **现象** | 收费员日结 SUCCESS 后，若实现未锁 payment，仍可退费/反结/改挂载。 |
| **修改方案** | 日结成码时：`UPDATE adm_payment_reconciliation SET daily_close_id=? WHERE … AND daily_close_id IS NULL`；已交账 payment **拒绝**退费 unless 主管撤销日结。 |

**状态**：待实施（依赖日结模块成码）

**工作量**：3～5 人天（含 UI）

---

### GAP-BAT-005 报表 vs 交易事实

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | `OutpatientDailySettlementReportDatasetService#build` 按 `findPaymentByBillDateRange` 聚合 SUCCESS，**不**过滤 `daily_close_id`、不 exclude 后续反结净额（取决于 query 是否 net）。 |
| **根因** | 报表为 **实时扫库** 非 **交账快照**。 |
| **业务影响** | 打印 C 类报表与出纳交账表不一致；监管检查风险。 |

**修改方案**

1. **短期**：报表参数 `settlementBatchId` / `dailyCloseId`，只读已交账批次；
2. **长期**：日结生成 **不可变 snapshot 表**（`fin_daily_close_line`），报表只读 snapshot；
3. 文档标注：在 GAP-BAT-004 完成前，C 类报表为 **参考值非法定交账**。

**涉及代码**

- `OutpatientDailySettlementReportDatasetService.java`
- 住院日结对数据集（对称）

**验收用例**

- 日结后对新 SUCCESS payment 退费 → 交账表不变；报表 batch 模式不变。

**工作量**：2～4 人天

---

### GAP-BAT-006 滚费取价时点

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **修改方案** | 见 **SYS-IMP-012 GAP-CLN-PRICE-001**；滚费 `resolveAutoRollChargeLine` 应写入 definition 版本/价快照字段。 |

---

## 4. 边界问题

| 编号 | 主题 | 说明 | 建议 |
| --- | --- | --- | --- |
| BND-BAT-001 | Job 失败重试 | 部分 encounter fail 不 rollback 全局 | 按 encounter 事务（现状）+ 幂等 |
| BND-BAT-002 | 手工补跑 billDate | 运维补历史床费 | 需审计 role + 幂等 |
| BND-BAT-003 | 报表实时 vs 交账 | 未日结前报表可波动 | UI 标注数据时点 |

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-BAT-001 + 002 | 滚费 DB 唯一 + 划价对齐 | 3～4 |
| 2 | GAP-BAT-004 | 日结交账锁（依赖成码） | 3～5 |
| 3 | GAP-BAT-005 | 报表读 snapshot | 2～4 |
| 4 | GAP-BAT-003 | Job 调度规范 | 1 |

---

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅靠 Job 日志人工对账 | 无法防双跑 |
| 报表继续全表扫 SUCCESS | 与日结语义冲突 |
| 滚费失败 silent skip 无告警 | 漏费难发现 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 双 Job 同 billDate | 1 Charge/definition/日 |
| T2 | Job 后立即重跑 | 全 skipped |
| T3 | ON_HOLD encounter 滚费 | **允许**（FLW-002 设计如此；清床收口） |
| T4 | 日结后改 payment | 拒绝 |
| T5 | 报表 batchId 模式 | 与交账表一致 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：批处理/滚费/日结报表一致性审查 |
