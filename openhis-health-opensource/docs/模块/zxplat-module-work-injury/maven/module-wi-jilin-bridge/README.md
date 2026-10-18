# module-wi-jilin-bridge

工伤 HIS 集成：ExtensionImpl + Workflow + Invoker + ContextLoader。

## 双根包

| 根包 | 职责 |
|------|------|
| `com.zxplat.plugin.wi.bridge.integration.workinjury` | ExtensionImpl、IntegrationMapper |
| `com.zxplat.plugin.wi.bridge.*` | Workflow、Invoker、Context |

## 能力切片（P5+P6 ✅）

```text
com.zxplat.plugin.wi.bridge/
  invoke/workflow/     WorkInjuryInvokeOrchestrator
  invoke/support/      InvokeLogSupport
  register/workflow/   WorkInjuryRegisterOrchestrator
  register/invoker/    Build2301PayloadInvokerImpl
  settle/workflow/     OutpatientSettleOrchestrator、SettleStepPlanner
  context/             ContextLoader、ContractResolver
  config/
```

Assembler/Parser 已迁入 gateway（P7 ✅）。

## JavaDoc 约定

与 CHS bridge 对齐（标杆：`InsuranceRegisterBenefitQueryExtensionImpl`、`Op1101Invoker`）。

| 类型 | 类注释 | 方法注释 |
|------|--------|----------|
| `*ExtensionImpl` | SPI 接口 + Health 调用方 + Bridge 委托类 | 每个 `@Override` 一行（含 infno/场景） |
| `*Orchestrator` | phase/infno 链 + 副作用表 | public 方法 `@param`/`@return` |
| `*Invoker` | 实现 domain access + infno | `invoke` 方法完整参数 |
| `*Support` / Resolver | 无 Win 出站时的纯辅助 | 公共方法一行 |
| `IntegrationMapper` | Extension DTO ↔ domain | static 映射方法一行 |

Workflow 类注释须写清：**不调 Win** 还是 **驱动 prepare/submit 循环**。

## 依赖（P9 ✅）

HIS 读侧仅经 `health-domain` `*QueryService`；**禁止** `health-module-support`（Enforcer 已 ban）。

## 脚本

`scripts/wi-p5-p6-bridge-restructure.ps1`
