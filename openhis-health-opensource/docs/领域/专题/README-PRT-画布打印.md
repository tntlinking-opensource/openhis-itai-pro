# Print 画布打印 · 领域模型索引

> **限界上下文**：本地扩展 · 画布打印（Print）  
> **代码模块**：`zxplat/zxplat`（`zxplat-domain` + `health-application` + `zxplat-domain`）  
> **产品定位**：HIS **内置能力**（非增值包）

| 编码 | 聚合 | 文档 | 物理表 | 状态 |
| --- | --- | --- | --- | --- |
| DM-PRT-001 | **PrintTemplate** | [画布打印模板](./DM-PRT-001-PrintTemplate-画布打印模板.md) | `prt_template` | **已实现** |
| DM-PRT-002 | **PrintLog** | [打印审计日志](./DM-PRT-002-PrintLog-打印审计日志.md) | `prt_print_event_log` | **已实现** |
| DM-PRT-003 | **PrintBinding** | [业务场景模板绑定](./DM-PRT-003-PrintBinding-业务场景模板绑定.md) | `prt_print_binding` | **已实现**（管理页；运行时主入口为 `scenario_code`） |

**Flyway 真源**：

| 脚本 | 分工 |
| --- | --- |
| `zxplat-domain/.../db/migration/health/V9__mrms_prt_ddl.sql` | **建表**（`prt_template` / `prt_print_event_log` / `prt_print_binding`） |
| `zxplat-domain/.../db/migration/health/V10__seed_platform.sql` | **出厂 `prt_template` 完整 JSON**（院点与 health-app 均执行；含门诊/住院/库房/调价/药房日结等） |
| `health-app/.../db/migration/health-dev/V24__seed_dev_prt_templates.sql` | **空壳**（模板已并入 V10；保留版本号避免 Flyway 空洞） |

手工/文档侧对齐种子（可重复执行 upsert，**勿删**）：`health/print/seed_*.sql`；同步脚本 `sync-*-seed-to-v10.mjs`。

**业务键约定**：

| 概念 | 库列 / API | 用途 |
| --- | --- | --- |
| **`busNo`** | `bus_no` | 模板业务键；新建时后端按日序号生成（`PTyyyyMMddNNNN`）；设计器/列表 CRUD 主键 |
| **`scenarioCode`** | `scenario_code` | **运行时主入口**；业务页写死（如 `op-rx-print`）；`preparePrint` 按场景解析 published |
| **`bindingCode`** | `binding_code` | PrintBinding 管理页映射键（legacy seed）；`preparePrint` 请求中作 `scenarioCode` **别名**（不查 binding 表） |

**出厂场景码（`prt_template.scenario_code`）**：

| scenarioCode | 模板名 |
| --- | --- |
| `op-rx-print` | 门诊处方 |
| `op-charge-print` | 门诊收费小票 |
| `op-reg-print` | 门诊挂号小票 |
| `op-treatment-print` | 门诊处置单 |
| `op-infusionLabel-print` | 输液瓶签 |
| `op-dailySettlement-print` | 门诊费用日结单 |
| `fin-price-adjustment-print` | 调价单（A4；platform **V10**；文档种子见 `health/print/seed_fin_price_adjustment_prt_a4.sql`） |
| `pacs-registration-slip-print` | 检查登记条（health **V14**；`PacsPrintScenarioConstants`） |

**HTTP 暴露**：ZXPlat `AppServiceControllerRegistrar`，一律 `POST /api/app/{serviceName}/{methodName}`（**camelCase**，如 `printJob/preparePrint`）。

**SDD 决策摘要（2026-06-22，写 SDD 前已定稿）**：

- 模板：方案 A；publish 插行 + archived；draft **软删**；列表默认筛掉 archived（可切历史）
- 日志：traceId；**trace_id 非 UK**（允许多次 confirm）；org_id 会话院点；JSONB 实体 **String**
- 绑定：租户 only；无 override **fallback** 平台 seed；prepare 权威模板来自 **scenarioCode → published** 或直传 **busNo**
- **设计器**：Web **vue-plugin-hiprint**（`print-template/designer/index.vue`）；**不做** OpenHIS.Win exe 设计器
- 权限：菜单 print:*；**本期 SDD 不写 @RequirePermission**
- 异常：PRT-4xx + ValidationException；事务：AppService 不声明 @Transactional
- SDD 路径：`health/DomainModel-SDD`、`health/AppService-SDD`

**交互 / 设计页**：`AS-BAS-MST-019-打印设计.md`（Web hiprint 设计器 · IF-11～17）

**应用服务（AS）**：

| AS | serviceName | 场景 |
| --- | --- | --- |
| [AS-BAS-PRT-001](../AppService/AS-BAS-PRT-001-打印任务运行时.md) | `printJob` | **运行时** preparePrint / confirmPrint |
| [AS-BAS-MST-018](../AppService/AS-BAS-MST-018-打印日志查看.md) | `printLog` | 打印日志查看 |
| [AS-BAS-MST-019](../AppService/AS-BAS-MST-019-打印设计.md) | `printTemplate` / `printBinding` | 打印设计（模板 + PrintBinding） |

**详细设计（SDD）**：

| SDD | 对应 |
| --- | --- |
| [DM-PRT-001-SDD](../DomainModel-SDD/DM-PRT-001-PrintTemplate-画布打印模板-SDD.md) | PrintTemplate 领域 |
| [DM-PRT-002-SDD](../DomainModel-SDD/DM-PRT-002-PrintLog-打印审计日志-SDD.md) | PrintLog 领域 |
| [DM-PRT-003-SDD](../DomainModel-SDD/DM-PRT-003-PrintBinding-业务场景模板绑定-SDD.md) | PrintBinding 领域 |
| [AS-BAS-PRT-001-SDD](../AppService-SDD/AS-BAS-PRT-001-打印任务运行时-SDD.md) | 运行时 Job |
| [AS-BAS-MST-019-SDD](../AppService-SDD/AS-BAS-MST-019-打印设计-SDD.md) | 模板 + 绑定 |
| [AS-BAS-MST-018-SDD](../AppService-SDD/AS-BAS-MST-018-打印日志查看-SDD.md) | 日志只读 |

> **实现状态（2026-06-28）**：后端三聚合 + 四 AppService 已落地；前端 `api/print/`（template + job）、`hiprint/`、`utils/print/` 运行时已落地；**打印日志页 / 绑定配置页**前端 API 待补。
