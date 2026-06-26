# Booking Flyway 迁移说明

> **Schema**：`flyway_schema_history_booking`（与 health 主库同库，独立历史表）  
> **清库重跑**：执行 `../_templates/booking/_RESET.sql` 或手动删表后，由 Flyway 按 **V1→V7** 顺序执行。

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

## 表清单（V1～V7 共 28 张）

### wkf_* 运行时（V1）

| 表名 | 说明 |
| --- | --- |
| `wkf_appointment_queue` | 预约与候诊队列 |
| `wkf_schedule` | 号源日程 |
| `wkf_schedule_actor` | 日程参与者 |
| `wkf_schedule_identifier` | 日程外部标识 |
| `wkf_slot` | 号源时段 |
| `wkf_slot_identifier` | 号源外部标识 |

### bkg_* 配置与扩展

| 表名 | 版本 | 说明 |
| --- | --- | --- |
| `bkg_release_policy` | V2 | 放号策略 |
| `bkg_registration_policy` | V2 | 挂号衔接策略 |
| `bkg_schedule_change_policy` | V2 | 停诊与预约处置策略 |
| `bkg_diagnostic_resource_map` | V3 | 医技资源映射 |
| `bkg_bed_reservation_ext` | V3 | 床位预留扩展 |
| `bkg_health_check_package` | V3 | 体检套餐 |
| `bkg_health_check_package_item` | V3 | 体检套餐明细 |
| `bkg_group_checkup_org` | V3 | 团检单位 |
| `bkg_group_checkup_batch` | V3 | 团检批次 |
| `bkg_group_checkup_roster` | V3 | 团检名单 |
| `bkg_health_check_booking_ext` | V3 | 体检预约扩展 |
| `bkg_channel_config` | V4 | 渠道配置 |
| `bkg_queue_display_config` | V4 | 叫号大屏配置 |
| `bkg_resource_profile` | V4 | 资源画像（暂无 Java 实体，预留） |
| `bkg_slot_provision_template` | V5 | T1 放号时段模板 |
| `bkg_schedule_template` | V5 | T2 排班日模板 |
| `bkg_calendar_exception` | V5 | T3 日历例外 |
| `bkg_week_schedule_template` | V5 | TW 周排班模板 |
| `bkg_week_schedule_template_item` | V5 | TW 明细 |
| `bkg_stop_clinic_slot_freeze` | V5 | 停诊预览号源冻结 |
| `bkg_waitlist_entry` | V7 | 候补队列 |

## 表命名约定

| 前缀 | 含义 |
| --- | --- |
| `wkf_*` | 运行时库存层（Schedule / Slot / Appointment） |
| `bkg_*` | Booking 配置与业务扩展表 |

## 清库重跑步骤

### 方式 A：仅重置 Booking 模块（推荐）

```bash
psql -h localhost -U postgres -d whale -f booking-application/src/main/resources/db/migration/_templates/booking/_RESET.sql
```

或手动：

```sql
-- 见 ../_templates/booking/_RESET.sql
DELETE FROM flyway_schema_history_booking;
-- DROP bkg_* / wkf_* 表 …
```

然后启动应用（`-Pwith-booking`），Flyway 依次应用 **V1 → V7**。

### 方式 B：整库 DROP 重建

1. 重建库并跑完 **health** 主迁移（含 `whale_menus`、`whale_permissions`）
2. 启动 **with-booking** 应用，自动跑 booking V1～V7

> health 侧仍保留历史 `V61～V71` 的 `wkf_*` 建表脚本；booking V1 使用 `CREATE IF NOT EXISTS`，与 health 共存无冲突。

### V6 菜单/权限

- V6 对 `whale_permissions` / `whale_menus` / `whale_permission_grants` 做 **upsert**（`ON CONFLICT`），重复执行安全。
- 若 `../_templates/booking/_RESET.sql` 中取消注释第 4 步，可软删 booking 菜单后由 V6 重建。

## 新增版本

复制 `../_templates/booking/_TEMPLATE.sql`，版本号递增为 `V8__...`，**勿修改已发布 V1～V7 文件内容**。
