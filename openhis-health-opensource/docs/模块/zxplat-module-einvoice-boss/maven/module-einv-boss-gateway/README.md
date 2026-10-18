# module-einv-boss-gateway

博思财政电子票据 HTTP 出站层：`EinvoiceBossGatewayClient`、签名、RestTemplate 传输。

## 职责边界

| 允许 | 禁止 |
|------|------|
| 博思平台 HTTP 传输、签名 | 业务编排（无 Orchestrator） |
| `BossSignSupport` 签名辅助 | 依赖 bridge / application |
| `EinvoiceBossGatewayClient` 统一出站口 | HIS 主数据查询 |
| Spring 装配、端点注册 | 复制 host 侧业务 DTO |

## 包结构

```text
com.zxplat.plugin.einvoice.boss.gateway/
  client/           EinvoiceBossGatewayClient（业务唯一出站口）
  local/            EinvoiceBossGatewayClientLocalImpl（同 JVM 直连）
  session/          BossSignSupport（P10-A ✅）
  scheme/
    share/model|parser|support   BossInvokeResult、BossResponseParser（HTTP 解码）
    issue/assembler/             BossPayloadBuilder + 四类 Assembler
    issue/parser/                IssueOutputParser（开具/查票/冲红共用票面映射）
    writeoff/assembler/          WriteOffPayloadAssembler
    recover/assembler/           RecoverPayloadAssembler
    reconcile/assembler/         ReconcilePayloadAssembler
    reconcile/parser/            ReconcileTotalOutputParser
    list/assembler/              AccountStatusPayloadAssembler
    list/model|parser/           BossAccountStatusResult、AccountStatusOutputParser
  config/           属性、RestTemplate、端点注册、AutoConfiguration
```

## serviceId 与 cap（迁移对齐）

| cap | serviceId 示例 |
|-----|----------------|
| issue | `issueEBill` 等开具类 |
| writeoff | `writeOffEBill` |
| recover | `getEBillByBusNo` |

**目标**（P10-C/D）：`scheme/{cap}/model|assembler` 承载请求/响应 JSON 模型。

## 脚本索引

| 阶段 | 脚本 |
|------|------|
| P10-A | `scripts/boss-p10-gateway.ps1` | ✅ |
| P10-C/D | `scripts/boss-p10-scheme.ps1` | ✅ |

## 与 CHS 差异

| 维度 | CHS gateway | Boss gateway |
|------|-------------|--------------|
| scheme 键 | infno | **serviceId** |
| 签名 | SM 等国密 | Boss 专有签名 |

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md)。
