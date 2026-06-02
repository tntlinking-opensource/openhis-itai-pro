# HIS 医院信息管理系统

基于 [Vue Vben Admin](https://github.com/vbenjs/vue-vben-admin) 的医院信息管理系统前端，采用 Monorepo 架构，主应用使用 Vue 3 + Element Plus。

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

## 项目结构

```
his-ui/
├── apps/
│   └── web-ele/          # 主应用（Element Plus）
│       └── src/
│           ├── api/      # 接口
│           ├── views/    # 页面（仓库、基础数据、系统、门诊等）
│           ├── router/
│           └── ...
├── packages/             # 公共包
└── pnpm-workspace.yaml
```

## 主要功能模块

- **仓库管理**: 盘点、采购、调拨、报损、盘点等
- **基础数据**: 患者、药品、收费类型、病区/库位、诊断与治疗、编码等
- **系统管理**: 菜单、用户权限、租户、供应商、系统设置等
- **门诊/收费**: 挂号、医生工作站等
- **数据分析**: 访问统计等

## 开发说明

- 代码规范：提交前会执行 ESLint、Prettier、lint-staged（lefthook pre-commit）
- 类型检查：`pnpm run check:type`
- 格式化：`pnpm run format`
