import type { ExecuteInpatientDischargeRequest } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.types';

import { httpExecuteInpatientDischarge } from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

/** 办理出院：仅提交 encounterId，操作人与出院时刻由后端解析 */
export async function submitInpatientDischarge(encounterId: string) {
  const body: ExecuteInpatientDischargeRequest = {
    encounterId: preserveApiIdForRequest(encounterId) ?? encounterId,
  };
  return httpExecuteInpatientDischarge(body);
}
