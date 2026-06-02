import type {
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

/** 分页加载待发/已发就诊队列 */
export async function httpPagePendingDispenseEncounterQueue(
  body: PagePendingDispenseEncounterQueueRequest,
): Promise<PagePendingDispenseEncounterQueueResponse> {
  return publicRequestClient.post<PagePendingDispenseEncounterQueueResponse>(
    `${BASE}/pagePendingDispenseEncounterQueue`,
    body,
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
