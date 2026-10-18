# GAP-REG-001 追溯 Gate 可选导致无 CHS 无码核发

> **问题类型**：监管合规与追溯
> **严重度**：**高危**（飞检「应扫未扫」）
> **影响范围**：门+住
> **优先级**：P0
> **状态**：部分缓解（2026-07-13 SPI 合并）；院内 mandatory 仍待实施
> **父文档**：[SYS-IMP-013](../08-监管合规与追溯/SYS-IMP-013-监管合规链与核发追溯.md)
> **关联 IF/BR**：追溯码 TRC-WIDE-R-001；3505 监管要求

---

## 1. 问题描述

追溯校验与 CHS 模块耦合；无 CHS 或未上报时，应码品种可能无医保侧拦截。

## 2. 发生场景

**2026-07-13 前**：`DispenseTraceGateExtension` 在 prepare/dispense 前校验，且依赖 CHS 装配。

**当前**：独立 `DispenseTraceGateExtension` 已删除；CHS 追溯策略与 `block-settle-without-trace` 在 `InventoryUploadTraceLineSupport` + 350x 出站前执行。本地核发仍靠 `TraceNumberQueryService`（提交码时校验在库/在院）。

## 3. 根因分析

缺 **独立于 CHS 的院内 mandatory 开关**；CHS 校验发生在业务成功后的上报阶段，不能替代核发前院内监管（若产品要求核发前硬拦）。

## 4. 业务影响

无 CHS 或未触发上报时，应码品种仍可能完成本地核发（取决于是否录入码及院内断言）。

## 5. 修改方案

1. **双开关**：`openhis.trace.dispense-gate.required=true` 独立于 CHS；院内监管品种表驱动 Gate（health 域，非进销存 SPI）。
2. **启动自检**：required=true 但无实现 → **启动失败**。
3. CHS：`block-settle-without-trace` 控制 350x 上报前是否因缺码失败（已实现于 Orchestrator）。

## 6. 涉及代码

- `InventoryUploadTraceLineSupport`、`InventoryThirdPartyUploadOrchestrator`
- `TraceNumberQueryService`、发药 AppService
- （待）院内 mandatory Gate 配置与实现

## 7. 验收用例

- T1：CHS 开、`block-settle-without-trace=true`、应码无 trace → 3505 上报 FAILED，AppService message 告警。
- T2：CHS 关、trace.required=true（待实现）→ 无码核发失败。

## 8. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
| v1.1 | 2026-07-13 | 移除 DispenseTraceGateExtension；CHS 校验合并进销存上报链 |
