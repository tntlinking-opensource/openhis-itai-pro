/** 银联 Win / ZXPlat 共享 DTO */
export interface ClientScanResultDto {
  merTradeNo: string;
  tradeNo: string;
  payType: string;
  respCode: string;
  respMsg: string;
  /** Win/POS 回传实付（分）；可小于院内下单 */
  txnAmtFen?: number;
  /** BPC 落库的 POS 实付（分）；退货优先用此值 */
  paidAmtFen?: number;
}

export interface ClientRefundResultDto {
  merTradeNo: string;
  tradeNo: string;
  refundMerTradeNo?: string;
  respCode: string;
  respMsg: string;
  refundAmountYuan?: number | string;
}
