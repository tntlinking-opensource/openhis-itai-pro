import { httpRecallInpatientDischarge } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';

import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/** 提交出院召回并返回后端响应 */
export async function submitRecallInpatientDischarge(encounterId: string) {
  const normalizedId = preserveApiIdForRequest(encounterId);
  if (!normalizedId) {
    throw new Error('就诊标识无效');
  }
  return httpRecallInpatientDischarge({ encounterId: normalizedId });
}
