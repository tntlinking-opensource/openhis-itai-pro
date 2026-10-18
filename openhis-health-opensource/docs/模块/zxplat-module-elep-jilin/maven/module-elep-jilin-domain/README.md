# elep-jilin-domain

吉林省电子处方领域层：Command/Result、`yb_elep_*` 实体、出站 access 端口。

## 职责边界

| 允许 | 禁止 |
|------|------|
| `yb_elep_*` 读写、RxNo 快照 | `import com.zxplat.elep.gateway.*` |
| 出站端口 `prescription/access`（A000x） | application / bridge 实现细节 |
| 院点契约 `config/`、OpCode `domain.gateway` | 包名 `dto`、`facade` |

## 包结构

```text
com.zxplat.elep.domain/
  prescription/command|result|entity|repository|access/
  catalog/command|model|repository|access/
  snapshot/                 ElepRxNoSnapshot
  config/                   院点契约
  gateway/                  ElepOpCode、ElepPlatformResponse
  client/                   JilinElepPrescriptionClient
```

## 命名·脚本

| 类型 | 包 | 示例 |
|------|-----|------|
| 出站端口 | `{cap}/access/` | `ElepA0001InvokeAccess` |
| 领域门面 | `{cap}/access/` | `ElepPrescriptionOperations` |
| result/model | 无 `Dto` 后缀 | `ElepPrescriptionDraft`、`ElepCatalogItem` |

- access 端口 JavaDoc：`【A000x】` 前缀；Invoker 实现类在 bridge

| 阶段 | 脚本 | 状态 |
|------|------|------|
| P2 | `scripts/elep-p2-domain.ps1` | ✅ |
| P13 | `scripts/elep-p13-rename-dto.ps1` | ✅ |

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md).

## 与 CHS 差异

| 维度 | CHS domain | ELEP domain |
|------|-----------|-------------|
| cap 对齐 | infno | **OpCode** A000x |
| result 命名 | 无 `Dto` 后缀 | ✅ 已收敛（P13） |
