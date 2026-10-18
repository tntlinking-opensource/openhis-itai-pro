# zxplat-module-unionpay-bpc

银联 BPC 被扫收单扩展 Module（v3，对齐 `zxplat-module-chs-jilin`）。

> **v3 真源**：`zxplat-plugin/whale-unionpay-bpc` 已下线；由本 Module 承载。

---

## 核心原则

| 原则 | 说明 |
|------|------|
| **可选扩展** | `zxplat.modules.unionpay-bpc.enabled=false` 时不装配 |
| **无 contracts** | Command/Result 在 `module-bpc-domain` |
| **P0 走 Win** | 被扫 DLL 仍经 OpenHIS.Win 本地桥，不在 JVM gateway 实现 |
| **bridge 禁 support** | `ThirdPartyPaymentExtensionImpl` → `UnionPaySettleOrchestrator` |

---

## 六层模块

| 层 | Maven 模块 | 职责 |
|----|------------|------|
| **domain** | `module-bpc-domain` | `upc_bpc_order`、Command/Result、`BpcSettleDomainService` |
| **gateway** | `module-bpc-gateway` | 院点契约配置、`BpcTransportConstants`（HTTPS 占位） |
| **bridge** | `module-bpc-bridge` | `ThirdPartyPaymentExtensionImpl`、`UnionPaySettleOrchestrator`、Invoker 占位 |
| **application** | `module-bpc-application` | `UnionPayBpcAppService`、Flyway |
| **starter** | `module-bpc-starter` | HIS embedded 安装入口 |

### 命名契约

| Maven 模块 | Java 根包 |
|-----------|----------|
| `module-bpc-domain` | `com.zxplat.plugin.unionpay.bpc.domain` |
| `module-bpc-gateway` | `com.zxplat.plugin.unionpay.bpc.gateway` |
| `module-bpc-bridge` | `com.zxplat.plugin.unionpay.bpc.bridge` + `com.openhis.health.integration.payment` |
| `module-bpc-application` | `com.zxplat.plugin.unionpay.bpc.application` |

---

## 装配

```bash
mvn install -DskipTests -pl zxplat-module-unionpay-bpc -am
```

health-app：`-Pwith-unionpay-bpc` → `module-bpc-starter`

---

## 文档

- [OPTIMIZATION-PLAYBOOK.md](./OPTIMIZATION-PLAYBOOK.md) — 结构优化推广手册
- [module-bpc-domain/README.md](./module-bpc-domain/README.md)
- [module-bpc-gateway/README.md](./module-bpc-gateway/README.md)
- [module-bpc-bridge/README.md](./module-bpc-bridge/README.md)
- [module-bpc-application/README.md](./module-bpc-application/README.md)
- [13-实现状态与文档对照](../docs/模块/zxplat-module-unionpay-bpc/13-实现状态与文档对照.md)

## 迁移脚本（`scripts/`）

| 脚本 | 阶段 |
|------|------|
| `bpc-p2-domain-cap.ps1` | domain cap 分包 |
| `bpc-p5-bridge-cap.ps1` | bridge workflow + invoker |
| `bpc-p1-app-rename.ps1` | application order/service |
| `bpc-p9-bridge-access.ps1` | domain access 端口 |
| `bpc-p10-gateway-client.ps1` | gateway client + session |
| `bpc-p10-scheme.ps1` | gateway scheme cap |
| `bpc-p10-invoker-wire.ps1` | Invoker 接线 gateway |
| `fix-bom.ps1` | UTF-8 BOM 修复 |
