# elep-jilin-host

Gateway-Only Remote：专网 HIS 侧 Remote Client，引用 domain access 与 gateway 线协议。

## 职责边界

| 允许 | 禁止 |
|------|------|
| `remote/client/*RemoteClientImpl` | 依赖 bridge / application |
| 引用 `gateway.client.remote.*` | 复制 gateway DTO |
| 透传 domain Command/Result | 业务编排 |

## 包结构

```text
com.zxplat.plugin.elep.bridge.integration.elep.remote/
  client/           ElepPrescriptionOperationsRemoteClientImpl
  appservice/       ElepRemoteAppServiceClient
  config/
```

## 命名·脚本

| 类型 | 包 | 示例 |
|------|-----|------|
| Remote 实现 | `remote/client/` | `ElepPrescriptionOperationsRemoteClientImpl` |
| 线协议 | 引用 `gateway.client.remote.*` | 禁止复制 gateway DTO |
| domain 契约 | Command/Result 透传 | 无 `Dto` 后缀 |

| 阶段 | 脚本 | 状态 |
|------|------|------|
| P10-host | `scripts/elep-p10-host.ps1` | ✅ |
| P13 | `scripts/elep-p13-rename-dto.ps1` | ✅ |

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md).

## 与 CHS 差异

| 维度 | CHS host | ELEP host |
|------|----------|-----------|
| 部署场景 | 医保专网 Remote | 电子处方专网 Remote |
| 透传键 | infno | **OpCode** A000x |
| 依赖 | gateway.client.remote | 同左，禁止 bridge/application |
