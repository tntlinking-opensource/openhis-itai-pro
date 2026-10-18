# SYS-IMP-007 附录：跨角色写接口 Gate 矩阵

> **父文档**：[SYS-IMP-007](./SYS-IMP-007-跨角色Stale页面与状态不同步.md)
>
> **用途**：Wave 2（GAP-STALE-005 + GAP-STALE-007）审计产出；新写接口评审模板。
>
> **标准**（已确认 2026-06-28）：Cmd 前 **最小重读 Gate**（Charge / Dispense / 医嘱态 **三取其二**）+ 能叠加则 **条件 UPDATE**；前端 `disable` 仅体验层。
>
> **状态图例**：✅ 已落地 · ⏳ 待补 · N/A 非跨角色 Stale · 🔄 见关联 IMP

---

## 1. Health 门诊 + 住院临床/财务

| # | 写接口 | 跨角色上游 | 重读实体 | Gate（三取二） | 错误码 | 条件 UPDATE | 关联 GAP | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| H1 | `OutpatientChargeAppService#confirmOutpatientChargeSettlement` | 医生签退 | Charge、MedReq/Dispense | Charge=`PLANNED` + 锚点可收费 | EX-OPC-411 | Charge `PLANNED→SETTLED` | STALE-001 | ✅ |
| H2 | `OutpatientChargeAppService#preSettleChargeItems` | 医生签退 | Charge | Charge=`PLANNED` | — | — | STALE-001 | ✅ |
| H3 | 住院正式结算 / 全结 | 取消执行 void | Charge | 源态匹配 | FIN-001 | Charge 条件迁移 | STALE-002 | 🔄 IMP-002 |
| H4 | `NurseStationOrderCancelExecuteAppService` | 已结算 / 配药中 | Charge、Dispense | Settlement + Pharmacy Gate | EX-IP-CXL-203/204 | Dispense 软删 | STALE-003/004 | ✅ |
| H5 | `InpatientPharmacyDispenseAppService#dispenseInpatientMedications` | 取消执行 | Charge、Procedure | Charge=`PENDING_SETTLEMENT` | — | Dispense 态 | STALE-004 | ✅ |
| H6 | 门诊/住院药房配药/核发 | 取消执行软删 | Dispense | 非软删 + 态允许 | NOT_FOUND/INVALID | — | STALE-003 | ✅ |
| H7 | `NurseStationDispenseSummaryAppService#createMedicationSummary` | Stale 双窗汇总 | Dispense | `PREPARATION` 且无 summaryNo | EX-IP-SUM-201 | 条件 UPDATE | STALE-006 | ✅ |
| H8 | `InpatientPharmacySummaryDispenseAppService#dispenseSummaryMedications` | Stale 双窗核发 | Dispense、Supply | `SUMMARIZED` | EX-IPS-201 | 条件 UPDATE | STALE-006 | ✅ |
| H9 | `InpatientDoctorStationAdviceAppService#withdrawInpatientDoctorStationAdviceBatch` | 护士已执行 | Procedure、Dispense、Charge | 无净执行/阻断发药/锁定费用 | EX-IP-ORD-103 | — | STALE-005 | ✅ |
| H10 | `InpatientDoctorStationTcmAppService` 签退/撤回 | 同上 | 同上（委托共用 Support） | 同上 | EX-IP-ORD-103 | — | STALE-005 | ✅ |
| H11 | `DoctorStationAdviceAppService#withdrawDoctorStationAdviceBatch` | 款台预结算 | Charge、MedReq | 回滚 PLANNED + 作废 draft | — | — | STALE-001 | ✅ |
| H12 | `NurseStationOrderExecuteAppService` 执行写 | 医生签退/停嘱 | MedReq/SvcReq | `VERIFIED` + occurrence 净态 | EX-IP-EXE-301/303 | — | STALE-007 | ✅ |
| H13 | 门诊护士执行 | 医生签退 | MedReq | 请求态 + occurrence | — | — | STALE-007 | ✅ |
| H14 | `NurseStationOrderExecuteAppService` 执行/不执行 | 护士办理出院后 | Encounter | `status_enum` 非 `PENDING/COMPLETED/DISCHARGED` | EX-IP-EXE-307 | — | FLW-008 | ✅ |
| H15 | `NurseStationOrderCancelExecuteAppService` | 同上 | Encounter | 同上 | EX-IP-CXL-206 | — | FLW-008 | ✅ |
| H16 | `NurseStationInpatientBillingAppService` 划价/补费/取消划价 | 同上 | Encounter | 同上 | EX-IP-PRC-406 | — | FLW-008 | ✅ |
| H17 | `NurseStationSkinTestAppService#saveSkinTestRecord` | 同上 | Encounter | 同上 | EX-IP-STK-208 | — | FLW-008 | ✅ |
| H18 | `InpatientDoctorStationAdviceMapper#validateInpatientEncounterWritableGate` | 同上 | Encounter | 同上 | EX-IP-ORD-107 | — | FLW-008 | ✅ |

---

## 2. 入出转 IMP-004（health）

| # | 写接口 | 跨角色上游 | 重读实体 | Gate | 错误码 | 条件 UPDATE | 关联 GAP | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A1 | `NurseStationATDManagerAppService` 分床/换床 | 双窗 / booking 预留 | Location | `operational_enum=NOT_IN_USE` 行锁 | EX-IP-ATD-201 | occupy 条件/行锁 | ATD-001 | ✅ |
| A2 | `executeSwapBeds` | 第三方占床 | Location×2 | 双床 `FOR UPDATE` | EX-IP-ATD-201 | — | ATD-003 | ✅ |
| A3 | `InpatientRegistrationAppService#registerInpatientEncounter` | 双窗登记 | Encounter | `PLANNED` 锁内断言 | EX-IP-REG-003 | — | ATD-004 | ✅ |
| A4 | 出院 / 召回 / 清床 | 双窗 | Encounter | 态 + 行锁 + `@Version` | EX-IP-ATD-304/402/504 | — | ATD-005 | ✅ |
| A5 | 分床 vs booking 预留 | 预约员 + 护士 stale | Location + Appointment | booking 三态联动 | EX-IP-ATD-207 | — | ATD-007 | ✅ |
| A6 | 转科/出院 ON_HOLD | 校对竞态 | Encounter | 行锁 + 异类已校对 Gate | — | — | ATD-006/008 | ✅ |
| A7 | **床板 UI** | 缓存滞后 | — | 体验层 refresh | — | — | BND-ATD-003 | ⏳ 体验层 |

---

## 3. Booking IMP-003（zxplat-module-booking）

| # | 写接口 | 跨角色上游 | 重读实体 | Gate | 错误码 | 条件 UPDATE | 关联 GAP | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| B1 | `BookingRegisterAppService#createOutpatientBooking` | 双 Tab 同患者同日 | Appointment 探测 + Slot | 读 Gate + DB 唯一 | REG-BIZ-409 | Slot `@Version` | BKG-001 | ✅ |
| B2 | `BookingRegisterAppService#cancelBooking` | Stale Tab 重复取消 | Appointment | `requireAppointment` + `assertActive`（终态 `isDeleted`） | APPOINTMENT_INVALID_ARGUMENT | `@Version` | STALE-007 | ✅ |
| B3 | `BookingRegisterAppService#rescheduleBooking` | 已取消后 stale 改约 | Appointment、Slot | 加载时拒终态 + `assertActive` + 已转挂号拒 | APPOINTMENT_* | Slot `@Version` | STALE-007 | ✅ |
| B4 | `BedBookingAppService` 床位预留 | health 已分床 | Location | 行锁 + 三态 | — | — | BKG-006/ATD-007 | ✅ |
| B5 | 渠道预约 / 候补提升 | 取消/改约竞态 | Slot、Waitlist | 乐观锁 + 条件提升 | SLOT_003 | — | BKG-002/005 | ✅ |
| B6 | `ChannelBookingAppService#cancelChannelBooking` | Stale 重复取消 | Appointment | 同 B2 | 同 B2 | — | STALE-007 | ✅ |

---

## 4. 审计结论（Wave 2）

| 类别 | 结论 |
| --- | --- |
| **Health 核心链路** | STALE-001～006 均已后端 Gate 或条件 UPDATE 覆盖；STALE-005 由 `InpatientAdviceRevokeGatesupport 落地 |
| **IMP-004 入出转** | 并发/Stale 硬门禁已齐（ATD-001～008）；剩余 A7 为前端床板 refresh，不挡后端安全 |
| **IMP-003 Booking** | 取消/改约/Stale 提交均经 `requireAppointment` + `assertActive` 或 Query 拒终态；双 Tab 改约由 `@Version` 映射冲突 |
| **GAP-STALE-007 闭环** | 无 P0 后端缺口；⏳ 项仅体验层（床板 refresh、统一失败文案、写后 invalidate） |

---

## 5. 体验层待办（P2，不阻塞 Wave 2 后端闭环）

| 项 | 做法 | 模块 |
| --- | --- | --- |
| E1 | 正式结算/签退失败统一「数据已变更，请刷新后重试」 | 款台/医生站 | ✅ `stale-cross-role-error.ts` + `request.ts` |
| E2 | 药房/护士 403/业务码统一文案 | 药房/护士 | ✅ 同上 + `useRequest` 去重 |
| E3 | 床板/预约列表写成功后 invalidate | ATD/Booking | ✅ `refreshOnStale` 各模块 |
| E4 | 分床/预约提交 loading + 可选幂等键 | ATD/Booking | ✅ `directBedSubmitting` / 预约 loading |

---

## 6. 回归测试索引

| 用例 | 场景 | 期望 | 测试落点 |
| --- | --- | --- | --- |
| T1 | 预结算 → 签退 → 旧 draft 结算 | 拒绝 | STALE-001 集成 |
| T2 | 取消 PREPARATION → 同 id 配药 | 拒绝 | STALE-003 |
| T3 | IN_PROGRESS → 取消执行 | EX-IP-CXL-204 | STALE-004 |
| T4 | 取消后同 id 核发 | 拒绝 | STALE-004 |
| T5 | 已执行未结算 → 住院签退 | EX-IP-ORD-103 | `InpatientAdviceRevokeGateSupportTest` |
| T6 | 已 SETTLED → 取消执行 | EX-IP-CXL-203 | 取消执行集成 |
| T7 | 取消后 stale 改约/再取消 | 拒绝 | Booking 域 `assertActive` |
| T8 | 分床 vs booking 同床 | EX-IP-ATD-207 | ATD-007 集成 |
| T9 | `PENDING` 就诊 → 执行/划价/开立 | EX-IP-EXE-307 等 | FLW-008 集成 |

---

## 7. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.1 | 2026-07-11 | 增补 **H14～H18**（**BR-IP-FLW-008** 出院结算轨临床/费用写 Gate）；回归 **T9** |
| v1.0 | 2026-06-28 | Wave 2 审计矩阵：Health + IMP-004 + IMP-003；STALE-005/007 合并交付 |
