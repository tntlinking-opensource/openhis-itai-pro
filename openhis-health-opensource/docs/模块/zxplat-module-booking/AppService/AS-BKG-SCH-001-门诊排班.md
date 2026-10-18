# Booking · 门诊排班 · 应用服务设计（AS）

> **编码**：AS-BKG-SCH-001 · **实现**：`OutpatientScheduleAppService` · **日期**：2026-09-24  
> **SDD**：`../AppService-SDD/AS-BKG-SCH-001-门诊排班-SDD.md`

## 1. 边界

在内：排班表单初始化、日历/月览、草稿创建更新、发布与冲突预览、号源批量供给、模板生成、复制下周、停诊影响/转约、代诊、停用/再激活/退役。  
在外：单 Slot 封锁删除（AS-BKG-SLT-001）；挂号（AS-BKG-REG-001）。

## 2. 接口总览（核心）

| IF | 方法 |
| --- | --- |
| IF-BKG-SCH-01 | `loadScheduleFormInit` |
| IF-BKG-SCH-02 | `pageSchedulesByCalendar` |
| IF-BKG-SCH-03 | `pageSchedulesByMonth` |
| IF-BKG-SCH-04 | `createSchedule` |
| IF-BKG-SCH-05 | `updateScheduleDraft` |
| IF-BKG-SCH-06 | `publishSchedule` |
| IF-BKG-SCH-07 | `previewPublishScheduleConflicts` |
| IF-BKG-SCH-08 | `provisionSlotBatch` |
| IF-BKG-SCH-09 | `generateFromTemplate` |
| IF-BKG-SCH-10 | `copyScheduleToNextWeek` |
| IF-BKG-SCH-11 | `previewStopClinicImpact` / `releaseStopClinicPreviewFreeze` |
| IF-BKG-SCH-12 | `transferAppointmentsOnStopClinic` |
| IF-BKG-SCH-13 | `substitutePractitioner` |
| IF-BKG-SCH-14 | `deactivateSchedule` / `reactivateSchedule` / `retireSchedule` |

## 3. Gap

无。

## 4. 编排

发布 → ScheduleDomainService；供给 → SlotDomainService 批量；停诊冻结 → StopClinic 域。
