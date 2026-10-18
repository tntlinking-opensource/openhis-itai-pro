# 17 — zxplat-module-booking 架构 v3

## 目标态

```text
health-application
  └─ Extension SPI (4 个预约 Extension)
       └─ *ExtensionImpl (booking-bridge)
            └─ Booking*Orchestrator
                 ├─ booking-domain *DomainService
                 └─ health-domain *QueryService（经 ContextLoader）
```

## 与 CHS v3 对齐

- 父工程 `zxplat-module-booking`；子模块 `{mod}-{layer}`
- **无 contracts**；**无 gateway**（领域型）
- bridge 自写 HIS 读组装（`*ContextLoader`），禁止 `health-module-support`

## 依赖方向

```text
domain ← bridge ← health-domain
domain ← application
starter → bridge + application + domain
```

bridge **禁止**依赖 `booking-application`、`health-module-support`。

## Profile

- `zxplat.modules.booking.enabled=true`
- health-app：`-Pwith-booking` → `booking-starter`
