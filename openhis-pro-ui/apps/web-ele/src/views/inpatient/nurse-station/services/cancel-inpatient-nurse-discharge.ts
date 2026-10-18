import type { CancelInpatientNurseDischargeRequest } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import { httpCancelInpatientNurseDischarge } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';

/**
 * 取消办理出院：仅提交 encounterId；目标态（待出院/已入科）由后端按出院嘱是否仍有效决定。
 *
 * @param encounterId 就诊主键
 */
export async function submitCancelInpatientNurseDischarge(encounterId: string) {
  const body: CancelInpatientNurseDischargeRequest = {
    encounterId: preserveApiIdForRequest(encounterId) ?? encounterId,
  };
  return httpCancelInpatientNurseDischarge(body);
}
