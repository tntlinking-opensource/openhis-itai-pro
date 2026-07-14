# OpenHIS 前端（openhis-pro-ui）

基于 [Vue Vben Admin](https://github.com/vbenjs/vue-vben-admin) 的医院信息系统前端 monorepo，主应用为 Vue 3 + Element Plus（`apps/web-ele`）。

**许可证**：[LGPL-3.0-or-later](LICENSE)（与后端 [whale-sever](../whale-sever/) 一致）。

院点差异放在 `projects/project-H*/`（与后端 `whale-project/` 对称），详见 [whale-doc/01-架构/前端院点定制方案.md](../whale-doc/01-架构/前端院点定制方案.md)。

---

## 技术栈

- **框架**: Vue 3、Vite
- **UI**: Element Plus、Vxe Table
- **状态**: Pinia
- **路由**: Vue Router
- **包管理**: pnpm、Turbo

## 环境要求

- Node.js >= 20.12.0
- pnpm >= 10.0.0

## 快速开始

```bash
# 安装依赖
pnpm install

# 启动主应用（Element Plus 版）
pnpm dev

# 构建主应用
pnpm build:ele
```

院点定制构建（需 `projects/project-H*` 目录）：

```bash
# 示例：朝阳中医院
OPENHIS_PROJECT=H22010402403 pnpm run dev:ele:project
pnpm run build:ele:project
```

## 项目结构

```text
openhis-pro-ui/
├── apps/
│   └── web-ele/          # 主应用（Element Plus）
│       └── src/
│           ├── api/      # 接口
│           ├── views/    # 页面
│           ├── router/
│           └── ...
├── packages/             # 公共包
├── projects/             # 院点定制（project-H*）
│   └── project-H*/
│       ├── .env.production
│       └── overrides/    # 编译期重载 #/ 路径
└── pnpm-workspace.yaml
```

## 主要功能模块

- **库存/库房**: 盘点、采购、调拨、报损等（`views/inventory/`）
- **基础数据**: 患者、药品、收费类型、病区/库位等
- **系统管理**: 菜单、用户权限、租户、供应商等
- **门诊/收费**: 挂号、医生工作站等

## 开发说明

- 代码规范：提交前执行 ESLint、Prettier、lint-staged（lefthook pre-commit）
- 类型检查：`pnpm run check:type`
- 格式化：`pnpm run format`
- 后端联调：默认对接 `whale-health/health-app`（见 `apps/web-ele` 环境配置）

## 许可证

全部前端源码许可以仓库根目录 **[LICENSE](LICENSE)**（GNU LGPL v3 或更高版本）为准。生产构建产物会在 bundle 头部注入 LGPL 版权注释（见 `internal/vite-config/src/plugins/license.ts`）。
