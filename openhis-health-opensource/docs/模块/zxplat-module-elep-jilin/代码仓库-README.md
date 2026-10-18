# zxplat-module-elep-jilin

吉林省电子处方（ELEP）扩展 Module（v3）。

> **v3 真源**：`zxplat-plugin/whale-elep-jilin` 已下线。

---

## 六层模块

| 层 | Maven 模块 | 职责 | README |
|----|------------|------|--------|
| **domain** | `module-elep-jilin-domain` | Command/Result、access 端口 | [domain](./module-module-elep-jilin-domain/README.md) |
| **gateway** | `module-elep-jilin-gateway` | GM 签名、`ElepGatewayClient` | [gateway](./module-module-elep-jilin-gateway/README.md) |
| **bridge** | `module-elep-jilin-bridge` | ExtensionImpl、A000x Invoker | [bridge](./module-module-elep-jilin-bridge/README.md) |
| **application** | `module-elep-jilin-application` | Workflow、hisproxy、PDF | [application](./module-module-elep-jilin-application/README.md) |
| **host** | `module-elep-jilin-host` | Remote Client | [host](./module-module-elep-jilin-host/README.md) |
| **starter** | `module-elep-jilin-starter` | 安装入口 | — |

## 结构优化（P0–P10-9 ✅）

| 阶段 | 要点 |
|------|------|
| P0 | 各层 README + `scripts/` |
| P2 | domain `dto/facade` → `{cap}/command\|result\|access` |
| P7–P8 | `orchestration/` → `invoker/` + `workflow/` |
| P9 | bridge cap 包 |
| P10-A/C/D | `crypto`→`session/`；scheme cap 目录 |
| P10-9 | Invoker 迁 bridge + domain access 端口 |

脚本索引：`scripts/elep-p*.ps1`、`scripts/fix-bom.ps1`

## 文档

- [OPTIMIZATION-PLAYBOOK.md](./OPTIMIZATION-PLAYBOOK.md) — 结构优化推广手册
- [13-实现状态与文档对照](../../docs/模块/zxplat-module-elep-jilin/13-实现状态与文档对照.md)
- [17-zxplat-module-elep-jilin-架构-v3](../../docs/模块/zxplat-module-elep-jilin/17-zxplat-module-elep-jilin-架构-v3.md)
