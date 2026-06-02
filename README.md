# OpenHIS Health（源码使用说明）

本仓库为**业务源码**（`whale-health`、`whale-emr`、`whale-mrms`）。**Whale Framework 不提供源码**，已以预编译 JAR 形式内置在 **`dist/framework-maven-repo/`**，clone 后无需额外下载。

## 简介

基于 **Whale Framework** 的医院信息系统后端：住院/门诊等业务（`whale-health`）、电子病历（`whale-emr`）与病案管理（`whale-mrms`）同进程集成，由 `health-app` 统一启动。

### 技术栈

| 项 | 说明 |
|----|------|
| Java | 17 |
| Spring Boot | 3.5.7（见各模块 `pom.xml`） |
| Whale Framework | 预编译 JAR（`dist/framework-maven-repo`，版本见 `whale.framework.version`） |
| 数据库 | PostgreSQL 16 |
| ORM | JPA / Hibernate |
| 迁移 | Flyway |
| API 文档 | SpringDoc / Swagger UI |

---

## 项目结构

### 仓库根目录

```text
<仓库根>/
├── pom.xml
├── dist/framework-maven-repo/   # Framework 预编译 JAR（已随仓携带）
├── whale-health/                # 医疗业务
├── whale-emr/                   # 电子病历
├── whale-mrms/                  # 病案管理
└── whale-framework/             # 本仓库中不存在（仅二进制依赖）
```

### whale-health 模块

```text
whale-health/
├── health-domain-shared/    # 枚举、常量等共享定义
├── health-domain/           # 领域层（实体、领域服务、读模型等）
├── health-application/      # 应用层（AppService）
├── health-infrastructure/   # 基础设施（资源、迁移脚本等）
└── health-app/              # 启动模块（HealthApplication、application.yml）
```

### whale-emr 模块

```text
whale-emr/
├── emr-domain/              # 病历领域（文书、模板等）
└── emr-application/         # 病历应用服务（由 health-app 依赖并同进程加载）
```

### whale-mrms 模块

```text
whale-mrms/
├── mrms-domain/             # 病案领域
└── mrms-application/        # 病案应用服务（由 health-app 依赖并同进程加载）
```

---

## 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.9+（建议 3.9+） |
| PostgreSQL | 14+（建议 16+） |

---

## 1. 克隆与构建

克隆本仓库后，确认 `dist/framework-maven-repo/com/openhis/whale-core/` 存在（正常 clone 即已包含）。

在仓库根目录执行：

```bash
mvn clean package -Pbinary-framework -pl whale-health/health-app -am -DskipTests
```

含 EMR 扩展时追加 `-Pwith-emr`。

| Maven Profile | 作用 |
|---------------|------|
| `binary-framework` | 从 `dist/framework-maven-repo` 解析 Framework JAR |

**IntelliJ IDEA：**

1. File → Open → 选择根目录 `pom.xml`。
2. Settings → Build Tools → Maven：配置本机 Maven 与 JDK 17。
3. 右侧 Maven → **Profiles** 勾选：`binary-framework`。
4. 等待索引与依赖下载完成。
5. Lifecycle → `package`（模块 `health-app` 或根项目均可，推荐上述命令行保证 profile 正确）。

产物路径：

```text
whale-health/health-app/target/health-app-2.0.0-SNAPSHOT.jar
```

### 仅修改业务代码时的快速编译

已完成首次全量 `package` 后，若只改 `whale-health` / `whale-emr`：

```bash
mvn clean compile -Pbinary-framework -pl whale-health/health-app -am -DskipTests
```

或在 IDEA 中直接 Run / Debug（见下文），由 IDE 增量编译。

---

## 2. 本地启动（开发调试）

### 2.1 数据库

1. 创建 PostgreSQL 库（库名与连接串一致即可，例如 `whale` 或 `whale_health_db`）。
2. 修改 `whale-health/health-app/src/main/resources/application.yml` 中的 `spring.datasource.url / username / password`，指向你的本机库。

也可用环境变量（Windows 示例）：

```bat
set DB_USERNAME=postgres
set DB_PASSWORD=你的密码
set SPRING_PROFILES_ACTIVE=dev
```

首次启动会执行 **Flyway** 迁移，脚本来自（按 classpath 合并）：

- `classpath:db/migration/health` — 框架 `whale_*` 与业务表（V1 起合并维护）

### 2.2 访问地址（默认配置）

`application.yml` 默认 **`server.port: 8081`**，启动成功后：

| 说明 | 地址 |
|------|------|
| 应用根路径 | http://localhost:8081/ |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |

生产环境请修改端口、关闭 Swagger 或增加访问控制。

### 2.3 IDEA 运行

| 项 | 值 |
|----|-----|
| Main class | `com.openhis.health.HealthApplication` |
| Module | `health-app` |

先完成第 1 节构建或至少 `compile`，再 **Run / Debug**。  
确认已勾选 Maven Profile：`binary-framework`。

### 2.4 命令行运行

Windows 可双击仓库根目录 **`run-dev.bat`**，或：

```bash
mvn spring-boot:run -Pbinary-framework -pl whale-health/health-app -DskipTests
```

打包后：

```bash
java -jar whale-health/health-app/target/health-app-2.0.0-SNAPSHOT.jar
```

### 2.5 日志

默认写文件：`logs/whale-health.log`（相对进程工作目录）。可在 `application.yml` 的 `logging` 段调整级别与路径。

---

## 3. 打包部署（服务器）

1. 在构建机执行第 1 节，得到 `health-app-2.0.0-SNAPSHOT.jar`。
2. 将 JAR 与生产配置发到服务器，例如：

```text
/opt/openhis/health-app.jar
/opt/openhis/config/application-prod.yml
```

3. 启动示例：

```bash
java -Xms512m -Xmx2g -jar /opt/openhis/health-app.jar --spring.profiles.active=prod
```

生产建议通过环境变量配置数据库与密钥，例如：

```bash
export DB_USERNAME=postgres
export DB_PASSWORD=***
export JWT_SECRET=你的32位以上密钥
export SPRING_PROFILES_ACTIVE=prod
```

可按需配置 JVM、systemd、反向代理（Nginx 等），与常规 Spring Boot 部署相同。

---

## 4. 业务开发说明

### 4.1 新增功能（推荐分层）

1. `health-domain-shared` — 枚举、常量  
2. `health-domain` — 实体、领域服务、仓储  
3. `health-application` — AppService、DTO  
4. `health-infrastructure` — 迁移脚本、外部适配  
5. `health-app` — 仅启动与配置，一般不写业务代码  

### 4.2 Whale Framework 常用能力（业务代码中）

- **多租户**：实体实现 `IMultiTenant`  
- **审计**：实体继承 `BaseAuditedEntity`  
- **权限**：`@RequirePermission`  
- **分页**：`PagedResult`、`BaseQueryRequest`  
- **动态查询**：JPA `Specification`  

Framework 实现细节在预编译 JAR 中，本仓库不提供其源码。

### 4.3 电子病历（EMR）

`whale-emr` 提供文书、模板等能力，由 `health-app` 依赖 `emr-application` 同进程加载。扩展 EMR 请在 `whale-emr` 模块内按领域/应用分层修改。

---

## 5. 版本对齐

根 `pom.xml` 中请关注：

| 属性 | 含义 |
|------|------|
| `whale.framework.version` | 须与 `dist/framework-maven-repo` 内 JAR 版本一致 |
| 各模块版本 | 一般为 `2.0.0-SNAPSHOT`，发版时以 tag 说明为准 |

---

## 6. 常见问题

### 找不到 whale-core / whale-web

- `dist/framework-maven-repo/` 是否完整（勿删改）？  
- Maven Profile 是否勾选 `binary-framework`？  

### 数据库 / Flyway 启动失败

- 检查 PostgreSQL 是否可达、库是否已创建。  
- 核对 `application.yml` 或环境变量中的连接信息。  
- 开发环境若改过迁移脚本，需按 Flyway 规则处理校验（参见 Spring Boot `spring.flyway` 配置）。  

### IDEA 未识别模块或依赖标红

- Reimport Maven Project。  
- 确认 `dist/framework-maven-repo` 存在且 Profile `binary-framework` 已勾选。  
- File → Project Structure → Project SDK 选 JDK 17。  

---

## 7. 许可证

业务源码许可以仓库根目录 **`LICENSE`** 为准。  
Whale Framework 二进制仅用于构建与运行本应用，未经授权请勿单独再分发 Framework JAR。
