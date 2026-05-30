# Whale MRMS

Whale MRMS（病案管理）是基于 Whale 框架的**可插拔病案管理扩展包**，可以：

- 作为 `whale-health` 的扩展包被引用（通过 Maven 依赖）；
- 也可以作为独立后端模块集成到其他基于 Whale 的业务系统中。

## 模块结构

```text
whale-mrms/
├── pom.xml                 # 聚合 POM，统一版本与模块管理
├── mrms-domain/            # 领域层：实体、仓储接口、领域服务
│   └── src/main/java/com/openhis/whale/mrms/domain
│       ├── entity/
│       ├── enums/
│       ├── model/
│       ├── repository/
│       └── service/
└── mrms-application/       # 应用层：AppService、DTO、Request 等
    └── src/main/java/com/openhis/whale/mrms/application
        ├── config/
        ├── dto/
        ├── request/
        └── support/
```

## 构建

```bash
cd whale-sever/whale-mrms
mvn clean install -DskipTests
```

安装产物：

- `com.openhis:mrms-domain:2.0.0-SNAPSHOT`
- `com.openhis:mrms-application:2.0.0-SNAPSHOT`

## 设计文档

写代码前请依次阅读：

1. [docs/00-功能清单.md](docs/00-功能清单.md) — 做什么、优先级、MVP  
2. [docs/01-架构设计.md](docs/01-架构设计.md) — 模块边界、Port、状态机、Wave  
3. [docs/02-概要设计.md](docs/02-概要设计.md) — 表结构、API 契约、集成清单  
4. [docs/03-病案首页-详细设计.md](docs/03-病案首页-详细设计.md) — 首页字段、自动取值、4401 映射  
5. [docs/04-归档与质控-详细设计.md](docs/04-归档与质控-详细设计.md) — 质控、归档、卷宗预览  
6. [docs/05-对外上报-详细设计.md](docs/05-对外上报-详细设计.md) — 医保 4401 上传
7. [docs/06-病案首页-前端设计.md](docs/06-病案首页-前端设计.md) — openhis-pro-ui 病案管理菜单与首页编辑（另仓）

## 与 whale-health 集成

**启用 MRMS 只改一处**：`health-app/src/main/resources/application.yml`

```yaml
openhis:
  modules:
    mrms:
      enabled: true   # false 则关闭病案 API
```

改后重启进程。`health-app` 默认 Maven 已带 `mrms-application`（profile `with-mrms`），Flyway 已扫描 `db/migration/mrms`，**不要**再在 `application-dev.yml` 里写 `spring.profiles.include`（Spring Boot 会报错）。

编译：

```bash
cd whale-sever/whale-mrms && mvn clean install -DskipTests
cd ../whale-health && mvn clean install -pl health-app -am -DskipTests
```

接口：`POST /api/app/caseRecord/queryPage`
