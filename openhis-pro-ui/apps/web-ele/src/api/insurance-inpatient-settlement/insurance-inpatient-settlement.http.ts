import type {
  GetIpInsuranceSettleStatusRequest,
  GetIpInsuranceSettleStatusResponse,
} from './insurance-inpatient-settlement.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/insuranceInpatientSettlement';

/** 轮询住院 IP_SETTLE 协同单状态 */
export async function httpGetInpatientInsuranceSettleStatus(
  body: GetIpInsuranceSettleStatusRequest,
): Promise<GetIpInsuranceSettleStatusResponse> {
  return publicRequestClient.post<GetIpInsuranceSettleStatusResponse>(
    `${BASE}/getInpatientInsuranceSettleStatus`,
    body,
  );
}
