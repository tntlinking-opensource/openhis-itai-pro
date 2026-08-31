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
  attendingDoctorName?: string;
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
  attendingDoctorName?: string;
  availableBalance?: number | string;
  /**
   * 差额提示：库内余额 − 选中合计（调试保留；UI 主路径勿当应付）。
   */
  balanceHint: number | string;
  bedName?: string;
  /** 渠道实付 C 预估（自费摘要；医保挂账户时为空，须预结算） */
  channelPayable?: null | number | string;
  /** 预交金冲抵 O 预估 */
  depositOffsetAmount?: null | number | string;
  /** 轧差分支 R1～R7（自费预估） */
  depositSettleRuleCode?: null | string;
  displayAccountBalance: number | string;
  lowBalanceWarning?: boolean;
  encounterBusNo: string;
  encounterId: string;
  organizationName?: string;
  patientId: string;
  patientName: string;
  pendingSettlementTotal: number | string;
  /** 个人现金应付 P 预估（自费≈选中合计；医保须预结算） */
  personalCashPayable?: null | number | string;
  /** 是否全额退预交金预估 */
  prepayFullRefund?: boolean | null;
  prepayNetBalance: number | string;
  /** 应退预交金预估 */
  prepayRefundAmount?: null | number | string;
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
  /** 医保等级 code */
  chrgitmLv?: null | number | string;
  /** 医保等级展示（甲类/乙类/丙类） */
  chrgitmLvText?: string;
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

/** 住院费用明细单批上传请求（2301 进度条） */
export interface UploadInpatientFeeDetailsBatchRequest {
  acctUsedFlag?: string;
  chargeItemIds: string[];
  contractId?: string;
  encounterId: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  /** 上批返回的平台就诊 ID；首批可空 */
  mdtrtId?: string;
  patientId: string;
  psnNo?: string;
  psnSetlway?: string;
  settleMode?: string;
  ybCardInfo?: string;
  ybMediumType?: string;
}

/** 住院费用明细单批上传响应 */
export interface UploadInpatientFeeDetailsBatchResponse {
  batchUploaded: number;
  done: boolean;
  mdtrtId?: string;
  remainingAfter: number;
}

/** 住院平台在院对齐请求 */
export interface AlignInpatientPlatformVisitRequest {
  contractId: string;
  encounterId: string;
  insuplcAdmdvs?: string;
  patientId: string;
  psnNo: string;
}

/** 住院平台在院对齐响应 */
export interface AlignInpatientPlatformVisitResponse {
  advice?: string;
  localMdtrtId?: string;
  mismatchCode?: string;
  platformInHospitalMdtrtIds?: string[];
  repaired?: boolean;
  repairSummary?: string;
}

/** 医保出院预交金原路退计划项 */
export interface InpatientPrepayRefundPlanItemDto {
  amount: number | string;
  originalPaymentId?: string;
  payChannelCode?: string;
  requiresClientRefund?: boolean;
}

/** 住院预结算响应 */
export interface PreSettleInpatientChargesResponse {
  /**
   * 渠道实付 C（确认 `paymentDetails` 合计须等于本值；C=0 可空明细）。
   * 不再表示「自费少补 / 医保全额」双语义。
   */
  displayAmount?: null | number | string;
  /** 预交金冲抵额 O */
  depositOffsetAmount?: null | number | string;
  /** 轧差分支 R1～R7 */
  depositSettleRuleCode?: null | string;
  insurancePreSettled?: boolean;
  paymentDraftId: string;
  /** 个人现金应付 P（结算主单金额；医保=psnCashPay，自费=本批应收） */
  personalCashPayable?: null | number | string;
  /** 是否全额退清预交金 D（R4 / R6 含银联） */
  prepayFullRefund?: boolean | null;
  /** 库内预交金余额 D */
  prepayNetBalance?: null | number | string;
  /** 应退预交金金额（R3=D−P；R4/R6=D；R1/R2=0） */
  prepayRefundAmount?: null | number | string;
  /** 预交金是否够用（D≥P） */
  prepaySufficient?: boolean;
  /** 出院原路退预交金拆分计划（自费/医保共用） */
  prepayRefundPlan?: InpatientPrepayRefundPlanItemDto[];
  previewText?: string;
  receivableTotal: number | string;
  settlementComposition?: string;
  settleMode?: string;
  ybAcctPay?: null | number | string;
  ybActPayDedc?: null | number | string;
  ybCvlservFlag?: null | string;
  ybCvlservPay?: null | number | string;
  ybFundPaySumamt?: null | number | string;
  ybFulamtOwnpayAmt?: null | number | string;
  ybHifpPay?: null | number | string;
  ybInscpScpAmt?: null | number | string;
  ybMedfeeSumamt?: null | number | string;
  ybOthPay?: null | number | string;
  ybOverlmtSelfpay?: null | number | string;
  ybPreselfpayAmt?: null | number | string;
  ybPreSettleId?: string;
  ybPsnCashPay?: null | number | string;
}

/** 住院结算支付明细分录 */
export interface InpatientSettlePaymentDetailDto {
  amount: number | string;
  payChannelCode: string;
}

/** 确认住院结算：预交金原路退 Win 回传项 */
export interface InpatientSettleClientRefundItemDto {
  clientRefundResult: import('#/api/payment/payment.types').ClientRefundResultDto;
  originalPaymentId: string;
}

/** 确认住院结算请求 */
export interface ConfirmInpatientSettleRequest {
  billDate?: string;
  chargeItemIds: string[];
  clientRefundResults?: InpatientSettleClientRefundItemDto[];
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
  /** 预交金扫码原路退是否待第二阶段 */
  prepayRefundPending?: boolean;
  /** 待原路退计划（pending 时有值） */
  prepayRefundPlan?: InpatientPrepayRefundPlanItemDto[];
  successMessageCode?: string;
  ybSetlId?: string;
}

/** 医保出院第二阶段：预交金原路退完结请求 */
export interface CompleteInpatientDischargePrepayRefundRequest {
  clientRefundResults?: InpatientSettleClientRefundItemDto[];
  encounterId: string;
  patientId: string;
}

/** 已结算/反结历史支付单摘要（IF-IP-STL-07 增量） */
export interface SettledPaymentBriefDto {
  /** 渠道实收合计 C（现金/微信等；不含冲抵与医保基金） */
  channelPayable?: null | number | string;
  /** 预交金冲抵额 O */
  depositOffsetAmount?: null | number | string;
  /** 是否已关联医保正式结算回执 */
  insuranceSettled?: boolean;
  payMethodText?: string;
  paymentId: string;
  /** 支付业务单号（fin_payment_reconciliation.payment_no） */
  paymentNo?: string;
  /** 个人现金应付 P（与主单 display/tendered 一致） */
  personalCashPayable?: null | number | string;
  /** 展示绝对值，不含反结符号（优先认 P） */
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
  /** 带符号展示金额：正向为正、逆向为负（认 P） */
  signedAmount?: number | string;
  settleTime: string;
  /** PaymentStatus code，如 SUCCESS / REFUNDED */
  statusEnum?: string;
  ybAcctPay?: null | number | string;
  ybActPayDedc?: null | number | string;
  ybCvlservPay?: null | number | string;
  ybFulamtOwnpayAmt?: null | number | string;
  ybFundPaySumamt?: null | number | string;
  ybHifpPay?: null | number | string;
  ybInscpScpAmt?: null | number | string;
  ybMedfeeSumamt?: null | number | string;
  ybOthPay?: null | number | string;
  ybOverlmtSelfpay?: null | number | string;
  ybPreselfpayAmt?: null | number | string;
  ybPsnCashPay?: null | number | string;
  /** 结算模式：DISCHARGE_FULL / MID_SETTLE */
  settleMode?: string;
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
