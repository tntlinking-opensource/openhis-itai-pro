import { createDoctorStationDiagnosisHttpClient } from '#/api/doctor-station/diagnosis-api-client';

/** 住院医生站 · 诊断录入 */
const BASE = '/inpatientDoctorStationDiagnosis';

const inpatientDiagnosisHttp = createDoctorStationDiagnosisHttpClient(BASE);

/** 查询当前就诊已保存的诊断列表 */
export const httpGetInpatientEncounterDiagnosis = inpatientDiagnosisHttp.httpGetEncounterDiagnosis;

/** 加载四类常用/历史/科室等诊断候选 */
export const httpGetInpatientConditionDefinitionBusinessClass =
  inpatientDiagnosisHttp.httpGetConditionDefinitionBusinessClass;

/** 分页检索已发布诊断定义 */
export const httpGetInpatientConditionDefinitionMetadata =
  inpatientDiagnosisHttp.httpGetConditionDefinitionMetadata;

/** 全量保存就诊诊断（批次替换） */
export const httpSaveInpatientDoctorDiagnosis = inpatientDiagnosisHttp.httpSaveDoctorDiagnosis;

/** 追加个人/科室常用诊断模板绑定 */
export const httpBindInpatientCommonDiagnosis = inpatientDiagnosisHttp.httpBindCommonDiagnosis;

/** 列出当前登录可维护的个人/科室常用诊断 */
export const httpListInpatientMaintainableCommonDiagnoses =
  inpatientDiagnosisHttp.httpListMaintainableCommonDiagnoses;

/** 解绑个人/科室常用诊断模板 */
export const httpUnbindInpatientCommonDiagnosis = inpatientDiagnosisHttp.httpUnbindCommonDiagnosis;

/** 删除一条就诊诊断事实 */
export const httpDelInpatientEncounterDiagnosis = inpatientDiagnosisHttp.httpDelEncounterDiagnosis;

/** 切换就诊主诊断（同就诊仅一条/一组主诊） */
export const httpSwitchMainInpatientEncounterDiagnosis =
  inpatientDiagnosisHttp.httpSwitchMainEncounterDiagnosis;
