import type {
  CheckInpatientAdmissionExistsRequest,
  CheckInpatientAdmissionExistsResponse,
  RequestInpatientAdmissionRequest,
  RequestInpatientAdmissionResponse,
  WithdrawInpatientAdmissionRequest,
  WithdrawInpatientAdmissionResponse,
} from './inpatient-admission.types';

import { publicRequestClient } from '#/api/request';

const BASE = '/doctorStationInpatientAdmission';

/** 按门诊就诊查询是否已存在未取消的住院申请 */
export async function httpCheckInpatientAdmissionExistsByAmbEncounter(
  body: CheckInpatientAdmissionExistsRequest,
): Promise<CheckInpatientAdmissionExistsResponse> {
  return publicRequestClient.post<CheckInpatientAdmissionExistsResponse>(
    `${BASE}/checkInpatientAdmissionExistsByAmbEncounter`,
    body,
  );
}

/** 发起住院申请（新建 IMP+PLANNED 住院线） */
export async function httpRequestInpatientAdmission(
  body: RequestInpatientAdmissionRequest,
): Promise<RequestInpatientAdmissionResponse> {
  return publicRequestClient.post<RequestInpatientAdmissionResponse>(
    `${BASE}/requestInpatientAdmission`,
    body,
  );
}

/** 撤回待登记（PLANNED）住院申请；撤回后可再次办理住院 */
export async function httpWithdrawInpatientAdmission(
  body: WithdrawInpatientAdmissionRequest,
): Promise<WithdrawInpatientAdmissionResponse> {
  return publicRequestClient.post<WithdrawInpatientAdmissionResponse>(
    `${BASE}/withdrawInpatientAdmission`,
    body,
  );
}
