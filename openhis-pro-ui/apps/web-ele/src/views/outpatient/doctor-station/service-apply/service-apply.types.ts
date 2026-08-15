import type {
  DoctorStationAdviceCatalogRowDto,
  ServiceApplyFormDetailItemRequest,
} from '#/api/doctor-station/advice.types';

/** 申请单类型：检验 / 检查 / 治疗（AS-OP-DOR-003 BR-REQ-003） */
export type ServiceApplyKind = 'exam' | 'lab' | 'treatment';

/** 目录行（与 `DoctorStationAdviceCatalogRowDto` 一致，不做字段改名） */
export type ServiceApplyCatalogRow = DoctorStationAdviceCatalogRowDto;

/** 已选申请行：保存字段与 `ServiceApplyFormLineRequest` 对齐，另加 UI 辅助字段 */
export interface ServiceApplySelectedItem {
  rowKey: string;
  catalogRowKey: string;
  activityDefinitionId: string;
  activityName: string;
  quantity: number;
  unitCode: string;
  /** 展示单价，来自目录 `unitPrice` */
  unitPrice: number;
  /** 医保等级展示（甲类/乙类/丙类）；仅展示，不参与分支 */
  chrgitmLvText?: string;
  remark?: string;
  detailItems: ServiceApplyFormDetailItemRequest[];
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
