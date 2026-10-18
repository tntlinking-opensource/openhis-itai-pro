# project-openhis · OpenHIS 产品演示院点

SiteId：`openhis`。主线能力基线，无定点定制业务；扩展模块通过 Maven `-Pwith-*` 按需启用。

## 构建

```bash
mvn -pl zxplat-projects/project-openhis -am package -DskipTests
# 示例：带医保
mvn -pl zxplat-projects/project-openhis -am package -DskipTests -Pwith-chs,with-emr
```

## 前端

同级 `zxplat-ui/projects/project-openhis`：

```powershell
$env:ZXPLAT_PROJECT = "openhis"
node scripts/zxplat-project/run-web-ele.mjs -- pnpm vite build --mode production
```

## Ops

`zxplat-private/zxplat-ops/config/sites/openhis.json`
