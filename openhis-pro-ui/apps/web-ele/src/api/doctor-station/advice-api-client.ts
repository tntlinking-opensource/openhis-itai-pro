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
  ListDoctorStationAdviceRequest,
  ListDoctorStationAdviceResponse,
  PageDoctorStationAdviceCatalogRequest,
  PageDoctorStationAdviceCatalogResponse,
  SaveDoctorStationAdviceDraftBatchRequest,
  SaveDoctorStationAdviceDraftBatchResponse,
  SignDoctorStationAdviceBatchRequest,
  SignDoctorStationAdviceBatchResponse,
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
  httpPageDoctorStationAdviceCatalog: (
    body: PageDoctorStationAdviceCatalogRequest,
  ) => Promise<PageDoctorStationAdviceCatalogResponse>;
  httpSaveDoctorStationAdviceDraftBatch: (
    body: SaveDoctorStationAdviceDraftBatchRequest,
  ) => Promise<SaveDoctorStationAdviceDraftBatchResponse>;
  httpSignDoctorStationAdviceBatch: (
    body: SignDoctorStationAdviceBatchRequest,
  ) => Promise<SignDoctorStationAdviceBatchResponse>;
  httpUpdateDoctorStationMedicationAdviceGroups: (
    body: UpdateDoctorStationMedicationAdviceGroupsRequest,
  ) => Promise<UpdateDoctorStationMedicationAdviceGroupsResponse>;
  httpWithdrawDoctorStationAdviceBatch: (
    body: WithdrawDoctorStationAdviceBatchRequest,
  ) => Promise<WithdrawDoctorStationAdviceBatchResponse>;
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
    httpPageDoctorStationAdviceCatalog: (body) => post('pageDoctorStationAdviceCatalog', body),
    httpGetDoctorStationAdviceOrderBindInfo: (body) =>
      post('getDoctorStationAdviceOrderBindInfo', body),
    httpGetEncounterAdviceDefaultContract: (body) =>
      post('getEncounterAdviceDefaultContract', body),
    httpSaveDoctorStationAdviceDraftBatch: (body) =>
      post('saveDoctorStationAdviceDraftBatch', body),
    httpSignDoctorStationAdviceBatch: (body) => post('signDoctorStationAdviceBatch', body),
    httpWithdrawDoctorStationAdviceBatch: (body) => post('withdrawDoctorStationAdviceBatch', body),
    httpUpdateDoctorStationMedicationAdviceGroups: (body) =>
      post('updateDoctorStationMedicationAdviceGroups', body),
    httpListActivityExecutionLocations: (body) => post('listActivityExecutionLocations', body),
  };
}
