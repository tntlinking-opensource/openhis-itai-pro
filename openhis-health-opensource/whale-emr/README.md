# whale-emr

Whale 电子病历（EMR）扩展包：TipTap 文书编辑、元数据解析、OpenPDF 渲染。

| 类型 | 说明 | 本模块 |
|------|------|--------|
| A 病历 | 入院记录、病程、文书树 | whale-emr |
| B 单证 | 处方、小票等 | PrintTemplate（AS-BAS-PRT） |
| C 查询 | 统计报表 | health AppService（范围外，不保留 SqlReport） |

打印单证见 **PrintTemplate（AS-BAS-PRT）**；病案首页见 **health 内嵌 MRMS**（`MrmsAppService`）。

---

## Maven 子模块

| 模块 | 职责 |
|------|------|
| `emr-contracts` | Port 契约（`EmrMetadataContextPort`） |
| `emr-domain` | 文书/模板/元数据实体、仓储、领域服务 |
| `emr-application` | AppService、Flyway、TipTap → OpenPDF 渲染 |
| `emr-adapter` | HIS 侧 Port / Extension 实现（`com.openhis.health.integration.emr`） |
| `emr-starter` | 安装入口（聚合 `emr-application` + `emr-adapter`） |

依赖方向：`application` → `domain` → `contracts`；`adapter` → `contracts` + `health-domain`（**禁止**依赖 `emr-application`）。

---

## 装配

- 开关：`openhis.modules.emr.enabled=true`
- Application：`EmrAutoConfiguration` → 扫描 `com.openhis.whale.emr.application`
- Adapter：`EmrAdapterAutoConfiguration` → 扫描 `com.openhis.health.integration.emr`（条件：`EmrMetadataContextPort` 在 classpath）

宿主工程引入 **`emr-starter`** 即可同时拉起 application 与 adapter。

---

## 应用层：一页面一 AppService

| UI 页面 | AppService | API 前缀 |
|---------|------------|----------|
| 文书编辑 | `DocumentAppService` | `POST /api/app/document/*` |
| 文书类型 | `DocumentTypeAppService` | `POST /api/app/documentType/*` |
| 模板管理 | `TemplateAppService` | `POST /api/app/template/*` |
| 元数据字段 | `MetadataFieldAppService` | `POST /api/app/metadataField/*` |
| 就诊元数据 | `EncounterMetadataAppService` | `POST /api/app/encounterMetadata/*` |

共享能力：`metadata/support`（元数据解析）、`pdf/`（OpenPDF 渲染）、`support/`（编辑器图片等）。

### 元数据 Port 数据流

```
EncounterMetadataAppService / DocumentAppService
  → EmrMetadataContextBuilder.build()
      → EmrMetadataContextPort（adapter 或 NoOp）
          → EmrMetadataContextPortAdapter（患者/就诊/诊断）
  → MetadataValueResolver.resolveFromPath()
```

---

## Port 与 Adapter

- 契约：`com.openhis.whale.emr.domain.port.metadata.EmrMetadataContextPort`
- 实现：`EmrMetadataContextPortAdapter`（`emr-adapter/.../metadata/port/`）
- 无 adapter 时装配：`NoOpEmrMetadataContextPort`（application 层 fallback）

---

## 包结构

### emr-contracts

根包 `com.openhis.whale.emr.domain.port`：

| 子包 | 接口 |
|------|------|
| `metadata/` | `EmrMetadataContextPort` |

### emr-domain

根包 `com.openhis.whale.emr.domain`：`entity/`、`repository/`、`service/`、`metadata/`、`enums/`、`model/`

### emr-application

根包 `com.openhis.whale.emr.application`：

| 子包 | 内容 |
|------|------|
| `document/{service,request,response}` | 文书 CRUD、PDF、版本 |
| `documentType/{service,request,response}` | 文书类型 |
| `template/{service,request,response}` | 模板 |
| `metadataField/{service,request,response}` | 元数据字段维护 |
| `encounterMetadata/{service,request,response}` | 就诊元数据解析 |
| `metadata/support/` | Builder、Resolver、Masker、NoOp Port |
| `pdf/` | OpenPDF 渲染 |
| `support/` | 跨用例工具 |
| `config/`、`module/` | 自动配置与模块描述符 |

### emr-adapter

| 子包 | 内容 |
|------|------|
| `com.openhis.health.integration.emr.metadata/` | 诊断贡献器等 |
| `com.openhis.health.integration.emr.metadata.port/` | `EmrMetadataContextPortAdapter` |
| `com.openhis.whale.emr.adapter.config/` | `EmrAdapterAutoConfiguration` |
| `com.openhis.whale.emr.adapter.module/` | `EmrAdapterModuleDescriptor` |

---

## Flyway

路径：`emr-application/src/main/resources/db/migration/emr/`（V1～V6）

---

## 本地编译

```bash
cd whale-sever
mvn -Pwith-emr -pl whale-emr/emr-application,whale-emr/emr-adapter,whale-emr/emr-contracts,whale-emr/emr-domain -am compile -DskipTests
```

或完整 starter：

```bash
mvn -Pwith-emr -pl whale-emr/emr-starter -am compile -DskipTests
```

---

## 工具脚本

`tools/restructure-packages.cjs` — 包结构迁移（一次性维护脚本，非运行时依赖）。
