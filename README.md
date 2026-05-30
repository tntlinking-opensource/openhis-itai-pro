
# 平台介绍

## 🏠【关于我们】

![天天开源](https://open.tntlinking.com/assets/logo-b-BzFUYaRU.png) 

天天开源致力于构建开放共赢的平台，推动安全高效的开源应用普及。我们始终秉持“开源、众包、共享”的理念，致力于为医疗、教育、中小企业等行业提供优质的开源解决方案。

天天开源聚焦医疗、企业、教育三大行业信息化市场，现已发布OpenHIS、OpenCOM、OpenEDU系列开源软件产品。我们通过建立开源生态，联合生态伙伴共同打造创新行业协作模式，让数字化普惠、可信、安全。

天天开源的前身是新致开源，最早于2022年6月发布OpenHIS开源医疗，于2023年6月发布OpenCOM开源企业。2025年7月，新致开源品牌升级为天天开源。我们将持续践行开源精神，期待成为全球开源生态的引领者。

了解我们：https://open.tntlinking.com/about?site=gitee

## 💾【部署包下载】

请访问官网产品中心下载部署包：https://open.tntlinking.com/resource/productCenter?site=gitee

## 📚【支持文档】

技术文档：https://open.tntlinking.com/resource/openProductDoc?site=gitee （含演示环境、操作手册、部署手册、开发手册、常见问题等）

产品介绍：https://open.tntlinking.com/resource/productPresentation?site=gitee

操作教程：https://open.tntlinking.com/resource/operationTutorial?site=gitee

沙龙回顾：https://open.tntlinking.com/resource/openSourceSalon#23?site=gitee

## 🤝【合作方式】

产品服务价格：https://open.tntlinking.com/cost?site=gitee

加入生态伙伴：https://open.tntlinking.com/ecology/becomePartner?site=gitee

## 🤗【技术社区】

请访问官网扫码加入技术社区交流：https://open.tntlinking.com/ecology/joinCommunity?site=gitee

请关注公众号【天天开源软件】以便获得最新产品更新信息。



# 项目介绍

OpenHIS医院系统（信创版）集十大核心模块于一体，涵盖目录管理、基础数据配置、个性化设置、门诊/住院全流程管理、药房药库智能管控、精细化耗材管理、财务核算体系、医保合规对接及多维报表分析等功能模块。目标适配民营公立一二级医院，支持单体医院、分院、集团化运营及区域医疗协同等多种部署模式，并通过国家信创认证体系，确保全栈技术自主可控。如有项目需求，可联系官方平台合作。



# OpenHIS 项目说明（前后端）

OpenHIS 医院信息系统由**前端**与**后端**两个仓库组成，本地联调时先启后端、再启前端。

| 仓库               | 说明                                                    |
| ------------------ | ------------------------------------------------------- |
| **openhis-pro-ui** | Vue 3 前端（本文档第一节）                              |
| **whale-sever**    | Java 后端：`whale-health` + `whale-emr`（本文档第二节） |

**联调默认**：后端 `http://localhost:8081`，前端开发服务将 `/api` 代理到后端（见 `apps/web-ele/vite.config.mts`）。

---

## 一、前端（openhis-pro-ui）

基于 [Vue Vben Admin](https://github.com/vbenjs/vue-vben-admin) 的 Monorepo，主应用为 `apps/web-ele`（Element Plus）。

### 技术栈

| 类别        | 选型                     |
| ----------- | ------------------------ |
| 框架        | Vue 3 + TypeScript       |
| 构建        | Vite 7                   |
| UI          | Element Plus、Vxe Table  |
| 状态 / 路由 | Pinia、Vue Router        |
| HTTP        | Axios（`@vben/request`） |
| 工程        | pnpm workspace + Turbo   |

### 环境要求

| 工具    | 版本      |
| ------- | --------- |
| Node.js | ≥ 20.12.0 |
| pnpm    | ≥ 10.0.0  |

### 常用命令

```bash
pnpm install      # 安装依赖
pnpm dev          # 开发（主应用 web-ele）
pnpm build:ele    # 生产构建 → apps/web-ele/dist
```

环境变量见 `apps/web-ele/.env*`（如 `VITE_GLOB_API_URL=/api`）。生产部署可参考 `apps/web-ele/nginx.conf`。

### 项目结构

```text
openhis-pro-ui/
├── apps/
│   └── web-ele/              # 主应用
│       └── src/
│           ├── api/          # 接口（按业务域）
│           ├── views/        # 页面
│           ├── components/   # 公共组件
│           ├── router/       # 路由与权限
│           ├── store/        # 业务 Store
│           └── layouts/      # 布局
├── packages/                 # Vben 公共包
├── internal/                 # 工程配置（vite、eslint 等）
└── pnpm-workspace.yaml
```

`views` 主要域：`basic`、`outpatient`、`inpatient`、`inventory`（库房）、`system` 等。路径别名 `#/` → `apps/web-ele/src/`。

更细的代码规范见仓库内 `DEVELOPMENT.md`。

---

## 二、后端（whale-sever / 开源 health+emr）

本段为**业务源码**（`whale-health`、`whale-emr`）。**Whale Framework 不提供源码**，请从 [Releases](https://github.com/YOUR_ORG/YOUR_REPO/releases) 下载与当前版本一致的 `framework-maven-repo-*.zip`。

### 简介

基于 **Whale Framework** 的医院信息系统后端：住院/门诊等业务（`whale-health`）与电子病历引擎（`whale-emr`）同进程集成，由 `health-app` 统一启动。

### 技术栈

| 项              | 说明                                                         |
| --------------- | ------------------------------------------------------------ |
| Java            | 17                                                           |
| Spring Boot     | 3.5.7（见各模块 `pom.xml`）                                  |
| Whale Framework | 预编译 JAR（见 Release zip，版本 `whale.framework.version`） |
| 数据库          | PostgreSQL 16                                                |
| ORM             | JPA / Hibernate                                              |
| 迁移            | Flyway                                                       |
| API 文档        | SpringDoc / Swagger UI                                       |

### 项目结构

**仓库根目录**

```text
<后端仓库根>/
├── pom.xml
├── dist/framework-maven-repo/   # 由 Release zip 解压
├── whale-health/                # 医疗业务
├── whale-emr/                   # 电子病历
└── whale-framework/             # 开源仓中不存在（仅二进制依赖）
```

**whale-health**

```text
whale-health/
├── health-domain-shared/    # 枚举、常量等共享定义
├── health-domain/           # 领域层（实体、领域服务、读模型等）
├── health-application/      # 应用层（AppService）
├── health-infrastructure/   # 基础设施（资源、迁移脚本等）
└── health-app/              # 启动模块（HealthApplication、application.yml）
```

**whale-emr**

```text
whale-emr/
├── emr-domain/              # 病历领域（文书、模板等）
└── emr-application/         # 病历应用服务（由 health-app 依赖并同进程加载）
```

### 环境要求

| 工具       | 版本            |
| ---------- | --------------- |
| JDK        | 17+             |
| Maven      | 3.9+            |
| PostgreSQL | 14+（建议 16+） |

### 1. 准备 Framework 依赖（必做）

1. 打开 Releases，下载 **`framework-maven-repo-<版本>.zip`**。  
   版本须与根 `pom.xml` 中 **`whale.framework.version`** 一致（默认 `1.0.0-SNAPSHOT`）。
2. 克隆本仓库后，将 zip **解压到仓库根目录**：

```text
dist/framework-maven-repo/
  com/openhis/whale-core/...
  com/openhis/whale-web/...
```

> 若该目录不存在或为空，构建时会报找不到 `com.openhis:whale-core` 等依赖。

### 2. 构建

在仓库根目录执行：

```bash
mvn clean package -Popensource,binary-framework -pl whale-health/health-app -am -DskipTests
```

| Maven Profile      | 作用                                              |
| ------------------ | ------------------------------------------------- |
| `opensource`       | 不包含 `whale-framework` 源码模块                 |
| `binary-framework` | 从 `dist/framework-maven-repo` 解析 Framework JAR |

**IntelliJ IDEA：**

1. File → Open → 选择根目录 `pom.xml`。
2. Settings → Build Tools → Maven：配置本机 Maven 与 JDK 17。
3. 右侧 Maven → **Profiles** 勾选：`opensource`、`binary-framework`。
4. 等待索引与依赖下载完成。
5. Lifecycle → `package`（模块 `health-app` 或根项目均可，推荐上述命令行保证 profile 正确）。

产物路径：

```text
whale-health/health-app/target/health-app-1.0.0-SNAPSHOT.jar
```

**仅修改业务代码时的快速编译**

```bash
mvn clean compile -Popensource,binary-framework -pl whale-health/health-app -am -DskipTests
```

### 3. 本地启动（开发调试）

**数据库**

1. 创建 PostgreSQL 库（例如 `whale` 或 `whale_health_db`）。
2. 修改 `whale-health/health-app/src/main/resources/application.yml` 中的 `spring.datasource.url / username / password`。

环境变量示例（Windows）：

```bat
set DB_USERNAME=postgres
set DB_PASSWORD=你的密码
set SPRING_PROFILES_ACTIVE=dev
```

首次启动会执行 **Flyway** 迁移：

- `classpath:db/migration/framework` — 框架表
- `classpath:db/migration/health` — 业务表

**访问地址（默认 `server.port: 8081`）**

| 说明         | 地址                                  |
| ------------ | ------------------------------------- |
| 应用根路径   | http://localhost:8081/                |
| Swagger UI   | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs     |

**IDEA 运行**

| 项         | 值                                     |
| ---------- | -------------------------------------- |
| Main class | `com.openhis.health.HealthApplication` |
| Module     | `health-app`                           |

**命令行运行**

```bash
java -jar whale-health/health-app/target/health-app-1.0.0-SNAPSHOT.jar
```

或：

```bash
mvn spring-boot:run -Popensource,binary-framework -pl whale-health/health-app -DskipTests
```

日志默认：`logs/whale-health.log`。

### 4. 打包部署（服务器）

1. 构建得到 `health-app-1.0.0-SNAPSHOT.jar`。
2. 部署 JAR 与 `application-prod.yml`。
3. 启动示例：

```bash
java -Xms512m -Xmx2g -jar /opt/openhis/health-app.jar --spring.profiles.active=prod
```

生产环境变量示例：`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`、`SPRING_PROFILES_ACTIVE=prod`。

### 5. 业务开发说明

**新增功能（推荐分层）**

1. `health-domain-shared` — 枚举、常量  
2. `health-domain` — 实体、领域服务、仓储  
3. `health-application` — AppService、DTO  
4. `health-infrastructure` — 迁移脚本、外部适配  
5. `health-app` — 仅启动与配置  

**Framework 常用能力**

- 多租户：实体实现 `IMultiTenant`  
- 审计：实体继承 `BaseAuditedEntity`  
- 权限：`@RequirePermission`  
- 分页：`PagedResult`、`BaseQueryRequest`  

**电子病历（EMR）**：在 `whale-emr` 模块内按领域/应用分层扩展。

### 6. 版本对齐

| 属性                      | 含义                                           |
| ------------------------- | ---------------------------------------------- |
| `whale.framework.version` | 须与 Release 中 framework zip 版本一致         |
| 各模块版本                | 一般为 `1.0.0-SNAPSHOT`，以 tag / Release 为准 |

### 7. 常见问题

**找不到 whale-core / whale-web**

- 是否已解压 zip 到 `dist/framework-maven-repo/`？  
- Maven Profiles 是否勾选 `opensource`、`binary-framework`？  
- zip 版本是否与 `whale.framework.version` 一致？  

**数据库 / Flyway 启动失败**

- 检查 PostgreSQL 与 `application.yml` 连接信息。  

**IDEA 依赖标红**

- Reimport Maven；确认 JDK 17 与 Profiles 已勾选。  

### 8. 许可证

业务源码许可以后端仓库根目录 **`LICENSE`** 为准。Whale Framework 二进制仅用于构建与运行本应用，未经授权请勿单独再分发 Framework JAR。
		