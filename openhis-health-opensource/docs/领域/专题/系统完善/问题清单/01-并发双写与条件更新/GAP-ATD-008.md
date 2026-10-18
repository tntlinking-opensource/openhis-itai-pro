# GAP-ATD-008 IF-IP-AUD-03 转科/出院退回未回滚 ON_HOLD

> **问题类型**：流程状态机与编排对称性（由 GAP-ATD-006 评审衍生）
> **严重度**：中危
> **影响范围**：住
> **优先级**：P2
> **状态**：**已实施**（2026-06-28）
> **父文档**：[SYS-IMP-004](../01-并发双写与条件更新/SYS-IMP-004-入出转分床与床位运营并发边界.md)
> **关联 IF/BR**：AS-IP-NUR-003 **IF-IP-AUD-03**、AS-IP-DOR-003 **IF-IP-DOR-16/20**；**BR-IP-AUD-011**；[GAP-ATD-006](./GAP-ATD-006.md)

---

## 1. 问题描述

转科/出院医嘱**逆向**操作（退回、停嘱、签退等）未对称回滚就诊 `ON_HOLD` 编排；`rejectNurseStationOrders` 类注释承诺「同步撤销转科/出院编排」但实现缺失。

## 2. 发生场景

| 场景 | 现象 |
| --- | --- |
| S1 | 护士校对转科/出院（IF-IP-AUD-02）→ 就诊 `IN_PROGRESS→ON_HOLD`；随后医生**停嘱/签退**或护士将诊疗退回草稿 → 医嘱已非 `VERIFIED`，就诊**仍** `ON_HOLD` |
| S2 | 待转科/待出院 Tab 仍展示该患者；护士办理出院（ATD-05）或入科分流错乱 |
| S3 | `initiateDepartmentTransfer` 已收口活动参与者；逆操作未恢复责任医护 |

> **说明**：IF-IP-AUD-03 允许 `ACTIVE/VERIFIED→DRAFT`（未校对/已校对 Tab 均可退回；已校对须 **BR-IP-AUD-013** 门控）。对**纯 ACTIVE** 退回，就诊通常仍为 `IN_PROGRESS`，编排为 no-op；**已校对 VERIFIED** 退回同样经 `tryRevertHoldAfterServiceRequestReverted` 对称回滚。

## 3. 根因分析

- IF-IP-AUD-02 有 `orchestrateEncounterAfterServiceVerified`（正向 `ON_HOLD`），**无**对称逆向编排。
- `rejectNurseStationOrders` 仅调用 `rejectMedicationOrder` / `rejectServiceRequestToDraft`，不触达 `EncounterDomainService`。
- 领域缺少 `ON_HOLD→IN_PROGRESS`（转科/出院撤销）原子动作与参与者恢复契约。

## 4. 业务影响

- 就诊主档与医嘱状态不一致；待转科/待出院读模型长期脏读。
- 与 ATD-05 出院链、入科分流门禁交叉误判。
- 运营侧需人工改库或二次操作，难审计。

## 5. 修改方案（已采用）

### 5.1 编排对称

1. **领域**：`Encounter#revertHoldToInProgress` + `EncounterDomainService#revertTransferOrDischargeHold`
   - `requireEncounterForUpdate` + 锁内断言 `ON_HOLD`
   - 转科轨：`ON_HOLD→IN_PROGRESS` + 按最近收口批次 INSERT 活动参与者
   - 出院轨：`ON_HOLD→IN_PROGRESS` + 清空 `outWayCode`
2. **App**：`InpatientTransferDischargeHoldOrchestrationSupport#tryRevertHoldAfterServiceRequestReverted`
   - 里程碑 `requestId` 对齐；无异类/同类其它 `VERIFIED` 转科/出院残留时回滚
3. **挂载点**：
   - `NurseStationOrderAuditAppService#rejectNurseStationOrders`（诊疗退回后）
   - `InpatientDoctorStationAdviceAppService` 签退（IF-IP-DOR-16）/ 长期停嘱（IF-IP-DOR-20）

### 5.2 并发

- 与 GAP-ATD-006 一致：`requireEncounterForUpdate` + `Encounter` `@Version`。
- 护士站路径：`ENCOUNTER_INVALID_STATUS` / 乐观锁 → **EX-IP-AUD-203**。

## 6. 涉及代码

- `Encounter.java`、`RevertTransferOrDischargeHoldCommand.java`
- `EncounterDomainService#revertTransferOrDischargeHold`
- `ServiceRequestQueryService#hasVerifiedTransferOrDischargeRequest`
- `InpatientTransferDischargeHoldOrchestrationsupport
- `NurseStationOrderAuditAppService`、`InpatientDoctorStationAdviceAppService`

## 7. 解决日志

| 项 | 说明 |
| --- | --- |
| **实施日期** | 2026-06-28 |
| **领域** | `revertTransferOrDischargeHold`；转科参与者最近批次恢复 |
| **App** | 共用 `InpatientTransferDischargeHoldOrchestrationsupport |
| **AUD-03** | 诊疗 `reject` 后尝试逆向编排；纯 ACTIVE 未校对为 no-op |
| **DOR-16/20** | 签退/停嘱已校对转科/出院后同步回滚 |
| **Pre 门禁** | 里程碑 request 对齐 + `hasVerifiedTransferOrDischargeRequest` |
| **错误码** | 护士站 **EX-IP-AUD-203**；医生站 `ENCOUNTER_INVALID_STATUS` |

## 8. 验收用例

- 校对转科 → `ON_HOLD`；同里程碑诊疗签退 → 就诊 `IN_PROGRESS`，待转科展示清除。
- 校对出院 → `ON_HOLD`；签退后就诊 `IN_PROGRESS`。
- 逆操作与 ATD-05 并发：仅一方成功，另一方明确失败码。
- 纯 ACTIVE 转科/出院退回（未校对）：就诊保持 `IN_PROGRESS`，无副作用。
- 仍存在其它已校对转科/出院医嘱：不回滚 `ON_HOLD`。

## 9. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-28 | GAP-ATD-001～007 评审衍生 |
| v1.1 | 2026-06-28 | 逆向编排落地；状态 → 已实施 |
