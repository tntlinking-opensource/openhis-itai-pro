import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insurancePerson';

export interface YbPersonInfoRequest {
  contractId: number | string;
  insuplcAdmdvs?: string;
  mdtrtCertNo: string;
  mdtrtCertType: string;
}

export interface YbPersonInfoResponse {
  balc?: number | string;
  certno?: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  psnName?: string;
  psnNo: string;
}

/** 1101 读卡/人员信息 */
export async function httpGetInsurancePersonInfo(
  body: YbPersonInfoRequest,
): Promise<YbPersonInfoResponse> {
  const res = await requestClient.post<ApiResponse<YbPersonInfoResponse>>(
    `${BASE}/getPersonInfo`,
    body,
    chsLongRequestConfig(),
  );
  if (!res.data?.psnNo) {
    throw new Error('医保读卡未返回人员编号');
  }
  return res.data;
}
