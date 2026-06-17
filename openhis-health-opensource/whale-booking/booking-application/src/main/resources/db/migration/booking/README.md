# Booking Flyway 迁移说明

> **Schema**：`flyway_schema_history_booking`（与 health 主库同库，独立历史表）  
> **清库重跑**：删除 `flyway_schema_history_booking` 中 booking 版本记录后，由 Flyway 按 V1→V7 顺序执行。

## 版本索引

| 版本 | 文件 | 内容 |
| --- | --- | --- |
| V1 | `V1__Booking_Wkf_Init.sql` | 运行时核心：`wkf_appointment_queue` / `wkf_schedule*` / `wkf_slot*`（含替诊字段） |
| V2 | `V2__Booking_Policy.sql` | 院点策略：放号、挂号衔接、停诊处置 |
| V3 | `V3__Booking_Business_Ext.sql` | P1 业务扩展：医技映射、床位预留、体检团检 |
| V4 | `V4__Booking_Channel_Queue.sql` | 渠道配置、叫号大屏、资源画像 |
| V5 | `V5__Booking_Schedule_Template.sql` | 排班模板 T1/T2/T3/TW、停诊预览冻结 |
| V6 | `V6__Booking_Menus.sql` | 菜单、权限、管理员角色授权（依赖 health `whale_menus`） |
| V7 | `V7__Booking_Waitlist.sql` | 候补队列：`bkg_waitlist_entry`（BR-EXT-011） |

## 表命名约定

| 前缀 | 含义 |
| --- | --- |
| `wkf_*` | 运行时库存层（Schedule / Slot / Appointment） |
| `bkg_*` | Booking 配置与业务扩展表 |

## 清库重跑步骤

1. **清空 booking Flyway 历史**（与 health 同库时勿动 `flyway_schema_history`）：
   ```sql
   DELETE FROM flyway_schema_history_booking;
   ```
2. **删除 booking 相关表**（按外键依赖从子表到父表，或直接 `DROP SCHEMA` 后重建）：
   - `bkg_*` 配置与扩展表
   - `wkf_*` 运行时表
   - V6 写入的菜单/权限可保留或一并清理后由 V6 重新 upsert
3. 启动应用或执行 Flyway migrate，将依次应用 **V1 → V7**。

> 若整库 DROP 重建，须先跑完 health 主迁移（含 `whale_menus` 等），再跑 booking V1～V7。

复制 `_TEMPLATE.sql`，版本号递增为 `V8__...`，勿修改已发布版本文件内容。
