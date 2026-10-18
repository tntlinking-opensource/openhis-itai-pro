# SYS-IMP-001 护士站执行与发药并发防超发

> **问题类型**：并发双写与条件更新 · [本类索引](./README.md) · [总索引](../README.md)
>
> **来源**：2026-06 代码审查（`NurseStationOrderExecuteAppService`、`NurseStationExecutionInventoryAssembler`、门诊/住院药房核发、门诊护士站处置）。
>
> **关联 AS**：`AS-IP-NUR-004`、`AS-IP-NUR-012`、`AS-OP-NUR-001`、`AS-OP-DOR-003`、`AS-IP-PHA-001`、`AS-OP-PHA-001`
>
> **状态**：**已实施**（2026-06-28：GAP-EXE-001～007、009 落地；008 待评估；010 文档化）
>
> **关联**：`SYS-IMP-002`（同一 planTime 双执行会导致重复待结算 ChargeItem，见 GAP-FIN-010）

---

## 1. 背景与结论摘要

ZXPlat 对药品/耗材「超发」采用 **多层软 Gate + 核发硬扣减** 策略：

| 阶段 | 行为 |
| --- | --- |
| 住院护士执行（药嘱） | 库存 Gate dry-run → 新建 `PREPARATION` Dispense + 待结算 Charge；**不扣** `wkf_inventory_item` |
| 门诊医生签发 | FIFO 分段新建 Dispense 草稿/待发；占用预减为**同就诊**口径 |
| 药房核发 | `assertSufficientForMedicationDispense` + `decreaseQuantitiesMerged`；`InventoryItem` 带 `@Version` |

**结论**：

- **物理超发（账面扣负）**：当前核发层基本能拦住，**不构成典型 P0 安全漏洞**。
- **业务超发（多单、多费、重复执行记录、待发堆积）**：并发与幂等缺口仍存在，**建议分阶段加固**。

现有兜底（实施加固时**不得削弱**）：

- `InventoryItemDomainService#decreaseQuantitiesMerged` + `assertDecreaseAllowed`
- 临时医嘱：`assertTemporaryTherapyQuantities` / `assertTemporaryTherapyQuantitiesForInpatient`
- 住院取消执行：`removeUndispensedDispense` 释放未发预占

---

## 2. 问题清单总览

| ID | 标题 | 严重度 | 门急诊 | 优先级 | 状态 |
| --- | --- | --- | --- | --- | --- |
| GAP-EXE-001 | 住院库存 Gate TOCTOU（全局预占读时不锁） | 中危 | 住院 | P1 | **已实施** |
| GAP-EXE-002 | 住院同一 planTime 并发双执行 | 中危 | 住院 | P1 | **已实施** |
| GAP-EXE-003 | 核发断言不含全局待发预占 | 中危 | 门+住 | P1 | **已实施** |
| GAP-EXE-004 | 住院绑定耗材执行无药嘱级 Gate、当场扣库 | 中危 | 住院 | P2 | **已实施** |
| GAP-EXE-005 | 门诊签发 FIFO 同就诊 TOCTOU | 中危 | 门诊 | P2 | **已实施** |
| GAP-EXE-006 | 门诊护士执行次数 netCount 非原子 | 低危～中危 | 门诊 | P2 | **已实施** |
| GAP-EXE-007 | 门诊耗材自动扣库并发（BR-NUR-008 静默 skip） | 中危（已知限制） | 门诊 | P2 | **已实施** |
| GAP-EXE-008 | 长期嘱写路径不校验 planTime ∈ 频次展开 | 低危～中危 | 住院 | P3 | 待评估 |
| GAP-EXE-009 | `MedicationDispense` 表有 version 列但实体未映射乐观锁 | 低危 | 门+住 | P3 | **已实施** |
| GAP-EXE-010 | 长期嘱无累计发放量上限（自然月 Gate） | 低危（设计） | 住院 | P3 | 已实施 |

---

## 3. 分项：问题与修改方案

### GAP-EXE-001 住院库存 Gate TOCTOU

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-EXE-02；BR-IP-EXE-014/016/022 |
| **现象** | 两名护士（或两请求）几乎同时对不同医嘱执行**同一药品、同一药房批次**；两请求 Gate dry-run 均读到「够用」，均新建 `PREPARATION` Dispense；预占之和超过物理库存。 |
| **根因** | `NurseStationExecutionInventoryAssembler#prepareMedicationFifoBatchPlan` 在事务内**只读**物理库存 + `listDispenseExeReserve`，无 `SELECT FOR UPDATE`；Gate 与写 Dispense 非原子可见。 |
| **现有兜底** | 核发时 `decreaseQuantitiesMerged` 拒绝扣负；第二条 Dispense 可能永久卡在待发。 |
| **业务影响** | 多发药单、多记待结算费；药房核发部分失败；运维需取消执行/作废。 |

**修改方案（推荐）**

1. **方案 A（优先，改动适中）**：Gate 前对本批涉及的 `(itemTable, itemId, locationId[, lotNumber])` 调用 `InventoryItemRepository#findByKeysForUpdate`（`SELECT … FOR UPDATE`，按 key 排序防死锁）。
2. 在**已锁定行**上重算：物理量 − `listDispenseExeReserve`（全局，不限就诊）− 本事务已插入 Dispense。
3. FIFO 分段通过后**立即**同循环调用 `generateDispenseFromExecution`（保持 Procedure → Dispense 同事务，BR-IP-EXE-022）。
4. **方案 B（中长期）**：独立预占表 `med_dispense_inventory_reserve`，Gate 通过 insert reserve；`listDispenseExeReserve` 合并 Dispense + reserve 汇总。

**涉及代码**

- `health-application/.../orderexecute/assembler/NurseStationExecutionInventoryAssembler.java`
- `zxplat-domain/.../inventoryitem/repository/InventoryItemRepository.java`（新增 FOR UPDATE 查询）
- `zxplat-domain/.../medicationdispense/service/MedicationDispenseQueryService.java`（若方案 B）

**验收用例**

- 两线程：库存 10 片，各执行需 6 片 → 至多 1 条 Dispense 成功，另一条 `EX-IP-EXE-302` 或核发失败。
- 同批多槽位仍按 working 库存逐行扣减，批内不超分。

**工作量**：3～5 人天（方案 A）

---

### GAP-EXE-002 住院同一 planTime 并发双执行

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-EXE-02；BR-IP-EXE-014/015；EX-IP-EXE-301 |
| **现象** | 同一 `(requestId, requestTable, planTime)` 双窗口同时点「执行」；各建 Procedure + Dispense + Charge。 |
| **根因** | `assertWriteSlotExecutable` 依赖 `prepareWriteContext` **预读** Procedure 净态；`cln_procedure` 无 `(request_id, request_table, occurrence_time)` 占用唯一约束；`skipDuplicateRequestAnchorCheck=true` 仅允许多 planTime，不防同 planTime。 |
| **现有兜底** | 无 DB 级硬约束；第二次应返回 EX-IP-EXE-301 仅当读到已提交 Procedure。 |
| **业务影响** | 同计划点双费、双发药单。 |

**修改方案（推荐，DB + 域双保险）**

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

**涉及代码**

- `zxplat-domain/.../db/migration/health/Vxx__cln_procedure_occurrence_unique.sql`
- `zxplat-domain/.../clinical/procedure/service/ProcedureDomainService.java`
- `health-application/.../orderexecute/service/NurseStationOrderExecuteAppService.java`

**验收用例**

- 同 planTime 并发 2 请求：仅 1 成功；另 1 为 EX-IP-EXE-301 或唯一索引冲突转业务码。
- 执行 → 取消执行 → 再执行：净态 pending，可再建 Procedure，不触发误伤。

**工作量**：2～3 人天

**注意**：须与 `AS-IP-NUR-012` 取消执行 CANCEL 对冲、`refund_id` 净态判定一并回归。

---

### GAP-EXE-003 核发断言不含全局待发预占

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（偏流程/运维） |
| **关联 IF** | IF-OPP-04/05；IF-IPP-04/05；IF-IP-EXE-02 Gate |
| **现象** | 多条 `PREPARATION` 待发单各自调用 `assertSufficientForMedicationDispense` 时，仅比对**物理库存**，未减其他待发 Dispense 预占；多条均可过断言，核发时部分失败。 |
| **根因** | `InventoryItemQueryService#assertSufficientForMedicationDispense` 只 `findAvailableInventories` 求和，未调用 `listDispenseExeReserve`。 |
| **现有兜底** | 最终 `decreaseQuantitiesMerged` 防扣负。 |
| **业务影响** | 待发堆积、核发失败体验差；与 GAP-EXE-001 叠加时更明显。 |

**修改方案**

1. 新增（或扩展）方法，例如：

```text
assertSufficientForMedicationDispenseWithReserve(
  itemTable, medicationId, locationId, lotNumber,
  requiredQty, unitCode, productDefinition,
  excludeDispenseId   // 当前行不计入预占
)
```

2. 计算：`可用 = 物理(批号) − 待发预占(同 location+med+**同批号**)`（排除 `excludeDispenseId`）。
   - **硬约束**：物理过滤了批号时，预占**必须**同批收窄；禁止「物理按批、预占按品」混算（中药 FIFO 拆多批时会假报不足）。
   - 批号为空时：物理与预占均按库房+药品全批汇总。
3. 替换调用点：门诊核发、住院核发、住院汇总核发。
4. 实现落点：`MedicationDispenseQueryService#sumDispenseExeReserveQuantityInSplitUnit(..., lotNumber)` +
   `MedicationDispenseInventoryAvailabilityGate` 传入断言行批号。

**涉及代码**

- `zxplat-domain/.../inventoryitem/service/InventoryItemQueryService.java`
- `zxplat-domain/.../medicationdispense/service/MedicationDispenseQueryService.java`（预占按批）
- `health-application/.../shared/pharmacy/MedicationDispenseInventoryAvailabilityGate.java`
- `health-application/.../outpatient/pharmacy/dispense/service/OutpatientPharmacyDispenseAppService.java`
- `health-application/.../inpatient/pharmacy/dispense/service/InpatientPharmacyDispenseAppService.java`
- `health-application/.../inpatient/pharmacy/summarydispense/service/InpatientPharmacySummaryDispenseAppService.java`

**验收用例**

- 物理 10 片，已有待发预占 8 片，新发 4 片 → 配药/核发断言失败，提示库存不足。
- 当前行 `excludeDispenseId` 不计入自身预占。
- **同药 FIFO 拆两批**：批 A 物理 80、待发 80；批 B 物理 40、待发 20；分别断言两行均通过（不得把 B 的 20 预占扣进 A 的 80）。

**工作量**：2～3 人天

---

### GAP-EXE-004 住院绑定耗材执行无药嘱级 Gate

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-IP-EXE-02 bindItems；BR-IP-EXE-019；文档明确绑定耗材**不参与**药嘱库存 Gate |
| **现象** | 执行勾选 `bindItems` 耗材时，路径为 `finalizeInpatientTemporaryPathDispense` + 批次末 `decreaseQuantitiesMerged`，无与药品同级的 FIFO dry-run / 全局预占。 |
| **根因** | 设计刻意排除 bind 于 `NurseStationExecutionInventoryAssembler`；扣库与建单同事务但无预占。 |
| **业务影响** | 并发执行同一绑定项可能双扣库存、双建 DeviceDispense。 |

**修改方案**

1. 执行前对 bind 耗材做 **dry-run FIFO**（复用 Device 库存 Query + 模板库位，口径对齐 `DeviceDispenseNurseWorkflowsupport）。
2. Gate 通过后同事务写发放单；扣库仍在批次末合并扣减。
3. 幂等：同 `(deviceReqId, procedureId)` 或执行锚点已存在非删除发放 → 拒绝（可选 DB 部分唯一索引）。

**涉及代码**

- `health-application/.../orderexecute/service/NurseStationOrderExecuteAppService.java`（`executeBindItems`）
- `health-module-support/.../orchestration/nursestation/DeviceDispenseNurseWorkflowSupport.java`

**验收用例**

- 绑定耗材并发双执行：至多一条扣库成功。
- 库存不足整批失败（与药嘱 Gate 整批回滚策略一致）。

**工作量**：3～4 人天

---

### GAP-EXE-005 门诊签发 FIFO 同就诊 TOCTOU

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危 |
| **关联 IF/BR** | IF-DOR-15；BR-ORD-021；BR-ORD-011 |
| **现象** | 双窗口对同一就诊批量签发；占用预减为**同就诊**口径，签发 FIFO 读时不锁；可能签出超量 Dispense。 |
| **根因** | `DoctorStationAdviceFifoAllocationAssembler` 与目录预减均为读时校验；无 request 级或库存行锁。 |
| **现有兜底** | 临时嘱药房 `assertTemporaryTherapyQuantities`；核发硬扣减。 |
| **业务影响** | 超量待发单、待收费行增多。 |

**修改方案**

1. 签发事务内对 FIFO 涉及库存行 `FOR UPDATE`（与 GAP-EXE-001 方案 A 同构）。
2. 或签发前对 `med_medication_request` / `wkf_device_request` 行 `FOR UPDATE`，保证同 request 不重写发放。
3. 与住院全局预占：`listDispenseExeReserve` 为全局，门诊签发占用仅同就诊——**跨就诊抢库存**仍靠 GAP-EXE-001/003 统一。

**涉及代码**

- `health-application/.../outpatient/doctorstation/.../DoctorStationAdviceFifoAllocationAssembler`（或等价装配器）
- `zxplat-domain/.../inventoryitem/repository/InventoryItemRepository.java`

**验收用例**

- 同就诊双签发同一药嘱：仅一套 Dispense 分段生效或第二请求显式冲突。

**工作量**：3～4 人天

---

### GAP-EXE-006 门诊护士执行次数 netCount 非原子

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危 |
| **关联 IF/BR** | IF-NUR-04；BR-NUR-005 |
| **现象** | `OutpatientNurseStationTreatmentAppService` 中 `netProcedureCount >= planned` 时 **`continue` 跳过**；两并发请求均读到 `net < planned`，各建 COMPLETED Procedure。 |
| **根因** | 读-改-写非原子；无 request 行锁或 DB 约束。 |
| **业务影响** | **药品**：Dispense 签发时已定量，多为 Procedure 超记；**诊疗**：执行次数可超 `execution_count` / `execute_num`。 |

**修改方案**

1. **推荐**：`ProcedureDomainService` 前增加 `assertPerformCountBelowPlanned`：对 `med_medication_request` / `wkf_service_request` **`SELECT FOR UPDATE`**，再数净 Procedure；≥ planned → 业务异常（**禁止静默 continue**）。
2. 批量执行时满额应 **抛错** 而非 skip，避免部分成功无感知。

**涉及代码**

- `health-application/.../outpatient/nursestation/treatment/service/OutpatientNurseStationTreatmentAppService.java`
- `zxplat-domain/.../clinical/procedure/service/ProcedureQueryService.java`（或 Request Repository）

**验收用例**

- `execute_num=1` 双点执行：第二次失败且提示明确。
- 取消执行对冲后净次数恢复，可再执行。

**工作量**：1～2 人天

---

### GAP-EXE-007 门诊耗材自动扣库并发（已知限制）

| 维度 | 说明 |
| --- | --- |
| **严重度** | 中危（**AS-OP-NUR-001 §7 已声明**「并发以后写者校验为准」） |
| **关联 IF/BR** | IF-NUR-04；BR-NUR-008；BR-NUR-014 |
| **现象** | `applyOutpatientNurseDeviceDispenses` 对 `validateDeviceDispensePreparable` 失败 **`continue` 静默跳过**；极端并发两请求均通过则可能双扣。 |
| **根因** | 无发放单状态乐观锁；静默 skip 掩盖冲突。 |

**修改方案**

1. `DeviceDispense` 实体映射 `@Version`（表若无列则 Flyway 增加）。
2. 状态迁移 `PREPARATION → PREPARED/COMPLETED` 使用乐观更新；失败 → `EX-NUR-207/208`，**整批失败**，提示刷新。
3. 用户提交的行若不可 preparable，**不得静默 skip**（与 BR-NUR-008「避免重复发放」一致化）。

**涉及代码**

- `zxplat-domain/.../workflow/devicedispense/entity/DeviceDispense.java`
- `health-module-support/.../DeviceDispenseNurseWorkflowSupport.java`

**验收用例**

- 同 `dispenseId` 双线程执行：至多一笔扣库；另一笔明确失败。
- 开关关（仅 PREPARED）：仍仅处理 PREPARATION 行。

**工作量**：2～3 人天

---

### GAP-EXE-008 长期嘱写路径不校验 planTime ∈ 频次展开

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危～中危（**BR-IP-EXE-023 允许补录/提前**为产品能力） |
| **关联 IF/BR** | IF-IP-EXE-02；BR-IP-EXE-023/024 |
| **现象** | 写 Gate 仅校验 occurrence 净态 + 不晚于停嘱；**不校验** planTime 是否在 `computeExecutionTimePoints` 展开集内；无 Procedure 的 occurrence 视为 PENDING。 |
| **根因** | 补录设计；列表展开与写 Gate 解耦。 |
| **业务影响** | 接口直调或非常规 planTime 可多执行 → 长期嘱多次 Dispense（每次单次剂量）。 |

**修改方案（可选，配置开关）**

1. 配置项 `inpatient.execute.strictPlanTime`（默认 `false` 保持补录）。
2. `true` 时：`assertWriteSlotExecutable` 增加 `frequencyQueryService.isValidOccurrence(...)`，不在展开集 → EX-IP-EXE-303。

**涉及代码**

- `NurseStationOrderExecuteAppService`
- `FrequencyQueryService` 封装

**验收用例**

- 开关 false：补录 21 日点仍成功。
- 开关 true：非展开 planTime 拒绝。

**工作量**：1～2 人天

---

### GAP-EXE-009 MedicationDispense 乐观锁未映射

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危 |
| **关联** | `AS-IP-PHA-001-SDD` §6.1 承诺「同一发放行并发配药/发药 乐观锁/版本」 |
| **现象** | 表 `med_medication_dispense.version` 存在，JPA 实体 `MedicationDispense` **未** `@Version` 映射。 |
| **根因** | 实现与 SDD 差距。 |
| **业务影响** | 同发放行并发配药/核发可能 last-write-wins；物理库存仍靠 `InventoryItem` 防超扣。 |

**修改方案**

1. `MedicationDispense` 增加 `@Version Integer version`。
2. `prepareMedicationDispense` / `dispenseMedication` 捕获 `OptimisticLockException` → 业务码「发放单已被他人处理，请刷新」。

**涉及代码**

- `zxplat-domain/.../medication/medicationdispense/entity/MedicationDispense.java`
- `MedicationDispenseDomainService.java`

**工作量**：1 人天

---

### GAP-EXE-010 长期嘱无累计发放量上限

| 维度 | 说明 |
| --- | --- |
| **严重度** | 低危（**设计取舍**，非缺陷） |
| **关联** | IF-IP-EXE-02；BR-IP-EXE-016；DM-MED-003 长期=单次用量 |
| **说明** | 长期药嘱每次执行新建单次剂量 Dispense；**无**临时嘱「待发合计=开立总量」类校验。 |
| **已实施** | **1 个自然月**硬 Gate：`LongTermMedicationMonthlyDispenseCapsupport；超上限 → **EX-IP-EXE-305**。详见 [GAP-EXE-010](../问题清单/01-并发双写与条件更新/GAP-EXE-010.md)。 |

**状态**：已实施

---

## 4. 推荐实施顺序

| 顺序 | ID | 内容 | 人天（粗估） |
| --- | --- | --- | --- |
| 1 | GAP-EXE-002 | Procedure 唯一索引 + 域校验 | 2～3 |
| 2 | GAP-EXE-003 | 核发 assert 含预占 | 2～3 |
| 3 | GAP-EXE-001 | Gate + FOR UPDATE | 3～5 |
| 4 | GAP-EXE-006 | 门诊执行次数 FOR UPDATE | 1～2 |
| 5 | GAP-EXE-004、007 | 耗材路径硬化 | 3～4 |
| 6 | GAP-EXE-005 | 门诊签发锁 | 3～4 |
| 7 | GAP-EXE-008、009 | 可选/补 SDD | 1～2 |

---

## 5. 不建议的做法

| 做法 | 原因 |
| --- | --- |
| 全局事务隔离级别 SERIALIZABLE | 性能差、易死锁 |
| 仅前端按钮 disable | 挡不住双开、接口直调 |
| 在 QueryService 跨聚合写 | 违反 AS-SDD 边界（Gate 编排在 AppService） |
| 去掉核发层硬扣减 | 会重新引入物理超发风险 |

---

## 6. 阶段四：体验与运维（低成本，可与 P1 并行）

| 项 | 做法 |
| --- | --- |
| 前端 | 执行/发药/核发提交态锁定、防抖 |
| API | 可选 `Idempotency-Key` + Redis 24h 去重 |
| 运维 | 待发 Dispense 超 N 天且库存不足标记，推药房待办 |

---

## 7. 回归测试矩阵（实施完成后）

| # | 场景 | 期望 |
| --- | --- | --- |
| T1 | 同 planTime 双请求 | 仅 1 成功 |
| T2 | 两线程抢 10 片各执行 6 片 | 至多 1 条 Dispense |
| T3 | 执行 → 取消 → 再执行 | 可再执行，无唯一索引误伤 |
| T4 | 临时嘱待发合计 | 仍 = 开立总量 |
| T5 | 门诊 execute_num=1 双点 | 第二次失败 |
| T6 | 绑定耗材并发双执行 | 至多 1 次扣库 |
| T7 | 核发时预占已占满 | 配药/核发断言失败，不扣负 |
| T8 | 同药 FIFO 拆两批核发 | 各批按本批物理−本批预占断言，不跨批误报不足 |

---

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 初稿：护士站执行与发药并发防超发审查结论与加固方案 |
| v1.1 | 2026-06-28 | GAP-EXE-001～007、009 代码落地；父文档状态 → 已实施 |
| v1.2 | 2026-06-28 | GAP-EXE-010：自然月累计 Gate 落地（EX-IP-EXE-305） |
| v1.3 | 2026-07-29 | **GAP-EXE-003 纠偏**：预占与物理同批口径（`sumDispenseExeReserveQuantityInSplitUnit` 增 `lotNumber`）；补 T8 / 验收用例 |
