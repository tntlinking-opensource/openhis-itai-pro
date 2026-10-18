# chs-jilin-host

Gateway-Only Remote 部署模块：HIS 侧 Remote Client + 医保网段 gateway-proxy 独立进程。

## 双角色

| 包 | 部署位置 | 职责 |
|----|---------|------|
| `remote/` | HIS 网段（starter-remote） | `InsuranceGatewayClientRemoteImpl` → HTTP 调 proxy |
| `proxy/` | 医保网段（gateway-proxy jar） | `ChsGatewayProxyController` → 同 JVM `InsuranceGatewayClient` local |

## 线协议 DTO

远程 HTTP 请求体定义在 **gateway** 模块：

```text
com.zxplat.plugin.chs.gateway.client.remote.*
  ChsGatewayRemoteInvokeRequest
  ChsGatewayRemoteSignInRequest
```

host 不再维护 `health.integration.chs.gateway.dto`（P10-A 已删除）。

## 配置矩阵

| 场景 | 配置 |
|------|------|
| HIS 直连医保 | `gateway.mode=local`（默认，不用 host） |
| HIS 经 proxy | `gateway.mode=remote` + `gateway.remote.base-url` |
| 独立 proxy 进程 | `gateway.proxy.standalone=true` |

## 边界

- **不含** bridge、application、ExtensionImpl
- proxy Controller 只透传 `InsuranceGatewayClient`，不做业务编排
- session 持久化：`ChsGatewayProxySessionAutoConfiguration` 扫描 `domain.session`

脚本：`scripts/p10-gateway-host-restructure.ps1`
