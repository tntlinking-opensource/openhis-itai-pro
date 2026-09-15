# OpenHIS（Whale）

基于 **Whale Framework** 的医院信息系统后端（[LGPL-3.0-or-later](LICENSE)）。本仓库为完整 Java 源码 monorepo：`whale-framework`、`whale-health` 与全部 `whale-module-*` 扩展包。

> **文档**：设计/架构类 Markdown 在 **[whale-doc](../whale-doc/)**；入口 **[文档地图](../whale-doc/文档地图.md)**。  
> 扩展 Module：**[扩展模块架构统一约定](../whale-doc/01-架构/扩展模块架构统一约定.md)** · **[新扩展包开发指南](../whale-doc/01-架构/新扩展包开发指南.md)**

当前版本：**2.0.7**（根 `pom.xml`）。

---

## 技术栈

| 项 | 说明 |
|----|------|
| Java | 17 |
| Spring Boot | 3.5.7 |
| 数据库 | PostgreSQL 16（建议） |
| ORM / 迁移 | JPA · Hibernate / Flyway |
| API 文档 | SpringDoc / Swagger UI |

---

## 项目结构

```text
whale-sever/
├── whale-framework/       # 框架源码（whale-core、identity、web 等）
├── whale-health/
│   ├── health-domain-shared/
│   ├── health-domain/         # 领域层（含内嵌病案 MRMS）
│   ├── health-application/
│   ├── health-infrastructure/
│   └── health-app/            # Spring Boot 启动模块
├── whale-module-emr/      # 电子病历（v3：bridge + starter）
├── whale-module-*/        # 可选扩展（chs、booking、crb 等，Profile 启用）
├── tools/copyright-prep/  # Spotless 版权头
└── pom.xml
```

---

## 环境要求

- JDK **17**
- Maven **3.9+**
- PostgreSQL **14+**（建议 16）

---

## 克隆与构建

在仓库根目录：

```bash
mvn clean package -pl whale-health/health-app -am -DskipTests
```

含 EMR 等扩展时追加 `-Pwith-emr`（其他扩展同理，如 `-Pwith-booking`）。

产物：

```text
whale-health/health-app/target/health-app-2.0.7.jar
```

### 修改 Framework 后

```bash
mvn clean install -f whale-framework/pom.xml -DskipTests
mvn compile -pl whale-health/health-app -am -DskipTests
```

### 只改业务代码时的快速编译

```bash
mvn compile -pl whale-health/health-app -am -DskipTests
```

---

## 本地启动

1. 创建 PostgreSQL 库，修改 `whale-health/health-app/src/main/resources/application.yml` 中的数据源（或用环境变量 `DB_USERNAME` / `DB_PASSWORD`）。
2. 首次启动会执行 Flyway 迁移（`classpath:db/migration/health`；扩展模块各自 `db/migration/{module}`）。

默认 **`server.port: 8081`**：

| 说明 | 地址 |
|------|------|
| 应用 | http://localhost:8081/ |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |

### IDEA

用 IDEA 打开仓库**根目录** `pom.xml`，等待 Maven 导入。仓库已提交共享配置：

| 已入库配置 | 作用 |
|------------|------|
| `.mvn/maven.config` | 扩展 with-* Profile 默认全关 |
| `.run/HealthApplication.run.xml` | HIS 核心启动（无扩展模块） |
| `.idea/compiler.xml` / `misc.xml` | 注解处理、JDK 17 对齐 |

运行主类：`com.openhis.health.HealthApplication`（模块 `health-app`）。

扩展模块 Profile（`with-emr` / `with-chs` 等）默认关闭，启用方式：Maven 工具窗口勾选 Profile，或命令行 `-Pwith-chs,with-emr`。

### 命令行

```bash
mvn spring-boot:run -pl whale-health/health-app -DskipTests
# 或
java -jar whale-health/health-app/target/health-app-2.0.7.jar
```

默认日志：`logs/whale-health.log`。

---

## 服务器发版

1. `mvn clean install -f whale-framework/pom.xml -DskipTests`（未改 framework 且本地已有对应版本可跳过）
2. `mvn clean package -pl whale-health/health-app -am -DskipTests`
3. 部署 `health-app-2.0.7.jar` 与生产配置
4. 启动示例：

```bash
java -Xms512m -Xmx2g -jar /opt/openhis/health-app.jar --spring.profiles.active=prod
```

生产建议用环境变量配置 `DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET` 等。

---

## 业务开发分层（推荐）

1. `health-domain-shared` — 枚举、常量  
2. `health-domain` — 实体、领域服务、仓储  
3. `health-application` — AppService、DTO  
4. `health-infrastructure` — 迁移脚本、外部适配  
5. `health-app` — 仅启动与配置  

Framework 常用能力：多租户 `IMultiTenant`、审计 `BaseAuditedEntity`、权限 `@RequirePermission`、分页 `PagedResult`。源码见 `whale-framework/`，文档见 [whale-doc/framework](../whale-doc/framework/)。

电子病历在 `whale-module-emr` 内按 domain / bridge / application 分层修改，经 `emr-starter`（`-Pwith-emr`）同进程加载。

---

## 代码格式（Spotless）

| 命令 | 用途 |
|------|------|
| `mvn spotless:apply` | 统一 Java 版权头、去未使用 import、行尾空格 |
| `mvn spotless:check` | 校验格式（不改文件） |

版权头模板：`tools/copyright-prep/java-copyright-header.txt`。

其它工具脚本见 [whale-doc/project/tools.md](../whale-doc/project/tools.md)。

---

## 版本

根 `pom.xml`（`artifactId: whale`）为全仓库 parent，当前 **`2.0.7`**。发版时改根 POM `<version>`，各模块经 parent 链继承；`whale.framework.version` 等属性默认 `${project.version}`。

---

## 常见问题

**找不到 whale-core / whale-web**  
在根目录用 `-pl whale-health/health-app -am` 构建；改 framework 后先 `mvn install -f whale-framework/pom.xml`。

**数据库 / Flyway 启动失败**  
检查 PostgreSQL 是否可达、库是否已创建，核对 `application.yml` 或环境变量。

**IDEA 模块未识别或依赖标红**  
Reimport Maven Project；Project SDK 选 JDK 17。

---

## 许可证

全部 Java 源码许可以仓库根目录 **[LICENSE](LICENSE)**（GNU LGPL v3 或更高版本）为准。
