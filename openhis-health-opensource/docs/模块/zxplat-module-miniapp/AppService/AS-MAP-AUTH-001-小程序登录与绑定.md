# 小程序登录与绑定 · 应用服务设计（AS）

> **编码**：AS-MAP-AUTH-001 · **版本**：v1.1 · **日期**：2026-09-24  
> **类名**：`MiniappAuthAppService` · **HTTP**：`POST /api/miniapp/miniappAuth/{methodName}`  
> **成稿编号**：DM-MAP-AUTH-001
> **详细设计编号**：AS-MAP-AUTH-001-SDD
> **模式**：B（代码先行回写）

---

## 2. 接口总览

| IF | 方法 | 读/写 |
|----|------|:--:|
| IF-MAP-AUTH-01 | `autoLogin` / `devLogin` | 写 |
| IF-MAP-AUTH-02 | `currentSession` | 读 |
| IF-MAP-AUTH-03 | `logout` | 写 |
| IF-MAP-AUTH-04 | `bindPatient` / `switchPatient` | 写 |
| IF-MAP-AUTH-05 | `bindCampusStudent` / `bindCampusStaff` | 写 |
| IF-MAP-AUTH-06 | `bindPractitioner` / `switchPractitioner` | 写 |

> 门面类：`MiniappAuthApiController`（`@RequestMapping("/api/miniapp/miniappAuth")`）。

## 4. 编排

1. code2session（或 mock）→ `MapAuthDomainService.loginOrCreateSession`  
2. 校园绑定：`PeisCampusIdentityPort` 解析 → `bindPatient`  
3. 医护绑定：`MiniappPractitionerLookupPort` → `bindPractitioner`  
4. Filter：`MiniappAuthFilter` 解析 Bearer → `MiniappContext`

## 5. 异常码

`ACCOUNT_DISABLED` / `BIND_REQUIRED` / `PRACTITIONER_BIND_REQUIRED` / `SESSION_NOT_FOUND` / `SESSION_ACCOUNT_MISMATCH`
