import type {
  CommonDiagnosesResponse,
  EncounterDiagnosisRowDto,
} from '#/api/doctor-station/diagnosis.types';

/** 诊断录入页签拉数所需上下文（与接口入参一致，id 保持 string） */
export interface EncounterDiagnosisTabContext {
  encounterId: string;
  patientId: string;
}

export interface EncounterDiagnosisTabData {
  businessClass: CommonDiagnosesResponse;
  /** 接口原始行 */
  diagnosisList: EncounterDiagnosisRowDto[];
  /** 列表展示：中医按 tcmSyndromeGroup 合并 */
  displayDiagnosisList: EncounterDiagnosisRowDto[];
}

export type DiagnosisMedicineType = 'tcm' | 'western';

export type DiagnosisScopeType = 'all' | 'common' | 'history';

export interface DiagnosisItem {
  id: string;
  code: string;
  name: string;
  medicineType: DiagnosisMedicineType;
  isCommon?: boolean;
  isHistory?: boolean;
  /** 就诊诊断接口 `EncounterDiagnosisRowDto.conditionDefinitionId`，保存医嘱时透传 */
  conditionDefinitionId?: string;
}

export interface DiagnosisConfirmPayload {
  selected: DiagnosisItem;
  /** 是否将本条设为主诊断（医嘱侧可忽略） */
  saveAsMainDiagnosis?: boolean;
  saveAsCommon: boolean;
}
