import type {
  GetOpInsuranceSettleStatusRequest,
  GetOpInsuranceSettleStatusResponse,
} from './insurance-outpatient-settlement.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/insuranceOutpatientSettlement';

/** 轮询门诊 OP_SETTLE 协同单状态 */
export async function httpGetOutpatientInsuranceSettleStatus(
  body: GetOpInsuranceSettleStatusRequest,
): Promise<GetOpInsuranceSettleStatusResponse> {
  return publicRequestClient.post<GetOpInsuranceSettleStatusResponse>(
    `${BASE}/getOutpatientInsuranceSettleStatus`,
    body,
  );
}
