# SYS-IMP-004 入出转、分床与床位运营并发边界

> **问题类型**：并发双写与条件更新 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`NurseStationATDManagerAppService`、`LocationDomainService`、`InpatientRegistrationAppService`、`EncounterDomainService`）。
>
> **关联 AS**：`AS-IP-NUR-002`（入出转管理）、`AS-IP-FIN-001`（住院登记）
>
> **关联**：`SYS-IMP-003` GAP-BKG-006（booking 床位预留与 health 占床未联动）
>
> **状态**：部分已实施（GAP-ATD-001～008 已于 2026-06-28 落地）

---

## 1. 背景与结论摘要

住院床位运营以 **`adm_location.operational_enum`（NOT_IN_USE / IN_USE）** 为床态真相。App 层在分床/换床前仍调用 **`assertTargetBedVacant` 读 Gate**（软预检）；**硬门禁**在 **`LocationDomainService#occupyBed`**：`PESSIMISTIC_WRITE` 行锁 + `assertVacantForOccupy()` + **`Location` `@Version`**。编排为 **先占床、再写就诊轨迹**（ASSIGN / TRANSFER）。

**结论**：

| 类型 | 是否已有硬兜底 | 说明 |
| --- | --- | --- |
| **同床双患（一床两人）** | **是**（2026-06-28） | `occupyBed` 悲观行锁 + 锁内 `assertVacantForOccupy` + `Location` `@Version`；ASSIGN/TRANSFER 先占床再写轨迹 |
| **同就诊双分床** | **中** | 就诊状态迁移有部分域校验；双窗仍可能重复写轨迹 |
| **PLANNED 双登记** | **是**（2026-06-28） | `registerInpatientEncounter` 悲观行锁 + 锁内 `PLANNED` 断言；失败 → **EX-IP-REG-003** |
| **出院/召回/清床** | **是**（2026-06-28） | ATD-05/06/07 就诊悲观行锁 + 锁内断言 + `Encounter` `@Version`；乐观锁/状态冲突 → **304/402/504** |

AS-IP-NUR-002 声明「**同床并发分床拒绝（EX-IP-ATD-201）**」——**GAP-ATD-001 + 002 已实施**（行锁 + 实体乐观锁）。

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门/住 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-ATD-001 | 分床/换床目标空床读 Gate TOCTOU | **高危** | 住 | P0 | **已实施**（2026-06-28） |
| GAP-ATD-002 | `Location.occupyBed` 领域未断言床态 + 无乐观锁 | **高危** | 住 | P0 | **已实施**（2026-06-28） |
| GAP-ATD-003 | 互换床（SWAP）双床同时 TOCTOU | 中危 | 住 | P1 | **已实施**（2026-06-28） |
| GAP-ATD-004 | PLANNED 住院登记/有申请登记双窗 | 中危 | 住 | P2 | **已实施**（2026-06-28） |
| GAP-ATD-005 | 出院/清床/召回并发 | 中危（已知） | 住 | P2 | **已实施**（2026-06-28） |
| GAP-ATD-006 | 转科/转病区 ON_HOLD 幂等窗口 | 低危～中危 | 住 | P3 | **已实施**（2026-06-28） |
| GAP-ATD-007 | booking 预留与分床竞态 | 中危 | 住 | P1 | **已实施**（2026-06-28） |
| GAP-ATD-008 | IF-IP-AUD-03 转科/出院退回未回滚 ON_HOLD | 中危 | 住 | P2 | **已实施**（2026-06-28） |

---

## 3. 分项：问题与修改方案

### GAP-ATD-001 分床/换床目标空床读 Gate TOCTOU

> **状态**：**已实施**（2026-06-28）· 详见 [GAP-ATD-001 问题单](../问题清单/01-并发双写与条件更新/GAP-ATD-001.md) §8 解决日志

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（一床两患） |
| **关联 IF** | IF-IP-NUR-ATD-02；**BR-IP-ATD-002** |
| **现象** | 两名护士对**同一空床**几乎同时为**不同就诊**点「分床」；均读到 `operational_enum = NOT_IN_USE`，均 `occupyBed` 成功。 |
| **根因** | `assertTargetBedVacant` 在 `NurseStationATDManagerAppService` **只读** Location；与 `occupyBed` 非原子可见。 |
| **现有兜底** | 第二笔若重新读到 IN_USE 应在 App Gate 失败——**仅当读发生在第一笔提交之后**；无 DB 条件更新则不可靠。 |
| **业务影响** | 一床两患、护理/计费/医保登记混乱；难自动发现。 |

**修改方案（推荐组合）**

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

2. **行锁（等价）**：`LocationRepository#findBedForUpdate(bedId)` → `FOR UPDATE`，再 `occupy()`。→ **已采用**：`findByIdAndFormEnumAndIsDeletedFalse` + `PESSIMISTIC_WRITE`。
3. **编排顺序保持**：先 **占床成功** 再 `assignInpatientBed`（或占床失败则整事务回滚，避免「有轨迹无床/有床无轨迹」）。→ **已调整** ASSIGN / TRANSFER 路径。
4. **前端**：分床提交锁定 + 可选幂等键。→ **未实施**。

**涉及代码**

- `health-application/.../ATDManager/service/NurseStationATDManagerAppService.java`
- `zxplat-domain/.../location/service/LocationDomainService.java`
- `zxplat-domain/.../location/repository/LocationRepository.java`
- ~~`zxplat-domain/.../db/migration/health/Vxx__location_operational_conditional.sql`~~（未采用原生 SQL）

**验收用例**

- 两线程：同一 `bedLocationId`、不同 `encounterId` 分床 → 仅 1 成功，另 1 为 EX-IP-ATD-201。
- 换床：旧床 release + 新床 occupy 同事务；任一步失败整单回滚。

**工作量**：3～4 人天（**已落地**）

---

### GAP-ATD-002 Location 领域未断言床态 + 无乐观锁

> **状态**：**已实施**（2026-06-28）· 详见 [GAP-ATD-002 问题单](../问题清单/01-并发双写与条件更新/GAP-ATD-002.md) §7 解决日志

| 维度 | 说明 |
| --- | --- |
| **严重度** | **高危**（GAP-ATD-001 根因之一） |
| **现象** | `LocationDomainService#occupyBed` 仅 `assertPatientAssignable()` + `occupy()`，**不**调用 `assertNotOccupiedBed`；`Location` 实体无 `@Version`。 |
| **修改方案** | ① `assertVacantForOccupy()`（已随 001）；② **仅 `Location`** 映射 `@Version`；③ ATD-02 映射 `OptimisticLockingFailureException` → `EX-IP-ATD-201`。 |
| **涉及代码** | `Location.java`、`NurseStationATDManagerAppService.java` |
| **工作量** | 1～2 人天（**已落地**） |

---

### GAP-ATD-003 互换床（SWAP）双床 TOCTOU

> **状态**：**已实施**（2026-06-28）· 详见 [GAP-ATD-003 问题单](../问题清单/01-并发双写与条件更新/GAP-ATD-003.md) §7 解决日志

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | 互换涉及两床 `release` + 两床 `occupy`；第三方同时对其中一床分床。 |
| **修改方案** | 双床按 **id 排序** `FOR UPDATE`；`swapBedsOperational` 锁内 release + 交叉 occupy **一次落库**；`releaseBed` 同步行锁 + `assertOccupiedForRelease`。 |
| **涉及代码** | `Location.java`、`LocationDomainService.java`、`NurseStationATDManagerAppService#executeSwapBeds` |
| **工作量** | 2 人天（**已落地**） |

---

### GAP-ATD-004 PLANNED 住院登记双窗

> **状态**：**已实施**（2026-06-28）· 详见 [GAP-ATD-004 问题单](../问题清单/01-并发双写与条件更新/GAP-ATD-004.md) §7 解决日志

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | `InpatientRegistrationAppService#submitInpatientRegistration`；**AS-IP-FIN-001 §7** |
| **现象** | 两窗口对同一 `encounterId`（PLANNED 有申请）同时登记；均读到 PLANNED，均 `registerInpatientEncounter`。 |
| **修改方案** | `registerInpatientEncounter` 按就诊 id 排序 `PESSIMISTIC_WRITE`；锁内 `PLANNED→REGISTERED`；`ENCOUNTER_INVALID_STATUS` → **EX-IP-REG-003**。 |
| **涉及代码** | `EncounterRepository`、`EncounterDomainService`、`InpatientRegistrationAppService` |
| **工作量** | 2 人天（**已落地**） |

---

### GAP-ATD-005 出院/清床/召回并发

> **状态**：**已实施**（2026-06-28，阶段 A + B）· 详见 [GAP-ATD-005 问题单](../问题清单/01-并发双写与条件更新/GAP-ATD-005.md) §7

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（文档已知） |
| **关联** | AS-IP-NUR-002 ATD-05/06/07；反结见 SYS-IMP-002 |
| **现象** | 双窗同时办理出院、清床或召回；读 Gate 后无锁写就诊态。 |
| **修改方案** | **阶段 A**：`executeInpatientNurseDischarge` / `clearDischargedBed` / `recallInpatientDischarge` 悲观行锁 + 锁内断言；`ENCOUNTER_INVALID_STATUS` → EX-IP-ATD-304/401/501。**阶段 B**：`Encounter` `@Version`；`completeInpatientFinancialSettlement` / `revertInpatientFinancialSettlement` 行锁；`OptimisticLockingFailureException` → **304/402/504**（ATD）、**EX-IP-STL-109**（全结）、**EX-IP-REG-003**（登记）。 |
| **涉及代码** | `Encounter.java`、`EncounterDomainService`、`NurseStationATDManagerAppService`、`InpatientSettleAppService`、`InpatientRegistrationAppService` |
| **工作量** | 2～3 人天（**已落地**） |

---

### GAP-ATD-006 转科/转病区 ON_HOLD

> **状态**：**已实施**（2026-06-28）· 详见 [GAP-ATD-006 问题单](../问题清单/01-并发双写与条件更新/GAP-ATD-006.md) §7

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **现象** | 校对 App 对 `ON_HOLD` 粗粒度跳过；领域转科/出院写无行锁。 |
| **修改方案** | `initiateDepartmentTransfer` / `dischargeInpatientEncounter` / `confirmDepartmentTransfer` 行锁 + 锁内断言；校对编排里程碑幂等 + 异类 `VERIFIED` Pre 门禁；**EX-IP-AUD-203**。 |
| **涉及代码** | `EncounterDomainService`、`ServiceRequestQueryService`、`NurseStationOrderAuditAppService` |
| **工作量** | 1.5～2 人天（**已落地**） |

---

### GAP-ATD-007 booking 预留与分床竞态

> **状态**：**已实施**（2026-06-28）· 详见 [GAP-ATD-007](../问题清单/01-并发双写与条件更新/GAP-ATD-007.md) §7

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联** | GAP-BKG-003/006 |
| **修改方案** | `RESERVED` 三态 + `reserveBed`/`releaseReservedBed` 行锁；分床 `resolveTargetBedAssignGate` + **EX-IP-ATD-207**；消费预约 Extension |
| **工作量** | 4～5 人天（**已落地**） |

---

### GAP-ATD-008 IF-IP-AUD-03 转科/出院退回未回滚 ON_HOLD

> **状态**：**已实施**（2026-06-28）· 详见 [GAP-ATD-008](../问题清单/01-并发双写与条件更新/GAP-ATD-008.md) §7

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **现象** | 校对正向编排 `IN_PROGRESS→ON_HOLD` 后，退回/签退/停嘱无对称逆向；待转科/待出院脏读。 |
| **修改方案** | `revertTransferOrDischargeHold` + `InpatientTransferDischargeHoldOrchestrationsupport；里程碑对齐 + 无异类 `VERIFIED` 残留；挂载 AUD-03 / DOR-16/20。 |
| **涉及代码** | `Encounter`、`EncounterDomainService`、`ServiceRequestQueryService`、`NurseStationOrderAuditAppService`、`InpatientDoctorStationAdviceAppService` |
| **工作量** | 1.5～2 人天（**已落地**） |

---

## 4. 边界问题（非并发）

| 编号 | 主题 | 说明 |
| --- | --- | --- |
| BND-ATD-001 | 分床与入院体征 | `start_time` 缺失时跳过体征（BR-IP-ATD-020）；非并发但影响质控 |
| BND-ATD-002 | 出院反结前置 | 召回/反结须在收银台完成，非 ATD 同事务（AS v2.8+） |
| BND-ATD-003 | 空床板缓存 | 前端床态与后端 `operational_enum` 刷新延迟 |

---

## 5. 推荐实施顺序

| 顺序 | ID | 内容 | 人天 | 状态 |
| --- | --- | --- | --- | --- |
| 1 | GAP-ATD-001 + 002 | Location 行锁占床 + 断言 + `@Version` | 3～4 | **已实施** |
| 2 | GAP-ATD-007 | booking 与 Location 联动 | 4～5 | **已实施** |
| 3 | GAP-ATD-003 | SWAP 双床锁 + 单次落库 | 2 | **已实施** |
| 4 | GAP-ATD-004 | PLANNED 登记行锁 | 2 | **已实施** |
| 5 | GAP-ATD-005 | ATD-05/06/07 就诊行锁 + `@Version` | 2～3 | **已实施** |
| 6 | GAP-ATD-006 | 转科 ON_HOLD 行锁 + 校对编排 | 1.5～2 | **已实施** |
| 7 | GAP-ATD-008 | 转科/出院逆向 ON_HOLD 回滚 | 1.5～2 | **已实施** |

## 6. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| **仅**依赖 `assertTargetBedVacant` 读 Gate 作硬兜底 | 挡不住同床双分（**仍须** `occupyBed` 行锁） |
| 仅前端床板 disable | 挡不住双开与接口直调 |
| 去掉 `releaseBed` 与占床同事务 | 换床/leak 空床态 |
| booking 与 health 各写床态不协同 | 见 GAP-ATD-007 |

---

## 7. 回归测试矩阵

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 同一空床双 encounter 分床 | 1 成功，1 EX-IP-ATD-201 |
| T2 | 换床：新床已被占 | 整单回滚，原床仍 IN_USE |
| T3 | SWAP 两床 + 第三方占其中一床 | 明确失败 |
| T4 | 同 PLANNED encounter 双登记 | 1 成功，1 EX-IP-REG-003 |
| T5 | 分床后 booking 同床预留 | 冲突（联动 003 后） |
| T6 | 出院 + 召回并发 | 后者拒绝或幂等 |
| T7 | 校对转科/出院后签退/退回 | 就诊 `ON_HOLD→IN_PROGRESS`，待转科/待出院清除 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：入出转分床与床位运营并发/边界审查 |
| v1.1 | 2026-06-28 | GAP-ATD-001 已实施：行锁硬门禁 + ASSIGN/TRANSFER 编排调整 |
| v1.2 | 2026-06-28 | GAP-ATD-002 已实施：`Location` `@Version` + 乐观锁异常映射 |
| v1.3 | 2026-06-28 | GAP-ATD-003 已实施：`swapBedsOperational` + `releaseBed` 行锁 |
| v1.4 | 2026-06-28 | GAP-ATD-004 已实施：`registerInpatientEncounter` 就诊行锁 + EX-IP-REG-003 映射 |
| v1.5 | 2026-06-28 | GAP-ATD-005 阶段 A：ATD-05/06/07 就诊行锁 + EX-IP-ATD 异常映射 |
| v1.6 | 2026-06-28 | GAP-ATD-005 阶段 B：`Encounter` `@Version` + 全结行锁 + 乐观锁映射 |
| v1.7 | 2026-06-28 | GAP-ATD-006：转科/出院 ON_HOLD 行锁 + 校对编排 + 异类已校对门禁 |
| v1.8 | 2026-06-28 | GAP-ATD-007：booking 预留三态 + 分床联动 + EX-IP-ATD-207 |
| v1.9 | 2026-06-28 | GAP-ATD-008：转科/出院逆向 ON_HOLD 回滚 + AUD-03/DOR-16/20 挂载 |
