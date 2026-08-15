import type {
  CommonDiagnosesResponse,
  EncounterDiagnosisRowDto,
  PatientOpspFilingDiagnosisResponse,
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
  /** 慢特病备案候选；无 CHS 时 enabled=false */
  opspFiling: PatientOpspFilingDiagnosisResponse;
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
  /** 就诊诊断行主键（绑定落库 `encounter_diagnosis_ids`；ElSelect value） */
  encounterDiagnosisId?: string;
  /** 主诊断标记：1=主诊（多选序列须排第一） */
  mainDiagnosisFlag?: number;
  /** 医疗类别码（慢病标识：14 / 140104 / 140201） */
  medicalTypeCode?: string;
  /** 中医病证组键；有值时删除走整组删除 */
  tcmSyndromeGroup?: string;
}

export interface DiagnosisConfirmPayload {
  selected: DiagnosisItem;
  /** 是否将本条设为主诊断（医嘱侧可忽略） */
  saveAsMainDiagnosis?: boolean;
  /** @deprecated 请用 saveAsPersonal；兼容旧消费方 */
  saveAsCommon: boolean;
  /** 是否追加个人常用模板 */
  saveAsPersonal?: boolean;
  /** 是否追加科室常用模板 */
  saveAsDepartment?: boolean;
}
