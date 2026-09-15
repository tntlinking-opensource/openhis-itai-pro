/** 可退账单行（加载可申请退费账单行） */
export interface RefundableChargeLineItemDto {
  paymentId: string;
  paymentBusNo?: string;
  paymentStatusEnum: string;
  paymentStatusEnumText?: string;
  chargeItemId: string;
  encounterId: string;
  serviceTable: string;
  serviceId: string;
  chargeItemStatusEnum: string;
  chargeItemStatusEnumText?: string;
  refundStatusText: string;
  itemDisplayName?: string;
  quantity?: null | number | string;
  /** 数量单位展示（如克/盒） */
  quantityUnit?: null | string;
  lineTotalAmount?: null | number | string;
  paymentGroupSortKey: string;
}

export interface ListRefundableChargeLinesForEncounterRequest {
  encounterId: string;
}

export interface ListRefundableChargeLinesForEncounterResponse {
  lines: RefundableChargeLineItemDto[];
}

export interface VerifyConsumableExecutionBeforeRefundRequest {
  encounterId: string;
  chargeItemIds: string[];
}

export interface VerifyConsumableExecutionBeforeRefundResponse {
  allowed: boolean;
  rejectReasonCode?: string;
}

/** 提交单行：支付锚点 + 收费行锚点 + 本单退费 */
export interface RefundApplicationLineItemDto {
  paymentId: string;
  chargeItemId: string;
  refundForCurrentBill: boolean;
}

export interface SubmitOutpatientRefundApplicationRequest {
  encounterId: string;
  lines: RefundApplicationLineItemDto[];
}

export interface SubmitOutpatientRefundApplicationResponse {
  success: boolean;
  refundingChargeItemCount: number;
}

/** 撤销退费提交行：支付锚点 + 收费行锚点 */
export interface CancelRefundApplicationLineItemDto {
  paymentId: string;
  chargeItemId: string;
}

export interface CancelOutpatientRefundApplicationRequest {
  encounterId: string;
  lines: CancelRefundApplicationLineItemDto[];
}

export interface CancelOutpatientRefundApplicationResponse {
  success: boolean;
  settledChargeItemCount: number;
}
