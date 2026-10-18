# zxplat-module-booking

ZXPlat 预约扩展 Module（v3），对齐 `zxplat-module-chs-jilin`。

> **v3 真源**：`zxplat/zxplat-module-booking` 已下线；由本 Module 承载。

---

## 核心原则

| 原则 | 说明 |
|------|------|
| **可选扩展** | `-Pwith-booking` / `zxplat.modules.booking.enabled` |
| **无 contracts** | Command/Result 在 `module-booking-domain` |
| **无 gateway** | 领域型扩展，出站渠道通知 P2 占位 |
| **bridge 禁 support** | Enforcer 禁止 `health-module-support`、`module-booking-application` |

---

## 四层模块

| 层 | Maven 模块 | 职责 |
|----|------------|------|
| **domain** | `module-booking-domain` | 日程/号源/预约实体、DomainService、Command/Result |
| **bridge** | `module-booking-bridge` | 4×ExtensionImpl、4×Workflow Orchestrator、ContextLoader |
| **application** | `module-booking-application` | UI AppService、Flyway(booking) |
| **starter** | `module-booking-starter` | HIS embedded 安装入口 |

### Extension → Orchestrator

| Extension | Orchestrator | 包 |
|-----------|--------------|-----|
| `OutpatientBookingQueryExtension` | `BookingOutpatientQueryOrchestrator` | `outpatient/workflow/` |
| `OutpatientBookingRegistrationExtension` | `BookingOutpatientRegistrationOrchestrator` | `outpatient/workflow/` |
| `InpatientBedBookingQueryExtension` | `BookingInpatientBedQueryOrchestrator` | `inpatient/workflow/` |
| `InpatientBedBookingConsumeExtension` | `BookingInpatientBedConsumeOrchestrator` | `inpatient/workflow/` |

### 依赖方向

```text
domain ← bridge（health-domain *QueryService）
domain ← application
starter → bridge + application + domain
```

---

## 装配

```bash
mvn install -DskipTests -pl zxplat-module-booking -am
```

health-app：`-Pwith-booking` → `module-booking-starter`

---

## 文档

- [OPTIMIZATION-PLAYBOOK.md](./OPTIMIZATION-PLAYBOOK.md) — 结构优化推广手册
- [module-booking-domain/README.md](./module-module-booking-domain/README.md) · [module-booking-bridge/README.md](./module-module-booking-bridge/README.md) · [module-booking-application/README.md](./module-module-booking-application/README.md)
- [13-实现状态与文档对照](../../docs/模块/zxplat-module-booking/13-实现状态与文档对照.md)
- [17-zxplat-module-booking-架构-v3](../../docs/模块/zxplat-module-booking/17-zxplat-module-booking-架构-v3.md)
