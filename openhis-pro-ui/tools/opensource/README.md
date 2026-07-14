# 前端开源导出

> 与后端 `whale-sever/tools/opensource/` 对称。院点定制 `projects/` **不进**公开仓。

## 一键导出

在 `openhis-pro-ui` 根目录：

```bat
tools\opensource\export-opensource.bat
tools\opensource\export-opensource.bat -Force -VerifyBuild
```

中文入口（同上）：`tools\opensource\导出开源源码树.bat`

输出：`dist/openhis-pro-ui-opensource/`

## 排除项

| 路径                     | 原因                            |
| ------------------------ | ------------------------------- |
| `projects/`              | 院点 env、overrides（客户定制） |
| `node_modules/`、`dist/` | 构建产物与依赖                  |

完整列表见 `opensource-export-manifest.json`。

## 导出后构建

```bash
pnpm install
pnpm run build:ele
```

开源树不含 `OPENHIS_PROJECT` 院点配置，默认构建产品主线。
