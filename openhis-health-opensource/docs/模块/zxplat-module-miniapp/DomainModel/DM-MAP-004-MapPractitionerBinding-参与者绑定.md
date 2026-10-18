# DM-MAP-004 MapPractitionerBinding · 参与者绑定

> **版本**：v1.0 · **日期**：2026-09-24 · **模式**：B  
> **包**：`com.zxplat.plugin.miniapp.domain.practitionerbinding`  
> **表**：`map_practitioner_binding`  
> **写侧编排**：经 `MapAuthDomainService.bindPractitioner` / `switchPractitioner`（无独立 DomainService）  
> **详细设计编号**：DM-MAP-004-MapPractitionerBinding

---

## 1. 概述

微信账号与 HIS `practitionerId` 的绑定关系；支持工号冗余与默认标记。

## 2. 持久化

| 表 | 实体 |
|----|------|
| `map_practitioner_binding` | `MapPractitionerBinding` |

## 3. 字段（摘要）

| 字段 | 说明 |
|------|------|
| wechatAccountId / practitionerId | 绑定对（必填） |
| bindChannelEnum | 绑定渠道 |
| employeeNo | 工号冗余（可选） |
| isDefault | 是否默认参与者 |
| boundAt / unboundAt | 绑定 / 解绑时刻 |
| 审计 / 租户 / 软删 | 基类 |

## 6. 领域服务

写侧方法见 **DM-MAP-AUTH-001** §6（`bindPractitioner` / `switchPractitioner`）。本聚合无独立写服务。

## 7. 不变式

- 切换会话参与者前必须存在未解绑绑定  
- 设默认时同账号其他绑定 `isDefault=false`（由 Auth 服务保证）

## 9. 修订

| v1.0 | 2026-09-24 | Mode B 补独立九章（关闭 DOC-DRIFT） |
