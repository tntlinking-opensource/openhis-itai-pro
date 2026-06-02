import type {
  CancelInpatientMedicationDispenseRequest,
  CancelInpatientMedicationDispenseResponse,
  DispenseInpatientMedicationsRequest,
  DispenseInpatientMedicationsResponse,
  LoadInpatientPharmacyDispensePageInitRequest,
  LoadInpatientPharmacyDispensePageInitResponse,
  PageEncounterMedicationDispenseSlipsRequest,
  PageEncounterMedicationDispenseSlipsResponse,
  PagePendingDispenseInpatientEncounterQueueRequest,
  PagePendingDispenseInpatientEncounterQueueResponse,
  SavePrepareInpatientMedicationDispenseRequest,
  SavePrepareInpatientMedicationDispenseResponse,
} from './inpatient-pharmacy-dispense.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/inpatientPharmacyDispense';

/** 加载住院发药页初始化（药房、病区、调配药师等） */
export async function httpLoadInpatientPharmacyDispensePageInit(
  body: LoadInpatientPharmacyDispensePageInitRequest,
): Promise<LoadInpatientPharmacyDispensePageInitResponse> {
  return publicRequestClient.post<LoadInpatientPharmacyDispensePageInitResponse>(
    `${BASE}/loadInpatientPharmacyDispensePageInit`,
    body,
  );
}

/** 分页查询在院待发/已发患者队列 */
export async function httpPagePendingDispenseInpatientEncounterQueue(
  body: PagePendingDispenseInpatientEncounterQueueRequest,
): Promise<PagePendingDispenseInpatientEncounterQueueResponse> {
  return publicRequestClient.post<PagePendingDispenseInpatientEncounterQueueResponse>(
    `${BASE}/pagePendingDispenseInpatientEncounterQueue`,
    body,
  );
}

/** 分页加载就诊下住院药品发放明细（批号/库房只读，无库存候选） */
export async function httpPageEncounterMedicationDispenseSlips(
  body: PageEncounterMedicationDispenseSlipsRequest,
): Promise<PageEncounterMedicationDispenseSlipsResponse> {
  return publicRequestClient.post<PageEncounterMedicationDispenseSlipsResponse>(
    `${BASE}/pageEncounterMedicationDispenseSlips`,
    body,
  );
}

/** 保存配药（PREPARATION→IN_PROGRESS，仅提交追溯码） */
export async function httpSavePrepareInpatientMedicationDispense(
  body: SavePrepareInpatientMedicationDispenseRequest,
): Promise<SavePrepareInpatientMedicationDispenseResponse> {
  return publicRequestClient.post<SavePrepareInpatientMedicationDispenseResponse>(
    `${BASE}/savePrepareInpatientMedicationDispense`,
    body,
  );
}

/** 发药扣库（IN_PROGRESS→COMPLETED） */
export async function httpDispenseInpatientMedications(
  body: DispenseInpatientMedicationsRequest,
): Promise<DispenseInpatientMedicationsResponse> {
  return publicRequestClient.post<DispenseInpatientMedicationsResponse>(
    `${BASE}/dispenseInpatientMedications`,
    body,
  );
}

/** 作废发放 */
export async function httpCancelInpatientMedicationDispense(
  body: CancelInpatientMedicationDispenseRequest,
): Promise<CancelInpatientMedicationDispenseResponse> {
  return publicRequestClient.post<CancelInpatientMedicationDispenseResponse>(
    `${BASE}/cancelInpatientMedicationDispense`,
    body,
  );
}
