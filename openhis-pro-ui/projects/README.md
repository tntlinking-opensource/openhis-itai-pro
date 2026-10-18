# projects — 院点 UI overlay

与定点医疗机构编码一一对应；**仅前端**（env + `overrides/`）。  
后端 / 院点文档仍在主仓 `zxplat/projects/project-H*/{backend,docs}`。

```text
projects/
  project-H{定点码}/
    .env.development
    .env.production
    overrides/     # 相对 apps/web-ele/src 的 #/ 重载
    README.md
```

构建：

```bash
$env:ZXPLAT_PROJECT = "H22010402403"
node scripts/zxplat-project/run-web-ele.mjs -- pnpm vite build --mode production
```

架构说明见主仓 `docs/架构/前端院点定制方案.md`。
