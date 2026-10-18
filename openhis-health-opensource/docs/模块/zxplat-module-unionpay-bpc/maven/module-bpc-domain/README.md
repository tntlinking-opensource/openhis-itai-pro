# unionpay-bpc-domain

银联 BPC 领域层：`upc_bpc_order` 实体、Command/Result、`BpcSettleDomainService` 与出站端口。

## 边界

| 允许 | 禁止 |
|------|------|
| `upc_bpc_order` 读写、被扫/退款状态机 | 直接 HTTP 出站 |
| `{cap}/command` `{cap}/result` | bridge / application 实现细节 |
| `query/access` 出站端口定义 | 修改 health 实体 |
| `share/` 枚举、工具 | — |

## 包结构

```text
com.zxplat.plugin.unionpay.bpc.domain/
  scan/command|result/
  refund/command|result/
  query/command|result|access/
  order/entity|repository/
  service/              BpcSettleDomainService
  share/                BpcTranType、MerTradeNoGenerator
```

## 命名约定

| 类型 | 包 | 示例 |
|------|-----|------|
| 出站端口 | `query/access` | `BpcHttpsQueryAccess` |
| 命令/结果 | `{cap}/command|result` | `ScanPayCommand` |

## 脚本

| 脚本 | 阶段 |
|------|------|
| `scripts/bpc-p2-domain-cap.ps1` | P2 domain cap 分包 |

## 与 CHS 差异

| 维度 | CHS | BPC |
|------|-----|-----|
| 出站 | 医保 HTTP | P0 **Win DLL** + P1+ HTTPS 占位 |
| cap 对齐 | infno | 交易码 / HTTPS API |
