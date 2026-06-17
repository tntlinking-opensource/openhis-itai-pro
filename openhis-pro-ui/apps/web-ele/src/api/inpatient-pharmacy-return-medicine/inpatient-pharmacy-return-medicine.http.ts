import type {
  ConfirmInpatientMedicineReturnRequest,
  ConfirmInpatientMedicineReturnResponse,
  GetInpatientReturnMedicineDetailRequest,
  GetInpatientReturnMedicineDetailResponse,
  LoadInpatientReturnMedicineInitRequest,
  LoadInpatientReturnMedicineInitResponse,
  PageInpatientReturnPatientRequest,
  PageInpatientReturnPatientResponse,
} from './inpatient-pharmacy-return-medicine.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/inpatientPharmacyReturnMedicine';

/** 加载住院退药页初始化元数据（药房、病区、退药状态） */
export async function httpLoadInpatientReturnMedicineInit(
  body: LoadInpatientReturnMedicineInitRequest = {},
): Promise<LoadInpatientReturnMedicineInitResponse> {
  return publicRequestClient.post<LoadInpatientReturnMedicineInitResponse>(
    `${BASE}/loadInpatientReturnMedicineInit`,
    body,
  );
}

/** 分页查询住院退药患者队列 */
export async function httpPageInpatientReturnPatientQueue(
  body: PageInpatientReturnPatientRequest,
): Promise<PageInpatientReturnPatientResponse> {
  return publicRequestClient.post<PageInpatientReturnPatientResponse>(
    `${BASE}/pageInpatientReturnPatientQueue`,
    body,
  );
}

/** 查询某就诊下住院退药明细 */
export async function httpGetInpatientReturnMedicineDetail(
  body: GetInpatientReturnMedicineDetailRequest,
): Promise<GetInpatientReturnMedicineDetailResponse> {
  return publicRequestClient.post<GetInpatientReturnMedicineDetailResponse>(
    `${BASE}/getInpatientReturnMedicineDetail`,
    body,
  );
}

/** 确认住院退药（仅确认，无拒绝分支） */
export async function httpConfirmInpatientMedicineReturn(
  body: ConfirmInpatientMedicineReturnRequest,
): Promise<ConfirmInpatientMedicineReturnResponse> {
  return publicRequestClient.post<ConfirmInpatientMedicineReturnResponse>(
    `${BASE}/confirmInpatientMedicineReturn`,
    body,
  );
}
