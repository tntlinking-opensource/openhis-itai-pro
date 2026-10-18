# GAP-ACL-006 Booking 模块可选导致 Gate 缺失

> **问题类型**：权限越权与数据域
> **严重度**：中危（部署配置）
> **影响范围**：门诊
> **优先级**：P2
> **状态**：文档化
> **父文档**：[SYS-IMP-008](../03-权限越权与数据域/SYS-IMP-008-多租户岗位与财务数据域.md)

---

## 1. 问题描述

Booking 模块未部署时预约 Gate 静默跳过

## 2. 发生场景

`OutpatientRegistrationAppService` 中多处 `if (bookingQueryPort == null) return/skip`；无 booking 模块时 **不**执行 `validateForRegistration`、不占号校验。

## 3. 根因分析

可选 Client 设计；单模块部署简化。

## 4. 业务影响

生产若误不配 booking 且仍启用预约入口 → 预约占号、时段校验全失效。

## 5. 修改方案

1. **配置显式开关**：`whale.booking.required=true` 时 Client 缺失启动失败；
2. 或挂号入口检测 `appointmentId` 非空但 Client 为空 → 明确 `BKG-MOD-503`；
3. 运维检查清单写入部署文档。

**状态**：文档化 + 启动校验（可选）

## 6. 修订记录

| 版本 | 日期 | 说明 |
| --- | --- | --- |
| v1.0 | 2026-06-21 | 从父文档拆分独立问题单 |
