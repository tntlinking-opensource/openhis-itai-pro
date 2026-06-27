import type { ClearDischargedBedRequest } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import { httpClearDischargedBed } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 清床离院：仅提交 encounterId，活动床与清床时刻由后端解析 */
export async function submitClearDischargedBed(encounterId: string) {
  const body: ClearDischargedBedRequest = {
    encounterId: preserveApiIdForRequest(encounterId) ?? encounterId,
  };
  return httpClearDischargedBed(body);
}
