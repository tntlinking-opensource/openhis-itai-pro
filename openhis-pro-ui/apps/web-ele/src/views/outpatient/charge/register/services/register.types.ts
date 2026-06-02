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
}

/** 父组件通过 ref 调用的挂号表单区能力 */
export interface RegisterFormAreaExpose {
  /** 把选中患者写入患者信息表单 */
  applyPatientRecord: (patient: RegisterPatientRecord) => void;
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
  /** 按号源类型 id 解析下拉展示名（挂号类型） */
  getServiceTypeLabel: (serviceTypeId: unknown) => string | undefined;
  /** 汇总患者区、挂号区字段及挂号费、号源类型 id，供提交 */
  getSubmitValues: () => Promise<Record<string, unknown>>;
  /** 清空挂号 + 患者信息，并清空下方就诊信息展示块 */
  resetForClear: () => void;
  /** 提交成功后重置表单并刷新医生下拉；不预填挂号区首条主数据 */
  resetForSubmitSuccess: () => Promise<void>;
  /** 校验患者信息、挂号信息两块表单 */
  validateForSubmit: () => Promise<boolean>;
}
