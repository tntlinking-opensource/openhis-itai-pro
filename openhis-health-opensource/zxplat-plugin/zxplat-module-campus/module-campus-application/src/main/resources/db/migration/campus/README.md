# Campus Flyway

| 版本 | 文件 | 内容 |
|------|------|------|
| V1 | `V1__campus_ddl.sql` | `cmp_*` DDL 最终态 |
| V2 | `V2__seed_nenu_campus_sites.sql` | 校区主数据 + 校医院菜单/权限（原 V3 合入） |
| V4 | `V4__seed_dev_campus_identities.sql` | **DEV ONLY** 联调学生/教职工档案（依赖 health-dev 患者） |

> 2026-09-15：原 V3 菜单已并入 V2；无 V3 文件。版本号可有缺口（保留 V4 以免改动已跑库 history）。  
> history：`flyway_schema_history_campus`
