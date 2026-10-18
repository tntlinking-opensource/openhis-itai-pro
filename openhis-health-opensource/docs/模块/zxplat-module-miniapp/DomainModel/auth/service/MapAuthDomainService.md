---
doc_type: type_card
---

# MapAuthDomainService

| 项 | 值 |
|----|-----|
| 源码 | `com.zxplat.plugin.miniapp.domain.auth.service.MapAuthDomainService` |
| 路径 | `zxplat-plugin/zxplat-module-miniapp/module-miniapp-domain/src/main/java/com/zxplat/plugin/miniapp/domain/auth/service/MapAuthDomainService.java` |
| 分层 | service |

## 关键写方法

| 方法 | 说明 |
|------|------|
| `loginOrCreateSession` | 建号/签发会话 |
| `revokeSession` | 登出吊销 |
| `switchPatient` / `switchPractitioner` | 切换当前上下文 |
| `bindPatient` / `bindPractitioner` | 幂等绑定 |

读：`resolveActiveSession`。工具：`hashToken`。

## 说明

跨 WechatAccount / Session / PatientBinding / PractitionerBinding 的写侧编排。

## 关联

- **领域模型**：[DM-MAP-AUTH-001](../../DM-MAP-AUTH-001-MapAuth-登录会话绑定.md)
- **SDD**：[DM-MAP-AUTH-001-SDD](../../../DomainModel-SDD/DM-MAP-AUTH-001-MapAuth-登录会话绑定-SDD.md)
- **领域索引**：[../../说明.md](../../说明.md)
