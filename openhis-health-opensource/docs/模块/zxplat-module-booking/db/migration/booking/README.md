> 文档入口指向本文件；脚本路径：`zxplat-module-booking/.../db/migration/booking/`

# Booking Flyway 迁移说明

> **Schema**：`flyway_schema_history_booking`（与 health 主库同库，独立历史表）  
> **2026-09-15 squash**：原 V3–V6 已并入 V1/V2 最终态后删除。

## 版本索引

| 版本 | 文件 | 内容 |
| --- | --- | --- |
| V1 | `V1__bkg_ddl.sql` | 全部 `bkg_*` DDL **最终态**（含体检号源日历表、`bkg_health_check_booking_ext` 分池列、日类型 partial unique） |
| V2 | `V2__seed_platform_menus.sql` | 窗口体检预约菜单 + 体检号源日历显示名终态 |

> Booking 主菜单主体仍在 **health V10**；V2 仅补模块增量并幂等覆盖日历显示名。  
> **禁止** booking V3+ 增补（清库重建场景改 V1/V2）。

## 关键表（体检号源）

| 表名 | 说明 |
| --- | --- |
| `bkg_hc_slot_template` | 体检号源时段模板 |
| `bkg_hc_day_plan` | 日计划 |
| `bkg_hc_day_type` | 日开放合同类型（partial unique） |
| `bkg_hc_day_slot` | 日时段配额分池 |

## 清库

已跑过旧 V3–V6 的库请 **删库重建**（或删除 `flyway_schema_history_booking` 后重建模块表）。
