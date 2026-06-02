import type {
  CheckInpatientAdmissionExistsRequest,
  CheckInpatientAdmissionExistsResponse,
  RequestInpatientAdmissionRequest,
  RequestInpatientAdmissionResponse,
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
