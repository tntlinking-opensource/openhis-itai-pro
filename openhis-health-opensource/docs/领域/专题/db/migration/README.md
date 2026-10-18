# health Flyway 迁移规范（PostgreSQL）

路径：`zxplat-domain/src/main/resources/db/migration/health/`  
历史表：`flyway_schema_history`  
开发演示种子：`health-app/src/main/resources/db/migration/health-dev/`（`openhis.flyway.dev-seed.enabled=true` 时追加；历史表 `flyway_schema_history_dev`，勿与核心共用）

> **Baseline squash 已执行**（2026-07；**2026-08-13 二次合入**：原 V11–V29 增量已并入 V1–V10 最终态后删除；**2026-09-15 再次合入**：原 V11–V32 增量已并入 V1–V10 最终态后删除；**2026-10-18 再次合入**：原 V11–V20 增量已并入 V1–V10 最终态后删除）。  
> 方案见 [FLYWAY_BASELINE_SQUASH_PLAN.md](../../工程/FLYWAY_BASELINE_SQUASH_PLAN.md)。

## 链式顺序（`ModularFlywayConfiguration`）

1. **health** → `flyway_schema_history`
2. **扩展模块**（**peis** / booking / chs / …）→ 各自 `flyway_schema_history_*`
3. **院点 project-*** → `flyway_schema_history_project_*`
4. **health-dev**（可选）→ `flyway_schema_history_dev`
5. **health-final**（V99）→ 再次写入 `flyway_schema_history`

## Squash 后版本结构

| 版本 | 文件 | history 表 | 内容 |
|------|------|------------|------|
| V1–V7、V9 | `health/V1__…` …（**无 V8**） | core | 按域合并的 DDL（含停嘱校对列、日结读索引、滚费 business_time、病案首页 P0 扩列等最终态） |
| V10 | `health/V10__seed_platform.sql` | core | platform 种子（术语/菜单/打印模板等全量最终态；**含 ddd_unit**） |
| **peis V1–V2** | `peis/V1__…` / `V2__…` | **`flyway_schema_history_peis`** | 全部 `peis_*` DDL + 菜单/打印种子 **最终态**（禁止 V3+ 增补；开关 `zxplat.modules.peis.enabled`） |
| V11–V26 | `health-dev/V11__…` … `V26__…` | **dev** | 仅开发演示数据（与 core 分 history，版本号可重叠） |
| V99 | `health-final/V99__…` | core | `whale_sequence` 同步 |

> **同 location 禁止同版本双文件**（否则启动报 `Found more than one migration with version N`）。  
> 核心 **无 V11+**：**禁止**再往 `health/` 放增补文件；新变更改 V1–V10（清库重建），或另定迁移策略。  
> 勿再增加 `health/V11__seed_ddd_unit.sql`：DDD 单位种子已在 **V10**。  
> 日志里核心跳过 health-dev 的 V11–V26 **是正常的**。  
> 核心无 V8 **是正常的**：`peis_*` 由 peis 模块独立 history 管理（见 [PeisAppService/06-Flyway](../PeisAppService/06-Flyway-表结构.md)）。
>
> **2026-08-13 扩展模块二次 squash（与 health 核心同日）**：`chs`→仅 V1；`emr`→V1–V5（原 V6–V12 合入）；`einvoice_boss`→V1–V2；`project-H22010402403`→仅 V1。已跑旧增量的库须对应 history **删库重建**。  
> **2026-08-20**：health **V11–V14 增补已合入基线后删除**（`permitted_gender_enum`/`sort_order`→V6；学历→V2；目录索引→V2/V3/V6/V7）；peis 仅保留 **V1+V2 最终态**。  
> **2026-09-15**：health **V11–V32 增补已合入基线后删除**（`adm_device`/`cus_specimen_tube_merge`→V2/V7；`print_count`→V3；报表索引→各域 DDL；病案首页扩列终态→V9；菜单/打印/术语/settings→V10）。已跑旧 V11–V32 的库须 **删库重建**。  
> **2026-09-15 扩展模块 squash**：`peis`→仅 V1+V2（原 V3–V6 合入）；`booking`→V1+V2（原 V3–V6 合入）；`unionpay_bpc` `paid_amt_fen`→V1；`elep` 开方上下文列→V1（删 V2）；`campus` 菜单→V2（V4 仍为 DEV 联调种子）。  
> **2026-10-18**：health **V11–V20 增补已合入基线后删除**（`mrms_homepage_icu/bld`→V9；`ICU_TYPE`/`patn_rlts` 01/02/`ip-dispenseSummary-print` 终态→V10；`charge_intent`→V2；护士站队列 tenant 索引→V2/V3/V5/V6）。已跑旧 V11–V20 的库须 **删库重建**。  
> **2026-10-18 扩展模块/院点 squash**：`chs`→仅 V1（`uk_yb_ip_fee_upload_feedetl`）；`emr`→V1–V5（原 V6 归档/编辑申请合入 V1）；`msfx`→V1+V2（原 V3 顶级菜单合入 V2）；`pacs`→V1 DDL + V2 打印种子；`wi`→仅 V1；`project-H22010202493`→仅 V1。已跑旧增量的库须对应 history **删库重建**。

扩展模块（peis / booking / chs / emr 等）各自脚本 + history；院点差异见 `whale-project/.../project-H22010402403/`。

## SQL 规范（发版后）

- 增量以 `CREATE` / `INSERT`（含 `ON CONFLICT`）为主；既有库补列可用 `ALTER … ADD COLUMN IF NOT EXISTS`
- 同一张表只在一个 DDL 脚本中 `CREATE`，禁止跨文件重复建表
- **同一 `db/migration/health` 下版本号全局唯一**（`V11__a.sql` 与 `V11__b.sql` 会直接起不来）

## 注释（必须）

每张表、每一列均须在同一脚本内补充：

```sql
COMMENT ON TABLE adm_patient IS '患者主档';
COMMENT ON COLUMN adm_patient.id IS '主键';
-- ...
```

审计字段标准注释见 [`scripts/flyway-comment-gen/README.md`](../../../../../../../scripts/flyway-comment-gen/README.md)。

## 删库重建

**必须整 schema 清空**（否则会残留 `flyway_schema_history_dev` 等，表现为「其它 history 新建、dev 已是 25」）：

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO CURRENT_USER;
GRANT ALL ON SCHEMA public TO public;
```

然后重新启动应用。期望：

- `flyway_schema_history`：V1–V7、V9–V10、V99（**无 V8**；无「关系已存在」WARN）
- `flyway_schema_history_peis`：当前 peis 最高版本（`zxplat.modules.peis.enabled=true`）
- `flyway_schema_history_dev`：V11–V26
- 各模块 history：各自版本…

> **已跑过旧 V11–V32 的库**：history checksum/缺口会与仓库不一致。开发环境请按上文 **删库重建**；生产/联调库勿直接删脚本文件而不做迁移策略（需 repair/baseline 或手工对齐）。

## 新建脚本（发版后）

- 核心增量从 **V11** 起编号（勿与 health-dev 语义混淆；以 `health/` 目录内已占用号为准）
- 模板：`../_templates/health/_TEMPLATE.sql`（Flyway 不扫描 `_templates`）
