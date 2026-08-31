# 前端开源导出 / 同步

> 与后端 `whale-sever/tools/sync-opensource/` 对称。院点定制 `projects/` **不进**公开仓。

## 一键同步到开源仓目录

在 `openhis-pro-ui` 根目录：

```bat
tools\sync-opensource\sync-to-opensource.bat
```

或：

```bat
tools\opensource\export-opensource.bat
tools\opensource\导出开源源码树.bat
```

带构建校验：

```bat
tools\opensource\export-opensource.bat -Force -VerifyBuild
```

改开源仓业务版本（只写目标根目录 `VERSION`，不改 `package.json` 的 Vben 5.x）：

```bat
tools\opensource\export-opensource.bat -Force -NewVersion 2.0.6
```

前后端一起同步（推荐）：在 `whale-sever` 双击  
`tools\sync-opensource\sync-to-opensource.bat`（会调本导出并写入同一版本号）。

**默认输出**：`../openhis-itai-pro/openhis-pro-ui`  
（即 `D:\workSpace\openhis-itai-pro\openhis-pro-ui`，与私有仓同级）

自定义输出：

```powershell
powershell -File tools\opensource\export-opensource.ps1 -Force -NewVersion 2.0.6 -OutputDir dist\openhis-pro-ui-opensource
```

## 排除项

| 路径                                            | 原因                            |
| ----------------------------------------------- | ------------------------------- |
| `projects/`                                     | 院点 env、overrides（客户定制） |
| `.cursor/`                                      | 私有 Agent/规则                 |
| `node_modules/`、`dist/`、`.turbo/`             | 构建产物与依赖                  |
| 根目录临时打印 JSON、`vite.config.local.mts` 等 | 本地联调/草稿                   |

完整列表见 `opensource-export-manifest.json`（含 `DefaultOutputDir`）。

## 导出后构建

在目标目录：

```bash
pnpm install
pnpm run build:ele
```

开源树不含 `OPENHIS_PROJECT` 院点配置，默认构建产品主线（不要跑 `*:project`）。
