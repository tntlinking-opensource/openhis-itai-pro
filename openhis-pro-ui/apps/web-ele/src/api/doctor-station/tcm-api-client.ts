import type {
  CancelStopLongTermTcmHerbalPrescriptionRequest,
  CancelStopLongTermTcmHerbalPrescriptionResponse,
  ChangeTcmHerbalPrescriptionOpenedTimeRequest,
  ChangeTcmHerbalPrescriptionOpenedTimeResponse,
  DeleteTcmDiagnosisGroupRequest,
  DeleteTcmDiagnosisGroupResponse,
  InitDoctorStationTcmRequest,
  InitDoctorStationTcmResponse,
  ListPatientTcmRxHistoryRequest,
  ListPatientTcmRxHistoryResponse,
  ListTcmDiagnosisGroupsRequest,
  ListTcmDiagnosisGroupsResponse,
  ListTcmRxRequest,
  ListTcmRxResponse,
  PageTcmConditionDefinitionRequest,
  PageTcmConditionDefinitionResponse,
  PageTcmMedCatalogRequest,
  PageTcmMedCatalogResponse,
  SaveTcmDiagnosisGroupsBatchRequest,
  SaveTcmDiagnosisGroupsBatchResponse,
  SaveTcmRxDraftBatchRequest,
  SaveTcmRxDraftBatchResponse,
  SignTcmRxBatchRequest,
  SignTcmRxBatchResponse,
  StopLongTermTcmHerbalPrescriptionRequest,
  StopLongTermTcmHerbalPrescriptionResponse,
  SupplementTcmDecoctionChargeRequest,
  SupplementTcmDecoctionChargeResponse,
  VoidTcmDecoctionChargeRequest,
  VoidTcmDecoctionChargeResponse,
  WithdrawOrDeleteTcmRxRequest,
  WithdrawOrDeleteTcmRxResponse,
} from './tcm.types';

import { publicRequestClient } from '#/api/request';

export interface DoctorStationTcmHttpClient {
  httpDeleteTcmDiagnosisGroup: (
    body: DeleteTcmDiagnosisGroupRequest,
  ) => Promise<DeleteTcmDiagnosisGroupResponse>;
  httpInitDoctorStationTcm: (
    body: InitDoctorStationTcmRequest,
  ) => Promise<InitDoctorStationTcmResponse>;
  httpListPatientTcmHerbalPrescriptionHistory: (
    body: ListPatientTcmRxHistoryRequest,
  ) => Promise<ListPatientTcmRxHistoryResponse>;
  httpListTcmDiagnosisGroups: (
    body: ListTcmDiagnosisGroupsRequest,
  ) => Promise<ListTcmDiagnosisGroupsResponse>;
  httpListTcmHerbalPrescriptions: (body: ListTcmRxRequest) => Promise<ListTcmRxResponse>;
  httpPageTcmConditionDefinitionCatalog: (
    body: PageTcmConditionDefinitionRequest,
  ) => Promise<PageTcmConditionDefinitionResponse>;
  httpPageTcmHerbalMedicationCatalog: (
    body: PageTcmMedCatalogRequest,
  ) => Promise<PageTcmMedCatalogResponse>;
  httpSaveTcmDiagnosisGroupsBatch: (
    body: SaveTcmDiagnosisGroupsBatchRequest,
  ) => Promise<SaveTcmDiagnosisGroupsBatchResponse>;
  httpSaveTcmHerbalPrescriptionDraftBatch: (
    body: SaveTcmRxDraftBatchRequest,
  ) => Promise<SaveTcmRxDraftBatchResponse>;
  httpSignTcmHerbalPrescriptionBatch: (
    body: SignTcmRxBatchRequest,
  ) => Promise<SignTcmRxBatchResponse>;
  httpVoidTcmDecoctionCharge: (
    body: VoidTcmDecoctionChargeRequest,
  ) => Promise<VoidTcmDecoctionChargeResponse>;
  httpSupplementTcmDecoctionCharge: (
    body: SupplementTcmDecoctionChargeRequest,
  ) => Promise<SupplementTcmDecoctionChargeResponse>;
  httpWithdrawOrDeleteTcmHerbalPrescription: (
    body: WithdrawOrDeleteTcmRxRequest,
  ) => Promise<WithdrawOrDeleteTcmRxResponse>;
}

export interface InpatientDoctorStationTcmHttpClient extends DoctorStationTcmHttpClient {
  httpStopLongTermTcmHerbalPrescription: (
    body: StopLongTermTcmHerbalPrescriptionRequest,
  ) => Promise<StopLongTermTcmHerbalPrescriptionResponse>;
  httpCancelStopLongTermTcmHerbalPrescription: (
    body: CancelStopLongTermTcmHerbalPrescriptionRequest,
  ) => Promise<CancelStopLongTermTcmHerbalPrescriptionResponse>;
  /** 按方改开立时间（IF-IP-TCM-42） */
  httpChangeTcmHerbalPrescriptionOpenedTime: (
    body: ChangeTcmHerbalPrescriptionOpenedTimeRequest,
  ) => Promise<ChangeTcmHerbalPrescriptionOpenedTimeResponse>;
}

/** 按应用服务前缀生成门诊中医 Tab HTTP 客户端 */
export function createDoctorStationTcmHttpClient(basePath: string): DoctorStationTcmHttpClient {
  const prefix = basePath.replace(/\/$/, '');
  const post = <TReq, TRes>(method: string, body: TReq) =>
    publicRequestClient.post<TRes>(`${prefix}/${method}`, body);

  return {
    httpListTcmDiagnosisGroups: (body) => post('listTcmDiagnosisGroups', body),
    httpPageTcmConditionDefinitionCatalog: (body) =>
      post('pageTcmConditionDefinitionCatalog', body),
    httpSaveTcmDiagnosisGroupsBatch: (body) => post('saveTcmDiagnosisGroupsBatch', body),
    httpDeleteTcmDiagnosisGroup: (body) => post('deleteTcmDiagnosisGroup', body),
    httpInitDoctorStationTcm: (body) => post('initDoctorStationTcm', body),
    httpListTcmHerbalPrescriptions: (body) => post('listTcmHerbalPrescriptions', body),
    httpPageTcmHerbalMedicationCatalog: (body) => post('pageTcmHerbalMedicationCatalog', body),
    httpSaveTcmHerbalPrescriptionDraftBatch: (body) =>
      post('saveTcmHerbalPrescriptionDraftBatch', body),
    httpSignTcmHerbalPrescriptionBatch: (body) => post('signTcmHerbalPrescriptionBatch', body),
    httpVoidTcmDecoctionCharge: (body) => post('voidTcmDecoctionCharge', body),
    httpSupplementTcmDecoctionCharge: (body) => post('supplementTcmDecoctionCharge', body),
    httpWithdrawOrDeleteTcmHerbalPrescription: (body) =>
      post('withdrawOrDeleteTcmHerbalPrescription', body),
    httpListPatientTcmHerbalPrescriptionHistory: (body) =>
      post('listPatientTcmHerbalPrescriptionHistory', body),
  };
}

/** 住院中医 Tab HTTP 客户端（初始化与停嘱接口名与门诊不同） */
export function createInpatientDoctorStationTcmHttpClient(
  basePath = '/inpatientDoctorStationTcm',
): InpatientDoctorStationTcmHttpClient {
  const base = createDoctorStationTcmHttpClient(basePath);
  const prefix = basePath.replace(/\/$/, '');
  const post = <TReq, TRes>(method: string, body: TReq) =>
    publicRequestClient.post<TRes>(`${prefix}/${method}`, body);

  return {
    ...base,
    httpInitDoctorStationTcm: (body) => post('initInpatientDoctorStationTcm', body),
    httpStopLongTermTcmHerbalPrescription: (body) =>
      post('stopLongTermTcmHerbalPrescription', body),
    httpCancelStopLongTermTcmHerbalPrescription: (body) =>
      post('cancelStopLongTermTcmHerbalPrescription', body),
    httpChangeTcmHerbalPrescriptionOpenedTime: (body) =>
      post('changeTcmHerbalPrescriptionOpenedTime', body),
  };
}
