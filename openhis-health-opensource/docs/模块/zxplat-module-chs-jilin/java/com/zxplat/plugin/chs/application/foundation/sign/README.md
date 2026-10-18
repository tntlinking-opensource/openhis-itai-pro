# sign 应用层

医保 9001 签到 UI（`POST /api/app/insuranceSign/*`）。

## 包结构

```text
sign/
  service/InsuranceSignAppService
gateway/session/
  InsuranceSignSessionService   会话缓存 + opter 轮换 + 持久化
  transport/Sign9001Transport       单次 9001 HTTP 出站
```

## infno 索引

| infno | Invoker | 编排 |
|-------|---------|------|
| 9001 | `Sign9001Transport`（gateway） | `InsuranceSignSessionService` |

## 调用链

```text
InsuranceSignAppService
  → InsuranceSignSessionService.signIn
      → Sign9001Transport（平台 HTTP）
      → YbSignPersistenceService（当日 sign_no 持久化）
```

业务 infno 经 `InsuranceGatewayClient.invoke` 时由 `ensureSigned` 自动复用 sign_no。
