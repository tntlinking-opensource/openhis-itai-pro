/**
 * 医保签到（9001）— 对接 {@code POST /api/app/insuranceSign/signIn}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insuranceSign';

export interface InsuranceSignInParams {
  contractBusNo: string;
  /** HeaSecReadInfo GetClientID 输出的 MAC 加密串 → 服务端 signMac */
  mac?: string;
  ip?: string;
}

export interface InsuranceSignInResult {
  signNo?: string;
}

/** 手动触发 9001 签到，返回 sign_no */
export async function insuranceSignInApi(params: InsuranceSignInParams): Promise<string> {
  const res = await requestClient.post<ApiResponse<InsuranceSignInResult>>(
    `${BASE}/signIn`,
    {
      contractBusNo: params.contractBusNo,
      signMac: params.mac,
      signIp: params.ip,
    },
    chsLongRequestConfig(),
  );
  return res.data?.signNo ?? '';
}
