# DM-MAP-AUTH-001 MapAuth · 登录会话绑定领域服务

> **版本**：v1.0 · **日期**：2026-09-24  
> **类**：`MapAuthDomainService`  
> **详细设计编号**：DM-MAP-AUTH-001-MapAuth
> **表设计真源**：[DM-MAP-AUTH-微信登录与就诊人绑定.md](../DM-MAP-AUTH-微信登录与就诊人绑定.md)

---

## 1. 概述

跨 WechatAccount / Session / PatientBinding / PractitionerBinding 的写侧编排；应用层不得直接拼装绑定/会话落库。

## 6. 领域服务

| 方法 | 说明 |
|------|------|
| `loginOrCreateSession` | appId+openid 必填；建号/建会话；回填默认 patient/practitioner |
| `resolveActiveSession` | token → 未过期未吊销会话 |
| `revokeSession` | 登出 |
| `switchPatient` / `switchPractitioner` | 须已绑定 |
| `bindPatient` / `bindPractitioner` | 幂等；可设默认 |

校验：`ValidationException`（空参/无租户）· `BusinessException`（禁用账号、未绑定、会话不匹配）。
