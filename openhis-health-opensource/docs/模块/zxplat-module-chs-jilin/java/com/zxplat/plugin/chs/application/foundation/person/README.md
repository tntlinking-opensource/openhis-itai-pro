# person 应用层

1101 读卡页（`POST /api/app/insurancePerson/getPersonInfo`）。

## 调用链

```text
InsurancePersonAppService
  → Op1101QueryInvoker（domain 端口）
      → Op1101Invoker（bridge/benefit，含当日缓存 yb_person_cache）
          → InsuranceGatewayClient（1101）
```

1101 出站实现位于 **bridge**（与挂号待遇 Extension 共用），application 只依赖 domain 端口 `Op1101QueryInvoker`。

## infno 索引

| infno | Invoker | 包 |
|-------|---------|-----|
| 1101 | `Op1101Invoker` | `chs-jilin-bridge` … `benefit` |
