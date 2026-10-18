# unionpay-bpc-application

银联 BPC UI 层：`UnionPayBpcAppService`、订单查询、Flyway。

## 边界

| 允许 | 禁止 |
|------|------|
| `order/service/` AppService | 依赖 `unionpay-bpc-bridge`（Enforcer 已禁） |
| `order/request|response/` UI 契约 | 注入 `BpcGatewayClient`（收敛到 bridge invoker） |
| 直调 domain `BpcSettleDomainService` | 包名 `dto` |

## 包结构

```text
com.zxplat.plugin.unionpay.bpc.application/
  order/service/    UnionPayBpcAppService、UnionPayBpcOrderQueryService
  order/response/   BpcOrderSummaryResponse
  config/
  module/
```

## 脚本

| 脚本 | 阶段 |
|------|------|
| `scripts/bpc-p1-app-rename.ps1` | P1–P3 order/service + response |
