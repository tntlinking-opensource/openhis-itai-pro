import {
  httpListDischargePendingExecutions,
  httpListDischargePendingMedications,
} from '#/api/inpatient/nurse-station/inpatient-nurse-station-atd.http';
import { preserveApiIdForRequest } from '#/utils/preserve-api-id';

/** 拉取出院前待执行医嘱 */
export async function listDischargePendingExecutions(encounterId: string) {
  const id = preserveApiIdForRequest(encounterId) ?? encounterId;
  return httpListDischargePendingExecutions({ encounterId: id });
}

/** 拉取出院前待发退药 */
export async function listDischargePendingMedications(encounterId: string) {
  const id = preserveApiIdForRequest(encounterId) ?? encounterId;
  return httpListDischargePendingMedications({ encounterId: id });
}
