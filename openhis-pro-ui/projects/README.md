# OpenHIS 前端院点工程

与后端 `whale-sever/whale-project/project-H*` 对称，按定点编码组织院点差异。

**架构文档**：[whale-doc/01-架构/前端院点定制方案.md](../../whale-doc/01-架构/前端院点定制方案.md)

> 开源导出时 **不含** 本目录，见 `tools/opensource/opensource-export-manifest.json`。

```text
projects/
└── project-H22010402403/     # 朝阳中医院
    ├── .env.production
    ├── .env.development
    ├── overrides/            # 编译期 #/ 路径重载
    └── README.md
```

## 常用命令

```powershell
# 在 openhis-pro-ui 根目录
$env:OPENHIS_PROJECT = 'H22010402403'
pnpm run build:ele:project    # 生产构建
pnpm run dev:ele:project      # 本地开发

# 或通过 publish（默认本院 H22010402403）
cd publish
.\publish-frontend.ps1
.\publish-frontend.ps1 -NoProject   # 产品主线，不含院点 env
```

## 新建院点

1. 复制 `project-H22010402403` 为 `project-Hxxxxxxxxxx`
2. 修改 `.env.production` / `.env.development`
3. 差异代码放入 `overrides/`（目录对齐 `apps/web-ele/src`）
4. 构建时设置 `OPENHIS_PROJECT=Hxxxxxxxxxx`
