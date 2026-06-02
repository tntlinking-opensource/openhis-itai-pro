import type { DrugGroupDisplayItem } from '#/utils/drug-group-table';

/** 护士站队列患者（演示字段，后续可对接接口） */
export interface NursePatient {
  id: string;
  name?: string;
  /** 挂号/就诊业务流水号，用于患者卡片底栏展示 */
  encounterBusNo?: string;
  /** 读模型返回的性别展示文案 */
  genderEnumText?: string;
  gender: string;
  age: string;
  admissionTime: string;
  status: string;
  priority: string;
  queueNumber: number;
  isRegistered: boolean;
  patientType: 'outpatient';
  pendingExecuteCount: number;
  riskHint?: string;
  paymentType?: string;
  phone?: string;
  medicalRecordNo?: string;
  registrationTime?: string;
  doctor?: string;
}

/**
 * 与医生站医嘱 `advice/index.vue` 表行字段对齐；护士执行列表额外用 `procedure*`、`name` 做上下块拆分展示。
 */
export interface NurseAdviceExecuteRow {
  key: string;
  status: string;
  type: string;
  content: string;
  startTime: string;
  signer: string;
  department: string;
  stopOrVoid: string;
  extension: string;
  dosageText?: string;
  /** 已执行次数列展示（接口字段组装） */
  executedCountDisplay?: string;
  groupItems?: DrugGroupDisplayItem[];
  name?: string;
  procedureContent?: string;
  procedureDosageText?: string;
  procedureExtension?: string;
  /** 执行人列展示 */
  performerDisplay?: string;
  /** 来源请求主键（取消执行 IF-NUR-03B/05） */
  requestId?: string;
  /** 来源表（wkf_service_request / med_medication_request） */
  requestTable?: string;
  /** 净已执行次数 performCount - cancelCount */
  netPerformCount?: number;
  /** 是否展示「取消执行」 */
  canCancelExecute?: boolean;
}

export interface SupplyItem {
  id: string;
  name: string;
  spec: string;
  usage: string;
  quantity: number;
  status: '已发放' | '待发放';
}

export interface ActionRecord {
  id: string;
  time: string;
  operator: string;
  type: '打印' | '执行';
  action: string;
  target: string;
  result: '成功';
}

export interface SupplyRoomOption {
  label: string;
  value: string;
}
