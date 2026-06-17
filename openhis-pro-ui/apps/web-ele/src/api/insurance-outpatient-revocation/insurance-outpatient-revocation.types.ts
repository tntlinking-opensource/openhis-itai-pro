/** 门诊 OP_SETTLE_REVERSE 协同单状态查询 */

export interface GetOpInsuranceRevokeStatusRequest {
  originalPaymentId: string;
}

export interface GetOpInsuranceRevokeStatusResponse {
  completed?: boolean;
  coordinationId?: string;
  errorMsg?: null | string;
  insuranceRevoked?: boolean;
  originalPaymentId?: string;
  state?: null | string;
  ybSetlId?: null | string;
}
