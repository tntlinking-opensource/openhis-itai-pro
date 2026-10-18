> 文档真源：**[whale-doc/health/PeisAppService/06-Flyway-表结构.md](../../../../../../../../../whale-doc/health/PeisAppService/06-Flyway-表结构.md)**  
> 链式规范：**[whale-doc/health/db/migration/README.md](../../../../../../../../../whale-doc/health/db/migration/README.md)**

| 项 | 值 |
|----|-----|
| locations | `classpath:db/migration/peis` |
| history | `flyway_schema_history_peis` |
| 开关 | `zxplat.modules.peis.enabled` |
| 范围 | 仅 `peis_*`；`cln_*` / AOL 在 health 核心 |
| 版本 | **仅 V1 DDL + V2 种子最终态**（2026-09-15 合入原 V3–V6 后删除） |

> **禁止** peis V3+ 增补；新变更改 V1/V2（清库重建）或另定迁移策略。  
> 已跑过旧 V3–V6 的库须 **删库重建**（或对应 history repair）。
