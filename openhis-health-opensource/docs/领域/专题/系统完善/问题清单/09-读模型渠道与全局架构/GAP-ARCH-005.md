# GAP-ARCH-005 全局 API 幂等

> **问题类型**：读模型渠道与全局架构
> **严重度**：中危
> **影响范围**：全局
> **优先级**：P1
> **状态**：待实施
> **父文档**：[SYS-IMP-014](../09-读模型渠道与全局架构/SYS-IMP-014-渠道对账读模型与全局幂等.md)

---

## 1. 问题描述

无全局 API Idempotency-Key 中间层

## 2. 发生场景

IMP-002 FIN-001、IMP-006 CHS-001 等 **各自**建议 Idempotency-Key；**无**网关/AppService 层统一 `(tenant, operator, key, api)` 表。

## 3. 根因分析

见父文档；核心为读 Gate + 无源态条件更新或 Gate 缺失。

## 4. 业务影响

账实/账价不一致，或合规与安全风险。

## 5. 修改方案

1. `AppServiceAspect` 或 Filter 解析 `Idempotency-Key`；2. 首次执行落库 response 快照；3. 重复请求返回相同 outcome（HTTP 200 + 同 paymentId）；4. 覆盖：正式结算、confirm 医保、核发、滚费 Job 触发接口。


## 6. 涉及代码

- `whale-framework` 或 `health-application` 新建 `IdempotencySupport`
- 各 P0 AppService 标注 `@Idempotent`

## 7. 验收用例

- 同 key 双 POST confirm → 1 条 SUCCESS。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
