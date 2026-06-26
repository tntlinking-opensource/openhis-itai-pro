# health Flyway 迁移规范（PostgreSQL）

路径：`health-infrastructure/src/main/resources/db/migration/health/`  
历史表：`flyway_schema_history`

## 注释（必须）

每张表、每一列均须在同一脚本内补充：

```sql
COMMENT ON TABLE adm_patient IS '患者主档';
COMMENT ON COLUMN adm_patient.id IS '主键';
-- ...
```

审计字段标准注释见 [`scripts/flyway-comment-gen/README.md`](../../../../../../../scripts/flyway-comment-gen/README.md)。

批量补注释：`node scripts/flyway-comment-gen/generate_and_apply.mjs --root . --apply`

## 命名

- 单表脚本：`V{n}__{表名}.sql`（如 `V17__adm_patient.sql`）
- 框架与种子：`V1__Init.sql`（全部 `whale_*`）
- 当前最大版本：**V74**（`prt_print`）
- 新建脚本模板：`../_templates/health/_TEMPLATE.sql`（Flyway 不扫描 `_templates` 目录）
- 发版 squash：`../../../../../../../scripts/squash-flyway-release.mjs`
