# PeisAppService · Flyway 表结构真源

> **版本**：v1.13 · **日期**：2026-08-20  
> **模块**：`zxplat/zxplat-domain` · ModularFlyway **`db/migration/peis/`**  
> **history**：`flyway_schema_history_peis` · **开关**：`zxplat.modules.peis.enabled`  
>
> | 版本 | 文件 | 说明 |
> |------|------|------|
> | **peis V1** | [V1__peis_ddl.sql](../../../zxplat/zxplat/zxplat-domain/src/main/resources/db/migration/peis/V1__peis_ddl.sql) | 全部 `peis_*` 表 **最终态**（含团检/档案/复查；`referrer_name` / `breakfast_flag` / `tag_code`） |
> | **peis V2** | [V2__seed_platform_menus.sql](../../../zxplat/zxplat/zxplat-domain/src/main/resources/db/migration/peis/V2__seed_platform_menus.sql) | 菜单/权限/术语/打印模板 **最终态**（含体检登记改名、指引单 `peis-guide-print`） |
>
> **禁止** peis V3+ 增补脚本；新变更改 V1/V2（清库重建场景）或另定迁移策略。  
> **CLN / AOL** 不在 peis 模块：见 health 核心 `V3__cln_ddl.sql`、`V7__ter_cus_ddl.sql`。  
> **链式规范**：[health/db/migration/README.md](../db/migration/README.md)  
> **成码规则**：[`whale-peis-codegen.mdc`](../../../../.cursor/rules/whale-peis-codegen.mdc) · [`PEIS-LIS-PACS文档口径`](../../../../.cursor/skills/PEIS-LIS-PACS文档口径/SKILL.md)

---

## 一、CLN（clinical 包，非 peis 专属 · health 核心）

| 表 | DM | Flyway |
|----|-----|--------|
| `cln_observation_definition` | [DM-CLN-005](./DM-CLN-005-ObservationDefinition-临床观察定义.md) | health **V3** |
| `cln_observation` | [DM-CLN-007](./DM-CLN-007-Observation-临床观察实例.md) | health **V3** |
| `cln_diagnostic_report_index` | [DM-CLN-006](./DM-CLN-006-DiagnosticReportIndex-报告读侧索引.md) | health **V3** |
| `cln_diagnostic_report` | [DM-CLN-008](./DM-CLN-008-DiagnosticReport-临床总检报告.md) | health **V3** |

---

## 二、PEIS 扩展表（peis 模块）

| 表 | DM | P | Flyway |
|----|-----|---|--------|
| `peis_service_request_ext` | [DM-PEIS-001](./DM-PEIS-001-PeisServiceRequestExt-项目执行扩展.md) | P0 | **peis V1** |
| `peis_encounter_ext` | [DM-PEIS-007](./DM-PEIS-007-PeisEncounterExt-就诊扩展.md) | P0 | **peis V1** |
| `peis_common_result` | [DM-PEIS-003](./DM-PEIS-003-PeisCommonResult-常见结果.md) | P0 | **peis V1** |
| `peis_config` | [DM-PEIS-004](./DM-PEIS-004-PeisConfig-模块参数.md) | P0 | **peis V1** |
| `peis_report_grant_ext` | [DM-PEIS-005](./DM-PEIS-005-PeisReportGrantExt-报告发放扩展.md) | P1 | **peis V1** |
| `peis_auto_express` | [DM-PEIS-006](./DM-PEIS-006-PeisAutoExpress-自动诊断表达式.md) | P1 | **peis V1** |
| `peis_recheck_task` | Peis 04 §4.5 复查 Task（方案 A 本地语义） | P1 | **peis V1** |
| `peis_group_company` / `peis_group_contract` / `peis_group_batch` / `peis_group_roster` | 团检名册 | P1 | **peis V1** |
| `peis_patient_health_archive` | 学生健康档案 | P1 | **peis V1** |

| 表 | DM | P | Flyway |
|----|-----|---|--------|
| `cus_activity_observation_link` | [DM-CUS-013](./DM-CUS-013-ActivityObservationLink-项目小项绑定.md) | P0 | health **V7**（CUS，非 peis 模块） |

**QueryService（无 Flyway 表）**：[DM-PEIS-008 PeisEncounterProgressQuery](./DM-PEIS-008-PeisEncounterProgressQuery-体检进度查询.md)

---

## 三、ADM 扩展

| 变更 | DM |
|------|-----|
| `EncounterClass.PE` | [DM-ADM-002 §4.1](./DM-ADM-002-Encounter-就诊.md#41-就诊类别encounterclass) |

---

## 四、模块注册

```json
{
  "moduleId": "peis",
  "enabledProperty": "zxplat.modules.peis.enabled",
  "locations": "classpath:db/migration/peis",
  "historyTable": "flyway_schema_history_peis"
}
```

落点：`zxplat-domain/src/main/resources/META-INF/zxplat/flyway-module.json`。

**已从核心迁出**：原 squash `health/V8__peis_ddl.sql` → **peis V1**；核心版本为 V1–V7、V9、V10（中间无 V8）。

---

## 五、修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.13 | 2026-08-20 | **仅 V1+V2 最终态**：删增补；列/指引单合入 V1/V2 |
| v1.12 | 2026-08-20 | （已回滚）曾恢复旧链 V14 |
| v1.11 | 2026-08-13 | peis squash V1–V2 |
| v1.10 | 2026-07-25 | **peis V11** `prt_template` 体检总检报告打印模板（`peis-report-print`） |
| v1.9 | 2026-07-25 | **peis V10** `peis_recheck_task`；报告打印/发放/复查菜单；补录 V6/V7 团检/档案表索引 |
| v1.8 | 2026-07-19 | **ModularFlyway**：`peis_*` 独立目录/history；CLN/AOL 仍 health 核心 |
| v1.7 | 2026-06-27 | 取消 `PeisAppService/flyway/` 副本；DDL 唯一落库 zxplat-domain；`cln_diagnostic_report **V77→V79** |
| v1.6 | 2026-06-27 | AOL 链 **DM-CUS-013** |
| v1.4 | 2026-06-27 | DM-CLN-008 重命名 DiagnosticReport |
| v1.3 | 2026-06-27 | V77 `cln_diagnostic_report（后改 V79） |
| v1.2 | 2026-06-27 | V76 P1 grant + auto_express |
| v1.1 | 2026-06-27 | P0 DDL V75 |
| v1.0 | 2026-06-27 | 首版 Flyway 索引 |
