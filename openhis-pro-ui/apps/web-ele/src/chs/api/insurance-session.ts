/**
 * 医保会话（9001 签到）— 对接 {@code POST /api/app/insuranceSession/<method>}
 */
import type { ApiResponse } from '#/types/api-response';

import { requestClient } from '#/api/request';
import { chsLongRequestConfig } from '#/chs/constants/request-timeout';

const BASE = '/insuranceSession';

export interface InsuranceSignInParams {
  contractBusNo: string;
  /** HeaSecReadInfo GetClientID 输出的 MAC 加密串 */
  mac?: string;
  ip?: string;
}

/** 手动触发 9001 签到，返回 sign_no */
export async function insuranceSignInApi(params: InsuranceSignInParams): Promise<string> {
  const res = await requestClient.post<ApiResponse<string>>(
    `${BASE}/signIn`,
    params,
    chsLongRequestConfig(),
  );
  return res.data ?? '';
}
