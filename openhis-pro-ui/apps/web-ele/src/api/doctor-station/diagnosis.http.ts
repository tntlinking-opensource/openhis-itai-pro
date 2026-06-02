import { createDoctorStationDiagnosisHttpClient } from './diagnosis-api-client';

const outpatientDiagnosisHttp = createDoctorStationDiagnosisHttpClient('/doctorStationDiagnosis');

/** 查询当前就诊已保存的诊断列表 */
export const httpGetEncounterDiagnosis = outpatientDiagnosisHttp.httpGetEncounterDiagnosis;

/** 加载四类常用/历史/科室等诊断候选 */
export const httpGetConditionDefinitionBusinessClass =
  outpatientDiagnosisHttp.httpGetConditionDefinitionBusinessClass;

/** 分页检索已发布诊断定义 */
export const httpGetConditionDefinitionMetadata =
  outpatientDiagnosisHttp.httpGetConditionDefinitionMetadata;

/** 全量保存就诊诊断（批次替换） */
export const httpSaveDoctorDiagnosis = outpatientDiagnosisHttp.httpSaveDoctorDiagnosis;

/** 删除一条就诊诊断事实 */
export const httpDelEncounterDiagnosis = outpatientDiagnosisHttp.httpDelEncounterDiagnosis;
