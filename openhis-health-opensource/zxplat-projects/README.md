# projects — 院点工程

每个院点**独立一个文件夹**（Maven 模块即该目录本身，无 `backend/` 子层）：

```text
projects/
  project-H{定点码}/
    README.md
    pom.xml
    src/
docs/projects/
  project-H{定点码}/   # 院点同伴文档（与代码同名）
```

- **UI overlay**：独立前端仓 [`zxplat-ui`](https://gitee.com/wuhan-xinzhi-xinyuan-software/zxplat-ui.git) 的 `projects/project-*`
- 聚合 POM：`projects/pom.xml`（`zxplat-projects`）
- 含定点院点 `project-H*` 与产品演示 `project-openhis`
- 密钥不进仓

## 构建

```bash
mvn -pl projects/project-H22010202493 -am package -DskipTests
```

```bash
# 前端（同级 zxplat-ui）
$env:ZXPLAT_PROJECT = "H22010202493"
node scripts/zxplat-project/run-web-ele.mjs -- pnpm vite build --mode production
```
