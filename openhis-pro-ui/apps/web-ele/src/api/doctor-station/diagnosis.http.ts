import { createDoctorStationDiagnosisHttpClient } from './diagnosis-api-client';

const outpatientDiagnosisHttp = createDoctorStationDiagnosisHttpClient('/doctorStationDiagnosis');

/** 查询当前就诊已保存的诊断列表 */
export const httpGetEncounterDiagnosis = outpatientDiagnosisHttp.httpGetEncounterDiagnosis;

/** 加载四类常用/历史/科室等诊断候选 */
export const httpGetConditionDefinitionBusinessClass =
  outpatientDiagnosisHttp.httpGetConditionDefinitionBusinessClass;

/** 加载慢特病备案可导入诊断（CHS 门控） */
export const httpGetPatientOpspFilingDiagnosis =
  outpatientDiagnosisHttp.httpGetPatientOpspFilingDiagnosis;

/** 未匹配慢病备案点击：幂等补录 chronic_spec 并返回可导入定义 */
export const httpEnsureOpspFilingDiagnosis = outpatientDiagnosisHttp.httpEnsureOpspFilingDiagnosis;

/** 分页检索已发布诊断定义 */
export const httpGetConditionDefinitionMetadata =
  outpatientDiagnosisHttp.httpGetConditionDefinitionMetadata;

/** 全量保存就诊诊断（批次替换） */
export const httpSaveDoctorDiagnosis = outpatientDiagnosisHttp.httpSaveDoctorDiagnosis;

/** 追加个人/科室常用诊断模板绑定 */
export const httpBindCommonDiagnosis = outpatientDiagnosisHttp.httpBindCommonDiagnosis;

/** 列出当前登录可维护的个人/科室常用诊断 */
export const httpListMaintainableCommonDiagnoses =
  outpatientDiagnosisHttp.httpListMaintainableCommonDiagnoses;

/** 解绑个人/科室常用诊断模板 */
export const httpUnbindCommonDiagnosis = outpatientDiagnosisHttp.httpUnbindCommonDiagnosis;

/** 删除一条就诊诊断事实 */
export const httpDelEncounterDiagnosis = outpatientDiagnosisHttp.httpDelEncounterDiagnosis;

/** 切换就诊主诊断（同就诊仅一条/一组主诊） */
export const httpSwitchMainEncounterDiagnosis =
  outpatientDiagnosisHttp.httpSwitchMainEncounterDiagnosis;
