# GAP-ARCH-006 功能矩阵启动自检

> **问题类型**：读模型渠道与全局架构
> **严重度**：中危
> **影响范围**：全局
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-014](../09-读模型渠道与全局架构/SYS-IMP-014-渠道对账读模型与全局幂等.md)

---

## 1. 问题描述

可选模块 Extension 无功能矩阵启动自检

## 2. 发生场景

`bookingQueryPort==null`、`dispenseTraceGateExtension.isEnabled()==false`、`chargeInsuranceExtension.isEnabled()==false` 等 **静默降级**；运维难以发现配置错误。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

1. `application.yml` 声明 **requiredModules**: `[booking, chs, trace-gate]`；2. `ModuleHealthIndicator` 启动时校验 Bean + Client；3. 管理端展示功能矩阵（绿/黄/红）。


## 6. 涉及代码

- Spring Boot `ApplicationRunner` 或 Actuator
- 对齐 IMP-008 ACL-006、IMP-013 REG-001

## 7. 验收用例

- required 含 booking 但 Client 缺失 → 启动失败或 WARN 升级 FAIL。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
