# GAP-ATD-001 分床/换床目标空床读 Gate TOCTOU

> **问题类型**：并发双写与条件更新
> **严重度**：**高危**（一床两患）
> **影响范围**：住
> **优先级**：P0
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-004](../01-并发双写与条件更新/SYS-IMP-004-入出转分床与床位运营并发边界.md)

---

## 1. 问题描述

分床/换床目标空床读 Gate TOCTOU

## 2. 发生场景

两名护士对**同一空床**几乎同时为**不同就诊**点「分床」；均读到 `operational_enum = NOT_IN_USE`，均 `occupyBed` 成功。

## 3. 根因分析

历史实现中 `assertTargetBedVacant` 在 `NurseStationATDManagerAppService` **只读** Location；与 `occupyBed` 非原子可见。实施后软 Gate 已替换为 `resolveTargetBedAssignGate`（含 [GAP-ATD-007](./GAP-ATD-007.md) 预留床 `RESERVED` 分支）；**并发硬兜底**仍在 `occupyBed` 行锁路径。

## 4. 业务影响

一床两患、护理/计费/医保登记混乱；难自动发现。

## 5. 修改方案

1. **条件更新（核心）**：

```sql
UPDATE adm_location
SET operational_enum = 'IN_USE', version = version + 1
WHERE id = :bedLocationId
  AND form_enum = 'BED'
  AND operational_enum = 'NOT_IN_USE'
  AND status_enum = 'ACTIVE'
  AND is_deleted = false;
-- 影响行数 = 0 → EX-IP-ATD-201
```

2. **行锁（等价）**：`LocationRepository#findBedForUpdate(bedId)` → `FOR UPDATE`，再 `occupy()`。
3. **编排顺序**（**已调整**）：先 **占床成功** 再 `assignInpatientBed`（占床失败则整事务回滚，避免「有轨迹无床/有床无轨迹」）。**ASSIGN**：`occupyBed` → `assignInpatientBed`；**TRANSFER**：`occupyBed`（新床）→ `confirmBedTransfer` → `releaseBed`（原床）。
4. **前端**：分床提交锁定 + 可选幂等键。


## 6. 涉及代码

- `health-application/.../ATDManager/service/NurseStationATDManagerAppService.java`
- `zxplat-domain/.../location/service/LocationDomainService.java`
- `zxplat-domain/.../location/repository/LocationRepository.java`
- `zxplat-domain/.../db/migration/health/Vxx__location_operational_conditional.sql`（若用原生 SQL）

## 7. 验收用例

- 两线程：同一 `bedLocationId`、不同 `encounterId` 分床 → 仅 1 成功，另 1 为 EX-IP-ATD-201。
- 换床：旧床 release + 新床 occupy 同事务；任一步失败整单回滚。

## 8. 解决日志

| 项 | 说明 |
| --- | --- |
| **实施日期** | 2026-06-28 |
| **采用方案** | **行锁（等价于条件 UPDATE）**；未新增 Flyway / 原生 SQL |
| **硬门禁** | `occupyBed` 悲观行锁 + 锁内 `assertVacantForOccupy`；`Location` `@Version` 叠加防丢失更新 |
| **多床死锁** | 批量 `occupyBed` / `releaseBed` 按 `locationId` 升序加锁；SWAP 见 [GAP-ATD-003](./GAP-ATD-003.md) `swapBedsOperational` |
| **编排调整** | **ASSIGN**：`occupyBed` → `assignInpatientBed`；**TRANSFER**：`occupyBed`（新床）→ `confirmBedTransfer` → `releaseBed`（原床） |
| **错误码** | 占床冲突 `LOCATION_RULE_CONFLICT` 在 ATD-02 映射为 **EX-IP-ATD-201** |
| **软 Gate** | `resolveTargetBedAssignGate`：`NOT_IN_USE` 直占；`RESERVED` 校验患者/入院日（007）；**不作**并发硬兜底 |
| **未实施** | 前端提交 disable + 幂等键 |
| **关联** | [GAP-ATD-002](./GAP-ATD-002.md)：`Location` `@Version` + 乐观锁异常映射；[GAP-ATD-007](./GAP-ATD-007.md) 预留床分床 |

**变更文件**

| 模块 | 文件 | 要点 |
| --- | --- | --- |
| zxplat-domain | `Location.java` | `assertVacantForOccupy()`；`@Version` |
| zxplat-domain | `LocationRepository.java` | `findByIdAndFormEnumAndIsDeletedFalse` + 悲观写锁 |
| zxplat-domain | `LocationDomainService.java` | `requireBedForUpdate`；占床路径锁内断言 |
| health-application | `NurseStationATDManagerAppService.java` | `resolveTargetBedAssignGate`；编排顺序；`LOCATION_RULE_CONFLICT` / 乐观锁冲突 → `EX-IP-ATD-201` |

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-06-28 | 行锁 + 编排调整实施；状态 → 已实施 |
| v1.2 | 2026-06-28 | 对齐 GAP-ATD-002 乐观锁落地 |
| v1.3 | 2026-06-28 | 文档漂移修正：软 Gate → `resolveTargetBedAssignGate`；关联 GAP-ATD-007 |
