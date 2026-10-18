# zxplat-domain

业务领域 jar（聚合根、仓储接口、领域/查询服务；Flyway 核心脚本在本模块 `db/migration/`）。

同级拆分：
- `../zxplat-share` — `com.zxplat.domain.share.*`
- `../zxplat-infrastructure` — `com.zxplat.domain.infrastructure.*`

源码根：`src/main/java/com/zxplat/domain/`（不含 `share` / `infrastructure` 子树）。
