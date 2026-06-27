import type {
  ComputeMedicationFrequencyExecutionsRequest,
  ComputeMedicationFrequencyExecutionsResponse,
  GetDoctorStationAdviceOrderBindInfoRequest,
  GetDoctorStationAdviceOrderBindInfoResponse,
  GetEncounterAdviceDefaultContractRequest,
  GetEncounterAdviceDefaultContractResponse,
  InitDoctorStationAdviceRequest,
  InitDoctorStationAdviceResponse,
  ListActivityExecutionLocationsRequest,
  ListActivityExecutionLocationsResponse,
  ListDoctorStationServiceApplyFormsRequest,
  ListDoctorStationServiceApplyFormsResponse,
  ListDoctorStationAdviceRequest,
  ListDoctorStationAdviceResponse,
  PageDoctorStationAdviceRequest,
  PageDoctorStationAdviceResponse,
  PageDoctorStationAdviceCatalogRequest,
  PageDoctorStationAdviceCatalogResponse,
  ResolveDoctorStationAdviceDraftBindsRequest,
  ResolveDoctorStationAdviceDraftBindsResponse,
  SaveDoctorStationAdviceDraftBatchRequest,
  SaveDoctorStationAdviceDraftBatchResponse,
  SaveDoctorStationServiceApplyFormRequest,
  SaveDoctorStationServiceApplyFormResponse,
  SignDoctorStationAdviceBatchRequest,
  SignDoctorStationAdviceBatchResponse,
  PageEncounterMedicationPrescriptionSlipsRequest,
  PageEncounterMedicationPrescriptionSlipsResponse,
  UpdateDoctorStationMedicationAdviceGroupsRequest,
  UpdateDoctorStationMedicationAdviceGroupsResponse,
  WithdrawDoctorStationAdviceBatchRequest,
  WithdrawDoctorStationAdviceBatchResponse,
} from './advice.types';

import { publicRequestClient } from '#/api/request';

export interface DoctorStationAdviceHttpClient {
  httpComputeMedicationFrequencyExecutions: (
    body: ComputeMedicationFrequencyExecutionsRequest,
  ) => Promise<ComputeMedicationFrequencyExecutionsResponse>;
  httpGetDoctorStationAdviceOrderBindInfo: (
    body: GetDoctorStationAdviceOrderBindInfoRequest,
  ) => Promise<GetDoctorStationAdviceOrderBindInfoResponse>;
  httpGetEncounterAdviceDefaultContract: (
    body: GetEncounterAdviceDefaultContractRequest,
  ) => Promise<GetEncounterAdviceDefaultContractResponse>;
  httpInitDoctorStationAdvice: (
    body: InitDoctorStationAdviceRequest,
  ) => Promise<InitDoctorStationAdviceResponse>;
  httpListActivityExecutionLocations: (
    body: ListActivityExecutionLocationsRequest,
  ) => Promise<ListActivityExecutionLocationsResponse>;
  httpListDoctorStationAdvice: (
    body: ListDoctorStationAdviceRequest,
  ) => Promise<ListDoctorStationAdviceResponse>;
  httpPageDoctorStationAdvice: (
    body: PageDoctorStationAdviceRequest,
  ) => Promise<PageDoctorStationAdviceResponse>;
  httpPageDoctorStationAdviceCatalog: (
    body: PageDoctorStationAdviceCatalogRequest,
  ) => Promise<PageDoctorStationAdviceCatalogResponse>;
  httpResolveDoctorStationAdviceDraftBinds: (
    body: ResolveDoctorStationAdviceDraftBindsRequest,
  ) => Promise<ResolveDoctorStationAdviceDraftBindsResponse>;
  httpSaveDoctorStationAdviceDraftBatch: (
    body: SaveDoctorStationAdviceDraftBatchRequest,
  ) => Promise<SaveDoctorStationAdviceDraftBatchResponse>;
  httpSignDoctorStationAdviceBatch: (
    body: SignDoctorStationAdviceBatchRequest,
  ) => Promise<SignDoctorStationAdviceBatchResponse>;
  httpPageEncounterMedicationPrescriptionSlips: (
    body: PageEncounterMedicationPrescriptionSlipsRequest,
  ) => Promise<PageEncounterMedicationPrescriptionSlipsResponse>;
  httpUpdateDoctorStationMedicationAdviceGroups: (
    body: UpdateDoctorStationMedicationAdviceGroupsRequest,
  ) => Promise<UpdateDoctorStationMedicationAdviceGroupsResponse>;
  httpWithdrawDoctorStationAdviceBatch: (
    body: WithdrawDoctorStationAdviceBatchRequest,
  ) => Promise<WithdrawDoctorStationAdviceBatchResponse>;
  httpListDoctorStationServiceApplyForms: (
    body: ListDoctorStationServiceApplyFormsRequest,
  ) => Promise<ListDoctorStationServiceApplyFormsResponse>;
  httpSaveDoctorStationServiceApplyForm: (
    body: SaveDoctorStationServiceApplyFormRequest,
  ) => Promise<SaveDoctorStationServiceApplyFormResponse>;
}

/** 按应用服务前缀生成医嘱开立 HTTP 客户端（门诊/住院方法名一致） */
export function createDoctorStationAdviceHttpClient(
  basePath: string,
): DoctorStationAdviceHttpClient {
  const prefix = basePath.replace(/\/$/, '');
  const post = <TReq, TRes>(method: string, body: TReq) =>
    publicRequestClient.post<TRes>(`${prefix}/${method}`, body);

  return {
    httpInitDoctorStationAdvice: (body) => post('initDoctorStationAdvice', body),
    httpComputeMedicationFrequencyExecutions: (body) =>
      post('computeMedicationFrequencyExecutions', body),
    httpListDoctorStationAdvice: (body) => post('listDoctorStationAdvice', body),
    httpPageDoctorStationAdvice: (body) => post('pageDoctorStationAdvice', body),
    httpPageDoctorStationAdviceCatalog: (body) => post('pageDoctorStationAdviceCatalog', body),
    httpGetDoctorStationAdviceOrderBindInfo: (body) =>
      post('getDoctorStationAdviceOrderBindInfo', body),
    httpGetEncounterAdviceDefaultContract: (body) =>
      post('getEncounterAdviceDefaultContract', body),
    httpResolveDoctorStationAdviceDraftBinds: (body) =>
      post('resolveDoctorStationAdviceDraftBinds', body),
    httpSaveDoctorStationAdviceDraftBatch: (body) =>
      post('saveDoctorStationAdviceDraftBatch', body),
    httpSignDoctorStationAdviceBatch: (body) => post('signDoctorStationAdviceBatch', body),
    httpWithdrawDoctorStationAdviceBatch: (body) => post('withdrawDoctorStationAdviceBatch', body),
    httpPageEncounterMedicationPrescriptionSlips: (body) =>
      post('pageEncounterMedicationPrescriptionSlips', body),
    httpUpdateDoctorStationMedicationAdviceGroups: (body) =>
      post('updateDoctorStationMedicationAdviceGroups', body),
    httpListActivityExecutionLocations: (body) => post('listActivityExecutionLocations', body),
    httpListDoctorStationServiceApplyForms: (body) =>
      post('listDoctorStationServiceApplyForms', body),
    httpSaveDoctorStationServiceApplyForm: (body) =>
      post('saveDoctorStationServiceApplyForm', body),
  };
}
