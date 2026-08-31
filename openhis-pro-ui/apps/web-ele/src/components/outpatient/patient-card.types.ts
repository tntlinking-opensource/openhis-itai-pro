import type { OutpatientInfoItemDto } from '#/api/doctor-station/encounter.types';

/**
 * 门诊患者卡片：与 IF-DOR 就诊列表行（OutpatientInfoItemDto）一致，可叠加护士站扩展字段。
 */
export interface OutpatientPatientCardPayload extends OutpatientInfoItemDto {
  /** 护士站：待执行处置项数 */
  pendingExecuteCount?: number;
  /** 护士站：是否仍有待发放耗材（红框提醒；选中态不得掩盖） */
  hasPendingSupply?: boolean;
  /** 护士站：风险提示 */
  riskHint?: string;
}

/**
 * 住院队列卡片（病区/护士站住院侧，非门诊就诊 DTO）。
 */
export interface InpatientPatientCardPayload {
  patientType: 'inpatient';
  /** 列表行主键 */
  id: string;
  name?: string;
  gender: string;
  age: string;
  hospitalizationNo?: string;
  admissionTime: string;
  departmentAdmissionTime?: string;
  status?: string;
  paymentType?: string;
  /** 险种类型码（310 职工 / 390 城乡居民）；医生站短标签展示 */
  insutype?: string;
  priority?: string;
  queueNumber?: number | string;
  visitNo?: string;
  isRegistered?: boolean;
  pendingExecuteCount?: number;
  riskHint?: string;
  /** 账户余额主文案（住院医生站等） */
  accountBalanceSummary?: string;
  /** 账户余额副文案 */
  accountBalanceSub?: string;
  lowBalanceWarning?: boolean;
}

export type PatientCardPatient = InpatientPatientCardPayload | OutpatientPatientCardPayload;

/**
 * 门诊患者信息条：与就诊列表/读模型字段一致（camelCase）。
 */
export interface PatientInfoBarPayload {
  /** 与列表行一致，接诊等逻辑与条上展示同源 */
  encounterId?: null | string;
  patientName?: null | string;
  /** 读模型返回的性别展示文案，优先于 genderEnum */
  genderEnumText?: null | string;
  genderEnum?: null | number;
  ageText?: null | string;
  phone?: null | string;
  medicalRecordNo?: null | string;
  /** 患者地址全文（主档；头部条完整展示，不截断） */
  address?: null | string;
  /** 就诊类别名称（如普通门诊），用于条上标签 */
  serviceTypeName?: null | string;
  /** 挂号/就诊开始时刻，原始字符串由条内格式化为展示 */
  startTime?: null | string;
  /** 就诊/队列状态编码，与 IF-DOR-01 `statusEnum` 一致 */
  statusEnum?: null | string;
}

/**
 * 使用场景：控制同一卡片内「公共区 + 住院/门诊信息区 + 可选附加区」的展示策略。
 * - default：通用队列（与未传 variant 行为一致）
 * - doctor：医生站患者列表
 * - nurse：护士站队列（待执行数、风险提示）
 * - dispense：门诊发药队列（不展示就诊状态标签；展示费用性质胶囊与电话；右上角第三段优先 `organizationName`；
 *   就诊号用队列 `visitNo` 全量）
 */
export type PatientCardVariant = 'default' | 'dispense' | 'doctor' | 'nurse';
