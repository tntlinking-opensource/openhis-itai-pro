# DM-MAP-002 MapSession · 会话

> **表名**：`map_session`  
> **版本**：v1.0 · **日期**：2026-09-24  
> **详细设计编号**：DM-MAP-002-MapSession

---

## 1. 概述

微信账号下的登录会话；存 `token_hash`（SHA-256），明文 token 仅登录响应返回一次。可挂 `current_patient_id` / `current_practitioner_id`。

## 2. 持久化形态

| 列 | 类型 | 说明 |
|----|------|------|
| id | int8 | PK |
| tenant_id | int8 | |
| wechat_account_id | int8 | FK → map_wechat_account |
| token_hash | varchar(128) | |
| client_type_enum | varchar(32) | 默认 `miniapp` |
| expires_at | timestamptz | |
| revoked_at | timestamptz | 可空 |
| current_patient_id | int8 | 可空 |
| current_practitioner_id | int8 | 可空 |

## 3. 实体与聚合

| 项 | 说明 |
|----|------|
| **聚合根** | `MapSession` |
| **解析** | `MapAuthDomainService.resolveActiveSession(rawToken)` |

## 6. 领域服务

`loginOrCreateSession` / `revokeSession` / `switchPatient` / `switchPractitioner`
