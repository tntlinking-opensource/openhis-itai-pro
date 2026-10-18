/**
 * 门诊挂号：`/outpatientRegistration` 请求与类型（camelCase，与后端一致）。
 */

import { publicRequestClient } from '#/api/request';

/** 通用下拉选项 */
export interface OptionDto {
  value: string;
  label: string;
  disabled?: boolean;
}

/** 挂号页面初始化请求参数 */
export interface LoadRegisterFormInitParams {
  organizationId?: null | number | string;
  practitionerId?: null | number | string;
  registrationDate?: string;
  keyword?: string;
}

/** 挂号页面初始化返回：科室、医生、费用性质、号源类型、性别字典等 */
export interface ContractInsuranceProfileDto {
  busNo?: string;
  categoryCode?: string;
  contractId?: string;
  treatmentAreaCode?: string;
  ybFlag?: boolean;
}

export interface RegisterContractMatchDto {
  blockRegister?: boolean;
  hintMessage?: string;
  matchStatus?: 'CROSS_PROVINCE' | 'IN_PROVINCE' | 'MATCH' | 'UNKNOWN' | string;
  patientInsuplcAdmdvs?: string;
  recommendedCategoryCode?: string;
  recommendedContractId?: string;
  recommendedContractName?: string;
  selectedContractId?: string;
  selectedTreatmentAreaCode?: string;
}

export interface LoadRegisterFormInitResult {
  contractOptions: OptionDto[];
  /** 费用性质主键 → ContractCategory 编码 */
  contractCategoryById?: Record<string, string>;
  contractInsuranceProfileById?: Record<string, ContractInsuranceProfileDto>;
  departmentOptions: OptionDto[];
  /** 患者性别单选项（如 MALE / FEMALE / UNKNOWN，与主数据字典一致） */
  genderOptions: OptionDto[];
  practitionerOptions: OptionDto[];
  serviceTypeOptions: OptionDto[];
  /** 号源类型主键 → 挂号费销售价（元） */
  serviceTypeFeeById?: Record<string, number | string>;
  paymentChannelOptions?: OptionDto[];
  workInjuryCapability?: {
    enabled?: boolean;
    winRequired?: boolean;
    fixmedinsCode?: string;
    contractBusNo?: string;
  };
}

/** 患者检索分页请求参数 */
export interface PagePatientsForSelectParams {
  keyword?: string;
  pageNo: number;
  pageSize: number;
}

/** 患者检索结果行（用于下拉选择） */
export interface PatientItemDto {
  /** 周岁 */
  age?: null | number;
  birthDate?: string;
  genderCode?: string;
  genderText?: string;
  idCardNoMasked?: string;
  name: string;
  patientBusNo: string;
  patientId: string;
  phoneMasked?: string;
}

/** 患者检索分页返回 */
export interface PagePatientsForSelectResult {
  records: PatientItemDto[];
  total: number | string;
}

/** 医生下拉查询请求参数 */
export interface ListPractitionersParams {
  keyword?: string;
  organizationId?: null | number | string;
}

/** 医生下拉查询返回 */
export interface ListPractitionersResult {
  practitionerOptions: OptionDto[];
}

/** 挂号预结算请求参数（患者信息 + 挂号要素） */
export interface PreRegisterSettlementParams {
  appointmentId?: null | number | string;
  birthDate: string;
  contractId: number | string;
  gender: string;
  idCardNo: string;
  name: string;
  organizationId: number | string;
  patientId?: null | number | string;
  phone: string;
  /**
   * 地址全文：身份证读卡户籍地址等；新建写入主档，复用时非空且不同则回写（BR-REG-019）。
   */
  address?: string;
  practitionerId: number | string;
  serviceTypeId: number | string;
  slotId?: null | number | string;
  /**
   * 挂号费是否走医保预结算。
   * 省/市医保费用性质下由前端「医保支付挂号费」勾选控制；true 才走 2201～2206。
   * false / 缺省：全程不调 2201～2206，不写 map(12)。
   */
  ybRegisterFee?: boolean;
  /** 是否使用医保个人账户（acct_used_flag）；勾选医保支付挂号费时前端固定传 true */
  ybUseAcctPay?: boolean;
  /** 医保读卡字段 */
  psnNo?: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  ybMediumType?: string;
  ybCardInfo?: string;
  /**
   * 医疗类别。挂号费医保须传 {@code 12}（门诊挂号）；勿再传 11。
   * 2201 仅在预结算 resolveMdtrtId 未命中 map 时触发。
   */
  medType?: string;
}

/** 1101 参保信息预览行 */
export interface YbInsuInfoPreviewDto {
  balc?: number | string;
  cvlservFlag?: string;
  empName?: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  psnInsuDate?: string;
  psnInsuStas?: string;
  psnType?: string;
}

/** 2001 待遇检查预览行 */
export interface YbTreatmentInfoPreviewDto {
  begndate?: string;
  enddate?: string;
  fundPayType?: string;
  psnNo?: string;
  trtChkRslt?: string;
  trtChkType?: string;
  trtEnjymntFlag?: string;
}

/** 5301 慢特病备案预览行 */
export interface YbOpspFilingPreviewDto {
  begndate?: string;
  enddate?: string;
  opspDiseCode?: string;
  opspDiseName?: string;
}

/** 5206 累计信息预览行 */
export interface YbCumulativeInfoPreviewDto {
  cum?: number | string;
  cumTypeCode?: string;
  cumYm?: string;
  insutype?: string;
  year?: string;
}

/** 参保地 vs 就医地对比提示 */
export interface YbRegisterBenefitAreaHintDto {
  areaKind?: 'CROSS_PROVINCE' | 'IN_PROVINCE' | 'LOCAL' | 'UNKNOWN' | string;
  hintMessage?: string;
  insuplcAdmdvs?: string;
  mdtrtareaAdmdvs?: string;
}

/** 医保挂号待遇预览 */
export interface YbRegisterBenefitPreviewDto {
  areaHint?: YbRegisterBenefitAreaHintDto;
  balc?: number | string;
  brdy?: string;
  certno?: string;
  cumulativeInfoList?: YbCumulativeInfoPreviewDto[];
  gend?: string;
  insuinfoList?: YbInsuInfoPreviewDto[];
  insuplcAdmdvs?: string;
  insutype?: string;
  /** 慢特病备案（5301 主查） */
  opspFilingList?: YbOpspFilingPreviewDto[];
  psnName?: string;
  psnNo?: string;
  /**
   * 1101 异地失败切省通道后的实际合同 bus_no；未切换时为空。
   * 后续 2001/5301 应使用对应费用性质，避免仍走市通道。
   */
  resolvedContractBusNo?: string;
  /** 1101 异地失败切省通道后的实际费用性质 id */
  resolvedContractId?: string;
  /** 2001 待遇检查结果 */
  treatmentBlocked?: boolean;
  /** 省内失败时前端弹警告；跨省静默为 false */
  treatmentCheckAlert?: boolean;
  /** 2001 提示：跨省「暂不可查」或省内失败说明 */
  treatmentCheckHint?: string;
  treatmentInfoList?: YbTreatmentInfoPreviewDto[];
}

/** 学籍记录预览 */
export interface StudentRecordPreviewDto {
  college?: string;
  grade?: string;
  leaveTimeText?: string;
  major?: string;
  name?: string;
  physicalExamResult?: number;
  schoolStatus?: number;
  studentId?: number | string;
  studentNo?: string;
}

/** 学生公费资格预览 */
export interface StudentEligibilityPreviewDto {
  eligible?: boolean;
  patientId?: number | string;
  reasonCode?: string;
  reasonMessage?: string;
  studentId?: number | string;
}

/** 学生公费待遇预览 */
export interface StudentRegisterBenefitPreviewDto {
  idCard?: string;
  patientId?: number | string;
  patientName?: string;
  phone?: string;
  studentRecords?: StudentRecordPreviewDto[];
}

export interface LoadRegisterBenefitPreviewParams {
  contractId: number | string;
  insuplcAdmdvs?: string;
  insutype?: string;
  medType?: string;
  patientId?: null | number | string;
  psnNo?: string;
  runCumulativeQuery?: boolean;
  runOpspFilingQuery?: boolean;
  runTreatmentCheck?: boolean;
  studentId?: null | number | string;
  ybCardInfo?: string;
  ybCardSn?: string;
  ybCertno?: string;
  ybMediumType?: string;
  ybPsnName?: string;
}

export interface LoadRegisterBenefitPreviewResult {
  benefitKind?: 'INSURANCE' | 'NONE' | 'STUDENT' | 'WORK_INJURY' | string;
  hintMessage?: string;
  contractMatch?: RegisterContractMatchDto;
  studentEligibility?: StudentEligibilityPreviewDto;
  studentPreview?: StudentRegisterBenefitPreviewDto;
  ybPreview?: YbRegisterBenefitPreviewDto;
}

/** 预结算费用明细行 */
export interface ChargeItemPreviewDto {
  amount: number | string;
  chargeItemCode: string;
  chargeItemName: string;
  quantity: number | string;
  unitPrice: number | string;
}

/** 预结算返回：支付草稿、总额、费用明细（及可选就诊标识） */
export interface PreRegisterSettlementResult {
  appointmentId?: null | number | string;
  chargeItems: ChargeItemPreviewDto[];
  encounterId?: number | string;
  paymentDraftId: number | string;
  slotId?: null | number | string;
  totalAmount: number | string;
  insurancePreSettled?: boolean;
  /** 医保医疗费总额（号别原价口径；预结后 totalAmount 可能已改成现金应付） */
  ybMedfeeSumamt?: number | string;
  ybFundPaySumamt?: number | string;
  ybAcctPay?: number | string;
  ybPsnCashPay?: number | string;
  /** 实际支付起付线（2206；仅展示） */
  ybActPayDedc?: number | string;
  /** 公务员标志（2206；仅展示） */
  ybCvlservFlag?: string;
  /** 公务员医疗补助资金支出（2206；仅展示） */
  ybCvlservPay?: number | string;
}

/** 结算时提交的单渠道支付明细 */
export interface PaymentDetailDto {
  payAmount: number | string;
  payChannelCode: string;
  transactionNo?: string;
}

import type { ClientScanResultDto } from '#/api/payment/payment.types';

/** 挂号费正式结算请求参数 */
export interface PayRegisterFeeParams {
  appointmentId?: null | number | string;
  paymentDetails: PaymentDetailDto[];
  paymentDraftId: number | string;
  clientScanResult?: ClientScanResultDto;
}

/** 挂号费正式结算返回 */
export interface PayRegisterFeeResult {
  encounterId?: null | number | string;
  invoiceId: number | string;
  invoiceStatus?: string;
  paymentId: number | string;
  status: string;
}

/** 门诊挂号记录分页查询请求参数 */
export interface PageClinicRegistersParams {
  dateFrom: string;
  dateTo: string;
  keyword?: string;
  pageNo: number;
  pageSize: number;
  /** 按患者查历史挂号 */
  patientId?: null | number | string;
}

/**
 * 门诊挂号记录分页列表行（与 pageClinicRegisters 返回实体一致）。
 */
export interface ClinicRegisterItemDto {
  amount: number;
  /** 是否允许窗口退号；缺省时不限制退号按钮（由后端逐步收口时可下发 false） */
  canCancel?: boolean;
  contactPhone: string;
  doctorName: string;
  encounterBusNo: string;
  encounterId: string;
  /** 挂号科室主键（改科室回显） */
  organizationId?: string;
  organizationName: string;
  patientGenderCode: string;
  patientGenderText: string;
  patientName: string;
  /** 患者年龄展示文案（由出生日期推算，如 45岁；无出生日期时为空） */
  patientAgeText?: string;
  /** 待正式结算时的支付草稿 id；无则不可点「收费」 */
  paymentDraftId?: string;
  /** 已成功缴费后的挂号费支付流水 id（按挂号费 chargeItem 锚定；退号银联退货/补打挂号小票） */
  paymentId?: string;
  /** 收费人姓名（已缴费时由支付流水 enterer 回显） */
  cashierName?: string;
  printCount?: null | number;
  serviceTypeId: string;
  serviceTypeName: string;
  startTime: string;
  /** 就诊状态枚举编码，如 PLANNED */
  statusEnum: string;
  statusText: string;
  /** 是否关联预约（供退号提示） */
  hasLinkedAppointment?: boolean;
  contractName?: string;
  insuplcAdmdvs?: string;
  /** 1901 字典 label；未同步字典时为空 */
  insuplcAdmdvsName?: string;
  insurancePlatformRegistered?: boolean;
  insurancePreSettled?: boolean;
  ybFundPaySumamt?: number;
  ybAcctPay?: number;
  ybPsnCashPay?: number;
}

/** 门诊挂号记录分页返回 */
export interface PageClinicRegistersResult {
  records: ClinicRegisterItemDto[];
  total: number | string;
}

/** 退号资格校验请求参数 */
export interface ValidateCancelableParams {
  encounterId: number | string;
  reason?: string;
}

/** 退号资格校验返回 */
export interface ValidateCancelableResult {
  cancelable: boolean;
  reasonCode?: string;
  reasonText?: string;
}

/** 退号并退费请求参数 */
export interface CancelWithRefundParams {
  cancelReason: string;
  encounterId: number | string;
  refundIntent?: string;
  refundRequestId: string;
  clientRefundResult?: import('#/api/payment/payment.types').ClientRefundResultDto;
}

/** 退号并退费返回 */
export interface CancelWithRefundResult {
  encounterStatus: string;
  redInvoiceId?: null | number | string;
  refundStatus: string;
  /** 关联预约释放提示（booking 模块退号后回填） */
  appointmentReleaseMessage?: string;
}

/** IF-REG-12：加载有效预约请求 */
export interface LoadAppointmentForRegisterParams {
  appointmentBusNo?: string;
  patientId?: null | number | string;
  registrationDate?: string;
}

/** IF-REG-12：有效预约快照 */
export interface OutpatientBookingSnapshotDto {
  appointmentBusNo: string;
  appointmentId: number | string;
  healthcareServiceId: number | string;
  organizationId: number | string;
  organizationName?: string;
  practitionerId?: null | number | string;
  practitionerName?: string;
  serviceTypeName?: string;
  slotEnd?: string;
  slotId?: null | number | string;
  slotStart?: string;
  statusEnum?: string;
}

/** IF-REG-14：可约时段摘要行 */
export interface BookableSlotHintDto {
  remainingCount?: null | number;
  slotEnd?: string;
  slotId?: null | number | string;
  slotStart?: string;
}

/** IF-REG-14：可约时段摘要请求 */
export interface ListBookableSlotHintsParams {
  healthcareServiceId?: null | number | string;
  organizationId: number | string;
  practitionerId?: null | number | string;
  registrationDate?: string;
}

/** IF-REG-14：可约时段摘要响应 */
export interface ListBookableSlotHintsResult {
  hints: BookableSlotHintDto[];
}

/** 挂号历史表格行：与 `ClinicRegisterItemDto` 同源 */
export type RegisterHistoryTableRow = ClinicRegisterItemDto;

/** 挂号小票收费项目行 */
export interface RegistrationReceiptChargeLineDto {
  amountText?: string;
  chargeCategoryText?: string;
  itemName?: string;
}

/** 挂号小票 slip（printRegisterTicket 返回） */
export interface RegistrationSlipDto {
  /** 医保个账余额（扣款后展示文案） */
  accountBalanceText?: string;
  accountPayText?: string;
  ageText?: string;
  alipayPayText?: string;
  amount?: number;
  cashierName?: string;
  cashPayText?: string;
  chargeItemLines?: RegistrationReceiptChargeLineDto[];
  chargeTimeText?: string;
  deductibleLineText?: string;
  /** 公务员待遇（是/否） */
  cvlservFlagText?: string;
  /** 公务员医疗补助金额 */
  cvlservPayText?: string;
  doctorName?: string;
  encounterId?: null | number | string;
  feeNatureText?: string;
  fundPayText?: string;
  genderEnumText?: string;
  hospitalName?: string;
  organizationName?: string;
  patientName?: string;
  poolingPayText?: string;
  printCount?: null | number;
  registerDepartmentName?: string;
  serviceTypeName?: string;
  slipNo?: string;
  statusText?: string;
  totalAmountText?: string;
  wechatPayText?: string;
}

export interface PrintRegisterTicketParams {
  encounterId?: null | number | string;
  paymentId?: null | number | string;
  slipNo?: string;
}

export interface PrintRegisterTicketResult {
  printCount?: null | number;
  slip?: RegistrationSlipDto;
}

/** 挂号窗口打开时加载下拉主数据：科室、医生、费用性质、号源类型等 */
export async function httpLoadRegisterFormInit(
  params: LoadRegisterFormInitParams = {},
): Promise<LoadRegisterFormInitResult> {
  return publicRequestClient.post<LoadRegisterFormInitResult>(
    '/outpatientRegistration/loadRegisterFormInit',
    params,
  );
}

/** 患者检索分页（姓名、病历号、证件号等关键字模糊查） */
export async function httpPagePatientsForSelect(
  params: PagePatientsForSelectParams,
): Promise<PagePatientsForSelectResult> {
  return publicRequestClient.post<PagePatientsForSelectResult>(
    '/outpatientRegistration/pagePatientsForSelect',
    params,
  );
}

/** 可挂号科室列表请求（关键字可选） */
export interface ListDepartmentsParams {
  keyword?: string;
}

/** 可挂号科室列表响应 */
export interface ListDepartmentsResult {
  departmentOptions: OptionDto[];
}

/** 加载可挂号科室下拉（供护士站开立科室筛选等复用） */
export async function httpListDepartments(
  params: ListDepartmentsParams = {},
): Promise<ListDepartmentsResult> {
  return publicRequestClient.post<ListDepartmentsResult>(
    '/outpatientRegistration/listDepartments',
    params,
  );
}

/** 按科室（可选）与关键字加载可挂号医生下拉 */
export async function httpListPractitioners(
  params: ListPractitionersParams = {},
): Promise<ListPractitionersResult> {
  return publicRequestClient.post<ListPractitionersResult>(
    '/outpatientRegistration/listPractitioners',
    params,
  );
}

/** 加载挂号待遇预览（1101 / 学籍） */
export async function httpLoadRegisterBenefitPreview(
  body: LoadRegisterBenefitPreviewParams,
): Promise<LoadRegisterBenefitPreviewResult> {
  return publicRequestClient.post<LoadRegisterBenefitPreviewResult>(
    '/outpatientRegistration/loadRegisterBenefitPreview',
    body,
  );
}

/** 执行挂号预结算 */
export async function httpPreRegisterSettlement(
  body: PreRegisterSettlementParams,
): Promise<PreRegisterSettlementResult> {
  return publicRequestClient.post<PreRegisterSettlementResult>(
    '/outpatientRegistration/preRegisterSettlement',
    body,
  );
}

/** IF-REG-13：预约转挂号预结算 */
export async function httpPreRegisterFromAppointment(
  body: PreRegisterSettlementParams,
): Promise<PreRegisterSettlementResult> {
  return publicRequestClient.post<PreRegisterSettlementResult>(
    '/outpatientRegistration/preRegisterFromAppointment',
    body,
  );
}

/** IF-REG-12：挂号台加载有效预约 */
export async function httpLoadAppointmentForRegister(
  body: LoadAppointmentForRegisterParams,
): Promise<OutpatientBookingSnapshotDto> {
  return publicRequestClient.post<OutpatientBookingSnapshotDto>(
    '/outpatientRegistration/loadAppointmentForRegister',
    body,
  );
}

/** IF-REG-14：可约时段摘要 */
export async function httpListBookableSlotHints(
  body: ListBookableSlotHintsParams,
): Promise<ListBookableSlotHintsResult> {
  return publicRequestClient.post<ListBookableSlotHintsResult>(
    '/outpatientRegistration/listBookableSlotHints',
    body,
  );
}

/** 按草稿与支付方式收取挂号费（正式结算） */
export async function httpPayRegisterFee(
  body: PayRegisterFeeParams,
): Promise<PayRegisterFeeResult> {
  return publicRequestClient.post<PayRegisterFeeResult>(
    '/outpatientRegistration/payRegisterFee',
    body,
  );
}

/** 挂号记录列表分页（按日期区间与关键字筛） */
export async function httpPageClinicRegisters(
  params: PageClinicRegistersParams,
): Promise<PageClinicRegistersResult> {
  return publicRequestClient.post<PageClinicRegistersResult>(
    '/outpatientRegistration/pageClinicRegisters',
    params,
  );
}

/** 当日挂号统计（不含 PLANNED / ENTERED_IN_ERROR；已退仅 CANCELLED） */
export interface GetTodayRegisterSummaryParams {
  date?: string;
}

export interface GetTodayRegisterSummaryResult {
  date?: string;
  /** 当日挂号（不含待收费、不含未收费取消） */
  totalCount?: number;
  /** 已退号数（仅退号 CANCELLED） */
  cancelledCount?: number;
  /** 有效 = total − cancelled */
  activeCount?: number;
}

export async function httpGetTodayRegisterSummary(
  params: GetTodayRegisterSummaryParams = {},
): Promise<GetTodayRegisterSummaryResult> {
  return publicRequestClient.post<GetTodayRegisterSummaryResult>(
    '/outpatientRegistration/getTodayRegisterSummary',
    params,
  );
}

/** 退号前校验当前就诊是否允许退号 */
export async function httpValidateCancelable(
  body: ValidateCancelableParams,
): Promise<ValidateCancelableResult> {
  return publicRequestClient.post<ValidateCancelableResult>(
    '/outpatientRegistration/validateCancelable',
    body,
  );
}

/** 待挂号（PLANNED）作废：作废草稿；有平台则按需 2205/2202(med_type=12) */
export interface CancelPlannedRegistrationParams {
  cancelReason?: string;
  encounterId: number | string;
}

export interface CancelPlannedRegistrationResult {
  encounterStatus: string;
  insurancePlatformRevoked?: boolean;
}

/** 刷新挂号费医保预结算（重新展码后 2205→2206） */
export interface RefreshRegisterFeeInsurancePreSettleParams {
  paymentDraftId: number | string;
  ybUseAcctPay?: boolean;
  ybMediumType?: string;
  ybCardInfo?: string;
  psnNo?: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  /** 挂号费刷新预结算默认 {@code 12} */
  medType?: string;
}

export interface RefreshRegisterFeeInsurancePreSettleResult {
  paymentDraftId?: number | string;
  totalAmount?: number | string;
  insurancePreSettled?: boolean;
  ybChrgBchno?: string;
  ybMedfeeSumamt?: number | string;
  ybFundPaySumamt?: number | string;
  ybAcctPay?: number | string;
  ybPsnCashPay?: number | string;
  /** 实际支付起付线（2206；仅展示） */
  ybActPayDedc?: number | string;
  /** 公务员标志（2206；仅展示） */
  ybCvlservFlag?: string;
  /** 公务员医疗补助资金支出（2206；仅展示） */
  ybCvlservPay?: number | string;
}

/** 挂号记录修改科室 */
export interface ChangeRegistrationDepartmentParams {
  departmentId: number | string;
  encounterId: number | string;
}

export interface ChangeRegistrationDepartmentResult {
  insurancePlatformChanged?: boolean;
  success?: boolean;
}

export async function httpChangeRegistrationDepartment(
  body: ChangeRegistrationDepartmentParams,
): Promise<ChangeRegistrationDepartmentResult> {
  return publicRequestClient.post<ChangeRegistrationDepartmentResult>(
    '/outpatientRegistration/changeRegistrationDepartment',
    body,
  );
}

export async function httpCancelPlannedRegistration(
  body: CancelPlannedRegistrationParams,
): Promise<CancelPlannedRegistrationResult> {
  return publicRequestClient.post<CancelPlannedRegistrationResult>(
    '/outpatientRegistration/cancelPlannedRegistration',
    body,
  );
}

/** 刷新挂号费医保预结算（电子凭证过期后重新展码） */
export async function httpRefreshRegisterFeeInsurancePreSettle(
  body: RefreshRegisterFeeInsurancePreSettleParams,
): Promise<RefreshRegisterFeeInsurancePreSettleResult> {
  return publicRequestClient.post<RefreshRegisterFeeInsurancePreSettleResult>(
    '/outpatientRegistration/refreshRegisterFeeInsurancePreSettle',
    body,
  );
}

/** 提交退号并按策略联动退费（含退款幂等键） */
export async function httpCancelWithRefund(
  body: CancelWithRefundParams,
): Promise<CancelWithRefundResult> {
  return publicRequestClient.post<CancelWithRefundResult>(
    '/outpatientRegistration/cancelWithRefund',
    body,
  );
}

/** 获取挂号小票打印数据（含支付明细回补字段） */
export async function httpPrintRegisterTicket(
  body: PrintRegisterTicketParams,
): Promise<PrintRegisterTicketResult> {
  return publicRequestClient.post<PrintRegisterTicketResult>(
    '/outpatientRegistration/printRegisterTicket',
    body,
  );
}
