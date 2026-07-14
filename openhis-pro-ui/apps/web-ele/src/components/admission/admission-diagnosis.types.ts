/** 入院诊断中西医类型（默认西医） */
export type AdmissionDiagnosisMedicineType = 'tcm' | 'western';

/** 入院诊断表单状态（登记 / 办理住院共用） */
export interface AdmissionDiagnosisFormState {
  medicineType: AdmissionDiagnosisMedicineType;
  /** 西医：诊断目录主键 */
  westernDefinitionId: string;
  /** 中医：疾病目录主键 */
  tcmDiseaseDefinitionId: string;
  /** 中医：证候目录主键 */
  tcmSyndromeDefinitionId: string;
  /** 中医：疾病名称（展示/提交描述） */
  tcmDiseaseName: string;
  /** 中医：证候名称（展示/提交描述） */
  tcmSyndromeName: string;
  /** 可选诊断说明 */
  diagnosisDescription: string;
  /** 引用已有病情事实（门诊预填时） */
  conditionId?: string;
  /** 只读展示文案（有申请登记带入时） */
  readonlyDisplay?: string;
}

/** 默认空表单：西医、未选目录 */
export function createEmptyAdmissionDiagnosisFormState(): AdmissionDiagnosisFormState {
  return {
    medicineType: 'western',
    westernDefinitionId: '',
    tcmDiseaseDefinitionId: '',
    tcmSyndromeDefinitionId: '',
    tcmDiseaseName: '',
    tcmSyndromeName: '',
    diagnosisDescription: '',
  };
}
