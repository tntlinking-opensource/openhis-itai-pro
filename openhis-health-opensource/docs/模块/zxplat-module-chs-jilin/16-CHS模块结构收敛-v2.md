# CHS 模块结构收敛 v2 — 实施说明

> **文档性质**：架构 ADR + Phase 5 落地指南（**已完成**）  
> **真源（防幻觉）**：[01-概要设计.md](./01-概要设计.md) — 包名、类名、调用链以真源为准  
> **关联**：[模块化开发规范.md](../../01-架构/模块化开发规范.md)  
> **版本**：v2.0 | **日期**：2026-06-02

---

## 1. 决策摘要

| # | 决策 | 说明 |
|---|------|------|
| D1 | **zxplat-module-chs-jilin 仅两层** | `chs-jilin-domain` + `chs-jilin-application`；废弃 `chs-api` / `chs-client` / `chs-server` |
| D2 | **`yb_*` 归属 CHS** | 实体 + Flyway `db/migration/chs` V1～V3 |
| D3 | **部署参数** | `zxplat.chs.deployment.mode=embedded \| remote` |
| D4 | **remote 方案 1A** | 前端仍调 `/api/app/insurance*`；adapter HTTP 代理至 `/api/app/chs*` |
| D5 | **adapter 合并 insurance** | `chs-jilin-bridge` 含 AppService 代理 + settlement 挂接 + remote 客户端 |
| D6 | **三扩展骨架对齐** | CHS 与 EMR/MRMS 同构 `config/module/*AppService`；**增量** `application/deployment` + `integration.chs.remote` |

---

## 2. 当前模块结构（Phase 5 ✅）

### 2.1 zxplat-module-chs-jilin

```text
zxplat-module-chs-jilin/
├── chs-jilin-domain/
│   ├── com.zxplat.plugin.chs.api.*           # InsuranceGatewayClient、infno DTO
│   └── com.zxplat.plugin.chs.domain.catalog.*
└── chs-jilin-application/
    ├── application.config/              # ChsAutoConfiguration
    ├── application.module/              # ChsModuleDescriptor
    ├── application.remote/service/        # Chs*AppService、*FacadeAppService（remote HTTP）
    ├── application.gateway/             # 国密、HTTP、9001、ChsGatewayAppService
    └── application.deployment/          # ChsServerApplication、ChsGatewayController（独立进程）
    └── resources/db/migration/chs/
```

### 2.2 chs-jilin-bridge

```text
chs-jilin-bridge/
├── whale.chs.adapter/client|internal   # ClientImpl + 编排辅助
└── integration.insurance/  # ExtensionImpl（含 workinjury/）、IntegrationMapper

chs-jilin-host/
└── integration.chs.remote/client/     # ChsRemoteAppServiceClient（appservice）、*RemoteClientImpl

health-app -Pwith-chs:        chs-jilin-starter（adapter + application）
health-app -Pwith-chs-remote: chs-jilin-host（仅 adapter）
```

---

## 3. 统一 1A 调用（摘要）

详见 [01-概要设计.md §4](./01-概要设计.md)。

| facade | adapter 前端 API | CHS AppService | remote HTTP |
|--------|-----------------|----------------|-------------|
| `ChsSessionOperations` | `insuranceSession/*` | `chsSession` | `/api/app/chsSession/*` |
| `ChsNationalCatalogOperations` | `insuranceNationalCatalog/*` | `chsNationalCatalog` | `/api/app/chsNationalCatalog/*` |
| `ChsGatewayOperations` | （adapter 内部） | `chsGateway` | `/api/app/chsGateway/*` |

---

## 4. 配置

```yaml
openhis:
  modules.chs.enabled: true
  chs.deployment.mode: embedded   # embedded | remote
  chs.deployment.remote.base-url: http://127.0.0.1:8091
```

---

## 5. Phase 5 完成状态

| ID | 状态 | 说明 |
|----|------|------|
| 5.0 | ✅ | `health-application-insurance` → `chs-jilin-bridge` |
| 5.1 | ✅ | chs-api/server → chs-domain/application |
| 5.2 | ✅ | yb_* + Flyway 迁入 chs |
| 5.3 | ✅ | remote 客户端进 adapter；`deployment.mode` |
| 5.4 | ✅ | 目录/会话迁入 chs-jilin-application；adapter 1A 代理 |
| 5.5 | ✅ | 骨架对齐 + 网关 remote 统一 AppService；文档真源 |

---

## 6. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.0 | 2026-06-02 | 初版 ADR |
| v2.0 | 2026-06-02 | Phase 5 完成；结构/包名/1A 三类 facade；指向 04-当前态真源 |
