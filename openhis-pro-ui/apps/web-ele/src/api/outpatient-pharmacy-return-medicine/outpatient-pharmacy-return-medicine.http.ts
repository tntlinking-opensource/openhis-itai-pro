import type {
  ConfirmMedicineReturnRequest,
  ConfirmMedicineReturnResponse,
  GetReturnMedicineDetailRequest,
  GetReturnMedicineDetailResponse,
  LoadReturnMedicineInitRequest,
  LoadReturnMedicineInitResponse,
  PageReturnPatientRequest,
  PageReturnPatientResponse,
} from './outpatient-pharmacy-return-medicine.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/outpatientPharmacyReturnMedicine';

/** 加载退药页初始化元数据（科室、退药状态选项） */
export async function httpLoadReturnMedicineInit(
  body: LoadReturnMedicineInitRequest = {},
): Promise<LoadReturnMedicineInitResponse> {
  return publicRequestClient.post<LoadReturnMedicineInitResponse>(
    `${BASE}/loadReturnMedicineInit`,
    body,
  );
}

/** 分页查询退药患者队列 */
export async function httpPageReturnPatientQueue(
  body: PageReturnPatientRequest,
): Promise<PageReturnPatientResponse> {
  return publicRequestClient.post<PageReturnPatientResponse>(
    `${BASE}/pageReturnPatientQueue`,
    body,
  );
}

/** 查询某就诊下退药明细 */
export async function httpGetReturnMedicineDetail(
  body: GetReturnMedicineDetailRequest,
): Promise<GetReturnMedicineDetailResponse> {
  return publicRequestClient.post<GetReturnMedicineDetailResponse>(
    `${BASE}/getReturnMedicineDetail`,
    body,
  );
}

/** 确认或拒绝退药 */
export async function httpConfirmMedicineReturn(
  body: ConfirmMedicineReturnRequest,
): Promise<ConfirmMedicineReturnResponse> {
  return publicRequestClient.post<ConfirmMedicineReturnResponse>(
    `${BASE}/confirmMedicineReturn`,
    body,
  );
}
