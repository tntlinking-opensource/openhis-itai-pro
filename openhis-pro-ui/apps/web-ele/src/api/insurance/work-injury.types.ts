/** 工伤 invoke/submit 响应（health WorkInjuryInvokeAppService）。 */
export interface WorkInjurySubmitInvokeResultResponse {
  sessionId?: string;
  nextAction?: string;
  errMsg?: string;
  summary?: string;
  settlementRecordId?: number | string;
  setlId?: string;
  chrgBchno?: string;
  medfeeSumamt?: number | string;
  fundPaySumamt?: number | string;
  psnCashPay?: number | string;
}
