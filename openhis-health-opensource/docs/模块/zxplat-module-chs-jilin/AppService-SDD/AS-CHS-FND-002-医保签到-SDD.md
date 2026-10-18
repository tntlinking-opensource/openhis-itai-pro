# 医保签到 · 应用服务详细设计（SDD）

> 编码：**AS-CHS-FND-002-SDD** · v0.1 · 2026-07-11  
> **应用服务编号**：AS-CHS-FND-002

## 1. 边界

| AppService | HTTP | infno |
| --- | --- | --- |
| `InsuranceSignAppService` | `/api/app/insuranceSign` | 9001 |

## 2. 包结构

```text
sign/
  service/InsuranceSignAppService.java
  request/SignInsuranceRequest.java
  response/SignInsuranceResponse.java
  mapper/InsuranceSignAppMapper.java
```

包路径：`com.zxplat.plugin.chs.application.foundation.sign.*`。**已删除**兼容入口 `foundation.session.*`（`/insuranceSession/signIn`）。

## 3. 编排

| 方法 | 落点 |
| --- | --- |
| `signIn` | `InsuranceSignSessionService` → gateway `Sign9001Invoker` |

## 4. 事务

签到写 session 状态；经 `AppServiceAspect` 写传播。
