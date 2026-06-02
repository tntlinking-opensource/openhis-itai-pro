# health Flyway 迁移

## 结构（按表拆分）

| 版本 | 文件 | 内容 |
|------|------|------|
| **V1** | `V1__Init.sql` | 全部 `whale_*` 表：建表 + 种子数据 |
| **V2–V86** | `V2__{表名}.sql` … | 每张非 `whale_*` 业务表一个文件（含 `V86__whale_framework_runtime_state.sql`） |

执行入口：`ModularFlywayConfiguration` → `classpath:db/migration/health`（单表 `flyway_schema_history`）。

## 新增变更约定

- `whale_*` 表：改 `V1__Init.sql`
- 其他表：新增 `V{n}__{表名}.sql`，版本号接当前最大 V 递增
- 禁止再向 Flyway 加入 `hisrm_*` 读模型表

## 本地库从旧版 V1–V21 迁移

若 `flyway_schema_history` 仍为旧版校验和，开发库请换空库或清空历史后重建：

```sql
TRUNCATE flyway_schema_history;
-- 或 DROP SCHEMA public CASCADE; CREATE SCHEMA public;
```

然后启动应用，按 V1→V86 顺序执行。
