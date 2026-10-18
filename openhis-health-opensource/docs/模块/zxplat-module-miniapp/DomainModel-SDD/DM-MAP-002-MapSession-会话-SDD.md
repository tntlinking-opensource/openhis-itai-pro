# DM-MAP-002 MapSession · DomainModel-SDD

> **对应 DM**：`../DomainModel/DM-MAP-002-MapSession-会话.md`  
> **包**：`com.zxplat.plugin.miniapp.domain.session` · **版本**：v1.1 · **日期**：2026-09-24

## 4. 字段全集（`map_session`）

| 字段 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| wechatAccountId | Long | 是 | 账号 |
| tokenHash | String | 是 | token 哈希 |
| currentPatientId | Long | 否 | 当前就诊人 |
| currentPractitionerId | Long | 否 | 当前参与者 |
| clientTypeEnum | String | 是 | 默认 `miniapp` |
| expiresAt | LocalDateTime | 是 | 过期 |
| revokedAt | LocalDateTime | 否 | 吊销 |

## 5. 写方法

经 AUTH：`loginOrCreateSession` / `revokeSession` / `switchPatient` / `switchPractitioner`。

## 9. 修订

| v1.1 | 2026-09-24 | 补 §4 |
| v1.0 | 2026-09-24 | Mode B 迁入 |
