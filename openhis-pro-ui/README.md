# zxplat-ui

ZXPlat 前端 monorepo（**独立仓库**，不在 ZXPlat 主仓内）。

- **仓库**：https://gitee.com/wuhan-xinzhi-xinyuan-software/zxplat-ui.git
- **包作用域**：@zxplat/* · @zxplat-core/*
- **应用**：apps/web-ele（主线）· apps/web-antd（壳）
- **院点 overlay**：本仓 `projects/project-H*/`（env + overrides）

## 目录

```text
zxplat-ui/
  apps/web-ele/          # 产品主线
  projects/
    project-H…/          # 院点 overlay（与定点码对应）
      .env.*
      overrides/
      README.md
  scripts/zxplat-project/run-web-ele.mjs
```

主仓 `zxplat/projects/<id>/` 仅保留 **backend + docs**；UI 以本仓为准。

## 与主仓协作（同级目录）

```text
03.ZXPlat/
  zxplat/       # 后端 + 院点 backend/docs
  zxplat-ui/    # 前端 + 院点 overlay
```

```bash
git clone https://gitee.com/wuhan-xinzhi-xinyuan-software/ZXPlat.git zxplat
git clone https://gitee.com/wuhan-xinzhi-xinyuan-software/zxplat-ui.git zxplat-ui
cd zxplat-ui && pnpm install
```

## 本地启动

在本仓根目录执行（pnpm workspace，不要进 `apps` 单独装依赖）。

```powershell
# 主线 Element Plus（apps/web-ele，端口 5778）
pnpm run dev:ele

# Ant Design 壳（apps/web-antd，端口 5779）
pnpm run dev:antd

# 带院点 overlay（env + projects/project-{定点码}/overrides）
node scripts/zxplat-project/run-web-ele.mjs --project H22010402403 -- pnpm vite --mode development --port 5778
```

日常开发用 `dev:ele`。要对齐院点 env 和 overrides，用第三条，把定点码换成目标院点。

```bash
# 院点构建示例
$env:ZXPLAT_PROJECT = "H22010200596"
node scripts/zxplat-project/run-web-ele.mjs -- pnpm vite build --mode production
```

脚本优先解析本仓 `projects/project-{id}/`；若无则回退旧路径（主仓 `…/ui`，兼容迁移期）。
