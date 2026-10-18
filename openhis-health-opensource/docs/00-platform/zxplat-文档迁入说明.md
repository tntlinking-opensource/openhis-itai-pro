# ZXPlat 文档迁入说明

> 同伴文档随 Wave 1–6 代码迁入；Wave 7 仅收横切残留与索引终稿。  
> 详本根目录：[docs/说明.md](../说明.md) · 总索引：[代码地图.md](../代码地图.md)。

## 落点对照（与代码路径对齐）

| 代码 / 范围 | 文档目录 |
|-------------|----------|
| `zxplat-framework/` | [框架/](../框架/) |
| `zxplat-core/zxplat-domain/` | [领域/](../领域/) |
| `zxplat-application/` 等 | [应用/](../应用/) |
| `zxplat-plugin/zxplat-module-*/` | [模块/zxplat-module-*/](../模块/) |
| 横切架构规范 | [架构/](../架构/) |
| 平台总览 / 贡献 | [00-platform/](./) |
| 模块架构模板 | [templates/](../templates/) |
| `projects/<id>/` | [projects/](../projects/)（代码在仓库根 `projects/<id>/`；UI 在 zxplat-ui） |
| Agent Skills | [prompts/](../../prompts/) · Cursor：[.cursor/skills/](../../.cursor/skills/) |

## 刻意不迁入本仓 `docs/` 的内容

- 源仓各医院定制目录、学习计划、未进 reactor 的增值包文档
- 院点正文只在 `docs/projects/<id>/`（代码在 `projects/<id>/`）
- 前端工程正文（独立仓 zxplat-ui；架构篇在 [架构/前端院点定制方案.md](../架构/前端院点定制方案.md)）

## 维护

新增模块文档须与对应 Maven 目录同步落盘，并更新 [代码地图.md](../代码地图.md)。文档自包含门禁：`plans/scripts/check-docs-self-contained.ps1`。
