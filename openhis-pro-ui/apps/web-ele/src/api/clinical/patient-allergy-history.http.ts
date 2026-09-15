import type {
  DeletePatientAllergyHistoryRequest,
  ListPatientAllergyHistoryRequest,
  ListPatientAllergyHistoryResponse,
  LoadPatientAllergyHistoryInitResponse,
  SavePatientAllergyHistoryRequest,
  SavePatientAllergyHistoryResponse,
  SavePatientNkdaRequest,
} from './patient-allergy-history.types';

import { publicRequestClient } from '#/api/request';

/** 加载过敏史表单枚举选项 */
export async function httpLoadPatientAllergyHistoryInit(): Promise<LoadPatientAllergyHistoryInitResponse> {
  return publicRequestClient.post<LoadPatientAllergyHistoryInitResponse>(
    '/patientAllergyHistory/loadPatientAllergyHistoryInit',
    {},
  );
}

/** 按患者查询过敏史（跨就诊） */
export async function httpListPatientAllergyHistory(
  body: ListPatientAllergyHistoryRequest,
): Promise<ListPatientAllergyHistoryResponse> {
  return publicRequestClient.post<ListPatientAllergyHistoryResponse>(
    '/patientAllergyHistory/listPatientAllergyHistory',
    body,
  );
}

/** 保存主诉过敏史 */
export async function httpSavePatientAllergyHistory(
  body: SavePatientAllergyHistoryRequest,
): Promise<SavePatientAllergyHistoryResponse> {
  return publicRequestClient.post<SavePatientAllergyHistoryResponse>(
    '/patientAllergyHistory/savePatientAllergyHistory',
    body,
  );
}

/** 登记无已知过敏 */
export async function httpSavePatientNkda(
  body: SavePatientNkdaRequest,
): Promise<SavePatientAllergyHistoryResponse> {
  return publicRequestClient.post<SavePatientAllergyHistoryResponse>(
    '/patientAllergyHistory/savePatientNkda',
    body,
  );
}

/** 删除主诉过敏史或无已知过敏 */
export async function httpDeletePatientAllergyHistory(
  body: DeletePatientAllergyHistoryRequest,
): Promise<{ success?: boolean }> {
  return publicRequestClient.post<{ success?: boolean }>(
    '/patientAllergyHistory/deletePatientAllergyHistory',
    body,
  );
}
