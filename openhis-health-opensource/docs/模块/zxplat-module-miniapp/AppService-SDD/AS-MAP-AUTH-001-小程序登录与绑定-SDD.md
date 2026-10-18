# AS-MAP-AUTH-001 小程序登录与绑定 · SDD

> **包**：`com.zxplat.plugin.miniapp.application.auth.service` · **类**：`MiniappAuthAppService`  
> **HTTP**：`POST /api/miniapp/miniappAuth/{methodName}` · **版本**：v1.1 · **日期**：2026-09-24

## 方法白名单

| IF | 方法 |
| --- | --- |
| 01 | `autoLogin` / `devLogin` |
| 02 | `currentSession` |
| 03 | `logout` |
| 04 | `bindPatient` / `switchPatient` |
| 05 | `bindCampusStudent` / `bindCampusStaff` |
| 06 | `bindPractitioner` / `switchPractitioner` |

写库顺序：`map_wechat_account` → `map_session` →（绑定）`map_patient_binding` / `map_practitioner_binding`

配置：`zxplat.miniapp.*`（default-app-id / session-ttl-hours / mock-login-enabled / wechat-app-secret / gateway-api-key）

本 SDD 不写 Gap。
