import { createInpatientDoctorStationTcmHttpClient } from '#/api/doctor-station/tcm-api-client';

const inpatientTcmHttp = createInpatientDoctorStationTcmHttpClient();

/** 住院中医 Tab 初始化（与门诊组件统一入口名） */
export const httpInitDoctorStationTcm = inpatientTcmHttp.httpInitDoctorStationTcm;
/** 住院中医 Tab 初始化 */
export const httpInitInpatientDoctorStationTcm = inpatientTcmHttp.httpInitDoctorStationTcm;

/** 加载中医病证组列表 */
export const httpListTcmDiagnosisGroups = inpatientTcmHttp.httpListTcmDiagnosisGroups;

/** 分页检索中医病名/证候目录 */
export const httpPageTcmConditionDefinitionCatalog =
  inpatientTcmHttp.httpPageTcmConditionDefinitionCatalog;

/** 全量保存病证组 */
export const httpSaveTcmDiagnosisGroupsBatch = inpatientTcmHttp.httpSaveTcmDiagnosisGroupsBatch;

/** 删除病证组 */
export const httpDeleteTcmDiagnosisGroup = inpatientTcmHttp.httpDeleteTcmDiagnosisGroup;

/** 加载中药处方列表 */
export const httpListTcmHerbalPrescriptions = inpatientTcmHttp.httpListTcmHerbalPrescriptions;

/** 分页检索中药饮片目录 */
export const httpPageTcmHerbalMedicationCatalog =
  inpatientTcmHttp.httpPageTcmHerbalMedicationCatalog;

/** 暂存中药处方 */
export const httpSaveTcmHerbalPrescriptionDraftBatch =
  inpatientTcmHttp.httpSaveTcmHerbalPrescriptionDraftBatch;

/** 签发中药处方 */
export const httpSignTcmHerbalPrescriptionBatch =
  inpatientTcmHttp.httpSignTcmHerbalPrescriptionBatch;

/** 签退/删除中药处方 */
export const httpWithdrawOrDeleteTcmHerbalPrescription =
  inpatientTcmHttp.httpWithdrawOrDeleteTcmHerbalPrescription;

/** 查询患者历史中药处方 */
export const httpListPatientTcmHerbalPrescriptionHistory =
  inpatientTcmHttp.httpListPatientTcmHerbalPrescriptionHistory;

/** 停嘱（长期中药方） */
export const httpStopLongTermTcmHerbalPrescription =
  inpatientTcmHttp.httpStopLongTermTcmHerbalPrescription;

/** 取消停嘱（长期中药方） */
export const httpCancelStopLongTermTcmHerbalPrescription =
  inpatientTcmHttp.httpCancelStopLongTermTcmHerbalPrescription;

/** 按方改开立时间（IF-IP-TCM-42） */
export const httpChangeTcmHerbalPrescriptionOpenedTime =
  inpatientTcmHttp.httpChangeTcmHerbalPrescriptionOpenedTime;
