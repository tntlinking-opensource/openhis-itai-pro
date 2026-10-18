# ZXPlat（维护者）

> **位置**：本文档位于主仓 `docs/00-platform/`。日常 clone / 构建 / 启动以根目录 **[README.md](../../README.md)** 为准。  
> 前端独立仓：[zxplat-ui](https://gitee.com/wuhan-xinzhi-xinyuan-software/zxplat-ui.git)（推荐与本仓同级）。

本仓库为 **ZXPlat Java 源码 monorepo**，含 `zxplat-framework`、`zxplat-domain`、`zxplat-application`、`zxplat-plugin/*`、`zxplat-app`，以及院点目录 `projects/`。当前版本 **2.0.9**。

---

## 项目结构

```text
zxplat/
├── zxplat-dependencies/   # BOM
├── zxplat-framework/      # 框架单体 jar
├── zxplat-core/
│   ├── zxplat-share/          # 跨聚合共享契约
│   ├── zxplat-domain/         # 业务领域 jar
│   └── zxplat-infrastructure/ # Flyway / 平台桥 / Integration SPI
├── zxplat-application/    # HIS 应用层
├── zxplat-starter/        # 运行时聚合
├── zxplat-app/            # 开发 Boot 壳
├── zxplat-plugin/         # 扩展插件
├── projects/              # 院点（backend / ui / docs）
├── docs/                  # 平台文档
├── prompts/               # Agent Skills 真源副本
└── pom.xml
```

同级前端：`../zxplat-ui/`（不在本仓）。

---

## 环境要求

- JDK **17**
- Maven **3.9+**
- PostgreSQL（本地/测试按 `zxplat-app` 的 `application.yml` 配置）
- 前端：Node 20+、pnpm（见 zxplat-ui README）

---

## 日常开发

### 构建全仓（跳过测试）

```bash
mvn clean install -DskipTests
```

### 构建并运行开发壳

```bash
mvn clean package -pl zxplat-app -am -DskipTests
java -jar zxplat-app/target/zxplat-app-2.0.9.jar
```

### IDEA

- 打开根目录 `pom.xml`。
- 运行主类：以 `zxplat-app` 模块声明为准。

### 前端（同级仓）

```bash
cd ../zxplat-ui
pnpm install
pnpm run build:ele
# 院点 overlay：ZXPLAT_PROJECT=H… + scripts/zxplat-project/run-web-ele.mjs
```

---

## 服务器发版

1. `mvn clean install -DskipTests`（或按变更范围缩小 `-pl`）
2. 部署院点 `projects/project-H*` 产物，或开发壳 `zxplat-app` jar
3. `java -jar … --spring.profiles.active=prod`（Profile 以院点/README 为准）

前端发布见 [架构/前端部署.md](../架构/前端部署.md)（若篇名不同见 [架构/](../架构/)）。

---

## 代码格式（Spotless）

| 命令 | 用途 |
|------|------|
| `mvn spotless:apply` | 统一 Java 版权头、去未使用 import |
| `mvn spotless:check` | 校验格式 |

版权头模板以仓内 `tools/` 或根配置为准（若有）。

---

## 版本属性（根 pom.xml）

根 `pom.xml` 当前版本 `2.0.9`。发版时修改根 POM `<version>`（及 BOM 对齐约定）。

---

## 注意事项

- `dist/`、`target/`、`node_modules/` 勿提交。
- 院点密钥不进仓；配置用 `*.example`。
