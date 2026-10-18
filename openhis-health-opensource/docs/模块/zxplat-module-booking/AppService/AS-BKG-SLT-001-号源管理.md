# Booking · 号源管理 · 应用服务设计（AS）

> **编码**：AS-BKG-SLT-001 · **实现**：`SlotManageAppService` · **日期**：2026-09-24  
> **SDD**：`../AppService-SDD/AS-BKG-SLT-001-号源管理-SDD.md`

## 1. 边界

在内：按日分页号源、详情、封锁/解封、超售标记、删除。  
在外：排班发布与批量供给（SCH）；挂号占号（REG）。

## 2. 接口

| IF | 方法 |
| --- | --- |
| IF-BKG-SLT-01 | `pageSlotsByDate` |
| IF-BKG-SLT-02 | `loadSlotDetail` |
| IF-BKG-SLT-03 | `blockSlots` |
| IF-BKG-SLT-04 | `unblockSlots` |
| IF-BKG-SLT-05 | `setOverbooked` |
| IF-BKG-SLT-06 | `deleteSlots` |

## 3. Gap

无。规则对齐 DM-BKG-003 BR-SLT-*。
