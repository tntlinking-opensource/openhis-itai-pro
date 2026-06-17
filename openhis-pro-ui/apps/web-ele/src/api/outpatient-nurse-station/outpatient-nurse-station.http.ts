import type {
  BatchCancelDispositionRequest,
  BatchCancelDispositionResponse,
  BatchPerformDispositionRequest,
  BatchPerformDispositionResponse,
  ListNetRemainingProcedureRecordsForNurseStationRequest,
  ListNetRemainingProcedureRecordsForNurseStationResponse,
  ListProcedureRecordsForNurseStationRequest,
  ListProcedureRecordsForNurseStationResponse,
  PageNurseStationPatientQueueRequest,
  PageNurseStationPatientQueueResponse,
  QueryEncounterTreatmentSheetRequest,
  QueryEncounterTreatmentSheetResponse,
} from './outpatient-nurse-station.types';

import { publicRequestClient } from '#/api/request';

/** 分页加载门诊护士站患者队列 */
export async function httpPageNurseStationPatientQueue(
  body: PageNurseStationPatientQueueRequest,
): Promise<PageNurseStationPatientQueueResponse> {
  return publicRequestClient.post<PageNurseStationPatientQueueResponse>(
    '/outpatientNurseStationPatient/pageNurseStationPatientQueue',
    body,
  );
}

/** 按就诊加载处置单（诊疗/药品/耗材三列表） */
export async function httpQueryEncounterTreatmentSheet(
  body: QueryEncounterTreatmentSheetRequest,
): Promise<QueryEncounterTreatmentSheetResponse> {
  return publicRequestClient.post<QueryEncounterTreatmentSheetResponse>(
    '/outpatientNurseStationTreatment/queryEncounterTreatmentSheet',
    body,
  );
}

/** 分页加载执行记录（医嘱执行单读模型） */
export async function httpListProcedureRecordsForNurseStation(
  body: ListProcedureRecordsForNurseStationRequest,
): Promise<ListProcedureRecordsForNurseStationResponse> {
  return publicRequestClient.post<ListProcedureRecordsForNurseStationResponse>(
    '/outpatientNurseStationTreatment/listProcedureRecordsForNurseStation',
    body,
  );
}

/** 提交批量处置执行（诊疗/药品登记与耗材发放） */
export async function httpBatchPerformDisposition(
  body: BatchPerformDispositionRequest,
): Promise<BatchPerformDispositionResponse> {
  return publicRequestClient.post<BatchPerformDispositionResponse>(
    '/outpatientNurseStationTreatment/batchPerformDisposition',
    body,
  );
}

/** 按就诊/请求查询净剩可取消的已完成执行记录（IF-NUR-03B） */
export async function httpListNetRemainingProcedureRecordsForNurseStation(
  body: ListNetRemainingProcedureRecordsForNurseStationRequest,
): Promise<ListNetRemainingProcedureRecordsForNurseStationResponse> {
  return publicRequestClient.post<ListNetRemainingProcedureRecordsForNurseStationResponse>(
    '/outpatientNurseStationTreatment/listNetRemainingProcedureRecordsForNurseStation',
    body,
  );
}

/** 批量取消执行（IF-NUR-05） */
export async function httpBatchCancelDisposition(
  body: BatchCancelDispositionRequest,
): Promise<BatchCancelDispositionResponse> {
  return publicRequestClient.post<BatchCancelDispositionResponse>(
    '/outpatientNurseStationTreatment/batchCancelDisposition',
    body,
  );
}
