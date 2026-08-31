> 文档真源：**[whale-doc/health/PeisAppService/06-Flyway-表结构.md](../../../../../../../../../whale-doc/health/PeisAppService/06-Flyway-表结构.md)**  
> 链式规范：**[whale-doc/health/db/migration/README.md](../../../../../../../../../whale-doc/health/db/migration/README.md)**

| 项 | 值 |
|----|-----|
| locations | `classpath:db/migration/peis` |
| history | `flyway_schema_history_peis` |
| 开关 | `openhis.modules.peis.enabled` |
| 范围 | 仅 `peis_*`；`cln_*` / AOL 在 health 核心 |
| 版本 | **V1** DDL 最终态 + **V2** 种子最终态（原增量已合入；**禁止**再加 V3+ 增补） |

> 存量库若 history 仍为旧 V1–V13：需 **删库重建**，或手工对齐列/种子后 repair；勿再新增增补脚本。
