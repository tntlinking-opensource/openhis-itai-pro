# zxplat-module-emr

ZXPlat 电子病历（EMR）扩展 Module（Wave3 M30）。

> **代码**：`zxplat-plugin/zxplat-module-emr/`  
> **文档**：`docs/模块/zxplat-module-emr/`  
> **包**：`com.zxplat.plugin.emr.*`

---

## 核心原则

| 原则 | 说明 |
|------|------|
| **可选扩展** | 未开通 EMR 不装 `module-emr-starter`（`zxplat.modules.emr.enabled=false`） |
| **与 domain 同进程** | bridge 直调 `zxplat-domain` Query/DomainService；共库 |
| **无 gateway** | 领域型扩展，无出站 HTTP 子模块 |
| **无 contracts** | Port 在 `module-emr-domain`（`loader/`、`document/access/`）；bridge / application 实现 |
| **层间禁依赖** | bridge 禁 `module-emr-application`；application 禁 `module-emr-bridge`（enforcer） |

---

## 四层模块

| 层 | Maven 模块 | 职责 |
|----|------------|------|
| **domain** | `module-emr-domain` | 实体、仓储、DocumentManager、`EmrAdmissionContextLoader` / `EmrPdfRenderer` 端口 |
| **bridge** | `module-emr-bridge` | ContextLoaderImpl、诊断 Orchestrator、ExtensionImpl（`bridge.integration`） |
| **application** | `module-emr-application` | EMR UI AppService、Flyway(emr)、PDF 适配器 |
| **starter** | `module-emr-starter` | 安装入口（无业务源码） |

### 命名契约

| Maven 模块 | Java 根包 |
|-----------|----------|
| `module-emr-domain` | `com.zxplat.plugin.emr.domain` |
| `module-emr-bridge` | `com.zxplat.plugin.emr.bridge` + `com.zxplat.plugin.emr.bridge.integration` |
| `module-emr-application` | `com.zxplat.plugin.emr.application` |
| `module-emr-starter` | （聚合） |

### 依赖方向

```text
module-emr-domain        → zxplat-framework
module-emr-bridge        → module-emr-domain + zxplat-domain + zxplat-framework
module-emr-application   → module-emr-domain + zxplat-framework
module-emr-starter       → domain + bridge + application
```

---

## Extension 链

```text
zxplat-domain SPI（Emr*Extension）
  → EmrAdmissionDiagnosisSyncExtensionImpl（bridge.integration）
  → EmrDiagnosisSyncOrchestrator（bridge.diagnosis.workflow）
  → module-emr-domain DocumentManager / DocumentStructureManager
```

元数据链：

```text
module-emr-application EmrMetadataContextBuilder
  → EmrAdmissionContextLoader（domain 端口）
  → EmrAdmissionContextLoaderImpl（bridge.metadata.context）
  → zxplat-domain *QueryService
```

---

## 配置

| 开关 | 含义 |
|------|------|
| `zxplat.modules.emr.enabled=true` | 装配 EMR（AutoConfiguration） |

宿主（Wave4）：`zxplat-app` 依赖 `module-emr-starter`（profile `with-emr`，待落地）。

## 构建

```bash
mvn -pl zxplat-plugin/zxplat-module-emr/module-emr-starter -am test
```

## 文档

- [模块说明-代码结构.md](./模块说明-代码结构.md)（各层边界已并入；原 `maven/*/README` 已废止）
- [13-实现状态与文档对照.md](./13-实现状态与文档对照.md)
- [17-zxplat-module-emr-架构-v3.md](./17-zxplat-module-emr-架构-v3.md)
