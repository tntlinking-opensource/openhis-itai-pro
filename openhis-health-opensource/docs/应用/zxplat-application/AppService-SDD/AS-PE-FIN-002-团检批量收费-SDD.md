# 团检批量收费 · 应用服务详细设计（SDD）

> 编码：AS-PE-FIN-002-SDD · 版本：v1.0 | 日期：2026-07-19  
> 来源契约：`AS-PE-FIN-002-团检批量收费.md` v1.0

---

## 1. 实现边界

| 项 | 内容 |
|----|------|
| 模块 | `health-application/.../physicalexam/financial/groupcharge/` |
| 类名 | `PhysicalExamGroupChargeAppService` |
| HTTP | `POST /api/app/physicalExamGroupCharge/{methodName}` |
| 支付复用 | `ChargeItemDomainService` + `PaymentReconciliationDomainService`（对齐 OP-FIN-002） |
| 建单 | `PeisRegistrationOrchestrator#registerGroupAfterPaid` |
| 名册 | `PeisGroupBookingPort`（booking bridge） |

### 1.1 预结算形态（锁定）

**按人多草稿**：勾选 N 人 → 每人独立 Encounter 建单后生成 ChargeItem → 每人一笔支付草稿 → 批量确认循环 settle；任一失败记录行错误，已成功行保留（返回 `successCount`/`failures[]`）。

> 说明：与「一单多就诊」相比更易复用门诊 settle 命令且事务边界清晰。

---

## 2. 代码结构

```text
physicalexam/financial/groupcharge/
  service/PhysicalExamGroupChargeAppService.java
  request/… response/… dto/…
  mapper/PhysicalExamGroupChargeMapper.java
  support/GroupChargeRosterLoader.java
```

### 2.1 方法映射

| IF | methodName |
|----|------------|
| 01 | `loadPageInit` |
| 02 | `pageUnpaidRoster` |
| 03 | `pagePaidRoster` |
| 04 | `preSettleBatch` |
| 05 | `confirmBatchSettlement` |
| 06 | `refreshRosterChargeStatus` |

---

## 3. 编排要点（05）

1. Gate：rosterIds 非空；Port 加载名册；patientId 必填。  
2. 年度唯一：学生档案命中时查同年有效 PE Encounter。  
3. 对每人：`registerGroupAfterPaid` → 生成 PLANNED ChargeItem（套餐子项定价）→ `preSettle` → `settlePayment`。  
4. Port：`markRosterPaid(rosterId, encounterId)`。  
5. Asm：汇总成功/失败。

---

## 4. 前端

| 项 | 约定 |
|----|------|
| 入口 | `/peis/group-check` 内「收费」Tab，或 `/peis/group-charge` |
| 禁止 | 与 `/peis/charge` 个检双页签混队列 |

---

## 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-07-19 | 首版；按人多草稿锁定 |
