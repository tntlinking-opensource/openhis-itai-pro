# module-wi-jilin-starter

工伤扩展装配入口：`WiJilinModuleAutoConfiguration`、Flyway `db/migration/wi`。

## 开关

```properties
zxplat.modules.work-injury.enabled=true
```

## 资源

| 路径 | 说明 |
|------|------|
| `db/migration/wi/V1–V3` | `yb_wi_*` 表 |
| `META-INF/spring/...AutoConfiguration.imports` | Boot 自动装配 |
| `META-INF/openhis/module-metadata.json` | 模块元数据 |

Flyway 在 starter（CHS 在 application — 本模块 intentional delta）。
