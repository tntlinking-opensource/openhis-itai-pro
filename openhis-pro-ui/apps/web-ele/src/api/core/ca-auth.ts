import type { ApiResponse } from '#/types/api-response';

import { loginRequestClient, requestClient } from '#/api/request';

export namespace CaAuthApi {
  /** 获取挑战值响应 */
  export interface ChallengeResponse {
    challengeId: string;
    nonce: string;
  }

  /** CA证书登录请求参数 */
  export interface CaLoginParams {
    challengeId: string;
    nonce: string;
    signature: string;
    certificate: string;
    algorithm?: string;
  }

  /** CA证书登录响应 */
  export interface CaLoginResult {
    accessToken: string;
    refreshToken: string;
    expiresIn: number;
    userId: number;
    userName: string;
    name: string;
    loginMethod: string;
    certSerialNumber: string;
    loginTime: string;
  }
}

/**
 * 获取CA挑战值
 */
export async function getChallengeApi() {
  return requestClient.post<ApiResponse<CaAuthApi.ChallengeResponse>>('/caAuth/getChallenge');
}

/**
 * CA证书登录
 */
export async function caLoginApi(data: CaAuthApi.CaLoginParams) {
  return loginRequestClient.post<ApiResponse<CaAuthApi.CaLoginResult>>('/caAuth/caLogin', data);
}
