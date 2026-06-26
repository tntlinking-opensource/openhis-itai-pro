# whale-report

Whale 打印与报表扩展包：B 类单证排版/预览、C 类查询报表、D 类外系统报告查看。

| 类型 | 说明 | 本模块 |
|------|------|--------|
| A 类 | 病历文书 | 见 `whale-emr` |
| B 类 | 打印单证（处方、小票、执行单等） | 设计器 + OpenPDF 渲染 + `PrintDataPort` |
| C 类 | 表格查询报表（日结、库房、财务等） | `ReportLoader` + `ReportTabularDataPort` |
| D 类 | 外系统报告（LIS/PACS 等） | `ReportExternalDocumentPort` |

病案首页打印见 **`whale-mrms`**，不在本包。

---

## Maven 子模块

| 模块 | 职责 |
|------|------|
| `report-contracts` | Port 契约（`PrintDataPort`、`ReportTabularDataPort`、`ReportExternalDocumentPort`） |
| `report-domain` | 模板/元素/绑定/打印日志实体、仓储、领域服务 |
| `report-application` | AppService、ReportLoader、Flyway、布局模型、OpenPDF 渲染 |
| `report-adapter` | HIS 侧 Port 实现（`com.openhis.whale.report.adapter`） |
| `report-starter` | 安装入口（聚合 `report-application` + `report-adapter`） |

依赖方向：`application` → `domain` → `contracts`；`adapter` → `contracts` + `health-domain`（**禁止**依赖 `report-application`）。

---

## 装配

- 开关：`openhis.modules.report.enabled=true`
- Application：`ReportAutoConfiguration` → 扫描 `com.openhis.whale.report.application`
- Adapter：`ReportAdapterAutoConfiguration` → 扫描 `com.openhis.whale.report.adapter`（条件：`ReportTabularDataPort` 在 classpath）

宿主工程引入 **`report-starter`** 即可同时拉起 application 与 adapter。

---

## 应用层：一页面一 AppService

`report-application` 按 UI 页面对应用例包，每个 AppService 只负责该页面的 API orchestration，不承载跨页业务。

| UI 页面 | AppService | API 前缀 |
|---------|------------|----------|
| 排版设计器 | `ReportDesignAppService` | `POST /api/app/reportDesign/*` |
| 模板管理 | `ReportTemplateAppService` | `POST /api/app/reportTemplate/*` |
| 院点打印绑定 | `ReportPrintBindingAppService` | `POST /api/app/reportPrintBinding/*` |
| C 类报表查询 / 设计 | `ReportAppService` | `POST /api/app/report/*` |
| 打印日志 | `ReportPrintLogAppService` | `POST /api/app/reportPrintLog/*` |
| D 类外系统报告 | `ReportExternalViewAppService` | `POST /api/app/reportExternalView/*` |

共享能力（非页面入口）：`PrintLayoutRenderService`（PDF）、`PreviewDatasetResolver`（预览数据集）、`ReportLoaderRegistry`（C 类 Loader 注册表）。

### 设计器主要接口

| 方法 | 路径 |
|------|------|
| 元素包 | `reportDesign/getElementPack` |
| 布局校验 | `reportDesign/validateLayout` |
| 模板列表（兼容） | `reportDesign/listTemplates` |
| PDF 预览 | `reportDesign/preview` |

---

## B 类：打印单证与预览

**packCode** 常量见 `ReportPackConstants`（如 `outpatient_prescription`、`charge_receipt`）。

### 预览数据流

```
ReportDesignAppService.preview()
  → PreviewDatasetResolver.resolve(packCode, request)
      → useSampleData / 实参不足 → PreviewSampleRegistry（preview/sample/*）
      → 实参齐全 → PrintDataPort.load(packCode, params)
          → PrintDataPortAdapter（按 packCode 路由）
              → *PrintDatasetService.build()
  → PrintLayoutRenderService.render() → Base64 PDF
```

### Port 与 Adapter

- 契约：`PrintDataPort.load(packCode, params)` → `Map` 根数据集
- 实现：`PrintDataPortAdapter` 一个类路由全部 B 类 pack（与 C 类 `ReportTabularDataPortAdapter` 对称）
- Dataset：`report-adapter/.../print/dataset/*PrintDatasetService`（28 种单证）

---

### C 类主要接口

| 方法 | 路径 | 说明 |
|------|------|------|
| 报表列表 | `report/listDefinitions` | 可选 `status`、`keyword` |
| 报表详情 | `report/getDefinition` | 设计器加载 |
| 新建/改/删 | `createDefinition` / `updateDefinition` / `deleteDefinition` | 报表模板 CRUD（SQL 报表） |
| 保存设计 | `report/saveReportDesign` | SQL、表头/表底/筛选器 JSON |
| 发布 / 撤回 | `report/publishDefinition` / `unpublishDefinition` | draft ↔ published |
| 分页查询 | `report/queryPage` | Loader 或 SQL；未发布拒绝（`designPreview=true` 除外） |
| 导出 | `report/exportExcel` | CSV Base64 |

---

## C 类：查询报表（重新设计）

三层菜单（health **V79**）：

| 菜单 | 页面 | 职责 |
|------|------|------|
| **报表模板** | `views/report/template` | CRUD（类比打印模板列表）；新建 SQL 报表 |
| **报表设计器** | `views/report/designer?id=` | 编辑 **SQL / 表头 / 表底 / 筛选器 / 列**；发布 |
| **报表中心** | `views/report/center` | 已发布报表：动态筛选、查询、Excel/PDF、打印 |

**数据来源**（`rpt_definition.source_type`）：

- `loader` — 出厂 Java `ReportLoader`（兼容现有 PH/FIN 等）
- `sql` — 设计器维护的只读 `SELECT`（`:dateFrom`、`:dateTo`、`:keyword` 等命名参数）

Flyway：**V19**（SQL/表头/表底字段）、**V18**（status）。

### 数据流

```
ReportAppService.queryPage()
  → ReportLoaderRegistry → *ReportLoader
      → ReportLoaderDataSupport.applyPortDataIfPresent()
          → ReportTabularDataPort.load(ReportCode, params)
              → ReportTabularDataPortAdapter
                  → *ReportDatasetService.build()
      → 无 Port 数据时 Loader 内置样例兜底
```

Loader 按领域分子包：`report/loader/{settlement,financial,warehouse,clinical}/`。

---

## D 类：外系统报告

| 方法 | 路径 |
|------|------|
| 来源列表 | `reportExternalView/listSources` |
| 解析文档 | `reportExternalView/resolve` |

出厂占位：`ExternalReportDocumentPortAdapter`（院点可替换为 LIS/PACS 实现）。

---

## 包结构

### report-contracts

| 子包 | 内容 |
|------|------|
| `contracts/port/` | `ReportTabularDataPort`、`ReportExternalDocumentPort` |
| `contracts/constants/` | 跨层通用技术契约（如 `ReportDatasetLimits` 行数上限） |
| `contracts/catalog/supply/` | `SupplyDocumentReportCatalog` — C 类供应单据 ReportCode 目录 |

### report-domain

根包 `com.openhis.whale.report.domain`：

| 子包 | 内容 |
|------|------|
| `entity/` | `ReportTemplate`、`ReportDefinition`、`PrintElementDef`、`PrintBinding`、`PrintEventLog` |
| `repository/` | JPA 仓储 + `PrintEventLogSummaryDao` |
| `service/` | `ReportTemplateManager`、`ReportDefinitionManager`、`PrintElementDefManager`、`PrintBindingManager`、`PrintEventLogManager` |
| `binding/` | 绑定目录与定义 |
| `constants/` | 领域专用业务常量：`ReportLoaderCodes`、`ReportPdfPrintCodes`、`ReportDefinitionStatus` 等 |

### report-application

根包 `com.openhis.whale.report.application`：

| 子包 | 内容 |
|------|------|
| `design/{service,request,response}` | 设计器用例 |
| `template/{service,request,response}` | 模板管理 |
| `binding/{service,request,response}` | 院点绑定 |
| `report/{service,request,response,support,loader/*}` | C 类报表（含 `ReportDefinitionCatalogService`） |
| `printlog/{service,request,response}` | 打印日志 |
| `external/{service,request,response}` | D 类外系统 |
| `preview/` | `PreviewDatasetResolver`、`PreviewSampleRegistry`、`preview/sample/*` |
| `layout/{model,support}` | 布局 JSON 模型与校验/变换 |
| `pdf/` | OpenPDF 渲染（element、overlay 等） |
| `support/` | 跨用例小工具 |
| `config/` | `ReportAutoConfiguration` |

### report-adapter

根包 `com.openhis.whale.report.adapter`（HIS 桥接，不按门诊/住院再拆）：

| 子包 | 内容 |
|------|------|
| `support/` | `ReportDatasetParamSupport`、`ReportDatasetTextSupport` |
| `print/dataset/` | B 类 `*PrintDatasetService` |
| `print/port/` | `PrintDataPortAdapter` |
| `dataset/` | C 类 `*ReportDatasetService` |
| `port/` | `ReportTabularDataPortAdapter`、`ExternalReportDocumentPortAdapter` |

配置类在 `com.openhis.whale.report.adapter.config`。

---

## 代码阅读指引

各包根目录有 `package-info.java`，核心类有 JavaDoc。建议按场景阅读：

| 场景 | 入口类 | 说明 |
|------|--------|------|
| C 类分页查询 | `ReportAppService` → `ReportQueryExecutionService` | SQL / Loader 双轨；列 override 在 execution 层 merge |
| C 类 SQL | `ReportQueryService` → `ReportQueryExecutor` | 只读 SELECT + 命名参数；`ReportQueryValidator` 白名单 |
| C 类 Loader | `ReportLoaderRegistry` → `*ReportLoader` | 按 ReportCode；内部调 `ReportTabularDataPort` |
| 列定制 | `ReportColumnOverrideManager` | 用户 > 机构 > 默认；`ReportColumnMergeSupport` 转 DTO |
| B 类预览/打印 | `ReportDesignAppService` → `PrintLayoutRenderService` | layoutJson + dataset → PDF 或 ESC/POS RAW |
| 元素渲染 | `PrintElementLayoutRenderer` → `PrintElementRendererRegistry` | renderType 分发；table 处方固定行见 `PrescriptionDrugTableSupport` |
| roll RAW | `LayoutJsonEscPosRenderer` → `EscPosElementRenderer` | `openhis.modules.report.roll-output=raw` |
| Port 路由 | `PrintDataPortAdapter` / `ReportTabularDataPortAdapter` | adapter 按 packCode / ReportCode 进 dataset 包 |

租户合并（definition/template/element）：`ReportDefinitionManager`、`ReportTemplateManager`、`PrintElementDefManager` 均 platform(0) + 院点覆盖。

**出厂预制清单与维护分工**：见 [whale-doc/report/10-出厂预制与维护说明.md](../../whale-doc/report/10-出厂预制与维护说明.md)。

---

## Flyway

路径：`report-application/src/main/resources/db/migration/report/`

| 版本 | 说明 |
|------|------|
| V1 | 表结构（含 `BaseAuditedEntity` 审计列） |
| V2 | B 类打印种子（元素目录、模板、绑定） |
| V3 | C 类 Loader 报表定义种子 |
| V4 | 08-常用报表清单 SQL 类预制（draft） |
| V5 | 种子后处理（表头 lines、默认租户复制、状态兜底） |

清库重跑：按 V1 → V5 顺序执行；勿保留旧版 `V20`/`V21` 独立脚本（已合并/删除）。

---

## 本地编译

```bash
cd whale-sever
mvn -pl whale-report/report-application,whale-report/report-adapter,whale-report/report-contracts,whale-report/report-domain -am compile -DskipTests
```

或编译完整 starter：

```bash
mvn -pl whale-report/report-starter -am compile -DskipTests
```

---

## 工具脚本

`tools/` 目录为一次性迁移/维护脚本（非运行时依赖），例如 `restructure-packages.cjs`、`generate-v5-v6-seed.mjs`。

---

## 设计文档

完整设计见 **`whale-doc/report/`**（入口 [README.md](../../whale-doc/report/README.md)）。

| 文档 | 用途 |
|------|------|
| [09-场景维护指南](../../whale-doc/report/09-场景维护指南.md) | 日常维护：B/C/D 按场景改哪里 |
| [03-详细设计与编码指导](../../whale-doc/report/03-详细设计与编码指导.md) | 任务分解、文件路径、验收 |
| [07-开发人员指南](../../whale-doc/report/07-开发人员指南.md) | onboarding、表设计 |
