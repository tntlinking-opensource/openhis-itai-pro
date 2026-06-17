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
  balanceHint: number | string;
  bedName?: string;
  displayAccountBalance: number | string;
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
  encounterId: string;
  patientId: string;
  paymentDetails: InpatientSettlePaymentDetailDto[];
  paymentDraftId: string;
  ybPreSettleId?: string;
}

/** 确认住院结算响应 */
export interface ConfirmInpatientSettleResponse {
  advanceRefundPaymentId?: string;
  coordinationIds?: string[];
  dischargeFullSettle?: boolean;
  insuranceSettled?: boolean;
  invoiceDraftId?: string;
  paymentId: string;
  paymentStatus?: string;
  settlementMode?: string;
  successMessageCode?: string;
  ybSetlId?: string;
}

/** 已结算支付单简要信息 */
export interface SettledPaymentBriefDto {
  payMethodText?: string;
  paymentId: string;
  receivableTotal: number | string;
  settleTime: string;
}

/** 已结算单查阅请求 */
export interface LoadSettledInpatientPaymentViewRequest {
  encounterId: string;
  patientId: string;
  paymentId?: string;
  presentationMode?: InpatientSettlePresentationMode | string;
}

/** 已结算单查阅响应 */
export interface LoadSettledInpatientPaymentViewResponse {
  activePaymentId: string;
  categories?: InpatientSettleCategorySummaryDto[];
  encounterId: string;
  lines?: InpatientSettleChargeLineDto[];
  operatorName?: string;
  payMethodText?: string;
  payments: SettledPaymentBriefDto[];
  receivableTotal?: null | number | string;
  settleTime?: string;
}

/** 取消住院结算请求（CHS Wave 2 外挂） */
export interface CancelInpatientSettleRequest {
  encounterId: string;
  patientId: string;
  paymentId: string;
}

/** 取消住院结算响应 */
export interface CancelInpatientSettleResponse {
  insuranceRevoked?: boolean;
  refundPaymentId?: string;
  ybSetlId?: string;
}
