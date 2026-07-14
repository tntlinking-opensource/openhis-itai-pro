/** 核对弹窗内「标签 / 值」一行（挂号确认、历史收费/退号等共用布局） */
export interface ConfirmDetailSectionRow {
  label: string;
  value: string;
  /** 值列长串换行（证件号、电话等） */
  breakAll?: boolean;
  /** 值列强调字重 */
  medium?: boolean;
}

/** 核对弹窗内一个分组（标题 + 若干行） */
export interface ConfirmDetailSection {
  title: string;
  rows: ConfirmDetailSectionRow[];
}

/** 挂号核对弹窗：展示用汇总（由左侧提交预结算后写入） */
export interface RegisterConfirmDetail {
  contractLabel: string;
  departmentLabel: string;
  doctorLabel: string;
  medicalRecordNo: string;
  patientAge: string;
  patientBirthday: string;
  patientGender: string;
  patientIdCard: string;
  patientName: string;
  patientPhone: string;
  serviceTypeLabel: string;
  totalAmount: number;
}

/** 患者下拉选中后写入「患者信息」表单的字段集合 */
export interface RegisterPatientRecord {
  /** 对应表单「年龄」字段（medicalInsuranceNo） */
  patientAge?: string;
  patientBirthday: string;
  patientGender: string;
  patientIdCard: string;
  patientName: string;
  patientPhone: string;
}

/** 右侧就诊记录表格对外暴露能力（与 History 组件 defineExpose 对齐） */
export interface RegisterHistoryExpose {
  refresh: () => Promise<void>;
  search: () => Promise<void>;
}

/** 右侧容器对外暴露能力（统计 + 当前 Tab） */
export type RegisterRightPanelExpose = RegisterHistoryExpose;

/** 当日号源点选后带入左侧挂号的上下文 */
export interface RegisterSlotSelectionPayload {
  healthcareServiceId: number | string;
  /** 号源类型展示名；带入时并入下拉，避免只显示主键 ID */
  healthcareServiceName?: string;
  organizationId: number | string;
  practitionerId?: null | number | string;
  slotEnd?: string;
  slotId: number | string;
  slotStart?: string;
}

/** 下方「待遇确认」只读展示区（费用性质切换后异步灌入） */
export interface RegisterBenefitDisplayPayload {
  kind?: '' | 'INSURANCE' | 'STUDENT' | 'WORK_INJURY';
  areaHint?: string;
  balc?: number | string;
  cardNo?: string;
  certno?: string;
  /** 02=身份证凭证，03=社保卡 */
  mediumType?: string;
  college?: string;
  eligibilityMessage?: string;
  eligible?: boolean;
  insuplcAdmdvs?: string;
  insutype?: string;
  mdtrtareaAdmdvs?: string;
  psnInsuStas?: string;
  psnName?: string;
  psnNo?: string;
  psnType?: string;
  specialDisease?: string;
  studentName?: string;
  studentNo?: string;
  treatmentBlocked?: boolean;
}

/** 下方 Tab 切换险种时重查 2001 的上下文 */
export interface RegisterBenefitPreviewContext {
  contractId?: string;
  ybCardInfo?: string;
  ybCardSn?: string;
  ybCertno?: string;
  ybMediumType?: string;
  ybPsnName?: string;
}

/** 父组件通过 ref 调用的挂号表单区能力 */
export interface RegisterFormAreaExpose {
  /** 预约快照预填并锁定三要素 */
  applyBookingSnapshot: (snapshot: {
    healthcareServiceId: number | string;
    organizationId: number | string;
    practitionerId?: null | number | string;
  }) => Promise<void>;
  /** 号源日历点选预填三要素（不锁定，可微调） */
  applySlotSelection: (payload: RegisterSlotSelectionPayload) => Promise<void>;
  /** 把选中患者写入患者信息表单 */
  applyPatientRecord: (patient: RegisterPatientRecord) => void;
  /** 解除预约锁定并保留已填值 */
  clearBookingLock: () => void;
  /** 清空患者信息表单各字段 */
  clearPatientFields: () => void;
  /** 焦点落到表单区内第一个可聚焦控件，便于选完患者后继续键盘录入 */
  focusFirstField: () => Promise<void>;
  /** 按科室 id 解析下拉展示名，供确认框等使用 */
  getDepartmentLabel: (departmentId: unknown) => string | undefined;
  /** 按医生 id 解析下拉展示名 */
  getDoctorLabel: (doctorId: unknown) => string | undefined;
  /** 按费用性质 id 解析下拉展示名 */
  getContractLabel: (contractId: unknown) => string | undefined;
  /** 按费用性质 id 解析合同类别编码 */
  getContractCategory: (contractId: unknown) => string | undefined;
  /** 取首个医保类费用性质，供读卡后引导 1101 */
  findBootstrapInsuranceContractId: () => string | undefined;
  /** 按发卡地推荐医保费用性质（优先于 findBootstrapInsuranceContractId） */
  findBootstrapInsuranceContractIdByInsuplc: (insuplcAdmdvs?: string) => string | undefined;
  /** 费用性质 → chs-contract bus_no（9001 签到） */
  getContractBusNo: (contractId: unknown) => string | undefined;
  /** 切换费用性质并通知父组件 */
  applyContractId: (contractId: string) => Promise<void>;
  /** 写入参保地匹配结果 */
  applyContractMatch: (
    match: import('#/api/outpatient-registration').RegisterContractMatchDto | null | undefined,
  ) => void;
  /** 一键切换到推荐费用性质 */
  applyRecommendedContract: () => Promise<void>;
  /** 待遇确认后刷新下方待遇展示区 */
  applyBenefitPreview: (
    payload: RegisterBenefitDisplayPayload,
    ybPreview?: import('#/api/outpatient-registration').YbRegisterBenefitPreviewDto,
    refreshContext?: RegisterBenefitPreviewContext,
  ) => void;
  /** 清空下方待遇确认展示块 */
  clearBenefitPreview: () => void;
  /** 按号源类型 id 解析下拉展示名（挂号类型） */
  getServiceTypeLabel: (serviceTypeId: unknown) => string | undefined;
  /** 汇总患者区、挂号区字段及挂号费、号源类型 id，供提交 */
  getSubmitValues: () => Promise<Record<string, unknown>>;
  /** 清空挂号 + 患者信息，并清空下方待遇确认展示块 */
  resetForClear: () => void;
  /** 提交成功后重置表单并刷新医生下拉；不预填挂号区首条主数据 */
  resetForSubmitSuccess: () => Promise<void>;
  /** 校验患者信息、挂号信息两块表单 */
  validateForSubmit: () => Promise<boolean>;
}
