# chs-jilin-gateway

吉林省医保平台 HTTP 出站层：`InsuranceGatewayClient`、报文 scheme、签到会话、审计装饰器。

## 职责边界

| 允许 | 禁止 |
|------|------|
| 平台 HTTP 传输、报文 Input/Assembler/Parser | 业务编排（无 Orchestrator） |
| 签到 session、`yb_invoke_log` 审计装饰 | 依赖 bridge / application |
| domain `access` 实现（契约读取） | HIS 主数据查询 |

## 包结构

```text
com.zxplat.plugin.chs.gateway/
  client/              InsuranceGatewayClient、InvokeContext、ResultBody
    remote/            gateway-proxy 线协议 DTO（与 host 共用）
  local/               InsuranceGatewayClientLocalImpl（同 JVM 直连）
  http/                ChsHttpAccessor（底层 HTTP）
  observability/       PersistingInsuranceGatewayClient、ChsInvokeLogContext*
  session/             9001 签到会话
    transport/         Sign9001Transport（传输层，非业务 Invoker）
  config/              Spring 装配、契约配置
  support/             HTTP/响应/insuplc_admdvs 工具
  crypto/              SM 加解密
  catalog/access/      ChsContractPropertiesAccess 实现
  scheme/{cap}/        平台报文（model / assembler / parser）
```

## scheme 能力切片

每个 `scheme/{cap}/` 下按职责分子包：

```text
scheme/{cap}/
  model/       *Input、*Output、*Row、*Data
  assembler/   *InputAssembler、*PlatformInputAssembler
  parser/      *OutputParser、*Indexer
  package-info.java
```

| 包 | infno 示例 |
|----|-----------|
| `scheme/benefit/` | 1101、2001 |
| `scheme/outpatient/` | 2201–2208 |
| `scheme/inpatient/` | 2301–2405 |
| `scheme/settlement/` | 2601 |
| `scheme/inventory/` | 3501–3506 |
| `scheme/medicalinfo/` | 5201–5301 |
| `scheme/reconciliation/` | 3201–3209A |
| `scheme/catalog/` | 1312 等国家目录下载 |
| `scheme/platformdict/` | 1901 |
| `scheme/common/model/` | setlinfo 共用；`wrapper/` 为平台 input 包装 |

## 命名约定

| 类型 | 说明 |
|------|------|
| `*Transport` | gateway 内部 HTTP 出站（如 Sign9001），**不是** bridge `{Op\|Ip}*Invoker` |
| `*InputAssembler` / `*OutputParser` | domain Command ↔ 平台 JSON |
| `InsuranceGatewayClient` | 唯一业务出站口（local / remote 实现） |

## JavaDoc 约定

与 domain/bridge 对齐：类注释说明传输职责；public 方法补 `@param`/`@return`。

脚本：`scripts/p10-gateway-host-restructure.ps1`、`p10c-scheme-slice-align.ps1`、`p10d-scheme-subfolder.ps1`
