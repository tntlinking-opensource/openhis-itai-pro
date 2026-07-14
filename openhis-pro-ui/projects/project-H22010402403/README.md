# project-H22010402403 · 长春市朝阳区中医院

与后端 `whale-sever/whale-project/project-H22010402403` 对称的前端院点工程。

**架构文档**：[whale-doc/01-架构/前端院点定制方案.md](../../../whale-doc/01-架构/前端院点定制方案.md)

## 目录

```text
project-H22010402403/
├── .env.development      # 本地开发院点差异（覆盖 apps/web-ele 同名 env）
├── .env.production       # 生产构建院点差异
├── overrides/            # 编译期文件重载（#/ 导入路径，存在则优先）
└── README.md
```

## 构建与开发

在 `openhis-pro-ui` 根目录：

```powershell
# 生产构建（朝阳院点）
$env:OPENHIS_PROJECT = 'H22010402403'
pnpm run build:ele:project

# 本地开发
$env:OPENHIS_PROJECT = 'H22010402403'
pnpm run dev:ele:project
```

或通过 publish（默认即本院）：

```powershell
cd publish
.\publish-frontend.ps1 -Project H22010402403
```

不指定院点时走产品主线 env，不含本院医保电子凭证等差异。

## overrides 用法

只需覆盖差异文件，目录结构与 `apps/web-ele/src` 一致：

```text
overrides/
└── views/outpatient/charge/register/services/
    └── charge-type-category.ts   # 覆盖 #/views/.../charge-type-category
```

导入仍写 `#/views/...`，Vite 插件 `openhis-project-override` 会在构建时自动解析到 overrides。

## 定制分级（与后端 §11 一致）

| 级别         | 做法                | 放哪                                   |
| ------------ | ------------------- | -------------------------------------- |
| L0 参数      | 环境变量            | `.env.production` / `.env.development` |
| L1 开关      | 后端 Feature / 字典 | 主线 + 配置                            |
| L2 页面/流程 | 单文件 override     | `overrides/`                           |
| L3 大模块    | 独立 workspace 包   | 待产品化后评估                         |

## 待迁移

以下逻辑仍在主线，后续可逐步迁入 `overrides/` 或改为后端配置：

- `apps/web-ele/src/views/outpatient/charge/register/services/charge-type-category.ts` 中吉林参保地回退映射

## 已覆盖

| 文件                                  | 说明                           |
| ------------------------------------- | ------------------------------ |
| `overrides/layouts/auth-copyright.ts` | 登录页底部授权文案（本院专用） |
