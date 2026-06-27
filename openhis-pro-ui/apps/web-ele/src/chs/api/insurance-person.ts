import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insurancePerson';

export interface YbPersonInfoRequest {
  cardSn?: string;
  certno?: string;
  contractId: number | string;
  insuplcAdmdvs?: string;
  mdtrtCertNo: string;
  mdtrtCertType: string;
  psnCertType?: string;
  psnName?: string;
}

export interface YbInsuInfoRowDto {
  balc?: number | string;
  cvlservFlag?: string;
  empName?: string;
  insuplcAdmdvs?: string;
  insutype?: string;
  psnInsuDate?: string;
  psnInsuStas?: string;
  psnType?: string;
}

export interface YbIdetInfoRowDto {
  begntime?: string;
  endtime?: string;
  memo?: string;
  psnIdetType?: string;
  psnTypeLv?: string;
}

export interface YbPersonInfoResponse {
  balc?: number | string;
  brdy?: string;
  certno?: string;
  gend?: string;
  idetinfoList?: YbIdetInfoRowDto[];
  insuinfoList?: YbInsuInfoRowDto[];
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
