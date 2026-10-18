# zxplat-module-work-injury

吉林省工伤（WORK_INJURY）扩展 Module，对齐 `zxplat-module-chs-jilin` v3 架构。

## 与 CHS 模块的差异

| 项 | CHS (`zxplat-module-chs-jilin`) | 工伤（本模块） |
|----|-------------------------------|--------------|
| 平台出站 | `InsuranceGatewayClient` HTTP | **OpenHIS.Win :5000** prepare/submit |
| gateway 层 | HTTP + scheme | **仅 scheme**（无 HTTP 客户端） |
| application 层 | 医保 UI AppService | **`module-wi-jilin-application`**（`/api/app/workInjuryInvoke/*`） |
| host 层 | gateway-proxy | **无** |
| 开关 | `zxplat.modules.chs.enabled` | `zxplat.modules.work-injury.enabled` |

## 子模块

```text
module-wi-jilin-domain/    com.zxplat.plugin.wi.domain（yb_wi_* + 复用 module-chs-jilin-domain 结算/审计）
module-wi-jilin-gateway/   com.zxplat.plugin.wi.gateway.scheme（Wi2201Input 等）
module-wi-jilin-bridge/    ExtensionImpl + Orchestrator + Invoker + ContextLoader
module-wi-jilin-application/  com.zxplat.plugin.wi.application（WorkInjuryInvokeAppService）
module-wi-jilin-starter/   Flyway wi + WiJilinModuleAutoConfiguration
```

## 编排链

```text
module-wi-jilin-application（工伤专属 HTTP）
  → domain invoke/register access → bridge InvokerImpl

health-application（HIS 嵌入）
  → WorkInjury*Extension（health-infrastructure）
  → *ExtensionImpl（bridge）
  → WorkInjury*Orchestrator
  → yb_wi_* DomainService + yb_invoke_log / yb_settlement_record
  → OpenHIS.Win（前端 invoke 循环）
```

## 装配

```xml
<dependency>
  <groupId>com.openhis</groupId>
  <artifactId>module-wi-jilin-starter</artifactId>
</dependency>
```

```properties
zxplat.modules.work-injury.enabled=true
```

## 结构优化

迁移方法论见 [`OPTIMIZATION-PLAYBOOK.md`](./OPTIMIZATION-PLAYBOOK.md)。

| 阶段 | 状态 |
|------|------|
| P2 domain command/result | ✅ |
| P3–P4 gateway scheme | ✅ |
| P5–P6 bridge cap/workflow | ✅ |
| P9 去 health-module-support | ✅ |
| Doc JavaDoc | ✅ |
| P7 assembler/parser + Invoker | ✅ |
| P8 domain access | ✅ |
| playbook 收尾 + whale-doc 同步 | ✅ |

脚本：`scripts/wi-p2-domain-rename.ps1` … `wi-p5-p6-bridge-restructure.ps1`

## 明确不做

- contracts / Facade 层（v3 用 Extension + Orchestrator）
- Whale JVM 直调工伤 DLL
- 迁入 CHS 国标医保代码

原 `whale-chs-jilin` 工伤存量已迁入本模块并下线旧包。
