# 17 — zxplat-module-unionpay-bpc 架构 v3

## 目标态

```text
health-app
  └─ ThirdPartyPaymentExtension (SPI)
       └─ ThirdPartyPaymentExtensionImpl (bridge)
            └─ UnionPaySettleOrchestrator
                 └─ BpcSettleDomainService (domain)
                      └─ BpcOrderRepository
```

UI 链：

```text
UnionPayBpcAppService (application)
  └─ BpcSettleDomainService (domain)
```

## 与 CHS v3 对齐点

- 父工程 `zxplat-module-unionpay-bpc`
- 子模块 `{mod}-{layer}`，目录 = artifactId
- 无 `*-contracts`
- bridge：`ExtensionImpl` + `Orchestrator` + `Invoker`
- gateway：配置与出站 scheme；**禁止** AppService

## P0 / P1 边界

| 阶段 | 被扫/查单执行 | Whale 职责 |
|------|--------------|------------|
| P0 | OpenHIS.Win + 银联 DLL | 预下单、回传落库、幂等 |
| P1+ | `unionpay-bpc-gateway` HTTPS | `BpcScanInvoker` / `BpcQueryInvoker` 实装 |

## 依赖方向

```text
domain ← gateway
domain ← bridge ← gateway
domain ← application
starter → bridge + application + gateway + domain
```

bridge **禁止**依赖 `unionpay-bpc-application`、`health-module-support`。

## 关键配置

- `zxplat.modules.unionpay-bpc.enabled=true`
- `unionpay-bpc-contract.properties`（gateway classpath 或 `UNIONPAY_BPC_CONTRACT_CONFIG_PATH`）
