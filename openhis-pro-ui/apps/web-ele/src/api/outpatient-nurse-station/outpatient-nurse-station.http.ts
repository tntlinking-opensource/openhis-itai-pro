import type {
  BatchCancelDispositionRequest,
  BatchCancelDispositionResponse,
  BatchPerformDispositionRequest,
  BatchPerformDispositionResponse,
  DeleteOutpatientSkinTestRecordRequest,
  DeleteOutpatientSkinTestRecordResponse,
  ListManagedSupplyRoomOptionsResponse,
  ListNetRemainingProcedureRecordsForNurseStationRequest,
  ListNetRemainingProcedureRecordsForNurseStationResponse,
  ListOutpatientSkinTestRowsRequest,
  ListOutpatientSkinTestRowsResponse,
  ListProcedureRecordsForNurseStationRequest,
  ListProcedureRecordsForNurseStationResponse,
  LoadOutpatientSkinTestInitResponse,
  PageNurseStationPatientQueueRequest,
  PageNurseStationPatientQueueResponse,
  QueryEncounterTreatmentSheetRequest,
  QueryEncounterTreatmentSheetResponse,
  SaveOutpatientSkinTestRecordRequest,
  SaveOutpatientSkinTestRecordResponse,
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

/** 加载登录者管辖药房作为耗材房下拉 */
export async function httpListManagedSupplyRoomOptions(): Promise<ListManagedSupplyRoomOptionsResponse> {
  return publicRequestClient.post<ListManagedSupplyRoomOptionsResponse>(
    '/outpatientNurseStationTreatment/listManagedSupplyRoomOptions',
    {},
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

/** 加载皮试登记表单枚举选项 */
export async function httpLoadOutpatientSkinTestInit(): Promise<LoadOutpatientSkinTestInitResponse> {
  return publicRequestClient.post<LoadOutpatientSkinTestInitResponse>(
    '/outpatientNurseStationSkinTest/loadOutpatientSkinTestInit',
    {},
  );
}

/** 按就诊查询皮试待登记/已登记列表 */
export async function httpListOutpatientSkinTestRows(
  body: ListOutpatientSkinTestRowsRequest,
): Promise<ListOutpatientSkinTestRowsResponse> {
  return publicRequestClient.post<ListOutpatientSkinTestRowsResponse>(
    '/outpatientNurseStationSkinTest/listOutpatientSkinTestRows',
    body,
  );
}

/** 保存皮试临床结果（双签） */
export async function httpSaveOutpatientSkinTestRecord(
  body: SaveOutpatientSkinTestRecordRequest,
): Promise<SaveOutpatientSkinTestRecordResponse> {
  return publicRequestClient.post<SaveOutpatientSkinTestRecordResponse>(
    '/outpatientNurseStationSkinTest/saveOutpatientSkinTestRecord',
    body,
  );
}

/** 删除误录皮试记录 */
export async function httpDeleteOutpatientSkinTestRecord(
  body: DeleteOutpatientSkinTestRecordRequest,
): Promise<DeleteOutpatientSkinTestRecordResponse> {
  return publicRequestClient.post<DeleteOutpatientSkinTestRecordResponse>(
    '/outpatientNurseStationSkinTest/deleteOutpatientSkinTestRecord',
    body,
  );
}
