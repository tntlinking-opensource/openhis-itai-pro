# zxplat-module-{name} 结构优化手册（模板）

> 复制本文件为 `docs/模块/zxplat-module-{name}/OPTIMIZATION-PLAYBOOK.md`，替换 `{name}` / `{mod}` / 层名。  
> **新建扩展包步骤**：[新扩展包开发指南.md](../架构/新扩展包开发指南.md)  
> **统一约定**：[扩展模块架构统一约定.md](../架构/扩展模块架构统一约定.md)  
> **样板**：[`模块/zxplat-module-einvoice-boss/`](../模块/zxplat-module-einvoice-boss/) · [`模块/zxplat-module-work-injury/`](../模块/zxplat-module-work-injury/)  
> 架构真源：[模块化开发规范.md](../架构/模块化开发规范.md)

---

## 0. 模块画像（必填）

| 项 | 本模块取值 |
|----|-----------|
| 模块类型 | `网关型六层` / `scheme-only` / `领域型` |
| 出站方式 | HTTP GatewayClient / Win 客户端 / 无出站 |
| application 层 | 有 / UI 在主应用 |
| host 层 | 有 / 无 |
| Maven 子模块 | `module-{short}-*` |
| 当前 Java 文件数 | domain ___ · gateway ___ · bridge ___ · application ___ |

---

## 1. 目标与成功标准

- [ ] 每层 README：边界 / 包树 / 命名 / 与样板差异
- [ ] Enforcer：application 禁 bridge；bridge 禁 application
- [ ] ArchitectureTest：与 Enforcer 互补
- [ ] `mvn compile test` 每阶段通过

---

## 2. 适用层（勾选）

- [ ] domain
- [ ] gateway（含 scheme）
- [ ] bridge
- [ ] application
- [ ] host

**本模块跳过的阶段**：（例：领域型 → 跳过 gateway）

---

## 3. 分阶段计划

| 阶段 | 范围 | 动作 | 状态 |
|------|------|------|------|
| P0 | 基线 | README + Playbook + Enforcer + ArchTest | ⬜ |
| P1–P3 | application | dto→request/response | ⬜ / N/A |
| P2 | domain | 根级泄漏→cap/command/result | ⬜ |
| P5–P6 | bridge | orchestration→{cap}/workflow | ⬜ |
| P10 | gateway | client + scheme | ⬜ / N/A |
| Doc | 全层 | JavaDoc + `docs/模块/…` 同步 | ⬜ |

---

## 4. 脚本约定

- PowerShell 写 Java：**UTF-8 无 BOM**
- 每阶段：`mvn compile test -pl zxplat-plugin/zxplat-module-{name} -am`

---

## 5. Enforcer 速查

**application** 禁：本模块 `bridge`、主应用 application 反向依赖  
**bridge** 禁：本模块 `application`、已废弃 support 层

---

## 6. 参考

| 文件 | 用途 |
|------|------|
| `docs/模块/zxplat-module-{name}/说明.md` | Module 总览 |
| [代码地图.md](../代码地图.md) | 仓级索引 |
| [prompts/](../../prompts/) | 成码 / DM / AS Skills |
