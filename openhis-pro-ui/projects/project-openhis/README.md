# project-openhis · OpenHIS 产品演示

与后端 `zxplat/zxplat-projects/project-openhis` 对称的前端院点工程（SiteId=`openhis`）。

## 目录

```text
project-openhis/
├── .env.development
├── .env.production
├── overrides/preferences.ts
└── README.md
```

## 构建与开发

在 `zxplat-ui` 根目录：

```powershell
$env:ZXPLAT_PROJECT = "openhis"
node scripts/zxplat-project/run-web-ele.mjs -- pnpm -F @zxplat/web-ele run dev
node scripts/zxplat-project/run-web-ele.mjs -- pnpm vite build --mode production
```
