import type { DrugGroupDisplayItem } from '#/utils/doctor-station/drug-group-table';
import type {
  NurseExecuteDisplayStatusCode,
  SupplyDispenseStatusCode,
} from '#/utils/nurse-station/nurse-execute-status';

export type NurseExecuteRowTypeCode = 'DEVICE' | 'MEDICATION' | 'OTHER' | 'SERVICE';

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
  /** 是否仍有待发放耗材（BR-NUR-017；卡片红框与「待发耗材」标签） */
  hasPendingSupply?: boolean;
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
  /** 展示态 code（逻辑判断用） */
  statusEnum?: NurseExecuteDisplayStatusCode;
  /** 行资源类型 code */
  rowType?: NurseExecuteRowTypeCode;
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
  /** 成组合并后的成员行（打印等需展开全部 requestId） */
  mergedMedicationMembers?: NurseAdviceExecuteRow[];
  name?: string;
  procedureContent?: string;
  procedureDosageText?: string;
  procedureExtension?: string;
  /** 执行人列展示 */
  performerDisplay?: string;
  /** 来源请求主键（取消执行 IF-NUR-03B/05） */
  requestId?: string;
  /** 申请业务号（采血贴/LIS applyNo；缺省回落 requestId） */
  busNo?: string;
  /** 来源表（wkf_service_request / med_medication_request） */
  requestTable?: string;
  /** 净已执行次数 performCount - cancelCount */
  netPerformCount?: number;
  /** 是否展示「取消执行」 */
  canCancelExecute?: boolean;
  /** 药品成组主键（输液组） */
  groupId?: string;
  /** 处方号（药品医嘱） */
  prescriptionNo?: string;
  /** 申请单整单号（诊疗 AR 前缀 prescriptionNo，同单多行共享） */
  applyFormNo?: string;
  /** 药品类目展示文案 */
  medCategoryCodeText?: string;
  /** 是否输液/注射相关（后端 infusion_flag / inject_flag 回落） */
  infusionFlag?: boolean | null;
  /** 是否皮试药（med_medication_request.skin_test_flag） */
  skinTestFlag?: boolean | null;
  /** 是否中药医嘱（med_medication_request.tcm_flag） */
  tcmFlag?: boolean | null;
  /** 给药途径 code（术语 method_code） */
  methodCode?: string;
  /** 诊疗类目 code（检验/处置等） */
  serviceCategoryCode?: string;
  /** 诊疗类目展示文案 */
  serviceCategoryCodeText?: string;
  /** 合管检验设备主键（采血贴分组） */
  specimenTubeMergeDeviceId?: string;
  /** 合管组序号（同设备多组时区分采血贴） */
  specimenTubeMergeGroupNo?: number | string;
  /** 医嘱优先级 code（EM=急；采血贴印「急」） */
  priorityEnum?: string;
}

export interface SupplyItem {
  id: string;
  /** 发放单主键（IF-NUR-05 取消执行） */
  dispenseId?: string;
  name: string;
  spec: string;
  usage: string;
  quantity: number;
  /** 签发批号展示文案；无则侧栏不展示批号行 */
  lotNumber?: string;
  /** 单价展示文案（已格式化为元）；无则侧栏不展示单价 */
  unitPriceText?: string;
  status: '已发放' | '已配药' | '待发放' | '待退费' | '已退费';
  /** 发放展示态 code（逻辑判断用） */
  statusCode: SupplyDispenseStatusCode;
  /** 护士执行后可取消（PREPARED / COMPLETED / PART_COMPLETED） */
  canCancelExecute?: boolean;
}

export type NurseActionRecordTypeCode = 'EXECUTE' | 'PRINT';

export type NurseActionRecordTypeFilter = '' | NurseActionRecordTypeCode;

export const NURSE_ACTION_RECORD_TYPE_LABEL: Record<NurseActionRecordTypeCode, string> = {
  EXECUTE: '执行',
  PRINT: '打印',
};

/** 护士站操作记录动作 code（逻辑判断用） */
export type NurseActionRecordActionCode =
  | 'CANCEL_EXECUTE'
  | 'DELETE_SKIN_TEST'
  | 'EXECUTE_AND_RECORD'
  | 'PRINT_EXAM_APPLY'
  | 'PRINT_INFUSION_LABEL'
  | 'PRINT_LAB_APPLY'
  | 'PRINT_NEBULIZATION_LABEL'
  | 'PRINT_PRESCRIPTION'
  | 'PRINT_SKIN_TEST_LABEL'
  | 'PRINT_SPECIMEN_BARCODE'
  | 'PRINT_TREATMENT_APPLY'
  | 'PRINT_TREATMENT_SHEET'
  | 'REGISTER_SKIN_TEST';

export const NURSE_ACTION_RECORD_ACTION_LABEL: Record<NurseActionRecordActionCode, string> = {
  EXECUTE_AND_RECORD: '执行并记录',
  CANCEL_EXECUTE: '取消执行',
  REGISTER_SKIN_TEST: '登记皮试',
  DELETE_SKIN_TEST: '删除皮试记录',
  PRINT_INFUSION_LABEL: '打印瓶贴',
  PRINT_TREATMENT_SHEET: '打印处置单',
  PRINT_PRESCRIPTION: '打印处方单',
  PRINT_SPECIMEN_BARCODE: '打印采血码',
  PRINT_SKIN_TEST_LABEL: '打印皮试标签',
  PRINT_NEBULIZATION_LABEL: '打印雾化标签',
  PRINT_EXAM_APPLY: '打印检查申请单',
  PRINT_LAB_APPLY: '打印检验申请单',
  PRINT_TREATMENT_APPLY: '打印治疗申请单',
};

/** 与 IF-NUR-03 服务端记录重复、需过滤的本地汇总动作 */
export const LOCAL_DEDUP_ACTION_CODES = new Set<NurseActionRecordActionCode>([
  'EXECUTE_AND_RECORD',
  'CANCEL_EXECUTE',
]);

export interface ActionRecord {
  id: string;
  time: string;
  operator: string;
  /** 操作人列标题（执行人 / 取消执行人）；本地记录未传时由 appendRecord 回落 */
  operatorLabel?: string;
  type: NurseActionRecordTypeCode;
  /** 动作 code（逻辑判断用） */
  actionCode?: NurseActionRecordActionCode;
  /** 动作展示文案 */
  action: string;
  target: string;
  result: '成功';
}

export interface SupplyRoomOption {
  label: string;
  value: string;
}
