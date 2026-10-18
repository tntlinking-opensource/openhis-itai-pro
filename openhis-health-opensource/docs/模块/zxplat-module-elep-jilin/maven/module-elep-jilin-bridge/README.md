# elep-jilin-bridge

ELEP health 集成层：ExtensionImpl、A000x Invoker（实现 domain access）。

## 职责边界

| 允许 | 禁止 |
|------|------|
| `ElepPrescriptionExtensionImpl` | 依赖 application |
| `{cap}/invoker/` 注入 `ElepGatewayClient` | 复制 gateway 业务 Result |
| 实现 domain `prescription/access/*InvokeAccess` | `health-module-support` |

## 包结构

```text
com.zxplat.plugin.elep.bridge.integration.elep/
  ElepPrescriptionExtensionImpl
  mapper/ElepIntegrationMapper

com.zxplat.elep.bridge/
  prescription/invoker/   ElepA0001–A0006Invoker
  prescription/context/   （HIS enrichment 扩展点）
  catalog/context/
  config/                 ElepJilinBridgeAutoConfiguration
  module/                 ModuleDescriptor
```

## access 对照

| domain 端口 | bridge 实现 |
|-------------|-------------|
| `ElepA0001InvokeAccess` | `ElepA0001Invoker` |
| `ElepA0002InvokeAccess` | `ElepA0002Invoker` |
| … | … |
| `ElepA0006InvokeAccess` | `ElepA0006Invoker` |

**架构测试**：`ElepJilinBridgeArchitectureTest` 禁止 main 源码 import `elep-jilin-application.*`。

## 命名·脚本

- Invoker JavaDoc：`【A000x】` 前缀 + 平台路径；实现 domain `*InvokeAccess`

| 阶段 | 脚本 | 状态 |
|------|------|------|
| P9 | `scripts/elep-p9-bridge.ps1` | ✅ |
| P10-9 | `scripts/elep-p10-invoker-bridge.ps1` | ✅ |

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md).

## 与 CHS 差异

| 维度 | CHS bridge | ELEP bridge |
|------|------------|-------------|
| Invoker 键 | infno | **A000x** OpCode |
