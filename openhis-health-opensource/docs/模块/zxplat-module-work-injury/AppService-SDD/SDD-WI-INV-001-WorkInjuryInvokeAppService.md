# SDD-WI-INV-001 · 工伤本机调用 详细设计

> **详细设计编号**：SDD-WI-INV-001
> **应用服务编号**：AS-WI-INV-001

## 1. 范围

对应 `WorkInjuryInvokeAppService`（`module-wi-jilin-application`）。

## 2. 流程要点

（Mode B 迁入占位；实现以源码为准。）

## 3. 依赖

- domain：`com.zxplat.plugin.wi.domain`
- 出站：Win invoke / CHS 结算实体（经 bridge，application **禁止**依赖 bridge）