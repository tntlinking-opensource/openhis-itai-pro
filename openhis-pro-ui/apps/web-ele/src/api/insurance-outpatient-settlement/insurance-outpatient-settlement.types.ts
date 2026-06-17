/** 门诊 OP_SETTLE 协同单状态查询 */

export interface GetOpInsuranceSettleStatusRequest {
  paymentId: string;
}

export interface GetOpInsuranceSettleStatusResponse {
  completed?: boolean;
  coordinationId?: string;
  errorMsg?: null | string;
  invoiceId?: null | string;
  paymentId?: string;
  paymentStatus?: null | string;
  state?: null | string;
  ybSetlId?: null | string;
}
