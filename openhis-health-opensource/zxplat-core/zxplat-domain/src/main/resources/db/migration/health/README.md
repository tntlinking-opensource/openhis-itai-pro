# health Flyway（核心）

位置：`classpath:db/migration/health`

## 口径（全新产品）

- 平台表、Quartz 表**直接**使用 `zxplat_*` / `zxplat_qrtz_*` 前缀创建。
- **无** `whale_*` → `zxplat_*` 改名脚本；不要在旧 whale 库上直接跑本链（请用空库或新库）。
- 业务域表仍为既有前缀（`adm_` / `cln_` / `fin_` / `med_` / `wkf_` 等），与品牌无关。

## 主要脚本

| 版本 | 文件 | 说明 |
|------|------|------|
| V1 | `V1__zxplat_platform.sql` | 平台身份/权限/菜单/审计/序号 + Quartz |
| V2–V7、V9 | 域 DDL | adm/cln/fin/med/wkf/ter/mrms…（**无 V8**；peis 独立 history） |
| V10 | `V10__seed_platform.sql` | 平台种子（术语/菜单/打印模板等全量最终态） |
| — | **无 V11+** | 新变更合入 V1–V10 后删库重建；禁止再往本目录放增补 |

## 既存 whale 库怎么办？

本链面向**新库**。若必须沿用旧库：

1. 先手工把物理表 `whale_*` rename 为 `zxplat_*`（可参考历史脚本思路），或导出数据进新库；  
2. 再按 zxplat 应用启动；  
3. 勿指望 Flyway 在 history 版本已远大于脚本版本时「自动改名」。

链顺序见 `ModularFlywayConfiguration`：health → 业务 Module → 院点 project-* → health-dev → health-final。
