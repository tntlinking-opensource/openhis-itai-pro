# zxplat-module-emr

电子病历（EMR）扩展模块。Wave3 **M30**。

| 层 | artifact | 包前缀 |
|----|----------|--------|
| domain | `module-emr-domain` | `com.zxplat.plugin.emr.domain` |
| bridge | `module-emr-bridge` | `com.zxplat.plugin.emr.bridge`（含 `bridge.integration` ExtensionImpl） |
| application | `module-emr-application` | `com.zxplat.plugin.emr.application` |
| starter | `module-emr-starter` | （聚合安装） |

**开关**：`zxplat.modules.emr.enabled=true`  
**同伴文档**：[`docs/模块/zxplat-module-emr/说明.md`](../../docs/模块/zxplat-module-emr/说明.md)

```bash
mvn -pl zxplat-plugin/zxplat-module-emr/module-emr-starter -am compile
```
