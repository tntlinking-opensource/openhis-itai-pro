# zxplat-module-pacs

PACS **RIS + Imaging** 扩展 Module。说明见 [docs/模块/zxplat-module-pacs/说明.md](../../docs/模块/zxplat-module-pacs/说明.md)。

| 层 | artifact |
|----|----------|
| domain | `module-pacs-domain`（含 `pacsimaging` 领域 + imaging Flyway） |
| bridge | `module-pacs-bridge`（厂商 adapter） |
| application | `module-pacs-application`（RIS + 采集/阅片） |
| starter | `module-pacs-starter` |

## 开关（方案 A · 单一开关）

`-Pwith-pacs` / classpath 即启用 **RIS + 影像**；yml 只写关闭：

```yaml
zxplat:
  modules:
    pacs:
      enabled: false
```

中间件连接（Orthanc/MinIO 等）仍用 `zxplat.modules.pacs.imaging.*` 配置项，不是模块启停开关。联调栈见 `deploy/`。
