# DM-MAP-AUTH-001 MapAuth · DomainModel-SDD

> **对应 DM**：`../DomainModel/DM-MAP-AUTH-001-MapAuth-登录会话绑定.md`  
> **包**：`com.zxplat.plugin.miniapp.domain.auth.service` · **类**：`MapAuthDomainService`  
> **版本**：v1.1 · **日期**：2026-09-24 · **模式**：B

---

## 1. 边界

- **在内**：微信账号建号、会话签发/吊销、就诊人/参与者绑定与切换  
- **在外**：code2session HTTP、校园/医护 Port 解析（应用层）

## 2. 依赖仓储

`MapWechatAccountRepository` · `MapSessionRepository` · `MapPatientBindingRepository` · `MapPractitionerBindingRepository`

## 3. 技术口径

主键/外键 `Long`；时间 `LocalDateTime`；软删基类；写方法 `@Transactional(rollbackFor = Exception.class)`。

## 4. 字段全集（经本服务触达的实体）

### 4.1 MapWechatAccount（`map_wechat_account`）

| 字段 | 类型 | 说明 |
|------|------|------|
| appId / openid | String | 必填；租户内唯一检索键 |
| unionId | String | 可选；登录可补写 |
| statusEnum | String | 默认 `active`；非 active 拒登 |
| lastLoginAt | LocalDateTime | 登录刷新 |

### 4.2 MapSession（`map_session`）

| 字段 | 类型 | 说明 |
|------|------|------|
| wechatAccountId | Long | 所属账号 |
| tokenHash | String | 明文 token 的 SHA-256 十六进制 |
| currentPatientId / currentPractitionerId | Long | 当前上下文 |
| clientTypeEnum | String | 默认 `miniapp` |
| expiresAt / revokedAt | LocalDateTime | 过期 / 吊销 |

### 4.3 MapPatientBinding（`map_patient_binding`）

| 字段 | 类型 | 说明 |
|------|------|------|
| wechatAccountId / patientId | Long | 绑定对 |
| bindChannelEnum | String | 如 `manual` |
| campusRefType / campusRefId / campusBizNo | | 校园扩展 |
| isDefault | Boolean | 默认就诊人 |
| boundAt / unboundAt | LocalDateTime | 绑定生命周期 |

### 4.4 MapPractitionerBinding（`map_practitioner_binding`）

| 字段 | 类型 | 说明 |
|------|------|------|
| wechatAccountId / practitionerId | Long | 绑定对 |
| bindChannelEnum / employeeNo | String | 渠道 / 工号冗余 |
| isDefault | Boolean | 默认参与者 |
| boundAt / unboundAt | LocalDateTime | 绑定生命周期 |

## 5. 写/读白名单

### 5.1 写方法

| 方法 | 入参摘要 | 行为 |
|------|----------|------|
| `loginOrCreateSession` | appId, openid, unionId, sessionTtlHours | 建号/刷新登录；签发会话；回填默认 patient/practitioner |
| `revokeSession` | rawToken | 置 `revokedAt` |
| `switchPatient` | wechatAccountId, patientId, rawToken | 须有效绑定；更新会话 currentPatientId |
| `switchPractitioner` | wechatAccountId, practitionerId, rawToken | 须有效绑定；更新 currentPractitionerId |
| `bindPatient` | wechatAccountId, patientId, channel, campus*, setDefault | 幂等绑定；可选设默认 |
| `bindPractitioner` | wechatAccountId, practitionerId, channel, employeeNo, setDefault | 幂等绑定；可选设默认 |

### 5.2 读方法

| 方法 | 说明 |
|------|------|
| `resolveActiveSession` | token → 未过期未吊销会话 |

### 5.3 工具

| 方法 | 说明 |
|------|------|
| `hashToken` | static；明文 → SHA-256 hex |

## 6. 校验

`ValidationException`：空参 / 无租户。  
`BusinessException`：`ACCOUNT_DISABLED` / 未绑定 / 会话不匹配。

## 7. 事件

本期不发布领域事件。

## 8. 缺口

无。

## 9. 修订

| 版本 | 日期 | 说明 |
|------|------|------|
| v1.1 | 2026-09-24 | 补 §4 字段全集 + §5.1 方法块 |
| v1.0 | 2026-09-24 | Mode B 迁入；单测 `MapAuthDomainServiceTest` |
