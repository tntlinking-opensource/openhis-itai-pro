# DM-MAP-001 MapWechatAccount · 微信账号

> **表名**：`map_wechat_account`  
> **版本**：v1.0 · **日期**：2026-09-24  
> **详细设计编号**：DM-MAP-001-MapWechatAccount
> **应用服务编号**：AS-MAP-AUTH-001

---

## 1. 概述

小程序稳定微信身份；`(tenantId, appId, openid)` 唯一。登录只保证「有 openid 会话」，不混叠 Patient/Practitioner。

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| app_id | varchar(64) | 小程序 appId |
| openid | varchar(64) | |
| union_id | varchar(64) | 可空 |
| status_enum | varchar(32) | `active` / `disabled` |
| last_login_at | timestamptz | |
| 审计 / 软删 | | |

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `MapWechatAccount` |
| **业务键** | `(tenantId, appId, openid)` |

## 6. 领域服务

写侧由 `MapAuthDomainService.loginOrCreateSession` 创建/更新；禁用态拒绝登录。

## 9. 关联

- 会话：[DM-MAP-002](./DM-MAP-002-MapSession-会话.md)
- 绑定：[DM-MAP-003](./DM-MAP-003-MapPatientBinding-就诊人绑定.md)
