# elep-jilin-gateway

吉林省电子处方 HTTP 出站层：`ElepGatewayClient`、GM 国密会话、scheme 报文。

## 职责边界

| 允许 | 禁止 |
|------|------|
| HSE 加密 HTTP、`ElepHttpAccessor` | 业务编排（无 Orchestrator） |
| `session/` GM 加解密 | 依赖 bridge / application |
| `scheme/{share|prescription|catalog}/` | HIS 主数据查询 |

## 包结构

```text
com.zxplat.elep.gateway/
  client/              ElepGatewayClient、ElepHttpAccessor
    remote/            ElepInvokeContext（线协议）
  scheme/
    share/model/       ElepGatewayResponse
    prescription/      model | assembler | parser（按 A000x 扩展）
    catalog/
  session/             HseEncAndDecUtil、SignUtil（原 crypto）
  support/             ElepCurrentOperatorResolver
  config/
```

## OpCode 与 cap

| cap | OpCode | 路径 |
|-----|--------|------|
| prescription | A0001–A0006 | `/fixmedins/*` |

## 命名·脚本

- scheme 按 **OpCode** 分包；线协议 DTO 在 `client.remote`，业务模型在 `scheme.{cap}.model`

| 阶段 | 脚本 | 状态 |
|------|------|------|
| P10-A | `scripts/elep-p10-gateway.ps1` | ✅ |
| P10-C/D | `scripts/elep-p10-scheme.ps1` | ✅ |

## 与 CHS 差异

| 维度 | CHS gateway | ELEP gateway |
|------|-------------|--------------|
| scheme 键 | infno | **OpCode** A0001–A0006 |
| 加密 | SM 等国密 | **GM HSE** 会话 |

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md).
