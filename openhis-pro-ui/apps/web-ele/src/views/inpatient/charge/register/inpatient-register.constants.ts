/** 登记列表状态筛选（与后端 InpatientRegistrationConstants 一致） */
export const REGISTRATION_LIST_STATUS = {
  PLANNED: 'PLANNED',
  WAIT_ADMISSION: 'WAIT_ADMISSION',
  ADMITTED: 'ADMITTED',
} as const;

export const REGISTRATION_LIST_STATUS_OPTIONS = [
  { label: '待登记', value: REGISTRATION_LIST_STATUS.PLANNED },
  { label: '待入科', value: REGISTRATION_LIST_STATUS.WAIT_ADMISSION },
  { label: '已入科', value: REGISTRATION_LIST_STATUS.ADMITTED },
] as const;

/** 就诊状态 code（EncounterStatus） */
export const ENCOUNTER_STATUS = {
  PLANNED: 'PLANNED',
  IN_PROGRESS: 'IN_PROGRESS',
  CANCELLED: 'CANCELLED',
} as const;

/** 行政性别 code（AdministrativeGender） */
export const GENDER_OPTIONS = [
  { label: '男性', value: 'MALE' },
  { label: '女性', value: 'FEMALE' },
  { label: '未知', value: 'UNKNOWN' },
] as const;

/** 预交金支付渠道 code */
export const PAY_CHANNEL_OPTIONS = [
  { label: '现金', value: 'CASH' },
  { label: '支付宝', value: 'ALIPAY' },
  { label: '微信', value: 'WECHAT' },
  { label: '银行卡', value: 'BANK_CARD' },
] as const;

export const DEFAULT_PAY_CHANNEL_CODE = 'CASH';

/** 民族（联调前静态选项） */
export const ETHNICITY_OPTIONS = [
  { label: '汉族', value: '汉族' },
  { label: '蒙古族', value: '蒙古族' },
  { label: '回族', value: '回族' },
  { label: '藏族', value: '藏族' },
  { label: '维吾尔族', value: '维吾尔族' },
  { label: '苗族', value: '苗族' },
  { label: '其他', value: '其他' },
] as const;

/** 诊断类别（联调前静态选项） */
export const DIAGNOSIS_CATEGORY_OPTIONS = [
  { label: '普通住院', value: 'GENERAL' },
  { label: '日间手术', value: 'DAY_SURGERY' },
  { label: '日间病房', value: 'DAY_WARD' },
] as const;

export const DEFAULT_DIAGNOSIS_CATEGORY = 'GENERAL';

/** 入院类型（联调前静态选项） */
export const ADMISSION_TYPE_OPTIONS = [
  { label: '门诊', value: 'OUTPATIENT' },
  { label: '急诊', value: 'EMERGENCY' },
  { label: '其他医疗机构转入', value: 'TRANSFER' },
] as const;

/** 入院方式（联调前静态选项） */
export const IN_WAY_OPTIONS = [
  { label: '步行', value: 'WALK' },
  { label: '轮椅', value: 'WHEELCHAIR' },
  { label: '平车', value: 'STRETCHER' },
  { label: '担架', value: 'LITTER' },
] as const;

/** 患者病情（联调前静态选项） */
export const PATIENT_CONDITION_OPTIONS = [
  { label: '一般', value: 'GENERAL' },
  { label: '急', value: 'URGENT' },
  { label: '危', value: 'CRITICAL' },
] as const;
