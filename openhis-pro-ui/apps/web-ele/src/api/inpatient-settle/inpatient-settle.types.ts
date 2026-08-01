/**
 * 住院结算：`/inpatientSettle` 请求与响应类型（camelCase，与后端 DTO 一致；Long 主键用 string）。
 */

import type { OptionDto } from '#/api/inpatient-registration';
import type { PageApiResponse, PageParams } from '#/types/api-response';

/** 结算队列页签：`PENDING_SETTLE` / `SETTLED` */
export type InpatientSettleQueueTabCode = 'PENDING_SETTLE' | 'SETTLED';

/** 费用呈现：`DETAIL` / `SUMMARY` */
export type InpatientSettlePresentationMode = 'DETAIL' | 'SUMMARY';

/** 结算页初始化响应 */
export interface LoadInpatientSettlePageInitResponse {
  defaultPresentationMode: string;
  defaultSettleQueueTab: string;
  paymentChannelOptions: OptionDto[];
  presentationModeOptions: OptionDto[];
  settleQueueTabOptions: OptionDto[];
}

/** 结算患者队列分页请求 */
export interface PageInpatientSettleQueueRequest extends PageParams {
  admissionDate?: string;
  keyword?: string;
  settleQueueTab?: InpatientSettleQueueTabCode | string;
}

/** 结算患者队列行 */
export interface InpatientSettleQueueItemDto {
  accountBalance?: null | number | string;
  accountBalanceDisplayMode?: string;
  accountBalanceSubText?: string;
  accountBalanceSummaryText?: string;
  availableBalance?: null | number | string;
  lowBalanceWarning?: boolean;
  ageText?: string;
  bedName?: string;
  contractDisplay?: string;
  contractId?: string;
  encounterBusNo: string;
  encounterId: string;
  genderEnum?: string;
  genderEnumText?: string;
  lastSettleTime?: string;
  organizationName?: string;
  patientId: string;
  patientName: string;
  pendingSettlementTotal?: null | number | string;
  startTime?: string;
  statusEnum: string;
  statusEnumText: string;
  wardName?: string;
}

/** 结算患者队列分页响应 */
export type PageInpatientSettleQueueResponse = PageApiResponse<InpatientSettleQueueItemDto>;

/** 结算摘要请求（IF-IP-STL-03；选中患者后加载） */
export interface LoadInpatientSettleSummaryRequest {
  encounterId: string;
  patientId: string;
  selectedChargeItemIds?: string[];
}

/** 结算摘要响应 */
export interface LoadInpatientSettleSummaryResponse {
  accountBalanceDisplayMode?: string;
  accountBalanceSubText?: string;
  accountBalanceSummaryText?: string;
  availableBalance?: number | string;
  balanceHint: number | string;
  bedName?: string;
  displayAccountBalance: number | string;
  lowBalanceWarning?: boolean;
  encounterBusNo: string;
  encounterId: string;
  organizationName?: string;
  patientId: string;
  patientName: string;
  pendingSettlementTotal: number | string;
  prepayNetBalance: number | string;
  selectedTotal: number | string;
  statusEnum: string;
  statusEnumText: string;
  wardName?: string;
}

/** 结算专用费用查询请求 */
export interface QueryInpatientSettleChargesRequest {
  chargeDateFrom?: string;
  chargeDateTo?: string;
  encounterId: string;
  patientId: string;
  presentationMode: InpatientSettlePresentationMode | string;
}

/** 待结算费用明细行 */
export interface InpatientSettleChargeLineDto {
  categoryCode?: string;
  categoryDisplay?: string;
  chargeItemId: string;
  itemName?: string;
  lineAmount: number | string;
  occurrenceTime?: string;
  quantityUnit?: string;
  quantityValue?: null | number | string;
  selected: boolean;
  sourceText?: string;
  specText?: string;
  statusEnum: string;
  statusEnumText?: string;
  unitPrice?: null | number | string;
}

/** 费用大类汇总行 */
export interface InpatientSettleCategorySummaryDto {
  amount: number | string;
  categoryCode: string;
  categoryDisplay: string;
  lineCount: number;
}

/** 结算专用费用查询响应 */
export interface QueryInpatientSettleChargesResponse {
  categories?: InpatientSettleCategorySummaryDto[];
  defaultSelectAll: boolean;
  encounterId: string;
  lines?: InpatientSettleChargeLineDto[];
  pendingSettlementTotal: number | string;
  presentationMode: string;
}

/** 住院预结算请求 */
export interface PreSettleInpatientChargesRequest {
  acctUsedFlag?: string;
  chargeItemIds: string[];
  contractId?: string;
  encounterId: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  patientId: string;
  psnNo?: string;
  psnSetlway?: string;
  settleMode?: string;
  ybCardInfo?: string;
  ybMediumType?: string;
}

/** 住院预结算响应 */
export interface PreSettleInpatientChargesResponse {
  displayAmount?: null | number | string;
  insurancePreSettled?: boolean;
  paymentDraftId: string;
  previewText?: string;
  receivableTotal: number | string;
  settlementComposition?: string;
  settleMode?: string;
  ybAcctPay?: null | number | string;
  ybFundPaySumamt?: null | number | string;
  ybMedfeeSumamt?: null | number | string;
  ybPreSettleId?: string;
  ybPsnCashPay?: null | number | string;
}

/** 住院结算支付明细分录 */
export interface InpatientSettlePaymentDetailDto {
  amount: number | string;
  payChannelCode: string;
}

/** 确认住院结算请求 */
export interface ConfirmInpatientSettleRequest {
  billDate?: string;
  chargeItemIds: string[];
  clientScanResult?: import('#/api/payment/payment.types').ClientScanResultDto;
  encounterId: string;
  patientId: string;
  paymentDetails: InpatientSettlePaymentDetailDto[];
  paymentDraftId: string;
  ybPreSettleId?: string;
}

/** 确认住院结算响应 */
export interface ConfirmInpatientSettleResponse {
  advanceRefundPaymentId?: string;
  dischargeFullSettle?: boolean;
  insuranceSettled?: boolean;
  invoiceDraftId?: string;
  invoiceStatus?: string;
  paymentId: string;
  successMessageCode?: string;
  ybSetlId?: string;
}

/** 已结算/反结历史支付单摘要（IF-IP-STL-07 增量） */
export interface SettledPaymentBriefDto {
  payMethodText?: string;
  paymentId: string;
  /** 支付业务单号（fin_payment_reconciliation.payment_no） */
  paymentNo?: string;
  /** 展示绝对值，不含反结符号 */
  receivableTotal: number | string;
  /** FORWARD 正向结单 / REVERSE 逆向反结单 */
  recordType?: 'FORWARD' | 'REVERSE' | string;
  /** 逆向单指向的原单 ID */
  relationId?: string;
  /** reverseEnabled=false 时的 Tooltip 文案 */
  reverseDisabledReason?: string;
  /** 是否可在收银台点击反结（未离院） */
  reverseEnabled?: boolean;
  /** 是否可在收银台点击召回（已离院 DISCHARGED） */
  recallEnabled?: boolean;
  /** 带符号展示金额：正向为正、逆向为负 */
  signedAmount?: number | string;
  settleTime: string;
  /** PaymentStatus code，如 SUCCESS / REFUNDED */
  statusEnum?: string;
}

/** 已结算单查阅请求 */
export interface LoadSettledInpatientPaymentViewRequest {
  encounterId: string;
  patientId: string;
  paymentId?: string;
  presentationMode?: InpatientSettlePresentationMode | string;
}

/** 已结算单查阅响应（含历史 NET） */
export interface LoadSettledInpatientPaymentViewResponse {
  activePaymentId: string;
  categories?: InpatientSettleCategorySummaryDto[];
  encounterId: string;
  lines?: InpatientSettleChargeLineDto[];
  operatorName?: string;
  payMethodText?: string;
  payments: SettledPaymentBriefDto[];
  /** 历史 signedAmount 代数和，一正一负抵净 */
  netSettleTotal?: null | number | string;
  receivableTotal?: null | number | string;
  settleTime?: string;
}

/** 住院反结请求（IF-IP-RVS-01） */
export interface ReverseInpatientSettleRequest {
  encounterId: string;
  patientId: string;
  paymentId: string;
}

/** 住院反结响应 */
export interface ReverseInpatientSettleResponse {
  refundPaymentId?: string;
  succeeded?: boolean;
}

/** 住院出院召回请求（IF-IP-RCL-01；已离院） */
export interface RecallInpatientDischargeRequest {
  encounterId: string;
  patientId: string;
  /** 可选；空则后端取末次正向 SUCCESS 出院全结单 */
  paymentId?: string;
}

/** 住院出院召回响应 */
export interface RecallInpatientDischargeResponse {
  encounterId?: string;
  refundPaymentId?: string;
  requiresBedAssignment?: boolean;
  succeeded?: boolean;
  targetStatusEnum?: string;
}
