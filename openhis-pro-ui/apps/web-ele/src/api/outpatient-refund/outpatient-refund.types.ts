/**
 * 门诊退费应用服务请求/响应类型（与后端 camelCase、Long→JSON 字符串约定一致）。
 */

import type { OptionDto } from '#/api/outpatient-registration';

/** 队列 Tab：`ALL` / `PENDING`（待退费）/ `SETTLED`（已退费，与后端 OutpatientRefundQueueTab 一致） */
export type OutpatientRefundQueueTabCode = 'ALL' | 'PENDING' | 'SETTLED';

/** 退费页初始化返回 */
export interface LoadOutpatientRefundPageInitResponse {
  chargeItemStatusOptions: OptionDto[];
  paymentChannelOptions?: OptionDto[];
}

/** 退费页初始化请求 */
export type LoadOutpatientRefundPageInitRequest = Record<string, never>;

/** 待退费患者队列行 */
export interface OutpatientRefundQueueRowDto {
  ageText?: null | string;
  chargeItemStatusSummary?: null | string;
  doctorName?: null | string;
  encounterBusNo?: null | string;
  encounterId: string;
  genderCode?: null | string;
  genderEnumText?: null | string;
  organizationName?: null | string;
  paidAmount?: null | number | string;
  patientId?: null | string;
  patientName: string;
  queueLabelCode: string;
  startTime?: null | string;
  totalAmount?: null | number | string;
}

/** 分页查询待退费队列请求 */
export interface PageOutpatientRefundQueueRequest {
  endDate?: null | string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
  queueTabCode: string;
  startDate?: null | string;
}

/** 分页查询待退费队列响应（PagedResult：content + totalCount） */
export interface PageOutpatientRefundQueueResponse {
  content?: OutpatientRefundQueueRowDto[];
  pageNo?: number;
  pageSize?: number;
  records?: OutpatientRefundQueueRowDto[];
  rows?: OutpatientRefundQueueRowDto[];
  total?: number | string;
  totalCount?: number | string;
}

/** 待执行退费明细行 */
export interface OutpatientRefundLineItemDto {
  chargeItemId: string;
  chargeItemStatusEnum: string;
  itemDisplayName?: null | string;
  lineTotalAmount?: null | number | string;
  paymentId?: null | string;
}

/** 按原支付分组的退费执行明细 */
export interface OutpatientRefundPaymentGroupDto {
  halfRefundOnPayment: boolean;
  /** 原支付落库渠道码（fin_payment_rec_detail.pay_enum 去重） */
  originalPayChannelCodes?: string[];
  paymentBusNo?: null | string;
  paymentId: string;
  paymentStatusEnum?: null | string;
  preservedLines: OutpatientRefundLineItemDto[];
  refundableAmount: number | string;
  refundingLines: OutpatientRefundLineItemDto[];
}

/** 加载就诊退费明细请求（与 OutpatientRefundQueueTab 一致） */
export interface LoadOutpatientRefundExecutionDetailRequest {
  encounterId: string;
  /** PENDING=待执行拟退；SETTLED=已退费只读；ALL=合并；缺省 PENDING */
  detailViewCode?: OutpatientRefundQueueTabCode | string;
}

/** 加载就诊待执行退费明细响应 */
export interface LoadOutpatientRefundExecutionDetailResponse {
  paymentGroups: OutpatientRefundPaymentGroupDto[];
}

/** 查询原支付落库渠道码请求 */
export interface ListOriginalPayChannelCodesRequest {
  paymentId: string;
}

/** 执行前可退门禁请求 */
export interface VerifyRefundableBeforeExecutionRequest {
  encounterId: string;
  refundingChargeItemIds: string[];
}

/** 执行前可退门禁响应 */
export interface VerifyRefundableBeforeExecutionResponse {
  allowed: boolean;
  rejectReasonCode?: null | string;
}

/** 退款渠道实退拆分 */
export interface RefundPaymentDetailSubmitDto {
  amount: number | string;
  payChannelCode: string;
}

/** 按原支付执行门诊账单退费请求 */
export interface ExecuteOutpatientBillRefundByPaymentRequest {
  billDate?: null | string;
  clientRefundResult?: import('#/api/payment/payment.types').ClientRefundResultDto;
  encounterId: string;
  originalPaymentId: string;
  preservedNewChargeItemIds?: string[];
  refundingChargeItemIds: string[];
  refundPaymentDetails: RefundPaymentDetailSubmitDto[];
}

/** 按原支付执行门诊账单退费响应 */
export interface ExecuteOutpatientBillRefundByPaymentResponse {
  halfRefundExecuted?: boolean;
  insuranceRevoked?: boolean;
  originalPaymentId?: string;
  reversePaymentId?: string;
  success: boolean;
}

/** 半退保留行转账户响应 */
export interface SwitchPreservedChargeItemsAccountResponse {
  success: boolean;
}

/** 半退保留行转账户请求 */
export interface SwitchPreservedChargeItemsRequest {
  encounterId: string;
}
