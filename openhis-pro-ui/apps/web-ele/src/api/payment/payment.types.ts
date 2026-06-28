/** 银联 Win / Whale 共享 DTO */
export interface ClientScanResultDto {
  merTradeNo: string;
  tradeNo: string;
  payType: string;
  respCode: string;
  respMsg: string;
  txnAmtFen?: number;
}

export interface ClientRefundResultDto {
  merTradeNo: string;
  tradeNo: string;
  refundMerTradeNo?: string;
  respCode: string;
  respMsg: string;
  refundAmountYuan?: number | string;
}
