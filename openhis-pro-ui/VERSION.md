# OpenHIS 前端业务版本

本目录的 **`VERSION`**（单行，如 `2.0.2` 或 `2.0.2.3`）由**前端研发手工维护**，与后端院点 POM 的产品版本对齐使用。

- **不是**根目录 / `apps/web-ele` 的 `package.json` 里 Vben 的 `5.x`。
- WhaleOps.Pack 打包时应读取本文件写入发布标识与产物名（见 `whale-doc/运维手册/版本与发布标识.md`）。
- 发版前改 `VERSION` 并提交 Git，再构建。
