/**
 * 门诊收费应用服务请求/响应类型（与后端 camelCase、Long→JSON 字符串约定一致）。
 */

import type { OptionDto } from '#/api/outpatient-registration';

/** 队列 Tab：`ALL` / `PENDING` / `SETTLED` */
export type OutpatientChargeQueueTabCode = 'ALL' | 'PENDING' | 'SETTLED';

/** IF-OPC-01 初始化返回 */
export interface LoadChargePageInitResponse {
  chargeItemStatusOptions: OptionDto[];
}

/** IF-OPC-01 初始化请求 */
export interface LoadChargePageInitRequest {
  organizationId?: null | string;
}

/** IF-OPC-02 队列行 */
export interface ChargeQueueRowDto {
  ageText?: null | string;
  doctorName?: null | string;
  encounterBusNo?: null | string;
  encounterId: string;
  genderCode?: null | string;
  /** 读模型性别展示文案，优先于 genderCode */
  genderEnumText?: null | string;
  organizationName?: null | string;
  patientId: string;
  patientName: string;
  paidAmount?: null | number | string;
  queueLabelCode: string;
  startTime?: null | string;
  totalAmount?: null | number | string;
}

/** IF-OPC-02 分页请求 */
export interface PageOutpatientChargeQueueRequest {
  endDate?: null | string;
  keyword?: string;
  organizationId?: null | string;
  pageNo: number;
  pageSize: number;
  queueTabCode: string;
  startDate?: null | string;
}

/** IF-OPC-02 分页响应 */
export interface PageOutpatientChargeQueueResponse {
  records: ChargeQueueRowDto[];
  total: number | string;
}

/** IF-OPC-03 待收明细行 */
export interface PendingChargeItemRowDto {
  accountId?: null | string;
  busNo?: null | string;
  cashierName?: null | string;
  cashierUserId?: null | string;
  chargeCategoryCode?: null | string;
  chargeCategoryDisplay?: null | string;
  chargeItemId: string;
  /** 费用性质合同主键（医保读卡/预结算） */
  contractId?: null | string;
  discountRateDisplay?: null | string;
  /** 费用性质展示（后端补齐后展示） */
  feeNatureDisplay?: null | string;
  itemName?: null | string;
  /** 医保编码（后端补齐后展示） */
  medicalInsuranceCode?: null | string;
  /** 医疗类别 med_type */
  medTypeCode?: null | string;
  quantityDisplay?: null | string;
  quantityUnit?: null | string;
  quantityValue?: null | number | string;
  serviceId?: null | string;
  serviceTable?: null | string;
  statusCode: string;
  statusText?: null | string;
  totalPrice?: null | number | string;
  unitPrice?: null | number | string;
}

/** IF-OPC-03 请求 */
export interface LoadPendingChargeItemsRequest {
  encounterId: string;
  includePriceExtension?: boolean;
}

/** IF-OPC-03 响应 */
export interface LoadPendingChargeItemsResponse {
  encounterId: string;
  items: PendingChargeItemRowDto[];
}

/** IF-OPC-04 / 05 切换账户响应 */
export interface SwitchEncounterChargeItemsAccountResponse {
  messageCode?: null | string;
  success: boolean;
}

/** IF-OPC-04 / 05 请求 */
export interface SwitchEncounterChargeItemsRequest {
  encounterId: string;
}

/** IF-OPC-06 预结算预览行 */
export interface PreSettleChargeLinePreviewDto {
  chargeItemId: string;
  itemLabel?: null | string;
  lineAmount: number | string;
  quantityDisplay?: null | string;
  quantityUnit?: null | string;
}

/** 医保预结算分组 */
export interface OpInsurancePreSettleGroupDto {
  accountId?: null | string;
  acctPay?: null | number | string;
  chargeItemIds?: string[];
  chrgBchno?: null | string;
  contractId?: null | string;
  diseCodg?: null | string;
  diseName?: null | string;
  fundPaySumamt?: null | number | string;
  groupIndex?: null | number;
  mdtrtId?: null | string;
  medType?: null | string;
  medTypeDisplay?: null | string;
  medfeeSumamt?: null | number | string;
  preSettleId?: null | string;
  psnCashPay?: null | number | string;
}

/** 自费汇总 */
export interface OpSelfPayRollupDto {
  accountId?: null | string;
  chargeItemIds?: string[];
  totalAmount?: null | number | string;
}

/** IF-OPC-06 请求 */
export interface PreSettleOutpatientChargesRequest {
  chargeItemIds: string[];
  contractId?: null | string;
  encounterId: string;
  insuplcAdmdvs?: null | string;
  insutype?: null | string;
  medType?: null | string;
  psnNo?: null | string;
  ybCardInfo?: null | string;
  ybMediumType?: null | string;
}

/** IF-OPC-06 响应 */
export interface PreSettleOutpatientChargesResponse {
  displayAmount: number | string;
  insuranceGroups?: OpInsurancePreSettleGroupDto[];
  insurancePreSettled?: boolean;
  paymentDraftId: string;
  previews: PreSettleChargeLinePreviewDto[];
  selfPayRollup?: null | OpSelfPayRollupDto;
  /** MIXED | INSURANCE_ONLY | SELF_PAY_ONLY */
  settlementComposition?: null | string;
  ybAcctPay?: null | number | string;
  ybChrgBchno?: null | string;
  ybFundPaySumamt?: null | number | string;
  ybMedfeeSumamt?: null | number | string;
  ybPsnCashPay?: null | number | string;
}

/** IF-OPC-07 支付明细分录 */
export interface PaymentDetailSubmitDto {
  amount: number | string;
  payChannelCode: string;
}

/** IF-OPC-07 正式结算请求 */
export interface ConfirmOutpatientChargeSettlementRequest {
  billDate: string;
  chargeItemIds: string[];
  encounterId: string;
  outcomeEnum: string;
  paymentDetails: PaymentDetailSubmitDto[];
  paymentDraftId: string;
  ybChrgBchno?: null | string;
  ybChrgBchnos?: string[];
  ybSettleIds?: null | string;
}

/** IF-OPC-07 正式结算响应 */
export interface ConfirmOutpatientChargeSettlementResponse {
  coordinationIds?: string[];
  invoiceId: string;
  paymentId: string;
  paymentStatus?: null | string;
  settlementMode?: null | string;
  successMessageCode?: null | string;
  ybSetlId?: null | string;
}
