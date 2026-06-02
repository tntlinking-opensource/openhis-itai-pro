# Flyway 多模块版本约定

## 不要用「全局号段」的原因

若所有模块共用一个 `flyway_schema_history`、共用一个版本序列：

1. `health` 已有 `V1`…`V21`
2. `mrms` 若用 `V500`，当时能启动
3. 之后 `health` 再发 **`V22`**：Flyway 发现「已执行 500，但未执行 22」→ **out-of-order / 校验失败**

因此：**按目录分包 + 全局 V 号段** 在持续迭代下会踩坑，你的判断是对的。

## 推荐方案：同库、分模块独立 history 表（已实现）

| 模块 | 脚本目录 | history 表 | 版本号 |
|------|----------|------------|--------|
| 核心（health，含 whale_* 与业务表） | `db/migration/health` | `flyway_schema_history` | 全局递增 V1、V2…（仅本表内） |
| MRMS | `db/migration/mrms` | `flyway_schema_history_mrms` | **模块内** V1、V2… |
| CHS | `db/migration/chs` | `flyway_schema_history_chs` | **模块内** V1、V2… |

实现：`health-infrastructure` · `com.openhis.health.infrastructure.config.ModularFlywayConfiguration`（`openhis.flyway.modular.enabled=true`，默认开启）。

启动顺序：核心 → MRMS（`openhis.modules.mrms.enabled=true`）→ CHS（`openhis.modules.chs.enabled=true`）。

配置：

```yaml
spring:
  flyway:
    enabled: false   # 关闭 Boot 默认单 Flyway

openhis:
  flyway:
    modular:
      enabled: true
  modules:
    mrms:
      enabled: true
```

**同一 PostgreSQL 库、同一 `public` schema**，表仍在一起；仅 Flyway 元数据分表，无需改 Flyway 源码。

## 备选：子模块单独一个库

适用：医保/病案要独立部署、独立备份，或团队完全解耦。

| 优点 | 缺点 |
|------|------|
| 版本完全隔离 | 跨库不能用 FK，需应用层保证一致性 |
| 可独立扩容 | 运维、连接池、事务跨库复杂 |

Whale 当前是 **单库 + Port 集成**，故默认 **分 history 表**，不分库。

## 不推荐

| 方式 | 说明 |
|------|------|
| 改 Flyway 底层 | 维护成本高，升级 Flyway 要合并 fork |
| 全局号段 V500+ | 阻塞低版本后补脚本（见上文） |
| 各模块都从 V1 共用一个 history | 文件名冲突（最初的 V1 重复问题） |

## 新增脚本怎么写？

- **Health**：仍在 `db/migration/health/`，下一版 `V87__*.sql`（接当前最大版本），只写入 `flyway_schema_history`
- **MRMS**：在 `db/migration/mrms/`，下一版 `V2__*.sql`，只写入 `flyway_schema_history_mrms`
- 各模块 **互不影响** 对方版本号

## 从「号段方案」迁回

若库中曾写入 `V500` / `flyway_schema_history` 冲突记录，空库直接重启即可。  
若已有脏数据，删除错误 history 行或对新表 `flyway_schema_history_mrms` 单独 baseline。

## 参考

- `com.openhis.health.infrastructure.config.ModularFlywayConfiguration`
- `com.openhis.health.infrastructure.config.FlywayBeforeQuartzConfig`（`entityManagerFactory`、`quartzScheduler` 依赖 `flywayInitializer`；`spring.flyway.enabled=false` 时必需）
- 院点 `health-project-H*`：依赖 `health-infrastructure`（或经 `health-app` 传递），`scanBasePackages` 含 `com.openhis.health.infrastructure`
