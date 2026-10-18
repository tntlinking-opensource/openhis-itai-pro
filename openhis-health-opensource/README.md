# ZXPlat

医院信息平台 monorepo（目录名 = Maven artifactId）。

| 目录 / 制品 | 说明 |
|-------------|------|
| `zxplat-dependencies` | BOM |
| `zxplat-framework` | 框架单体 |
| `zxplat-core` | 业务聚合（`zxplat-share` / `zxplat-domain` / `zxplat-infrastructure`） |
| `zxplat-application` / `zxplat-starter` / `zxplat-app` | HIS 应用层 / 聚合 / 开发 Boot 壳 |
| `zxplat-plugin` | 扩展模块 |
| [zxplat-ui](https://gitee.com/wuhan-xinzhi-xinyuan-software/zxplat-ui.git) | 前端独立仓（与本仓同级） |
| `projects/` | 院点 Maven 模块（`project-H*` 根目录，无 `backend/` 子层） |
| `docs/projects/` | 院点同伴文档 |

**版本：** `2.0.9` · **JDK 17+**（D004：根 POM Enforcer + `ci-local.ps1` 校验；勿用 JDK 8）

### 冷构建（D003）

根 POM `dependencyManagement` **import** 了 BOM。首次/干净本地仓请先安装 BOM，再编业务模块（须 **JDK 17+**）：

```bash
mvn -f zxplat-dependencies/pom.xml install -N
mvn -pl zxplat-app -am package -DskipTests
```

院点示例：

```bash
mvn -pl projects/project-H22010202493 -am package -DskipTests
```

本地一键门禁（含文档自包含）：`pwsh -File plans/scripts/ci-local.ps1`

- **代码地图**：[docs/代码地图.md](docs/代码地图.md)
- **技术文档**：[docs/说明.md](docs/说明.md)
- **部署路径（院端）**：`/opt/zxplat/`（工程约定；以院点部署说明为准）
