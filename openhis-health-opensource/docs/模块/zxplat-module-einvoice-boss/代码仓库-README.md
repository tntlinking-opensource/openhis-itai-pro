# zxplat-module-einvoice-boss

吉林省财政电子票据（Boss）扩展 Module（v3）。

> **v3 真源**：`zxplat-plugin/whale-einvoice-boss` 已下线。

---

## 六层模块

| 层 | Maven 模块 | 职责 | README |
|----|------------|------|--------|
| **domain** | `module-einv-boss-domain` | 票据领域、Command/Result | [domain](./module-einv-boss-domain/README.md) |
| **gateway** | `module-einv-boss-gateway` | `EinvoiceBossGatewayClient`、HTTP 出站 | [gateway](./module-einv-boss-gateway/README.md) |
| **bridge** | `module-einv-boss-bridge` | ExtensionImpl、ContextLoader | [bridge](./module-einv-boss-bridge/README.md) |
| **application** | `module-einv-boss-application` | UI AppService、issue Worker | [application](./module-einv-boss-application/README.md) |
| **host** | `module-einv-boss-host` | Remote forward proxy | [host](./module-einv-boss-host/README.md) |
| **starter** | `module-einv-boss-starter` | 安装入口 | — |

## 文档

- [OPTIMIZATION-PLAYBOOK.md](./OPTIMIZATION-PLAYBOOK.md) — 结构优化推广手册（P0–P10-9 + cap scheme ✅）
- [13-实现状态与文档对照](../../docs/模块/zxplat-module-einvoice-boss/13-实现状态与文档对照.md)
- [17-zxplat-module-einvoice-boss-架构-v3](../../docs/模块/zxplat-module-einvoice-boss/17-zxplat-module-einvoice-boss-架构-v3.md)
