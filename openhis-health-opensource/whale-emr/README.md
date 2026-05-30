# Whale EMR

Whale EMR（电子病历）是基于 Whale 框架的**可插拔电子病历引擎**，可以：

- 作为 `whale-health` 的扩展包被引用（通过 Maven 依赖）；
- 也可以作为独立后端模块打包售卖（集成到其他基于 Whale 的业务系统中）。

## 模块结构

```text
whale-emr/
├── pom.xml                 # 聚合 POM，统一版本与模块管理
├── emr-domain/             # 领域层：实体、仓储接口等
│   └── src/main/java/com/openhis/whale/emr/domain
│       └── entity/         # Document、Template、DocumentType 等实体
├── emr-application/        # 应用层：AppService、DTO、Request、Mapper 等
│   └── src/main/java/com/openhis/whale/emr/application
└── emr-infrastructure/     # 基础设施：数据库迁移脚本等
    └── src/main/resources/db/migration/emr
        └── V13__Emr_Init.sql   # EMR 初始化 DDL（独立部署时使用）
```

> 说明：`whale-health` 中已有一份针对健康项目的 EMR DDL（`V6__H5_Emr_Document.sql` 等），
> `whale-emr/emr-infrastructure` 下的脚本用于 **whale-emr 独立构建/交付场景**，二者可按部署方式择一使用或合并。

## 主要能力（对应 EMR 需求文档）

- **文书管理（Document）**：电子病历文书的创建、打开、保存、状态管理、版本与留痕。
- **模板管理（Template）**：
  - 支持结构化模板（区域/数据组/数据元）；
  - 可配置「适用科室（organization_id）」和「适用就诊类型/子类型（encounter_class_enum / encounter_type_enum）」控制模板可见性；
  - 模板启用/停用、与文书类型的关联。
- **文书类型（DocumentType）**：配置入院记录、病程记录、出院小结等类型，以及默认模板。
- **版本与留痕（DocumentVersion）**：保存版本快照，支持查看历史版本和回滚。
- **数据项名索引（DocumentDataItemIndex）**：可选的结构化索引表，支持按数据项名检索与统计。

更多业务细节见 `docs/EMR-电子病历-需求文档.md`。

## 构建与引用方式

### 1. 在本仓库内构建 whale-emr

```bash
cd D:\0.product\openhis-314\server\whale-emr
mvn clean install -DskipTests
```

这会在本地 Maven 仓库中安装：

- `com.openhis:emr-domain:2.0.0-SNAPSHOT`
- `com.openhis:emr-application:2.0.0-SNAPSHOT`
- `com.openhis:emr-infrastructure:2.0.0-SNAPSHOT`

### 2. 在 whale-health 中引用

`whale-health/pom.xml` 已预置：

```xml
<properties>
    <!-- Whale EMR 版本（引用 whale-emr/src/backend 时使用，需先在该目录 mvn install） -->
    <whale.emr.version>2.0.0-SNAPSHOT</whale.emr.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Whale EMR（电子病历） -->
        <dependency>
            <groupId>com.openhis</groupId>
            <artifactId>emr-application</artifactId>
            <version>${whale.emr.version}</version>
        </dependency>
        <dependency>
            <groupId>com.openhis</groupId>
            <artifactId>emr-domain</artifactId>
            <version>${whale.emr.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

在具体模块（如 `health-application`）中直接声明依赖即可使用 EMR 的 AppService/实体。

### 3. 作为独立后端模块集成

若在其他基于 Whale 的项目中单独集成 EMR：

1. 在该项目的 POM 中引入：

```xml
<dependency>
    <groupId>com.openhis</groupId>
    <artifactId>emr-application</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

2. 迁移脚本：
   - 可以直接使用 `emr-infrastructure/src/main/resources/db/migration/emr/V13__Emr_Init.sql`；
   - 或将其中的 DDL 合并到项目自己的 Flyway 路径中。

## 开发提示

- **领域层**：遵循 Whale 框架开发指南，实体继承 `BaseAuditedEntity`，支持多租户（`IMultiTenant`）。
- **应用层**：AppService 通过 Whale Web 自动暴露为 `POST /api/app/{serviceName}/{methodName}`。
- **脚本版本号**：当前 EMR 初始化脚本使用 `V13__Emr_Init.sql`，如在目标项目中已有 V13，请根据实际情况调整为更高版本号。

