# SYS-IMP-005 进销存单据与汇总发药并发边界

> **问题类型**：并发双写与条件更新 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`InventoryWarehouseInboundReceiptAppService`、`InventoryWarehouseStockOutAppService`、`*StocktakeAppService`、`NurseStationDispenseSummaryAppService`、`InpatientPharmacySummaryDispenseAppService`、`SupplyRequestDomainService`）。
>
> **关联 AS**：`AS-INV-WHS-001～007`、`AS-INV-PHA-001～006`、`AS-IP-NUR-010`、`AS-IP-PHA-002`
>
> **关联**：`SYS-IMP-001`（核发硬扣减、`MedicationDispense` 无 `@Version` GAP-EXE-009；汇总发药为待发→核发中间态）
>
> **状态**：待实施

---

## 1. 背景与结论摘要

进销存链路采用 **`SupplyRequest`（单据头/行）→ `SupplyDelivery`（完成态发放）→ `InventoryItem` 增减** 模型。与护士站核发不同，此处 **同意/完成即改账面库存**，无单独「核发 Gate」二次拦截。

| 层级 | 并发防护 | 说明 |
| --- | --- | --- |
| **SupplyRequest** | 实体已 `@Version` | `agreeRequest` 可抛 `OptimisticLockingFailureException` → EX-WHS-*-105 |
| **同意前读 Gate** | `hasAgreed` / `findRequestIdsHavingCompletedDelivery` | **读时**防重复同意；与写之间存在 TOCTOU 窗口 |
| **InventoryItem 扣增** | `@Version` + `decreaseQuantitiesMerged` | 物理扣负仍较硬（与 SYS-IMP-001 一致） |
| **汇总领药/核发** | 读 Gate（PREPARATION + 无 summaryNo） | `MedicationDispense` **无 `@Version`**；双汇总/双核发为中危 |

**结论**：

- **重复同意入账（双 Delivery）**：App 层有多道读 Gate + Supply 乐观锁 + 完成态 Delivery 批量 Gate，**相对成熟**，但仍非条件 UPDATE 硬约束。
- **汇总领药双写**：`summarizeMedicationDispenses` 无源态条件更新，与 GAP-EXE-002 同族。
- **盘点/损益同意**：一次同意改多行库存，并发编辑单据与同意竞态影响账实。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-INV-001 | 进货/出库「同意」读 Gate TOCTOU | 中危 | 药库+药房 | P1 | 待实施 |
| GAP-INV-002 | 盘点/损益同意与单据编辑并发 | 中危 | 药库+药房 | P2 | 待实施 |
| GAP-INV-003 | 汇总领药双写（同 dispenseId） | 中危 | 住 | P1 | 已实施 |
| GAP-INV-004 | 汇总核发双提交（EX-IPS-201） | 中危 | 住 | P1 | 已实施 |
| GAP-INV-005 | `MedicationDispense` 汇总/核发无乐观锁 | 低危～中危 | 住 | P2 | 见 SYS-IMP-001 |
| GAP-INV-006 | 订货/进货/出库单据编辑 EX-*-105 未统一重试语义 | 低危 | 药库 | P3 | 待实施 |
| GAP-INV-007 | 医保 3505 盘点上报与库存同意顺序 | 低危～中危 | 药库 | P3 | 见 SYS-IMP-006 |

---

## 3. 分项：问题与修改方案

### GAP-INV-001 进货/出库「同意」读 Gate TOCTOU

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（重复入账） |
| **关联** | `InventoryWarehouseInboundReceiptAppService#agreeInboundReceipt`；出库/药房入库对称路径 |
| **现象** | 两窗口对同一 `busNo` 几乎同时「同意」；均读到 `hasAgreed=false`、无完成态 Delivery，均 `agreeRequest` + `createSupplyDelivery` + 加库存。 |
| **根因** | ① 防重在事务开头只读；② `agreeRequest` 虽用 `@Version`，两事务可能基于同一 version 均通过读 Gate 后顺序提交（第二笔乐观锁失败**若未捕获**仍可能部分编排）；③ 无 `UPDATE wkf_supply_request SET status=AGREE WHERE bus_no=? AND status=PENDING`。 |
| **现有兜底** | `OptimisticLockingFailureException` → WHS_IN_105；`findRequestIdsHavingCompletedDelivery` 二次 Gate；`InventoryItem` 层不防重复**加**库存。 |
| **业务影响** |  duplicate 入库、批号价重复、账实不符。 |

**修改方案（推荐）**

1. **条件更新 agree**：`SupplyRequestRepository#agreeIfPending(busNo, expectedStatus)`，影响行数≠明细行数 → WHS_IN_104。
2. **Delivery 唯一**：`wkf_supply_delivery (supply_request_id)` 完成态部分唯一索引（`status=COMPLETED`）。
3. **保持现有** `OptimisticLockingFailureException` 映射与 `flushPendingChanges` 回读。
4. **对称改造**：药库出库、药房入库、退库、损益等 `agree*` 路径共用同一 Domain 方法。

**涉及代码**

- `health-application/.../warehouse/inboundreceipt/service/InventoryWarehouseInboundReceiptAppService.java`
- `health-application/.../warehouse/stockout/service/InventoryWarehouseStockOutAppService.java`
- `zxplat-domain/.../supplyrequest/service/SupplyRequestDomainService.java`
- `zxplat-domain/.../supplyrequest/repository/SupplyRequestRepository.java`

**验收用例**

- 两线程同一 busNo 同意：仅 1 次库存增加，另 1 WHS_IN_104/105。
- 同意后重复点同意：WHS_IN_104。

**工作量**：3～4 人天（含出库/药房对称）

---

### GAP-INV-002 盘点/损益同意与编辑并发

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | `InventoryWarehouseStocktakeAppService#agreeStocktake`；药房盘点对称 |
| **现象** | 线程 A 编辑盘点单行；线程 B 同时同意；或双同意。 |
| **修改方案** | 与 GAP-INV-001 相同条件 agree；编辑路径校验 `status=PENDING_APPROVAL` 条件更新；盘点同意前对涉及 `InventoryItem` 行 `FOR UPDATE`（防同意与核发/发药交叉）。 |
| **工作量** | 2～3 人天 |

---

### GAP-INV-003 汇总领药双写（同 dispenseId）

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF** | IF-IP-SUM-04；**AS-IP-NUR-010**（EX-IP-SUM-201） |
| **现象** | 两护士对**同一批 dispenseId** 同时「汇总保存」；均读到 `PREPARATION` 且无 `summaryNo`，各生成 `summaryNo` + SupplyRequest。 |
| **根因** | `assertAllDispensesSummarizable` 读 Gate；`MedicationDispenseDomainService#summarizeMedicationDispenses` 直接 `applySummaryState` + `saveAll`，**无** `WHERE status=PREPARATION AND summary_no IS NULL`。 |
| **业务影响** | 双汇总单、药房重复核发风险、取消执行汇总重建复杂化（BR-IP-CXL-012）。 |

**修改方案**

1. **条件更新（核心）**：

```sql
UPDATE med_medication_dispense
SET summary_no = :summaryNo, status_enum = 'SUMMARIZED', version = version + 1
WHERE id IN (:ids)
  AND status_enum = 'PREPARATION'
  AND (summary_no IS NULL OR summary_no = '')
  AND is_deleted = false;
```

2. 影响行数 ≠ `dispenseIds.size()` → EX-IP-SUM-201。
3. **`MedicationDispense` 映射 `@Version`**（与 GAP-INV-005 / GAP-EXE-009 合并）。
4. 可选：`(id)` 上对 `SUMMARIZED` 态 `summary_no` 非空部分唯一（防重复汇总号绑定）。

**涉及代码**

- `NurseStationDispenseSummaryAppService#createMedicationSummary`
- `MedicationDispenseDomainService#summarizeMedicationDispenses`
- `MedicationDispenseRepository`（新增 `summarizeIfPreparation`）

**验收用例**

- 两线程同一 dispenseIds 汇总：1 个 summaryNo，另 1 EX-IP-SUM-201。

**工作量**：2～3 人天

---

### GAP-INV-004 汇总核发双提交（EX-IPS-201）

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | `InpatientPharmacySummaryDispenseAppService`；**AS-IP-PHA-002** |
| **现象** | 两窗口对同一 `summaryNo` 同时核发；均读到 Supply `APPROVAL` + Dispense `SUMMARIZED`。 |
| **修改方案** | ① Supply 行 `APPROVAL → AGREE` 条件更新；② Dispense `SUMMARIZED → COMPLETED` 条件更新；③ 扣库存仍走 `decreaseQuantitiesMerged`（最后防线）。 |
| **涉及代码** | `InpatientPharmacySummaryDispenseAppService`、核发 Domain 命令 |
| **工作量** | 2～3 人天 |

---

### GAP-INV-005 MedicationDispense 无乐观锁

| 维度 | 说明 |
| --- | --- |
| **修改方案** | 见 **SYS-IMP-001 GAP-EXE-009**；与本章 GAP-INV-003/004 合并实施。 |

---

### GAP-INV-006 单据编辑 EX-*-105 语义不统一

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危 |
| **现象** | 部分 AppService 捕获 `OptimisticLockingFailureException`，部分未捕获。 |
| **修改方案** | 统一 `SupplyWorkflowExceptionMapper`；禁止裸抛 500。 |

---

### GAP-INV-007 医保 3505 与盘点同意

| 维度 | 说明 |
| --- | --- |
| **修改方案** | 见 **SYS-IMP-006**；盘点同意后异步 3505 须幂等键。 |

---

## 4. 边界问题（非并发）

| 编号 | 主题 | 说明 |
| --- | --- | --- |
| BND-INV-001 | 批号价去重 | 进货同意按批号补价表；重复 agree 导致重复价行 |
| BND-INV-002 | 汇总合并规则 | 同药房+同单价分组合并（IF-IP-SUM-04）；与并发无关但影响核发 |
| BND-INV-003 | 取消汇总 | 须 Supply + Dispense 对称回退 |

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 |
| --- | --- | --- | --- |
| 1 | GAP-INV-003 + 005 | 汇总条件更新 + Dispense @Version | 2～3 |
| 2 | GAP-INV-004 | 汇总核发条件迁移 | 2～3 |
| 3 | GAP-INV-001 | 进货/出库 agree 条件更新 + Delivery 唯一 | 3～4 |
| 4 | GAP-INV-002 | 盘点/损益 | 2～3 |
| 5 | GAP-INV-006、007 | 体验/医保 | 1～2 |

---

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 仅依赖 `hasAgreed` 读 Gate | 挡不住双同意 |
| 去掉 `findRequestIdsHavingCompletedDelivery` | 会引入重复 Delivery |
| 汇总层不做条件更新、只靠核发扣库存 | 多发汇总单、运维成本高 |
| 在 QueryService 写 Supply/Delivery | 违反分层 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 同一 busNo 双同意进货 | 1 次加库存 |
| T2 | 同一 dispenseIds 双汇总 | 1 summaryNo |
| T3 | 同一 summaryNo 双核发 | 1 成功，1 EX-IPS-201 |
| T4 | 编辑盘点单 + 同时同意 | 1 成功，1 EX-WHS-ST-105 |
| T5 | 同意后重复 agree | WHS_IN_104 |
| T6 | 核发库存不足 | 不扣负，事务回滚 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.1 | 2026-06-28 | GAP-INV-003/004 条件更新已实施（含 GAP-STALE-006） |
| v1.0 | 2026-06-21 | 初稿：进销存单据与汇总发药并发/边界审查 |
