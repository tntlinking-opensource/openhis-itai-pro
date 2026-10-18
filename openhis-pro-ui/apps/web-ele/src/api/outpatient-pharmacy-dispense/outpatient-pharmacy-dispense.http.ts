import type {
  BatchResolveTraceIdentityRequest,
  BatchResolveTraceIdentityResponse,
  CancelOutpatientMedicationDispenseRequest,
  CancelOutpatientMedicationDispenseResponse,
  DispenseOutpatientMedicationsRequest,
  DispenseOutpatientMedicationsResponse,
  LoadPharmacyDispensePageInitRequest,
  LoadPharmacyDispensePageInitResponse,
  PageEncounterMedicationDispenseSlipsRequest,
  PageEncounterMedicationDispenseSlipsResponse,
  PagePendingDispenseEncounterQueueRequest,
  PagePendingDispenseEncounterQueueResponse,
  SavePrepareOutpatientMedicationDispenseRequest,
  SavePrepareOutpatientMedicationDispenseResponse,
} from './outpatient-pharmacy-dispense.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/outpatientPharmacyDispense';

/** 发药页初始化：药房/枚举/调配药师候选等 */
export async function httpLoadPharmacyDispensePageInit(
  body: LoadPharmacyDispensePageInitRequest,
): Promise<LoadPharmacyDispensePageInitResponse> {
  return publicRequestClient.post<LoadPharmacyDispensePageInitResponse>(
    `${BASE}/loadPharmacyDispensePageInit`,
    body,
  );
}

/** 与后端 {@code SilentPollHttpHeaders#NAME} 对齐：带此头时跳过访问/入参日志 */
const SILENT_POLL_HEADER = 'X-Zxplat-Silent-Poll';

export interface PagePendingDispenseEncounterQueueHttpOptions {
  /** 静默轮询：不写后端访问日志、失败不 toast；前端勿配合 loading */
  silentPoll?: boolean;
}

/** 分页加载待发/已发就诊队列 */
export async function httpPagePendingDispenseEncounterQueue(
  body: PagePendingDispenseEncounterQueueRequest,
  options?: PagePendingDispenseEncounterQueueHttpOptions,
): Promise<PagePendingDispenseEncounterQueueResponse> {
  const headers =
    options?.silentPoll === true
      ? {
          [SILENT_POLL_HEADER]: '1',
          'X-Zxplat-Soft-Fail': '1',
        }
      : undefined;
  return publicRequestClient.post<PagePendingDispenseEncounterQueueResponse>(
    `${BASE}/pagePendingDispenseEncounterQueue`,
    body,
    { headers },
  );
}

/** 分页加载某就诊下药品发放明细（含库存候选） */
export async function httpPageEncounterMedicationDispenseSlips(
  body: PageEncounterMedicationDispenseSlipsRequest,
): Promise<PageEncounterMedicationDispenseSlipsResponse> {
  return publicRequestClient.post<PageEncounterMedicationDispenseSlipsResponse>(
    `${BASE}/pageEncounterMedicationDispenseSlips`,
    body,
  );
}

/** 保存配药（PREPARATION→IN_PROGRESS） */
export async function httpSavePrepareOutpatientMedicationDispense(
  body: SavePrepareOutpatientMedicationDispenseRequest,
): Promise<SavePrepareOutpatientMedicationDispenseResponse> {
  return publicRequestClient.post<SavePrepareOutpatientMedicationDispenseResponse>(
    `${BASE}/savePrepareOutpatientMedicationDispense`,
    body,
  );
}

/** 发药扣库（IN_PROGRESS→COMPLETED） */
export async function httpDispenseOutpatientMedications(
  body: DispenseOutpatientMedicationsRequest,
): Promise<DispenseOutpatientMedicationsResponse> {
  return publicRequestClient.post<DispenseOutpatientMedicationsResponse>(
    `${BASE}/dispenseOutpatientMedications`,
    body,
  );
}

/** 作废发放 */
export async function httpCancelOutpatientMedicationDispense(
  body: CancelOutpatientMedicationDispenseRequest,
): Promise<CancelOutpatientMedicationDispenseResponse> {
  return publicRequestClient.post<CancelOutpatientMedicationDispenseResponse>(
    `${BASE}/cancelOutpatientMedicationDispense`,
    body,
  );
}

/** 批量扫码身份反查（只读，供发药/库房等前端行级匹配） */
export async function httpBatchResolveTraceIdentityForDispense(
  body: BatchResolveTraceIdentityRequest,
): Promise<BatchResolveTraceIdentityResponse> {
  return publicRequestClient.post<BatchResolveTraceIdentityResponse>(
    `${BASE}/batchResolveTraceIdentity`,
    body,
  );
}
