<!-- zxplat-curated -->
# zxplat-module-chs-jilin · 文档索引

> **目录**：`docs/模块/zxplat-module-chs-jilin/`（原 `zxplat-module-chs-jilin/`）  
> **★ v3 真源**：[17-zxplat-module-chs-jilin-架构-v3.md](./17-zxplat-module-chs-jilin-架构-v3.md) · [13-实现状态与文档对照.md](./13-实现状态与文档对照.md)  
> 代码：[代码仓库-README.md](./代码仓库-README.md) · Maven/Java 包 [maven/](maven/) · [java/](java/)

## 数据表

- [YB表说明.md](../YB表说明.md) — 全部 `yb_*` 表用途速查（含工伤 / 电子处方）

## 联调缺陷（近期）

- [29-门诊日结与医保退费缺陷-问题与修改方案-2026-07-27.md](./29-门诊日结与医保退费缺陷-问题与修改方案-2026-07-27.md) — 日结 -5.01 / 退费写残；**BUG-1/2/4/5 已改，待 R1–R5 回归**
- [30-跨省挂号费强制自费与失败改自费.md](./30-跨省挂号费强制自费与失败改自费.md) — 跨省强制挂号费自费；预结算失败弹窗确认后自费重试

## 阅读顺序

| 序号 | 文档 | 用途 |
|------|------|------|
| **17** | [17-zxplat-module-chs-jilin-架构-v3.md](./17-zxplat-module-chs-jilin-架构-v3.md) | **架构 v3 真源** |
| **13** | [13-实现状态与文档对照.md](./13-实现状态与文档对照.md) | **实现进度 / 类名对照** |
| **00** | [00-功能清单.md](./00-功能清单.md) | 范围、Wave |
| **01** | [01-概要设计.md](./01-概要设计.md) | v2 HLD（存量；包名以 17 为准） |
| **02** | [02-交互说明.md](./02-交互说明.md) | insurance* 前端 API |
| **AS** | [AppService/README.md](./AppService/README.md) | **页面边界 / IF 契约**（对齐 health AS） |
| **SDD** | [AppService-SDD/README.md](./AppService-SDD/README.md) | **实现映射**（包结构、HTTP↔IF、编排落点） |
| **03～12 / 25** | 详细设计 | 业务 LLD（**05 协同单与 Worker 已作废**，见 [13 §2.3](./13-实现状态与文档对照.md)；**31xx** 见 [25-明细审核31xx-详细设计](./25-明细审核31xx-详细设计.md)） |
| **—** | [模块说明-代码结构.md](./模块说明-代码结构.md) | Maven + Java 包 |
| **Playbook** | [OPTIMIZATION-PLAYBOOK.md](./OPTIMIZATION-PLAYBOOK.md) | **结构优化推广手册**（模板：[_templates/OPTIMIZATION-PLAYBOOK-template.md](../_templates/OPTIMIZATION-PLAYBOOK-template.md)） |

## 工伤（独立 Module）

工伤文档在 [zxplat-module-work-injury/](../zxplat-module-work-injury/)，代码在 `zxplat-module-work-injury`（`wi-jilin-*`）。

## 接口参考

- [14-报文格式与约定.md](./14-报文格式与约定.md)
- [15-接口总表.md](./15-接口总表.md)
- `apis/**` — 单接口（curated 保护）

## 成码

- Rule：`docs/../.cursor/rules/zxplat-module-chs-jilin-codegen.mdc`
- 通用：`docs/../.cursor/skills/代码生成通用规范/`
