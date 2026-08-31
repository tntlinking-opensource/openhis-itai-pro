# EMR Flyway 种子（清库压扁）

history 表：`flyway_schema_history_emr`  
配置：`META-INF/openhis/flyway-module.json` → `classpath:db/migration/emr`

| 版本 | 文件 | 内容 |
|------|------|------|
| V1 | `V1__emr_ddl.sql` | 6 张表 DDL |
| V2 | `V2__seed_platform.sql` | 平台类型 101–104、元数据 001–012、病程模板 203/204 |
| V3 | `V3__seed_notice_forms.sql` | NOTICE 类型、元数据 013–051、告知模板 206–215 |
| V4 | `V4__seed_outpatient_admission_forms.sql` | 门诊/入院元数据与模板 201/205（含原 V6–V12 终态：OP_RECORD_STD、IP_ADMISSION_STD、physicalExam、中西医诊断绑定、戒烟页脚） |
| V5 | `V5__seed_chaoyang_inpatient_forms.sql` | 朝阳住院增量类型/元数据/模板（单院徽终态；含原 V10 手术交接可点选复选框；`wardName`→ENCOUNTER/`encounter.houseName`） |

清库步骤：删除 `emr_*` 表与 `flyway_schema_history_emr` 后重启，或 `flyway repair` 不适用——须空 history 从 V1 重跑。

**勿**在已跑到旧增量（原 V6–V12 或更早编号）的库上直接替换本目录（validate 会失败）。
**禁止**再加 V6+ 增补；新变更改 V1–V5 最终态（清库重建）。
