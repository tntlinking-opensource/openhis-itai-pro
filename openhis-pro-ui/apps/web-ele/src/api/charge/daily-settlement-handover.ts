/**
 * 门诊/住院日结交账弹窗共用 DTO（IF-OP/IP-DCL-02 items enrichment + handoverSummary）
 */

/** 单笔 payment 下的收费项目行。 */
export interface DailySettlementPaymentChargeLineDto {
  itemName?: string;
  unitPrice?: string;
  quantity?: string;
  lineAmount?: string;
  finTypeCode?: string;
}

/** 单笔 payment 下的自付支付方式行。 */
export interface DailySettlementPaymentPayMethodDto {
  payMethodCode?: string;
  payMethodName?: string;
  amount?: string;
}

/** 待交账候选 payment 明细（弹窗表格行）。 */
export interface DailySettlementHandoverPaymentItemDto {
  paymentId?: string;
  paymentNo?: string;
  kindEnum?: string;
  statusEnum?: string;
  billDate?: string;
  paymentEnum?: string;
  relationId?: string;
  tenderedAmount?: string;
  returnedAmount?: string;
  netAmount?: string;
  hasInsurance?: boolean;
  contractNo?: string;
  /** 住院：病区名 / 预交金分类。 */
  wardName?: string;
  categoryLabel?: string;
  chargeLines?: DailySettlementPaymentChargeLineDto[];
  payMethods?: DailySettlementPaymentPayMethodDto[];
}

/** 上半区财务类别行（handoverRows 与 rows 同构）。 */
export interface DailySettlementHandoverChannelRowDto {
  categoryCode?: string;
  category?: string;
  amount?: string;
  unit?: string;
}

/** 下半区固定汇总项（handoverSummary 与 summary 同构，字段子集因门诊/住院略有差异）。 */
export interface DailySettlementHandoverSummaryDto {
  dateRange?: string;
  totalIncome?: string;
  receivableAmount?: string;
  discountAmount?: string;
  actualAmount?: string;
  wechatAmount?: string;
  alipayAmount?: string;
  onlineBankAmount?: string;
  cashAmount?: string;
  depositAmount?: string;
  selfPayCashTotal?: string;
  selfPayCount?: number;
  provincialInsuranceCount?: number;
  municipalInsuranceCount?: number;
  provincialInsuranceCash?: string;
  provincialInsuranceFund?: string;
  provincialInsuranceAccount?: string;
  provincialInsuranceFundTotal?: string;
  provincialInsuranceTotal?: string;
  municipalInsuranceCash?: string;
  municipalInsuranceFund?: string;
  municipalInsuranceAccount?: string;
  municipalInsuranceFundTotal?: string;
  municipalInsuranceTotal?: string;
  totalCashIncome?: string;
  physicalExamCount?: number;
  insuranceCount?: number;
  refundTotal?: string;
}
