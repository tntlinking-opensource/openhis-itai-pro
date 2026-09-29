import type {
  DoctorStationAdviceCatalogRowDto,
  ServiceApplyFormDetailItemRequest,
} from '#/api/doctor-station/advice.types';

/** 申请单类型：检验 / 检查 / 治疗（AS-OP-DOR-003 BR-REQ-003） */
export type ServiceApplyKind = 'exam' | 'lab' | 'treatment';

/** 目录行（与 `DoctorStationAdviceCatalogRowDto` 一致，不做字段改名） */
export type ServiceApplyCatalogRow = DoctorStationAdviceCatalogRowDto;

/** 诊疗模板绑定的带出项（耗材可改；合管采血费只读） */
export interface ServiceApplyBoundConsumable {
  key: string;
  targetItemId: string;
  targetItemName: string;
  quantity: number;
  unitCode: string;
  unitName?: string;
  /** 目标表；缺省按耗材；采血费为 wkf_activity_definition */
  targetProductTableName?: string;
  /** 合管采血费等诊疗派生：卡内只读不可删 */
  readOnly?: boolean;
}

/** 已选申请行：保存字段与 `ServiceApplyFormLineRequest` 对齐，另加 UI 辅助字段 */
export interface ServiceApplySelectedItem {
  rowKey: string;
  catalogRowKey: string;
  activityDefinitionId: string;
  activityName: string;
  /** UI：单次执行数量；保存时 × 本行 executionCount 写入账单 quantity */
  quantity: number;
  /** UI：本行执行次数（默认 1）；落库 ServiceRequest.execution_count */
  executionCount: number;
  unitCode: string;
  /** 展示单价，来自目录 `unitPrice` */
  unitPrice: number;
  /** 医保等级展示（甲类/乙类/丙类）；仅展示，不参与分支 */
  chrgitmLvText?: string;
  remark?: string;
  detailItems: ServiceApplyFormDetailItemRequest[];
  /** 带出耗材是否已尝试加载（区分「无绑定」与「加载中/失败」） */
  boundConsumablesLoaded?: boolean;
  /** 诊疗绑定的带出耗材，可在卡内改数量或删行 */
  boundConsumables?: ServiceApplyBoundConsumable[];
  /** 医嘱优先级 code（按项目加急=EM；空=非急） */
  priorityEnum?: string;
}

/** 弹窗顶部患者摘要（展示用，非申请单保存契约） */
export interface ServiceApplyPatientSummary {
  patientName: string;
  genderText: string;
  ageText: string;
  encounterNo: string;
  feeNatureText: string;
}

/** 申请单诊断下拉项（就诊诊断主键 + 展示名） */
export interface ServiceApplyDiagnosisOption {
  label: string;
  value: string;
  conditionDefinitionId?: string;
}
