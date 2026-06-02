import type {
  ComputeMedicationFrequencyExecutionsRequest,
  ComputeMedicationFrequencyExecutionsResponse,
  CreateDischargeInpatientDoctorStationAdviceRequest,
  CreateDischargeInpatientDoctorStationAdviceResponse,
  GetInpatientDoctorStationAdviceOrderBindInfoRequest,
  GetInpatientDoctorStationAdviceOrderBindInfoResponse,
  InitInpatientDoctorStationAdviceRequest,
  InitInpatientDoctorStationAdviceResponse,
  ListActivityExecutionLocationsRequest,
  ListActivityExecutionLocationsResponse,
  ListInpatientDoctorStationAdviceRequest,
  ListInpatientDoctorStationAdviceResponse,
  PageInpatientDoctorStationAdviceCatalogRequest,
  PageInpatientDoctorStationAdviceCatalogResponse,
  SaveInpatientDoctorStationAdviceDraftBatchRequest,
  SaveInpatientDoctorStationAdviceDraftBatchResponse,
  SignInpatientDoctorStationAdviceBatchRequest,
  SignInpatientDoctorStationAdviceBatchResponse,
  StopLongTermInpatientDoctorStationAdviceRequest,
  StopLongTermInpatientDoctorStationAdviceResponse,
  UpdateInpatientDoctorStationMedicationAdviceGroupsRequest,
  UpdateInpatientDoctorStationMedicationAdviceGroupsResponse,
  WithdrawInpatientDoctorStationAdviceBatchRequest,
  WithdrawInpatientDoctorStationAdviceBatchResponse,
} from './inpatient-doctor-station-advice.types';

import { publicRequestClient } from '#/api/request';

export interface InpatientDoctorStationAdviceHttpClient {
  httpListInpatientDoctorStationAdvice: (
    body: ListInpatientDoctorStationAdviceRequest,
  ) => Promise<ListInpatientDoctorStationAdviceResponse>;
  httpPageInpatientDoctorStationAdviceCatalog: (
    body: PageInpatientDoctorStationAdviceCatalogRequest,
  ) => Promise<PageInpatientDoctorStationAdviceCatalogResponse>;
  httpGetInpatientDoctorStationAdviceOrderBindInfo: (
    body: GetInpatientDoctorStationAdviceOrderBindInfoRequest,
  ) => Promise<GetInpatientDoctorStationAdviceOrderBindInfoResponse>;
  httpInitInpatientDoctorStationAdvice: (
    body: InitInpatientDoctorStationAdviceRequest,
  ) => Promise<InitInpatientDoctorStationAdviceResponse>;
  httpListActivityExecutionLocations: (
    body: ListActivityExecutionLocationsRequest,
  ) => Promise<ListActivityExecutionLocationsResponse>;
  httpSaveInpatientDoctorStationAdviceDraftBatch: (
    body: SaveInpatientDoctorStationAdviceDraftBatchRequest,
  ) => Promise<SaveInpatientDoctorStationAdviceDraftBatchResponse>;
  httpSignInpatientDoctorStationAdviceBatch: (
    body: SignInpatientDoctorStationAdviceBatchRequest,
  ) => Promise<SignInpatientDoctorStationAdviceBatchResponse>;
  httpWithdrawInpatientDoctorStationAdviceBatch: (
    body: WithdrawInpatientDoctorStationAdviceBatchRequest,
  ) => Promise<WithdrawInpatientDoctorStationAdviceBatchResponse>;
  httpUpdateInpatientDoctorStationMedicationAdviceGroups: (
    body: UpdateInpatientDoctorStationMedicationAdviceGroupsRequest,
  ) => Promise<UpdateInpatientDoctorStationMedicationAdviceGroupsResponse>;
  httpStopLongTermInpatientDoctorStationAdvice: (
    body: StopLongTermInpatientDoctorStationAdviceRequest,
  ) => Promise<StopLongTermInpatientDoctorStationAdviceResponse>;
  httpCreateDischargeInpatientDoctorStationAdvice: (
    body: CreateDischargeInpatientDoctorStationAdviceRequest,
  ) => Promise<CreateDischargeInpatientDoctorStationAdviceResponse>;
  /** 频次推算总量：SDD 未单列 IF，与门诊同方法名挂住院前缀（联调以 Swagger 为准） */
  httpComputeMedicationFrequencyExecutions: (
    body: ComputeMedicationFrequencyExecutionsRequest,
  ) => Promise<ComputeMedicationFrequencyExecutionsResponse>;
}

/** 住院医生站医嘱开立应用服务 HTTP 客户端（方法名与后端 AppService 一致） */
export function createInpatientDoctorStationAdviceHttpClient(
  basePath = '/inpatientDoctorStationAdvice',
): InpatientDoctorStationAdviceHttpClient {
  const prefix = basePath.replace(/\/$/, '');
  const post = <TReq, TRes>(method: string, body: TReq) =>
    publicRequestClient.post<TRes>(`${prefix}/${method}`, body);

  return {
    httpListInpatientDoctorStationAdvice: (body) => post('listInpatientDoctorStationAdvice', body),
    httpPageInpatientDoctorStationAdviceCatalog: (body) =>
      post('pageInpatientDoctorStationAdviceCatalog', body),
    httpGetInpatientDoctorStationAdviceOrderBindInfo: (body) =>
      post('getInpatientDoctorStationAdviceOrderBindInfo', body),
    httpInitInpatientDoctorStationAdvice: (body) => post('initInpatientDoctorStationAdvice', body),
    httpListActivityExecutionLocations: (body) => post('listActivityExecutionLocations', body),
    httpSaveInpatientDoctorStationAdviceDraftBatch: (body) =>
      post('saveInpatientDoctorStationAdviceDraftBatch', body),
    httpSignInpatientDoctorStationAdviceBatch: (body) =>
      post('signInpatientDoctorStationAdviceBatch', body),
    httpWithdrawInpatientDoctorStationAdviceBatch: (body) =>
      post('withdrawInpatientDoctorStationAdviceBatch', body),
    httpUpdateInpatientDoctorStationMedicationAdviceGroups: (body) =>
      post('updateInpatientDoctorStationMedicationAdviceGroups', body),
    httpStopLongTermInpatientDoctorStationAdvice: (body) =>
      post('stopLongTermInpatientDoctorStationAdvice', body),
    httpCreateDischargeInpatientDoctorStationAdvice: (body) =>
      post('createDischargeInpatientDoctorStationAdvice', body),
    httpComputeMedicationFrequencyExecutions: (body) =>
      post('computeMedicationFrequencyExecutions', body),
  };
}
