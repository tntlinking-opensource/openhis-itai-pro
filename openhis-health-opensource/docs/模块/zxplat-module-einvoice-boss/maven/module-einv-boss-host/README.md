# module-einv-boss-host

博思票据 Gateway-Only Remote：网段隔离时的 HTTP forward proxy。

## 职责边界

| 允许 | 禁止 |
|------|------|
| `POST /eleInvoice/forward/{serviceId}` 透传 | 业务编排 |
| RestTemplate 转发至真实博思平台 | 依赖 bridge / application |
| host 侧配置（URL、超时） | 复制 gateway 业务 DTO |

## 包结构

```text
com.zxplat.plugin.einvoice.boss.host/
  web/       ForwardController
  config/    HostProperties、RestTemplate、AutoConfiguration
```

## 命名·脚本

| 类型 | 包 | 示例 |
|------|-----|------|
| 转发入口 | `web/` | `ForwardController` |
| 线协议 | 引用 gateway | 禁止复制 gateway 业务 DTO |
| 透传键 | serviceId 路径变量 | 非 infno |

| 阶段 | 脚本 | 状态 |
|------|------|------|
| P10-A | `scripts/boss-p10-gateway.ps1` | ✅ |

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md).

## 部署

- HIS embedded 默认使用 `module-einv-boss-gateway` 本地 `EinvoiceBossGatewayClientLocalImpl`
- 专网/隔离网段：health-app 指向 host 模块 URL，由 host 转发博思 HTTP

## 与 CHS 差异

| 维度 | CHS host | Boss host |
|------|----------|-----------|
| 部署场景 | 医保专网 Remote | 博思票据专网 forward |
| 透传键 | infno | **serviceId** |
| 协议 | 医保线协议 | RestTemplate HTTP 转发 |
