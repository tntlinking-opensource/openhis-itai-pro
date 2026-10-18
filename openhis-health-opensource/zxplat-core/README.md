# zxplat-core

核心领域聚合（目录名与 Maven `artifactId` 均为 **zxplat-core**）。

| 子模块 | 说明 |
|--------|------|
| `zxplat-share` | 跨聚合共享契约（`com.zxplat.domain.share.*`） |
| `zxplat-domain` | 业务领域聚合根 / 仓储 / 领域服务；含 health Flyway 脚本 |
| `zxplat-infrastructure` | Flyway 装配、平台桥接、Integration Extension SPI / NoOp / 编排 |

依赖方向：`share` ← `domain` ← `infrastructure`。

应用层 / starter / boot 在仓库根级：`zxplat-application`、`zxplat-starter`、`zxplat-app`（不进本聚合）。

框架见 `../zxplat-framework`。
