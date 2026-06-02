import type {
  ListRefundableChargeLinesForEncounterRequest,
  ListRefundableChargeLinesForEncounterResponse,
  SubmitOutpatientRefundApplicationRequest,
  SubmitOutpatientRefundApplicationResponse,
  VerifyConsumableExecutionBeforeRefundRequest,
  VerifyConsumableExecutionBeforeRefundResponse,
} from './refund-application.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/doctorStationRefundApplication';

/** 按当前就诊加载可申请退费的收费行清单 */
export async function httpListRefundableChargeLinesForEncounter(
  body: ListRefundableChargeLinesForEncounterRequest,
): Promise<ListRefundableChargeLinesForEncounterResponse> {
  return publicRequestClient.post<ListRefundableChargeLinesForEncounterResponse>(
    `${BASE}/listRefundableChargeLinesForEncounter`,
    body,
  );
}

/** 提交前校验勾选收费项中耗材是否已执行 */
export async function httpVerifyConsumableExecutionBeforeRefund(
  body: VerifyConsumableExecutionBeforeRefundRequest,
): Promise<VerifyConsumableExecutionBeforeRefundResponse> {
  return publicRequestClient.post<VerifyConsumableExecutionBeforeRefundResponse>(
    `${BASE}/verifyConsumableExecutionBeforeRefund`,
    body,
  );
}

/** 提交门诊退费申请 */
export async function httpSubmitOutpatientRefundApplication(
  body: SubmitOutpatientRefundApplicationRequest,
): Promise<SubmitOutpatientRefundApplicationResponse> {
  return publicRequestClient.post<SubmitOutpatientRefundApplicationResponse>(
    `${BASE}/submitOutpatientRefundApplication`,
    body,
  );
}
