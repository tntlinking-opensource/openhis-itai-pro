# AS-CHS-AUD-002 事前提醒主路径门禁 · SDD

> **应用服务编号**：AS-CHS-AUD-002  
> **包**：`com.zxplat.plugin.chs.application` / `domain.audit`  
> **版本**：v0.7 · **日期**：2026-10-10

## 5.1 编排要点

主路径门禁；平台交互边界见 domain.audit 与 gateway Client。  
`syscode` 必填：入参或 `zxplat.chs.audit.syscode`（无代码默认）。

### 5.1.1 自费明细过滤（与预结算同口径）

| 步骤 | 实现 | 说明 |
|------|------|------|
| 候选 ID | 门诊：入参 `chargeItemIds` 或就诊 `PLANNED`；住院：入参勾选 | 在 enrich / 3101 之前 |
| 剔自费 | `OutpatientInsuranceChargeGrouper#listInsuranceChargeItemIds` | 非医保账户 + 代煎 → 不进 3101（对齐 2204 不上传） |
| 全自费 | `DetailAuditGateResult.skipped=true`，`skipReason=SKIP_REASON_ALL_SELF_PAY` | **不出站**；前端 `gateHasViolations` 视为通过 |
| 混合批 | 仅医保行写入 `OpPreSettleCommand` / `IpPreSettleCommand.chargeItemIds` 后再 enrich → Snapshot → 3101 | med_type 解析亦仅基于医保行 |

落点：`DetailAuditGateInvokerImpl#analyzeOp` / `#analyzeIp`。

## 5.2 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| v0.7 | 2026-10-10 | 补 §5.1.1 自费不进 3101 / 全自费 skipped |
| v0.1 | 2026-07-24 | 初版编排要点 |
