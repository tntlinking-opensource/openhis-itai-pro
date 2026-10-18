# GAP-EXE-002 住院同一 planTime 并发双执行

> **问题类型**：并发双写与条件更新
> **严重度**：中危
> **影响范围**：住院
> **优先级**：P1
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-001](../01-并发双写与条件更新/SYS-IMP-001-护士站执行与发药并发防超发.md)
> **关联 IF/BR**：IF-IP-EXE-02；BR-IP-EXE-014/015；EX-IP-EXE-301

---

## 1. 问题描述

住院同一 planTime 并发双执行

## 2. 发生场景

同一 `(requestId, requestTable, planTime)` 双窗口同时点「执行」；各建 Procedure + Dispense + Charge。

## 3. 根因分析

`assertWriteSlotExecutable` 依赖 `prepareWriteContext` **预读** Procedure 净态；`cln_procedure` 无 `(request_id, request_table, occurrence_time)` 占用唯一约束；`skipDuplicateRequestAnchorCheck=true` 仅允许多 planTime，不防同 planTime。

## 4. 业务影响

同计划点双费、双发药单。

## 5. 修改方案

1. **Flyway 部分唯一索引**（PostgreSQL，实施前须存量清洗 + 与 CANCEL 对冲规则联调）：

```sql
CREATE UNIQUE INDEX uk_cln_procedure_request_occurrence_terminal
ON cln_procedure (request_id, request_table, occurrence_time)
WHERE is_deleted = false
  AND status_enum IN ('COMPLETED', 'STOPPED');
```

2. **域层**：`ProcedureDomainService#createProcedureRecord` 在住院执行路径增加 occurrence 占用校验：同锚点已有未对冲 `COMPLETED/STOPPED` → `EX-IP-EXE-301`。
3. **App 层（可选）**：写循环内对单槽位 **再查一次** Procedure 历史，缩小 TOCTOU 窗口。
4. **前端（辅助）**：提交 disable + 可选 `Idempotency-Key`。


## 6. 涉及代码

- `zxplat-domain/.../db/migration/health/Vxx__cln_procedure_occurrence_unique.sql`
- `zxplat-domain/.../clinical/procedure/service/ProcedureDomainService.java`
- `health-application/.../orderexecute/service/NurseStationOrderExecuteAppService.java`

## 7. 验收用例

- 同 planTime 并发 2 请求：仅 1 成功；另 1 为 EX-IP-EXE-301 或唯一索引冲突转业务码。
- 执行 → 取消执行 → 再执行：净态 pending，可再建 Procedure，不触发误伤。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | V100 唯一索引 + `occurrence_slot_active` + 域层占用校验；状态 → 已实施 |
