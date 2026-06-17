import type {
  GetOpInsuranceRevokeStatusRequest,
  GetOpInsuranceRevokeStatusResponse,
} from './insurance-outpatient-revocation.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/insuranceOutpatientRevocation';

/** 轮询门诊 OP_SETTLE_REVERSE 协同单状态 */
export async function httpGetOutpatientInsuranceRevokeStatus(
  body: GetOpInsuranceRevokeStatusRequest,
): Promise<GetOpInsuranceRevokeStatusResponse> {
  return publicRequestClient.post<GetOpInsuranceRevokeStatusResponse>(
    `${BASE}/getOutpatientInsuranceRevokeStatus`,
    body,
  );
}
