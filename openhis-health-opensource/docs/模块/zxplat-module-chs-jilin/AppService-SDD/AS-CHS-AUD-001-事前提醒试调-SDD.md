# 事前提醒试调 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-AUD-001-SDD** · v0.1 · 2026-07-23  
> **应用服务编号**：AS-CHS-AUD-001

## 1. 边界

| 项 | 内容 |
| --- | --- |
| AppService | `InsuranceDetailAuditTryAppService` |
| HTTP | `/api/app/insuranceDetailAuditTry` |
| infno | 3101、3103 |

## 2. 包结构

```text
application/audit/
  service/InsuranceDetailAuditTryAppService.java
  request/DetailAuditAnalyzeFromEncounterRequest.java
domain/audit/
  access/Audit3101Invoker · Audit3103Invoker
  command|result|entity|snapshot|service
bridge/audit/
  invoker/Audit3101InvokerImpl · Audit3103InvokerImpl
  support/AuditInvokeSupport
  mapper/Audit3101ResultMapper
gateway/scheme/audit/
  model|assembler|parser
```

**无强制 Workflow**：试调页直调 domain access；落库在 Invoker 成功后写 `yb_detail_audit_*`。

## 3. HTTP ↔ IF

| 方法 | IF | 说明 |
| --- | --- | --- |
| `analyze3101` | IF-CHS-AUD-C-01 | 入参 `AuditAnalyze3101Command` |
| `analyze3101FromEncounter` | IF-CHS-AUD-C-02 | `OpPreSettleContextLoader` → Snapshot → 3101 |
| `feedback3103` | IF-CHS-AUD-C-03 | 入参 `AuditFeedback3103Command` |

## 4. 前端

| 项 | 路径 |
| --- | --- |
| 页面 | `openhis-pro-ui/.../chs/detail-audit-try/index.vue` |
| API | `chs/api/detail-audit-try.ts` |
| 菜单 | health Flyway `V18__seed_chs_detail_audit_try_menu.sql` |

## 5. 事务

写路径：3101/3103 成功后 DomainService 落库；读侧无分页查询（统计页属 W3）。
