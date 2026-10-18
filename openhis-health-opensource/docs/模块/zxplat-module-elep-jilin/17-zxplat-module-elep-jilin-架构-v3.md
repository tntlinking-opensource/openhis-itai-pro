# zxplat-module-elep-jilin 架构 v3 — Gateway + Bridge

> **文档性质**：吉林省电子处方（ELEP）重架构决策  
> **日期**：2026-07-11  
> **代码**：`zxplat-plugin/zxplat-module-elep-jilin/`

---

## 1. 与 v2 的差异

| 项 | v2 | v3 |
|----|----|-----|
| 父工程 | `whale-elep-jilin` | **`zxplat-module-elep-jilin`** |
| 集成层 | `elep-jilin-adapter` | **`elep-jilin-bridge`** |
| 契约 | `elep-jilin-contracts` | **无**；Command/Result 在 `elep-jilin-domain` |
| 出站 HTTP | Coordinator 内联 | **`elep-jilin-gateway`**（GM 签名） |
| 业务编排 | `ElepPrescriptionCoordinator` (~900 LOC) | **`ElepPrescriptionOrchestrator` + Invoker** |
| HTTP 中转 | `elep-jilin-http-starter` | **`elep-jilin-host`** |

---

## 2. 六层模块

| 层 | 模块 | 职责 |
|----|------|------|
| domain | `elep-jilin-domain` | Command/Result、处方快照、`yb_elep_prescription` |
| gateway | `elep-jilin-gateway` | GM 加解密、`ElepGatewayClient` |
| bridge | `elep-jilin-bridge` | `ElepPrescriptionExtensionImpl`、`ElepIntegrationMapper` |
| application | `elep-jilin-application` | `ElepPrescriptionOrchestrator`、`ElepA0001`–`A0006` Invoker、Flyway |
| host | `elep-jilin-host` | 网段隔离时的 Remote Client |
| starter | `elep-jilin-starter` | embedded 装配 |

---

## 3. 依赖方向

```text
domain ← gateway ← application
domain ← bridge
bridge  → gateway
bridge  → health-domain（禁止 health-module-support、application）
starter → domain + gateway + bridge + application
host    → gateway（remote client；不含 application / bridge）
```

---

## 4. Extension 链

```text
health-app
  └─ ElepPrescriptionExtension (SPI)
       └─ ElepPrescriptionExtensionImpl (bridge)
            └─ JilinElepPrescriptionClient（application 编排入口）
                 └─ ElepPrescriptionOrchestrator
                      └─ ElepA0001Invoker … ElepA0006Invoker
                           └─ elep-jilin-gateway（GM 签名 HTTP）
```

---

## 5. Invoker 命名

| Invoker | 接口 |
|---------|------|
| `ElepA0001Invoker` | 电子处方上传预核验 |
| `ElepA0002Invoker` | 电子处方医保电子签名 |
| `ElepA0003Invoker` | 电子处方上传 |
| `ElepA0004Invoker` | 电子处方撤销 |
| `ElepA0005Invoker` | 电子处方信息查询 |
| `ElepA0006Invoker` | 电子处方取药结果查询 |

---

## 6. Remote 部署

`zxplat.elep.deployment.mode=remote` 时，HIS 侧经 `elep-jilin-host` Remote Client 访问嵌入式 ELEP AppService。

---

## 7. 迁移状态

| 阶段 | 状态 |
|------|------|
| scaffold + contracts→domain | ✅ |
| adapter→bridge | ✅ |
| gateway 从 application 拆出 | ✅ |
| http-starter→host | ✅ |
| Orchestrator + Invokers | ✅ |
| 根 pom / BOM 更新 | ✅ |
| 删除 `whale-elep-jilin` | ✅ |
