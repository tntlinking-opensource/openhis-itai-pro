import type {
  BatchCancelMedicalTechExecutionRequest,
  BatchCancelMedicalTechExecutionResponse,
  BatchPerformMedicalTechExecutionRequest,
  BatchPerformMedicalTechExecutionResponse,
  ListMedicalTechNetRemainingProceduresRequest,
  ListMedicalTechNetRemainingProceduresResponse,
  LoadMedicalTechExecutionSheetRequest,
  LoadMedicalTechExecutionSheetResponse,
  PageMedicalTechPatientQueueRequest,
  PageMedicalTechPatientQueueResponse,
  PageMedicalTechProcedureRecordsRequest,
  PageMedicalTechProcedureRecordsResponse,
} from './medical-tech-execution.types';

import { publicRequestClient } from '#/api/request';
import { parsePagedRowsFromApiData } from '#/utils/api/unwrap-paged-response';

const BASE = '/medicalTechExecution';

/** 分页加载医技患者队列 */
export async function httpPageMedicalTechPatientQueue(
  body: PageMedicalTechPatientQueueRequest,
): Promise<PageMedicalTechPatientQueueResponse> {
  const data = await publicRequestClient.post<unknown>(`${BASE}/pageMedicalTechPatientQueue`, body);
  const parsed =
    parsePagedRowsFromApiData<PageMedicalTechPatientQueueResponse['content'][number]>(data);
  return {
    content: parsed.content,
    totalCount: parsed.totalCount,
    pageNo: parsed.pageNo,
    pageSize: parsed.pageSize,
  };
}

/** 按就诊加载医技执行单（诊疗行 + 耗材行） */
export async function httpLoadMedicalTechExecutionSheet(
  body: LoadMedicalTechExecutionSheetRequest,
): Promise<LoadMedicalTechExecutionSheetResponse> {
  return publicRequestClient.post<LoadMedicalTechExecutionSheetResponse>(
    `${BASE}/loadMedicalTechExecutionSheet`,
    body,
  );
}

/** 分页加载医技执行记录 */
export async function httpPageMedicalTechProcedureRecords(
  body: PageMedicalTechProcedureRecordsRequest,
): Promise<PageMedicalTechProcedureRecordsResponse> {
  const data = await publicRequestClient.post<unknown>(
    `${BASE}/pageMedicalTechProcedureRecords`,
    body,
  );
  const parsed =
    parsePagedRowsFromApiData<PageMedicalTechProcedureRecordsResponse['content'][number]>(data);
  return {
    content: parsed.content,
    totalCount: parsed.totalCount,
    pageNo: parsed.pageNo,
    pageSize: parsed.pageSize,
  };
}

/** 查询净剩可取消的已完成执行记录 */
export async function httpListMedicalTechNetRemainingProcedures(
  body: ListMedicalTechNetRemainingProceduresRequest,
): Promise<ListMedicalTechNetRemainingProceduresResponse> {
  return publicRequestClient.post<ListMedicalTechNetRemainingProceduresResponse>(
    `${BASE}/listMedicalTechNetRemainingProcedures`,
    body,
  );
}

/** 批量医技执行（诊疗登记 + 耗材发放） */
export async function httpBatchPerformMedicalTechExecution(
  body: BatchPerformMedicalTechExecutionRequest,
): Promise<BatchPerformMedicalTechExecutionResponse> {
  return publicRequestClient.post<BatchPerformMedicalTechExecutionResponse>(
    `${BASE}/batchPerformMedicalTechExecution`,
    body,
  );
}

/** 批量取消医技执行（诊疗对冲 + 耗材回退） */
export async function httpBatchCancelMedicalTechExecution(
  body: BatchCancelMedicalTechExecutionRequest,
): Promise<BatchCancelMedicalTechExecutionResponse> {
  return publicRequestClient.post<BatchCancelMedicalTechExecutionResponse>(
    `${BASE}/batchCancelMedicalTechExecution`,
    body,
  );
}
