import type {
  PreRegisterSettlementResult,
  RefreshRegisterFeeInsurancePreSettleResult,
} from '#/api/outpatient-registration';

/** 将刷新预结算响应合并进确认弹窗使用的预结算快照。 */
export function mergeRefreshIntoPreSettlement(
  pre: PreRegisterSettlementResult,
  refresh: RefreshRegisterFeeInsurancePreSettleResult,
): PreRegisterSettlementResult {
  return {
    ...pre,
    insurancePreSettled: refresh.insurancePreSettled ?? pre.insurancePreSettled,
    totalAmount:
      refresh.totalAmount === null || refresh.totalAmount === undefined
        ? pre.totalAmount
        : Number(refresh.totalAmount),
    ybAcctPay: refresh.ybAcctPay ?? pre.ybAcctPay,
    ybChrgBchno: refresh.ybChrgBchno ?? pre.ybChrgBchno,
    ybFundPaySumamt: refresh.ybFundPaySumamt ?? pre.ybFundPaySumamt,
    ybMedfeeSumamt: refresh.ybMedfeeSumamt ?? pre.ybMedfeeSumamt,
    ybPsnCashPay: refresh.ybPsnCashPay ?? pre.ybPsnCashPay,
  };
}

/** 将刷新预结算响应写入就诊列表收费行（历史收费弹窗）。 */
export function applyRefreshToHistoryRow<T extends Record<string, unknown>>(
  row: T,
  refresh: RefreshRegisterFeeInsurancePreSettleResult,
): T {
  return {
    ...row,
    insurancePreSettled: refresh.insurancePreSettled ?? row.insurancePreSettled,
    amount: refresh.totalAmount ?? row.amount,
    ybAcctPay: refresh.ybAcctPay ?? row.ybAcctPay,
    ybFundPaySumamt: refresh.ybFundPaySumamt ?? row.ybFundPaySumamt,
    ybPsnCashPay: refresh.ybPsnCashPay ?? row.ybPsnCashPay,
  };
}
