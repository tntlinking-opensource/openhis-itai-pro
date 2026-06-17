import { createDoctorStationTcmHttpClient } from './tcm-api-client';

const outpatientTcmHttp = createDoctorStationTcmHttpClient('/doctorStationTcm');

/** 加载中医病证组列表 */
export const httpListTcmDiagnosisGroups = outpatientTcmHttp.httpListTcmDiagnosisGroups;

/** 分页检索中医病名/证候目录 */
export const httpPageTcmConditionDefinitionCatalog =
  outpatientTcmHttp.httpPageTcmConditionDefinitionCatalog;

/** 全量保存病证组 */
export const httpSaveTcmDiagnosisGroupsBatch = outpatientTcmHttp.httpSaveTcmDiagnosisGroupsBatch;

/** 删除病证组 */
export const httpDeleteTcmDiagnosisGroup = outpatientTcmHttp.httpDeleteTcmDiagnosisGroup;

/** 中医 Tab 初始化（仅中药用法术语） */
export const httpInitDoctorStationTcm = outpatientTcmHttp.httpInitDoctorStationTcm;

/** 加载中药处方列表 */
export const httpListTcmHerbalPrescriptions = outpatientTcmHttp.httpListTcmHerbalPrescriptions;

/** 分页检索中药饮片目录 */
export const httpPageTcmHerbalMedicationCatalog =
  outpatientTcmHttp.httpPageTcmHerbalMedicationCatalog;

/** 暂存中药处方 */
export const httpSaveTcmHerbalPrescriptionDraftBatch =
  outpatientTcmHttp.httpSaveTcmHerbalPrescriptionDraftBatch;

/** 签发中药处方 */
export const httpSignTcmHerbalPrescriptionBatch =
  outpatientTcmHttp.httpSignTcmHerbalPrescriptionBatch;

/** 签退/删除中药处方 */
export const httpWithdrawOrDeleteTcmHerbalPrescription =
  outpatientTcmHttp.httpWithdrawOrDeleteTcmHerbalPrescription;

/** 查询患者历史中药处方 */
export const httpListPatientTcmHerbalPrescriptionHistory =
  outpatientTcmHttp.httpListPatientTcmHerbalPrescriptionHistory;
