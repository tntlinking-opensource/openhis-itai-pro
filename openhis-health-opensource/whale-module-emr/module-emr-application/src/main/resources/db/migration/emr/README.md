# EMR Flyway 种子（清库压扁）

history 表：`flyway_schema_history_emr`  
配置：`META-INF/openhis/flyway-module.json` → `classpath:db/migration/emr`

| 版本 | 文件 | 内容 |
|------|------|------|
| V1 | `V1__emr_ddl.sql` | 6 张表 DDL |
| V2 | `V2__seed_platform.sql` | 平台类型 101–104、元数据 001–012、病程模板 203/204 |
| V3 | `V3__seed_notice_forms.sql` | NOTICE 类型、元数据 013–051、告知模板 206–215 |
| V4 | `V4__seed_outpatient_admission_forms.sql` | 门诊/入院元数据与模板 201/205 |
| V5 | `V5__seed_sample_inpatient_forms.sql` | 住院样例增量类型/元数据/模板（单院徽终态） |
| V6 | `V6__update_outpatient_emr_from_customer_word.sql` | 按客户 Word 更新 OP_RECORD_STD（页眉顺序、印象诊断、仅医生签字、戒烟页脚） |
| V7 | `V7__align_outpatient_emr_word_layout.sql` | 版式贴近 Word：页眉/签字前横线、签字两行右齐 |
| V8 | `V8__align_admission_record_sample.sql` | 入院样例记录 A4：续页提示、页眉横线、诊断缩进、签字三行 |
| V9 | `V9__fix_admission_empty_text_nodes.sql` | 去掉空 text 节点，避免 TipTap/ProseMirror 拒收导致编辑器空白 |
| V10 | `V10__surgery_handover_interactive_checkboxes.sql` | 手术交接单：□ 改为可点选行内复选框 |

清库步骤：删除 `emr_*` 表与 `flyway_schema_history_emr` 后重启，或 `flyway repair` 不适用——须空 history 从 V1 重跑。

**勿**在已跑到旧 V21 的库上直接替换本目录（validate 会失败）。
