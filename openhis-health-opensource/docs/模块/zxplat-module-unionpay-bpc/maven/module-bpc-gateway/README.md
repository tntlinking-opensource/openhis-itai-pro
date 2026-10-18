# unionpay-bpc-gateway

银联 BPC HTTPS 出站层：`BpcGatewayClient`、scheme 报文、院点契约 session。

## 边界

| 允许 | 禁止 |
|------|------|
| HTTPS 传输、scheme model/assembler/parser | 业务编排 |
| `session/` 院点终端配置加载 | 依赖 bridge / application |
| `client/` 统一出站口 | P0 DLL 被扫实现（仍走 OpenHIS.Win） |

## 包结构

```text
com.zxplat.plugin.unionpay.bpc.gateway/
  client/           BpcGatewayClient
  local/            BpcGatewayClientLocalImpl（P1+ 占位）
  scheme/scan/      model | assembler | parser
  scheme/refund/
  scheme/query/
  scheme/share/     BpcInvokeResult、BpcResponseParser
  session/          BpcContractConfigLoader
  transport/        BpcTransportConstants
  config/           BpcContractConfig
```

## 脚本

| 脚本 | 阶段 |
|------|------|
| `scripts/bpc-p10-gateway-client.ps1` | P10-A client + session |
| `scripts/bpc-p10-scheme.ps1` | P10-C/D scheme cap |

## 与 CHS 差异

| 维度 | CHS gateway | BPC gateway |
|------|-------------|-------------|
| scheme 键 | infno | scan/refund/query **交易 cap** |
| P0 | — | DLL 不在 JVM gateway |
