/**
 * 住院医保协同单：`/insuranceInpatientSettlement` 类型。
 */

/** 查询住院医保结算协同单状态请求 */
export interface GetIpInsuranceSettleStatusRequest {
  paymentId: string;
}

/** 住院医保结算协同单状态响应 */
export interface GetIpInsuranceSettleStatusResponse {
  completed?: boolean;
  coordinationId?: string;
  errorMsg?: string;
  invoiceDraftId?: string;
  paymentId?: string;
  paymentStatus?: string;
  state?: string;
  ybSetlId?: string;
}

/** 协同单终态（轮询结束） */
export type IpInsuranceCoordinationTerminalState =
  | 'CANCELLED'
  | 'COMPLETED'
  | 'COMPENSATED'
  | 'FAILED'
  | 'UNKNOWN';
