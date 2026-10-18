/**
 * 门诊收费应用服务请求/响应类型（与后端 camelCase、Long→JSON 字符串约定一致）。
 */

import type { OptionDto } from '#/api/outpatient-registration';

/** 队列 Tab：`ALL` / `PENDING` / `SETTLED` */
export type OutpatientChargeQueueTabCode = 'ALL' | 'PENDING' | 'SETTLED';

/** IF-OPC-01 初始化返回 */
export interface LoadChargePageInitResponse {
  chargeItemStatusOptions: OptionDto[];
  paymentChannelOptions?: OptionDto[];
  /** 是否已配置 ACTIVE 打折内容；false 时收银台不展示折扣控件 */
  discountFeatureEnabled?: boolean;
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
  /** 患者身份证号（BR-OPC-035：读卡核验） */
  idCard?: null | string;
  paidAmount?: null | number | string;
  /** 最近一次成功支付流水（补打收费小票） */
  paymentId?: null | string;
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
  /**
   * 费用性质合同分类码（fin_contract.category_enum，如 3=省医保）。
   * 与 chargeCategoryCode（药品/项目目录分类）不同，收银读卡判断用本字段。
   */
  contractCategoryCode?: null | string;
  discountRateDisplay?: null | string;
  /** 费用性质展示（后端补齐后展示） */
  feeNatureDisplay?: null | string;
  itemName?: null | string;
  /** 医保编码（后端补齐后展示） */
  medicalInsuranceCode?: null | string;
  /** 医保类别码（CHRGITM_LV） */
  chrgitmLv?: null | number | string;
  /** 医保类别展示（甲类/乙类/丙类） */
  chrgitmLvText?: null | string;
  /** 就诊类型 code（YbMedType / adm_encounter_diagnosis.med_type） */
  medTypeCode?: null | string;
  /** 就诊类型展示（YbMedType 文案；供收费项目列表「就诊类型」列） */
  medTypeDisplay?: null | string;
  /** 病种展示名（绑定诊断首位；供收费列表「病种」列） */
  diseName?: null | string;
  /** 绑定就诊诊断主键有序串（首位为主诊断） */
  encounterDiagnosisIds?: null | string;
  quantityDisplay?: null | string;
  quantityUnit?: null | string;
  quantityValue?: null | number | string;
  serviceId?: null | string;
  serviceTable?: null | string;
  statusCode: string;
  statusText?: null | string;
  totalPrice?: null | number | string;
  /**
   * 折后应收（ChargeItem.discount_amount）。
   * 结算后为实收刻度；未打折可与 totalPrice 相同。患者信息栏「已收/折扣」用本字段，勿用 Number 转主键。
   */
  discountAmount?: null | number | string;
  unitPrice?: null | number | string;
  /** 折后价（列表试算回填；未试算时页面回落金额） */
  discountedAmount?: null | number | string;
  /**
   * 执行科室主键（诊疗申请 org_id，缺省 location_id）。
   * Long 以字符串传输，禁止 Number()。
   */
  performOrgId?: null | string;
  /** 执行科室名称（导诊按科室拆单、申请单回显） */
  performOrgName?: null | string;
}

/** IF-OPC-03 请求 */
export interface LoadPendingChargeItemsRequest {
  encounterId: string;
  includePriceExtension?: boolean;
  /** 队列行最近成功支付流水；用于已结算医保摘要 */
  paymentId?: null | string;
}

/** 国家局 2206/2207 setlinfo 常见分摊字段（正式结算回执） */
export interface ChargeSettlementSetlinfoDto {
  acctMulaidPay?: null | number | string;
  acctPay?: null | number | string;
  actPayDedc?: null | number | string;
  balc?: null | number | string;
  chrgBchno?: null | string;
  clrOptins?: null | string;
  clrType?: null | string;
  clrWay?: null | string;
  /** 公务员标志（0=否 / 1=是） */
  cvlservFlag?: null | string;
  cvlservPay?: null | number | string;
  fulamtOwnpayAmt?: null | number | string;
  fundPaySumamt?: null | number | string;
  hifdmPay?: null | number | string;
  hifesPay?: null | number | string;
  hifmiPay?: null | number | string;
  hifobPay?: null | number | string;
  hifpPay?: null | number | string;
  hospPartAmt?: null | number | string;
  inscpScpAmt?: null | number | string;
  mafPay?: null | number | string;
  mdtrtId?: null | string;
  medfeeSumamt?: null | number | string;
  othPay?: null | number | string;
  overlmtSelfpay?: null | number | string;
  poolPropSelfpay?: null | number | string;
  preselfpayAmt?: null | number | string;
  psnCashPay?: null | number | string;
  psnPartAmt?: null | number | string;
  setlId?: null | string;
}

/** 已结算支付医保分摊摘要（与收费小票结算单口径一致） */
export interface ChargeSettlementInsuranceSummaryDto {
  acctPay?: null | number | string;
  billDate?: null | string;
  /** 收款人姓名（支付流水 enterer_id → 医务人员姓名） */
  cashierName?: null | string;
  /** 本支付单据关联的收费项主键 */
  chargeItemIds?: null | string[];
  /** 本支付关联收费明细（只读；可含收银台列表外的挂号费/已退费项） */
  chargeLines?: null | ChargeSettlementPaymentLineDto[];
  displayAmount?: null | number | string;
  fundPaySumamt?: null | number | string;
  insuranceSettled?: boolean;
  medfeeSumamt?: null | number | string;
  paymentId?: null | string;
  paymentNo?: null | string;
  /** 本支付实收渠道展示（如「微信」「医保统筹、微信」） */
  payMethodText?: null | string;
  psnCashPay?: null | string | number;
  /** 2206/2207 setlinfo 细项；无正式回执时为空 */
  setlinfo?: ChargeSettlementSetlinfoDto | null;
}

/** 支付记录弹窗只读明细行 */
export interface ChargeSettlementPaymentLineDto {
  busNo?: null | string;
  chargeItemId?: null | string;
  itemName?: null | string;
  quantityDisplay?: null | string;
  quantityUnit?: null | string;
  statusCode?: null | string;
  statusText?: null | string;
  totalPrice?: null | number | string;
}

/** IF-OPC-03 响应 */
export interface LoadPendingChargeItemsResponse {
  /** 是否允许转医保/转自费（医保双账户挂号为 true，纯自费挂号为 false） */
  accountSwitchEnabled?: boolean;
  encounterId: string;
  items: PendingChargeItemRowDto[];
  /** 本就诊全部成功支付摘要（账单时间倒序）；供切换支付单据 */
  settlementInsuranceOptions?: ChargeSettlementInsuranceSummaryDto[] | null;
  /** 当前选中支付的结算摘要；无成功支付时为空 */
  settlementInsuranceSummary?: ChargeSettlementInsuranceSummaryDto | null;
}

/** IF-OPC-04 / 05 切换账户响应 */
export interface SwitchEncounterChargeItemsAccountResponse {
  messageCode?: null | string;
  success: boolean;
}

/** IF-OPC-04 / 05 请求 */
export interface SwitchEncounterChargeItemsRequest {
  /** 勾选收费项主键；空则后端按就诊整批可切换行处理 */
  chargeItemIds?: string[];
  encounterId: string;
}

/** IF-OPC-06 预结算预览行 */
export interface PreSettleChargeLinePreviewDto {
  chargeItemId: string;
  /** 本行有效折扣率 (0,1]；未打折为空 */
  discountRate?: null | number | string;
  /** 费用性质展示（账户 contract_no → fin_contract 名称，与 IF-OPC-03 同口径） */
  feeNatureDisplay?: null | string;
  itemLabel?: null | string;
  lineAmount: number | string;
  /** 行原价合计（结算窗「金额」列） */
  originalAmount?: null | number | string;
  /** 单价（原价口径，结算窗「单价」列） */
  unitPrice?: null | number | string;
  /** 折后应收（结算窗「折后价」列；未打折=原价） */
  discountedAmount?: null | number | string;
  quantityDisplay?: null | string;
  quantityUnit?: null | string;
  /** 费用分类展示 */
  chargeCategoryDisplay?: null | string;
}

/** 医保预结算分组 */
export interface OpInsurancePreSettleGroupDto {
  accountId?: null | string;
  acctPay?: null | number | string;
  /** 家庭共济（2206 acct_mulaid_pay；含于 acctPay） */
  acctMulaidPay?: null | number | string;
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
  /** 本组起付线（单病种一笔的平台报价） */
  actPayDedc?: null | number | string;
  /** 本组统筹 */
  hifpPay?: null | number | string;
}

/** 自费汇总 */
export interface OpSelfPayRollupDto {
  accountId?: null | string;
  chargeItemIds?: string[];
  totalAmount?: null | number | string;
}

/** IF-OPC-06 请求 */
export interface PreSettleOutpatientChargesRequest {
  /** 款员所选折扣率 (0,1]；仅已配置有效折扣时可传；含款员定率项目时必传 */
  cashierDiscountRate?: null | number;
  chargeItemIds: string[];
  contractId?: null | string;
  encounterId: string;
  insuplcAdmdvs?: null | string;
  insutype?: null | string;
  medType?: null | string;
  psnNo?: null | string;
  /** 1101 参保人身份证号（BR-OPC-035） */
  payerCertno?: null | string;
  /** 1101 参保人姓名（BR-OPC-035） */
  payerPsnName?: null | string;
  ybCardInfo?: null | string;
  ybMediumType?: null | string;
}

/** IF-OPC-06 响应 */
export interface PreSettleOutpatientChargesResponse {
  displayAmount: number | string;
  /** 本批优惠合计（原价−折后）；未打折为 0 */
  discountAmountTotal?: null | number | string;
  insuranceGroups?: OpInsurancePreSettleGroupDto[];
  insurancePreSettled?: boolean;
  paymentDraftId: string;
  previews: PreSettleChargeLinePreviewDto[];
  selfPayRollup?: null | OpSelfPayRollupDto;
  /** MIXED | INSURANCE_ONLY | SELF_PAY_ONLY | WORK_INJURY_INVOKE */
  settlementComposition?: null | string;
  /** 2206 完成后保留的 invoke sessionId（2207 用）。 */
  workInjurySessionId?: string;
  /** 工伤 invoke 首步（WORK_INJURY_INVOKE 时）。 */
  workInjuryInvokeStep?: {
    sessionId?: string;
    stepToken?: string;
    infno?: string;
    fixmedinsCode?: string;
    operatorNo?: string;
    input?: Record<string, unknown>;
    nextAction?: string;
    errMsg?: string;
  };
  ybAcctPay?: null | number | string;
  /** 家庭共济（2206 acct_mulaid_pay；含于 ybAcctPay，仅展示） */
  ybAcctMulaidPay?: null | number | string;
  /** 实际支付起付线（2206 act_pay_dedc；仅展示） */
  ybActPayDedc?: null | number | string;
  ybChrgBchno?: null | string;
  /** 公务员标志（2206 cvlserv_flag；仅展示） */
  ybCvlservFlag?: null | string;
  /** 公务员医疗补助资金支出（2206 cvlserv_pay；仅展示） */
  ybCvlservPay?: null | number | string;
  /** 基金支付总额（fund_pay_sumamt；≠ 单列统筹） */
  ybFundPaySumamt?: null | number | string;
  /** 基本医保统筹（hifp_pay） */
  ybHifpPay?: null | number | string;
  ybMedfeeSumamt?: null | number | string;
  /** 其他支出（oth_pay，含电子钱包等） */
  ybOthPay?: null | number | string;
  ybPsnCashPay?: null | number | string;
}

/** 列表折后价试算请求 */
export interface PreviewOutpatientChargeDiscountsRequest {
  encounterId: string;
  chargeItemIds: string[];
  /** 款员所选折扣率 (0,1]；空=不传手动折扣 */
  cashierDiscountRate?: null | number;
}

/** 列表折后价试算行 */
export interface PreviewOutpatientChargeDiscountLineDto {
  chargeItemId: string;
  originalAmount?: null | number | string;
  discountedAmount?: null | number | string;
  discountGap?: null | number | string;
  discountRate?: null | number | string;
  discounted?: boolean;
}

/** 列表折后价试算响应 */
export interface PreviewOutpatientChargeDiscountsResponse {
  encounterId?: string;
  discountAmountTotal?: null | number | string;
  lines?: PreviewOutpatientChargeDiscountLineDto[];
}

/** IF-OPC-07 支付明细分录 */
export interface PaymentDetailSubmitDto {
  amount: number | string;
  payChannelCode: string;
}

/** 银联被扫 Win 回传（settle 顶层 clientScanResult） */
export interface ClientScanResultDto {
  merTradeNo: string;
  tradeNo: string;
  payType: string;
  respCode: string;
  respMsg: string;
  txnAmtFen?: number;
  paidAmtFen?: number;
}

/** 银联补收准备请求（平台已 2207、支付取消、收费项仍待收） */
export interface PrepareUnionPayMakeupCollectRequest {
  encounterId: string;
  chargeItemIds: string[];
}

/** IF-OPC-07 正式结算请求 */
export interface ConfirmOutpatientChargeSettlementRequest {
  billDate: string;
  /** 款员所选折扣率；须与预结算一致；仅已配置有效折扣时可传 */
  cashierDiscountRate?: null | number;
  chargeItemIds: string[];
  encounterId: string;
  outcomeEnum: string;
  paymentDetails: PaymentDetailSubmitDto[];
  paymentDraftId: string;
  clientScanResult?: ClientScanResultDto;
  ybChrgBchno?: null | string;
  ybChrgBchnos?: string[];
}

/** 银联扫码前医保 2207 确认请求 */
export interface ConfirmOutpatientInsuranceBeforeScanRequest {
  paymentDraftId: string;
  encounterId?: string;
  ybChrgBchno?: null | string;
  ybChrgBchnos?: string[];
}

/** 银联扫码前医保 2207 确认响应 */
export interface ConfirmOutpatientInsuranceBeforeScanResponse {
  confirmedBatchCount?: number;
  reusedExisting?: boolean;
  /** 全部 2207 后的权威个人现金；扫码应付以此为准 */
  patientCashAmount?: null | number | string;
}

/** IF-OPC-07 正式结算响应 */
export interface ConfirmOutpatientChargeSettlementResponse {
  invoiceId: string;
  invoiceStatus?: string;
  paymentId: string;
  successMessageCode?: null | string;
  ybSetlId?: null | string;
}

/** 收费小票收费项目行 */
export interface ChargeReceiptChargeLineDto {
  itemName?: string;
  quantityText?: string;
  amountText?: string;
  chargeCategoryText?: string;
}

/** 门诊患者费用清单明细行 */
export interface ChargeFeeListRowDto {
  encounterBusNo?: string;
  patientName?: string;
  departmentName?: string;
  orderingDoctorName?: string;
  itemName?: string;
  unitPriceText?: string;
  quantityText?: string;
  amountText?: string;
  insuranceLevelText?: string;
  visitTypeText?: string;
  itemTypeText?: string;
  timeText?: string;
}

/** 收费小票 slip（printChargeReceipt 返回；亦供发票/费用清单） */
export interface ChargeReceiptSlipDto {
  encounterId?: null | number | string;
  patientName?: string;
  hospitalName?: string;
  genderEnumText?: string;
  ageText?: string;
  feeNatureText?: string;
  chargeItemLines?: ChargeReceiptChargeLineDto[];
  totalAmountText?: string;
  poolingPayText?: string;
  fundPayText?: string;
  accountPayText?: string;
  deductibleLineText?: string;
  /** 公务员待遇（是/否） */
  cvlservFlagText?: string;
  /** 公务员医疗补助金额 */
  cvlservPayText?: string;
  /** 其他支出（含电子钱包等） */
  othPayText?: string;
  cashPayText?: string;
  wechatPayText?: string;
  alipayPayText?: string;
  accountBalanceText?: string; // 医保个账余额（扣款后展示文案）
  cashierName?: string;
  chargeTimeText?: string;
  encounterBusNo?: string;
  /** 挂号时间（yyyy-MM-dd HH:mm:ss） */
  registerTimeText?: string;
  departmentName?: string;
  doctorName?: string;
  /** 开方科室 */
  orderingDepartmentName?: string;
  /** 开方医生 */
  orderingDoctorName?: string;
  prescriptionNoText?: string;
  paymentNoText?: string;
  insuranceNoText?: string;
  chargeDateText?: string;
  westernMedicineFeeText?: string;
  examFeeText?: string;
  chinesePatentFeeText?: string;
  surgeryFeeText?: string;
  herbalFeeText?: string;
  oxygenFeeText?: string;
  labFeeText?: string;
  bloodFeeText?: string;
  xrayFeeText?: string;
  disposeFeeText?: string;
  electroFeeText?: string;
  injectionFeeText?: string;
  therapyFeeText?: string;
  materialFeeText?: string;
  originalAccountBalanceText?: string;
  insurancePayText?: string;
  invoiceCashPayText?: string;
  employeeCriticalFeeText?: string;
  residentCriticalFeeText?: string;
  totalAmountChineseText?: string;
  /** 费用清单标题（普通/慢病） */
  documentTitle?: string;
  /** 机构行（机构：xxx） */
  organizationLabel?: string;
  /** 费用清单明细 */
  feeListRows?: ChargeFeeListRowDto[];
  /** 费用清单合计 */
  feeListTotalAmountText?: string;
}

export interface PrintChargeReceiptParams {
  encounterId?: null | number | string;
  paymentId?: null | number | string;
}

export interface PrintChargeReceiptResult {
  slip?: ChargeReceiptSlipDto;
}
