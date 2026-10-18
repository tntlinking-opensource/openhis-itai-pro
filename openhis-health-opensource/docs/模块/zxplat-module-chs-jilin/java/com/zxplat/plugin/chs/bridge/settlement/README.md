# settlement bridge

收银落账后不确定态结算冲正（2601）。

## 包结构

```text
settlement/
  invoker/     Op2601Invoker
```

## infno 索引

| infno | Invoker | 调用方 |
|-------|---------|--------|
| 2601 | `Op2601Invoker` | `InsurancePaymentSettlementExtensionImpl`；补偿路径 `OutpatientChargeInsuranceExtensionImpl#correctSettleTransaction` |

## 调用链

```text
PaymentReconciliationSettleIntegrationSupport.afterPaymentSettled
  → InsurancePaymentSettlementExtensionImpl
      → Op2601Invoker.correctUncertainSettlements
          → PaymentSettledCorrectionQueryService.listUncertainByPayment
          → Op2601Invoker.invoke
              → 平台 2601
              → YbSettlementCorrectionDomainService.persistLocalAfterPlatformSuccess（软删回执）
              → PaymentReconciliationDomainService.markYbSettlementsRevoked（桥表 REVOKED）
              → PaymentReconciliationDomainService.softDeleteInsuranceFundAcctDetailsBySetlId（软删统筹/个账）
```

## 防误冲（BR-OP-DCL-029）

- 失败 2207/2304 **之后已有成功**正式结算 invoke → **不发** 2601（避免冲掉重试成功的结算）。
- `omsgid` **仅**用失败 invoke 的 `msgid`，禁止回落到其它成功回执的 `msgid_2207`。
- 回执绑定：仅 `msgid`=`msgid_2207` 或 invoke 已挂 `settlement_record_id`；禁止 `chrg_bchno` / 单笔回落。

## 本地同态（平台净额 0）

2601 平台成功后本地必须：

1. 软删 `yb_settlement_record`（冲正视同未发生，不对账写 reversal）
2. `fin_payment_yb_settlement.status = REVOKED`
3. 软删该 `setl_id` 的 `INSURANCE_FUND` / `INSURANCE_ACCT` 明细

**不**改现金/微信等自付渠道；**不**替代正常 2208 + UN_PAY 退费。

存量纠错 SQL：

- `ops/fix-2601-miscorrect-success-settle-2026-09-01.sql`（9/1 两笔：软删虚增统筹/个账 + 桥表 REVOKED）
- `ops/fix-2601-miscorrect-success-settle-2026-09-01-static.sql`（9/1：明细已软删后补扣 static / 交账头，总收入 −190.30）
- `ops/fix-2601-miscorrect-success-settle-2026-08-14.sql`（8/14：明细已软删后补扣 static / 交账头，总收入 −137.57）
- `ops/verify-cashier-settlement-income-snapshot-0814-0901.sql`（只读验收：头表 `total_income` = 挂载现算，排除工伤）

**纠偏（2026-09-15）**：上列「补扣 static」把日结上半区 fin_type 压成自付额，与 `charge_item` / 药房交账售价打架。已用
`ops/fix-0814-0901-restore-static-from-charge.sql` 按收费行恢复 static，并重算交账头 `total_income`。
**不**恢复已软删统筹/个账（平台 2601 已成功）。修复后「总收入 − 支付渠道」差额即误冲未退药挂账（8/14：137.57；9/1：190.30）。

说明：收款交账**不落** `print_snapshot_json`（**BR-OP-DCL-025**）；历史重打/审批详情按挂载 payment **现算**。列表金额快照仅为头表 `total_income`（及 `payment_count` / `practitioner_ids`）。
