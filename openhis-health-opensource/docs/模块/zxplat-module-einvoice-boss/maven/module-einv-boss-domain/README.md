# module-einv-boss-domain

博思财政电子票据领域层。

## 边界

| 允许 | 禁止 |
|------|------|
| `einv_boss_*` 读写、开具/冲红状态机 | `import ...gateway.*` |
| `{cap}/command|result|entity|access|loader` | bridge / application 实现细节 |
| `share/` 错误码、serviceId | 修改 health 实体 |

## 包结构（P12 ✅）

```text
com.zxplat.plugin.einvoice.boss.domain/
  share/                 错误码、BossServiceIds
  enums/
  issue/
    entity|repository/   EinvBossIssueTask
    loader/              EinvoiceIssueSnapshotContextLoader
    access/              EinvoiceIssueOrchestrator
    result/
  document/
    entity|repository|service/
    result/
  invoke/
    entity|repository|support/
  contrast/
    entity|repository/
  writeoff/
    loader/              EinvoiceInvoiceWritebackLoader
  reconcile/result/
```

## 依赖

- 不依赖 gateway / bridge / application
- bridge 实现 `issue/loader/*`、`writeoff/loader/*`、`{cap}/access/*`

## 命名约定

| 类型 | 包 | 示例 |
|------|-----|------|
| 出站编排 | `{cap}/access` | `BossIssueOutboundAccess` |
| HIS enrichment | `{cap}/loader` | `EinvoiceIssueSnapshotContextLoader` |
| 结果 | `{cap}/result` | `IssueDocumentResult` |

## 与 CHS 差异

| 维度 | CHS | Boss |
|------|-----|------|
| cap 对齐 | infno | **serviceId** |
| 表前缀 | `yb_*` | `einv_boss_*` |

## 迁移

见 [`../OPTIMIZATION-PLAYBOOK.md`](../OPTIMIZATION-PLAYBOOK.md) · 脚本 `scripts/boss-p12-domain.ps1`
