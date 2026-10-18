# zxplat-infrastructure

领域基础设施 jar（原 `zxplat-domain` 内 `com.zxplat.domain.infrastructure`）。

| 项 | 值 |
|----|----|
| 制品 | `com.zxplat:zxplat-infrastructure` |
| 根包 | `com.zxplat.domain.infrastructure.*` |
| 源码根 | `src/main/java/com/zxplat/domain/infrastructure/` |

含模块化 Flyway 装配、平台身份/组织桥接、Integration Extension SPI / NoOp / 编排。依赖 `zxplat-domain` 与 `zxplat-share`。

Spring Boot 自动装配入口：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。

health 核心 Flyway 脚本仍在 `zxplat-domain` 的 `db/migration/`（与实体同模块；本模块负责链式执行）。
