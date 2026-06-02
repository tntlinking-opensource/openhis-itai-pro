import type {
  PageInpatientDoctorStationPatientQueueRequest,
  PageInpatientDoctorStationPatientQueueResponse,
} from './inpatient-doctor-station-encounter.types';

import { publicRequestClient } from '#/api/request';

/** 住院医生站 · 就诊管理（AS-IP-DOR-001） */
const BASE = '/inpatientDoctorStationEncounterManage';

/** 分页查询在院患者列表（POST …/pagePatientQueue，科室由登录上下文限定） */
export async function httpPageInpatientDoctorStationPatientQueue(
  body: PageInpatientDoctorStationPatientQueueRequest,
): Promise<PageInpatientDoctorStationPatientQueueResponse> {
  return publicRequestClient.post<PageInpatientDoctorStationPatientQueueResponse>(
    `${BASE}/pagePatientQueue`,
    body,
  );
}
